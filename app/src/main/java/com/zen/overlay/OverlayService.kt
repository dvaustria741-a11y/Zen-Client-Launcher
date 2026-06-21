package com.zen.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * Route A overlay service: draws UI on top of Minecraft, doesn't touch the
 * game's process. The FPS counter below is a Choreographer-based proxy for
 * overall device frame pacing -- it is NOT read from Minecraft's internal
 * renderer (that would require Route B / process injection).
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager

    private lateinit var bubbleView: View
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private var guiView: View? = null
    private var fpsView: TextView? = null
    private var fpsCallback: Choreographer.FrameCallback? = null
    private val frameTimestamps = ArrayDeque<Long>()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopFpsCounter()
        guiView?.let { runCatching { windowManager.removeView(it) } }
        runCatching { windowManager.removeView(bubbleView) }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ---- Draggable bubble, tap to open/close the ClickGUI panel ----

    private fun addBubble() {
        bubbleView = TextView(this).apply {
            text = "Z"
            setBackgroundColor(Color.parseColor("#CC2196F3"))
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) dragged = true
                    bubbleParams.x = initialX + dx
                    bubbleParams.y = initialY + dy
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) toggleGui()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, bubbleParams)
    }

    // ---- ClickGUI toggle panel ----

    private fun toggleGui() {
        guiView?.let {
            runCatching { windowManager.removeView(it) }
            guiView = null
            return
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6121212"))
            setPadding(32, 32, 32, 32)
        }

        panel.addView(TextView(this).apply {
            text = "ZenOverlay"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 0, 0, 16)
        })

        val fpsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fpsRow.addView(TextView(this).apply {
            text = "FPS Counter"
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        fpsRow.addView(Switch(this).apply {
            setOnCheckedChangeListener { _, checked ->
                if (checked) startFpsCounter() else stopFpsCounter()
            }
        })
        panel.addView(fpsRow)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams.x + 120
            y = bubbleParams.y
        }

        windowManager.addView(panel, params)
        guiView = panel
    }

    // ---- FPS module ----

    private fun startFpsCounter() {
        if (fpsView != null) return

        fpsView = TextView(this).apply {
            setTextColor(Color.GREEN)
            setBackgroundColor(Color.parseColor("#99000000"))
            textSize = 14f
            setPadding(12, 6, 12, 6)
            text = "FPS: --"
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 100
        }

        windowManager.addView(fpsView, params)

        fpsCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameTimestamps.addLast(frameTimeNanos)
                val cutoff = frameTimeNanos - 1_000_000_000L
                while (frameTimestamps.isNotEmpty() && frameTimestamps.first() < cutoff) {
                    frameTimestamps.removeFirst()
                }
                fpsView?.text = "FPS: ${frameTimestamps.size}"
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(fpsCallback!!)
    }

    private fun stopFpsCounter() {
        fpsCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        fpsCallback = null
        frameTimestamps.clear()
        fpsView?.let { runCatching { windowManager.removeView(it) } }
        fpsView = null
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "ZenOverlay", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZenOverlay running")
            .setContentText("Tap the bubble to open the menu")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "zen_overlay_channel"
        private const val NOTIF_ID = 1001
    }
}
