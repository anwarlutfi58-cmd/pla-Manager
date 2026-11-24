package com.pla.manajer.util

object VideoTransformHelper {

    // Fungsi untuk Kecepatan Video
    fun getSpeedCommand(speedType: Int): String {
        return when(speedType) {
            0 -> "setpts=0.5*PTS" // Cepat (Fast Motion)
            1 -> "setpts=2.0*PTS" // Lambat (Slow Motion)
            2 -> "setpts=0.25*PTS" // Sangat Cepat
            else -> ""
        }
    }

    // Fungsi untuk Ganti Rasio / Crop
    fun getCropCommand(aspectRatio: Int): String {
        return when(aspectRatio) {
            0 -> "crop=ih*16/9:ih" // 16:9 Lanskap
            1 -> "crop=ih*9/16:ih" // 9:16 Potret (TikTok)
            2 -> "crop=ih:ih" // 1:1 Persegi
            3 -> "crop=ih*4/3:ih" // 4:3 Klasik
            else -> ""
        }
    }

    // Fungsi untuk Rotate Video
    fun getRotateCommand(rotateType: Int): String {
        return when(rotateType) {
            0 -> "transpose=1" // Putar 90 Kanan
            1 -> "transpose=2" // Putar 90 Kiri
            2 -> "transpose=1,transpose=1" // Putar 180
            3 -> "hflip" // Cermin Kiri-Kanan
            4 -> "vflip" // Cermin Atas-Bawah
            else -> ""
        }
    }

    // Konversi Format
    fun getFormatCommand(formatType: Int): String {
        return when(formatType) {
            0 -> "-c:v libx264 -crf 28 -preset fast" // MP4 Hemat Ukuran (Kompres)
            1 -> "-c:v libx265 -crf 28" // H.265 (Lebih Kecil)
            2 -> "-vf fps=10 -loop 0" // Siapkan untuk GIF
            else -> ""
        }
    }
}