package com.example.netswissknife.app

import android.app.Application
import com.example.netswissknife.app.util.AppLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NetSwissKnifeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.i("App", "NetSwissKnife started (versionName=${packageManager.getPackageInfo(packageName, 0).versionName})")
    }
}
