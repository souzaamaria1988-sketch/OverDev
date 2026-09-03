package com.zalith.overdev.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import com.zalith.overdev.Prefs
import com.zalith.overdev.R
import com.zalith.overdev.SettingsActivity
import com.zalith.overdev.util.CrashHandler
import com.zalith.overdev.util.safeRun
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class BrowserOverlay(private val service: Service) : SharedPreferences.OnSharedPreferenceChangeListener {

    internal val context: Context = ContextThemeWrapper(service.applicationContext, R.style.Theme_OverDev)
    internal val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    internal val density = context.resources.displayMetrics.density
    internal val ui = Handler(Looper.getMainLooper())

    internal val root: View = LayoutInflater.from(context).inflate(R.layout.floating_browser, null)
    internal val urlEdit: EditText = root.findViewById(R.id.urlEdit)
    internal val webView: WebView = root.findViewById(R.id.webView)
    internal val btnBack: TextView = root.findViewById(R.id.btnBack)
    internal val btnFwd: TextView = root.findViewById(R.id.btnFwd)
    internal val btnReload: TextView = root.findViewById(R.id.btnReload)
    internal val btnStar: TextView = root.findViewById(R.id.btnStar)
    internal val btnShare: TextView = root.findViewById(R.id.btnShare)
    internal val btnGo: TextView = root.findViewById(R.id.btnGo)
    internal val btnConsole: TextView = root.findViewById(R.id.btnConsole)
    internal val btnExpand: TextView = root.findViewById(R.id.btnExpand)
    internal val btnCopy: View = root.findViewById(R.id.btnCopy)
    internal val btnPaste: View = root.findViewById(R.id.btnPaste)
    internal val loadingBar: ProgressBar = root.findViewById(R.id.loadingBar)

    internal val bubble: TextView = TextView(context).apply {
        text = "\u2756"
        textSize = 18f
        setTextColor(0xFFFF5245.toInt())
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xCC141011.toInt())
            setStroke(dp(1.5f).toInt(), 0xFFE23B2E.toInt())
        }
    }

    internal var expanded = false
    internal var minimized = false
    internal var lastUrl = ""

    internal val baseFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

    internal val params = WindowManager.LayoutParams(
        0, 0,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        baseFlags,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    internal val bubbleParams = WindowManager.LayoutParams(
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

    private val webPane = WebPane(this)
    private val bar = AddressBar(this)

    private var winDragX = 0
    private var winDragY = 0
    private var winStartRawX = 0f
    private var winStartRawY = 0f

    private var bubbleMoved = false
    private var bubblePlaced = false
    private var bubbleDownAt = 0L
    private var bubbleStartRawX = 0f
    private var bubbleStartRawY = 0f
    private var bubbleBaseX = 0
    private var bubbleBaseY = 0

    init {
        safeRun { CrashHandler.install(context) }
        setupWindowControls()
        setupBubble()
    }

    fun attach() {
        safeRun {
            expanded = Prefs.startMax(context)
            btnExpand.text = if (expanded) "\u25a2" else "\u25a3"
            applyWindowSize()
            params.x = ((screenW() - params.width).coerceAtLeast(0)) / 2
            params.y = ((screenH() - params.height).coerceAtLeast(0)) / 4
            params.flags = baseFlags
            wm.addView(root, params)
            webView.requestFocus()
            OverlaySettings.apply(this)
            Prefs.raw(context).registerOnSharedPreferenceChangeListener(this)
            val home = Prefs.home(context)
            if (home.isNotBlank()) webView.loadUrl(home)
        }
    }

    fun detach() {
        safeRun {
            try { Prefs.raw(context).unregisterOnSharedPreferenceChangeListener(this) } catch (e: Exception) { }
            try { wm.removeView(root) } catch (e: IllegalArgumentException) { }
            try { wm.removeView(bubble) } catch (e: IllegalArgumentException) { }
            webView.destroy()
        }
    }

    fun loadUrl(url: String) { safeRun { bar.load(url) } }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        safeRun { OverlaySettings.apply(this) }
    }

    private fun setupWindowControls() {
        safeRun {
            val header: View = root.findViewById(R.id.ovHeader)
            header.setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        winDragX = params.x; winDragY = params.y
                        winStartRawX = ev.rawX; winStartRawY = ev.rawY; true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = winDragX + (ev.rawX - winStartRawX).toInt()
                        params.y = winDragY + (ev.rawY - winStartRawY).toInt()
                        updateWindow(); true
                    }
                    else -> false
                }
            }
            root.findViewById<View>(R.id.btnMin).setOnClickListener { safeRun { minimize() } }
            root.findViewById<View>(R.id.btnExpand).setOnClickListener { safeRun { toggleExpand() } }
            root.findViewById<View>(R.id.btnClose).setOnClickListener { safeRun { service.stopSelf() } }
            root.findViewById<View>(R.id.btnCfg).setOnClickListener {
                safeRun {
                    context.startActivity(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            btnConsole.setOnClickListener { safeRun { webPane.showConsole() } }
        }
    }

    private fun toggleExpand() {
        safeRun {
            expanded = !expanded
            applyWindowSize()
            btnExpand.text = if (expanded) "\u25a2" else "\u25a3"
            updateWindow()
        }
    }

    internal fun minimize() {
        safeRun {
            if (minimized) return
            minimized = true
            hideKb()
            try { wm.removeView(root) } catch (e: IllegalArgumentException) { }
            bubbleParams.width = dp(Prefs.bubbleDp(context))
            bubbleParams.height = bubbleParams.width
            if (!bubblePlaced) {
                bubbleParams.x = ((screenW() - bubbleParams.width) / 2).coerceAtLeast(0)
                bubbleParams.y = ((screenH() - bubbleParams.height) / 3).coerceAtLeast(0)
            }
            try { wm.addView(bubble, bubbleParams) } catch (e: Exception) { }
        }
    }

    private fun restore() {
        safeRun {
            if (!minimized) return
            minimized = false
            try { wm.removeView(bubble) } catch (e: IllegalArgumentException) { }
            try { wm.addView(root, params); webView.requestFocus() }
            catch (e: Exception) { service.stopSelf() }
        }
    }

    private fun setupBubble() {
        safeRun {
            bubble.setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        bubbleMoved = false; bubbleDownAt = System.currentTimeMillis()
                        bubbleStartRawX = ev.rawX; bubbleStartRawY = ev.rawY
                        bubbleBaseX = bubbleParams.x; bubbleBaseY = bubbleParams.y; true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - bubbleStartRawX; val dy = ev.rawY - bubbleStartRawY
                        if (abs(dx) > dp(6) || abs(dy) > dp(6)) bubbleMoved = true
                        if (bubbleMoved) {
                            bubblePlaced = true
                            bubbleParams.x = clamp(bubbleBaseX + dx.toInt(), 0, screenW() - bubbleParams.width)
                            bubbleParams.y = clamp(bubbleBaseY + dy.toInt(), 0, screenH() - bubbleParams.height)
                            updateBubble()
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
    }

    internal fun refreshNav() {
        safeRun {
            btnBack.alpha = if (webView.canGoBack()) 1f else 0.3f
            btnFwd.alpha = if (webView.canGoForward()) 1f else 0.3f
        }
    }

    internal fun paintStar(marked: Boolean) {
        safeRun {
            btnStar.text = if (marked) "\u2605" else "\u2606"
            btnStar.setTextColor(if (marked) 0xFFFF5245.toInt() else 0xFF9A8F8A.toInt())
        }
    }

    internal fun showLoading(show: Boolean) {
        safeRun { loadingBar.visibility = if (show) View.VISIBLE else View.GONE }
    }

    internal fun updateProgress(progress: Int) {
        safeRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) loadingBar.setProgress(progress, true)
            else loadingBar.progress = progress
            loadingBar.visibility = if (progress < 100) View.VISIBLE else View.GONE
        }
    }

    internal fun hideKb() {
        safeRun {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(urlEdit.windowToken, 0)
            imm.hideSoftInputFromWindow(webView.windowToken, 0)
        }
    }

    internal fun applyWindowSize() {
        safeRun {
            if (expanded) { params.width = WindowManager.LayoutParams.MATCH_PARENT; params.height = WindowManager.LayoutParams.MATCH_PARENT }
            else { params.width = (screenW() * Prefs.widthPct(context)).toInt(); params.height = (screenH() * Prefs.heightPct(context)).toInt() }
        }
    }

    internal fun updateWindow() {
        safeRun { if (minimized) return; try { wm.updateViewLayout(root, params) } catch (e: IllegalArgumentException) { } }
    }

    internal fun updateBubble() {
        safeRun { if (!minimized) return; try { wm.updateViewLayout(bubble, bubbleParams) } catch (e: IllegalArgumentException) { } }
    }

    internal fun screenW() = safeRun(0) { context.resources.displayMetrics.widthPixels }
    internal fun screenH() = safeRun(0) { context.resources.displayMetrics.heightPixels }
    internal fun clamp(v: Int, min: Int, max: Int): Int = if (max <= min) min else v.coerceIn(min, max)
    internal fun dp(v: Int) = safeRun(0) { (v * density + 0.5f).toInt() }
    internal fun dp(v: Float) = safeRun(0f) { v * density }
}
