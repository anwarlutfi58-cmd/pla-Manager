package com.pla.manajer.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pla.manajer.R
import com.pla.manajer.databinding.ItemFileBinding
import com.pla.manajer.util.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileAdapter(
    private val onOpen: (File) -> Unit,
    private val onLong: (File) -> Unit,
    private val onSelectToggle: (File) -> Unit,
    private val onMoreOptions: (File) -> Unit = {}
) : RecyclerView.Adapter<FileAdapter.VH>() {
    private var items: List<File> = emptyList()
    private var showDateTime = false
    var displayNames: Map<String, String> = emptyMap()

    var isSelectionMode = false
    var isRootMode = false
    var hideCheckboxAndMore = false
    val selectedFiles = mutableSetOf<File>()
    var fileCounts: Map<String, Int> = emptyMap()

    fun submit(list: List<File>) { items = list; notifyDataSetChanged() }
    fun setDateTimeMode(showTime: Boolean) { showDateTime = showTime }

    inner class VH(val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val f = items[position]
        h.b.tvName.text = displayNames[f.absolutePath] ?: f.name

        val pattern = if (showDateTime) "dd MMM yyyy HH:mm" else "dd MMM yyyy"
        val dateStr = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(f.lastModified()))

        if (f.isDirectory) {
            val count = fileCounts[f.absolutePath] ?: (f.listFiles()?.size ?: 0)
            h.b.tvMeta.text = "$count item"
            h.b.ivIcon.setImageResource(R.drawable.ic_folder)
            h.b.ivIcon.contentDescription = "Folder"
        } else {
            h.b.tvMeta.text = "${FileUtils.humanSize(f.length())} • $dateStr"
            h.b.ivIcon.setImageResource(R.drawable.ic_file)
            h.b.ivIcon.contentDescription = "File ${f.extension}"
        }

        // ATURAN: kategori media = tanpa centang & titik tiga. File manager = centang WAJIB.
        if (isRootMode || hideCheckboxAndMore) {
            h.b.cbSelect.visibility = View.GONE
            h.b.btnMore.visibility = View.GONE
        } else {
            h.b.cbSelect.visibility = View.VISIBLE
            h.b.btnMore.visibility = View.VISIBLE
            h.b.cbSelect.contentDescription = "Tandai ${f.name}"
        }

        val isSelected = selectedFiles.contains(f)
        h.b.cbSelect.isChecked = isSelected

        if (isSelected) h.b.root.setBackgroundColor(Color.parseColor("#8090C0C0"))
        else h.b.root.setBackgroundColor(Color.TRANSPARENT)

        // Deskripsi lengkap untuk TalkBack
        h.b.root.contentDescription = buildString {
            append(if (f.isDirectory) "Folder " else "File ")
            append(f.name)
            if (!f.isDirectory) append(", ${FileUtils.humanSize(f.length())}, $dateStr")
            if (isSelected) append(", ditandai")
        }

        h.b.cbSelect.setOnClickListener { onSelectToggle(f) }
        h.b.btnMore.setOnClickListener { onMoreOptions(f) }
        h.b.root.setOnClickListener { if (isSelectionMode) onSelectToggle(f) else onOpen(f) }
        // Tekan tahan = mulai menandai (sama seperti centang), berlaku juga di mode kategori utk file
        h.b.root.setOnLongClickListener {
            if (isRootMode && f.isDirectory) return@setOnLongClickListener false
            onLong(f); true
        }
    }

    override fun getItemCount() = items.size
}