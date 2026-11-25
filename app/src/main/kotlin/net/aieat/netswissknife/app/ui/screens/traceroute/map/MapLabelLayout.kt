package net.aieat.netswissknife.app.ui.screens.traceroute.map

import kotlin.math.abs
import kotlin.math.hypot

/** An axis-aligned rectangle in canvas pixels. */
data class PixelBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun intersects(other: PixelBox): Boolean {
        val acrossX = left < other.right && other.left < right
        return acrossX && top < other.bottom && other.top < bottom
    }

    fun isInside(
        widthPx: Float,
        heightPx: Float,
    ): Boolean = left >= 0f && top >= 0f && right <= widthPx && bottom <= heightPx

    /** True when ([x], [y]) is in the box, grown where needed to reach [minHalfSize] each way from its centre. */
    fun contains(
        x: Float,
        y: Float,
        minHalfSize: Float,
    ): Boolean {
        val halfWidth = maxOf((right - left) / 2f, minHalfSize)
        val halfHeight = maxOf((bottom - top) / 2f, minHalfSize)
        return abs(x - centerX) <= halfWidth && abs(y - centerY) <= halfHeight
    }

    companion object {
        fun around(
            point: ScreenPoint,
            radius: Float,
        ) = PixelBox(point.x - radius, point.y - radius, point.x + radius, point.y + radius)
    }
}

/** Where a label sits next to its marker; tried in this order. */
enum class LabelAnchor { RIGHT, LEFT, ABOVE, BELOW }

/**
 * Markers that are closer on screen than a marker's diameter, shown with one label. [members] index
 * [TraceRouteMap.allStops]; [hopNumbers] are the hops of every member, so the label and the callout
 * name all of them.
 */
data class MarkerCluster(
    val members: List<Int>,
    val hopNumbers: List<Int>,
    val bounds: PixelBox,
) {
    val label: String get() = RouteStop.labelOf(hopNumbers)
}

/** A label that fits: its text and the box it is drawn in, background padding included. */
data class PlacedLabel(
    val cluster: Int,
    val text: String,
    val box: PixelBox,
    val anchor: LabelAnchor,
)

/** The canvas labels must stay inside, and boxes on it that labels must keep clear of, such as buttons. */
data class LabelArea(
    val widthPx: Float,
    val heightPx: Float,
    val blocked: List<PixelBox> = emptyList(),
)

/** Size of a label's box in pixels, background padding included. */
data class LabelSize(
    val width: Float,
    val height: Float,
)

/**
 * Marker labels without overlap, at the current zoom: markers closer than a marker diameter merge into
 * one cluster with one label, and each label takes the first anchor that keeps it inside the canvas and
 * clear of every marker and every label placed before it. A label that fits nowhere is left out; its
 * marker stays tappable and the callout lists its hops.
 */
object MapLabelLayout {
    /**
     * Clusters of [points] (one per stop, in [TraceRouteMap.allStops] order) whose markers of [markerRadiusPx]
     * would touch. Closeness is chained, so a row of markers each close to the next is one cluster.
     */
    fun clusters(
        points: List<ScreenPoint>,
        hopNumbers: List<List<Int>>,
        markerRadiusPx: Float,
    ): List<MarkerCluster> {
        val parent = IntArray(points.size) { it }

        fun root(index: Int): Int {
            var i = index
            while (parent[i] != i) i = parent[i]
            return i
        }
        for (a in points.indices) {
            for (b in a + 1 until points.size) {
                val distance = hypot(points[a].x - points[b].x, points[a].y - points[b].y)
                if (distance < 2f * markerRadiusPx) parent[root(b)] = root(a)
            }
        }
        return points.indices
            .groupBy { root(it) }
            .values
            .map { members ->
                val boxes = members.map { PixelBox.around(points[it], markerRadiusPx) }
                MarkerCluster(
                    members = members,
                    hopNumbers = members.flatMap { hopNumbers[it] }.distinct().sorted(),
                    bounds =
                        PixelBox(
                            left = boxes.minOf { it.left },
                            top = boxes.minOf { it.top },
                            right = boxes.maxOf { it.right },
                            bottom = boxes.maxOf { it.bottom },
                        ),
                )
            }.sortedBy { it.hopNumbers.first() }
    }

    /**
     * Labels for [clusters] inside [area], [gapPx] from their markers. Clusters holding a hop in
     * [firstHops] (the destination) are placed first, then in hop order; a cluster whose markers are off
     * the canvas gets no label.
     */
    fun place(
        clusters: List<MarkerCluster>,
        measure: (String) -> LabelSize,
        area: LabelArea,
        gapPx: Float,
        firstHops: Set<Int> = emptySet(),
    ): List<PlacedLabel> {
        val obstacles = clusters.map { it.bounds } + area.blocked
        val placed = mutableListOf<PlacedLabel>()
        val order = clusters.indices.sortedBy { index -> clusters[index].hopNumbers.none { it in firstHops } }
        order.forEach { index ->
            val cluster = clusters[index]
            val visible = cluster.bounds.centerX in 0f..area.widthPx && cluster.bounds.centerY in 0f..area.heightPx
            if (!visible) return@forEach
            val size = measure(cluster.label)
            LabelAnchor.entries
                .map { anchor -> anchor to boxFor(anchor, cluster.bounds, size, gapPx) }
                .firstOrNull { (_, box) ->
                    box.isInside(area.widthPx, area.heightPx) &&
                        obstacles.none { it.intersects(box) } &&
                        placed.none { it.box.intersects(box) }
                }?.let { (anchor, box) -> placed += PlacedLabel(index, cluster.label, box, anchor) }
        }
        return placed.sortedBy { it.cluster }
    }

    /** Index of the cluster with a marker within [radiusPx] of ([x], [y]), nearest first, or null. */
    fun clusterAt(
        clusters: List<MarkerCluster>,
        points: List<ScreenPoint>,
        x: Float,
        y: Float,
        radiusPx: Float,
    ): Int? {
        val stop = nearestPointIndex(points, x, y, radiusPx) ?: return null
        return clusters.indexOfFirst { stop in it.members }.takeIf { it >= 0 }
    }

    /**
     * The cluster a tap at ([x], [y]) selects: the one with a marker within [radiusPx], else the one whose
     * label is under the tap. A label is hit within [radiusPx] of its centre as well, so a short label is
     * as easy to tap as a marker.
     */
    fun hitCluster(
        clusters: List<MarkerCluster>,
        points: List<ScreenPoint>,
        labels: List<PlacedLabel>,
        x: Float,
        y: Float,
        radiusPx: Float,
    ): Int? =
        clusterAt(clusters, points, x, y, radiusPx)
            ?: labels.firstOrNull { it.box.contains(x, y, radiusPx) }?.cluster

    private fun boxFor(
        anchor: LabelAnchor,
        marker: PixelBox,
        size: LabelSize,
        gap: Float,
    ): PixelBox {
        val left =
            when (anchor) {
                LabelAnchor.RIGHT -> marker.right + gap
                LabelAnchor.LEFT -> marker.left - gap - size.width
                LabelAnchor.ABOVE, LabelAnchor.BELOW -> marker.centerX - size.width / 2f
            }
        val top =
            when (anchor) {
                LabelAnchor.RIGHT, LabelAnchor.LEFT -> marker.centerY - size.height / 2f
                LabelAnchor.ABOVE -> marker.top - gap - size.height
                LabelAnchor.BELOW -> marker.bottom + gap
            }
        return PixelBox(left, top, left + size.width, top + size.height)
    }
}
