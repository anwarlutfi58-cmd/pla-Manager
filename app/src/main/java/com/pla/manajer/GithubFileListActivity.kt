package com.pla.manajer

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

class GithubFileListActivity : AppCompatActivity() {
    
    private lateinit var listView: ListView
    private lateinit var tvTitle: TextView
    private var token: String = ""
    private var owner: String = ""
    private var repo: String = ""
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_github_file_list)

        token = getSharedPreferences("PlaPrefs", MODE_PRIVATE).getString("github_token", "") ?: ""
        owner = intent.getStringExtra("owner") ?: ""
        repo = intent.getStringExtra("repo") ?: ""

        tvTitle = findViewById(R.id.tvTitle)
        listView = findViewById(R.id.listFiles)
        tvTitle.text = "Berkas: $repo"

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        fetchFiles("")
    }

    private fun fetchFiles(path: String) {
        Toast.makeText(this, "Memuat berkas...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                try {
                    val url = "https://api.github.com/repos/$owner/$repo/contents/$path"
                    val req = Request.Builder().url(url).header("Authorization", "token $token").build()
                    val res = client.newCall(req).execute()
                    if (res.isSuccessful) {
                        val arr = JSONArray(res.body?.string() ?: "")
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val name = obj.getString("name")
                            val type = obj.getString("type") // "file" atau "dir"
                            list.add("${if(type == "dir") "📁" else "📄"} $name")
                        }
                        list
                    } else null
                } catch (e: Exception) { null }
            }

            if (files != null) {
                val adapter = ArrayAdapter(this@GithubFileListActivity, android.R.layout.simple_list_item_1, files)
                listView.adapter = adapter
            } else {
                Toast.makeText(this@GithubFileListActivity, "Gagal memuat berkas", Toast.LENGTH_SHORT).show()
            }
        }
    }
}