package com.example.netswissknife.app

import android.app.Application
import com.example.netswissknife.app.util.AppLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NetSwissKnifeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        installCrashHandler()
        AppLogger.i("App", "NetSwissKnife started (versionName=${packageManager.getPackageInfo(packageName, 0).versionName})")
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppLogger.e("CRASH", "Uncaught exception on thread '${thread.name}'", throwable)
            } catch (_: Exception) {
                // Never block the default handler
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
