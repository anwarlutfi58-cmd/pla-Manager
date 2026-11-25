package com.pla.manajer

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var tvInternalSize: TextView
    private lateinit var tvSdSize: TextView
    private lateinit var tvImageStats: TextView
    private lateinit var tvAudioStats: TextView
    private lateinit var tvVideoStats: TextView
    private lateinit var tvDocStats: TextView
    private lateinit var tvDownloadStats: TextView
    private lateinit var tvAppStats: TextView

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)

        tvInternalSize = findViewById(R.id.tvInternalSize)
        tvSdSize = findViewById(R.id.tvSdSize)
        tvImageStats = findViewById(R.id.tvImageStats)
        tvAudioStats = findViewById(R.id.tvAudioStats)
        tvVideoStats = findViewById(R.id.tvVideoStats)
        tvDocStats = findViewById(R.id.tvDocStats)
        tvDownloadStats = findViewById(R.id.tvDownloadStats)
        tvAppStats = findViewById(R.id.tvAppStats)

        findViewById<View>(R.id.cardInternal).setOnClickListener {
            startActivity(Intent(this, FileManagerActivity::class.java).apply {
                putExtra("initial_path", Environment.getExternalStorageDirectory().absolutePath)
            })
        }
        findViewById<View>(R.id.cardSdCard).setOnClickListener {
            val dirs = getExternalFilesDirs(null)
            if (dirs.size > 1 && dirs[1] != null) {
                var sdPath = dirs[1]!!.absolutePath
                val idx = sdPath.indexOf("/Android")
                if (idx != -1) sdPath = sdPath.substring(0, idx)
                startActivity(Intent(this, FileManagerActivity::class.java).apply {
                    putExtra("initial_path", sdPath)
                })
            }
        }

        findViewById<View>(R.id.cardImages).setOnClickListener { openMedia("IMAGE") }
        findViewById<View>(R.id.cardAudio).setOnClickListener { openMedia("AUDIO") }
        findViewById<View>(R.id.cardVideos).setOnClickListener { openMedia("VIDEO") }
        findViewById<View>(R.id.cardDocuments).setOnClickListener { openCategory(Environment.DIRECTORY_DOCUMENTS) }
        findViewById<View>(R.id.cardDownloads).setOnClickListener { openCategory(Environment.DIRECTORY_DOWNLOADS) }
        findViewById<View>(R.id.cardApps).setOnClickListener {
            startActivity(Intent(this, AppManagerActivity::class.java))
        }

        findViewById<View>(R.id.btnFileManager).setOnClickListener {
            startActivity(Intent(this, FileManagerActivity::class.java))
        }

        loadStatistics()
    }

    private fun openMedia(type: String) {
        val intent = Intent(this, MediaManagerActivity::class.java)
        intent.putExtra("media_type", type)
        startActivity(intent)
    }

    private fun openCategory(dirName: String) {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val intent = Intent(this, FileManagerActivity::class.java)
        intent.putExtra("initial_path", "$rootPath/$dirName")
        startActivity(intent)
    }

    private fun loadStatistics() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
                    val total = stat.blockCountLong * stat.blockSizeLong
                    val free = stat.availableBlocksLong * stat.blockSizeLong
                    val used = total - free
                    withContext(Dispatchers.Main) {
                        tvInternalSize.text = "${formatSize(used)} / ${formatSize(total)}"
                    }
                } catch (_: Exception) {}

                try {
                    val dirs = getExternalFilesDirs(null)
                    if (dirs.size > 1 && dirs[1] != null) {
                        var sdPath = dirs[1]!!.absolutePath
                        val idx = sdPath.indexOf("/Android")
                        if (idx != -1) sdPath = sdPath.substring(0, idx)
                        val stat = StatFs(sdPath)
                        val total = stat.blockCountLong * stat.blockSizeLong
                        val free = stat.availableBlocksLong * stat.blockSizeLong
                        val used = total - free
                        withContext(Dispatchers.Main) {
                            tvSdSize.text = "${formatSize(used)} / ${formatSize(total)}"
                        }
                    } else {
                        withContext(Dispatchers.Main) { tvSdSize.text = "Tidak terdeteksi" }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) { tvSdSize.text = "Tidak terdeteksi" }
                }

                val imageExts = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
                val audioExts = listOf("mp3", "wav", "aac", "m4a", "ogg", "flac", "opus")
                val videoExts = listOf("mp4", "mkv", "avi", "mov", "webm")
                val docExts = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")
                val apkExts = listOf("apk")

                var imageSize = 0L; var imageCount = 0
                var audioSize = 0L; var audioCount = 0
                var videoSize = 0L; var videoCount = 0
                var docSize = 0L; var docCount = 0
                var dlSize = 0L; var dlCount = 0
                var appSize = 0L; var appCount = 0

                val root = Environment.getExternalStorageDirectory()
                fun scanDir(dir: File) {
                    val files = dir.listFiles() ?: return
                    for (f in files) {
                        if (f.isDirectory && !f.name.startsWith(".") && f.name != "Android") {
                            scanDir(f)
                        } else if (f.isFile) {
                            val ext = f.extension.lowercase()
                            when {
                                ext in imageExts -> { imageSize += f.length(); imageCount++ }
                                ext in audioExts -> { audioSize += f.length(); audioCount++ }
                                ext in videoExts -> { videoSize += f.length(); videoCount++ }
                                ext in docExts -> { docSize += f.length(); docCount++ }
                                ext in apkExts -> { appSize += f.length(); appCount++ }
                            }
                            if (f.absolutePath.contains("/Download/", ignoreCase = true)) {
                                dlSize += f.length(); dlCount++
                            }
                        }
                    }
                }
                scanDir(root)

                withContext(Dispatchers.Main) {
                    tvImageStats.text = "${formatSize(imageSize)} ($imageCount)"
                    tvAudioStats.text = "${formatSize(audioSize)} ($audioCount)"
                    tvVideoStats.text = "${formatSize(videoSize)} ($videoCount)"
                    tvDocStats.text = "${formatSize(docSize)} ($docCount)"
                    tvDownloadStats.text = "${formatSize(dlSize)} ($dlCount)"
                    tvAppStats.text = "${formatSize(appSize)} ($appCount)"
                }
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}