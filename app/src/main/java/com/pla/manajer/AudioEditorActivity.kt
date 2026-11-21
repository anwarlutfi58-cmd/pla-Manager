package com.pla.manajer

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
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

        // --- LOGIKA PINDAH TAB (BARU) ---
        b.tabLayoutAudio.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // Sembunyikan semua tampilan tab dulu
                b.layoutTabTrim.visibility = View.GONE
                b.layoutTabFormat.visibility = View.GONE
                b.layoutTabMetadata.visibility = View.GONE

                // Tampilkan sesuai tab yang dipilih
                when (tab?.position) {
                    0 -> b.layoutTabTrim.visibility = View.VISIBLE   // Tab Potong
                    1 -> b.layoutTabFormat.visibility = View.VISIBLE // Tab Format
                    2 -> b.layoutTabMetadata.visibility = View.VISIBLE // Tab Metadata
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        b.btnProcess.setOnClickListener { processAudio() }
    }

    private fun processAudio() {
        val outputFormat = formats[b.spinnerFormat.selectedItemPosition]
        val outputFile = File(inputFile.parentFile, "EDIT_${System.currentTimeMillis()}.${outputFormat}")

        // 1. Ambil Input Waktu (Potong)
        val start = b.etStartTime.text.toString().ifEmpty { "00:00" }
        val end = b.etEndTime.text.toString()

        // 2. Siapkan Perintah FFmpeg
        val cmd = StringBuilder()

        // Perintah dasar (-y untuk menimpa file otomatis)
        cmd.append("-y ")

        // Perintah Potong (Trim)
        if (end.isNotEmpty()) {
            cmd.append("-ss $start -to $end ")
        }

        // Input File
        cmd.append("-i \"${inputFile.absolutePath}\" ")

        // Perintah Hapus Watermark (Suno biasanya di frekuensi > 16kHz)
        if (b.cbRemoveWatermark.isChecked) {
            // Lowpass filter memotong frekuensi tinggi (menghilangkan sidik jari digital)
            cmd.append("-af \"lowpass=f=16000\" ")
        }

        // Perintah Metadata
        val title = b.etTitle.text.toString()
        val artist = b.etArtist.text.toString()
        if (title.isNotEmpty()) cmd.append("-metadata title=\"$title\" ")
        if (artist.isNotEmpty()) cmd.append("-metadata artist=\"$artist\" ")

        // Output File
        cmd.append("\"${outputFile.absolutePath}\"")

        // Tampilkan Loading
        b.btnProcess.isEnabled = false
        b.tvProgress.text = "Sedang memproses... Mohon tunggu."

        // 3. Jalankan Mesin FFmpeg
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
            // Log berjalan (bisa diabaikan)
        }, { statistics ->
            // Progress (bisa diabaikan)
        })
    }
}