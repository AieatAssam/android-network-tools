package com.example.netswissknife.app.util

import android.content.Context
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.MapTileRequestState
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

/**
 * A [MapTileProviderBasic] that forwards tile-load failures to [AppLogger].
 *
 * When a tile download fails (e.g. HTTP 403, network error, or timeout) osmdroid calls
 * [mapTileRequestFailed]. We intercept that callback to write a warning entry to the app's
 * persistent debug log so the failure shows up both in logcat and in the in-app log file.
 *
 * Actual HTTP response codes (403 etc.) are printed by osmdroid itself when
 * [org.osmdroid.config.Configuration.isDebugMode] is true – see [NetSwissKnifeApp] – under
 * the "OsmDroid" logcat tag.
 */
class LoggingMapTileProvider(context: Context) :
    MapTileProviderBasic(context, TileSourceFactory.MAPNIK) {

    override fun mapTileRequestFailed(pState: MapTileRequestState) {
        super.mapTileRequestFailed(pState)
        val tile = MapTileIndex.toString(pState.mapTile)
        AppLogger.w("OsmTile", "Tile download failed: $tile (check OsmDroid logcat tag for HTTP status code)")
    }
}
