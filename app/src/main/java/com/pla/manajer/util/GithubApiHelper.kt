package com.pla.manajer.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
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
        } catch (e: Exception) {
            null
        }
    }

    // Fungsi untuk menghapus repo (Bisa dipanggil nanti)
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
}