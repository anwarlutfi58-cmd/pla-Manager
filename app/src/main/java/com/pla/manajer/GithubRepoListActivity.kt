package com.pla.manajer

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pla.manajer.util.GithubApiHelper
import kotlinx.coroutines.launch

class GithubRepoListActivity : AppCompatActivity() {
    
    private lateinit var btnRefresh: Button
    private lateinit var btnFilter: Button
    private lateinit var listView: ListView
    
    private var token: String = ""
    private var allRepos: List<GithubApiHelper.RepoInfo> = emptyList()
    private var filteredRepos: List<GithubApiHelper.RepoInfo> = emptyList()
    
    private var filterMode = 0 // 0 = Semua, 1 = Publik, 2 = Privat

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_github_repo_list)

        token = getSharedPreferences("PlaPrefs", MODE_PRIVATE).getString("github_token", "") ?: ""

        btnRefresh = findViewById(R.id.btnRefresh)
        btnFilter = findViewById(R.id.btnFilter)
        listView = findViewById(R.id.listRepos)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        btnRefresh.setOnClickListener { fetchRepos() }

        btnFilter.setOnClickListener {
            val options = arrayOf("Semua Repositori", "Hanya Publik", "Hanya Privat")
            AlertDialog.Builder(this)
                .setTitle("Filter Repositori")
                .setSingleChoiceItems(options, filterMode) { dialog, which ->
                    filterMode = which
                    btnFilter.text = "Filter: ${options[which]}"
                    applyFilter()
                    dialog.dismiss()
                }.show()
        }

        // Klik item repo -> Buka halaman detail (24 menu)
        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedRepo = filteredRepos[position]
            val intent = Intent(this, GithubRepoDetailActivity::class.java).apply {
                putExtra("repo_name", selectedRepo.name)
                putExtra("repo_full_name", selectedRepo.fullName)
                putExtra("repo_private", selectedRepo.isPrivate)
                putExtra("repo_url", selectedRepo.htmlUrl)
            }
            startActivity(intent)
        }

        fetchRepos()
    }

    private fun fetchRepos() {
        btnRefresh.text = "Memuat data..."
        btnRefresh.isEnabled = false
        
        lifecycleScope.launch {
            val result = GithubApiHelper.fetchMyRepos(token)
            btnRefresh.text = "Segarkan daftar"
            btnRefresh.isEnabled = true

            if (result != null) {
                allRepos = result
                applyFilter()
            } else {
                Toast.makeText(this@GithubRepoListActivity, "Gagal memuat repositori", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyFilter() {
        filteredRepos = when (filterMode) {
            1 -> allRepos.filter { !it.isPrivate }
            2 -> allRepos.filter { it.isPrivate }
            else -> allRepos
        }

        val displayList = filteredRepos.mapIndexed { index, repo ->
            "${index + 1}. ${repo.name} [${if(repo.isPrivate) "Privat" else "Publik"}]"
        }

        val repoAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
        listView.adapter = repoAdapter
    }
}