package com.pla.manajer

import android.content.ContentValues
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
    
    private var previewPlayer: MediaPlayer? = null
    private var isPlayingPreview = false

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAudioEditorBinding.inflate(layoutInflater)
        setContentView(b.root)

        val path = intent.getStringExtra("file_path") ?: run { finish(); return }
        inputFile = File(path)
        b.tvFileName.text = inputFile.name

        // Setup Spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, formats)
        b.spinnerFormat.adapter = adapter
        b.spinnerBitrate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("64k", "128k", "192k", "320k"))
        b.spinnerSampleRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("44100", "48000"))

        // Setup Tab
        b.tabLayoutAudio.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                b.layoutTabWaktu.visibility = View.GONE
                b.layoutTabKualitas.visibility = View.GONE
                b.layoutTabEfek.visibility = View.GONE
                b.layoutTabMetadata.visibility = View.GONE
                when (tab?.position) {
                    0 -> b.layoutTabWaktu.visibility = View.VISIBLE
                    1 -> b.layoutTabKualitas.visibility = View.VISIBLE
                    2 -> b.layoutTabEfek.visibility = View.VISIBLE
                    3 -> b.layoutTabMetadata.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Setup Slider Label
        b.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { b.tvVolumeLabel.text = "$progress%" }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        
        b.seekTempo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = (progress + 50) / 100f
                b.tvTempoLabel.text = "Kecepatan (Tempo): ${String.format("%.2f", speed)}x"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = (progress - 50) / 10f
                b.tvPitchLabel.text = "Nada (Pitch): ${if (pitch > 0) "+" else ""}$pitch semitone"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Fitur Putar
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
        previewPlayer?.stop()
        previewPlayer?.release()
        previewPlayer = null

        val outputFormat = formats[b.spinnerFormat.selectedItemPosition]
        val isRingtone = b.cbRingtone.isChecked
        val outputFile = File(inputFile.parentFile, "EDIT_${System.currentTimeMillis()}.${outputFormat}")

        val cmd = StringBuilder()
        cmd.append("-y ")

        val start = b.etStartTime.text.toString().ifEmpty { "00:00" }
        val end = b.etEndTime.text.toString()
        if (end.isNotEmpty()) cmd.append("-ss $start -to $end ")

        cmd.append("-i \"${inputFile.absolutePath}\" ")

        val audioFilters = mutableListOf<String>()

        // 1. Watermark
        if (b.cbRemoveWatermark.isChecked) audioFilters.add("lowpass=f=16000")

        // 2. Silence Remove
        if (b.cbSilenceRemove.isChecked) audioFilters.add("silenceremove=start_periods=1:start_duration=1:start_threshold=-50dB:detection=peak")

        // 3. Tempo & Pitch
        val tempo = (b.seekTempo.progress + 50) / 100f
        val pitch = (b.seekPitch.progress - 50) / 10f
        
        if (b.cbSpeedRamping.isChecked) {
            // Speed Ramping Sederhana (Mulai pelan, makin cepat)
            audioFilters.add("atempo=1.0,atempo=1.1,atempo=1.2") 
        } else if (tempo != 1.0f) {
            audioFilters.add("atempo=$tempo")
        }

        if (pitch != 0.0f) {
            val pitchMultiplier = Math.pow(2.0, pitch.toDouble() / 12.0)
            // Ubah Sample Rate lalu kembalikan (teknik Pitch Shift FFmpeg)
            audioFilters.add("asetrate=44100*${pitchMultiplier},aresample=44100,atempo=${1.0/pitchMultiplier}")
        }

        // 4. Volume
        if (b.seekVolume.progress != 100) audioFilters.add("volume=${b.seekVolume.progress / 100.0}")

        // 5. EQ
        fun addEq(freq: Int, seekBar: SeekBar) {
            val db = seekBar.progress - 10
            if (db != 0) audioFilters.add("equalizer=f=$freq:width_type=o:width=2:g=$db")
        }
        addEq(31, b.seek31); addEq(44, b.seek44); addEq(80, b.seek80); addEq(250, b.seek250)
        addEq(500, b.seek500); addEq(1000, b.seek1k); addEq(2000, b.seek2k)
        addEq(4000, b.seek4k); addEq(8000, b.seek8k); addEq(12000, b.seek12k)

        // 6. Mastering & Efek
        if (b.cbMastering.isChecked) audioFilters.add("loudnorm=I=-16:TP=-1.5:LRA=11")
        if (b.cbStereoWiden.isChecked) audioFilters.add("stereowiden=delay=20:feedback=0.3:crossfeed=0.3:drymix=0.8")
        if (b.cbEcho.isChecked) audioFilters.add("aecho=0.8:0.9:1000:0.3")
        if (b.cbLimiter.isChecked) audioFilters.add("alimiter=limit=0.95")

        if (audioFilters.isNotEmpty()) cmd.append("-af \"${audioFilters.joinToString(",")}\" ")

        // 7. Kualitas (Bitrate & Sample Rate)
        if (b.cbCompress.isChecked) {
            cmd.append("-b:a 64k ")
        } else {
            cmd.append("-b:a ${b.spinnerBitrate.selectedItem} ")
        }
        cmd.append("-ar ${b.spinnerSampleRate.selectedItem} ")

        // 8. Hapus Metadata & Tambah Metadata
        if (b.cbRemoveMetadata.isChecked) cmd.append("-map_metadata -1 ")

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
                    if (isRingtone) {
                        val ringtoneDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
                        val ringtoneFile = File(ringtoneDir, outputFile.name)
                        outputFile.copyTo(ringtoneFile, overwrite = true)
                        outputFile.delete() // Hapus file asli di folder edit
                        
                        val values = ContentValues().apply {
                            put(MediaStore.Audio.Media.DISPLAY_NAME, ringtoneFile.name)
                            put(MediaStore.Audio.Media.MIME_TYPE, "audio/*")
                            put(MediaStore.Audio.Media.DATA, ringtoneFile.absolutePath)
                        }
                        contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                        b.tvProgress.text = "Berhasil disimpan sebagai Nada Dering!"
                    } else {
                        // Daftarkan file audio ke Galeri
                        val values = ContentValues().apply {
                            put(MediaStore.Audio.Media.DISPLAY_NAME, outputFile.name)
                            put(MediaStore.Audio.Media.MIME_TYPE, "audio/*")
                            put(MediaStore.Audio.Media.DATA, outputFile.absolutePath)
                        }
                        contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
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