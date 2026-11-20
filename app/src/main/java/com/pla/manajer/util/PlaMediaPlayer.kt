package com.pla.manajer.util

import android.media.MediaPlayer
import java.io.File

object PlaMediaPlayer {
    private var mediaPlayer: MediaPlayer? = null
    
    private var _currentFile: File? = null
    val currentFile: File? get() = _currentFile
    
    private var _isPlaying: Boolean = false
    val isPlaying: Boolean get() = _isPlaying

    val audioSessionId: Int get() = mediaPlayer?.audioSessionId ?: 0

    fun play(file: File, onCompletion: () -> Unit = {}) {
        stop()
        _currentFile = file
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
            setOnCompletionListener {
                _isPlaying = false
                onCompletion()
            }
        }
        _isPlaying = true
    }

    fun pause() {
        if (_isPlaying) {
            mediaPlayer?.pause()
            _isPlaying = false
        }
    }

    fun resume() {
        if (!_isPlaying && mediaPlayer != null) {
            mediaPlayer?.start()
            _isPlaying = true
        } else if (mediaPlayer == null && _currentFile != null) {
            play(_currentFile!!)
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying = false
    }

    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun seekTo(ms: Int) { mediaPlayer?.seekTo(ms) }
}