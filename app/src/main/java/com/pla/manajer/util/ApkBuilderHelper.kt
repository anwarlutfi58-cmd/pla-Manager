package com.pla.manajer.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApkBuilderHelper {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build()

    // Fungsi untuk membuat Workflow YAML dan mengunggahnya ke GitHub
    suspend fun setupBuildWorkflow(token: String, owner: String, repo: String, useCustomKey: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val yamlContent = """
                name: Build Android APK
                on:
                  push:
                    branches: [ "main", "master" ]
                  workflow_dispatch:
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                    - uses: actions/checkout@v3
                    - name: set up JDK 17
                      uses: actions/setup-java@v3
                      with:
                        java-version: '17'
                        distribution: 'temurin'
                    - name: Build with Gradle
                      run: |
                        chmod +x ./gradlew
                        ./gradlew assembleRelease
                    - name: Sign APK
                      uses: r0adkll/sign-android-release@v1
                      id: sign_app
                      with:
                        releaseDirectory: app/build/outputs/apk/release
                        signingKeyBase64: ${'$'}{{ secrets.KEYSTORE_BASE64 }}
                        alias: ${'$'}{{ secrets.KEY_ALIAS }}
                        keyStorePassword: ${'$'}{{ secrets.KEY_STORE_PASSWORD }}
                        keyPassword: ${'$'}{{ secrets.KEY_PASSWORD }}
                    - name: Create Release
                      uses: softprops/action-gh-release@v1
                      with:
                        files: ${'$'}{{steps.sign_app.outputs.signedReleaseFile}}
                      env:
                        GITHUB_TOKEN: ${'$'}{{ secrets.GITHUB_TOKEN }}
            """.trimIndent()

            // Base64 encode YAML
            val encoded = android.util.Base64.encodeToString(yamlContent.toByteArray(), android.util.Base64.DEFAULT).replace("\n", "")
            
            // Cek apakah file sudah ada
            var sha: String? = null
            val getReq = Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/.github/workflows/build.yml").header("Authorization", "token $token").build()
            val getRes = client.newCall(getReq).execute()
            if (getRes.isSuccessful) sha = JSONObject(getRes.body?.string() ?: "").optString("sha", null)

            val json = JSONObject().apply {
                put("message", "Setup APK Builder Workflow")
                put("content", encoded)
                if (sha != null) put("sha", sha)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val putReq = Request.Builder().url("https://api.github.com/repos/$owner/$repo/contents/.github/workflows/build.yml").header("Authorization", "token $token").put(body).build()
            
            client.newCall(putReq).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    // Fungsi untuk membuat Keystore baru (Membuat kunci sendiri)
    fun generateKeystoreLocally(context: Context, alias: String, pass: String): Boolean {
        // Catatan: Di Android, kita menggunakan keytool via terminal/FFmpeg (jika tersedia) atau library BouncyCastle.
        // Untuk simplifikasi tanpa library berat, fungsi ini akan menyimpan data ke SharedPreferences 
        // dan memberi tahu user bahwa file .jks akan dibuat di PC (karena Android sangat sulit membuat .jks murni tanpa library).
        // Kita simpan konfigurasinya:
        val prefs = context.getSharedPreferences("PlaPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("keystore_alias", alias)
            putString("keystore_pass", pass)
            putBoolean("has_custom_keystore", true)
            apply()
        }
        return true
    }
}