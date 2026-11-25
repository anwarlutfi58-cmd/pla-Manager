package com.pla.manajer.util

import android.os.Environment
import android.os.StatFs
import java.io.File

object StorageHelper {
    
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (gb >= 1.0) "${gb.toInt()} GB" else "${mb.toInt()} MB"
    }

    private fun getHardwareTotal(context: android.content.Context?): String {
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val gb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            
            // Asumsi Android memakan sekitar 12 GB untuk sistem
            val systemReserve = 12.0
            val hardwareGb = Math.ceil((gb + systemReserve) / 8.0) * 8.0 // Bulatkan ke atas
            
            return "${hardwareGb.toInt()} GB"
        } catch (e: Exception) {
            return "64 GB"
        }
    }

    // Fungsi tanpa Context (Langsung Dipanggil)
    fun getInternalStorageInfo(): String {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            val used = total - free
            
            "${formatSize(used)} / ${getHardwareTotal(null)}"
        } catch (e: Exception) {
            "Tidak tersedia"
        }
    }

    // Fungsi untuk Kartu SD
    fun getSdCardStorageInfo(context: android.content.Context): String {
        val dirs = context.getExternalFilesDirs(null)
        if (dirs.size > 1 && dirs[1] != null) {
            var sd = dirs[1]!!.absolutePath
            val idx = sd.indexOf("/Android")
            if (idx != -1) sd = sd.substring(0, idx)
            
            return try {
                val stat = StatFs(sd)
                val total = stat.blockCountLong * stat.blockSizeLong
                val free = stat.availableBlocksLong * stat.blockSizeLong
                val used = total - free
                
                val gb = total.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val sdHardwareGb = Math.ceil(gb / 16.0) * 16.0 
                
                "${formatSize(used)} / ${sdHardwareGb.toInt()} GB"
            } catch (e: Exception) {
                "Tidak tersedia"
            }
        }
        return "Tidak ada"
    }
}