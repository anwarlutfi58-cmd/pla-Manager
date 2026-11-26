package com.pla.manajer.util

import android.content.Context
import android.provider.MediaStore

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
            "${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL",
            null, null
        )?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in docExts) {
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    totalSize += size
                    totalCount++
                }
            }
        }
        return Stats(totalSize, totalCount)
    }

    private fun queryDownloadStats(context: Context): Stats {
        var totalSize = 0L
        var totalCount = 0
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.RELATIVE_PATH),
            null, null, null
        )?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val pathIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val path = if (pathIdx >= 0) cursor.getString(pathIdx) ?: "" else ""
                if (path.contains("Download", ignoreCase = true)) {
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
}