package com.zalith.overdev.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.zalith.overdev.Prefs
import com.zalith.overdev.data.HistoryStore
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder

/**
 * Barra de endereço: navegação, voltar/avançar, favorito e os botões
 * de copiar (seleção do site) e colar (no campo focado do site).
 */
internal class AddressBar(private val ov: BrowserOverlay) {

    init {
        setup()
    }

    private fun setup() {
        ov.urlEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigate()
                true
            } else false
        }
        ov.btnGo.setOnClickListener { navigate() }
        ov.btnBack.setOnClickListener {
            if (ov.webView.canGoBack()) ov.webView.goBack()
        }
        ov.btnFwd.setOnClickListener {
            if (ov.webView.canGoForward()) ov.webView.goForward()
        }
        ov.btnCopy.setOnClickListener { copySelection() }
        ov.btnPaste.setOnClickListener { pasteIntoSite() }
        ov.btnStar.setOnClickListener { toggleStar() }
    }

    fun load(url: String) {
        ov.webView.loadUrl(normalize(url))
    }

    private fun navigate() {
        ov.webView.loadUrl(normalize(ov.urlEdit.text.toString()))
        ov.hideKb()
        ov.urlEdit.clearFocus()
        ov.webView.requestFocus()
    }

    private fun toggleStar() {
        if (!ov.lastUrl.startsWith("http")) return
        val url = ov.lastUrl
        val title = ov.webView.title ?: url
        Thread {
            val added = HistoryStore.toggleBookmark(ov.context, url, title)
            val marked = HistoryStore.isBookmarked(ov.context, url)
            ov.ui.post {
                ov.paintStar(marked)
                Toast.makeText(
                    ov.context,
                    if (added) "favorito adicionado" else "favorito removido",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    private fun copySelection() {
        try {
            ov.webView.evaluateJavascript(SELECTION_JS) { result ->
                val sel = unwrapJson(result)
                if (!sel.isNullOrBlank()) {
                    copyToClipboard(sel, "seleção do site copiada")
                } else if (ov.lastUrl.startsWith("http")) {
                    copyToClipboard(ov.lastUrl, "nada selecionado — endereço copiado")
                } else {
                    Toast.makeText(
                        ov.context,
                        "selecione texto no site (toque longo) e toque em copiar",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) { }
    }

    private fun pasteIntoSite() {
        val text = readClipboard()
        if (text.isNullOrEmpty()) {
            Toast.makeText(ov.context, "transferência vazia ou bloqueada pelo sistema", Toast.LENGTH_LONG).show()
            return
        }
        val quoted = JSONObject.quote(text)
        try {
            ov.webView.evaluateJavascript(PASTE_JS + "(" + quoted + ")") { result ->
                val pastedInSite = "true" == unwrapJson(result)
                if (pastedInSite) {
                    Toast.makeText(ov.context, "colado no site", Toast.LENGTH_SHORT).show()
                } else {
                    ov.urlEdit.setText(text)
                    Toast.makeText(ov.context, "sem campo focado — colado na barra de endereço", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) { }
    }

    private fun copyToClipboard(text: String, msg: String) {
        try {
            val cm = ov.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("overdev", text))
            Toast.makeText(ov.context, msg, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ov.context, "não consegui copiar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readClipboard(): String? {
        return try {
            val cm = ov.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(ov.context)?.toString()?.trim()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun unwrapJson(result: String?): String? {
        if (result == null || result == "null") return null
        return try {
            JSONTokener(result).nextValue()?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun normalize(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return Prefs.home(ov.context)
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        if (s.contains(" ") || !s.contains(".")) {
            return "https://duckduckgo.com/?q=" + URLEncoder.encode(s, "UTF-8")
        }
        return "https://" + s
    }

    companion object {
        private const val SELECTION_JS =
            "(function(){try{var e=document.activeElement;" +
            "if(e&&e.selectionStart!=null&&e.selectionEnd!=null&&e.selectionEnd>e.selectionStart){" +
            "return String(e.value).substring(e.selectionStart,e.selectionEnd)}" +
            "return String(window.getSelection())}catch(x){return ''}})()"

        private const val PASTE_JS =
            "(function(t){var e=document.activeElement;" +
            "if(!e||!e.tagName)return false;" +
            "var tag=e.tagName;" +
            "if(tag=='TEXTAREA'||(tag=='INPUT'&&!/^(checkbox|radio|file|button|submit|image|reset|hidden)$/.test(e.type||'text'))){" +
            "var s=e.selectionStart==null?e.value.length:e.selectionStart;" +
            "var en=e.selectionEnd==null?s:e.selectionEnd;" +
            "e.value=e.value.substring(0,s)+t+e.value.substring(en);" +
            "e.dispatchEvent(new Event('input',{bubbles:true}));" +
            "return true}" +
            "if(e.isContentEditable){try{document.execCommand('insertText',false,t)}catch(x){}return true}" +
            "return false})"
    }
}
