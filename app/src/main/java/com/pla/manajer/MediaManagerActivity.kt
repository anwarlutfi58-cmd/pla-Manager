package com.pla.manajer

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pla.manajer.adapter.FileAdapter
import com.pla.manajer.databinding.ActivityMediaManagerBinding
import com.pla.manajer.util.FileUtils
import com.pla.manajer.util.MediaBrowser
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
    private var allMediaFiles: List<File> = emptyList()
    private var currentItems: List<File> = emptyList()
    private var searchQuery: String = ""
    private lateinit var crumbBar: LinearLayout
    private lateinit var searchBar: EditText

    private val exts get() = MediaBrowser.extsFor(mediaType)

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMediaManagerBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)
        notif = NotificationHelper(this)

        mediaType = intent.getStringExtra("media_type") ?: "AUDIO"

        b.tvTitle.text = when (mediaType) {
            "AUDIO" -> "Audio"; "VIDEO" -> "Video"; "IMAGE" -> "Gambar"; "DOC" -> "Dokumen"; else -> "Media"
        }

        // ATURAN TEGAS: kategori = TANPA centang & titik tiga
        adapter = FileAdapter(
            onOpen = { f -> if (!adapter.isSelectionMode) open(f) },
            onLong = { f -> enableSelection(f) },
            onSelectToggle = { f -> toggleSelection(f) },
            onMoreOptions = { }
        ).apply {
            setDateTimeMode(prefs.showDateTime)
            hideCheckboxAndMore = true   // <-- tidak ada centang & titik tiga di kategori
        }

        b.recyclerMedia.layoutManager = LinearLayoutManager(this)
        b.recyclerMedia.adapter = adapter

        // Toolbar bawah: muncul HANYA saat menandai (tekan tahan file)
        b.selectionToolbar.visibility = View.GONE
        b.miniPlayerLayout.visibility = View.GONE

        b.btnDeleteSelected.setOnClickListener {
            val files = adapter.selectedFiles.toList()
            if (files.isNotEmpty()) confirmDelete(files)
        }
        b.btnCopySelected.setOnClickListener {
            val files = adapter.selectedFiles.toList()
            if (files.isNotEmpty()) copyOrMove(files, false)
        }
        b.btnMoveSelected.setOnClickListener {
            val files = adapter.selectedFiles.toList()
            if (files.isNotEmpty()) copyOrMove(files, true)
        }
        b.btnRenameSelected.setOnClickListener {
            adapter.selectedFiles.firstOrNull()?.let { renameFile(it) }
        }
        b.btnMoreSelected.setOnClickListener {
            val files = adapter.selectedFiles.toList()
            if (files.size == 1) showSmartMenu(files[0]) else if (files.size > 1) showMultiMenu(files)
        }

        // Breadcrumb rumah > folder
        crumbBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 12, 24, 12)
        }
        val parent = b.recyclerMedia.parent as LinearLayout
        parent.addView(crumbBar, 1)

        // Kolom pencarian
        searchBar = EditText(this).apply {
            hint = "Cari folder atau file..."
            setSingleLine()
            textSize = 14f
            setPadding(32, 12, 32, 12)
            visibility = View.GONE
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {
                    searchQuery = s?.toString() ?: ""
                    applyFilter()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        parent.addView(searchBar, 2)

        val btnSearch = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Cari"
            layoutParams = LinearLayout.LayoutParams(100, 100)
            setOnClickListener {
                searchBar.visibility = if (searchBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                if (searchBar.visibility == View.VISIBLE) searchBar.requestFocus()
            }
        }
        (b.btnSort.parent as LinearLayout).addView(btnSearch, 2)

        b.btnTopBack.setOnClickListener { handleBack() }
        b.btnSort.setOnClickListener { showSortDialog() }
        b.btnSort.contentDescription = "Urutkan"

        loadRoots()
    }

    private fun updateCrumb() {
        crumbBar.removeAllViews()
        crumbBar.addView(TextView(this).apply {
            text = "🏠"
            textSize = 20f
            contentDescription = "Kembali ke daftar folder"
            setOnClickListener { loadRoots() }
        })
        val dir = currentDir ?: return
        crumbBar.addView(TextView(this).apply { text = "  ›  "; textSize = 18f; setTextColor(Color.GRAY) })
        crumbBar.addView(TextView(this).apply {
            text = "📁 ${dir.name}"
            textSize = 15f
            setTextColor(Color.parseColor("#00BFA5"))
        })
    }

    /** BERANDA KATEGORI: semua folder yang berisi media berdiri MANDIRI & sejajar */
    private fun loadRoots() {
        currentDir = null
        closeSelection()
        searchQuery = ""
        searchBar.setText("")
        updateCrumb()
        lifecycleScope.launch {
            allMediaFiles = withContext(Dispatchers.IO) {
                MediaBrowser.queryAllMediaFiles(this@MediaManagerActivity, mediaType)
            }
            val (folders, counts) = withContext(Dispatchers.IO) {
                val map = LinkedHashMap<String, File>()
                val cnt = HashMap<String, Int>()
                for (f in allMediaFiles) {
                    val p = f.parentFile ?: continue
                    if (p.name.startsWith(".")) continue
                    val key = p.absolutePath
                    if (!map.containsKey(key)) map[key] = p
                    cnt[key] = (cnt[key] ?: 0) + 1
                }
                Pair(map.values.sortedBy { it.name.lowercase() }, cnt)
            }
            currentItems = folders
            adapter.fileCounts = counts
            adapter.displayNames = folders.associate { it.absolutePath to "${it.name}  (${counts[it.absolutePath] ?: 0})" }
            applyFilter()
        }
    }

    /** MASUK FOLDER: HANYA file media langsung, TIDAK ADA subfolder */
    private fun loadDir(dir: File) {
        currentDir = dir
        closeSelection()
        searchQuery = ""
        searchBar.setText("")
        updateCrumb()
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                allMediaFiles.filter { it.parentFile?.absolutePath == dir.absolutePath }
                    .sortedBy { it.name.lowercase() }
            }
            currentItems = files
            adapter.fileCounts = emptyMap()
            adapter.displayNames = emptyMap()
            applyFilter()
        }
    }

    private fun applyFilter() {
        val q = searchQuery.lowercase()
        val filtered = if (q.isEmpty()) currentItems else currentItems.filter { it.name.lowercase().contains(q) }
        val sorted = when (prefs.sortMode) {
            1 -> filtered.sortedByDescending { it.name.lowercase() }
            2 -> filtered.sortedByDescending { it.lastModified() }
            3 -> filtered.sortedBy { it.lastModified() }
            4 -> filtered.sortedByDescending { it.length() }
            5 -> filtered.sortedBy { it.length() }
            else -> filtered.sortedBy { it.name.lowercase() }
        }
        adapter.submit(sorted)
    }

    private fun showSortDialog() {
        val options = arrayOf("Nama (A-Z)", "Nama (Z-A)", "Tanggal (Terbaru)", "Tanggal (Terlama)", "Ukuran (Terbesar)", "Ukuran (Terkecil)")
        AlertDialog.Builder(this).setTitle("Urutkan").setSingleChoiceItems(options, prefs.sortMode) { dialog, which ->
            prefs.sortMode = which; applyFilter(); dialog.dismiss()
        }.setNegativeButton("Batal", null).show()
    }

    // ===== SELEKSI (tekan tahan file → toolbar bawah) =====
    private fun enableSelection(f: File) {
        if (f.isDirectory) return  // folder terlarang ditandai
        adapter.isSelectionMode = true
        adapter.selectedFiles.add(f)
        adapter.notifyDataSetChanged()
        updateSelectionUi()
    }

    private fun toggleSelection(f: File) {
        if (f.isDirectory) return
        if (adapter.selectedFiles.contains(f)) adapter.selectedFiles.remove(f) else adapter.selectedFiles.add(f)
        adapter.isSelectionMode = adapter.selectedFiles.isNotEmpty()
        adapter.notifyDataSetChanged()
        updateSelectionUi()
    }

    private fun closeSelection() {
        adapter.isSelectionMode = false
        adapter.selectedFiles.clear()
        adapter.notifyDataSetChanged()
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        val active = adapter.selectedFiles.isNotEmpty()
        b.selectionToolbar.visibility = if (active) View.VISIBLE else View.GONE
    }

    // ===== AKSI TOOLBAR BAWAH =====
    private fun confirmDelete(files: List<File>) {
        AlertDialog.Builder(this).setTitle("Hapus ${files.size} file?")
            .setMessage("File akan dihapus permanen dari penyimpanan.")
            .setPositiveButton("HAPUS") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { files.forEach { it.delete() } }
                    toast("${files.size} file dihapus")
                    closeSelection()
                    currentDir?.let { loadDir(it) } ?: loadRoots()
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun copyOrMove(files: List<File>, isMove: Boolean) {
        val options = arrayOf("Memori Internal", "Kartu SD", "Folder Unduhan")
        AlertDialog.Builder(this)
            .setTitle(if (isMove) "Pindah ${files.size} file ke..." else "Salin ${files.size} file ke...")
            .setItems(options) { _, which ->
                val dest = when (which) {
                    0 -> android.os.Environment.getExternalStorageDirectory()
                    1 -> MediaBrowser.getSdCard(this)
                    else -> android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                if (dest == null) { toast("Tujuan tidak tersedia"); return@setItems }
                lifecycleScope.launch {
                    notif.showFile(if (isMove) "Memindah" else "Menyalin", "${files.size} file...")
                    try {
                        withContext(Dispatchers.IO) {
                            files.forEach { src ->
                                val dst = File(dest, src.name)
                                if (isMove) FileUtils.move(src, dst) else FileUtils.copy(src, dst)
                            }
                        }
                        notif.showFile("Selesai", "Berhasil")
                        closeSelection()
                        currentDir?.let { loadDir(it) } ?: loadRoots()
                    } catch (e: Exception) { toast("Gagal: ${e.message}") }
                }
            }.show()
    }

    private fun renameFile(f: File) {
        val input = EditText(this).apply { setText(f.name) }
        AlertDialog.Builder(this).setTitle("Ganti Nama").setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && f.renameTo(File(f.parentFile, newName))) {
                    toast("Berhasil diganti")
                    closeSelection()
                    currentDir?.let { loadDir(it) } ?: loadRoots()
                } else toast("Gagal mengganti nama")
            }.setNegativeButton("Batal", null).show()
    }

    /** OPSI LAINNYA CERDAS sesuai jenis file */
    private fun showSmartMenu(f: File) {
        val ext = f.extension.lowercase()
        val options = mutableListOf("Buka", "Bagikan")
        when {
            ext in MediaBrowser.audioExts -> { options.add("Edit Audio"); options.add("Putar Musik") }
            ext in MediaBrowser.imageExts -> options.add("Edit Gambar")
            ext in MediaBrowser.videoExts -> options.add("Edit Video")
            ext == "zip" -> { options.add("Ekstrak ZIP"); options.add("Lihat Isi ZIP") }
            ext == "apk" -> options.add("Instal APK")
            ext in listOf("txt", "pdf", "doc", "docx") -> options.add("Buka dengan Aplikasi Lain")
        }
        options.addAll(listOf("Jadikan ZIP", "Batalkan Tanda"))

        AlertDialog.Builder(this).setTitle(f.name).setItems(options.toTypedArray()) { _, which ->
            when (options[which]) {
                "Buka" -> open(f)
                "Bagikan" -> ShareHelper.shareFile(this, f)
                "Edit Audio" -> startActivity(Intent(this, AudioEditorActivity::class.java).putExtra("file_path", f.absolutePath))
                "Putar Musik" -> {
                    val list = currentItems.filter { it.isFile && it.extension.lowercase() in MediaBrowser.audioExts }.map { it.absolutePath }
                    startActivity(Intent(this, AudioPlayerActivity::class.java).apply {
                        putStringArrayListExtra("file_paths", ArrayList(list))
                        putExtra("start_path", f.absolutePath)
                    })
                }
                "Edit Gambar" -> startActivity(Intent(this, ImageEditorActivity::class.java).putExtra("file_path", f.absolutePath))
                "Edit Video" -> startActivity(Intent(this, VideoEditorActivity::class.java).putExtra("file_path", f.absolutePath))
                "Ekstrak ZIP" -> unzip(f)
                "Lihat Isi ZIP" -> showZipContents(f)
                "Instal APK" -> installApk(f)
                "Buka dengan Aplikasi Lain" -> openExternal(f)
                "Jadikan ZIP" -> zipOne(f)
                "Batalkan Tanda" -> closeSelection()
            }
        }.show()
    }

    private fun showMultiMenu(files: List<File>) {
        val options = arrayOf("Bagikan Semua", "Jadikan ZIP Bersama", "Batalkan Tanda")
        AlertDialog.Builder(this).setTitle("Opsi Lainnya (${files.size} file)")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Bagikan Semua" -> {
                        try {
                            val uris = ArrayList<Uri>()
                            files.forEach { uris.add(FileProvider.getUriForFile(this, "$packageName.provider", it)) }
                            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "*/*"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Bagikan via"))
                        } catch (e: Exception) { toast("Gagal: ${e.message}") }
                    }
                    "Jadikan ZIP Bersama" -> {
                        lifecycleScope.launch {
                            notif.showFile("Kompres ZIP", "${files.size} file...")
                            val outDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                            val out = File(outDir, "PLA_${System.currentTimeMillis()}.zip")
                            withContext(Dispatchers.IO) {
                                java.util.zip.ZipOutputStream(out.outputStream().buffered()).use { zos ->
                                    files.forEach { f ->
                                        zos.putNextEntry(java.util.zip.ZipEntry(f.name))
                                        f.inputStream().copyTo(zos)
                                        zos.closeEntry()
                                    }
                                }
                            }
                            notif.showFile("ZIP selesai", out.name)
                            closeSelection()
                        }
                    }
                    "Batalkan Tanda" -> closeSelection()
                }
            }.show()
    }

    private fun unzip(f: File) {
        lifecycleScope.launch {
            notif.showFile("Ekstrak", f.name)
            withContext(Dispatchers.IO) { FileUtils.unzip(f, File(f.parentFile, f.nameWithoutExtension)) }
            notif.showFile("Ekstrak selesai", "Berhasil")
            currentDir?.let { loadDir(it) } ?: loadRoots()
        }
    }

    private fun zipOne(f: File) {
        lifecycleScope.launch {
            notif.showFile("Kompres ZIP", f.name)
            withContext(Dispatchers.IO) { FileUtils.zip(f, File(f.parentFile, f.name + ".zip")) }
            notif.showFile("ZIP selesai", "Berhasil")
            closeSelection()
            currentDir?.let { loadDir(it) } ?: loadRoots()
        }
    }

    private fun showZipContents(f: File) {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                try {
                    val list = mutableListOf<String>()
                    java.util.zip.ZipInputStream(f.inputStream().buffered()).use { zis ->
                        var e = zis.nextEntry
                        while (e != null && list.size < 100) { list.add(e.name); e = zis.nextEntry }
                    }
                    list
                } catch (ex: Exception) { listOf("Gagal membaca ZIP") }
            }
            AlertDialog.Builder(this@MediaManagerActivity)
                .setTitle("Isi ${f.name}")
                .setItems(entries.toTypedArray(), null)
                .setPositiveButton("Tutup", null).show()
        }
    }

    private fun installApk(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) { toast("Izinkan instal dari sumber tidak dikenal") }
    }

    private fun open(f: File) {
        if (f.isDirectory) { loadDir(f); return }
        val ext = f.extension.lowercase()
        when {
            ext in MediaBrowser.audioExts -> {
                val audioList = currentItems.filter { it.isFile && it.extension.lowercase() in MediaBrowser.audioExts }.map { it.absolutePath }
                startActivity(Intent(this, AudioPlayerActivity::class.java).apply {
                    putStringArrayListExtra("file_paths", ArrayList(audioList))
                    putExtra("start_path", f.absolutePath)
                })
            }
            ext in MediaBrowser.imageExts -> startActivity(Intent(this, ImageEditorActivity::class.java).putExtra("file_path", f.absolutePath))
            else -> openExternal(f)
        }
    }

    private fun openExternal(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Buka dengan"))
        } catch (e: Exception) { toast("Gagal buka file") }
    }

    private fun handleBack() {
        if (adapter.isSelectionMode) { closeSelection(); return }
        if (currentDir != null) loadRoots() else finish()
    }

    override fun onBackPressed() { handleBack() }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}