package com.pla.manajer

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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
    private var currentFolders: List<MediaFolder> = emptyList()
    private var currentFiles: List<MediaItem> = emptyList()
    private var sortMode: Int = 2 

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMediaManagerBinding.inflate(layoutInflater)
        setContentView(b.root)

        mediaType = intent.getStringExtra("media_type") ?: "AUDIO"
        currentFolderId = null

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
                    val intent = Intent(this, AudioPlayerActivity::class.java)
                    intent.putStringArrayListExtra("file_paths", ArrayList(currentFiles.map { it.path }))
                    intent.putExtra("index", currentFiles.indexOf(item))
                    startActivity(intent)
                    setupMiniPlayer(item)
                } else {
                    openMediaFile(item)
                }
            },
            onLong = { item -> enableSelectionMode(item) },
            onSelectToggle = { item -> toggleSelection(item) }
        )

        b.btnDeleteSelected.setOnClickListener { deleteSelectedFiles() }
        b.btnCopySelected.setOnClickListener { Toast.makeText(this, "Disalin", Toast.LENGTH_SHORT).show(); closeSelectionMode() }
        b.btnMoveSelected.setOnClickListener { Toast.makeText(this, "Dipindah", Toast.LENGTH_SHORT).show(); closeSelectionMode() }
        b.btnRenameSelected.setOnClickListener { 
            val item = fileAdapter.selectedFiles.firstOrNull()
            if (item != null) renameMediaFile(item)
            closeSelectionMode() 
        }
        
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
        val options = mutableListOf("Bagikan", "Tambahkan ke Layar Beranda", "Kompresikan")
        
        // Jika ini file AUDIO, tambahkan menu Edit Audio
        if (mediaType == "AUDIO") {
            options.add(0, "Edit Audio")
        }

        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(options.toTypedArray()) { _, which ->
                val selectedOption = options[which]
                when (selectedOption) {
                    "Edit Audio" -> {
                        val intent = Intent(this, AudioEditorActivity::class.java)
                        intent.putExtra("file_path", item.path)
                        startActivity(intent)
                    }
                    "Tambahkan ke Layar Beranda" -> addToHomeScreen(item)
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

    private fun getContentUri(): Uri {
        return when (mediaType) {
            "AUDIO" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            "VIDEO" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            "IMAGE" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }
    }

    private fun loadFolders() {
        b.recyclerMedia.adapter = folderAdapter
        b.selectionToolbar.visibility = View.GONE
        b.tvTitle.text = when(mediaType) {
            "AUDIO" -> "Audio"; "VIDEO" -> "Video"; "IMAGE" -> "Gambar"; else -> "Media"
        }

        lifecycleScope.launch {
            currentFolders = withContext(Dispatchers.IO) {
                val result = mutableMapOf<String, MediaFolder>()
                val contentUri = getContentUri()
                
                val projection = arrayOf(
                    MediaStore.MediaColumns.BUCKET_ID,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.SIZE
                )

                val cursor = contentResolver.query(contentUri, projection, null, null, null)
                cursor?.use {
                    val bucketIdCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                    val bucketNameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val dateCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

                    while (it.moveToNext()) {
                        val bucketId = it.getString(bucketIdCol) ?: "unknown"
                        val bucketName = it.getString(bucketNameCol) ?: "Lainnya"
                        val id = it.getLong(idCol)
                        val date = it.getLong(dateCol) * 1000
                        val size = it.getLong(sizeCol)
                        val uriString = Uri.withAppendedPath(contentUri, id.toString()).toString()

                        if (result.containsKey(bucketId)) {
                            val existing = result[bucketId]!!
                            result[bucketId] = existing.copy(
                                count = existing.count + 1,
                                latestDate = maxOf(existing.latestDate, date),
                                totalSize = existing.totalSize + size
                            )
                        } else {
                            result[bucketId] = MediaFolder(bucketId, bucketName, uriString, 1, date, size)
                        }
                    }
                }
                result.values.toList()
            }
            applySort()
        }
    }

    private fun loadFiles(bucketId: String) {
        b.recyclerMedia.adapter = fileAdapter
        b.tvTitle.text = "Memuat file..."

        lifecycleScope.launch {
            currentFiles = withContext(Dispatchers.IO) {
                val result = mutableListOf<MediaItem>()
                val contentUri = getContentUri()
                val selection = "${MediaStore.MediaColumns.BUCKET_ID} = ?"
                val selectionArgs = arrayOf(bucketId)
                
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.MIME_TYPE 
                )

                val cursor = contentResolver.query(contentUri, projection, selection, selectionArgs, null)
                cursor?.use {
                    val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val pathCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val mimeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

                    while (it.moveToNext()) {
                        val id = it.getLong(idCol)
                        val name = it.getString(nameCol) ?: "Unknown"
                        val path = it.getString(pathCol) ?: ""
                        val size = it.getLong(sizeCol)
                        val date = it.getLong(dateCol) * 1000 
                        val uriString = Uri.withAppendedPath(contentUri, id.toString()).toString()
                        val mime = it.getString(mimeCol) ?: "*/*"

                        if (path.isNotEmpty()) result.add(MediaItem(id, name, path, size, date, uriString, mime))
                    }
                }
                result
            }
            if (currentFiles.isNotEmpty()) b.tvTitle.text = File(currentFiles[0].path).parentFile?.name ?: "File"
            applySort()
        }
    }

    private fun showSortDialog() {
        val options = arrayOf("Nama (A-Z)", "Nama (Z-A)", "Tanggal (Terbaru)", "Tanggal (Terlama)", "Ukuran (Terbesar)", "Ukuran (Terkecil)")
        AlertDialog.Builder(this)
            .setTitle("Sortir Berdasarkan")
            .setSingleChoiceItems(options, sortMode) { dialog, which ->
                sortMode = which
                applySort()
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun applySort() {
        if (currentFolderId == null) {
            val sortedFolders = when (sortMode) {
                0 -> currentFolders.sortedBy { it.name.lowercase() }
                1 -> currentFolders.sortedByDescending { it.name.lowercase() }
                2 -> currentFolders.sortedByDescending { it.latestDate }
                3 -> currentFolders.sortedBy { it.latestDate }
                4 -> currentFolders.sortedByDescending { it.totalSize }
                5 -> currentFolders.sortedBy { it.totalSize }
                else -> currentFolders
            }
            folderAdapter.submit(sortedFolders)
        } else {
            val sortedFiles = when (sortMode) {
                0 -> currentFiles.sortedBy { it.name.lowercase() }
                1 -> currentFiles.sortedByDescending { it.name.lowercase() }
                2 -> currentFiles.sortedByDescending { it.dateModified }
                3 -> currentFiles.sortedBy { it.dateModified }
                4 -> currentFiles.sortedByDescending { it.size }
                5 -> currentFiles.sortedBy { it.size }
                else -> currentFiles
            }
            fileAdapter.submit(sortedFiles)
        }
    }

    private fun enableSelectionMode(item: MediaItem) {
        fileAdapter.isSelectionMode = true
        fileAdapter.selectedFiles.add(item)
        fileAdapter.notifyDataSetChanged()
        b.selectionToolbar.visibility = View.VISIBLE
    }

    private fun toggleSelection(item: MediaItem) {
        if (fileAdapter.selectedFiles.contains(item)) {
            fileAdapter.selectedFiles.remove(item)
        } else {
            fileAdapter.selectedFiles.add(item)
        }
        fileAdapter.isSelectionMode = fileAdapter.selectedFiles.isNotEmpty()
        if (fileAdapter.selectedFiles.isEmpty()) closeSelectionMode() else fileAdapter.notifyDataSetChanged()
    }

    private fun closeSelectionMode() {
        fileAdapter.isSelectionMode = false
        fileAdapter.selectedFiles.clear()
        fileAdapter.notifyDataSetChanged()
        b.selectionToolbar.visibility = View.GONE
    }

    private fun deleteSelectedFiles() {
        val filesToDelete = fileAdapter.selectedFiles.toList()
        if (filesToDelete.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("Hapus ${filesToDelete.size} file?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        filesToDelete.forEach { item ->
                            val file = File(item.path)
                            if (file.exists()) file.delete()
                        }
                    }
                    Toast.makeText(this@MediaManagerActivity, "Dihapus", Toast.LENGTH_SHORT).show()
                    closeSelectionMode()
                    loadFiles(currentFolderId ?: "")
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun renameMediaFile(item: MediaItem) {
        val input = EditText(this).apply { setText(item.name) }
        AlertDialog.Builder(this)
            .setTitle("Ganti Nama")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newName = input.text.toString().trim()
                val oldFile = File(item.path)
                val newFile = File(oldFile.parentFile, newName)
                if (oldFile.renameTo(newFile)) {
                    Toast.makeText(this, "Berhasil diganti", Toast.LENGTH_SHORT).show()
                    loadFiles(currentFolderId ?: "")
                } else {
                    Toast.makeText(this, "Gagal ganti nama", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Batal", null).show()
    }

    private fun openMediaFile(mediaItem: MediaItem) {
        try {
            val uri = Uri.parse(mediaItem.uriString)
            var mime = mediaItem.mimeType
            if (mime == "*/*" || mime.isEmpty()) {
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(mediaItem.path).extension.lowercase()) ?: "*/*"
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            val resInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, "Buka dengan"))
        } catch (e: Exception) {
            try {
                val file = File(mediaItem.path)
                val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
                var mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val resInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Buka dengan"))
            } catch (ex: Exception) {
                Toast.makeText(this, "Gagal membuka file: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentFolderId != null) {
            currentFolderId = null
            loadFolders()
        } else {
            super.onBackPressed()
        }
    }
}