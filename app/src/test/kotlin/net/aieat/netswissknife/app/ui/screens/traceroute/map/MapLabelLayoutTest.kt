package net.aieat.netswissknife.app.ui.screens.traceroute.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MapLabelLayout")
class MapLabelLayoutTest {
    private val radius = 6f
    private val gap = 4f

    /** Every label is 8 px per character wide and 16 px tall. */
    private val measure: (String) -> LabelSize = { LabelSize(it.length * 8f, 16f) }

    private fun point(
        x: Float,
        y: Float,
    ) = ScreenPoint(x, y)

    /** Clusters of [points], each the stop of the hop numbered one more than its index. */
    private fun clustersOf(vararg points: ScreenPoint): List<MarkerCluster> {
        val hops = points.indices.map { listOf(it + 1) }
        return MapLabelLayout.clusters(points.toList(), hops, radius)
    }

    @Nested
    @DisplayName("clustering")
    inner class Clustering {
        @Test
        fun `markers closer than a marker diameter share one cluster and one label`() {
            val clusters = clustersOf(point(100f, 100f), point(108f, 100f), point(200f, 100f))

            assertEquals(2, clusters.size)
            assertEquals(listOf(0, 1), clusters[0].members)
            assertEquals("1-2", clusters[0].label)
            assertEquals(listOf(2), clusters[1].members)
        }

        @Test
        fun `markers a diameter or more apart stay separate`() {
            assertEquals(2, clustersOf(point(100f, 100f), point(112f, 100f)).size)
        }

        @Test
        fun `closeness is chained, and the cluster label names every hop`() {
            val clusters = clustersOf(point(0f, 0f), point(10f, 0f), point(20f, 0f), point(30f, 0f))

            assertEquals(1, clusters.size)
            assertEquals("1-4", clusters.single().label)
            assertEquals(PixelBox(-6f, -6f, 36f, 6f), clusters.single().bounds)
        }

        @Test
        fun `a cluster merges hop numbers of stops that are not consecutive`() {
            val clusters =
                MapLabelLayout.clusters(
                    listOf(point(0f, 0f), point(50f, 0f), point(4f, 0f)),
                    listOf(listOf(3, 4), listOf(5), listOf(9)),
                    radius,
                )

            assertEquals(listOf(3, 4, 9), clusters.first().hopNumbers)
            assertEquals("3-4, 9", clusters.first().label)
        }

        @Test
        fun `zooming in splits a cluster again`() {
            val far = listOf(point(100f, 100f), point(108f, 100f))
            val zoomedIn = far.map { point((it.x - 100f) * 4f + 100f, it.y) }

            assertEquals(1, MapLabelLayout.clusters(far, listOf(listOf(1), listOf(2)), radius).size)
            assertEquals(2, MapLabelLayout.clusters(zoomedIn, listOf(listOf(1), listOf(2)), radius).size)
        }
    }

    @Nested
    @DisplayName("placing labels")
    inner class Placing {
        private val area = LabelArea(widthPx = 400f, heightPx = 300f)

        private fun place(
            clusters: List<MarkerCluster>,
            area: LabelArea = this.area,
            firstHops: Set<Int> = emptySet(),
        ) = MapLabelLayout.place(clusters, measure, area, gap, firstHops)

        @Test
        fun `a lone marker is labelled on its right`() {
            val label = place(clustersOf(point(100f, 100f))).single()

            assertEquals(LabelAnchor.RIGHT, label.anchor)
            assertEquals(PixelBox(110f, 92f, 118f, 108f), label.box)
        }

        @Test
        fun `a marker at the right edge is labelled on its left`() {
            assertEquals(LabelAnchor.LEFT, place(clustersOf(point(395f, 100f))).single().anchor)
        }

        @Test
        fun `with no room at either side a label goes below, or above near the bottom`() {
            // A canvas only 30 px wide leaves no room beside a marker in its middle.
            val narrow = LabelArea(widthPx = 30f, heightPx = 300f)
            val wide: (String) -> LabelSize = { LabelSize(20f, 16f) }

            val top = MapLabelLayout.place(clustersOf(point(15f, 8f)), wide, narrow, gap).single()
            val bottom = MapLabelLayout.place(clustersOf(point(15f, 290f)), wide, narrow, gap).single()

            assertEquals(LabelAnchor.BELOW, top.anchor)
            assertEquals(PixelBox(5f, 18f, 25f, 34f), top.box)
            assertEquals(LabelAnchor.ABOVE, bottom.anchor)
        }

        @Test
        fun `every placed label stays inside the canvas`() {
            val corners = clustersOf(point(10f, 10f), point(390f, 10f), point(10f, 290f), point(390f, 290f))

            place(corners).forEach { assertTrue(it.box.isInside(400f, 300f)) }
            assertEquals(4, place(corners).size)
        }

        @Test
        fun `a second label that would overlap the first takes the next anchor`() {
            // Two markers side by side, 20 px apart: the first label on the right would cover the second marker.
            val labels = place(clustersOf(point(100f, 100f), point(120f, 100f)))

            assertEquals(2, labels.size)
            val (first, second) = labels
            assertFalse(first.box.intersects(second.box))
            assertEquals(LabelAnchor.LEFT, first.anchor)
            assertEquals(LabelAnchor.RIGHT, second.anchor)
        }

        @Test
        fun `a label is dropped rather than drawn over another label or marker`() {
            // A ring of markers boxes the centre one in on every side.
            val centre = point(200f, 150f)
            val ring =
                listOf(
                    point(214f, 150f),
                    point(186f, 150f),
                    point(200f, 136f),
                    point(200f, 164f),
                    point(222f, 150f),
                    point(178f, 150f),
                    point(200f, 128f),
                    point(200f, 172f),
                )
            val clusters = clustersOf(centre, *ring.toTypedArray())

            val labels = place(clusters, firstHops = setOf(2, 3, 4, 5, 6, 7, 8, 9))

            assertTrue(labels.none { clusters[it.cluster].hopNumbers == listOf(1) })
            labels.forEachIndexed { i, a ->
                labels.drop(i + 1).forEach { b -> assertFalse(a.box.intersects(b.box)) }
                clusters.forEach { assertFalse(a.box.intersects(it.bounds)) }
            }
        }

        @Test
        fun `the destination is labelled first, so it keeps its preferred place`() {
            // One above the other, close enough that their right-hand labels would overlap.
            val clusters = clustersOf(point(100f, 100f), point(100f, 112f))

            val inHopOrder = place(clusters)
            val destinationFirst = place(clusters, firstHops = setOf(2))

            assertEquals(listOf(LabelAnchor.RIGHT, LabelAnchor.LEFT), inHopOrder.map { it.anchor })
            assertEquals(listOf(LabelAnchor.LEFT, LabelAnchor.RIGHT), destinationFirst.map { it.anchor })
        }

        @Test
        fun `labels keep clear of blocked areas such as the zoom buttons`() {
            val blocked = LabelArea(widthPx = 400f, heightPx = 300f, blocked = listOf(PixelBox(340f, 0f, 400f, 160f)))

            val label = place(clustersOf(point(325f, 50f)), area = blocked).single()

            assertEquals(LabelAnchor.LEFT, label.anchor)
        }

        @Test
        fun `a marker off the canvas gets no label`() {
            assertTrue(place(clustersOf(point(-50f, 100f), point(100f, 400f))).isEmpty())
        }
    }

    @Nested
    @DisplayName("tapping")
    inner class Tapping {
        @Test
        fun `a tap near any member selects its cluster`() {
            val points = listOf(point(100f, 100f), point(108f, 100f), point(300f, 100f))
            val clusters = MapLabelLayout.clusters(points, listOf(listOf(1), listOf(2), listOf(3)), radius)

            assertEquals(0, MapLabelLayout.clusterAt(clusters, points, 110f, 104f, 24f))
            assertEquals(1, MapLabelLayout.clusterAt(clusters, points, 290f, 100f, 24f))
            assertNull(MapLabelLayout.clusterAt(clusters, points, 200f, 200f, 24f))
        }

        @Test
        fun `a tap on a label selects its cluster, not nothing`() {
            val points = listOf(point(100f, 100f), point(300f, 300f))
            val clusters = clustersOf(*points.toTypedArray())
            val labels = listOf(PlacedLabel(1, "2", PixelBox(320f, 292f, 380f, 308f), LabelAnchor.RIGHT))

            assertEquals(1, MapLabelLayout.hitCluster(clusters, points, labels, 370f, 300f, 24f))
            assertNull(MapLabelLayout.clusterAt(clusters, points, 370f, 300f, 24f))
        }

        @Test
        fun `a label's target is at least 48 px tall for a 24 px touch radius`() {
            val points = listOf(point(100f, 100f), point(300f, 300f))
            val clusters = clustersOf(*points.toTypedArray())
            val labels = listOf(PlacedLabel(1, "2", PixelBox(330f, 292f, 380f, 308f), LabelAnchor.RIGHT))

            assertEquals(1, MapLabelLayout.hitCluster(clusters, points, labels, 360f, 323f, 24f))
            assertNull(MapLabelLayout.hitCluster(clusters, points, labels, 360f, 326f, 24f))
        }

        @Test
        fun `a marker wins over a label that overlaps its touch radius`() {
            val points = listOf(point(100f, 100f), point(130f, 100f))
            val clusters = clustersOf(*points.toTypedArray())
            val labels = listOf(PlacedLabel(1, "2", PixelBox(90f, 92f, 150f, 108f), LabelAnchor.RIGHT))

            assertEquals(0, MapLabelLayout.hitCluster(clusters, points, labels, 102f, 100f, 24f))
        }

        @Test
        fun `a tap away from every marker and label selects nothing`() {
            val points = listOf(point(100f, 100f))
            val labels = listOf(PlacedLabel(0, "1", PixelBox(110f, 92f, 130f, 108f), LabelAnchor.RIGHT))

            assertNull(MapLabelLayout.hitCluster(clustersOf(*points.toTypedArray()), points, labels, 400f, 400f, 24f))
        }
    }
}
