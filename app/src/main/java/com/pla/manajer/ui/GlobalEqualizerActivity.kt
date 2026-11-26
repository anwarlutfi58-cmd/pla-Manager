package com.pla.manajer.ui

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pla.manajer.service.GlobalEqualizerService
import com.pla.manajer.util.PlaAudioFx
import com.pla.manajer.util.PlaMediaPlayer
import com.pla.manajer.util.Prefs

class GlobalEqualizerActivity : AppCompatActivity() {

    private val freqs = intArrayOf(
        20, 25, 31, 39, 49, 61, 76, 95, 119, 149, 186, 232, 290, 362, 453, 566,
        707, 883, 1100, 1380, 1720, 2150, 2690, 3360, 4200, 5250, 6560, 8200, 10250, 12810, 16010, 20000
    )

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        PlaAudioFx.init(this)
        val prefs = Prefs(this)

        val scroll = android.widget.ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#121212"))
        }
        scroll.addView(layout)

        val title = TextView(this).apply {
            text = "Equalizer 32 Band (Global)"
            textSize = 22f
            setTextColor(Color.parseColor("#00BFA5"))
            setPadding(0, 0, 0, 8)
        }
        layout.addView(title)

        val info = TextView(this).apply {
            text = "Berlaku untuk pemutar musik PLA. Putar lagu dulu agar efek terdengar langsung. Jika dimatikan, kartu EQ di beranda akan disembunyikan."
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(info)

        // ===== SAKLAR UTAMA ON/OFF (sinkron dengan Pengaturan & Beranda) =====
        val masterSwitch = Switch(this).apply {
            text = "Aktifkan Equalizer"
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = prefs.eqEnabled
            setOnCheckedChangeListener { _, checked ->
                prefs.eqEnabled = checked
                PlaAudioFx.applyAll()
                if (checked) {
                    val intent = Intent(this@GlobalEqualizerActivity, GlobalEqualizerService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                    Toast.makeText(this@GlobalEqualizerActivity, "Equalizer AKTIF", Toast.LENGTH_SHORT).show()
                } else {
                    stopService(Intent(this@GlobalEqualizerActivity, GlobalEqualizerService::class.java))
                    Toast.makeText(this@GlobalEqualizerActivity, "Equalizer MATI. Kartu EQ disembunyikan dari beranda.", Toast.LENGTH_LONG).show()
                }
            }
        }
        layout.addView(masterSwitch)

        // ===== BASS BOOST =====
        val bassLabel = TextView(this).apply {
            text = "Bass Boost: ${prefs.bassBoost / 10}%"
            setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 0)
        }
        val bassSeek = SeekBar(this).apply {
            max = 1000
            progress = prefs.bassBoost
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        prefs.bassBoost = progress
                        bassLabel.text = "Bass Boost: ${progress / 10}%"
                        PlaAudioFx.applyAll()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        layout.addView(bassLabel)
        layout.addView(bassSeek)

        val divider = TextView(this).apply {
            text = "─── 32 Band Frekuensi ───"
            setTextColor(Color.parseColor("#00BFA5"))
            setPadding(0, 32, 0, 8)
        }
        layout.addView(divider)

        // ===== 32 SLIDER FREKUENSI =====
        for (i in freqs.indices) {
            val freqText = if (freqs[i] >= 1000) "${freqs[i] / 1000} kHz" else "${freqs[i]} Hz"
            val saved = prefs.getEqLevel(freqs[i])

            val label = TextView(this).apply {
                text = "$freqText : $saved dB"
                setTextColor(Color.WHITE)
                setPadding(0, 16, 0, 0)
            }

            val seekBar = SeekBar(this).apply {
                max = 30
                progress = saved + 15
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val db = progress - 15
                            label.text = "$freqText : $db dB"
                            prefs.setEqLevel(freqs[i], db)
                            PlaAudioFx.applyAll()
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            layout.addView(label)
            layout.addView(seekBar)
        }

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (PlaMediaPlayer.audioSessionId > 0) {
            PlaAudioFx.attach(PlaMediaPlayer.audioSessionId)
        }
    }
}