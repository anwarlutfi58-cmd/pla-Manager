package com.pla.manajer

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pla.manajer.adapter.FileAdapter
import com.pla.manajer.data.TrashItem
import com.pla.manajer.databinding.ActivityFileManagerBinding
import com.pla.manajer.util.FileUtils
import com.pla.manajer.util.NotificationHelper
import com.pla.manajer.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileManagerActivity : AppCompatActivity() {
    private lateinit var b: ActivityFileManagerBinding
    private lateinit var adapter: FileAdapter
    private lateinit var currentDir: File
    private lateinit var prefs: Prefs
    private lateinit var notif: NotificationHelper
    private var allFiles: List<File> = emptyList()
    private var currentSortedFiles: List<File> = emptyList()
    private var showDateTime = false

    private var clipboard: File? = null
    private var isCut = false

    private val allFilesPerm = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) load(File(prefs.lastPath)) else toast("Izin penyimpanan diperlukan")
        }
    }
    private val legacyPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) load(File(prefs.lastPath)) else toast("Izin penyimpanan diperlukan")
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityFileManagerBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)
        notif = NotificationHelper(this)

        currentDir = File(intent.getStringExtra("initial_path") ?: prefs.lastPath)
        showDateTime = prefs.showDateTime

        adapter = FileAdapter(
            onOpen = { f -> if (!adapter.isSelectionMode) open(f) },
            onLong = { f -> enableSelectionMode(f) },
            onSelectToggle = { f -> toggleSelection(f) },
            onMoreOptions = { f -> showContextMenu(f) }
        ).apply { setDateTimeMode(showDateTime) }

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        // Tombol Tandai Semua: tersembunyi saat normal, muncul saat menandai
        b.btnSelectAllTop.visibility = View.GONE
        b.btnSelectAllTop.text = "Tandai Semua"
        b.btnSelectAllTop.contentDescription = "Tandai semua file dan folder"
        b.btnSelectAllTop.setOnClickListener { selectAll() }

        b.btnTopNew.visibility = View.VISIBLE
        b.btnSort.visibility = View.VISIBLE
        b.btnSort.contentDescription = "Urutkan file"

        b.btnTopBack.setOnClickListener { finish() }
        b.btnTopNew.setOnClickListener { showCreateDialog() }
        b.btnSort.setOnClickListener { showSortDialog() }
        b.fabPaste.setOnClickListener { pasteHere() }

        b.btnDeleteSelected.setOnClickListener { val f = adapter.selectedFiles.toList(); if (f.isNotEmpty()) showDeleteDialog(f) }
        b.btnCopySelected.setOnClickListener { val f = adapter.selectedFiles.toList(); if (f.isNotEmpty()) { showCopyMoveDialog(f, false); closeSelectionMode() } }
        b.btnMoveSelected.setOnClickListener { val f = adapter.selectedFiles.toList(); if (f.isNotEmpty()) { showCopyMoveDialog(f, true); closeSelectionMode() } }
        b.btnRenameSelected.setOnClickListener { adapter.selectedFiles.firstOrNull()?.let { renameDialog(it) }; closeSelectionMode() }
        b.btnMoreActions.setOnClickListener {
            val sel = adapter.selectedFiles.toList()
            if (sel.size == 1) showContextMenu(sel[0]) else if (sel.size > 1) showMultiSelectMenu(sel)
        }

        b.etSearch.doAfterTextChanged { refreshList() }
        ensurePermission()
    }

    override fun onResume() { super.onResume(); if (clipboard != null) b.fabPaste.show() else b.fabPaste.hide() }

    private fun selectAll() {
        adapter.isSelectionMode = true
        adapter.selectedFiles.clear()
        adapter.selectedFiles.addAll(currentSortedFiles)
        adapter.notifyDataSetChanged()
        updateSelectionUi()
        toast("${adapter.selectedFiles.size} item ditandai")
    }

    private fun showDeleteDialog(files: List<File>) {
        val cb = CheckBox(this).apply { text = "Pindah ke Sampah?"; isChecked = true; setPadding(50, 20, 50, 20) }
        AlertDialog.Builder(this).setTitle("Hapus ${files.size} item?").setView(cb)
            .setPositiveButton("Hapus") { _, _ -> if (cb.isChecked) deleteToTrash(files) else deletePermanent(files); closeSelectionMode() }
            .setNegativeButton("Batal", null).show()
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
                    dao.add(TrashItem(originalPath = f.absolutePath, trashPath = target.absolutePath, name = f.name, isDir = f.isDirectory))
                }
            }
            load(currentDir); notif.showFile("Selesai", "Dipindah ke Sampah")
        }
    }

    private fun deletePermanent(files: List<File>) {
        lifecycleScope.launch {
            notif.showFile("Menghapus", "Menghapus permanen...")
            withContext(Dispatchers.IO) { files.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() } }
            load(currentDir); notif.showFile("Selesai", "Dihapus permanen")
        }
    }

    private fun showCopyMoveDialog(files: List<File>, isMove: Boolean) {
        val options = arrayOf("Memori Internal", "Kartu SD", "Folder Saat Ini")
        AlertDialog.Builder(this).setTitle(if (isMove) "Pindah ${files.size} item ke..." else "Salin ${files.size} item ke...")
            .setItems(options) { _, which ->
                val destRoot = when (which) {
                    0 -> Environment.getExternalStorageDirectory()
                    1 -> getSdCard()
                    else -> currentDir
                }
                if (destRoot != null) performCopyMove(files, destRoot, isMove) else toast("Kartu SD tidak ditemukan")
            }.show()
    }

    private fun performCopyMove(files: List<File>, destDir: File, isMove: Boolean) {
        lifecycleScope.launch {
            notif.showFile(if (isMove) "Memindah" else "Menyalin", "${files.size} item → ${destDir.name}")
            try {
                withContext(Dispatchers.IO) {
                    files.forEach { src ->
                        val dst = File(destDir, src.name)
                        if (isMove) FileUtils.move(src, dst) else FileUtils.copy(src, dst)
                    }
                }
                load(currentDir); notif.showFile("Selesai", "Berhasil")
            } catch (e: Exception) { toast("Gagal: ${e.message}") }
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

    private fun getSimplePath(path: String): String {
        val internal = Environment.getExternalStorageDirectory().absolutePath
        val sd = getSdCard()?.absolutePath
        return when {
            path == internal -> "Memori Internal"
            sd != null && path == sd -> "Kartu SD"
            path.startsWith(internal) -> "Memori Internal" + path.replaceFirst(internal, "")
            sd != null && path.startsWith(sd) -> "Kartu SD" + path.replaceFirst(sd, "")
            else -> path
        }
    }

    /** Opsi Lainnya untuk BANYAK file sekaligus */
    private fun showMultiSelectMenu(files: List<File>) {
        val options = arrayOf("Bagikan Semua", "Jadikan ZIP Bersama", "Batalkan Tanda")
        AlertDialog.Builder(this).setTitle("Opsi Lainnya (${files.size} item)")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Bagikan Semua" -> shareMultiple(files)
                    "Jadikan ZIP Bersama" -> zipMultiple(files)
                    "Batalkan Tanda" -> closeSelectionMode()
                }
            }.show()
    }

    private fun shareMultiple(files: List<File>) {
        try {
            val uris = ArrayList<Uri>()
            files.filter { it.isFile }.forEach { uris.add(FileProvider.getUriForFile(this, "$packageName.provider", it)) }
            if (uris.isEmpty()) { toast("Tidak ada file untuk dibagikan"); return }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Bagikan ${uris.size} file via"))
        } catch (e: Exception) { toast("Gagal: ${e.message}") }
    }

    private fun zipMultiple(files: List<File>) {
        lifecycleScope.launch {
            notif.showFile("Kompres ZIP", "Mengompres ${files.size} item...")
            val out = File(currentDir, "PLA_${System.currentTimeMillis()}.zip")
            withContext(Dispatchers.IO) {
                java.util.zip.ZipOutputStream(out.outputStream().buffered()).use { zos ->
                    files.forEach { f ->
                        fun add(file: File, base: String) {
                            val name = if (base.isEmpty()) file.name else "$base/${file.name}"
                            if (file.isDirectory) file.listFiles()?.forEach { add(it, name) }
                            else {
                                zos.putNextEntry(java.util.zip.ZipEntry(name))
                                file.inputStream().copyTo(zos)
                                zos.closeEntry()
                            }
                        }
                        add(f, "")
                    }
                }
            }
            load(currentDir); notif.showFile("ZIP selesai", out.name)
            closeSelectionMode()
        }
    }

    /** OPSI LAINNYA CERDAS: beda menu untuk folder, ZIP, audio, gambar, video, file biasa */
    private fun showContextMenu(f: File) {
        val ext = f.extension.lowercase()
        val options = mutableListOf<String>()

        if (f.isDirectory) {
            // ===== MENU KHUSUS FOLDER =====
            options.addAll(listOf("Buka", "Ganti Nama", "Salin", "Pindah", "Hapus", "Jadikan ZIP", "Buat Pintasan Layar Beranda"))
        } else {
            // ===== MENU KHUSUS FILE =====
            options.addAll(listOf("Buka", "Bagikan", "Ganti Nama", "Salin", "Pindah", "Hapus"))
            when {
                ext == "zip" -> { options.add("Ekstrak ZIP"); options.add("Lihat Isi ZIP") }
                ext in listOf("rar", "7z", "tar", "gz") -> options.add("Ekstrak Arsip")
                ext in listOf("mp3", "wav", "aac", "m4a", "ogg", "flac", "opus") -> { options.add("Edit Audio"); options.add("Putar Musik") }
                ext in listOf("jpg", "jpeg", "png", "webp", "bmp") -> options.add("Edit Gambar")
                ext in listOf("mp4", "mkv", "avi", "mov", "webm") -> options.add("Edit Video")
                ext == "apk" -> options.add("Instal APK")
                ext in listOf("kt", "java", "xml", "json", "txt", "md", "gradle", "kts", "py", "js", "html", "css", "yml", "yaml", "properties") -> options.add("Edit Kode/Teks")
            }
            options.add("Jadikan ZIP")
        }

        AlertDialog.Builder(this).setTitle(f.name).setItems(options.toTypedArray()) { _, which ->
            when (val selected = options[which]) {
                "Buka" -> open(f)
                "Bagikan" -> com.pla.manajer.util.ShareHelper.shareFile(this, f)
                "Ganti Nama" -> renameDialog(f)
                "Salin" -> { clipboard = f; isCut = false; b.fabPaste.show(); toast("Disalin: ${f.name}") }
                "Pindah" -> { clipboard = f; isCut = true; b.fabPaste.show(); toast("Siap pindah: ${f.name}") }
                "Hapus" -> showDeleteDialog(listOf(f))
                "Jadikan ZIP" -> zipFile(f)
                "Ekstrak ZIP", "Ekstrak Arsip" -> unzipFile(f)
                "Lihat Isi ZIP" -> showZipContents(f)
                "Buat Pintasan Layar Beranda" -> createShortcut(f)
                "Edit Audio" -> startActivity(Intent(this, AudioEditorActivity::class.java).putExtra("file_path", f.absolutePath))
                "Putar Musik" -> {
                    val intent = Intent(this, AudioPlayerActivity::class.java)
                    intent.putStringArrayListExtra("file_paths", arrayListOf(f.absolutePath))
                    intent.putExtra("start_path", f.absolutePath)
                    startActivity(intent)
                }
                "Edit Gambar" -> startActivity(Intent(this, ImageEditorActivity::class.java).putExtra("file_path", f.absolutePath))
                "Edit Video" -> startActivity(Intent(this, VideoEditorActivity::class.java).putExtra("file_path", f.absolutePath))
                "Edit Kode/Teks" -> startActivity(Intent(this, CodeEditorActivity::class.java).putExtra("path", f.absolutePath))
                "Instal APK" -> installApk(f)
            }
            closeSelectionMode()
        }.setNegativeButton("Tutup", null).show()
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
            AlertDialog.Builder(this@FileManagerActivity)
                .setTitle("Isi ${f.name} (${entries.size})")
                .setItems(entries.toTypedArray(), null)
                .setPositiveButton("Tutup", null).show()
        }
    }

    private fun installApk(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) { toast("Izinkan instal dari sumber tidak dikenal di Pengaturan HP") }
    }

    private fun createShortcut(folder: File) {
        try {
            val intent = Intent(this, FileManagerActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                putExtra("initial_path", folder.absolutePath)
            }
            val shortcut = ShortcutInfoCompat.Builder(this, "shortcut_${folder.name}_${System.currentTimeMillis()}")
                .setShortLabel(folder.name)
                .setLongLabel("Buka folder ${folder.name}")
                .setIcon(IconCompat.createWithResource(this, android.R.drawable.ic_menu_myplaces))
                .setIntent(intent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
            toast("Pintasan '${folder.name}' ditambahkan ke Layar Beranda")
        } catch (e: Exception) { toast("Gagal membuat pintasan: ${e.message}") }
    }

    private fun enableSelectionMode(f: File) {
        adapter.isSelectionMode = true
        adapter.selectedFiles.add(f)
        adapter.notifyDataSetChanged()
        updateSelectionUi()
    }

    private fun toggleSelection(f: File) {
        if (adapter.selectedFiles.contains(f)) adapter.selectedFiles.remove(f) else adapter.selectedFiles.add(f)
        adapter.isSelectionMode = adapter.selectedFiles.isNotEmpty()
        adapter.notifyDataSetChanged()
        updateSelectionUi()
    }

    private fun closeSelectionMode() {
        adapter.isSelectionMode = false
        adapter.selectedFiles.clear()
        adapter.notifyDataSetChanged()
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        val active = adapter.selectedFiles.isNotEmpty()
        b.selectionToolbar.visibility = if (active) View.VISIBLE else View.GONE
        // TOMBOL TANDAI SEMUA MUNCUL SAAT MENANDAI
        b.btnSelectAllTop.visibility = if (active) View.VISIBLE else View.GONE
        b.btnTopNew.visibility = if (!active) View.VISIBLE else View.GONE
        b.btnSort.visibility = if (!active) View.VISIBLE else View.GONE
        if (active) b.btnSelectAllTop.text = "Tandai Semua (${adapter.selectedFiles.size})"
    }

    private fun open(f: File) {
        if (f.isDirectory) { closeSelectionMode(); load(f); return }
        val ext = f.extension.lowercase()
        val codeExts = listOf("kt", "java", "py", "js", "html", "css", "json", "md", "xml", "gradle", "kts", "txt", "properties", "yml", "yaml", "cpp", "c", "h")
        val audioExts = listOf("mp3", "wav", "aac", "m4a", "ogg", "flac", "opus")

        when {
            codeExts.contains(ext) -> startActivity(Intent(this, CodeEditorActivity::class.java).putExtra("path", f.absolutePath))
            audioExts.contains(ext) -> {
                val audioList = currentSortedFiles.filter { it.isFile && it.extension.lowercase() in audioExts }.map { it.absolutePath }
                val intent = Intent(this, AudioPlayerActivity::class.java)
                intent.putStringArrayListExtra("file_paths", ArrayList(audioList))
                intent.putExtra("start_path", f.absolutePath)
                startActivity(intent)
            }
            else -> openWith(f)
        }
    }

    private fun openWith(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
            var mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            if (mime == "*/*" && f.extension.lowercase() == "apk") mime = "application/vnd.android.package-archive"
            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            startActivity(Intent.createChooser(intent, "Buka dengan"))
        } catch (e: Exception) { toast("Tidak ada aplikasi untuk membuka file ini") }
    }

    private fun showSortDialog() {
        val options = arrayOf("Nama (A-Z)", "Nama (Z-A)", "Tanggal (Terlama)", "Tanggal (Terbaru)", "Ukuran (Terkecil)", "Ukuran (Terbesar)")
        AlertDialog.Builder(this).setTitle("Sortir Berdasarkan").setSingleChoiceItems(options, prefs.sortMode) { dialog, which ->
            prefs.sortMode = which; refreshList(); dialog.dismiss()
        }.setNegativeButton("Batal", null).show()
    }

    private fun refreshList() {
        val q = b.etSearch.text?.toString()?.lowercase() ?: ""
        val visibleFiles = allFiles.filter { !it.name.startsWith(".") }
        val filtered = if (q.isEmpty()) visibleFiles else visibleFiles.filter { it.name.lowercase().contains(q) }
        val sorted = if (prefs.sortMode == 2 || prefs.sortMode == 4 || prefs.sortMode == 5) {
            filtered.sortedWith(compareBy<File> { !it.isDirectory }.thenByDescending { if (prefs.sortMode == 2) it.lastModified() else it.length() })
        } else if (prefs.sortMode == 3) {
            filtered.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.lastModified() })
        } else {
            filtered.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }
        currentSortedFiles = sorted
        adapter.submit(sorted)
    }

    private fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:$packageName") }
                    allFilesPerm.launch(i)
                } catch (_: Exception) { allFilesPerm.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            } else load(currentDir)
        } else {
            val okRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (okRead) load(currentDir) else legacyPerm.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun load(dir: File) {
        if (!dir.exists()) dir.mkdirs()
        currentDir = dir; prefs.lastPath = dir.absolutePath
        b.tvPath.text = getSimplePath(dir.absolutePath)
        try {
            val stat = StatFs(dir.absolutePath)
            val used = (stat.blockSizeLong * stat.blockCountLong) - (stat.blockSizeLong * stat.availableBlocksLong)
            val total = stat.blockSizeLong * stat.blockCountLong
            b.tvStorageInfo.text = "${(used.toDouble() / total * 100).toInt()}%"
        } catch (_: Exception) {}
        lifecycleScope.launch {
            allFiles = withContext(Dispatchers.IO) { FileUtils.listFiles(dir) }
            refreshList()
        }
    }

    private fun showCreateDialog() {
        val input = EditText(this).apply { hint = "Nama folder / file.kt"; setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f) }
        AlertDialog.Builder(this).setTitle("Buat Baru").setView(input).setPositiveButton("Buat") { _, _ ->
            val path = input.text.toString().trim()
            if (path.isNotEmpty()) { lifecycleScope.launch { withContext(Dispatchers.IO) { FileUtils.createSmartPath(currentDir, path) }; load(currentDir); toast("Dibuat: $path") } }
        }.setNegativeButton("Batal", null).show()
    }

    private fun renameDialog(f: File) {
        val input = EditText(this).apply { setText(f.name) }
        AlertDialog.Builder(this).setTitle("Ganti Nama").setView(input).setPositiveButton("OK") { _, _ ->
            if (f.renameTo(File(f.parentFile, input.text.toString()))) load(currentDir) else toast("Gagal rename")
        }.setNegativeButton("Batal", null).show()
    }

    private fun zipFile(f: File) {
        lifecycleScope.launch {
            notif.showFile("Kompres ZIP", "Mengompres ${f.name}...")
            val out = File(f.parentFile, f.name + ".zip")
            withContext(Dispatchers.IO) { FileUtils.zip(f, out) }
            load(currentDir); notif.showFile("Kompres selesai", "Berhasil")
        }
    }

    private fun unzipFile(f: File) {
        lifecycleScope.launch {
            notif.showFile("Ekstrak", "Mengekstrak ${f.name}...")
            val out = File(f.parentFile, f.nameWithoutExtension)
            withContext(Dispatchers.IO) { FileUtils.unzip(f, out) }
            load(currentDir); notif.showFile("Ekstrak selesai", "Berhasil")
        }
    }

    private fun pasteHere() {
        val src = clipboard ?: return
        if (!src.exists()) { clipboard = null; b.fabPaste.hide(); return }
        lifecycleScope.launch {
            val dst = File(currentDir, src.name)
            withContext(Dispatchers.IO) { if (isCut) FileUtils.move(src, dst) else FileUtils.copy(src, dst) }
            if (isCut) { clipboard = null; b.fabPaste.hide() }
            load(currentDir); toast("Selesai")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}