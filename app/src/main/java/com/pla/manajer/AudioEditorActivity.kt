package com.pla.manajer

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
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
import com.pla.manajer.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AudioEditorActivity : AppCompatActivity() {
    private lateinit var b: ActivityAudioEditorBinding
    private lateinit var inputFile: File
    private lateinit var notif: NotificationHelper

    private val formatLabels = arrayOf("MP3", "WAV", "AAC", "M4A", "FLAC (Lossless)", "AIFF (Lossless)", "WAV 24-bit", "MP3 VBR")
    private val formatIds = arrayOf("mp3", "wav", "aac", "m4a", "flac", "aiff", "wav24", "mp3vbr")

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
        notif = NotificationHelper(this)

        // --- PENANGKAP SHARE INTENT DARI LUAR ---
        var path = intent.getStringExtra("file_path")
        if (path == null && intent?.action == Intent.ACTION_SEND) {
            val uri = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null) {
                val tempFile = File(cacheDir, "shared_edit_audio.mp3")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                path = tempFile.absolutePath
            }
        }
        if (path == null) { finish(); return }
        inputFile = File(path)
        // ----------------------------------------

        b.tvFileName.text = inputFile.name

        detectSampleRate()
        setupSpinners()
        setupTabs()
        setupSliders()
        setupTimePickers()

        b.btnPreviewAudio.setOnClickListener { onPreviewClick() }
        b.btnProcess.setOnClickListener { processAudio() }
        b.btnMeasureLoudness.setOnClickListener { measureLoudness() }
    }

    private fun detectSampleRate() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val session = FFprobeKit.getMediaInformation(inputFile.absolutePath)
                val sr = session?.mediaInformation?.streams?.firstOrNull { it.type == "audio" }?.sampleRate
                if (!sr.isNullOrBlank()) sourceSampleRate = sr.toIntOrNull() ?: 44100
            } catch (_: Exception) { sourceSampleRate = 44100 }
        }
    }

    private fun setupSpinners() {
        b.spinnerFormat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, formatLabels)
        b.spinnerBitrate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("64k", "128k", "192k", "320k"))
        b.spinnerSampleRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("44100", "48000"))
        b.spinnerQuality.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Kualitas Rendah (Kompres)", "Kualitas Normal", "Kualitas Tinggi"))
        b.spinnerSaveAs.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Simpan Normal", "Nada Dering (Ringtone)", "Suara Alarm", "Suara Notifikasi"))
        b.spinnerResampleRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("44100", "48000", "88200", "96000"))
        b.spinnerBitDepth.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("16-bit", "24-bit", "32-bit"))
        b.spinnerBitDepth.setSelection(1)
    }

    private fun setupTabs() {
        b.tabLayoutAudio.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val layouts = listOf(b.layoutTabTrim, b.layoutTabFormat, b.layoutTabSpeed, b.layoutTabEq, b.layoutTabStereo, b.layoutTabLoudness, b.layoutTabDinamika, b.layoutTabResample, b.layoutTabStereoPro)
                layouts.forEach { it.visibility = View.GONE }
                val i = tab?.position ?: 0
                if (i in layouts.indices) layouts[i].visibility = View.VISIBLE
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSliders() {
        b.seekTempo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                b.tvTempoLabel.text = "Kecepatan: ${String.format("%.2f", (progress + 50) / 100f)}x"; dirtyPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val semis = (progress - 50) / 10f
                b.tvPitchLabel.text = "Nada: ${if (semis > 0) "+" else ""}${String.format("%.1f", semis)} ${if (semis > 0) "(naik)" else if (semis < 0) "(turun)" else "(tetap)"}"; dirtyPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        eqFreqs = intArrayOf(31, 44, 60, 80, 150, 250, 360, 460, 700, 900, 1500, 2500, 4000, 6000, 8000, 12000)
        eqBars = arrayOf(b.eq31, b.eq44, b.eq60, b.eq80, b.eq150, b.eq250, b.eq360, b.eq460, b.eq700, b.eq900, b.eq1500, b.eq2500, b.eq4000, b.eq6000, b.eq8000, b.eq12000)
        val eqL = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { dirtyPreview() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
        for (bar in eqBars) bar.setOnSeekBarChangeListener(eqL)

        b.seekVolume.setOnSeekBarChangeListener(slider(b.tvVolumeLabel, "Volume: ", "%"))
        b.seekMaster.setOnSeekBarChangeListener(slider(b.tvMasterLabel, "Mastering: ", "%"))
        b.seekLimiter.setOnSeekBarChangeListener(slider(b.tvLimiterLabel, "Limiter: ", "%"))
        b.seekEcho.setOnSeekBarChangeListener(slider(b.tvEchoLabel, "Echo: ", "%"))

        b.seekPanning.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val pan = progress - 100
                b.tvPanningLabel.text = "Panning: ${if (pan == 0) "Tengah" else if (pan < 0) "Kiri ($pan)" else "Kanan ($pan)"}"; dirtyPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekStereo.setOnSeekBarChangeListener(slider(b.tvStereoLabel, "Stereo Width: ", "%"))

        b.seekCompThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                b.tvCompThresholdLabel.text = "Threshold: ${String.format("%.0f", -60.0 + (progress / 100.0) * 60.0)} dB"; dirtyPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekCompRatio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                b.tvCompRatioLabel.text = "Ratio: ${String.format("%.1f", 1 + progress / 100.0 * 9)}:1"; dirtyPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekCompAttack.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                b.tvCompAttackLabel.text = "Attack: ${String.format("%.0f", 0.01 + progress / 100.0 * 199.99)} ms"; dirtyPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekCompRelease.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                b.tvCompReleaseLabel.text = "Release: ${String.format("%.0f", 10 + progress / 100.0 * 990)} ms"; dirtyPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekMbIntensity.setOnSeekBarChangeListener(slider(b.tvMbIntensityLabel, "Intensitas: ", "%"))
        b.seekEnhancerIntensity.setOnSeekBarChangeListener(slider(b.tvEnhancerIntensityLabel, "Intensitas Enhancer: ", "%"))
        b.seekWidenerMid.setOnSeekBarChangeListener(slider(b.tvWidenerMidLabel, "Mid Level: ", "%"))
        b.seekWidenerSide.setOnSeekBarChangeListener(slider(b.tvWidenerSideLabel, "Side Level: ", "%"))

        val cbL = View.OnClickListener { dirtyPreview() }
        b.cbTabTrim.setOnClickListener(cbL); b.cbTabFormat.setOnClickListener(cbL); b.cbTabSpeed.setOnClickListener(cbL)
        b.cbTabEq.setOnClickListener(cbL); b.cbTabStereo.setOnClickListener(cbL); b.cbTabLoudness.setOnClickListener(cbL)
        b.cbTabDinamika.setOnClickListener(cbL); b.cbTabResample.setOnClickListener(cbL); b.cbTabStereoPro.setOnClickListener(cbL)
        b.cbRemoveWatermark.setOnClickListener(cbL); b.cbRemoveMetadata.setOnClickListener(cbL)
        b.cbNormalizeLufs.setOnClickListener(cbL); b.cbNormalizePeak.setOnClickListener(cbL); b.cbAgc.setOnClickListener(cbL)
        b.cbCompressor.setOnClickListener(cbL); b.cbMultiband.setOnClickListener(cbL); b.cbSoxResample.setOnClickListener(cbL)
        b.cbStereoEnhancer.setOnClickListener(cbL); b.cbStereoWidener.setOnClickListener(cbL)
    }

    private fun slider(label: android.widget.TextView, prefix: String, suffix: String) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
            label.text = "$prefix$progress$suffix"; dirtyPreview()
        }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }

    private fun setupTimePickers() {
        b.btnPickStartTime.setOnClickListener { showTimePicker("Waktu Mulai", startTime) { startTime = it; b.btnPickStartTime.text = it; dirtyPreview() } }
        b.btnPickEndTime.setOnClickListener { showTimePicker("Waktu Selesai", endTime) { endTime = it; b.btnPickEndTime.text = it; dirtyPreview() } }
    }

    private fun showTimePicker(title: String, currentTime: String, onSave: (String) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_time_picker, null)
        val etH = dialogView.findViewById<EditText>(R.id.etHours)
        val etM = dialogView.findViewById<EditText>(R.id.etMinutes)
        val etS = dialogView.findViewById<EditText>(R.id.etSeconds)
        val parts = currentTime.split(":")
        etH.setText(parts[0]); etM.setText(parts[1]); etS.setText(parts[2])
        if (previewPlayer != null && previewPlayer!!.duration < 3600000) {
            etH.isEnabled = false; etH.setText("00"); etH.hint = "00"
        }
        AlertDialog.Builder(this).setTitle(title).setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                val h = etH.text.toString().padStart(2, '0').ifEmpty { "00" }
                val m = etM.text.toString().padStart(2, '0').ifEmpty { "00" }
                val s = etS.text.toString().padStart(2, '0').ifEmpty { "00" }
                onSave("$h:$m:$s")
            }.setNegativeButton("Batal", null).show()
    }

    private fun buildSimpleFilters(): List<String> {
        val f = mutableListOf<String>()

        if (b.cbTabSpeed.isChecked) {
            val tempoActive = b.seekTempo.progress != 50
            val pitchActive = b.seekPitch.progress != 50
            if (tempoActive || pitchActive) {
                val speed = (b.seekTempo.progress + 50) / 100f
                val pitchSemis = (b.seekPitch.progress - 50) / 10f
                val pitchFactor = Math.pow(2.0, pitchSemis.toDouble() / 12.0)
                if (pitchSemis != 0f) {
                    f.add("asetrate=${(sourceSampleRate * pitchFactor).toInt()}")
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

        if (b.cbTabEq.isChecked) {
            if (b.seekVolume.progress != 100) f.add("volume=${b.seekVolume.progress / 100.0}")
            for (i in eqFreqs.indices) {
                val db = eqBars[i].progress - 10
                if (db != 0) f.add("equalizer=f=${eqFreqs[i]}:width_type=o:width=2:g=$db")
            }
            if (b.seekMaster.progress > 0) f.add("dynaudnorm=f=150:g=${b.seekMaster.progress}")
            if (b.seekLimiter.progress > 0) f.add("alimiter=limit=${b.seekLimiter.progress / 100.0}")
        }

        if (b.cbTabStereo.isChecked) {
            if (b.seekStereo.progress > 0) f.add("stereowiden=delay=20:feedback=0.3:crossfeed=0.3:drymix=0.8")
            if (b.seekEcho.progress > 0) f.add("aecho=0.8:0.9:1000:${b.seekEcho.progress / 100.0}")
            if (b.seekPanning.progress != 100) {
                val p = (b.seekPanning.progress - 100) / 100.0
                f.add("pan=stereo|c0=c0*${1-p}+c1*$p|c1=c1*${1-p}+c0*$p")
            }
        }

        if (b.cbTabFormat.isChecked && b.cbRemoveWatermark.isChecked) f.add("lowpass=f=16000")

        if (b.cbTabLoudness.isChecked) {
            if (b.cbNormalizeLufs.isChecked) f.add("loudnorm=I=-14:TP=-1:LRA=11")
            else if (b.cbNormalizePeak.isChecked) f.add("loudnorm=I=-1:TP=-1:LRA=11")
            if (b.cbAgc.isChecked) f.add("dynaudnorm=p=0.9:s=5:m=10")
        }

        if (b.cbTabDinamika.isChecked && b.cbCompressor.isChecked) {
            val db = -60.0 + (b.seekCompThreshold.progress / 100.0) * 60.0
            val threshold = Math.pow(10.0, db / 20.0)
            val ratio = 1.0 + (b.seekCompRatio.progress / 100.0) * 9.0
            val attack = 0.01 + (b.seekCompAttack.progress / 100.0) * 199.99
            val release = 10.0 + (b.seekCompRelease.progress / 100.0) * 990.0
            f.add("acompressor=threshold=$threshold:ratio=$ratio:attack=$attack:release=$release")
        }

        if (b.cbTabResample.isChecked) {
            val sr = b.spinnerResampleRate.selectedItem.toString()
            if (b.cbSoxResample.isChecked) f.add("aresample=resampler=soxr:osr=$sr") else f.add("aresample=osr=$sr")
        }

        if (b.cbTabStereoPro.isChecked) {
            if (b.cbStereoEnhancer.isChecked) {
                val k = 1.0 + (b.seekEnhancerIntensity.progress / 100.0) * 0.5
                f.add("stereotools=mlev=$k:slev=$k")
            }
            if (b.cbStereoWidener.isChecked) {
                val mid = 0.5 + (b.seekWidenerMid.progress / 100.0)
                val side = 0.5 + (b.seekWidenerSide.progress / 100.0)
                f.add("stereotools=mlev=$mid:slev=$side")
            }
        }
        return f
    }

    private fun needMultiband() = b.cbTabDinamika.isChecked && b.cbMultiband.isChecked

    private fun buildMultibandFilterGraph(simple: List<String>): String {
        val intensity = b.seekMbIntensity.progress
        val th = (0.3 - (intensity / 100.0) * 0.25).coerceAtLeast(0.05)
        val comp = "acompressor=threshold=$th:ratio=4:attack=20:release=250"
        val prefix = if (simple.isEmpty()) "" else simple.joinToString(",") + ","
        return "[0:a]${prefix}asplit=3[mb_lo][mb_mi][mb_hi];" +
            "[mb_lo]lowpass=f=200,$comp[mb_lo2];" +
            "[mb_mi]highpass=f=200,lowpass=f=2000,$comp[mb_mi2];" +
            "[mb_hi]highpass=f=2000,$comp[mb_hi2];" +
            "[mb_lo2][mb_mi2][mb_hi2]amix=inputs=3[mb_out]"
    }

    private fun hasAnyFilter() = buildSimpleFilters().isNotEmpty() || needMultiband()

    private fun dirtyPreview() { previewFile?.delete(); previewFile = null }

    private fun setPlayBtn(playing: Boolean) {
        if (playing) { b.btnPreviewAudio.text = "Jeda"; b.btnPreviewAudio.setIconResource(android.R.drawable.ic_media_pause) }
        else { b.btnPreviewAudio.text = "Putar"; b.btnPreviewAudio.setIconResource(android.R.drawable.ic_media_play) }
        b.btnPreviewAudio.isEnabled = true
    }

    private fun onPreviewClick() {
        if (isPlayingPreview) { previewPlayer?.pause(); isPlayingPreview = false; setPlayBtn(false); return }
        if (isPreparingPreview) return
        if (!hasAnyFilter()) { previewFile?.delete(); previewFile = null; playFile(inputFile) }
        else if (previewFile == null || !previewFile!!.exists()) buildPreview()
        else playFile(previewFile!!)
    }

    private fun buildPreview() {
        isPreparingPreview = true
        b.btnPreviewAudio.text = "Menyiapkan..."
        b.btnPreviewAudio.isEnabled = false
        notif.showEdit("Menyiapkan Preview", inputFile.name)

        val outFile = File(cacheDir, "preview_${System.currentTimeMillis()}.m4a")
        val simple = buildSimpleFilters(); val hasMb = needMultiband()
        val cmd = StringBuilder("-y ")
        if (b.cbTabTrim.isChecked && endTime != "00:00:00") cmd.append("-ss $startTime -to $endTime ")
        cmd.append("-i \"${inputFile.absolutePath}\" ")
        if (hasMb) cmd.append("-filter_complex \"${buildMultibandFilterGraph(simple)}\" -map \"[mb_out]\" ")
        else if (simple.isNotEmpty()) cmd.append("-filter_complex \"[0:a]${simple.joinToString(",")}[out]\" -map \"[out]\" ")
        cmd.append("-b:a 192k \"${outFile.absolutePath}\"")

        FFmpegKit.executeAsync(cmd.toString(), { session ->
            runOnUiThread {
                isPreparingPreview = false
                notif.hideEdit()
                if (ReturnCode.isSuccess(session.returnCode)) { previewFile = outFile; playFile(outFile) }
                else { setPlayBtn(false); Toast.makeText(this, "Gagal menyiapkan preview", Toast.LENGTH_SHORT).show() }
            }
        }, { }, { })
    }

    private fun playFile(f: File) {
        try {
            previewPlayer?.release()
            previewPlayer = MediaPlayer().apply {
                setDataSource(f.absolutePath); prepare()
                setOnCompletionListener { isPlayingPreview = false; setPlayBtn(false) }
                start()
            }
            isPlayingPreview = true; setPlayBtn(true)
        } catch (e: Exception) {
            isPlayingPreview = false; setPlayBtn(false)
            Toast.makeText(this, "Gagal memutar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun measureLoudness() {
        b.tvLoudnessResult.text = "Mengukur..."
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val session = FFmpegKit.execute("-i \"${inputFile.absolutePath}\" -af ebur128=peak=true -f null -")
                val logs = session.allLogsAsString ?: ""
                val i = Regex("I:\\s*(-?[\\d.]+)\\s*LUFS").findAll(logs).lastOrNull()?.groupValues?.get(1) ?: "?"
                val lra = Regex("LRA:\\s*(-?[\\d.]+)\\s*LU").findAll(logs).lastOrNull()?.groupValues?.get(1) ?: "?"
                val peak = Regex("Peak:\\s*(-?[\\d.]+)\\s*dBFS").findAll(logs).lastOrNull()?.groupValues?.get(1) ?: "?"
                withContext(Dispatchers.Main) { b.tvLoudnessResult.text = "Loudness: $i LUFS\nLRA: $lra LU\nTrue Peak: $peak dBFS" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { b.tvLoudnessResult.text = "Gagal mengukur: ${e.message}" }
            }
        }
    }

    private fun processAudio() {
        previewPlayer?.release(); previewPlayer = null; isPlayingPreview = false
        previewFile?.delete(); previewFile = null

        val idx = b.spinnerFormat.selectedItemPosition
        val fmt = formatIds[idx.coerceIn(0, formatIds.size - 1)]
        val ext = when (fmt) { "wav24" -> "wav"; "mp3vbr" -> "mp3"; else -> fmt }

        val outDir = File(Environment.getExternalStorageDirectory(), "PlaProjects/Edit_Audio")
        if (!outDir.exists()) outDir.mkdirs()
        val outFile = File(outDir, "EDIT_${System.currentTimeMillis()}.${ext}")

        val simple = buildSimpleFilters(); val hasMb = needMultiband()
        val cmd = StringBuilder("-y ")
        if (b.cbTabTrim.isChecked && endTime != "00:00:00") cmd.append("-ss $startTime -to $endTime ")
        cmd.append("-i \"${inputFile.absolutePath}\" ")
        if (hasMb) cmd.append("-filter_complex \"${buildMultibandFilterGraph(simple)}\" -map \"[mb_out]\" ")
        else if (simple.isNotEmpty()) cmd.append("-filter_complex \"[0:a]${simple.joinToString(",")}[out]\" -map \"[out]\" ")

        val bitDepth = b.spinnerBitDepth.selectedItem.toString()
        when (fmt) {
            "flac" -> cmd.append("-c:a flac -compression_level 8 " + if (bitDepth == "16-bit") "-sample_fmt s16 " else "-sample_fmt s32 ")
            "wav" -> cmd.append("-c:a pcm_s16le ")
            "wav24" -> cmd.append("-c:a pcm_s