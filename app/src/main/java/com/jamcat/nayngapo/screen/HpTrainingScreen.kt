package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CatTeam
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SpriteManager
import kotlin.math.abs
import kotlin.math.sin

/**
 * 고양이 체력 훈련장 — 상점 마을 13번 천막 (대표님 2026-10-10 「고양이 체력 훈련장을 만들어 줘」).
 * 데려온 냥이 하나를 골라 러닝머신(GPT 그림 treadmill)을 달리게 하면 **30초마다 체력 +5** (영구, CatTeam.trainHp).
 * 투기장처럼 **루비 1개 = 1분** 쓴다 (남은 시간이 있을 때만 달린다). 값·늘어나는 양·시간은 Claude 판단.
 */
class HpTrainingScreen(private val gameView: GameView, catId: Int) : Screen {

    private val cat = CatTeam.cat(catId)
    private val sprite: Bitmap? = cat?.let {
        com.jamcat.nayngapo.entity.AllyCat(0f, 0f, it.kind, level = it.level, teamId = it.id,
            armored = it.hasArmor, attackBonus = it.attackBonus, coat = it.coat).spriteNow()
    }
    private val treadmill: Bitmap? = gameView.resources.getIdentifier("treadmill", "drawable", gameView.context.packageName)
        .takeIf { it != 0 }?.let { BitmapFactory.decodeResource(gameView.resources, it) }

    private var trained = 0f
    private var gained = 0
    private var timer = 0f
    private var popTimer = 0f
    private var noticeText = ""
    private var noticeTimer = 0f

    private val bg = Paint().apply { color = Color.rgb(226, 240, 250) }
    private val floor = Paint().apply { color = Color.rgb(196, 170, 140) }
    private val card = Paint().apply { color = Color.argb(225, 255, 250, 236); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val barBack = Paint().apply { color = Color.rgb(90, 80, 74); isAntiAlias = true }
    private val barFill = Paint().apply { color = Color.rgb(90, 200, 110); isAntiAlias = true }
    private val timeBtnPaint = Paint().apply { color = Color.rgb(190, 240, 170); isAntiAlias = true }
    private val sweat = Paint().apply { color = Color.rgb(120, 190, 255); isAntiAlias = true }
    private val speedLine = Paint().apply { color = Color.argb(150, 120, 120, 130); strokeWidth = 6f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val veil = Paint().apply { color = Color.argb(120, 0, 0, 0) }
    private val text = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val pop = Paint().apply {
        color = Color.rgb(60, 200, 90); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(5f, 0f, 2f, Color.argb(160, 0, 0, 0))
    }
    private val rect = RectF()
    private val timeBtn = RectF()
    private val exitRect = RectF()
    private val upRect = RectF()

    private val running: Boolean get() = cat != null && !cat.gone && timeLeft > 0f

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime
        if (popTimer > 0f) popTimer -= deltaTime
        if (!running || cat == null) return
        timeLeft = (timeLeft - deltaTime).coerceAtLeast(0f)
        trained += deltaTime
        if (trained >= CatTeam.HP_TRAIN_SECONDS) {
            trained -= CatTeam.HP_TRAIN_SECONDS
            if (CatTeam.trainHp(cat.id)) { gained += CatTeam.hpTrainGain; popTimer = 2f }
        }
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        canvas.drawRect(0f, 0f, w, h, bg)
        val floorY = h * 0.82f
        canvas.drawRect(0f, floorY, w, h, floor)
        text.textSize = h * 0.07f
        canvas.drawText("체력 훈련장", w / 2f, h * 0.10f, text)

        val c = cat
        if (c == null || c.gone || sprite == null) {
            text.textSize = h * 0.05f
            canvas.drawText("훈련할 냥이가 없어요", w / 2f, h / 2f, text)
            drawExit(canvas, w, h); return
        }

        // 러닝머신 + 그 위에서 달리는 냥이
        val tmH = h * 0.36f
        val tm = treadmill
        val tmW = if (tm != null) tm.width * tmH / tm.height else w * 0.5f
        val tmX = w * 0.5f
        rect.set(tmX - tmW / 2f, floorY - tmH, tmX + tmW / 2f, floorY)
        if (tm != null) canvas.drawBitmap(tm, null, rect, null)
        val beltY = floorY - tmH * 0.31f   // 벨트 윗면 (그림 아래에서 31%)
        val ch = h * 0.22f; val cw = sprite.width * ch / sprite.height
        val hop = if (running) abs(sin(timer * (12f + GameProgress.treadmillLevel * 2f))) * h * 0.025f else 0f   // Lv 이 높으면 더 빨리 달린다
        val catCx = tmX - tmW * 0.08f
        rect.set(catCx - cw / 2f, beltY - ch - hop, catCx + cw / 2f, beltY - hop)
        canvas.drawBitmap(sprite, null, rect, null)
        if (running) {
            // 뒤로 흐르는 속도 줄 · 땀방울
            for (k in 0 until 3) {
                val y = beltY - ch * (0.3f + k * 0.22f)
                val off = ((timer * 600f + k * 70f) % 120f)
                canvas.drawLine(rect.left - 30f - off, y, rect.left - 90f - off, y, speedLine)
            }
            val sy = rect.top + ((timer * 1.5f) % 1f) * ch * 0.4f
            canvas.drawCircle(rect.right - cw * 0.1f, sy, 9f, sweat)
        }

        // 왼쪽 위: 이름 · 체력
        rect.set(w * 0.03f, h * 0.05f, w * 0.33f, h * 0.27f)
        canvas.drawRoundRect(rect, 22f, 22f, card); canvas.drawRoundRect(rect, 22f, 22f, ink)
        text.textAlign = Paint.Align.LEFT
        text.textSize = h * 0.05f
        canvas.drawText(c.name, rect.left + 28f, rect.top + h * 0.065f, text)
        text.textSize = h * 0.042f
        val hp = CatTeam.maxHpFor(c.level) + CatTeam.hpBonusOf(c)
        canvas.drawText("체력 $hp", rect.left + 28f, rect.top + h * 0.125f, text)
        if (gained > 0) canvas.drawText("이번에 +$gained", rect.left + 28f, rect.top + h * 0.185f, text)
        text.textAlign = Paint.Align.CENTER

        if (popTimer > 0f) {
            pop.textSize = h * 0.09f
            pop.alpha = (popTimer.coerceAtMost(0.5f) / 0.5f * 255).toInt()
            canvas.drawText("체력 +${CatTeam.hpTrainGain}!", w / 2f, h * 0.36f - (2f - popTimer) * 40f, pop)
        }

        // 아래: 다음 +5 까지
        val left = (CatTeam.HP_TRAIN_SECONDS - trained).toInt().coerceAtLeast(0) + 1
        val bw = w * 0.5f; val bx = (w - bw) / 2f; val by = h * 0.90f; val bh = h * 0.045f
        rect.set(bx, by, bx + bw, by + bh); canvas.drawRoundRect(rect, bh / 2f, bh / 2f, barBack)
        rect.set(bx, by, bx + bw * (trained / CatTeam.HP_TRAIN_SECONDS).coerceIn(0f, 1f), by + bh); canvas.drawRoundRect(rect, bh / 2f, bh / 2f, barFill)
        rect.set(bx, by, bx + bw, by + bh); canvas.drawRoundRect(rect, bh / 2f, bh / 2f, ink)
        text.textSize = h * 0.038f
        canvas.drawText("다음 체력 +${CatTeam.hpTrainGain}까지 ${left}초", w / 2f, by - h * 0.02f, text)

        // 루비 1개 = 1분
        val sec = kotlin.math.ceil(timeLeft).toInt()
        if (timeLeft <= 0f) {
            canvas.drawRect(0f, 0f, w, h, veil)
            pop.textSize = h * 0.06f; pop.alpha = 255; pop.color = Color.WHITE
            canvas.drawText("루비 1개를 내면 1분 달릴 수 있어요", w / 2f, h * 0.48f, pop)
            pop.color = Color.rgb(60, 200, 90)
        }
        timeBtn.set(w / 2f - 300f, h * 0.15f, w / 2f + 300f, h * 0.15f + 96f)
        canvas.drawRoundRect(timeBtn, 48f, 48f, timeBtnPaint); canvas.drawRoundRect(timeBtn, 48f, 48f, ink)
        text.textSize = 40f
        canvas.drawText("⏱ ${sec / 60}:${"%02d".format(sec % 60)}   +1분 (루비 1)", timeBtn.centerX(), timeBtn.centerY() + 14f, text)

        drawExit(canvas, w, h)
        // 러닝머신 업그레이드 (나가기 아래)
        val lv = GameProgress.treadmillLevel
        upRect.set(w - w * 0.03f - 440f, h * 0.05f + 116f, w - w * 0.03f, h * 0.05f + 206f)
        canvas.drawRoundRect(upRect, 45f, 45f, timeBtnPaint); canvas.drawRoundRect(upRect, 45f, 45f, ink)
        text.textSize = 32f
        canvas.drawText(if (lv >= GameProgress.TREADMILL_MAX) "러닝머신 Lv $lv (최대)" else "러닝머신 Lv $lv → ${lv + 1} · 루비 ${GameProgress.treadmillUpPrice()}",
            upRect.centerX(), upRect.centerY() + 11f, text)
        if (noticeTimer > 0f) {
            text.textSize = 40f
            val nw = text.measureText(noticeText) + 70f
            rect.set(w / 2f - nw / 2f, h * 0.30f, w / 2f + nw / 2f, h * 0.30f + 80f)
            canvas.drawRoundRect(rect, 24f, 24f, card); canvas.drawRoundRect(rect, 24f, 24f, ink)
            canvas.drawText(noticeText, w / 2f, rect.centerY() + 14f, text)
        }
    }

    private fun drawExit(canvas: Canvas, w: Float, h: Float) {
        exitRect.set(w - w * 0.03f - 240f, h * 0.05f, w - w * 0.03f, h * 0.05f + 96f)
        canvas.drawRoundRect(exitRect, 48f, 48f, card); canvas.drawRoundRect(exitRect, 48f, 48f, ink)
        text.textSize = 42f
        canvas.drawText("나가기", exitRect.centerX(), exitRect.centerY() + 15f, text)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val x = event.x; val y = event.y
        if (exitRect.contains(x, y)) { gameView.showShop(); return true }
        if (upRect.contains(x, y)) {
            val lv = GameProgress.treadmillLevel
            noticeText = when {
                lv >= GameProgress.TREADMILL_MAX -> "러닝머신이 이미 최대 Lv 이에요"
                GameProgress.upgradeTreadmill() -> "러닝머신 Lv ${lv + 1}! 30초마다 체력 +${CatTeam.hpTrainGain}"
                else -> "루비가 모자라요 (루비 ${GameProgress.treadmillUpPrice()})"
            }
            noticeTimer = 2f
            return true
        }
        if (timeBtn.contains(x, y)) {
            if (GameProgress.spendRubies(1)) { timeLeft += 60f; noticeText = "1분 늘었어요! (루비 ${GameProgress.rubies}개 남음)" }
            else noticeText = "루비가 모자라요 (1분 = 루비 1)"
            noticeTimer = 1.8f
        }
        return true
    }

    override fun onBack(): Boolean {
        gameView.showShop()
        return true
    }

    companion object {
        /** 훈련장 남은 시간 (초) — 나갔다 와도 남는다 (앱을 끄면 사라짐) */
        private var timeLeft = 0f
    }
}
