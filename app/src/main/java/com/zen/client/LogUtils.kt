package com.zen.client

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dumps this process's own logcat buffer to a plain file under
 * getExternalFilesDir(null), readable via any file manager — no root,
 * no adb, no PC required.
 *
 * Apps may read logcat entries tagged with their own UID without any
 * special permission; READ_LOGS is only required to read OTHER apps'
 * or system-wide logs. A native crash's "Fatal signal ..." / backtrace
 * lines (written by debuggerd) are associated with the crashing
 * process's UID and are typically still readable this way on the next
 * launch, even though they happen below the level Java's
 * UncaughtExceptionHandler can catch.
 */
object LogUtils {
    private const val TAG = "LogUtils"

    fun dumpLogcatToFile(context: Context, label: String) {
        try {
            val process = ProcessBuilder("logcat", "-d", "-v", "time")
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            process.waitFor()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFile = File(context.getExternalFilesDir(null), "zen_log_${timestamp}_$label.log")
            outFile.writeText(output)
            Log.i(TAG, "Wrote logcat dump to ${outFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dump logcat to file", e)
        }
    }
}
