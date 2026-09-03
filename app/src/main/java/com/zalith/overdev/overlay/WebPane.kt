package com.zalith.overdev.overlay

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zalith.overdev.data.HistoryStore
import com.zalith.overdev.util.safeRun
import java.util.ArrayDeque

internal class WebPane(private val ov: BrowserOverlay) {

    private val consoleBuf = ArrayDeque<String>()

    init { setup() }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setup() {
        safeRun {
            ov.webView.apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    allowFileAccess = true
                    allowContentAccess = true
                    mediaPlaybackRequiresUserGesture = false
                }
                isLongClickable = true
                isFocusable = true
                isFocusableInTouchMode = true
                setOnTouchListener { _, _ -> false }
            }
        }

        safeRun {
            ov.webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    safeRun {
                        url?.let { ov.urlEdit.setText(it); ov.lastUrl = it }
                        ov.refreshNav()
                        ov.showLoading(true)
                    }
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    safeRun {
                        ov.showLoading(false)
                        url ?: return
                        val title = view?.title ?: url
                        Thread {
                            safeRun {
                                HistoryStore.add(ov.context, url, title)
                                val marked = url.startsWith("http") && HistoryStore.isBookmarked(ov.context, url)
                                ov.ui.post { safeRun { ov.paintStar(marked); ov.refreshNav() } }
                            }
                        }.start()
                        injectSelectionFix(view)
                    }
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
            }
        }

        safeRun {
            ov.webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    safeRun {
                        val cm = consoleMessage ?: return true
                        val src = cm.sourceId() ?: ""
                        val short = src.substringAfterLast('/')
                        consoleBuf.addLast("[" + cm.messageLevel().name + "] " + cm.message() + " (" + short + ":" + cm.lineNumber() + ")")
                        if (consoleBuf.size > 250) consoleBuf.removeFirst()
                    }
                    return true
                }
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    safeRun { ov.updateProgress(newProgress) }
                }
            }
        }

        safeRun {
            ov.webView.setDownloadListener { _, _, _, _, _ ->
                safeRun { Toast.makeText(ov.context, "downloads desativados", Toast.LENGTH_SHORT).show() }
            }
            ov.webView.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    safeRun { if (ov.webView.canGoBack()) ov.webView.goBack() else ov.minimize() }
                    true
                } else false
            }
        }
    }

    private fun injectSelectionFix(view: WebView?) {
        safeRun {
            view ?: return
            val js = "(function(){document.addEventListener('mousedown',function(e){" +
                "if(e.target.tagName==='INPUT'||e.target.tagName==='TEXTAREA'||e.target.isContentEditable)return;" +
                "var s=window.getSelection();if(s&&s.toString().length>0)e.preventDefault();" +
                "},true);})();" 
            view.evaluateJavascript(js, null)
        }
    }

    fun showConsole() {
        safeRun {
            if (consoleBuf.isEmpty()) {
                Toast.makeText(ov.context, "console vazio", Toast.LENGTH_SHORT).show()
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
                .setTitle("console - " + consoleBuf.size + " linhas")
                .setView(sv)
                .setPositiveButton("limpar") { _, _ -> safeRun { consoleBuf.clear() } }
                .setNegativeButton("fechar", null)
                .create()
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()
        }
    }

    fun reload() { safeRun { ov.webView.reload() } }
}
