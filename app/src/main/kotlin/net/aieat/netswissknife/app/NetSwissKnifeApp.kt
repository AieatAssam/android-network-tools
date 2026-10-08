package net.aieat.netswissknife.app

import android.app.Application
import net.aieat.netswissknife.app.crash.CrashHandler
import net.aieat.netswissknife.app.crash.CrashReportBuilder
import net.aieat.netswissknife.app.util.AppLogger
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
        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(
                context = this,
                defaultHandler = defaultHandler,
                onBeforeHandle = { thread, throwable ->
                    try {
                        val safeThread = CrashReportBuilder.safeMetadata(thread.name ?: "unknown")
                        val safeClass = CrashReportBuilder.safeMetadata(throwable.javaClass.name)
                        AppLogger.e("CRASH", "Uncaught $safeClass on thread '$safeThread'")
                    } catch (_: Throwable) {
                        // Never block crash reporting
                    }
                },
            )
        )
    }
}
