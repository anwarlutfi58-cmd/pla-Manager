package com.pla.manajer

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pla.manajer.databinding.ActivityCodeEditorBinding
import com.pla.manajer.util.CodeHighlighter
import com.pla.manajer.util.Prefs
import java.io.File

class CodeEditorActivity : AppCompatActivity() {
    private lateinit var b: ActivityCodeEditorBinding
    private lateinit var file: File
    private var loading = false

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityCodeEditorBinding.inflate(layoutInflater)
        setContentView(b.root)
        val path = intent.getStringExtra("path") ?: run { finish(); return }
        file = File(path)

        b.tvFileName.text = file.name
        b.fabBack.setOnClickListener { save(); finish() }
        b.btnSave.setOnClickListener { save() }

        b.editor.textSize = Prefs(this).editorFontSize.toFloat()
        val lang = CodeHighlighter.languageFromName(file.name)
        b.tvLang.text = lang.uppercase()

        if (file.exists()) {
            loading = true
            b.editor.setText(file.readText())
            loading = false
        }
        b.editor.setTextKeepState(CodeHighlighter.highlight(b.editor.text ?: "", lang))

        b.editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, bb: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, bb: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (loading) return
                val cursor = b.editor.selectionStart
                loading = true
                b.editor.setTextKeepState(CodeHighlighter.highlight(s ?: "", lang))
                b.editor.setSelection(cursor.coerceAtMost(b.editor.text?.length ?: 0))
                loading = false
            }
        })
    }

    private fun save() {
        try {
            file.writeText(b.editor.text?.toString() ?: "")
            Toast.makeText(this, "Tersimpan", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}