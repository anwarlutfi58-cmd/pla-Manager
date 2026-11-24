package com.pla.manajer

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.material.tabs.TabLayout
import com.pla.manajer.databinding.ActivityAudioEditorBinding
import com.pla.manajer.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudioEditorActivity : AppCompatActivity() {
    private lateinit var b: ActivityAudioEditorBinding
    private lateinit var inputFile: File
    private lateinit var notif: NotificationHelper

    private val formatLabels = arrayOf("MP3", "WAV", "AAC", "M4A", "FLAC (Lossless)", "AIFF", "WAV 24-bit", "MP3 VBR", "Opus", "FLAC 12")
    private val formatIds = arrayOf("mp3", "wav", "aac", "m4a", "flac", "aiff", "wav24", "mp3vbr", "opus", "flac12")

    private var previewPlayer: MediaPlayer? = null
    private var isPlayingPreview = false
    private var previewFile: File? = null
    private var isPreparingPreview = false
    private var sourceSampleRate = 44100
    private var startTime = "00:00:00"
    private var endTime = "00:00:00"
    private var customEffectFilter = ""

    private lateinit var eqFreqs: IntArray
    private lateinit var eqBars: Array<SeekBar>

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAudioEditorBinding.inflate(layoutInflater)
        setContentView(b.root)
        notif = NotificationHelper(this)

        var path = intent.getStringExtra("file_path")
        if (path == null && intent?.action == Intent.ACTION_SEND) {
            val uri = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                      else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            if (uri != null) {
                val tempFile = AudioSystemHelper.copyUriToCache(this, uri, "shared_edit_audio.mp3")
                if (tempFile != null) path = tempFile.absolutePath
            }
        }
        if (path == null) { finish(); return }
        inputFile = File(path)

        b.tvFileName.text = inputFile.name
        detectSampleRate()
        setupSpinners()
        setupTabs()
        setupSliders()
        
        b.btnPickStartTime.setOnClickListener { AudioUiHelper.showTimePicker(this, "Waktu Mulai", startTime) { startTime = it; b.btnPickStartTime.text = it; dirtyPreview() } }
        b.btnPickEndTime.setOnClickListener { AudioUiHelper.showTimePicker(this, "Waktu Selesai", endTime) { endTime = it; b.btnPickEndTime.text = it; dirtyPreview() } }

        val btnMoreTools = Button(this).apply {
            text = "Buka Menu Alat Lanjutan (Efek, Master, 3D)"
            setOnClickListener { 
                AudioUiHelper.showAdvancedToolsMenu(this@AudioEditorActivity, 
                    onFilterSelected = { filter -> customEffectFilter = filter },
                    onVisualizer = { generateVisualizer() }
                ) 
            }
        }
        (b.layoutTabStereoPro as? LinearLayout)?.addView(btnMoreTools)

        b.btnPreviewAudio.setOnClickListener { onPreviewClick() }
        b.btnProcess.setOnClickListener { processAudio() }
        b.btnMeasureLoudness.setOnClickListener { measureLoudness() }
    }

    private fun generateVisualizer() {
        val outDir = File(Environment.getExternalStorageDirectory(), "PlaProjects/Edit_Audio")
        if (!outDir.exists()) outDir.mkdirs()
        val outFile = File(outDir, "visualizer_${System.currentTimeMillis()}.mp4")
        val cmd = AudioAnalysisHelper.getWaveformVideoCommand(inputFile.absolutePath, outFile.absolutePath)
        notif.showEdit("Membuat Visualizer", "Mohon tunggu...")
        
        FFmpegKit.executeAsync(cmd) { session ->
            runOnUiThread {
                notif.hideEdit()
                if (ReturnCode.isSuccess(session.returnCode)) {
                    AudioSystemHelper.registerToMediaStore(this, outFile, "mp4")
                    Toast.makeText(this, "Visualizer disimpan di Edit_Audio", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Gagal membuat visualizer.", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
        b.spinnerQuality.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Kualitas Rendah", "Kualitas Normal", "Kualitas Tinggi"))
        b.spinnerSaveAs.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Simpan Normal", "Nada Dering", "Suara Alarm", "Notifikasi"))
        b.spinnerResampleRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("44100", "48000", "88200", "96000"))
        b.spinnerBitDepth.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("16-bit", "24-bit", "32-bit"))
        b.spinnerBitDepth.setSelection(1)
    }

    private fun setupTabs() {
        b.tabLayoutAudio.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val layouts = listOf(
                    b.layoutTabTrim, b.layoutTabFormat, b.layoutTabSpeed, b.layoutTabEq, b.layoutTabStereo, 
                    b.layoutTabLoudness, b.layoutTabDinamika, b.layoutTabResample, b.layoutTabStereoPro,
                    b.layoutTabVokal, b.layoutTabEfekSpesial, b.layoutTabEchoReverb, b.layoutTabFade
                )
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

        AudioUiHelper.setupSeekBar(b.seekVolume, b.tvVolumeLabel, "Volume: ", "%") { dirtyPreview() }
        AudioUiHelper.setupSeekBar(b.seekMaster, b.tvMasterLabel, "Mastering: ", "%") { dirtyPreview() }
        AudioUiHelper.setupSeekBar(b.seekLimiter, b.tvLimiterLabel, "Limiter: ", "%") { dirtyPreview() }
        
        b.seekPanning.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val pan = progress - 100
                b.tvPanningLabel.text = "Panning: ${if (pan == 0) "Tengah" else if (pan < 0) "Kiri ($pan)" else "Kanan ($pan)"}"; dirtyPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        
        AudioUiHelper.setupSeekBar(b.seekStereo, b.tvStereoLabel, "Stereo Width: ", "%") { dirtyPreview() }
        AudioUiHelper.setupSeekBar(b.seekMbIntensity, b.tvMbIntensityLabel, "Intensitas: ", "%") { dirtyPreview() }
        AudioUiHelper.setupSeekBar(b.seekEnhancerIntensity, b.tvEnhancerIntensityLabel, "Intensitas Enhancer: ", "%") { dirtyPreview() }
        AudioUiHelper.setupSeekBar(b.seekWidenerMid, b.tvWidenerMidLabel, "Mid Level: ", "%") { dirtyPreview() }
        AudioUiHelper.setupSeekBar(b.seekWidenerSide, b.tvWidenerSideLabel, "Side Level: ", "%") { dirtyPreview() }

        // Setup Listener untuk Echo & Reverb
        b.seekEchoDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                b.tvEchoDelayLabel.text = "Delay (Jeda): $progress ms"; dirtyPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        AudioUiHelper.setupSeekBar(b.seekEchoFeedback, b.tvEchoFeedbackLabel, "Feedback (Ulangan): ", "%") { dirtyPreview() }
        AudioUiHelper.setupSeekBar(b.seekReverbRoom, b.tvReverbRoomLabel, "Ukuran Ruangan: ", "%") { dirtyPreview() }
        AudioUiHelper.setupSeekBar(b.seekReverbDamping, b.tvReverbDampingLabel, "Kelembutan Gema: ", "%") { dirtyPreview() }

        AudioUiHelper.setupSeekBar(b.seekFadeIn, b.tvFadeInLabel, "Fade In: ", " Detik") { dirtyPreview() }
        AudioUiHelper.setupSeekBar(b.seekFadeOut, b.tvFadeOutLabel, "Fade Out: ", " Detik") { dirtyPreview() }
        AudioUiHelper.setupSeekBar(b.seekAutoTune, b.tvAutoTuneLabel, "Auto-Tune Ringan: ", "%") { dirtyPreview() }

        val cbL = View.OnClickListener { dirtyPreview() }
        b.cbTabTrim.setOnClickListener(cbL); b.cbTabFormat.setOnClickListener(cbL); b.cbTabSpeed.setOnClickListener(cbL)
        b.cbTabEq.setOnClickListener(cbL); b.cbTabStereo.setOnClickListener(cbL); b.cbTabLoudness.setOnClickListener(cbL)
        b.cbTabDinamika.setOnClickListener(cbL); b.cbTabResample.setOnClickListener(cbL); b.cbTabStereoPro.setOnClickListener(cbL)
        b.cbRemoveWatermark.setOnClickListener(cbL); b.cbRemoveMetadata.setOnClickListener(cbL)
        b.cbNormalizeLufs.setOnClickListener(cbL); b.cbNormalizePeak.setOnClickListener(cbL); b.cbAgc.setOnClickListener(cbL)
        b.cbCompressor.setOnClickListener(cbL); b.cbMultiband.setOnClickListener(cbL); b.cbSoxResample.setOnClickListener(cbL)
        b.cbStereoEnhancer.setOnClickListener(cbL); b.cbStereoWidener.setOnClickListener(cbL)
        
        b.cbTabVokal.setOnClickListener(cbL); b.cbTabEfekSpesial.setOnClickListener(cbL); b.cbTabEchoReverb.setOnClickListener(cbL); b.cbTabFade.setOnClickListener(cbL)
        b.cbVocalDoubler.setOnClickListener(cbL); b.cbWhisper.setOnClickListener(cbL); b.cbBreathControl.setOnClickListener(cbL)
        b.cbDeEsser.setOnClickListener(cbL); b.cbChorus.setOnClickListener(cbL)
        b.cbRobotVoice.setOnClickListener(cbL); b.cbChipmunk.setOnClickListener(cbL); b.cbDeepVoice.setOnClickListener(cbL)
        b.cbTelephone.setOnClickListener(cbL); b.cbRadio.setOnClickListener(cbL); b.cb8DAudio.setOnClickListener(cbL)
        b.cbTapeStop.setOnClickListener(cbL); b.cbVinylCrackle.setOnClickListener(cbL)
        b.cbNoiseReduction.setOnClickListener(cbL); b.cbHighPass.setOnClickListener(cbL)
        b.cbDeClick.setOnClickListener(cbL); b.cbSilenceRemove.setOnClickListener(cbL)
    }

    private fun getFilters(): List<String> {
        val filters = AudioEngineHelper.buildFilters(
            b.cbTabSpeed.isChecked, b.seekTempo.progress, b.seekPitch.progress, sourceSampleRate,
            b.cbTabEq.isChecked, b.seekVolume.progress, eqBars, eqFreqs, b.seekMaster.progress, b.seekLimiter.progress,
            b.cbTabStereo.isChecked, b.seekStereo.progress, b.seekPanning.progress,
            b.cbTabFormat.isChecked, b.cbRemoveWatermark.isChecked,
            b.cbTabLoudness.isChecked, b.cbNormalizeLufs.isChecked, b.cbNormalizePeak.isChecked, b.cbAgc.isChecked,
            b.cbTabDinamika.isChecked, b.cbCompressor.isChecked, b.seekCompThreshold.progress, b.seekCompRatio.progress, b.seekCompAttack.progress, b.seekCompRelease.progress,
            b.cbTabResample.isChecked, b.spinnerResampleRate.selectedItem.toString(), b.cbSoxResample.isChecked,
            b.cbTabStereoPro.isChecked, b.cbStereoEnhancer.isChecked, b.seekEnhancerIntensity.progress, b.cbStereoWidener.isChecked, b.seekWidenerMid.progress, b.seekWidenerSide.progress,
            // Vokal Pro
            b.cbTabVokal.isChecked, b.cbVocalDoubler.isChecked, b.cbWhisper.isChecked, b.cbBreathControl.isChecked, b.cbDeEsser.isChecked, b.cbChorus.isChecked, b.seekAutoTune.progress,
            // Efek Spesial
            b.cbTabEfekSpesial.isChecked, b.cbRobotVoice.isChecked, b.cbChipmunk.isChecked, b.cbDeepVoice.isChecked, b.cbTelephone.isChecked, b.cbRadio.isChecked, b.cb8DAudio.isChecked, b.cbTapeStop.isChecked, b.cbVinylCrackle.isChecked,
            // Echo & Reverb
            b.cbTabEchoReverb.isChecked, b.seekEchoDelay.progress, b.seekEchoFeedback.progress, b.seekReverbRoom.progress, b.seekReverbDamping.progress,
            // Fade & Noise
            b.seekFadeIn.progress, b.seekFadeOut.progress, b.cbNoiseReduction.isChecked, b.cbHighPass.isChecked, b.cbDeClick.isChecked, b.cbSilenceRemove.isChecked
        ).toMutableList()

        if (customEffectFilter.isNotEmpty()) filters.add(customEffectFilter)
        return filters
    }

    private fun needMultiband() = b.cbTabDinamika.isChecked && b.cbMultiband.isChecked
    private fun hasAnyFilter() = getFilters().isNotEmpty() || needMultiband() || customEffectFilter.isNotEmpty()
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
        val simple = getFilters(); val hasMb = needMultiband()
        
        val cmd = StringBuilder("-y ")
        if (b.cbTabTrim.isChecked && endTime != "00:00:00") cmd.append("-ss $startTime -to $endTime ")
        cmd.append("-i \"${inputFile.absolutePath}\" ")
        if (hasMb) cmd.append("-filter_complex \"${AudioEngineHelper.buildMultibandFilterGraph(simple, b.seekMbIntensity.progress)}\" -map \"[mb_out]\" ")
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
        val ext = when (fmt) { "wav24" -> "wav"; "mp3vbr" -> "mp3"; "flac12" -> "flac"; else -> fmt }
        val outFile = AudioSystemHelper.getOutputFile(ext)

        b.btnProcess.isEnabled = false
        b.tvProgress.text = "Sedang memproses... Mohon tunggu."
        notif.showEdit("Memproses Audio", inputFile.name)

        AudioProcessHelper.renderAudio(
            context = this, inputFile = inputFile, outputFile = outFile,
            simpleFilters = getFilters(), hasMb = needMultiband(), mbIntensity = b.seekMbIntensity.progress,
            isTrim = b.cbTabTrim.isChecked, startTime = startTime, endTime = endTime,
            format = fmt, bitDepth = b.spinnerBitDepth.selectedItem.toString(),
            bitrate = b.spinnerBitrate.selectedItem.toString(), sampleRate = b.spinnerSampleRate.selectedItem.toString(),
            removeMeta = b.cbRemoveMetadata.isChecked,
            onSuccess = { file ->
                runOnUiThread {
                    b.btnProcess.isEnabled = true; notif.hideEdit()
                    AudioSystemHelper.registerToMediaStore(this, file, ext)
                    b.tvProgress.text = "Berhasil disimpan di:\nPlaProjects/Edit_Audio/${file.name}"
                    Toast.makeText(this, "Audio berhasil diproses!", Toast.LENGTH_LONG).show()
                }
            },
            onError = { err ->
                runOnUiThread {
                    b.btnProcess.isEnabled = true; notif.hideEdit()
                    b.tvProgress.text = "Gagal memproses audio."
                    Toast.makeText(this, "Gagal: $err", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        previewPlayer?.release()
        previewFile?.delete()
        notif.hideEdit()
    }
}