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

        // --- 1. BAGIAN PENYIMPANAN ---
        b.cardInternal.setOnClickListener { 
            openFileManager(Environment.getExternalStorageDirectory().absolutePath) 
        }
        b.cardSdCard.setOnClickListener { openSdCard() }

        // --- 2. BAGIAN KATEGORI MEDIA ---
        b.cardImages.setOnClickListener { openMedia("IMAGE") }
        b.cardAudio.setOnClickListener { openMedia("AUDIO") }
        b.cardVideos.setOnClickListener { openMedia("VIDEO") }
        b.cardDocuments.setOnClickListener { openFileManager(Environment.DIRECTORY_DOCUMENTS) }
        b.cardDownloads.setOnClickListener { openFileManager(Environment.DIRECTORY_DOWNLOADS) }
        b.cardApps.setOnClickListener { startActivity(Intent(this, AppManagerActivity::class.java)) }

        // --- 3. BAGIAN FITUR UTAMA ---
        b.cardProjects.setOnClickListener { startActivity(Intent(this, ProjectManagerActivity::class.java)) }
        b.cardFavorites.setOnClickListener { startActivity(Intent(this, FavoritesActivity::class.java)) }
        b.cardTrash.setOnClickListener { startActivity(Intent(this, TrashActivity::class.java)) }
        b.cardSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.cardCloud.setOnClickListener { startActivity(Intent(this, CloudActivity::class.java)) }
        b.cardGithub.setOnClickListener { startActivity(Intent(this, GithubBuilderActivity::class.java)) }

        // --- 4. BAGIAN EDIT (PLA Edit Foto, Audio, Video) ---
        b.cardEditImage.setOnClickListener { 
            pickImageLauncher.launch("image/*") 
        }
        b.cardEditAudio.setOnClickListener { 
            pickAudioLauncher.launch("audio/*") 
        }
        b.cardEditVideo.setOnClickListener { 
            startActivity(Intent(this, VideoEditorActivity::class.java)) 
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = Prefs(this)

        // Logika Sembunyikan/Tampilkan Tombol Berdasarkan Pengaturan
        if (prefs.showImageEditor) b.cardEditImage.visibility = View.VISIBLE else b.cardEditImage.visibility = View.GONE
        if (prefs.showAudioEditor) b.cardEditAudio.visibility = View.VISIBLE else b.cardEditAudio.visibility = View.GONE
        if (prefs.showVideoEditor) b.cardEditVideo.visibility = View.VISIBLE else b.cardEditVideo.visibility = View.GONE
        if (prefs.showGithubBuilder) b.cardGithub.visibility = View.VISIBLE else b.cardGithub.visibility = View.GONE
    }

    // --- FUNGSI PENDUKUNG ---

    private fun openFileManager(path: String) {
        val intent = Intent(this, FileManagerActivity::class.java)
        intent.putExtra("initial_path", path)
        startActivity(intent)
    }

    private fun openMedia(type: String) {
        val intent = Intent(this, MediaManagerActivity::class.java)
        intent.putExtra("media_type", type)
        startActivity(intent)
    }

    private fun openSdCard() {
        val dirs = getExternalFilesDirs(null)
        if (dirs.size > 1 && dirs[1] != null) {
            var sdPath = dirs[1]!!.absolutePath
            val idx = sdPath.indexOf("/Android")
            if (idx != -1) sdPath = sdPath.substring(0, idx)
            openFileManager(sdPath)
        } else {
            Toast.makeText(this, "Kartu SD tidak terdeteksi", Toast.LENGTH_SHORT).show()
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
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            val intent = Intent(this, targetActivity)
            intent.putExtra("file_path", tempFile.absolutePath)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}