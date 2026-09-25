package net.aieat.netswissknife.core.network.portscan

import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException

internal data class BannerReadResult(
    val banner: String?,
    val truncated: Boolean,
)

/** Reads a bounded banner without mistaking a short socket read for end-of-stream. */
internal object BannerReader {
    const val MAX_BYTES = 1_024

    /** [prepareRead] configures the next socket read and returns false when its budget is gone. */
    fun read(input: InputStream, prepareRead: () -> Boolean = { true }): BannerReadResult {
        val bytes = ByteArray(MAX_BYTES)
        var total = 0
        while (total < bytes.size) {
            if (!prepareRead()) break
            val count = try {
                input.read(bytes, total, bytes.size - total)
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
        }

        val sanitized = BannerSanitizer.sanitizeWithTruncation(String(bytes, 0, total))
        return BannerReadResult(
            banner = sanitized.text.takeIf(String::isNotEmpty),
            truncated = total == bytes.size || sanitized.truncated,
        )
    }
}
