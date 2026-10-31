package net.aieat.netswissknife.core.network.wol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
        val bytes = WolMagicPacket.parseMac("02:23:45:67:89:AB")
        assertArrayEquals(
            byteArrayOf(0x02, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte()),
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
        val packet = WolMagicPacket.build("02:02:03:04:05:06")

        assertEquals(WolMagicPacket.PACKET_SIZE, packet.size)
        assertEquals(102, packet.size)

        for (i in 0 until 6) {
            assertEquals(0xFF.toByte(), packet[i], "header byte $i")
        }

        val mac = byteArrayOf(0x02, 0x02, 0x03, 0x04, 0x05, 0x06)
        for (rep in 0 until 16) {
            val offset = 6 + rep * 6
            assertArrayEquals(mac, packet.copyOfRange(offset, offset + 6), "repetition $rep")
        }
    }

    @Test
    fun `build throws on invalid MAC`() {
        assertThrows<IllegalArgumentException> { WolMagicPacket.build("ZZ:ZZ:ZZ:ZZ:ZZ:ZZ") }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "AA:BB-CC:DD:EE:FF",
            "AA-BB:CC-DD:EE-FF",
            "AA:BB:CC:DD:EE-FF",
            "AA.BB.CC.DD.EE.FF",
            "AABB.CCDD.EE.FF",
            "AA:BBCC:DD:EE:FF",
        ],
    )
    fun `rejects mixed or malformed separator styles`(mac: String) {
        assertFalse(WolMagicPacket.isValidMac(mac), mac)
    }

    @ParameterizedTest
    @ValueSource(strings = ["AA:BB:CC:DD:EE FF", "AA BB CC DD EE FF", "AA:BB:CC:DD:EE:FF\t:"])
    fun `rejects whitespace inside the address`(mac: String) {
        assertFalse(WolMagicPacket.isValidMac(mac), mac)
    }

    @ParameterizedTest
    @ValueSource(strings = ["01:00:5E:00:00:01", "33:33:00:00:00:01", "01-23-45-67-89-AB"])
    fun `rejects multicast MAC addresses`(mac: String) {
        assertFalse(WolMagicPacket.isValidMac(mac), mac)
    }

    @Test
    fun `rejects the broadcast and all-zero MAC addresses`() {
        assertFalse(WolMagicPacket.isValidMac("FF:FF:FF:FF:FF:FF"))
        assertFalse(WolMagicPacket.isValidMac("ffffffffffff"))
        assertFalse(WolMagicPacket.isValidMac("00:00:00:00:00:00"))
    }

    @Test
    fun `macProblem names the specific reason for each rejected shape`() {
        assertEquals(WolMacProblem.BLANK, WolMagicPacket.macProblem("   "))
        assertEquals(WolMacProblem.LENGTH, WolMagicPacket.macProblem("AA:BB:CC:DD:EE"))
        assertEquals(WolMacProblem.LENGTH, WolMagicPacket.macProblem("AA:BB:CC:DD:EE:FF:00"))
        assertEquals(WolMacProblem.CHARACTERS, WolMagicPacket.macProblem("zz:zz:zz:zz:zz:zz"))
        assertEquals(WolMacProblem.SEPARATORS, WolMagicPacket.macProblem("AA:BB-CC:DD:EE:FF"))
        assertEquals(WolMacProblem.MULTICAST, WolMagicPacket.macProblem("01:00:5e:00:00:01"))
        assertEquals(WolMacProblem.BROADCAST, WolMagicPacket.macProblem("FF:FF:FF:FF:FF:FF"))
        assertEquals(WolMacProblem.ZERO, WolMagicPacket.macProblem("00:00:00:00:00:00"))
        assertNull(WolMagicPacket.macProblem("AABB.CCDD.EEFF"))
    }

    @Test
    fun `canonicalMacOrNull accepts every notation and returns uppercase colon form`() {
        assertEquals("AA:BB:CC:DD:EE:FF", WolMagicPacket.canonicalMacOrNull(" aa-bb-cc-dd-ee-ff "))
        assertEquals("AA:BB:CC:DD:EE:FF", WolMagicPacket.canonicalMacOrNull("AABB.CCDD.EEFF"))
        assertEquals("AA:BB:CC:DD:EE:FF", WolMagicPacket.canonicalMacOrNull("aabbccddeeff"))
        assertNull(WolMagicPacket.canonicalMacOrNull("01:00:5E:00:00:01"))
    }

    @Test
    fun `all accepted notations build identical magic packets`() {
        val reference = WolMagicPacket.build("AA:BB:CC:DD:EE:FF")
        for (mac in listOf("aa-bb-cc-dd-ee-ff", "AABB.CCDD.EEFF", "aabbccddeeff", " AABBCCDDEEFF ")) {
            assertArrayEquals(reference, WolMagicPacket.build(mac), mac)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["192.168.1.255", "255.255.255.255", "10.0.0.1", "0.0.0.0", " 172.16.0.9 "])
    fun `parseIpv4 accepts dotted decimal literals`(address: String) {
        val octets = address.trim().split('.')
        val expected = ByteArray(4) { octets[it].toInt().toByte() }
        assertArrayEquals(expected, WolMagicPacket.parseIpv4(address))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "", "router.local", "example.com", "256.1.1.1", "192.168.1", "192.168.1.1.1",
            "192.168.01.1", "192.168..1", "-1.2.3.4", "fe80::1", "192.168.1.1/24",
        ],
    )
    fun `parseIpv4 rejects hostnames, IPv6, malformed and out-of-range literals`(address: String) {
        assertNull(WolMagicPacket.parseIpv4(address), address)
    }
}
