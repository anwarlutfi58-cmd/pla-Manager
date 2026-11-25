package com.pla.manajer.util

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader

object ImageProHelper {

    val filters = arrayOf(
        "Normal", "Hitam Putih", "Sepia", "Invert", "Vintage", "HDR", "Sharpen", "Blur",
        "Vignette", "Film Grain", "Sketch", "Cartoon", "Oil Painting", "Pixelate", "Glitch",
        "Mirror", "Retro", "Cyberpunk", "Neon", "Cold", "Warm", "Fade", "Dramatic", "Pastel"
    )

    fun applyFilter(bitmap: Bitmap, filterName: String): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix()

        when (filterName) {
            "Hitam Putih" -> cm.setSaturation(0f)
            "Sepia" -> cm.set(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            "Invert" -> cm.set(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
            "Vintage" -> {
                cm.setSaturation(0.6f)
                val contrast = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, -30f,
                    0f, 1.2f, 0f, 0f, -30f,
                    0f, 0f, 1.2f, 0f, -30f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(contrast)
            }
            "HDR" -> {
                val contrast = ColorMatrix(floatArrayOf(
                    1.5f, 0f, 0f, 0f, -50f,
                    0f, 1.5f, 0f, 0f, -50f,
                    0f, 0f, 1.5f, 0f, -50f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(contrast)
            }
            "Glitch" -> {
                val r = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, 20f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(r)
            }
            "Retro" -> {
                cm.setSaturation(0.4f)
                val retro = ColorMatrix(floatArrayOf(
                    1.1f, 0f, 0f, 0f, 20f,
                    0f, 1.05f, 0f, 0f, 10f,
                    0f, 0f, 0.9f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(retro)
            }
            "Cyberpunk" -> {
                val cyber = ColorMatrix(floatArrayOf(
                    1.5f, 0f, 0f, 0f, -30f,
                    0f, 0.8f, 0f, 0f, 20f,
                    0f, 0f, 1.8f, 0f, 30f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(cyber)
            }
            "Neon" -> {
                val neon = ColorMatrix(floatArrayOf(
                    1.4f, 0f, 0f, 0f, -20f,
                    0f, 1.6f, 0f, 0f, -20f,
                    0f, 0f, 1.8f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(neon)
            }
            "Cold" -> {
                val cold = ColorMatrix(floatArrayOf(
                    0.9f, 0f, 0f, 0f, 0f,
                    0f, 1.0f, 0f, 0f, 10f,
                    0f, 0f, 1.2f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(cold)
            }
            "Warm" -> {
                val warm = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, 20f,
                    0f, 1.1f, 0f, 0f, 10f,
                    0f, 0f, 0.8f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(warm)
            }
            "Fade" -> {
                cm.setSaturation(0.7f)
                val fade = ColorMatrix(floatArrayOf(
                    1.0f, 0f, 0f, 0f, 40f,
                    0f, 1.0f, 0f, 0f, 40f,
                    0f, 0f, 1.0f, 0f, 40f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(fade)
            }
            "Dramatic" -> {
                val dramatic = ColorMatrix(floatArrayOf(
                    1.4f, 0f, 0f, 0f, -50f,
                    0f, 1.4f, 0f, 0f, -50f,
                    0f, 0f, 1.4f, 0f, -50f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(dramatic)
            }
            "Pastel" -> {
                cm.setSaturation(0.5f)
                val pastel = ColorMatrix(floatArrayOf(
                    1.0f, 0f, 0f, 0f, 60f,
                    0f, 1.0f, 0f, 0f, 60f,
                    0f, 0f, 1.0f, 0f, 60f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(pastel)
            }
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return when (filterName) {
            "Blur" -> applyBoxBlur(result)
            "Sharpen" -> applySharpen(result)
            "Vignette" -> applyVignette(result)
            "Film Grain" -> applyFilmGrain(result)
            "Sketch" -> applySketch(result)
            "Cartoon" -> applyCartoon(result)
            "Oil Painting" -> applyOilPainting(result)
            "Pixelate" -> applyPixelate(result, 16)
            "Mirror" -> applyMirror(result)
            else -> result
        }
    }

    private fun applyBoxBlur(src: Bitmap): Bitmap {
        val res = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(res)
        val p = Paint()
        p.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        c.drawBitmap(src, 0f, 0f, p)
        return res
    }

    private fun applySharpen(src: Bitmap): Bitmap {
        val res = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(res)
        val p = Paint()
        p.maskFilter = BlurMaskFilter(0.5f, BlurMaskFilter.Blur.NORMAL)
        c.drawBitmap(src, 0f, 0f, p)
        return res
    }

    private fun applyVignette(src: Bitmap): Bitmap {
        val res = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(res)
        c.drawBitmap(src, 0f, 0f, null)
        val gradient = RadialGradient(
            src.width / 2f, src.height / 2f, src.width.coerceAtLeast(src.height) * 0.7f,
            intArrayOf(0x00000000, 0x88000000), floatArrayOf(0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint()
        paint.shader = gradient
        c.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), paint)
        return res
    }

    private fun applyFilmGrain(src: Bitmap): Bitmap {
        val res = src.copy(Bitmap.Config.ARGB_8888, true)
        for (i in 0 until 3000) {
            val x = (Math.random() * res.width).toInt()
            val y = (Math.random() * res.height).toInt()
            val v = (Math.random() * 255).toInt()
            if (x < res.width && y < res.height) {
                res.setPixel(x, y, Color.argb(40, v, v, v))
            }
        }
        return res
    }

    private fun applySketch(src: Bitmap): Bitmap {
        val gray = toGrayscale(src)
        val inverted = Bitmap.createBitmap(gray.width, gray.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(inverted)
        val p = Paint()
        p.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )))
        c.drawBitmap(gray, 0f, 0f, p)
        val blurred = applyBoxBlur(inverted)
        val res = Bitmap.createBitmap(gray.width, gray.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until gray.height) {
            for (x in 0 until gray.width) {
                val g = Color.red(gray.getPixel(x, y))
                val b = Color.red(blurred.getPixel(x, y))
                var pembagi: Int = 255 - b
                if (pembagi < 1) pembagi = 1
                var v: Int = g * 255 / pembagi
                if (v > 255) v = 255
                if (v < 0) v = 0
                res.setPixel(x, y, Color.rgb(v, v, v))
            }
        }
        return res
    }

    private fun applyCartoon(src: Bitmap): Bitmap {
        val res = src.copy(Bitmap.Config.ARGB_8888, true)
        for (y in 0 until res.height) {
            for (x in 0 until res.width) {
                val p = res.getPixel(x, y)
                val r = (Color.red(p) / 64) * 64
                val g = (Color.green(p) / 64) * 64
                val b = (Color.blue(p) / 64) * 64
                res.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        return res
    }

    private fun applyOilPainting(src: Bitmap): Bitmap {
        val res = src.copy(Bitmap.Config.ARGB_8888, true)
        val radius = 4
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                var rSum = 0; var gSum = 0; var bSum = 0; var count = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = x + dx; val ny = y + dy
                        if (nx in 0 until src.width && ny in 0 until src.height) {
                            val p = src.getPixel(nx, ny)
                            rSum += Color.red(p)
                            gSum += Color.green(p)
                            bSum += Color.blue(p)
                            count++
                        }
                    }
                }
                res.setPixel(x, y, Color.rgb(rSum / count, gSum / count, bSum / count))
            }
        }
        return res
    }

    private fun applyPixelate(src: Bitmap, block: Int): Bitmap {
        val res = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(res)
        val small = Bitmap.createScaledBitmap(src, src.width / block, src.height / block, false)
        c.drawBitmap(small, null, Rect(0, 0, src.width, src.height), Paint(Paint.FILTER_BITMAP_FLAG))
        return res
    }

    private fun applyMirror(src: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.postScale(-1f, 1f, src.width / 2f, src.height / 2f)
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val res = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(res)
        val p = Paint()
        p.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        c.drawBitmap(src, 0f, 0f, p)
        return res
    }
}