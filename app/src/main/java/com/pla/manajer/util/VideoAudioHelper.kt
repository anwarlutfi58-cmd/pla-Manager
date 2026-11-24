package com.pla.manajer.util

object VideoAudioHelper {

    // Perintah FFmpeg untuk Audio
    fun getAudioCommand(audioType: Int): String {
        return when(audioType) {
            0 -> "volume=2.0" // Suara Diperbesar
            1 -> "volume=0.5" // Suara Diperkecil
            2 -> "volume=0" // Mute (Hapus Suara)
            3 -> "atempo=2.0" // Suara Dipercepat
            4 -> "atempo=0.5" // Suara Diperlambat
            5 -> "aecho=0.8:0.9:1000:0.3" // Efek Gema (Echo)
            6 -> "afade=t=in:ss=0:d=3" // Fade In (Suara Muncul Perlahan)
            7 -> "afade=t=out:st=10:d=3" // Fade Out (Suara Hilang Perlahan)
            else -> ""
        }
    }

    // Ekstrak Audio dari Video
    fun getExtractAudioCommand(): String {
        return "-vn -acodec libmp3lame -q:a 2" // Hanya ambil audio jadi MP3
    }

    // Gabung Dua Audio (Mix)
    fun getMixAudioCommand(): String {
        return "-filter_complex amix=inputs=2:duration=first:dropout_transition=2"
    }
}