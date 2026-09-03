package com.zalith.overdev.util

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

object CrashHandler {
    private val initialized = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    fun install(context: Context) {
        if (initialized.getAndSet(true)) return
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            logCrash(throwable)
            handler.post {
                try { showErrorDialog(context, throwable) } catch (e: Exception) { logCrash(e) }
            }
        }
    }

    private fun logCrash(throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            println("OVERDEV CRASH CAPTURED: " + throwable.message)
        } catch (e: Exception) { }
    }

    private fun showErrorDialog(context: Context, throwable: Throwable) {
        try {
            AlertDialog.Builder(context)
                .setTitle("Erro Recuperado")
                .setMessage("O app encontrou um erro mas foi recuperado.\n\n" + (throwable.message ?: "desconhecido"))
                .setPositiveButton("OK", null)
                .setCancelable(true)
                .show()
        } catch (e: Exception) { }
    }
}

inline fun safeRun(block: () -> Unit) {
    try { block() } catch (e: Throwable) {
        try { println("OVERDEV SAFE RUN ERROR: " + e.message) } catch (x: Exception) { }
    }
}

inline fun <T> safeRun(defaultValue: T, block: () -> T): T {
    return try { block() } catch (e: Throwable) {
        try { println("OVERDEV SAFE RUN ERROR: " + e.message) } catch (x: Exception) { }
        defaultValue
    }
}
