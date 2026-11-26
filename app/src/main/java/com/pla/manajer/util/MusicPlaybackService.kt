package com.pla.manajer.util

import android.app.Service
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import com.pla.manajer.AudioPlayerActivity
import java.io.File

/**
 * Service musik latar belakang. Menjalankan player + MediaSession + notifikasi MediaStyle.
 * Cara kerja seperti YouTube: notifikasi muncul saat main, hilang otomatis saat dijeda.
 */
class MusicPlaybackService : Service() {

    private lateinit var notif: NotificationHelper
    private var mediaSession: MediaSession? = null
    private var playlist: List<String> = emptyList()
    private var currentIndex = 0
    private var isShuffle = false
    private var repeatMode = 0

    companion object {
        const val EXTRA_PATHS = "extra_paths"
        const val EXTRA_START_INDEX = "extra_start_index"
        const val EXTRA_SHUFFLE = "extra_shuffle"
        const val EXTRA_REPEAT = "extra_repeat"

        const val CMD_PLAY_LIST = "cmd_play_list"
        const val CMD_RESUME = "cmd_resume"
    }

    override fun onCreate() {
        super.onCreate()
        notif = NotificationHelper(this)
        PlaMediaPlayer.init(this)
        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            CMD_PLAY_LIST -> {
                playlist = intent.getStringArrayListExtra(EXTRA_PATHS) ?: emptyList()
                currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
                isShuffle = intent.getBooleanExtra(EXTRA_SHUFFLE, false)
                repeatMode = intent.getIntExtra(EXTRA_REPEAT, 0)
                playCurrent()
            }
            CMD_RESUME -> if (!PlaMediaPlayer.isPlaying) {
                PlaMediaPlayer.resume()
                refreshNotif()
                promoteForeground()
                notifyStateChanged(true)
            }
            NotificationHelper.ACTION_PLAY_PAUSE -> togglePlayPause()
            NotificationHelper.ACTION_NEXT -> next()
            NotificationHelper.ACTION_PREV -> prev()
            NotificationHelper.ACTION_CLOSE -> stopAndRelease()
        }
        return START_NOT_STICKY
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "PlaMusicService").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!PlaMediaPlayer.isPlaying) {
                        PlaMediaPlayer.resume()
                        refreshNotif()
                        promoteForeground()
                        notifyStateChanged(true)
                    }
                }
                override fun onPause() { pauseWithAutoHide() }
                override fun onSkipToNext() { next() }
                override fun onSkipToPrevious() { prev() }
                override fun onStop() { stopAndRelease() }
            })
            isActive = true
        }
    }

    private fun playCurrent() {
        if (playlist.isEmpty() || currentIndex !in playlist.indices) { stopAndRelease(); return }
        val file = File(playlist[currentIndex])
        if (!file.exists()) { next(); return }

        PlaMediaPlayer.play(file) {
            runOnMain {
                updateSessionState(false)
                notifyStateChanged(false)
                when (repeatMode) {
                    2 -> playCurrent()
                    1 -> next()
                    0 -> if (currentIndex < playlist.size - 1) next() else pauseWithAutoHide()
                }
            }
        }
        updateSessionState(true)
        notif.showPlayer(file.name, true, mediaSession)
        notifyStateChanged(true)
        try { startForeground(NotificationHelper.ID_PLAYER, buildForegroundNotif(file.name)) } catch (_: Exception) {}
    }

    private fun notifyStateChanged(isPlaying: Boolean) {
        sendBroadcast(Intent(AudioPlayerActivity.ACTION_STATE_CHANGED).apply {
            setPackage(packageName)
            putExtra(AudioPlayerActivity.EXTRA_PLAYING, isPlaying)
            putExtra(AudioPlayerActivity.EXTRA_INDEX, currentIndex)
        })
    }

    private fun buildForegroundNotif(title: String): android.app.Notification {
        notif.showPlayer(title, true, mediaSession)
        return android.app.Notification.Builder(this, NotificationHelper.CHANNEL_PLAYER)
            .setSmallIcon(com.pla.manajer.R.drawable.ic_music_note)
            .setContentTitle(title).setContentText("Sedang diputar")
            .setOngoing(true).build()
    }

    private fun togglePlayPause() {
        if (PlaMediaPlayer.isPlaying) pauseWithAutoHide()
        else {
            PlaMediaPlayer.resume()
            updateSessionState(true)
            refreshNotif()
            promoteForeground()
            notifyStateChanged(true)
        }
    }

    private fun pauseWithAutoHide() {
        PlaMediaPlayer.pause()
        updateSessionState(false)
        refreshNotif()
        notifyStateChanged(false)
        try { stopForeground(STOP_FOREGROUND_DETACH) } catch (_: Exception) {}
        handler.removeCallbacks(autoHideRunnable)
        handler.postDelayed(autoHideRunnable, 8_000L)
    }

    private val autoHideRunnable = Runnable {
        if (!PlaMediaPlayer.isPlaying) {
            notif.hidePlayer()
            sendBroadcast(Intent(AudioPlayerActivity.ACTION_FINISHED).apply { setPackage(packageName) })
            stopSelf()
        }
    }

    private fun refreshNotif() {
        notif.showPlayer(PlaMediaPlayer.currentFile?.name ?: "Musik", PlaMediaPlayer.isPlaying, mediaSession)
    }

    private fun promoteForeground() {
        try {
            notif.showPlayer(PlaMediaPlayer.currentFile?.name ?: "Musik", true, mediaSession)
            startForeground(NotificationHelper.ID_PLAYER,
                android.app.Notification.Builder(this, NotificationHelper.CHANNEL_PLAYER)
                    .setSmallIcon(com.pla.manajer.R.drawable.ic_music_note)
                    .setContentTitle(PlaMediaPlayer.currentFile?.name ?: "Musik")
                    .setContentText("Sedang diputar").setOngoing(true).build())
        } catch (_: Exception) {}
    }

    private fun next() {
        if (playlist.isEmpty()) return
        currentIndex = if (isShuffle) playlist.indices.random()
        else if (currentIndex < playlist.size - 1) currentIndex + 1 else 0
        playCurrent()
    }

    private fun prev() {
        if (playlist.isEmpty()) return
        currentIndex = if (isShuffle) playlist.indices.random()
        else if (currentIndex > 0) currentIndex - 1 else playlist.size - 1
        playCurrent()
    }

    private fun updateSessionState(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP
                )
                .setState(state, PlaMediaPlayer.getCurrentPosition().toLong(), 1.0f)
                .build()
        )
    }

    private fun stopAndRelease() {
        handler.removeCallbacks(autoHideRunnable)
        PlaMediaPlayer.stop()
        notif.hidePlayer()
        mediaSession?.isActive = false
        notifyStateChanged(false)
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }

    private fun runOnMain(block: () -> Unit) = handler.post(block)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        mediaSession?.release()
        mediaSession = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}