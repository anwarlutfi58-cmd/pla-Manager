package com.pla.manajer.util

import android.content.Context
import android.os.Environment
import java.io.File

object MediaScannerHelper {
    data class Stats(val size: Long, val count: Int)

    fun getCategoryStats(context: Context, type: String): Stats {
        var totalSize = 0L
        var totalCount = 0

        val internal = Environment.getExternalStorageDirectory()
        val sd = getSdCard(context)

        fun scan(dir: File) {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    if (!f.name.startsWith(".") && f.name != "Android") scan(f)
                } else {
                    val ext = f.extension.lowercase()
                    val isMatch = when (type) {
                        "IMAGE" -> ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
                        "AUDIO" -> ext in listOf("mp3", "wav", "aac", "m4a", "ogg", "flac", "opus")
                        "VIDEO" -> ext in listOf("mp4", "mkv", "avi", "mov", "webm")
                        "DOC" -> ext in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx")
                        "DOWNLOAD" -> f.absolutePath.contains("/Download/", ignoreCase = true)
                        else -> false
                    }
                    if (isMatch) {
                        totalSize += f.length()
                        totalCount++
                    }
                }
            }
        }

        if (internal.exists()) scan(internal)
        sd?.let { if (it.exists()) scan(it) }
        return Stats(totalSize, totalCount)
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