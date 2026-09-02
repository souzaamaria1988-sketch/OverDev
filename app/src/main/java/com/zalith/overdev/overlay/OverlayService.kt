package com.zalith.overdev.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zalith.overdev.CrashGuard
import com.zalith.overdev.MainActivity
import com.zalith.overdev.R

class OverlayService : Service() {

    companion object { const val CHANNEL = "overlay" }

    private var overlay: BrowserOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        createChannel()
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_overlay)
            .setContentTitle("OverDev ativo")
            .setContentText("navegador flutuante em execução")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        // nunca mais "app fecha sem explicação": qualquer falha aqui vira
        // crash.log e o serviço sai limpo. Reabra o app para ler o motivo.
        try {
            overlay = BrowserOverlay(this)
            overlay?.attach()
        } catch (t: Throwable) {
            CrashGuard.record(this, "overlay", t)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url")
        if (url != null) {
            try {
                overlay?.loadUrl(url)
            } catch (t: Throwable) {
                CrashGuard.record(this, "overlay-url", t)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            overlay?.detach()
        } catch (t: Throwable) {
            CrashGuard.record(this, "overlay-destroy", t)
        }
        overlay = null
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "overlay", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
