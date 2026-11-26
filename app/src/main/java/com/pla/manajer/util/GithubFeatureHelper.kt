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
import java.util.zip.ZipInputStream

object GithubFeatureHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun uploadFile(token: String, owner: String, repo: String, path: String, fileBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            var sha: String? = null
            val getReq = Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "token $token").build()
            val getRes = client.newCall(getReq).execute()
            if (getRes.isSuccessful) sha = JSONObject(getRes.body?.string() ?: "").optString("sha", null)

            val encoded = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP)
            val json = JSONObject().apply { put("message", "Upload dari Aplikasi"); put("content", encoded); if (sha != null) put("sha", sha) }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val req = Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/$path").header("Authorization", "token $token").put(body).build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun getWorkflows(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs").header("Authorization", "token $token").build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val runs = JSONObject(res.body?.string() ?: "").getJSONArray("workflow_runs")
                val sb = StringBuilder()
                for (i in 0 until runs.length()) {
                    val run = runs.getJSONObject(i)
                    sb.append("• ${run.getString("name")} (${run.getString("status")}) - ${run.optString("conclusion", "Berjalan")}\n")
                }
                if (sb.isEmpty()) "Belum ada build." else sb.toString()
            } else "Gagal mengambil data build."
        } catch (e: Exception) { "Error: ${e.message}" }
    }

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
                if (sb.isEmpty()) "Belum ada commit." else sb.toString()
            } else "Gagal mengambil data commit."
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    suspend fun getReleases(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.github.com/repos/$owner/$repo/releases").header("Authorization", "token $token").build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val releases = JSONArray(res.body?.string() ?: "")
                val sb = StringBuilder()
                for (i in 0 until releases.length()) {
                    val r = releases.getJSONObject(i)
                    sb.append("• ${r.getString("tag_name")} (${r.optString("name", "")})\n")
                }
                if (sb.isEmpty()) "Belum ada rilis." else sb.toString()
            } else "Gagal mengambil data rilis."
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    /** BARU: Unduh artefak ZIP lalu ekstrak APK di dalamnya. Return path APK atau null */
    suspend fun downloadArtifactApk(context: Context, token: String, downloadUrl: String, artifactName: String): File? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(downloadUrl).header("Authorization", "token $token").build()
            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return@withContext null
            val bytes = res.body?.bytes() ?: return@withContext null

            val outDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "PLA_APK")
            if (!outDir.exists()) outDir.mkdirs()

            var apkFile: File? = null
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                        val outFile = File(outDir, "${artifactName}_${System.currentTimeMillis()}.apk")
                        outFile.outputStream().use { fos -> zis.copyTo(fos) }
                        apkFile = outFile
                        break
                    }
                    entry = zis.nextEntry
                }
            }
            apkFile
        } catch (e: Exception) { null }
    }

    /** BARU: Ambil log build dari run terakhir (potongan akhir yang relevan) */
    suspend fun getLatestRunLog(token: String, owner: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val runsReq = Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=1").header("Authorization", "token $token").build()
            val runsRes = client.newCall(runsReq).execute()
            if (!runsRes.isSuccessful) return@withContext "Gagal mengambil run."
            val runs = JSONObject(runsRes.body?.string() ?: "").getJSONArray("workflow_runs")
            if (runs.length() == 0) return@withContext "Belum ada build."
            val runId = runs.getJSONObject(0).getLong("id")

            val jobsReq = Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/runs/$runId/jobs").header("Authorization", "token $token").build()
            val jobsRes = client.newCall(jobsReq).execute()
            if (!jobsRes.isSuccessful) return@withContext "Gagal mengambil jobs."
            val jobs = JSONObject(jobsRes.body?.string() ?: "").getJSONArray("jobs")
            if (jobs.length() == 0) return@withContext "Tidak ada job."
            val jobId = jobs.getJSONObject(0).getLong("id")
            val jobName = jobs.getJSONObject(0).getString("name")

            val logReq = Request.Builder().url("https://api.github.com/repos/$owner/$repo/actions/jobs/$jobId/logs").header("Authorization", "token $token").build()
            val logRes = client.newCall(logReq).execute()
            if (!logRes.isSuccessful) return@withContext "Log belum tersedia (build mungkin masih berjalan)."
            val fullLog = logRes.body?.string() ?: ""
            // Ambil 4000 karakter terakhir agar tidak terlalu panjang untuk dibaca
            val tail = if (fullLog.length > 4000) fullLog.takeLast(4000) else fullLog
            "Job: $jobName\n\n...potongan akhir log...\n$tail"
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}