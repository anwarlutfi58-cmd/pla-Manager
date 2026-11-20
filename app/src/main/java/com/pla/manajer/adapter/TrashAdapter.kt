package com.pla.manajer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pla.manajer.data.TrashItem
import com.pla.manajer.databinding.ItemTrashBinding

class TrashAdapter(
    private val onRestore: (TrashItem) -> Unit,
    private val onDelete: (TrashItem) -> Unit
) : RecyclerView.Adapter<TrashAdapter.VH>() {
    private var items: List<TrashItem> = emptyList()
    fun submit(l: List<TrashItem>) { items = l; notifyDataSetChanged() }
    inner class VH(val b: ItemTrashBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, v: Int): VH =
        VH(ItemTrashBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) {
        val t = items[pos]
        h.b.tvName.text = t.name
        h.b.tvPath.text = t.originalPath
        h.b.btnRestore.setOnClickListener { onRestore(t) }
        h.b.btnDelete.setOnClickListener { onDelete(t) }
    }
    override fun getItemCount() = items.size
}