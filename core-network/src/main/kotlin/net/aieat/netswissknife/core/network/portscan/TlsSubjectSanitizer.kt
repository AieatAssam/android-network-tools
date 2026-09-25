package net.aieat.netswissknife.core.network.portscan

/** Makes untrusted certificate CN text safe for a single-line result row and exported report. */
object TlsSubjectSanitizer {
    private const val MAX_LENGTH = 200

    /** Replaces control/format/separator characters with spaces, collapses whitespace, and caps length. */
    fun sanitize(subject: String): String {
        val printable = StringBuilder(subject.length)
        subject.codePoints().forEach { codePoint ->
            val type = Character.getType(codePoint)
            val unsafe = Character.isISOControl(codePoint) ||
                type == Character.FORMAT.toInt() ||
                type == Character.LINE_SEPARATOR.toInt() ||
                type == Character.PARAGRAPH_SEPARATOR.toInt() ||
                type == Character.SURROGATE.toInt() ||
                type == Character.UNASSIGNED.toInt()
            if (unsafe || Character.isWhitespace(codePoint)) printable.append(' ')
            else printable.appendCodePoint(codePoint)
        }
        val normalized = printable.toString()
            .replace(WHITESPACE, " ")
            .trim()
        return truncateAtCodePointBoundary(normalized)
    }

    /** Keeps the 200 UTF-16-unit cap without returning half of a supplementary character. */
    private fun truncateAtCodePointBoundary(value: String): String {
        var offset = 0
        var utf16Units = 0
        while (offset < value.length) {
            val codePoint = value.codePointAt(offset)
            val width = Character.charCount(codePoint)
            if (utf16Units + width > MAX_LENGTH) break
            utf16Units += width
            offset += width
        }
        return value.substring(0, offset).trimEnd()
    }

    private val WHITESPACE = Regex("\\s+")
}
