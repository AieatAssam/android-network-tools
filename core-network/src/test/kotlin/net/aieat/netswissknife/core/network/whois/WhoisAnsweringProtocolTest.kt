package net.aieat.netswissknife.core.network.whois

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WhoisAnsweringProtocolTest {
    @Test
    fun `an RDAP hop answers as RDAP`() {
        val result = resultWith(rdapHop)
        assertEquals(WhoisProtocol.RDAP, result.answeringProtocol())
    }

    @Test
    fun `a port-43 chain answers as WHOIS`() {
        val result = resultWith(ianaHop, registryHop)
        assertEquals(WhoisProtocol.WHOIS, result.answeringProtocol())
    }

    @Test
    fun `a failed trailing hop is ignored in favour of the last successful one`() {
        val result = resultWith(rirHop, failedRirHop)
        assertEquals(WhoisProtocol.WHOIS, result.answeringProtocol())
    }

    @Test
    fun `no successful hop means no answering protocol`() {
        val result = resultWith(failedRirHop)
        assertNull(result.answeringProtocol())
    }

    private val rdapHop = WhoisHop(WhoisServer("rdap.example", WhoisServerRole.RDAP), "raw", 1L, null)
    private val ianaHop = WhoisHop(WhoisServer("whois.iana.org", WhoisServerRole.IANA), "raw", 1L, null)
    private val registryHop = WhoisHop(WhoisServer("whois.registry.example", WhoisServerRole.REGISTRY), "raw", 1L, null)
    private val rirHop = WhoisHop(WhoisServer("whois.rir.example", WhoisServerRole.RIR), "raw", 1L, null)
    private val failedRirHop =
        WhoisHop(WhoisServer("whois.rir.example", WhoisServerRole.RIR), "", 1L, null, "connect failed")

    private fun resultWith(vararg hops: WhoisHop): WhoisResult = baseResult.copy(hops = hops.toList())

    private val baseResult =
        WhoisResult(
            query = "example.com",
            queryType = WhoisQueryType.DOMAIN,
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
            netName = null,
            netRange = null,
            orgName = null,
            country = null,
            totalQueryTimeMs = 1L,
        )
}
