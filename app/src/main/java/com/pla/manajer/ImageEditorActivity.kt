package com.pla.manajer

import android.content.ContentValues
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.google.android.material.tabs.TabLayout
import com.pla.manajer.databinding.ActivityImageEditorBinding
import java.io.File
import java.io.FileOutputStream

class ImageEditorActivity : AppCompatActivity() {
    private lateinit var b: ActivityImageEditorBinding
    private lateinit var originalBitmap: Bitmap
    private lateinit var mutableBitmap: Bitmap
    private lateinit var canvas: Canvas
    
    private var isEraserMode = false
    private var lastX = 0f
    private var lastY = 0f

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityImageEditorBinding.inflate(layoutInflater)
        setContentView(b.root)

        val path = intent.getStringExtra("file_path") ?: run { finish(); return }
        val file = File(path)
        
        try {
            originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
            mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            canvas = Canvas(mutableBitmap)
            b.ivImage.setImageBitmap(mutableBitmap)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // --- 1. SETUP LOGIKA TAB ---
        b.tabLayoutImage.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                b.layoutTabUkuran.visibility = View.GONE
                b.layoutTabPutar.visibility = View.GONE
                b.layoutTabWarna.visibility = View.GONE
                b.layoutTabLainnya.visibility = View.GONE
                when(tab?.position) {
                    0 -> b.layoutTabUkuran.visibility = View.VISIBLE
                    1 -> b.layoutTabPutar.visibility = View.VISIBLE
                    2 -> b.layoutTabWarna.visibility = View.VISIBLE
                    3 -> b.layoutTabLainnya.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // --- 2. SETUP SPINNER (UKURAN & PRESET) ---
        val sizes = arrayOf("14", "100", "500", "720", "1080", "1400", "2000", "4000", "6000")
        val sizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sizes)
        b.spinnerWidth.adapter = sizeAdapter
        b.spinnerHeight.adapter = sizeAdapter
        b.spinnerWidth.setSelection(5) // Default 1400
        b.spinnerHeight.setSelection(5)

        val presets = arrayOf("Bebas", "1:1 (Persegi)", "3:4 (Pas Foto)", "4:6 (Pas Foto)", "16:9 (Video)")
        b.spinnerPreset.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, presets)

        val formats = arrayOf("JPG", "PNG", "WEBP")
        b.spinnerFormat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, formats)

        // --- 3. LOGIKA TOMBOL PUTAR & BALIK ---
        b.btnRotateLeft.setOnClickListener { rotateImage(-90f) }
        b.btnRotateRight.setOnClickListener { rotateImage(90f) }
        b.btnFlip.setOnClickListener { flipImage() }

        // --- 4. LOGIKA TOMBOL TAMBAH TEKS & ERASER ---
        b.btnAddText.setOnClickListener {
            val text = b.etText.text.toString()
            if (text.isNotEmpty()) {
                val textPaint = Paint().apply {
                    color = Color.RED
                    textSize = mutableBitmap.width / 15f
                    isAntiAlias = true
                }
                canvas.drawText(text, 50f, mutableBitmap.height / 2f, textPaint)
                b.ivImage.setImageBitmap(mutableBitmap)
                b.etText.setText("")
            }
        }

        b.btnMagicEraser.setOnClickListener {
            isEraserMode = !isEraserMode
            b.btnMagicEraser.text = if (isEraserMode) "MODE HAPUS AKTIF" else "Gosok Hapus Watermark"
        }

        b.ivImage.setOnTouchListener { _, event ->
            if (!isEraserMode) return@setOnTouchListener false
            val scaleX = mutableBitmap.width.toFloat() / b.ivImage.width
            val scaleY = mutableBitmap.height.toFloat() / b.ivImage.height
            val bmpX = event.x * scaleX
            val bmpY = event.y * scaleY

            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastX = bmpX; lastY = bmpY }
                MotionEvent.ACTION_MOVE -> {
                    val eraserPaint = Paint().apply {
                        color = Color.TRANSPARENT
                        strokeWidth = 80f
                        style = Paint.Style.STROKE
                        maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.drawLine(lastX, lastY, bmpX, bmpY, eraserPaint)
                    lastX = bmpX; lastY = bmpY
                    b.ivImage.invalidate()
                }
            }
            true
        }

        // --- 5. LOGIKA TERAPKAN EFEK (WARNA & UKURAN) ---
        b.btnApplyEffects.setOnClickListener {
            applyColorAndSizeEffects()
        }

        // --- 6. LOGIKA SIMPAN ---
        b.btnSaveImage.setOnClickListener { saveImageToGallery(file) }
    }

    private fun rotateImage(degrees: Float) {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        mutableBitmap = Bitmap.createBitmap(mutableBitmap, 0, 0, mutableBitmap.width, mutableBitmap.height, matrix, true)
        canvas = Canvas(mutableBitmap)
        b.ivImage.setImageBitmap(mutableBitmap)
    }

    private fun flipImage() {
        val matrix = Matrix()
        matrix.postScale(-1f, 1f, mutableBitmap.width / 2f, mutableBitmap.height / 2f)
        mutableBitmap = Bitmap.createBitmap(mutableBitmap, 0, 0, mutableBitmap.width, mutableBitmap.height, matrix, true)
        canvas = Canvas(mutableBitmap)
        b.ivImage.setImageBitmap(mutableBitmap)
    }

    private fun applyColorAndSizeEffects() {
        // 1. Terapkan Efek Warna (Brightness, Contrast, Saturation)
        val brightness = b.seekBrightness.progress / 100f
        val contrast = b.seekContrast.progress / 100f
        val saturation = b.seekSaturation.progress / 100f

        val colorMatrix = ColorMatrix()
        // Saturasi
        colorMatrix.setSaturation(saturation)
        // Kecerahan & Kontras
        val scale = brightness * contrast
        val translate = (-0.5f * scale + 0.5f) * 255f
        val brightnessMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(brightnessMatrix)

        val filteredBitmap = Bitmap.createBitmap(mutableBitmap.width, mutableBitmap.height, Bitmap.Config.ARGB_8888)
        val filterCanvas = Canvas(filteredBitmap)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        filterCanvas.drawBitmap(mutableBitmap, 0f, 0f, paint)
        
        mutableBitmap = filteredBitmap

        // 2. Terapkan Ukuran (Resize & Preset)
        val w = b.spinnerWidth.selectedItem.toString().toInt()
        val h = b.spinnerHeight.selectedItem.toString().toInt()
        
        // Logika Preset Pas Foto
        var finalW = w
        var finalH = h
        when(b.spinnerPreset.selectedItemPosition) {
            1 -> finalH = finalW // 1:1
            2 -> finalH = (finalW * 4) / 3 // 3:4
            3 -> finalH = (finalW * 6) / 4 // 4:6
            4 -> finalH = (finalW * 9) / 16 // 16:9
        }

        mutableBitmap = Bitmap.createScaledBitmap(mutableBitmap, finalW, finalH, true)
        canvas = Canvas(mutableBitmap)
        b.ivImage.setImageBitmap(mutableBitmap)
        Toast.makeText(this, "Efek dan Ukuran Diterapkan!", Toast.LENGTH_SHORT).show()
    }

    private fun saveImageToGallery(originalFile: File) {
        try {
            val format = b.spinnerFormat.selectedItem.toString()
            val fileName = "EDIT_${System.currentTimeMillis()}.${format.lowercase()}"
            
            // 1. Hapus EXIF jika dicentang
            if (b.cbRemoveExif.isChecked) {
                try {
                    // ExifInterface butuh file asli. Jika file tidak ditemukan, abaikan.
                    val exif = ExifInterface(originalFile.absolutePath)
                    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                    exif.setAttribute(ExifInterface.TAG_MAKE, null)
                    exif.setAttribute(ExifInterface.TAG_MODEL, null)
                    exif.saveAttributes()
                } catch (e: Exception) {
                    // Abaikan jika gagal hapus exif
                }
            }

            // 2. Simpan File
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val file = File(dir, fileName)
            val outputStream = FileOutputStream(file)

            // 3. Kompresi (Kualitas 100 atau 50 jika dicentang)
            val quality = if (b.cbCompress.isChecked) 50 else 100
            
            val compressFormat = when(format) {
                "JPG" -> Bitmap.CompressFormat.JPEG
                "PNG" -> Bitmap.CompressFormat.PNG
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
            }

            mutableBitmap.compress(compressFormat, quality, outputStream)
            outputStream.flush()
            outputStream.close()

            // 4. Daftarkan ke Galeri
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/*")
                put(MediaStore.Images.Media.DATA, file.absolutePath)
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            Toast.makeText(this, "Gambar disimpan: $fileName", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}