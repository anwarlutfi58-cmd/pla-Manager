package com.pla.manajer

import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pla.agent.PlAiEngine
import com.pla.manajer.databinding.ActivityProjectManagerBinding
import com.pla.manajer.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProjectManagerActivity : AppCompatActivity() {
    private lateinit var b: ActivityProjectManagerBinding

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityProjectManagerBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.fabBack.setOnClickListener { finish() }
        b.btnCreate.setOnClickListener {
            val name  = b.etProjectName.text.toString().trim()
            val paths = b.etPath.text.toString().trim()
            if (name.isEmpty() || paths.isEmpty()) {
                Toast.makeText(this, "Isi nama proyek & path", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            createProject(name, paths)
        }

        b.btnAndroid.setOnClickListener {
            b.etPath.setText(PlAiEngine.suggestStructure("android").joinToString("\n"))
        }
        b.btnWeb.setOnClickListener {
            b.etPath.setText(PlAiEngine.suggestStructure("web").joinToString("\n"))
        }
        b.btnPython.setOnClickListener {
            b.etPath.setText(PlAiEngine.suggestStructure("python").joinToString("\n"))
        }
    }

    private fun createProject(name: String, paths: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val root = File(Environment.getExternalStorageDirectory(), "PlaProjects/$name")
                root.mkdirs()
                paths.lines().filter { it.isNotBlank() }.forEach { p ->
                    FileUtils.createSmartPath(root, p)
                }
            }
            Toast.makeText(this@ProjectManagerActivity, "Proyek '$name' dibuat!", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}