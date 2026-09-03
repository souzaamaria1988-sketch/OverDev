package com.zalith.overdev.overlay

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebSettings
import com.zalith.overdev.Prefs

/**
 * Aplica as preferências na janela e no WebView — no attach e a cada
 * mudança nas Configurações.
 */
internal object OverlaySettings {

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    fun apply(ov: BrowserOverlay) {
        val s = ov.webView.settings
        s.javaScriptEnabled = Prefs.jsEnabled(ov.context)
        s.blockNetworkImage = Prefs.blockImages(ov.context)
        s.domStorageEnabled = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        applyAlgorithmicDarkening(s, Prefs.forceDark(ov.context))
        val ua = Prefs.userAgent(ov.context)
        s.userAgentString = when {
            ua.isNotEmpty() -> ua
            Prefs.desktopMode(ov.context) -> DESKTOP_UA
            else -> null
        }
        ov.params.alpha = Prefs.alpha(ov.context)
        if (!ov.expanded) ov.applyWindowSize()
        ov.updateWindow()
        ov.bubbleParams.width = ov.dp(Prefs.bubbleDp(ov.context))
        ov.bubbleParams.height = ov.bubbleParams.width
        ov.updateBubble()
    }

    private fun applyAlgorithmicDarkening(s: WebSettings, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < 33) return
        val names = arrayOf("setAlgorithmicDarkeningAllowed", "setIsAlgorithmicDarkeningAllowed")
        for (name in names) {
            try {
                val method = s.javaClass.getMethod(name, java.lang.Boolean.TYPE)
                method.invoke(s, enabled)
                return
            } catch (e: NoSuchMethodException) {
                // tenta o próximo nome
            } catch (e: Exception) {
                return
            }
        }
    }
}
