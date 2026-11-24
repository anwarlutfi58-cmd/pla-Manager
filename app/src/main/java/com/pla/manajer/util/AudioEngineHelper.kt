package com.pla.manajer.util

object AudioEngineHelper {

    fun buildFilters(
        isTabSpeed: Boolean, tempoProgress: Int, pitchProgress: Int, sourceSampleRate: Int,
        isTabEq: Boolean, volumeProgress: Int, eqBars: Array<android.widget.SeekBar>, eqFreqs: IntArray, masterProgress: Int, limiterProgress: Int,
        isTabStereo: Boolean, stereoProgress: Int, echoProgress: Int, panningProgress: Int,
        isTabFormat: Boolean, removeWatermark: Boolean,
        isTabLoudness: Boolean, normLufs: Boolean, normPeak: Boolean, agc: Boolean,
        isTabDinamika: Boolean, compressor: Boolean, compThreshProgress: Int, compRatioProgress: Int, compAttackProgress: Int, compReleaseProgress: Int,
        isTabResample: Boolean, resampleRate: String, soxResample: Boolean,
        isTabStereoPro: Boolean, stereoEnhancer: Boolean, enhancerIntProgress: Int, stereoWidener: Boolean, widenerMidProgress: Int, widenerSideProgress: Int,
        // Parameter Baru:
        fadeInProgress: Int = 0, fadeOutProgress: Int = 0,
        noiseReduction: Boolean = false, highPass: Boolean = false,
        removeVocal: Boolean = false, isolateVocal: Boolean = false, centerCancel: Boolean = false
    ): List<String> {
        val f = mutableListOf<String>()

        if (isTabSpeed) {
            val tempoActive = tempoProgress != 50
            val pitchActive = pitchProgress != 50
            if (tempoActive || pitchActive) {
                val speed = (tempoProgress + 50) / 100f
                val pitchSemis = (pitchProgress - 50) / 10f
                val pitchFactor = Math.pow(2.0, pitchSemis.toDouble() / 12.0)
                if (pitchSemis != 0f) {
                    f.add("asetrate=${(sourceSampleRate * pitchFactor).toInt()}")
                    f.add("aresample=$sourceSampleRate")
                }
                val atempoTarget = (speed / pitchFactor).toFloat()
                if (atempoTarget != 1.0f) {
                    var rem = atempoTarget.toDouble()
                    while (rem < 0.5) { f.add("atempo=0.5"); rem /= 0.5 }
                    while (rem > 2.0) { f.add("atempo=2.0"); rem /= 2.0 }
                    if (rem != 1.0) f.add("atempo=$rem")
                }
            }
        }

        if (isTabEq) {
            if (volumeProgress != 100) f.add("volume=${volumeProgress / 100.0}")
            for (i in eqFreqs.indices) {
                val db = eqBars[i].progress - 10
                if (db != 0) f.add("equalizer=f=${eqFreqs[i]}:width_type=o:width=2:g=$db")
            }
            if (masterProgress > 0) f.add("dynaudnorm=f=150:g=$masterProgress")
            if (limiterProgress > 0) f.add("alimiter=limit=${limiterProgress / 100.0}")
        }

        if (isTabStereo) {
            if (stereoProgress > 0) f.add("stereowiden=delay=20:feedback=0.3:crossfeed=0.3:drymix=0.8")
            if (echoProgress > 0) f.add("aecho=0.8:0.9:1000:${echoProgress / 100.0}")
            if (panningProgress != 100) {
                val p = (panningProgress - 100) / 100.0
                f.add("pan=stereo|c0=c0*${1-p}+c1*$p|c1=c1*${1-p}+c0*$p")
            }
        }

        if (isTabFormat && removeWatermark) f.add("lowpass=f=16000")

        if (isTabLoudness) {
            if (normLufs) f.add("loudnorm=I=-14:TP=-1:LRA=11")
            else if (normPeak) f.add("loudnorm=I=-1:TP=-1:LRA=11")
            if (agc) f.add("dynaudnorm=p=0.9:s=5:m=10")
        }

        if (isTabDinamika && compressor) {
            val db = -60.0 + (compThreshProgress / 100.0) * 60.0
            val threshold = Math.pow(10.0, db / 20.0)
            val ratio = 1.0 + (compRatioProgress / 100.0) * 9.0
            val attack = 0.01 + (compAttackProgress / 100.0) * 199.99
            val release = 10.0 + (compReleaseProgress / 100.0) * 990.0
            f.add("acompressor=threshold=$threshold:ratio=$ratio:attack=$attack:release=$release")
        }

        if (isTabResample) {
            if (soxResample) f.add("aresample=resampler=soxr:osr=$resampleRate") else f.add("aresample=osr=$resampleRate")
        }

        if (isTabStereoPro) {
            if (stereoEnhancer) {
                val k = 1.0 + (enhancerIntProgress / 100.0) * 0.5
                f.add("stereotools=mlev=$k:slev=$k")
            }
            if (stereoWidener) {
                val mid = 0.5 + (widenerMidProgress / 100.0)
                val side = 0.5 + (widenerSideProgress / 100.0)
                f.add("stereotools=mlev=$mid:slev=$side")
            }
        }

        // ===== FITUR BARU =====
        // Karaoke / Vokal
        if (removeVocal || centerCancel) f.add("pan=stereo|c0=c0-c1|c1=c1-c0")
        if (isolateVocal) f.add("pan=mono|c0=0.5*c0+0.5*c1")

        // Fade In / Out
        if (fadeInProgress > 0) f.add("afade=t=in:st=0:d=$fadeInProgress")
        if (fadeOutProgress > 0) f.add("afade=t=out:st=9999:d=$fadeOutProgress") // Akan disesuaikan oleh FFmpeg di akhir audio (butuh durasi asli)

        // Noise & Filter
        if (noiseReduction) f.add("afftdn")
        if (highPass) f.add("highpass=f=200")

        return f
    }

    fun buildMultibandFilterGraph(simple: List<String>, mbIntensity: Int): String {
        val th = (0.3 - (mbIntensity / 100.0) * 0.25).coerceAtLeast(0.05)
        val comp = "acompressor=threshold=$th:ratio=4:attack=20:release=250"
        val prefix = if (simple.isEmpty()) "" else simple.joinToString(",") + ","
        return "[0:a]${prefix}asplit=3[mb_lo][mb_mi][mb_hi];" +
            "[mb_lo]lowpass=f=200,$comp[mb_lo2];" +
            "[mb_mi]highpass=f=200,lowpass=f=2000,$comp[mb_mi2];" +
            "[mb_hi]highpass=f=2000,$comp[mb_hi2];" +
            "[mb_lo2][mb_mi2][mb_hi2]amix=inputs=3[mb_out]"
    }
}