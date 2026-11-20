package com.pla.manajer

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pla.manajer.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.cardStorage.setOnClickListener   { startActivity(Intent(this, FileManagerActivity::class.java)) }
        b.cardProjects.setOnClickListener  { startActivity(Intent(this, ProjectManagerActivity::class.java)) }
        b.cardFavorites.setOnClickListener { startActivity(Intent(this, FavoritesActivity::class.java)) }
        b.cardTrash.setOnClickListener     { startActivity(Intent(this, TrashActivity::class.java)) }
        b.cardSettings.setOnClickListener  { startActivity(Intent(this, SettingsActivity::class.java)) }

        // KATEGORI MEDIA (LANGSUNG TAMPILKAN FILE, BUKAN FOLDER)
        b.cardImages.setOnClickListener    { openMedia("IMAGE") }
        b.cardVideos.setOnClickListener    { openMedia("VIDEO") }
        b.cardAudio.setOnClickListener     { openMedia("AUDIO") }
        
        // DOKUMEN & UNDUHAN TETAP PAKAI FILE MANAGER
        b.cardDocuments.setOnClickListener { openCategory(Environment.DIRECTORY_DOCUMENTS) }
        b.cardDownloads.setOnClickListener { openCategory(Environment.DIRECTORY_DOWNLOADS) }
        
        b.cardApps.setOnClickListener      { startActivity(Intent(this, AppManagerActivity::class.java)) }
        b.cardCloud.setOnClickListener     { Toast.makeText(this, "Integrasi Cloud segera hadir!", Toast.LENGTH_LONG).show() }
        b.cardSdCard.setOnClickListener    { openSdCard() }
    }

    private fun openMedia(type: String) {
        val intent = Intent(this, MediaManagerActivity::class.java)
        intent.putExtra("media_type", type)
        startActivity(intent)
    }

    private fun openCategory(dirName: String) {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val path = "$rootPath/$dirName"
        val intent = Intent(this, FileManagerActivity::class.java)
        intent.putExtra("initial_path", path)
        startActivity(intent)
    }

    private fun openSdCard() {
        val dirs = getExternalFilesDirs(null)
        if (dirs.size > 1 && dirs[1] != null) {
            var sdPath = dirs[1]!!.absolutePath
            val idx = sdPath.indexOf("/Android")
            if (idx != -1) sdPath = sdPath.substring(0, idx)
            
            val intent = Intent(this, FileManagerActivity::class.java)
            intent.putExtra("initial_path", sdPath)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Kartu SD tidak terdeteksi", Toast.LENGTH_SHORT).show()
        }
    }
}