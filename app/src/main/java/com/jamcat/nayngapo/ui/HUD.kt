package com.jamcat.nayngapo.ui

import android.graphics.*
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.entity.Player
import com.jamcat.nayngapo.level.Level

/**
 * 게임 HUD.
 * 캐릭터는 하찮고 귀엽게 두되, UI는 어두운 반투명 패널 + 얇은 서체 + 한 가지 강조색으로
 * 담백하고 세련되게 정리한다.
 */
class HUD {

    /** 지금 팀 냥이에게 내린 지시 (아군이 없으면 null — 줄을 그리지 않는다) */
    var allyOrderLabel: String? = null

    // ── 팔레트 ────────────────────────────────────────────────
    private val panelPaint = Paint().apply { color = Color.argb(165, 16, 18, 26); isAntiAlias = true }
    private val panelEdgePaint = Paint().apply {
        color = Color.argb(40, 255, 255, 255); style = Paint.Style.STROKE
        strokeWidth = 1.5f; isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = Color.argb(170, 226, 232, 244); textSize = 17f
        letterSpacing = 0.16f; isAntiAlias = true
    }
    private val valuePaint = Paint().apply {
        color = Color.WHITE; textSize = 30f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    private val accentPaint = Paint().apply { color = ACCENT; isAntiAlias = true }
    private val accentTextPaint = Paint().apply {
        color = ACCENT; textSize = 30f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    private val dividerPaint = Paint().apply { color = Color.argb(45, 255, 255, 255) }
    private val trackPaint = Paint().apply { color = Color.argb(90, 255, 255, 255); isAntiAlias = true }
    private val emptyHeartPaint = Paint().apply {
        color = Color.argb(70, 255, 255, 255); style = Paint.Style.STROKE
        strokeWidth = 2.5f; isAntiAlias = true
    }
    private val iconPaint = Paint().apply {
        color = Color.argb(220, 235, 240, 250); strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val smallValuePaint = Paint().apply {
        color = Color.WHITE; textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    /** 루비 모양 전용 (다른 페인트 설정을 건드리지 않게 따로 둔다) */
    private val gemPaint = Paint().apply { isAntiAlias = true }
    private val debtPaint = Paint().apply {
        color = Color.rgb(255, 110, 110); textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }

    private val rect = RectF()

    /** HUD 에 보여 줄 값 묶음 — 땅(GamePlayScreen)과 하늘(SkyStageScreen)이 같은 화면 구성을 쓴다 */
    data class Data(
        val stage: Int, val score: Int, val level: Int, val lives: Int, val rescueCount: Int,
        /** 필살기 게이지 0~1 */
        val specialFill: Float, val hp: Int, val maxHp: Int, val defeated: Int, val total: Int,
        /** 마지막 하트가 반 칸만 남았는가 (새끼 고양이에게 물림) */
        val halfLost: Boolean = false,
    )

    fun draw(canvas: Canvas, player: Player, level: Level, screenWidth: Int, screenHeight: Int) {
        draw(canvas, Data(
            stage = level.stageNumber, score = player.score, level = player.level, lives = player.lives,
            rescueCount = player.rescueCount,
            specialFill = (1f - player.specialCooldown / Constants.SPECIAL_COOLDOWN).coerceIn(0f, 1f),
            hp = player.hp, maxHp = player.maxHp, defeated = level.defeatedEnemies, total = level.totalEnemies,
            halfLost = player.halfLost,
        ), screenWidth, screenHeight)
    }

    fun draw(canvas: Canvas, d: Data, screenWidth: Int, screenHeight: Int) {
        val w = screenWidth.toFloat()
        val h = screenHeight.toFloat()

        drawTopBar(canvas, d, w)
        drawSideStats(canvas, d, w)
        drawHearts(canvas, d, w, h)
        drawProgress(canvas, d, w, h)
    }

    /** 상단 가운데: STAGE · SCORE · LEVEL 을 한 패널에 담는다 */
    private fun drawTopBar(canvas: Canvas, d: Data, w: Float) {
        val panelW = 620f
        val panelH = 74f
        val left = w / 2f - panelW / 2f
        rect.set(left, 14f, left + panelW, 14f + panelH)
        canvas.drawRoundRect(rect, 18f, 18f, panelPaint)
        canvas.drawRoundRect(rect, 18f, 18f, panelEdgePaint)

        val cellW = panelW / 3f
        val labelY = 14f + 27f
        val valueY = 14f + 60f

        // STAGE
        var cx = left + cellW * 0.5f
        canvas.drawText("STAGE", cx - textW("STAGE", labelPaint) / 2f, labelY, labelPaint)
        val stageText = "${d.stage}"
        val totalText = " / ${GameProgress.TOTAL_STAGES}"
        val stageW = textW(stageText, accentTextPaint) + textW(totalText, labelPaint)
        canvas.drawText(stageText, cx - stageW / 2f, valueY, accentTextPaint)
        canvas.drawText(totalText, cx - stageW / 2f + textW(stageText, accentTextPaint), valueY, labelPaint)

        canvas.drawRect(left + cellW - 0.5f, 30f, left + cellW + 0.5f, 72f, dividerPaint)

        // SCORE
        cx = left + cellW * 1.5f
        canvas.drawText("SCORE", cx - textW("SCORE", labelPaint) / 2f, labelY, labelPaint)
        val score = "%,d".format(d.score)
        canvas.drawText(score, cx - textW(score, valuePaint) / 2f, valueY, valuePaint)

        canvas.drawRect(left + cellW * 2 - 0.5f, 30f, left + cellW * 2 + 0.5f, 72f, dividerPaint)

        // LEVEL
        cx = left + cellW * 2.5f
        canvas.drawText("LEVEL", cx - textW("LEVEL", labelPaint) / 2f, labelY, labelPaint)
        val lv = "${d.level}"
        canvas.drawText(lv, cx - textW(lv, valuePaint) / 2f, valueY, valuePaint)
    }

    /** 우상단: 남은 목숨 · 구출한 인간 · 필살기 게이지 · 모은 루비 */
    private fun drawSideStats(canvas: Canvas, d: Data, w: Float) {
        val panelW = 208f
        val panelH = if (allyOrderLabel != null) 182f else 146f   // 루비 줄 + 지시 줄
        val left = w - panelW - 22f
        val top = 14f
        rect.set(left, top, left + panelW, top + panelH)
        canvas.drawRoundRect(rect, 18f, 18f, panelPaint)
        canvas.drawRoundRect(rect, 18f, 18f, panelEdgePaint)

        // 목숨
        drawLifeIcon(canvas, left + 34f, top + 32f)
        canvas.drawText("×${d.lives}", left + 58f, top + 40f, smallValuePaint)

        // 구출한 인간
        drawDoorIcon(canvas, left + 122f, top + 32f)
        canvas.drawText("×${d.rescueCount}", left + 146f, top + 40f, smallValuePaint)

        // 필살기 게이지
        val gaugeY = top + 74f
        val gaugeL = left + 22f
        val gaugeR = left + panelW - 22f
        canvas.drawText("SPECIAL", gaugeL, gaugeY - 8f, labelPaint)
        rect.set(gaugeL, gaugeY, gaugeR, gaugeY + 8f)
        canvas.drawRoundRect(rect, 4f, 4f, trackPaint)

        val fill = d.specialFill.coerceIn(0f, 1f)
        if (fill > 0f) {
            rect.set(gaugeL, gaugeY, gaugeL + (gaugeR - gaugeL) * fill, gaugeY + 8f)
            canvas.drawRoundRect(rect, 4f, 4f, accentPaint)
        }

        // 쓰러뜨린 냥이가 떨어뜨린 루비가 쌓이는 것을 여기서 본다
        val rubyCy = top + 116f
        drawRubyIcon(canvas, gaugeL + 11f, rubyCy, 11f, gemPaint)
        canvas.drawText("×${GameProgress.rubies}", gaugeL + 30f, rubyCy + 8f, smallValuePaint)
        if (GameProgress.rubyDebt > 0) canvas.drawText("빚 ${GameProgress.rubyDebt}", gaugeL + 110f, rubyCy + 8f, debtPaint)

        // 팀 냥이에게 내린 지시
        allyOrderLabel?.let { label ->
            canvas.drawText("냥이 지시: $label", gaugeL, top + 156f, labelPaint)
        }
    }

    /** 하단 가운데: 체력 하트를 얇은 트레이 위에 올린다 */
    private fun drawHearts(canvas: Canvas, d: Data, w: Float, h: Float) {
        val heart = SpriteManager.heart
        val step = heart.width + 8f
        val totalW = d.maxHp * step - 8f

        val trayPad = 18f
        rect.set(
            w / 2f - totalW / 2f - trayPad, h - heart.height - 42f,
            w / 2f + totalW / 2f + trayPad, h - 22f
        )
        canvas.drawRoundRect(rect, 16f, 16f, panelPaint)
        canvas.drawRoundRect(rect, 16f, 16f, panelEdgePaint)

        var x = w / 2f - totalW / 2f
        val y = h - heart.height - 32f
        for (i in 0 until d.maxHp) {
            if (i < d.hp) {
                if (d.halfLost && i == d.hp - 1) {
                    // 반 칸: 빈 하트 위에 왼쪽 절반만 그린다
                    rect.set(x + 4f, y + 6f, x + heart.width - 4f, y + heart.height - 4f)
                    canvas.drawRoundRect(rect, 5f, 5f, emptyHeartPaint)
                    canvas.save()
                    canvas.clipRect(x, y, x + heart.width / 2f, y + heart.height)
                    canvas.drawBitmap(heart, x, y, null)
                    canvas.restore()
                } else canvas.drawBitmap(heart, x, y, null)
            } else {
                rect.set(x + 4f, y + 6f, x + heart.width - 4f, y + heart.height - 4f)
                canvas.drawRoundRect(rect, 5f, 5f, emptyHeartPaint)
            }
            x += step
        }
    }

    /** 우하단: 남은 냥이 진행도 */
    private fun drawProgress(canvas: Canvas, d: Data, w: Float, h: Float) {
        val panelW = 236f
        val panelH = 60f
        val left = w - panelW - 22f
        val top = h - panelH - 22f
        rect.set(left, top, left + panelW, top + panelH)
        canvas.drawRoundRect(rect, 16f, 16f, panelPaint)
        canvas.drawRoundRect(rect, 16f, 16f, panelEdgePaint)

        canvas.drawText("CLEARED", left + 20f, top + 26f, labelPaint)
        val count = "${d.defeated}/${d.total}"
        canvas.drawText(count, left + panelW - 20f - textW(count, smallValuePaint), top + 26f, smallValuePaint)

        val barY = top + 38f
        rect.set(left + 20f, barY, left + panelW - 20f, barY + 8f)
        canvas.drawRoundRect(rect, 4f, 4f, trackPaint)
        val frac = if (d.total == 0) 0f
        else (d.defeated / d.total.toFloat()).coerceIn(0f, 1f)
        if (frac > 0f) {
            rect.set(left + 20f, barY, left + 20f + (panelW - 40f) * frac, barY + 8f)
            canvas.drawRoundRect(rect, 4f, 4f, accentPaint)
        }
    }

    /** 목숨 아이콘: 얇은 선으로 그린 사람 */
    private fun drawLifeIcon(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy - 12f, 5.5f, iconPaint)
        canvas.drawLine(cx, cy - 6f, cx, cy + 5f, iconPaint)
        canvas.drawLine(cx - 7f, cy - 1f, cx + 7f, cy - 1f, iconPaint)
        canvas.drawLine(cx, cy + 5f, cx - 5f, cy + 14f, iconPaint)
        canvas.drawLine(cx, cy + 5f, cx + 5f, cy + 14f, iconPaint)
    }

    /** 구출 아이콘: 얇은 선으로 그린 문 */
    private fun drawDoorIcon(canvas: Canvas, cx: Float, cy: Float) {
        rect.set(cx - 10f, cy - 14f, cx + 10f, cy + 14f)
        canvas.drawRoundRect(rect, 3f, 3f, iconPaint.apply { style = Paint.Style.STROKE })
        canvas.drawCircle(cx + 5f, cy + 1f, 2f, iconPaint.apply { style = Paint.Style.FILL })
    }

    private fun textW(text: String, paint: Paint) = paint.measureText(text)

    private companion object {
        val ACCENT = Color.rgb(255, 205, 84)
    }
}
