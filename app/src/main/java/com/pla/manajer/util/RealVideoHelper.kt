package com.pla.manajer.util

object RealVideoHelper {

    // 1. Potong Video (Trim) - Memotong dari detik ke-X sampai ke-Y
    fun getTrimCommand(startTime: String, endTime: String): String {
        return "-ss $startTime -to $endTime"
    }

    // 2. Gabung 2 Video Asli
    fun getMergeCommand(input1: String, input2: String, output: String): String {
        return "-i \"$input1\" -i \"$input2\" -filter_complex \"[0:v][0:a][1:v][1:a]concat=n=2:v=1:a=1[v][a]\" -map \"[v]\" -map \"[a]\" \"$output\""
    }

    // 3. Mengubah Video Asli jadi GIF
    fun getVideoToGifCommand(): String {
        return "-vf \"fps=10,scale=320:-1:flags=lanczos\" -c:v gif"
    }

    // 4. Mute Video (Hapus Suara)
    fun getMuteCommand(): String {
        return "-an"
    }

    // 5. Kompres Ukuran Video Asli
    fun getCompressCommand(): String {
        return "-vcodec libx264 -crf 28 -preset fast"
    }
}