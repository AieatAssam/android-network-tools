package net.aieat.netswissknife.app.ui.screens.whois

import net.aieat.netswissknife.core.network.whois.WhoisQueryType
import net.aieat.netswissknife.core.network.whois.WhoisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhoisShareTextTest {
    @Test
    fun `golden text lists the parsed fields without a raw section when there is no raw response`() {
        val result =
            WhoisResult(
                query = "example.com",
                queryType = WhoisQueryType.DOMAIN,
                hops = emptyList(),
                domainName = "example.com",
                registrar = "Example Registrar",
                registrarUrl = null,
                registeredOn = null,
                expiresOn = null,
                updatedOn = null,
                nameServers = listOf("ns1.example.com", "ns2.example.com"),
                statusCodes = listOf("clientTransferProhibited"),
                registrantOrg = null,
                registrantCountry = null,
                dnssec = null,
                netName = null,
                netRange = null,
                orgName = null,
                country = null,
                totalQueryTimeMs = 30,
            )
        val expected =
            listOf(
                "WHOIS – example.com",
                "",
                "Registrar: Example Registrar",
                "Name Servers: ns1.example.com, ns2.example.com",
                "Status: clientTransferProhibited",
            ).joinToString(separator = "\n", postfix = "\n")

        assertEquals(expected, buildWhoisShareText(result))
    }
}
