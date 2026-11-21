package com.pla.manajer

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.pla.manajer.databinding.ActivitySettingsBinding
import com.pla.manajer.util.Prefs

class SettingsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySettingsBinding
    
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.fabBack.setOnClickListener { finish() }

        val prefs = Prefs(this)
        
        // Pengaturan Font
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

        // Pengaturan Screen Reader
        b.swReader.isChecked = prefs.screenReaderHints
        b.swReader.setOnCheckedChangeListener { _, v -> prefs.screenReaderHints = v }

        b.tvTrash.text = prefs.trashFolder

        // ==========================================
        // LOGIKA TOMBOL AJAIB
        // ==========================================
        b.swShowImageEditor.isChecked = prefs.showImageEditor
        b.swShowAudioEditor.isChecked = prefs.showAudioEditor
        b.swShowVideoEditor.isChecked = prefs.showVideoEditor
        b.swShowGithubBuilder.isChecked = prefs.showGithubBuilder

        b.swShowImageEditor.setOnCheckedChangeListener { _, isChecked -> prefs.showImageEditor = isChecked }
        b.swShowAudioEditor.setOnCheckedChangeListener { _, isChecked -> prefs.showAudioEditor = isChecked }
        b.swShowVideoEditor.setOnCheckedChangeListener { _, isChecked -> prefs.showVideoEditor = isChecked }
        b.swShowGithubBuilder.setOnCheckedChangeListener { _, isChecked -> prefs.showGithubBuilder = isChecked }
    }
}