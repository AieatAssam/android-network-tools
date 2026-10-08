package net.aieat.netswissknife.core.network.httprobe

import okhttp3.ResponseHeaderLimitException
import okhttp3.ResponseHeaderLimitKind
import okhttp3.internal.http1.HeadersReader
import okhttp3.internal.http2.Header
import okhttp3.internal.http2.Hpack
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResponseHeaderParserLimitTest {
    @Test
    fun `HTTP1 parser accepts exactly 100 occurrences and rejects the 101st before returning headers`() {
        val accepted = readHttp1Headers(List(100) { "X-Test: value" })
        assertEquals(100, accepted.size)

        val exception = assertThrows(ResponseHeaderLimitException::class.java) {
            readHttp1Headers(List(101) { "X-Test: value" })
        }
        assertEquals(ResponseHeaderLimitKind.FIELD_COUNT, exception.kind)
    }

    @Test
    fun `HTTP1 parser enforces exact UTF8 value and aggregate boundaries`() {
        val exactUtf8Value = "é".repeat(8_192)
        assertEquals(16_384, exactUtf8Value.toByteArray(Charsets.UTF_8).size)
        assertEquals(exactUtf8Value, readHttp1Headers(listOf("X-Test: $exactUtf8Value"))["X-Test"])

        val valueException = assertThrows(ResponseHeaderLimitException::class.java) {
            readHttp1Headers(listOf("X-Test: ${"a".repeat(16_385)}"))
        }
        assertEquals(ResponseHeaderLimitKind.VALUE_BYTES, valueException.kind)

        val overUtf8Value = "é".repeat(8_192) + "a" // 16,385 bytes in only 8,193 UTF-16 code units.
        assertEquals(16_385, overUtf8Value.toByteArray(Charsets.UTF_8).size)
        val overUtf8Exception = assertThrows(ResponseHeaderLimitException::class.java) {
            readHttp1Headers(listOf("X-Test: $overUtf8Value"))
        }
        assertEquals(ResponseHeaderLimitKind.VALUE_BYTES, overUtf8Exception.kind)

        val exactAggregate =
            List(3) { "x: ${"a".repeat(16_379)}" } +
                "x: ${"é".repeat(8_000)}${"a".repeat(379)}"
        assertEquals(65_536, exactAggregate.sumOf { 1 + it.substringAfter(": ").toByteArray(Charsets.UTF_8).size + 4 })
        assertEquals(4, readHttp1Headers(exactAggregate).size)

        val aggregateException = assertThrows(ResponseHeaderLimitException::class.java) {
            readHttp1Headers(exactAggregate.dropLast(1) + "x: ${"é".repeat(8_000)}${"a".repeat(380)}")
        }
        assertEquals(ResponseHeaderLimitKind.AGGREGATE_BYTES, aggregateException.kind)
    }

    @Test
    fun `HPACK parser enforces decoded limits before returning the field block`() {
        val hundredFields = List(100) { Header("x-test", "value") }
        assertEquals(hundredFields, readHpackHeaders(hundredFields))

        val countException = assertThrows(ResponseHeaderLimitException::class.java) {
            readHpackHeaders(List(101) { Header("x-test", "value") })
        }
        assertEquals(ResponseHeaderLimitKind.FIELD_COUNT, countException.kind)

        val exactUtf8Value = "é".repeat(8_192)
        assertEquals(16_384, exactUtf8Value.toByteArray(Charsets.UTF_8).size)
        assertEquals(listOf(Header("x-test", exactUtf8Value)), readHpackHeaders(listOf(Header("x-test", exactUtf8Value))))

        val valueException = assertThrows(ResponseHeaderLimitException::class.java) {
            readHpackHeaders(listOf(Header("x-test", "a".repeat(16_385))))
        }
        assertEquals(ResponseHeaderLimitKind.VALUE_BYTES, valueException.kind)

        val overUtf8Value = "é".repeat(8_192) + "a"
        assertEquals(16_385, overUtf8Value.toByteArray(Charsets.UTF_8).size)
        val overUtf8Exception = assertThrows(ResponseHeaderLimitException::class.java) {
            readHpackHeaders(listOf(Header("x-test", overUtf8Value)))
        }
        assertEquals(ResponseHeaderLimitKind.VALUE_BYTES, overUtf8Exception.kind)

        val exactAggregate =
            List(3) { Header("x", "a".repeat(16_379)) } +
                Header("x", "é".repeat(8_000) + "a".repeat(379))
        assertEquals(65_536, exactAggregate.sumOf { it.name.size + it.value.size + 4 })
        assertEquals(exactAggregate, readHpackHeaders(exactAggregate))

        val aggregateException = assertThrows(ResponseHeaderLimitException::class.java) {
            readHpackHeaders(exactAggregate.dropLast(1) + Header("x", "é".repeat(8_000) + "a".repeat(380)))
        }
        assertEquals(ResponseHeaderLimitKind.AGGREGATE_BYTES, aggregateException.kind)
    }

    @Test
    fun `HPACK parser excludes status pseudo field from the HTTP response field budget`() {
        val fields =
            listOf(
                Header(":status", "200"),
                Header(":method", "GET"),
                Header(":path", "/"),
                Header(":scheme", "https"),
                Header(":authority", "example.test"),
            ) + List(100) { Header("x-test", "value") }
        val decoded = readHpackHeaders(fields)
        assertEquals(105, decoded.size)
        assertTrue(decoded.first().name.utf8() == ":status")

        val exception = assertThrows(ResponseHeaderLimitException::class.java) {
            readHpackHeaders(List(6) { Header(":status", "200") })
        }
        assertEquals(ResponseHeaderLimitKind.PSEUDO_FIELD_COUNT, exception.kind)
        assertEquals(5, exception.maximum)
    }

    @Test
    fun `HPACK parser bounds pseudo fields cumulatively across informational blocks`() {
        val encoded = Buffer()
        val writer = Hpack.Writer(out = encoded)
        val reader = Hpack.Reader(source = encoded, headerTableSizeSetting = 4_096)
        repeat(100) {
            writer.writeHeaders(listOf(Header(":status", "103")))
            reader.readHeaders(streamId = 1)
            assertEquals(listOf(Header(":status", "103")), reader.getAndResetHeaderList(streamId = 1, endStream = false))
        }

        val exception = assertThrows(ResponseHeaderLimitException::class.java) {
            writer.writeHeaders(listOf(Header(":status", "103")))
            reader.readHeaders(streamId = 1)
        }
        assertEquals(ResponseHeaderLimitKind.PSEUDO_FIELD_COUNT, exception.kind)
        assertEquals(100, exception.maximum)
    }

    private fun readHttp1Headers(lines: List<String>): okhttp3.Headers {
        val source = Buffer().apply {
            lines.forEach { writeUtf8(it).writeUtf8("\r\n") }
            writeUtf8("\r\n")
        }
        return HeadersReader(source).readHeaders()
    }

    private fun readHpackHeaders(headers: List<Header>): List<Header> {
        val encoded = Buffer()
        Hpack.Writer(out = encoded).writeHeaders(headers)
        val reader = Hpack.Reader(source = encoded, headerTableSizeSetting = 4_096)
        reader.readHeaders(streamId = 1)
        return reader.getAndResetHeaderList(streamId = 1, endStream = true)
    }
}
