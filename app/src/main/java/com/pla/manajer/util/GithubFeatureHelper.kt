package com.pla.manajer.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GithubFeatureHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // 1. Fungsi untuk mengunggah file dari HP (Pindah Arah)
    suspend fun uploadFile(token: String, owner: String, repo: String, path: String, fileBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            var sha: String? = null
            val getReq = Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "token $token").build()
            val getRes = client.newCall(getReq).execute()
            if (getRes.isSuccessful) sha = JSONObject(getRes.body?.string() ?: "").optString("sha", null)

            val encoded = android.util.Base64.encodeToString(fileBytes, android.util.Base64.DEFAULT).replace("\n", "")
            val json = JSONObject().apply { put("message", "Upload dari Aplikasi"); put("content", encoded); if (sha != null) put("sha", sha) }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val req = Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "token $token").put(body).build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    // 2. Fungsi untuk melihat Workflow / Build
    suspend fun getWorkflows(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs").header("Authorization", "token $token").build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val runs = JSONObject(res.body?.string() ?: "").getJSONArray("workflow_runs")
                val sb = StringBuilder()
                for (i in 0 until runs.length()) {
                    val run = runs.getJSONObject(i)
                    sb.append("• ${run.getString("name")} (${run.getString("status")}) - ${run.getString("conclusion") ?: "Berjalan"}\n")
                }
                sb.toString()
            } else "Gagal mengambil data build."
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    // 3. Fungsi untuk Commit History
    suspend fun getCommits(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/repos/$owner/$repo/commits").header("Authorization", "token $token").build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val commits = JSONArray(res.body?.string() ?: "")
                val sb = StringBuilder()
                for (i in 0 until commits.length()) {
                    val c = commits.getJSONObject(i)
                    val msg = c.getJSONObject("commit").getString("message").replace("\n", " ")
                    sb.append("• ${c.getString("sha").take(7)}: $msg\n")
                }
                sb.toString()
            } else "Gagal mengambil data commit."
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    // 4. Fungsi untuk Rilis (Releases)
    suspend fun getReleases(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/repos/$owner/$repo/releases").header("Authorization", "token $token").build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val releases = JSONArray(res.body?.string() ?: "")
                val sb = StringBuilder()
                for (i in 0 until releases.length()) {
                    val r = releases.getJSONObject(i)
                    sb.append("• ${r.getString("tag_name")} (${r.getString("name")})\n")
                }
                sb.toString()
            } else "Gagal mengambil data rilis."
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}