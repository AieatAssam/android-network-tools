package net.aieat.netswissknife.app.ui.screens.traceroute

import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Traceroute summary")
class TraceSummaryTest {
    private fun reply(
        number: Int,
        rtt: Long = 10L + number,
        destination: Boolean = false,
    ) = HopResult(
        hopNumber = number,
        ip = "198.51.100.$number",
        hostname = null,
        rtTimeMs = rtt,
        status = HopStatus.SUCCESS,
        destinationReached = destination,
    )

    private fun silent(number: Int) = HopResult(number, null, null, null, HopStatus.TIMEOUT)

    /** The trace seen on a device: hops 1 to 17 mostly answered, then 13 hops of silence. */
    private val farHost =
        (1..17).map { if (it == 6 || it == 7) silent(it) else reply(it) } + (18..30).map(::silent)

    @Nested
    @DisplayName("hop list rows")
    inner class Rows {
        @Test
        fun `answered hops are one row each, in hop order`() {
            val rows = hopListRows(listOf(reply(2), reply(1)), finished = true)

            assertEquals(listOf(HopListRow.Hop(reply(1)), HopListRow.Hop(reply(2))), rows)
        }

        @Test
        fun `a single unanswered hop keeps its own row`() {
            val rows = hopListRows(listOf(reply(1), silent(2), reply(3)), finished = true)

            assertEquals(listOf(1, 2, 3), rows.map { (it as HopListRow.Hop).hop.hopNumber })
        }

        @Test
        fun `consecutive unanswered hops collapse into one row`() {
            val rows = hopListRows(listOf(reply(1), silent(2), silent(3), silent(4), reply(5)), finished = true)

            assertEquals(
                listOf(
                    HopListRow.Hop(reply(1)),
                    HopListRow.NoReplyRun(firstHop = 2, lastHop = 4, afterLastReply = false),
                    HopListRow.Hop(reply(5)),
                ),
                rows,
            )
        }

        @Test
        fun `silence after the last reply of a finished trace is one row marked as the end of replies`() {
            val rows = hopListRows(farHost, finished = true)

            assertEquals(HopListRow.NoReplyRun(firstHop = 18, lastHop = 30, afterLastReply = true), rows.last())
            assertEquals(HopListRow.NoReplyRun(firstHop = 6, lastHop = 7, afterLastReply = false), rows[5])
            assertEquals(17, rows.size)
        }

        @Test
        fun `while a trace runs, the latest silence may still be followed by replies`() {
            val rows = hopListRows(listOf(reply(1), silent(2), silent(3)), finished = false)

            assertEquals(HopListRow.NoReplyRun(firstHop = 2, lastHop = 3, afterLastReply = false), rows.last())
        }

        @Test
        fun `row keys stay the same as a run grows`() {
            val before = hopListRows(listOf(reply(1), silent(2), silent(3)), finished = false)
            val after = hopListRows(listOf(reply(1), silent(2), silent(3), silent(4)), finished = false)

            assertEquals(before.map { it.key }, after.map { it.key })
            assertEquals(3, (after.last() as HopListRow.NoReplyRun).count)
        }

        @Test
        fun `a trace where nothing answered is one row`() {
            val rows = hopListRows((1..5).map(::silent), finished = true)

            assertEquals(listOf(HopListRow.NoReplyRun(firstHop = 1, lastHop = 5, afterLastReply = true)), rows)
        }
    }

    @Nested
    @DisplayName("summary numbers")
    inner class Numbers {
        @Test
        fun `counts say how many probed hops replied, without averaging RTTs across hops`() {
            val summary = TraceSummary.of(farHost)

            assertEquals(30, summary.probedHops)
            assertEquals(15, summary.repliedHops)
            assertEquals(17, summary.lastReply?.hopNumber)
            assertEquals(18..30, summary.silentAfterLastReply)
            assertNull(summary.destination)
        }

        @Test
        fun `the RTT shown is the destination's when it answered`() {
            val hops = listOf(reply(1, rtt = 3L), reply(2, rtt = 40L, destination = true))

            val summary = TraceSummary.of(hops)

            assertEquals(2, summary.destination?.hopNumber)
            assertEquals(2, summary.lastReply?.hopNumber)
            assertNull(summary.silentAfterLastReply)
            assertEquals(40L, summary.lastReply?.rtTimeMs)
        }

        @Test
        fun `a trace whose last hop answered has no silent tail`() {
            val summary = TraceSummary.of(listOf(reply(1), silent(2), reply(3)))

            assertEquals(2, summary.repliedHops)
            assertNull(summary.silentAfterLastReply)
        }

        @Test
        fun `a trace where nothing answered is all silence`() {
            val summary = TraceSummary.of((1..4).map(::silent))

            assertEquals(0, summary.repliedHops)
            assertNull(summary.lastReply)
            assertEquals(1..4, summary.silentAfterLastReply)
        }

        @Test
        fun `an empty trace has nothing to report`() {
            val summary = TraceSummary.of(emptyList())

            assertEquals(0, summary.probedHops)
            assertNull(summary.silentAfterLastReply)
        }
    }
}
