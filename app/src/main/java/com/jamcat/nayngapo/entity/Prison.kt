package com.jamcat.nayngapo.entity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class Prison(val x: Float, val y: Float) {
    val width = 100f
    val height = 140f
    var isOpen = false
    var rescueCount = 3

    val hitBox: RectF get() = RectF(x, y - height, x + width, y)

    private val cagePaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val barPaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 3f
    }
    private val starPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val humanPaint = Paint().apply {
        color = Color.rgb(255, 200, 150)
    }

    fun open() {
        isOpen = true
    }

    fun draw(canvas: Canvas) {
        val top = y - height
        if (isOpen) {
            val openPaint = Paint().apply {
                color = Color.rgb(255, 215, 0)
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRect(x, top, x + width, y, openPaint)
            canvas.drawText("★", x + width / 2, top - 10f, starPaint)
        } else {
            canvas.drawRect(x, top, x + width, y, cagePaint)
            val barSpacing = width / 5f
            for (i in 1..4) {
                canvas.drawLine(x + i * barSpacing, top, x + i * barSpacing, y, barPaint)
            }
            val cx = x + width / 2f
            val cy = top + height / 2f
            canvas.drawCircle(cx, cy - 15f, 8f, humanPaint)
            canvas.drawRect(cx - 6f, cy - 7f, cx + 6f, cy + 15f, humanPaint)
        }
    }
}
