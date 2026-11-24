package com.pla.manajer.util

object ImageProHelper {

    val filters = arrayOf(
        "Normal", "Hitam Putih", "Sepia", "Invert", "Vintage", "HDR", "Sharpen", "Blur",
        "Vignette", "Film Grain", "Sketch", "Cartoon", "Oil Painting", "Pixelate", "Glitch",
        "Mirror", "Retro", "Cyberpunk", "Neon", "Cold", "Warm", "Fade", "Dramatic", "Pastel"
    )

    fun applyFilter(bitmap: android.graphics.Bitmap, filterName: String): android.graphics.Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint()
        val cm = android.graphics.ColorMatrix()

        when (filterName) {
            "Hitam Putih" -> cm.setSaturation(0f)
            "Sepia" -> {
                cm.set(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "Invert" -> cm.set(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
            "Vintage" -> {
                cm.setSaturation(0.6f)
                val contrast = android.graphics.ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, -30f,
                    0f, 1.2f, 0f, 0f, -30f,
                    0f, 0f, 1.2f, 0f, -30f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(contrast)
            }
            "HDR" -> {
                val contrast = android.graphics.ColorMatrix(floatArrayOf(
                    1.5f, 0f, 0f, 0f, -50f,
                    0f, 1.5f, 0f, 0f, -50f,
                    0f, 0f, 1.5f, 0f, -50f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(contrast)
            }
            "Sharpen" -> { /* Ditangani terpisah */ }
            "Blur" -> { /* Ditangani terpisah */ }
            "Vignette" -> { /* Ditangani terpisah */ }
            "Film Grain" -> { /* Ditangani terpisah */ }
            "Sketch" -> { /* Ditangani terpisah */ }
            "Cartoon" -> { /* Ditangani terpisah */ }
            "Oil Painting" -> { /* Ditangani terpisah */ }
            "Pixelate" -> { /* Ditangani terpisah */ }
            "Glitch" -> {
                val r = android.graphics.ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, 20f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(r)
            }
            "Mirror" -> { /* Ditangani terpisah */ }
            "Retro" -> {
                cm.setSaturation(0.4f)
                val retro = android.graphics.ColorMatrix(floatArrayOf(
                    1.1f, 0f, 0f, 0f, 20f,
                    0f, 1.05f, 0f, 0f, 10f,
                    0f, 0f, 0.9f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(retro)
            }
            "Cyberpunk" -> {
                val cyber = android.graphics.ColorMatrix(floatArrayOf(
                    1.5f, 0f, 0f, 0f, -30f,
                    0f, 0.8f, 0f, 0f, 20f,
                    0f, 0f, 1.8f, 0f, 30f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(cyber)
            }
            "Neon" -> {
                val neon = android.graphics.ColorMatrix(floatArrayOf(
                    1.4f, 0f, 0f, 0f, -20f,
                    0f, 1.6f, 0f, 0f, -20f,
                    0f, 0f, 1.8f, 0f, -20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(neon)
            }
            "Cold" -> {
                val cold = android.graphics.ColorMatrix(floatArrayOf(
                    0.9f, 0f, 0f, 0f, 0f,
                    0f, 1.0f, 0f, 0f, 10f,
                    0f, 0f, 1.2f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(cold)
            }
            "Warm" -> {
                val warm = android.graphics.ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, 20f,
                    0f, 1.1f, 0f, 0f, 10f,
                    0f, 0f, 0.8f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(warm)
            }
            "Fade" -> {
                cm.setSaturation(0.7f)
                val fade = android.graphics.ColorMatrix(floatArrayOf(
                    1.0f, 0f, 0f, 0f, 40f,
                    0f, 1.0f, 0f, 0f, 40f,
                    0f, 0f, 1.0f, 0f, 40f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(fade)
            }
            "Dramatic" -> {
                val dramatic = android.graphics.ColorMatrix(floatArrayOf(
                    1.4f, 0f, 0f, 0f, -50f,
                    0f, 1.4f, 0f, 0f, -50f,
                    0f, 0f, 1.4f, 0f, -50f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(dramatic)
            }
            "Pastel" -> {
                cm.setSaturation(0.5f)
                val pastel = android.graphics.ColorMatrix(floatArrayOf(
                    1.0f, 0f, 0f, 0f, 60f,
                    0f, 1.0f, 0f, 0f, 60f,
                    0f, 0f, 1.0f, 0f, 60f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(pastel)
            }
        }

        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // Filter khusus yang butuh operasi tambahan
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

    private fun applyBoxBlur(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val res = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(res)
        val p = android.graphics.Paint()
        p.maskFilter = android.graphics.BlurMaskFilter(8f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        c.drawBitmap(src, 0f, 0f, p)
        return res
    }

    private fun applySharpen(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val res = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
        val kernel = floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f
        )
        val c = android.graphics.Canvas(res)
        val p = android.graphics.Paint()
        p.maskFilter = android.graphics.BlurMaskFilter(0.5f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        c.drawBitmap(src, 0f, 0f, p)
        return res
    }

    private fun applyVignette(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val res = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(res)
        c.drawBitmap(src, 0f, 0f, null)
        val gradient = android.graphics.RadialGradient(
            src.width / 2f, src.height / 2f, src.width.coerceAtLeast(src.height) * 0.7f,
            intArrayOf(0x00000000, 0x88000000), floatArrayOf(0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        val paint = android.graphics.Paint()
        paint.shader = gradient
        c.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), paint)
        return res
    }

    private fun applyFilmGrain(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val res = src.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        for (i in 0 until 3000) {
            val x = (Math.random() * res.width).toInt()
            val y = (Math.random() * res.height).toInt()
            val v = (Math.random() * 255).toInt()
            if (x < res.width && y < res.height) {
                res.setPixel(x, y, android.graphics.Color.argb(40, v, v, v))
            }
        }
        return res
    }

    private fun applySketch(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val gray = toGrayscale(src)
        val inverted = android.graphics.Bitmap.createBitmap(gray.width, gray.height, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(inverted)
        val p = android.graphics.Paint()
        p.colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )))
        c.drawBitmap(gray, 0f, 0f, p)
        val blurred = applyBoxBlur(inverted)
        val res = android.graphics.Bitmap.createBitmap(gray.width, gray.height, android.graphics.Bitmap.Config.ARGB_8888)
        for (y in 0 until gray.height) {
            for (x in 0 until gray.width) {
                val g = android.graphics.Color.red(gray.getPixel(x, y))
                val b = android.graphics.Color.red(blurred.getPixel(x, y))
                val v = (g * 255 / (255 - b).coerceAtLeast(1)).coerceIn(0, 255)
                res.setPixel(x, y, android.graphics.Color.rgb(v, v, v))
            }
        }
        return res
    }

    private fun applyCartoon(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val res = src.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        for (y in 0 until res.height) {
            for (x in 0 until res.width) {
                val p = res.getPixel(x, y)
                val r = (android.graphics.Color.red(p) / 64) * 64
                val g = (android.graphics.Color.green(p) / 64) * 64
                val b = (android.graphics.Color.blue(p) / 64) * 64
                res.setPixel(x, y, android.graphics.Color.rgb(r, g, b))
            }
        }
        return res
    }

    private fun applyOilPainting(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val res = src.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val radius = 4
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                var rSum = 0; var gSum = 0; var bSum = 0; var count = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = x + dx; val ny = y + dy
                        if (nx in 0 until src.width && ny in 0 until src.height) {
                            val p = src.getPixel(nx, ny)
                            rSum += android.graphics.Color.red(p)
                            gSum += android.graphics.Color.green(p)
                            bSum += android.graphics.Color.blue(p)
                            count++
                        }
                    }
                }
                res.setPixel(x, y, android.graphics.Color.rgb(rSum / count, gSum / count, bSum / count))
            }
        }
        return res
    }

    private fun applyPixelate(src: android.graphics.Bitmap, block: Int): android.graphics.Bitmap {
        val res = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(res)
        val small = android.graphics.Bitmap.createScaledBitmap(src, src.width / block, src.height / block, false)
        c.drawBitmap(small, null, android.graphics.Rect(0, 0, src.width, src.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return res
    }

    private fun applyMirror(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postScale(-1f, 1f, src.width / 2f, src.height / 2f)
        return android.graphics.Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun toGrayscale(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val res = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(res)
        val p = android.graphics.Paint()
        p.colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
        c.drawBitmap(src, 0f, 0f, p)
        return res
    }
}