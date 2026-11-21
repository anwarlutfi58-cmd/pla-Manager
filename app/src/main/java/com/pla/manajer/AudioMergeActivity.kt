package com.pla.manajer

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.pla.manajer.databinding.ActivityAudioMergeBinding
import java.io.File
import java.io.FileOutputStream

class AudioMergeActivity : AppCompatActivity() {
    private lateinit var b: ActivityAudioMergeBinding
    private var audioUri1: Uri? = null
    private var audioUri2: Uri? = null

    private val pickAudio1 = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { audioUri1 = uri; updateStatus() }
    }
    private val pickAudio2 = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { audioUri2 = uri; updateStatus() }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAudioMergeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnPickAudio1.setOnClickListener { pickAudio1.launch("audio/*") }
        b.btnPickAudio2.setOnClickListener { pickAudio2.launch("audio/*") }

        b.btnMergeAudio.setOnClickListener {
            if (audioUri1 == null || audioUri2 == null) {
                Toast.makeText(this, "Pilih kedua audio terlebih dahulu!", Toast.LENGTH_SHORT).show()
            } else {
                mergeAudio()
            }
        }
    }

    private fun updateStatus() {
        val s1 = if (audioUri1 != null) "Audio 1: Siap" else "Audio 1: Belum dipilih"
        val s2 = if (audioUri2 != null) "Audio 2: Siap" else "Audio 2: Belum dipilih"
        b.tvStatusMerge.text = "$s1\n$s2"
    }

    private fun mergeAudio() {
        b.btnMergeAudio.isEnabled = false
        b.tvStatusMerge.text = "Sedang menggabungkan audio..."

        // Simpan URI ke File sementara
        val tempFile1 = File(cacheDir, "temp_merge_1")
        val tempFile2 = File(cacheDir, "temp_merge_2")
        
        try {
            contentResolver.openInputStream(audioUri1!!)?.use { input ->
                FileOutputStream(tempFile1).use { output -> input.copyTo(output) }
            }
            contentResolver.openInputStream(audioUri2!!)?.use { input ->
                FileOutputStream(tempFile2).use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca file", Toast.LENGTH_SHORT).show()
            b.btnMergeAudio.isEnabled = true
            return
        }

        val outputFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "MERGE_${System.currentTimeMillis()}.mp3")
        
        // Perintah FFmpeg untuk menggabungkan audio
        val cmd = "-y -i \"${tempFile1.absolutePath}\" -i \"${tempFile2.absolutePath}\" -filter_complex \"[0:a][1:a]concat=n=2:v=0:a=1[out]\" -map \"[out]\" \"${outputFile.absolutePath}\""

        FFmpegKit.executeAsync(cmd, { session ->
            runOnUiThread {
                b.btnMergeAudio.isEnabled = true
                if (ReturnCode.isSuccess(session.returnCode)) {
                    b.tvStatusMerge.text = "Berhasil disimpan di:\n${outputFile.name}"
                    Toast.makeText(this, "Audio berhasil digabung!", Toast.LENGTH_LONG).show()
                    tempFile1.delete()
                    tempFile2.delete()
                } else {
                    b.tvStatusMerge.text = "Gagal menggabungkan audio."
                    Toast.makeText(this, "Gagal: ${session.failStackTrace}", Toast.LENGTH_LONG).show()
                }
            }
        }, { }, { })
    }
}