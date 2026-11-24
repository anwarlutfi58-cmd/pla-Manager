package com.pla.manajer.util

object VideoFilterHelper {

    // Fungsi untuk Filter Warna & Efek Visual
    fun getFilterCommand(filterType: Int): String {
        return when(filterType) {
            0 -> "hue=s=0" // Hitam Putih
            1 -> "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131" // Sepia
            2 -> "negate" // Negatif
            3 -> "curves=vintage" // Vintage
            4 -> "eq=brightness=0.1:contrast=1.5" // Cerah & Kontras
            5 -> "eq=brightness=-0.1:contrast=0.8" // Gelap
            6 -> "eq=saturation=2.0" // Warna Tajam
            7 -> "gblur=sigma=10" // Blur
            8 -> "edgedetect" // Sketsa (Edge)
            9 -> "colorbalance=rs=.3" // Efek Merah
            10 -> "colorbalance=bs=.3" // Efek Biru
            11 -> "vignette" // Vignette (Pinggir Gelap)
            12 -> "noise=alls=20:allf=t" // Efek Grain (Film)
            13 -> "pixelize=w=50:h=50" // Mosaik / Pixelate
            14 -> "sharpen=1.0" // Pertajam
            15 -> "chromashift=rx=5:ry=5" // Efek Glitch
            else -> ""
        }
    }

    // Fungsi untuk Teks & Watermark
    fun getTextCommand(text: String, position: String, color: String, size: Int): String {
        // position: "bottom", "top", "center"
        val pos = when(position) {
            "top" -> "x=(w-text_w)/2:y=50"
            "center" -> "x=(w-text_w)/2:y=(h-text_h)/2"
            else -> "x=(w-text_w)/2:y=h-th-50"
        }
        return "drawtext=text='$text':fontcolor=$color:fontsize=$size:$pos"
    }

    // Fungsi untuk Transisi (Sederhana)
    fun getTransitionCommand(type: Int): String {
        return when(type) {
            0 -> "fade=t=in:st=0:d=1,fade=t=out:st=END_TIME:d=1" // Fade In/Out (butuh timing dinamis)
            else -> ""
        }
    }
}