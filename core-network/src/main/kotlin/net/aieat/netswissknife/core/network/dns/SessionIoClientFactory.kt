package net.aieat.netswissknife.core.network.dns

import org.xbill.DNS.io.IoClientFactory
import org.xbill.DNS.io.TcpIoClient
import org.xbill.DNS.io.UdpIoClient
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * dnsjava 3.6.5's default IoClientFactory shares a selector client and exposes no close hook.
 * Its CompletionStage cancellation therefore does not close a pending datagram exchange.
 * These per-query clients use ordinary sockets that the operation ResourceScope can close.
 */
internal class SessionIoClientFactory(
    private val session: OperationSession,
    private val onSocketRegistered: (java.io.Closeable) -> Unit = {},
    private val taskExecutor: Executor = DEFAULT_EXECUTOR,
) : IoClientFactory {
    override fun createOrGetUdpClient(): UdpIoClient = UdpIoClient { local, remote, _, query, size, timeout ->
        submit {
            val socket = DatagramSocket(null)
            val localAddress = local ?: localFor(remote)
            val lease = session.resources.register(SocketLease(socket))
            try {
                onSocketRegistered(socket)
                socket.bind(localAddress)
                socket.soTimeout = timeoutMillis(timeout)
                // A connected UDP socket filters packets from any address other than the
                // configured resolver, which prevents an unrelated datagram winning the query.
                socket.connect(remote)
                val responseBuffer = ByteArray(size.coerceAtLeast(512).coerceAtMost(65_535))
                socket.send(DatagramPacket(query, query.size))
                val response = DatagramPacket(responseBuffer, responseBuffer.size)
                do {
                    response.length = responseBuffer.size
                    socket.receive(response)
                } while (!isPeer(response, remote))
                response.data.copyOf(response.length)
            } finally {
                if (session.resources.release(lease)) lease.close()
            }
        }
    }

    override fun createOrGetTcpClient(): TcpIoClient = TcpIoClient { local, remote, _, query, timeout ->
        submit {
            val socket = Socket()
            val lease = session.resources.register(SocketLease(socket))
            try {
                onSocketRegistered(socket)
                if (local != null) socket.bind(local)
                socket.connect(remote, timeoutMillis(timeout))
                socket.soTimeout = timeoutMillis(timeout)
                val out = DataOutputStream(socket.getOutputStream())
                out.writeShort(query.size)
                out.write(query)
                out.flush()
                val input = DataInputStream(socket.getInputStream())
                val length = input.readUnsignedShort()
                val response = ByteArray(length)
                input.readFully(response)
                response
            } finally {
                if (session.resources.release(lease)) lease.close()
            }
        }
    }

    private fun <T> submit(block: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        val taskLease = TaskLease(future)
        try {
            session.resources.register(taskLease)
        } catch (failure: Throwable) {
            taskLease.completeExceptionally(failure)
            return future
        }
        try {
            val task = Runnable {
                if (!taskLease.start(Thread.currentThread())) {
                    if (session.resources.release(taskLease)) taskLease.close()
                } else {
                    try {
                        session.throwIfCancelled()
                        session.budget.throwIfExpired()
                        taskLease.complete(block())
                    } catch (failure: Throwable) {
                        if (session.cancellationReason != null) {
                            // Scope cleanup closes sockets before the worker lease. That socket
                            // close can wake this task with an IOException; preserve the
                            // already-recorded operation cancellation on the transport future.
                            taskLease.close()
                        } else {
                            taskLease.completeExceptionally(CompletionException(failure))
                        }
                    } finally {
                        taskLease.finish()
                        if (session.resources.release(taskLease)) taskLease.close()
                    }
                }
            }
            taskLease.bindTask(task, taskExecutor)
            if (future.isDone) {
                if (session.resources.release(taskLease)) taskLease.close()
                return future
            }
            taskExecutor.execute(task)
            // Cancellation can win just before or during execute(); remove the no-op task
            // immediately when the executor supports queue removal.
            if (future.isDone) taskLease.removeQueuedTask()
        } catch (failure: Throwable) {
            if (session.cancellationReason != null) taskLease.close()
            else taskLease.completeExceptionally(failure)
            if (session.resources.release(taskLease)) taskLease.close()
        }
        return future
    }

    private fun timeoutMillis(timeout: Duration): Int =
        timeout.toMillis().coerceAtLeast(1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun localFor(remote: InetSocketAddress): InetSocketAddress =
        if (remote.address is Inet6Address) {
            InetSocketAddress(Inet6Address.getByAddress(null, ByteArray(16), 0), 0)
        } else {
            InetSocketAddress(InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0)), 0)
        }

    private fun isPeer(response: DatagramPacket, remote: InetSocketAddress): Boolean {
        val sender = response.socketAddress as? InetSocketAddress ?: return false
        if (sender.port != remote.port || sender.isUnresolved || remote.isUnresolved) return false
        // Compare address bytes rather than InetSocketAddress equality so IPv6 scope-id
        // representation differences do not reject a reply from the connected peer.
        return sender.address.address.contentEquals(remote.address.address)
    }

    private class SocketLease(private val socket: java.io.Closeable) : AutoCloseable {
        private val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        override fun close() {
            if (closed.compareAndSet(false, true)) socket.close()
        }
    }

    private class TaskLease<T>(
        private val future: CompletableFuture<T>,
    ) : AutoCloseable {
        private enum class State { QUEUED, RUNNING, FINISHED, CANCELLED }

        private val lock = Any()
        private var state = State.QUEUED
        private var thread: Thread? = null
        private var queuedTask: Pair<ThreadPoolExecutor, Runnable>? = null

        fun start(worker: Thread): Boolean = synchronized(lock) {
            if (state != State.QUEUED || future.isDone) return@synchronized false
            Thread.interrupted()
            state = State.RUNNING
            thread = worker
            true
        }

        fun finish() {
            synchronized(lock) {
                if (state == State.RUNNING) state = State.FINISHED
                thread = null
                // A cancelled socket receive may leave the worker interrupted. Clear it before
                // the executor can assign this thread to another DNS task.
                Thread.interrupted()
            }
        }

        fun complete(value: T): Boolean = synchronized(lock) {
            if (state != State.RUNNING || future.isDone) false else future.complete(value)
        }

        fun completeExceptionally(failure: Throwable): Boolean = synchronized(lock) {
            if (state == State.CANCELLED || state == State.FINISHED || future.isDone) {
                false
            } else {
                future.completeExceptionally(failure)
            }
        }

        fun bindTask(task: Runnable, executor: Executor) {
            val pool = executor as? ThreadPoolExecutor ?: return
            synchronized(lock) {
                queuedTask = pool to task
                if (state == State.CANCELLED || future.isDone) pool.remove(task)
            }
        }

        fun removeQueuedTask() {
            synchronized(lock) { queuedTask?.let { (pool, task) -> pool.remove(task) } }
        }

        override fun close() {
            val cancelFuture = synchronized(lock) {
                if (state == State.FINISHED || state == State.CANCELLED) return
                state = State.CANCELLED
                thread?.interrupt()
                queuedTask?.let { (pool, task) -> pool.remove(task) }
                true
            }
            if (cancelFuture) future.cancel(true)
        }
    }

    private companion object {
        // DNS queries are single-flight per UI operation; the bounded worker pool keeps a
        // caller-owned resolver from creating unbounded blocking workers under cancellation.
        val DEFAULT_EXECUTOR: Executor = createDnsIoExecutor()
    }
}

/** Same bounded pool factory used by the production shared executor and saturation regression. */
internal fun createDnsIoExecutor(): ThreadPoolExecutor = ThreadPoolExecutor(
    DNS_IO_WORKER_COUNT,
    DNS_IO_WORKER_COUNT,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(DNS_IO_QUEUE_CAPACITY),
    { task -> Thread(task, "dns-query-io").apply { isDaemon = true } },
    ThreadPoolExecutor.AbortPolicy(),
)

internal const val DNS_IO_WORKER_COUNT = 4
internal const val DNS_IO_QUEUE_CAPACITY = 16
