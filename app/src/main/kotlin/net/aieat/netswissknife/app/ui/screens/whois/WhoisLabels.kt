package net.aieat.netswissknife.app.ui.screens.whois

import java.util.Locale

/**
 * Human label for one domain status as shown on a WHOIS status chip.
 *
 * RDAP serves statuses as spaced phrases ("client delete prohibited") while port-43 WHOIS serves
 * EPP camel case ("clientDeleteProhibited https://icann.org/epp#..."). Both forms are matched
 * on their whitespace-free lowercase code, and anything unlisted keeps its words.
 */
internal fun whoisStatusLabel(raw: String): String {
    val status = raw.substringBefore(" http").trim()
    KNOWN_LABELS[status.filterNot(Char::isWhitespace).lowercase(Locale.ROOT)]?.let { return it }

    val words =
        status
            .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
            .map { it.lowercase(Locale.ROOT) }
    val meaningful = if (words.size > 1 && words.first() in REGISTRY_SIDE_PREFIXES) words.drop(1) else words
    return meaningful.joinToString(" ").replaceFirstChar { it.uppercase(Locale.ROOT) }
}

private val REGISTRY_SIDE_PREFIXES = setOf("client", "server")

private val KNOWN_LABELS =
    mapOf(
        "clienttransferprohibited" to "Transfer Locked",
        "clientdeleteprohibited" to "Delete Locked",
        "clientupdateprohibited" to "Update Locked",
        "servertransferprohibited" to "Server Transfer Locked",
        "clienthold" to "Client Hold",
        "pendingdelete" to "Pending Delete",
    )

/** The two DNSSEC states WHOIS reports, shown with one localised label each. */
enum class WhoisDnssecState { SIGNED, UNSIGNED }

/** A DNSSEC value as shown: a known state (localised by the screen) or a humanized unlisted value. */
sealed interface WhoisDnssecLabel {
    data class Known(
        val state: WhoisDnssecState,
    ) : WhoisDnssecLabel

    data class Text(
        val text: String,
    ) : WhoisDnssecLabel
}

/**
 * Maps the DNSSEC value from either source to one label. Port-43 WHOIS reports
 * `signedDelegation` or `unsigned`, and RDAP reports "Signed" or "Unsigned"; both must read the same.
 * Anything else is humanized rather than shown as raw camel case.
 */
internal fun whoisDnssecLabel(raw: String): WhoisDnssecLabel {
    val key = raw.filterNot(Char::isWhitespace).lowercase(Locale.ROOT)
    val words =
        raw
            .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
            .map { it.lowercase(Locale.ROOT) }
    val humanized = words.joinToString(" ").replaceFirstChar { it.uppercase(Locale.ROOT) }
    return when (key) {
        "signed", "signeddelegation" -> WhoisDnssecLabel.Known(WhoisDnssecState.SIGNED)
        "unsigned" -> WhoisDnssecLabel.Known(WhoisDnssecState.UNSIGNED)
        else -> WhoisDnssecLabel.Text(humanized)
    }
}
