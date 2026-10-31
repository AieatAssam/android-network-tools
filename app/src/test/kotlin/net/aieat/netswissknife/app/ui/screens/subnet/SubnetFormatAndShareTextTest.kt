package net.aieat.netswissknife.app.ui.screens.subnet

import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.subnet.SubnetCalculatorRepositoryImpl
import net.aieat.netswissknife.core.network.subnet.SubnetInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class SubnetFormatAndShareTextTest {
    private val labels =
        SubnetShareLabels(
            cidr = "CIDR",
            networkAddress = "Network",
            broadcast = "Broadcast",
            firstHost = "First",
            lastHost = "Last",
            totalAddresses = "Total",
            usableHosts = "Usable",
            subnetMask = "Mask",
            wildcardMask = "Wildcard",
            scope = "Scope",
        )

    @Test
    fun `counts use exact digits with thousands separators`() {
        assertEquals("256", formatSubnetCount(256L, Locale.US))
        assertEquals("16,777,216", formatSubnetCount(16_777_216L, Locale.US))
        assertEquals("4,294,967,296", formatSubnetCount(4_294_967_296L, Locale.US))
    }

    @Test
    fun `plural quantity is clamped to Int range for the default route`() {
        assertEquals(1, subnetPluralQuantity(1L))
        assertEquals(Int.MAX_VALUE, subnetPluralQuantity(4_294_967_294L))
        assertEquals(0, subnetPluralQuantity(0L))
    }

    @Test
    fun `share text lists the calculated fields with plain numbers`() {
        val info = calculated("10.0.0.0/8")

        val text = buildSubnetShareText(info, labels, scopeLabel = "Private (RFC 1918)")

        val expected =
            listOf(
                "CIDR: 10.0.0.0/8",
                "Network: 10.0.0.0",
                "Broadcast: 10.255.255.255",
                "First: 10.0.0.1",
                "Last: 10.255.255.254",
                "Total: 16777216",
                "Usable: 16777214",
                "Mask: 255.0.0.0",
                "Wildcard: 0.255.255.255",
                "Scope: Private (RFC 1918)",
            ).joinToString("\n")
        assertEquals(expected, text)
    }

    @Test
    fun `share text for a slash 31 reports both addresses usable`() {
        val info = calculated("192.168.1.0/31")

        val text = buildSubnetShareText(info, labels, scopeLabel = "Private (RFC 1918)")

        assertEquals("Usable: 2", text.lines().first { it.startsWith("Usable:") })
    }

    private fun calculated(input: String): SubnetInfo {
        val result = SubnetCalculatorRepositoryImpl().calculate(input)
        return (result as NetworkResult.Success).data
    }
}
