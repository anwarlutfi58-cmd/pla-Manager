package com.pla.manajer

import android.app.AlertDialog
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.pla.manajer.databinding.ActivityAudioEditorBinding
import java.io.File

class AudioEditorActivity : AppCompatActivity() {
    private lateinit var b: ActivityAudioEditorBinding
    private lateinit var inputFile: File
    private val formats = arrayOf("mp3", "wav", "aac", "m4a")
    
    private var previewPlayer: MediaPlayer? = null
    private var isPlayingPreview = false

    // Waktu Potong
    private var startTime = "00:00:00"
    private var endTime = "00:00:00"

    // Daftar 16 Band EQ
    private lateinit var eqFreqs: IntArray
    private lateinit var eqBars: Array<SeekBar>

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAudioEditorBinding.inflate(layoutInflater)
        setContentView(b.root)

        val path = intent.getStringExtra("file_path") ?: run { finish(); return }
        inputFile = File(path)
        b.tvFileName.text = inputFile.name

        setupSpinners()
        setupTabs()
        setupSliders()
        setupTimePickers()

        // Fitur Putar Preview
        b.btnPreviewAudio.setOnClickListener {
            if (isPlayingPreview) {
                previewPlayer?.pause()
                isPlayingPreview = false
                b.btnPreviewAudio.text = "Putar"
                b.btnPreviewAudio.setIconResource(android.R.drawable.ic_media_play)
            } else {
                if (previewPlayer == null) {
                    previewPlayer = MediaPlayer().apply {
                        setDataSource(inputFile.absolutePath)
                        prepare()
                        setOnCompletionListener {
                            isPlayingPreview = false
                            b.btnPreviewAudio.text = "Putar"
                            b.btnPreviewAudio.setIconResource(android.R.drawable.ic_media_play)
                        }
                    }
                }
                previewPlayer?.start()
                isPlayingPreview = true
                b.btnPreviewAudio.text = "Jeda"
                b.btnPreviewAudio.setIconResource(android.R.drawable.ic_media_pause)
            }
        }

        b.btnProcess.setOnClickListener { processAudio() }
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
        // Kecepatan & Nada
        b.seekTempo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = (progress + 50) / 100f
                b.tvTempoLabel.text = "Kecepatan: ${String.format("%.2f", speed)}x"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = (progress - 50) / 10f
                b.tvPitchLabel.text = "Nada: ${if (pitch > 0) "+" else ""}$pitch"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // EQ 16 Band
        eqFreqs = intArrayOf(31, 44, 60, 80, 150, 250, 360, 460, 700, 900, 1500, 2500, 4000, 6000, 8000, 12000)
        eqBars = arrayOf(b.eq31, b.eq44, b.eq60, b.eq80, b.eq150, b.eq250, b.eq360, b.eq460, b.eq700, b.eq900, b.eq1500, b.eq2500, b.eq4000, b.eq6000, b.eq8000, b.eq12000)

        b.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { b.tvVolumeLabel.text = "Volume: $progress%" }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.seekMaster.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { b.tvMasterLabel.text = "Mastering: $progress%" }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.seekLimiter.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { b.tvLimiterLabel.text = "Limiter: $progress%" }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Stereo
        b.seekPanning.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val pan = progress - 100
                b.tvPanningLabel.text = "Panning: ${if (pan == 0) "Tengah" else if (pan < 0) "Kiri ($pan)" else "Kanan ($pan)"}"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.seekStereo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { b.tvStereoLabel.text = "Stereo Width: $progress%" }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.seekEcho.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { b.tvEchoLabel.text = "Echo: $progress%" }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupTimePickers() {
        b.btnPickStartTime.setOnClickListener { showTimePicker("Waktu Mulai", startTime) { newTime -> 
            startTime = newTime
            b.btnPickStartTime.text = newTime
        }}
        
        b.btnPickEndTime.setOnClickListener { showTimePicker("Waktu Selesai", endTime) { newTime -> 
            endTime = newTime
            b.btnPickEndTime.text = newTime
        }}
    }

    private fun showTimePicker(title: String, currentTime: String, onSave: (String) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_time_picker, null)
        val etHours = dialogView.findViewById<EditText>(R.id.etHours)
        val etMinutes = dialogView.findViewById<EditText>(R.id.etMinutes)
        val etSeconds = dialogView.findViewById<EditText>(R.id.etSeconds)
        
        // Split format "HH:MM:SS"
        val parts = currentTime.split(":")
        etHours.setText(parts[0])
        etMinutes.setText(parts[1])
        etSeconds.setText(parts[2])

        // Logika matikan kolom jam jika durasi file < 1 jam
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

    private fun processAudio() {
        previewPlayer?.stop()
        previewPlayer?.release()
        previewPlayer = null

        val outputFormat = formats[b.spinnerFormat.selectedItemPosition]
        val isRingtone = b.spinnerSaveAs.selectedItemPosition != 0
        val outputFile = File(inputFile.parentFile, "EDIT_${System.currentTimeMillis()}.${outputFormat}")

        val cmd = StringBuilder()
        cmd.append("-y ")

        // Trim (Waktu)
        if (endTime != "00:00:00") {
            cmd.append("-ss $startTime -to $endTime ")
        }

        cmd.append("-i \"${inputFile.absolutePath}\" ")

        val audioFilters = mutableListOf<String>()

        // Watermark
        if (b.cbRemoveWatermark.isChecked) audioFilters.add("lowpass=f=16000")

        // Tempo & Pitch
        val tempo = (b.seekTempo.progress + 50) / 100f
        val pitch = (b.seekPitch.progress - 50) / 10f
        
        if (tempo != 1.0f) audioFilters.add("atempo=$tempo")
        
        if (pitch != 0.0f) {
            val pitchMultiplier = Math.pow(2.0, pitch.toDouble() / 12.0)
            audioFilters.add("asetrate=44100*${pitchMultiplier},aresample=44100,atempo=${1.0/pitchMultiplier}")
        }

        // Volume
        if (b.seekVolume.progress != 100) audioFilters.add("volume=${b.seekVolume.progress / 100.0}")

        // EQ 16 Band
        for (i in eqFreqs.indices) {
            val db = eqBars[i].progress - 10
            if (db != 0) audioFilters.add("equalizer=f=${eqFreqs[i]}:width_type=o:width=2:g=$db")
        }

        // Mastering & Limiter (Slider)
        if (b.seekMaster.progress > 0) {
            val strength = b.seekMaster.progress
            audioFilters.add("dynaudnorm=f=150:g=$strength")
        }
        if (b.seekLimiter.progress > 0) {
            audioFilters.add("alimiter=limit=${b.seekLimiter.progress / 100.0}")
        }

        // Stereo
        if (b.seekStereo.progress > 0) {
            audioFilters.add("stereowiden=delay=20:feedback=0.3:crossfeed=0.3:drymix=0.8")
        }
        if (b.seekEcho.progress > 0) {
            val mix = b.seekEcho.progress / 100.0
            audioFilters.add("aecho=0.8:0.9:1000:$mix")
        }
        if (b.seekPanning.progress != 100) {
            val panVal = (b.seekPanning.progress - 100) / 100.0
            audioFilters.add("pan=stereo|c0=c0*${1-panVal}+c1*${panVal}|c1=c1*${1-panVal}+c0*${panVal}")
        }

        if (audioFilters.isNotEmpty()) cmd.append("-af \"${audioFilters.joinToString(",")}\" ")

        // Format & Kualitas
        cmd.append("-b:a ${b.spinnerBitrate.selectedItem} ")
        cmd.append("-ar ${b.spinnerSampleRate.selectedItem} ")
        
        if (b.cbRemoveMetadata.isChecked) cmd.append("-map_metadata -1 ")

        cmd.append("\"${outputFile.absolutePath}\"")

        b.btnProcess.isEnabled = false
        b.tvProgress.text = "Sedang memproses... Mohon tunggu."

        FFmpegKit.executeAsync(cmd.toString(), { session ->
            runOnUiThread {
                b.btnProcess.isEnabled = true
                if (ReturnCode.isSuccess(session.returnCode)) {
                    if (isRingtone) {
                        b.tvProgress.text = "Berhasil disimpan sebagai Nada Dering!"
                    } else {
                        b.tvProgress.text = "Berhasil disimpan di:\n${outputFile.name}"
                    }
                    Toast.makeText(this, "Audio berhasil diproses!", Toast.LENGTH_LONG).show()
                } else {
                    b.tvProgress.text = "Gagal memproses audio."
                    Toast.makeText(this, "Gagal: ${session.failStackTrace}", Toast.LENGTH_LONG).show()
                }
            }
        }, { }, { })
    }

    override fun onDestroy() {
        super.onDestroy()
        previewPlayer?.release()
    }
}