package com.jamcat.nayngapo.ui

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * 상단 왼쪽 멈추기 / 나가기 버튼 — 땅 스테이지(GamePlayScreen)와 같은 자리·같은 생김새.
 * 하늘 스테이지도 이걸 써서 화면 구성을 맞춘다 (대표님 2026-09-20 「하늘에 나가기·멈추기 버튼이 없다」).
 */
object MenuButtons {
    enum class Hit { PAUSE, EXIT }

    private const val BTN_R = 46f
    /** 손가락은 그려진 원보다 굵다 — 판정만 넓힌다 */
    private const val HIT_R = 60f
    private const val BTN_Y = 118f
    private const val PAUSE_X = 110f
    private const val EXIT_X = 232f

    private val btnPaint = Paint().apply { color = Color.argb(165, 16, 18, 26); isAntiAlias = true }
    private val exitBtnPaint = Paint().apply { color = Color.argb(165, 34, 18, 22); isAntiAlias = true }
    private val edgePaint = Paint().apply {
        color = Color.argb(60, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true
    }
    private val iconPaint = Paint().apply { color = Color.argb(230, 238, 242, 250); isAntiAlias = true }
    private val iconStrokePaint = Paint().apply {
        color = Color.argb(230, 255, 150, 140); strokeWidth = 4.5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = Color.argb(150, 226, 232, 244); textSize = 20f; textAlign = Paint.Align.CENTER
        letterSpacing = 0.1f; isAntiAlias = true
    }
    private val playPath = Path()

    fun draw(canvas: Canvas, paused: Boolean) {
        canvas.drawCircle(PAUSE_X, BTN_Y, BTN_R, btnPaint)
        canvas.drawCircle(PAUSE_X, BTN_Y, BTN_R, edgePaint)
        if (paused) {
            playPath.reset()
            playPath.moveTo(PAUSE_X - 12f, BTN_Y - 18f); playPath.lineTo(PAUSE_X + 20f, BTN_Y); playPath.lineTo(PAUSE_X - 12f, BTN_Y + 18f); playPath.close()
            canvas.drawPath(playPath, iconPaint)
        } else {
            canvas.drawRect(PAUSE_X - 13f, BTN_Y - 16f, PAUSE_X - 5f, BTN_Y + 16f, iconPaint)
            canvas.drawRect(PAUSE_X + 5f, BTN_Y - 16f, PAUSE_X + 13f, BTN_Y + 16f, iconPaint)
        }
        canvas.drawTr("멈추기", PAUSE_X, BTN_Y + BTN_R + 30f, labelPaint)

        canvas.drawCircle(EXIT_X, BTN_Y, BTN_R, exitBtnPaint)
        canvas.drawCircle(EXIT_X, BTN_Y, BTN_R, edgePaint)
        canvas.drawLine(EXIT_X - 14f, BTN_Y - 14f, EXIT_X + 14f, BTN_Y + 14f, iconStrokePaint)
        canvas.drawLine(EXIT_X + 14f, BTN_Y - 14f, EXIT_X - 14f, BTN_Y + 14f, iconStrokePaint)
        canvas.drawTr("나가기", EXIT_X, BTN_Y + BTN_R + 30f, labelPaint)
    }

    /** 눌린 자리가 버튼이면 어느 버튼인지, 아니면 null */
    fun hitTest(px: Float, py: Float): Hit? {
        if (Math.hypot((px - PAUSE_X).toDouble(), (py - BTN_Y).toDouble()) < HIT_R) return Hit.PAUSE
        if (Math.hypot((px - EXIT_X).toDouble(), (py - BTN_Y).toDouble()) < HIT_R) return Hit.EXIT
        return null
    }
}
