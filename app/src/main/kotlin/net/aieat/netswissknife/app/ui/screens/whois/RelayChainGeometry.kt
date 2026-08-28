package net.aieat.netswissknife.app.ui.screens.whois

/**
 * A single horizontal connector drawn between two adjacent relay-chain nodes.
 * Coordinates are pixels in the connector canvas' own coordinate space.
 */
data class ConnectorSegment(val startX: Float, val endX: Float)

/**
 * Layout maths for the WHOIS relay-chain visualiser, kept free of Compose so the
 * node/connector alignment is unit-testable.
 *
 * The node row lays `nodeCount` equally weighted cells across the full canvas
 * width with no arrangement spacing and no padding, so cell `i` spans
 * `[i * w/n, (i + 1) * w/n)` and its node is centred within that cell. The
 * connector canvas must use exactly these positions, otherwise the line and the
 * circles drift apart.
 */
object RelayChainGeometry {

    /** Centre X of node [index] when [nodeCount] nodes share [width] pixels. */
    fun nodeCenterX(index: Int, nodeCount: Int, width: Float): Float {
        require(nodeCount > 0) { "nodeCount must be positive" }
        require(index in 0 until nodeCount) { "index $index out of bounds for $nodeCount nodes" }
        val cell = width / nodeCount
        return cell * index + cell / 2f
    }

    /**
     * Connector segments joining consecutive node edges. Returns an empty list
     * when there is nothing to join, when the canvas has no width, or when the
     * nodes are packed so tightly that their circles already touch (in which
     * case a segment would render backwards).
     */
    fun connectorSegments(
        nodeCount: Int,
        width: Float,
        nodeRadius: Float
    ): List<ConnectorSegment> {
        if (nodeCount < 2 || width <= 0f || nodeRadius < 0f) return emptyList()
        return (0 until nodeCount - 1).mapNotNull { i ->
            val start = nodeCenterX(i, nodeCount, width) + nodeRadius
            val end = nodeCenterX(i + 1, nodeCount, width) - nodeRadius
            if (end > start) ConnectorSegment(start, end) else null
        }
    }
}
