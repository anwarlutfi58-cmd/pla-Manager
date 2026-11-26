package com.pla.manajer.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pla.manajer.AudioPlayerActivity

/**
 * Penerima tombol notifikasi. Hidup mandiri walau Activity pemutar sudah ditutup.
 */
class MusicActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        PlaMediaPlayer.init(context)
        val notif = NotificationHelper(context)
        when (intent.action) {
            NotificationHelper.ACTION_PLAY_PAUSE -> {
                if (PlaMediaPlayer.isPlaying) PlaMediaPlayer.pause() else PlaMediaPlayer.resume()
                notif.showPlayer(PlaMediaPlayer.currentFile?.name ?: "Musik", PlaMediaPlayer.isPlaying)
            }
            NotificationHelper.ACTION_NEXT -> {
                val i = Intent(context, AudioPlayerActivity::class.java).apply {
                    action = NotificationHelper.ACTION_NEXT
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(i)
            }
            NotificationHelper.ACTION_PREV -> {
                val i = Intent(context, AudioPlayerActivity::class.java).apply {
                    action = NotificationHelper.ACTION_PREV
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(i)
            }
            NotificationHelper.ACTION_CLOSE -> {
                PlaMediaPlayer.pause()
                notif.hidePlayer()
            }
        }
    }
}