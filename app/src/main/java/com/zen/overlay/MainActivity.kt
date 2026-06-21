package com.zen.overlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

private const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvVersionInfo = findViewById<TextView>(R.id.tvVersionInfo)
        val btnLaunch = findViewById<Button>(R.id.btnLaunch)
        val btnSettings = findViewById<Button>(R.id.btnSettings)

        tvGreeting.text = timeGreeting()

        val mcInstalled = isPackageInstalled(MINECRAFT_PACKAGE)
        val mcVersion = if (mcInstalled) getMcVersion() else "Not installed"
        tvVersionInfo.text = "$mcVersion | Zen Client 1.0"

        btnLaunch.setOnClickListener {
            when {
                !Settings.canDrawOverlays(this) -> {
                    tvStatus.text = "Grant overlay permission first"
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
                !mcInstalled -> Toast.makeText(this, "Minecraft Bedrock not installed", Toast.LENGTH_SHORT).show()
                else -> {
                    tvStatus.text = "Launching..."
                    startForegroundService(Intent(this, OverlayService::class.java))
                    packageManager.getLaunchIntentForPackage(MINECRAFT_PACKAGE)?.let {
                        it.addCategory(Intent.CATEGORY_LAUNCHER)
                        startActivity(it)
                    }
                }
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val hasOverlay = Settings.canDrawOverlays(this)
        findViewById<TextView>(R.id.tvStatus).text =
            if (hasOverlay) "Ready to launch" else "Overlay permission needed"
    }

    private fun timeGreeting(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good Morning!"
            in 12..16 -> "Good Afternoon!"
            else -> "Good Evening!"
        }
    }

    private fun isPackageInstalled(pkg: String) = try {
        packageManager.getPackageInfo(pkg, 0); true
    } catch (e: Exception) { false }

    private fun getMcVersion() = try {
        "Minecraft ${packageManager.getPackageInfo(MINECRAFT_PACKAGE, 0).versionName}"
    } catch (e: Exception) { "Minecraft" }
}
