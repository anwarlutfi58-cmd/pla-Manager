package com.pla.manajer.adapter

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pla.manajer.R
import com.pla.manajer.databinding.ItemMediaBinding
import com.pla.manajer.util.MediaBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaBrowserAdapter(
    private val scope: CoroutineScope,
    private val onOpen: (File) -> Unit,
    private val onFileMenu: (File) -> Unit
) : RecyclerView.Adapter<MediaBrowserAdapter.VH>() {

    data class Row(val file: File, val isDir: Boolean, val count: Int, val thumbSource: File?)

    private var rows: List<Row> = emptyList()
    private var exts: Set<String> = emptySet()
    private val thumbCache = HashMap<String, android.graphics.Bitmap>()

    fun submit(newRows: List<Row>, newExts: Set<String>) {
        rows = newRows
        exts = newExts
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemMediaBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val row = rows[position]
        h.b.tvName.text = row.file.name
        h.b.tvCount.text = if (row.isDir) "(${row.count})" else ""
        h.b.ivThumb.tag = row.file.absolutePath
        h.b.ivThumb.setImageDrawable(null)

        val cached = thumbCache[row.file.absolutePath]
        if (cached != null) {
            h.b.ivThumb.setImageBitmap(cached)
        } else {
            h.b.ivThumb.setImageResource(
                when {
                    row.isDir -> R.drawable.ic_folder
                    row.file.extension.lowercase() in MediaBrowser.audioExts -> R.drawable.ic_music_note
                    else -> R.drawable.ic_file
                }
            )
            val src = row.thumbSource
            if (src != null) {
                val key = row.file.absolutePath
                scope.launch(Dispatchers.IO) {
                    val bmp = loadThumb(src)
                    if (bmp != null) {
                        thumbCache[key] = bmp
                        withContext(Dispatchers.Main) {
                            if (h.b.ivThumb.tag == key) h.b.ivThumb.setImageBitmap(bmp)
                        }
                    }
                }
            }
        }

        h.b.root.setOnClickListener { onOpen(row.file) }
        h.b.root.setOnLongClickListener {
            if (!row.isDir) { onFileMenu(row.file); true } else false
        }
    }

    private fun loadThumb(src: File): android.graphics.Bitmap? {
        val ext = src.extension.lowercase()
        return try {
            if (ext in MediaBrowser.imageExts) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(src.absolutePath, opts)
                var sample = 1
                while (opts.outWidth / sample > 200 || opts.outHeight / sample > 200) sample *= 2
                val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(src.absolutePath, opts2)
            } else if (ext in MediaBrowser.videoExts) {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(src.absolutePath)
                val frame = mmr.frameAtTime
                mmr.release()
                frame
            } else null
        } catch (_: Exception) { null }
    }
}