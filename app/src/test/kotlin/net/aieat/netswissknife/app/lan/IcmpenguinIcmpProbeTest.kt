package net.aieat.netswissknife.app.lan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class IcmpenguinIcmpProbeTest {

    @Test
    fun `native linkage failure is treated as an unavailable echo`() = runBlocking {
        val probe = IcmpenguinIcmpProbe { _, _ -> throw UnsatisfiedLinkError("missing JNI library") }

        assertEquals(null, probe.echo("192.0.2.7", 100))
    }

    @Test
    fun `cancellation is not converted to an unavailable echo`() {
        val probe = IcmpenguinIcmpProbe { _, _ -> throw CancellationException("stop scan") }

        assertThrows(CancellationException::class.java) {
            runBlocking { probe.echo("192.0.2.7", 100) }
        }
    }
}
