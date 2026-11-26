package com.pla.manajer.util

import android.content.Context
import android.os.Environment
import java.io.File

/** Mesin pemindai folder media ala File Manager+ (gambar, audio, video, dokumen) */
object MediaBrowser {

    val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    val audioExts = setOf("mp3", "wav", "aac", "m4a", "ogg", "flac", "opus", "amr", "mid")
    val videoExts = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "ts")
    val docExts = setOf(
        "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv", "rtf", "odt",
        "epub", "zip", "rar", "7z", "tar", "gz", "apk", "json", "xml", "html", "md",
        "kt", "java", "py", "js", "css", "gradle", "kts", "yml", "yaml", "properties", "log"
    )

    fun extsFor(type: String): Set<String> = when (type) {
        "IMAGE" -> imageExts
        "AUDIO" -> audioExts
        "VIDEO" -> videoExts
        "DOC" -> docExts
        else -> emptySet()
    }

    fun roots(context: Context): List<File> {
        val list = mutableListOf<File>()
        val internal = Environment.getExternalStorageDirectory()
        if (internal.exists()) list.add(internal)
        getSdCard(context)?.let { if (it.exists()) list.add(it) }
        return list
    }

    fun getSdCard(context: Context): File? {
        val dirs = context.getExternalFilesDirs(null)
        if (dirs.size > 1 && dirs[1] != null) {
            var sd = dirs[1]!!.absolutePath
            val idx = sd.indexOf("/Android")
            if (idx != -1) sd = sd.substring(0, idx)
            return File(sd)
        }
        return null
    }

    /** Folder yang berisi media (langsung atau di dalamnya), gabungan internal + SD, nama sama digabung */
    fun findMediaFolders(context: Context, type: String): List<File> {
        val exts = extsFor(type)
        if (exts.isEmpty()) return emptyList()
        val found = LinkedHashMap<String, File>()
        for (root in roots(context)) scan(root, exts, found, 0)
        return found.values.sortedBy { it.name.lowercase() }
    }

    private fun scan(dir: File, exts: Set<String>, found: LinkedHashMap<String, File>, depth: Int) {
        if (depth > 12) return
        val name = dir.name
        if (name.startsWith(".") || name == "Android" || name == "obb") return
        val files = dir.listFiles() ?: return
        var hasDirect = false
        for (f in files) {
            if (f.isFile && f.extension.lowercase() in exts) { hasDirect = true; break }
        }
        if (hasDirect && !found.containsKey(name)) found[name] = dir
        for (f in files) {
            if (f.isDirectory) scan(f, exts, found, depth + 1)
        }
    }

    /** Jumlah file media secara rekursif di dalam folder */
    fun countMedia(dir: File, exts: Set<String>): Int {
        var count = 0
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        var guard = 0
        while (stack.isNotEmpty() && guard < 20000) {
            guard++
            val d = stack.removeLast()
            val files = d.listFiles() ?: continue
            for (f in files) {
                if (f.isFile && f.extension.lowercase() in exts) count++
                else if (f.isDirectory && !f.name.startsWith(".")) stack.addLast(f)
            }
        }
        return count
    }

    /** File contoh untuk thumbnail folder */
    fun firstMediaFile(dir: File, exts: Set<String>): File? {
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        var guard = 0
        while (stack.isNotEmpty() && guard < 5000) {
            guard++
            val d = stack.removeLast()
            val files = d.listFiles() ?: continue
            for (f in files) {
                if (f.isFile && f.extension.lowercase() in exts) return f
            }
            for (f in files) {
                if (f.isDirectory && !f.name.startsWith(".")) stack.addLast(f)
            }
        }
        return null
    }

    /** Isi folder: subfolder yang punya media + file media langsung */
    fun listDirContents(dir: File, exts: Set<String>): Pair<List<File>, List<File>> {
        val files = dir.listFiles() ?: return Pair(emptyList(), emptyList())
        val subDirs = files.filter {
            it.isDirectory && !it.name.startsWith(".") && countMedia(it, exts) > 0
        }.sortedBy { it.name.lowercase() }
        val mediaFiles = files.filter {
            it.isFile && it.extension.lowercase() in exts
        }.sortedBy { it.name.lowercase() }
        return Pair(subDirs, mediaFiles)
    }
}