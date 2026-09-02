package com.zalith.overdev

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Gravador de crash: qualquer exceção não tratada (na activity ou no
 * serviço do overlay) vira crash.log ANTES do processo morrer. Na
 * próxima abertura a MainActivity mostra o rastro com botão de
 * copiar — o diagnóstico deixa de ser adivinhação.
 */
object CrashGuard {

    private const val FILE = "crash.log"

    fun install(context: Context) {
        val appCtx = context.applicationContext
        val systemHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(appCtx, thread.name, throwable)
            } catch (ignored: Exception) {
                // se até isso falhou, não há mais nada a fazer
            }
            systemHandler?.uncaughtException(thread, throwable)
        }
    }

    /** Para exceções capturadas antes de derrubar o app (ex: overlay). */
    fun record(context: Context, where: String, throwable: Throwable) {
        try {
            write(context.applicationContext, where, throwable)
        } catch (ignored: Exception) {
        }
    }

    fun read(context: Context): String? {
        return try {
            val f = File(context.filesDir, FILE)
            if (f.exists()) f.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE).delete()
        } catch (e: Exception) {
        }
    }

    private fun write(appCtx: Context, where: String, throwable: Throwable) {
        val text = buildString {
            append("onde: ").append(where).append('\n')
            append("android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(')').append('\n')
            append("aparelho: ").append(Build.MANUFACTURER)
                .append(' ').append(Build.MODEL).append('\n')
            append('\n')
            append(Log.getStackTraceString(throwable))
        }
        appCtx.filesDir.mkdirs()
        File(appCtx.filesDir, FILE).writeText(text)
    }
}
