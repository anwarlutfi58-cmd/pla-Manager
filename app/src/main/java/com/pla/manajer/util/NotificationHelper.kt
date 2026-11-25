package com.pla.manajer.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.pla.manajer.AudioPlayerActivity
import com.pla.manajer.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_PLAYER = "pla_player_channel"
        const val CHANNEL_EDIT   = "pla_edit_channel"
        const val CHANNEL_FILE   = "pla_file_channel"

        const val ID_PLAYER = 1001
        const val ID_EDIT   = 1002
        const val ID_FILE   = 1003

        const val ACTION_PREV       = "com.pla.manajer.NOTIF_PREV"
        const val ACTION_PLAY_PAUSE = "com.pla.manajer.NOTIF_PLAY_PAUSE"
        const val ACTION_NEXT       = "com.pla.manajer.NOTIF_NEXT"
        const val ACTION_CLOSE      = "com.pla.manajer.NOTIF_CLOSE"
    }

    private val manager = context.getSystemService(NotificationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    init { createChannels() }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val player = NotificationChannel(
                CHANNEL_PLAYER, "Pemutar Musik",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kontrol pemutar musik"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val edit = NotificationChannel(
                CHANNEL_EDIT, "Proses Edit",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi saat mengedit audio/gambar/video"
                setShowBadge(false)
            }
            val file = NotificationChannel(
                CHANNEL_FILE, "Operasi File",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifikasi saat menyalin, memindahkan, atau mengekstrak"
                setShowBadge(false)
            }
            manager.createNotificationChannels(listOf(player, edit, file))
        }
    }

    fun showPlayer(title: String, isPlaying: Boolean) {
        // PERBAIKAN: Tambahkan setPackage agar diizinkan sistem Android
        val prevIntent = Intent(context, AudioPlayerActivity::class.java).apply {
            action = ACTION_PREV
            setPackage(context.packageName)
        }
        val prevPi  = PendingIntent.getBroadcast(context, 10, prevIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val playIntent = Intent(context, AudioPlayerActivity::class.java).apply {
            action = ACTION_PLAY_PAUSE
            setPackage(context.packageName)
        }
        val playPi  = PendingIntent.getBroadcast(context, 11, playIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val nextIntent = Intent(context, AudioPlayerActivity::class.java).apply {
            action = ACTION_NEXT
            setPackage(context.packageName)
        }
        val nextPi  = PendingIntent.getBroadcast(context, 12, nextIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val closeIntent = Intent(context, AudioPlayerActivity::class.java).apply {
            action = ACTION_CLOSE
            setPackage(context.packageName)
        }
        val closePi = PendingIntent.getBroadcast(context, 13, closeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val openPi  = PendingIntent.getActivity(
            context, 14,
            Intent(context, AudioPlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_PLAYER)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(if (isPlaying) "Sedang diputar" else "Dijeda")
            .setContentIntent(openPi)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_previous, "Sebelumnya", prevPi)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Jeda" else "Putar", playPi
            )
            .addAction(android.R.drawable.ic_media_next, "Berikutnya", nextPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tutup", closePi)
            .build()

        manager.notify(ID_PLAYER, notif)
    }

    fun hidePlayer() { manager.cancel(ID_PLAYER) }

    fun scheduleHidePlayer(delayMs: Long = 5 * 60 * 1000L, hideRunnable: Runnable) {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, delayMs)
    }

    fun cancelScheduledHide(hideRunnable: Runnable) {
        handler.removeCallbacks(hideRunnable)
    }

    fun showEdit(title: String, message: String) {
        val notif = NotificationCompat.Builder(context, CHANNEL_EDIT)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(ID_EDIT, notif)
    }

    fun hideEdit() { manager.cancel(ID_EDIT) }

    fun showFile(title: String, message: String, autoHideMs: Long = 3000L) {
        val notif = NotificationCompat.Builder(context, CHANNEL_FILE)
            .setSmallIcon(R.drawable.ic_file)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(ID_FILE, notif)

        if (autoHideMs > 0) {
            handler.postDelayed({ manager.cancel(ID_FILE) }, autoHideMs)
        }
    }

    fun hideFile() { manager.cancel(ID_FILE) }
}