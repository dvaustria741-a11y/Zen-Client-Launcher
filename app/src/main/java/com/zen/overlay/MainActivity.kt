package com.zen.overlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

private const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 200, 64, 64)
        }

        val title = TextView(this).apply {
            text = "ZenOverlay"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        root.addView(title)

        statusText = TextView(this).apply {
            textSize = 15f
            setPadding(0, 0, 0, 48)
        }
        root.addView(statusText)

        root.addView(AppCompatButton(this).apply {
            text = "Grant Overlay Permission"
            setOnClickListener { requestOverlayPermission() }
        })

        root.addView(AppCompatButton(this).apply {
            text = "Launch Minecraft"
            setOnClickListener { launchMinecraft() }
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val mcInstalled = isPackageInstalled(MINECRAFT_PACKAGE)
        statusText.text = buildString {
            append("Overlay permission: ")
            append(if (hasOverlay) "granted" else "NOT granted")
            append("\nMinecraft Bedrock: ")
            append(if (mcInstalled) "found" else "not installed")
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Already granted", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun launchMinecraft() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isPackageInstalled(MINECRAFT_PACKAGE)) {
            Toast.makeText(this, "Minecraft Bedrock isn't installed", Toast.LENGTH_SHORT).show()
            return
        }

        startForegroundService(Intent(this, OverlayService::class.java))

        packageManager.getLaunchIntentForPackage(MINECRAFT_PACKAGE)?.let { intent ->
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            startActivity(intent)
        }
    }
}
