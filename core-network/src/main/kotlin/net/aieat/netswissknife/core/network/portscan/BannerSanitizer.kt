package net.aieat.netswissknife.core.network.portscan

/**
 * Sanitizes raw port banner text to safe, ASCII-printable characters only.
 *
 * Restricts output to the printable ASCII range (0x20–0x7E), trims surrounding
 * whitespace, and caps length at 200 characters to prevent log injection and
 * avoid leaking arbitrary Unicode data from untrusted remote services.
 */
internal object BannerSanitizer {
    private const val MAX_LEN = 200

    data class Result(val text: String, val truncated: Boolean)

    fun sanitize(raw: String): String = sanitizeWithTruncation(raw).text

    fun sanitizeWithTruncation(raw: String): Result {
        val trimmed = raw.trim()
        val printable = trimmed.take(MAX_LEN).filter { it.code in 0x20..0x7E }
        return Result(text = printable, truncated = trimmed.length > MAX_LEN)
    }
}
