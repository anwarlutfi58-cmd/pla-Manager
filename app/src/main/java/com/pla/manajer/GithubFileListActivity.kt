package com.pla.manajer

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
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
    private lateinit var adapter: ArrayAdapter<String>
    
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
                adapter = ArrayAdapter(this@GithubFileListActivity, android.R.layout.simple_list_item_1, displayList)
                listView.adapter = adapter

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
            "Lihat Riwayat Versi (Commit)",
            "Ganti Nama (Rename)",
            "Salin Tautan File",
            "Hapus File"
        )

        AlertDialog.Builder(this)
            .setTitle("${file.name}\nUkuran: ${formatSize(file.size)}")
            .setItems(options) { _, which ->
                when(which) {
                    0 -> showCommits(file)
                    1 -> Toast.makeText(this, "Fitur Rename di GitHub API membutuhkan proses commit", Toast.LENGTH_SHORT).show()
                    2 -> {
                        val url = "https://github.com/$owner/$repo/blob/main/${file.path}"
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                        Toast.makeText(this, "Tautan disalin", Toast.LENGTH_SHORT).show()
                    }
                    3 -> Toast.makeText(this, "Fitur hapus file via API", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun showCommits(file: GithubApiHelper.FileInfo) {
        Toast.makeText(this, "Mengambil riwayat...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val commits = GithubApiHelper.getFileCommits(token, owner, repo, file.path)
            if (commits != null && commits.isNotEmpty()) {
                // Format Tanggal
                val displayList = commits.mapIndexed { i, pair ->
                    val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(java.util.Date(pair.second))
                    "${i + 1}. Versi: ${pair.first.take(7)} | Tanggal: $date"
                }.toTypedArray()

                AlertDialog.Builder(this@GithubFileListActivity)
                    .setTitle("Riwayat: ${file.name}")
                    .setItems(displayList) { _, which ->
                        val sha = commits[which].first
                        val confirm = AlertDialog.Builder(this@GithubFileListActivity)
                            .setTitle("Kembalikan Versi?")
                            .setMessage("Kembalikan file ke versi ${sha.take(7)}?")
                            .setPositiveButton("Ya, Kembalikan") { _, _ ->
                                Toast.makeText(this@GithubFileListActivity, "Proses Revert via API (Dibutuhkan Commit)", Toast.LENGTH_LONG).show()
                            }.setNegativeButton("Batal", null).show()
                    }.setNegativeButton("Tutup", null).show()
            } else {
                Toast.makeText(this@GithubFileListActivity, "Tidak ada riwayat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        return if (kb > 1024) String.format("%.1f MB", kb / 1024) else String.format("%.1f KB", kb)
    }
}