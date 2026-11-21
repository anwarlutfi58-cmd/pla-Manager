package com.pla.manajer

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.pla.manajer.databinding.ActivityCollageBinding
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CollageActivity : AppCompatActivity() {
    private lateinit var b: ActivityCollageBinding
    private var bitmap1: Bitmap? = null
    private var bitmap2: Bitmap? = null

    // Pengambil Gambar dari Galeri
    private val pickImage1 = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            bitmap1 = getBitmapFromUri(uri)
            combineImages()
        }
    }

    private val pickImage2 = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            bitmap2 = getBitmapFromUri(uri)
            combineImages()
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityCollageBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnPickImage1.setOnClickListener { pickImage1.launch("image/*") }
        b.btnPickImage2.setOnClickListener { pickImage2.launch("image/*") }

        // Jika pilihan orientasi diganti, gabungkan ulang
        b.rgOrientation.setOnCheckedChangeListener { _, _ ->
            combineImages()
        }

        b.btnSaveCollage.setOnClickListener {
            if (bitmap1 == null || bitmap2 == null) {
                Toast.makeText(this, "Pilih kedua gambar terlebih dahulu!", Toast.LENGTH_SHORT).show()
            } else {
                saveCollageToGallery()
            }
        }
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return null
            // Downscale bitmap agar tidak OOM (Out of Memory)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            var scale = 1
            while (options.outWidth / scale > 2000 || options.outHeight / scale > 2000) {
                scale *= 2
            }

            val options2 = BitmapFactory.Options().apply { inSampleSize = scale }
            val inputStream2 = contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(inputStream2, null, options2)
            inputStream2?.close()
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun combineImages() {
        val img1 = bitmap1 ?: return
        val img2 = bitmap2 ?: return

        val isVertical = b.rbVertical.isChecked
        val width: Int
        val height: Int

        if (isVertical) {
            width = maxOf(img1.width, img2.width)
            height = img1.height + img2.height
        } else {
            width = img1.width + img2.width
            height = maxOf(img1.height, img2.height)
        }

        val combinedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combinedBitmap)

        if (isVertical) {
            canvas.drawBitmap(img1, 0f, 0f, null)
            canvas.drawBitmap(img2, 0f, img1.height.toFloat(), null)
        } else {
            canvas.drawBitmap(img1, 0f, 0f, null)
            canvas.drawBitmap(img2, img1.width.toFloat(), 0f, null)
        }

        b.ivCollagePreview.setImageBitmap(combinedBitmap)
        b.ivCollagePreview.tag = combinedBitmap // Simpan bitmap di tag untuk disave
    }

    private fun saveCollageToGallery() {
        try {
            val finalBitmap = b.ivCollagePreview.tag as? Bitmap ?: return
            val fileName = "COLLAGE_${System.currentTimeMillis()}.jpg"
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val file = File(dir, fileName)
            val outputStream = FileOutputStream(file)

            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATA, file.absolutePath)
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            Toast.makeText(this, "Collage disimpan di Galeri!", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}