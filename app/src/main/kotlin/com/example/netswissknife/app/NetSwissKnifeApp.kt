package com.example.netswissknife.app

import android.app.Application
import android.preference.PreferenceManager
import com.example.netswissknife.app.crash.CrashHandler
import com.example.netswissknife.app.util.AppLogger
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import java.io.File

@HiltAndroidApp
class NetSwissKnifeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        initOsmdroid()
        installCrashHandler()
        AppLogger.i("App", "NetSwissKnife started (versionName=${packageManager.getPackageInfo(packageName, 0).versionName})")
    }

    private fun initOsmdroid() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
        // Load osmdroid config from shared prefs (required for proper tile cache storage)
        @Suppress("DEPRECATION")
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, prefs)
        // Override cache path to app-internal storage so no WRITE_EXTERNAL_STORAGE permission
        // is required on any API level. The default path (external storage) is inaccessible on
        // Android 10+ (targetSdk 29+) without legacy external storage, causing the SQLite tile
        // cache DB to fail → every session re-downloads all tiles → OSM rate-limits with 403.
        val osmBaseDir = File(filesDir, "osmdroid").also { it.mkdirs() }
        osmConfig.osmdroidBasePath = osmBaseDir
        osmConfig.osmdroidTileCache = File(osmBaseDir, "tiles").also { it.mkdirs() }
        // Distinctive User-Agent required by https://operations.osmfoundation.org/policies/tiles/
        // Format: AppName/version (+contact_url) — generic/example IDs are blocked with 403
        osmConfig.userAgentValue =
            "NetSwissKnife/$versionName (+https://github.com/aieatassam/android-network-tools)"
        // OSM tile usage policy mandates a minimum 7-day cache for tiles
        osmConfig.expirationOverrideDuration = 7L * 24 * 60 * 60 * 1000
        // Keep concurrent downloads low to avoid triggering rate-limit blocks
        osmConfig.tileDownloadThreads = 2
        osmConfig.tileDownloadMaxQueueSize = 40
        // Enable osmdroid's verbose HTTP logging so tile errors (including 403 status codes)
        // appear in logcat under tag "OsmDroid" – invaluable for diagnosing tile server rejections
        osmConfig.isDebugMode = true
        AppLogger.i("OsmInit", "OSM tile provider configured – User-Agent: ${osmConfig.userAgentValue}")
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(
                context = this,
                defaultHandler = defaultHandler,
                onBeforeHandle = { thread, throwable ->
                    try {
                        AppLogger.e("CRASH", "Uncaught exception on thread '${thread.name}'", throwable)
                    } catch (_: Exception) {
                        // Never block crash reporting
                    }
                },
            )
        )
    }
}
