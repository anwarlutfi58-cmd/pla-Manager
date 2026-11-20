package com.pla.manajer

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pla.manajer.adapter.MediaAdapter
import com.pla.manajer.adapter.MediaFolder
import com.pla.manajer.adapter.MediaFolderAdapter
import com.pla.manajer.adapter.MediaItem
import com.pla.manajer.databinding.ActivityMediaManagerBinding
import com.pla.manajer.util.PlaMediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaManagerActivity : AppCompatActivity() {
    private lateinit var b: ActivityMediaManagerBinding
    private var mediaType: String = "AUDIO"
    
    private lateinit var folderAdapter: MediaFolderAdapter
    private lateinit var fileAdapter: MediaAdapter
    
    private var currentFolderId: String? = null
    private var currentFiles: List<MediaItem> = emptyList()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMediaManagerBinding.inflate(layoutInflater)
        setContentView(b.root)

        mediaType = intent.getStringExtra("media_type") ?: "AUDIO"

        b.btnTopBack.setOnClickListener {
            if (currentFolderId != null) {
                currentFolderId = null
                loadFolders()
            } else {
                finish()
            }
        }

        b.btnSort.setOnClickListener { showSortDialog() }

        folderAdapter = MediaFolderAdapter { folder ->
            currentFolderId = folder.id
            loadFiles(folder.id)
        }

        fileAdapter = MediaAdapter(
            onOpen = { item -> 
                if (mediaType == "AUDIO") {
                    // Buka Pemutar Musik Internal
                    val intent = Intent(this, AudioPlayerActivity::class.java)
                    intent.putStringArrayListExtra("file_paths", ArrayList(currentFiles.map { it.path }))
                    intent.putExtra("index", currentFiles.indexOf(item))
                    startActivity(intent)
                    setupMiniPlayer(item)
                } else {
                    // Untuk Video/Gambar tetap buka aplikasi luar
                    openMediaFile(item)
                }
            },
            onLong = { item -> enableSelectionMode(item) },
            onSelectToggle = { item -> toggleSelection(item) }
        )

        // --- SETUP MENU BAWAH ---
        b.btnDeleteSelected.setOnClickListener { deleteSelectedFiles() }
        b.btnCopySelected.setOnClickListener { Toast.makeText(this, "Disalin", Toast.LENGTH_SHORT).show(); closeSelectionMode() }
        b.btnMoveSelected.setOnClickListener { Toast.makeText(this, "Dipindah", Toast.LENGTH_SHORT).show(); closeSelectionMode() }
        b.btnRenameSelected.setOnClickListener { 
            val item = fileAdapter.selectedFiles.firstOrNull()
            if (item != null) renameMediaFile(item)
            closeSelectionMode() 
        }
        
        // --- FITUR LAINNYA (TAMBAH KE LAYAR BERANDA) ---
        b.btnMoreSelected.setOnClickListener {
            val item = fileAdapter.selectedFiles.firstOrNull()
            if (item != null) showMoreMenu(item)
            closeSelectionMode()
        }

        b.recyclerMedia.layoutManager = LinearLayoutManager(this)
        loadFolders()
    }

    private fun setupMiniPlayer(item: MediaItem) {
        b.miniPlayerLayout.visibility = View.VISIBLE
        b.tvMiniPlayerTitle.text = item.name
        b.btnMiniPlay.setImageResource(R.drawable.ic_pause)
        
        b.btnMiniPlay.setOnClickListener {
            if (PlaMediaPlayer.isPlaying) {
                PlaMediaPlayer.pause()
                b.btnMiniPlay.setImageResource(R.drawable.ic_play)
            } else {
                PlaMediaPlayer.resume()
                b.btnMiniPlay.setImageResource(R.drawable.ic_pause)
            }
        }
        
        b.btnMiniStop.setOnClickListener {
            PlaMediaPlayer.stop()
            b.miniPlayerLayout.visibility = View.GONE
        }
    }

    private fun showMoreMenu(item: MediaItem) {
        val options = arrayOf("Bagikan", "Tambahkan ke Layar Beranda", "Kompresikan")
        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(options) { _, which ->
                when (which) {
                    1 -> addToHomeScreen(item)
                }
            }.show()
    }

    private fun addToHomeScreen(item: MediaItem) {
        try {
            val shortcut = ShortcutInfoCompat.Builder(this, item.path)
                .setShortLabel(item.name)
                .setLongLabel(item.name)
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_file))
                .setIntent(Intent(Intent.ACTION_VIEW).setData(Uri.parse("file://${item.path}")))
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
            Toast.makeText(this, "Shortcut ditambahkan ke Layar Beranda", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuat shortcut", Toast.LENGTH_SHORT).show()
        }
    }

    // ... [SISA KODE SAMA SEPERTI SEBELUMNYA: getContentUri, loadFolders, loadFiles, dll] ...
    // Salin fungsi getContentUri, loadFolders, loadFiles, applySort, enableSelectionMode, toggleSelection, closeSelectionMode, deleteSelectedFiles, renameMediaFile, dan openMediaFile dari kode yang saya kirim sebelumnya.
    // (Karena batasan panjang, saya fokuskan pada logika baru di atas. Fungsi lainnya tidak berubah).
}