package com.pla.manajer

import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.pla.manajer.databinding.ActivityImageEditorBinding
import com.pla.manajer.util.*
import java.io.File
import java.io.FileOutputStream

class ImageEditorActivity : AppCompatActivity() {
    private lateinit var b: ActivityImageEditorBinding
    private lateinit var originalBitmap: Bitmap
    private lateinit var mutableBitmap: Bitmap
    private lateinit var canvas: Canvas
    private lateinit var notif: NotificationHelper

    private var currentMode = 0 // 0 = Normal, 1 = Eraser, 2 = Brush, 3 = Shape
    private var lastX = 0f
    private var lastY = 0f
    private var startX = 0f
    private var startY = 0f
    private var currentColor = Color.RED
    private var shapeType = 0

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityImageEditorBinding.inflate(layoutInflater)
        setContentView(b.root)
        notif = NotificationHelper(this)

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

        b.tabLayoutImage.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                b.layoutTabUkuran.visibility = View.GONE
                b.layoutTabPutar.visibility = View.GONE
                b.layoutTabWarna.visibility = View.GONE
                b.layoutTabLainnya.visibility = View.GONE
                b.layoutTabFilter.visibility = View.GONE
                b.layoutTabEfek.visibility = View.GONE
                when(tab?.position) {
                    0 -> b.layoutTabUkuran.visibility = View.VISIBLE
                    1 -> b.layoutTabPutar.visibility = View.VISIBLE
                    2 -> b.layoutTabWarna.visibility = View.VISIBLE
                    3 -> b.layoutTabLainnya.visibility = View.VISIBLE
                    4 -> b.layoutTabFilter.visibility = View.VISIBLE
                    5 -> b.layoutTabEfek.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        val sizes = arrayOf("14", "100", "500", "720", "1080", "1400", "2000", "4000", "6000")
        val sizeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sizes)
        b.spinnerWidth.adapter = sizeAdapter
        b.spinnerHeight.adapter = sizeAdapter
        b.spinnerWidth.setSelection(5)
        b.spinnerHeight.setSelection(5)

        val presets = arrayOf("Bebas", "1:1 (Persegi)", "3:4 (Pas Foto)", "4:6 (Pas Foto)", "16:9 (Video)")
        b.spinnerPreset.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, presets)

        val formats = arrayOf("JPG", "PNG", "WEBP")
        b.spinnerFormat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, formats)

        // Spinner Filter & Efek menggunakan ImageProHelper
        b.spinnerFilters.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ImageProHelper.filters)
        b.spinnerEfek.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf(
            "Normal", "Sketch", "Cartoon", "Oil Painting", "Pixelate", "Vignette", "Film Grain"
        ))

        b.btnRotateLeft.setOnClickListener { rotateImage(-90f) }
        b.btnRotateRight.setOnClickListener { rotateImage(90f) }
        b.btnFlip.setOnClickListener { flipImage() }
        b.btnAddText.setOnClickListener { showTextColorDialog() }

        b.btnMagicEraser.setOnClickListener {
            currentMode = if (currentMode == 1) 0 else 1
            b.btnMagicEraser.text = if (currentMode == 1) "MODE HAPUS AKTIF" else "Gosok Hapus Watermark"
            Toast.makeText(this, if(currentMode == 1) "Mode Hapus Aktif" else "Mode Normal", Toast.LENGTH_SHORT).show()
        }

        b.ivImage.setOnTouchListener { _, event ->
            if (currentMode == 0) return@setOnTouchListener false
            
            val scaleX = mutableBitmap.width.toFloat() / b.ivImage.width
            val scaleY = mutableBitmap.height.toFloat() / b.ivImage.height
            val bmpX = event.x * scaleX
            val bmpY = event.y * scaleY

            when (event.action) {
                MotionEvent.ACTION_DOWN -> { 
                    lastX = bmpX; lastY = bmpY
                    startX = bmpX; startY = bmpY
                }
                MotionEvent.ACTION_MOVE -> {
                    if (currentMode == 1) { // Eraser
                        val eraserPaint = Paint().apply {
                            color = Color.TRANSPARENT
                            strokeWidth = 80f
                            style = Paint.Style.STROKE
                            maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.drawLine(lastX, lastY, bmpX, bmpY, eraserPaint)
                        lastX = bmpX; lastY = bmpY
                    } else if (currentMode == 2) { // Kuas
                        ImageDrawHelper.drawBrush(canvas, lastX, lastY, bmpX, bmpY, currentColor, 15f)
                        lastX = bmpX; lastY = bmpY
                    } else if (currentMode == 3) { // Bentuk
                        canvas.drawBitmap(mutableBitmap, 0f, 0f, null)
                        ImageDrawHelper.drawShape(canvas, shapeType, startX, startY, bmpX, bmpY, currentColor, false)
                    }
                    b.ivImage.invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    if (currentMode == 3) {
                        canvas.drawBitmap(mutableBitmap, 0f, 0f, null)
                        ImageDrawHelper.drawShape(canvas, shapeType, startX, startY, bmpX, bmpY, currentColor, false)
                        b.ivImage.invalidate()
                    }
                }
            }
            true
        }

        b.btnApplyEffects.setOnClickListener { applyColorAndSizeEffects() }
        b.btnSaveImage.setOnClickListener { saveImageToGallery() }
    }

    private fun showTextColorDialog() {
        val input = EditText(this).apply { hint = "Ketik teks di sini..." }
        val colors = ImageFilterHelper.get20Colors()
        val colorNames = colors.map { it.first }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Tulis Teks & Pilih Warna")
            .setView(input)
            .setPositiveButton("Lanjut Pilih Warna") { _, _ ->
                val text = input.text.toString()
                if (text.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Pilih Warna Teks")
                        .setItems(colorNames) { _, which ->
                            val selectedColor = colors[which].second
                            val textPaint = Paint().apply {
                                color = selectedColor
                                textSize = mutableBitmap.width / 15f
                                isAntiAlias = true
                                setShadowLayer(5f, 5f, 5f, Color.BLACK)
                            }
                            canvas.drawText(text, 50f, mutableBitmap.height / 2f, textPaint)
                            b.ivImage.setImageBitmap(mutableBitmap)
                        }.show()
                }
            }
            .setNegativeButton("Batal", null).show()
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
        notif.showEdit("Menerapkan Efek", "Memproses gambar...")
        try {
            val brightness = b.seekBrightness.progress / 100f
            val contrast = b.seekContrast.progress / 100f
            val saturation = b.seekSaturation.progress / 100f

            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(saturation)

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

            // Terapkan Filter Warna dari Spinner
            val filterName = b.spinnerFilters.selectedItem.toString()
            if (filterName != "Normal") {
                mutableBitmap = ImageProHelper.applyFilter(mutableBitmap, filterName)
            }

            // Terapkan Efek Foto dari Spinner
            val efekName = b.spinnerEfek.selectedItem.toString()
            if (efekName != "Normal") {
                mutableBitmap = ImageProHelper.applyFilter(mutableBitmap, efekName)
            }

            val w = b.spinnerWidth.selectedItem.toString().toInt()
            val h = b.spinnerHeight.selectedItem.toString().toInt()

            var finalW = w
            var finalH = h
            when(b.spinnerPreset.selectedItemPosition) {
                1 -> finalH = finalW
                2 -> finalH = (finalW * 4) / 3
                3 -> finalH = (finalW * 6) / 4
                4 -> finalH = (finalW * 9) / 16
            }

            mutableBitmap = Bitmap.createScaledBitmap(mutableBitmap, finalW, finalH, true)
            canvas = Canvas(mutableBitmap)
            b.ivImage.setImageBitmap(mutableBitmap)
            Toast.makeText(this, "Filter dan Efek Diterapkan!", Toast.LENGTH_SHORT).show()
        } finally {
            notif.hideEdit()
        }
    }

    private fun saveImageToGallery() {
        notif.showEdit("Menyimpan Gambar", "Menulis ke PlaProjects/Edit_Gambar...")
        try {
            val format = b.spinnerFormat.selectedItem.toString()
            val fileName = "EDIT_${System.currentTimeMillis()}.${format.lowercase()}"

            val dir = File(Environment.getExternalStorageDirectory(), "PlaProjects/Edit_Gambar")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)

            val outputStream = FileOutputStream(file)
            val quality = if (b.cbCompress.isChecked) 50 else 100

            val compressFormat = when(format) {
                "JPG" -> Bitmap.CompressFormat.JPEG
                "PNG" -> Bitmap.CompressFormat.PNG
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
            }

            mutableBitmap.compress(compressFormat, quality, outputStream)
            outputStream.flush()
            outputStream.close()

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/${format.lowercase()}")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "PlaProjects/Edit_Gambar")
                } else {
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                }
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

            Toast.makeText(this, "Gambar disimpan di PlaProjects/Edit_Gambar", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            notif.hideEdit()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        notif.hideEdit()
    }
}