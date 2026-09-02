package com.zalith.overdev.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zalith.overdev.Prefs
import com.zalith.overdev.R
import com.zalith.overdev.SettingsActivity
import com.zalith.overdev.data.HistoryStore
import java.net.URLEncoder
import java.util.ArrayDeque
import kotlin.math.abs

/**
 * A janela flutuante inteira: header arrastável, barra de endereço,
 * WebView e a bolinha de minimizar. Mudanças de configuração aplicam
 * na hora (listener de prefs).
 */
@SuppressLint("ClickableViewAccessibility")
class BrowserOverlay(private val service: Service) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val context: Context = service.applicationContext
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density

    private val root: View = LayoutInflater.from(context).inflate(R.layout.floating_browser, null)
    private val urlEdit: EditText = root.findViewById(R.id.urlEdit)
    private val webView: WebView = root.findViewById(R.id.webView)
    private val btnBack: TextView = root.findViewById(R.id.btnBack)
    private val btnFwd: TextView = root.findViewById(R.id.btnFwd)
    private val btnStar: TextView = root.findViewById(R.id.btnStar)
    private val btnGo: TextView = root.findViewById(R.id.btnGo)
    private val btnConsole: TextView = root.findViewById(R.id.btnConsole)
    private val btnExpand: TextView = root.findViewById(R.id.btnExpand)
    private val bubble = TextView(context).apply {
        text = "❖"
        textSize = 18f
        setTextColor(0xFFFF5245.toInt())
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xCC141011.toInt())
            setStroke(dp(1.5f), 0xFFE23B2E.toInt())
        }
    }

    private val consoleBuf = ArrayDeque<String>()

    private var expanded = false
    private var minimized = false
    private var lastUrl = ""

    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

    private val params = WindowManager.LayoutParams(
        0, 0,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    private val bubbleParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 200
    }

    // drag da janela
    private var winDragX = 0
    private var winDragY = 0
    private var winStartRawX = 0f
    private var winStartRawY = 0f

    // drag da bolinha
    private var bubbleMoved = false
    private var bubblePlaced = false
    private var bubbleDownAt = 0L
    private var bubbleStartRawX = 0f
    private var bubbleStartRawY = 0f
    private var bubbleBaseX = 0
    private var bubbleBaseY = 0

    init {
        setupWindowControls()
        setupAddressBar()
        setupWebView()
        setupBubble()
    }

    fun attach() {
        expanded = Prefs.startMax(context)
        btnExpand.text = if (expanded) "▢" else "▣"
        applyWindowSize()
        params.x = ((screenW() - params.width).coerceAtLeast(0)) / 2
        params.y = ((screenH() - params.height).coerceAtLeast(0)) / 4
        params.flags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        wm.addView(root, params)
        applySettings()
        Prefs.raw(context).registerOnSharedPreferenceChangeListener(this)
        val home = Prefs.home(context)
        if (home.isNotBlank()) webView.loadUrl(home)
    }

    fun detach() {
        try { Prefs.raw(context).unregisterOnSharedPreferenceChangeListener(this) } catch (e: Exception) {}
        try { wm.removeView(root) } catch (e: IllegalArgumentException) {}
        try { wm.removeView(bubble) } catch (e: IllegalArgumentException) {}
        webView.destroy()
    }

    fun loadUrl(url: String) {
        webView.loadUrl(normalize(url))
    }

    // ── controles da janela ─────────────────────────────

    private fun setupWindowControls() {
        val header: View = root.findViewById(R.id.ovHeader)
        header.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    winDragX = params.x
                    winDragY = params.y
                    winStartRawX = ev.rawX
                    winStartRawY = ev.rawY
                    setFocusable(false)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = winDragX + (ev.rawX - winStartRawX).toInt()
                    params.y = winDragY + (ev.rawY - winStartRawY).toInt()
                    updateSafe()
                    true
                }
                else -> false
            }
        }
        root.findViewById<View>(R.id.btnMin).setOnClickListener { minimize() }
        root.findViewById<View>(R.id.btnExpand).setOnClickListener { toggleExpand() }
        root.findViewById<View>(R.id.btnClose).setOnClickListener { service.stopSelf() }
        root.findViewById<View>(R.id.btnCfg).setOnClickListener {
            context.startActivity(
                Intent(context, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        btnConsole.setOnClickListener { showConsole() }
        root.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_OUTSIDE) {
                setFocusable(false)
                true
            } else false
        }
    }

    private fun toggleExpand() {
        expanded = !expanded
        applyWindowSize()
        btnExpand.text = if (expanded) "▢" else "▣"
        updateSafe()
    }

    private fun minimize() {
        if (minimized) return
        minimized = true
        try { wm.removeView(root) } catch (e: IllegalArgumentException) {}
        bubbleParams.width = dp(Prefs.bubbleDp(context))
        bubbleParams.height = bubbleParams.width
        if (!bubblePlaced) {
            bubbleParams.x = ((screenW() - bubbleParams.width) / 2).coerceAtLeast(0)
            bubbleParams.y = ((screenH() - bubbleParams.height) / 3).coerceAtLeast(0)
        }
        try { wm.addView(bubble, bubbleParams) } catch (e: Exception) {}
    }

    private fun restore() {
        if (!minimized) return
        minimized = false
        try { wm.removeView(bubble) } catch (e: IllegalArgumentException) {}
        try {
            wm.addView(root, params)
        } catch (e: Exception) {
            service.stopSelf()
        }
    }

    // ── bolinha ─────────────────────────────────────────

    private fun setupBubble() {
        bubble.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleMoved = false
                    bubbleDownAt = System.currentTimeMillis()
                    bubbleStartRawX = ev.rawX
                    bubbleStartRawY = ev.rawY
                    bubbleBaseX = bubbleParams.x
                    bubbleBaseY = bubbleParams.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - bubbleStartRawX
                    val dy = ev.rawY - bubbleStartRawY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) bubbleMoved = true
                    if (bubbleMoved) {
                        bubblePlaced = true
                        bubbleParams.x = clamp(bubbleBaseX + dx.toInt(), 0, screenW() - bubbleParams.width)
                        bubbleParams.y = clamp(bubbleBaseY + dy.toInt(), 0, screenH() - bubbleParams.height)
                        updateSafeBubble()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!bubbleMoved && System.currentTimeMillis() - bubbleDownAt < 350) restore()
                    true
                }
                else -> false
            }
        }
    }

    // ── barra de endereço ───────────────────────────────

    private fun setupAddressBar() {
        urlEdit.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) setFocusable(true) }
        urlEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigate()
                true
            } else false
        }
        btnGo.setOnClickListener { navigate() }
        btnBack.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        btnFwd.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        btnStar.setOnClickListener {
            if (lastUrl.startsWith("http")) {
                val added = HistoryStore.toggleBookmark(context, lastUrl, webView.title ?: lastUrl)
                refreshStar()
                Toast.makeText(
                    context,
                    if (added) "favorito adicionado" else "favorito removido",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun navigate() {
        webView.loadUrl(normalize(urlEdit.text.toString()))
        hideKb()
        urlEdit.clearFocus()
        setFocusable(false)
    }

    private fun normalize(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return Prefs.home(context)
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        if (s.contains(" ") || !s.contains(".")) {
            return "https://duckduckgo.com/?q=" + URLEncoder.encode(s, "UTF-8")
        }
        return "https://" + s
    }

    // ── webview ─────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                url?.let {
                    urlEdit.setText(it)
                    lastUrl = it
                }
                refreshNav()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                url ?: return
                val title = view?.title ?: url
                HistoryStore.add(context, url, title)
                refreshStar()
                refreshNav()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
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
        webView.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(context, "downloads desativados neste navegador", Toast.LENGTH_SHORT).show()
        }
        webView.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                hideKb()
                setFocusable(false)
            }
            false
        }
        webView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (webView.canGoBack()) webView.goBack() else minimize()
                true
            } else false
        }
    }

    private fun refreshStar() {
        val marked = lastUrl.startsWith("http") && HistoryStore.isBookmarked(context, lastUrl)
        btnStar.text = if (marked) "★" else "☆"
        btnStar.setTextColor(if (marked) 0xFFFF5245.toInt() else 0xFF9A8F8A.toInt())
    }

    private fun refreshNav() {
        btnBack.alpha = if (webView.canGoBack()) 1f else 0.3f
        btnFwd.alpha = if (webView.canGoForward()) 1f else 0.3f
    }

    // ── console (dev) ───────────────────────────────────

    private fun showConsole() {
        if (consoleBuf.isEmpty()) {
            Toast.makeText(context, "console vazio — nada capturado ainda", Toast.LENGTH_SHORT).show()
            return
        }
        val tv = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(0xFFCFC4B0.toInt())
            setTextIsSelectable(true)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            text = consoleBuf.joinToString("
")
        }
        val sv = ScrollView(context)
        sv.addView(tv)
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("console · " + consoleBuf.size + " linhas")
            .setView(sv)
            .setPositiveButton("limpar") { _, _ -> consoleBuf.clear() }
            .setNegativeButton("fechar", null)
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    // ── prefs ───────────────────────────────────────────

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        applySettings()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applySettings() {
        val s = webView.settings
        s.javaScriptEnabled = Prefs.jsEnabled(context)
        s.blockNetworkImage = Prefs.blockImages(context)
        s.domStorageEnabled = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        if (Build.VERSION.SDK_INT >= 33) {
            s.isAlgorithmicDarkeningAllowed = Prefs.forceDark(context)
        }
        val ua = Prefs.userAgent(context)
        s.userAgentString = when {
            ua.isNotEmpty() -> ua
            Prefs.desktopMode(context) -> DESKTOP_UA
            else -> null
        }
        params.alpha = Prefs.alpha(context)
        if (!expanded) applyWindowSize()
        updateSafe()
        bubbleParams.width = dp(Prefs.bubbleDp(context))
        bubbleParams.height = bubbleParams.width
        updateSafeBubble()
    }

    // ── util ────────────────────────────────────────────

    private fun applyWindowSize() {
        if (expanded) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
        } else {
            params.width = (screenW() * Prefs.widthPct(context)).toInt()
            params.height = (screenH() * Prefs.heightPct(context)).toInt()
        }
    }

    private fun setFocusable(f: Boolean) {
        if (minimized) return
        params.flags = if (f) baseFlags
        else baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        updateSafe()
    }

    private fun updateSafe() {
        if (minimized) return
        try { wm.updateViewLayout(root, params) } catch (e: IllegalArgumentException) {}
    }

    private fun updateSafeBubble() {
        if (!minimized) return
        try { wm.updateViewLayout(bubble, bubbleParams) } catch (e: IllegalArgumentException) {}
    }

    private fun hideKb() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlEdit.windowToken, 0)
    }

    private fun screenW() = context.resources.displayMetrics.widthPixels
    private fun screenH() = context.resources.displayMetrics.heightPixels

    private fun clamp(v: Int, min: Int, max: Int): Int =
        if (max <= min) min else v.coerceIn(min, max)

    private fun dp(v: Int) = (v * density + 0.5f).toInt()
    private fun dp(v: Float) = v * density

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}
