package net.aieat.netswissknife.core.network.whois

import java.net.IDN
import java.text.Normalizer
import java.util.Locale

/**
 * Resolves a host's registrable domain (effective TLD + one label) using the
 * Mozilla Public Suffix List bundled with this module.
 *
 * The PSL's implicit `*` rule is used for unknown suffixes, so an unfamiliar
 * TLD still resolves to its final two labels. A host which is itself a public
 * suffix (and single-label hosts) is returned in canonical ASCII form because
 * it has no registrable label above the suffix.
 *
 * IDN conversion uses `java.net.IDN` (IDNA2003), matching the WHOIS query
 * normalizer; Unicode labels are returned as lowercase Punycode.
 */
object PublicSuffix {
    private val rules: Rules by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val stream = PublicSuffix::class.java.getResourceAsStream(RESOURCE_PATH)
            ?: error("Bundled Public Suffix List is missing: $RESOURCE_PATH")
        val contents = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        Rules.parse(contents)
    }

    /** Returns the site boundary in lowercase ASCII/Punycode form, including PRIVATE rules. */
    @JvmStatic
    fun registrableDomain(host: String): String = resolve(host, includePrivate = true)

    /**
     * Returns the domain boundary relevant to a domain-registry WHOIS query.
     * PRIVATE PSL rules describe hosted tenant boundaries (for example, GitHub
     * Pages), not domains registered with the TLD registry, so they are omitted.
     */
    internal fun domainForWhois(host: String): String = resolve(host, includePrivate = false)

    private fun resolve(host: String, includePrivate: Boolean): String {
        val rootedHost = host.trim().removeSuffix(".")
        if (rootedHost.isEmpty()) return rootedHost

        val asciiHost = try {
            rootedHost.split('.').joinToString(".") { label ->
                require(label.isNotEmpty())
                canonicalLabel(label)
            }
        } catch (_: IllegalArgumentException) {
            // Callers commonly validate domain names before reaching this API;
            // keep malformed input harmless and avoid surprising partial rewrites.
            return rootedHost
        }

        val labels = asciiHost.split('.')
        if (labels.size == 1) return asciiHost
        return rules.registrableDomain(labels, includePrivate)
    }

    private data class Rules(
        val icann: RuleSet,
        val privateRules: RuleSet,
    ) {
        fun registrableDomain(labels: List<String>, includePrivate: Boolean): String {
            // PSL algorithm's prevailing rule when there is no explicit match.
            var publicSuffixLength = 1
            var exceptionLength: Int? = null
            val ruleSets = if (includePrivate) listOf(icann, privateRules) else listOf(icann)

            for (start in labels.indices) {
                val candidate = labels.subList(start, labels.size).joinToString(".")
                val length = labels.size - start
                val unicodeCandidate = candidate.toUnicodeRuleCandidate()
                for (ruleSet in ruleSets) {
                    if ((candidate in ruleSet.exceptions ||
                            (unicodeCandidate != null && unicodeCandidate in ruleSet.exceptionsUnicode)) &&
                        (exceptionLength == null || length > exceptionLength)
                    ) {
                        exceptionLength = length
                    }
                    if ((candidate in ruleSet.exact ||
                            (unicodeCandidate != null && unicodeCandidate in ruleSet.exactUnicode)) &&
                        length > publicSuffixLength
                    ) {
                        publicSuffixLength = length
                    }

                    // A wildcard rule stores its fixed suffix (e.g. `ck` for `*.ck`).
                    // It consumes exactly one additional label to its left.
                    if (start > 0 &&
                        (candidate in ruleSet.wildcard ||
                            (unicodeCandidate != null && unicodeCandidate in ruleSet.wildcardUnicode)) &&
                        length + 1 > publicSuffixLength
                    ) {
                        publicSuffixLength = length + 1
                    }
                }
            }

            if (exceptionLength != null) {
                publicSuffixLength = exceptionLength - 1
            }

            val registrableLength = (publicSuffixLength + 1).coerceAtMost(labels.size)
            return labels.takeLast(registrableLength).joinToString(".")
        }

        companion object {
            fun parse(contents: String): Rules {
                // Capacities are based on the bundled 2026-09-24 snapshot.
                // Keep sections separate without duplicating their rule sets.
                val icann = RuleSet(exactCapacity = 8_192, wildcardCapacity = 320,
                    exceptionCapacity = 8, unicodeCapacity = 384)
                val privateRules = RuleSet(exactCapacity = 5_120, wildcardCapacity = 96,
                    exceptionCapacity = 8, unicodeCapacity = 256)
                var inPrivateSection = false

                var lineStart = 0
                while (lineStart < contents.length) {
                    val currentLineStart = lineStart
                    val newline = contents.indexOf('\n', lineStart).let { if (it < 0) contents.length else it }
                    lineStart = newline + 1
                    var ruleStart = currentLineStart
                    var ruleEnd = newline
                    if (ruleEnd > ruleStart && contents[ruleEnd - 1] == '\r') ruleEnd--

                    while (ruleStart < ruleEnd && contents[ruleStart].isWhitespace()) ruleStart++
                    if (ruleStart >= ruleEnd) continue
                    if (contents[ruleStart] == '/') {
                        val lineLength = ruleEnd - ruleStart
                        if (lineLength == PRIVATE_BEGIN.length &&
                            contents.regionMatches(ruleStart, PRIVATE_BEGIN, 0, lineLength)
                        ) inPrivateSection = true
                        if (lineLength == PRIVATE_END.length &&
                            contents.regionMatches(ruleStart, PRIVATE_END, 0, lineLength)
                        ) inPrivateSection = false
                        continue
                    }

                    var inlineComment = -1
                    var scan = ruleStart
                    while (scan + 1 < ruleEnd) {
                        if (contents[scan] == '/' && contents[scan + 1] == '/') {
                            inlineComment = scan
                            break
                        }
                        scan++
                    }
                    if (inlineComment >= 0) ruleEnd = inlineComment
                    while (ruleEnd > ruleStart && contents[ruleEnd - 1].isWhitespace()) ruleEnd--
                    if (ruleStart >= ruleEnd) continue

                    val marker = when {
                        contents[ruleStart] == '!' -> {
                            ruleStart++
                            '!'
                        }
                        contents[ruleStart] == '*' && ruleStart + 1 < ruleEnd && contents[ruleStart + 1] == '.' -> {
                            ruleStart += 2
                            '*'
                        }
                        else -> '='
                    }
                    if (ruleStart >= ruleEnd) continue
                    try {
                        addRule(contents, ruleStart, ruleEnd, marker, if (inPrivateSection) privateRules else icann)
                    } catch (_: IllegalArgumentException) {
                        // Ignore malformed entries rather than making the full
                        // bundled table unavailable due to one bad rule.
                    }
                }
                return Rules(icann, privateRules)
            }

            private fun addRule(
                contents: String,
                start: Int,
                end: Int,
                marker: Char,
                rules: RuleSet,
            ) {
                var unicode = false
                var validAscii = true
                var hasUppercase = false
                var labelLength = 0
                var previousWasHyphen = false

                for (index in start until end) {
                    val char = contents[index]
                    if (char.code > 127) {
                        unicode = true
                        continue
                    }
                    if (char in 'A'..'Z') hasUppercase = true
                    when {
                        char == '.' -> {
                            if (labelLength == 0 || labelLength > 63 || previousWasHyphen) validAscii = false
                            labelLength = 0
                            previousWasHyphen = false
                        }
                        char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' -> {
                            labelLength++
                            previousWasHyphen = false
                        }
                        char == '-' -> {
                            if (labelLength == 0) validAscii = false
                            labelLength++
                            previousWasHyphen = true
                        }
                        else -> validAscii = false
                    }
                }
                if (labelLength == 0 || labelLength > 63 || previousWasHyphen) validAscii = false

                val rule = contents.substring(start, end)
                val canonical = when {
                    unicode -> {
                        // The bundled official rules are lowercase NFC. Preserve
                        // them directly on the cold path; test the invariant.
                        if (hasUppercase) rule.lowercase(Locale.ROOT) else rule
                    }
                    validAscii -> if (hasUppercase) rule.lowercase(Locale.ROOT) else rule
                    else -> rule.split('.').joinToString(".") { label ->
                        require(label.isNotEmpty())
                        canonicalLabel(label)
                    }
                }

                val isUnicode = unicode
                when {
                    isUnicode && marker == '!' -> rules.exceptionsUnicode += canonical
                    isUnicode && marker == '*' -> rules.wildcardUnicode += canonical
                    isUnicode -> rules.exactUnicode += canonical
                    marker == '!' -> rules.exceptions += canonical
                    marker == '*' -> rules.wildcard += canonical
                    else -> rules.exact += canonical
                }
            }
        }
    }

    private class RuleSet(
        exactCapacity: Int,
        wildcardCapacity: Int,
        exceptionCapacity: Int,
        unicodeCapacity: Int,
    ) {
        val exact = HashSet<String>(exactCapacity)
        val wildcard = HashSet<String>(wildcardCapacity)
        val exceptions = HashSet<String>(exceptionCapacity)
        val exactUnicode = HashSet<String>(unicodeCapacity)
        val wildcardUnicode = HashSet<String>(unicodeCapacity / 32)
        val exceptionsUnicode = HashSet<String>(unicodeCapacity / 128)
    }

    private fun String.toUnicodeRuleCandidate(): String? =
        if (contains("xn--")) IDN.toUnicode(this).lowercase(Locale.ROOT).normalizeUnicodeRule() else null

    private fun String.normalizeUnicodeRule(): String = Normalizer.normalize(this, Normalizer.Form.NFC)

    private fun canonicalLabel(label: String): String {
        if (isValidAsciiLabel(label) && !label.startsWith("xn--", ignoreCase = true)) {
            return label.lowercase(Locale.ROOT)
        }
        return IDN.toASCII(label, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
    }

    private fun isValidAsciiLabel(label: String): Boolean {
        if (label.isEmpty() || label.length > 63) return false
        if (label.first() == '-' || label.last() == '-') return false
        return label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }
    }

    private const val RESOURCE_PATH = "/psl/public_suffix_list.dat"
    private const val PRIVATE_BEGIN = "// ===BEGIN PRIVATE DOMAINS==="
    private const val PRIVATE_END = "// ===END PRIVATE DOMAINS==="
}
