package com.zalith.overdev

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.zalith.overdev.data.HistoryStore

class SettingsActivity : AppCompatActivity() {

    private lateinit var etHome: EditText
    private lateinit var etUA: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val p = Prefs.raw(this)

        val sAlpha = findViewById<Slider>(R.id.sAlpha)
        val sWidth = findViewById<Slider>(R.id.sWidth)
        val sHeight = findViewById<Slider>(R.id.sHeight)
        val sBubble = findViewById<Slider>(R.id.sBubble)

        sAlpha.value = p.getInt("alpha", 95).toFloat()
        sWidth.value = p.getInt("widthPct", 85).toFloat()
        sHeight.value = p.getInt("heightPct", 55).toFloat()
        sBubble.value = p.getInt("bubbleDp", 56).toFloat()

        sAlpha.addOnChangeListener { _, v, _ -> p.edit().putInt("alpha", v.toInt()).apply() }
        sWidth.addOnChangeListener { _, v, _ -> p.edit().putInt("widthPct", v.toInt()).apply() }
        sHeight.addOnChangeListener { _, v, _ -> p.edit().putInt("heightPct", v.toInt()).apply() }
        sBubble.addOnChangeListener { _, v, _ -> p.edit().putInt("bubbleDp", v.toInt()).apply() }

        sw(R.id.swMax, "startMax", Prefs.startMax(this))
        sw(R.id.swJs, "js", Prefs.jsEnabled(this))
        sw(R.id.swImg, "blockImages", Prefs.blockImages(this))
        sw(R.id.swDesk, "desktop", Prefs.desktopMode(this))
        sw(R.id.swDark, "forceDark", Prefs.forceDark(this))

        etHome = findViewById(R.id.etHome)
        etUA = findViewById(R.id.etUA)
        etHome.setText(Prefs.home(this))
        etUA.setText(Prefs.userAgent(this))

        findViewById<android.view.View>(R.id.btnUaAndroid).setOnClickListener { etUA.setText(UA_ANDROID) }
        findViewById<android.view.View>(R.id.btnUaDesktop).setOnClickListener { etUA.setText(UA_DESKTOP) }
        findViewById<android.view.View>(R.id.btnUaIos).setOnClickListener { etUA.setText(UA_IOS) }
        findViewById<android.view.View>(R.id.btnUaClear).setOnClickListener { etUA.setText("") }

        findViewById<android.view.View>(R.id.btnCache).setOnClickListener { clearCache() }
        findViewById<android.view.View>(R.id.btnCookies).setOnClickListener { clearCookies() }
        findViewById<android.view.View>(R.id.btnHist).setOnClickListener {
            HistoryStore.clearHistory(this)
            toast("histórico limpo")
        }
        findViewById<android.view.View>(R.id.btnFavs).setOnClickListener {
            HistoryStore.clearBookmarks(this)
            toast("favoritos limpos")
        }
    }

    private fun sw(id: Int, key: String, initial: Boolean) {
        val s = findViewById<MaterialSwitch>(id)
        s.isChecked = initial
        s.setOnCheckedChangeListener { _, c -> Prefs.raw(this).edit().putBoolean(key, c).apply() }
    }

    override fun onPause() {
        super.onPause()
        Prefs.raw(this).edit()
            .putString("home", etHome.text.toString().trim())
            .putString("ua", etUA.text.toString().trim())
            .apply()
    }

    private fun clearCache() {
        val wv = WebView(this)
        wv.clearCache(true)
        wv.destroy()
        toast("cache limpo")
    }

    private fun clearCookies() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
        toast("cookies limpos")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val UA_ANDROID =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        const val UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        const val UA_IOS =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
    }
}
