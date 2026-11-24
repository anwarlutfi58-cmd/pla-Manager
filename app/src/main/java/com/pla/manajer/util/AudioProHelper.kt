package com.pla.manajer.util

class AudioProHelper {
    companion object {
        fun buildCommand(
            inputPath: String, outputPath: String,
            startTime: String?, endTime: String?,
            format: String, bitrate: String, sampleRate: String,
            removeWatermark: Boolean, removeMetadata: Boolean,
            tempo: Float, pitch: Float,
            volume: Float,
            eqBands: Map<Int, Float>,
            masterIntensity: Float, limiterIntensity: Float,
            panning: Float, stereoWidth: Float, echoAmount: Float,
            normalizeLufs: Boolean, normalizePeak: Boolean,
            compressor: Boolean, compThreshold: Float, compRatio: Float,
            soxResample: Boolean, targetSampleRate: String, targetBitDepth: String,
            stereoEnhancer: Boolean, enhancerIntensity: Float,
            stereoWidener: Boolean, widenerMid: Float, widenerSide: Float,
            removeVocal: Boolean, isolateVocal: Boolean, centerCancel: Boolean,
            fadeIn: Int, fadeOut: Int,
            noiseReduction: Boolean, highPass: Boolean
        ): String {
            val cmd = StringBuilder("-y ")
            
            // Trim
            if (!startTime.isNullOrEmpty()) cmd.append("-ss $startTime ")
            if (!endTime.isNullOrEmpty()) cmd.append("-to $endTime ")
            
            cmd.append("-i \"$inputPath\" ")

            // Filter Audio (AF)
            val filters = mutableListOf<String>()

            // Speed & Pitch
            if (tempo != 1.0f) {
                val t1 = if (tempo > 2.0f) 2.0f else tempo
                var t2 = 1.0f
                if (tempo > 2.0f) t2 = tempo / 2.0f
                filters.add("atempo=$t1")
                if (t2 != 1.0f) filters.add("atempo=$t2")
            }
            if (pitch != 0.0f) {
                val p = 1.0f + (pitch / 100.0f)
                filters.add("asetrate=44100*$p,aresample=44100,atempo=${1.0f/p}")
            }

            // Volume
            if (volume != 1.0f) filters.add("volume=$volume")

            // EQ
            if (eqBands.isNotEmpty()) {
                val eqStr = eqBands.entries.joinToString(";") { "f=${it.key}:g=${it.value}:w=1" }
                filters.add("equalizer=$eqStr")
            }

            // Master & Limiter
            if (masterIntensity > 0f) filters.add("compand=attacks=0.1:decays=0.1:points=-90/-90|${-20 + (20 * masterIntensity)}/${-20 + (20 * masterIntensity)}|0/-6")
            if (limiterIntensity > 0f) filters.add("alimiter=limit=${1.0f - (limiterIntensity * 0.9f)}")

            // Panning & Stereo
            if (panning != 0f) filters.add("stereotools=balance_out=$panning")
            if (stereoWidth > 0f) filters.add("stereowiden=delay=20:feedback=0.3:crossfeed=0.3:drymix=${1.0f - stereoWidth}")

            // Echo
            if (echoAmount > 0f) filters.add("aecho=0.8:0.9:1000:${echoAmount}")

            // Loudness & Normalize
            if (normalizeLufs) filters.add("loudnorm=I=-14:TP=-1:LRA=11")
            if (normalizePeak) filters.add("dynaudnorm=p=0.9:m=10")

            // Compressor
            if (compressor) filters.add("acompressor=threshold=${compThreshold}dB:ratio=$compRatio:attack=10:release=100")
            
            // Resample
            if (soxResample) filters.add("aresample=$targetSampleRate:resampler=soxr")

            // Stereo Pro
            if (stereoEnhancer) filters.add("stereotools=mlev=1:slev=${1.0f + enhancerIntensity}")
            if (stereoWidener) filters.add("stereotools=mlev=$widenerMid:slev=$widenerSide")

            // Vokal / Karaoke
            if (removeVocal || centerCancel) filters.add("pan=stereo|c0=c0-c1|c1=c1-c0")
            if (isolateVocal) filters.add("pan=mono|c0=0.5*c0+0.5*c1")

            // Fade
            if (fadeIn > 0) filters.add("afade=t=in:st=0:d=$fadeIn")
            if (fadeOut > 0) filters.add("afade=t=out:st=9999:d=$fadeOut") // Akan diatur ulang oleh FFmpeg (di akhir file)

            // Noise Reduction & HighPass
            if (noiseReduction) filters.add("afftdn")
            if (highPass) filters.add("highpass=f=200")

            // Watermark Removal
            if (removeWatermark) filters.add("lowpass=f=15000")

            if (filters.isNotEmpty()) {
                cmd.append("-af \"${filters.joinToString(",")}\" ")
            }

            // Output Format
            cmd.append("-c:a $format ")
            if (format != "pcm_s16le" && format != "flac") cmd.append("-b:a $bitrate ")
            cmd.append("-ar $targetSampleRate ")

            if (removeMetadata) cmd.append("-map_metadata -1 ")

            cmd.append("\"$outputPath\"")
            return cmd.toString()
        }
    }
}