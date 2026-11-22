package com.pla.manajer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pla.manajer.R
import com.pla.manajer.databinding.ItemFileBinding
import com.pla.manajer.util.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaItem(
    val id: Long,
    val name: String,
    val path: String,
    val size: Long,
    val dateModified: Long,
    val uriString: String,
    val mimeType: String
)

class MediaAdapter(
    private val onOpen: (MediaItem) -> Unit,
    private val onLong: (MediaItem) -> Unit,
    private val onSelectToggle: (MediaItem) -> Unit,
    private val onMoreOptions: (MediaItem) -> Unit = {}
) : RecyclerView.Adapter<MediaAdapter.VH>() {
    private var items: List<MediaItem> = emptyList()

    var isSelectionMode = false
    val selectedFiles = mutableSetOf<MediaItem>()

    fun submit(list: List<MediaItem>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.b.tvName.text = item.name

        val sdf = SimpleDateFormat("dd MMM yyyy HH.mm", Locale.getDefault())
        val dateString = sdf.format(Date(item.dateModified))
        h.b.tvMeta.text = "${FileUtils.humanSize(item.size)} • $dateString"

        val iconRes = when {
            item.mimeType.startsWith("audio") -> R.drawable.ic_music_note
            item.mimeType.startsWith("video") -> R.drawable.ic_video
            item.mimeType.startsWith("image") -> R.drawable.ic_image
            else -> R.drawable.ic_file
        }
        h.b.ivIcon.setImageResource(iconRes)

        h.b.cbSelect.isChecked = selectedFiles.contains(item)

        h.b.cbSelect.setOnClickListener { onSelectToggle(item) }
        h.b.btnMore.setOnClickListener { onMoreOptions(item) }

        h.b.root.setOnClickListener {
            if (isSelectionMode) onSelectToggle(item) else onOpen(item)
        }

        h.b.root.setOnLongClickListener {
            if (!isSelectionMode) onLong(item)
            true
        }
    }

    override fun getItemCount() = items.size
}