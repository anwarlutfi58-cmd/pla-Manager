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
import com.pla.manajer.util.*
import java.io.File
import java.io.FileOutputStream

class VideoEditorActivity : AppCompatActivity() {
    private lateinit var b: ActivityVideoEditorBinding
    private lateinit var notif: NotificationHelper
    private val imageUris = mutableListOf<Uri>()
    private var audioUri: Uri? = null
    private var isLandscape = false
    
    private var lastExportedFile: File? = null

    // Menyimpan filter string tambahan yang dipilih user
    private var extraVideoFilter = ""
    private var extraAudioFilter = ""

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

        // --- TOMBOL PUTAR HASIL VIDEO ---
        val btnPlay = Button(this).apply {
            text = "Putar Hasil Video"
            setOnClickListener {
                val file = lastExportedFile
                if (file != null && file.exists()) {
                    VideoPlayerHelper.playVideo(this@VideoEditorActivity, file)
                } else {
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
        
        // --- TOMBOL MENU TAMBAHAN (Fitur Lanjutan) ---
        val btnAdvanced = Button(this).apply {
            text = "Buka Alat Video Lanjutan (Crop, Speed, Filter, dll)"
            setOnClickListener { showAdvancedToolsDialog() }
        }

        val parent = b.btnExport.parent as? LinearLayout
        if (parent != null) {
            val index = parent.indexOfChild(b.btnExport)
            parent.addView(btnPlay, index)
            parent.addView(btnAdvanced, index + 1)
        }

        b.btnExport.setOnClickListener {
            if (imageUris.isEmpty()) {
                Toast.makeText(this, "Pilih gambar terlebih dahulu!", Toast.LENGTH_SHORT).show()
            } else {
                showExportDialog()
            }
        }
    }

    // --- LOGIKA MENU TAMBAHAN ---
    private fun showAdvancedToolsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_video_tools, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialogView.findViewById<Button>(R.id.btnSpeed).setOnClickListener {
            dialog.dismiss()
            showChoiceDialog("Pilih Kecepatan", arrayOf("Normal", "Cepat 2x", "Lambat 2x", "Sangat Cepat 4x")) { index ->
                extraVideoFilter += if (index > 0) VideoTransformHelper.getSpeedCommand(index - 1) + "," else ""
            }
        }

        dialogView.findViewById<Button>(R.id.btnFilter).setOnClickListener {
            dialog.dismiss()
            showChoiceDialog("Pilih Filter", arrayOf("Normal", "Hitam Putih", "Sepia", "Negatif", "Vintage", "Cerah", "Gelap", "Warna Tajam", "Blur", "Sketsa", "Vignette", "Grain", "Mosaik", "Pertajam", "Glitch")) { index ->
                extraVideoFilter += if (index > 0) VideoFilterHelper.getFilterCommand(index - 1) + "," else ""
            }
        }

        dialogView.findViewById<Button>(R.id.btnAudio).setOnClickListener {
            dialog.dismiss()
            showChoiceDialog("Pilih Efek Audio", arrayOf("Normal", "Perbesar", "Perkecil", "Hapus Suara (Mute)", "Cepat", "Lambat", "Gema", "Fade In")) { index ->
                extraAudioFilter = if (index > 0) VideoAudioHelper.getAudioCommand(index - 1) else ""
            }
        }

        dialogView.findViewById<Button>(R.id.btnCrop).setOnClickListener {
            dialog.dismiss()
            showChoiceDialog("Pilih Rasio (Crop)", arrayOf("Bebas", "16:9 Lanskap", "9:16 Potret", "1:1 Persegi", "4:3 Klasik")) { index ->
                extraVideoFilter += if (index > 0) VideoTransformHelper.getCropCommand(index - 1) + "," else ""
            }
        }

        dialogView.findViewById<Button>(R.id.btnRotate).setOnClickListener {
            dialog.dismiss()
            showChoiceDialog("Putar/Balik Video", arrayOf("Normal", "Putar Kanan 90°", "Putar Kiri 90°", "Putar 180°", "Cermin Kiri-Kanan", "Cermin Atas-Bawah")) { index ->
                extraVideoFilter += if (index > 0) VideoTransformHelper.getRotateCommand(index - 1) + "," else ""
            }
        }

        dialogView.findViewById<Button>(R.id.btnText).setOnClickListener {
            dialog.dismiss()
            showTextInputDialog()
        }

        dialogView.findViewById<Button>(R.id.btnCompress).setOnClickListener {
            dialog.dismiss()
            showChoiceDialog("Kompres Video", arrayOf("Normal", "Hemat Ukuran (H.264)", "Super Hemat (H.265)")) { index ->
                extraVideoFilter += if (index > 0) VideoTransformHelper.getFormatCommand(index - 1) + "," else ""
            }
        }

        dialog.show()
    }

    private fun showChoiceDialog(title: String, options: Array<String>, onSelected: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options) { _, which -> onSelected(which) }
            .show()
    }

    private fun showTextInputDialog() {
        val input = EditText(this).apply { hint = "Masukkan teks..." }
        AlertDialog.Builder(this)
            .setTitle("Tambah Teks/Watermark")
            .setView(input)
            .setPositiveButton("Tambah") { _, _ ->
                val text = input.text.toString()
                if (text.isNotEmpty()) {
                    extraVideoFilter += VideoFilterHelper.getTextCommand(text, "bottom", "white", 40) + ","
                }
            }.setNegativeButton("Batal", null).show()
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
        lastExportedFile = outputFile

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

            // GABUNGKAN FILTER TAMBAHAN DENGAN FILTER DASAR
            val baseFilter = "scale=$resolution,setsar=1,fps=$fps"
            val combinedFilter = if (extraVideoFilter.isNotEmpty()) {
                "$extraVideoFilter$baseFilter".removeSuffix(",") // Buang koma terakhir jika ada
            } else baseFilter

            cmd.append("-vf $combinedFilter ")
            cmd.append("-c:v libx264 -pix_fmt yuv420p ")

            if (audioUri != null) {
                if (extraAudioFilter.isNotEmpty()) {
                    cmd.append("-af $extraAudioFilter ")
                }
                cmd.append("-c:a aac -shortest ")
            }

            cmd.append("\"${outputFile.absolutePath}\"")

            FFmpegKit.executeAsync(cmd.toString(), { session ->
                runOnUiThread {
                    b.btnExport.isEnabled = true
                    b.btnExport.text = "Ekspor"
                    notif.hideEdit()
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        registerVideoToMediaStore(outputFile)
                        Toast.makeText(this, "Berhasil! Silakan tekan 'Putar Hasil Video'", Toast.LENGTH_LONG).show()
                        extraVideoFilter = "" // Reset setelah ekspor
                        extraAudioFilter = ""
                    } else {
                        Toast.makeText(this, "Gagal membuat video.", Toast.LENGTH_LONG).show()
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