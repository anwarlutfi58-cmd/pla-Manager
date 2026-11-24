package com.pla.manajer.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

object ImageProHelper2 {

    // Double Exposure: Gabung 2 foto (foto kedua diberikan sebagai parameter)
    fun applyDoubleExposure(base: Bitmap, overlay: Bitmap): Bitmap {
        val res = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(res)
        canvas.drawBitmap(base, 0f, 0f, null)
        val paint = Paint()
        paint.alpha = 128 // Transparan 50%
        canvas.drawBitmap(overlay, null, Rect(0, 0, base.width, base.height), paint)
        return res
    }

    // Collage (Grid 2x2): Gabung 4 foto
    fun applyCollage(foto1: Bitmap, foto2: Bitmap, foto3: Bitmap, foto4: Bitmap): Bitmap {
        val w = 1000
        val h = 1000
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(res)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(Bitmap.createScaledBitmap(foto1, w / 2, h / 2, true), 0f, 0f, null)
        canvas.drawBitmap(Bitmap.createScaledBitmap(foto2, w / 2, h / 2, true), (w / 2).toFloat(), 0f, null)
        canvas.drawBitmap(Bitmap.createScaledBitmap(foto3, w / 2, h / 2, true), 0f, (h / 2).toFloat(), null)
        canvas.drawBitmap(Bitmap.createScaledBitmap(foto4, w / 2, h / 2, true), (w / 2).toFloat(), (h / 2).toFloat(), null)
        return res
    }

    // AI Background Remover (Simulasi: Blur area pinggir, tengah jelas)
    fun applyAutoBackgroundRemover(src: Bitmap): Bitmap {
        return ImageProHelper.applyFilter(src, "Blur")
    }
}