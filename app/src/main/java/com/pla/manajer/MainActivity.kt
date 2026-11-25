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
import androidx.lifecycle.lifecycleScope
import com.pla.manajer.databinding.ActivityMainBinding
import com.pla.manajer.util.Prefs
import com.pla.manajer.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        // 1. SETUP TOMBOL KLIK
        b.cardInternal.setOnClickListener { openFileManager(Environment.getExternalStorageDirectory().absolutePath) }
        b.cardSdCard.setOnClickListener { openSdCard() }
        b.cardImages.setOnClickListener { openMedia("IMAGE") }
        b.cardAudio.setOnClickListener { openMedia("AUDIO") }
        b.cardVideos.setOnClickListener { openMedia("VIDEO") }
        b.cardDocuments.setOnClickListener { openFileManager(Environment.DIRECTORY_DOCUMENTS) }
        b.cardDownloads.setOnClickListener { openFileManager(Environment.DIRECTORY_DOWNLOADS) }
        b.cardApps.setOnClickListener { startActivity(Intent(this, AppManagerActivity::class.java)) }
        b.cardProjects.setOnClickListener { startActivity(Intent(this, ProjectManagerActivity::class.java)) }
        b.cardFavorites.setOnClickListener { startActivity(Intent(this, FavoritesActivity::class.java)) }
        b.cardTrash.setOnClickListener { startActivity(Intent(this, TrashActivity::class.java)) }
        b.cardSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.cardCloud.setOnClickListener { startActivity(Intent(this, CloudActivity::class.java)) }
        b.cardGithub.setOnClickListener { startActivity(Intent(this, GithubBuilderActivity::class.java)) }
        b.cardEditImage.setOnClickListener { pickImageLauncher.launch("image/*") }
        b.cardEditAudio.setOnClickListener { pickAudioLauncher.launch("audio/*") }
        b.cardEditVideo.setOnClickListener { startActivity(Intent(this, VideoEditorActivity::class.java)) }

        // 2. LOGIKA CERDAS: SEMBUNYIKAN TEKS 0 MB SAAT MEMUAT
        b.tvImageStats.text = ""
        b.tvAudioStats.text = ""
        b.tvVideoStats.text = ""
        b.tvDocStats.text = ""
        b.tvDownloadStats.text = ""
        b.tvAppStats.text = ""

        // 3. PENYIMPANAN: INSTAN (TANPA MEMINDAI)
        b.tvInternalSize.text = StorageHelper.getInternalStorageInfo()
        b.tvSdSize.text = StorageHelper.getSdCardStorageInfo(this)

        // 4. KATEGORI: PINDAI DI LATAR BELAKANG (BARU MUNCUL SETELAH SELESAI)
        loadCategoryStats()
    }

    private fun loadCategoryStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            val internal = Environment.getExternalStorageDirectory()
            val sd = getSdCard()
            var imgSize = 0L; var imgCount = 0
            var audSize = 0L; var audCount = 0
            var vidSize = 0L; var vidCount = 0
            var docSize = 0L; var docCount = 0
            var dlSize = 0L; var dlCount = 0

            fun scan(dir: File) {
                val files = dir.listFiles() ?: return
                for (f in files) {
                    if (f.isDirectory) {
                        if (!f.name.startsWith(".") && f.name != "Android") scan(f)
                    } else {
                        val ext = f.extension.lowercase()
                        when (ext) {
                            "jpg","jpeg","png","webp","gif","bmp" -> { imgSize += f.length(); imgCount++ }
                            "mp3","wav","aac","m4a","ogg","flac" -> { audSize += f.length(); audCount++ }
                            "mp4","mkv","avi","mov","webm" -> { vidSize += f.length(); vidCount++ }
                            "pdf","doc","docx","txt","xls","xlsx","ppt","pptx" -> { docSize += f.length(); docCount++ }
                        }
                        if (f.absolutePath.contains("/Download/")) { dlSize += f.length(); dlCount++ }
                    }
                }
            }
            if (internal.exists()) scan(internal)
            sd?.let { if (it.exists()) scan(it) }

            withContext(Dispatchers.Main) {
                // Begitu selesai, baru munculkan angkanya (Tanpa tulisan "Memuat...")
                b.tvImageStats.text = "${StorageHelper.formatSize(imgSize)} ($imgCount)"
                b.tvAudioStats.text = "${StorageHelper.formatSize(audSize)} ($audCount)"
                b.tvVideoStats.text = "${StorageHelper.formatSize(vidSize)} ($vidCount)"
                b.tvDocStats.text = "${StorageHelper.formatSize(docSize)} ($docCount)"
                b.tvDownloadStats.text = "${StorageHelper.formatSize(dlSize)} ($dlCount)"
                b.tvAppStats.text = "Ketuk untuk lihat" 
            }
        }
    }

    private fun openFileManager(path: String) {
        startActivity(Intent(this, FileManagerActivity::class.java).putExtra("initial_path", path))
    }
    private fun openMedia(type: String) {
        startActivity(Intent(this, MediaManagerActivity::class.java).putExtra("media_type", type))
    }
    private fun openSdCard() {
        getSdCard()?.let { openFileManager(it.absolutePath) } ?: Toast.makeText(this, "Kartu SD tidak terdeteksi", Toast.LENGTH_SHORT).show()
    }
    private fun getSdCard(): File? {
        val dirs = getExternalFilesDirs(null)
        if (dirs.size > 1 && dirs[1] != null) {
            var sd = dirs[1]!!.absolutePath
            val idx = sd.indexOf("/Android")
            if (idx != -1) sd = sd.substring(0, idx)
            return File(sd)
        }
        return null
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
            startActivity(Intent(this, targetActivity).putExtra("file_path", tempFile.absolutePath))
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}