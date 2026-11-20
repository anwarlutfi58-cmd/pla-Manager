package com.pla.manajer

import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pla.manajer.adapter.MediaAdapter
import com.pla.manajer.adapter.MediaFolder
import com.pla.manajer.adapter.MediaFolderAdapter
import com.pla.manajer.adapter.MediaItem
import com.pla.manajer.databinding.ActivityMediaManagerBinding
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

        b.btnSort.setOnClickListener {
            Toast.makeText(this, "Sortir Media aktif", Toast.LENGTH_SHORT).show()
        }

        folderAdapter = MediaFolderAdapter { folder ->
            currentFolderId = folder.id
            loadFiles(folder.id)
        }

        fileAdapter = MediaAdapter(
            onOpen = { item -> openMediaFile(item) },
            onLong = { item -> enableSelectionMode(item) },
            onSelectToggle = { item -> toggleSelection(item) }
        )

        // Setup Menu Bawah
        b.btnDeleteSelected.setOnClickListener { deleteSelectedFiles() }
        b.btnCopySelected.setOnClickListener { 
            Toast.makeText(this, "Fitur Salin Media", Toast.LENGTH_SHORT).show()
            closeSelectionMode() 
        }
        b.btnMoveSelected.setOnClickListener { 
            Toast.makeText(this, "Fitur Pindah Media", Toast.LENGTH_SHORT).show()
            closeSelectionMode() 
        }
        b.btnRenameSelected.setOnClickListener { 
            val item = fileAdapter.selectedFiles.firstOrNull()
            if (item != null) renameMediaFile(item)
            closeSelectionMode() 
        }

        b.recyclerMedia.layoutManager = LinearLayoutManager(this)
        loadFolders()
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
            val folders = withContext(Dispatchers.IO) {
                val result = mutableMapOf<String, MediaFolder>()
                val contentUri = getContentUri()
                
                val projection = arrayOf(
                    MediaStore.MediaColumns.BUCKET_ID,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.MediaColumns._ID
                )

                val cursor = contentResolver.query(contentUri, projection, null, null, null)
                cursor?.use {
                    val bucketIdCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                    val bucketNameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)

                    while (it.moveToNext()) {
                        val bucketId = it.getString(bucketIdCol) ?: "unknown"
                        val bucketName = it.getString(bucketNameCol) ?: "Lainnya"
                        val id = it.getLong(idCol)
                        val uriString = Uri.withAppendedPath(contentUri, id.toString()).toString()

                        if (result.containsKey(bucketId)) {
                            val existing = result[bucketId]!!
                            result[bucketId] = existing.copy(count = existing.count + 1)
                        } else {
                            result[bucketId] = MediaFolder(bucketId, bucketName, uriString, 1)
                        }
                    }
                }
                result.values.sortedBy { it.name.lowercase() }
            }
            folderAdapter.submit(folders)
        }
    }

    private fun loadFiles(bucketId: String) {
        b.recyclerMedia.adapter = fileAdapter
        b.tvTitle.text = "Memuat file..."

        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
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
            if (list.isNotEmpty()) b.tvTitle.text = File(list[0].path).parentFile?.name ?: "File"
            fileAdapter.submit(list)
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

    // --- KUNCI PERBAIKAN: MENGGUNAKAN URI ASLI MEDIASTORE ---
    private fun openMediaFile(mediaItem: MediaItem) {
        try {
            val uri = Uri.parse(mediaItem.uriString) // Pakai URI dari database Android
            
            // Cari MIME tipe yang tepat
            var mime = mediaItem.mimeType
            if (mime == "*/*" || mime.isEmpty()) {
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(mediaItem.path).extension.lowercase()) ?: "*/*"
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newRawUri("", uri)
            }
            
            // Ini akan memunculkan pilihan aplikasi (Spotify, MX Player, Galeri, dll)
            startActivity(Intent.createChooser(intent, "Buka dengan"))
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka file: ${e.message}", Toast.LENGTH_SHORT).show()
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