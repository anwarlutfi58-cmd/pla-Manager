package com.pla.manajer.adapter

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pla.manajer.databinding.ItemAppBinding

class AppAdapter(
    private val pm: PackageManager,
    private val onOpen: (ApplicationInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {
    
    private var items: List<ApplicationInfo> = emptyList()

    fun submit(list: List<ApplicationInfo>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val app = items[position]
        
        // Ambil Nama Aplikasi dan Ikon dari sistem
        val appName = pm.getApplicationLabel(app).toString()
        val appIcon = pm.getApplicationIcon(app)
        
        h.b.tvAppName.text = appName
        h.b.tvPackageName.text = app.packageName
        h.b.ivAppIcon.setImageDrawable(appIcon)
        
        h.b.root.setOnClickListener { onOpen(app) }
    }

    override fun getItemCount() = items.size
}