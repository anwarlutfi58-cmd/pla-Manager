package com.pla.manajer.util

import android.content.Context
import android.media.MediaPlayer
import android.os.Environment
import android.widget.Toast
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

object AudioProcessHelper {

    fun renderAudio(
        context: Context,
        inputFile: File,
        outputFile: File,
        simpleFilters: List<String>,
        hasMb: Boolean,
        mbIntensity: Int,
        isTrim: Boolean,
        startTime: String,
        endTime: String,
        format: String,
        bitDepth: String,
        bitrate: String,
        sampleRate: String,
        removeMeta: Boolean,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val cmd = StringBuilder("-y ")
        if (isTrim && endTime != "00:00:00") cmd.append("-ss $startTime -to $endTime ")
        cmd.append("-i \"${inputFile.absolutePath}\" ")
        
        if (hasMb) cmd.append("-filter_complex \"${AudioEngineHelper.buildMultibandFilterGraph(simpleFilters, mbIntensity)}\" -map \"[mb_out]\" ")
        else if (simpleFilters.isNotEmpty()) cmd.append("-filter_complex \"[0:a]${simpleFilters.joinToString(",")}[out]\" -map \"[out]\" ")

        when (format) {
            "flac" -> cmd.append("-c:a flac -compression_level 8 " + if (bitDepth == "16-bit") "-sample_fmt s16 " else "-sample_fmt s32 ")
            "flac12" -> cmd.append("-c:a flac -compression_level 12 " + if (bitDepth == "16-bit") "-sample_fmt s16 " else "-sample_fmt s32 ")
            "wav" -> cmd.append("-c:a pcm_s16le ")
            "wav24" -> cmd.append("-c:a pcm_s24le ")
            "aiff" -> cmd.append(when (bitDepth) { "16-bit" -> "-c:a pcm_s16be "; "32-bit" -> "-c:a pcm_s32be "; else -> "-c:a pcm_s24be " })
            "mp3vbr" -> cmd.append("-c:a libmp3lame -q:a 2 ")
            "mp3" -> cmd.append("-c:a libmp3lame -b:a $bitrate ")
            "aac" -> cmd.append("-c:a aac -b:a $bitrate ")
            "m4a" -> cmd.append("-c:a aac -b:a $bitrate ")
            "opus" -> cmd.append("-c:a libopus -b:a $bitrate ")
            else -> cmd.append("-b:a $bitrate ")
        }
        
        if (format in listOf("mp3", "aac", "m4a", "mp3vbr")) cmd.append("-ar $sampleRate ")
        if (removeMeta) cmd.append("-map_metadata -1 ")
        cmd.append("\"${outputFile.absolutePath}\"")

        FFmpegKit.executeAsync(cmd.toString()) { session ->
            if (ReturnCode.isSuccess(session.returnCode)) {
                onSuccess(outputFile)
            } else {
                onError(session.failStackTrace ?: "Gagal memproses audio.")
            }
        }
    }
}