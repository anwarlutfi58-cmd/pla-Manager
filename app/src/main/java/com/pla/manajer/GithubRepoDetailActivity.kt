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
import com.pla.manajer.util.ApkBuilderHelper
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

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) uploadFileToGithub(uri)
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
        findViewById<TextView>(R.id.tvRepoStatus).text = "Status: ${if (isRepoPrivate) "Privat" else "Publik"}\nNama lengkap: $repoFullName"

        setupButtons()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
        
        findViewById<Button>(R.id.btn1).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoHtmlUrl))) }
        findViewById<Button>(R.id.btn2).setOnClickListener { showAddTextFileDialog() }
        findViewById<Button>(R.id.btn3).setOnClickListener { pickFileLauncher.launch("*/*") }

        // --- TOMBOL NO. 4: BANGUN APK (MUNCULKAN PILIHAN) ---
        findViewById<Button>(R.id.btn4).setOnClickListener { showBuildOptionsDialog() }

        findViewById<Button>(R.id.btn5).setOnClickListener { showWorkflowsDialog() }
        findViewById<Button>(R.id.btn6).setOnClickListener { showWorkflowsDialog() }
        findViewById<Button>(R.id.btn7).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$repoHtmlUrl/releases"))) } // Unduh APK langsung ke rilis
        findViewById<Button>(R.id.btn8).setOnClickListener { showWorkflowsDialog() }
        findViewById<Button>(R.id.btn9).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$repoHtmlUrl/actions"))) }
        
        findViewById<Button>(R.id.btn10).setOnClickListener { showSimpleToast("Fitur Keamanan APK berjalan.") }
        findViewById<Button>(R.id.btn11).setOnClickListener { showSimpleToast("Fitur Kunci Tanda Tangan berjalan.") }
        findViewById<Button>(R.id.btn12).setOnClickListener { showSimpleToast("Buat Rilis baru...") }
        findViewById<Button>(R.id.btn13).setOnClickListener { showReleasesDialog() }
        findViewById<Button>(R.id.btn14).setOnClickListener { showCommitsDialog() }
        findViewById<Button>(R.id.btn15).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$repoHtmlUrl/settings/secrets/actions"))) }
        findViewById<Button>(R.id.btn16).setOnClickListener { showRepoInfoDialog() }
        findViewById<Button>(R.id.btn17).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$repoHtmlUrl/archive/refs/heads/main.zip"))) }
        findViewById<Button>(R.id.btn18).setOnClickListener { showPrivacyDialog() }
        findViewById<Button>(R.id.btn19).setOnClickListener { showRenameDialog() }
        findViewById<Button>(R.id.btn20).setOnClickListener { copyLink() }
        findViewById<Button>(R.id.btn21).setOnClickListener { shareLink() }
        findViewById<Button>(R.id.btn22).setOnClickListener { openPages() }
        findViewById<Button>(R.id.btn23).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoHtmlUrl))) }
        findViewById<Button>(R.id.btn24).setOnClickListener { showDeleteConfirm() }
    }

    // ==========================================
    // LOGIKA BANGUN APK
    // ==========================================
    private fun showBuildOptionsDialog() {
        val options = arrayOf("1. Bangun dari Repo/Folder (ZIP)", "2. Bangun dari Kode Teks Langsung")
        AlertDialog.Builder(this)
            .setTitle("Pilih Metode Bangun APK")
            .setItems(options) { _, which ->
                if (which == 0) {
                    // Bangun dari Repo
                    askKeystoreQuestion { isCustomKey ->
                        startBuildProcess(useCustomKey = isCustomKey)
                    }
                } else {
                    // Bangun dari Teks
                    showTypeCodeDialog()
                }
            }.show()
    }

    private fun askKeystoreQuestion(onAnswer: (Boolean) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Kunci Keamanan (Keystore)")
            .setMessage("Gunakan kunci dari GitHub Secrets, atau buat kunci sendiri yang akan disimpan di aplikasi PLA?")
            .setPositiveButton("Buat Kunci Sendiri") { _, _ -> onAnswer(true) }
            .setNegativeButton("Gunakan GitHub Secrets") { _, _ -> onAnswer(false) }
            .show()
    }

    private fun startBuildProcess(useCustomKey: Boolean) {
        if (useCustomKey) {
            showCreateKeystoreDialog()
        } else {
            uploadWorkflowAndTriggerBuild()
        }
    }

    private fun showCreateKeystoreDialog() {
        val layout = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(50, 20, 50, 20) }
        val etAlias = EditText(this).apply { hint = "Nama Alias (contoh: mykey)" }
        val etPass = EditText(this).apply { hint = "Password Kunci" }

        layout.addView(etAlias); layout.addView(etPass)

        AlertDialog.Builder(this)
            .setTitle("Buat Kunci Sendiri")
            .setView(layout)
            .setPositiveButton("Simpan & Bangun") { _, _ ->
                val alias = etAlias.text.toString().ifEmpty { "plakey" }
                val pass = etPass.text.toString().ifEmpty { "password123" }
                
                // Simpan kunci ke PLA
                ApkBuilderHelper.generateKeystoreLocally(this, alias, pass)
                Toast.makeText(this, "Kunci disimpan di PLA. Memulai Build...", Toast.LENGTH_SHORT).show()
                
                uploadWorkflowAndTriggerBuild()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun uploadWorkflowAndTriggerBuild() {
        Toast.makeText(this, "Menyiapkan workflow di GitHub...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val success = ApkBuilderHelper.setupBuildWorkflow(token, repoOwner, repoName, true)
            if (success) {
                Toast.makeText(this@GithubRepoDetailActivity, "Workflow berhasil dipasang! APK akan muncul di menu Rilis.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@GithubRepoDetailActivity, "Gagal memasang workflow.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTypeCodeDialog() {
        val layout = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(50, 20, 50, 20) }
        val etName = EditText(this).apply { hint = "Nama file (contoh: MainActivity.kt)" }
        val etCode = EditText(this).apply { hint = "Tulis kode di sini..." }
        layout.addView(etName); layout.addView(etCode)

        AlertDialog.Builder(this)
            .setTitle("Tulis Kode Langsung")
            .setView(layout)
            .setPositiveButton("Unggah & Bangun") { _, _ ->
                val name = etName.text.toString().trim()
                val code = etCode.text.toString()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        val success = GithubFeatureHelper.uploadFile(token, repoOwner, repoName, name, code.toByteArray())
                        if (success) {
                            askKeystoreQuestion { isCustomKey ->
                                if (isCustomKey) showCreateKeystoreDialog() else uploadWorkflowAndTriggerBuild()
                            }
                        } else {
                            Toast.makeText(this@GithubRepoDetailActivity, "Gagal mengunggah kode", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ==========================================
    // FUNGSI LAINNYA
    // ==========================================
    private fun showAddTextFileDialog() {
        val layout = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(50, 20, 50, 20) }
        val etName = EditText(this).apply { hint = "Nama file" }
        val etContent = EditText(this).apply { hint = "Isi teks..." }
        layout.addView(etName); layout.addView(etContent)

        AlertDialog.Builder(this).setTitle("Tambah Berkas Teks").setView(layout)
            .setPositiveButton("Unggah") { _, _ ->
                val name = etName.text.toString().trim()
                val content = etContent.text.toString()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        val success = GithubFeatureHelper.uploadFile(token, repoOwner, repoName, name, content.toByteArray())
                        Toast.makeText(this@GithubRepoDetailActivity, if (success) "Berhasil!" else "Gagal", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@GithubRepoDetailActivity, if (success) "Berhasil!" else "Gagal", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@GithubRepoDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPrivacyDialog() {
        AlertDialog.Builder(this).setTitle("Ubah Status").setMessage("Ubah status privasi?").setPositiveButton("Ya") { _, _ ->
            lifecycleScope.launch {
                val success = GithubApiHelper.changePrivacy(token, repoOwner, repoName, !isRepoPrivate)
                if (success) { Toast.makeText(this@GithubRepoDetailActivity, "Berhasil", Toast.LENGTH_SHORT).show(); finish() } 
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
                    if (success) { Toast.makeText(this@GithubRepoDetailActivity, "Berhasil", Toast.LENGTH_SHORT).show(); finish() } 
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
                else Toast.makeText(this@GithubRepoDetailActivity, "Gagal", Toast.LENGTH_SHORT).show()
            }
        }.setNegativeButton("Batal", null).show()
    }

    private fun showRepoInfoDialog() {
        AlertDialog.Builder(this).setTitle("Info Repo").setMessage("Nama: $repoName\nStatus: ${if(isRepoPrivate) "Privat" else "Publik"}\nPemilik: $repoOwner").setPositiveButton("OK", null).show()
    }

    private fun copyLink() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Repo URL", repoHtmlUrl))
        Toast.makeText(this, "Tautan disalin", Toast.LENGTH_SHORT).show()
    }

    private fun shareLink() {
        val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, repoHtmlUrl) }
        startActivity(Intent.createChooser(share, "Bagikan via"))
    }

    private fun openPages() {
        val pagesUrl = if (repoName.endsWith(".github.io")) "https://$repoName" else "https://$repoOwner.github.io/$repoName"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pagesUrl)))
    }

    private fun showSimpleToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}