package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameView
import kotlin.math.sin

class StageClearScreen(
    private val gameView: GameView,
    private val stageNumber: Int,
    private val score: Int,
    private val rescueCount: Int,
    private val defeatedEnemies: Int,
    private val totalEnemies: Int,
    private val playerLevel: Int
) : Screen {
    private var timer = 0f

    private val bgPaint = Paint().apply { color = Color.rgb(20, 30, 50) }
    private val titlePaint = Paint().apply {
        color = Color.rgb(255, 220, 50); textSize = 64f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }
    private val statPaint = Paint().apply {
        color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val tapPaint = Paint().apply {
        color = Color.rgb(200, 200, 200); textSize = 24f; textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val starPaint = Paint().apply {
        color = Color.rgb(255, 220, 50); textSize = 48f; textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = Color.rgb(170, 180, 200); textSize = 24f; textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }
    private val numPaint = Paint().apply {
        color = Color.rgb(255, 220, 80); textSize = 28f; textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }

    override fun update(deltaTime: Float) {
        timer += deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Stars
        canvas.drawText("★  ★  ★", w / 2f, h * 0.2f, starPaint)

        canvas.drawText("STAGE $stageNumber CLEAR!", w / 2f, h * 0.36f, titlePaint)

        // 결과 정리: 점수·처치·구출·레벨
        val cxL = w / 2f - 20f
        val cxR = w / 2f + 20f
        var rowY = h * 0.48f
        val rowGap = h * 0.075f
        val rows = listOf(
            "점수" to "$score",
            "무찌른 냥이" to "$defeatedEnemies / $totalEnemies",
            "구출한 인간" to "${rescueCount}명",
            "도달 레벨" to "Lv.$playerLevel",
        )
        for ((label, value) in rows) {
            canvas.drawText(label, cxL, rowY, labelPaint)
            canvas.drawText(value, cxR, rowY, numPaint)
            rowY += rowGap
        }

        if (stageNumber == com.jamcat.nayngapo.engine.GameProgress.TOTAL_STAGES) {
            val endPaint = Paint().apply {
                color = Color.rgb(100, 255, 100); textSize = 36f; textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
            }
            canvas.drawText("마법사 냥이를 타도했습니다!", w / 2f, h * 0.79f, endPaint)
            canvas.drawText("인류에 평화가 찾아왔다!", w / 2f, h * 0.85f, endPaint)
        }

        val alpha = ((sin(timer.toDouble() * 3.0) * 0.5 + 0.5) * 255).toInt()
        tapPaint.alpha = alpha
        canvas.drawText("TAP TO CONTINUE", w / 2f, h * 0.90f, tapPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && timer > 1.5f) {
            gameView.showWorldMap()
        }
        return true
    }
}
