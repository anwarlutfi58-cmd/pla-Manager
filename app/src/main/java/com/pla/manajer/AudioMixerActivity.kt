package com.pla.manajer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.pla.manajer.databinding.ActivityAudioMixerBinding
import java.io.File
import java.io.FileOutputStream

class AudioMixerActivity : AppCompatActivity() {
    private lateinit var b: ActivityAudioMixerBinding
    private val tracks = mutableListOf<MixerTrack>()
    private lateinit var adapter: MixerAdapter

    private val pickAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                var fileName = "audio_file"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst()) fileName = cursor.getString(nameIndex)
                }

                val tempFile = File(cacheDir, "mix_${System.currentTimeMillis()}_$fileName")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                tracks.add(MixerTrack(tempFile))
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal memuat audio: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAudioMixerBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBackMixer.setOnClickListener { finish() }

        adapter = MixerAdapter(tracks, 
            onDelete = { position ->
                tracks.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, tracks.size)
            },
            onDataChanged = {
                // Bisa diabaikan jika tidak butuh validasi instan
            }
        )

        b.recyclerMixer.layoutManager = LinearLayoutManager(this)
        b.recyclerMixer.adapter = adapter

        b.btnAddTrack.setOnClickListener {
            pickAudioLauncher.launch("audio/*")
        }

        b.btnProcessMixer.setOnClickListener {
            if (tracks.isEmpty()) {
                Toast.makeText(this, "Tambahkan audio terlebih dahulu!", Toast.LENGTH_SHORT).show()
            } else {
                processMixer()
            }
        }
    }

    private fun processMixer() {
        b.btnProcessMixer.isEnabled = false
        b.tvMixerProgress.text = "Sedang menggabungkan audio... Mohon tunggu."
        Toast.makeText(this, "Proses Mixer berjalan...", Toast.LENGTH_LONG).show()

        val outputFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "MIX_${System.currentTimeMillis()}.mp3")

        val cmd = StringBuilder()
        cmd.append("-y ")

        // 1. Masukkan semua input
        for (track in tracks) {
            cmd.append("-i \"${track.file.absolutePath}\" ")
        }

        // 2. Buat Filter Complex
        val filterBuilder = StringBuilder()
        val mixInputs = StringBuilder()

        for ((index, track) in tracks.withIndex()) {
            // Hitung Waktu Delay (dalam milidetik)
            val parts = track.startTime.split(":")
            val h = parts[0].toIntOrNull() ?: 0
            val m = parts[1].toIntOrNull() ?: 0
            val sec = parts[2].toIntOrNull() ?: 0
            val totalMs = (h * 3600000) + (m * 60000) + (sec * 1000)

            // Hitung Volume
            val volMultiplier = track.volume / 100.0

            // Hitung Panning (Kiri/Tengah/Kanan)
            // Panning 0 -> Full Kiri, 100 -> Tengah, 200 -> Full Kanan
            val panValue = track.panning / 100.0
            val leftGain = if (panValue <= 1.0) 1.0 else (2.0 - panValue)
            val rightGain = if (panValue >= 1.0) 1.0 else panValue

            // Filter untuk track ini: delay, volume, panning
            filterBuilder.append("[$index:a]adelay=$totalMs|$totalMs,volume=$volMultiplier,pan=stereo|c0=c0*$leftGain|c1=c1*$rightGain[a$index]; ")
            
            // Kumpulkan input untuk amix
            mixInputs.append("[a$index]")
        }

        // Gabungkan semua track
        filterBuilder.append("${mixInputs}amix=inputs=${tracks.size}:duration=longest[out]")

        cmd.append("-filter_complex \"$filterBuilder\" ")
        cmd.append("-map \"[out]\" ")
        cmd.append("\"${outputFile.absolutePath}\"")

        // 3. Jalankan FFmpeg
        FFmpegKit.executeAsync(cmd.toString(), { session ->
            runOnUiThread {
                b.btnProcessMixer.isEnabled = true
                if (ReturnCode.isSuccess(session.returnCode)) {
                    b.tvMixerProgress.text = "Berhasil disimpan di:\n${outputFile.name}"
                    Toast.makeText(this, "Mixer Berhasil!", Toast.LENGTH_LONG).show()
                } else {
                    b.tvMixerProgress.text = "Gagal memproses audio."
                    Toast.makeText(this, "Gagal: ${session.failStackTrace}", Toast.LENGTH_LONG).show()
                }
            }
        }, { }, { })
    }
}