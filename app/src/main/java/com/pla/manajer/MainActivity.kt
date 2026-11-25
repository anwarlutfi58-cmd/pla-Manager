package com.pla.manajer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pla.manajer.databinding.ActivityMainBinding
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

        loadStorageStats()
    }

    private fun loadStorageStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            val internal = Environment.getExternalStorageDirectory()
            val sd = getSdCard()
            val imgs = mutableListOf<File>(); val auds = mutableListOf<File>(); val vids = mutableListOf<File>()
            val docs = mutableListOf<File>(); val dls = mutableListOf<File>()

            fun scan(dir: File) {
                val files = dir.listFiles() ?: return
                for (f in files) {
                    if (f.isDirectory) {
                        if (!f.name.startsWith(".") && f.name != "Android") scan(f)
                    } else {
                        when (f.extension.lowercase()) {
                            "jpg","jpeg","png","webp","gif","bmp" -> imgs.add(f)
                            "mp3","wav","aac","m4a","ogg","flac" -> auds.add(f)
                            "mp4","mkv","avi","mov","webm" -> vids.add(f)
                            "pdf","doc","docx","txt","xls","xlsx","ppt","pptx" -> docs.add(f)
                        }
                        if (f.absolutePath.contains("/Download/")) dls.add(f)
                    }
                }
            }
            if (internal.exists()) scan(internal)
            sd?.let { if (it.exists()) scan(it) }

            withContext(Dispatchers.Main) {
                fun fmtSize(bytes: Long): String {
                    if (bytes <= 0) return "0 B"
                    val units = arrayOf("B", "KB", "MB", "GB", "TB")
                    val digit = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
                    return String.format("%.1f %s", bytes / Math.pow(1024.0, digit.toDouble()), units[digit])
                }

                try {
                    val stat = StatFs(internal.absolutePath)
                    val total = stat.blockCountLong * stat.blockSizeLong
                    val free = stat.availableBlocksLong * stat.blockSizeLong
                    b.tvInternalSize.text = "${fmtSize(total - free)} / ${fmtSize(total)}"
                } catch (_: Exception) {}

                if (sd != null) {
                    try {
                        val stat = StatFs(sd.absolutePath)
                        val total = stat.blockCountLong * stat.blockSizeLong
                        val free = stat.availableBlocksLong * stat.blockSizeLong
                        b.tvSdSize.text = "${fmtSize(total - free)} / ${fmtSize(total)}"
                    } catch (_: Exception) {}
                } else {
                    b.tvSdSize.text = "Tidak ada SD Card"
                }

                b.tvImageStats.text = "${fmtSize(imgs.sumOf { it.length() })} (${imgs.size})"
                b.tvAudioStats.text = "${fmtSize(auds.sumOf { it.length() })} (${auds.size})"
                b.tvVideoStats.text = "${fmtSize(vids.sumOf { it.length() })} (${vids.size})"
                b.tvDocStats.text = "${fmtSize(docs.sumOf { it.length() })} (${docs.size})"
                b.tvDownloadStats.text = "${fmtSize(dls.sumOf { it.length() })} (${dls.size})"
                b.tvAppStats.text = "0 MB (0)" 
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
        getSdCard()?.let { openFileManager(it.absolutePath) } ?: Toast.makeText(this, "SD tidak ada", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}