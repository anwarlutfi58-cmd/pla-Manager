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
        return if (gb >= 1.0) String.format("%.0f GB", gb) else "${mb.toInt()} MB"
    }

    /** Total kapasitas hardware (dibulatkan ke angka pemasaran: 64/128/256 GB) */
    private fun roundToMarketing(totalBytes: Long, stepGb: Double): Int {
        val gb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        // Tambah cadangan sistem ~7% lalu bulatkan ke atas kelipatan step
        val withSystem = gb * 1.07
        return (Math.ceil(withSystem / stepGb) * stepGb).toInt()
    }

    /**
     * Memori Internal: pakai freeSpace (ruang kosong aktual) bukan availableBlocks.
     * Ini cara yang sama dengan File Manager+ sehingga angka terpakai akurat.
     */
    fun getInternalStorageInfo(): String {
        return try {
            val root = Environment.getExternalStorageDirectory()
            val stat = StatFs(root.absolutePath)
            val total = stat.totalBytes                 // kapasitas partisi data
            val free = root.freeSpace                   // ruang kosong AKTUAL (akurat)
            val used = total - free
            val marketingTotal = roundToMarketing(stat.blockCountLong * stat.blockSizeLong, 8.0)
            "${formatSize(used)} / $marketingTotal GB"
        } catch (e: Exception) {
            "Tidak tersedia"
        }
    }

    /** Kartu SD: pakai freeSpace juga agar konsisten & akurat */
    fun getSdCardStorageInfo(context: Context): String {
        val dirs = context.getExternalFilesDirs(null)
        if (dirs.size > 1 && dirs[1] != null) {
            var sd = dirs[1]!!.absolutePath
            val idx = sd.indexOf("/Android")
            if (idx != -1) sd = sd.substring(0, idx)

            return try {
                val sdFile = File(sd)
                val stat = StatFs(sd)
                val total = stat.totalBytes
                val free = sdFile.freeSpace             // ruang kosong AKTUAL
                val used = total - free
                val marketingTotal = roundToMarketing(stat.blockCountLong * stat.blockSizeLong, 16.0)
                "${formatSize(used)} / $marketingTotal GB"
            } catch (e: Exception) {
                "Tidak tersedia"
            }
        }
        return "Tidak ada"
    }
}