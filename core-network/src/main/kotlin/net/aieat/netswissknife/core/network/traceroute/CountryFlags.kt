package net.aieat.netswissknife.core.network.traceroute

import java.util.Locale

/** Formats validated ISO 3166-1 alpha-2 country codes for traceroute presentation. */
object CountryFlags {
    private val supportedCodes = Locale.getISOCountries().toSet()

    /** Returns an emoji flag for a known two-letter ISO country code, or an empty string. */
    fun flagEmoji(countryCode: String?): String {
        val normalizedCode = normalize(countryCode) ?: return ""
        return buildString {
            normalizedCode.forEach { letter ->
                val regionalIndicator = REGIONAL_INDICATOR_A + (letter.code - 'A'.code)
                append(Character.toChars(regionalIndicator).concatToString())
            }
        }
    }

    /** Returns an English country name for a known code, or null for malformed/unknown codes. */
    fun countryName(countryCode: String?): String? {
        val normalizedCode = normalize(countryCode) ?: return null
        return Locale.Builder().setRegion(normalizedCode).build()
            .getDisplayCountry(DISPLAY_LOCALE)
            .takeIf { it.isNotBlank() && it != normalizedCode }
    }

    /** Uses the code-derived flag and name, falling back to the provider's name when needed. */
    fun label(countryCode: String?, fallbackCountryName: String): String {
        val name = countryName(countryCode) ?: fallbackCountryName
        val flag = flagEmoji(countryCode)
        return if (flag.isEmpty()) name else "$flag $name"
    }

    private fun normalize(countryCode: String?): String? {
        val rawCode = countryCode?.trim() ?: return null
        if (rawCode.length != 2 || rawCode.any { it !in 'A'..'Z' && it !in 'a'..'z' }) return null
        val code = rawCode.uppercase(Locale.ROOT)
        return code.takeIf { it in supportedCodes }
    }

    private const val REGIONAL_INDICATOR_A = 0x1F1E6
    private val DISPLAY_LOCALE = Locale.US
}
