package net.aieat.netswissknife.app.ui.screens.whois

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("RelayChainGeometry")
class RelayChainGeometryTest {

    private val tolerance = 0.001f

    @Nested
    @DisplayName("nodeCenterX")
    inner class NodeCenterX {

        @Test
        @DisplayName("centres a single node in the middle of the canvas")
        fun singleNode() {
            assertEquals(50f, RelayChainGeometry.nodeCenterX(0, 1, 100f), tolerance)
        }

        @Test
        @DisplayName("spaces node centres at cell midpoints")
        fun evenlySpaced() {
            assertEquals(25f, RelayChainGeometry.nodeCenterX(0, 4, 200f), tolerance)
            assertEquals(75f, RelayChainGeometry.nodeCenterX(1, 4, 200f), tolerance)
            assertEquals(125f, RelayChainGeometry.nodeCenterX(2, 4, 200f), tolerance)
            assertEquals(175f, RelayChainGeometry.nodeCenterX(3, 4, 200f), tolerance)
        }

        @Test
        @DisplayName("keeps the chain symmetric about the canvas centre")
        fun symmetric() {
            val width = 330f
            val n = 5
            val first = RelayChainGeometry.nodeCenterX(0, n, width)
            val last = RelayChainGeometry.nodeCenterX(n - 1, n, width)
            assertEquals(width, first + last, tolerance)
        }

        @Test
        @DisplayName("returns zero centres for a zero-width canvas")
        fun zeroWidth() {
            assertEquals(0f, RelayChainGeometry.nodeCenterX(2, 3, 0f), tolerance)
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1])
        @DisplayName("rejects a non-positive node count")
        fun rejectsNonPositiveCount(nodeCount: Int) {
            assertThrows(IllegalArgumentException::class.java) {
                RelayChainGeometry.nodeCenterX(0, nodeCount, 100f)
            }
        }

        @ParameterizedTest
        @ValueSource(ints = [-1, 3, 4])
        @DisplayName("rejects an out-of-bounds index")
        fun rejectsOutOfBoundsIndex(index: Int) {
            assertThrows(IllegalArgumentException::class.java) {
                RelayChainGeometry.nodeCenterX(index, 3, 100f)
            }
        }
    }

    @Nested
    @DisplayName("connectorSegments")
    inner class Segments {

        @Test
        @DisplayName("produces one fewer segment than nodes")
        fun segmentCount() {
            assertEquals(3, RelayChainGeometry.connectorSegments(4, 400f, 10f).size)
        }

        @Test
        @DisplayName("joins node edges, not node centres")
        fun joinsEdges() {
            val segments = RelayChainGeometry.connectorSegments(2, 200f, 20f)
            assertEquals(1, segments.size)
            // Centres at 50 and 150; edges at 70 and 130.
            assertEquals(70f, segments[0].startX, tolerance)
            assertEquals(130f, segments[0].endX, tolerance)
        }

        @Test
        @DisplayName("chains segments between successive node edges")
        fun contiguousChain() {
            // Cells of 100 => centres at 50, 150, 250; radius 10 trims each end.
            val segments = RelayChainGeometry.connectorSegments(3, 300f, 10f)
            assertEquals(listOf(60f, 160f), segments.map { it.startX })
            assertEquals(listOf(140f, 240f), segments.map { it.endX })
        }

        @Test
        @DisplayName("draws a full-width connector when the node radius is zero")
        fun zeroRadius() {
            val segments = RelayChainGeometry.connectorSegments(2, 200f, 0f)
            assertEquals(1, segments.size)
            assertEquals(50f, segments[0].startX, tolerance)
            assertEquals(150f, segments[0].endX, tolerance)
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 1])
        @DisplayName("returns nothing when there is no pair to join")
        fun tooFewNodes(nodeCount: Int) {
            assertTrue(RelayChainGeometry.connectorSegments(nodeCount, 200f, 10f).isEmpty())
        }

        @ParameterizedTest
        @ValueSource(floats = [0f, -100f])
        @DisplayName("returns nothing for a non-positive canvas width")
        fun noWidth(width: Float) {
            assertTrue(RelayChainGeometry.connectorSegments(3, width, 10f).isEmpty())
        }

        @Test
        @DisplayName("returns nothing for a negative node radius")
        fun negativeRadius() {
            assertTrue(RelayChainGeometry.connectorSegments(3, 300f, -1f).isEmpty())
        }

        @Test
        @DisplayName("omits segments when crowded nodes would render backwards")
        fun crowdedNodes() {
            // Cell width 20 => centres 20 apart, but radius 15 => edges overlap.
            assertTrue(RelayChainGeometry.connectorSegments(5, 100f, 15f).isEmpty())
        }

        @Test
        @DisplayName("omits a segment when node edges exactly touch")
        fun touchingNodes() {
            // Cell width 40 => centres 40 apart; radius 20 => edges meet at one point.
            assertTrue(RelayChainGeometry.connectorSegments(5, 200f, 20f).isEmpty())
        }
    }

    @Nested
    @DisplayName("ConnectorSegment")
    inner class Segment {

        @Test
        @DisplayName("compares by value")
        fun valueSemantics() {
            assertEquals(ConnectorSegment(1f, 2f), ConnectorSegment(1f, 2f))
            assertEquals(ConnectorSegment(1f, 2f).hashCode(), ConnectorSegment(1f, 2f).hashCode())
            assertTrue(ConnectorSegment(1f, 2f).toString().contains("1.0"))
            assertEquals(3f, ConnectorSegment(1f, 2f).copy(startX = 3f).startX, tolerance)
            assertEquals(1f, ConnectorSegment(1f, 2f).component1(), tolerance)
            assertEquals(2f, ConnectorSegment(1f, 2f).component2(), tolerance)
        }
    }
}
