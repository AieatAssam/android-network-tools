package net.aieat.netswissknife.app.ui.screens.whois

import net.aieat.netswissknife.core.network.operation.OperationId
import net.aieat.netswissknife.core.network.whois.WhoisHop
import net.aieat.netswissknife.core.network.whois.WhoisQueryType
import net.aieat.netswissknife.core.network.whois.WhoisResult
import net.aieat.netswissknife.core.network.whois.WhoisServer
import net.aieat.netswissknife.core.network.whois.WhoisServerRole

/** Shared fixtures for the WhoisViewModel tests: one IANA hop and a domain result built from it. */
internal val whoisStubServer = WhoisServer("whois.iana.org", WhoisServerRole.IANA)
internal val whoisStubHop =
    WhoisHop(
        server = whoisStubServer,
        rawResponse = "Domain: example.com",
        queryTimeMs = 10L,
        referral = null,
    )
internal val whoisStubResult =
    WhoisResult(
        query = "example.com",
        queryType = WhoisQueryType.DOMAIN,
        hops = listOf(whoisStubHop),
        domainName = "example.com",
        registrar = "IANA",
        registrarUrl = null,
        registeredOn = null,
        expiresOn = null,
        updatedOn = null,
        nameServers = listOf("a.iana-servers.net"),
        statusCodes = listOf("clientDeleteProhibited"),
        registrantOrg = null,
        registrantCountry = null,
        dnssec = null,
        netName = null,
        netRange = null,
        orgName = null,
        country = null,
        totalQueryTimeMs = 10L,
    )

/** A hop from a registry server that answered before the lookup was stopped. */
internal fun whoisPartialHop(operationId: OperationId): WhoisHop =
    whoisStubHop.copy(
        server = WhoisServer("whois.partial.test", WhoisServerRole.REGISTRY),
        operationId = operationId,
    )
