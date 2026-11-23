package com.pla.manajer

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.audiofx.Equalizer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.pla.manajer.databinding.ActivityAudioPlayerBinding
import com.pla.manajer.util.NotificationHelper
import com.pla.manajer.util.PlaMediaPlayer
import java.io.File

class AudioPlayerActivity : AppCompatActivity() {
    private lateinit var b: ActivityAudioPlayerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var currentIndex = 0
    private var filePaths: List<String> = emptyList()

    private var isShuffle = false
    private var repeatMode = 0
    private var equalizer: Equalizer? = null
    private val savedBandLevels = mutableMapOf<Short, Short>()

    private lateinit var notif: NotificationHelper
    private var mediaSession: MediaSession? = null

    private val autoHideRunnable = Runnable {
        if (!PlaMediaPlayer.isPlaying) notif.hidePlayer()
    }

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                NotificationHelper.ACTION_PREV -> playPrevSong()
                NotificationHelper.ACTION_PLAY_PAUSE -> togglePlayPause()
                NotificationHelper.ACTION_NEXT -> playNextSong()
                NotificationHelper.ACTION_CLOSE -> {
                    notif.hidePlayer()
                    PlaMediaPlayer.pause()
                    updateTombolPutar(false)
                    finish()
                }
            }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)

        notif = NotificationHelper(this)

        filePaths = intent.getStringArrayListExtra("file_paths") ?: emptyList()

        // ROTASI: lagu yang ditekan jadi nomor 1, sisanya menyusul
        val startPath = intent.getStringExtra("start_path")
        if (startPath != null && filePaths.isNotEmpty()) {
            val i = filePaths.indexOf(startPath)
            if (i > 0) filePaths = filePaths.drop(i) + filePaths.take(i)
        }
        currentIndex = 0

        requestNotifPermissionIfNeeded()
        registerActionReceiver()
        setupMediaSession()

        b.btnBack.setOnClickListener { finish() }
        b.btnPlayPause.setOnClickListener { togglePlayPause() }
        b.btnNext.setOnClickListener { playNextSong() }
        b.btnPrev.setOnClickListener { playPrevSong() }

        b.btnShuffle.setOnClickListener {
            isShuffle = !isShuffle
            b.btnShuffle.setColorFilter(
                if (isShuffle) android.graphics.Color.parseColor("#00BFA5")
                else android.graphics.Color.parseColor("#555555")
            )
            Toast.makeText(this, if (isShuffle) "Acak Aktif" else "Acak Mati", Toast.LENGTH_SHORT).show()
        }

        b.btnRepeat.setOnClickListener {
            repeatMode = (repeatMode + 1) % 3
            updateRepeatIcon()
            val txt = when (repeatMode) {
                0 -> "Ulangi: Mati"; 1 -> "Ulangi: Semua Lagu"; else -> "Ulangi: Lagu Ini"
            }
            Toast.makeText(this, txt, Toast.LENGTH_SHORT).show()
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

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)

        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101)
    }

    private fun registerActionReceiver() {
        val f = IntentFilter().apply {
            addAction(NotificationHelper.ACTION_PREV)
            addAction(NotificationHelper.ACTION_PLAY_PAUSE)
            addAction(NotificationHelper.ACTION_NEXT)
            addAction(NotificationHelper.ACTION_CLOSE)
        }
        ContextCompat.registerReceiver(this, actionReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "PlaMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { if (!PlaMediaPlayer.isPlaying) togglePlayPause() }
                override fun onPause() { if (PlaMediaPlayer.isPlaying) togglePlayPause() }
                override fun onSkipToNext() { playNextSong() }
                override fun onSkipToPrevious() { playPrevSong() }
                override fun onStop() { notif.hidePlayer(); PlaMediaPlayer.pause(); updateTombolPutar(false) }
            })
            isActive = true
        }
    }

    private fun updateSessionState(isPlaying: Boolean) {
        val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val pb = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP
            )
            .setState(state, PlaMediaPlayer.getCurrentPosition().toLong(), 1.0f)
            .build()
        mediaSession?.setPlaybackState(pb)
    }

    private fun togglePlayPause() {
        if (PlaMediaPlayer.isPlaying) {
            PlaMediaPlayer.pause(); updateTombolPutar(false); updateSessionState(false)
            notif.showPlayer(currentTitle(), false); scheduleAutoHide()
        } else {
            PlaMediaPlayer.resume(); updateTombolPutar(true); updateSessionState(true)
            notif.showPlayer(currentTitle(), true); cancelAutoHide()
        }
    }

    private fun scheduleAutoHide() = notif.scheduleHidePlayer(5 * 60 * 1000L, autoHideRunnable)
    private fun cancelAutoHide() = notif.cancelScheduledHide(autoHideRunnable)
    private fun currentTitle(): String = PlaMediaPlayer.currentFile?.name ?: "Musik"

    private fun updateTombolPutar(isPlaying: Boolean) {
        if (isPlaying) {
            b.btnPlayPause.setImageResource(R.drawable.ic_pause)
            b.btnPlayPause.contentDescription = "Jeda"
        } else {
            b.btnPlayPause.setImageResource(R.drawable.ic_play)
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

    private fun playPrevSong() {
        if (isShuffle) currentIndex = filePaths.indices.random()
        else currentIndex = if (currentIndex > 0) currentIndex - 1 else filePaths.size - 1
        playSong(currentIndex)
    }

    private fun playNextSong() {
        if (isShuffle) currentIndex = filePaths.indices.random()
        else currentIndex = if (currentIndex < filePaths.size - 1) currentIndex + 1 else 0
        playSong(currentIndex)
    }

    private fun playSong(index: Int) {
        if (index !in filePaths.indices) return
        val file = File(filePaths[index])

        b.tvTitle.text = file.name
        b.tvCounter.text = "${index + 1} dari ${filePaths.size}"

        PlaMediaPlayer.play(file) {
            updateTombolPutar(false); updateSessionState(false)
            notif.showPlayer(file.name, false); scheduleAutoHide()

            when (repeatMode) {
                2 -> playSong(currentIndex)
                1 -> playNextSong()
                0 -> {
                    if (currentIndex < filePaths.size - 1) {
                        currentIndex++; playSong(currentIndex)
                    }
                }
            }
        }

        updateTombolPutar(true); updateSessionState(true); updateSeekBar()
        notif.showPlayer(file.name, true); cancelAutoHide()

        val sessionId = PlaMediaPlayer.audioSessionId
        equalizer?.release()
        equalizer = Equalizer(0, sessionId).apply {
            enabled = true
            savedBandLevels.forEach { (band, level) -> setBandLevel(band, level) }
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
            Toast.makeText(this, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEqualizerDialog() {
        if (equalizer == null) {
            Toast.makeText(this, "Tidak ada audio yang diputar", Toast.LENGTH_SHORT).show(); return
        }
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Efek Suara (Equalizer)")
        val scrollView = android.widget.ScrollView(this)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        scrollView.addView(layout)

        val numBands = equalizer!!.numberOfBands
        val range = equalizer!!.bandLevelRange

        for (i in 0 until numBands) {
            val bandIndex = i.toShort()
            val freq = equalizer!!.getCenterFreq(bandIndex) / 1000
            val freqText = if (freq >= 1000) "${freq / 1000} kHz" else "$freq Hz"

            val label = android.widget.TextView(this).apply {
                text = freqText
                setTextColor(android.graphics.Color.WHITE)
                setPadding(0, 20, 0, 0)
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
        builder.setView(scrollView)
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

    override fun onStop() {
        super.onStop()
        if (!PlaMediaPlayer.isPlaying) scheduleAutoHide()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        cancelAutoHide()
        try { unregisterReceiver(actionReceiver) } catch (_: Exception) {}
        mediaSession?.isActive = false
        mediaSession?.release()
        equalizer?.release()
    }
}