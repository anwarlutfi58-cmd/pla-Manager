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
    private val onSelectToggle: (File) -> Unit
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
        
        // Atur status Checkbox
        h.b.cbSelect.isChecked = selectedFiles.contains(f)

        // Klik Checkbox = Tandai / Batal Tandai
        h.b.cbSelect.setOnClickListener {
            onSelectToggle(f)
        }

        // Klik List (Baris) = Buka file ATAU Tandai (jika sedang mode pilih)
        h.b.root.setOnClickListener { 
            if (isSelectionMode) {
                onSelectToggle(f)
            } else {
                onOpen(f) 
            }
        }
        
        // Tahan Lama = Masuk ke mode pilih
        h.b.root.setOnLongClickListener { 
            onLong(f)
            true 
        }
    }

    override fun getItemCount() = items.size
}