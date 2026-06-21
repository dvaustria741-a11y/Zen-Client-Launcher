package com.zen.overlay

import android.app.Application

class ZenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
