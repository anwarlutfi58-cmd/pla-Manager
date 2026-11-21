package com.pla.manajer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class GithubBuilderActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_github_builder)

        val btn1 = findViewById<Button>(R.id.btnMenu1)
        val btn2 = findViewById<Button>(R.id.btnMenu2)
        val btn7 = findViewById<Button>(R.id.btnMenu7)
        val btn10 = findViewById<Button>(R.id.btnMenu10)
        val btnBack = findViewById<Button>(R.id.btnBack)

        // Buka Web untuk buat token (Poin 7)
        btn7.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens")))
        }

        // Keluar Akun (Hapus Token)
        btn10.setOnClickListener {
            val prefs = getSharedPreferences("PlaPrefs", MODE_PRIVATE)
            prefs.edit().remove("github_token").apply()
            Toast.makeText(this, "Token berhasil dihapus (Logout)", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnBack.setOnClickListener { finish() }

        // Fitur GitHub API akan diimplementasikan di sini
        btn1.setOnClickListener { Toast.makeText(this, "Fitur Repositori Saya membutuhkan Token GitHub", Toast.LENGTH_SHORT).show() }
        btn2.setOnClickListener { Toast.makeText(this, "Fitur Pencarian Repositori", Toast.LENGTH_SHORT).show() }
    }
}