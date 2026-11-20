package com.pla.manajer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pla.manajer.data.Favorite
import com.pla.manajer.databinding.ItemFavoriteBinding

class FavoriteAdapter(
    private val onOpen: (Favorite) -> Unit,
    private val onRemove: (Favorite) -> Unit
) : RecyclerView.Adapter<FavoriteAdapter.VH>() {
    private var items: List<Favorite> = emptyList()
    fun submit(l: List<Favorite>) { items = l; notifyDataSetChanged() }
    inner class VH(val b: ItemFavoriteBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(p: ViewGroup, v: Int): VH =
        VH(ItemFavoriteBinding.inflate(LayoutInflater.from(p.context), p, false))
    override fun onBindViewHolder(h: VH, pos: Int) {
        val f = items[pos]
        h.b.tvName.text = f.name
        h.b.tvPath.text = f.path
        h.b.root.setOnClickListener { onOpen(f) }
        h.b.btnRemove.setOnClickListener { onRemove(f) }
    }
    override fun getItemCount() = items.size
}