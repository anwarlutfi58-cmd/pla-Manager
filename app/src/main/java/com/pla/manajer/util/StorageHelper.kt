package com.pla.manajer.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

object StorageHelper {
    
    // Fungsi format ke GB tanpa desimal aneh (misal 53 GB / 64 GB)
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        
        return if (gb >= 1.0) {
            "${gb.toInt()} GB" // Jika lebih dari 1 GB, tampilkan bulat (contoh: 53 GB)
        } else {
            "${mb.toInt()} MB" // Jika kurang dari 1 GB, tampilkan MB
        }
    }

    // Mendapatkan info Memori Internal
    fun getInternalStorageInfo(): String {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            val used = total - free // Terpakai = Total - Sisa
            
            "${formatSize(used)} / ${formatSize(total)}"
        } catch (e: Exception) {
            "Tidak tersedia"
        }
    }

    // Mendapatkan info Kartu SD
    fun getSdCardStorageInfo(context: Context): String {
        val dirs = context.getExternalFilesDirs(null)
        if (dirs.size > 1 && dirs[1] != null) {
            var sd = dirs[1]!!.absolutePath
            val idx = sd.indexOf("/Android")
            if (idx != -1) sd = sd.substring(0, idx)
            
            return try {
                val stat = StatFs(sd)
                val total = stat.blockCountLong * stat.blockSizeLong
                val free = stat.availableBlocksLong * stat.blockSizeLong
                val used = total - free // Terpakai = Total - Sisa
                
                "${formatSize(used)} / ${formatSize(total)}"
            } catch (e: Exception) {
                "Tidak tersedia"
            }
        }
        return "Tidak ada"
    }
}