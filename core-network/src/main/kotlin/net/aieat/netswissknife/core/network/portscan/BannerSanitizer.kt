package net.aieat.netswissknife.core.network.portscan

/**
 * Sanitizes raw port banner text to safe, ASCII-printable characters only.
 *
 * Line breaks (CR, LF, or CRLF, including blank-line runs) become a single
 * printable [LINE_SEPARATOR], so multi-line service replies stay readable
 * instead of running their lines together. Other characters outside the
 * printable ASCII range (0x20-0x7E) are dropped, surrounding whitespace is
 * trimmed, and output is capped at 200 characters to prevent log injection and
 * avoid leaking arbitrary Unicode data from untrusted remote services.
 */
internal object BannerSanitizer {
    private const val MAX_LEN = 200
    const val LINE_SEPARATOR = " | "
    private val lineBreaks: Regex = "\\s*[\\r\\n]+\\s*".toRegex()

    data class Result(val text: String, val truncated: Boolean)

    fun sanitize(raw: String): String = sanitizeWithTruncation(raw).text

    fun sanitizeWithTruncation(raw: String): Result {
        val joined = raw.trim().replace(lineBreaks, LINE_SEPARATOR)
        val printable = joined.take(MAX_LEN).filter { it.code in 0x20..0x7E }
        return Result(text = printable, truncated = joined.length > MAX_LEN)
    }
}
