package com.pla.manajer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pla.manajer.databinding.ActivityCloudBinding

class CloudActivity : AppCompatActivity() {
    private lateinit var b: ActivityCloudBinding

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityCloudBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBack.setOnClickListener { finish() }

        b.cardDrive.setOnClickListener { openCloud("https://drive.google.com") }
        b.cardDropbox.setOnClickListener { openCloud("https://www.dropbox.com") }
        b.cardOneDrive.setOnClickListener { openCloud("https://onedrive.live.com") }
        b.cardMega.setOnClickListener { openCloud("https://mega.nz") }
    }

    private fun openCloud(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak ada browser atau aplikasi untuk membuka", Toast.LENGTH_SHORT).show()
        }
    }
}