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
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    data class RepoInfo(val name: String, val fullName: String, val isPrivate: Boolean, val htmlUrl: String, val description: String)
    data class FileInfo(val name: String, val path: String, val type: String, val size: Long, val sha: String)
    data class ArtifactInfo(val id: Long, val name: String, val sizeBytes: Long, val expired: Boolean, val downloadUrl: String)
    data class ReleaseInfo(val id: Long, val tag: String, val name: String, val htmlUrl: String)
    data class CommitInfo(val sha: String, val message: String, val date: String)
    data class RunInfo(val id: Long, val name: String, val status: String, val conclusion: String, val htmlUrl: String, val createdAt: String)

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

    suspend fun renameFile(token: String, owner: String, repo: String, oldPath: String, oldSha: String, newPath: String, contentBase64: String): Boolean = withContext(Dispatchers.IO) {
        try {
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

    /** BARU: Ambil isi file pada versi commit tertentu (untuk revert) */
    suspend fun getFileContentAtRef(token: String, owner: String, repo: String, path: String, ref: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/contents/$path?ref=$ref"
            val request = Request.Builder().url(url).header("Authorization", "token $token").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val obj = JSONObject(response.body?.string() ?: "")
                obj.optString("content", null)?.replace("\n", "")
            } else null
        } catch (e: Exception) { null }
    }

    /** BARU: Timpa file dengan konten base64 (untuk revert & edit) */
    suspend fun updateFileContent(token: String, owner: String, repo: String, path: String, sha: String, contentBase64: String, message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("message", message)
                put("content", contentBase64)
                put("sha", sha)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/contents/$path")
                .header("Authorization", "token $token")
                .put(body)
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    /** BARU: Daftar artefak (hasil build APK) */
    suspend fun getArtifacts(token: String, owner: String, repo: String): List<ArtifactInfo>? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/artifacts?per_page=30").header("Authorization", "token $token").build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val arr = JSONObject(res.body?.string() ?: "").getJSONArray("artifacts")
                val list = mutableListOf<ArtifactInfo>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(ArtifactInfo(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        sizeBytes = o.optLong("size_in_bytes", 0),
                        expired = o.optBoolean("expired", false),
                        downloadUrl = o.optString("archive_download_url", "")
                    ))
                }
                list
            } else null
        } catch (e: Exception) { null }
    }

    /** BARU: Hapus artefak */
    suspend fun deleteArtifact(token: String, owner: String, repo: String, artifactId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/actions/artifacts/$artifactId")
                .header("Authorization", "token $token")
                .delete()
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    /** BARU: Daftar workflow runs (riwayat build) */
    suspend fun getWorkflowRuns(token: String, owner: String, repo: String): List<RunInfo>? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=20").header("Authorization", "token $token").build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val arr = JSONObject(res.body?.string() ?: "").getJSONArray("workflow_runs")
                val list = mutableListOf<RunInfo>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(RunInfo(
                        id = o.getLong("id"),
                        name = o.optString("name", "Build"),
                        status = o.optString("status", ""),
                        conclusion = o.optString("conclusion", "berjalan"),
                        htmlUrl = o.optString("html_url", ""),
                        createdAt = o.optString("created_at", "")
                    ))
                }
                list
            } else null
        } catch (e: Exception) { null }
    }

    /** BARU: Daftar rilis lengkap dengan ID (untuk hapus) */
    suspend fun getReleasesFull(token: String, owner: String, repo: String): List<ReleaseInfo>? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/repos/$owner/$repo/releases").header("Authorization", "token $token").build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val arr = JSONArray(res.body?.string() ?: "")
                val list = mutableListOf<ReleaseInfo>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(ReleaseInfo(
                        id = o.getLong("id"),
                        tag = o.getString("tag_name"),
                        name = o.optString("name", ""),
                        htmlUrl = o.optString("html_url", "")
                    ))
                }
                list
            } else null
        } catch (e: Exception) { null }
    }

    /** BARU: Hapus rilis */
    suspend fun deleteRelease(token: String, owner: String, repo: String, releaseId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/$releaseId")
                .header("Authorization", "token $token")
                .delete()
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    /** BARU: Buat rilis baru */
    suspend fun createRelease(token: String, owner: String, repo: String, tag: String, name: String, body: String): Long? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("tag_name", tag)
                put("name", name)
                put("body", body)
                put("draft", false)
                put("prerelease", false)
            }
            val reqBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases")
                .header("Authorization", "token $token")
                .post(reqBody)
                .build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) JSONObject(res.body?.string() ?: "").getLong("id") else null
        } catch (e: Exception) { null }
    }

    /** BARU: Unggah aset (APK) ke rilis */
    suspend fun uploadReleaseAsset(token: String, owner: String, repo: String, releaseId: Long, fileName: String, fileBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = fileBytes.toRequestBody("application/vnd.android.package-archive".toMediaType())
            val req = Request.Builder()
                .url("https://uploads.github.com/repos/$owner/$repo/releases/$releaseId/assets?name=$fileName")
                .header("Authorization", "token $token")
                .post(body)
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    /** BARU: Daftar commit repo lengkap */
    suspend fun getRepoCommitsFull(token: String, owner: String, repo: String): List<CommitInfo>? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/repos/$owner/$repo/commits?per_page=30").header("Authorization", "token $token").build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val arr = JSONArray(res.body?.string() ?: "")
                val list = mutableListOf<CommitInfo>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(CommitInfo(
                        sha = o.getString("sha"),
                        message = o.getJSONObject("commit").getString("message").replace("\n", " "),
                        date = o.getJSONObject("commit").getJSONObject("author").getString("date")
                    ))
                }
                list
            } else null
        } catch (e: Exception) { null }
    }

    /** BARU: Info repo lengkap (deskripsi, bintang, ukuran, branch) */
    suspend fun getRepoDetailInfo(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/repos/$owner/$repo").header("Authorization", "token $token").build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val o = JSONObject(res.body?.string() ?: "")
                buildString {
                    append("Nama: ${o.getString("full_name")}\n")
                    append("Deskripsi: ${o.optString("description", "-")}\n")
                    append("Status: ${if (o.getBoolean("private")) "Privat" else "Publik"}\n")
                    append("Branch utama: ${o.optString("default_branch", "main")}\n")
                    append("Bintang: ${o.optInt("stargazers_count", 0)}\n")
                    append("Fork: ${o.optInt("forks_count", 0)}\n")
                    append("Ukuran: ${o.optLong("size", 0)} KB\n")
                    append("Dibuat: ${o.optString("created_at", "-")}\n")
                    append("Update terakhir: ${o.optString("pushed_at", "-")}")
                }
            } else "Gagal mengambil info repo."
        } catch (e: Exception) { "Error: ${e.message}" }
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