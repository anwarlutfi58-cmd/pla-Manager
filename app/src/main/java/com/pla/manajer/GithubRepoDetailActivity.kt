package com.pla.manajer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pla.manajer.util.GithubApiHelper
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class GithubRepoDetailActivity : AppCompatActivity() {
    
    private lateinit var token: String
    private var repoName: String = ""
    private var repoFullName: String = ""
    private var repoOwner: String = ""
    private var isRepoPrivate: Boolean = false
    private var repoHtmlUrl: String = ""

    // Launcher untuk Pindah Arah (Upload File dari HP)
    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadFileToGithub(uri)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_github_repo_detail)

        token = getSharedPreferences("PlaPrefs", MODE_PRIVATE).getString("github_token", "") ?: ""

        // Ambil data dari Intent
        repoName = intent.getStringExtra("repo_name") ?: ""
        repoFullName = intent.getStringExtra("repo_full_name") ?: ""
        isRepoPrivate = intent.getBooleanExtra("repo_private", false)
        repoHtmlUrl = intent.getStringExtra("repo_url") ?: ""
        repoOwner = repoFullName.split("/").firstOrNull() ?: ""

        findViewById<TextView>(R.id.tvRepoName).text = "Repo: $repoName"
        findViewById<TextView>(R.id.tvRepoStatus).text = 
            "Status: ${if (isRepoPrivate) "Privat" else "Publik"}\nNama lengkap: $repoFullName"

        setupButtons()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        
        // Menu 1-24
        findViewById<Button>(R.id.btn1).setOnClickListener { showSimpleToast("Membuka daftar berkas...") }
        findViewById<Button>(R.id.btn2).setOnClickListener { showSimpleToast("Form ketik teks akan dibuka") }
        
        // --- PINDAH ARAH (UPLOAD BERKAS) ---
        findViewById<Button>(R.id.btn3).setOnClickListener { pickFileLauncher.launch("*/*") }

        findViewById<Button>(R.id.btn4).setOnClickListener { showSimpleToast("Memulai proses Build APK...") }
        findViewById<Button>(R.id.btn5).setOnClickListener { showSimpleToast("Mengecek status Build...") }
        findViewById<Button>(R.id.btn6).setOnClickListener { showSimpleToast("Membuka riwayat Build...") }
        findViewById<Button>(R.id.btn7).setOnClickListener { showSimpleToast("Mengunduh APK...") }
        findViewById<Button>(R.id.btn8).setOnClickListener { showSimpleToast("Membuka workflow...") }
        findViewById<Button>(R.id.btn9).setOnClickListener { showSimpleToast("Menghapus artefak...") }
        findViewById<Button>(R.id.btn10).setOnClickListener { showSimpleToast("Fitur keamanan APK...") }
        findViewById<Button>(R.id.btn11).setOnClickListener { showSimpleToast("Mengunci tanda tangan...") }
        findViewById<Button>(R.id.btn12).setOnClickListener { showSimpleToast("Membuat rilis baru...") }
        findViewById<Button>(R.id.btn13).setOnClickListener { showSimpleToast("Daftar rilis...") }
        findViewById<Button>(R.id.btn14).setOnClickListener { showSimpleToast("Melihat riwayat commit...") }
        findViewById<Button>(R.id.btn15).setOnClickListener { showSimpleToast("Secrets dan variables...") }
        findViewById<Button>(R.id.btn16).setOnClickListener { showSimpleToast("Info repositori...") }

        // 17. Unduh Repositori (ZIP)
        findViewById<Button>(R.id.btn17).setOnClickListener {
            val url = "https://github.com/$repoFullName/archive/refs/heads/main.zip"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        // 18. Ubah Status Privasi
        findViewById<Button>(R.id.btn18).setOnClickListener { showPrivacyDialog() }

        // 19. Ganti Nama Repositori
        findViewById<Button>(R.id.btn19).setOnClickListener { showRenameDialog() }

        // 20. Salin Tautan
        findViewById<Button>(R.id.btn20).setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Repo URL", repoHtmlUrl))
            Toast.makeText(this, "Tautan disalin", Toast.LENGTH_SHORT).show()
        }

        // 21. Bagikan Tautan
        findViewById<Button>(R.id.btn21).setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, repoHtmlUrl)
            }
            startActivity(Intent.createChooser(share, "Bagikan via"))
        }

        // 22. Situs Web (GitHub Pages)
        findViewById<Button>(R.id.btn22).setOnClickListener {
            val pagesUrl = if (repoName.endsWith(".github.io")) "https://$repoName" else "https://$repoOwner.github.io/$repoName"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pagesUrl)))
        }

        // 23. Buka di Browser
        findViewById<Button>(R.id.btn23).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoHtmlUrl)))
        }

        // 24. Hapus Repositori
        findViewById<Button>(R.id.btn24).setOnClickListener { showDeleteConfirm() }
    }

    private fun showSimpleToast(pesan: String) {
        Toast.makeText(this, pesan, Toast.LENGTH_SHORT).show()
    }

    // --- LOGIKA PINDAH ARAH (UPLOAD FILE) ---
    private fun uploadFileToGithub(uri: Uri) {
        Toast.makeText(this, "Menyiapkan file untuk diunggah...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                // 1. Ambil nama file asli
                var fileName = "file_${System.currentTimeMillis()}"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst()) fileName = cursor.getString(nameIndex)
                }

                // 2. Salin ke cache
                val tempFile = File(cacheDir, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }

                // 3. Upload ke Github
                val success = GithubApiHelper.uploadFileToRepo(token, repoOwner, repoName, fileName, tempFile)
                
                if (success) {
                    Toast.makeText(this@GithubRepoDetailActivity, "Berhasil! File '$fileName' sudah diunggah.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@GithubRepoDetailActivity, "Gagal mengunggah file.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@GithubRepoDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPrivacyDialog() {
        val status = if (isRepoPrivate) "Privat" else "Publik"
        AlertDialog.Builder(this)
            .setTitle("Ubah Status Privasi")
            .setMessage("Saat ini: $status. Ubah status?")
            .setPositiveButton("Ya, Ubah") { _, _ ->
                lifecycleScope.launch {
                    val success = GithubApiHelper.changePrivacy(token, repoOwner, repoName, !isRepoPrivate)
                    if (success) {
                        Toast.makeText(this@GithubRepoDetailActivity, "Status berhasil diubah", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@GithubRepoDetailActivity, "Gagal mengubah status", Toast.LENGTH_SHORT).show()
                    }
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun showRenameDialog() {
        val input = EditText(this).apply { setText(repoName) }
        AlertDialog.Builder(this)
            .setTitle("Ganti Nama Repositori")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != repoName) {
                    lifecycleScope.launch {
                        val success = GithubApiHelper.renameRepo(token, repoOwner, repoName, newName)
                        if (success) {
                            Toast.makeText(this@GithubRepoDetailActivity, "Berhasil diganti menjadi $newName", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this@GithubRepoDetailActivity, "Gagal mengganti nama", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun showDeleteConfirm() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Repositori?")
            .setMessage("Apakah Anda yakin ingin menghapus $repoName secara permanen?")
            .setPositiveButton("HAPUS") { _, _ ->
                lifecycleScope.launch {
                    val success = GithubApiHelper.deleteRepo(token, repoOwner, repoName)
                    if (success) {
                        Toast.makeText(this@GithubRepoDetailActivity, "Repositori berhasil dihapus", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@GithubRepoDetailActivity, "Gagal menghapus repositori", Toast.LENGTH_SHORT).show()
                    }
                }
            }.setNegativeButton("Batal", null).show()
    }
}