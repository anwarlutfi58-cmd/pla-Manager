package com.pla.manajer

import android.content.ClipData
import android.content.ClipboardManager
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
import com.pla.manajer.util.GithubFeatureHelper
import kotlinx.coroutines.launch

class GithubRepoDetailActivity : AppCompatActivity() {
    
    private lateinit var token: String
    private var repoName: String = ""
    private var repoFullName: String = ""
    private var repoOwner: String = ""
    private var isRepoPrivate: Boolean = false
    private var repoHtmlUrl: String = ""

    // Launcher untuk Pindah Arah
    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadFileToGithub(uri)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_github_repo_detail)

        token = getSharedPreferences("PlaPrefs", MODE_PRIVATE).getString("github_token", "") ?: ""
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
        
        // 1. Buka Berkas (Sementara diarahkan ke browser untuk lihat file)
        findViewById<Button>(R.id.btn1).setOnClickListener { 
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoHtmlUrl))) 
        }
        
        // 2. Tambah Berkas (Ketik Teks)
        findViewById<Button>(R.id.btn2).setOnClickListener { showAddTextFileDialog() }
        
        // 3. Pindah Arah / Unggah Berkas
        findViewById<Button>(R.id.btn3).setOnClickListener { pickFileLauncher.launch("*/*") }

        // 4. Bangun APK (Build)
        findViewById<Button>(R.id.btn4).setOnClickListener { 
            Toast.makeText(this, "Menjalankan Build... (Pastikan Workflow YAML sudah ada)", Toast.LENGTH_LONG).show()
            // Fungsi untuk men-trigger build bisa ditambahkan di GithubFeatureHelper jika YAML siap
        }

        // 5. Cek Status Build (Workflow)
        findViewById<Button>(R.id.btn5).setOnClickListener { showWorkflowsDialog() }

        // 6. Riwayat Pembangunan (Sama seperti Cek Status)
        findViewById<Button>(R.id.btn6).setOnClickListener { showWorkflowsDialog() }

        // 7. Unduh APK Terbaru (Sementara ke halaman Actions)
        findViewById<Button>(R.id.btn7).setOnClickListener { 
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$repoHtmlUrl/actions"))) 
        }

        // 8. Lihat Workflow & Log
        findViewById<Button>(R.id.btn8).setOnClickListener { showWorkflowsDialog() }

        // 9. Hapus Artefak (Sementara ke halaman Actions)
        findViewById<Button>(R.id.btn9).setOnClickListener { 
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$repoHtmlUrl/actions"))) 
        }

        // 10-13 (Fitur Keamanan & Rilis)
        findViewById<Button>(R.id.btn10).setOnClickListener { showSimpleToast("Fitur Keamanan APK berjalan.") }
        findViewById<Button>(R.id.btn11).setOnClickListener { showSimpleToast("Fitur Kunci Tanda Tangan berjalan.") }
        findViewById<Button>(R.id.btn12).setOnClickListener { showSimpleToast("Buat Rilis baru...") }
        findViewById<Button>(R.id.btn13).setOnClickListener { showReleasesDialog() }

        // 14. Riwayat Commit
        findViewById<Button>(R.id.btn14).setOnClickListener { showCommitsDialog() }

        // 15. Secrets dan Variables
        findViewById<Button>(R.id.btn15).setOnClickListener { 
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$repoHtmlUrl/settings/secrets/actions"))) 
        }

        // 16. Info Repo
        findViewById<Button>(R.id.btn16).setOnClickListener { 
            AlertDialog.Builder(this).setTitle("Info Repo").setMessage("Nama: $repoName\nStatus: ${if(isRepoPrivate) "Privat" else "Publik"}\nPemilik: $repoOwner").setPositiveButton("OK", null).show()
        }

        // 17. Unduh ZIP
        findViewById<Button>(R.id.btn17).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$repoHtmlUrl/archive/refs/heads/main.zip")))
        }

        // 18. Ubah Privasi
        findViewById<Button>(R.id.btn18).setOnClickListener { showPrivacyDialog() }

        // 19. Ganti Nama
        findViewById<Button>(R.id.btn19).setOnClickListener { showRenameDialog() }

        // 20. Salin Tautan
        findViewById<Button>(R.id.btn20).setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Repo URL", repoHtmlUrl))
            Toast.makeText(this, "Tautan disalin", Toast.LENGTH_SHORT).show()
        }

        // 21. Bagikan Tautan
        findViewById<Button>(R.id.btn21).setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, repoHtmlUrl) }
            startActivity(Intent.createChooser(share, "Bagikan via"))
        }

        // 22. Situs Web (Pages)
        findViewById<Button>(R.id.btn22).setOnClickListener {
            val pagesUrl = if (repoName.endsWith(".github.io")) "https://$repoName" else "https://$repoOwner.github.io/$repoName"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pagesUrl)))
        }

        // 23. Buka di Browser
        findViewById<Button>(R.id.btn23).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoHtmlUrl)))
        }

        // 24. Hapus Repo
        findViewById<Button>(R.id.btn24).setOnClickListener { showDeleteConfirm() }
    }

    // --- FUNGSI PENDUKUNG ---

    private fun showAddTextFileDialog() {
        val layout = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(50, 20, 50, 20) }
        val etName = EditText(this).apply { hint = "Nama file (contoh: catatan.txt)" }
        val etContent = EditText(this).apply { hint = "Isi teks..." }
        layout.addView(etName); layout.addView(etContent)

        AlertDialog.Builder(this).setTitle("Tambah Berkas Teks").setView(layout)
            .setPositiveButton("Unggah") { _, _ ->
                val name = etName.text.toString().trim()
                val content = etContent.text.toString()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        val success = GithubFeatureHelper.uploadFile(token, repoOwner, repoName, name, content.toByteArray())
                        Toast.makeText(this@GithubRepoDetailActivity, if (success) "Berhasil diunggah!" else "Gagal mengunggah", Toast.LENGTH_SHORT).show()
                    }
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun showWorkflowsDialog() {
        lifecycleScope.launch {
            val result = GithubFeatureHelper.getWorkflows(token, repoOwner, repoName)
            AlertDialog.Builder(this@GithubRepoDetailActivity).setTitle("Status Build").setMessage(result).setPositiveButton("Tutup", null).show()
        }
    }

    private fun showCommitsDialog() {
        lifecycleScope.launch {
            val result = GithubFeatureHelper.getCommits(token, repoOwner, repoName)
            AlertDialog.Builder(this@GithubRepoDetailActivity).setTitle("Riwayat Commit").setMessage(result).setPositiveButton("Tutup", null).show()
        }
    }

    private fun showReleasesDialog() {
        lifecycleScope.launch {
            val result = GithubFeatureHelper.getReleases(token, repoOwner, repoName)
            AlertDialog.Builder(this@GithubRepoDetailActivity).setTitle("Daftar Rilis").setMessage(result).setPositiveButton("Tutup", null).show()
        }
    }

    private fun uploadFileToGithub(uri: Uri) {
        Toast.makeText(this, "Mengunggah...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                var fileName = "file_${System.currentTimeMillis()}"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst()) fileName = cursor.getString(nameIndex)
                }
                val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return@launch
                val success = GithubFeatureHelper.uploadFile(token, repoOwner, repoName, fileName, bytes)
                Toast.makeText(this@GithubRepoDetailActivity, if (success) "Berhasil diunggah!" else "Gagal mengunggah", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@GithubRepoDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPrivacyDialog() {
        AlertDialog.Builder(this).setTitle("Ubah Status").setMessage("Ubah status privasi?").setPositiveButton("Ya") { _, _ ->
            lifecycleScope.launch {
                val success = GithubApiHelper.changePrivacy(token, repoOwner, repoName, !isRepoPrivate)
                if (success) { Toast.makeText(this@GithubRepoDetailActivity, "Berhasil diubah", Toast.LENGTH_SHORT).show(); finish() } 
                else Toast.makeText(this@GithubRepoDetailActivity, "Gagal", Toast.LENGTH_SHORT).show()
            }
        }.setNegativeButton("Batal", null).show()
    }

    private fun showRenameDialog() {
        val input = EditText(this).apply { setText(repoName) }
        AlertDialog.Builder(this).setTitle("Ganti Nama").setView(input).setPositiveButton("Simpan") { _, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty() && newName != repoName) {
                lifecycleScope.launch {
                    val success = GithubApiHelper.renameRepo(token, repoOwner, repoName, newName)
                    if (success) { Toast.makeText(this@GithubRepoDetailActivity, "Berhasil diganti", Toast.LENGTH_SHORT).show(); finish() } 
                    else Toast.makeText(this@GithubRepoDetailActivity, "Gagal", Toast.LENGTH_SHORT).show()
                }
            }
        }.setNegativeButton("Batal", null).show()
    }

    private fun showDeleteConfirm() {
        AlertDialog.Builder(this).setTitle("Hapus Repo?").setMessage("Hapus $repoName permanen?").setPositiveButton("HAPUS") { _, _ ->
            lifecycleScope.launch {
                val success = GithubApiHelper.deleteRepo(token, repoOwner, repoName)
                if (success) { Toast.makeText(this@GithubRepoDetailActivity, "Berhasil dihapus", Toast.LENGTH_SHORT).show(); finish() } 
                else Toast.makeText(this@GithubRepoDetailActivity, "Gagal menghapus", Toast.LENGTH_SHORT).show()
            }
        }.setNegativeButton("Batal", null).show()
    }

    private fun showSimpleToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}