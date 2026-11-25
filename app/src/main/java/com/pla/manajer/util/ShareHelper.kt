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
            // Dapatkan URI file (mendukung memori internal dan SD Card)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            // Deteksi MIME Type secara cerdas
            var mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase())
            
            if (mimeType == null || mimeType == "*/*") {
                mimeType = when (file.extension.lowercase()) {
                    "zip" -> "application/zip"
                    "apk" -> "application/vnd.android.package-archive"
                    "rar" -> "application/x-rar-compressed"
                    "pdf" -> "application/pdf"
                    "doc", "docx" -> "application/msword"
                    "xls", "xlsx" -> "application/vnd.ms-excel"
                    "ppt", "pptx" -> "application/vnd.ms-powerpoint"
                    "txt" -> "text/plain"
                    "xml" -> "text/xml"
                    "kt" -> "text/x-kotlin"
                    else -> "*/*"
                }
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "File", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(intent, "Bagikan File via"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Gagal membagikan: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}