package com.zalith.overdev

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zalith.overdev.overlay.OverlayService

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.tvStatus)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        findViewById<android.view.View>(R.id.btnOpen).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "conceda a permissão de sobreposição primeiro", Toast.LENGTH_SHORT).show()
                askPermission()
            } else {
                ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
                finish()
            }
        }
        findViewById<android.view.View>(R.id.btnPermission).setOnClickListener { askPermission() }
        findViewById<android.view.View>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "overlay parado", Toast.LENGTH_SHORT).show()
        }
        findViewById<android.view.View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.btnLibrary).setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
        }
    }

    private fun askPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    override fun onResume() {
        super.onResume()
        val ok = Settings.canDrawOverlays(this)
        status.text = if (ok) "concedida — pronto para flutuar" else "negada — toque em conceder permissão"
        status.setTextColor(if (ok) 0xFF8FBF6F.toInt() else 0xFFFF5245.toInt())
    }
}
