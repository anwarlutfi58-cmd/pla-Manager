package com.pla.manajer.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

object PlaMediaPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var appContext: Context? = null

    private var _currentFile: File? = null
    val currentFile: File? get() = _currentFile

    private var _isPlaying: Boolean = false
    val isPlaying: Boolean get() = _isPlaying

    val audioSessionId: Int get() = mediaPlayer?.audioSessionId ?: 0

    var onSongChanged: (() -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        PlaAudioFx.init(context)
    }

    fun play(file: File, onCompletion: () -> Unit = {}) {
        stop()
        _currentFile = file
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(file.absolutePath)
            prepare()
            start()
            setOnCompletionListener {
                _isPlaying = false
                onCompletion()
            }
        }
        _isPlaying = true
        // Pasang equalizer global ke sesi audio lagu ini
        PlaAudioFx.attach(audioSessionId)
        onSongChanged?.invoke()
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