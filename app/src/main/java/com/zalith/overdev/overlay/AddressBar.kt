package com.zalith.overdev.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.zalith.overdev.Prefs
import com.zalith.overdev.data.HistoryStore
import com.zalith.overdev.util.safeRun
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder

internal class AddressBar(private val ov: BrowserOverlay) {

    init { setup() }

    private fun setup() {
        safeRun {
            ov.urlEdit.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) { navigate(); true } else false
            }
            ov.btnGo.setOnClickListener { safeRun { navigate() } }
            ov.btnBack.setOnClickListener { safeRun { if (ov.webView.canGoBack()) ov.webView.goBack() } }
            ov.btnFwd.setOnClickListener { safeRun { if (ov.webView.canGoForward()) ov.webView.goForward() } }
            ov.btnReload.setOnClickListener { safeRun { ov.webView.reload(); Toast.makeText(ov.context, "recarregando", Toast.LENGTH_SHORT).show() } }
            ov.btnShare.setOnClickListener { safeRun { shareUrl() } }
            ov.btnCopy.setOnClickListener { safeRun { copySelection() } }
            ov.btnPaste.setOnClickListener { safeRun { pasteIntoSite() } }
            ov.btnStar.setOnClickListener { safeRun { toggleStar() } }
        }
    }

    fun load(url: String) { safeRun { ov.webView.loadUrl(normalize(url)) } }

    private fun navigate() {
        safeRun {
            ov.webView.loadUrl(normalize(ov.urlEdit.text.toString()))
            ov.hideKb()
            ov.urlEdit.clearFocus()
            ov.webView.requestFocus()
        }
    }

    private fun toggleStar() {
        safeRun {
            if (!ov.lastUrl.startsWith("http")) return
            val url = ov.lastUrl
            val title = ov.webView.title ?: url
            Thread {
                safeRun {
                    val added = HistoryStore.toggleBookmark(ov.context, url, title)
                    val marked = HistoryStore.isBookmarked(ov.context, url)
                    ov.ui.post { safeRun {
                        ov.paintStar(marked)
                        Toast.makeText(ov.context, if (added) "favorito adicionado" else "favorito removido", Toast.LENGTH_SHORT).show()
                    } }
                }
            }.start()
        }
    }

    private fun shareUrl() {
        safeRun {
            val url = ov.lastUrl
            if (!url.startsWith("http")) { Toast.makeText(ov.context, "sem URL valida", Toast.LENGTH_SHORT).show(); return }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
                putExtra(Intent.EXTRA_SUBJECT, ov.webView.title ?: url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { ov.context.startActivity(Intent.createChooser(intent, "compartilhar via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (e: Exception) { Toast.makeText(ov.context, "nenhum app disponivel", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun copySelection() {
        safeRun {
            ov.webView.evaluateJavascript(SELECTION_JS) { result ->
                safeRun {
                    val sel = unwrapJson(result)
                    if (!sel.isNullOrBlank()) copyToClipboard(sel, "selecao copiada")
                    else if (ov.lastUrl.startsWith("http")) copyToClipboard(ov.lastUrl, "endereco copiado")
                    else Toast.makeText(ov.context, "selecione texto no site", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun pasteIntoSite() {
        safeRun {
            val text = readClipboard()
            if (text.isNullOrEmpty()) { Toast.makeText(ov.context, "transferencia vazia", Toast.LENGTH_LONG).show(); return }
            val quoted = JSONObject.quote(text)
            try {
                ov.webView.evaluateJavascript(PASTE_JS + "(" + quoted + ")") { result ->
                    safeRun {
                        if ("true" == unwrapJson(result)) Toast.makeText(ov.context, "colado no site", Toast.LENGTH_SHORT).show()
                        else { ov.urlEdit.setText(text); Toast.makeText(ov.context, "colado na barra", Toast.LENGTH_SHORT).show() }
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun copyToClipboard(text: String, msg: String) {
        safeRun {
            val cm = ov.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("overdev", text))
            Toast.makeText(ov.context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun readClipboard(): String? {
        return safeRun(null) {
            val cm = ov.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(ov.context)?.toString()?.trim() else null
        }
    }

    private fun unwrapJson(result: String?): String? {
        if (result == null || result == "null") return null
        return safeRun(null) { JSONTokener(result).nextValue()?.toString() }
    }

    private fun normalize(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return Prefs.home(ov.context)
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        if (s.contains(" ") || !s.contains(".")) return "https://duckduckgo.com/?q=" + URLEncoder.encode(s, "UTF-8")
        return "https://" + s
    }

    companion object {
        private const val SELECTION_JS = "(function(){try{var e=document.activeElement;if(e&&e.selectionStart!=null&&e.selectionEnd!=null&&e.selectionEnd>e.selectionStart){return String(e.value).substring(e.selectionStart,e.selectionEnd)}return String(window.getSelection())}catch(x){return ''}})()"
        private const val PASTE_JS = "(function(t){var e=document.activeElement;if(!e||!e.tagName)return false;var tag=e.tagName;if(tag=='TEXTAREA'||(tag=='INPUT'&&!/^(checkbox|radio|file|button|submit|image|reset|hidden)$/.test(e.type||'text'))){var s=e.selectionStart==null?e.value.length:e.selectionStart;var en=e.selectionEnd==null?s:e.selectionEnd;e.value=e.value.substring(0,s)+t+e.value.substring(en);e.dispatchEvent(new Event('input',{bubbles:true}));return true}if(e.isContentEditable){try{document.execCommand('insertText',false,t)}catch(x){}return true}return false})"
    }
}
