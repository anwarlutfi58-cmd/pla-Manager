package com.pla.manajer.util

object AudioBatchHelper {
    // Fitur 116-122 & 130-135 (Tagging, Konversi)
    fun getId3TaggingCommand(title: String, artist: String): String {
        return "-metadata title=\"$title\" -metadata artist=\"$artist\""
    }
    
    fun getCoverArtEmbedCommand(imagePath: String): String {
        return "-i \"$imagePath\" -map 0:a -map 1:v -c:v copy -disposition:v attached_pic"
    }

    fun getOpusEncoding(): String = "-c:a libopus -b:a 64k"
    fun getFlacEncoding(): String = "-c:a flac -compression_level 12"
    fun getWav24Bit(): String = "-c:a pcm_s24le"
}