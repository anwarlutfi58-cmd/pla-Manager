package com.pla.manajer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pla.manajer.databinding.ActivitySettingsBinding
import com.pla.manajer.service.GlobalEqualizerService
import com.pla.manajer.util.PlaAudioFx
import com.pla.manajer.util.Prefs

class SettingsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.fabBack.setOnClickListener { finish() }

        val prefs = Prefs(this)
        PlaAudioFx.init(this)

        b.seekFont.progress = prefs.editorFontSize
        b.tvFont.text = "Ukuran Font Editor: ${prefs.editorFontSize}"
        b.seekFont.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                b.tvFont.text = "Ukuran Font Editor: $p"
                prefs.editorFontSize = p
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.swReader.isChecked = prefs.screenReaderHints
        b.swReader.setOnCheckedChangeListener { _, v -> prefs.screenReaderHints = v }

        b.tvTrash.text = prefs.trashFolder

        b.swShowImageEditor.isChecked = prefs.showImageEditor
        b.swShowAudioEditor.isChecked = prefs.showAudioEditor
        b.swShowVideoEditor.isChecked = prefs.showVideoEditor
        b.swShowGithubBuilder.isChecked = prefs.showGithubBuilder

        b.swShowImageEditor.setOnCheckedChangeListener { _, isChecked -> prefs.showImageEditor = isChecked }
        b.swShowAudioEditor.setOnCheckedChangeListener { _, isChecked -> prefs.showAudioEditor = isChecked }
        b.swShowVideoEditor.setOnCheckedChangeListener { _, isChecked -> prefs.showVideoEditor = isChecked }
        b.swShowGithubBuilder.setOnCheckedChangeListener { _, isChecked -> prefs.showGithubBuilder = isChecked }

        b.switchDateTime.isChecked = prefs.showDateTime
        b.switchDateTime.setOnCheckedChangeListener { _, isChecked -> prefs.showDateTime = isChecked }

        // ===== EQUALIZER: SAKLAR UTAMA (tersimpan & sinkron dengan beranda) =====
        b.swEqualizer.isChecked = prefs.eqEnabled
        b.swEqualizer.setOnCheckedChangeListener { _, isChecked ->
            prefs.eqEnabled = isChecked
            PlaAudioFx.applyAll()
            if (isChecked) {
                val intent = Intent(this, GlobalEqualizerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
                Toast.makeText(this, "Equalizer AKTIF. Kartu EQ muncul di beranda.", Toast.LENGTH_SHORT).show()
            } else {
                stopService(Intent(this, GlobalEqualizerService::class.java))
                Toast.makeText(this, "Equalizer MATI. Kartu EQ disembunyikan dari beranda.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}