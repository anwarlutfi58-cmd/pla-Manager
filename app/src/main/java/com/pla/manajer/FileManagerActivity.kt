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
    private var allFiles: List<File> = emptyList()

    companion object {
        private var sharedClipboard: File? = null
        private var sharedIsCut = false
    }

    private var clipboard: File?
        get() = sharedClipboard
        set(v) { sharedClipboard = v }
    private var isCut: Boolean
        get() = sharedIsCut
        set(v) { sharedIsCut = v }

    private val allFilesPerm = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) load(File(prefs.lastPath))
            else toast("Izin penyimpanan diperlukan")
        }
    }

    private val legacyPerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) load(File(prefs.lastPath))
        else toast("Izin penyimpanan diperlukan")
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityFileManagerBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        val initialPath = intent.getStringExtra("initial_path") ?: prefs.lastPath
        currentDir = File(initialPath)

        adapter = FileAdapter(
            onOpen = { f -> if (!adapter.isSelectionMode) open(f) },
            onLong = { f -> enableSelectionMode(f) },
            onSelectToggle = { f -> toggleSelection(f) },
            onMoreOptions = { f -> showContextMenu(f) }
        )

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.btnTopBack.setOnClickListener { finish() }
        b.btnTopUp.setOnClickListener   { currentDir.parentFile?.let { load(it) } }
        b.btnTopNew.setOnClickListener  { showCreateDialog() }
        b.btnSort.setOnClickListener    { showSortDialog() }
        b.fabPaste.setOnClickListener   { pasteHere() }

        b.btnSelectAllTop.setOnClickListener {
            val query = b.etSearch.text?.toString()?.lowercase() ?: ""
            val filtered = if (query.isEmpty()) allFiles else allFiles.filter { it.name.lowercase().contains(query) }
            if (adapter.selectedFiles.size == filtered.size) {
                adapter.selectedFiles.clear()
                adapter.isSelectionMode = false
            } else {
                adapter.selectedFiles.clear()
                adapter.selectedFiles.addAll(filtered)
                adapter.isSelectionMode = true
            }
            adapter.notifyDataSetChanged()
            updateSelectionUi()
        }

        b.btnDeleteSelected.setOnClickListener {
            val filesToDelete = adapter.selectedFiles.toList()
            if (filesToDelete.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Hapus ${filesToDelete.size} item?")
                    .setPositiveButton("Hapus") { _, _ ->
                        deleteMultipleFiles(filesToDelete)
                        closeSelectionMode()
                    }.setNegativeButton("Batal", null).show()
            }
        }

        b.btnCopySelected.setOnClickListener {
            if (adapter.selectedFiles.size == 1) {
                clipboard = adapter.selectedFiles.first()
                isCut = false
                b.fabPaste.show()
                toast("Disalin: ${clipboard?.name}")
                closeSelectionMode()
            } else toast("Pilih hanya 1 file untuk menyalin")
        }

        b.btnMoveSelected.setOnClickListener {
            if (adapter.selectedFiles.size == 1) {
                clipboard = adapter.selectedFiles.first()
                isCut = true
                b.fabPaste.show()
                toast("Siap pindah: ${clipboard?.name}")
                closeSelectionMode()
            } else toast("Pilih hanya 1 file untuk memindah")
        }

        b.btnRenameSelected.setOnClickListener {
            if (adapter.selectedFiles.size == 1) {
                renameDialog(adapter.selectedFiles.first())
                closeSelectionMode()
            } else toast("Pilih hanya 1 file untuk ganti nama")
        }

        b.btnMoreActions.setOnClickListener {
            if (adapter.selectedFiles.size == 1) showContextMenu(adapter.selectedFiles.first())
            else showMultiSelectMenu()
        }

        b.etSearch.doAfterTextChanged { refreshList() }
        ensurePermission()
    }

    override fun onResume() {
        super.onResume()
        if (clipboard != null) b.fabPaste.show() else b.fabPaste.hide()
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
        if (ext in listOf("mp3", "wav", "aac", "m4a", "ogg", "flac", "opus")) options.add(0, "Edit Audio")
        else if (ext in listOf("jpg", "jpeg", "png", "webp")) options.add(0, "Edit Gambar")
        else if (ext in listOf("mp4", "mkv", "avi")) options.add(0, "Edit Video")

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
                    "Hapus" -> deleteMultipleFiles(listOf(f))
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
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("File", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Bagikan File via"))
        } catch (e: Exception) { toast("Gagal membagikan: ${e.message}") }
    }

    private fun toggleSelection(f: File) {
        if (adapter.selectedFiles.contains(f)) adapter.selectedFiles.remove(f) else adapter.selectedFiles.add(f)
        adapter.isSelectionMode = adapter.selectedFiles.isNotEmpty()
        adapter.notifyDataSetChanged()
        updateSelectionUi()
    }

    private fun enableSelectionMode(f: File) {
        adapter.isSelectionMode = true
        adapter.selectedFiles.add(f)
        adapter.notifyDataSetChanged()
        updateSelectionUi()
    }

    private fun closeSelectionMode() {
        adapter.isSelectionMode = false
        adapter.selectedFiles.clear()
        adapter.notifyDataSetChanged()
        b.selectionToolbar.visibility = View.GONE
    }

    private fun updateSelectionUi() {
        b.selectionToolbar.visibility = if (adapter.selectedFiles.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun deleteMultipleFiles(files: List<File>) {
        lifecycleScope.launch {
            val dao = (application as PlaApp).database.trashDao()
            withContext(Dispatchers.IO) {
                val trashDir = File(prefs.trashFolder).apply { mkdirs() }
                files.forEach { f ->
                    val target = File(trashDir, "${System.currentTimeMillis()}_${f.name}")
                    FileUtils.move(f, target)
                    dao.add(TrashItem(originalPath = f.absolutePath, trashPath = target.absolutePath, name = f.name, isDir = f.isDirectory))
                }
            }
            load(currentDir)
            toast("${files.size} item dipindah ke Sampah")
        }
    }

    // PERBAIKAN: audio diputar pakai AudioPlayerActivity bawaan, bukan buka aplikasi luar
    private fun open(f: File) {
        if (f.isDirectory) {
            closeSelectionMode()
            load(f)
            return
        }

        val ext = f.extension.lowercase()
        val codeExts = listOf("kt", "java", "py", "js", "html", "css", "json", "md", "xml", "gradle", "kts", "txt", "properties", "yml", "yaml", "cpp", "c", "h")
        val audioExts = listOf("mp3", "wav", "aac", "m4a", "ogg", "flac", "opus")

        when {
            codeExts.contains(ext) -> {
                startActivity(Intent(this, CodeEditorActivity::class.java).putExtra("path", f.absolutePath))
            }
            audioExts.contains(ext) -> {
                val audioList = allFiles
                    .filter { it.isFile && it.extension.lowercase() in audioExts }
                    .map { it.absolutePath }
                val idx = audioList.indexOf(f.absolutePath).coerceAtLeast(0)
                val intent = Intent(this, AudioPlayerActivity::class.java)
                intent.putStringArrayListExtra("file_paths", ArrayList(audioList))
                intent.putExtra("index", idx)
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
                    "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"
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
        } catch (e: Exception) { toast("Tidak ada aplikasi untuk membuka file ini") }
    }

    private fun showSortDialog() {
        val options = arrayOf("Nama (A-Z)", "Nama (Z-A)", "Tanggal (Terlama)", "Tanggal (Terbaru)", "Ukuran (Terkecil)", "Ukuran (Terbesar)")
        AlertDialog.Builder(this).setTitle("Sortir Berdasarkan")
            .setSingleChoiceItems(options, prefs.sortMode) { dialog, which ->
                prefs.sortMode = which; refreshList(); dialog.dismiss()
            }.setNegativeButton("Batal", null).show()
    }

    private fun refreshList() {
        val query = b.etSearch.text?.toString()?.lowercase() ?: ""
        val filtered = if (query.isEmpty()) allFiles else allFiles.filter { it.name.lowercase().contains(query) }
        val sortedList: List<File> = when (prefs.sortMode) {
            0 -> filtered.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            1 -> filtered.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }).reversed())
            2 -> filtered.sortedBy { it.lastModified() }
            3 -> filtered.sortedByDescending { it.lastModified() }
            4 -> filtered.sortedBy { it.length() }
            5 -> filtered.sortedByDescending { it.length() }
            else -> filtered.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
        adapter.submit(sortedList)
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
        currentDir = dir
        prefs.lastPath = dir.absolutePath
        b.tvPath.text = dir.absolutePath
        try {
            val stat = StatFs(dir.absolutePath)
            val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
            val bytesTotal = stat.blockSizeLong * stat.blockCountLong
            val usedBytes = bytesTotal - bytesAvailable
            val percentUsed = (usedBytes.toDouble() / bytesTotal.toDouble() * 100).toInt()
            b.tvStorageInfo.text = "$percentUsed% DIGUNAKAN"
        } catch (e: Exception) { b.tvStorageInfo.text = "0% DIGUNAKAN" }
        lifecycleScope.launch {
            allFiles = withContext(Dispatchers.IO) { FileUtils.listFiles(dir) }
            refreshList()
        }
    }

    private fun showCreateDialog() {
        val input = EditText(this).apply {
            hint = "app/src/main/java/com/pla/agent/PlAiEngine.kt"
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
            val out = File(f.parentFile, f.name + ".zip")
            withContext(Dispatchers.IO) { FileUtils.zip(f, out) }
            load(currentDir); toast("Diarsipkan: ${out.name}")
        }
    }

    private fun unzipFile(f: File) {
        if (!f.name.endsWith(".zip")) { toast("Bukan file ZIP"); return }
        lifecycleScope.launch {
            val out = File(f.parentFile, f.nameWithoutExtension)
            withContext(Dispatchers.IO) { FileUtils.unzip(f, out) }
            load(currentDir); toast("Diekstrak ke: ${out.name}")
        }
    }

    private fun pasteHere() {
        val src = clipboard ?: run { toast("Clipboard kosong"); return }
        if (!src.exists()) { clipboard = null; isCut = false; b.fabPaste.hide(); toast("File sumber sudah tidak ada"); return }
        lifecycleScope.launch {
            try {
                val dst = File(currentDir, src.name)
                withContext(Dispatchers.IO) { if (isCut) FileUtils.move(src, dst) else FileUtils.copy(src, dst) }
                if (!dst.exists()) throw Exception("Gagal menulis ke ${currentDir.absolutePath}")
                if (isCut) { clipboard = null; isCut = false; b.fabPaste.hide() }
                load(currentDir)
                toast("Ditempel di: ${currentDir.name}")
            } catch (e: Exception) { toast("Gagal tempel: ${e.message ?: "Tidak bisa menulis ke folder ini"}") }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}