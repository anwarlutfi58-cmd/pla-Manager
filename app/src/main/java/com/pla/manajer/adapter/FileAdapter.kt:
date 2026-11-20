package com.pla.manajer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pla.manajer.R
import com.pla.manajer.databinding.ItemFileBinding
import com.pla.manajer.util.FileUtils
import java.io.File

class FileAdapter(
    private val onOpen: (File) -> Unit,
    private val onLong: (File) -> Unit,
    private val onFavorite: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {
    private var items: List<File> = emptyList()
    
    var isSelectionMode = false
    val selectedFiles = mutableSetOf<File>()

    fun submit(list: List<File>) { 
        items = list; 
        notifyDataSetChanged() 
    }

    inner class VH(val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val f = items[position]
        h.b.tvName.text = f.name
        h.b.tvMeta.text = if (f.isDirectory) "Folder" else FileUtils.humanSize(f.length())
        h.b.ivIcon.setImageResource(if (f.isDirectory) R.drawable.ic_folder else R.drawable.ic_file)
        
        // Logika Tampilan Mode Pilih
        h.b.cbSelect.visibility = if (isSelectionMode) android.view.View.VISIBLE else android.view.View.GONE
        h.b.cbSelect.isChecked = selectedFiles.contains(f)
        h.b.btnFav.visibility = if (isSelectionMode) android.view.View.GONE else android.view.View.VISIBLE

        h.b.root.setOnClickListener { 
            if (isSelectionMode) {
                toggleSelection(f, h)
            } else {
                onOpen(f) 
            }
        }
        
        h.b.root.setOnLongClickListener { 
            if (!isSelectionMode) {
                onLong(f)
            } else {
                toggleSelection(f, h)
            }
            true 
        }
        
        h.b.btnFav.setOnClickListener { onFavorite(f) }
    }

    private fun toggleSelection(f: File, h: VH) {
        if (selectedFiles.contains(f)) {
            selectedFiles.remove(f)
            h.b.cbSelect.isChecked = false
        } else {
            selectedFiles.add(f)
            h.b.cbSelect.isChecked = true
        }
        
        if (selectedFiles.isEmpty()) {
            isSelectionMode = false
        }
        
        notifyDataSetChanged()
        (h.b.root.context as? com.pla.manajer.FileManagerActivity)?.updateSelectionUi()
    }

    override fun getItemCount() = items.size
}