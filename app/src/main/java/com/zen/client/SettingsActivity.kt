package com.zen.client

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var contentPanel: FrameLayout
    private lateinit var navGeneral: Button
    private lateinit var navAbout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        contentPanel = findViewById(R.id.contentPanel)
        navGeneral = findViewById(R.id.navGeneral)
        navAbout = findViewById(R.id.navAbout)

        navGeneral.setOnClickListener { showGeneral() }
        navAbout.setOnClickListener { showAbout() }
        findViewById<Button>(R.id.btnReturn).setOnClickListener { finish() }

        showGeneral()
    }

    private fun setNavState(selected: Button, idle: Button) {
        selected.setBackgroundResource(R.drawable.bg_nav_selected)
        idle.setBackgroundResource(R.drawable.bg_nav_idle)
    }

    private fun showGeneral() {
        setNavState(navGeneral, navAbout)
        contentPanel.removeAllViews()

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        root.addView(sectionHeader("Folders"))

        val foldersRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(24) }
        }
        foldersRow.addView(redButton("Open Files") {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(Environment.getExternalStorageDirectory().toString()), "resource/folder")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { startActivity(intent) }
        }.also { btn ->
            (btn.layoutParams as LinearLayout.LayoutParams).apply {
                weight = 1f; marginEnd = dp(12)
            }
        })
        foldersRow.addView(redButton("Open Crash Logs") {
            val dir = getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(dir), "resource/folder")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { startActivity(intent) }
        }.also { btn ->
            (btn.layoutParams as LinearLayout.LayoutParams).apply { weight = 1f }
        })
        root.addView(foldersRow)

        root.addView(sectionHeader("Launcher"))
        root.addView(infoRow("Launcher Version", "Zen Client 1.0"))
        root.addView(infoRow("Minecraft", getMcInfo()))

        scroll.addView(root)
        contentPanel.addView(scroll)
    }

    private fun showAbout() {
        setNavState(navAbout, navGeneral)
        contentPanel.removeAllViews()

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        root.addView(sectionHeader("Credits"))
        root.addView(TextView(this).apply {
            text = "dvaustria741-a11y"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = "Built with ZenOverlay framework"
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 13f
            setPadding(0, 0, 0, dp(24))
        })

        scroll.addView(root)
        contentPanel.addView(scroll)
    }

    private fun sectionHeader(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(12) }
        addView(TextView(this@SettingsActivity).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        })
        addView(View(this@SettingsActivity).apply {
            setBackgroundColor(Color.parseColor("#FFFF2A3C"))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(3))
        })
    }

    private fun infoRow(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_settings_row)
        setPadding(dp(20), dp(16), dp(20), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(8) }
        addView(TextView(this@SettingsActivity).apply {
            text = label; setTextColor(Color.WHITE); textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(this@SettingsActivity).apply {
            text = value; setTextColor(Color.parseColor("#99FFFFFF")); textSize = 14f
        })
    }

    private fun redButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 15f
        setBackgroundResource(R.drawable.bg_button_primary)
        stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(52))
        setOnClickListener { onClick() }
    }

    private fun getMcInfo(): String = try {
        val info = packageManager.getPackageInfo("com.mojang.minecraftpe", 0)
        "Minecraft ${info.versionName} | Zen Client 1.0"
    } catch (e: Exception) { "Minecraft not installed" }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
