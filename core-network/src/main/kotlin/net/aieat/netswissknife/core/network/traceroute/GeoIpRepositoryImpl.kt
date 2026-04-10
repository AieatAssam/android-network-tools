package net.aieat.netswissknife.core.network.traceroute

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

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
class GeoIpRepositoryImpl : GeoIpRepository {

    private val cache = ConcurrentHashMap<String, HopGeoLocation?>()

    override suspend fun lookup(ip: String): HopGeoLocation? {
        if (isPrivateOrReserved(ip)) return null
        return cache.getOrPut(ip) { fetchGeoIp(ip) }
    }

    // ── Network ───────────────────────────────────────────────────────────────

    private suspend fun fetchGeoIp(ip: String): HopGeoLocation? = withContext(Dispatchers.IO) {
        val conn = URI("https://ipinfo.io/$ip/json").toURL().openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5_000
            conn.readTimeout    = 5_000
            conn.requestMethod  = "GET"
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode != 200) return@withContext null

            val body = conn.inputStream.use { it.bufferedReader().readText() }
            parseIpInfoResponse(ip, body)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private fun parseIpInfoResponse(ip: String, json: String): HopGeoLocation? {
        // Bogon check – ipinfo returns {"bogon":true} for private addresses
        if (json.contains("\"bogon\"")) return null

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
