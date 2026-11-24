package net.aieat.netswissknife.app.ui.screens.topology

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

class TopologyCanvasGeometryTest {
    private val diameter = 2 * TopologyCanvasGeometry.MAX_NODE_RADIUS_DP

    private fun star(n: Int): Pair<String, List<String>> {
        val root = "10.0.0.1"
        return root to listOf(root) + (2..n + 1).map { "10.0.0.$it" }
    }

    private fun edgesFromRoot(root: String, ips: List<String>): List<Pair<String, String>> =
        ips.filter { it != root }.map { root to it }

    private fun distance(a: Offset, b: Offset): Float = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

    private fun assertNoOverlap(positions: Map<String, Offset>) {
        val entries = positions.entries.toList()
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val d = distance(entries[i].value, entries[j].value)
                assertTrue(d >= diameter, "nodes ${entries[i].key} and ${entries[j].key} are $d dp apart")
            }
        }
    }

    @Test
    fun `root sits at the centre`() {
        val (root, ips) = star(5)
        val layout = TopologyCanvasGeometry.layout(root, ips, edgesFromRoot(root, ips))
        assertEquals(Offset.Zero, layout.positions.getValue(root))
    }

    @Test
    fun `nodes on one ring are evenly spaced for every ring size up to 48`() {
        for (n in listOf(1, 2, 3, 5, 8, 17, 32, 48)) {
            val (root, ips) = star(n)
            val layout = TopologyCanvasGeometry.layout(root, ips, edgesFromRoot(root, ips))
            val angles = ips.filter { it != root }
                .map { ip -> layout.positions.getValue(ip).let { atan2(it.y, it.x).toDouble() } }
                .map { if (it < 0) it + 2 * PI else it }
                .sorted()
            if (n < 2) continue
            val expectedStep = 2 * PI / n
            for (i in 1 until angles.size) {
                assertEquals(expectedStep, angles[i] - angles[i - 1], 1e-3, "ring of $n, gap $i")
            }
            val wrap = angles.first() + 2 * PI - angles.last()
            assertEquals(expectedStep, wrap, 1e-3, "ring of $n, wrap-around gap")
        }
    }

    @Test
    fun `no two nodes are closer than a node diameter for up to 48 nodes`() {
        for (n in listOf(2, 6, 12, 24, 48)) {
            val (root, ips) = star(n)
            assertNoOverlap(TopologyCanvasGeometry.layout(root, ips, edgesFromRoot(root, ips)).positions)
        }
    }

    @Test
    fun `multi-hop layout keeps every pair apart and rings grow outward`() {
        val root = "10.0.0.1"
        val children = (2..7).map { "10.1.0.$it" }
        val grandchildren = children.flatMapIndexed { i, child -> (1..6).map { "10.2.$i.$it" } }
        val ips = listOf(root) + children + grandchildren
        val edges = children.map { root to it } + grandchildren.mapIndexed { idx, gc -> children[idx / 6] to gc }
        val layout = TopologyCanvasGeometry.layout(root, ips, edges)

        assertNoOverlap(layout.positions)
        val childRadius = distance(layout.positions.getValue(children.first()), Offset.Zero)
        val grandchildRadius = distance(layout.positions.getValue(grandchildren.first()), Offset.Zero)
        assertTrue(grandchildRadius > childRadius)
    }

    @Test
    fun `unreachable nodes are placed on an outer ring`() {
        val root = "10.0.0.1"
        val ips = listOf(root, "10.0.0.2", "10.0.0.3", "10.9.9.9")
        val layout = TopologyCanvasGeometry.layout(root, ips, listOf(root to "10.0.0.2", root to "10.0.0.3"))
        assertEquals(4, layout.positions.size)
        val far = distance(layout.positions.getValue("10.9.9.9"), Offset.Zero)
        val near = distance(layout.positions.getValue("10.0.0.2"), Offset.Zero)
        assertTrue(far > near)
    }

    @Test
    fun `adding a node to an outer ring leaves inner rings where they were`() {
        val root = "10.0.0.1"
        val first = listOf(root, "10.0.0.2", "10.0.0.3", "10.0.0.4")
        val before = TopologyCanvasGeometry.layout(root, first, edgesFromRoot(root, first)).positions
        // An address with no path to the seed lands on the outer ring, so the inner rings must not move.
        val second = first + "10.9.0.1"
        val after = TopologyCanvasGeometry.layout(root, second, edgesFromRoot(root, first)).positions
        assertEquals(before.getValue(root), after.getValue(root))
        first.forEach { assertEquals(before.getValue(it), after.getValue(it)) }
    }

    @Test
    fun `layout does not depend on arrival order`() {
        val root = "10.0.0.1"
        val ips = listOf(root, "10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5")
        val forward = TopologyCanvasGeometry.layout(root, ips, edgesFromRoot(root, ips)).positions
        val reversed = TopologyCanvasGeometry.layout(root, ips.reversed(), edgesFromRoot(root, ips).reversed()).positions
        assertEquals(forward, reversed)
    }

    @Test
    fun `extent covers the farthest node edge`() {
        val (root, ips) = star(6)
        val layout = TopologyCanvasGeometry.layout(root, ips, edgesFromRoot(root, ips))
        val farthest = layout.positions.values.maxOf { distance(it, Offset.Zero) }
        assertTrue(layout.extentDp >= farthest + TopologyCanvasGeometry.MAX_NODE_RADIUS_DP - 1e-3f)
    }

    @Test
    fun `nearest node within its hit radius wins`() {
        val candidates = listOf(
            "10.0.0.1" to Offset(0f, 0f),
            "10.0.0.2" to Offset(10f, 0f),
        )
        val hit = TopologyCanvasGeometry.nearestNodeWithin(Offset(7f, 0f), candidates) { 30f }
        assertEquals("10.0.0.2", hit)
    }

    @Test
    fun `tap outside every hit radius selects nothing`() {
        val candidates = listOf("10.0.0.1" to Offset(0f, 0f))
        assertNull(TopologyCanvasGeometry.nearestNodeWithin(Offset(100f, 0f), candidates) { 24f })
    }

    @Test
    fun `fit scale keeps the graph inside the viewport and never zooms in`() {
        assertEquals(1f, TopologyCanvasGeometry.fitScale(extentPx = 0f, viewportPx = 800f, marginPx = 24f))
        assertEquals(1f, TopologyCanvasGeometry.fitScale(extentPx = 100f, viewportPx = 800f, marginPx = 24f))
        val scale = TopologyCanvasGeometry.fitScale(extentPx = 900f, viewportPx = 800f, marginPx = 24f)
        assertTrue(scale < 1f)
        assertTrue(900f * scale + 24f <= 400f + 1e-3f)
        assertEquals(TopologyCanvasGeometry.MIN_ZOOM, TopologyCanvasGeometry.fitScale(9000f, 800f, 24f))
    }
}
