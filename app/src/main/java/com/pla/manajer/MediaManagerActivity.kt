package com.pla.manajer

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
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
    
    // Adapter untuk Folder dan untuk File
    private lateinit var folderAdapter: MediaFolderAdapter
    private lateinit var fileAdapter: MediaAdapter
    
    // Menyimpan state: Apakah sedang melihat folder atau file di dalam folder
    private var currentFolderId: String? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMediaManagerBinding.inflate(layoutInflater)
        setContentView(b.root)

        mediaType = intent.getStringExtra("media_type") ?: "AUDIO"

        b.btnTopBack.setOnClickListener {
            if (currentFolderId != null) {
                // Kembali ke daftar folder
                currentFolderId = null
                loadFolders()
            } else {
                finish()
            }
        }

        folderAdapter = MediaFolderAdapter { folder ->
            currentFolderId = folder.id
            loadFiles(folder.id)
        }

        fileAdapter = MediaAdapter { mediaItem ->
            openFile(mediaItem)
        }

        b.recyclerMedia.layoutManager = LinearLayoutManager(this)
        loadFolders()
    }

    // --- 1. MEMUAT DAFTAR FOLDER (Seperti Screenshot) ---
    private fun loadFolders() {
        b.recyclerMedia.adapter = folderAdapter
        b.tvTitle.text = when(mediaType) {
            "AUDIO" -> "Audio"
            "VIDEO" -> "Video"
            "IMAGE" -> "Gambar"
            else -> "Media"
        }

        lifecycleScope.launch {
            val folders = withContext(Dispatchers.IO) {
                val result = mutableMapOf<String, MediaFolder>()
                val contentUri = getContentUri()
                
                val projection = arrayOf(
                    MediaStore.MediaColumns.BUCKET_ID,
                    MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.DATE_MODIFIED
                )

                val cursor = contentResolver.query(contentUri, projection, null, null, null)
                cursor?.use {
                    val bucketIdCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                    val bucketNameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val dateCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                    while (it.moveToNext()) {
                        val bucketId = it.getString(bucketIdCol) ?: "unknown"
                        val bucketName = it.getString(bucketNameCol) ?: "Lainnya"
                        val id = it.getLong(idCol)
                        val date = it.getLong(dateCol)
                        val uriString = Uri.withAppendedPath(contentUri, id.toString()).toString()

                        if (result.containsKey(bucketId)) {
                            // Jika folder sudah ada, tambah jumlah filenya
                            val existing = result[bucketId]!!
                            result[bucketId] = existing.copy(count = existing.count + 1)
                        } else {
                            // Jika folder baru, buat dengan cover file pertama
                            result[bucketId] = MediaFolder(bucketId, bucketName, uriString, 1)
                        }
                    }
                }
                result.values.sortedBy { it.name.lowercase() }
            }
            folderAdapter.submit(folders)
        }
    }

    // --- 2. MEMUAT DAFTAR FILE DI DALAM FOLDER ---
    private fun loadFiles(bucketId: String) {
        b.recyclerMedia.adapter = fileAdapter
        b.tvTitle.text = "Memuat..."

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

                        if (path.isNotEmpty()) {
                            result.add(MediaItem(id, name, path, size, date, uriString, mime))
                        }
                    }
                }
                result
            }
            if (list.isNotEmpty()) {
                // Ambil nama folder dari file pertama sebagai judul
                b.tvTitle.text = File(list[0].path).parentFile?.name ?: "File"
            }
            fileAdapter.submit(list)
        }
    }

    private fun openFile(mediaItem: MediaItem) {
        try {
            val file = File(mediaItem.path)
            if (!file.exists()) {
                Toast.makeText(this, "File tidak ditemukan", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            var mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: mediaItem.mimeType
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newRawUri("", uri)
            }
            startActivity(Intent.createChooser(intent, "Buka dengan"))
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka file: ${e.message}", Toast.LENGTH_LONG).show()
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