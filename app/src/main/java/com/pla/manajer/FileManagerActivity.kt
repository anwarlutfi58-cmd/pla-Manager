package com.pla.manajer

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pla.manajer.adapter.FileAdapter
import com.pla.manajer.data.Favorite
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
        
        // --- INI BAGIAN YANG DITAMBAHKAN ---
        // Menerima alamat folder dari MainActivity (Gambar, Video, dll)
        val initialPath = intent.getStringExtra("initial_path") ?: prefs.lastPath
        currentDir = File(initialPath)
        // -----------------------------------

        adapter = FileAdapter(
            onOpen     = { f -> open(f) },
            onLong     = { f -> showContextMenu(f) },
            onFavorite = { f -> toggleFavorite(f) }
        )
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.fabBack.setOnClickListener  { finish() }
        b.fabUp.setOnClickListener    { currentDir.parentFile?.let { load(it) } }
        b.fabNew.setOnClickListener   { showCreateDialog() }
        b.fabPaste.setOnClickListener { pasteHere() }

        b.etSearch.doAfterTextChanged { text ->
            val q = text?.toString()?.lowercase() ?: ""
            adapter.submit(if (q.isEmpty()) allFiles else allFiles.filter { it.name.lowercase().contains(q) })
        }
        ensurePermission()
    }

    override fun onResume() {
        super.onResume()
        if (::currentDir.isInitialized) load(currentDir)
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
            else legacyPerm.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun load(dir: File) {
        if (!dir.exists()) dir.mkdirs()
        currentDir = dir
        prefs.lastPath = dir.absolutePath
        b.tvPath.text = dir.absolutePath
        lifecycleScope.launch {
            allFiles = withContext(Dispatchers.IO) { FileUtils.listFiles(dir) }
            adapter.submit(allFiles)
        }
    }

    private fun open(f: File) {
        if (f.isDirectory) load(f)
        else startActivity(Intent(this, CodeEditorActivity::class.java).putExtra("path", f.absolutePath))
    }

    private fun showContextMenu(f: File) {
        val items = arrayOf("Buka", "Ganti Nama", "Salin", "Pindah", "Hapus", "Kompres (ZIP)", "Ekstrak (ZIP)")
        AlertDialog.Builder(this)
            .setTitle(f.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> open(f)
                    1 -> renameDialog(f)
                    2 -> { clipboard = f; isCut = false; toast("Disalin: ${f.name}") }
                    3 -> { clipboard = f; isCut = true;  toast("Siap pindah: ${f.name}") }
                    4 -> deleteFile(f)
                    5 -> zipFile(f)
                    6 -> unzipFile(f)
                }
            }
            .setNegativeButton("Tutup", null).show()
    }

    private fun showCreateDialog() {
        val input = EditText(this).apply {
            hint = "app/src/main/java/com/pla/agent/PlAiEngine.kt"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(this)
            .setTitle("Buat Jalur / Berkas Baru")
            .setMessage("Ketik struktur (contoh: app/build.gradle)")
            .setView(input)
            .setPositiveButton("Buat") { _, _ ->
                val path = input.text.toString().trim()
                if (path.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { FileUtils.createSmartPath(currentDir, path) }
                    load(currentDir)
                    toast("Dibuat: $path")
                }
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun renameDialog(f: File) {
        val input = EditText(this).apply { setText(f.name) }
        AlertDialog.Builder(this)
            .setTitle("Ganti Nama")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newFile = File(f.parentFile, input.text.toString())
                if (f.renameTo(newFile)) load(currentDir) else toast("Gagal rename")
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun deleteFile(f: File) {
        lifecycleScope.launch {
            val dao = (application as PlaApp).database.trashDao()
            withContext(Dispatchers.IO) {
                val trashDir = File(prefs.trashFolder).apply { mkdirs() }
                val target = File(trashDir, "${System.currentTimeMillis()}_${f.name}")
                FileUtils.move(f, target)
                dao.add(TrashItem(originalPath = f.absolutePath, trashPath = target.absolutePath, name = f.name, isDir = f.isDirectory))
            }
            load(currentDir)
            toast("Dipindah ke Sampah")
        }
    }

    private fun zipFile(f: File) {
        lifecycleScope.launch {
            val out = File(f.parentFile, f.name + ".zip")
            withContext(Dispatchers.IO) { FileUtils.zip(f, out) }
            load(currentDir)
            toast("Diarsipkan: ${out.name}")
        }
    }

    private fun unzipFile(f: File) {
        if (!f.name.endsWith(".zip")) { toast("Bukan file ZIP"); return }
        lifecycleScope.launch {
            val out = File(f.parentFile, f.nameWithoutExtension)
            withContext(Dispatchers.IO) { FileUtils.unzip(f, out) }
            load(currentDir)
            toast("Diekstrak ke: ${out.name}")
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
            load(currentDir)
            toast("Ditempel")
        }
    }

    private fun toggleFavorite(f: File) {
        lifecycleScope.launch {
            val dao = (application as PlaApp).database.favoriteDao()
            if (dao.exists(f.absolutePath)) { dao.remove(f.absolutePath); toast("Dihapus dari favorit") }
            else { dao.add(Favorite(f.absolutePath, f.name, f.isDirectory)); toast("Ditambahkan ke favorit") }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}