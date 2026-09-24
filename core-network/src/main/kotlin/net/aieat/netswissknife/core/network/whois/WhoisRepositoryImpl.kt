package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationId
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.operation.ensureCurrentOperationActive
import net.aieat.netswissknife.core.network.NetworkResult
import java.net.Socket

class WhoisRepositoryImpl @JvmOverloads constructor(
    private val resolver: WhoisHostResolver = InetAddressWhoisHostResolver,
    private val socketFactory: WhoisSocketFactory = WhoisSocketFactory { Socket() },
    private val clock: MonotonicClock = SystemMonotonicClock,
) : WhoisRepository {

    private val _hopProgress = MutableSharedFlow<WhoisHop>(extraBufferCapacity = 16)
    override val hopProgress: SharedFlow<WhoisHop> = _hopProgress.asSharedFlow()

    override suspend fun lookup(query: String, timeoutMs: Int): NetworkResult<WhoisResult> {
        if (query.isBlank()) return NetworkResult.Error("Query must not be blank")
        if (timeoutMs !in 500..30_000) return NetworkResult.Error("Timeout must be between 500 ms and 30 000 ms")
        return lookup(query, timeoutMs, WhoisOperation.newSession(timeoutMs, clock))
    }

    override suspend fun lookup(
        query: String,
        timeoutMs: Int,
        operationSession: OperationSession,
    ): NetworkResult<WhoisResult> {
        if (query.isBlank()) return NetworkResult.Error("Query must not be blank")
        if (timeoutMs < 500) return NetworkResult.Error("Timeout must be between 500 ms and 30 000 ms")
        if (timeoutMs > 30_000) return NetworkResult.Error("Timeout must be between 500 ms and 30 000 ms")

        val session = operationSession
        val responseBudget = WhoisResponseBudget(session.budget.maxResponseBytes)
        return try {
            // A chain contains at most three hops. Keep each socket operation bounded
            // by timeoutMs and cap the whole lookup at three such timeouts. OperationRunner
            // shares this monotonic total deadline and preserves parent cancellation.
            OperationRunner.run(session) {
                withContext(Dispatchers.IO) {
                    val start = System.nanoTime()
                    val queryType = WhoisQueryTypeDetector.detect(query)
                    when (queryType) {
                        WhoisQueryType.DOMAIN -> performDomainLookup(
                            query, timeoutMs, start, session.budget, session.budget.operationId, responseBudget
                        )
                        WhoisQueryType.IPV4, WhoisQueryType.IPV6, WhoisQueryType.ASN ->
                            performIpAsnLookup(
                                query, queryType, timeoutMs, start, session.budget, session.budget.operationId,
                                responseBudget,
                            )
                    }
                }
            }
        } catch (e: OperationDeadlineExceededException) {
            NetworkResult.Error("WHOIS lookup exceeded its total deadline", e)
        } catch (e: OperationCancellationException) {
            if (e.reason == CancellationReason.DEADLINE_EXCEEDED) {
                NetworkResult.Error("WHOIS lookup exceeded its total deadline", e)
            } else if (e.reason == CancellationReason.PARENT_CANCELLED && e.cause is CancellationException) {
                // OperationRunner records cancellation from a blocking adapter as a
                // parent reason; retain the adapter's original cancellation contract.
                val originalCancellation = e.cause as CancellationException
                throw originalCancellation
            } else {
                throw e
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "WHOIS lookup failed", e)
        }
    }

    private suspend fun performDomainLookup(
        domain: String,
        timeoutMs: Int,
        overallStart: Long,
        budget: OperationBudget,
        operationId: OperationId,
        responseBudget: WhoisResponseBudget,
    ): NetworkResult<WhoisResult> {
        // Strip subdomains — WHOIS registries only know about the registrable domain (eTLD+1)
        val registrableDomain = extractRegistrableDomain(domain)
        val hops = mutableListOf<WhoisHop>()

        // Hop 1 — IANA
        val ianaHop = try {
            queryServer(IANA_SERVER, registrableDomain, timeoutMs, budget, responseBudget)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OperationDeadlineExceededException) {
            throw e
        } catch (e: Exception) {
            return NetworkResult.Error("IANA lookup failed: ${e.message}", e)
        }
        val ianaReferral = WhoisResponseParser.parseReferral(ianaHop.second)
        val hop1 = WhoisHop(
            server = WhoisServer(IANA_SERVER, WhoisServerRole.IANA),
            rawResponse = ianaHop.second,
            queryTimeMs = ianaHop.first,
            referral = ianaReferral,
            operationId = operationId,
        )
        hops.add(hop1)
        ensureCurrentOperationActive()
        _hopProgress.emit(hop1)

        // Hop 2 — TLD Registry
        val registryHost = ianaReferral
            ?: getTldFallback(registrableDomain)
            ?: return buildDomainResult(domain, WhoisQueryType.DOMAIN, hops, overallStart)

        val registryHop = try {
            queryServer(registryHost, registrableDomain, timeoutMs, budget, responseBudget)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OperationDeadlineExceededException) {
            throw e
        } catch (e: Exception) {
            return buildDomainResult(domain, WhoisQueryType.DOMAIN, hops, overallStart)
        }
        val registrarWhoisServer = WhoisResponseParser.parseRegistrarWhoisServer(registryHop.second)
        val hop2 = WhoisHop(
            server = WhoisServer(registryHost, WhoisServerRole.REGISTRY),
            rawResponse = registryHop.second,
            queryTimeMs = registryHop.first,
            referral = registrarWhoisServer,
            operationId = operationId,
        )
        hops.add(hop2)
        ensureCurrentOperationActive()
        _hopProgress.emit(hop2)

        // Hop 3 — Registrar
        if (registrarWhoisServer != null) {
            val registrarHop = try {
                queryServer(registrarWhoisServer, registrableDomain, timeoutMs, budget, responseBudget)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OperationDeadlineExceededException) {
                throw e
            } catch (e: Exception) {
                val failedHop = WhoisHop(
                    server = WhoisServer(registrarWhoisServer, WhoisServerRole.REGISTRAR),
                    rawResponse = "",
                    queryTimeMs = 0L,
                    referral = null,
                    error = e.message ?: "Connection failed",
                    operationId = operationId,
                )
                hops.add(failedHop)
                ensureCurrentOperationActive()
                _hopProgress.emit(failedHop)
                return buildDomainResult(domain, WhoisQueryType.DOMAIN, hops, overallStart)
            }
            val hop3 = WhoisHop(
                server = WhoisServer(registrarWhoisServer, WhoisServerRole.REGISTRAR),
                rawResponse = registrarHop.second,
                queryTimeMs = registrarHop.first,
                referral = null,
                operationId = operationId,
            )
            hops.add(hop3)
            ensureCurrentOperationActive()
            _hopProgress.emit(hop3)
        }

        return buildDomainResult(domain, WhoisQueryType.DOMAIN, hops, overallStart)
    }

    private suspend fun performIpAsnLookup(
        query: String,
        queryType: WhoisQueryType,
        timeoutMs: Int,
        overallStart: Long,
        budget: OperationBudget,
        operationId: OperationId,
        responseBudget: WhoisResponseBudget,
    ): NetworkResult<WhoisResult> {
        val hops = mutableListOf<WhoisHop>()

        // Hop 1 — ARIN
        val arinHop = try {
            queryServer(ARIN_SERVER, query, timeoutMs, budget, responseBudget)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OperationDeadlineExceededException) {
            throw e
        } catch (e: Exception) {
            return NetworkResult.Error("ARIN lookup failed: ${e.message}", e)
        }
        val referral = WhoisResponseParser.parseRirReferral(arinHop.second)
        val hop1 = WhoisHop(
            server = WhoisServer(ARIN_SERVER, WhoisServerRole.RIR),
            rawResponse = arinHop.second,
            queryTimeMs = arinHop.first,
            referral = referral,
            operationId = operationId,
        )
        hops.add(hop1)
        ensureCurrentOperationActive()
        _hopProgress.emit(hop1)

        // Hop 2 — Referred RIR (if any)
        if (referral != null && referral != ARIN_SERVER) {
            val referralHop = try {
                queryServer(referral, query, timeoutMs, budget, responseBudget)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OperationDeadlineExceededException) {
                throw e
            } catch (e: Exception) {
                return buildIpResult(query, queryType, hops, overallStart)
            }
            val hop2 = WhoisHop(
                server = WhoisServer(referral, WhoisServerRole.RIR),
                rawResponse = referralHop.second,
                queryTimeMs = referralHop.first,
                referral = null,
                operationId = operationId,
            )
            hops.add(hop2)
            ensureCurrentOperationActive()
            _hopProgress.emit(hop2)
        }

        return buildIpResult(query, queryType, hops, overallStart)
    }

    /**
     * True for a loopback, private (RFC 1918), link-local, multicast, or wildcard
     * address. A malicious or compromised WHOIS server can hand back an arbitrary
     * `refer:`/registrar-server host in its response text (see [WhoisResponseParser]);
     * without this check that referral is followed blindly, letting a remote WHOIS
     * server redirect this app's own socket connection to the device's loopback
     * interface or an internal LAN host.
     */
    internal fun isDisallowedReferralAddress(address: java.net.InetAddress): Boolean =
        address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isAnyLocalAddress ||
            address.isMulticastAddress

    private suspend fun queryServer(
        host: String,
        query: String,
        timeoutMs: Int,
        budget: OperationBudget,
        responseBudget: WhoisResponseBudget,
    ): Pair<Long, String> {
        // OperationBudget performs an overflow-safe ceiling conversion. Adding a
        // rounding constant to a saturated Long.MAX_VALUE deadline would overflow
        // and turn a large but valid remaining budget into a negative socket timeout.
        val remainingMs = budget.remainingTimeoutMillis()
        if (remainingMs <= 0L) throw OperationDeadlineExceededException()
        // A positive remaining sub-millisecond deadline rounds up to 1 ms. Reject
        // zero because Socket.connect(timeout = 0) means "no timeout" in the JDK API.
        val remainingTimeoutMs = remainingMs
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        ensureCurrentOperationActive()
        return WhoisBlockingTransport.query(
            host = host,
            query = query,
            port = WHOIS_PORT,
            timeoutMs = minOf(timeoutMs, remainingTimeoutMs),
            resolver = resolver,
            socketFactory = socketFactory,
            isDisallowedAddress = ::isDisallowedReferralAddress,
            responseBudget = responseBudget,
        )
    }

    private fun buildDomainResult(
        query: String,
        queryType: WhoisQueryType,
        hops: List<WhoisHop>,
        overallStart: Long
    ): NetworkResult<WhoisResult> {
        val lastResponse = hops.lastOrNull { it.error == null }?.rawResponse ?: ""
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
                totalQueryTimeMs = (System.nanoTime() - overallStart) / 1_000_000L
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
                totalQueryTimeMs = (System.nanoTime() - overallStart) / 1_000_000L
            )
        )
    }

    /**
     * Strips subdomains to return the registrable domain (eTLD+1).
     * e.g. "sub.example.co.uk" → "example.co.uk", "sub.example.com" → "example.com"
     */
    internal fun extractRegistrableDomain(domain: String): String {
        val parts = domain.lowercase(java.util.Locale.ROOT).split('.')
        if (parts.size <= 2) return domain
        val lastTwo = "${parts[parts.size - 2]}.${parts.last()}"
        return if (COMPOUND_TLDS.contains(lastTwo)) {
            // e.g. co.uk → take 3 labels: example.co.uk
            if (parts.size >= 3) parts.takeLast(3).joinToString(".") else domain
        } else {
            // Standard TLD → take 2 labels: example.com
            parts.takeLast(2).joinToString(".")
        }
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
        private const val MAX_HOPS = 3
        private const val IANA_SERVER = "whois.iana.org"
        private const val ARIN_SERVER = "whois.arin.net"

        private val COMPOUND_TLDS = setOf(
            "co.uk", "org.uk", "me.uk", "net.uk", "ltd.uk", "plc.uk",
            "co.nz", "net.nz", "org.nz", "gov.nz",
            "com.au", "net.au", "org.au", "gov.au",
            "co.jp", "or.jp", "ne.jp",
            "co.in", "net.in", "org.in",
            "com.br", "net.br", "org.br",
            "com.cn", "net.cn", "org.cn",
            "com.mx", "com.ar", "com.sg", "com.hk"
        )

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
