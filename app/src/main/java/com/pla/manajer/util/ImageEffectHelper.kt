package com.pla.manajer.util

import android.graphics.*

object ImageEffectHelper {

    // 1. Efek Mosaik (Pixelate)
    fun applyMosaic(bitmap: Bitmap, blockSize: Int = 30): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)

        for (y in 0 until height step blockSize) {
            for (x in 0 until width step blockSize) {
                val pixel = bitmap.getPixel(x, y)
                val endX = Math.min(x + blockSize, width)
                val endY = Math.min(y + blockSize, height)
                for (yy in y until endY) {
                    for (xx in x until endX) {
                        result.setPixel(xx, yy, pixel)
                    }
                }
            }
        }
        return result
    }

    // 2. Efek Sketsa Pensil (Pencil Sketch)
    fun applySketch(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val gray = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gray)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // Invert warna grayscale
        val inverted = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val invCanvas = Canvas(inverted)
        val invPaint = Paint()
        val invMatrix = ColorMatrix()
        invMatrix.set(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        invPaint.colorFilter = ColorMatrixColorFilter(invMatrix)
        invCanvas.drawBitmap(gray, 0f, 0f, invPaint)

        // Blur gambar yang di-invert
        val blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val blurCanvas = Canvas(blurred)
        val blurPaint = Paint()
        blurPaint.maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
        blurCanvas.drawBitmap(inverted, 0f, 0f, blurPaint)

        // Blend dengan Color Dodge
        val blendPaint = Paint()
        blendPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.COLOR_DODGE)
        val resultCanvas = Canvas(result)
        resultCanvas.drawBitmap(gray, 0f, 0f, null)
        resultCanvas.drawBitmap(blurred, 0f, 0f, blendPaint)

        return result
    }

    // 3. Efek Kartun (Posterize)
    fun applyCartoon(bitmap: Bitmap, levels: Int = 5): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        val contrast = (levels / 5f) + 1f
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f
        colorMatrix.set(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    // 4. Efek Tilt-Shift (Blur Area Pinggir)
    fun applyTiltShift(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        // Gambar asli di background
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        
        // Siapkan gambar blur
        val blurred = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val blurCanvas = Canvas(blurred)
        val blurPaint = Paint()
        blurPaint.maskFilter = BlurMaskFilter(25f, BlurMaskFilter.Blur.NORMAL)
        blurCanvas.drawBitmap(bitmap, 0f, 0f, blurPaint)

        // Masker gradasi untuk area blur (atas & bawah)
        val paint = Paint()
        paint.isAntiAlias = true
        val shader = LinearGradient(
            0f, 0f, 0f, bitmap.height.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(0f, 0.25f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        
        // Terapkan ke gambar blur (transparan di tengah)
        val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(mask)
        maskCanvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        
        // Xfermode untuk menempelkan blur hanya di pinggir
        val finalPaint = Paint()
        finalPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        maskCanvas.drawBitmap(blurred, 0f, 0f, finalPaint)
        
        canvas.drawBitmap(mask, 0f, 0f, null)
        return result
    }
}