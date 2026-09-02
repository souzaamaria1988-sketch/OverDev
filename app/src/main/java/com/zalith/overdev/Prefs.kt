package com.zalith.overdev

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "overdev"

    fun raw(c: Context): SharedPreferences = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun jsEnabled(c: Context) = raw(c).getBoolean("js", true)
    fun blockImages(c: Context) = raw(c).getBoolean("blockImages", false)
    fun desktopMode(c: Context) = raw(c).getBoolean("desktop", false)
    fun forceDark(c: Context) = raw(c).getBoolean("forceDark", true)
    fun alpha(c: Context) = raw(c).getInt("alpha", 95) / 100f
    fun widthPct(c: Context) = raw(c).getInt("widthPct", 85) / 100f
    fun heightPct(c: Context) = raw(c).getInt("heightPct", 55) / 100f
    fun bubbleDp(c: Context) = raw(c).getInt("bubbleDp", 56)
    fun startMax(c: Context) = raw(c).getBoolean("startMax", false)
    fun userAgent(c: Context) = raw(c).getString("ua", "") ?: ""
    fun home(c: Context) = raw(c).getString("home", "https://duckduckgo.com") ?: "https://duckduckgo.com"
}
