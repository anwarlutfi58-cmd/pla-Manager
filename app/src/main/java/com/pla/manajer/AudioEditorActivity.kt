package com.pla.manajer

import android.app.AlertDialog
import android.content.ContentValues
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.material.tabs.TabLayout
import com.pla.manajer.databinding.ActivityAudioEditorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudioEditorActivity : AppCompatActivity() {
    private lateinit var b: ActivityAudioEditorBinding
    private lateinit var inputFile: File
    private val formats = arrayOf("mp3", "wav", "aac", "m4a")

    private var previewPlayer: MediaPlayer? = null
    private var isPlayingPreview = false
    private var previewFile: File? = null
    private var isPreparingPreview = false
    private var sourceSampleRate = 44100

    private var startTime = "00:00:00"
    private var endTime = "00:00:00"

    private lateinit var eqFreqs: IntArray
    private lateinit var eqBars: Array<SeekBar>

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAudioEditorBinding.inflate(layoutInflater)
        setContentView(b.root)

        val path = intent.getStringExtra("file_path") ?: run { finish(); return }
        inputFile = File(path)
        b.tvFileName.text = inputFile.name

        detectSampleRate()
        setupSpinners()
        setupTabs()
        setupSliders()
        setupTimePickers()

        b.btnPreviewAudio.setOnClickListener { onPreviewClick() }
        b.btnProcess.setOnClickListener { processAudio() }
    }

    private fun detectSampleRate() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val session = FFprobeKit.getMediaInformation(inputFile.absolutePath)
                val sr = session?.mediaInformation?.streams
                    ?.firstOrNull { it.type == "audio" }
                    ?.sampleRate
                if (!sr.isNullOrBlank()) sourceSampleRate = sr.toIntOrNull() ?: 44100
            } catch (_: Exception) {
                sourceSampleRate = 44100
            }
        }
    }

    private fun setupSpinners() {
        b.spinnerFormat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, formats)
        b.spinnerBitrate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("64k", "128k", "192k", "320k"))
        b.spinnerSampleRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("44100", "48000"))
        b.spinnerQuality.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Kualitas Rendah (Kompres)", "Kualitas Normal", "Kualitas Tinggi"))
        b.spinnerSaveAs.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Simpan Normal", "Nada Dering (Ringtone)", "Suara Alarm", "Suara Notifikasi"))
    }

    private fun setupTabs() {
        b.tabLayoutAudio.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                b.layoutTabTrim.visibility = View.GONE
                b.layoutTabFormat.visibility = View.GONE
                b.layoutTabSpeed.visibility = View.GONE
                b.layoutTabEq.visibility = View.GONE
                b.layoutTabStereo.visibility = View.GONE
                when (tab?.position) {
                    0 -> b.layoutTabTrim.visibility = View.VISIBLE
                    1 -> b.layoutTabFormat.visibility = View.VISIBLE
                    2 -> b.layoutTabSpeed.visibility = View.VISIBLE
                    3 -> b.layoutTabEq.visibility = View.VISIBLE
                    4 -> b.layoutTabStereo.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSliders() {
        b.seekTempo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = (progress + 50) / 100f
                b.tvTempoLabel.text = "Kecepatan: ${String.format("%.2f", speed)}x"
                dirtyPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val semis = (progress - 50) / 10f
                val label = when {
                    semis > 0 -> "+${String.format("%.1f", semis)} (naik)"
                    semis < 0 -> "${String.format("%.1f", semis)} (turun)"
                    else -> "0 (Tetap)"
                }
                b.tvPitchLabel.text = "Nada: $label"
                dirtyPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        eqFreqs = intArrayOf(31, 44, 60, 80, 150, 250, 360, 460, 700, 900, 1500, 2500, 4000, 6000, 8000, 12000)
        eqBars = arrayOf(b.eq31, b.eq44, b.eq60, b.eq80, b.eq150, b.eq250, b.eq360, b.eq460, b.eq700, b.eq900, b.eq1500, b.eq2500, b.eq4000, b.eq6000, b.eq8000, b.eq12000)

        val eqListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { dirtyPreview() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        for (bar in eqBars) bar.setOnSeekBarChangeListener(eqListener)

        b.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { b.tvVolumeLabel.text = "Volume: $progress%"; dirtyPreview() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekMaster.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { b.tvMasterLabel.text = "Mastering: $progress%"; dirtyPreview() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekLimiter.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { b.tvLimiterLabel.text = "Limiter: $progress%"; dirtyPreview() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekPanning.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val pan = progress - 100
                b.tvPanningLabel.text = "Panning: ${if (pan == 0) "Tengah" else if (pan < 0) "Kiri ($pan)" else "Kanan ($pan)"}"
                dirtyPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekStereo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { b.tvStereoLabel.text = "Stereo Width: $progress%"; dirtyPreview() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekEcho.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { b.tvEchoLabel.text = "Echo: $progress%"; dirtyPreview() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Checkbox tab → invalidasi preview
        val cbListener = View.OnClickListener { dirtyPreview() }
        b.cbTabTrim.setOnClickListener(cbListener)
        b.cbTabFormat.setOnClickListener(cbListener)
        b.cbTabSpeed.setOnClickListener(cbListener)
        b.cbTabEq.setOnClickListener(cbListener)
        b.cbTabStereo.setOnClickListener(cbListener)
        b.cbRemoveWatermark.setOnClickListener(cbListener)
        b.cbRemoveMetadata.setOnClickListener(cbListener)
    }

    private fun setupTimePickers() {
        b.btnPickStartTime.setOnClickListener {
            showTimePicker("Waktu Mulai", startTime) { newTime ->
                startTime = newTime
                b.btnPickStartTime.text = newTime
                dirtyPreview()
            }
        }
        b.btnPickEndTime.setOnClickListener {
            showTimePicker("Waktu Selesai", endTime) { newTime ->
                endTime = newTime
                b.btnPickEndTime.text = newTime
                dirtyPreview()
            }
        }
    }

    private fun showTimePicker(title: String, currentTime: String, onSave: (String) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_time_picker, null)
        val etHours = dialogView.findViewById<EditText>(R.id.etHours)
        val etMinutes = dialogView.findViewById<EditText>(R.id.etMinutes)
        val etSeconds = dialogView.findViewById<EditText>(R.id.etSeconds)

        val parts = currentTime.split(":")
        etHours.setText(parts[0])
        etMinutes.setText(parts[1])
        etSeconds.setText(parts[2])

        if (previewPlayer != null && previewPlayer!!.duration < 3600000) {
            etHours.isEnabled = false
            etHours.setText("00")
            etHours.hint = "00"
        } else {
            etHours.isEnabled = true
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                val h = etHours.text.toString().padStart(2, '0').ifEmpty { "00" }
                val m = etMinutes.text.toString().padStart(2, '0').ifEmpty { "00" }
                val s = etSeconds.text.toString().padStart(2, '0').ifEmpty { "00" }
                onSave("$h:$m:$s")
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun dirtyPreview() {
        previewFile?.delete()
        previewFile = null
    }

    private fun updatePlayButtonToPlay() {
        b.btnPreviewAudio.text = "Putar"
        b.btnPreviewAudio.setIconResource(android.R.drawable.ic_media_play)
        b.btnPreviewAudio.isEnabled = true
    }

    private fun updatePlayButtonToPause() {
        b.btnPreviewAudio.text = "Jeda"
        b.btnPreviewAudio.setIconResource(android.R.drawable.ic_media_pause)
        b.btnPreviewAudio.isEnabled = true
    }

    private fun onPreviewClick() {
        if (isPlayingPreview) {
            previewPlayer?.pause()
            isPlayingPreview = false
            updatePlayButtonToPlay()
            return
        }
        if (isPreparingPreview) return

        val filters = buildAudioFilters()
        if (filters.isEmpty()) {
            previewFile?.delete()
            previewFile = null
            playFile(inputFile)
        } else if (previewFile == null || !previewFile!!.exists()) {
            buildPreview(filters)
        } else {
            playFile(previewFile!!)
        }
    }

    private fun buildPreview(filters: List<String>) {
        isPreparingPreview = true
        b.btnPreviewAudio.text = "Menyiapkan..."
        b.btnPreviewAudio.isEnabled = false

        val outFile = File(cacheDir, "preview_audio_${System.currentTimeMillis()}.m4a")
        val cmd = StringBuilder()
        cmd.append("-y ")
        if (b.cbTabTrim.isChecked && endTime != "00:00:00") {
            cmd.append("-ss $startTime -to $endTime ")
        }
        cmd.append("-i \"${inputFile.absolutePath}\" ")
        cmd.append("-af \"${filters.joinToString(",")}\" ")
        cmd.append("-b:a 192k ")
        cmd.append("\"${outFile.absolutePath}\"")

        FFmpegKit.executeAsync(cmd.toString(), { session ->
            runOnUiThread {
                isPreparingPreview = false
                if (ReturnCode.isSuccess(session.returnCode)) {
                    previewFile = outFile
                    playFile(outFile)
                } else {
                    updatePlayButtonToPlay()
                    Toast.makeText(this, "Gagal menyiapkan preview", Toast.LENGTH_SHORT).show()
                }
            }
        }, { }, { })
    }

    private fun playFile(f: File) {
        try {
            previewPlayer?.release()
            previewPlayer = MediaPlayer().apply {
                setDataSource(f.absolutePath)
                prepare()
                setOnCompletionListener {
                    isPlayingPreview = false
                    updatePlayButtonToPlay()
                }
                start()
            }
            isPlayingPreview = true
            updatePlayButtonToPause()
        } catch (e: Exception) {
            isPlayingPreview = false
            updatePlayButtonToPlay()
            Toast.makeText(this, "Gagal memutar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildAudioFilters(): List<String> {
        val f = mutableListOf<String>()

        // Tab Kecepatan & Nada
        if (b.cbTabSpeed.isChecked) {
            val tempoActive = b.seekTempo.progress != 50
            val pitchActive = b.seekPitch.progress != 50
            if (tempoActive || pitchActive) {
                val speed = (b.seekTempo.progress + 50) / 100f
                val pitchSemis = (b.seekPitch.progress - 50) / 10f
                val pitchFactor = Math.pow(2.0, pitchSemis.toDouble() / 12.0)

                if (pitchSemis != 0f) {
                    f.add("asetrate=$sourceSampleRate*$pitchFactor")
                    f.add("aresample=$sourceSampleRate")
                }

                val atempoTarget = (speed / pitchFactor).toFloat()
                if (atempoTarget != 1.0f) {
                    var rem = atempoTarget.toDouble()
                    while (rem < 0.5) { f.add("atempo=0.5"); rem /= 0.5 }
                    while (rem > 2.0) { f.add("atempo=2.0"); rem /= 2.0 }
                    if (rem != 1.0) f.add("atempo=$rem")
                }
            }
        }

        // Tab EQ & Master
        if (b.cbTabEq.isChecked) {
            if (b.seekVolume.progress != 100) f.add("volume=${b.seekVolume.progress / 100.0}")
            for (i in eqFreqs.indices) {
                val db = eqBars[i].progress - 10
                if (db != 0) f.add("equalizer=f=${eqFreqs[i]}:width_type=o:width=2:g=$db")
            }
            if (b.seekMaster.progress > 0) f.add("dynaudnorm=f=150:g=${b.seekMaster.progress}")
            if (b.seekLimiter.progress > 0) f.add("alimiter=limit=${b.seekLimiter.progress / 100.0}")
        }

        // Tab Stereo
        if (b.cbTabStereo.isChecked) {
            if (b.seekStereo.progress > 0) f.add("stereowiden=delay=20:feedback=0.3:crossfeed=0.3:drymix=0.8")
            if (b.seekEcho.progress > 0) f.add("aecho=0.8:0.9:1000:${b.seekEcho.progress / 100.0}")
            if (b.seekPanning.progress != 100) {
                val panVal = (b.seekPanning.progress - 100) / 100.0
                f.add("pan=stereo|c0=c0*${1-panVal}+c1*${panVal}|c1=c1*${1-panVal}+c0*${panVal}")
            }
        }

        // Tab Format → watermark lowpass
        if (b.cbTabFormat.isChecked && b.cbRemoveWatermark.isChecked) {
            f.add("lowpass=f=16000")
        }

        return f
    }

    private fun processAudio() {
        previewPlayer?.release()
        previewPlayer = null
        isPlayingPreview = false
        previewFile?.delete()
        previewFile = null

        val outputFormat = formats[b.spinnerFormat.selectedItemPosition]
        val isRingtone = b.spinnerSaveAs.selectedItemPosition != 0

        val outputDir = File(Environment.getExternalStorageDirectory(), "PlaProjects/Edit_Audio")
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, "EDIT_${System.currentTimeMillis()}.${outputFormat}")

        val cmd = StringBuilder()
        cmd.append("-y ")

        if (b.cbTabTrim.isChecked && endTime != "00:00:00") {
            cmd.append("-ss $startTime -to $endTime ")
        }
        cmd.append("-i \"${inputFile.absolutePath}\" ")

        val filters = buildAudioFilters()
        if (filters.isNotEmpty()) cmd.append("-af \"${filters.joinToString(",")}\" ")

        if (b.cbTabFormat.isChecked) {
            cmd.append("-b:a ${b.spinnerBitrate.selectedItem} ")
            cmd.append("-ar ${b.spinnerSampleRate.selectedItem} ")
            if (b.cbRemoveMetadata.isChecked) cmd.append("-map_metadata -1 ")
        }

        cmd.append("\"${outputFile.absolutePath}\"")

        b.btnProcess.isEnabled = false
        b.tvProgress.text = "Sedang memproses... Mohon tunggu."

        FFmpegKit.executeAsync(cmd.toString(), { session ->
            runOnUiThread {
                b.btnProcess.isEnabled = true
                if (ReturnCode.isSuccess(session.returnCode)) {
                    registerAudioToMediaStore(outputFile, outputFormat)
                    b.tvProgress.text = if (isRingtone) "Berhasil disimpan sebagai Nada Dering!"
                    else "Berhasil disimpan di:\nPlaProjects/Edit_Audio/${outputFile.name}"
                    Toast.makeText(this, "Audio berhasil diproses!", Toast.LENGTH_LONG).show()
                } else {
                    b.tvProgress.text = "Gagal memproses audio."
                    Toast.makeText(this, "Gagal: ${session.failStackTrace}", Toast.LENGTH_LONG).show()
                }
            }
        }, { }, { })
    }

    private fun registerAudioToMediaStore(file: File, ext: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/$ext")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "PlaProjects/Edit_Audio")
                } else {
                    put(MediaStore.Audio.Media.DATA, file.absolutePath)
                }
            }
            contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        previewPlayer?.release()
        previewFile?.delete()
    }
}