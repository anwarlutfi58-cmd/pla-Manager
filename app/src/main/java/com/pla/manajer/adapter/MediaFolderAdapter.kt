package com.pla.manajer.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pla.manajer.databinding.ItemMediaFolderBinding

data class MediaFolder(
    val id: String,
    val name: String,
    val coverUri: String,
    val count: Int,
    val latestDate: Long,
    val totalSize: Long
)

class MediaFolderAdapter(
    private val onOpen: (MediaFolder) -> Unit
) : RecyclerView.Adapter<MediaFolderAdapter.VH>() {
    private var items: List<MediaFolder> = emptyList()

    fun submit(list: List<MediaFolder>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemMediaFolderBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMediaFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val folder = items[position]
        h.b.tvFolderName.text = folder.name
        h.b.tvCount.text = "(${folder.count})"
        
        try {
            val inputStream = h.itemView.context.contentResolver.openInputStream(Uri.parse(folder.coverUri))
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            h.b.ivCover.setImageBitmap(bitmap)
            inputStream?.close()
        } catch (e: Exception) {
            h.b.ivCover.setImageResource(com.pla.manajer.R.drawable.ic_image)
        }

        h.b.root.setOnClickListener { onOpen(folder) }
    }

    override fun getItemCount() = items.size
}