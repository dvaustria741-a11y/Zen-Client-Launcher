package com.zen.client

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

private const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        tvStatus = findViewById(R.id.tvStatus)
        val tvVersionInfo = findViewById<TextView>(R.id.tvVersionInfo)
        val btnLaunch = findViewById<Button>(R.id.btnLaunch)
        val btnSettings = findViewById<Button>(R.id.btnSettings)

        tvGreeting.text = timeGreeting()

        val mcInstalled = isPackageInstalled(MINECRAFT_PACKAGE)
        val mcVersion = if (mcInstalled) getMcVersion() else "Not installed"
        tvVersionInfo.text = "$mcVersion | Zen Client 1.0"
        tvStatus.text = if (mcInstalled) "Ready to launch" else "Minecraft not found"

        btnLaunch.setOnClickListener {
            if (!mcInstalled) {
                Toast.makeText(this, "Minecraft Bedrock not installed", Toast.LENGTH_SHORT).show()
            } else {
                launchMinecraft()
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    /**
     * Launches ZenNativeActivity, which hosts libzenclient.so and Bedrock's
     * native code in-process (see ZenNativeActivity.kt / zen_native_activity.cpp).
     * No system overlay window and no SYSTEM_ALERT_WINDOW permission are
     * involved — the ClickGUI is drawn natively inside the game itself,
     * the same way Flarial does it.
     */
    private fun launchMinecraft() {
        tvStatus.text = "Launching..."
        val bedrockNativeDir = try {
            packageManager.getApplicationInfo(MINECRAFT_PACKAGE, 0).nativeLibraryDir
        } catch (e: Exception) {
            null
        }
        val intent = Intent(this, ZenNativeActivity::class.java).apply {
            bedrockNativeDir?.let { putExtra("bedrock_native_dir", it) }
        }
        startActivity(intent)
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
