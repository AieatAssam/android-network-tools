package net.aieat.netswissknife.core.network.portscan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException

class BannerReaderTest {
    @Test
    fun `short banner reads through partial reads until eof without marking truncated`() {
        val input = PartialReadInputStream("SSH-2.0-OpenSSH_9.0".toByteArray(), maxChunk = 3)

        val result = BannerReader.read(input)

        assertEquals("SSH-2.0-OpenSSH_9.0", result.banner)
        assertFalse(result.truncated)
    }

    @Test
    fun `banner reaching byte cap is marked truncated and sanitized`() {
        val input = ByteArrayInputStream(ByteArray(BannerReader.MAX_BYTES) { 'A'.code.toByte() })

        val result = BannerReader.read(input)

        assertEquals("A".repeat(200), result.banner)
        assertTrue(result.truncated)
    }

    @Test
    fun `timeout preserves partial banner without claiming byte cap truncation`() {
        val input = object : InputStream() {
            private var readOnce = false

            override fun read(): Int = error("read(byte[], ...) should be used")

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (!readOnce) {
                    readOnce = true
                    "HTTP/1.0".toByteArray().copyInto(bytes, offset)
                    return 8
                }
                throw SocketTimeoutException("read budget elapsed")
            }
        }

        val result = BannerReader.read(input)

        assertEquals("HTTP/1.0", result.banner)
        assertFalse(result.truncated)
    }

    @Test
    fun `read failure preserves bytes received before the failure`() {
        val input = object : InputStream() {
            private var readOnce = false

            override fun read(): Int = error("read(byte[], ...) should be used")

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (!readOnce) {
                    readOnce = true
                    "SSH-2.0".toByteArray().copyInto(bytes, offset)
                    return 7
                }
                throw IOException("connection reset")
            }
        }

        val result = BannerReader.read(input)

        assertEquals("SSH-2.0", result.banner)
        assertFalse(result.truncated)
    }

    private class PartialReadInputStream(
        private val content: ByteArray,
        private val maxChunk: Int,
    ) : ByteArrayInputStream(content) {
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
            super.read(bytes, offset, minOf(length, maxChunk))
    }
}
