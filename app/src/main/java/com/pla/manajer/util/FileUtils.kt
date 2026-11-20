package com.pla.manajer.util

import android.os.Environment
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtils {

    fun internalRoot(): File = Environment.getExternalStorageDirectory()

    fun listFiles(dir: File): List<File> {
        val files = dir.listFiles() ?: return emptyList()
        return files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun createSmartPath(baseDir: File, path: String): File {
        val cleaned = path.trim().trim('/')
        if (cleaned.isEmpty()) return baseDir
        val target = File(baseDir, cleaned)
        target.parentFile?.let { if (!it.exists()) it.mkdirs() }
        if (!target.exists()) {
            if (path.endsWith("/")) target.mkdirs() else target.createNewFile()
        }
        return target
    }

    fun copy(src: File, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { copy(it, File(dst, it.name)) }
        } else {
            dst.parentFile?.mkdirs()
            src.copyTo(dst, overwrite = true)
        }
    }

    fun move(src: File, dst: File) {
        dst.parentFile?.mkdirs()
        if (!src.renameTo(dst)) { copy(src, dst); deleteRecursive(src) }
    }

    fun deleteRecursive(f: File) {
        if (f.isDirectory) f.listFiles()?.forEach { deleteRecursive(it) }
        f.delete()
    }

    fun zip(src: File, destZip: File) {
        ZipOutputStream(destZip.outputStream().buffered()).use { zos ->
            fun add(f: File, base: String) {
                val name = if (base.isEmpty()) f.name else "$base/${f.name}"
                if (f.isDirectory) f.listFiles()?.forEach { add(it, name) }
                else {
                    zos.putNextEntry(ZipEntry(name))
                    f.inputStream().copyTo(zos)
                    zos.closeEntry()
                }
            }
            if (src.isDirectory) src.listFiles()?.forEach { add(it, src.name) } else add(src, "")
        }
    }

    fun unzip(zipFile: File, destDir: File) {
        destDir.mkdirs()
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var e: ZipEntry? = zis.nextEntry
            while (e != null) {
                val out = File(destDir, e.name)
                if (e.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
    }

    fun humanSize(bytes: Long): String {
        val kb = 1024.0
        return when {
            bytes < kb -> "$bytes B"
            bytes < kb * kb -> String.format("%.1f KB", bytes / kb)
            bytes < kb * kb * kb -> String.format("%.1f MB", bytes / kb / kb)
            else -> String.format("%.1f GB", bytes / kb / kb / kb)
        }
    }
}