package com.jamcat.nayngapo.entity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * 공중 발판 — 슈퍼마리오의 떠 있는 벽돌 (대표님 2026-09-20). 점프해서 올라서고, 가장자리를 벗어나면 떨어진다.
 * 인간만 올라탄다(냥이들은 땅으로 다닌다). 사각형 하나에 벽돌 무늬.
 */
data class Platform(val x: Float, val y: Float, val width: Float, val height: Float = 34f) {
    val rect: RectF = RectF(x, y, x + width, y + height)
    val right: Float get() = x + width

    /** 발 중심 x 가 발판 위에 있는가 (가장자리 6px 는 아직 안전) */
    fun holds(centerX: Float): Boolean = centerX >= x - 6f && centerX <= right + 6f

    private val brickPaint = Paint().apply { color = Color.rgb(196, 120, 72); isAntiAlias = true }
    private val brickDark = Paint().apply { color = Color.rgb(150, 86, 48); isAntiAlias = true }
    private val edgePaint = Paint().apply { color = Color.rgb(60, 36, 20); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val linePaint = Paint().apply { color = Color.rgb(110, 62, 34); strokeWidth = 2f; isAntiAlias = true }
    private val topPaint = Paint().apply { color = Color.rgb(230, 170, 110); isAntiAlias = true }

    fun draw(canvas: Canvas) {
        canvas.drawRoundRect(rect, 6f, 6f, brickPaint)
        canvas.drawRect(x, y + height * 0.55f, right, y + height, brickDark)
        canvas.drawRect(x + 2f, y + 2f, right - 2f, y + 6f, topPaint)
        // 벽돌 줄무늬
        canvas.drawLine(x, y + height * 0.5f, right, y + height * 0.5f, linePaint)
        var bx = x + 34f
        var row = 0
        while (bx < right) {
            val yy0 = if (row % 2 == 0) y else y + height * 0.5f
            canvas.drawLine(bx, yy0, bx, yy0 + height * 0.5f, linePaint)
            bx += 68f
        }
        var bx2 = x + 68f
        while (bx2 < right) { canvas.drawLine(bx2, y + height * 0.5f, bx2, y + height, linePaint); bx2 += 68f }
        canvas.drawRoundRect(rect, 6f, 6f, edgePaint)
    }
}
