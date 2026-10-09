package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CarCatalog
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.ui.drawRubyIcon

/**
 * 자동차 매장 — 상점 마을 맨 끝 (대표님 2026-10-09).
 * 차를 루비로 사고, **자동차 선택**: 가진 차 중 하나를 골라 두면 땅 스테이지에서 그 차를 타고 다닌다. '안 타기'로 걸어 다닌다.
 * 화면 모양(카드 3장·버튼)은 Claude 판단.
 */
class CarShopScreen(private val gameView: GameView) : Screen {

    private val cards = CarCatalog.cars.map { RectF() }
    private val buttons = CarCatalog.cars.map { RectF() }
    private val noneRect = RectF()
    private val backRect = RectF()
    private var noticeText = ""
    private var noticeTimer = 0f

    private val bg = Paint().apply { color = Color.rgb(236, 244, 252) }
    private val cardPaint = Paint().apply { color = Color.rgb(255, 252, 244); isAntiAlias = true }
    private val chosenPaint = Paint().apply { color = Color.rgb(255, 232, 160); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val btnBuy = Paint().apply { color = Color.rgb(255, 196, 90); isAntiAlias = true }
    private val btnRide = Paint().apply { color = Color.rgb(150, 220, 140); isAntiAlias = true }
    private val gemPaint = Paint().apply { isAntiAlias = true }
    private val title = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val text = Paint().apply { color = Color.rgb(50, 42, 38); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val rect = RectF()

    override fun update(deltaTime: Float) {
        if (noticeTimer > 0f) noticeTimer -= deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        canvas.drawRect(0f, 0f, w, h, bg)
        title.textSize = h * 0.065f
        canvas.drawText("자동차 매장 · 자동차 선택", w / 2f, h * 0.11f, title)
        text.textSize = h * 0.032f
        canvas.drawText("고른 차를 땅 스테이지에서 타고 다녀요 (바다·하늘은 빼고)", w / 2f, h * 0.17f, text)

        // 루비
        drawRubyIcon(canvas, w * 0.06f, h * 0.09f, h * 0.025f, gemPaint)
        text.textAlign = Paint.Align.LEFT; text.textSize = h * 0.04f
        canvas.drawText("${GameProgress.rubies}", w * 0.06f + h * 0.04f, h * 0.105f, text)
        text.textAlign = Paint.Align.CENTER

        val n = CarCatalog.cars.size
        val cw = w * 0.26f; val gap = (w - cw * n) / (n + 1)
        val top = h * 0.22f; val bottom = h * 0.80f
        val selected = CarCatalog.selected
        for ((i, car) in CarCatalog.cars.withIndex()) {
            val r = cards[i]
            r.set(gap + i * (cw + gap), top, gap + i * (cw + gap) + cw, bottom)
            canvas.drawRoundRect(r, 26f, 26f, if (selected?.id == car.id) chosenPaint else cardPaint)
            canvas.drawRoundRect(r, 26f, 26f, ink)
            SpriteManager.cars[car.id]?.let { bmp ->
                val s = minOf(r.width() * 0.8f / bmp.width, r.height() * 0.38f / bmp.height)
                rect.set(r.centerX() - bmp.width * s / 2f, r.top + r.height() * 0.08f, r.centerX() + bmp.width * s / 2f, r.top + r.height() * 0.08f + bmp.height * s)
                canvas.drawBitmap(bmp, null, rect, null)
            }
            text.textSize = h * 0.042f
            canvas.drawText(car.name, r.centerX(), r.top + r.height() * 0.58f, text)
            text.textSize = h * 0.032f
            canvas.drawText("빠르기 ×${car.speedMul}", r.centerX(), r.top + r.height() * 0.67f, text)
            // 아래 버튼: 사기(루비) / 타기 / 타는 중
            val b = buttons[i]
            b.set(r.left + 24f, r.bottom - r.height() * 0.22f, r.right - 24f, r.bottom - 24f)
            val owned = CarCatalog.owned(car.id)
            canvas.drawRoundRect(b, 40f, 40f, if (owned) btnRide else btnBuy)
            canvas.drawRoundRect(b, 40f, 40f, ink)
            text.textSize = h * 0.036f
            val label = when {
                !owned -> "사기  루비 ${car.price}"
                selected?.id == car.id -> "타는 중 ✓"
                else -> "이 차 타기"
            }
            canvas.drawText(label, b.centerX(), b.centerY() + h * 0.012f, text)
        }

        // 안 타기 · 나가기
        noneRect.set(w / 2f - 380f, h * 0.85f, w / 2f - 20f, h * 0.85f + 90f)
        backRect.set(w / 2f + 20f, h * 0.85f, w / 2f + 380f, h * 0.85f + 90f)
        for ((rr, label) in listOf(noneRect to (if (selected == null) "걸어 다니는 중 ✓" else "안 타기 (걸어 다니기)"), backRect to "나가기")) {
            canvas.drawRoundRect(rr, 45f, 45f, cardPaint); canvas.drawRoundRect(rr, 45f, 45f, ink)
            text.textSize = h * 0.034f
            canvas.drawText(label, rr.centerX(), rr.centerY() + h * 0.012f, text)
        }

        if (noticeTimer > 0f) {
            text.textSize = 42f
            val nw = text.measureText(noticeText) + 70f
            rect.set(w / 2f - nw / 2f, h * 0.40f, w / 2f + nw / 2f, h * 0.40f + 80f)
            canvas.drawRoundRect(rect, 24f, 24f, chosenPaint); canvas.drawRoundRect(rect, 24f, 24f, ink)
            canvas.drawText(noticeText, w / 2f, rect.centerY() + 15f, text)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val x = event.x; val y = event.y
        if (backRect.contains(x, y)) { gameView.showShop(); return true }
        if (noneRect.contains(x, y)) { GameProgress.selectCar(""); notice("이제 걸어 다녀요"); return true }
        val i = buttons.indexOfFirst { it.contains(x, y) }
        if (i < 0) return true
        val car = CarCatalog.cars[i]
        if (!CarCatalog.owned(car.id)) {
            if (GameProgress.buyItem(car.id, car.price)) {
                GameProgress.selectCar(car.id)   // 사면 바로 그 차를 탄다
                SoundManager.playCatHit()
                notice("${car.name} 구매! 스테이지에서 타고 다녀요")
            } else notice("루비가 모자라요 (루비 ${car.price})")
        } else {
            GameProgress.selectCar(car.id)
            notice("${car.name}을(를) 타요!")
        }
        return true
    }

    private fun notice(t: String) { noticeText = t; noticeTimer = 1.8f }

    override fun onBack(): Boolean {
        gameView.showShop()
        return true
    }
}
