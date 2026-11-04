package net.aieat.netswissknife.core.network.traceroute

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TracerouteStopPolicyTest {
    private val device = "10.109.208.146"
    private val localAddresses = setOf(device)

    @Test
    fun `the trace stops at the hop where the destination first replied`() {
        val hops = listOf(hop(1, "192.0.2.1"), hop(2, "1.1.1.1", reached = true))

        assertEquals(
            TraceStopReason.DESTINATION_REACHED,
            TracerouteStopPolicy.stopAfter(hops, localAddresses),
        )
    }

    @Test
    fun `a router hop before the destination does not stop the trace`() {
        assertEquals(null, TracerouteStopPolicy.stopAfter(listOf(hop(1, "192.0.2.1")), localAddresses))
    }

    @Test
    fun `one reply from this device is not yet proof of unreachability`() {
        val hops = listOf(hop(1, "192.0.2.1"), hop(2, device))

        assertEquals(null, TracerouteStopPolicy.stopAfter(hops, localAddresses))
        assertFalse(TracerouteStopPolicy.isUnreachableFromThisDevice(hops, localAddresses))
    }

    @Test
    fun `two consecutive replies from this device stop the trace as unreachable`() {
        val hops = listOf(hop(1, device), hop(2, device))

        assertEquals(TraceStopReason.UNREACHABLE_FROM_THIS_DEVICE, TracerouteStopPolicy.stopAfter(hops, localAddresses))
        assertTrue(TracerouteStopPolicy.isUnreachableFromThisDevice(hops, localAddresses))
    }

    @Test
    fun `a router reply in the middle of the run resets the count`() {
        val hops = listOf(hop(1, device), hop(2, "192.0.2.1"), hop(3, device))

        assertEquals(null, TracerouteStopPolicy.stopAfter(hops, localAddresses))
    }

    @Test
    fun `a timeout breaks a run of replies from this device`() {
        val hops = listOf(hop(1, device), hop(2, null), hop(3, device))

        assertEquals(null, TracerouteStopPolicy.stopAfter(hops, localAddresses))
    }

    @Test
    fun `a destination reply from this device is a reached destination, not unreachable`() {
        val hops = listOf(hop(1, device), hop(2, device, reached = true))

        assertEquals(TraceStopReason.DESTINATION_REACHED, TracerouteStopPolicy.stopAfter(hops, localAddresses))
        assertFalse(TracerouteStopPolicy.isUnreachableFromThisDevice(hops, localAddresses))
    }

    @Test
    fun `no local addresses means no unreachable verdict`() {
        val hops = listOf(hop(1, device), hop(2, device))

        assertEquals(null, TracerouteStopPolicy.stopAfter(hops, emptySet()))
    }

    @Test
    fun `an empty trace has no stop reason`() {
        assertEquals(null, TracerouteStopPolicy.stopAfter(emptyList(), localAddresses))
    }

    private fun hop(
        number: Int,
        ip: String?,
        reached: Boolean = false,
    ): HopResult =
        HopResult(
            hopNumber = number,
            ip = ip,
            hostname = null,
            rtTimeMs = ip?.let { 5L },
            status = if (ip == null) HopStatus.TIMEOUT else HopStatus.SUCCESS,
            destinationReached = reached,
        )
}
