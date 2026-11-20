package com.pla.manajer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pla.manajer.databinding.ActivityAudioPlayerBinding
import com.pla.manajer.util.PlaMediaPlayer
import java.io.File

class AudioPlayerActivity : AppCompatActivity() {
    private lateinit var b: ActivityAudioPlayerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var currentIndex = 0
    private var filePaths: List<String> = emptyList()

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
                b.btnPlayPause.setImageResource(R.drawable.ic_pause) // Asumsi ada ic_pause
            }
        }

        playSong(currentIndex)

        b.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) PlaMediaPlayer.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun playSong(index: Int) {
        if (index !in filePaths.indices) return
        val file = File(filePaths[index])
        
        b.tvTitle.text = file.name
        b.tvCounter.text = "${index + 1} dari ${filePaths.size}"
        
        PlaMediaPlayer.play(file) {
            // Auto next saat lagu habis
            if (index < filePaths.size - 1) {
                currentIndex++
                playSong(currentIndex)
            } else {
                b.btnPlayPause.setImageResource(R.drawable.ic_play)
            }
        }
        
        b.btnPlayPause.setImageResource(R.drawable.ic_pause) // Asumsi ada ic_pause
        updateSeekBar()
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
    }
}