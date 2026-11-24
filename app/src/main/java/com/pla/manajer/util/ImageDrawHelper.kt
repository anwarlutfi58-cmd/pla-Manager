package com.pla.manajer.util

import android.graphics.*

object ImageDrawHelper {

    // 1. Menggambar Bentuk (Kotak, Lingkaran, Garis)
    fun drawShape(canvas: Canvas, shapeType: Int, startX: Float, startY: Float, endX: Float, endY: Float, color: Int, isFill: Boolean) {
        val paint = Paint().apply {
            this.color = color
            strokeWidth = 10f
            isAntiAlias = true
            style = if (isFill) Paint.Style.FILL else Paint.Style.STROKE
        }

        when (shapeType) {
            0 -> canvas.drawLine(startX, startY, endX, endY, paint) // Garis
            1 -> canvas.drawRect(startX, startY, endX, endY, paint) // Kotak
            2 -> { // Lingkaran
                val radius = Math.hypot((endX - startX).toDouble(), (endY - startY).toDouble()).toFloat()
                canvas.drawCircle(startX, startY, radius, paint)
            }
            3 -> { // Panah
                canvas.drawLine(startX, startY, endX, endY, paint)
                // Gambar kepala panah
                val angle = Math.atan2((endY - startY).toDouble(), (endX - startX).toDouble())
                val arrowSize = 30f
                val angle1 = angle - Math.PI / 6
                val angle2 = angle + Math.PI / 6
                canvas.drawLine(endX, endY, endX - arrowSize * Math.cos(angle1).toFloat(), endY - arrowSize * Math.sin(angle1).toFloat(), paint)
                canvas.drawLine(endX, endY, endX - arrowSize * Math.cos(angle2).toFloat(), endY - arrowSize * Math.sin(angle2).toFloat(), paint)
            }
        }
    }

    // 2. Kuas Bebas (Freehand Brush)
    fun drawBrush(canvas: Canvas, lastX: Float, lastY: Float, currentX: Float, currentY: Float, color: Int, strokeWidth: Float) {
        val paint = Paint().apply {
            this.color = color
            this.strokeWidth = strokeWidth
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawLine(lastX, lastY, currentX, currentY, paint)
    }

    // 3. Mengambil Warna dari Gambar (Color Picker / Pipet)
    fun getPixelColor(bitmap: Bitmap, x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= bitmap.width || y >= bitmap.height) return Color.BLACK
        return bitmap.getPixel(x, y)
    }
}