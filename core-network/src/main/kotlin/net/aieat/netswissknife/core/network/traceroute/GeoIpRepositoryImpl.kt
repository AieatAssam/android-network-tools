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
        try {
            val conn = URI("https://ipinfo.io/$ip/json").toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout    = 5_000
            conn.requestMethod  = "GET"
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode != 200) return@withContext null

            val body = conn.inputStream.bufferedReader().readText()
            parseIpInfoResponse(ip, body)
        } catch (_: Exception) {
            null
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

    @Suppress("CyclomaticComplexMethod")
    private fun countryName(code: String): String = when (code.uppercase()) {
        "US" -> "United States"
        "GB" -> "United Kingdom"
        "DE" -> "Germany"
        "FR" -> "France"
        "JP" -> "Japan"
        "CN" -> "China"
        "CA" -> "Canada"
        "AU" -> "Australia"
        "BR" -> "Brazil"
        "IN" -> "India"
        "RU" -> "Russia"
        "NL" -> "Netherlands"
        "SE" -> "Sweden"
        "SG" -> "Singapore"
        "HK" -> "Hong Kong"
        "KR" -> "South Korea"
        "IT" -> "Italy"
        "ES" -> "Spain"
        "CH" -> "Switzerland"
        "NO" -> "Norway"
        "DK" -> "Denmark"
        "FI" -> "Finland"
        "PL" -> "Poland"
        "ZA" -> "South Africa"
        "MX" -> "Mexico"
        "AR" -> "Argentina"
        "TR" -> "Turkey"
        "ID" -> "Indonesia"
        "TH" -> "Thailand"
        "PH" -> "Philippines"
        "MY" -> "Malaysia"
        "UA" -> "Ukraine"
        "IE" -> "Ireland"
        "NZ" -> "New Zealand"
        "PT" -> "Portugal"
        "AT" -> "Austria"
        "BE" -> "Belgium"
        "CZ" -> "Czech Republic"
        "HU" -> "Hungary"
        "RO" -> "Romania"
        "GR" -> "Greece"
        "TW" -> "Taiwan"
        "VN" -> "Vietnam"
        "EG" -> "Egypt"
        "SA" -> "Saudi Arabia"
        "AE" -> "UAE"
        "IL" -> "Israel"
        "PK" -> "Pakistan"
        "NG" -> "Nigeria"
        else -> code
    }
}
