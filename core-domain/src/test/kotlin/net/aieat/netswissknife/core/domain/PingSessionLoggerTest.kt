package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.ping.PingPacketResult
import net.aieat.netswissknife.core.network.ping.PingStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

@DisplayName("PingSessionLogger")
class PingSessionLoggerTest {

    private lateinit var file: File
    private lateinit var logger: PingSessionLogger

    @BeforeEach
    fun setUp() {
        file = File.createTempFile("ping_test_", ".csv")
        file.deleteOnExit()
        logger = PingSessionLogger(file)
    }

    @Nested
    @DisplayName("init")
    inner class Init {

        @Test
        fun `init writes CSV header`() {
            logger.init()
            val lines = file.readLines()
            assertEquals("seq,timestamp_ms,latency_ms,status", lines[0])
        }

        @Test
        fun `init overwrites any existing content`() {
            file.writeText("stale data\n")
            logger.init()
            val lines = file.readLines()
            assertEquals(1, lines.size)
            assertEquals("seq,timestamp_ms,latency_ms,status", lines[0])
        }
    }

    @Nested
    @DisplayName("append")
    inner class Append {

        @Test
        fun `success packet writes ok status`() {
            logger.init()
            logger.append(1, PingPacketResult(1, "8.8.8.8", 15L, PingStatus.SUCCESS))
            val dataLine = file.readLines()[1]
            assertTrue(dataLine.endsWith(",ok"), "Expected line to end with ',ok' but was: $dataLine")
        }

        @Test
        fun `timeout packet writes timeout status and empty latency`() {
            logger.init()
            logger.append(2, PingPacketResult(2, "8.8.8.8", null, PingStatus.TIMEOUT))
            val dataLine = file.readLines()[1]
            assertTrue(dataLine.endsWith(",timeout"), "Expected ',timeout' but was: $dataLine")
            val cols = dataLine.split(",")
            assertEquals("", cols[2], "latency should be empty for timeout")
        }

        @Test
        fun `error packet writes error status`() {
            logger.init()
            logger.append(3, PingPacketResult(3, "bad", null, PingStatus.ERROR, "unknown host"))
            val dataLine = file.readLines()[1]
            assertTrue(dataLine.endsWith(",error"), "Expected ',error' but was: $dataLine")
        }

        @Test
        fun `seq number is written in first column`() {
            logger.init()
            logger.append(42, PingPacketResult(42, "8.8.8.8", 10L, PingStatus.SUCCESS))
            val dataLine = file.readLines()[1]
            assertEquals("42", dataLine.split(",")[0])
        }

        @Test
        fun `latency is written for success packets`() {
            logger.init()
            logger.append(1, PingPacketResult(1, "8.8.8.8", 18L, PingStatus.SUCCESS))
            val dataLine = file.readLines()[1]
            assertEquals("18", dataLine.split(",")[2])
        }

        @Test
        fun `multiple appends produce one row each`() {
            logger.init()
            repeat(5) { i ->
                logger.append(i + 1, PingPacketResult(i + 1, "8.8.8.8", 10L, PingStatus.SUCCESS))
            }
            val lines = file.readLines().filter { it.isNotBlank() }
            assertEquals(6, lines.size) // header + 5 data rows
        }
    }
}
