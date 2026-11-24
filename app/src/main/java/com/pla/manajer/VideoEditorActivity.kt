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
    
    // State fitur baru (memanfaatkan alat lama)
    private var speedMultiplier = 1.0f
    private var volumeMultiplier = 1.0f
    private var effectFilter = ""
    private var textFilter = ""
    private var isReversed = false
    private var isMuted = false
    private var isGif = false

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data?.clipData != null) {
                val clipData = data.clipData!!
                for (i in 0 until clipData.itemCount) imageUris.add(clipData.getItemAt(i).uri)
            } else if (data?.data != null) imageUris.add(data.data!!)
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
            Toast.makeText(this, "Video terpilih: ${getFileName(uri)}", Toast.LENGTH_SHORT).show()
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

        // Setup UI
        b.spinnerRatio.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("16:9 (Lanskap)", "9:16 (Potret)", "1:1 (Persegi)", "4:3", "21:9"))
        b.spinnerTextPos.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Bawah Tengah", "Atas Tengah", "Tengah", "Bawah Kiri", "Bawah Kanan"))
        b.spinnerEffects.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Normal", "Hitam Putih (Grayscale)", "Vintage/Sepia", "Blur Background", "Echo Audio", "Bass Boost Audio", "Ganti Suara (Robot)", "Ganti Suara (Chipmunk)"))

        // Listener Tools
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
        b.toolSpeed.setOnClickListener { showPanel(b.panelSpeed) }
        b.toolVolume.setOnClickListener { showPanel(b.panelVolume) }
        b.toolEffects.setOnClickListener { showPanel(b.panelEffects) }

        b.toolRotateScreen.setOnClickListener {
            isLandscape = !isLandscape
            requestedOrientation = if (isLandscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        b.toolReverse.setOnClickListener {
            isReversed = !isReversed
            Toast.makeText(this, if (isReversed) "Video akan dibalik (Reverse)" else "Reverse dimatikan", Toast.LENGTH_SHORT).show()
        }

        b.toolMute.setOnClickListener {
            isMuted = !isMuted
            Toast.makeText(this, if (isMuted) "Suara akan dihapus (Mute)" else "Mute dimatikan", Toast.LENGTH_SHORT).show()
        }

        b.toolGif.setOnClickListener {
            isGif = !isGif
            Toast.makeText(this, if (isGif) "Akan diekspor sebagai GIF" else "Ekspor sebagai MP4", Toast.LENGTH_SHORT).show()
        }

        b.btnPickAudio.setOnClickListener { pickAudioLauncher.launch("audio/*") }
        
        // Seekbar Listeners (Memanfaatkan fitur lama agar bisa digeser)
        b.seekBrightness.setOnSeekBarChangeListener(simpleSeekListener { progress ->
            val v = (progress - 100) / 100.0f
            effectFilter = "eq=brightness=$v"
        })
        b.seekContrast.setOnSeekBarChangeListener(simpleSeekListener { progress ->
            val c = progress / 100.0f
            effectFilter = "eq=contrast=$c"
        })
        b.seekSaturation.setOnSeekBarChangeListener(simpleSeekListener { progress ->
            val s = progress / 100.0f
            effectFilter = "eq=saturation=$s"
        })
        b.seekSpeed.setOnSeekBarChangeListener(simpleSeekListener { progress ->
            speedMultiplier = progress / 100.0f
        })
        b.seekVolume.setOnSeekBarChangeListener(simpleSeekListener { progress ->
            volumeMultiplier = progress / 100.0f
        })

        b.btnExport.setOnClickListener {
            if (imageUris.isEmpty() && videoUri == null) {
                Toast.makeText(this, "Pilih gambar atau video terlebih dahulu!", Toast.LENGTH_SHORT).show()
            } else {
                showExportDialog()
            }
        }
    }

    private fun simpleSeekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) onChange(progress) }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
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

        spinnerRes.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("400p (Hemat)", "720p (HD)", "1080p (Full HD)", "4K (Ultra HD)"))
        spinnerFps.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("24 FPS", "30 FPS", "60 FPS"))

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Mulai Ekspor") { _, _ ->
                val resolution = when(spinnerRes.selectedItemPosition) {
                    0 -> "640:360"; 1 -> "1280:720"; 2 -> "1920:1080"; else -> "3840:2160"
                }
                val fps = when(spinnerFps.selectedItemPosition) {
                    0 -> "24"; 1 -> "30"; else -> "60"
                }
                if (videoUri != null) renderRealVideo(resolution, fps) else renderSlideshow(resolution, fps)
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun renderRealVideo(resolution: String, fps: String) {
        b.btnExport.isEnabled = false
        b.btnExport.text = "Memproses..."
        notif.showEdit("Memproses Video", "Mengedit video asli...")

        val outputDir = File(Environment.getExternalStorageDirectory(), "PlaProjects/Edit_Video").apply { mkdirs() }
        val ext = if (isGif) "gif" else "mp4"
        val outputFile = File(outputDir, "EDIT_VIDEO_${System.currentTimeMillis()}.$ext")
        lastExportedFile = outputFile

        try {
            val tempVideo = File(cacheDir, "input_video.mp4")
            contentResolver.openInputStream(videoUri!!)?.use { input -> FileOutputStream(tempVideo).use { output -> input.copyTo(output) } }

            val cmd = StringBuilder("-y -i \"${tempVideo.absolutePath}\" ")
            
            // Filter Video
            val vFilters = mutableListOf<String>()
            if (!isGif) { vFilters.add("scale=$resolution"); vFilters.add("setsar=1"); vFilters.add("fps=$fps") }
            
            when (b.spinnerEffects.selectedItemPosition) {
                1 -> vFilters.add("hue=s=0") // Hitam Putih
                2 -> vFilters.add("colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131") // Sepia
            }

            if (effectFilter.isNotEmpty()) vFilters.add(effectFilter)
            if (speedMultiplier != 1.0f) vFilters.add("setpts=${1.0f/speedMultiplier}*PTS")
            if (isReversed) vFilters.add("reverse")
            
            if (textFilter.isNotEmpty()) {
                val pos = when(b.spinnerTextPos.selectedItemPosition) {
                    0 -> "x=(w-text_w)/2:y=h-th-20"; 1 -> "x=(w-text_w)/2:y=20"; 2 -> "x=(w-text_w)/2:y=(h-text_h)/2"; 3 -> "x=20:y=h-th-20"; else -> "x=w-tw-20:y=h-th-20"
                }
                vFilters.add("drawtext=text='$textFilter':fontcolor=white:fontsize=36:box=1:boxcolor=black@0.5:$pos")
            }
            
            if (vFilters.isNotEmpty()) cmd.append("-vf \"${vFilters.joinToString(",")}\" ")

            // Filter Audio
            if (isMuted) {
                cmd.append("-an ")
            } else {
                val aFilters = mutableListOf<String>()
                if (volumeMultiplier != 1.0f) aFilters.add("volume=$volumeMultiplier")
                if (speedMultiplier != 1.0f) aFilters.add("atempo=$speedMultiplier")
                when (b.spinnerEffects.selectedItemPosition) {
                    4 -> aFilters.add("aecho=0.8:0.9:1000:0.3") // Echo
                    5 -> aFilters.add("bass=g=10") // Bass Boost
                    6 -> aFilters.add("flanger") // Robot
                    7 -> aFilters.add("asetrate=44100*1.5,atempo=0.5") // Chipmunk
                }
                if (aFilters.isNotEmpty()) cmd.append("-af \"${aFilters.joinToString(",")}\" ")
                cmd.append("-c:a aac ")
            }

            if (!isGif) cmd.append("-c:v libx264 -pix_fmt yuv420p ")
            cmd.append("\"${outputFile.absolutePath}\"")

            FFmpegKit.executeAsync(cmd.toString(), { session ->
                runOnUiThread {
                    b.btnExport.isEnabled = true
                    b.btnExport.text = "Ekspor"
                    notif.hideEdit()
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        registerVideoToMediaStore(outputFile)
                        Toast.makeText(this, "Berhasil! Tekan 'Putar Hasil Video'", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Gagal memproses video.", Toast.LENGTH_LONG).show()
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

    private fun renderSlideshow(resolution: String, fps: String) {
        b.btnExport.isEnabled = false
        b.btnExport.text = "Memproses..."
        notif.showEdit("Membuat Video", "Mengekspor ${imageUris.size} gambar ke MP4...")

        val duration = b.etDuration.text.toString().toIntOrNull() ?: 3
        val concatFile = File(cacheDir, "concat_list.txt")
        val outputDir = File(Environment.getExternalStorageDirectory(), "PlaProjects/Edit_Video").apply { mkdirs() }
        val outputFile = File(outputDir, "EDIT_${System.currentTimeMillis()}.mp4")
        lastExportedFile = outputFile

        try {
            val writer = concatFile.bufferedWriter()
            imageUris.forEach { uri ->
                val tempImg = File(cacheDir, "img_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(tempImg).use { output -> input.copyTo(output) } }
                writer.write("file '${tempImg.absolutePath}'\n")
                writer.write("duration $duration\n")
            }
            val lastImg = File(cacheDir, "img_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(imageUris.last())?.use { input -> FileOutputStream(lastImg).use { output -> input.copyTo(output) } }
            writer.write("file '${lastImg.absolutePath}'\n")
            writer.close()

            val cmd = StringBuilder("-y -f concat -safe 0 -i \"${concatFile.absolutePath}\" ")
            if (audioUri != null) {
                val tempAudio = File(cacheDir, "bgm.m4a")
                contentResolver.openInputStream(audioUri!!)?.use { input -> FileOutputStream(tempAudio).use { output -> input.copyTo(output) } }
                cmd.append("-i \"${tempAudio.absolutePath}\" ")
            }
            
            val vFilters = mutableListOf<String>()
            vFilters.add("scale=$resolution")
            vFilters.add("setsar=1")
            vFilters.add("fps=$fps")
            if (textFilter.isNotEmpty()) {
                val pos = when(b.spinnerTextPos.selectedItemPosition) {
                    0 -> "x=(w-text_w)/2:y=h-th-20"; 1 -> "x=(w-text_w)/2:y=20"; 2 -> "x=(w-text_w)/2:y=(h-text_h)/2"; 3 -> "x=20:y=h-th-20"; else -> "x=w-tw-20:y=h-th-20"
                }
                vFilters.add("drawtext=text='$textFilter':fontcolor=white:fontsize=36:box=1:boxcolor=black@0.5:$pos")
            }
            cmd.append("-vf \"${vFilters.joinToString(",")}\" ")
            
            cmd.append("-c:v libx264 -pix_fmt yuv420p ")
            if (audioUri != null) cmd.append("-c:a aac -