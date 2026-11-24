package net.aieat.netswissknife.app.ui.screens.traceroute

import net.aieat.netswissknife.core.network.traceroute.HopResult

/** A row of the hop list: one answered hop, a single silent hop, or a run of silent hops shown as one row. */
sealed interface HopListRow {
    /** Stable across updates of a running trace, for use as a list key. */
    val key: String

    data class Hop(
        val hop: HopResult,
    ) : HopListRow {
        override val key: String get() = "hop-${hop.hopNumber}"
    }

    /**
     * Hops [firstHop] to [lastHop] sent no reply. [afterLastReply] marks silence after the last hop that
     * answered in a finished trace: those TTLs were probed, but nothing shows they are part of the route.
     */
    data class NoReplyRun(
        val firstHop: Int,
        val lastHop: Int,
        val afterLastReply: Boolean,
    ) : HopListRow {
        override val key: String get() = "silent-$firstHop"
        val count: Int get() = lastHop - firstHop + 1
    }
}

/** The rows for [hops]: two or more consecutive hops without a reply become one [HopListRow.NoReplyRun]. */
fun hopListRows(
    hops: List<HopResult>,
    finished: Boolean,
): List<HopListRow> {
    val ordered = hops.sortedBy { it.hopNumber }
    val lastReply = ordered.lastOrNull { it.ip != null }?.hopNumber ?: 0
    val rows = mutableListOf<HopListRow>()
    var silent = mutableListOf<HopResult>()

    fun flushSilent() {
        when {
            silent.isEmpty() -> {
                Unit
            }

            silent.size == 1 && !(finished && silent.first().hopNumber > lastReply) -> {
                rows += HopListRow.Hop(silent.first())
            }

            else -> {
                rows +=
                    HopListRow.NoReplyRun(
                        firstHop = silent.first().hopNumber,
                        lastHop = silent.last().hopNumber,
                        afterLastReply = finished && silent.first().hopNumber > lastReply,
                    )
            }
        }
        silent = mutableListOf()
    }

    ordered.forEach { hop ->
        if (hop.ip == null) {
            silent += hop
        } else {
            flushSilent()
            rows += HopListRow.Hop(hop)
        }
    }
    flushSilent()
    return rows
}

/** Plain counts for the summary card. Round-trip times are never averaged across hops. */
data class TraceSummary(
    /** TTLs probed: the destination's TTL when it answered. */
    val probedHops: Int,
    val repliedHops: Int,
    /** The last hop that answered; the destination when it was reached. */
    val lastReply: HopResult?,
    val destination: HopResult?,
    /** Probed TTLs after the last reply, or null when the last probed hop answered. */
    val silentAfterLastReply: IntRange?,
) {
    companion object {
        fun of(hops: List<HopResult>): TraceSummary {
            val probed = hops.maxOfOrNull { it.hopNumber } ?: 0
            val lastReply = hops.filter { it.ip != null }.maxByOrNull { it.hopNumber }
            val silentFrom = (lastReply?.hopNumber ?: 0) + 1
            return TraceSummary(
                probedHops = probed,
                repliedHops = hops.count { it.ip != null },
                lastReply = lastReply,
                destination = hops.firstOrNull { it.destinationReached },
                silentAfterLastReply = (silentFrom..probed).takeUnless { it.isEmpty() },
            )
        }
    }
}
