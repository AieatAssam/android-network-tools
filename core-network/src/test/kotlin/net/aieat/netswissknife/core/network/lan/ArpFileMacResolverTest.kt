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
    fun `empty readable table remains supported while read failure is unsupported`() {
        assertTrue(ArpFileMacResolver { "" }.supported)
        assertFalse(ArpFileMacResolver { error("permission denied") }.supported)
    }

    @Test
    fun `unknown ip has no mac`() = runTest {
        assertNull(ArpFileMacResolver { content }.resolve("192.168.1.99"))
    }

    @Test
    fun `snapshot reads a fresh table while cached resolver remains bounded`() = runTest {
        val expandedContent = content + "\n192.168.1.3      0x1     0x2   11:22:33:44:55:66     *        wlan0"
        var reads = 0
        val resolver = ArpFileMacResolver {
            reads++
            if (reads == 1) content else expandedContent
        }

        assertEquals("AA:BB:CC:DD:EE:FF", resolver.resolve("192.168.1.1"))
        assertEquals(1, reads)

        val snapshot = resolver.snapshot()
        assertTrue(snapshot.supported)
        assertEquals("11:22:33:44:55:66", snapshot.resolve("192.168.1.3"))
        assertEquals(2, reads)
        assertNull(snapshot.resolve("192.168.1.99"))
        assertEquals(2, reads)
    }
}
