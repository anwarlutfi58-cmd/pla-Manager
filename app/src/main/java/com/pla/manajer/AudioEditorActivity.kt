package com.pla.manajer

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.material.tabs.TabLayout
import com.pla.manajer.databinding.ActivityAudioEditorBinding
import java.io.File

class AudioEditorActivity : AppCompatActivity() {
    private lateinit var b: ActivityAudioEditorBinding
    private lateinit var inputFile: File
    private val formats = arrayOf("mp3", "wav", "aac", "m4a")

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAudioEditorBinding.inflate(layoutInflater)
        setContentView(b.root)

        val path = intent.getStringExtra("file_path") ?: run { finish(); return }
        inputFile = File(path)
        b.tvFileName.text = inputFile.name

        // Setup Spinner Format
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, formats)
        b.spinnerFormat.adapter = adapter

        // --- LOGIKA PINDAH TAB ---
        b.tabLayoutAudio.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                b.layoutTabTrim.visibility = View.GONE
                b.layoutTabFormat.visibility = View.GONE
                b.layoutTabEfek.visibility = View.GONE
                b.layoutTabMetadata.visibility = View.GONE

                when (tab?.position) {
                    0 -> b.layoutTabTrim.visibility = View.VISIBLE
                    1 -> b.layoutTabFormat.visibility = View.VISIBLE
                    2 -> b.layoutTabEfek.visibility = View.VISIBLE
                    3 -> b.layoutTabMetadata.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // --- LOGIKA SLIDER EFEK & MASTERING ---
        // Volume (0% - 200%)
        b.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                b.tvVolumeLabel.text = "Volume: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Bass 31 Hz
        b.seekBass31.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val db = progress - 10
                b.tvBass31Label.text = "Bass (31 Hz): ${if(db > 0) "+" else ""}$db dB"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Bass 44 Hz
        b.seekBass44.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val db = progress - 10
                b.tvBass44Label.text = "Bass (44 Hz): ${if(db > 0) "+" else ""}$db dB"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Bass 80 Hz
        b.seekBass80.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val db = progress - 10
                b.tvBass80Label.text = "Bass (80 Hz): ${if(db > 0) "+" else ""}$db dB"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Mid
        b.seekMid.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val db = progress - 10
                b.tvMidLabel.text = "Mid (Vokal): ${if(db > 0) "+" else ""}$db dB"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Treble
        b.seekTreble.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val db = progress - 10
                b.tvTrebleLabel.text = "Treble (High): ${if(db > 0) "+" else ""}$db dB"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        b.btnProcess.setOnClickListener { processAudio() }
    }

    private fun processAudio() {
        val outputFormat = formats[b.spinnerFormat.selectedItemPosition]
        val outputFile = File(inputFile.parentFile, "EDIT_${System.currentTimeMillis()}.${outputFormat}")

        val start = b.etStartTime.text.toString().ifEmpty { "00:00" }
        val end = b.etEndTime.text.toString()

        val cmd = StringBuilder()
        cmd.append("-y ")

        if (end.isNotEmpty()) {
            cmd.append("-ss $start -to $end ")
        }

        cmd.append("-i \"${inputFile.absolutePath}\" ")

        // --- MERAKIT FILTER AUDIO (EQ MANUAL, VOLUME, DLL) ---
        val audioFilters = mutableListOf<String>()

        // 1. Filter Hapus Watermark
        if (b.cbRemoveWatermark.isChecked) {
            audioFilters.add("lowpass=f=16000")
        }

        // 2. Filter Volume
        val volProgress = b.seekVolume.progress
        if (volProgress != 100) {
            val volMultiplier = volProgress / 100.0
            audioFilters.add("volume=$volMultiplier")
        }

        // 3. Filter Equalizer Manual (Bass, Mid, Treble)
        val bass31Db = b.seekBass31.progress - 10
        val bass44Db = b.seekBass44.progress - 10
        val bass80Db = b.seekBass80.progress - 10
        val midDb = b.seekMid.progress - 10
        val trebleDb = b.seekTreble.progress - 10

        if (bass31Db != 0) {
            audioFilters.add("equalizer=f=31:width_type=o:width=2:g=$bass31Db")
        }
        if (bass44Db != 0) {
            audioFilters.add("equalizer=f=44:width_type=o:width=2:g=$bass44Db")
        }
        if (bass80Db != 0) {
            audioFilters.add("equalizer=f=80:width_type=o:width=2:g=$bass80Db")
        }
        if (midDb != 0) {
            audioFilters.add("equalizer=f=1000:width_type=o:width=2:g=$midDb")
        }
        if (trebleDb != 0) {
            audioFilters.add("equalizer=f=10000:width_type=o:width=2:g=$trebleDb")
        }

        // 4. Filter Mastering (Kompresi Dinamis agar suara rata)
        if (b.cbMastering.isChecked) {
            audioFilters.add("dynaudnorm=f=150:g=15")
        }

        // Gabungkan semua filter ke dalam satu perintah
        if (audioFilters.isNotEmpty()) {
            val combinedFilters = audioFilters.joinToString(",")
            cmd.append("-af \"$combinedFilters\" ")
        }

        // 5. Metadata
        val title = b.etTitle.text.toString()
        val artist = b.etArtist.text.toString()
        val genre = b.etGenre.text.toString()
        val composer = b.etComposer.text.toString()
        if (title.isNotEmpty()) cmd.append("-metadata title=\"$title\" ")
        if (artist.isNotEmpty()) cmd.append("-metadata artist=\"$artist\" ")
        if (genre.isNotEmpty()) cmd.append("-metadata genre=\"$genre\" ")
        if (composer.isNotEmpty()) cmd.append("-metadata composer=\"$composer\" ")

        cmd.append("\"${outputFile.absolutePath}\"")

        b.btnProcess.isEnabled = false
        b.tvProgress.text = "Sedang memproses... Mohon tunggu."

        // Jalankan FFmpeg
        FFmpegKit.executeAsync(cmd.toString(), { session ->
            runOnUiThread {
                b.btnProcess.isEnabled = true
                if (ReturnCode.isSuccess(session.returnCode)) {
                    b.tvProgress.text = "Berhasil disimpan di:\n${outputFile.name}"
                    Toast.makeText(this, "Audio berhasil diedit!", Toast.LENGTH_LONG).show()
                } else {
                    b.tvProgress.text = "Gagal memproses audio."
                    Toast.makeText(this, "Gagal: ${session.failStackTrace}", Toast.LENGTH_LONG).show()
                }
            }
        }, { log ->
            // Abaikan log
        }, { statistics ->
            // Abaikan progress
        })
    }
}