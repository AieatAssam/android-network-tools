package com.example.netswissknife.core.network.dns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("DnsRecordType")
class DnsRecordTypeTest {

    @Nested
    @DisplayName("display names")
    inner class DisplayNames {

        @Test
        fun `A record has correct display name`() {
            assertEquals("A", DnsRecordType.A.displayName)
        }

        @Test
        fun `AAAA record has correct display name`() {
            assertEquals("AAAA", DnsRecordType.AAAA.displayName)
        }

        @Test
        fun `MX record has correct display name`() {
            assertEquals("MX", DnsRecordType.MX.displayName)
        }

        @Test
        fun `TXT record has correct display name`() {
            assertEquals("TXT", DnsRecordType.TXT.displayName)
        }

        @Test
        fun `CNAME record has correct display name`() {
            assertEquals("CNAME", DnsRecordType.CNAME.displayName)
        }

        @Test
        fun `NS record has correct display name`() {
            assertEquals("NS", DnsRecordType.NS.displayName)
        }

        @Test
        fun `SOA record has correct display name`() {
            assertEquals("SOA", DnsRecordType.SOA.displayName)
        }

        @Test
        fun `PTR record has correct display name`() {
            assertEquals("PTR", DnsRecordType.PTR.displayName)
        }
    }

    @Nested
    @DisplayName("DNS type integers")
    inner class DnsTypeIntegers {

        @Test
        fun `A record maps to type 1`() {
            assertEquals(1, DnsRecordType.A.dnsTypeInt)
        }

        @Test
        fun `AAAA record maps to type 28`() {
            assertEquals(28, DnsRecordType.AAAA.dnsTypeInt)
        }

        @Test
        fun `MX record maps to type 15`() {
            assertEquals(15, DnsRecordType.MX.dnsTypeInt)
        }

        @Test
        fun `TXT record maps to type 16`() {
            assertEquals(16, DnsRecordType.TXT.dnsTypeInt)
        }

        @Test
        fun `CNAME record maps to type 5`() {
            assertEquals(5, DnsRecordType.CNAME.dnsTypeInt)
        }

        @Test
        fun `NS record maps to type 2`() {
            assertEquals(2, DnsRecordType.NS.dnsTypeInt)
        }

        @Test
        fun `SOA record maps to type 6`() {
            assertEquals(6, DnsRecordType.SOA.dnsTypeInt)
        }

        @Test
        fun `PTR record maps to type 12`() {
            assertEquals(12, DnsRecordType.PTR.dnsTypeInt)
        }

        @Test
        fun `SRV record maps to type 33`() {
            assertEquals(33, DnsRecordType.SRV.dnsTypeInt)
        }

        @Test
        fun `CAA record maps to type 257`() {
            assertEquals(257, DnsRecordType.CAA.dnsTypeInt)
        }
    }

    @Nested
    @DisplayName("descriptions")
    inner class Descriptions {

        @ParameterizedTest
        @EnumSource(DnsRecordType::class)
        fun `every record type has a non-empty description`(type: DnsRecordType) {
            assertTrue(type.description.isNotBlank(), "${type.name} must have a non-empty description")
        }
    }

    @Nested
    @DisplayName("fromDisplayName")
    inner class FromDisplayName {

        @Test
        fun `finds A record by display name`() {
            assertEquals(DnsRecordType.A, DnsRecordType.fromDisplayName("A"))
        }

        @Test
        fun `lookup is case-insensitive`() {
            assertEquals(DnsRecordType.MX, DnsRecordType.fromDisplayName("mx"))
        }

        @Test
        fun `returns null for unknown display name`() {
            assertNull(DnsRecordType.fromDisplayName("UNKNOWN"))
        }

        @ParameterizedTest
        @EnumSource(DnsRecordType::class)
        fun `fromDisplayName round-trips for every type`(type: DnsRecordType) {
            assertNotNull(DnsRecordType.fromDisplayName(type.displayName))
        }
    }
}
