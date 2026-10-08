package net.aieat.netswissknife.app.wifi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import net.aieat.netswissknife.core.network.wifi.WifiScanRefreshStatus

class ScanFreshnessTest {

    @Test
    fun `a rejected request is distinguished without waiting`() {
        val outcome = decideOutcome(startScanReturned = false, broadcastArrived = false)

        assertEquals(WifiScanRefreshStatus.REJECTED, outcome.status)
    }

    @Test
    fun `an updated completion is successful`() {
        val outcome = decideOutcome(
            startScanReturned = true,
            broadcastArrived = true,
            resultsUpdated = true
        )

        assertEquals(WifiScanRefreshStatus.UPDATED, outcome.status)
    }

    @Test
    fun `a completion with a false or missing updated extra preserves cached status`() {
        val outcome = decideOutcome(startScanReturned = true, broadcastArrived = true)

        assertEquals(WifiScanRefreshStatus.NOT_UPDATED, outcome.status)
    }

    @Test
    fun `an accepted request without its completion broadcast times out`() {
        val outcome = decideOutcome(startScanReturned = true, broadcastArrived = false)

        assertEquals(WifiScanRefreshStatus.TIMED_OUT, outcome.status)
    }

    @Test
    fun `cache read timestamp reflects the sample age`() {
        assertEquals(
            58_000L,
            estimateScanSampleTimeMs(
                nowWallClockMs = 100_000L,
                scanAgeMs = 42_000L,
                refreshStatus = WifiScanRefreshStatus.TIMED_OUT
            )
        )
    }

    @Test
    fun `empty confirmed update uses its completion time`() {
        assertEquals(
            100_000L,
            estimateScanSampleTimeMs(
                nowWallClockMs = 100_000L,
                scanAgeMs = null,
                refreshStatus = WifiScanRefreshStatus.UPDATED
            )
        )
    }

    @Test
    fun `empty failed refresh has no invented sample timestamp`() {
        assertEquals(
            0L,
            estimateScanSampleTimeMs(
                nowWallClockMs = 100_000L,
                scanAgeMs = null,
                refreshStatus = WifiScanRefreshStatus.TIMED_OUT
            )
        )
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
