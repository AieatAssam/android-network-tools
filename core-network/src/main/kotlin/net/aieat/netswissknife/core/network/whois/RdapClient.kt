package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A bounded HTTP exchange, kept separate so the client can be tested without real RDAP services. */
data class RdapHttpResponse(
    val statusCode: Int,
    val body: String,
    val finalUrl: String,
    val cacheControl: String? = null,
    val date: String? = null,
    val expires: String? = null,
    val age: String? = null,
)

fun interface RdapTransport {
    suspend fun get(url: HttpUrl): RdapHttpResponse
}

/** Raw RDAP payload and the responding server identity for the mapper. */
sealed interface RdapLookupResult {
    data class Found(val rawJson: String, val finalResponseHost: String) : RdapLookupResult
    data class Unsupported(val statusCode: Int = 404) : RdapLookupResult
}

/** RFC 9224 bootstrap-based RDAP lookup with a bounded in-memory DNS bootstrap cache. */
class RdapClient(
    private val transport: RdapTransport,
    private val bootstrapUrl: HttpUrl = IANA_DNS_BOOTSTRAP_URL,
    private val ipRedirectorUrl: HttpUrl = RDAP_ORG_URL,
    private val asnRedirectorUrl: HttpUrl = RDAP_ORG_URL,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    constructor(
        client: OkHttpClient = OkHttpClient(),
        bootstrapUrl: HttpUrl = IANA_DNS_BOOTSTRAP_URL,
        ipRedirectorUrl: HttpUrl = RDAP_ORG_URL,
        asnRedirectorUrl: HttpUrl = RDAP_ORG_URL,
        nowNanos: () -> Long = System::nanoTime,
        dns: Dns = Dns.SYSTEM,
    ) : this(OkHttpRdapTransport(client, dns), bootstrapUrl, ipRedirectorUrl, asnRedirectorUrl, nowNanos)

    /** Local-address access is available only to same-module MockWebServer tests. */
    internal constructor(
        client: OkHttpClient,
        bootstrapUrl: HttpUrl,
        ipRedirectorUrl: HttpUrl,
        asnRedirectorUrl: HttpUrl,
        nowNanos: () -> Long,
        dns: Dns,
        allowPrivateHostnamesForTests: Set<String>,
    ) : this(
        OkHttpRdapTransport(client, dns, allowPrivateHostnamesForTests),
        bootstrapUrl,
        ipRedirectorUrl,
        asnRedirectorUrl,
        nowNanos,
    )

    private val bootstrapMutex = Mutex()
    @Volatile private var cachedBootstrap: CachedBootstrap? = null
    @Volatile private var bootstrapGeneration = 0L
    @Volatile private var recentBootstrapFailure: BootstrapFailure? = null

    init {
        validateRequestUrl(bootstrapUrl)
        validateRequestUrl(ipRedirectorUrl)
        validateRequestUrl(asnRedirectorUrl)
    }

    suspend fun lookup(query: String, queryType: WhoisQueryType): RdapLookupResult {
        val url = when (queryType) {
            WhoisQueryType.DOMAIN -> domainUrl(query)
            WhoisQueryType.IPV4, WhoisQueryType.IPV6 -> redirectorUrl(ipRedirectorUrl, "ip", query)
            WhoisQueryType.ASN -> redirectorUrl(asnRedirectorUrl, "autnum", normalizeAsn(query))
        }
        validateRequestUrl(url)
        val response = transport.get(url)
        val final = validateFinalUrl(response.finalUrl)
        if (url.isHttps && !final.isHttps) throw IOException("RDAP response attempted an HTTPS downgrade")
        if (response.statusCode == 404) return RdapLookupResult.Unsupported()
        if (response.statusCode !in 200..299) throw IOException("RDAP request failed with HTTP ${response.statusCode}")
        return RdapLookupResult.Found(response.body, final.host)
    }

    private suspend fun domainUrl(query: String): HttpUrl {
        val domain = normalizeDomain(query)
        val bootstrap = cachedOrFetchBootstrap()
        val service = bootstrap.services
            .filter { service -> service.suffixes.any { domainMatches(domain, it) } }
            .maxByOrNull { service -> service.suffixes.filter { domainMatches(domain, it) }.maxOf(String::length) }
            ?: throw IOException("No RDAP service is registered for this domain")
        val base = service.baseUrls.firstOrNull { it.isHttps }
            ?: service.baseUrls.firstOrNull()
            ?: throw IOException("RDAP bootstrap entry has no usable base URL")
        return base.newBuilder()
            .addPathSegment("domain")
            .addPathSegment(domain)
            .build()
    }

    private suspend fun cachedOrFetchBootstrap(): DnsBootstrap {
        val current = cachedBootstrap
        if (current != null && nowNanos() - current.expiresAtNanos < 0L) return current.value
        val observedGeneration = bootstrapGeneration
        return bootstrapMutex.withLock {
            val again = cachedBootstrap
            if (again != null && nowNanos() - again.expiresAtNanos < 0L) return@withLock again.value
            recentBootstrapFailure?.takeIf { it.generation > observedGeneration }?.let { throw it.error }

            // One caller fetches at a time, so a slower stale response cannot replace a newer
            // bootstrap document after a racing request. Waiters that were already in flight
            // also share a failed attempt, while later calls may retry immediately.
            try {
                val response = transport.get(bootstrapUrl)
                val final = validateFinalUrl(response.finalUrl)
                if (bootstrapUrl.isHttps && !final.isHttps) throw IOException("IANA bootstrap response attempted an HTTPS downgrade")
                if (response.statusCode !in 200..299) throw IOException("IANA RDAP bootstrap failed with HTTP ${response.statusCode}")
                val parsed = DnsBootstrap.parse(response.body)
                val freshness = cacheFreshnessMillis(response.cacheControl, response.date, response.expires, response.age)
                cachedBootstrap = CachedBootstrap(parsed, nowNanos() + freshness * 1_000_000L)
                recentBootstrapFailure = null
                parsed
            } catch (error: IOException) {
                val failure = BootstrapFailure(bootstrapGeneration + 1L, error)
                recentBootstrapFailure = failure
                bootstrapGeneration = failure.generation
                throw error
            }
        }
    }

    private fun redirectorUrl(base: HttpUrl, objectType: String, value: String): HttpUrl =
        base.newBuilder().addPathSegment(objectType).addPathSegment(value).build()

    private fun validateFinalUrl(value: String): HttpUrl {
        val url = value.toHttpUrlOrNull() ?: throw IOException("RDAP transport returned an invalid final URL")
        if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.host.isBlank()) {
            throw IOException("RDAP transport returned an unsafe final URL")
        }
        return url
    }

    private fun validateRequestUrl(url: HttpUrl) {
        require((url.isHttps || url.scheme == "http") && url.host.isNotBlank()) { "RDAP URL must use HTTP or HTTPS and have a host" }
        require(url.username.isEmpty() && url.password.isEmpty()) { "RDAP URL cannot contain credentials" }
        require(url.query == null && url.fragment == null) { "RDAP endpoint URL cannot contain a query or fragment" }
    }

    private fun normalizeAsn(query: String): String {
        val trimmed = query.trim()
        val normalized = if (trimmed.startsWith("AS", ignoreCase = true)) trimmed.drop(2) else trimmed
        val number = normalized.toLongOrNull()
        if (number == null || number !in 1..MAX_ASN) throw IllegalArgumentException("Invalid ASN")
        return number.toString()
    }

    private data class CachedBootstrap(val value: DnsBootstrap, val expiresAtNanos: Long)
    private data class BootstrapFailure(val generation: Long, val error: IOException)

    companion object {
        private const val MAX_ASN = 4_294_967_295L
        private const val MAX_BOOTSTRAP_AGE_MILLIS = 24 * 60 * 60 * 1000L
        private val IANA_DNS_BOOTSTRAP_URL = "https://data.iana.org/rdap/dns.json".toHttpUrlOrNull()!!
        private val RDAP_ORG_URL = "https://rdap.org/".toHttpUrlOrNull()!!

        private fun cacheFreshnessMillis(
            cacheControl: String?,
            date: String?,
            expires: String?,
            age: String?,
        ): Long {
            val directives = cacheControl.orEmpty().split(',').map(String::trim)
            val names = directives.map { it.substringBefore('=').trim() }
            if (names.any { it.equals("no-store", ignoreCase = true) || it.equals("no-cache", ignoreCase = true) }) return 0L

            val maxAgeDirective = directives.firstOrNull { it.substringBefore('=').trim().equals("max-age", ignoreCase = true) }
            val freshnessMillis = if (maxAgeDirective != null) {
                val seconds = maxAgeDirective.substringAfter('=', "").trim().trim('"').toLongOrNull()
                    ?.takeIf { it >= 0L } ?: return 0L
                secondsToMillis(seconds)
            } else if (expires != null) {
                val expiresAt = parseHttpDate(expires) ?: return 0L
                val referenceTime = date?.let(::parseHttpDate) ?: System.currentTimeMillis()
                (expiresAt - referenceTime).coerceAtLeast(0L)
            } else {
                MAX_BOOTSTRAP_AGE_MILLIS
            }
            val ageMillis = age?.toLongOrNull()?.takeIf { it >= 0L }?.let(::secondsToMillis)
                ?: if (age == null) 0L else return 0L
            return (freshnessMillis - ageMillis).coerceAtLeast(0L).coerceAtMost(MAX_BOOTSTRAP_AGE_MILLIS)
        }

        private fun secondsToMillis(seconds: Long): Long =
            if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L

        private fun parseHttpDate(value: String): Long? = runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull()
    }
}

/** Parser for the IANA DNS RDAP bootstrap file (RFC 9224). */
internal data class DnsBootstrap(val services: List<Service>) {
    internal data class Service(val suffixes: List<String>, val baseUrls: List<HttpUrl>)

    companion object {
        fun parse(json: String): DnsBootstrap {
            val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(json) as? kotlinx.serialization.json.JsonObject }
                .getOrNull() ?: throw IOException("Malformed IANA RDAP bootstrap JSON")
            val services = root["services"] as? kotlinx.serialization.json.JsonArray
                ?: throw IOException("IANA RDAP bootstrap is missing services")
            val parsed = services.mapNotNull { entry ->
                val tuple = entry as? kotlinx.serialization.json.JsonArray ?: return@mapNotNull null
                val suffixes = (tuple.getOrNull(0) as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { value ->
                        val primitive = value as? kotlinx.serialization.json.JsonPrimitive
                        primitive?.takeIf { it.isString }?.contentOrNull?.let(::normalizeSuffix)
                    }
                    .orEmpty()
                val urls = (tuple.getOrNull(1) as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { value ->
                        val primitive = value as? kotlinx.serialization.json.JsonPrimitive
                        primitive?.takeIf { it.isString }?.contentOrNull
                    }
                    ?.mapNotNull(::validatedBaseUrl)
                    .orEmpty()
                if (suffixes.isEmpty() || urls.isEmpty()) null else Service(suffixes, urls)
            }
            if (parsed.isEmpty()) throw IOException("IANA RDAP bootstrap contains no usable services")
            return DnsBootstrap(parsed)
        }

        private fun normalizeSuffix(value: String): String? {
            if (value.isEmpty()) return "" // RFC 9224 root service: applicable to any DNS name.
            return runCatching {
                val normalized = IDN.toASCII(value.trim().trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
                require(normalized.isNotEmpty() && normalized.split('.').all(String::isNotEmpty))
                normalized
            }.getOrNull()
        }

        private fun validatedBaseUrl(value: String): HttpUrl? {
            val url = value.toHttpUrlOrNull() ?: return null
            if (url.host.isBlank() || url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null) return null
            // RFC bootstrap entries should be secure where possible; an HTTP-only entry remains
            // usable for registries that have not published HTTPS, while redirects may never
            // downgrade an HTTPS request (enforced by the transport).
            return url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/").build()
        }
    }
}

private fun normalizeDomain(value: String): String {
    val trimmed = value.trim().trimEnd('.')
    require(trimmed.isNotEmpty() && !trimmed.any(Char::isWhitespace)) { "Invalid domain" }
    val ascii = try { IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT) }
    catch (error: IllegalArgumentException) { throw IllegalArgumentException("Invalid domain", error) }
    require(ascii.length <= 253 && ascii.split('.').all { it.isNotEmpty() && it.length <= 63 }) { "Invalid domain" }
    return ascii
}

private fun domainMatches(domain: String, suffix: String): Boolean = suffix.isEmpty() || domain == suffix || domain.endsWith(".$suffix")

/** Uses callbacks so coroutine cancellation cancels the exact OkHttp call. */
private class OkHttpRdapTransport(
    client: OkHttpClient,
    dns: Dns = Dns.SYSTEM,
    allowPrivateHostnamesForTests: Set<String> = emptySet(),
) : RdapTransport {
    private val client = client.newBuilder()
        .dns(PublicInternetDns(dns, allowPrivateHostnamesForTests))
        .proxy(Proxy.NO_PROXY)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    override suspend fun get(url: HttpUrl): RdapHttpResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("Accept", "application/rdap+json, application/json").get().build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        val body = it.body.source().use { source ->
                            val buffer = Buffer()
                            var total = 0L
                            while (total <= MAX_RESPONSE_BYTES) {
                                val count = source.read(buffer, minOf(8_192L, MAX_RESPONSE_BYTES + 1 - total))
                                if (count == -1L) break
                                total += count
                            }
                            if (total > MAX_RESPONSE_BYTES) throw IOException("RDAP response exceeds size limit")
                            buffer.readUtf8()
                        }
                        val finalUrl = it.request.url
                        if (continuation.isActive) continuation.resume(
                            RdapHttpResponse(
                                it.code,
                                body,
                                finalUrl.toString(),
                                it.header("Cache-Control"),
                                it.header("Date"),
                                it.header("Expires"),
                                it.header("Age"),
                            ),
                        )
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    companion object { private const val MAX_RESPONSE_BYTES = 1024 * 1024L }
}

/** Resolves every initial and redirected host, then rejects any local/private address. */
internal class PublicInternetDns(
    private val delegate: Dns = Dns.SYSTEM,
    allowedPrivateHostnamesForTests: Set<String> = emptySet(),
) : Dns {
    private val allowedPrivateHostnames = allowedPrivateHostnamesForTests
        .map { it.trimEnd('.').lowercase(Locale.ROOT) }
        .toSet()

    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        val normalizedHost = hostname.trimEnd('.').lowercase(Locale.ROOT)
        if (normalizedHost !in allowedPrivateHostnames && addresses.any(::isLocalOrPrivateAddress)) {
            throw UnknownHostException("RDAP host resolves to a local or private address")
        }
        return addresses
    }

    private fun isLocalOrPrivateAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return true

        val bytes = address.address.map { it.toInt() and 0xff }
        if (address is Inet4Address) {
            val (first, second, third) = bytes
            return first == 0 || (first == 100 && second in 64..127) ||
                (first == 192 && second == 0 && (third == 0 || third == 2)) ||
                (first == 192 && second == 88 && third == 99) || (first == 198 && second in 18..19) ||
                (first == 198 && second == 51 && third == 100) ||
                (first == 203 && second == 0 && third == 113) || first >= 240
        }
        if (address is Inet6Address) {
            if ((bytes[0] and 0xfe) == 0xfc) return true // Unique-local fc00::/7.
            if (bytes[0] == 0x20 && bytes[1] == 0x02) return true // 6to4 embeds an IPv4 endpoint.
            val globalUnicast = (bytes[0] and 0xe0) == 0x20
            if (!globalUnicast) return true
            if (bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8) return true
        }
        return false
    }
}
