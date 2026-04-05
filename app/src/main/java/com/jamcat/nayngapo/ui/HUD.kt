package com.jamcat.nayngapo.ui

import android.graphics.*
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.entity.Player
import com.jamcat.nayngapo.level.Level

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

    fun draw(canvas: Canvas, player: Player, level: Level, screenWidth: Int, screenHeight: Int) {
        val hudH = Constants.HUD_HEIGHT

        // Top HUD bar
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), hudH, barPaint)

        val thirdW = screenWidth / 3f

        // STAGE
        canvas.drawText("STAGE", thirdW * 0.5f, 22f, labelPaint)
        canvas.drawText("${level.stageNumber} / ${level.currentWave + 1}", thirdW * 0.5f, 48f, valuePaint)

        // SCORE
        canvas.drawText("SCORE", thirdW * 1.5f, 22f, labelPaint)
        canvas.drawText("${player.score}", thirdW * 1.5f, 48f, valuePaint)

        // LEVEL
        canvas.drawText("LEVEL", thirdW * 2.5f, 22f, labelPaint)
        canvas.drawText("${player.level} / ${player.maxHp}", thirdW * 2.5f, 48f, valuePaint)

        // Lives icon (top right - stick figure + count)
        val livesX = screenWidth - 50f
        val livesY = hudH + 20f
        drawMiniStickFigure(canvas, livesX - 10f, livesY)
        canvas.drawText("${player.lives}/${5}", livesX, livesY + 22f, livesPaint)

        // Special gauge bar (below lives)
        val gaugeX = screenWidth - 90f
        val gaugeY = livesY + 35f
        val gaugeW = 70f
        val gaugeH = 8f
        canvas.drawRect(gaugeX, gaugeY, gaugeX + gaugeW, gaugeY + gaugeH, specialBgPaint)
        val fillW = gaugeW * (player.specialGauge / Constants.SPECIAL_MAX).coerceIn(0f, 1f)
        canvas.drawRect(gaugeX, gaugeY, gaugeX + fillW, gaugeY + gaugeH, specialFgPaint)
        val spLabel = Paint().apply {
            color = Color.WHITE; textSize = 10f; textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        canvas.drawText("SP", gaugeX + gaugeW / 2f, gaugeY - 2f, spLabel)

        // PROGRESS (bottom right)
        val progX = screenWidth - 20f
        val progY = screenHeight - 50f
        canvas.drawText("PROGRESS", progX, progY, progressPaint)
        canvas.drawText(
            "${level.totalEnemies} / ${level.defeatedEnemies}",
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
