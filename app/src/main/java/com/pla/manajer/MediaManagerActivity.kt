package com.pla.manajer

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pla.manajer.adapter.FileAdapter
import com.pla.manajer.databinding.ActivityMediaManagerBinding
import com.pla.manajer.util.FileUtils
import com.pla.manajer.util.NotificationHelper
import com.pla.manajer.util.Prefs
import com.pla.manajer.util.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaManagerActivity : AppCompatActivity() {
    private lateinit var b: ActivityMediaManagerBinding
    private lateinit var adapter: FileAdapter
    private lateinit var prefs: Prefs
    private lateinit var notif: NotificationHelper
    private var mediaType: String = "AUDIO"
    private var currentDir: File? = null
    private var allItems: List<File> = emptyList()

    private val audioExts = listOf("mp3", "wav", "aac", "m4a", "ogg", "flac", "opus")
    private val videoExts = listOf("mp4", "mkv", "avi", "mov", "webm")
    private val imageExts = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMediaManagerBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)
        notif = NotificationHelper(this)

        mediaType = intent.getStringExtra("media_type") ?: "AUDIO"

        adapter = FileAdapter(
            onOpen = { f -> open(f) },
            onLong = { f -> /* Abaikan tekan lama untuk folder */ },
            onSelectToggle = { /* Abaikan checkbox */ },
            onMoreOptions = { f -> showMoreMenu(f) }
        ).apply { setDateTimeMode(prefs.showDateTime) }

        b.recyclerMedia.layoutManager = LinearLayoutManager(this)
        b.recyclerMedia.adapter = adapter

        b.btnTopBack.setOnClickListener { handleBack() }
        b.selectionToolbar.visibility = View.GONE // Sembunyikan toolbar centang
        b.miniPlayerLayout.visibility = View.GONE
        b.btnSort.setOnClickListener { showSortDialog() }
        setupSearchBar()
        
        // Sembunyikan tombol hapus/salin massal di bawah
        b.btnDeleteSelected.visibility = View.GONE
        b.btnCopySelected.visibility = View.GONE
        b.btnMoveSelected.visibility = View.GONE
        b.btnRenameSelected.visibility = View.GONE
        b.btnMoreSelected.visibility = View.GONE

        loadRoots()
    }

    private fun setupSearchBar() {
        val parent = b.recyclerMedia.parent as? android.view.ViewGroup ?: return
        val searchBar = EditText(this).apply {
            hint = "Cari..."
            setSingleLine()
            setPadding(32, 16, 32, 16)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilter(s?.toString() ?: "") }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            setOnEditorActionListener { v, actionId, event ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { v.text = null; true } else false
            }
        }
        (parent as android.widget.LinearLayout).addView(searchBar, 1)
    }

    private fun loadRoots() {
        currentDir = null
        b.tvTitle.text = when(mediaType) { "AUDIO" -> "Folder Audio"; "VIDEO" -> "Folder Video"; "IMAGE" -> "Folder Gambar"; else -> "Folder Media" }
        
        lifecycleScope.launch {
            val folders = withContext(Dispatchers.IO) {
                val exts = when(mediaType) { "AUDIO" -> audioExts; "VIDEO" -> videoExts; "IMAGE" -> imageExts; else -> emptyList() }
                val roots = mutableListOf<File>()
                roots.add(Environment.getExternalStorageDirectory())
                getSdCard()?.let { roots.add(it) }
                val foundFolders = mutableSetOf<File>()

                fun scanForMediaFolders(dir: File) {
                    val files = dir.listFiles() ?: return
                    if (files.any { it.isFile && it.extension.lowercase() in exts }) {
                        if (!dir.name.startsWith(".") && dir.name != "Android") foundFolders.add(dir)
                    }
                    files.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "Android" }.forEach { scanForMediaFolders(it) }
                }
                roots.forEach { scanForMediaFolders(it) }
                foundFolders.sortedBy { it.name.lowercase() }
            }
            allItems = folders
            applyFilter("")
        }
    }

    private fun loadDir(dir: File) {
        currentDir = dir
        b.tvTitle.text = dir.name

        lifecycleScope.launch {
            allItems = withContext(Dispatchers.IO) {
                val files = dir.listFiles() ?: return@withContext emptyList<File>()
                val exts = when(mediaType) { "AUDIO" -> audioExts; "VIDEO" -> videoExts; "IMAGE" -> imageExts; else -> emptyList() }
                // Hanya tampilkan file (bukan folder)
                files.filter { it.isFile && it.extension.lowercase() in exts }
            }
            applyFilter("")
        }
    }

    private fun applyFilter(query: String) {
        val filtered = if (query.isEmpty()) allItems else allItems.filter { it.name.lowercase().contains(query.lowercase()) }
        adapter.submit(sortFiles(filtered))
    }

    private fun sortFiles(files: List<File>): List<File> {
        return when (prefs.sortMode) {
            1 -> files.sortedByDescending { it.name.lowercase() }
            2 -> files.sortedByDescending { it.lastModified() }
            3 -> files.sortedBy { it.lastModified() }
            4 -> files.sortedByDescending { it.length() }
            5 -> files.sortedBy { it.length() }
            else -> files.sortedBy { it.name.lowercase() }
        }
    }

    private fun showSortDialog() {
        val options = arrayOf("Nama (A-Z)", "Nama (Z-A)", "Tanggal (Terbaru)", "Tanggal (Terlama)", "Ukuran (Terbesar)", "Ukuran (Terkecil)")
        AlertDialog.Builder(this).setTitle("Urutkan").setSingleChoiceItems(options, prefs.sortMode) { dialog, which ->
            prefs.sortMode = which; applyFilter(""); dialog.dismiss()
        }.setNegativeButton("Batal", null).show()
    }

    private fun open(f: File) {
        if (f.isDirectory) {
            loadDir(f)
        } else {
            val ext = f.extension.lowercase()
            if (ext in audioExts) {
                val audioList = allItems.filter { it.isFile && it.extension.lowercase() in audioExts }.map { it.absolutePath }
                val intent = Intent(this, AudioPlayerActivity::class.java)
                intent.putStringArrayListExtra("file_paths", ArrayList(audioList))
                intent.putExtra("start_path", f.absolutePath)
                startActivity(intent)
            } else {
                try {
                    val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Buka dengan"))
                } catch (e: Exception) { Toast.makeText(this, "Gagal buka", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun handleBack() {
        if (currentDir != null) { currentDir = null; loadRoots() } else finish()
    }

    override fun onBackPressed() { handleBack() }

    private fun showMoreMenu(f: File) {
        // JIKA YANG DIKLIK ADALAH FOLDER, TIDAK ADA MENU OPSI
        if (f.isDirectory) return
        
        // JIKA FILE, HANYA BAGIKAN DAN ZIP
        val options = arrayOf("Bagikan ke Aplikasi Lain", "Jadikan File ZIP")
        AlertDialog.Builder(this).setTitle(f.name).setItems(options) { _, which ->
            when (options[which]) {
                "Bagikan ke Aplikasi Lain" -> ShareHelper.shareFile(this, f)
                "Jadikan File ZIP" -> zipFile(f)
            }
        }.show()
    }

    private fun zipFile(f: File) {
        lifecycleScope.launch {
            notif.showFile("Kompres ZIP", "Mengompres ${f.name}...")
            val out = File(f.parentFile, f.name + ".zip")
            withContext(Dispatchers.IO) { FileUtils.zip(f, out) }
            if (currentDir != null) currentDir?.let { loadDir(it) } else loadRoots()
            notif.showFile("Kompres selesai", "Berhasil")
        }
    }

    private fun getSdCard(): File? {
        val dirs = getExternalFilesDirs(null)
        if (dirs.size > 1 && dirs[1] != null) {
            var sd = dirs[1]!!.absolutePath
            val idx = sd.indexOf("/Android")
            if (idx != -1) sd = sd.substring(0, idx)
            return File(sd)
        }
        return null
    }
}