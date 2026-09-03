package com.zalith.overdev.overlay

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zalith.overdev.data.HistoryStore
import java.util.ArrayDeque

/**
 * O WebView da janela: cliente de páginas, captura do console do
 * JavaScript, bloqueio de download e botão-voltar do aparelho.
 */
internal class WebPane(private val ov: BrowserOverlay) {

    private val consoleBuf = ArrayDeque<String>()

    init {
        setup()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setup() {
        ov.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                url?.let {
                    ov.urlEdit.setText(it)
                    ov.lastUrl = it
                }
                ov.refreshNav()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                url ?: return
                val title = view?.title ?: url
                Thread {
                    HistoryStore.add(ov.context, url, title)
                    val marked = url.startsWith("http") && HistoryStore.isBookmarked(ov.context, url)
                    ov.ui.post {
                        ov.paintStar(marked)
                        ov.refreshNav()
                    }
                }.start()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }
        ov.webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val cm = consoleMessage ?: return super.onConsoleMessage(consoleMessage)
                val src = cm.sourceId() ?: ""
                val short = src.substringAfterLast('/')
                consoleBuf.addLast(
                    "[" + cm.messageLevel().name + "] " + cm.message() +
                            " (" + short + ":" + cm.lineNumber() + ")"
                )
                if (consoleBuf.size > 250) consoleBuf.removeFirst()
                return true
            }
        }
        ov.webView.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(ov.context, "downloads desativados neste navegador", Toast.LENGTH_SHORT).show()
        }
        ov.webView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (ov.webView.canGoBack()) ov.webView.goBack() else ov.minimize()
                true
            } else false
        }
    }

    fun showConsole() {
        if (consoleBuf.isEmpty()) {
            Toast.makeText(ov.context, "console vazio — nada capturado ainda", Toast.LENGTH_SHORT).show()
            return
        }
        val tv = TextView(ov.context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(0xFFCFC4B0.toInt())
            setTextIsSelectable(true)
            setPadding(ov.dp(14), ov.dp(10), ov.dp(14), ov.dp(10))
            text = consoleBuf.joinToString("\n")
        }
        val sv = ScrollView(ov.context)
        sv.addView(tv)
        val dialog = MaterialAlertDialogBuilder(ov.context)
            .setTitle("console · " + consoleBuf.size + " linhas")
            .setView(sv)
            .setPositiveButton("limpar") { _, _ -> consoleBuf.clear() }
            .setNegativeButton("fechar", null)
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }
}
