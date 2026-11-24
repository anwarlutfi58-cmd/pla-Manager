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
import java.io.File
import java.util.concurrent.TimeUnit

object GithubApiHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class RepoInfo(
        val name: String,
        val fullName: String,
        val isPrivate: Boolean,
        val htmlUrl: String,
        val description: String
    )

    suspend fun fetchMyRepos(token: String): List<RepoInfo>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/user/repos?per_page=100&sort=updated")
                .header("Authorization", "token $token")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val jsonArray = JSONArray(body)
                val list = mutableListOf<RepoInfo>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        RepoInfo(
                            name = obj.getString("name"),
                            fullName = obj.getString("full_name"),
                            isPrivate = obj.getBoolean("private"),
                            htmlUrl = obj.getString("html_url"),
                            description = obj.optString("description", "")
                        )
                    )
                }
                list
            } else null
        } catch (e: Exception) { null }
    }

    // Menghapus Repositori
    suspend fun deleteRepo(token: String, owner: String, repoName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repoName")
                .header("Authorization", "token $token")
                .delete()
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    // Ubah Status Privasi
    suspend fun changePrivacy(token: String, owner: String, repoName: String, isPrivate: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("private", isPrivate) }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repoName")
                .header("Authorization", "token $token")
                .patch(body)
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    // Ganti Nama Repositori
    suspend fun renameRepo(token: String, owner: String, repoName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("name", newName) }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repoName")
                .header("Authorization", "token $token")
                .patch(body)
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    // Mendapatkan token user (untuk upload)
    suspend fun getAuthUser(token: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/user")
                .header("Authorization", "token $token")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                JSONObject(response.body?.string() ?: "").getString("login")
            } else null
        } catch (e: Exception) { null }
    }

    // Pindah Arah / Unggah Berkas (Upload File)
    suspend fun uploadFileToRepo(token: String, owner: String, repoName: String, pathInRepo: String, localFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Dapatkan SHA file yang ada (jika ada, untuk update)
            var sha: String? = null
            val getReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repoName/contents/$pathInRepo")
                .header("Authorization", "token $token")
                .build()
            val getRes = client.newCall(getReq).execute()
            if (getRes.isSuccessful) {
                val body = JSONObject(getRes.body?.string() ?: "")
                sha = body.optString("sha", null)
            }

            // 2. Siapkan data Base64
            val bytes = localFile.readBytes()
            val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT).replace("\n", "")

            // 3. Siapkan JSON request
            val json = JSONObject().apply {
                put("message", "Upload file dari PLA Manajer")
                put("content", encoded)
                if (sha != null) put("sha", sha)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            // 4. Kirim PUT request
            val putReq = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repoName/contents/$pathInRepo")
                .header("Authorization", "token $token")
                .put(body)
                .build()
            client.newCall(putReq).execute().isSuccessful
        } catch (e: Exception) { false }
    }
}