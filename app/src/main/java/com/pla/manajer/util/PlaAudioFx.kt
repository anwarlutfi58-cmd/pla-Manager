package com.pla.manajer.util

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer

/**
 * Equalizer GLOBAL: satu mesin efek untuk seluruh aplikasi.
 * Terikat ke sesi audio PlaMediaPlayer dan TETAP HIDUP walau layar pemutar ditutup.
 */
object PlaAudioFx {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var attachedSession = -1
    private lateinit var prefs: Prefs

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = Prefs(context.applicationContext)
    }

    fun attach(sessionId: Int) {
        if (sessionId <= 0) return
        if (attachedSession == sessionId && equalizer != null) { applyAll(); return }
        release()
        attachedSession = sessionId
        try {
            equalizer = Equalizer(0, sessionId)
            applyAll()
        } catch (_: Exception) { equalizer = null }
        try {
            bassBoost = BassBoost(0, sessionId)
            bassBoost?.enabled = prefs.eqEnabled && prefs.bassBoost > 0
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(prefs.bassBoost.toShort())
            }
        } catch (_: Exception) { bassBoost = null }
    }

    fun applyAll() {
        val eq = equalizer ?: return
        try {
            eq.enabled = prefs.eqEnabled
            if (!prefs.eqEnabled) return
            val n = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            for (i in 0 until n) {
                val targetHz = mapBandToFreq(i, n)
                val savedDb = prefs.getEqLevel(targetHz)
                val levelMb = (savedDb * 100).toShort()
                val clamped = levelMb.coerceIn(range[0], range[1])
                eq.setBandLevel(i.toShort(), clamped)
            }
        } catch (_: Exception) {}
        try {
            bassBoost?.enabled = prefs.eqEnabled && prefs.bassBoost > 0
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(prefs.bassBoost.toShort())
            }
        } catch (_: Exception) {}
    }

    /** Memetakan band hardware (biasanya 5) ke frekuensi ISO 32-band terdekat */
    fun mapBandToFreq(bandIndex: Int, totalBands: Int): Int {
        val iso = intArrayOf(
            20, 25, 31, 39, 49, 61, 76, 95, 119, 149, 186, 232, 290, 362, 453, 566,
            707, 883, 1100, 1380, 1720, 2150, 2690, 3360, 4200, 5250, 6560, 8200,
            10250, 12810, 16010, 20000
        )
        val eq = equalizer
        if (eq != null) {
            try {
                val centerHz = eq.getCenterFreq(bandIndex.toShort()) / 1000
                return iso.minByOrNull { kotlin.math.abs(it - centerHz) } ?: iso[0]
            } catch (_: Exception) {}
        }
        val idx = ((bandIndex.toFloat() / maxOf(1, totalBands - 1)) * 31).toInt().coerceIn(0, 31)
        return iso[idx]
    }

    fun numberOfBands(): Int = try { equalizer?.numberOfBands?.toInt() ?: 0 } catch (_: Exception) { 0 }

    fun bandLevelRange(): ShortArray? = try { equalizer?.bandLevelRange } catch (_: Exception) { null }

    fun centerFreqHz(band: Int): Int = try { (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000 } catch (_: Exception) { 0 }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        attachedSession = -1
    }
}