package com.pla.manajer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pla.manajer.util.ApkBuilderHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GithubBuilderActivity : AppCompatActivity() {

    private lateinit var layoutLogin: LinearLayout
    private lateinit var layoutMain: LinearLayout
    private lateinit var etToken: EditText

    private var token: String = ""
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private data class RepoItem(val name: String, val fullName: String, val isPrivate: Boolean, val htmlUrl: String)

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_github_builder)

        layoutLogin = findViewById(R.id.layoutLogin)
        layoutMain = findViewById(R.id.layoutMain)
        etToken = findViewById(R.id.etToken)

        val prefs = getSharedPreferences("PlaPrefs", MODE_PRIVATE)
        token = prefs.getString("github_token", "") ?: ""

        if (token.isNotEmpty()) {
            layoutLogin.visibility = View.GONE
            layoutMain.visibility = View.VISIBLE
            setupMenuButtons()
        }

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val inputToken = etToken.text.toString().trim()
            if (inputToken.isNotEmpty()) verifyAndSaveToken(inputToken)
            else Toast.makeText(this, "Token tidak boleh kosong", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnBuatTokenWeb).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new")))
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun verifyAndSaveToken(inputToken: String) {
        lifecycleScope.launch {
            val isValid = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url("https://api.github.com/user")
                        .header("Authorization", "token $inputToken").build()
                    client.newCall(request).execute().isSuccessful
                } catch (e: Exception) { false }
            }
            if (isValid) {
                token = inputToken
                getSharedPreferences("PlaPrefs", MODE_PRIVATE).edit().putString("github_token", token).apply()
                layoutLogin.visibility = View.GONE
                layoutMain.visibility = View.VISIBLE
                setupMenuButtons()
                Toast.makeText(this@GithubBuilderActivity, "Login Berhasil!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@GithubBuilderActivity, "Token tidak valid atau tidak ada internet", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupMenuButtons() {
        findViewById<Button>(R.id.btnMenu1).setOnClickListener { fetchMyRepos() }
        findViewById<Button>(R.id.btnMenu2).setOnClickListener { showSearchDialog() }
        findViewById<Button>(R.id.btnMenu3).setOnClickListener { showReleasesDialog() }
        findViewById<Button>(R.id.btnMenu4).setOnClickListener { showCreateRepoDialog() }
        findViewById<Button>(R.id.btnMenu5).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/pages")))
        }
        findViewById<Button>(R.id.btnMenu6).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("GitHub Token", token))
            Toast.makeText(this, "Token disalin ke clipboard", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnMenu7).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new")))
        }
        // MENU 8: PENGATURAN BUILD (sekarang NYATA)
        findViewById<Button>(R.id.btnMenu8).setOnClickListener { showBuildSettingsDialog() }
        findViewById<Button>(R.id.btnMenu9).setOnClickListener {
            Toast.makeText(this, "Tema: Mengikuti Sistem", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnMenu10).setOnClickListener {
            getSharedPreferences("PlaPrefs", MODE_PRIVATE).edit().remove("github_token").apply()
            token = ""
            layoutMain.visibility = View.GONE
            layoutLogin.visibility = View.VISIBLE
            etToken.setText("")
            Toast.makeText(this, "Berhasil Logout", Toast.LENGTH_SHORT).show()
        }
    }

    /** MENU 8: Pengaturan Build APK global */
    private fun showBuildSettingsDialog() {
        val options = arrayOf(
            "Pasang Workflow Build di Semua Repo",
            "Info Cara Kerja Build Otomatis",
            "Buka Dokumentasi GitHub Actions"
        )
        AlertDialog.Builder(this)
            .setTitle("Pengaturan Build APK")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showInstallWorkflowToAll()
                    1 -> showBuildHowItWorks()
                    2 -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.github.com/en/actions")))
                }
            }.show()
    }

    private fun showBuildHowItWorks() {
        val msg = """
            Cara kerja Build APK otomatis di PLA Manajer:
            
            1. PLA memasang file workflow (.yml) ke repo kamu via API.
            2. Workflow menjalankan Gradle di server GitHub (gratis).
            3. Hasil APK diunggah sebagai "Artefak" & "Rilis".
            4. Kamu unduh APK langsung dari menu 7 di halaman repo.
            
            Tidak butuh laptop. Semua berjalan di cloud GitHub.
            Build biasanya selesai dalam 2-5 menit.
        """.trimIndent()
        AlertDialog.Builder(this).setTitle("Cara Kerja Build").setMessage(msg).setPositiveButton("Mengerti", null).show()
    }

    private fun showInstallWorkflowToAll() {
        Toast.makeText(this, "Mengambil daftar repo...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val repos = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url("https://api.github.com/user/repos?per_page=100")
                        .header("Authorization", "token $token").build()
                    val res = client.newCall(req).execute()
                    if (res.isSuccessful) {
                        val arr = JSONArray(res.body?.string() ?: "")
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) list.add(arr.getJSONObject(i).getString("name"))
                        list
                    } else null
                } catch (e: Exception) { null }
            }
            if (repos.isNullOrEmpty()) {
                Toast.makeText(this@GithubBuilderActivity, "Gagal mengambil repo", Toast.LENGTH_SHORT).show()
                return@launch
            }
            AlertDialog.Builder(this@GithubBuilderActivity)
                .setTitle("Pilih Repo untuk Dipasangi Workflow")
                .setItems(repos.toTypedArray()) { _, which ->
                    val repoName = repos[which]
                    val owner = token.let { /* owner diambil dari user */ }
                    lifecycleScope.launch {
                        val userReq = Request.Builder().url("https://api.github.com/user")
                            .header("Authorization", "token $token").build()
                        val userRes = withContext(Dispatchers.IO) { client.newCall(userReq).execute() }
                        val ownerName = if (userRes.isSuccessful) {
                            JSONObject(userRes.body?.string() ?: "").optString("login", "")
                        } else ""
                        if (ownerName.isEmpty()) {
                            Toast.makeText(this@GithubBuilderActivity, "Gagal deteksi pemilik", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        Toast.makeText(this@GithubBuilderActivity, "Memasang workflow di $repoName...", Toast.LENGTH_SHORT).show()
                        val ok = ApkBuilderHelper.setupBuildWorkflow(token, ownerName, repoName, true)
                        Toast.makeText(this@GithubBuilderActivity, if (ok) "✅ Workflow terpasang di $repoName" else "❌ Gagal memasang", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Batal", null).show()
        }
    }

    private fun openRepoDetail(repo: RepoItem) {
        startActivity(Intent(this, GithubRepoDetailActivity::class.java).apply {
            putExtra("repo_name", repo.name)
            putExtra("repo_full_name", repo.fullName)
            putExtra("repo_private", repo.isPrivate)
            putExtra("repo_url", repo.htmlUrl)
        })
    }

    private fun fetchMyRepos() {
        Toast.makeText(this, "Mengambil data...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url("https://api.github.com/user/repos?per_page=100&sort=updated")
                        .header("Authorization", "token $token").build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val jsonArray = JSONArray(body)
                        val list = mutableListOf<RepoItem>()
                        for (i in 0 until jsonArray.length()) {
                            val repo = jsonArray.getJSONObject(i)
                            list.add(RepoItem(repo.getString("name"), repo.getString("full_name"), repo.getBoolean("private"), repo.getString("html_url")))
                        }
                        list
                    } else null
                } catch (e: Exception) { null }
            }
            if (result != null) {
                if (result.isEmpty()) {
                    Toast.makeText(this@GithubBuilderActivity, "Belum ada repositori.", Toast.LENGTH_SHORT).show()
                } else {
                    val displayList = result.map { "${it.name} [${if (it.isPrivate) "Privat" else "Publik"}]" }
                    AlertDialog.Builder(this@GithubBuilderActivity)
                        .setTitle("Repositori Saya (${result.size})")
                        .setItems(displayList.toTypedArray()) { _, which -> openRepoDetail(result[which]) }
                        .setPositiveButton("Tutup", null).show()
                }
            } else {
                Toast.makeText(this@GithubBuilderActivity, "Gagal mengambil data repo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply { hint = "Kata kunci repo" }
        AlertDialog.Builder(this).setTitle("Cari Repositori GitHub").setView(input)
            .setPositiveButton("Cari") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) searchRepos(query)
            }.setNegativeButton("Batal", null).show()
    }

    private fun searchRepos(query: String) {
        Toast.makeText(this, "Mencari...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url("https://api.github.com/search/repositories?q=${Uri.encode(query)}&per_page=15")
                        .header("Authorization", "token $token").build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val items = JSONObject(body).getJSONArray("items")
                        val list = mutableListOf<Pair<RepoItem, Int>>()
                        for (i in 0 until items.length()) {
                            val repo = items.getJSONObject(i)
                            list.add(Pair(RepoItem(repo.getString("name"), repo.getString("full_name"), repo.getBoolean("private"), repo.getString("html_url")), repo.getInt("stargazers_count")))
                        }
                        list
                    } else null
                } catch (e: Exception) { null }
            }
            if (result != null) {
                if (result.isEmpty()) {
                    Toast.makeText(this@GithubBuilderActivity, "Tidak ditemukan", Toast.LENGTH_SHORT).show()
                } else {
                    val displayList = result.map { "${it.first.fullName}\n⭐ ${it.second}" }
                    AlertDialog.Builder(this@GithubBuilderActivity)
                        .setTitle("Hasil Pencarian: $query")
                        .setItems(displayList.toTypedArray()) { _, which -> openRepoDetail(result[which].first) }
                        .setPositiveButton("Tutup", null).show()
                }
            } else {
                Toast.makeText(this@GithubBuilderActivity, "Pencarian gagal", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showReleasesDialog() {
        val input = EditText(this).apply { hint = "username/nama-repo" }
        AlertDialog.Builder(this).setTitle("Lihat Rilis Repositori").setView(input)
            .setPositiveButton("Lihat") { _, _ ->
                val repo = input.text.toString().trim()
                if (repo.isNotEmpty()) fetchReleases(repo)
            }.setNegativeButton("Batal", null).show()
    }

    private fun fetchReleases(repo: String) {
        Toast.makeText(this, "Mengambil rilis...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url("https://api.github.com/repos/$repo/releases")
                        .header("Authorization", "token $token").build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val jsonArray = JSONArray(body)
                        val list = mutableListOf<Pair<String, String>>()
                        for (i in 0 until jsonArray.length()) {
                            val release = jsonArray.getJSONObject(i)
                            list.add(Pair("${release.getString("tag_name")} - ${release.optString("name", "")}", release.optString("html_url", "https://github.com/$repo/releases")))
                        }
                        list
                    } else null
                } catch (e: Exception) { null }
            }
            if (result != null) {
                if (result.isEmpty()) {
                    Toast.makeText(this@GithubBuilderActivity, "Tidak ada rilis", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(this@GithubBuilderActivity)
                        .setTitle("Rilis $repo")
                        .setItems(result.map { it.first }.toTypedArray()) { _, which ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result[which].second)))
                        }.setPositiveButton("Tutup", null).show()
                }
            } else {
                Toast.makeText(this@GithubBuilderActivity, "Gagal mengambil rilis", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateRepoDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 20, 50, 20) }
        val etName = EditText(this).apply { hint = "Nama Repositori" }
        val etDesc = EditText(this).apply { hint = "Deskripsi Singkat" }
        layout.addView(etName); layout.addView(etDesc)

        AlertDialog.Builder(this).setTitle("Buat Repositori Baru").setView(layout)
            .setPositiveButton("Buat") { _, _ ->
                val name = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (name.isNotEmpty()) createRepo(name, desc)
            }.setNegativeButton("Batal", null).show()
    }

    private fun createRepo(name: String, desc: String) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val json = JSONObject().apply { put("name", name); put("description", desc); put("private", false) }
                    val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder().url("https://api.github.com/user/repos")
                        .header("Authorization", "token $token").post(requestBody).build()
                    client.newCall(request).execute().isSuccessful
                } catch (e: Exception) { false }
            }
            if (success) Toast.makeText(this@GithubBuilderActivity, "Repositori '$name' berhasil dibuat!", Toast.LENGTH_LONG).show()
            else Toast.makeText(this@GithubBuilderActivity, "Gagal membuat repositori", Toast.LENGTH_SHORT).show()
        }
    }
}