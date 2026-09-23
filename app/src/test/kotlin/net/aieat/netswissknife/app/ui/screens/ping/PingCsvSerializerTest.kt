package net.aieat.netswissknife.app.ui.screens.ping

import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingResult
import net.aieat.netswissknife.core.network.ping.PingStats
import net.aieat.netswissknife.core.network.ping.PingStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Locale

@DisplayName("PingCsvSerializer")
class PingCsvSerializerTest {

    @Test
    fun `fractional statistics stay seven locale-neutral columns`() {
        val originalLocale = Locale.getDefault()
        try {
            val outputs = listOf(Locale.US, Locale.GERMANY, Locale.FRANCE).map { locale ->
                Locale.setDefault(locale)
                val csv = PingCsvSerializer.serialize(resultWithFractionalStats())
                val rows = parseCsv(csv)
                val statsHeaderIndex = rows.indexOf(
                    listOf("sent", "received", "loss_percent", "min_ms", "avg_ms", "max_ms", "jitter_ms")
                )
                assertTrue(statsHeaderIndex >= 0, "statistics header should be present for $locale")
                val statsRow = rows[statsHeaderIndex + 1]
                assertEquals(7, statsRow.size)
                assertEquals(listOf("8", "7", "12.5", "1", "3.125", "9", "0.125"), statsRow)
                csv
            }

            assertEquals(outputs.first(), outputs[1])
            assertEquals(outputs.first(), outputs[2])
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `packet text is quoted and null packet fields remain empty columns`() {
        val rows = parseCsv(PingCsvSerializer.serialize(resultWithFractionalStats()))
        val packetRow = rows[1]

        assertEquals(7, packetRow.size)
        assertEquals("host,\"primary\"\r\nnext", packetRow[1])
        assertEquals("", packetRow[3])
        assertEquals("timeout, \"read failed\"\nretry", packetRow[4])
        assertEquals("", packetRow[5])
        assertEquals("", packetRow[6])

        val nullErrorRow = rows[2]
        assertEquals(7, nullErrorRow.size)
        assertEquals("", nullErrorRow[4])
    }

    @Test
    fun `non-finite statistics use explicit locale-neutral tokens`() {
        val result = resultWithFractionalStats().copy(
            stats = PingStats(
                sent = 1,
                received = 1,
                lossPercent = Float.NaN,
                minMs = 0,
                maxMs = 0,
                avgMs = Double.POSITIVE_INFINITY,
                jitterMs = Double.NEGATIVE_INFINITY
            )
        )

        val rows = parseCsv(PingCsvSerializer.serialize(result))
        val statsHeaderIndex = rows.indexOf(
            listOf("sent", "received", "loss_percent", "min_ms", "avg_ms", "max_ms", "jitter_ms")
        )

        assertEquals(7, rows[statsHeaderIndex + 1].size)
        assertEquals(listOf("1", "1", "NaN", "0", "Infinity", "0", "-Infinity"), rows[statsHeaderIndex + 1])
    }

    private fun resultWithFractionalStats() = PingResult(
        host = "example.com",
        packets = listOf(
            PingPacketResult(
                sequence = 1,
                host = "host,\"primary\"\r\nnext",
                rtTimeMs = null,
                status = PingStatus.ERROR,
                errorMessage = "timeout, \"read failed\"\nretry",
                replyTtl = null,
                bytes = null
            ),
            PingPacketResult(
                sequence = 2,
                host = "example.com",
                rtTimeMs = 9,
                status = PingStatus.SUCCESS,
                errorMessage = null,
                replyTtl = 64,
                bytes = 56
            )
        ),
        stats = PingStats(
            sent = 8,
            received = 7,
            lossPercent = 12.5f,
            minMs = 1,
            maxMs = 9,
            avgMs = 3.125,
            jitterMs = 0.125
        ),
        rawOutput = ""
    )

    /** Minimal RFC 4180 row parser for validating the serializer's CSV field boundaries. */
    private fun parseCsv(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0

        fun finishField() {
            row += field.toString()
            field.clear()
        }

        fun finishRow() {
            finishField()
            rows += row.toList()
            row.clear()
        }

        while (index < csv.length) {
            val char = csv[index]
            if (quoted) {
                if (char == '"') {
                    if (index + 1 < csv.length && csv[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(char)
                }
            } else {
                when (char) {
                    '"' -> {
                        check(field.isEmpty()) { "quoted field must start at the beginning of a field" }
                        quoted = true
                    }
                    ',' -> finishField()
                    '\n' -> finishRow()
                    '\r' -> {
                        finishRow()
                        if (index + 1 < csv.length && csv[index + 1] == '\n') index++
                    }
                    else -> field.append(char)
                }
            }
            index++
        }

        check(!quoted) { "unterminated quoted field" }
        if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rows
    }
}
