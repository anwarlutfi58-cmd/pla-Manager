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
            // Gunakan FileProvider untuk mendapatkan URI
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            // Deteksi MIME Type
            var mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase())
            if (mimeType == null) {
                mimeType = when (file.extension.lowercase()) {
                    "zip" -> "application/zip"
                    "apk" -> "application/vnd.android.package-archive"
                    else -> "*/*"
                }
            }

            // Intent ACTION_SEND dengan izin baca
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                // Tambahkan ClipData agar kompatibel dengan semua aplikasi (WhatsApp, Gmail, dll)
                clipData = ClipData.newUri(context.contentResolver, "File", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(intent, "Bagikan File via"))
        } catch (e: Exception) {
            // Jika FileProvider gagal (karena path SD tidak terdaftar), 
            // kita gunakan fallback Intent dengan ACTION_VIEW atau share manual
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Bagikan File via"))
            } catch (ex: Exception) {
                android.widget.Toast.makeText(context, "Gagal membagikan file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}