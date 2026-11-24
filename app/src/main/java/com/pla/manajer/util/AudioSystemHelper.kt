package com.pla.manajer.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

object AudioSystemHelper {

    fun copyUriToCache(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val tempFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    fun registerToMediaStore(context: Context, file: File, ext: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Audio.Media.MIME_TYPE, if (ext == "aiff") "audio/aiff" else "audio/$ext")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Audio.Media.RELATIVE_PATH, "PlaProjects/Edit_Audio")
                else put(MediaStore.Audio.Media.DATA, file.absolutePath)
            }
            context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        } catch (_: Exception) {}
    }

    fun getOutputFile(ext: String): File {
        val outDir = File(Environment.getExternalStorageDirectory(), "PlaProjects/Edit_Audio")
        if (!outDir.exists()) outDir.mkdirs()
        return File(outDir, "EDIT_${System.currentTimeMillis()}.${ext}")
    }
}