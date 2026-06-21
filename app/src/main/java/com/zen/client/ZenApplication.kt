package com.zen.client

import android.app.Application

class ZenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Grab whatever's left in the logcat ring buffer from the PREVIOUS
        // process before any new logging from this launch starts crowding
        // it out. This is what catches a native crash's backtrace, since
        // that happens below the level CrashHandler (Java-only) can see.
        LogUtils.dumpLogcatToFile(this, "app_start")
        CrashHandler.install(this)
    }
}
