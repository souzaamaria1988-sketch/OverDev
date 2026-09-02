package com.zalith.overdev

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zalith.overdev.overlay.OverlayService

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.install(this)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.tvStatus)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        findViewById<View>(R.id.btnOpen).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "conceda a permissão de sobreposição primeiro", Toast.LENGTH_SHORT).show()
                askPermission()
            } else {
                try {
                    ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
                } catch (t: Throwable) {
                    CrashGuard.record(this, "start-service", t)
                    showCrashLogIfAny()
                }
                finish()
            }
        }
        findViewById<View>(R.id.btnPermission).setOnClickListener { askPermission() }
        findViewById<View>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "overlay parado", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnLibrary).setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
        }
    }

    private fun askPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
    }

    override fun onResume() {
        super.onResume()
        showCrashLogIfAny()
        val ok = Settings.canDrawOverlays(this)
        status.text = if (ok) "concedida — pronto para flutuar" else "negada — toque em conceder permissão"
        status.setTextColor(if (ok) 0xFF8FBF6F.toInt() else 0xFFFF5245.toInt())
    }

    /** Se o app crashou desde a última visita, o motivo aparece aqui. */
    private fun showCrashLogIfAny() {
        val log = CrashGuard.read(this) ?: return
        if (log.isBlank()) return
        val tv = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(0xFFE8E0DC.toInt())
            setTextIsSelectable(true)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            text = log
        }
        val sv = ScrollView(this)
        sv.addView(tv)
        MaterialAlertDialogBuilder(this)
            .setTitle("o app crashou — este é o motivo")
            .setView(sv)
            .setPositiveButton("copiar") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("overdev-crash", log))
                CrashGuard.clear(this)
                Toast.makeText(this, "stack trace copiado — cole para o assistente", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("ok") { _, _ -> CrashGuard.clear(this) }
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
