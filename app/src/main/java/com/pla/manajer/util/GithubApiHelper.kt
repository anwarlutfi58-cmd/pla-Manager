package com.pla.manajer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GithubApiHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class RepoInfo(val name: String, val fullName: String, val isPrivate: Boolean, val htmlUrl: String, val description: String)
    
    // Model untuk File (Menu 1)
    data class FileInfo(val name: String, val path: String, val type: String, val size: Long, val sha: String)

    suspend fun fetchMyRepos(token: String): List<RepoInfo>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("https://api.github.com/user/repos?per_page=100&sort=updated").header("Authorization", "token $token").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonArray = JSONArray(response.body?.string() ?: "")
                val list = mutableListOf<RepoInfo>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(RepoInfo(obj.getString("name"), obj.getString("full_name"), obj.getBoolean("private"), obj.getString("html_url"), obj.optString("description", "")))
                }
                list
            } else null
        } catch (e: Exception) { null }
    }

    // Fungsi Baru: Ambil File/Folder untuk Menu 1
    suspend fun getRepoContents(token: String, owner: String, repo: String, path: String = ""): List<FileInfo>? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/contents/$path"
            val request = Request.Builder().url(url).header("Authorization", "token $token").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val arr = JSONArray(response.body?.string() ?: "")
                val list = mutableListOf<FileInfo>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(FileInfo(
                        name = obj.getString("name"),
                        path = obj.getString("path"),
                        type = obj.getString("type"), // "file" atau "dir"
                        size = obj.optLong("size", 0),
                        sha = obj.getString("sha")
                    ))
                }
                list
            } else null
        } catch (e: Exception) { null }
    }

    // Fungsi Baru: Ambil Riwayat Commit per File (untuk melihat versi lama)
    suspend fun getFileCommits(token: String, owner: String, repo: String, filePath: String): List<Pair<String, String>>? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/commits?path=$filePath"
            val request = Request.Builder().url(url).header("Authorization", "token $token").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val arr = JSONArray(response.body?.string() ?: "")
                val list = mutableListOf<Pair<String, String>>() // SHA to Date
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val date = obj.getJSONObject("commit").getJSONObject("author").getString("date")
                    list.add(obj.getString("sha") to date)
                }
                list
            } else null
        } catch (e: Exception) { null }
    }

    suspend fun deleteRepo(token: String, owner: String, repoName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("https://api.github.com/repos/$owner/$repoName").header("Authorization", "token $token").delete().build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun changePrivacy(token: String, owner: String, repoName: String, isPrivate: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("private", isPrivate) }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url("https://api.github.com/repos/$owner/$repoName").header("Authorization", "token $token").patch(body).build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun renameRepo(token: String, owner: String, repoName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("name", newName) }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url("https://api.github.com/repos/$owner/$repoName").header("Authorization", "token $token").patch(body).build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) { false }
    }
}