package net.aieat.netswissknife.app.ui.screens.traceroute.map

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

@DisplayName("WorldLandParser")
class WorldLandParserTest {
    private fun asset(
        magic: String = "NSKL",
        version: Int = 1,
        rings: List<List<Pair<Int, Int>>>,
        declaredRings: Int = rings.size,
        trailing: ByteArray = ByteArray(0),
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.write(magic.toByteArray(Charsets.US_ASCII))
            out.writeShort(version)
            out.writeShort(declaredRings)
            rings.forEach { ring ->
                out.writeShort(ring.size)
                ring.forEach { (lon, lat) ->
                    out.writeShort(lon)
                    out.writeShort(lat)
                }
            }
            out.write(trailing)
        }
        return bytes.toByteArray()
    }

    private val triangle = listOf(0 to 0, 1_000 to 0, 1_000 to 1_000)

    @Test
    fun `reads rings as longitude latitude pairs in degrees`() {
        val extremes = listOf(-18_000 to -9_000, 18_000 to 9_000, 12_345 to -6_789)
        val land = WorldLandParser.parse(asset(rings = listOf(triangle, extremes)))

        assertEquals(2, land.rings.size)
        assertArrayEquals(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f), land.rings[0], 1e-4f)
        assertArrayEquals(floatArrayOf(-180f, -90f, 180f, 90f, 123.45f, -67.89f), land.rings[1], 1e-4f)
        assertEquals(6, land.pointCount)
    }

    @Test
    fun `rejects a file that is not a base map`() {
        val bytes = asset(magic = "PNG!", rings = listOf(triangle))
        assertThrows<IllegalArgumentException> { WorldLandParser.parse(bytes) }
    }

    @Test
    fun `rejects an unsupported format version`() {
        assertThrows<IllegalArgumentException> { WorldLandParser.parse(asset(version = 2, rings = listOf(triangle))) }
    }

    @Test
    fun `rejects a truncated file`() {
        val bytes = asset(rings = listOf(triangle))
        assertThrows<IllegalArgumentException> { WorldLandParser.parse(bytes.copyOf(bytes.size - 1)) }
        val missingRing = asset(rings = listOf(triangle), declaredRings = 2)
        assertThrows<IllegalArgumentException> { WorldLandParser.parse(missingRing) }
        assertThrows<IllegalArgumentException> { WorldLandParser.parse(ByteArray(3)) }
    }

    @Test
    fun `rejects trailing bytes`() {
        assertThrows<IllegalArgumentException> {
            WorldLandParser.parse(asset(rings = listOf(triangle), trailing = byteArrayOf(0)))
        }
    }

    @Test
    fun `rejects coordinates outside the globe`() {
        assertThrows<IllegalArgumentException> {
            WorldLandParser.parse(asset(rings = listOf(listOf(0 to 0, 18_001 to 0, 0 to 10))))
        }
        assertThrows<IllegalArgumentException> {
            WorldLandParser.parse(asset(rings = listOf(listOf(0 to 0, 0 to -9_001, 10 to 10))))
        }
    }

    @Test
    fun `rejects a ring with fewer than three points`() {
        assertThrows<IllegalArgumentException> {
            WorldLandParser.parse(asset(rings = listOf(listOf(0 to 0, 10 to 10))))
        }
    }

    @Test
    fun `the bundled Natural Earth asset parses and stays compact`() {
        val file = File("src/main/assets/${WorldLandParser.ASSET_PATH}")
        assertTrue(file.isFile, "missing ${file.absolutePath}")
        assertTrue(file.length() < 80 * 1024, "asset is ${file.length()} bytes")

        val land = WorldLandParser.parse(file.readBytes())

        assertTrue(land.rings.size > 100, "only ${land.rings.size} rings")
        assertTrue(land.pointCount > 4_000, "only ${land.pointCount} points")
        land.rings.forEach { ring ->
            for (i in ring.indices step 2) {
                assertTrue(ring[i] in -180f..180f && ring[i + 1] in -90f..90f)
            }
        }
    }
}
