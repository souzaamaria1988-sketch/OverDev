package com.zalith.overdev.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object HistoryStore {

    class Link(val url: String, val title: String, val ts: Long)

    private fun file(c: Context): File = File(c.filesDir, "links.json")

    private fun read(c: Context): JSONObject {
        return try {
            val f = file(c)
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun write(c: Context, root: JSONObject) {
        try {
            file(c).writeText(root.toString())
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun add(c: Context, url: String, title: String) {
        if (!url.startsWith("http")) return
        val root = read(c)
        val arr = root.optJSONArray("history") ?: JSONArray()
        if (arr.length() > 0) {
            val last = arr.optJSONObject(arr.length() - 1)
            if (last != null && last.optString("url") == url) {
                last.put("ts", System.currentTimeMillis())
                root.put("history", arr)
                write(c, root)
                return
            }
        }
        arr.put(JSONObject()
            .put("url", url)
            .put("title", if (title.isBlank()) url else title)
            .put("ts", System.currentTimeMillis()))
        while (arr.length() > 500) arr.remove(0)
        root.put("history", arr)
        write(c, root)
    }

    private fun links(c: Context, key: String): List<Link> {
        val out = ArrayList<Link>()
        val arr = read(c).optJSONArray(key) ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Link(o.optString("url"), o.optString("title"), o.optLong("ts", 0L)))
        }
        return out
    }

    @Synchronized
    fun history(c: Context): List<Link> = links(c, "history").asReversed()

    @Synchronized
    fun bookmarks(c: Context): List<Link> = links(c, "bookmarks")

    @Synchronized
    fun isBookmarked(c: Context, url: String): Boolean {
        val arr = read(c).optJSONArray("bookmarks") ?: return false
        for (i in 0 until arr.length()) {
            if (arr.optJSONObject(i)?.optString("url") == url) return true
        }
        return false
    }

    @Synchronized
    fun toggleBookmark(c: Context, url: String, title: String): Boolean {
        val root = read(c)
        val arr = root.optJSONArray("bookmarks") ?: JSONArray()
        var idx = -1
        for (i in 0 until arr.length()) {
            if (arr.optJSONObject(i)?.optString("url") == url) {
                idx = i
                break
            }
        }
        return if (idx >= 0) {
            arr.remove(idx)
            root.put("bookmarks", arr)
            write(c, root)
            false
        } else {
            arr.put(JSONObject()
                .put("url", url)
                .put("title", if (title.isBlank()) url else title)
                .put("ts", System.currentTimeMillis()))
            root.put("bookmarks", arr)
            write(c, root)
            true
        }
    }

    @Synchronized
    fun clearHistory(c: Context) {
        write(c, read(c).put("history", JSONArray()))
    }

    @Synchronized
    fun clearBookmarks(c: Context) {
        write(c, read(c).put("bookmarks", JSONArray()))
    }
}
