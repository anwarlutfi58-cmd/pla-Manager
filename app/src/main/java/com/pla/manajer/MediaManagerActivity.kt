package com.pla.manajer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pla.manajer.adapter.MediaAdapter
import com.pla.manajer.adapter.MediaItem
import com.pla.manajer.databinding.ActivityMediaManagerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaManagerActivity : AppCompatActivity() {
    private lateinit var b: ActivityMediaManagerBinding
    private lateinit var adapter: MediaAdapter
    private var mediaType: String = "AUDIO"
    private var allMedia: List<MediaItem> = emptyList()
    private var isAscending = false

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMediaManagerBinding.inflate(layoutInflater)
        setContentView(b.root)

        mediaType = intent.getStringExtra("media_type") ?: "AUDIO"
        b.tvTitle.text = when(mediaType) {
            "AUDIO" -> "Daftar Audio"
            "VIDEO" -> "Daftar Video"
            "IMAGE" -> "Daftar Gambar"
            else -> "Media"
        }

        b.btnTopBack.setOnClickListener { finish() }
        b.btnSort.setOnClickListener {
            isAscending = !isAscending
            sortData()
            Toast.makeText(this, if(isAscending) "Terlama ke Terbaru" else "Terbaru ke Terlama", Toast.LENGTH_SHORT).show()
        }

        adapter = MediaAdapter { mediaItem ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(mediaItem.uriString), getMimeType(mediaType))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(Intent.createChooser(intent, "Buka dengan"))
            } catch (e: Exception) {
                Toast.makeText(this, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show()
            }
        }

        b.recyclerMedia.layoutManager = LinearLayoutManager(this)
        b.recyclerMedia.adapter = adapter

        loadMediaData()
    }

    private fun getMimeType(type: String): String {
        return when(type) {
            "AUDIO" -> "audio/*"
            "VIDEO" -> "video/*"
            "IMAGE" -> "image/*"
            else -> "*/*"
        }
    }

    private fun loadMediaData() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                val result = mutableListOf<MediaItem>()
                val contentUri = when (mediaType) {
                    "AUDIO" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    "VIDEO" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "IMAGE" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    else -> return@withContext emptyList<MediaItem>()
                }

                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED
                )

                val cursor = contentResolver.query(contentUri, projection, null, null, null)
                cursor?.use {
                    val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val pathCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                    while (it.moveToNext()) {
                        val id = it.getLong(idCol)
                        val name = it.getString(nameCol) ?: "Unknown"
                        val path = it.getString(pathCol) ?: ""
                        val size = it.getLong(sizeCol)
                        val date = it.getLong(dateCol) * 1000 
                        val uriString = Uri.withAppendedPath(contentUri, id.toString()).toString()

                        result.add(MediaItem(id, name, path, size, date, uriString))
                    }
                }
                result
            }
            allMedia = list
            sortData()
        }
    }

    private fun sortData() {
        val sorted = if (isAscending) {
            allMedia.sortedBy { it.dateModified }
        } else {
            allMedia.sortedByDescending { it.dateModified }
        }
        adapter.submit(sorted)
    }
}