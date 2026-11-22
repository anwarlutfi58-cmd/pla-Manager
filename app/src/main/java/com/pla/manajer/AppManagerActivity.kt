package com.pla.manajer

import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.pla.manajer.adapter.AppAdapter
import com.pla.manajer.databinding.ActivityAppManagerBinding
import com.pla.manajer.databinding.DialogAppActionBinding
import com.pla.manajer.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class AppManagerActivity : AppCompatActivity() {
    private lateinit var b: ActivityAppManagerBinding
    private lateinit var adapter: AppAdapter
    
    private var allApps: List<ApplicationInfo> = emptyList()
    private var userApps: List<ApplicationInfo> = emptyList()
    private var showGithubTab = false

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAppManagerBinding.inflate(layoutInflater)
        setContentView(b.root)
        
        b.fabBack.setOnClickListener { finish() }

        // Cek apakah Tab Buat APK diaktifkan di Pengaturan
        val prefs = Prefs(this)
        showGithubTab = prefs.showGithubBuilder

        adapter = AppAdapter(packageManager) { app ->
            showActionDialog(app)
        }
        
        b.recyclerApps.layoutManager = LinearLayoutManager(this)
        b.recyclerApps.adapter = adapter

        // Setup Tab
        b.tabLayout.addTab(b.tabLayout.newTab().setText("SEMUA"))
        b.tabLayout.addTab(b.tabLayout.newTab().setText("DIUNDUH"))
        
        // Jika pengaturan ON, tambahkan Tab Buat APK
        if (showGithubTab) {
            b.tabLayout.addTab(b.tabLayout.newTab().setText("BUAT APK"))
        }

        b.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> adapter.submit(allApps)
                    1 -> adapter.submit(userApps)
                    2 -> {
                        // Jika Tab ke-3 (Buat APK) dipilih, buka layar Github Builder
                        if (showGithubTab) {
                            startActivity(Intent(this@AppManagerActivity, GithubBuilderActivity::class.java))
                            // Kembalikan posisi tab ke awal setelah dibuka
                            b.tabLayout.getTabAt(0)?.select()
                        }
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Jika tab yang sama diklik lagi, buka Github Builder
                if (tab?.position == 2 && showGithubTab) {
                    startActivity(Intent(this@AppManagerActivity, GithubBuilderActivity::class.java))
                }
            }
        })

        loadApps()
    }

    private fun loadApps() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val pm = packageManager
                val apps = pm.getInstalledApplications(0)
                
                allApps = apps.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
                userApps = apps.filter { 
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 
                }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            }
            
            if (b.tabLayout.selectedTabPosition == 0 || b.tabLayout.selectedTabPosition == -1) {
                adapter.submit(allApps)
            } else if (b.tabLayout.selectedTabPosition == 1) {
                adapter.submit(userApps)
            }
        }
    }

    private fun showActionDialog(app: ApplicationInfo) {
        val dialogBinding = DialogAppActionBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle(packageManager.getApplicationLabel(app))
            .setView(dialogBinding.root)
            .setNegativeButton("Tutup", null)
            .create()

        dialogBinding.actionOpen.setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) startActivity(intent)
            else Toast.makeText(this, "Aplikasi tidak bisa dibuka", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // AKSI BAGIKAN APK (Diperbaiki: Copy ke cache dulu)
        dialogBinding.actionShare.setOnClickListener {
            dialog.dismiss()
            shareApk(app)
        }

        dialogBinding.actionDelete.setOnClickListener {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:${app.packageName}")
            startActivity(intent)
            dialog.dismiss()
        }

        dialogBinding.actionBackup.setOnClickListener {
            dialog.dismiss()
            backupApk(app)
        }

        dialog.show()
    }

    private fun shareApk(app: ApplicationInfo) {
        lifecycleScope.launch {
            try {
                val apkPath = app.sourceDir
                val apkFile = File(apkPath)
                val appName = packageManager.getApplicationLabel(app).toString().replace(" ", "_")
                val cacheApk = File(cacheDir, "$appName.apk")
                
                withContext(Dispatchers.IO) {
                    FileInputStream(apkFile).use { input ->
                        FileOutputStream(cacheApk).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                val uri = FileProvider.getUriForFile(
                    this@AppManagerActivity,
                    "$packageName.provider",
                    cacheApk
                )
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("APK", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Bagikan APK via"))
                
            } catch (e: Exception) {
                Toast.makeText(this@AppManagerActivity, "Gagal membagikan APK: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun backupApk(app: ApplicationInfo) {
        val appName = packageManager.getApplicationLabel(app).toString().replace(" ", "_")
        val apkPath = app.sourceDir
        val apkFile = File(apkPath)

        val backupDir = File(Environment.getExternalStorageDirectory(), "PlaProjects/Backup_Aplikasi")
        if (!backupDir.exists()) backupDir.mkdirs()

        val destFile = File(backupDir, "$appName.apk")

        Toast.makeText(this, "Mencadangkan $appName...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    FileInputStream(apkFile).use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                Toast.makeText(this@AppManagerActivity, "Berhasil dicadangkan ke:\n${destFile.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@AppManagerActivity, "Gagal mencadangkan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}