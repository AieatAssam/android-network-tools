package net.aieat.netswissknife.core.network.traceroute

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationContext
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

fun interface GeoIpConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

/** Provider endpoint and parser seam, kept internal so provider behavior is fixture-testable. */
internal interface GeoIpProvider {
    val id: String

    fun url(ip: String): URL

    fun parse(
        ip: String,
        body: String,
    ): GeoIpParseOutcome
}

internal sealed interface GeoIpParseOutcome {
    data class Found(
        val location: HopGeoLocation,
    ) : GeoIpParseOutcome

    data object NoData : GeoIpParseOutcome

    data object Invalid : GeoIpParseOutcome
}

internal class IpInfoGeoIpProvider(
    baseUrl: String = "https://ipinfo.io",
) : GeoIpProvider {
    private val endpoint = baseUrl.trimEnd('/')
    override val id: String = "ipinfo"

    override fun url(ip: String): URL = URI("$endpoint/$ip/json").toURL()

    override fun parse(
        ip: String,
        body: String,
    ): GeoIpParseOutcome {
        val json = parseJsonObject(body) ?: return GeoIpParseOutcome.Invalid
        if (!responseIpMatches(ip, json.string("ip"))) return GeoIpParseOutcome.Invalid
        if (json.boolean("bogon") == true) return GeoIpParseOutcome.NoData
        val countryCode = json.string("country") ?: return GeoIpParseOutcome.Invalid
        val (lat, lon) =
            json.string("loc")?.let(::parseCoordinates)
                ?: return GeoIpParseOutcome.Invalid
        val org = json.string("org")
        val asnToken = org?.let { Regex("^(AS\\d+)").find(it)?.groupValues?.get(1) }
        val asn = asnToken?.let(::normalizeAsn)
        if (asnToken != null && asn == null) return GeoIpParseOutcome.Invalid
        return GeoIpParseOutcome.Found(
            HopGeoLocation(
                ip = ip,
                country = countryName(countryCode),
                countryCode = countryCode,
                city = json.string("city").orEmpty(),
                lat = lat,
                lon = lon,
                isp = org?.removePrefix(asnToken ?: "")?.trim()?.ifBlank { null },
                asn = asn,
            ),
        )
    }
}

internal class IpWhoGeoIpProvider(
    baseUrl: String = "https://ipwho.is",
) : GeoIpProvider {
    private val endpoint = baseUrl.trimEnd('/')
    override val id: String = "ipwho"

    override fun url(ip: String): URL = URI("$endpoint/$ip").toURL()

    override fun parse(
        ip: String,
        body: String,
    ): GeoIpParseOutcome {
        val json = parseJsonObject(body) ?: return GeoIpParseOutcome.Invalid
        val success = json.boolean("success") ?: return GeoIpParseOutcome.Invalid
        if (!success) return GeoIpParseOutcome.Invalid
        if (!responseIpMatches(ip, json.string("ip"))) return GeoIpParseOutcome.Invalid
        val countryCode = json.string("country_code") ?: return GeoIpParseOutcome.Invalid
        val coordinates =
            parseCoordinates(json.number("latitude"), json.number("longitude"))
                ?: return GeoIpParseOutcome.Invalid
        val connection = json.objectValue("connection")
        val org = connection?.string("org")
        val isp = connection?.string("isp")
        val asnField = connection?.primitive("asn")
        if (asnField != null && asnField !== JsonNull && normalizeAsn(asnField.content) == null) {
            return GeoIpParseOutcome.Invalid
        }
        val asn = asnField?.takeUnless { it === JsonNull }?.let { normalizeAsn(it.content) }
        return GeoIpParseOutcome.Found(
            HopGeoLocation(
                ip = ip,
                country = json.string("country") ?: countryName(countryCode),
                countryCode = countryCode,
                city = json.string("city").orEmpty(),
                lat = coordinates.first,
                lon = coordinates.second,
                isp = org ?: isp,
                asn = asn,
            ),
        )
    }
}

internal class IpApiGeoIpProvider(
    baseUrl: String = "https://ipapi.co",
) : GeoIpProvider {
    private val endpoint = baseUrl.trimEnd('/')
    override val id: String = "ipapi"

    override fun url(ip: String): URL = URI("$endpoint/$ip/json/").toURL()

    override fun parse(
        ip: String,
        body: String,
    ): GeoIpParseOutcome {
        val json = parseJsonObject(body) ?: return GeoIpParseOutcome.Invalid
        if ("error" in json) {
            val error = json.boolean("error") ?: return GeoIpParseOutcome.Invalid
            if (error) return GeoIpParseOutcome.Invalid
        }
        if (!responseIpMatches(ip, json.string("ip"))) return GeoIpParseOutcome.Invalid
        val countryCode = json.string("country_code") ?: json.string("country") ?: return GeoIpParseOutcome.Invalid
        val coordinates =
            parseCoordinates(json.number("latitude"), json.number("longitude"))
                ?: return GeoIpParseOutcome.Invalid
        val org = json.string("org")
        val asnField = json.primitive("asn")
        if (asnField != null && asnField !== JsonNull && normalizeAsn(asnField.content) == null) {
            return GeoIpParseOutcome.Invalid
        }
        val orgAsn = org?.let { Regex("^(AS\\d+)").find(it)?.groupValues?.get(1) }
        val normalizedOrgAsn = orgAsn?.let(::normalizeAsn)
        if (orgAsn != null && normalizedOrgAsn == null) return GeoIpParseOutcome.Invalid
        val asn =
            asnField?.takeUnless { it === JsonNull }?.let { normalizeAsn(it.content) }
                ?: normalizedOrgAsn
        return GeoIpParseOutcome.Found(
            HopGeoLocation(
                ip = ip,
                country = json.string("country_name") ?: countryName(countryCode),
                countryCode = countryCode,
                city = json.string("city").orEmpty(),
                lat = coordinates.first,
                lon = coordinates.second,
                isp = org,
                asn = asn,
            ),
        )
    }
}

private val providerJson = Json { ignoreUnknownKeys = true }

private fun parseJsonObject(body: String): JsonObject? {
    val parsed = runCatching { providerJson.parseToJsonElement(body).jsonObject }
    return parsed.getOrNull()
}

private fun JsonObject.primitive(name: String): JsonPrimitive? = this[name] as? JsonPrimitive

private fun JsonObject.string(name: String): String? = primitive(name)?.takeIf { it.isString }?.content

private fun JsonObject.boolean(name: String): Boolean? {
    val value = primitive(name)?.takeUnless { it.isString }
    return value?.booleanOrNull
}

private fun JsonObject.number(name: String): Double? = primitive(name)?.doubleOrNull

private fun JsonObject.objectValue(name: String): JsonObject? =
    (this[name] as? JsonElement)?.let {
        runCatching { it.jsonObject }.getOrNull()
    }

private fun responseIpMatches(
    requestedIp: String,
    responseIp: String?,
): Boolean {
    if (responseIp == null || !ReservedRanges.isPublicGlobalLiteral(requestedIp) ||
        !ReservedRanges.isPublicGlobalLiteral(responseIp)
    ) {
        return false
    }
    // ReservedRanges proves these are numeric literals before InetAddress parses them, so this
    // canonicalization cannot fall through to hostname DNS resolution.
    val requested = runCatching { InetAddress.getByName(requestedIp).hostAddress.substringBefore('%') }.getOrNull()
    val returned = runCatching { InetAddress.getByName(responseIp).hostAddress.substringBefore('%') }.getOrNull()
    return requested != null && requested.equals(returned, ignoreCase = true)
}

private fun parseCoordinates(value: String): Pair<Double, Double>? {
    val parts = value.split(',')
    if (parts.size != 2) return null
    val latitude = parts[0].trim().toDoubleOrNull() ?: return null
    val longitude = parts[1].trim().toDoubleOrNull() ?: return null
    return validCoordinates(latitude, longitude)
}

private fun parseCoordinates(
    latitude: Double?,
    longitude: Double?,
): Pair<Double, Double>? {
    if (latitude == null || longitude == null) return null
    return validCoordinates(latitude, longitude)
}

private fun validCoordinates(
    latitude: Double,
    longitude: Double,
): Pair<Double, Double>? {
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return latitude to longitude
}

private fun normalizeAsn(raw: String): String? {
    val digits = if (raw.startsWith("AS", ignoreCase = true)) raw.substring(2) else raw
    if (digits.isEmpty() || digits.any { !it.isDigit() }) return null
    val value = digits.toLongOrNull()?.takeIf { it in 1L..4_294_967_295L } ?: return null
    return "AS$value"
}

private val COUNTRY_NAMES =
    mapOf(
        "US" to "United States",
        "GB" to "United Kingdom",
        "DE" to "Germany",
        "FR" to "France",
        "JP" to "Japan",
        "CN" to "China",
        "CA" to "Canada",
        "AU" to "Australia",
        "BR" to "Brazil",
        "IN" to "India",
        "RU" to "Russia",
        "NL" to "Netherlands",
        "SE" to "Sweden",
        "SG" to "Singapore",
        "HK" to "Hong Kong",
        "KR" to "South Korea",
        "IT" to "Italy",
        "ES" to "Spain",
        "CH" to "Switzerland",
        "NO" to "Norway",
        "DK" to "Denmark",
        "FI" to "Finland",
        "PL" to "Poland",
        "ZA" to "South Africa",
        "MX" to "Mexico",
        "AR" to "Argentina",
        "TR" to "Turkey",
        "ID" to "Indonesia",
        "TH" to "Thailand",
        "PH" to "Philippines",
        "MY" to "Malaysia",
        "UA" to "Ukraine",
        "IE" to "Ireland",
        "NZ" to "New Zealand",
        "PT" to "Portugal",
        "AT" to "Austria",
        "BE" to "Belgium",
        "CZ" to "Czech Republic",
        "HU" to "Hungary",
        "RO" to "Romania",
        "GR" to "Greece",
        "TW" to "Taiwan",
        "VN" to "Vietnam",
        "EG" to "Egypt",
        "SA" to "Saudi Arabia",
        "AE" to "UAE",
        "IL" to "Israel",
        "PK" to "Pakistan",
        "NG" to "Nigeria",
    )

private fun countryName(code: String): String = COUNTRY_NAMES[code.uppercase()] ?: code

private data class ProviderBreakerState(
    var consecutiveFailures: Int = 0,
    var openUntilNanos: Long? = null,
    var generation: Long = 0,
    var nextAttemptToken: Long = 0,
    var halfOpenAttemptToken: Long? = null,
)

private data class ProviderBreakerAttempt(
    val generation: Long,
    val halfOpenToken: Long?,
)

/**
 * [GeoIpRepository] with an HTTPS provider chain through ipinfo.io, ipwho.is, and ipapi.co.
 *
 * Response shape:
 *   { "ip":"8.8.8.8", "city":"Mountain View", "region":"California",
 *     "country":"US", "loc":"37.3861,-122.0839", "org":"AS15169 Google LLC" }
 *
 * Private / reserved IP ranges are skipped and return null immediately.
 * Results are cached in-memory to avoid repeat calls for the same IP.
 */
class GeoIpRepositoryImpl internal constructor(
    private val providers: List<GeoIpProvider>,
    private val connectionFactory: GeoIpConnectionFactory,
    internal var clock: MonotonicClock,
) : GeoIpRepository {
    constructor(baseUrl: String = DEFAULT_BASE_URL) : this(
        providers = if (baseUrl.trimEnd('/') == DEFAULT_BASE_URL) defaultProviders() else listOf(IpInfoGeoIpProvider(baseUrl)),
        connectionFactory = GeoIpConnectionFactory { it.openConnection() as HttpURLConnection },
        clock = SystemMonotonicClock,
    )

    internal constructor(
        baseUrl: String,
        connectionFactory: GeoIpConnectionFactory,
    ) : this(listOf(IpInfoGeoIpProvider(baseUrl)), connectionFactory, SystemMonotonicClock)

    init {
        require(providers.isNotEmpty()) { "At least one GeoIP provider is required" }
        require(providers.map { it.id }.distinct().size == providers.size) { "GeoIP provider IDs must be unique" }
    }

    private val cache = ConcurrentHashMap<String, HopGeoLocation?>()
    private val breakerLock = Any()
    private val providerBreakers = providers.associate { it.id to ProviderBreakerState() }

    override suspend fun lookup(ip: String): HopGeoLocation? {
        currentCoroutineContext().ensureActive()
        if (!ReservedRanges.isPublicGlobalLiteral(ip)) return null
        cache[ip]?.let { return it }
        val session = newOperationSession(clock)
        val result = executeLookup(ip, session, mapDeadlineToNull = true)
        if (result != null) cache[ip] = result
        return result
    }

    override suspend fun lookup(
        ip: String,
        operationSession: OperationSession,
    ): HopGeoLocation? {
        currentCoroutineContext().ensureActive()
        operationSession.cancellationReason?.let { reason ->
            if (reason == CancellationReason.DEADLINE_EXCEEDED) throw OperationDeadlineExceededException()
            throw OperationCancellationException(reason)
        }
        operationSession.budget.throwIfExpired()
        if (!ReservedRanges.isPublicGlobalLiteral(ip)) return null
        cache[ip]?.let { return it }
        val result = executeLookup(ip, operationSession, mapDeadlineToNull = false)
        if (result != null) cache[ip] = result
        return result
    }

    // ── Network ───────────────────────────────────────────────────────────────

    private suspend fun executeLookup(
        ip: String,
        session: OperationSession,
        mapDeadlineToNull: Boolean,
    ): HopGeoLocation? =
        withContext(Dispatchers.IO) {
            try {
                OperationRunner.runOrJoin(session) { fetchGeoIp(ip) }
            } catch (cancelled: OperationCancellationException) {
                if (mapDeadlineToNull && cancelled.reason == CancellationReason.DEADLINE_EXCEEDED) {
                    null
                } else {
                    throw cancelled
                }
            } catch (deadline: OperationDeadlineExceededException) {
                if (mapDeadlineToNull) null else throw deadline
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

    @Suppress("CyclomaticComplexMethod", "ThrowsCount")
    private suspend fun OperationContext.fetchGeoIp(ip: String): HopGeoLocation? {
        for (provider in providers) {
            ensureOperationActive()
            val attempt = acquireProviderAttempt(provider.id) ?: continue
            try {
                val outcome = fetchFromProvider(provider, ip)
                ensureOperationActive()
                when (outcome) {
                    is GeoIpParseOutcome.Found -> {
                        recordProviderSuccess(provider.id, attempt)
                        return outcome.location
                    }

                    GeoIpParseOutcome.NoData -> {
                        recordProviderNoData(provider.id, attempt)
                        continue
                    }

                    GeoIpParseOutcome.Invalid -> {
                        recordProviderFailure(provider.id, attempt)
                    }
                }
            } catch (cancelled: CancellationException) {
                releaseProviderAttempt(provider.id, attempt)
                throw cancelled
            } catch (cancelled: OperationCancellationException) {
                releaseProviderAttempt(provider.id, attempt)
                throw cancelled
            } catch (deadline: OperationDeadlineExceededException) {
                releaseProviderAttempt(provider.id, attempt)
                throw deadline
            } catch (_: Exception) {
                // A disconnect caused by Stop/deadline can surface as ordinary I/O failure.
                // Re-check the operation before treating it as a provider failure.
                try {
                    ensureOperationActive()
                } catch (cancelled: CancellationException) {
                    releaseProviderAttempt(provider.id, attempt)
                    throw cancelled
                } catch (cancelled: OperationCancellationException) {
                    releaseProviderAttempt(provider.id, attempt)
                    throw cancelled
                } catch (deadline: OperationDeadlineExceededException) {
                    releaseProviderAttempt(provider.id, attempt)
                    throw deadline
                }
                recordProviderFailure(provider.id, attempt)
            }
        }
        return null
    }

    private suspend fun net.aieat.netswissknife.core.network.operation.OperationContext.fetchFromProvider(
        provider: GeoIpProvider,
        ip: String,
    ): GeoIpParseOutcome {
        var lease: GeoIpConnectionLease? = null
        try {
            ensureOperationActive()
            val connection = connectionFactory.open(provider.url(ip))
            lease = resources.register(GeoIpConnectionLease(connection))
            ensureOperationActive()
            val timeoutMs =
                budget
                    .remainingTimeoutMillis()
                    .coerceAtMost(REQUEST_TIMEOUT_MS)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                    .coerceAtLeast(1)
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")

            ensureOperationActive()
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("${provider.id} returned HTTP $responseCode")
            }
            ensureOperationActive()

            val body =
                connection.inputStream.use {
                    readBoundedBody(it, budget.maxResponseBytes.coerceAtMost(MAX_RESPONSE_BYTES))
                }
            ensureOperationActive()
            return provider.parse(ip, body)
        } finally {
            lease?.let { registered ->
                if (resources.release(registered)) registered.close()
            }
        }
    }

    private fun acquireProviderAttempt(providerId: String): ProviderBreakerAttempt? =
        synchronized(breakerLock) {
            val state = checkNotNull(providerBreakers[providerId])
            val openUntil = state.openUntilNanos
            if (openUntil == null) return@synchronized ProviderBreakerAttempt(state.generation, halfOpenToken = null)
            if (clock.nowNanos() < openUntil || state.halfOpenAttemptToken != null) return@synchronized null
            val token = ++state.nextAttemptToken
            state.halfOpenAttemptToken = token
            ProviderBreakerAttempt(state.generation, halfOpenToken = token)
        }

    private fun recordProviderSuccess(
        providerId: String,
        attempt: ProviderBreakerAttempt,
    ) = synchronized(breakerLock) {
        val state = providerBreakers.getValue(providerId)
        if (!ownsProviderAttempt(state, attempt)) return@synchronized
        state.apply {
            consecutiveFailures = 0
            openUntilNanos = null
            halfOpenAttemptToken = null
            generation++
        }
    }

    private fun recordProviderNoData(
        providerId: String,
        attempt: ProviderBreakerAttempt,
    ) = recordProviderSuccess(providerId, attempt)

    private fun recordProviderFailure(
        providerId: String,
        attempt: ProviderBreakerAttempt,
    ) = synchronized(breakerLock) {
        val state = providerBreakers.getValue(providerId)
        if (!ownsProviderAttempt(state, attempt)) return@synchronized
        val wasHalfOpenProbe = attempt.halfOpenToken != null
        state.consecutiveFailures++
        if (wasHalfOpenProbe || state.consecutiveFailures >= PROVIDER_FAILURE_THRESHOLD) {
            state.consecutiveFailures = 0
            state.openUntilNanos = clock.nowNanos() + PROVIDER_BREAKER_OPEN_NANOS
            state.halfOpenAttemptToken = null
            state.generation++
        }
    }

    private fun releaseProviderAttempt(
        providerId: String,
        attempt: ProviderBreakerAttempt,
    ) = synchronized(breakerLock) {
        val state = providerBreakers.getValue(providerId)
        if (state.generation == attempt.generation && attempt.halfOpenToken != null &&
            state.halfOpenAttemptToken == attempt.halfOpenToken
        ) {
            state.halfOpenAttemptToken = null
        }
    }

    private fun ownsProviderAttempt(
        state: ProviderBreakerState,
        attempt: ProviderBreakerAttempt,
    ): Boolean =
        state.generation == attempt.generation &&
            when (val token = attempt.halfOpenToken) {
                null -> state.openUntilNanos == null && state.halfOpenAttemptToken == null
                else -> state.halfOpenAttemptToken == token
            }

    private suspend fun net.aieat.netswissknife.core.network.operation.OperationContext.readBoundedBody(
        input: java.io.InputStream,
        maxBytes: Long,
    ): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BODY_BUFFER_BYTES)
        while (true) {
            ensureOperationActive()
            val read = input.read(buffer)
            ensureOperationActive()
            if (read < 0) break
            if (output.size().toLong() + read > maxBytes) return ""
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private class GeoIpConnectionLease(
        private val connection: HttpURLConnection,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) connection.disconnect()
        }
    }

    private fun newOperationSession(clock: MonotonicClock): OperationSession =
        OperationSession(
            OperationBudget.start(
                requirement = OperationRequirement.INTERNET,
                timeoutMillis = REQUEST_TIMEOUT_MS,
                maxConcurrentProbes = 1,
                maxResponseBytes = MAX_RESPONSE_BYTES,
                clock = clock,
            ),
        )

    companion object {
        private const val DEFAULT_BASE_URL = "https://ipinfo.io"
        private const val REQUEST_TIMEOUT_MS = TracerouteOperation.MAX_GEO_IP_WAIT_MILLIS
        private const val MAX_RESPONSE_BYTES = 65_536L
        private const val BODY_BUFFER_BYTES = 4_096
        private const val PROVIDER_FAILURE_THRESHOLD = 3
        private const val PROVIDER_BREAKER_OPEN_NANOS = 60_000_000_000L

        private fun defaultProviders(): List<GeoIpProvider> =
            listOf(
                IpInfoGeoIpProvider(),
                IpWhoGeoIpProvider(),
                IpApiGeoIpProvider(),
            )
    }
}
