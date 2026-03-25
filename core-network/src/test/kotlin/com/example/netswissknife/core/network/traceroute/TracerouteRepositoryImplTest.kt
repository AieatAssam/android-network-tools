package com.example.netswissknife.core.network.traceroute

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("TracerouteRepositoryImpl – line parsing")
class TracerouteRepositoryImplTest {

    private val impl = TracerouteRepositoryImpl()

    // ── parseTracerouteLine ────────────────────────────────────────────────────

    @Nested
    @DisplayName("parseTracerouteLine")
    inner class ParseLine {

        @Test
        fun `returns null for blank line`() {
            assertNull(impl.parseTracerouteLine(""))
        }

        @Test
        fun `returns null for header line`() {
            assertNull(impl.parseTracerouteLine("traceroute to google.com, 30 hops max"))
        }

        @Test
        fun `parses successful hop with IP and RTT`() {
            val result = impl.parseTracerouteLine(" 1  192.168.1.1  1.234 ms  1.456 ms  1.789 ms")
            assertNotNull(result)
            assertEquals(1, result!!.hopNumber)
            assertEquals("192.168.1.1", result.ip)
            assertEquals(HopStatus.SUCCESS, result.status)
            assertEquals(1L, result.rtTimeMs)
        }

        @Test
        fun `parses all-timeout hop`() {
            val result = impl.parseTracerouteLine(" 3  * * *")
            assertNotNull(result)
            assertEquals(3, result!!.hopNumber)
            assertNull(result.ip)
            assertEquals(HopStatus.TIMEOUT, result.status)
            assertNull(result.rtTimeMs)
        }

        @Test
        fun `parses mixed hop where first probe timed out`() {
            val result = impl.parseTracerouteLine(" 2  10.0.0.1  10.234 ms  *  11.234 ms")
            assertNotNull(result)
            assertEquals(2, result!!.hopNumber)
            assertEquals("10.0.0.1", result.ip)
            assertEquals(HopStatus.SUCCESS, result.status)
        }

        @Test
        fun `parses hop number correctly`() {
            val result = impl.parseTracerouteLine("15  8.8.8.8  12.3 ms")
            assertNotNull(result)
            assertEquals(15, result!!.hopNumber)
        }

        @Test
        fun `returns null when line starts with non-numeric`() {
            assertNull(impl.parseTracerouteLine("  traceroute header text"))
        }

        @Test
        fun `parses IP at position 2 even with leading spaces`() {
            val result = impl.parseTracerouteLine("   4  172.16.0.1  5.0 ms")
            assertNotNull(result)
            assertEquals("172.16.0.1", result!!.ip)
        }

        @Test
        fun `timeout hop has null geoLocation by default`() {
            val result = impl.parseTracerouteLine(" 5  * * *")
            assertNull(result?.geoLocation)
        }

        @Test
        fun `success hop has null geoLocation before enrichment`() {
            val result = impl.parseTracerouteLine(" 1  8.8.8.8  15 ms")
            assertNull(result?.geoLocation)
        }
    }

    // ── Command builder injection ──────────────────────────────────────────────

    @Nested
    @DisplayName("CommandBuilder")
    inner class CommandBuilderTests {

        @Test
        @DisplayName("custom command builder is used")
        fun customCommandBuilderIsUsed() = runTest {
            var capturedHost: String? = null
            var capturedMaxHops = 0
            val fakeBuilder = TracerouteRepositoryImpl.CommandBuilder { host, maxHops, _, _ ->
                capturedHost    = host
                capturedMaxHops = maxHops
                // Return a command that produces minimal output and exits quickly
                listOf("echo", "traceroute header\n 1  127.0.0.1  0.1 ms")
            }

            val repo = TracerouteRepositoryImpl(commandBuilder = fakeBuilder)
            repo.trace("8.8.8.8", maxHops = 5, timeoutMs = 1000, queriesPerHop = 1).toList()

            assertEquals("8.8.8.8", capturedHost)
            assertEquals(5, capturedMaxHops)
        }
    }

    // ── GeoIpRepositoryImpl private-IP detection ───────────────────────────────

    @Nested
    @DisplayName("GeoIpRepositoryImpl – private IP detection")
    inner class PrivateIpTest {

        private val geoRepo = GeoIpRepositoryImpl()

        @Test
        @DisplayName("lookup returns null for 192.168 range")
        fun lookupReturnsNullForRfc1918Class192() = runTest {
            assertNull(geoRepo.lookup("192.168.1.100"))
        }

        @Test
        @DisplayName("lookup returns null for 10.x range")
        fun lookupReturnsNullForRfc1918Class10() = runTest {
            assertNull(geoRepo.lookup("10.0.0.1"))
        }

        @Test
        @DisplayName("lookup returns null for 172.16 range")
        fun lookupReturnsNullForRfc1918Class172() = runTest {
            assertNull(geoRepo.lookup("172.16.5.5"))
        }

        @Test
        @DisplayName("lookup returns null for loopback")
        fun lookupReturnsNullForLoopback() = runTest {
            assertNull(geoRepo.lookup("127.0.0.1"))
        }

        @Test
        @DisplayName("lookup returns null for link-local")
        fun lookupReturnsNullForLinkLocal() = runTest {
            assertNull(geoRepo.lookup("169.254.1.1"))
        }
    }
}
