package com.pla.manajer

import java.io.File

data class MixerTrack(
    var file: File,
    var startTime: String = "00:00:00",
    var volume: Int = 100, // 0 - 200%
    var panning: Int = 100 // 0 (Kiri) - 100 (Tengah) - 200 (Kanan)
)