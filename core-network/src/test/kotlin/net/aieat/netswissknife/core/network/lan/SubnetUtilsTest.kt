package net.aieat.netswissknife.core.network.lan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("SubnetUtils")
class SubnetUtilsTest {

    @Nested
    @DisplayName("isValidCidr")
    inner class IsValidCidr {

        @Test fun `valid slash-24 subnet`() = assertTrue(SubnetUtils.isValidCidr("192.168.1.0/24"))

        @Test fun `valid slash-16 subnet`() = assertTrue(SubnetUtils.isValidCidr("10.0.0.0/16"))

        @Test fun `valid slash-30 subnet`() = assertTrue(SubnetUtils.isValidCidr("192.168.1.0/30"))

        @Test fun `prefix 0 is invalid`() = assertFalse(SubnetUtils.isValidCidr("0.0.0.0/0"))

        @Test fun `prefix 31 is invalid`() = assertFalse(SubnetUtils.isValidCidr("192.168.1.0/31"))

        @Test fun `prefix 32 is invalid`() = assertFalse(SubnetUtils.isValidCidr("192.168.1.0/32"))

        @Test fun `missing prefix is invalid`() = assertFalse(SubnetUtils.isValidCidr("192.168.1.0"))

        @Test fun `blank string is invalid`() = assertFalse(SubnetUtils.isValidCidr(""))

        @Test fun `non-ip part is invalid`() = assertFalse(SubnetUtils.isValidCidr("abc.def.ghi.jkl/24"))

        @Test fun `octet above 255 is invalid`() = assertFalse(SubnetUtils.isValidCidr("192.168.256.0/24"))

        @Test fun `prefix 15 is invalid (too large subnet)`() =
            assertFalse(SubnetUtils.isValidCidr("10.0.0.0/15"))
    }

    @Nested
    @DisplayName("parseSubnet")
    inner class ParseSubnet {

        @Test
        fun `slash-30 generates 2 host addresses`() {
            val hosts = SubnetUtils.parseSubnet("192.168.1.0/30")
            assertEquals(2, hosts.size)
            assertEquals("192.168.1.1", hosts[0])
            assertEquals("192.168.1.2", hosts[1])
        }

        @Test
        fun `slash-24 generates 254 host addresses`() {
            val hosts = SubnetUtils.parseSubnet("192.168.1.0/24")
            assertEquals(254, hosts.size)
            assertEquals("192.168.1.1", hosts.first())
            assertEquals("192.168.1.254", hosts.last())
        }

        @Test
        fun `slash-24 does not include network address`() {
            val hosts = SubnetUtils.parseSubnet("192.168.1.0/24")
            assertFalse(hosts.contains("192.168.1.0"))
        }

        @Test
        fun `slash-24 does not include broadcast address`() {
            val hosts = SubnetUtils.parseSubnet("192.168.1.0/24")
            assertFalse(hosts.contains("192.168.1.255"))
        }

        @Test
        fun `input ip is normalized to network address`() {
            // Even if user provides a host IP, we still parse the network correctly
            val hosts = SubnetUtils.parseSubnet("192.168.1.5/24")
            assertEquals(254, hosts.size)
            assertTrue(hosts.contains("192.168.1.1"))
        }

        @Test
        fun `slash-16 generates 65534 host addresses`() {
            val hosts = SubnetUtils.parseSubnet("10.0.0.0/16")
            assertEquals(65534, hosts.size)
        }

        @Test
        fun `invalid prefix throws exception`() {
            assertThrows<Exception> { SubnetUtils.parseSubnet("192.168.1.0/33") }
        }

        @Test
        fun `octet above 255 throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> { SubnetUtils.parseSubnet("999.0.0.0/24") }
        }

        @Test
        fun `octet above 255 in second position throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> { SubnetUtils.parseSubnet("192.300.0.0/24") }
        }

        @Test
        fun `non-numeric octet throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> { SubnetUtils.parseSubnet("abc.0.0.0/24") }
        }

        @Test
        fun `too few octets throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> { SubnetUtils.parseSubnet("192.168.1/24") }
        }
    }

    @Nested
    @DisplayName("parseIpToLong")
    inner class ParseIpToLong {

        @Test
        fun `valid ip converts correctly`() {
            assertEquals(0xC0A80101L, SubnetUtils.parseIpToLong("192.168.1.1"))
        }

        @Test
        fun `octet above 255 throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> { SubnetUtils.parseIpToLong("256.0.0.1") }
        }

        @Test
        fun `non-numeric octet throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> { SubnetUtils.parseIpToLong("192.168.x.1") }
        }

        @Test
        fun `wrong number of octets throws IllegalArgumentException`() {
            assertThrows<IllegalArgumentException> { SubnetUtils.parseIpToLong("192.168.1") }
        }
    }
}
