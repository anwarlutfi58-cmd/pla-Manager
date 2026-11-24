package com.pla.manajer

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
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
            if (Environment.isExternalStorageManager()) load(File(prefs.lastPath))
            else toast("Izin penyimpanan diperlukan")
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

        // Sembunyikan tombol yang tidak perlu secara default
        b.btnSelectAllTop.visibility = View.GONE
        b.btnTopNew.visibility = View.GONE
        b.btnSort.visibility = View.GONE

        b.btnTopBack.setOnClickListener { finish() }
        b.btnTopUp.setOnClickListener { currentDir.parentFile?.let { load(it) } }
        b.fabPaste.setOnClickListener { pasteHere() }

        b.btnDeleteSelected.setOnClickListener {
            val files = adapter.selectedFiles.toList()
            if (files.isNotEmpty()) showDeleteDialog(files)
        }
        b.btnCopySelected.setOnClickListener {
            val f = adapter.selectedFiles.firstOrNull()
            if (f != null) showCopyMoveDialog(f, false); closeSelectionMode()
        }
        b.btnMoveSelected.setOnClickListener {
            val f = adapter.selectedFiles.firstOrNull()
            if (f != null) showCopyMoveDialog(f, true); closeSelectionMode()
        }
        b.btnRenameSelected.setOnClickListener {
            adapter.selectedFiles.firstOrNull()?.let { renameDialog(it) }
            closeSelectionMode()
        }
        b.btnMoreActions.setOnClickListener {
            adapter.selectedFiles.firstOrNull()?.let { showContextMenu(it) } ?: showMultiSelectMenu()
        }

        b.etSearch.doAfterTextChanged { refreshList() }
        ensurePermission()
    }

    override fun onResume() {
        super.onResume()
        if (clipboard != null) b.fabPaste.show() else b.fabPaste.hide()
    }

    private fun showDeleteDialog(files: List<File>) {
        val cb = CheckBox(this).apply {
            text = "Pindahkan ke Sampah (Hapus centang untuk hapus permanen)"
            isChecked = true
            setPadding(50, 20, 50, 20)
        }
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
                    dao.add(TrashItem(originalPath = f.absolutePath, trashPath = target.absolutePath, name = f.name, isDir = f.isDirectory))
                }
            }
            load(currentDir); notif.showFile("Selesai", "Dipindah ke Sampah")
        }
    }

    private fun deletePermanent(files: List<File>) {
        lifecycleScope.launch {
            notif.showFile("Menghapus", "Menghapus permanen...")
            withContext(Dispatchers.IO) {
                files.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
            }
            load(currentDir); notif.showFile("Selesai", "Dihapus permanen")
        }
    }

    private fun showCopyMoveDialog(f: File, isMove: Boolean) {
        val options = arrayOf("Memori Internal", "Kartu SD")
        AlertDialog.Builder(this)
            .setTitle(if (isMove) "Pindah ke..." else "Salin ke...")
            .setItems(options) { _, which ->
                val destRoot = if (which == 0) Environment.getExternalStorageDirectory() else getSdCard()
                if (destRoot != null) {
                    val dest = File(destRoot, f.name)
                    performCopyMove(f, dest, isMove)
                } else toast("Kartu SD tidak ditemukan")
            }.show()
    }

    private fun performCopyMove(src: File, dst: File, isMove: Boolean) {
        lifecycleScope.launch {
            notif.showFile(if (isMove) "Memindah" else "Menyalin", "${src.name} → ${dst.parentFile?.name}")
            try {
                withContext(Dispatchers.IO) { if (isMove) FileUtils.move(src, dst) else FileUtils.copy(src, dst) }
                if (!dst.exists()) throw Exception("Gagal menulis ke tujuan")
                load(currentDir); notif.showFile("Selesai", "Berhasil")
            } catch (e: Exception) {
                toast("Gagal: ${e.message}")
            }
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

    private fun showMultiSelectMenu() {
        AlertDialog.Builder(this)
            .setTitle("Aksi Lainnya (${adapter.selectedFiles.size} file)")
            .setItems(arrayOf("Bagikan File Terpilih")) { _, which ->
                if (which == 0) toast("Fitur bagikan banyak file akan segera hadir")
            }.show()
    }

    private fun showContextMenu(f: File) {
        val ext = f.extension.lowercase()
        val options = mutableListOf("Buka", "Bagikan", "Ganti Nama", "Salin", "Pindah", "Hapus", "Kompres (ZIP)", "Ekstrak (ZIP)")
        if (ext in listOf("mp3","wav","aac","m4a","ogg","flac","opus")) options.add(0, "Edit Audio")
        else if (ext in listOf("jpg","jpeg","png","webp")) options.add(0, "Edit Gambar")
        else if (ext in listOf("mp4","mkv","avi")) options.add(0, "Edit Video")

        AlertDialog.Builder(this)
            .setTitle(f.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (val selected = options[which]) {
                    "Edit Audio" -> startActivity(Intent(this, AudioEditorActivity::class.java).putExtra("file_path", f.absolutePath))
                    "Edit Gambar" -> startActivity(Intent(this, ImageEditorActivity::class.java).putExtra("file_path", f.absolutePath))
                    "Edit Video" -> startActivity(Intent(this, VideoEditorActivity::class.java))
                    "Buka" -> open(f)
                    "Bagikan" -> shareFile(f)
                    "Ganti Nama" -> renameDialog(f)
                    "Salin" -> { clipboard = f; isCut = false; b.fabPaste.show(); toast("Disalin: ${f.name}") }
                    "Pindah" -> { clipboard = f; isCut = true; b.fabPaste.show(); toast("Siap pindah: ${f.name}") }
                    "Hapus" -> showDeleteDialog(listOf(f))
                    "Kompres (ZIP)" -> zipFile(f)
                    "Ekstrak (ZIP)" -> unzipFile(f)
                }
                closeSelectionMode()
            }.setNegativeButton("Tutup", null).show()
    }

    private fun shareFile(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
            var mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            if (mime == "*/*" && f.extension.lowercase() == "apk") {
                mime = "application/vnd.android.package-archive"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("File", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Bagikan File via"))
        } catch (e: Exception) {
            toast("Gagal membagikan: ${e.message}")
        }
    }

    private fun enableSelectionMode(f: File) {
        adapter.isSelectionMode = true; adapter.selectedFiles.add(f)
        adapter.notifyDataSetChanged(); updateSelectionUi()
    }

    private fun toggleSelection(f: File) {
        if (adapter.selectedFiles.contains(f)) adapter.selectedFiles.remove(f) else adapter.selectedFiles.add(f)
        adapter.isSelectionMode = adapter.selectedFiles.isNotEmpty()
        adapter.notifyDataSetChanged(); updateSelectionUi()
    }

    private fun closeSelectionMode() {
        adapter.isSelectionMode = false; adapter.selectedFiles.clear()
        adapter.notifyDataSetChanged(); updateSelectionUi()
    }

    private fun updateSelectionUi() {
        val active = adapter.selectedFiles.isNotEmpty()
        b.selectionToolbar.visibility = if (active) View.VISIBLE else View.GONE
        b.btnSelectAllTop.visibility = if (active) View.VISIBLE else View.GONE
        b.btnTopNew.visibility = if (!active) View.VISIBLE else View.GONE
        b.btnSort.visibility = if (!active) View.VISIBLE else View.GONE
    }

    private fun open(f: File) {
        if (f.isDirectory) { closeSelectionMode(); load(f); return }
        val ext = f.extension.lowercase()
        val codeExts = listOf("kt","java","py","js","html","css","json","md","xml","gradle","kts","txt","properties","yml","yaml","cpp","c","h")
        val audioExts = listOf("mp3","wav","aac","m4a","ogg","flac","opus")

        when {
            codeExts.contains(ext) -> startActivity(Intent(this, CodeEditorActivity::class.java).putExtra("path", f.absolutePath))
            audioExts.contains(ext) -> {
                val audioList = currentSortedFiles.filter { it.isFile && it.extension.lowercase() in audioExts }.map { it.absolutePath }
                val intent = Intent(this, AudioPlayerActivity::class.java)
                intent.putStringArrayListExtra("file_paths", ArrayList(audioList))
                intent.putExtra("start_path", f.absolutePath)
                intent.putExtra("index", 0)
                startActivity(intent)
            }
            else -> openWith(f)
        }
    }

    private fun openWith(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
            var mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())
            if (mime == null || mime == "*/*") {
                mime = when (f.extension.lowercase()) {
                    "mp4" -> "video/mp4"; "mkv" -> "video/x-matroska"
                    "jpg","jpeg" -> "image/jpeg"; "png" -> "image/png"
                    "apk" -> "application/vnd.android.package-archive"
                    else -> "*/*"
                }
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach {
                grantUriPermission(it.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Buka dengan"))
        } catch (e: Exception) {
            toast("Tidak ada aplikasi untuk membuka file ini")
        }
    }

    private fun showSortDialog() {
        val options = arrayOf("Nama (A-Z)","Nama (Z-A)","Tanggal (Terlama)","Tanggal (Terbaru)","Ukuran (Terkecil)","Ukuran (Terbesar)")
        AlertDialog.Builder(this).setTitle("Sortir Berdasarkan")
            .setSingleChoiceItems(options, prefs.sortMode) { dialog, which ->
                prefs.sortMode = which; refreshList(); dialog.dismiss()
            }.setNegativeButton("Batal", null).show()
    }

    private fun refreshList() {
        val q = b.etSearch.text?.toString()?.lowercase() ?: ""
        val filtered = if (q.isEmpty()) allFiles else allFiles.filter { it.name.lowercase().contains(q) }
        
        // KUNCI: Folder selalu di atas (true), File di bawah (false). Lalu diurutkan sesuai sortMode.
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
                    val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    i.data = Uri.parse("package:$packageName")
                    allFilesPerm.launch(i)
                } catch (_: Exception) {
                    allFilesPerm.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else load(currentDir)
        } else {
            val okRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val okWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (okRead && okWrite) load(currentDir)
            else legacyPerm.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
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
        val input = EditText(this).apply {
            hint = "Nama folder / file.kt"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(this).setTitle("Buat Jalur / Berkas Baru").setView(input)
            .setPositiveButton("Buat") { _, _ ->
                val path = input.text.toString().trim()
                if (path.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { FileUtils.createSmartPath(currentDir, path) }
                    load(currentDir); toast("Dibuat: $path")
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun renameDialog(f: File) {
        val input = EditText(this).apply { setText(f.name) }
        AlertDialog.Builder(this).setTitle("Ganti Nama").setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newFile = File(f.parentFile, input.text.toString())
                if (f.renameTo(newFile)) load(currentDir) else toast("Gagal rename")
            }.setNegativeButton("Batal", null).show()
    }

    private fun zipFile(f: File) {
        lifecycleScope.launch {
            notif.showFile("Kompres ZIP", "Mengompres ${f.name}...")
            val out = File(f.parentFile, f.name + ".zip")
            withContext(Dispatchers.IO) { FileUtils.zip(f, out) }
            load(currentDir); notif.showFile("Kompres selesai", "Berhasil: ${out.name}")
        }
    }

    private fun unzipFile(f: File) {
        if (!f.name.endsWith(".zip")) { toast("Bukan file ZIP"); return }
        lifecycleScope.launch {
            notif.showFile("Ekstrak ZIP", "Mengekstrak ${f.name}...")
            val out = File(f.parentFile, f.nameWithoutExtension)
            withContext(Dispatchers.IO) { FileUtils.unzip(f, out) }
            load(currentDir); notif.showFile("Ekstrak selesai", "Berhasil ke folder: ${out.name}")
        }
    }

    private fun pasteHere() {
        val src = clipboard ?: run { toast("Clipboard kosong"); return }
        if (!src.exists()) { clipboard = null; isCut = false; b.fabPaste.hide(); toast("File sumber sudah tidak ada"); return }
        lifecycleScope.launch {
            try {
                val dst = File(currentDir, src.name)
                notif.showFile(if (isCut) "Memindah" else "Menyalin", "${src.name} → ${currentDir.name}")
                withContext(Dispatchers.IO) { if (isCut) FileUtils.move(src, dst) else FileUtils.copy(src, dst) }
                if (!dst.exists()) throw Exception("Gagal menulis ke folder tujuan")
                if (isCut) { clipboard = null; isCut = false; b.fabPaste.hide() }
                load(currentDir)
                notif.showFile(if (isCut) "Selesai dipindah" else "Selesai disalin", "${dst.name} → ${currentDir.name}")
                toast("Ditempel di: ${currentDir.name}")
            } catch (e: Exception) {
                notif.showFile("Gagal", e.message ?: "Tidak bisa menulis ke folder ini")
                toast("Gagal tempel: ${e.message ?: "Tidak bisa menulis ke folder ini"}")
            }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}