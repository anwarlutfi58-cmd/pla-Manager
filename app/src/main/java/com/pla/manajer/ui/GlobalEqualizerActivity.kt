package com.pla.manajer.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pla.manajer.util.Prefs

class GlobalEqualizerActivity : AppCompatActivity() {
    
    // Frekuensi 32 Band
    private val freqs = intArrayOf(
        20, 25, 31, 39, 49, 61, 76, 95, 119, 149, 186, 232, 290, 362, 453, 566,
        707, 883, 1100, 1380, 1720, 2150, 2690, 3360, 4200, 5250, 6560, 8200, 10250, 12810, 16010, 20000
    )

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        
        val prefs = Prefs(this)
        val scroll = android.widget.ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        scroll.addView(layout)

        val title = TextView(this).apply {
            text = "Equalizer 32 Band (Sistem)"
            textSize = 22f
            setPadding(0, 0, 0, 24)
        }
        layout.addView(title)

        for (i in freqs.indices) {
            val freqText = if (freqs[i] >= 1000) "${freqs[i] / 1000} kHz" else "${freqs[i]} Hz"
            
            val label = TextView(this).apply { 
                text = "$freqText : 0 dB"
                setPadding(0, 16, 0, 0)
            }
            
            val seekBar = SeekBar(this).apply {
                max = 30 // -15 to +15
                progress = 15 // 0 dB
                
                // Ambil nilai tersimpan
                val saved = prefs.getEqLevel(i)
                progress = saved + 15
                label.text = "$freqText : ${saved} dB"

                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                        val db = progress - 15
                        label.text = "$freqText : $db dB"
                        prefs.setEqLevel(i, db)
                        // Di sini perintah FFmpeg akan dijalankan (dalam tahap selanjutnya)
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
}