package com.pla.manajer.util

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.pla.manajer.R
import com.pla.manajer.databinding.ActivityAudioEditorBinding

object AudioUiHelper {

    fun setupSeekBar(sb: SeekBar, label: TextView, prefix: String, suffix: String, onChanged: () -> Unit) {
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = "$prefix$progress$suffix"
                onChanged()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    fun showTimePicker(context: Context, title: String, currentTime: String, onSave: (String) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_time_picker, null)
        val etH = dialogView.findViewById<EditText>(R.id.etHours)
        val etM = dialogView.findViewById<EditText>(R.id.etMinutes)
        val etS = dialogView.findViewById<EditText>(R.id.etSeconds)
        
        val parts = currentTime.split(":")
        etH.setText(parts[0]); etM.setText(parts[1]); etS.setText(parts[2])
        
        AlertDialog.Builder(context).setTitle(title).setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                val h = etH.text.toString().padStart(2, '0').ifEmpty { "00" }
                val m = etM.text.toString().padStart(2, '0').ifEmpty { "00" }
                val s = etS.text.toString().padStart(2, '0').ifEmpty { "00" }
                onSave("$h:$m:$s")
            }.setNegativeButton("Batal", null).show()
    }

    fun showAdvancedToolsMenu(context: Context, onFilterSelected: (String) -> Unit, onVisualizer: () -> Unit) {
        val options = arrayOf(
            "Efek 8D Audio (Berputar)",
            "Mastering OTT (Suara Padat)",
            "Hapus Noise (De-bleed)",
            "Gema Ruangan (Convolution)",
            "Buat Video Visualizer",
            "Efek Auto-Tune (Pitch)",
            "Efek Saturation (Hangat)",
            "Bersihkan Kresek (De-click)"
        )

        AlertDialog.Builder(context)
            .setTitle("Pilih Alat Lanjutan")
            .setItems(options) { _, which ->
                when(which) {
                    0 -> onFilterSelected(AudioSpatialHelper.get8DAudio())
                    1 -> onFilterSelected(AudioScienceHelper.getOTTCompression())
                    2 -> onFilterSelected(AudioScienceHelper.getDeBleed())
                    3 -> onFilterSelected(AudioSpatialHelper.getSpatialAudio())
                    4 -> onVisualizer()
                    5 -> onFilterSelected(AudioScienceHelper.getPitchCorrection())
                    6 -> onFilterSelected(AudioScienceHelper.getSaturation())
                    7 -> onFilterSelected(AudioScienceHelper.getCrackleRepair())
                }
                if (which != 4) Toast.makeText(context, "Efek Terpasang! Tekan Ekspor.", Toast.LENGTH_SHORT).show()
            }.show()
    }
}