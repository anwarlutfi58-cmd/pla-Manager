package com.pla.manajer.util

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Penghitung media RESMI dari database Android (MediaStore).
 * Download dihitung dari ISI FOLDER FISIK (internal + SD), sama seperti File Manager+.
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
                "DOWNLOAD" -> scanDownloadFolders(context)
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
            uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null
        )?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                if (size > 0) { totalSize += size; totalCount++ }
            }
        }
        return Stats(totalSize, totalCount)
    }

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
                    if (size > 0) { totalSize += size; totalCount++ }
                }
            }
        }
        return Stats(totalSize, totalCount)
    }

    /** DOWNLOAD: scan isi folder Download FISIK di memori internal + kartu SD (cara File Manager+) */
    private fun scanDownloadFolders(context: Context): Stats {
        var totalSize = 0L
        var totalCount = 0
        val dirs = mutableListOf<File>()
        // Folder Download resmi memori internal
        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        // Folder Download di kartu SD (jika ada)
        getSdCard(context)?.let { sd ->
            val sdDl = File(sd, Environment.DIRECTORY_DOWNLOADS)
            if (sdDl.exists()) dirs.add(sdDl)
        }
        dirs.forEach { dir ->
            scanRecursive(dir) { f ->
                totalSize += f.length()
                totalCount++
            }
        }
        return Stats(totalSize, totalCount)
    }

    private fun scanRecursive(dir: File, onFile: (File) -> Unit) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isFile) onFile(f)
            else if (f.isDirectory) scanRecursive(f, onFile)  // termasuk folder tersembunyi spt .7934039b
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