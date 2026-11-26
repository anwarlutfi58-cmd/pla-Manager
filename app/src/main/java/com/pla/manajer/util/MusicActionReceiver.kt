package com.pla.manajer.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver lama — sudah TIDAK DIPAKAI lagi (digantikan MusicPlaybackService).
 * Disimpan kosong agar tidak mengganggu build. Aman diabaikan.
 */
class MusicActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Tidak melakukan apa-apa. Kontrol musik sekarang via MusicPlaybackService.
    }
}