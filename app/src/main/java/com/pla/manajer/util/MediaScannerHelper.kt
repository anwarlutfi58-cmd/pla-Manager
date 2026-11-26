package com.pla.manajer.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Penghitung media RESMI dari database Android (MediaStore).
 * Sumber data sama dengan File Manager+ sehingga angka akurat & konsisten.
 */
object MediaScannerHelper {
    data class Stats(val size: Long, val count: Int)

    fun getCategoryStats(context: Context, type: String): Stats {
        return try {
            when (type) {
                "IMAGE" -> queryStats(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                "AUDIO" -> queryStats(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                "VIDEO" -> queryStats(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                "DOC" -> queryDocStats(context)
                "DOWNLOAD" -> queryDownloadStats(context)
                else -> Stats(0L, 0)
            }
        } catch (e: Exception) {
            Stats(0L, 0)
        }
    }

    private fun queryStats(context: Context, uri: android.net.Uri): Stats {
        var totalSize = 0L
        var totalCount = 0
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.SIZE),
            null, null, null
        )?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                if (size > 0) {
                    totalSize += size
                    totalCount++
                }
            }
        }
        return Stats(totalSize, totalCount)
    }

    /** Dokumen: hanya format dokumen sungguhan (bukan file proyek/kode) */
    private fun queryDocStats(context: Context): Stats {
        val docExts = setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv", "epub", "rtf", "odt")
        var totalSize = 0L
        var totalCount = 0
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in docExts) {
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    if (size > 0) {
                        totalSize += size
                        totalCount++
                    }
                }
            }
        }
        return Stats(totalSize, totalCount)
    }

    /**
     * Download: HANYA folder Download resmi di memori internal + kartu SD.
     * Pakai MediaStore.Downloads (API 29+) atau scan path Download eksplisit (API <29).
     * Tidak lagi menangkap folder "Download" milik aplikasi/cache.
     */
    private fun queryDownloadStats(context: Context): Stats {
        var totalSize = 0L
        var totalCount = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: sumber resmi folder Download
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns.SIZE),
                null, null, null
            )?.use { cursor ->
                val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext()) {
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    if (size > 0) {
                        totalSize += size
                        totalCount++
                    }
                }
            }
        } else {
            // API <29: scan hanya folder Download resmi di internal + SD
            val downloadDirs = mutableListOf<File>()
            downloadDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            getSdCard(context)?.let { sd ->
                val sdDownload = File(sd, Environment.DIRECTORY_DOWNLOADS)
                if (sdDownload.exists()) downloadDirs.add(sdDownload)
            }
            downloadDirs.forEach { dir -> scanFolder(dir) { f -> totalSize += f.length(); totalCount++ } }
        }

        return Stats(totalSize, totalCount)
    }

    private fun scanFolder(dir: File, onFile: (File) -> Unit) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isFile) onFile(f)
            else if (f.isDirectory && !f.name.startsWith(".")) scanFolder(f, onFile)
        }
    }

    private fun getSdCard(context: Context): File? {
        val dirs = context.getExternalFilesDirs(null)
        if (dirs.size > 1 && dirs[1] != null) {
            var sd = dirs[1]!!.absolutePath
            val idx = sd.indexOf("/Android")
            if (idx != -1) sd = sd.substring(0, idx)
            return File(sd)
        }
        return null
    }
}