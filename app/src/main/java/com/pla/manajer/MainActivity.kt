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
import com.pla.manajer.ui.GlobalEqualizerActivity
import com.pla.manajer.service.GlobalEqualizerService
import com.pla.manajer.util.MediaScannerHelper
import com.pla.manajer.util.PlaMediaPlayer
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

        PlaMediaPlayer.init(this)

        b.cardInternal.setOnClickListener { openFileManager(Environment.getExternalStorageDirectory().absolutePath) }
        b.cardSdCard.setOnClickListener { openSdCard() }
        b.cardImages.setOnClickListener { openMedia("IMAGE") }
        b.cardAudio.setOnClickListener { openMedia("AUDIO") }
        b.cardVideos.setOnClickListener { openMedia("VIDEO") }
        b.cardDocuments.setOnClickListener { openMedia("DOC") }
        // PERBAIKAN: path ABSOLUT folder Download (sebelumnya cuma string "Download" → layar kosong!)
        b.cardDownloads.setOnClickListener {
            openFileManager(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
        }
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

        b.cardGlobalEq.setOnClickListener {
            startActivity(Intent(this, GlobalEqualizerActivity::class.java))
        }

        b.tvImageStats.text = ""
        b.tvAudioStats.text = ""
        b.tvVideoStats.text = ""
        b.tvDocStats.text = ""
        b.tvDownloadStats.text = ""
        b.tvAppStats.text = ""

        refreshStorageInfo()
        loadCategoryStats()
    }

    private fun refreshStorageInfo() {
        b.tvInternalSize.text = StorageHelper.getInternalStorageInfo()
        b.tvSdSize.text = StorageHelper.getSdCardStorageInfo(this)
    }

    override fun onResume() {
        super.onResume()
        val prefs = Prefs(this)
        if (prefs.showImageEditor) b.cardEditImage.visibility = View.VISIBLE else b.cardEditImage.visibility = View.GONE
        if (prefs.showAudioEditor) b.cardEditAudio.visibility = View.VISIBLE else b.cardEditAudio.visibility = View.GONE
        if (prefs.showVideoEditor) b.cardEditVideo.visibility = View.VISIBLE else b.cardEditVideo.visibility = View.GONE
        if (prefs.showGithubBuilder) b.cardGithub.visibility = View.VISIBLE else b.cardGithub.visibility = View.GONE

        if (prefs.eqEnabled) {
            b.cardGlobalEq.visibility = View.VISIBLE
        } else {
            b.cardGlobalEq.visibility = View.GONE
            try { stopService(Intent(this, GlobalEqualizerService::class.java)) } catch (_: Exception) {}
        }

        // Segarkan angka setiap kembali ke beranda
        refreshStorageInfo()
        loadCategoryStats()
    }

    private fun loadCategoryStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            val img = MediaScannerHelper.getCategoryStats(this@MainActivity, "IMAGE")
            val aud = MediaScannerHelper.getCategoryStats(this@MainActivity, "AUDIO")
            val vid = MediaScannerHelper.getCategoryStats(this@MainActivity, "VIDEO")
            val doc = MediaScannerHelper.getCategoryStats(this@MainActivity, "DOC")
            val dl = MediaScannerHelper.getCategoryStats(this@MainActivity, "DOWNLOAD")

            withContext(Dispatchers.Main) {
                b.tvImageStats.text = "${StorageHelper.formatSize(img.size)} (${img.count})"
                b.tvAudioStats.text = "${StorageHelper.formatSize(aud.size)} (${aud.count})"
                b.tvVideoStats.text = "${StorageHelper.formatSize(vid.size)} (${vid.count})"
                b.tvDocStats.text = "${StorageHelper.formatSize(doc.size)} (${doc.count})"
                b.tvDownloadStats.text = "${StorageHelper.formatSize(dl.size)} (${dl.count})"
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
        val dirs = getExternalFilesDirs(null)
        if (dirs.size > 1 && dirs[1] != null) {
            var sd = dirs[1]!!.absolutePath
            val idx = sd.indexOf("/Android")
            if (idx != -1) sd = sd.substring(0, idx)
            openFileManager(sd)
        } else Toast.makeText(this, "SD tidak ada", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}