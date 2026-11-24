package net.aieat.netswissknife.app.ui.screens.topology

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Node centres in dp relative to the seed at (0, 0), and the farthest centre distance in dp. */
internal data class TopologyLayout(
    val positions: Map<String, Offset>,
    val extentDp: Float,
)

/**
 * Pure layout and hit-testing for the topology canvas. Units are dp unless a name says px.
 *
 * Nodes sit on concentric rings by hop distance from the seed. Each ring is spaced so that
 * neighbouring nodes keep [RING_SPACING_DP] between their edges, which holds for every node
 * radius up to [MAX_NODE_RADIUS_DP]. Ring members are ordered by address, so a node's position
 * changes only when another node joins its own ring.
 */
internal object TopologyCanvasGeometry {
    const val MAX_NODE_RADIUS_DP = 28f
    const val REPORTED_NODE_RADIUS_DP = 14f
    const val MIN_HIT_RADIUS_DP = 24f
    const val MIN_ZOOM = 0.3f

    private const val RING_GAP_DP = 16f
    private const val RING_SPACING_DP = 2 * MAX_NODE_RADIUS_DP + RING_GAP_DP

    /**
     * Places [ips] by hop distance from [rootIp] over the undirected [edges]. Nodes with no path
     * to the root go on the ring after the last reachable hop. Edges that name an address not in
     * [ips] are ignored.
     */
    fun layout(
        rootIp: String?,
        ips: List<String>,
        edges: List<Pair<String, String>>,
    ): TopologyLayout {
        val known = ips.toSet()
        if (known.isEmpty()) return TopologyLayout(emptyMap(), 0f)

        val adjacency = HashMap<String, MutableList<String>>()
        for ((a, b) in edges) {
            if (a == b || a !in known || b !in known) continue
            adjacency.getOrPut(a) { mutableListOf() }.add(b)
            adjacency.getOrPut(b) { mutableListOf() }.add(a)
        }

        val hops = HashMap<String, Int>()
        if (rootIp != null && rootIp in known) {
            hops[rootIp] = 0
            val queue = ArrayDeque<String>()
            queue.addLast(rootIp)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val nextHop = hops.getValue(current) + 1
                for (next in adjacency[current].orEmpty()) {
                    if (next !in hops) {
                        hops[next] = nextHop
                        queue.addLast(next)
                    }
                }
            }
        }
        val unreachedRing = (hops.values.maxOrNull() ?: 0) + 1
        val rings = known.groupBy { hops[it] ?: unreachedRing }.toSortedMap()

        val positions = HashMap<String, Offset>()
        var previousRadius = 0f
        for ((ring, members) in rings) {
            val ordered = members.sortedWith { a, b -> compareAddresses(a, b) }
            if (ring == 0) {
                ordered.forEach { positions[it] = Offset.Zero }
                continue
            }
            val count = ordered.size
            val chordRadius = if (count >= 2) RING_SPACING_DP / (2f * sin(PI / count).toFloat()) else 0f
            val radius = max(chordRadius, previousRadius + RING_SPACING_DP)
            ordered.forEachIndexed { index, ip ->
                val angle = 2.0 * PI * index / count - PI / 2
                positions[ip] = Offset((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
            }
            previousRadius = radius
        }

        val extent = positions.values.maxOfOrNull { sqrt(it.x * it.x + it.y * it.y) } ?: 0f
        return TopologyLayout(positions, extent + MAX_NODE_RADIUS_DP)
    }

    /**
     * Zoom that fits a graph of [extentPx] radius (from the seed to the farthest node edge)
     * inside a square viewport of [viewportPx] with [marginPx] kept clear at each edge.
     * Never zooms in past 1 so small graphs keep their natural size.
     */
    fun fitScale(
        extentPx: Float,
        viewportPx: Float,
        marginPx: Float,
    ): Float {
        if (extentPx <= 0f || viewportPx <= 0f) return 1f
        val available = viewportPx / 2f - marginPx
        return (available / extentPx).coerceIn(MIN_ZOOM, 1f)
    }

    /**
     * The nearest candidate whose hit radius contains [tap], or null. [candidates] pair each
     * node address with its centre in the same coordinate space as [tap].
     */
    fun nearestNodeWithin(
        tap: Offset,
        candidates: List<Pair<String, Offset>>,
        radiusOf: (String) -> Float,
    ): String? {
        var best: String? = null
        var bestDistance = Float.MAX_VALUE
        for ((ip, center) in candidates) {
            val distance = (tap - center).getDistance()
            if (distance <= radiusOf(ip) && distance < bestDistance) {
                best = ip
                bestDistance = distance
            }
        }
        return best
    }

    /** Dotted-quad addresses compare octet by octet so 10.0.0.9 sorts before 10.0.0.10. */
    private fun compareAddresses(
        a: String,
        b: String,
    ): Int {
        val partsA = a.split('.')
        val partsB = b.split('.')
        for (i in 0 until min(partsA.size, partsB.size)) {
            val x = partsA[i].toIntOrNull()
            val y = partsB[i].toIntOrNull()
            val result = if (x != null && y != null) x.compareTo(y) else partsA[i].compareTo(partsB[i])
            if (result != 0) return result
        }
        return a.compareTo(b)
    }
}
