package com.jamcat.nayngapo.ui

import android.graphics.*
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.entity.Player
import com.jamcat.nayngapo.level.Level

/**
 * 전투화면 목업 스타일 HUD.
 * 상단 바(STAGE/SCORE/LEVEL) + 우상단 목숨/출구(구출 인원) + 우하단 PROGRESS.
 * 보스 게이지는 표시하지 않는다 — 필드의 냥이를 전부 무찌르면 클리어.
 */
class HUD {
    private val barPaint = Paint().apply { color = Constants.HUD_COLOR }
    private val labelPaint = Paint().apply {
        color = Color.WHITE; textSize = 18f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val valuePaint = Paint().apply {
        color = Color.rgb(255, 200, 50); textSize = 22f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val livesPaint = Paint().apply {
        color = Color.WHITE; textSize = 18f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val progressPaint = Paint().apply {
        color = Color.WHITE; textSize = 16f; textAlign = Paint.Align.RIGHT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val progressValuePaint = Paint().apply {
        color = Color.rgb(255, 200, 50); textSize = 20f; textAlign = Paint.Align.RIGHT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val specialBgPaint = Paint().apply { color = Color.DKGRAY }
    private val specialFgPaint = Paint().apply { color = Color.rgb(255, 200, 50) }
    private val stickPaint = Paint().apply {
        color = Color.WHITE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val stickHeadPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val doorPaint = Paint().apply { color = Color.rgb(150, 100, 60); isAntiAlias = true }
    private val doorFramePaint = Paint().apply {
        color = Color.rgb(230, 220, 200); style = Paint.Style.STROKE
        strokeWidth = 2f; isAntiAlias = true
    }
    private val doorKnobPaint = Paint().apply { color = Color.rgb(255, 220, 120); isAntiAlias = true }

    fun draw(canvas: Canvas, player: Player, level: Level, screenWidth: Int, screenHeight: Int) {
        val hudH = Constants.HUD_HEIGHT

        // Top HUD bar
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), hudH, barPaint)

        val thirdW = screenWidth / 3f

        // STAGE (현재 / 전체)
        canvas.drawText("STAGE", thirdW * 0.5f, 22f, labelPaint)
        canvas.drawText("${level.stageNumber} / ${GameProgress.TOTAL_STAGES}", thirdW * 0.5f, 48f, valuePaint)

        // SCORE
        canvas.drawText("SCORE", thirdW * 1.5f, 22f, labelPaint)
        canvas.drawText("${player.score}", thirdW * 1.5f, 48f, valuePaint)

        // LEVEL
        canvas.drawText("LEVEL", thirdW * 2.5f, 22f, labelPaint)
        canvas.drawText("${player.level}", thirdW * 2.5f, 48f, valuePaint)

        // 목숨 (우상단: 막대인간 + 개수)
        val livesX = screenWidth - 50f
        val livesY = hudH + 20f
        drawMiniStickFigure(canvas, livesX - 10f, livesY)
        canvas.drawText("x${player.lives}", livesX + 12f, livesY + 5f, livesPaint)

        // 출구 문 + 구출한 인간 수 (목업의 우상단 문 아이콘)
        val doorX = screenWidth - 60f
        val doorY = livesY + 38f
        canvas.drawRect(doorX - 12f, doorY - 16f, doorX + 12f, doorY + 16f, doorPaint)
        canvas.drawRect(doorX - 12f, doorY - 16f, doorX + 12f, doorY + 16f, doorFramePaint)
        canvas.drawCircle(doorX + 6f, doorY, 2.5f, doorKnobPaint)
        canvas.drawText("x${player.rescueCount}", doorX + 34f, doorY + 6f, livesPaint)

        // SP 게이지 (문 아래)
        val gaugeX = screenWidth - 90f
        val gaugeY = doorY + 30f
        val gaugeW = 70f
        val gaugeH = 8f
        canvas.drawRect(gaugeX, gaugeY, gaugeX + gaugeW, gaugeY + gaugeH, specialBgPaint)
        val fillW = gaugeW * (player.specialGauge / Constants.SPECIAL_MAX).coerceIn(0f, 1f)
        canvas.drawRect(gaugeX, gaugeY, gaugeX + fillW, gaugeY + gaugeH, specialFgPaint)
        val spLabel = Paint().apply {
            color = Color.WHITE; textSize = 10f; textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        canvas.drawText("SP", gaugeX + gaugeW / 2f, gaugeY - 2f, spLabel)

        // PROGRESS (우하단: 처치한 냥이 / 전체 냥이)
        val progX = screenWidth - 20f
        val progY = screenHeight - 50f
        canvas.drawText("PROGRESS", progX, progY, progressPaint)
        canvas.drawText(
            "${level.defeatedEnemies} / ${level.totalEnemies}",
            progX, progY + 24f, progressValuePaint
        )
    }

    private fun drawMiniStickFigure(canvas: Canvas, x: Float, y: Float) {
        // Head
        canvas.drawCircle(x, y - 12f, 5f, stickHeadPaint)
        // Body
        canvas.drawLine(x, y - 7f, x, y + 5f, stickPaint)
        // Arms
        canvas.drawLine(x - 7f, y - 3f, x + 7f, y - 3f, stickPaint)
        // Legs
        canvas.drawLine(x, y + 5f, x - 5f, y + 14f, stickPaint)
        canvas.drawLine(x, y + 5f, x + 5f, y + 14f, stickPaint)
    }
}
