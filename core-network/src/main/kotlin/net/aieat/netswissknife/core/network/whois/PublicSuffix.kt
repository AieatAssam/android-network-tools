package net.aieat.netswissknife.core.network.whois

import java.net.IDN
import java.text.Normalizer
import java.util.Locale
import java.util.zip.GZIPInputStream

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
        val contents = GZIPInputStream(stream).use { it.readBytes().toString(Charsets.UTF_8) }
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
                // Capacities fit the bundled 2026-09-24 section counts without HashSet rehashes.
                val icann = RuleSet(
                    exactCapacity = 10_240,
                    wildcardCapacity = 32,
                    exceptionCapacity = 16,
                    exactUnicodeCapacity = 640,
                    wildcardUnicodeCapacity = 16,
                    exceptionsUnicodeCapacity = 16,
                )
                val privateRules = RuleSet(
                    exactCapacity = 5_120,
                    wildcardCapacity = 400,
                    exceptionCapacity = 1,
                    exactUnicodeCapacity = 32,
                    wildcardUnicodeCapacity = 16,
                    exceptionsUnicodeCapacity = 1,
                )
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
                    addRule(contents, ruleStart, ruleEnd, marker, if (inPrivateSection) privateRules else icann)
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
                val rule = contents.substring(start, end)
                // The vendored, SHA-recorded source is lowercase NFC with no
                // inline comments, so retain each official rule without a
                // second normalization/validation pass on the cold path.
                val isUnicode = rule.any { it.code > 127 }
                when {
                    isUnicode && marker == '!' -> rules.exceptionsUnicode += rule
                    isUnicode && marker == '*' -> rules.wildcardUnicode += rule
                    isUnicode -> rules.exactUnicode += rule
                    marker == '!' -> rules.exceptions += rule
                    marker == '*' -> rules.wildcard += rule
                    else -> rules.exact += rule
                }
            }
        }
    }

    private class RuleSet(
        exactCapacity: Int,
        wildcardCapacity: Int,
        exceptionCapacity: Int,
        exactUnicodeCapacity: Int,
        wildcardUnicodeCapacity: Int,
        exceptionsUnicodeCapacity: Int,
    ) {
        val exact = HashSet<String>(exactCapacity)
        val wildcard = HashSet<String>(wildcardCapacity)
        val exceptions = HashSet<String>(exceptionCapacity)
        val exactUnicode = HashSet<String>(exactUnicodeCapacity)
        val wildcardUnicode = HashSet<String>(wildcardUnicodeCapacity)
        val exceptionsUnicode = HashSet<String>(exceptionsUnicodeCapacity)
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

    private const val RESOURCE_PATH = "/psl/public_suffix_list.dat.gz"
    private const val PRIVATE_BEGIN = "// ===BEGIN PRIVATE DOMAINS==="
    private const val PRIVATE_END = "// ===END PRIVATE DOMAINS==="
}
