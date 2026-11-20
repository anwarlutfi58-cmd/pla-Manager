package com.pla.manajer.util

import android.media.MediaPlayer
import java.io.File

object PlaMediaPlayer {
    private var mediaPlayer: MediaPlayer? = null
    var currentFile: File? = null
        private set
    var isPlaying: Boolean = false
        private set

    fun play(file: File, onCompletion: () -> Unit = {}) {
        stop()
        currentFile = file
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
            setOnCompletionListener {
                isPlaying = false
                onCompletion()
            }
        }
        isPlaying = true
    }

    fun pause() {
        if (isPlaying) {
            mediaPlayer?.pause()
            isPlaying = false
        }
    }

    fun resume() {
        if (!isPlaying && mediaPlayer != null) {
            mediaPlayer?.start()
            isPlaying = true
        } else if (mediaPlayer == null && currentFile != null) {
            play(currentFile!!)
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        // currentFile sengaja tidak dihapus agar mini player tetap tahu file terakhir
    }

    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun seekTo(ms: Int) { mediaPlayer?.seekTo(ms) }
}