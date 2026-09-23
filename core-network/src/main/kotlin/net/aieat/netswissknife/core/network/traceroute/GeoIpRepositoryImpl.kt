package net.aieat.netswissknife.core.network.traceroute

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.SystemMonotonicClock
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap

fun interface GeoIpConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

/**
 * [GeoIpRepository] that calls the ipinfo.io JSON API (free tier, HTTPS, no API key).
 *
 * Response shape:
 *   { "ip":"8.8.8.8", "city":"Mountain View", "region":"California",
 *     "country":"US", "loc":"37.3861,-122.0839", "org":"AS15169 Google LLC" }
 *
 * Private / reserved IP ranges are skipped and return null immediately.
 * Results are cached in-memory to avoid repeat calls for the same IP.
 */
class GeoIpRepositoryImpl internal constructor(
    private val baseUrl: String,
    private val connectionFactory: GeoIpConnectionFactory,
    internal var clock: MonotonicClock,
) : GeoIpRepository {

    constructor(baseUrl: String = DEFAULT_BASE_URL) : this(
        baseUrl = baseUrl,
        connectionFactory = GeoIpConnectionFactory { it.openConnection() as HttpURLConnection },
        clock = SystemMonotonicClock,
    )

    internal constructor(
        baseUrl: String,
        connectionFactory: GeoIpConnectionFactory,
    ) : this(baseUrl, connectionFactory, SystemMonotonicClock)

    private val cache = ConcurrentHashMap<String, HopGeoLocation?>()

    override suspend fun lookup(ip: String): HopGeoLocation? {
        currentCoroutineContext().ensureActive()
        if (isPrivateOrReserved(ip)) return null
        cache[ip]?.let { return it }
        val session = newOperationSession(clock)
        val result = executeLookup(ip, session, mapDeadlineToNull = true)
        if (result != null) cache[ip] = result
        return result
    }

    override suspend fun lookup(ip: String, operationSession: OperationSession): HopGeoLocation? {
        currentCoroutineContext().ensureActive()
        operationSession.cancellationReason?.let { reason ->
            if (reason == CancellationReason.DEADLINE_EXCEEDED) throw OperationDeadlineExceededException()
            throw OperationCancellationException(reason)
        }
        operationSession.budget.throwIfExpired()
        if (isPrivateOrReserved(ip)) return null
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
    ): HopGeoLocation? = withContext(Dispatchers.IO) {
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

    private suspend fun net.aieat.netswissknife.core.network.operation.OperationContext.fetchGeoIp(
        ip: String,
    ): HopGeoLocation? {
        var lease: GeoIpConnectionLease? = null
        try {
            ensureOperationActive()
            val url = URI("$baseUrl/$ip/json").toURL()
            val connection = connectionFactory.open(url)
            lease = resources.register(GeoIpConnectionLease(connection))
            ensureOperationActive()
            val timeoutMs = budget.remainingTimeoutMillis()
                .coerceAtMost(REQUEST_TIMEOUT_MS)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
                .coerceAtLeast(1)
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")

            ensureOperationActive()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            ensureOperationActive()

            val body = connection.inputStream.use {
                readBoundedBody(it, budget.maxResponseBytes.coerceAtMost(MAX_RESPONSE_BYTES))
            }
            ensureOperationActive()
            return parseIpInfoResponse(ip, body)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (deadline: OperationDeadlineExceededException) {
            throw deadline
        } catch (_: Exception) {
            // A disconnect caused by Stop/deadline can surface as ordinary I/O failure.
            ensureOperationActive()
            return null
        } finally {
            lease?.let { registered ->
                if (resources.release(registered)) registered.close()
            }
        }
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

    private class GeoIpConnectionLease(private val connection: HttpURLConnection) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        override fun close() {
            if (closed.compareAndSet(false, true)) connection.disconnect()
        }
    }

    private fun newOperationSession(clock: MonotonicClock): OperationSession = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.INTERNET,
            timeoutMillis = REQUEST_TIMEOUT_MS,
            maxConcurrentProbes = 1,
            maxResponseBytes = MAX_RESPONSE_BYTES,
            clock = clock,
        )
    )

    // ── Parsing ───────────────────────────────────────────────────────────────

    private fun parseIpInfoResponse(ip: String, json: String): HopGeoLocation? {
        // ipinfo may include `bogon: false` for public addresses. Only a root
        // JSON boolean true marks the response as a bogon; nested keys and text
        // inside strings are unrelated metadata.
        if (hasRootBooleanTrue(json, "bogon")) return null

        val city    = extractString(json, "city")    ?: ""
        val country = extractString(json, "country") ?: return null
        val loc     = extractString(json, "loc")     ?: return null
        val org     = extractString(json, "org")

        val (lat, lon) = loc.split(",").mapNotNull { it.trim().toDoubleOrNull() }
            .let { parts -> if (parts.size == 2) Pair(parts[0], parts[1]) else return null }

        val (isp, asn) = if (org != null) {
            val asnPart = Regex("^(AS\\d+)").find(org)?.groupValues?.get(1)
            val ispPart = org.removePrefix(asnPart ?: "").trim().trimStart()
            Pair(ispPart.ifBlank { null }, asnPart)
        } else Pair(null, null)

        return HopGeoLocation(
            ip          = ip,
            country     = countryName(country),
            countryCode = country,
            city        = city,
            lat         = lat,
            lon         = lon,
            isp         = isp,
            asn         = asn
        )
    }

    private fun extractString(json: String, key: String): String? {
        return Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
    }

    /**
     * Checks a top-level boolean field without matching nested or quoted text.
     * Invalid or non-boolean field values are treated as absent for this check.
     */
    private fun hasRootBooleanTrue(json: String, expectedKey: String): Boolean {
        var index = 0

        fun skipWhitespace() {
            while (index < json.length && json[index].isWhitespace()) index++
        }

        fun readString(): String? {
            if (index >= json.length || json[index] != '"') return null
            index++
            val value = StringBuilder()
            while (index < json.length) {
                val char = json[index++]
                when {
                    char == '"' -> return value.toString()
                    char == '\\' -> {
                        if (index >= json.length) return null
                        when (val escaped = json[index++]) {
                            '"', '\\', '/' -> value.append(escaped)
                            'b' -> value.append('\b')
                            'f' -> value.append('\u000c')
                            'n' -> value.append('\n')
                            'r' -> value.append('\r')
                            't' -> value.append('\t')
                            'u' -> {
                                if (index + 4 > json.length) return null
                                val codeUnit = json.substring(index, index + 4).toIntOrNull(16) ?: return null
                                value.append(codeUnit.toChar())
                                index += 4
                            }
                            else -> return null
                        }
                    }
                    else -> value.append(char)
                }
            }
            return null
        }

        fun skipValue(): Boolean {
            skipWhitespace()
            if (index >= json.length) return false
            if (json[index] == '"') return readString() != null
            if (json[index] == '{' || json[index] == '[') {
                var depth = 0
                var inString = false
                var escaped = false
                while (index < json.length) {
                    val char = json[index++]
                    if (inString) {
                        if (escaped) escaped = false
                        else when (char) {
                            '\\' -> escaped = true
                            '"' -> inString = false
                        }
                    } else when (char) {
                        '"' -> inString = true
                        '{', '[' -> depth++
                        '}', ']' -> {
                            depth--
                            if (depth == 0) return true
                            if (depth < 0) return false
                        }
                    }
                }
                return false
            }
            val start = index
            while (index < json.length && json[index] !in ",}" && !json[index].isWhitespace()) index++
            return index > start
        }

        skipWhitespace()
        if (index >= json.length || json[index++] != '{') return false
        while (true) {
            skipWhitespace()
            if (index >= json.length || json[index] == '}') return false
            val key = readString() ?: return false
            skipWhitespace()
            if (index >= json.length || json[index++] != ':') return false
            skipWhitespace()
            if (key == expectedKey && json.startsWith("true", index)) {
                val end = index + "true".length
                var delimiter = end
                while (delimiter < json.length && json[delimiter].isWhitespace()) delimiter++
                if (delimiter < json.length && json[delimiter] in ",}") return true
            }
            if (!skipValue()) return false
            skipWhitespace()
            when {
                index < json.length && json[index] == ',' -> index++
                index < json.length && json[index] == '}' -> return false
                else -> return false
            }
        }
    }

    // ── Private IP detection ──────────────────────────────────────────────────

    private fun isPrivateOrReserved(ip: String): Boolean {
        val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return true
        val (a, b) = parts
        return a == 10 ||
               (a == 172 && b in 16..31) ||
               (a == 192 && b == 168) ||
               a == 127 ||
               (a == 169 && b == 254) ||
               a == 0 ||
               a >= 240
    }

    // ── Country code → full name (ISO 3166-1 alpha-2 for common countries) ───

    private fun countryName(code: String): String = COUNTRY_NAMES[code.uppercase()] ?: code

    companion object {
        private const val DEFAULT_BASE_URL = "https://ipinfo.io"
        private const val REQUEST_TIMEOUT_MS = 5_000L
        private const val MAX_RESPONSE_BYTES = 65_536L
        private const val BODY_BUFFER_BYTES = 4_096
        private val COUNTRY_NAMES = mapOf(
            "US" to "United States",  "GB" to "United Kingdom", "DE" to "Germany",
            "FR" to "France",         "JP" to "Japan",          "CN" to "China",
            "CA" to "Canada",         "AU" to "Australia",      "BR" to "Brazil",
            "IN" to "India",          "RU" to "Russia",         "NL" to "Netherlands",
            "SE" to "Sweden",         "SG" to "Singapore",      "HK" to "Hong Kong",
            "KR" to "South Korea",    "IT" to "Italy",          "ES" to "Spain",
            "CH" to "Switzerland",    "NO" to "Norway",         "DK" to "Denmark",
            "FI" to "Finland",        "PL" to "Poland",         "ZA" to "South Africa",
            "MX" to "Mexico",         "AR" to "Argentina",      "TR" to "Turkey",
            "ID" to "Indonesia",      "TH" to "Thailand",       "PH" to "Philippines",
            "MY" to "Malaysia",       "UA" to "Ukraine",        "IE" to "Ireland",
            "NZ" to "New Zealand",    "PT" to "Portugal",       "AT" to "Austria",
            "BE" to "Belgium",        "CZ" to "Czech Republic", "HU" to "Hungary",
            "RO" to "Romania",        "GR" to "Greece",         "TW" to "Taiwan",
            "VN" to "Vietnam",        "EG" to "Egypt",          "SA" to "Saudi Arabia",
            "AE" to "UAE",            "IL" to "Israel",         "PK" to "Pakistan",
            "NG" to "Nigeria",
        )
    }
}
