package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.RobotCatalog
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.ui.drawRubyIcon

/**
 * 로봇 상점 — 상점 마을 11번 천막 (대표님 2026-10-09).
 * 로봇을 사고(Lv1 500루비) Lv 을 올린다(Lv 마다 피·공격·값 두 배). '로봇 타기'를 켜 두면 땅 스테이지에서 로봇을 타고 시작한다.
 * 화면 모양은 Claude 판단.
 */
class RobotShopScreen(private val gameView: GameView) : Screen {

    private val buyRect = RectF()
    private val rideRect = RectF()
    private val backRect = RectF()
    private var noticeText = ""
    private var noticeTimer = 0f

    private val bg = Paint().apply { color = Color.rgb(232, 236, 244) }
    private val card = Paint().apply { color = Color.rgb(255, 252, 244); isAntiAlias = true }
    private val yellow = Paint().apply { color = Color.rgb(255, 214, 120); isAntiAlias = true }
    private val green = Paint().apply { color = Color.rgb(150, 220, 140); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val gemPaint = Paint().apply { isAntiAlias = true }
    private val text = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val rect = RectF()

    override fun update(deltaTime: Float) {
        if (noticeTimer > 0f) noticeTimer -= deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        canvas.drawRect(0f, 0f, w, h, bg)
        text.textSize = h * 0.065f
        canvas.drawTr("로봇 상점", w / 2f, h * 0.11f, text)
        drawRubyIcon(canvas, w * 0.06f, h * 0.09f, h * 0.025f, gemPaint)
        text.textAlign = Paint.Align.LEFT; text.textSize = h * 0.04f
        canvas.drawTr("${GameProgress.rubies}", w * 0.06f + h * 0.04f, h * 0.105f, text)
        text.textAlign = Paint.Align.CENTER

        // 왼쪽: 로봇 그림
        rect.set(w * 0.08f, h * 0.18f, w * 0.46f, h * 0.82f)
        canvas.drawRoundRect(rect, 28f, 28f, card); canvas.drawRoundRect(rect, 28f, 28f, ink)
        SpriteManager.robotSide?.let { bmp ->
            val s = minOf(rect.width() * 0.8f / bmp.width, rect.height() * 0.8f / bmp.height)
            val bw = bmp.width * s; val bh = bmp.height * s
            val dst = RectF(rect.centerX() - bw / 2f, rect.centerY() - bh / 2f, rect.centerX() + bw / 2f, rect.centerY() + bh / 2f)
            canvas.drawBitmap(bmp, null, dst, null)
        }

        // 오른쪽: 지금 Lv · 다음 Lv · 버튼
        val lv = GameProgress.robotLevel
        val x = w * 0.72f
        text.textSize = h * 0.055f
        canvas.drawTr(if (lv == 0) "아직 로봇이 없어요" else "내 로봇 Lv $lv", x, h * 0.26f, text)
        text.textSize = h * 0.04f
        if (lv > 0) canvas.drawTr("피 ${RobotCatalog.hpOf(lv)} · 공격 ${RobotCatalog.attackOf(lv)}", x, h * 0.34f, text)
        if (lv < RobotCatalog.MAX_LEVEL) {
            text.textSize = h * 0.034f
            canvas.drawTr("Lv ${lv + 1}: 피 ${RobotCatalog.hpOf(lv + 1)} · 공격 ${RobotCatalog.attackOf(lv + 1)}", x, h * 0.42f, text)
        }
        buyRect.set(x - w * 0.2f, h * 0.48f, x + w * 0.2f, h * 0.48f + 100f)
        canvas.drawRoundRect(buyRect, 50f, 50f, yellow); canvas.drawRoundRect(buyRect, 50f, 50f, ink)
        text.textSize = h * 0.04f
        val buyLabel = when {
            lv >= RobotCatalog.MAX_LEVEL -> "최대 Lv"
            lv == 0 -> "로봇 사기 · 루비 ${RobotCatalog.priceOf(1)}"
            else -> "Lv ${lv + 1}로 올리기 · 루비 ${RobotCatalog.priceOf(lv + 1)}"
        }
        canvas.drawTr(buyLabel, buyRect.centerX(), buyRect.centerY() + h * 0.014f, text)
        rideRect.setEmpty()
        if (lv > 0) {
            rideRect.set(x - w * 0.2f, h * 0.62f, x + w * 0.2f, h * 0.62f + 100f)
            canvas.drawRoundRect(rideRect, 50f, 50f, if (GameProgress.rideRobot) green else card); canvas.drawRoundRect(rideRect, 50f, 50f, ink)
            canvas.drawTr(if (GameProgress.rideRobot) "로봇 타기 ✓ (스테이지)" else "로봇 안 타기", rideRect.centerX(), rideRect.centerY() + h * 0.014f, text)
        }
        backRect.set(w / 2f - 180f, h * 0.86f, w / 2f + 180f, h * 0.86f + 90f)
        canvas.drawRoundRect(backRect, 45f, 45f, card); canvas.drawRoundRect(backRect, 45f, 45f, ink)
        canvas.drawTr("나가기", backRect.centerX(), backRect.centerY() + h * 0.014f, text)

        if (noticeTimer > 0f) {
            text.textSize = 42f
            val nw = text.measureTr(noticeText) + 70f
            rect.set(w / 2f - nw / 2f, h * 0.40f, w / 2f + nw / 2f, h * 0.40f + 80f)
            canvas.drawRoundRect(rect, 24f, 24f, yellow); canvas.drawRoundRect(rect, 24f, 24f, ink)
            canvas.drawTr(noticeText, w / 2f, rect.centerY() + 15f, text)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val x = event.x; val y = event.y
        when {
            backRect.contains(x, y) -> gameView.showShop()
            buyRect.contains(x, y) -> {
                val lv = GameProgress.robotLevel
                when {
                    lv >= RobotCatalog.MAX_LEVEL -> notice("이미 최대 Lv 이에요")
                    GameProgress.upgradeRobot() -> {
                        SoundManager.playCatHit()
                        notice(if (lv == 0) "로봇을 샀어요! 스테이지에서 타요" else "로봇 Lv ${lv + 1}! 두 배로 세졌다")
                    }
                    else -> notice("루비가 모자라요 (루비 ${RobotCatalog.priceOf(lv + 1)})")
                }
            }
            !rideRect.isEmpty && rideRect.contains(x, y) -> {
                GameProgress.setRideRobot(!GameProgress.rideRobot)
                notice(if (GameProgress.rideRobot) "스테이지에서 로봇을 타요" else "로봇을 안 타요")
            }
        }
        return true
    }

    private fun notice(t: String) { noticeText = t; noticeTimer = 1.8f }

    override fun onBack(): Boolean {
        gameView.showShop()
        return true
    }
}
