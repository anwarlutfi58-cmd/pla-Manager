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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

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

    // Untuk menu 12: pilih APK yang akan diunggah ke rilis
    private val pickApkForReleaseLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) createReleaseAndUpload(uri)
    }
    private var pendingReleaseTag = ""
    private var pendingReleaseName = ""
    private var pendingReleaseBody = ""

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

        // 1. BUKA BERKAS
        findViewById<Button>(R.id.btn1).setOnClickListener {
            startActivity(Intent(this, GithubFileListActivity::class.java).apply {
                putExtra("owner", repoOwner)
                putExtra("repo", repoName)
            })
        }

        // 2. TAMBAH BERKAS TEKS
        findViewById<Button>(R.id.btn2).setOnClickListener { showAddTextFileDialog() }

        // 3. UNGGAH FILE DARI HP
        findViewById<Button>(R.id.btn3).setOnClickListener { pickFileLauncher.launch("*/*") }

        // 4. BANGUN APK
        findViewById<Button>(R.id.btn4).setOnClickListener { showBuildMethodDialog() }

        // 5. CEK STATUS PEMBANGUNAN (daftar run terbaru)
        findViewById<Button>(R.id.btn5).setOnClickListener { showLatestRunsDialog() }

        // 6. RIWAYAT PEMBANGUNAN (semua run)
        findViewById<Button>(R.id.btn6).setOnClickListener { showAllRunsDialog() }

        // 7. UNDUH APK TERBARU (langsung download dari artefak)
        findViewById<Button>(R.id.btn7).setOnClickListener { downloadLatestApk() }

        // 8. LIHAT WORKFLOW & LOG (log build terakhir)
        findViewById<Button>(R.id.btn8).setOnClickListener { showBuildLog() }

        // 9. HAPUS ARTEFAK (daftar artefak + hapus)
        findViewById<Button>(R.id.btn9).setOnClickListener { showArtifactsDialog() }

        // 10. LINDUNGI TANDA TANGAN ULANG (info + panduan)
        findViewById<Button>(R.id.btn10).setOnClickListener { showSignatureProtectionInfo() }

        // 11. KUNCI TERIKAT TANDA TANGAN (buat/pakai keystore)
        findViewById<Button>(R.id.btn11).setOnClickListener { askKeystoreQuestion("MANUAL") }

        // 12. BUAT RILIS & UNGGAH APK
        findViewById<Button>(R.id.btn12).setOnClickListener { showCreateReleaseDialog() }

        // 13. DAFTAR RILIS & HAPUS RILIS
        findViewById<Button>(R.id.btn13).setOnClickListener { showReleasesManageDialog() }

        // 14. RIWAYAT COMMIT (lihat & kembalikan)
        findViewById<Button>(R.id.btn14).setOnClickListener { showCommitsManageDialog() }

        // 15. SECRETS DAN VARIABLES
        findViewById<Button>(R.id.btn15).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$repoHtmlUrl/settings/secrets/actions")))
        }

        // 16. INFO REPOSITORI (lengkap dari API)
        findViewById<Button>(R.id.btn16).setOnClickListener { showRepoFullInfo() }

        // 17. UNDUH REPOSITORI (ZIP)
        findViewById<Button>(R.id.btn17).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$repoHtmlUrl/archive/refs/heads/main.zip")))
        }

        // 18. UBAH STATUS PRIVASI
        findViewById<Button>(R.id.btn18).setOnClickListener { showPrivacyDialog() }

        // 19. GANTI NAMA REPOSITORI
        findViewById<Button>(R.id.btn19).setOnClickListener { showRenameDialog() }

        // 20. SALIN TAUTAN
        findViewById<Button>(R.id.btn20).setOnClickListener { copyLink() }

        // 21. BAGIKAN TAUTAN
        findViewById<Button>(R.id.btn21).setOnClickListener { shareLink() }

        // 22. SITUS WEB (GITHUB PAGES)
        findViewById<Button>(R.id.btn22).setOnClickListener { openPages() }

        // 23. BUKA DI BROWSER
        findViewById<Button>(R.id.btn23).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repoHtmlUrl)))
        }

        // 24. HAPUS REPOSITORI
        findViewById<Button>(R.id.btn24).setOnClickListener { showDeleteConfirm() }
    }

    // ==========================================
    // MENU 5 & 6: STATUS & RIWAYAT BUILD
    // ==========================================
    private fun showLatestRunsDialog() {
        Toast.makeText(this, "Memuat status...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val runs = GithubApiHelper.getWorkflowRuns(token, repoOwner, repoName)
            if (runs.isNullOrEmpty()) {
                Toast.makeText(this@GithubRepoDetailActivity, "Belum ada build.", Toast.LENGTH_SHORT).show()
            } else {
                val latest = runs.take(3)
                val display = latest.mapIndexed { i, r ->
                    "${i + 1}. ${r.name}\n   Status: ${r.status} | Hasil: ${r.conclusion ?: "berjalan"}"
                }.toTypedArray()
                AlertDialog.Builder(this@GithubRepoDetailActivity)
                    .setTitle("Status Build Terbaru")
                    .setItems(display) { _, which ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest[which].htmlUrl)))
                    }
                    .setPositiveButton("Tutup", null).show()
            }
        }
    }

    private fun showAllRunsDialog() {
        Toast.makeText(this, "Memuat riwayat...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val runs = GithubApiHelper.getWorkflowRuns(token, repoOwner, repoName)
            if (runs.isNullOrEmpty()) {
                Toast.makeText(this@GithubRepoDetailActivity, "Belum ada build.", Toast.LENGTH_SHORT).show()
            } else {
                val display = runs.mapIndexed { i, r ->
                    "${i + 1}. ${r.name} - ${r.conclusion ?: r.status}"
                }.toTypedArray()
                AlertDialog.Builder(this@GithubRepoDetailActivity)
                    .setTitle("Riwayat Pembangunan (${runs.size})")
                    .setItems(display) { _, which ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(runs[which].htmlUrl)))
                    }
                    .setPositiveButton("Tutup", null).show()
            }
        }
    }

    // ==========================================
    // MENU 7: UNDUH APK TERBARU (LANGSUNG DOWNLOAD)
    // ==========================================
    private fun downloadLatestApk() {
        Toast.makeText(this, "Mencari APK terbaru...", Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            val artifacts = GithubApiHelper.getArtifacts(token, repoOwner, repoName)
            if (artifacts.isNullOrEmpty()) {
                Toast.makeText(this@GithubRepoDetailActivity, "Belum ada artefak. Bangun APK dulu (menu 4).", Toast.LENGTH_LONG).show()
                return@launch
            }
            val apkArtifact = artifacts.firstOrNull { !it.expired && it.name.contains("apk", true) }
                ?: artifacts.firstOrNull { !it.expired }
            if (apkArtifact == null || apkArtifact.downloadUrl.isEmpty()) {
                Toast.makeText(this@GithubRepoDetailActivity, "Tidak ada APK yang bisa diunduh.", Toast.LENGTH_LONG).show()
                return@launch
            }
            Toast.makeText(this@GithubRepoDetailActivity, "Mengunduh ${apkArtifact.name}...", Toast.LENGTH_SHORT).show()
            val apkFile = GithubFeatureHelper.downloadArtifactApk(this@GithubRepoDetailActivity, token, apkArtifact.downloadUrl, apkArtifact.name)
            if (apkFile != null) {
                AlertDialog.Builder(this@GithubRepoDetailActivity)
                    .setTitle("APK Berhasil Diunduh!")
                    .setMessage("Lokasi: ${apkFile.absolutePath}\n\nBuka atau instal sekarang?")
                    .setPositiveButton("Instal APK") { _, _ ->
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { startActivity(installIntent) } catch (e: Exception) {
                            Toast.makeText(this@GithubRepoDetailActivity, "Izinkan instal dari sumber tidak dikenal di Pengaturan.", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Tutup", null).show()
            } else {
                Toast.makeText(this@GithubRepoDetailActivity, "Gagal mengunduh APK.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==========================================
    // MENU 8: LOG BUILD TERAKHIR
    // ==========================================
    private fun showBuildLog() {
        Toast.makeText(this, "Mengambil log build...", Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            val log = GithubFeatureHelper.getLatestRunLog(token, repoOwner, repoName)
            AlertDialog.Builder(this@GithubRepoDetailActivity)
                .setTitle("Log Build Terakhir")
                .setMessage(log)
                .setPositiveButton("Tutup", null).show()
        }
    }

    // ==========================================
    // MENU 9: HAPUS ARTEFAK
    // ==========================================
    private fun showArtifactsDialog() {
        Toast.makeText(this, "Memuat artefak...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val artifacts = GithubApiHelper.getArtifacts(token, repoOwner, repoName)
            if (artifacts.isNullOrEmpty()) {
                Toast.makeText(this@GithubRepoDetailActivity, "Tidak ada artefak.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val display = artifacts.mapIndexed { i, a ->
                val sizeKb = a.sizeBytes / 1024
                "${i + 1}. ${a.name} ($sizeKb KB)${if (a.expired) " [KADALUARSA]" else ""}"
            }.toTypedArray()
            AlertDialog.Builder(this@GithubRepoDetailActivity)
                .setTitle("Artefak Build (${artifacts.size}) - Ketuk untuk hapus")
                .setItems(display) { _, which ->
                    val target = artifacts[which]
                    AlertDialog.Builder(this@GithubRepoDetailActivity)
                        .setTitle("Hapus Artefak?")
                        .setMessage("Hapus '${target.name}' permanen?")
                        .setPositiveButton("HAPUS") { _, _ ->
                            lifecycleScope.launch {
                                val ok = GithubApiHelper.deleteArtifact(token, repoOwner, repoName, target.id)
                                Toast.makeText(this@GithubRepoDetailActivity, if (ok) "Berhasil dihapus" else "Gagal menghapus", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Batal", null).show()
                }
                .setPositiveButton("Tutup", null).show()
        }
    }

    // ==========================================
    // MENU 10: INFO PERLINDUNGAN TANDA TANGAN
    // ==========================================
    private fun showSignatureProtectionInfo() {
        val msg = """
            Agar APK tidak bisa ditandatangani ulang oleh orang lain:
            
            1. Simpan kunci (keystore) di GitHub Secrets, JANGAN di repo publik.
            2. Gunakan password kuat untuk keystore.
            3. Jangan pernah membagikan file .jks/.keystore.
            4. Aktifkan "App Signing by Google Play" jika publish ke Play Store.
            
            PLA Manajer menyimpan kunci lokal di folder privat aplikasi (aman).
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Lindungi dari Tanda Tangan Ulang")
            .setMessage(msg)
            .setPositiveButton("Buka Secrets GitHub") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$repoHtmlUrl/settings/secrets/actions")))
            }
            .setNegativeButton("Tutup", null).show()
    }

    // ==========================================
    // MENU 4 & 11: BANGUN APK & KUNCI TANDA TANGAN
    // ==========================================
    private fun showBuildMethodDialog() {
        val options = arrayOf("Bangun dari File ZIP", "Bangun dari Kode yang Sudah Ada")
        AlertDialog.Builder(this)
            .setTitle("Pilih Metode Bangun APK")
            .setItems(options) { _, which ->
                askKeystoreQuestion(if (which == 0) "ZIP" else "KODE")
            }.show()
    }

    private fun askKeystoreQuestion(method: String) {
        AlertDialog.Builder(this)
            .setTitle("Kunci Keamanan (Keystore)")
            .setMessage("Gunakan kunci dari GitHub Secrets, atau buat kunci sendiri yang disimpan di aplikasi PLA?")
            .setPositiveButton("Buat Kunci Sendiri") { _, _ -> showCreateKeystoreDialog(method) }
            .setNegativeButton("Gunakan GitHub Secrets") { _, _ -> uploadWorkflowAndTriggerBuild() }
            .show()
    }

    private fun showCreateKeystoreDialog(method: String) {
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
                ApkBuilderHelper.generateKeystoreLocally(this, alias, pass)
                Toast.makeText(this, "Kunci disimpan. Memulai Build...", Toast.LENGTH_SHORT).show()
                uploadWorkflowAndTriggerBuild()
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun uploadWorkflowAndTriggerBuild() {
        Toast.makeText(this, "Menyiapkan workflow di GitHub...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val success = ApkBuilderHelper.setupBuildWorkflow(token, repoOwner, repoName, true)
            if (success) {
                Toast.makeText(this@GithubRepoDetailActivity, "Workflow dipasang! APK muncul di menu 7 setelah build selesai.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@GithubRepoDetailActivity, "Gagal memasang workflow.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==========================================
    // MENU 12: BUAT RILIS & UNGGAH APK
    // ==========================================
    private fun showCreateReleaseDialog() {
        val layout = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(50, 20, 50, 20) }
        val etTag = EditText(this).apply { hint = "Tag versi (contoh: v1.0.0)" }
        val etName = EditText(this).apply { hint = "Nama rilis (contoh: Rilis Perdana)" }
        val etBody = EditText(this).apply { hint = "Catatan rilis (opsional)" }
        layout.addView(etTag); layout.addView(etName); layout.addView(etBody)

        AlertDialog.Builder(this)
            .setTitle("Buat Rilis Baru")
            .setView(layout)
            .setPositiveButton("Lanjut: Pilih APK") { _, _ ->
                val tag = etTag.text.toString().trim()
                val name = etName.text.toString().trim().ifEmpty { tag }
                val body = etBody.text.toString()
                if (tag.isEmpty()) {
                    Toast.makeText(this, "Tag versi wajib diisi", Toast.LENGTH_SHORT).show()
                } else {
                    pendingReleaseTag = tag
                    pendingReleaseName = name
                    pendingReleaseBody = body
                    pickApkForReleaseLauncher.launch("application/vnd.android.package-archive")
                }
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun createReleaseAndUpload(apkUri: Uri) {
        Toast.makeText(this, "Membuat rilis & mengunggah APK...", Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            val releaseId = GithubApiHelper.createRelease(token, repoOwner, repoName, pendingReleaseTag, pendingReleaseName, pendingReleaseBody)
            if (releaseId == null) {
                Toast.makeText(this@GithubRepoDetailActivity, "Gagal membuat rilis.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val bytes = contentResolver.openInputStream(apkUri)?.readBytes()
            if (bytes == null) {
                Toast.makeText(this@GithubRepoDetailActivity, "Gagal membaca APK.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val fileName = "pla-${pendingReleaseTag}.apk"
            val ok = GithubApiHelper.uploadReleaseAsset(token, repoOwner, repoName, releaseId, fileName, bytes)
            if (ok) {
                Toast.makeText(this@GithubRepoDetailActivity, "✅ Rilis $pendingReleaseTag dibuat & APK diunggah!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@GithubRepoDetailActivity, "Rilis dibuat tapi gagal unggah APK.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==========================================
    // MENU 13: DAFTAR RILIS & HAPUS RILIS
    // ==========================================
    private fun showReleasesManageDialog() {
        Toast.makeText(this, "Memuat rilis...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val releases = GithubApiHelper.getReleasesFull(token, repoOwner, repoName)
            if (releases.isNullOrEmpty()) {
                Toast.makeText(this@GithubRepoDetailActivity, "Belum ada rilis.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val display = releases.mapIndexed { i, r ->
                "${i + 1}. ${r.tag} - ${r.name.ifEmpty { "(tanpa nama)" }}"
            }.toTypedArray()
            AlertDialog.Builder(this@GithubRepoDetailActivity)
                .setTitle("Daftar Rilis (${releases.size}) - Ketuk untuk hapus")
                .setItems(display) { _, which ->
                    val target = releases[which]
                    AlertDialog.Builder(this@GithubRepoDetailActivity)
                        .setTitle("Hapus Rilis?")
                        .setMessage("Hapus rilis '${target.tag}' permanen?")
                        .setPositiveButton("HAPUS") { _, _ ->
                            lifecycleScope.launch {
                                val ok = GithubApiHelper.deleteRelease(token, repoOwner, repoName, target.id)
                                Toast.makeText(this@GithubRepoDetailActivity, if (ok) "Rilis dihapus" else "Gagal menghapus", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Batal", null)
                        .setNeutralButton("Buka di Browser") { _, _ ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.htmlUrl)))
                        }
                        .show()
                }
                .setPositiveButton("Tutup", null).show()
        }
    }

    // ==========================================
    // MENU 14: RIWAYAT COMMIT & KEMBALIKAN
    // ==========================================
    private fun showCommitsManageDialog() {
        Toast.makeText(this, "Memuat riwayat commit...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val commits = GithubApiHelper.getRepoCommitsFull(token, repoOwner, repoName)
            if (commits.isNullOrEmpty()) {
                Toast.makeText(this@GithubRepoDetailActivity, "Belum ada commit.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            val display = commits.mapIndexed { i, c ->
                val date = try { sdf.format(java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(c.date)!!) } catch (_: Exception) { c.date }
                "${i + 1}. ${c.sha.take(7)} - ${c.message.take(40)}\n   $date"
            }.toTypedArray()
            AlertDialog.Builder(this@GithubRepoDetailActivity)
                .setTitle("Riwayat Commit (${commits.size})")
                .setItems(display) { _, which ->
                    val target = commits[which]
                    AlertDialog.Builder(this@GithubRepoDetailActivity)
                        .setTitle("Commit ${target.sha.take(7)}")
                        .setMessage("${target.message}\n\nBuka detail di browser?")
                        .setPositiveButton("Buka di Browser") { _, _ ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$repoHtmlUrl/commit/${target.sha}")))
                        }
                        .setNegativeButton("Tutup", null).show()
                }
                .setPositiveButton("Tutup", null).show()
        }
    }

    // ==========================================
    // MENU 16: INFO REPO LENGKAP
    // ==========================================
    private fun showRepoFullInfo() {
        Toast.makeText(this, "Memuat info...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val info = GithubApiHelper.getRepoDetailInfo(token, repoOwner, repoName)
            AlertDialog.Builder(this@GithubRepoDetailActivity)
                .setTitle("Info Repositori")
                .setMessage(info)
                .setPositiveButton("Tutup", null).show()
        }
    }

    // ==========================================
    // FUNGSI DASAR LAINNYA
    // ==========================================
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
        AlertDialog.Builder(this).setTitle("Ubah Status Privasi")
            .setMessage("Ubah '$repoName' menjadi ${if (isRepoPrivate) "PUBLIK" else "PRIVAT"}?")
            .setPositiveButton("Ya, Ubah") { _, _ ->
                lifecycleScope.launch {
                    val success = GithubApiHelper.changePrivacy(token, repoOwner, repoName, !isRepoPrivate)
                    if (success) { Toast.makeText(this@GithubRepoDetailActivity, "Berhasil diubah", Toast.LENGTH_SHORT).show(); finish() }
                    else Toast.makeText(this@GithubRepoDetailActivity, "Gagal mengubah", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun showRenameDialog() {
        val input = EditText(this).apply { setText(repoName) }
        AlertDialog.Builder(this).setTitle("Ganti Nama Repositori").setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != repoName) {
                    lifecycleScope.launch {
                        val success = GithubApiHelper.renameRepo(token, repoOwner, repoName, newName)
                        if (success) { Toast.makeText(this@GithubRepoDetailActivity, "Berhasil diganti", Toast.LENGTH_SHORT).show(); finish() }
                        else Toast.makeText(this@GithubRepoDetailActivity, "Gagal mengganti nama", Toast.LENGTH_SHORT).show()
                    }
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun showDeleteConfirm() {
        val input = EditText(this).apply { hint = "Ketik nama repo '$repoName' untuk konfirmasi" }
        AlertDialog.Builder(this).setTitle("⚠️ HAPUS REPOSITORI PERMANEN")
            .setMessage("Tindakan ini TIDAK BISA dibatalkan. Semua kode, rilis, dan riwayat akan hilang selamanya.")
            .setView(input)
            .setPositiveButton("HAPUS PERMANEN") { _, _ ->
                if (input.text.toString().trim() == repoName) {
                    lifecycleScope.launch {
                        val success = GithubApiHelper.deleteRepo(token, repoOwner, repoName)
                        if (success) { Toast.makeText(this@GithubRepoDetailActivity, "Repositori dihapus", Toast.LENGTH_LONG).show(); finish() }
                        else Toast.makeText(this@GithubRepoDetailActivity, "Gagal menghapus (cek permission token)", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Nama tidak cocok. Pembatalan.", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Batal", null).show()
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
}