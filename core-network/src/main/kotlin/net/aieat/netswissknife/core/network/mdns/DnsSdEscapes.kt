package net.aieat.netswissknife.core.network.mdns

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Presentation-format escapes used in DNS-SD instance labels (RFC 1035 section 5.1, RFC 6763 section 4.3).
 *
 * - `\DDD` (three decimal digits) is one raw byte. Consecutive byte escapes are decoded together as UTF-8,
 *   so `\226\128\153` becomes U+2019.
 * - `\X` for any other character is that literal character (`\.`, `\\`, `\(`, `\)`).
 * - Malformed escapes are never rewritten or rejected: they stay exactly as written.
 */
object DnsSdEscapes {
    /** A decimal byte escape is a backslash followed by exactly three digits. */
    private const val DECIMAL_ESCAPE_LENGTH = 4
    private const val DECIMAL_DIGIT_COUNT = 3
    private const val MAX_BYTE_VALUE = 255
    private const val DECIMAL_RADIX = 10

    /** Decodes presentation escapes in one label or display string. Never throws. */
    fun decode(raw: String): String {
        if (raw.indexOf('\\') < 0) return raw

        val out = StringBuilder(raw.length)
        val pendingBytes = ByteArrayOutputStream()
        val pendingRaw = StringBuilder()

        fun flushBytes() {
            if (pendingBytes.size() == 0) return
            val decoded = decodeUtf8Strict(pendingBytes.toByteArray())
            if (decoded != null) out.append(decoded) else out.append(pendingRaw)
            pendingBytes.reset()
            pendingRaw.setLength(0)
        }

        var index = 0
        while (index < raw.length) {
            val current = raw[index]
            if (current != '\\') {
                flushBytes()
                out.append(current)
                index++
                continue
            }

            val next = raw.getOrNull(index + 1)
            when {
                next != null && isAsciiDigit(next) && isDecimalEscape(raw, index) -> {
                    val value = raw.substring(index + 1, index + DECIMAL_ESCAPE_LENGTH).toInt(DECIMAL_RADIX)
                    pendingBytes.write(value)
                    pendingRaw.append(raw, index, index + DECIMAL_ESCAPE_LENGTH)
                    index += DECIMAL_ESCAPE_LENGTH
                }

                next != null && !isAsciiDigit(next) -> {
                    flushBytes()
                    out.append(next)
                    index += 2
                }

                else -> {
                    // Trailing backslash or a digit run that is not a three-digit byte: keep the backslash.
                    flushBytes()
                    out.append(current)
                    index++
                }
            }
        }
        flushBytes()
        return out.toString()
    }

    /** Splits a presentation-format name into labels on unescaped dots. Labels keep their escapes. */
    fun splitLabels(name: String): List<String> {
        val labels = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < name.length) {
            val ch = name[index]
            when {
                ch == '\\' && isDecimalEscape(name, index) -> {
                    current.append(name, index, index + DECIMAL_ESCAPE_LENGTH)
                    index += DECIMAL_ESCAPE_LENGTH
                }

                ch == '\\' && index + 1 < name.length -> {
                    current.append(ch).append(name[index + 1])
                    index += 2
                }

                ch == '.' -> {
                    labels += current.toString()
                    current.setLength(0)
                    index++
                }

                else -> {
                    current.append(ch)
                    index++
                }
            }
        }
        labels += current.toString()
        // A trailing unescaped dot produces an empty final label, which is the DNS root and is dropped.
        if (labels.last().isEmpty()) labels.removeAt(labels.lastIndex)
        return labels
    }

    private fun isDecimalEscape(
        text: String,
        backslashIndex: Int,
    ): Boolean {
        if (backslashIndex + DECIMAL_ESCAPE_LENGTH > text.length) return false
        val digits = text.substring(backslashIndex + 1, backslashIndex + DECIMAL_ESCAPE_LENGTH)
        return digits.length == DECIMAL_DIGIT_COUNT &&
            digits.all { isAsciiDigit(it) } &&
            digits.toInt(DECIMAL_RADIX) <= MAX_BYTE_VALUE
    }

    private fun isAsciiDigit(ch: Char): Boolean = ch in '0'..'9'

    private fun decodeUtf8Strict(bytes: ByteArray): String? =
        try {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
}
