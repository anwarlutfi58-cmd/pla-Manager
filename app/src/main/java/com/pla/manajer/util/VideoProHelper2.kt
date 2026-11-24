package com.pla.manajer.util

object VideoProHelper2 {
    // Chroma Key: Hapus warna hijau
    fun getChromaKeyFilter(): String {
        return "chromakey=green:0.1:0.2"
    }

    // PIP: Overlay video kedua di pojok kanan atas, ukuran 30%
    fun getPIPFilter(overlayVideoIndex: Int): String {
        return "overlay=W-w-20:20:scale=0.3"
    }

    // Cinematic Bars (garis hitam atas-bawah)
    fun getCinematicBarsFilter(): String {
        return "drawbox=y=0:color=black@1.0:t=fill:h=ih*0.1,drawbox=y=ih-ih*0.1:color=black@1.0:t=fill:h=ih*0.1"
    }

    // Mirror Video
    fun getMirrorFilter(): String = "hflip"

    // Auto-Enhance (sedikit tingkatkan kontras & saturasi)
    fun getAutoEnhanceFilter(): String = "eq=contrast=1.1:saturation=1.2:brightness=0.02"
}