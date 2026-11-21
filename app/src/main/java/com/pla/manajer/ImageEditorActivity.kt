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
            
            b.etWidth.setText(mutableBitmap.width.toString())
            b.etHeight.setText(mutableBitmap.height.toString())
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Inisialisasi Paint untuk Teks / Brush
        paint = Paint().apply {
            color = Color.RED
            textSize = 100f
            isAntiAlias = true
            strokeWidth = 50f
            style = Paint.Style.STROKE
        }

        // --- LOGIKA RESIZE ---
        b.btnResize.setOnClickListener {
            val w = b.etWidth.text.toString().toIntOrNull() ?: return@setOnClickListener
            val h = b.etHeight.text.toString().toIntOrNull() ?: return@setOnClickListener
            if (w in 100..6000 && h in 100..6000) {
                mutableBitmap = Bitmap.createScaledBitmap(mutableBitmap, w, h, true)
                canvas = Canvas(mutableBitmap)
                b.ivImage.setImageBitmap(mutableBitmap)
                Toast.makeText(this, "Ukuran diubah ke $w x $h", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Ukuran maksimal 6000px", Toast.LENGTH_SHORT).show()
            }
        }

        // --- LOGIKA TAMBAH TEKS ---
        b.btnAddText.setOnClickListener {
            val text = b.etText.text.toString()
            if (text.isNotEmpty()) {
                val textPaint = Paint().apply {
                    color = Color.RED
                    textSize = mutableBitmap.width / 15f // Ukuran teks menyesuaikan lebar gambar
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
            
            // Konversi koordinat layar ke koordinat Bitmap
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
                    // Menggunakan Blur Mask Filter untuk mengaburkan area watermark
                    val eraserPaint = Paint().apply {
                        color = Color.TRANSPARENT
                        strokeWidth = 80f
                        style = Paint.Style.STROKE
                        maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
                    }
                    // Ambil area sekitar dan gambar ulang (teknik blur)
                    canvas.drawLine(lastX, lastY, bmpX, bmpY, eraserPaint)
                    lastX = bmpX
                    lastY = bmpY
                    b.ivImage.invalidate()
                }
            }
            true
        }

        // --- LOGIKA SIMPAN (UBAH FORMAT & SIMPAN) ---
        b.btnSaveImage.setOnClickListener { showSaveDialog() }
    }

    private fun showSaveDialog() {
        val formats = arrayOf("JPG", "PNG", "WEBP")
        AlertDialog.Builder(this)
            .setTitle("Pilih Format Simpan")
            .setItems(formats) { _, which ->
                val format = formats[which]
                saveImage(format)
            }.show()
    }

    private fun saveImage(format: String) {
        try {
            val fileName = "EDIT_${System.currentTimeMillis()}.${format.lowercase()}"
            val mimeType = when(format) {
                "JPG" -> "image/jpeg"
                "PNG" -> "image/png"
                else -> "image/webp"
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

            mutableBitmap.compress(compressFormat, 100, outputStream)
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