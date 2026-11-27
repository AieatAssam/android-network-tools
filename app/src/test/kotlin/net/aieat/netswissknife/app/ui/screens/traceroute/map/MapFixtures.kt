package net.aieat.netswissknife.app.ui.screens.traceroute.map

import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus

/** Places and hops shared by the route map tests. */
internal object MapFixtures {
    fun place(
        city: String,
        lat: Double,
        lon: Double,
    ) = HopGeoLocation(ip = "", country = "", countryCode = "", city = city, lat = lat, lon = lon)

    val london = place("London", 51.5074, -0.1278)
    val paris = place("Paris", 48.8566, 2.3522)
    val frankfurt = place("Frankfurt", 50.1109, 8.6821)
    val newYork = place("New York", 40.7128, -74.0060)
    val brisbane = place("Brisbane", -27.4698, 153.0251)

    /** Hop [number] with one RTT per probe; a null probe got no reply. */
    fun hop(
        number: Int,
        location: HopGeoLocation? = null,
        probes: List<Long?> = listOf(10L),
        destination: Boolean = false,
    ) = HopResult(
        hopNumber = number,
        ip = if (probes.any { it != null }) "198.51.100.$number" else null,
        hostname = null,
        rtTimeMs = probes.filterNotNull().firstOrNull(),
        status = if (probes.any { it != null }) HopStatus.SUCCESS else HopStatus.TIMEOUT,
        geoLocation = location?.copy(ip = "198.51.100.$number"),
        probeRttsMs = probes,
        destinationReached = destination,
    )

    fun timeout(
        number: Int,
        probes: Int = 1,
    ) = hop(number, probes = List(probes) { null })
}
