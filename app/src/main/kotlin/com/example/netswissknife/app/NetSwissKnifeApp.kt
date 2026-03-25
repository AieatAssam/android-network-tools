package com.example.netswissknife.app

import android.app.Application
import com.example.netswissknife.app.BuildConfig
import com.example.netswissknife.app.crash.CrashHandler
import com.example.netswissknife.app.util.AppLogger
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class NetSwissKnifeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // OSMDroid: identify this app to tile servers using the application ID per
        // https://github.com/osmdroid/osmdroid/wiki/Important-notes-on-using-osmdroid-in-your-app
        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        AppLogger.init(this)
        installCrashHandler()
        AppLogger.i("App", "NetSwissKnife started (versionName=${packageManager.getPackageInfo(packageName, 0).versionName})")
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
