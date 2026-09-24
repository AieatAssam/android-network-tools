package net.aieat.netswissknife.app.ui.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class ToolIntentCodecTest {
    @Test
    fun `hostname round trips with source and URL safe encoding`() {
        val intent = ToolIntent(
            ToolDestination.HostTarget(HostTool.HTTP, host("example.com")),
            ToolSource.MDNS,
        )

        val encoded = ToolIntentCodec.encode(intent)

        assertTrue(encoded.startsWith("ti1."))
        assertTrue(encoded.matches(Regex("ti1\\.[A-Za-z0-9_-]+")))
        assertEquals(intent, ToolIntentCodec.decode(encoded))
    }

    @Test
    fun `IPv4 and IPv6 including interface zone round trip`() {
        val intents = listOf(
            ToolIntent(ToolDestination.HostTarget(HostTool.PING, host("192.0.2.8"))),
            ToolIntent(ToolDestination.HostTarget(HostTool.PING, host("fe80::1%wlan0"))),
        )

        intents.forEach { assertEquals(it, ToolIntentCodec.decode(ToolIntentCodec.encode(it))) }
    }

    @Test
    fun `Unicode IDN round trips and exposes ASCII canonical form`() {
        val intent = ToolIntent(ToolDestination.HostTarget(HostTool.TLS, host("bücher.example"), port(443)))

        val decoded = ToolIntentCodec.decode(ToolIntentCodec.encode(intent))

        assertEquals(intent, decoded)
        assertEquals("xn--bcher-kva.example", (decoded?.destination as ToolDestination.HostTarget).host.canonical)
    }

    @Test
    fun `port MAC and subnet values round trip`() {
        val intents = listOf(
            ToolIntent(ToolDestination.HostTarget(HostTool.PORTS, host("router.local"), port(65535))),
            ToolIntent(ToolDestination.WakeOnLan(mac("02-23-45-67-89-ab")), ToolSource.LAN),
            ToolIntent(ToolDestination.Subnet(subnet("192.168.1.0/24"))),
            ToolIntent(ToolDestination.Subnet(subnet("2001:db8::/48"))),
        )

        intents.forEach { assertEquals(it, ToolIntentCodec.decode(ToolIntentCodec.encode(it))) }
        assertEquals("02:23:45:67:89:AB", mac("02-23-45-67-89-ab").value)
    }

    @Test
    fun `rejects unsupported versions malformed encodings and invalid typed values`() {
        assertNull(ToolIntentCodec.decode("ti2.YQ"))
        assertNull(ToolIntentCodec.decode("ti1."))
        assertNull(ToolIntentCodec.decode("ti1.abc="))
        assertNull(ToolIntentCodec.decode("ti1.@@@"))
        assertNull(ToolIntentCodec.decode(payload("host|ports|bad host||lan")))
        assertNull(ToolIntentCodec.decode(payload("host|ports|example.com|0|lan")))
        assertNull(ToolIntentCodec.decode(payload("host|ports|example.com|65536|lan")))
        assertNull(ToolIntentCodec.decode(payload("host|ports|example.com|80|unknown")))
        assertNull(ToolIntentCodec.decode(payload("wol|01:23:45:67:89:GG|lan")))
        assertNull(ToolIntentCodec.decode(payload("subnet|192.168.1.0/33|lan")))
        assertNull(ToolIntentCodec.decode(payload("subnet|router.local/24|lan")))
        assertNull(ToolHost.parse(" example.com"))
        assertNull(ToolHost.parse("example.com/with-path"))
        assertNull(ToolPort.parse(0))
        assertNull(ToolMacAddress.parse("01:23:45:67:89"))
        assertNull(ToolSubnet.parse("fe80::1%wlan0/64"))
        assertNull(ToolHost.parse("fe80::1%wlan0%eth0"))
        assertNull(ToolHost.parse("fe80::1%wlan0/../../host"))
        assertNull(ToolHost.parse("fe80::1%wlan/0"))
        assertNull(ToolHost.parse("fe80::1%wlan0%2Fother"))
        assertNull(ToolMacAddress.parse("01:23-45:67-89:AB"))
        assertNull(ToolMacAddress.parse("00:00:00:00:00:00"))
        assertNull(ToolMacAddress.parse("FF:FF:FF:FF:FF:FF"))
        assertNull(ToolMacAddress.parse("01:23:45:67:89:AB"))
        assertThrows(IllegalArgumentException::class.java) {
            ToolMacAddress("01-23-45-67-89-AB")
        }
    }

    @Test
    fun `legacy Ports query route decodes percent escaped IPv6 and Unicode host`() {
        val ipv6Route = "ports?host=%5Bfe80%3A%3A1%25wlan0%5D"
        val idnRoute = "ports?host=b%C3%BCcher.example"

        assertEquals(
            ToolDestination.HostTarget(HostTool.PORTS, host("[fe80::1%wlan0]")),
            ToolIntentCodec.decodeLegacyPortsRoute(ipv6Route)?.destination,
        )
        assertEquals(
            ToolDestination.HostTarget(HostTool.PORTS, host("bücher.example")),
            ToolIntentCodec.decodeLegacyPortsRoute(idnRoute)?.destination,
        )
    }

    @Test
    fun `legacy route rejects malformed ambiguous and unsupported routes`() {
        assertNull(ToolIntentCodec.decodeLegacyPortsRoute("ping?host=example.com"))
        assertNull(ToolIntentCodec.decodeLegacyPortsRoute("ports"))
        assertNull(ToolIntentCodec.decodeLegacyPortsRoute("ports?host=example.com&host=other.example"))
        assertNull(ToolIntentCodec.decodeLegacyPortsRoute("ports?host=bad%2"))
        assertNull(ToolIntentCodec.decodeLegacyPortsRoute("ports?host=bad%20host"))
        assertNull(ToolIntentCodec.decodeLegacyPortsRoute("ports?host=example.com&source=lan"))
        assertNull(ToolIntentCodec.decodeLegacyPortsRoute("ports?%68ost=example.com"))
        assertNull(ToolIntentCodec.decodeLegacyPortsRoute("ports?host=example.com&%68ost=other.example"))
        assertNull(ToolIntentCodec.decodeLegacyPortsRoute("ports?host=example.com&"))
    }

    @Test
    fun `legacy route treats plus as literal as Android Uri decoder does`() {
        assertNull(ToolIntentCodec.decodeLegacyPortsRoute("ports?host=a+b.example"))
        assertFalse(ToolIntentCodec.encode(ToolIntent(ToolDestination.HostTarget(HostTool.PORTS, host("example.com"))))
            .contains('+'))
    }

    private fun host(value: String) = requireNotNull(ToolHost.parse(value))
    private fun port(value: Int) = requireNotNull(ToolPort.parse(value))
    private fun mac(value: String) = requireNotNull(ToolMacAddress.parse(value))
    private fun subnet(value: String) = requireNotNull(ToolSubnet.parse(value))

    private fun payload(value: String): String =
        "ti1." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}
