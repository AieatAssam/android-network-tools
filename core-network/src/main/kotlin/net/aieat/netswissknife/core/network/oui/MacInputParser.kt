package net.aieat.netswissknife.core.network.oui

import java.util.Locale

/** Why a pasted entry is neither a MAC address nor a three-byte OUI prefix. */
enum class MacInputProblem {
    /** A character other than a hexadecimal digit or a separator was used. */
    CHARACTERS,

    /** The entry does not contain exactly 6 (OUI prefix) or 12 (MAC) hexadecimal digits. */
    LENGTH,

    /** The digits are grouped in a way no notation uses, such as `B8:27EB:11:22:33`. */
    SEPARATORS,
}

sealed interface MacParseResult {
    /** [hex] is 12 uppercase digits for a full MAC or 6 for an OUI prefix. */
    data class Parsed(
        val hex: String,
    ) : MacParseResult

    data class Invalid(
        val problem: MacInputProblem,
    ) : MacParseResult
}

/**
 * Lenient parser for the MAC Lookup tool. It accepts every notation a person might paste:
 * colon, hyphen, Cisco dotted and bare hex, mixed case, a 3-byte OUI prefix on its own, and
 * any multicast or broadcast address. It does not apply the Wake-on-LAN unicast rules; use
 * [net.aieat.netswissknife.core.network.wol.WolMagicPacket] for those.
 */
object MacInputParser {
    private const val HEX_DIGITS = "0123456789abcdefABCDEF"
    private const val SEPARATORS = ":-."
    private val SEPARATOR_CHARS = SEPARATORS.toCharArray()
    private const val MAC_DIGITS = 12
    private const val OUI_DIGITS = 6
    private val DELIMITERS = Regex("[\\s,;]+")
    private val GROUPINGS =
        setOf(
            listOf(2, 2, 2, 2, 2, 2),
            listOf(2, 2, 2),
            listOf(4, 4, 4),
            listOf(4, 2),
        )

    /** Splits pasted text on newlines, whitespace, commas and semicolons, dropping empty pieces. */
    fun splitEntries(text: String): List<String> = text.split(DELIMITERS).filter { it.isNotEmpty() }

    fun parse(entry: String): MacParseResult {
        val trimmed = entry.trim()
        val problem = problemOf(trimmed)
        return if (problem != null) {
            MacParseResult.Invalid(problem)
        } else {
            MacParseResult.Parsed(digitsOf(trimmed))
        }
    }

    private fun problemOf(trimmed: String): MacInputProblem? {
        val groups = trimmed.split(*SEPARATOR_CHARS)
        val digitCount = groups.sumOf { it.length }
        return when {
            trimmed.any { it !in HEX_DIGITS && it !in SEPARATORS } -> MacInputProblem.CHARACTERS
            digitCount != MAC_DIGITS && digitCount != OUI_DIGITS -> MacInputProblem.LENGTH
            groups.size > 1 && groups.map { it.length } !in GROUPINGS -> MacInputProblem.SEPARATORS
            else -> null
        }
    }

    private fun digitsOf(trimmed: String): String =
        trimmed
            .split(*SEPARATOR_CHARS)
            .joinToString("")
            .uppercase(Locale.ROOT)
}
