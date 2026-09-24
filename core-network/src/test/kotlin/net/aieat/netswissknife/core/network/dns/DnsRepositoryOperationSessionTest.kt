package net.aieat.netswissknife.core.network.dns

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.io.Closeable
import java.io.DataInputStream
import java.io.InterruptedIOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Executor

class DnsRepositoryOperationSessionTest {

    @Test
    fun `cancellation before executor starts prevents a queued task opening a socket`() {
        val queuedTask = AtomicReference<Runnable?>()
        val openedSocket = AtomicReference<Closeable?>()
        val session = newSession()
        val transport = SessionIoClientFactory(
            session = session,
            onSocketRegistered = { openedSocket.set(it) },
            taskExecutor = Executor { queuedTask.set(it) },
        )
        val query = Message.newQuery(Record.newRecord(Name.fromString("example.com."), Type.A, DClass.IN))
        val future = transport.createOrGetUdpClient().sendAndReceiveUdp(
            null,
            InetSocketAddress(InetAddress.getLoopbackAddress(), 53),
            query,
            query.toWire(),
            512,
            Duration.ofSeconds(1),
        )

        session.cancel(CancellationReason.USER_STOP)
        checkNotNull(queuedTask.get()).run()

        assertTrue(future.isCancelled)
        assertTrue(openedSocket.get() == null, "cancelled queued work must not open a late socket")
        assertTrue(session.resources.isClosed)
    }

    @Test
    fun `cancelled queued work frees a bounded executor slot for a retry`() {
        val executor = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(1),
        )
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        executor.execute {
            workerStarted.countDown()
            releaseWorker.await()
        }
        assertTrue(workerStarted.await(2, TimeUnit.SECONDS))

        val firstSession = newSession()
        val firstTransport = SessionIoClientFactory(firstSession, taskExecutor = executor)
        val query = Message.newQuery(Record.newRecord(Name.fromString("example.com."), Type.A, DClass.IN))
        val firstFuture = firstTransport.createOrGetUdpClient().sendAndReceiveUdp(
            null,
            InetSocketAddress(InetAddress.getLoopbackAddress(), 53),
            query,
            query.toWire(),
            512,
            Duration.ofSeconds(1),
        )
        assertTrue(executor.queue.size == 1)

        val secondSession = newSession()
        try {
            firstSession.cancel(CancellationReason.USER_STOP)
            assertTrue(firstFuture.isCancelled)
            assertTrue(executor.queue.isEmpty(), "cancellation should remove queued transport work")

            val secondFuture = SessionIoClientFactory(secondSession, taskExecutor = executor)
                .createOrGetUdpClient()
                .sendAndReceiveUdp(
                    null,
                    InetSocketAddress(InetAddress.getLoopbackAddress(), 53),
                    query,
                    query.toWire(),
                    512,
                    Duration.ofSeconds(1),
                )
            assertFalse(secondFuture.isCompletedExceptionally, "retry should not be rejected behind a cancelled task")
            assertTrue(executor.queue.size == 1)
        } finally {
            secondSession.cancel(CancellationReason.USER_STOP)
            releaseWorker.countDown()
            executor.shutdownNow()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `udp response from a different source cannot win the lookup`() {
        val resolver = DatagramSocket(0, InetAddress.getLoopbackAddress())
        val unrelatedSender = DatagramSocket(0, InetAddress.getLoopbackAddress())
        val receivedQuery = CountDownLatch(1)
        val sendConfiguredReply = CountDownLatch(1)
        val session = newSession()
        val correctResponse = byteArrayOf(9, 8, 7, 6)
        val responder = Thread {
            runCatching {
                val payload = ByteArray(512)
                val request = DatagramPacket(payload, payload.size)
                resolver.receive(request)
                receivedQuery.countDown()
                unrelatedSender.send(
                    DatagramPacket(byteArrayOf(1, 1), 2, request.socketAddress as InetSocketAddress)
                )
                sendConfiguredReply.await(2, TimeUnit.SECONDS)
                resolver.send(
                    DatagramPacket(correctResponse, correctResponse.size, request.socketAddress)
                )
            }
        }.apply {
            name = "dns-wrong-source-fixture"
            isDaemon = true
            start()
        }

        try {
            val query = Message.newQuery(Record.newRecord(Name.fromString("example.com."), Type.A, DClass.IN))
            val future = SessionIoClientFactory(session).createOrGetUdpClient().sendAndReceiveUdp(
                null,
                InetSocketAddress(InetAddress.getLoopbackAddress(), resolver.localPort),
                query,
                query.toWire(),
                512,
                Duration.ofSeconds(2),
            )
            assertTrue(receivedQuery.await(2, TimeUnit.SECONDS))
            // Let the unrelated datagram reach the client before the configured server replies.
            Thread.sleep(50)
            assertFalse(future.isDone, "an unrelated sender must not complete the query")
            sendConfiguredReply.countDown()
            assertArrayEquals(correctResponse, future.get(2, TimeUnit.SECONDS))
        } finally {
            session.cancel(CancellationReason.USER_STOP)
            sendConfiguredReply.countDown()
            resolver.close()
            unrelatedSender.close()
            responder.join(500)
        }
    }

    @Test
    fun `user cancellation closes an active dns tcp socket`() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val queryReceived = CountDownLatch(1)
        val serverReadClosed = CountDownLatch(1)
        val clientSocket = AtomicReference<Closeable?>()
        val serverThread = Thread {
            runCatching {
                server.accept().use { accepted ->
                    val input = DataInputStream(accepted.getInputStream())
                    val length = input.readUnsignedShort()
                    input.readFully(ByteArray(length))
                    queryReceived.countDown()
                    try {
                        input.read()
                    } finally {
                        serverReadClosed.countDown()
                    }
                }
            }
        }.apply {
            name = "dns-tcp-test-fixture"
            isDaemon = true
            start()
        }
        val session = newSession()
        try {
            val query = Message.newQuery(Record.newRecord(Name.fromString("example.com."), Type.A, DClass.IN))
            val future = SessionIoClientFactory(
                session = session,
                onSocketRegistered = { clientSocket.set(it) },
            ).createOrGetTcpClient().sendAndReceiveTcp(
                null,
                InetSocketAddress(InetAddress.getLoopbackAddress(), server.localPort),
                query,
                query.toWire(),
                Duration.ofSeconds(3),
            )
            assertTrue(queryReceived.await(2, TimeUnit.SECONDS))
            val socket = checkNotNull(clientSocket.get()) as Socket
            assertFalse(socket.isClosed)

            session.cancel(CancellationReason.USER_STOP)
            withTimeout(2_000) {
                while (!future.isDone || serverReadClosed.count != 0L) kotlinx.coroutines.yield()
            }

            assertTrue(future.isCancelled)
            assertTrue(socket.isClosed)
            assertTrue(serverReadClosed.count == 0L)
            assertTrue(session.resources.isClosed)
        } finally {
            session.cancel(CancellationReason.USER_STOP)
            server.close()
            serverThread.join(500)
        }
    }

    @Test
    fun `user cancellation closes active dns socket and prevents late result`() = runBlocking {
        val fixture = HangingDnsFixture()
        val socket = AtomicReference<Closeable?>()
        val session = newSession()
        val repository = fixture.repository().apply {
            ioClientFactoryFactory = { current ->
                SessionIoClientFactory(current, onSocketRegistered = { socket.set(it) })
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val query = scope.async {
                repository.lookup("example.com", DnsRecordType.A, DnsServer.Custom("127.0.0.1"), session)
            }
            assertTrue(fixture.queryReceived.await(2, TimeUnit.SECONDS), "DNS datagram should reach the fixture")
            assertFalse(checkNotNull(socket.get()).let { (it as DatagramSocket).isClosed })

            session.cancel(CancellationReason.USER_STOP)
            withTimeout(2_000) { query.join() }

            assertTrue(query.isCancelled)
            assertTrue(session.resources.isClosed)
            assertTrue(checkNotNull(socket.get()).let { (it as DatagramSocket).isClosed })
            assertTrue(fixture.queryReceived.count == 0L)
        } finally {
            scope.cancel()
            fixture.close()
        }
    }

    @Test
    fun `deadline closes active dns socket and returns timeout error`() = runBlocking {
        val fixture = HangingDnsFixture()
        val socket = AtomicReference<Closeable?>()
        val budget = OperationBudget.start(
            requirement = OperationRequirement.INTERNET,
            timeoutMillis = 100,
            maxConcurrentProbes = 1,
        )
        val session = OperationSession(budget)
        val repository = fixture.repository().apply {
            ioClientFactoryFactory = { current ->
                SessionIoClientFactory(current, onSocketRegistered = { socket.set(it) })
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val query = scope.async {
                repository.lookup("example.com", DnsRecordType.A, DnsServer.Custom("127.0.0.1"), session)
            }
            assertTrue(fixture.queryReceived.await(2, TimeUnit.SECONDS), "DNS datagram should reach the fixture")
            val result = withTimeout(2_000) { query.await() }
            assertTrue(result is NetworkResult.Error)
            assertTrue((result as NetworkResult.Error).message.contains("deadline", ignoreCase = true))
            assertTrue(session.resources.isClosed)
            assertTrue(checkNotNull(socket.get()).let { (it as DatagramSocket).isClosed })
        } finally {
            scope.cancel()
            fixture.close()
        }
    }

    @Test
    fun `user cancellation interrupts a resolver wait between transport stages`() = runBlocking {
        val enteredSend = CountDownLatch(1)
        val interruptedSend = CountDownLatch(1)
        val releaseSend = CountDownLatch(1)
        val session = newSession()
        val resolver = object : SimpleResolver(InetSocketAddress(InetAddress.getLoopbackAddress(), 53)) {
            override fun send(query: Message): Message {
                enteredSend.countDown()
                try {
                    releaseSend.await()
                    return query
                } catch (interrupted: InterruptedException) {
                    interruptedSend.countDown()
                    throw InterruptedIOException("resolver send interrupted").also {
                        it.initCause(interrupted)
                    }
                }
            }
        }
        val repository = DnsRepositoryImpl(DnsRepositoryImpl.ResolverFactory { resolver })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val query = scope.async {
                repository.lookup("example.com", DnsRecordType.A, DnsServer.Custom("127.0.0.1"), session)
            }
            assertTrue(enteredSend.await(2, TimeUnit.SECONDS))
            session.cancel(CancellationReason.USER_STOP)
            withTimeout(2_000) { query.join() }

            assertTrue(query.isCancelled)
            assertTrue(interruptedSend.await(2, TimeUnit.SECONDS), "scope cleanup should interrupt blocking resolver wait")
            assertTrue(session.resources.isClosed)
        } finally {
            releaseSend.countDown()
            scope.cancel()
        }
    }

    private fun newSession(): OperationSession = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.INTERNET,
            timeoutMillis = 5_000,
            maxConcurrentProbes = 1,
        )
    )

    private class HangingDnsFixture : Closeable {
        private val socket = DatagramSocket(0, InetAddress.getLoopbackAddress())
        val queryReceived = CountDownLatch(1)
        private val receiver = Thread {
            runCatching {
                val payload = ByteArray(65_535)
                socket.receive(DatagramPacket(payload, payload.size))
                queryReceived.countDown()
                // Deliberately withhold a DNS response; cancellation/deadline must close the client socket.
                while (!socket.isClosed) Thread.sleep(10)
            }
        }.apply {
            name = "dns-test-fixture"
            isDaemon = true
            start()
        }

        fun repository(): DnsRepositoryImpl = DnsRepositoryImpl(
            resolverFactory = DnsRepositoryImpl.ResolverFactory {
                object : SimpleResolver(InetSocketAddress(InetAddress.getLoopbackAddress(), socket.localPort)) {
                    // Keep dnsjava's own timeout beyond the operation deadline so the deadline
                    // watcher, not SimpleResolver's timeout, must close the in-flight transport.
                    override fun setTimeout(timeout: Duration) = Unit
                }
            }
        )

        override fun close() {
            socket.close()
            receiver.join(500)
        }
    }
}
