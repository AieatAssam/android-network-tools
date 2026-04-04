package net.aieat.netswissknife.core.network.whois

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object WhoisResponseParser {

    // ── Referral parsers ──────────────────────────────────────────────────────

    fun parseReferral(response: String): String? =
        findFirstValue(response, "refer")?.takeIf { it.isNotBlank() }

    fun parseRegistrarWhoisServer(response: String): String? =
        findFirstValue(response, "Registrar WHOIS Server")?.takeIf { it.isNotBlank() }

    fun parseRirReferral(response: String): String? {
        val raw = findFirstValue(response, "ReferralServer")
            ?: findFirstValue(response, "whois")
            ?: return null
        return raw
            .removePrefix("whois://")
            .removePrefix("rwhois://")
            .substringBefore(":")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    // ── Domain field parsers ──────────────────────────────────────────────────

    fun parseDomainName(response: String): String? =
        findFirstValue(response, "Domain Name", "domain")

    fun parseRegistrar(response: String): String? =
        findFirstValue(response, "Registrar", "registrar")

    fun parseRegistrarUrl(response: String): String? =
        findFirstValue(response, "Registrar URL")

    fun parseRegisteredOn(response: String): Long? =
        parseDate(findFirstValue(response, "Creation Date", "created", "Registered On"))

    fun parseExpiresOn(response: String): Long? =
        parseDate(findFirstValue(response, "Registry Expiry Date", "Expiry Date", "expires"))

    fun parseUpdatedOn(response: String): Long? =
        parseDate(findFirstValue(response, "Updated Date", "last-modified"))

    fun parseNameServers(response: String): List<String> =
        findAllValues(response, "Name Server", "nserver")
            .map { it.uppercase(Locale.ROOT).substringBefore(" ").trim() }
            .filter { it.isNotBlank() }
            .distinct()

    fun parseStatusCodes(response: String): List<String> =
        findAllValues(response, "Domain Status", "status")
            .map { it.substringBefore("http").trim() }
            .filter { it.isNotBlank() }

    fun parseRegistrantOrg(response: String): String? =
        findFirstValue(response, "Registrant Organization", "org-name", "Organization")

    fun parseRegistrantCountry(response: String): String? =
        findFirstValue(response, "Registrant Country", "country")

    fun parseDnssec(response: String): String? =
        findFirstValue(response, "DNSSEC")

    // ── IP/ASN field parsers ──────────────────────────────────────────────────

    fun parseNetName(response: String): String? =
        findFirstValue(response, "NetName", "network")

    fun parseNetRange(response: String): String? =
        findFirstValue(response, "NetRange", "inetnum")

    fun parseOrgName(response: String): String? =
        findFirstValue(response, "OrgName", "descr")

    fun parseCountry(response: String): String? =
        findFirstValue(response, "Country", "country")

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun findFirstValue(response: String, vararg keys: String): String? {
        for (line in response.lines()) {
            for (key in keys) {
                val prefix = "$key:"
                if (line.trimStart().startsWith(prefix, ignoreCase = true)) {
                    return line.substringAfter(":").trim().takeIf { it.isNotBlank() }
                }
            }
        }
        return null
    }

    private fun findAllValues(response: String, vararg keys: String): List<String> {
        val results = mutableListOf<String>()
        for (line in response.lines()) {
            for (key in keys) {
                val prefix = "$key:"
                if (line.trimStart().startsWith(prefix, ignoreCase = true)) {
                    val value = line.substringAfter(":").trim()
                    if (value.isNotBlank()) results.add(value)
                    break
                }
            }
        }
        return results
    }

    private val DATE_FORMATS = listOf(
        DateTimeFormatter.ISO_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
    )

    fun parseDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        for (fmt in DATE_FORMATS) {
            try {
                return try {
                    java.time.OffsetDateTime.parse(trimmed, fmt)
                        .toInstant().toEpochMilli()
                } catch (_: DateTimeParseException) {
                    LocalDate.parse(trimmed, fmt)
                        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                }
            } catch (_: Exception) {
                // try next format
            }
        }
        return null
    }
}
