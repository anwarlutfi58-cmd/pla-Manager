package com.pla.manajer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.pla.manajer.databinding.ActivityMainBinding
import com.pla.manajer.util.Prefs
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleSelectedFile(it, ImageEditorActivity::class.java) }
    }

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleSelectedFile(it, AudioEditorActivity::class.java) }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Menu Utama
        b.cardStorage.setOnClickListener   { startActivity(Intent(this, FileManagerActivity::class.java)) }
        b.cardProjects.setOnClickListener  { startActivity(Intent(this, ProjectManagerActivity::class.java)) }
        b.cardFavorites.setOnClickListener { startActivity(Intent(this, FavoritesActivity::class.java)) }
        b.cardTrash.setOnClickListener     { startActivity(Intent(this, TrashActivity::class.java)) }
        b.cardSettings.setOnClickListener  { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.cardImages.setOnClickListener    { openMedia("IMAGE") }
        b.cardVideos.setOnClickListener    { openMedia("VIDEO") }
        b.cardAudio.setOnClickListener     { openMedia("AUDIO") }
        b.cardDocuments.setOnClickListener { openCategory(Environment.DIRECTORY_DOCUMENTS) }
        b.cardDownloads.setOnClickListener { openCategory(Environment.DIRECTORY_DOWNLOADS) }
        b.cardApps.setOnClickListener      { startActivity(Intent(this, AppManagerActivity::class.java)) }
        b.cardCloud.setOnClickListener     { startActivity(Intent(this, CloudActivity::class.java)) }
        b.cardSdCard.setOnClickListener    { openSdCard() }

        // ==========================================
        // LOGIKA MENAMPILKAN TOMBOL AJAIB DI BERANDA
        // ==========================================
        val prefs = Prefs(this)
        
        // Jika salah satu menyala, tampilkan judul "Alat Tambahan" dan Grid-nya
        if (prefs.showImageEditor || prefs.showAudioEditor || prefs.showVideoEditor || prefs.showGithubBuilder) {
            b.tvToolsTitle.visibility = View.VISIBLE
            b.gridTools.visibility = View.VISIBLE
        }

        // Tampilkan tombol sesuai pengaturan
        if (prefs.showImageEditor) b.cardEditImage.visibility = View.VISIBLE
        if (prefs.showAudioEditor) b.cardEditAudio.visibility = View.VISIBLE
        if (prefs.showVideoEditor) b.cardEditVideo.visibility = View.VISIBLE
        if (prefs.showGithubBuilder) b.cardGithub.visibility = View.VISIBLE

        // Aksi Tombol Ajaib
        b.cardEditImage.setOnClickListener { pickImageLauncher.launch("image/*") }
        b.cardEditAudio.setOnClickListener { pickAudioLauncher.launch("audio/*") }
        b.cardEditVideo.setOnClickListener { 
            // Langsung buka VideoEditorActivity
            startActivity(Intent(this, VideoEditorActivity::class.java)) 
        }
        b.cardGithub.setOnClickListener { 
            startActivity(Intent(this, GithubBuilderActivity::class.java)) 
        }
    }

    private fun handleSelectedFile(uri: Uri, targetActivity: Class<*>) {
        try {
            var fileName = "temp_file"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) fileName = cursor.getString(nameIndex)
            }

            val tempFile = File(cacheDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val intent = Intent(this, targetActivity)
            intent.putExtra("file_path", tempFile.absolutePath)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMedia(type: String) {
        val intent = Intent(this, MediaManagerActivity::class.java)
        intent.putExtra("media_type", type)
        startActivity(intent)
    }

    private fun openCategory(dirName: String) {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val intent = Intent(this, FileManagerActivity::class.java)
        intent.putExtra("initial_path", "$rootPath/$dirName")
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