package com.pla.manajer.util

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

object VideoPlayerHelper {
    fun playVideo(activity: AppCompatActivity, file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(file), "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, "Putar Video dengan"))
        } catch (e: Exception) {
            Toast.makeText(activity, "Tidak ada aplikasi pemutar video", Toast.LENGTH_SHORT).show()
        }
    }
}