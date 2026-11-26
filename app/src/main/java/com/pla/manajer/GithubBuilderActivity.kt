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

    // Data lengkap repo agar item bisa diketuk & dibuka detailnya
    private data class RepoItem(
        val name: String,
        val fullName: String,
        val isPrivate: Boolean,
        val htmlUrl: String
    )

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
            if (inputToken.isNotEmpty()) {
                verifyAndSaveToken(inputToken)
            } else {
                Toast.makeText(this, "Token tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
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
                    val request = Request.Builder()
                        .url("https://api.github.com/user")
                        .header("Authorization", "token $inputToken")
                        .build()
                    val response = client.newCall(request).execute()
                    response.isSuccessful
                } catch (e: Exception) {
                    false
                }
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

        findViewById<Button>(R.id.btnMenu8).setOnClickListener {
            Toast.makeText(this, "Pengaturan Build APK akan diimplementasikan.", Toast.LENGTH_SHORT).show()
        }

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

    /** Buka halaman detail repo (24 menu) */
    private fun openRepoDetail(repo: RepoItem) {
        val intent = Intent(this, GithubRepoDetailActivity::class.java).apply {
            putExtra("repo_name", repo.name)
            putExtra("repo_full_name", repo.fullName)
            putExtra("repo_private", repo.isPrivate)
            putExtra("repo_url", repo.htmlUrl)
        }
        startActivity(intent)
    }

    // ==========================================
    // FUNGSI API GITHUB
    // ==========================================

    private fun fetchMyRepos() {
        Toast.makeText(this, "Mengambil data...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://api.github.com/user/repos?per_page=100&sort=updated")
                        .header("Authorization", "token $token")
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val jsonArray = JSONArray(body)
                        val list = mutableListOf<RepoItem>()
                        for (i in 0 until jsonArray.length()) {
                            val repo = jsonArray.getJSONObject(i)
                            list.add(
                                RepoItem(
                                    name = repo.getString("name"),
                                    fullName = repo.getString("full_name"),
                                    isPrivate = repo.getBoolean("private"),
                                    htmlUrl = repo.getString("html_url")
                                )
                            )
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
                        .setItems(displayList.toTypedArray()) { _, which ->
                            // PERBAIKAN UTAMA: item sekarang BISA DIKETUK → buka detail repo
                            openRepoDetail(result[which])
                        }
                        .setPositiveButton("Tutup", null)
                        .show()
                }
            } else {
                Toast.makeText(this@GithubBuilderActivity, "Gagal mengambil data repo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSearchDialog() {
        val input = EditText(this)
        input.hint = "Kata kunci repo"
        AlertDialog.Builder(this)
            .setTitle("Cari Repositori GitHub")
            .setView(input)
            .setPositiveButton("Cari") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) searchRepos(query)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun searchRepos(query: String) {
        Toast.makeText(this, "Mencari...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://api.github.com/search/repositories?q=${Uri.encode(query)}&per_page=15")
                        .header("Authorization", "token $token")
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val items = JSONObject(body).getJSONArray("items")
                        val list = mutableListOf<Pair<RepoItem, Int>>()
                        for (i in 0 until items.length()) {
                            val repo = items.getJSONObject(i)
                            list.add(
                                Pair(
                                    RepoItem(
                                        name = repo.getString("name"),
                                        fullName = repo.getString("full_name"),
                                        isPrivate = repo.getBoolean("private"),
                                        htmlUrl = repo.getString("html_url")
                                    ),
                                    repo.getInt("stargazers_count")
                                )
                            )
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
                        .setItems(displayList.toTypedArray()) { _, which ->
                            // PERBAIKAN: hasil pencarian juga BISA DIKETUK
                            openRepoDetail(result[which].first)
                        }
                        .setPositiveButton("Tutup", null)
                        .show()
                }
            } else {
                Toast.makeText(this@GithubBuilderActivity, "Pencarian gagal", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showReleasesDialog() {
        val input = EditText(this)
        input.hint = "username/nama-repo"
        AlertDialog.Builder(this)
            .setTitle("Lihat Rilis Repositori")
            .setView(input)
            .setPositiveButton("Lihat") { _, _ ->
                val repo = input.text.toString().trim()
                if (repo.isNotEmpty()) fetchReleases(repo)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun fetchReleases(repo: String) {
        Toast.makeText(this, "Mengambil rilis...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://api.github.com/repos/$repo/releases")
                        .header("Authorization", "token $token")
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val jsonArray = JSONArray(body)
                        val list = mutableListOf<Pair<String, String>>()
                        for (i in 0 until jsonArray.length()) {
                            val release = jsonArray.getJSONObject(i)
                            val label = "${release.getString("tag_name")} - ${release.optString("name", "")}"
                            val url = release.optString("html_url", "https://github.com/$repo/releases")
                            list.add(Pair(label, url))
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
                            // PERBAIKAN: ketuk rilis → buka halaman rilis (bisa download APK)
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result[which].second)))
                        }
                        .setPositiveButton("Tutup", null)
                        .show()
                }
            } else {
                Toast.makeText(this@GithubBuilderActivity, "Gagal mengambil rilis", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateRepoDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 20)

        val etName = EditText(this)
        etName.hint = "Nama Repositori"
        layout.addView(etName)

        val etDesc = EditText(this)
        etDesc.hint = "Deskripsi Singkat"
        layout.addView(etDesc)

        AlertDialog.Builder(this)
            .setTitle("Buat Repositori Baru")
            .setView(layout)
            .setPositiveButton("Buat") { _, _ ->
                val name = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (name.isNotEmpty()) createRepo(name, desc)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun createRepo(name: String, desc: String) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val json = JSONObject().apply {
                        put("name", name)
                        put("description", desc)
                        put("private", false)
                    }
                    val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder()
                        .url("https://api.github.com/user/repos")
                        .header("Authorization", "token $token")
                        .post(requestBody)
                        .build()
                    val response = client.newCall(request).execute()
                    response.isSuccessful
                } catch (e: Exception) { false }
            }
            if (success) {
                Toast.makeText(this@GithubBuilderActivity, "Repositori '$name' berhasil dibuat!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@GithubBuilderActivity, "Gagal membuat repositori", Toast.LENGTH_SHORT).show()
            }
        }
    }
}