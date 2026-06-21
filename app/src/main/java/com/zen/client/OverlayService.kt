package com.zen.client

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

private const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var guiView: View? = null
    private var fpsView: TextView? = null
    private var fpsCallback: Choreographer.FrameCallback? = null
    private val frameTimestamps = ArrayDeque<Long>()

    private var foregroundHandler: Handler? = null
    private var foregroundRunnable: Runnable? = null
    private var overlayVisible = true

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
        startForegroundWatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundWatcher()
        stopFpsCounter()
        guiView?.let { runCatching { windowManager.removeView(it) } }
        runCatching { windowManager.removeView(bubbleView) }
    }

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun addBubble() {
        val size = (56 * resources.displayMetrics.density).toInt()
        bubbleView = ImageView(this).apply {
            setImageResource(R.drawable.ic_zen_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(4, 4, 4, 4)
        }
        bubbleParams = WindowManager.LayoutParams(size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = 0; y = 200
        }
        var initialX = 0; var initialY = 0; var touchX = 0f; var touchY = 0f; var dragged = false
        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { initialX = bubbleParams.x; initialY = bubbleParams.y; touchX = event.rawX; touchY = event.rawY; dragged = false; true }
                MotionEvent.ACTION_MOVE -> { val dx = (event.rawX - touchX).toInt(); val dy = (event.rawY - touchY).toInt(); if (abs(dx) > 8 || abs(dy) > 8) dragged = true; bubbleParams.x = initialX + dx; bubbleParams.y = initialY + dy; windowManager.updateViewLayout(bubbleView, bubbleParams); true }
                MotionEvent.ACTION_UP -> { if (!dragged) toggleGui(); true }
                else -> false
            }
        }
        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun toggleGui() {
        guiView?.let { runCatching { windowManager.removeView(it) }; guiView = null; return }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6121212"))
            setPadding(32, 32, 32, 32)
        }
        panel.addView(TextView(this).apply { text = "Zen Client"; setTextColor(Color.WHITE); textSize = 16f; setPadding(0, 0, 0, 16) })
        val fpsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fpsRow.addView(TextView(this).apply { text = "FPS Counter"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        fpsRow.addView(Switch(this).apply { setOnCheckedChangeListener { _, on -> if (on) startFpsCounter() else stopFpsCounter() } })
        panel.addView(fpsRow)
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = bubbleParams.x + 120; y = bubbleParams.y }
        windowManager.addView(panel, params); guiView = panel
    }

    private fun startFpsCounter() {
        if (fpsView != null) return
        fpsView = TextView(this).apply { setTextColor(Color.parseColor("#FF2A3C")); setBackgroundColor(Color.parseColor("#CC121212")); textSize = 14f; setPadding(12, 6, 12, 6); text = "FPS: --" }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = 24; y = 100 }
        windowManager.addView(fpsView, params)
        fpsCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameTimestamps.addLast(frameTimeNanos)
                val cutoff = frameTimeNanos - 1_000_000_000L
                while (frameTimestamps.isNotEmpty() && frameTimestamps.first() < cutoff) frameTimestamps.removeFirst()
                fpsView?.text = "FPS: ${frameTimestamps.size}"
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(fpsCallback!!)
    }

    private fun stopFpsCounter() {
        fpsCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        fpsCallback = null; frameTimestamps.clear()
        fpsView?.let { runCatching { windowManager.removeView(it) } }; fpsView = null
    }

    // --- Foreground-app watcher: only show the bubble/GUI while Minecraft is the active app ---

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun currentForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 10_000
        val events = usm.queryEvents(begin, end)
        var lastPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }

    private fun startForegroundWatcher() {
        if (!hasUsageAccess()) return
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val fg = currentForegroundPackage()
                if (fg != null) setOverlayVisible(fg == MINECRAFT_PACKAGE)
                foregroundHandler?.postDelayed(this, 1000)
            }
        }
        foregroundHandler = handler
        foregroundRunnable = runnable
        handler.post(runnable)
    }

    private fun stopForegroundWatcher() {
        foregroundRunnable?.let { foregroundHandler?.removeCallbacks(it) }
        foregroundHandler = null
        foregroundRunnable = null
    }

    private fun setOverlayVisible(visible: Boolean) {
        if (visible == overlayVisible) return
        overlayVisible = visible
        bubbleView.visibility = if (visible) View.VISIBLE else View.GONE
        fpsView?.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) {
            guiView?.let { runCatching { windowManager.removeView(it) } }
            guiView = null
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Zen Client", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zen Client running")
            .setContentText("Tap the bubble to open the menu")
            .setSmallIcon(R.mipmap.ic_launcher).setOngoing(true).build()
    }

    companion object {
        private const val CHANNEL_ID = "zen_overlay_channel"
        private const val NOTIF_ID = 1001
    }
}
