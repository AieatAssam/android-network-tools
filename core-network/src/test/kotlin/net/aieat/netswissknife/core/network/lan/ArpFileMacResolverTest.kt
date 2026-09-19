package net.aieat.netswissknife.core.network.lan

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArpFileMacResolverTest {
    private val content = """
        IP address       HW type Flags HW address            Mask     Device
        192.168.1.1      0x1     0x2   aa:bb:cc:dd:ee:ff     *        wlan0
        192.168.1.2      0x1     0x0   00:00:00:00:00:00     *        wlan0
    """.trimIndent()

    @Test
    fun `parses known arp entry and normalises mac`() = runTest {
        val resolver = ArpFileMacResolver { content }
        assertEquals("AA:BB:CC:DD:EE:FF", resolver.resolve("192.168.1.1"))
        assertTrue(resolver.supported)
    }

    @Test
    fun `ignores incomplete entry`() = runTest {
        assertNull(ArpFileMacResolver { content }.resolve("192.168.1.2"))
    }

    @Test
    fun `unsupported when reader is empty or throws`() {
        assertFalse(ArpFileMacResolver { "" }.supported)
        assertFalse(ArpFileMacResolver { error("permission denied") }.supported)
    }

    @Test
    fun `unknown ip has no mac`() = runTest {
        assertNull(ArpFileMacResolver { content }.resolve("192.168.1.99"))
    }
}
