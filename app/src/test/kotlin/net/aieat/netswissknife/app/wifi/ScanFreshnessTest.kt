package net.aieat.netswissknife.app.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScanFreshnessTest {

    @Test
    fun `a rejected scan request is reported as throttled without waiting`() {
        val outcome = decideOutcome(startScanReturned = false, broadcastArrived = false)

        assertTrue(outcome.throttled)
        assertFalse(outcome.broadcastArrived)
    }

    @Test
    fun `a completed scan request is not throttled`() {
        val outcome = decideOutcome(startScanReturned = true, broadcastArrived = true)

        assertFalse(outcome.throttled)
        assertTrue(outcome.broadcastArrived)
    }

    @Test
    fun `a timed out scan is not confused with platform throttling`() {
        val outcome = decideOutcome(startScanReturned = true, broadcastArrived = false)

        assertFalse(outcome.throttled)
        assertFalse(outcome.broadcastArrived)
    }

    @Test
    fun `link addresses are split into IPv4 and IPv6 values`() {
        val mapped = WifiConnectionInfoMapper.mapLinkAddresses(
            listOf("192.168.1.5/24", "fe80::1/64", "2001:db8::5/64")
        )

        assertEquals("192.168.1.5", mapped.ipv4Address)
        assertEquals(listOf("fe80::1", "2001:db8::5"), mapped.ipv6Addresses)
    }
}
