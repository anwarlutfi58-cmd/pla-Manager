package com.pla.manajer

import android.media.MediaPlayer
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
    
    // MediaPlayer untuk fitur Putar Preview
    private var previewPlayer: MediaPlayer? = null
    private var isPlayingPreview = false

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAudioEditorBinding.inflate(layoutInflater)
        setContentView(b.root)

        val path = intent.getStringExtra("file_path") ?: run { finish(); return }
        inputFile = File(path)
        b.tvFileName.text = inputFile.name

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, formats)
        b.spinnerFormat.adapter = adapter

        // --- SETUP TAB ---
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

        // --- SETUP LABEL VOLUME ---
        b.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                b.tvVolumeLabel.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // --- FITUR PUTAR PREVIEW ---
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

    private fun processAudio() {
        // Hentikan preview jika sedang berjalan
        previewPlayer?.stop()
        previewPlayer?.release()
        previewPlayer = null

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

        val audioFilters = mutableListOf<String>()

        if (b.cbRemoveWatermark.isChecked) {
            audioFilters.add("lowpass=f=16000")
        }

        val volProgress = b.seekVolume.progress
        if (volProgress != 100) {
            audioFilters.add("volume=${volProgress / 100.0}")
        }

        // Fungsi pembantu untuk menambahkan EQ
        fun addEq(freq: Int, seekBar: SeekBar) {
            val db = seekBar.progress - 10
            if (db != 0) {
                audioFilters.add("equalizer=f=$freq:width_type=o:width=2:g=$db")
            }
        }

        addEq(31, b.seek31); addEq(44, b.seek44); addEq(80, b.seek80)
        addEq(250, b.seek250); addEq(500, b.seek500); addEq(1000, b.seek1k)
        addEq(2000, b.seek2k); addEq(4000, b.seek4k); addEq(8000, b.seek8k)
        addEq(12000, b.seek12k)

        if (b.cbMastering.isChecked) {
            audioFilters.add("dynaudnorm=f=150:g=15")
        }
        if (b.cbLimiter.isChecked) {
            audioFilters.add("alimiter=limit=0.95")
        }

        if (audioFilters.isNotEmpty()) {
            cmd.append("-af \"${audioFilters.joinToString(",")}\" ")
        }

        val title = b.etTitle.text.toString()
        val artist = b.etArtist.text.toString()
        val album = b.etAlbum.text.toString()
        val genre = b.etGenre.text.toString()
        val composer = b.etComposer.text.toString()
        val year = b.etYear.text.toString()
        val track = b.etTrack.text.toString()

        if (title.isNotEmpty()) cmd.append("-metadata title=\"$title\" ")
        if (artist.isNotEmpty()) cmd.append("-metadata artist=\"$artist\" ")
        if (album.isNotEmpty()) cmd.append("-metadata album=\"$album\" ")
        if (genre.isNotEmpty()) cmd.append("-metadata genre=\"$genre\" ")
        if (composer.isNotEmpty()) cmd.append("-metadata composer=\"$composer\" ")
        if (year.isNotEmpty()) cmd.append("-metadata date=\"$year\" ")
        if (track.isNotEmpty()) cmd.append("-metadata track=\"$track\" ")

        cmd.append("\"${outputFile.absolutePath}\"")

        b.btnProcess.isEnabled = false
        b.tvProgress.text = "Sedang memproses... Mohon tunggu."

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
        }, { }, { })
    }

    override fun onDestroy() {
        super.onDestroy()
        previewPlayer?.release()
        previewPlayer = null
    }
}