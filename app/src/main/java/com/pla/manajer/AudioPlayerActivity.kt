package com.pla.manajer

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pla.manajer.databinding.ActivityAudioPlayerBinding
import com.pla.manajer.util.MusicPlaybackService
import com.pla.manajer.util.NotificationHelper
import com.pla.manajer.util.PlaMediaPlayer
import java.io.File
import java.io.FileOutputStream

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

    // Pantau perubahan dari Service (lagu berganti / dijeda dari notifikasi)
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STATE_CHANGED -> {
                    val playing = intent.getBooleanExtra(EXTRA_PLAYING, false)
                    val idx = intent.getIntExtra(EXTRA_INDEX, currentIndex)
                    currentIndex = idx
                    updateTombolPutar(playing)
                    refreshNowPlaying()
                    attachEqualizer()
                }
                ACTION_FINISHED -> {
                    updateTombolPutar(false)
                }
            }
        }
    }

    companion object {
        const val ACTION_STATE_CHANGED = "com.pla.manajer.PLAYER_STATE_CHANGED"
        const val ACTION_FINISHED = "com.pla.manajer.PLAYER_FINISHED"
        const val EXTRA_PLAYING = "extra_playing"
        const val EXTRA_INDEX = "extra_index"
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)

        notif = NotificationHelper(this)
        PlaMediaPlayer.init(this)

        var startPath: String? = intent.getStringExtra("start_path")
        var pathsList = intent.getStringArrayListExtra("file_paths")

        if (pathsList.isNullOrEmpty() && intent?.action == Intent.ACTION_SEND) {
            val uri = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null) {
                val tempFile = File(cacheDir, "shared_music_player.mp3")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
                startPath = tempFile.absolutePath
                pathsList = arrayListOf(tempFile.absolutePath)
            }
        }

        filePaths = pathsList ?: emptyList()
        if (startPath != null && filePaths.isNotEmpty()) {
            val i = filePaths.indexOf(startPath)
            if (i > 0) filePaths = filePaths.drop(i) + filePaths.take(i)
        }
        currentIndex = 0

        requestNotifPermissionIfNeeded()
        registerStateReceiver()

        b.btnBack.setOnClickListener { finish() }
        b.btnPlayPause.setOnClickListener { togglePlayPause() }
        b.btnNext.setOnClickListener { sendCommand(NotificationHelper.ACTION_NEXT) }
        b.btnPrev.setOnClickListener { sendCommand(NotificationHelper.ACTION_PREV) }

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

        // Mulai pemutaran via Service (notifikasi ala YouTube)
        startPlaybackService()

        b.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) PlaMediaPlayer.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun startPlaybackService() {
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.CMD_PLAY_LIST
            putStringArrayListExtra(MusicPlaybackService.EXTRA_PATHS, ArrayList(filePaths))
            putExtra(MusicPlaybackService.EXTRA_START_INDEX, currentIndex)
            putExtra(MusicPlaybackService.EXTRA_SHUFFLE, isShuffle)
            putExtra(MusicPlaybackService.EXTRA_REPEAT, repeatMode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun sendCommand(action: String) {
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            this.action = action
            // ikutkan ulang playlist agar service tahu konteksnya
            putStringArrayListExtra(MusicPlaybackService.EXTRA_PATHS, ArrayList(filePaths))
            putExtra(MusicPlaybackService.EXTRA_START_INDEX, currentIndex)
            putExtra(MusicPlaybackService.EXTRA_SHUFFLE, isShuffle)
            putExtra(MusicPlaybackService.EXTRA_REPEAT, repeatMode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun togglePlayPause() {
        if (PlaMediaPlayer.isPlaying) {
            sendCommand(NotificationHelper.ACTION_PLAY_PAUSE)
        } else {
            // Kalau service belum jalan (misal baru buka lagi), mulai ulang
            if (filePaths.isNotEmpty()) startPlaybackService()
            else sendCommand(MusicPlaybackService.CMD_RESUME)
        }
    }

    private fun registerStateReceiver() {
        val f = IntentFilter().apply {
            addAction(ACTION_STATE_CHANGED)
            addAction(ACTION_FINISHED)
        }
        ContextCompat.registerReceiver(this, stateReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun refreshNowPlaying() {
        if (currentIndex in filePaths.indices) {
            val file = File(filePaths[currentIndex])
            b.tvTitle.text = file.name
            b.tvCounter.text = "${currentIndex + 1} dari ${filePaths.size}"
        }
        updateSeekBar()
    }

    private fun attachEqualizer() {
        val sessionId = PlaMediaPlayer.audioSessionId
        if (sessionId <= 0) return
        equalizer?.release()
        equalizer = Equalizer(0, sessionId).apply {
            enabled = true
            savedBandLevels.forEach { (band, level) -> setBandLevel(band, level) }
        }
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)

        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101)
    }

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

    private fun shareCurrentSong() {
        val file = PlaMediaPlayer.currentFile ?: return
        try {
            val uri = com.pla.manajer.util.ShareHelper.let {
                androidx.core.content.FileProvider.getUriForFile(this, "$packageName.provider", file)
            }
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
        handler.removeCallbacks(seekRunnable)
        handler.postDelayed(seekRunnable, 500)
    }

    private val seekRunnable = object : Runnable {
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
    }

    private fun formatTime(ms: Int): String {
        val sec = ms / 1000
        return String.format("%02d:%02d", sec / 60, sec % 60)
    }

    override fun onResume() {
        super.onResume()
        updateTombolPutar(PlaMediaPlayer.isPlaying)
        refreshNowPlaying()
        attachEqualizer()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
        equalizer?.release()
        // JANGAN stop musik di sini — biar terus main di background seperti YouTube
    }
}