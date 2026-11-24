package com.pla.manajer

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.MimeTypeMap
import android.widget.CheckBox
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
    private var allFiles: List<File> = emptyList()
    private var currentFilteredFiles: List<File> = emptyList()

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
            onLong = { f -> enableSelectionMode(f) },
            onSelectToggle = { f -> toggleSelection(f) },
            onMoreOptions = { f -> showMoreMenu(f) }
        ).apply {
            setDateTimeMode(prefs.showDateTime)
        }

        b.recyclerMedia.layoutManager = LinearLayoutManager(this)
        b.recyclerMedia.adapter = adapter

        b.btnTopBack.setOnClickListener { handleBack() }
        b.selectionToolbar.visibility = View.GONE
        b.miniPlayerLayout.visibility = View.GONE

        // 1. TOMBOL SORTIR BERFUNGSI
        b.btnSort.setOnClickListener { showSortDialog() }

        // 2. KOLOM PENCARIAN OTOMATIS (Karena tidak ada di XML)
        // Kita tambahkan EditText secara programatik di atas RecyclerView
        setupSearchBar()

        b.btnDeleteSelected.setOnClickListener { deleteSelectedFiles() }
        b.btnCopySelected.setOnClickListener {
            val f = adapter.selectedFiles.firstOrNull()
            if (f != null) showCopyMoveDialog(f, false)
            closeSelectionMode()
        }
        b.btnMoveSelected.setOnClickListener {
            val f = adapter.selectedFiles.firstOrNull()
            if (f != null) showCopyMoveDialog(f, true)
            closeSelectionMode()
        }
        b.btnRenameSelected.setOnClickListener {
            adapter.selectedFiles.firstOrNull()?.let { renameDialog(it) }
            closeSelectionMode()
        }
        b.btnMoreSelected.setOnClickListener {
            adapter.selectedFiles.firstOrNull()?.let { showMoreMenu(it) }
            closeSelectionMode()
        }

        loadRoots()
    }

    private fun setupSearchBar() {
        val parent = b.recyclerMedia.parent as? android.view.ViewGroup ?: return
        val searchBar = EditText(this).apply {
            hint = "Cari folder atau file..."
            setSingleLine()
            setPadding(32, 16, 32, 16)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            
            // Logika: Saat diketik, filter langsung jalan
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    applyFilter(s?.toString() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            // Logika: Saat Enter ditekan, filter diterapkan dan teks DIHAPUS
            setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val q = v.text.toString()
                    applyFilter(q)
                    v.text = null // Teks otomatis hilang setelah search
                    true
                } else false
            }
        }
        // Masukkan search bar tepat di posisi recyclerMedia (indeks 1 di LinearLayout)
        val layout = parent as android.widget.LinearLayout
        layout.addView(searchBar, 1)
    }

    private fun applyFilter(query: String) {
        if (query.isEmpty()) {
            currentFilteredFiles = allFiles
        } else {
            currentFilteredFiles = allFiles.filter { it.name.lowercase().contains(query.lowercase()) }
        }
        val sorted = sortFiles(currentFilteredFiles)
        adapter.submit(sorted)
    }

    private fun sortFiles(files: List<File>): List<File> {
        return when (prefs.sortMode) {
            2 -> files.sortedWith(compareBy<File> { !it.isDirectory }.thenByDescending { it.lastModified() })
            3 -> files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.lastModified() })
            4 -> files.sortedWith(compareBy<File> { !it.isDirectory }.thenByDescending { it.length() })
            5 -> files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.length() })
            else -> files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    private fun showSortDialog() {
        val options = arrayOf("Nama (A-Z)","Nama (Z-A)","Tanggal (Terlama)","Tanggal (Terbaru)","Ukuran (Terkecil)","Ukuran (Terbesar)")
        AlertDialog.Builder(this).setTitle("Urutkan Berdasarkan")
            .setSingleChoiceItems(options, prefs.sortMode) { dialog, which ->
                prefs.sortMode = which
                applyFilter("") // Reset filter saat urutan diubah
                dialog.dismiss()
            }.setNegativeButton("Batal", null).show()
    }

    private fun loadRoots() {
        currentDir = null
        b.tvTitle.text = when(mediaType) {
            "AUDIO" -> "Audio"; "VIDEO" -> "Video"; "IMAGE" -> "Gambar"; else -> "Media"
        }
        
        lifecycleScope.launch {
            val mediaFolders = withContext(Dispatchers.IO) {
                val exts = when(mediaType) {
                    "AUDIO" -> audioExts; "VIDEO" -> videoExts; "IMAGE" -> imageExts; else -> emptyList()
                }
                val rootDir = Environment.getExternalStorageDirectory()
                val sdCard = getSdCard()
                val foundFolders = mutableSetOf<File>()

                fun scan(dir: File) {
                    val files = dir.listFiles() ?: return
                    var hasMedia = false
                    if (files.any { it.isFile && it.extension.lowercase() in exts }) hasMedia = true
                    if (hasMedia) foundFolders.add(dir)
                    
                    files.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "Android" }
                         .forEach { scan(it) }
                }

                scan(rootDir)
                sdCard?.let { scan(it) }
                foundFolders.toList()
            }
            
            allFiles = mediaFolders
            applyFilter("")
        }
    }

    private fun loadDir(dir: File) {
        currentDir = dir
        b.tvTitle.text = dir.name

        lifecycleScope.launch {
            allFiles = withContext(Dispatchers.IO) {
                val files = dir.listFiles() ?: return@withContext emptyList<File>()
                val exts = when(mediaType) {
                    "AUDIO" -> audioExts; "VIDEO" -> videoExts; "IMAGE" -> imageExts; else -> emptyList()
                }
                files.filter { it.isDirectory || (it.isFile && it.extension.lowercase() in exts) }
            }
            applyFilter("")
        }
    }

    private fun open(f: File) {
        if (f.isDirectory) {
            loadDir(f)
        } else {
            val ext = f.extension.lowercase()
            if (ext in audioExts) {
                val audioList = allFiles.filter { it.isFile && it.extension.lowercase() in audioExts }.map { it.absolutePath }
                val intent = Intent(this, AudioPlayerActivity::class.java)
                intent.putStringArrayListExtra("file_paths", ArrayList(audioList))
                intent.putExtra("start_path", f.absolutePath)
                startActivity(intent)
            } else {
                try {
                    val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Buka dengan"))
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal membuka file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteSelectedFiles() {
        val files = adapter.selectedFiles.toList()
        if (files.isEmpty()) return
        val cb = CheckBox(this).apply { text = "Pindah ke Sampah (Hapus centang = permanen)"; isChecked = true }
        AlertDialog.Builder(this)
            .setTitle("Hapus ${files.size} item?")
            .setView(cb)
            .setPositiveButton("Hapus") { _, _ ->
                if (cb.isChecked) deleteToTrash(files) else deletePermanent(files)
                closeSelectionMode()
            }.setNegativeButton("Batal", null).show()
    }

    private fun deleteToTrash(files: List<File>) {
        lifecycleScope.launch {
            notif.showFile("Menghapus", "Memindah ke Sampah...")
            val dao = (application as PlaApp).database.trashDao()
            withContext(Dispatchers.IO) {
                val trashDir = File(prefs.trashFolder).apply { mkdirs() }
                files.forEach { f ->
                    val target = File(trashDir, "${System.currentTimeMillis()}_${f.name}")
                    FileUtils.move(f, target)
                    dao.add(com.pla.manajer.data.TrashItem(originalPath = f.absolutePath, trashPath = target.absolutePath, name = f.name, isDir = f.isDirectory))
                }
            }
            if (currentDir != null) currentDir?.let { loadDir(it) } else loadRoots()
            notif.showFile("Selesai", "Dipindah ke Sampah")
        }
    }

    private fun deletePermanent(files: List<File>) {
        lifecycleScope.launch {
            notif.showFile("Menghapus", "Menghapus permanen...")
            withContext(Dispatchers.IO) { files.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() } }
            if (currentDir != null) currentDir?.let { loadDir(it) } else loadRoots()
            notif.showFile("Selesai", "Dihapus permanen")
        }
    }

    private fun showCopyMoveDialog(f: File, isMove: Boolean) {
        val options = arrayOf("Memori Internal", "Kartu SD")
        AlertDialog.Builder(this)
            .setTitle(if (isMove) "Pindah ke..." else "Salin ke...")
            .setItems(options) { _, which ->
                val destRoot = if (which == 0) Environment.getExternalStorageDirectory() else getSdCard()
                if (destRoot != null) performCopyMove(f, File(destRoot, f.name), isMove) else Toast.makeText(this, "Kartu SD tidak ditemukan", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun performCopyMove(src: File, dst: File, isMove: Boolean) {
        lifecycleScope.launch {
            notif.showFile(if (isMove) "Memindah" else "Menyalin", "${src.name} → ${dst.parentFile?.name}")
            try {
                withContext(Dispatchers.IO) { if (isMove) FileUtils.move(src, dst) else FileUtils.copy(src, dst) }
                if (currentDir != null) currentDir?.let { loadDir(it) } else loadRoots()
                notif.showFile("Selesai", "Berhasil")
            } catch (e: Exception) { Toast.makeText(this@MediaManagerActivity, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun enableSelectionMode(f: File) {
        adapter.isSelectionMode = true; adapter.selectedFiles.add(f)
        adapter.notifyDataSetChanged(); b.selectionToolbar.visibility = View.VISIBLE
    }

    private fun toggleSelection(f: File) {
        if (adapter.selectedFiles.contains(f)) adapter.selectedFiles.remove(f) else adapter.selectedFiles.add(f)
        adapter.isSelectionMode = adapter.selectedFiles.isNotEmpty()
        if (adapter.selectedFiles.isEmpty()) closeSelectionMode() else adapter.notifyDataSetChanged()
    }

    private fun closeSelectionMode() {
        adapter.isSelectionMode = false; adapter.selectedFiles.clear()
        adapter.notifyDataSetChanged(); b.selectionToolbar.visibility = View.GONE
    }

    private fun showMoreMenu(f: File) {
        val options = mutableListOf("Bagikan", "Ganti Nama")
        if (mediaType == "AUDIO") options.add(0, "Edit Audio")
        if (mediaType == "IMAGE") options.add(0, "Edit Gambar")

        AlertDialog.Builder(this)
            .setTitle(f.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Edit Audio" -> startActivity(Intent(this, AudioEditorActivity::class.java).putExtra("file_path", f.absolutePath))
                    "Edit Gambar" -> startActivity(Intent(this, ImageEditorActivity::class.java).putExtra("file_path", f.absolutePath))
                    "Bagikan" -> shareFile(f)
                    "Ganti Nama" -> renameDialog(f)
                }
            }.show()
    }

    private fun shareFile(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Bagikan via"))
        } catch (e: Exception) { Toast.makeText(this, "Gagal", Toast.LENGTH_SHORT).show() }
    }

    private fun renameDialog(f: File) {
        val input = EditText(this).apply { setText(f.name) }
        AlertDialog.Builder(this).setTitle("Ganti Nama").setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newFile = File(f.parentFile, input.text.toString())
                if (f.renameTo(newFile)) {
                    if (currentDir != null) currentDir?.let { loadDir(it) } else loadRoots()
                } else Toast.makeText(this, "Gagal rename", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Batal", null).show()
    }

    private fun handleBack() {
        if (currentDir != null) {
            currentDir = null
            loadRoots()
        } else finish()
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { handleBack() }
}