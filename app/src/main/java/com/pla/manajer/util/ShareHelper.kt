package com.pla.manajer.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {
    fun shareFile(context: Context, file: File) {
        try {
            // Gunakan authority yang sudah terdaftar di manifest Anda
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            // Deteksi tipe file secara otomatis
            var mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase())
            
            if (mimeType == null) {
                mimeType = when (file.extension.lowercase()) {
                    "zip" -> "application/zip"
                    "apk" -> "application/vnd.android.package-archive"
                    "rar" -> "application/x-rar-compressed"
                    else -> "*/*"
                }
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                // Tambahkan ClipData agar kompatibel dengan aplikasi lain (WhatsApp, Telegram, dll)
                clipData = ClipData.newRawUri("File", uri)
                // Berikan izin baca ke aplikasi penerima
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(intent, "Bagikan File via"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Gagal membagikan: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}