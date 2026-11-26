package com.pla.manajer.util

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Penjelajah media berbasis MediaStore (database resmi Android).
 * Cara kerja sama dengan File Manager+: akurat, cepat, tanpa scan folder manual.
 */
object MediaBrowser {

    val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    val audioExts = setOf("mp3", "wav", "aac", "m4a", "ogg", "flac", "opus", "amr", "mid")
    val videoExts = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "ts")
    val docExts = setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv", "epub", "rtf", "odt")

    fun extsFor(type: String): Set<String> = when (type) {
        "IMAGE" -> imageExts
        "AUDIO" -> audioExts
        "VIDEO" -> videoExts
        "DOC" -> docExts
        else -> emptySet()
    }

    /** Ambil SEMUA file media dari database Android sekaligus (cepat & akurat) */
    fun queryAllMediaFiles(context: Context, type: String): List<File> {
        val result = mutableListOf<File>()
        try {
            when (type) {
                "IMAGE" -> queryUri(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, result, null)
                "AUDIO" -> queryUri(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, result, null)
                "VIDEO" -> queryUri(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, result, null)
                "DOC" -> queryUri(context, MediaStore.Files.getContentUri("external"), result, docExts)
            }
        } catch (_: Exception) {}
        return result.filter { it.exists() }
    }

    private fun queryUri(context: Context, uri: android.net.Uri, out: MutableList<File>, extFilter: Set<String>?) {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
            null, null, null
        )?.use { cursor ->
            val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val path = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                if (path.isNullOrEmpty() || size <= 0) continue
                if (extFilter != null) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in extFilter) continue
                }
                // Abaikan folder sistem & aplikasi
                if (path.contains("/Android/data/") || path.contains("/Android/obb/")) continue
                out.add(File(path))
            }
        }
    }

    /** Folder tingkat atas yang berisi media — dikelompokkan dari database, bukan scan manual */
    fun findMediaFolders(context: Context, type: String): List<File> {
        val files = queryAllMediaFiles(context, type)
        val folderMap = LinkedHashMap<String, File>()
        for (f in files) {
            val parent = f.parentFile ?: continue
            if (parent.name.startsWith(".")) continue
            val key = parent.absolutePath
            if (!folderMap.containsKey(key)) folderMap[key] = parent
        }
        return folderMap.values.sortedBy { it.name.lowercase() }
    }

    /** Jumlah file media LANGSUNG di folder ini (dari database, akurat) */
    fun countMediaInDir(allFiles: List<File>, dir: File): Int {
        return allFiles.count { it.parentFile?.absolutePath == dir.absolutePath }
    }

    /** File contoh untuk thumbnail */
    fun firstMediaInDir(allFiles: List<File>, dir: File): File? {
        return allFiles.firstOrNull { it.parentFile?.absolutePath == dir.absolutePath }
    }

    /**
     * Isi folder: HANYA subfolder yang punya media LANGSUNG + file media langsung.
     * Tidak ada lagi folder asing yang ikut tampil.
     */
    fun listDirContents(allFiles: List<File>, dir: File): Pair<List<File>, List<File>> {
        val directFiles = allFiles.filter { it.parentFile?.absolutePath == dir.absolutePath }
            .sortedBy { it.name.lowercase() }
        val subDirsWithMedia = directFiles.mapNotNull { null } // placeholder, dihitung di bawah
        val subDirs = dir.listFiles()?.filter { sub ->
            sub.isDirectory && !sub.name.startsWith(".") &&
            allFiles.any { it.parentFile?.absolutePath == sub.absolutePath }
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
        return Pair(subDirs, directFiles)
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
}