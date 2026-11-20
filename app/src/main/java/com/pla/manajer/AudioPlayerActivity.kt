package com.pla.manajer

import android.app.AlertDialog
import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.pla.manajer.databinding.ActivityAudioPlayerBinding
import com.pla.manajer.util.PlaMediaPlayer
import java.io.File

class AudioPlayerActivity : AppCompatActivity() {
    private lateinit var b: ActivityAudioPlayerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var currentIndex = 0
    private var filePaths: List<String> = emptyList()
    
    // Status Fitur
    private var isShuffle = false
    private var repeatMode = 0 // 0 = Off, 1 = Ulangi Semua, 2 = Ulangi Satu Lagu
    private var equalizer: Equalizer? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)

        filePaths = intent.getStringArrayListExtra("file_paths") ?: emptyList()
        currentIndex = intent.getIntExtra("index", 0)

        b.btnBack.setOnClickListener { finish() }
        
        b.btnPlayPause.setOnClickListener {
            if (PlaMediaPlayer.isPlaying) {
                PlaMediaPlayer.pause()
                b.btnPlayPause.setImageResource(R.drawable.ic_play)
            } else {
                PlaMediaPlayer.resume()
                b.btnPlayPause.setImageResource(R.drawable.ic_pause)
            }
        }

        b.btnNext.setOnClickListener {
            playNextSong()
        }

        b.btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                playSong(currentIndex)
            }
        }
        
        // FITUR ACAK
        b.btnShuffle.setOnClickListener {
            isShuffle = !isShuffle
            b.btnShuffle.setColorFilter(if (isShuffle) android.graphics.Color.parseColor("#00BFA5") else android.graphics.Color.parseColor("#555555"))
            Toast.makeText(this, if (isShuffle) "Acak Aktif" else "Acak Mati", Toast.LENGTH_SHORT).show()
        }
        
        // FITUR ULANGI
        b.btnRepeat.setOnClickListener {
            repeatMode = (repeatMode + 1) % 3
            updateRepeatIcon()
        }

        // FITUR BAGIKAN
        b.btnShare.setOnClickListener {
            shareCurrentSong()
        }

        // FITUR EQUALIZER
        b.btnEqualizer.setOnClickListener {
            showEqualizerDialog()
        }

        playSong(currentIndex)

        b.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) PlaMediaPlayer.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateRepeatIcon() {
        when (repeatMode) {
            0 -> {
                b.btnRepeat.setImageResource(R.drawable.ic_repeat)
                b.btnRepeat.setColorFilter(android.graphics.Color.parseColor("#555555"))
            }
            1 -> {
                b.btnRepeat.setImageResource(R.drawable.ic_repeat)
                b.btnRepeat.setColorFilter(android.graphics.Color.parseColor("#00BFA5"))
            }
            2 -> {
                b.btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                b.btnRepeat.setColorFilter(android.graphics.Color.parseColor("#00BFA5"))
            }
        }
    }

    private fun playSong(index: Int) {
        if (index !in filePaths.indices) return
        val file = File(filePaths[index])
        
        b.tvTitle.text = file.name
        b.tvCounter.text = "${index + 1} dari ${filePaths.size}"
        
        PlaMediaPlayer.play(file) {
            // Logika saat lagu selesai
            when (repeatMode) {
                2 -> playSong(currentIndex) // Ulangi lagu ini
                1 -> playNextSong() // Ulangi semua (lanjut next)
                0 -> if (currentIndex < filePaths.size - 1) playNextSong() else b.btnPlayPause.setImageResource(R.drawable.ic_play)
            }
        }
        
        b.btnPlayPause.setImageResource(R.drawable.ic_pause)
        updateSeekBar()
        
        // Reset Equalizer untuk lagu baru
        equalizer?.release()
        equalizer = Equalizer(0, PlaMediaPlayer.audioSessionId).apply { enabled = true }
    }

    private fun playNextSong() {
        if (isShuffle) {
            currentIndex = filePaths.indices.random()
            playSong(currentIndex)
        } else {
            if (currentIndex < filePaths.size - 1) {
                currentIndex++
                playSong(currentIndex)
            } else {
                // Jika sudah di ujung dan mode ulangi semua
                if (repeatMode == 1) {
                    currentIndex = 0
                    playSong(currentIndex)
                }
            }
        }
    }

    private fun shareCurrentSong() {
        val file = PlaMediaPlayer.currentFile ?: return
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Bagikan Lagu via"))
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membagikan lagu", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEqualizerDialog() {
        if (equalizer == null) {
            Toast.makeText(this, "Tidak ada audio yang diputar", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Efek Suara (Equalizer)")

        // Menggunakan layout sederhana via program
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        // Equalizer Android punya band frekuensi (biasanya 5). Kita ambil 3 utama: Bass, Mid, Treble
        val numBands = equalizer!!.numberOfBands
        val range = equalizer!!.bandLevelRange

        for (i in 0 until minOf(3, numBands)) {
            val freq = equalizer!!.getCenterFreq(i.toShort()) / 1000
            val label = android.widget.TextView(this).apply {
                text = "${freq} Hz"
                setTextColor(android.graphics.Color.WHITE)
            }
            val seekBar = SeekBar(this).apply {
                max = (range[1] - range[0]).toInt()
                progress = (equalizer!!.getBandLevel(i.toShort()) - range[0]).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            equalizer!!.setBandLevel(i.toShort(), (progress + range[0]).toShort())
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            layout.addView(label)
            layout.addView(seekBar)
        }

        builder.setView(layout)
        builder.setPositiveButton("Tutup", null)
        builder.show()
    }

    private fun updateSeekBar() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (PlaMediaPlayer.isPlaying) {
                    val current = PlaMediaPlayer.getCurrentPosition()
                    val total = PlaMediaPlayer.getDuration()
                    b.seekBar.max = total
                    b.seekBar.progress = current
                    b.tvCurrent.text = formatTime(current)
                    b.tvTotal.text = formatTime(total)
                }
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun formatTime(ms: Int): String {
        val sec = ms / 1000
        return String.format("%02d:%02d", sec / 60, sec % 60)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        equalizer?.release()
    }
}