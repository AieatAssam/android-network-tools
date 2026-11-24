package net.aieat.netswissknife.app.ui.screens.traceroute.map

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

/**
 * Land polygons for the offline route map.
 *
 * @property rings each ring as `[lon0, lat0, lon1, lat1, ...]` in degrees; the closing
 *   point is implied. Holes are ordinary rings, so fill with the even-odd rule.
 */
class WorldLand(
    val rings: List<FloatArray>,
) {
    val pointCount: Int get() = rings.sumOf { it.size / 2 }
}

/**
 * Parses the compact Natural Earth base map written by `tools/worldmap/build_world_land.py`.
 * Big-endian: magic "NSKL", u16 version, u16 ring count, then per ring a u16 point count
 * followed by (i16 longitude, i16 latitude) pairs in centidegrees.
 */
object WorldLandParser {
    /** Asset path under `src/main/assets`. */
    const val ASSET_PATH = "worldmap/land-110m.bin"

    private const val MAGIC = 0x4E534B4C // "NSKL"
    private const val VERSION = 1
    private const val SCALE = 100f
    private const val MAX_LON_CENTI = 18_000
    private const val MAX_LAT_CENTI = 9_000
    private const val MIN_RING_POINTS = 3
    private const val U16_MASK = 0xFFFF

    /** @throws IllegalArgumentException when [bytes] is not a complete, valid base map. */
    fun parse(bytes: ByteArray): WorldLand {
        val buffer = ByteBuffer.wrap(bytes)
        try {
            require(buffer.getInt() == MAGIC) { "Not a base map asset" }
            val version = buffer.getShort().toInt() and U16_MASK
            require(version == VERSION) { "Unsupported base map version $version" }
            val ringCount = buffer.getShort().toInt() and U16_MASK
            val rings = List(ringCount) { readRing(buffer) }
            require(!buffer.hasRemaining()) { "Unexpected bytes after the last ring" }
            return WorldLand(rings)
        } catch (e: BufferUnderflowException) {
            throw IllegalArgumentException("Truncated base map asset", e)
        }
    }

    private fun readRing(buffer: ByteBuffer): FloatArray {
        val points = buffer.getShort().toInt() and U16_MASK
        require(points >= MIN_RING_POINTS) { "Ring has $points points" }
        val ring = FloatArray(points * 2)
        for (i in 0 until points) {
            val lon = buffer.getShort().toInt()
            val lat = buffer.getShort().toInt()
            require(lon in -MAX_LON_CENTI..MAX_LON_CENTI && lat in -MAX_LAT_CENTI..MAX_LAT_CENTI) {
                "Coordinate out of range"
            }
            ring[i * 2] = lon / SCALE
            ring[i * 2 + 1] = lat / SCALE
        }
        return ring
    }
}
