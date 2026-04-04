package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.NetworkResult
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

class WhoisRepositoryImpl : WhoisRepository {

    private val _hopProgress = MutableSharedFlow<WhoisHop>(extraBufferCapacity = 16)
    override val hopProgress: SharedFlow<WhoisHop> = _hopProgress.asSharedFlow()

    override suspend fun lookup(query: String, timeoutMs: Int): NetworkResult<WhoisResult> {
        if (query.isBlank()) return NetworkResult.Error("Query must not be blank")
        if (timeoutMs < 500) return NetworkResult.Error("Timeout must be between 500 ms and 30 000 ms")
        if (timeoutMs > 30_000) return NetworkResult.Error("Timeout must be between 500 ms and 30 000 ms")

        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val queryType = WhoisQueryTypeDetector.detect(query)
                val result = when (queryType) {
                    WhoisQueryType.DOMAIN -> performDomainLookup(query, timeoutMs, start)
                    WhoisQueryType.IPV4, WhoisQueryType.IPV6, WhoisQueryType.ASN ->
                        performIpAsnLookup(query, queryType, timeoutMs, start)
                }
                result
            } catch (e: Exception) {
                NetworkResult.Error(e.message ?: "WHOIS lookup failed", e)
            }
        }
    }

    private suspend fun performDomainLookup(
        domain: String,
        timeoutMs: Int,
        overallStart: Long
    ): NetworkResult<WhoisResult> {
        val hops = mutableListOf<WhoisHop>()

        // Hop 1 — IANA
        val ianaHop = try {
            queryServer(IANA_SERVER, domain, timeoutMs)
        } catch (e: Exception) {
            return NetworkResult.Error("IANA lookup failed: ${e.message}", e)
        }
        val ianaReferral = WhoisResponseParser.parseReferral(ianaHop.second)
        val hop1 = WhoisHop(
            server = WhoisServer(IANA_SERVER, WhoisServerRole.IANA),
            rawResponse = ianaHop.second,
            queryTimeMs = ianaHop.first,
            referral = ianaReferral
        )
        hops.add(hop1)
        _hopProgress.emit(hop1)

        // Hop 2 — TLD Registry
        val registryHost = ianaReferral
            ?: getTldFallback(domain)
            ?: return buildDomainResult(domain, WhoisQueryType.DOMAIN, hops, overallStart)

        val registryHop = try {
            queryServer(registryHost, domain, timeoutMs)
        } catch (e: Exception) {
            return buildDomainResult(domain, WhoisQueryType.DOMAIN, hops, overallStart)
        }
        val registrarWhoisServer = WhoisResponseParser.parseRegistrarWhoisServer(registryHop.second)
        val hop2 = WhoisHop(
            server = WhoisServer(registryHost, WhoisServerRole.REGISTRY),
            rawResponse = registryHop.second,
            queryTimeMs = registryHop.first,
            referral = registrarWhoisServer
        )
        hops.add(hop2)
        _hopProgress.emit(hop2)

        // Hop 3 — Registrar
        if (registrarWhoisServer != null) {
            val registrarHop = try {
                queryServer(registrarWhoisServer, domain, timeoutMs)
            } catch (e: Exception) {
                return buildDomainResult(domain, WhoisQueryType.DOMAIN, hops, overallStart)
            }
            val hop3 = WhoisHop(
                server = WhoisServer(registrarWhoisServer, WhoisServerRole.REGISTRAR),
                rawResponse = registrarHop.second,
                queryTimeMs = registrarHop.first,
                referral = null
            )
            hops.add(hop3)
            _hopProgress.emit(hop3)
        }

        return buildDomainResult(domain, WhoisQueryType.DOMAIN, hops, overallStart)
    }

    private suspend fun performIpAsnLookup(
        query: String,
        queryType: WhoisQueryType,
        timeoutMs: Int,
        overallStart: Long
    ): NetworkResult<WhoisResult> {
        val hops = mutableListOf<WhoisHop>()

        // Hop 1 — ARIN
        val arinHop = try {
            queryServer(ARIN_SERVER, query, timeoutMs)
        } catch (e: Exception) {
            return NetworkResult.Error("ARIN lookup failed: ${e.message}", e)
        }
        val referral = WhoisResponseParser.parseRirReferral(arinHop.second)
        val hop1 = WhoisHop(
            server = WhoisServer(ARIN_SERVER, WhoisServerRole.RIR),
            rawResponse = arinHop.second,
            queryTimeMs = arinHop.first,
            referral = referral
        )
        hops.add(hop1)
        _hopProgress.emit(hop1)

        // Hop 2 — Referred RIR (if any)
        if (referral != null && referral != ARIN_SERVER) {
            val referralHop = try {
                queryServer(referral, query, timeoutMs)
            } catch (e: Exception) {
                return buildIpResult(query, queryType, hops, overallStart)
            }
            val hop2 = WhoisHop(
                server = WhoisServer(referral, WhoisServerRole.RIR),
                rawResponse = referralHop.second,
                queryTimeMs = referralHop.first,
                referral = null
            )
            hops.add(hop2)
            _hopProgress.emit(hop2)
        }

        return buildIpResult(query, queryType, hops, overallStart)
    }

    private fun queryServer(host: String, query: String, timeoutMs: Int): Pair<Long, String> {
        val start = System.currentTimeMillis()
        val socket = Socket()
        try {
            socket.connect(java.net.InetSocketAddress(host, WHOIS_PORT), timeoutMs)
            socket.soTimeout = timeoutMs
            socket.getOutputStream().write("$query\r\n".toByteArray(Charsets.UTF_8))
            val response = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                .use { it.readText() }
            return Pair(System.currentTimeMillis() - start, response)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun buildDomainResult(
        query: String,
        queryType: WhoisQueryType,
        hops: List<WhoisHop>,
        overallStart: Long
    ): NetworkResult<WhoisResult> {
        val lastResponse = hops.lastOrNull()?.rawResponse ?: ""
        val p = WhoisResponseParser
        return NetworkResult.Success(
            WhoisResult(
                query = query,
                queryType = queryType,
                hops = hops,
                domainName = p.parseDomainName(lastResponse),
                registrar = p.parseRegistrar(lastResponse),
                registrarUrl = p.parseRegistrarUrl(lastResponse),
                registeredOn = p.parseRegisteredOn(lastResponse),
                expiresOn = p.parseExpiresOn(lastResponse),
                updatedOn = p.parseUpdatedOn(lastResponse),
                nameServers = p.parseNameServers(lastResponse),
                statusCodes = p.parseStatusCodes(lastResponse),
                registrantOrg = p.parseRegistrantOrg(lastResponse),
                registrantCountry = p.parseRegistrantCountry(lastResponse),
                dnssec = p.parseDnssec(lastResponse),
                netName = null,
                netRange = null,
                orgName = null,
                country = null,
                totalQueryTimeMs = System.currentTimeMillis() - overallStart
            )
        )
    }

    private fun buildIpResult(
        query: String,
        queryType: WhoisQueryType,
        hops: List<WhoisHop>,
        overallStart: Long
    ): NetworkResult<WhoisResult> {
        val lastResponse = hops.lastOrNull()?.rawResponse ?: ""
        val p = WhoisResponseParser
        return NetworkResult.Success(
            WhoisResult(
                query = query,
                queryType = queryType,
                hops = hops,
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
                netName = p.parseNetName(lastResponse),
                netRange = p.parseNetRange(lastResponse),
                orgName = p.parseOrgName(lastResponse),
                country = p.parseCountry(lastResponse),
                totalQueryTimeMs = System.currentTimeMillis() - overallStart
            )
        )
    }

    private fun getTldFallback(domain: String): String? {
        val parts = domain.lowercase(java.util.Locale.ROOT).split('.')
        if (parts.size < 2) return null
        val tld = parts.last()
        val twoLevel = if (parts.size >= 3) "${parts[parts.size - 2]}.$tld" else null
        // Check two-level TLD first
        if (twoLevel != null) {
            TLD_FALLBACK[twoLevel]?.let { return it }
        }
        return TLD_FALLBACK[tld]
    }

    companion object {
        private const val WHOIS_PORT = 43
        private const val IANA_SERVER = "whois.iana.org"
        private const val ARIN_SERVER = "whois.arin.net"

        private val TLD_FALLBACK = mapOf(
            "com"   to "whois.verisign-grs.com",
            "net"   to "whois.verisign-grs.com",
            "org"   to "whois.pir.org",
            "io"    to "whois.nic.io",
            "co.uk" to "whois.nic.uk",
            "uk"    to "whois.nic.uk",
            "de"    to "whois.denic.de",
            "fr"    to "whois.nic.fr",
            "edu"   to "whois.educause.edu",
            "app"   to "whois.nic.google",
            "dev"   to "whois.nic.google"
        )
    }
}
