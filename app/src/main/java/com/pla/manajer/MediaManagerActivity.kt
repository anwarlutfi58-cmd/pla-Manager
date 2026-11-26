package com.pla.manajer

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
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
import com.pla.manajer.adapter.MediaBrowserAdapter
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
    private lateinit var adapter: MediaBrowserAdapter
    private lateinit var prefs: Prefs
    private lateinit var notif: NotificationHelper

    private var mediaType: String = "AUDIO"
    private var currentDir: File? = null
    private var allMediaFiles: List<File> = emptyList()
    private var currentRows: List<MediaBrowserAdapter.Row> = emptyList()
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

        b.selectionToolbar.visibility = View.GONE
        b.miniPlayerLayout.visibility = View.GONE

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

        // Tombol cari di toolbar
        val btnSearch = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(100, 100)
            setOnClickListener {
                searchBar.visibility = if (searchBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                if (searchBar.visibility == View.VISIBLE) searchBar.requestFocus()
            }
        }
        (b.btnSort.parent as LinearLayout).addView(btnSearch, 2)

        adapter = MediaBrowserAdapter(
            scope = lifecycleScope,
            onOpen = { f -> open(f) },
            onFileMenu = { f -> showFileMenu(f) }
        )
        b.recyclerMedia.layoutManager = LinearLayoutManager(this)
        b.recyclerMedia.adapter = adapter

        b.btnTopBack.setOnClickListener { handleBack() }
        b.btnSort.setOnClickListener { showSortDialog() }

        loadRoots()
    }

    private fun updateCrumb() {
        crumbBar.removeAllViews()
        val home = TextView(this).apply {
            text = "🏠"
            textSize = 20f
            setOnClickListener { loadRoots() }
        }
        crumbBar.addView(home)
        val dir = currentDir ?: return
        crumbBar.addView(TextView(this).apply { text = "  ›  "; textSize = 18f; setTextColor(Color.GRAY) })
        crumbBar.addView(TextView(this).apply {
            text = "📁 ${dir.name}"
            textSize = 15f
            setTextColor(Color.parseColor("#00BFA5"))
        })
    }

    private fun loadRoots() {
        currentDir = null
        searchQuery = ""
        searchBar.setText("")
        updateCrumb()
        lifecycleScope.launch {
            // SATU kali query database untuk semua file media (cepat & akurat)
            allMediaFiles = withContext(Dispatchers.IO) {
                MediaBrowser.queryAllMediaFiles(this@MediaManagerActivity, mediaType)
            }
            val rows = withContext(Dispatchers.IO) {
                val folders = LinkedHashMap<String, File>()
                for (f in allMediaFiles) {
                    val p = f.parentFile ?: continue
                    if (p.name.startsWith(".")) continue
                    if (!folders.containsKey(p.absolutePath)) folders[p.absolutePath] = p
                }
                folders.values.sortedBy { it.name.lowercase() }.map { dir ->
                    MediaBrowserAdapter.Row(
                        file = dir, isDir = true,
                        count = MediaBrowser.countMediaInDir(allMediaFiles, dir),
                        thumbSource = MediaBrowser.firstMediaInDir(allMediaFiles, dir)
                    )
                }
            }
            currentRows = rows
            applyFilter()
        }
    }

    private fun loadDir(dir: File) {
        currentDir = dir
        searchQuery = ""
        searchBar.setText("")
        updateCrumb()
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) {
                val (subDirs, files) = MediaBrowser.listDirContents(allMediaFiles, dir)
                val dirRows = subDirs.map {
                    MediaBrowserAdapter.Row(
                        it, true,
                        MediaBrowser.countMediaInDir(allMediaFiles, it),
                        MediaBrowser.firstMediaInDir(allMediaFiles, it)
                    )
                }
                val fileRows = files.map { MediaBrowserAdapter.Row(it, false, 0, it) }
                dirRows + fileRows
            }
            currentRows = rows
            applyFilter()
        }
    }

    private fun applyFilter() {
        val q = searchQuery.lowercase()
        val filtered = if (q.isEmpty()) currentRows else currentRows.filter { it.file.name.lowercase().contains(q) }
        val sorted = when (prefs.sortMode) {
            1 -> filtered.sortedWith(compareByDescending<MediaBrowserAdapter.Row> { it.isDir }.thenByDescending { it.file.name.lowercase() })
            2 -> filtered.sortedWith(compareByDescending<MediaBrowserAdapter.Row> { it.isDir }.thenByDescending { it.file.lastModified() })
            3 -> filtered.sortedWith(compareByDescending<MediaBrowserAdapter.Row> { it.isDir }.thenBy { it.file.lastModified() })
            4 -> filtered.sortedWith(compareByDescending<MediaBrowserAdapter.Row> { it.isDir }.thenByDescending { it.file.length() })
            5 -> filtered.sortedWith(compareByDescending<MediaBrowserAdapter.Row> { it.isDir }.thenBy { it.file.length() })
            else -> filtered.sortedWith(compareByDescending<MediaBrowserAdapter.Row> { it.isDir }.thenBy { it.file.name.lowercase() })
        }
        adapter.submit(sorted, exts)
    }

    private fun showSortDialog() {
        val options = arrayOf("Nama (A-Z)", "Nama (Z-A)", "Tanggal (Terbaru)", "Tanggal (Terlama)", "Ukuran (Terbesar)", "Ukuran (Terkecil)")
        AlertDialog.Builder(this).setTitle("Urutkan").setSingleChoiceItems(options, prefs.sortMode) { dialog, which ->
            prefs.sortMode = which; applyFilter(); dialog.dismiss()
        }.setNegativeButton("Batal", null).show()
    }

    private fun open(f: File) {
        if (f.isDirectory) { loadDir(f); return }
        val ext = f.extension.lowercase()
        when {
            ext in MediaBrowser.audioExts -> {
                val audioList = currentRows.filter { !it.isDir && it.file.extension.lowercase() in MediaBrowser.audioExts }
                    .map { it.file.absolutePath }
                val intent = Intent(this, AudioPlayerActivity::class.java)
                intent.putStringArrayListExtra("file_paths", ArrayList(audioList))
                intent.putExtra("start_path", f.absolutePath)
                startActivity(intent)
            }
            ext in MediaBrowser.imageExts -> {
                startActivity(Intent(this, ImageEditorActivity::class.java).putExtra("file_path", f.absolutePath))
            }
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
        } catch (e: Exception) { Toast.makeText(this, "Gagal buka file", Toast.LENGTH_SHORT).show() }
    }

    private fun handleBack() {
        if (currentDir != null) loadRoots() else finish()
    }

    override fun onBackPressed() { handleBack() }

    private fun showFileMenu(f: File) {
        val options = arrayOf("Buka", "Bagikan", "Jadikan ZIP", "Hapus")
        AlertDialog.Builder(this).setTitle(f.name).setItems(options) { _, which ->
            when (options[which]) {
                "Buka" -> open(f)
                "Bagikan" -> ShareHelper.shareFile(this, f)
                "Jadikan ZIP" -> zipFile(f)
                "Hapus" -> {
                    AlertDialog.Builder(this).setTitle("Hapus ${f.name}?")
                        .setPositiveButton("Hapus") { _, _ ->
                            f.delete()
                            loadRoots()
                        }.setNegativeButton("Batal", null).show()
                }
            }
        }.show()
    }

    private fun zipFile(f: File) {
        lifecycleScope.launch {
            notif.showFile("Kompres ZIP", "Mengompres ${f.name}...")
            val out = File(f.parentFile, f.name + ".zip")
            withContext(Dispatchers.IO) { FileUtils.zip(f, out) }
            currentDir?.let { loadDir(it) } ?: loadRoots()
            notif.showFile("Kompres selesai", "Berhasil")
        }
    }
}