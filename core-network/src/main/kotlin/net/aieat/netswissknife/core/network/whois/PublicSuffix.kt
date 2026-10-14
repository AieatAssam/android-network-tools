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
        val contents = stream.use { input ->
            readPinnedResource(input, UNCOMPRESSED_BYTES).toString(Charsets.UTF_8)
        }
        Rules.parse(contents)
    }

    /** Bounded exact-size read using APIs available on the app's API 26 minimum. */
    private fun readPinnedResource(input: java.io.InputStream, expectedBytes: Int): ByteArray {
        val bytes = ByteArray(expectedBytes)
        var offset = 0
        while (offset < expectedBytes) {
            val count = input.read(bytes, offset, expectedBytes - offset)
            if (count < 0) break
            if (count == 0) {
                val next = input.read()
                if (next < 0) break
                bytes[offset++] = next.toByte()
            } else {
                offset += count
            }
        }
        check(offset == expectedBytes && input.read() == -1) {
            "Bundled Public Suffix List length changed; refresh its pinned size and hash"
        }
        return bytes
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
                    contents = contents,
                    exactCapacity = 10_240,
                    wildcardCapacity = 32,
                    exceptionCapacity = 16,
                    exactUnicodeCapacity = 640,
                    wildcardUnicodeCapacity = 16,
                    exceptionsUnicodeCapacity = 16,
                )
                val privateRules = RuleSet(
                    contents = contents,
                    exactCapacity = 5_120,
                    wildcardCapacity = 400,
                    exceptionCapacity = 1,
                    exactUnicodeCapacity = 32,
                    wildcardUnicodeCapacity = 16,
                    exceptionsUnicodeCapacity = 1,
                )
                check(contents.startsWith(INDEX_HEADER)) { "Bundled Public Suffix List index has an unsupported header" }
                var lineStart = INDEX_HEADER.length
                while (lineStart < contents.length) {
                    val newline = contents.indexOf('\n', lineStart).let { if (it < 0) contents.length else it }
                    check(newline < contents.length && newline - lineStart >= 3) {
                        "Bundled Public Suffix List index contains a malformed record"
                    }
                    val ruleEnd = newline
                    val section = contents[lineStart]
                    val kind = contents[lineStart + 1]
                    val ruleStart = lineStart + 2
                    val rules = when (section) {
                        'I' -> icann
                        'P' -> privateRules
                        else -> error("Bundled Public Suffix List index contains an invalid section")
                    }
                    val marker = when (kind) {
                        EXACT_KIND -> '='
                        WILDCARD_KIND -> '*'
                        EXCEPTION_KIND -> '!'
                        else -> error("Bundled Public Suffix List index contains an invalid rule kind")
                    }
                    addRule(contents, ruleStart, ruleEnd, marker, rules)
                    lineStart = newline + 1
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
                check(start < end) { "Bundled Public Suffix List index contains an empty rule" }
                // The vendored, SHA-recorded source is lowercase NFC with no
                // inline comments. Hash directly from the source slice rather
                // than allocating a String for each bundled rule.
                var hash = 0
                var isUnicode = false
                for (index in start until end) {
                    val char = contents[index]
                    hash = 31 * hash + char.code
                    if (char.code > 127) isUnicode = true
                }
                when {
                    isUnicode && marker == '!' -> rules.exceptionsUnicode.add(start, end, hash)
                    isUnicode && marker == '*' -> rules.wildcardUnicode.add(start, end, hash)
                    isUnicode -> rules.exactUnicode.add(start, end, hash)
                    marker == '!' -> rules.exceptions.add(start, end, hash)
                    marker == '*' -> rules.wildcard.add(start, end, hash)
                    else -> rules.exact.add(start, end, hash)
                }
            }
        }
    }

    private class RuleSet(
        contents: String,
        exactCapacity: Int,
        wildcardCapacity: Int,
        exceptionCapacity: Int,
        exactUnicodeCapacity: Int,
        wildcardUnicodeCapacity: Int,
        exceptionsUnicodeCapacity: Int,
    ) {
        val exact = RuleTable(contents, exactCapacity)
        val wildcard = RuleTable(contents, wildcardCapacity)
        val exceptions = RuleTable(contents, exceptionCapacity)
        val exactUnicode = RuleTable(contents, exactUnicodeCapacity)
        val wildcardUnicode = RuleTable(contents, wildcardUnicodeCapacity)
        val exceptionsUnicode = RuleTable(contents, exceptionsUnicodeCapacity)
    }

    /** Open addressing over source offsets avoids per-rule String and HashMap.Node allocations. */
    private class RuleTable(
        private val contents: String,
        expectedCapacity: Int,
    ) {
        private var starts = IntArray(tableCapacity(expectedCapacity))
        private var lengths = IntArray(starts.size)
        private var hashes = IntArray(starts.size)
        private var size = 0

        fun add(start: Int, end: Int, hash: Int) {
            if (size + 1 > starts.size * MAX_LOAD_NUMERATOR / MAX_LOAD_DENOMINATOR) resize()
            insert(start, end, hash)
        }

        private fun insert(start: Int, end: Int, hash: Int) {
            val length = end - start
            val mask = starts.lastIndex
            var index = spread(hash) and mask
            while (true) {
                val existingLength = lengths[index]
                if (existingLength == 0) {
                    starts[index] = start
                    lengths[index] = length
                    hashes[index] = hash
                    size++
                    return
                }
                if (hashes[index] == hash && existingLength == length &&
                    contents.regionMatches(starts[index], contents, start, length)
                ) return
                index = (index + 1) and mask
            }
        }

        operator fun contains(value: String): Boolean {
            val hash = value.hashCode()
            val mask = starts.lastIndex
            var index = spread(hash) and mask
            while (true) {
                val length = lengths[index]
                if (length == 0) return false
                if (hashes[index] == hash && length == value.length &&
                    contents.regionMatches(starts[index], value, 0, length)
                ) return true
                index = (index + 1) and mask
            }
        }

        private fun resize() {
            val previousStarts = starts
            val previousLengths = lengths
            val previousHashes = hashes
            starts = IntArray(previousStarts.size shl 1)
            lengths = IntArray(starts.size)
            hashes = IntArray(starts.size)
            size = 0
            for (index in previousStarts.indices) {
                val length = previousLengths[index]
                if (length != 0) {
                    val start = previousStarts[index]
                    insert(start, start + length, previousHashes[index])
                }
            }
        }

        private fun spread(hash: Int): Int = hash xor (hash ushr 16)

        private fun tableCapacity(expectedCapacity: Int): Int {
            var capacity = 2
            while (capacity < expectedCapacity) capacity = capacity shl 1
            return capacity
        }

        private companion object {
            const val MAX_LOAD_NUMERATOR = 3
            const val MAX_LOAD_DENOMINATOR = 4
        }
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

    private const val RESOURCE_PATH = "/psl/public_suffix_list.index"
    private const val UNCOMPRESSED_BYTES = 165_002
    private const val EXACT_KIND = '='
    private const val WILDCARD_KIND = '*'
    private const val EXCEPTION_KIND = '!'
    private const val INDEX_HEADER = "# PSL-RUNTIME-INDEX/1\n" +
        "# Mozilla Public Suffix List, MPL-2.0\n" +
        "# Version: 2026-09-24_13-26-36_UTC\n" +
        "# Upstream commit: a179a48c465e818cfd8d626691cb317985da87fb\n" +
        "# Source SHA-256: 257b298daca42f6d8ec964e238c2a55518e14f09d3117917ec8acee6f188503e\n"
}
