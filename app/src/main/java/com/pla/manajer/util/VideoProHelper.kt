package com.pla.manajer.util

object VideoProHelper {

    val visualEffects = arrayOf(
        "Normal", "Hitam Putih", "Sepia", "Vintage", "Invert", "Vignette", "Glitch/VHS",
        "Mirror", "Cartoon", "Sketch", "Oil Painting", "Pixelate", "Blur Area", "Blur Background",
        "Slow Motion", "Time-lapse", "Boomerang", "Freeze Frame", "Speed Ramping"
    )

    fun getVisualFilter(effect: String): String {
        return when (effect) {
            "Hitam Putih" -> "hue=s=0"
            "Sepia" -> "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131"
            "Vintage" -> "curves=preset=vintage"
            "Invert" -> "negate"
            "Vignette" -> "vignette=PI/4"
            "Glitch/VHS" -> "noise=alls=20:allf=t+u"
            "Mirror" -> "hflip"
            "Cartoon" -> "edgedetect=low=0.1:high=0.4"
            "Sketch" -> "edgedetect=low=0.1:high=0.4,negate"
            "Oil Painting" -> "convolution='0 -1 0 -1 5 -1 0 -1 0'"
            "Pixelate" -> "pixelize=w=10:h=10"
            "Blur Background" -> "gblur=sigma=10"
            "Slow Motion" -> "setpts=2.0*PTS"
            "Time-lapse" -> "setpts=0.5*PTS"
            "Boomerang" -> "setpts=0.5*PTS"
            "Freeze Frame" -> "tpad=stop_mode=clone:stop=1"
            "Speed Ramping" -> "setpts=0.5*PTS"
            else -> ""
        }
    }

    val watermarkPositions = arrayOf(
        "Kiri Atas", "Tengah Atas", "Kanan Atas",
        "Kiri Tengah", "Tengah", "Kanan Tengah",
        "Kiri Bawah", "Tengah Bawah", "Kanan Bawah"
    )

    fun getWatermarkPosition(pos: String): String {
        return when (pos) {
            "Kiri Atas" -> "x=20:y=20"
            "Tengah Atas" -> "x=(w-text_w)/2:y=20"
            "Kanan Atas" -> "x=w-tw-20:y=20"
            "Kiri Tengah" -> "x=20:y=(h-text_h)/2"
            "Tengah" -> "x=(w-text_w)/2:y=(h-text_h)/2"
            "Kanan Tengah" -> "x=w-tw-20:y=(h-text_h)/2"
            "Kiri Bawah" -> "x=20:y=h-th-20"
            "Tengah Bawah" -> "x=(w-text_w)/2:y=h-th-20"
            "Kanan Bawah" -> "x=w-tw-20:y=h-th-20"
            else -> "x=(w-text_w)/2:y=h-th-20"
        }
    }
}