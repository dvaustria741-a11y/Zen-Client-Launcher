package com.zen.client

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class CrashHandler(private val appContext: Context) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val log = "Zen Client crash - $timestamp\n\n$sw"

            val crashFile = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, CRASH_LOG_FILE)
            crashFile.writeText(log)

            val intent = Intent(appContext, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            appContext.startActivity(intent)
        } catch (e: Exception) {
            // if logging itself fails, just fall through to process kill below
        } finally {
            Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }

    companion object {
        private const val CRASH_LOG_FILE = "crash_log.txt"

        fun install(application: Application) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(application))
        }

        fun readLog(context: Context): String {
            val file = File(context.getExternalFilesDir(null) ?: context.filesDir, CRASH_LOG_FILE)
            return if (file.exists()) file.readText() else "No crash log found."
        }
    }
}
