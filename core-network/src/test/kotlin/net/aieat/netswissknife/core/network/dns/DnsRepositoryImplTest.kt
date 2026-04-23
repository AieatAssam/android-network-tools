package net.aieat.netswissknife.core.network.dns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("DnsRepositoryImpl.normalizeDomain")
class DnsRepositoryImplTest {

    // ── IPv4 PTR ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("IPv4 PTR reversal")
    inner class IPv4Ptr {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(
            "172.217.17.78,  78.17.217.172.in-addr.arpa.",
            "8.8.8.8,        8.8.8.8.in-addr.arpa.",
            "1.2.3.4,        4.3.2.1.in-addr.arpa.",
            "192.168.1.100,  100.1.168.192.in-addr.arpa.",
            "10.0.0.1,       1.0.0.10.in-addr.arpa."
        )
        fun `plain IPv4 is reversed and suffixed`(ip: String, expected: String) {
            assertEquals(
                expected.trim(),
                DnsRepositoryImpl.normalizeDomain(ip.trim(), DnsRecordType.PTR)
            )
        }

        @Test
        fun `trailing dot on IPv4 input is stripped before reversal`() {
            assertEquals(
                "4.3.2.1.in-addr.arpa.",
                DnsRepositoryImpl.normalizeDomain("1.2.3.4.", DnsRecordType.PTR)
            )
        }

        @Test
        fun `already-reversed in-addr arpa input passes through with trailing dot`() {
            assertEquals(
                "78.17.217.172.in-addr.arpa.",
                DnsRepositoryImpl.normalizeDomain("78.17.217.172.in-addr.arpa", DnsRecordType.PTR)
            )
        }

        @Test
        fun `already-reversed in-addr arpa with trailing dot is unchanged`() {
            assertEquals(
                "78.17.217.172.in-addr.arpa.",
                DnsRepositoryImpl.normalizeDomain("78.17.217.172.in-addr.arpa.", DnsRecordType.PTR)
            )
        }
    }

    // ── IPv6 PTR ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("IPv6 PTR reversal")
    inner class IPv6Ptr {

        @Test
        fun `Google DNS IPv6 compressed address is fully expanded and reversed`() {
            // 2001:4860:4860::8888 -> 2001:4860:4860:0000:0000:0000:0000:8888
            // hex: 20014860486000000000000000008888
            // reversed nibbles: 8.8.8.8.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.6.8.4.0.6.8.4.1.0.0.2
            assertEquals(
                "8.8.8.8.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.6.8.4.0.6.8.4.1.0.0.2.ip6.arpa.",
                DnsRepositoryImpl.normalizeDomain("2001:4860:4860::8888", DnsRecordType.PTR)
            )
        }

        @Test
        fun `loopback IPv6 is reversed`() {
            // ::1 -> 0000:0000:0000:0000:0000:0000:0000:0001
            // hex: 00000000000000000000000000000001
            // reversed: 1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0
            assertEquals(
                "1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.ip6.arpa.",
                DnsRepositoryImpl.normalizeDomain("::1", DnsRecordType.PTR)
            )
        }

        @Test
        fun `Cloudflare IPv6 DNS address is reversed`() {
            // 2606:4700:4700::1111
            // expanded: 2606:4700:4700:0000:0000:0000:0000:1111
            // hex: 26064700470000000000000000001111
            // reversed nibbles: 1.1.1.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.7.4.0.0.7.4.6.0.6.2
            assertEquals(
                "1.1.1.1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.7.4.0.0.7.4.6.0.6.2.ip6.arpa.",
                DnsRepositoryImpl.normalizeDomain("2606:4700:4700::1111", DnsRecordType.PTR)
            )
        }

        @Test
        fun `full uncompressed IPv6 address is reversed`() {
            // 2001:0db8:0000:0000:0000:0000:0000:0001
            // hex: 20010db8000000000000000000000001
            // reversed: 1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2
            assertEquals(
                "1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.b.d.0.1.0.0.2.ip6.arpa.",
                DnsRepositoryImpl.normalizeDomain("2001:0db8:0000:0000:0000:0000:0000:0001", DnsRecordType.PTR)
            )
        }

        @Test
        fun `leading double-colon IPv6 is reversed`() {
            // ::ffff:192.0.2.1 (IPv4-mapped) should still be treated as IPv6
            // But a simple :: all-zeros address:
            // :: -> 0000:0000:0000:0000:0000:0000:0000:0000
            assertEquals(
                "0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.ip6.arpa.",
                DnsRepositoryImpl.normalizeDomain("::", DnsRecordType.PTR)
            )
        }

        @Test
        fun `already-reversed ip6 arpa input passes through with trailing dot`() {
            assertEquals(
                "8.8.8.8.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.6.8.4.0.6.8.4.1.0.0.2.ip6.arpa.",
                DnsRepositoryImpl.normalizeDomain(
                    "8.8.8.8.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.6.8.4.0.6.8.4.1.0.0.2.ip6.arpa",
                    DnsRecordType.PTR
                )
            )
        }
    }

    // ── Non-PTR types ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Non-PTR record types")
    inner class NonPtr {

        @ParameterizedTest(name = "{1} query for {0}")
        @CsvSource(
            "example.com,   A",
            "example.com,   AAAA",
            "example.com,   MX",
            "example.com,   TXT",
            "example.com,   CNAME",
            "example.com,   NS",
            "example.com,   SOA",
            "example.com,   SRV",
            "example.com,   CAA"
        )
        fun `plain domain has trailing dot appended`(domain: String, type: String) {
            val recordType = DnsRecordType.valueOf(type.trim())
            assertEquals(
                "example.com.",
                DnsRepositoryImpl.normalizeDomain(domain.trim(), recordType)
            )
        }

        @Test
        fun `domain with existing trailing dot is unchanged`() {
            assertEquals(
                "example.com.",
                DnsRepositoryImpl.normalizeDomain("example.com.", DnsRecordType.A)
            )
        }

        @Test
        fun `IPv4-looking string is NOT reversed for non-PTR types`() {
            assertEquals(
                "8.8.8.8.",
                DnsRepositoryImpl.normalizeDomain("8.8.8.8", DnsRecordType.A)
            )
        }

        @Test
        fun `SRV service label is left as-is`() {
            assertEquals(
                "_http._tcp.example.com.",
                DnsRepositoryImpl.normalizeDomain("_http._tcp.example.com", DnsRecordType.SRV)
            )
        }
    }

    // ── PTR with non-IP hostname ──────────────────────────────────────────────

    @Nested
    @DisplayName("PTR with non-IP input")
    inner class PtrNonIp {

        @Test
        fun `hostname passed as PTR query appends trailing dot without modification`() {
            assertEquals(
                "some.custom.ptr.host.",
                DnsRepositoryImpl.normalizeDomain("some.custom.ptr.host", DnsRecordType.PTR)
            )
        }
    }
}
