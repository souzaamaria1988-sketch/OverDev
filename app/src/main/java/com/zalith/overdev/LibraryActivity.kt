package com.zalith.overdev

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zalith.overdev.data.HistoryStore
import com.zalith.overdev.overlay.OverlayService

class LibraryActivity : AppCompatActivity() {

    private var mode = 0
    private val items = ArrayList<HistoryStore.Link>()
    private val adapter = LinkAdapter()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        findViewById<RecyclerView>(R.id.links).apply {
            layoutManager = LinearLayoutManager(this@LibraryActivity)
            adapter = this@LibraryActivity.adapter
            itemAnimator = null
        }
        empty = findViewById(R.id.empty)

        findViewById<TextView>(R.id.tabFav).setOnClickListener { setMode(0) }
        findViewById<TextView>(R.id.tabHist).setOnClickListener { setMode(1) }
        findViewById<TextView>(R.id.btnClear).setOnClickListener { clearCurrent() }
        setMode(0)
    }

    private fun setMode(m: Int) {
        mode = m
        findViewById<TextView>(R.id.tabFav).setTextColor(if (m == 0) RED else DIM)
        findViewById<TextView>(R.id.tabHist).setTextColor(if (m == 1) RED else DIM)
        findViewById<TextView>(R.id.btnClear).text = if (m == 0) "limpar favoritos" else "limpar histórico"
        load()
    }

    private fun load() {
        Thread {
            val list = if (mode == 0) HistoryStore.bookmarks(this) else HistoryStore.history(this)
            ui.post {
                items.clear()
                items.addAll(list)
                adapter.notifyDataSetChanged()
                empty.text = if (items.isEmpty()) "nada aqui ainda" else ""
            }
        }.start()
    }

    private fun clearCurrent() {
        if (mode == 0) HistoryStore.clearBookmarks(this) else HistoryStore.clearHistory(this)
        load()
    }

    private fun open(l: HistoryStore.Link) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "conceda a permissão de sobreposição primeiro", Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(this, OverlayService::class.java)
        i.putExtra("url", l.url)
        ContextCompat.startForegroundService(this, i)
    }

    private fun fmtAgo(ts: Long): String {
        val m = (System.currentTimeMillis() - ts) / 60000L
        return when {
            m < 1 -> "agora"
            m < 60 -> m.toString() + " min"
            m < 1440 -> (m / 60).toString() + "h"
            else -> (m / 1440).toString() + "d"
        }
    }

    private inner class LinkAdapter : RecyclerView.Adapter<LinkAdapter.VH>() {

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_link, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val l = items[pos]
            h.title.text = if (l.title.isBlank()) l.url else l.title
            h.url.text = l.url
            h.time.text = fmtAgo(l.ts)
            h.itemView.setOnClickListener { open(l) }
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvTitle)
            val url: TextView = v.findViewById(R.id.tvUrl)
            val time: TextView = v.findViewById(R.id.tvTime)
        }
    }

    companion object {
        private val RED = 0xFFFF5245.toInt()
        private val DIM = 0xFF9A8F8A.toInt()
    }
}
