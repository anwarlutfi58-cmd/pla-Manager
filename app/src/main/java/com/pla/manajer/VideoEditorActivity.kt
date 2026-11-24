package com.pla.manajer

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.pla.manajer.databinding.ActivityVideoEditorBinding
import com.pla.manajer.util.NotificationHelper
import com.pla.manajer.util.VideoPlayerHelper // <-- Import Helper
import java.io.File
import java.io.FileOutputStream

class VideoEditorActivity : AppCompatActivity() {
    private lateinit var b: ActivityVideoEditorBinding
    private lateinit var notif: NotificationHelper
    private val imageUris = mutableListOf<Uri>()
    private var audioUri: Uri? = null
    private var isLandscape = false
    
    // Menyimpan file video terakhir yang diekspor agar bisa diputar
    private var lastExportedFile: File? = null

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data?.clipData != null) {
                val clipData = data.clipData!!
                for (i in 0 until clipData.itemCount) {
                    imageUris.add(clipData.getItemAt(i).uri)
                }
            } else if (data?.data != null) {
                imageUris.add(data.data!!)
            }
            updatePreview()
        }
    }

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            audioUri = uri
            b.tvAudioSelected.text = getFileName(uri)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityVideoEditorBinding.inflate(layoutInflater)
        setContentView(b.root)
        notif = NotificationHelper(this)

        b.btnBack.setOnClickListener { finish() }

        b.toolAddImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            pickImagesLauncher.launch(intent)
        }

        b.toolDuration.setOnClickListener { showPanel(b.panelDuration) }
        b.toolAudio.setOnClickListener { showPanel(b.panelAudio) }
        b.toolRatio.setOnClickListener { showPanel(b.panelRatio) }
        b.toolFilter.setOnClickListener { showPanel(b.panelFilter) }

        b.toolRotateScreen.setOnClickListener {
            isLandscape = !isLandscape
            requestedOrientation = if (isLandscape) {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }

        b.btnPickAudio.setOnClickListener { pickAudioLauncher.launch("audio/*") }

        b.spinnerRatio.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("16:9 (Lanskap)", "9:16 (Potret/TikTok)", "1:1 (Persegi)"))

        // Tambahkan tombol Putar otomatis di atas tombol Ekspor jika belum ada
        val btnPlay = Button(this).apply {
            text = "Putar Hasil Video"
            setOnClickListener {
                val file = lastExportedFile
                if (file != null && file.exists()) {
                    VideoPlayerHelper.playVideo(this@VideoEditorActivity, file)
                } else {
                    // Jika belum diedit, coba cari video terakhir di folder
                    val dir = File(Environment.getExternalStorageDirectory(), "PlaProjects/Edit_Video")
                    val lastVideo = dir.listFiles()?.filter { it.extension == "mp4" }?.maxByOrNull { it.lastModified() }
                    if (lastVideo != null) {
                        VideoPlayerHelper.playVideo(this@VideoEditorActivity, lastVideo)
                    } else {
                        Toast.makeText(this@VideoEditorActivity, "Belum ada video yang dibuat.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        // Masukkan tombol play ke layout (di atas tombol export)
        val parent = b.btnExport.parent as? LinearLayout
        if (parent != null) {
            val index = parent.indexOfChild(b.btnExport)
            parent.addView(btnPlay, index)
        }

        b.btnExport.setOnClickListener {
            if (imageUris.isEmpty()) {
                Toast.makeText(this, "Pilih gambar terlebih dahulu!", Toast.LENGTH_SHORT).show()
            } else {
                showExportDialog()
            }
        }
    }

    private fun showPanel(panelToShow: View) {
        b.panelControls.visibility = View.VISIBLE
        b.panelDuration.visibility = View.GONE
        b.panelAudio.visibility = View.GONE
        b.panelRatio.visibility = View.GONE
        b.panelFilter.visibility = View.GONE
        panelToShow.visibility = View.VISIBLE
    }

    private fun updatePreview() {
        if (imageUris.isNotEmpty()) {
            b.ivPreview.setImageURI(imageUris.first())
            b.tvNoImage.visibility = View.GONE
            Toast.makeText(this, "${imageUris.size} gambar dipilih", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExportDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_video, null)
        val spinnerRes = dialogView.findViewById<Spinner>(R.id.spinnerResolution)
        val spinnerFps = dialogView.findViewById<Spinner>(R.id.spinnerFps)

        spinnerRes.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("400p (Hemat Data)", "720p (HD)", "1080p (Full HD)"))
        spinnerFps.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("30 FPS", "60 FPS"))

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Mulai Ekspor") { _, _ ->
                val resolution = when(spinnerRes.selectedItemPosition) {
                    0 -> "640:360"
                    1 -> "1280:720"
                    else -> "1920:1080"
                }
                val fps = if (spinnerFps.selectedItemPosition == 0) "30" else "60"
                startRendering(resolution, fps)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun startRendering(resolution: String, fps: String) {
        b.btnExport.isEnabled = false
        b.btnExport.text = "Memproses..."
        Toast.makeText(this, "Sedang membuat video... Mohon tunggu.", Toast.LENGTH_LONG).show()
        notif.showEdit("Membuat Video", "Mengekspor ${imageUris.size} gambar ke MP4...")

        val duration = b.etDuration.text.toString().toIntOrNull() ?: 3
        val concatFile = File(cacheDir, "concat_list.txt")

        val outputDir = File(Environment.getExternalStorageDirectory(), "PlaProjects/Edit_Video")
        if (!outputDir.exists()) outputDir.mkdirs()
        
        val outputFile = File(outputDir, "EDIT_${System.currentTimeMillis()}.mp4")
        lastExportedFile = outputFile // Simpan referensi untuk tombol putar

        try {
            val writer = concatFile.bufferedWriter()
            imageUris.forEach { uri ->
                val tempImg = File(cacheDir, "img_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempImg).use { output -> input.copyTo(output) }
                }
                writer.write("file '${tempImg.absolutePath}'\n")
                writer.write("duration $duration\n")
            }
            val lastImg = File(cacheDir, "img_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(imageUris.last())?.use { input ->
                FileOutputStream(lastImg).use { output -> input.copyTo(output) }
            }
            writer.write("file '${lastImg.absolutePath}'\n")
            writer.close()

            val cmd = StringBuilder()
            cmd.append("-y -f concat -safe 0 -i \"${concatFile.absolutePath}\" ")

            if (audioUri != null) {
                val tempAudio = File(cacheDir, "bgm.m4a")
                contentResolver.openInputStream(audioUri!!)?.use { input ->
                    FileOutputStream(tempAudio).use { output -> input.copyTo(output) }
                }
                cmd.append("-i \"${tempAudio.absolutePath}\" ")
            }

            cmd.append("-vf scale=$resolution,setsar=1,fps=$fps ")
            cmd.append("-c:v libx264 -pix_fmt yuv420p ")

            if (audioUri != null) cmd.append("-c:a aac -shortest ")

            cmd.append("\"${outputFile.absolutePath}\"")

            FFmpegKit.executeAsync(cmd.toString(), { session ->
                runOnUiThread {
                    b.btnExport.isEnabled = true
                    b.btnExport.text = "Ekspor"
                    notif.hideEdit()
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        registerVideoToMediaStore(outputFile)
                        Toast.makeText(this, "Berhasil! Silakan tekan 'Putar Hasil Video'", Toast.LENGTH_LONG).show()
                        // Tidak langsung finish() agar user bisa memutar hasilnya
                    } else {
                        Toast.makeText(this, "Gagal membuat video. Coba kurangi jumlah gambar.", Toast.LENGTH_LONG).show()
                    }
                }
            }, { }, { })

        } catch (e: Exception) {
            b.btnExport.isEnabled = true
            b.btnExport.text = "Ekspor"
            notif.hideEdit()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerVideoToMediaStore(file: File) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "PlaProjects/Edit_Video")
                } else {
                    put(MediaStore.Video.Media.DATA, file.absolutePath)
                }
            }
            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "Audio Terpilih"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) name = cursor.getString(nameIndex)
        }
        return name
    }

    override fun onDestroy() {
        super.onDestroy()
        notif.hideEdit()
    }
}