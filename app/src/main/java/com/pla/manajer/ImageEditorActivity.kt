package com.pla.manajer

import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pla.manajer.databinding.ActivityImageEditorBinding
import java.io.File
import java.io.FileOutputStream

class ImageEditorActivity : AppCompatActivity() {
    private lateinit var b: ActivityImageEditorBinding
    private lateinit var originalBitmap: Bitmap
    private lateinit var mutableBitmap: Bitmap
    private lateinit var canvas: Canvas
    private lateinit var paint: Paint
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
            // Buat bitmap yang bisa diedit (Mutable)
            mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            canvas = Canvas(mutableBitmap)
            b.ivImage.setImageBitmap(mutableBitmap)
            
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // --- SETUP DAFTAR PILIHAN (SPINNER) ---
        // 1. Daftar Ukuran 14px sampai 6000px
        val sizes = arrayOf("14", "100", "500", "720", "1080", "1400", "2000", "4000", "6000")
        val sizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sizes)
        b.spinnerWidth.adapter = sizeAdapter
        b.spinnerHeight.adapter = sizeAdapter
        
        // Set default ke ukuran asli gambar jika ada di daftar, kalau tidak set ke 1400
        val defaultW = sizes.indexOf(mutableBitmap.width.toString()).takeIf { it >= 0 } ?: 5
        val defaultH = sizes.indexOf(mutableBitmap.height.toString()).takeIf { it >= 0 } ?: 5
        b.spinnerWidth.setSelection(defaultW)
        b.spinnerHeight.setSelection(defaultH)

        // 2. Daftar Format Gambar
        val formats = arrayOf("JPG", "PNG", "WEBP")
        val formatAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, formats)
        b.spinnerFormat.adapter = formatAdapter

        // 3. Daftar Tes Warna
        val colorFilters = arrayOf("Normal (Asli)", "Grayscale (Hitam Putih)", "Sepia (Klasik)", "Invert (Negatif)")
        val colorAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, colorFilters)
        b.spinnerColorFilter.adapter = colorAdapter

        // Inisialisasi Paint untuk Teks / Brush
        paint = Paint().apply {
            color = Color.RED
            textSize = 100f
            isAntiAlias = true
            strokeWidth = 50f
            style = Paint.Style.STROKE
        }

        // --- LOGIKA RESIZE (Menggunakan Spinner) ---
        b.btnResize.setOnClickListener {
            val w = b.spinnerWidth.selectedItem.toString().toIntOrNull() ?: return@setOnClickListener
            val h = b.spinnerHeight.selectedItem.toString().toIntOrNull() ?: return@setOnClickListener
            
            mutableBitmap = Bitmap.createScaledBitmap(mutableBitmap, w, h, true)
            canvas = Canvas(mutableBitmap)
            b.ivImage.setImageBitmap(mutableBitmap)
            Toast.makeText(this, "Ukuran diubah ke $w x $h", Toast.LENGTH_SHORT).show()
        }

        // --- LOGIKA TAMBAH TEKS ---
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

        // --- LOGIKA HAPUS WATERMARK (MAGIC ERASER) ---
        b.btnMagicEraser.setOnClickListener {
            isEraserMode = !isEraserMode
            if (isEraserMode) {
                b.btnMagicEraser.text = "MODE HAPUS AKTIF (Gosok dengan Jari)"
                Toast.makeText(this, "Gosokkan jari pada tulisan watermark", Toast.LENGTH_LONG).show()
            } else {
                b.btnMagicEraser.text = "Aktifkan Hapus Watermark (Gosok dengan Jari)"
            }
        }

        b.ivImage.setOnTouchListener { _, event ->
            if (!isEraserMode) return@setOnTouchListener false
            
            val x = event.x
            val y = event.y
            
            val scaleX = mutableBitmap.width.toFloat() / b.ivImage.width
            val scaleY = mutableBitmap.height.toFloat() / b.ivImage.height
            val bmpX = x * scaleX
            val bmpY = y * scaleY

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = bmpX
                    lastY = bmpY
                }
                MotionEvent.ACTION_MOVE -> {
                    val eraserPaint = Paint().apply {
                        color = Color.TRANSPARENT
                        strokeWidth = 80f
                        style = Paint.Style.STROKE
                        maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
                    }
                    canvas.drawLine(lastX, lastY, bmpX, bmpY, eraserPaint)
                    lastX = bmpX
                    lastY = bmpY
                    b.ivImage.invalidate()
                }
            }
            true
        }

        // --- LOGIKA SIMPAN ---
        // Kita tidak perlu AlertDialog lagi karena format sudah dipilih di Spinner
        b.btnSaveImage.setOnClickListener { 
            val format = b.spinnerFormat.selectedItem.toString()
            saveImage(format) 
        }
    }

    private fun saveImage(format: String) {
        try {
            val fileName = "EDIT_${System.currentTimeMillis()}.${format.lowercase()}"
            val mimeType = when(format) {
                "JPG" -> "image/jpeg"
                "PNG" -> "image/png"
                else -> "image/webp"
            }
            
            // --- LOGIKA TES WARNA (COLOR FILTER) ---
            // Kita buat bitmap baru agar filter diterapkan saat disimpan tanpa merusak tampilan editor
            var finalBitmap = mutableBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val filterName = b.spinnerColorFilter.selectedItem.toString()
            
            if (filterName != "Normal (Asli)") {
                val filterCanvas = Canvas(finalBitmap)
                val filterPaint = Paint()
                val matrix = ColorMatrix()

                when (filterName) {
                    "Grayscale (Hitam Putih)" -> matrix.setSaturation(0f)
                    "Sepia (Klasik)" -> {
                        matrix.setSaturation(0.5f)
                        val sepiaMatrix = ColorMatrix(floatArrayOf(
                            0.393f, 0.769f, 0.189f, 0f, 0f,
                            0.349f, 0.686f, 0.168f, 0f, 0f,
                            0.272f, 0.534f, 0.131f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                        matrix.postConcat(sepiaMatrix)
                    }
                    "Invert (Negatif)" -> matrix.set(floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                }
                filterPaint.colorFilter = ColorMatrixColorFilter(matrix)
                filterCanvas.drawBitmap(mutableBitmap, 0f, 0f, filterPaint)
            }

            // Simpan ke folder Pictures
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val file = File(dir, fileName)
            val outputStream = FileOutputStream(file)

            val compressFormat = when(format) {
                "JPG" -> Bitmap.CompressFormat.JPEG
                "PNG" -> Bitmap.CompressFormat.PNG
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
            }

            // Simpan bitmap yang sudah difilter
            finalBitmap.compress(compressFormat, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            // Daftarkan ke Galeri
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.DATA, file.absolutePath)
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            Toast.makeText(this, "Gambar disimpan di Galeri: $fileName", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}