package com.pla.manajer

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pla.manajer.util.GithubApiHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class GithubFileListActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var tvTitle: TextView

    private var token: String = ""
    private var owner: String = ""
    private var repo: String = ""
    private var currentPath: String = ""
    private var fileList: List<GithubApiHelper.FileInfo> = emptyList()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_github_file_list)

        token = getSharedPreferences("PlaPrefs", MODE_PRIVATE).getString("github_token", "") ?: ""
        owner = intent.getStringExtra("owner") ?: ""
        repo = intent.getStringExtra("repo") ?: ""

        tvTitle = findViewById(R.id.tvTitle)
        listView = findViewById(R.id.listFiles)
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        loadFiles("")
    }

    private fun loadFiles(path: String) {
        currentPath = path
        tvTitle.text = if (path.isEmpty()) "Berkas: $repo" else "Folder: $path"
        Toast.makeText(this, "Memuat...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val files = GithubApiHelper.getRepoContents(token, owner, repo, path)
            if (files != null) {
                fileList = files
                val displayList = files.map {
                    val icon = if (it.type == "dir") "📁" else "📄"
                    val sizeStr = if (it.type == "file") formatSize(it.size) else ""
                    "$icon ${it.name}  $sizeStr"
                }
                listView.adapter = ArrayAdapter(this@GithubFileListActivity, android.R.layout.simple_list_item_1, displayList)

                listView.setOnItemClickListener { _, _, position, _ ->
                    val selected = fileList[position]
                    if (selected.type == "dir") {
                        loadFiles(selected.path)
                    } else {
                        showFileOptions(selected)
                    }
                }
            } else {
                Toast.makeText(this@GithubFileListActivity, "Gagal memuat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFileOptions(file: GithubApiHelper.FileInfo) {
        val options = arrayOf(
            "Lihat / Edit Isi File",
            "Ganti Nama (Rename)",
            "Salin Tautan File",
            "Lihat Riwayat Versi",
            "Hapus File"
        )

        AlertDialog.Builder(this)
            .setTitle("${file.name}\nUkuran: ${formatSize(file.size)}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewOrEditFile(file)
                    1 -> renameFile(file)
                    2 -> {
                        val url = "https://github.com/$owner/$repo/blob/main/${file.path}"
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                        Toast.makeText(this, "Tautan disalin", Toast.LENGTH_SHORT).show()
                    }
                    3 -> showCommits(file)
                    4 -> deleteFile(file)
                }
            }.show()
    }

    /** Lihat isi file teks & edit langsung */
    private fun viewOrEditFile(file: GithubApiHelper.FileInfo) {
        Toast.makeText(this, "Memuat isi file...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val base64 = GithubApiHelper.getFileContentBase64(token, owner, repo, file.path)
            if (base64 == null) {
                Toast.makeText(this@GithubFileListActivity, "Gagal memuat (file mungkin biner/terlalu besar)", Toast.LENGTH_LONG).show()
                return@launch
            }
            val content = try {
                String(android.util.Base64.decode(base64, android.util.Base64.DEFAULT))
            } catch (e: Exception) {
                Toast.makeText(this@GithubFileListActivity, "File bukan teks biasa", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val etContent = EditText(this@GithubFileListActivity).apply {
                setText(content)
                textSize = 13f
            }
            val scroll = android.widget.ScrollView(this@GithubFileListActivity).apply { addView(etContent) }
            AlertDialog.Builder(this@GithubFileListActivity)
                .setTitle("Edit: ${file.name}")
                .setView(scroll)
                .setPositiveButton("Simpan Perubahan") { _, _ ->
                    val newContent = etContent.text.toString()
                    lifecycleScope.launch {
                        val newBase64 = android.util.Base64.encodeToString(newContent.toByteArray(), android.util.Base64.NO_WRAP)
                        val ok = GithubApiHelper.updateFileContent(token, owner, repo, file.path, file.sha, newBase64, "Edit via PLA Manajer")
                        Toast.makeText(this@GithubFileListActivity, if (ok) "Perubahan disimpan!" else "Gagal menyimpan", Toast.LENGTH_SHORT).show()
                        if (ok) loadFiles(currentPath)
                    }
                }
                .setNegativeButton("Tutup", null).show()
        }
    }

    /** Rename file: ambil konten → buat di path baru → hapus yang lama */
    private fun renameFile(file: GithubApiHelper.FileInfo) {
        val input = EditText(this).apply { setText(file.name) }
        AlertDialog.Builder(this).setTitle("Ganti Nama File").setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty() || newName == file.name) {
                    Toast.makeText(this, "Nama tidak berubah", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Toast.makeText(this, "Memproses rename...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    val base64 = GithubApiHelper.getFileContentBase64(token, owner, repo, file.path)
                    if (base64 == null) {
                        Toast.makeText(this@GithubFileListActivity, "Gagal membaca file", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val parentPath = if (file.path.contains("/")) file.path.substringBeforeLast("/") + "/" else ""
                    val newPath = parentPath + newName
                    val ok = GithubApiHelper.renameFile(token, owner, repo, file.path, file.sha, newPath, base64)
                    Toast.makeText(this@GithubFileListActivity, if (ok) "Berhasil diganti nama!" else "Gagal rename", Toast.LENGTH_SHORT).show()
                    if (ok) loadFiles(currentPath)
                }
            }.setNegativeButton("Batal", null).show()
    }

    /** Hapus file */
    private fun deleteFile(file: GithubApiHelper.FileInfo) {
        AlertDialog.Builder(this).setTitle("Hapus File?")
            .setMessage("Hapus '${file.name}' permanen dari repositori?")
            .setPositiveButton("HAPUS") { _, _ ->
                lifecycleScope.launch {
                    val ok = GithubApiHelper.deleteFile(token, owner, repo, file.path, file.sha)
                    Toast.makeText(this@GithubFileListActivity, if (ok) "File dihapus" else "Gagal menghapus", Toast.LENGTH_SHORT).show()
                    if (ok) loadFiles(currentPath)
                }
            }.setNegativeButton("Batal", null).show()
    }

    /** Riwayat versi file + revert ke versi lama */
    private fun showCommits(file: GithubApiHelper.FileInfo) {
        Toast.makeText(this, "Mengambil riwayat...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val commits = GithubApiHelper.getFileCommits(token, owner, repo, file.path)
            if (commits.isNullOrEmpty()) {
                Toast.makeText(this@GithubFileListActivity, "Tidak ada riwayat", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val displayList = commits.mapIndexed { i, pair ->
                val date = try {
                    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                        .format(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).parse(pair.second)!!)
                } catch (_: Exception) { pair.second }
                "${i + 1}. Versi: ${pair.first.take(7)} | $date"
            }.toTypedArray()

            AlertDialog.Builder(this@GithubFileListActivity)
                .setTitle("Riwayat: ${file.name}")
                .setItems(displayList) { _, which ->
                    val sha = commits[which].first
                    AlertDialog.Builder(this@GithubFileListActivity)
                        .setTitle("Kembalikan ke Versi Ini?")
                        .setMessage("File akan dikembalikan ke versi ${sha.take(7)}. Versi saat ini akan tertimpa.")
                        .setPositiveButton("Ya, Kembalikan") { _, _ -> revertFile(file, sha) }
                        .setNegativeButton("Batal", null).show()
                }.setNegativeButton("Tutup", null).show()
        }
    }

    /** Revert: ambil konten versi lama → timpa versi sekarang */
    private fun revertFile(file: GithubApiHelper.FileInfo, oldSha: String) {
        Toast.makeText(this, "Mengembalikan versi...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val oldContent = GithubApiHelper.getFileContentAtRef(token, owner, repo, file.path, oldSha)
            if (oldContent == null) {
                Toast.makeText(this@GithubFileListActivity, "Gagal mengambil versi lama", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // Ambil SHA terbaru dulu (karena bisa berubah)
            val currentFiles = GithubApiHelper.getRepoContents(token, owner, repo, currentPath)
            val currentFile = currentFiles?.firstOrNull { it.path == file.path }
            val currentSha = currentFile?.sha ?: file.sha
            val ok = GithubApiHelper.updateFileContent(token, owner, repo, file.path, currentSha, oldContent, "Revert ke $oldSha via PLA Manajer")
            Toast.makeText(this@GithubFileListActivity, if (ok) "Berhasil dikembalikan!" else "Gagal mengembalikan", Toast.LENGTH_SHORT).show()
            if (ok) loadFiles(currentPath)
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        return if (kb > 1024) String.format("%.1f MB", kb / 1024) else String.format("%.1f KB", kb)
    }
}