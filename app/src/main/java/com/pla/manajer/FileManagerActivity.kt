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
        showDateTime = prefs.getBoolean("showDateTime", false)

        adapter = FileAdapter(
            onOpen = { f -> if (!adapter.isSelectionMode) open(f) },
            onLong = { f -> enableSelectionMode(f) },
            onSelectToggle = { f -> toggleSelection(f) },
            onMoreOptions = { f -> showContextMenu(f) }
        ).apply { setDateTimeMode(showDateTime) }

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        // Sembunyikan tombol-tombol yang tidak perlu secara default
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

    private fun showDeleteDialog(files: List<File>) {
        val cb = CheckBox(this).apply { text = "Pindahkan ke Sampah (uncheck = permanen)"; isChecked = true }
        AlertDialog.Builder(this)
            .setTitle("Hapus ${files.size} item?")
            .setView(cb)
            .setPositiveButton("Hapus") { _, _ ->
                if (cb.isChecked) deleteToTrash(files) else deletePermanent(files)
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
            withContext(Dispatchers.IO) { files.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() } }
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

    // ... (Sisa fungsi lain seperti open, share, refreshList, dll. di bawah ini)
    // (Saya potong agar tidak kepanjangan, logika salin/pindah/hapus sudah diganti di atas)
    
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

    private fun refreshList() {
        val q = b.etSearch.text?.toString()?.lowercase() ?: ""
        val filtered = if (q.isEmpty()) allFiles else allFiles.filter { it.name.lowercase().contains(q) }
        // KUNCI: Folder di atas (true), File di bawah (false). Lalu urutkan.
        val sorted = filtered.sortedWith(compareBy<File> { !it.isDirectory }
            .thenBy { it.name.lowercase() })
        currentSortedFiles = sorted
        adapter.submit(sorted)
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

    // Fungsi pendukung lain (toast, context menu, open, dll) tetap gunakan milik Anda
    // (Bagian ini tidak saya ubah karena hanya fokus ke perbaikan di atas)
    private fun open(f: File) { /* ... kode asli ... */ }
    private fun showContextMenu(f: File) { /* ... kode asli ... */ }
    private fun shareFile(f: File) { /* ... kode asli ... */ }
    private fun showMultiSelectMenu() { /* ... kode asli ... */ }
    private fun pasteHere() { /* ... kode asli ... */ }
    private fun ensurePermission() { /* ... kode asli ... */ }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}