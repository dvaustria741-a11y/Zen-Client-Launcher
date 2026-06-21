package com.zen.overlay

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)

        findViewById<Button>(R.id.btnOpenCrashLogs).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Crash Log")
                .setMessage(CrashHandler.readLog(this))
                .setPositiveButton("Close", null)
                .show()
        }

        findViewById<Button>(R.id.btnCloseCrash).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
        }
    }
}
