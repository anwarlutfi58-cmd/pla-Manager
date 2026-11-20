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
import com.pla.manajer.data.Favorite
import com.pla.manajer.data.TrashItem
import com.pla.manajer.databinding.ActivityFileManagerBinding
import com.pla.manajer.databinding.DialogFileActionBinding
import com.pla.manajer.databinding.DialogFileMoreBinding
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
    private var clipboard: File? = null
    private var isCut = false
    private var allFiles: List<File> = emptyList()

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
            onSelectToggle = { f -> toggleSelection(f) }
        )
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.btnTopBack.setOnClickListener { finish() }
        b.btnTopUp.setOnClickListener   { currentDir.parentFile?.let { load(it) } }
        b.btnTopNew.setOnClickListener  { showCreateDialog() }
        b.btnSort.setOnClickListener    { showSortDialog() }
        b.fabPaste.setOnClickListener   { pasteHere() }

        b.btnCloseSelection.setOnClickListener { closeSelectionMode() }
        b.btnSelectAll.setOnClickListener { 
            adapter.selectedFiles.clear()
            adapter.selectedFiles.addAll(allFiles)
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
            } else {
                toast("Pilih hanya 1 file untuk menyalin")
            }
        }

        b.btnMoveSelected.setOnClickListener {
            if (adapter.selectedFiles.size == 1) {
                clipboard = adapter.selectedFiles.first()
                isCut = true
                b.fabPaste.show()
                toast("Siap pindah: ${clipboard?.name}")
                closeSelectionMode()
            } else {
                toast("Pilih hanya 1 file untuk memindah")
            }
        }

        b.btnRenameSelected.setOnClickListener {
            if (adapter.selectedFiles.size == 1) {
                renameDialog(adapter.selectedFiles.first())
                closeSelectionMode()
            } else {
                toast("Pilih hanya 1 file untuk ganti nama")
            }
        }

        b.etSearch.doAfterTextChanged { refreshList() }
        ensurePermission()
    }

    // Fungsi untuk Tandai / Batal Tandai
    private fun toggleSelection(f: File) {
        if (adapter.selectedFiles.contains(f)) {
            adapter.selectedFiles.remove(f)
        } else {
            adapter.selectedFiles.add(f)
        }

        if (adapter.selectedFiles.isEmpty()) {
            closeSelectionMode()
        } else {
            enableSelectionMode(null)
        }
        adapter.notifyDataSetChanged()
    }

    fun updateSelectionUi() {
        if (adapter.selectedFiles.isEmpty()) closeSelectionMode()
        else b.tvSelectedCount.text = "${adapter.selectedFiles.size} terpilih"
    }

    private fun enableSelectionMode(f: File?) {
        adapter.isSelectionMode = true
        if (f != null) adapter.selectedFiles.add(f)
        adapter.notifyDataSetChanged()
        b.selectionToolbar.visibility = View.VISIBLE
        updateSelectionUi()
    }

    private fun closeSelectionMode() {
        adapter.isSelectionMode = false
        adapter.selectedFiles.clear()
        adapter.notifyDataSetChanged()
        b.selectionToolbar.visibility = View.GONE
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

    private fun open(f: File) {
        if (f.isDirectory) {
            closeSelectionMode()
            load(f)
        } else {
            val ext = f.extension.lowercase()
            val codeExts = listOf("kt", "java", "py", "js", "html", "css", "json", "md", "xml", "gradle", "kts", "txt", "properties", "yml", "yaml", "cpp", "c", "h")
            if (codeExts.contains(ext)) {
                startActivity(Intent(this, CodeEditorActivity::class.java).putExtra("path", f.absolutePath))
            } else {
                openWith(f)
            }
        }
    }

    private fun openWith(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
            var mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())
            if (mime == null) {
                mime = when (f.extension.lowercase()) {
                    "mp3" -> "audio/mpeg"
                    "mp4" -> "video/mp4"
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    else -> "*/*"
                }
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newRawUri("", uri)
            }
            startActivity(Intent.createChooser(intent, "Buka dengan"))
        } catch (e: Exception) {
            toast("Tidak ada aplikasi untuk membuka file ini")
        }
    }

    private fun showContextMenu(f: File) {
        val dialogBinding = DialogFileActionBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setTitle(f.name).setView(dialogBinding.root).setNegativeButton("Tutup", null).create()

        dialogBinding.actionCopy.setOnClickListener { 
            clipboard = f; isCut = false; toast("Disalin: ${f.name}"); b.fabPaste.show(); dialog.dismiss() 
        }
        dialogBinding.actionMove.setOnClickListener { 
            clipboard = f; isCut = true; toast("Siap pindah: ${f.name}"); b.fabPaste.show(); dialog.dismiss() 
        }
        dialogBinding.actionRename.setOnClickListener { dialog.dismiss(); renameDialog(f) }
        dialogBinding.actionDelete.setOnClickListener { dialog.dismiss(); deleteMultipleFiles(listOf(f)) }
        dialogBinding.actionMore.setOnClickListener { dialog.dismiss(); showMoreMenu(f) }
        dialog.show()
    }

    private fun showMoreMenu(f: File) {
        val dialogBinding = DialogFileMoreBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setTitle(f.name).setView(dialogBinding.root).setNegativeButton("Batal", null).create()

        dialogBinding.moreShare.setOnClickListener { shareFile(f); dialog.dismiss() }
        dialogBinding.moreShortcut.setOnClickListener { toast("Shortcut dibuat"); dialog.dismiss() }
        dialogBinding.moreCompress.setOnClickListener { zipFile(f); dialog.dismiss() }
        dialogBinding.moreOpenWith.setOnClickListener { openWith(f); dialog.dismiss() }
        dialog.show()
    }

    private fun shareFile(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", f)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("", uri)
            }
            startActivity(Intent.createChooser(intent, "Bagikan via"))
        } catch (e: Exception) {
            toast("Gagal membagikan: ${e.message}")
        }
    }

    private fun showSortDialog() {
        val options = arrayOf("Tidak disortir", "Nama ▲", "Nama ▼", "Ukuran ▲", "Ukuran ▼", "Tanggal ▲ (Terlama)", "Tanggal ▼ (Terbaru)", "Jenis ▲", "Jenis ▼")
        AlertDialog.Builder(this)
            .setTitle("Sortir Berdasarkan")
            .setSingleChoiceItems(options, prefs.sortMode) { dialog, which ->
                prefs.sortMode = which
                refreshList()
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun refreshList() {
        val query = b.etSearch.text?.toString()?.lowercase() ?: ""
        var filtered = if (query.isEmpty()) allFiles else allFiles.filter { it.name.lowercase().contains(query) }

        val mode = prefs.sortMode
        val dirs = filtered.filter { it.isDirectory }
        val files = filtered.filter { !it.isDirectory }

        val sortedDirs = when (mode) {
            1 -> dirs.sortedBy { it.name.lowercase() }; 2 -> dirs.sortedByDescending { it.name.lowercase() }
            3 -> dirs.sortedBy { it.length() }; 4 -> dirs.sortedByDescending { it.length() }
            5 -> dirs.sortedBy { it.lastModified() }; 6 -> dirs.sortedByDescending { it.lastModified() }
            7 -> dirs.sortedBy { it.extension.lowercase() }; 8 -> dirs.sortedByDescending { it.extension.lowercase() }
            else -> dirs.sortedBy { it.name.lowercase() }
        }

        val sortedFiles = when (mode) {
            0 -> files
            1 -> files.sortedBy { it.name.lowercase() }; 2 -> files.sortedByDescending { it.name.lowercase() }
            3 -> files.sortedBy { it.length() }; 4 -> files.sortedByDescending { it.length() }
            5 -> files.sortedBy { it.lastModified() }; 6 -> files.sortedByDescending { it.lastModified() }
            7 -> files.sortedBy { it.extension.lowercase() }; 8 -> files.sortedByDescending { it.extension.lowercase() }
            else -> files.sortedBy { it.name.lowercase() }
        }
        adapter.submit(sortedDirs + sortedFiles)
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
            val okRead  = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)  == PackageManager.PERMISSION_GRANTED
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
                    load(currentDir)
                    toast("Dibuat: $path")
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
            load(currentDir)
            toast("Diarsipkan: ${out.name}")
        }
    }

    private fun pasteHere() {
        val src = clipboard ?: run { toast("Clipboard kosong"); return }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dst = File(currentDir, src.name)
                if (isCut) FileUtils.move(src, dst) else FileUtils.copy(src, dst)
            }
            clipboard = null
            b.fabPaste.hide() 
            load(currentDir)
            toast("Ditempel")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}