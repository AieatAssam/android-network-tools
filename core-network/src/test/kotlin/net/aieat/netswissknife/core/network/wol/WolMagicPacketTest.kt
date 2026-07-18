package net.aieat.netswissknife.core.network.wol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class WolMagicPacketTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "AA:BB:CC:DD:EE:FF",
            "aa:bb:cc:dd:ee:ff",
            "AA-BB-CC-DD-EE-FF",
            "AABB.CCDD.EEFF",
            "AABBCCDDEEFF",
            "00:11:22:33:44:55",
        ]
    )
    fun `accepts common MAC notations`(mac: String) {
        assertTrue(WolMagicPacket.isValidMac(mac))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "AA:BB:CC:DD:EE",
            "AA:BB:CC:DD:EE:FF:00",
            "GG:BB:CC:DD:EE:FF",
            "AA:BB:CC:DD:EE:F",
            "AABBCCDDEEF",
            "AA.BB.CC.DD.EE.FF",
            "192.168.1.1",
            "hello",
        ]
    )
    fun `rejects invalid MAC strings`(mac: String) {
        assertFalse(WolMagicPacket.isValidMac(mac))
    }

    @Test
    fun `isValidMac tolerates surrounding whitespace`() {
        assertTrue(WolMagicPacket.isValidMac("  AA:BB:CC:DD:EE:FF  "))
    }

    @Test
    fun `parseMac returns the six raw bytes`() {
        val bytes = WolMagicPacket.parseMac("01:23:45:67:89:AB")
        assertArrayEquals(
            byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte()),
            bytes
        )
    }

    @Test
    fun `parseMac throws on invalid input`() {
        assertThrows<IllegalArgumentException> { WolMagicPacket.parseMac("nonsense") }
    }

    @Test
    fun `normalizeMac produces canonical colon form`() {
        assertEquals("AA:BB:CC:DD:EE:FF", WolMagicPacket.normalizeMac("aabb.ccdd.eeff"))
        assertEquals("00:11:22:33:44:55", WolMagicPacket.normalizeMac("001122334455"))
    }

    @Test
    fun `build produces 102-byte payload with FF header and 16 MAC repetitions`() {
        val packet = WolMagicPacket.build("01:02:03:04:05:06")

        assertEquals(WolMagicPacket.PACKET_SIZE, packet.size)
        assertEquals(102, packet.size)

        for (i in 0 until 6) {
            assertEquals(0xFF.toByte(), packet[i], "header byte $i")
        }

        val mac = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)
        for (rep in 0 until 16) {
            val offset = 6 + rep * 6
            assertArrayEquals(mac, packet.copyOfRange(offset, offset + 6), "repetition $rep")
        }
    }

    @Test
    fun `build throws on invalid MAC`() {
        assertThrows<IllegalArgumentException> { WolMagicPacket.build("ZZ:ZZ:ZZ:ZZ:ZZ:ZZ") }
    }
}
