package net.aieat.netswissknife.core.network.testkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScriptedSocketTest {
    @Test
    fun `closing socket unblocks a blocked input read and counts closes`() {
        val socket = ScriptedSocket()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val read = executor.submit<Int> { socket.getInputStream().read() }

            assertTrue(socket.awaitBlockingRead(1, TimeUnit.SECONDS), "read did not start")
            assertFalse(read.isDone, "read should wait until close")

            socket.close()

            assertEquals(-1, read.get(1, TimeUnit.SECONDS))
            assertTrue(socket.isClosed)
            assertEquals(1, socket.closeCallCount)

            socket.close()
            assertEquals(2, socket.closeCallCount)
        } finally {
            socket.close()
            executor.shutdownNow()
        }
    }
}
