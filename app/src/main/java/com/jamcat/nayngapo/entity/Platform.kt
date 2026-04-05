package com.jamcat.nayngapo.entity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

data class Platform(val x: Float, val y: Float, val width: Float, val height: Float) {
    val rect: RectF = RectF(x, y, x + width, y + height)

    private val dirtPaint = Paint().apply {
        color = Color.rgb(101, 67, 33)
    }
    private val grassPaint = Paint().apply {
        color = Color.rgb(34, 139, 34)
    }

    fun draw(canvas: Canvas) {
        canvas.drawRect(rect, dirtPaint)
        canvas.drawRect(x, y, x + width, y + 8f, grassPaint)
    }
}
