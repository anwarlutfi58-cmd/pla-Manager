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
    private var videoUri: Uri? = null
    private var isLandscape = false
    
    private var lastExportedFile: File? = null
    private var extraVideoFilter = ""
    private var extraAudioFilter = ""
    private var useReverse = false
    private var useMute = false
    private var useGif = false
    
    private val videoFilters = arrayOf("Normal", "Vintage", "Sepia", "Black & White", "HDR", "Cyberpunk", "Neon", "Retro", "Cinematic", "Cold", "Warm", "Fade", "Dramatic", "Pastel", "Glitch", "Invert", "Oil Painting")
    private val soundEffects = arrayOf("Tidak Ada", "Tepuk Tangan", "Hujan", "Angin", "Suara Hati (Detak)", "Petir", "Sorak Sorai", "Sirene", "Klik Kamera", "Bunyi Pop", "Whoosh", "Alarm")

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
            videoUri = null
            updatePreview()
        }
    }

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            videoUri = uri
            imageUris.clear()
            b.ivPreview.setImageResource(android.R.drawable.ic_media_play)
            b.tvNoImage.visibility = View.GONE
            val name = getFileName(uri)
            Toast.makeText(this, "Video terpilih: $name", Toast.LENGTH_SHORT).show()
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

        b.spinnerEffects.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, VideoProHelper.visualEffects)
        b.spinnerTextPos.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, VideoProHelper.watermarkPositions)
        b.spinnerTextColor.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Putih", "Hitam", "Kuning", "Merah", "Biru", "Hijau"))
        b.spinnerVideoFilters.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, videoFilters)
        b.spinnerSoundEffects.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, soundEffects)

        b.btnBack.setOnClickListener { finish() }

        b.toolAddImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            pickImagesLauncher.launch(intent)
        }
        b.toolAddVideo.setOnClickListener { pickVideoLauncher.launch("video/*") }

        b.toolDuration.setOnClickListener { showPanel(b.panelDuration) }
        b.toolAudio.setOnClickListener { showPanel(b.panelAudio) }
        b.toolRatio.setOnClickListener { showPanel(b.panelRatio) }
        b.toolFilter.setOnClickListener { showPanel(b.panelFilter) }
        b.toolText.setOnClickListener { showPanel(b.panelText) }
        b.toolEffects.setOnClickListener { showPanel(b.panelEffects) }
        b.toolSpeed.setOnClickListener { showPanel(b.panelSpeed) }
        b.toolVolume.setOnClickListener { showPanel(b.panelVolume) }

        b.toolReverse.setOnClickListener {
            useReverse = !useReverse
            Toast.makeText(this, if (useReverse) "Reverse Aktif" else "Reverse Dimatikan", Toast.LENGTH_SHORT).show()
        }
        b.toolMute.setOnClickListener {
            useMute = !useMute
            Toast.makeText(this, if (useMute) "Mute Aktif" else "Mute Dimatikan", Toast.LENGTH_SHORT).show()
        }
        b.toolGif.setOnClickListener {
            useGif = !useGif
            Toast.makeText(this, if (useGif) "Mode GIF Aktif" else "Mode GIF Dimatikan", Toast.LENGTH_SHORT).show()
        }

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

        b.seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        val btnPickRealVideo = Button(this).apply {
            text = "Pilih Video Asli (.mp4) dari HP"
            setOnClickListener { pickVideoLauncher.launch("video/*") }
        }

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
        
        val btnAdvanced = Button(this).apply {
            text = "Buka Alat Video Asli (Potong, Mute, dll)"
            setOnClickListener { showRealVideoTools() }
        }

        val parent = b.btnExport.parent as? LinearLayout
        if (parent != null) {
            val index = parent.indexOfChild(b.btnExport)
            parent.addView(btnPickRealVideo, index)
            parent.addView(btnPlay, index + 1)
            parent.addView(btnAdvanced, index + 2)
        }

        b.btnExport.setOnClickListener {
            if (imageUris.isEmpty() && videoUri == null) {
                Toast.makeText(this, "Pilih gambar atau video terlebih dahulu!", Toast.LENGTH_SHORT).show()
            } else {
                showExportDialog()
            }
        }
    }

    private fun showRealVideoTools() {
        if (videoUri == null) {
            Toast.makeText(this, "Pilih Video Asli terlebih dahulu (tombol paling atas)!", Toast.LENGTH_LONG).show()
            return
        }

        val options = arrayOf("Potong Video (Trim)", "Hapus Suara (Mute)", "Percepat Video 2x", "Jadikan GIF")
        AlertDialog.Builder(this)
            .setTitle("Alat Video Asli")
            .setItems(options) { _, which ->
                when(which) {
                    0 -> showTrimDialog()
                    1 -> {
                        extraVideoFilter = RealVideoHelper.getMuteCommand()
                        Toast.makeText(this, "Suara akan dihapus saat ekspor", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        extraVideoFilter = RealVideoHelper.getSpeedUpCommand(2.0f)
                        Toast.makeText(this, "Video akan dipercepat saat ekspor", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        extraVideoFilter = RealVideoHelper.getToGifCommand()
                        Toast.makeText(this, "Video akan diubah jadi GIF saat ekspor", Toast.LENGTH_SHORT).show()
                    }
                }
            }.show()
    }

    private fun showTrimDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        val etStart = EditText(this).apply { hint = "Mulai (contoh: 00:00:05)" }
        val etEnd = EditText(this).apply { hint = "Selesai (contoh: 00:00:15)" }
        layout.addView(etStart)
        layout.addView(etEnd)

        AlertDialog.Builder(this)
            .setTitle("Potong Video")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val start = etStart.text.toString()
                val end = etEnd.text.toString()
                if (start.isNotEmpty() && end.isNotEmpty()) {
                    extraVideoFilter = RealVideoHelper.getTrimCommand(start, end)
                    Toast.makeText(this, "Video akan dipotong saat ekspor", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun showPanel(panelToShow: View) {
        b.panelControls.visibility = View.VISIBLE
        b.panelDuration.visibility = View.GONE
        b.panelAudio.visibility = View.GONE
        b.panelRatio.visibility = View.GONE
        b.panelFilter.visibility = View.GONE
        b.panelText.visibility = View.GONE
        b.panelSpeed.visibility = View.GONE
        b.panelVolume.visibility = View.GONE
        b.panelEffects.visibility = View.GONE
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
                
                if (videoUri != null) {
                    renderRealVideo(resolution, fps)
                } else {
                    renderSlideshow(resolution, fps)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun renderRealVideo(resolution: String, fps: String) {
        b.btnExport.isEnabled = false
        b.btnExport.text = "Memproses Video..."
        notif.showEdit("Memproses Video", "Mengedit video asli...")

        val outputDir = File(Environment.getExternalStorageDirectory(), "PlaProjects/Edit_Video")
        if (!outputDir.exists()) outputDir.mkdirs()
        val ext = if (useGif) "gif" else "mp4"
        val outputFile = File(outputDir, "EDIT_VIDEO_${System.currentTimeMillis()}.$ext")
        lastExportedFile = outputFile

        try {
            val tempVideo = File(cacheDir, "input_video.mp4")
            contentResolver.openInputStream(videoUri!!)?.use { input ->
                FileOutputStream(tempVideo).use { output -> input.copyTo(output) }
            }

            val cmd = StringBuilder()
            if (extraVideoFilter.contains("-ss")) cmd.append("${extraVideoFilter} ")
            
            cmd.append("-i \"${tempVideo.absolutePath}\" ")
            
            val vFilter = StringBuilder("scale=$resolution,setsar=1,fps=$fps")
            val effectFilter = VideoProHelper.getVisualFilter(b.spinnerEffects.selectedItem.toString())
            if (effectFilter.isNotEmpty()) vFilter.append(",$effectFilter")
            
            val selectedFilter = b.spinnerVideoFilters.selectedItem.toString()
            when (selectedFilter) {
                "Vintage" -> vFilter.append(",curves=preset=vintage")
                "Sepia" -> vFilter.append(",colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131")
                "Black & White" -> vFilter.append(",hue=s=0")
                "HDR" -> vFilter.append(",eq=contrast=1.5:saturation=1.5")
                "Cyberpunk" -> vFilter.append(",colorbalance=rs=0.3:gs=-0.1:bs=0.5")
                "Neon" -> vFilter.append(",eq=contrast=1.4:brightness=0.1:saturation=2.0")
                "Retro" -> vFilter.append(",curves=preset=color_negative")
                "Cinematic" -> vFilter.append(",curves=preset=strong_contrast")
                "Cold" -> vFilter.append(",colorbalance=bs=0.3:gs=0.1")
                "Warm" -> vFilter.append(",colorbalance=rs=0.3:gs=0.1")
                "Fade" -> vFilter.append(",eq=brightness=0.1:contrast=0.8")
                "Dramatic" -> vFilter.append(",eq=contrast=1.5:brightness=-0.1")
                "Pastel" -> vFilter.append(",eq=saturation=0.6:brightness=0.1")
                "Glitch" -> vFilter.append(",noise=alls=20:allf=t+u")
                "Invert" -> vFilter.append(",negate")
                "Oil Painting" -> vFilter.append(",convolution='0 -1 0 -1 5 -1 0 -1 0'")
            }
            
            if (useReverse) vFilter.append(",reverse")
            
            val wmText = b.etWatermark.text.toString()
            if (wmText.isNotEmpty()) {
                val pos = VideoProHelper.getWatermarkPosition(b.spinnerTextPos.selectedItem.toString())
                val color = when(b.spinnerTextColor.selectedItemPosition) {
                    1 ->