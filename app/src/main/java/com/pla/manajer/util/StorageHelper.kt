package com.pla.manajer.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

object StorageHelper {

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (gb >= 1.0) String.format("%.1f GB", gb) else String.format("%.1f MB", mb)
    }

    /** Memori Internal: total & terpakai dihitung dari StatFs penuh (seperti File Manager+) */
    fun getInternalStorageInfo(): String {
        return try {
            val root = Environment.getExternalStorageDirectory()
            val stat = StatFs(root.absolutePath)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val freeBytes = stat.freeBlocksLong * stat.blockSizeLong   // freeBlocks, BUKAN availableBlocks
            val usedBytes = totalBytes - freeBytes
            // Total hardware dibulatkan ke angka pemasaran terdekat (64/128/256)
            val marketing = intArrayOf(32, 64, 128, 256, 512, 1024)
            val totalGb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val hardwareGb = marketing.firstOrNull { it >= totalGb } ?: 64
            "${formatSize(usedBytes)} / $hardwareGb GB"
        } catch (e: Exception) {
            "Tidak tersedia"
        }
    }

    /** Kartu SD: cara hitung sama persis */
    fun getSdCardStorageInfo(context: Context): String {
        val dirs = context.getExternalFilesDirs(null)
        if (dirs.size > 1 && dirs[1] != null) {
            var sd = dirs[1]!!.absolutePath
            val idx = sd.indexOf("/Android")
            if (idx != -1) sd = sd.substring(0, idx)
            return try {
                val stat = StatFs(sd)
                val totalBytes = stat.blockCountLong * stat.blockSizeLong
                val freeBytes = stat.freeBlocksLong * stat.blockSizeLong
                val usedBytes = totalBytes - freeBytes
                val marketing = intArrayOf(16, 32, 64, 128, 256, 512, 1024)
                val totalGb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val hardwareGb = marketing.firstOrNull { it >= totalGb } ?: 128
                "${formatSize(usedBytes)} / $hardwareGb GB"
            } catch (e: Exception) {
                "Tidak tersedia"
            }
        }
        return "Tidak ada"
    }
}