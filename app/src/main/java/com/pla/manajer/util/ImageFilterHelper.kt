package com.pla.manajer.util

import android.graphics.*

object ImageFilterHelper {

    // 1. EFEK 3D (Emboss / Timbul)
    fun apply3D(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val matrix = ColorMatrix()
        
        // Matriks emboss untuk efek 3D
        val emboss = floatArrayOf(
            -2f, -1f, 0f, 0f, 0f,
            -1f,  1f, 1f, 0f, 0f,
             0f,  1f, 2f, 0f, 0f,
             0f,  0f, 0f, 1f, 0f
        )
        matrix.set(emboss)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    // 2. FILTER WARNA
    fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    fun applySepia(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val matrix = ColorMatrix()
        matrix.set(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    fun applyInvert(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val matrix = ColorMatrix()
        matrix.set(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    // 3. EFEK VIGNETTE (Pinggir Gelap)
    fun applyVignette(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        
        val paint = Paint()
        paint.isAntiAlias = true
        val shader = RadialGradient(
            bitmap.width / 2f, bitmap.height / 2f,
            Math.max(bitmap.width, bitmap.height) / 1.5f,
            intArrayOf(Color.TRANSPARENT, Color.BLACK),
            floatArrayOf(0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        return result
    }

    // 4. EFEK BLUR
    fun applyBlur(bitmap: Bitmap, radius: Int = 20): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        paint.maskFilter = BlurMaskFilter(radius.toFloat(), BlurMaskFilter.Blur.NORMAL)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    // 5. TEKS WARNA LENGKAP (20 PILIHAN)
    fun get20Colors(): Array<Pair<String, Int>> {
        return arrayOf(
            "Merah" to Color.RED,
            "Biru" to Color.BLUE,
            "Hijau" to Color.GREEN,
            "Kuning" to Color.YELLOW,
            "Hitam" to Color.BLACK,
            "Putih" to Color.WHITE,
            "Ungu" to Color.parseColor("#800080"),
            "Oranye" to Color.parseColor("#FFA500"),
            "Pink" to Color.parseColor("#FFC0CB"),
            "Coklat" to Color.parseColor("#8B4513"),
            "Abu-abu" to Color.GRAY,
            "Cyan" to Color.CYAN,
            "Magenta" to Color.MAGENTA,
            "Lime" to Color.parseColor("#00FF00"),
            "Navy" to Color.parseColor("#000080"),
            "Teal" to Color.parseColor("#008080"),
            "Gold" to Color.parseColor("#FFD700"),
            "Silver" to Color.parseColor("#C0C0C0"),
            "Maroon" to Color.parseColor("#800000"),
            "Olive" to Color.parseColor("#808000")
        )
    }
}