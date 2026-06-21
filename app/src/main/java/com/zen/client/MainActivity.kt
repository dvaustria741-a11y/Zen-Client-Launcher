package com.zen.client

import android.app.AppOpsManager
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

private const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"
private const val PREFS_NAME = "zen_client_prefs"
private const val KEY_ASKED_USAGE_ACCESS = "asked_usage_access"

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private var pendingLaunch = false

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

        btnLaunch.setOnClickListener {
            when {
                !Settings.canDrawOverlays(this) -> showOverlayPermissionDialog()
                !mcInstalled -> Toast.makeText(this, "Minecraft Bedrock not installed", Toast.LENGTH_SHORT).show()
                !hasUsageAccess() && !askedUsageAccessBefore() -> showUsageAccessDialog()
                else -> launchMinecraft()
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val hasOverlay = Settings.canDrawOverlays(this)
        if (pendingLaunch && hasOverlay) {
            pendingLaunch = false
            tvStatus.text = "Permission granted!"
            launchMinecraft()
            return
        }
        tvStatus.text = if (hasOverlay) "Ready to launch" else "Overlay permission needed"
    }

    private fun showOverlayPermissionDialog() {
        showPermissionDialog(
            title = "Enable Overlay Access",
            message = "Zen Client needs permission to draw over Minecraft so the bubble and ClickGUI can appear in-game.",
            onGrant = {
                pendingLaunch = true
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            },
            onNotNow = null
        )
    }

    private fun showUsageAccessDialog() {
        markUsageAccessAsked()
        showPermissionDialog(
            title = "Auto-Hide In Other Apps",
            message = "Usage Access lets the bubble auto-hide outside Minecraft and reappear when you switch back. Optional \u2014 Zen Client still works without it.",
            onGrant = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            onNotNow = { launchMinecraft() }
        )
    }

    private fun showPermissionDialog(title: String, message: String, onGrant: () -> Unit, onNotNow: (() -> Unit)?) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_overlay_permission)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.findViewById<TextView>(R.id.tvPermissionTitle).text = title
        dialog.findViewById<TextView>(R.id.tvPermissionMessage).text = message

        dialog.findViewById<Button>(R.id.btnGrantPermission).setOnClickListener {
            dialog.dismiss()
            onGrant()
        }
        dialog.findViewById<Button>(R.id.btnNotNow).setOnClickListener {
            dialog.dismiss()
            onNotNow?.invoke()
        }
        dialog.show()
    }

    private fun launchMinecraft() {
        tvStatus.text = "Launching..."
        startForegroundService(Intent(this, OverlayService::class.java))
        packageManager.getLaunchIntentForPackage(MINECRAFT_PACKAGE)?.let {
            it.addCategory(Intent.CATEGORY_LAUNCHER)
            startActivity(it)
        }
    }

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

    private fun askedUsageAccessBefore(): Boolean =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ASKED_USAGE_ACCESS, false)

    private fun markUsageAccessAsked() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ASKED_USAGE_ACCESS, true).apply()
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
