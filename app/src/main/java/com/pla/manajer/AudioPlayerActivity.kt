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
    
    private var isShuffle = false
    private var repeatMode = 0 // 0 = Off, 1 = Ulangi Semua, 2 = Ulangi Satu Lagu
    private var equalizer: Equalizer? = null
    private val savedBandLevels = mutableMapOf<Short, Short>() 

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)

        filePaths = intent.getStringArrayListExtra("file_paths") ?: emptyList()
        currentIndex = intent.getIntExtra("index", 0)

        b.btnBack.setOnClickListener { finish() }
        
        // LOGIKA TOMBOL PLAY/PAUSE (DIPERBARUI UNTUK TALKBACK)
        b.btnPlayPause.setOnClickListener {
            if (PlaMediaPlayer.isPlaying) {
                PlaMediaPlayer.pause()
                updateTombolPutar(false) // Set ke Ikon Putar
            } else {
                PlaMediaPlayer.resume()
                updateTombolPutar(true) // Set ke Ikon Jeda
            }
        }

        b.btnNext.setOnClickListener { playNextSong(manualClick = true) }
        
        b.btnPrev.setOnClickListener {
            if (isShuffle) {
                currentIndex = filePaths.indices.random()
            } else {
                currentIndex = if (currentIndex > 0) currentIndex - 1 else filePaths.size - 1
            }
            playSong(currentIndex)
        }
        
        b.btnShuffle.setOnClickListener {
            isShuffle = !isShuffle
            b.btnShuffle.setColorFilter(if (isShuffle) android.graphics.Color.parseColor("#00BFA5") else android.graphics.Color.parseColor("#555555"))
            Toast.makeText(this, if (isShuffle) "Acak Aktif" else "Acak Mati", Toast.LENGTH_SHORT).show()
        }
        
        b.btnRepeat.setOnClickListener {
            repeatMode = (repeatMode + 1) % 3
            updateRepeatIcon()
            val modeText = when(repeatMode) {
                0 -> "Ulangi: Mati"
                1 -> "Ulangi: Semua Lagu"
                else -> "Ulangi: Lagu Ini"
            }
            Toast.makeText(this, modeText, Toast.LENGTH_SHORT).show()
        }

        b.btnShare.setOnClickListener { shareCurrentSong() }
        b.btnEqualizer.setOnClickListener { showEqualizerDialog() }

        playSong(currentIndex)

        b.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) PlaMediaPlayer.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // FUNGSI BARU: Mengatur Ikon dan Suara TalkBack
    private fun updateTombolPutar(isPlaying: Boolean) {
        if (isPlaying) {
            b.btnPlayPause.setImageResource(R.drawable.ic_pause)
            // TalkBack akan membacakan "Jeda" dalam bahasa Indonesia
            b.btnPlayPause.contentDescription = "Jeda" 
        } else {
            b.btnPlayPause.setImageResource(R.drawable.ic_play)
            // TalkBack akan membacakan "Putar" dalam bahasa Indonesia
            b.btnPlayPause.contentDescription = "Putar" 
        }
    }

    private fun updateRepeatIcon() {
        when (repeatMode) {
            0 -> {
                b.btnRepeat.setImageResource(R.drawable.ic_repeat)
                b.btnRepeat.setColorFilter(android.graphics.Color.parseColor("#555555"))
                b.btnRepeat.contentDescription = "Ulangi Mati"
            }
            1 -> {
                b.btnRepeat.setImageResource(R.drawable.ic_repeat)
                b.btnRepeat.setColorFilter(android.graphics.Color.parseColor("#00BFA5"))
                b.btnRepeat.contentDescription = "Ulangi Semua Lagu"
            }
            2 -> {
                b.btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                b.btnRepeat.setColorFilter(android.graphics.Color.parseColor("#00BFA5"))
                b.btnRepeat.contentDescription = "Ulangi Lagu Ini"
            }
        }
    }

    private fun playSong(index: Int) {
        if (index !in filePaths.indices) return
        val file = File(filePaths[index])
        
        b.tvTitle.text = file.name
        b.tvCounter.text = "${index + 1} dari ${filePaths.size}"
        
        PlaMediaPlayer.play(file) {
            // Saat lagu habis otomatis, kembalikan tombol ke Putar
            updateTombolPutar(false)
            
            when (repeatMode) {
                2 -> playSong(currentIndex) 
                1 -> playNextSong(manualClick = false) 
                0 -> {
                    if (currentIndex < filePaths.size - 1) {
                        currentIndex++
                        playSong(currentIndex)
                    }
                }
            }
        }
        
        updateTombolPutar(true) // Set ke Ikon Jeda saat lagu mulai
        updateSeekBar()
        
        val sessionId = PlaMediaPlayer.audioSessionId
        equalizer?.release()
        equalizer = Equalizer(0, sessionId).apply { 
            enabled = true 
            savedBandLevels.forEach { (band, level) ->
                setBandLevel(band, level)
            }
        }
    }

    private fun playNextSong(manualClick: Boolean) {
        if (isShuffle) {
            currentIndex = filePaths.indices.random()
        } else {
            if (currentIndex < filePaths.size - 1) {
                currentIndex++
            } else {
                currentIndex = 0 
            }
        }
        playSong(currentIndex)
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
            Toast.makeText(this, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEqualizerDialog() {
        if (equalizer == null) {
            Toast.makeText(this, "Tidak ada audio yang diputar", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Efek Suara (Equalizer)")

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val numBands = equalizer!!.numberOfBands
        val range = equalizer!!.bandLevelRange

        for (i in 0 until minOf(3, numBands)) {
            val bandIndex = i.toShort()
            val freq = equalizer!!.getCenterFreq(bandIndex) / 1000
            val label = android.widget.TextView(this).apply {
                text = "${freq} Hz"
                setTextColor(android.graphics.Color.WHITE)
            }
            
            val seekBar = SeekBar(this).apply {
                max = (range[1] - range[0]).toInt()
                val currentLevel = savedBandLevels[bandIndex] ?: equalizer!!.getBandLevel(bandIndex)
                progress = (currentLevel - range[0]).toInt()
                
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val newLevel = (progress + range[0]).toShort()
                            equalizer!!.setBandLevel(bandIndex, newLevel)
                            savedBandLevels[bandIndex] = newLevel 
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