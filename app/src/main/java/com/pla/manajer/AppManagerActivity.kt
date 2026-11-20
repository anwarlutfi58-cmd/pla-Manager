package com.pla.manajer

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.pla.manajer.adapter.AppAdapter
import com.pla.manajer.databinding.ActivityAppManagerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppManagerActivity : AppCompatActivity() {
    private lateinit var b: ActivityAppManagerBinding
    private lateinit var adapter: AppAdapter
    
    private var allApps: List<ApplicationInfo> = emptyList()
    private var userApps: List<ApplicationInfo> = emptyList()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityAppManagerBinding.inflate(layoutInflater)
        setContentView(b.root)
        
        b.fabBack.setOnClickListener { finish() }

        adapter = AppAdapter(packageManager) { app ->
            // Membuka aplikasi saat diklik
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "Aplikasi ini tidak bisa dibuka", Toast.LENGTH_SHORT).show()
            }
        }
        
        b.recyclerApps.layoutManager = LinearLayoutManager(this)
        b.recyclerApps.adapter = adapter

        // Setup Tab
        b.tabLayout.addTab(b.tabLayout.newTab().setText("SEMUA"))
        b.tabLayout.addTab(b.tabLayout.newTab().setText("DIUNDUH"))

        b.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    adapter.submit(allApps) // Tampilkan semua
                } else {
                    adapter.submit(userApps) // Tampilkan aplikasi unduhan (bukan sistem)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadApps()
    }

    private fun loadApps() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val pm = packageManager
                // Ambil semua aplikasi yang terinstal
                val apps = pm.getInstalledApplications(0)
                
                allApps = apps.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
                
                // Filter: Hanya ambil yang bukan aplikasi sistem (Aplikasi yang diunduh)
                userApps = apps.filter { 
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 
                }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            }
            
            // Tampilkan data pertama kali (Tab "SEMUA" aktif)
            if (b.tabLayout.selectedTabPosition == 0 || b.tabLayout.selectedTabPosition == -1) {
                adapter.submit(allApps)
            } else {
                adapter.submit(userApps)
            }
        }
    }
}