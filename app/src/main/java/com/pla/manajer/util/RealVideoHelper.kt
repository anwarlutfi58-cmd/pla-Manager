package com.pla.manajer.util

object RealVideoHelper {

    // 1. Perintah untuk Memotong Video (Trim)
    fun getTrimCommand(startTime: String, endTime: String): String {
        // startTime dan endTime formatnya "00:00:05" (Jam:Menit:Detik)
        return "-ss $startTime -to $endTime"
    }

    // 2. Perintah untuk Mute (Hapus Suara)
    fun getMuteCommand(): String {
        return "-an"
    }

    // 3. Perintah untuk Mempercepat Video Asli (tanpa mengubah suara jadi robot)
    fun getSpeedUpCommand(speed: Float): String {
        return "-filter_complex \"[0:v]setpts=${1/speed}*PTS[v];[0:a]atempo=$speed[a]\" -map \"[v]\" -map \"[a]\""
    }

    // 4. Perintah untuk Menjadikan GIF
    fun getToGifCommand(): String {
        return "-vf \"fps=10,scale=320:-1:flags=lanczos\" -c:v gif"
    }
}