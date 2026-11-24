package com.pla.manajer.util

object AudioProHelper {
    // Audio Split: Pisah Kiri / Kanan (contoh output ke 2 file)
    fun getSplitChannelCommand(input: String, outLeft: String, outRight: String): String {
        return "-i \"$input\" -filter_complex \"[0:a]channelsplit=channel_layout=stereo[left][right]\" -map \"[left]\" \"$outLeft\" -map \"[right]\" \"$outRight\""
    }

    // Spectral Delay (Echo Lembut)
    fun getSpectralDelayFilter(): String = "aecho=0.8:0.88:1000:0.5"

    // Auto-Tune Ringan (Rubberband)
    fun getAutoTuneFilter(strength: Int): String {
        return "rubberband=pitch=1.0:formant=preserved:transients=smooth"
    }
}