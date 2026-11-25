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
                        type = obj.getString("type"),
                        size = obj.optLong("size", 0),
                        sha = obj.getString("sha")
                    ))
                }
                list
            } else null
        } catch (e: Exception) { null }
    }

    // Menghapus File via API (Membutuhkan SHA file saat ini)
    suspend fun deleteFile(token: String, owner: String, repo: String, path: String, sha: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("message", "Hapus file via PLA Manajer")
                put("sha", sha)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/contents/$path")
                .header("Authorization", "token $token")
                .delete(body)
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    // Ganti Nama File (Create baru, lalu hapus yang lama)
    suspend fun renameFile(token: String, owner: String, repo: String, oldPath: String, oldSha: String, newPath: String, contentBase64: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Buat File Baru
            val createJson = JSONObject().apply {
                put("message", "Rename file via PLA Manajer")
                put("content", contentBase64)
            }
            val createBody = createJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val createReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/contents/$newPath")
                .header("Authorization", "token $token")
                .put(createBody)
                .build()
            
            if (!client.newCall(createReq).execute().isSuccessful) return@withContext false

            // 2. Hapus File Lama
            val deleteJson = JSONObject().apply {
                put("message", "Hapus file lama setelah rename")
                put("sha", oldSha)
            }
            val deleteBody = deleteJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val deleteReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/contents/$oldPath")
                .header("Authorization", "token $token")
                .delete(deleteBody)
                .build()
            
            client.newCall(deleteReq).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun getFileCommits(token: String, owner: String, repo: String, filePath: String): List<Pair<String, String>>? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/commits?path=$filePath"
            val request = Request.Builder().url(url).header("Authorization", "token $token").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val arr = JSONArray(response.body?.string() ?: "")
                val list = mutableListOf<Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val date = obj.getJSONObject("commit").getJSONObject("author").getString("date")
                    list.add(obj.getString("sha") to date)
                }
                list
            } else null
        } catch (e: Exception) { null }
    }

    // Mendapatkan isi konten base64 dari file (untuk rename)
    suspend fun getFileContentBase64(token: String, owner: String, repo: String, path: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/contents/$path"
            val request = Request.Builder().url(url).header("Authorization", "token $token").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val obj = JSONObject(response.body?.string() ?: "")
                obj.optString("content", null)?.replace("\n", "")
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