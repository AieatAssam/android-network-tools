package net.aieat.netswissknife.app.ui.screens.whois

import net.aieat.netswissknife.core.network.whois.WhoisQueryType
import net.aieat.netswissknife.core.network.whois.WhoisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IpAsnHeadingTest {
    private fun ipResult(
        orgName: String?,
        netName: String?,
    ) = WhoisResult(
        query = "8.8.8.8",
        queryType = WhoisQueryType.IPV4,
        hops = emptyList(),
        domainName = null,
        registrar = null,
        registrarUrl = null,
        registeredOn = null,
        expiresOn = null,
        updatedOn = null,
        nameServers = emptyList(),
        statusCodes = emptyList(),
        registrantOrg = null,
        registrantCountry = null,
        dnssec = null,
        netName = netName,
        netRange = null,
        orgName = orgName,
        country = null,
        totalQueryTimeMs = 0,
    )

    @Test
    fun `the organisation is the title and the registry handle is secondary`() {
        assertEquals(IpAsnHeading("Google LLC", "GOGL"), ipAsnHeadingOf(ipResult("Google LLC", "GOGL")))
    }

    @Test
    fun `without an organisation the registry name is the title`() {
        assertEquals(IpAsnHeading("GOGL", null), ipAsnHeadingOf(ipResult(null, "GOGL")))
    }

    @Test
    fun `the same name is not repeated as the secondary line`() {
        assertEquals(IpAsnHeading("GOGL", null), ipAsnHeadingOf(ipResult("GOGL", "GOGL")))
    }

    @Test
    fun `without any name the query is the title`() {
        assertEquals(IpAsnHeading("8.8.8.8", null), ipAsnHeadingOf(ipResult(null, null)))
    }
}
