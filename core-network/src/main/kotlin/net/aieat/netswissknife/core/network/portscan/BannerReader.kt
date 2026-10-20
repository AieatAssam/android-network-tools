package net.aieat.netswissknife.core.network.portscan

import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException

internal data class BannerReadResult(
    val banner: String?,
    val truncated: Boolean,
    val bytesRead: Int = 0,
    val stopConditionMet: Boolean = false,
)

/** Reads a bounded banner without mistaking a short socket read for end-of-stream. */
internal object BannerReader {
    const val MAX_BYTES = 1_024

    /** [prepareRead] configures the next socket read and returns false when its budget is gone. */
    fun read(
        input: InputStream,
        maxBytes: Int = MAX_BYTES,
        stopAfterLine: Boolean = false,
        stopWhenLine: ((String) -> Boolean)? = null,
        lineSeparator: String = "",
        prepareRead: () -> Boolean = { true },
    ): BannerReadResult {
        require(maxBytes in 1..MAX_BYTES)
        val bytes = ByteArray(maxBytes)
        var total = 0
        var lastLineStart = 0
        var stopConditionMet = false
        while (total < bytes.size) {
            if (!prepareRead()) break
            val count = try {
                input.read(bytes, total, if (stopAfterLine || stopWhenLine != null) 1 else bytes.size - total)
            } catch (_: SocketTimeoutException) {
                break
            } catch (_: IOException) {
                break
            }

            when {
                count < 0 -> break
                count == 0 -> {
                    // InputStream permits a zero result for a non-empty request. Make
                    // progress with a single-byte read rather than spinning forever.
                    if (!prepareRead()) break
                    val next = try {
                        input.read()
                    } catch (_: SocketTimeoutException) {
                        break
                    } catch (_: IOException) {
                        break
                    }
                    if (next < 0) break
                    bytes[total++] = next.toByte()
                }
                else -> total += count
            }
            if ((stopAfterLine || stopWhenLine != null) && total > 0 && bytes[total - 1] == '\n'.code.toByte()) {
                val line = String(bytes, lastLineStart, total - lastLineStart)
                    .removeSuffix("\n")
                    .removeSuffix("\r")
                lastLineStart = total
                if (stopAfterLine || stopWhenLine?.invoke(line) == true) {
                    stopConditionMet = true
                    break
                }
            }
        }

        val rawText = String(bytes, 0, total).let { raw ->
            if (lineSeparator.isEmpty()) raw else raw.replace("\r\n", lineSeparator).replace("\n", lineSeparator)
        }
        val sanitized = BannerSanitizer.sanitizeWithTruncation(rawText)
        return BannerReadResult(
            banner = sanitized.text.takeIf(String::isNotEmpty),
            truncated = total == bytes.size || sanitized.truncated,
            bytesRead = total,
            stopConditionMet = stopConditionMet,
        )
    }
}
