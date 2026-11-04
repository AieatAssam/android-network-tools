package net.aieat.netswissknife.core.network.netinfo

import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetInfoSummaryFormatterTest {
    private val labels =
        NetInfoSummaryLabels(
            title = "Network Info",
            transports = "Transport",
            metered = "Metered",
            roaming = "Roaming",
            yes = "Yes",
            no = "No",
            wifi = "Wi-Fi",
            ssid = "Network name",
            ssidUnavailable = "Unavailable (location permission needed)",
            signal = "Signal",
            linkSpeed = "Link speed",
            frequency = "Frequency",
            interfaceName = "Interface",
            addresses = "Addresses",
            gateways = "Gateway",
            dns = "DNS servers",
            mtu = "MTU",
            mac = "MAC address",
            macUnavailable = "Not available",
            domains = "Search domains",
            proxy = "Proxy",
            noneValue = "None",
            publicIp = "Public IP",
            publicIpv4 = "Public IPv4",
            publicIpv6 = "Public IPv6",
            hostname = "Reverse DNS",
            location = "Location",
            asn = "ASN",
            notChecked = "Not checked",
            notAvailable = "Not available",
            noActiveNetwork = "No active network",
            scopeName = { it.name.lowercase() },
            transportName = { it.name.lowercase() },
        )

    private val snapshot =
        NetInfoSnapshot(
            hasActiveNetwork = true,
            transports = listOf(NetworkTransport.WIFI),
            metered = false,
            roaming = false,
            wifi =
                WifiLinkDetails(
                    ssid = SsidState.Known("Home"),
                    rssiDbm = -48,
                    linkSpeedMbps = 866,
                    frequencyMhz = 5180,
                ),
            interfaceName = "wlan0",
            addresses =
                listOf(
                    LocalAddress("192.168.1.20", IpFamily.IPV4, 24, IpAddressScope.PRIVATE),
                    LocalAddress("fd00::20", IpFamily.IPV6, 64, IpAddressScope.UNIQUE_LOCAL),
                ),
            gateways = listOf("192.168.1.1"),
            dnsServers = listOf("192.168.1.1"),
            mtuBytes = 1500,
            mac = MacAddressState.Unavailable(MacUnavailableReason.HIDDEN_BY_ANDROID),
            searchDomains = listOf("lan.example"),
            proxy = null,
        )

    @Test
    fun `no active network reports only that fact`() {
        val text = NetInfoSummaryFormatter.format(NetInfoSnapshot.noNetwork(), null, labels)

        assertTrue(text.contains("Network Info"))
        assertTrue(text.contains("No active network"))
        assertFalse(text.contains("Transport"))
    }

    @Test
    fun `wifi name is shown when readable and marked unavailable otherwise`() {
        assertTrue(NetInfoSummaryFormatter.format(snapshot, null, labels).contains("Network name: Home"))

        val locked = SsidState.Unavailable(SsidUnavailableReason.LOCATION_PERMISSION_REQUIRED)
        val hidden = snapshot.copy(wifi = snapshot.wifi?.copy(ssid = locked))
        val text = NetInfoSummaryFormatter.format(hidden, null, labels)
        assertTrue(text.contains("Network name: Unavailable (location permission needed)"))
        assertFalse(text.contains("Home"))
    }

    @Test
    fun `addresses carry scope, prefix and mask`() {
        val text = NetInfoSummaryFormatter.format(snapshot, null, labels)

        assertTrue(text.contains("192.168.1.20/24 (private, mask 255.255.255.0)"))
        assertTrue(text.contains("fd00::20/64 (unique_local)"))
    }

    @Test
    fun `missing hardware address is never printed as a value`() {
        val text = NetInfoSummaryFormatter.format(snapshot, null, labels)

        assertTrue(text.contains("MAC address: Not available"))
        assertFalse(text.contains("02:00:00:00:00:00"))
    }

    @Test
    fun `public IP section lists both families or says it was not checked`() {
        val unchecked = NetInfoSummaryFormatter.format(snapshot, null, labels)
        assertTrue(unchecked.contains("Public IP: Not checked"))

        val report =
            PublicIpReport(
                ipv4 =
                    NetworkResult.Success(
                        PublicAddressInfo(
                            address = "203.0.113.7",
                            family = IpFamily.IPV4,
                            hostname = "host.example",
                            geo = HopGeoLocation("203.0.113.7", "Country", "CC", "City", 0.0, 0.0, "ISP", "AS64500"),
                        ),
                    ),
                ipv6 = NetworkResult.error(ErrorCode.NETWORK_REQUEST_FAILED, "no route"),
            )
        val text = NetInfoSummaryFormatter.format(snapshot, report, labels)
        assertTrue(text.contains("Public IPv4: 203.0.113.7"))
        assertTrue(text.contains("Reverse DNS: host.example"))
        assertTrue(text.contains("ASN: AS64500"))
        assertTrue(text.contains("Public IPv6: Not available"))
    }

    @Test
    fun `proxy and domains show explicit none values`() {
        val text = NetInfoSummaryFormatter.format(snapshot, null, labels)

        assertTrue(text.contains("Proxy: None"))
        assertTrue(text.contains("Search domains: lan.example"))
    }
}
