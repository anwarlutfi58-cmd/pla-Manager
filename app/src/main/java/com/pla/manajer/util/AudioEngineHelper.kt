package com.pla.manajer.util

object AudioEngineHelper {

    fun buildFilters(
        isTabSpeed: Boolean, tempoProgress: Int, pitchProgress: Int, sourceSampleRate: Int,
        isTabEq: Boolean, volumeProgress: Int, eqBars: Array<android.widget.SeekBar>, eqFreqs: IntArray, masterProgress: Int, limiterProgress: Int,
        isTabStereo: Boolean, stereoProgress: Int, panningProgress: Int,
        isTabFormat: Boolean, removeWatermark: Boolean,
        isTabLoudness: Boolean, normLufs: Boolean, normPeak: Boolean, agc: Boolean,
        isTabDinamika: Boolean, compressor: Boolean, compThreshProgress: Int, compRatioProgress: Int, compAttackProgress: Int, compReleaseProgress: Int,
        isTabResample: Boolean, resampleRate: String, soxResample: Boolean,
        isTabStereoPro: Boolean, stereoEnhancer: Boolean, enhancerIntProgress: Int, stereoWidener: Boolean, widenerMidProgress: Int, widenerSideProgress: Int,
        // Tab Vokal Pro
        isTabVokal: Boolean, vocalDoubler: Boolean, whisper: Boolean, breathControl: Boolean, deEsser: Boolean, chorus: Boolean, autoTuneProgress: Int,
        // Tab Efek Spesial
        isTabEfekSpesial: Boolean, robotVoice: Boolean, chipmunk: Boolean, deepVoice: Boolean, telephone: Boolean, radio: Boolean, audio8D: Boolean, tapeStop: Boolean, vinylCrackle: Boolean,
        // Tab Echo & Reverb (Terpisah)
        isTabEchoReverb: Boolean, echoDelayProgress: Int, echoFeedbackProgress: Int, reverbRoomProgress: Int, reverbDampingProgress: Int,
        // Tab Fade & Noise
        fadeInProgress: Int, fadeOutProgress: Int, noiseReduction: Boolean, highPass: Boolean, deClick: Boolean, silenceRemove: Boolean
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

        // ===== VOKAL PRO =====
        if (isTabVokal) {
            if (vocalDoubler) f.add("aecho=0.8:0.88:60:0.4")
            if (whisper) f.add("highpass=f=1000,lowpass=f=4000")
            if (breathControl) f.add("agate=threshold=0.05:ratio=2:attack=10:release=100")
            if (deEsser) f.add("deesser=i=0.5:m=0.5:f=0.5")
            if (chorus) f.add("chorus=0.6:0.9:50|60:0.4|0.32:0.25|0.4:2|1.3")
            if (autoTuneProgress > 0) {
                val strength = autoTuneProgress / 100.0 * 0.5
                f.add("rubberband=pitch=1.0:formant=preserved:transients=smooth")
            }
        }

        // ===== EFEK SPESIAL =====
        if (isTabEfekSpesial) {
            if (robotVoice) f.add("flanger=delay=0:depth=2:regen=10:width=71:speed=0.5")
            if (chipmunk) f.add("asetrate=44100*1.5,aresample=44100,atempo=0.67")
            if (deepVoice) f.add("asetrate=44100*0.7,aresample=44100,atempo=1.43")
            if (telephone) f.add("highpass=f=300,lowpass=f=3400")
            if (radio) f.add("highpass=f=200,lowpass=f=6000,acompressor=threshold=0.05:ratio=8")
            if (audio8D) f.add("apulsator=hz=0.1:mode=square")
            if (tapeStop) f.add("atempo=0.3")
            if (vinylCrackle) f.add("anoisesrc=d=1:c=pink:a=0.02")
        }

        // ===== ECHO & REVERB (DIPISAH) =====
        if (isTabEchoReverb) {
            // Echo: Delay + Feedback (dalam ms, max 2000)
            if (echoFeedbackProgress > 0) {
                val delay = echoDelayProgress
                val feedback = echoFeedbackProgress / 100.0
                f.add("aecho=0.8:$feedback:$delay:0.3")
            }
            // Reverb: Room Size + Damping (menggunakan aecho sebagai pendekatan ruangan)
            if (reverbRoomProgress > 0) {
                val roomSize = 0.5 + (reverbRoomProgress / 100.0) * 0.5
                val damping = (reverbDampingProgress / 100.0) * 0.7
                f.add("aecho=0.8:$damping:${(roomSize * 1000).toInt()}:0.5")
            }
        }

        // ===== FADE & NOISE =====
        if (fadeInProgress > 0) f.add("afade=t=in:st=0:d=$fadeInProgress")
        if (fadeOutProgress > 0) f.add("afade=t=out:st=9999:d=$fadeOutProgress")
        if (noiseReduction) f.add("afftdn")
        if (highPass) f.add("highpass=f=200")
        if (deClick) f.add("adeclick")
        if (silenceRemove) f.add("silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.1")

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