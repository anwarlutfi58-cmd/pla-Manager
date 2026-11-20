package com.pla.manajer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pla.manajer.databinding.ItemFileBinding
import com.pla.manajer.util.FileUtils

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
    private val onOpen: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.VH>() {
    private var items: List<MediaItem> = emptyList()

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
        h.b.tvMeta.text = FileUtils.humanSize(item.size)
        
        h.b.btnFav.visibility = android.view.View.GONE
        h.b.cbSelect.visibility = android.view.View.GONE
        
        h.b.root.setOnClickListener { onOpen(item) }
    }

    override fun getItemCount() = items.size
}