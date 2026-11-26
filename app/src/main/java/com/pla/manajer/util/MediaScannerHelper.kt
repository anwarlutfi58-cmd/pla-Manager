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
        val sd = MediaBrowser.getSdCard(context)

        fun scan(dir: File) {
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    if (!f.name.startsWith(".") && f.name != "Android") scan(f)
                } else {
                    val ext = f.extension.lowercase()
                    val isMatch = when (type) {
                        "IMAGE" -> ext in MediaBrowser.imageExts
                        "AUDIO" -> ext in MediaBrowser.audioExts
                        "VIDEO" -> ext in MediaBrowser.videoExts
                        "DOC" -> ext in MediaBrowser.docExts
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
}