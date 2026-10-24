package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.BoatCatalog
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.ui.drawRubyIcon
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * 조선소 (대표님 2026-10-24 「조선소에서는 여러 종류의 배를 팔아」「배가 이미 있어도 들어가서 배를 구경하며 살 수 있어」「조선소에 배는 많아」).
 * 물 위에 떠 있는 배 카드가 옆으로 늘어서고(값 싼 것부터), 안 산 배는 [사기], 가진 배는 [타기]로 낚시터에서 탈 배를 고른다.
 */
class ShipyardScreen(private val gameView: GameView) : Screen {

    private val boats = BoatCatalog.boats.sortedBy { it.price }
    private val cardRects = boats.map { RectF() }
    private val btnRects = boats.map { RectF() }
    private val backRect = RectF()
    private var scrollX = 0f
    private var maxScroll = 0f
    private var downX = 0f
    private var lastX = 0f
    private var dragging = false
    private var timer = 0f
    private var noticeText = ""
    private var noticeTimer = 0f

    private val sky = Paint().apply { color = Color.rgb(200, 230, 250) }
    private val sea = Paint().apply { color = Color.rgb(90, 170, 230) }
    private val wave = Paint().apply { color = Color.rgb(170, 215, 245); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val card = Paint().apply { color = Color.argb(235, 255, 252, 244); isAntiAlias = true }
    private val chosenCard = Paint().apply { color = Color.argb(240, 255, 244, 200); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 4.5f; isAntiAlias = true }
    private val gold = Paint().apply { color = Color.rgb(240, 170, 20); style = Paint.Style.STROKE; strokeWidth = 8f; isAntiAlias = true }
    private val buyPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val ridePaint = Paint().apply { color = Color.rgb(160, 220, 255); isAntiAlias = true }
    private val offPaint = Paint().apply { color = Color.rgb(200, 230, 200); isAntiAlias = true }
    private val title = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val text = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val dim = Paint().apply { color = Color.rgb(120, 112, 100); textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val gem = Paint().apply { isAntiAlias = true }
    private val rect = RectF()

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, sky)
        val waterY = h * 0.56f
        canvas.drawRect(0f, waterY, w, h, sea)
        var x = -((timer * 30f) % 160f)
        var k = 0
        while (x < w) { canvas.drawLine(x, waterY + 40f + (k % 3) * 60f, x + 70f, waterY + 40f + (k % 3) * 60f, wave); x += 160f; k++ }

        title.textSize = h * 0.065f
        canvas.drawTr("조선소", w / 2f, h * 0.085f, title)
        title.textSize = h * 0.042f; title.textAlign = Paint.Align.RIGHT
        val rs = "${GameProgress.rubies}"
        canvas.drawTr(rs, w - w * 0.03f, h * 0.085f, title)
        drawRubyIcon(canvas, w - w * 0.03f - title.measureText(rs) - h * 0.035f, h * 0.07f, h * 0.022f, gem)
        title.textAlign = Paint.Align.CENTER

        val cw = w * 0.22f; val gap = w * 0.02f; val padX = w * 0.03f
        maxScroll = (boats.size * cw + (boats.size - 1) * gap - (w - padX * 2f)).coerceAtLeast(0f)
        scrollX = scrollX.coerceIn(0f, maxScroll)
        val current = BoatCatalog.current
        val boatBmp = SpriteManager.fishingBoat
        for ((i, b) in boats.withIndex()) {
            val l = padX + i * (cw + gap) - scrollX
            val r = cardRects[i]
            r.set(l, h * 0.13f, l + cw, h * 0.86f)
            if (r.right < 0f || r.left > w) { btnRects[i].setEmpty(); continue }
            val owned = BoatCatalog.owns(b.id)
            val riding = current?.id == b.id
            canvas.drawRoundRect(r, 20f, 20f, if (riding) chosenCard else card)
            canvas.drawRoundRect(r, 20f, 20f, if (riding) gold else ink)
            // 배 그림 (출렁출렁)
            val bw = cw * 0.86f; val bh = bw * boatBmp.height / boatBmp.width
            val bob = sin(timer * 1.8f + i) * 6f
            rect.set(r.centerX() - bw / 2f, h * 0.40f - bh / 2f + bob, r.centerX() + bw / 2f, h * 0.40f + bh / 2f + bob)
            canvas.drawBitmap(boatBmp, null, rect, b.paint)
            text.textSize = h * 0.042f
            canvas.drawTr(b.name, r.centerX(), h * 0.20f, text)
            dim.textSize = h * 0.026f
            canvas.drawTr(b.desc, r.centerX(), h * 0.555f, dim)
            text.textSize = h * 0.030f
            canvas.drawTr("♥ ${b.hp}   공격 ${b.attack}", r.centerX(), h * 0.615f, text)
            canvas.drawTr("빠르기 " + "★".repeat(((b.speed - 0.6f) / 0.2f).toInt().coerceIn(1, 5)), r.centerX(), h * 0.66f, text)
            // 사기 / 타기 / 타는 중
            val br = btnRects[i]
            br.set(r.left + cw * 0.10f, h * 0.71f, r.right - cw * 0.10f, h * 0.81f)
            canvas.drawRoundRect(br, 26f, 26f, when { riding -> offPaint; owned -> ridePaint; else -> buyPaint }); canvas.drawRoundRect(br, 26f, 26f, ink)
            text.textSize = h * 0.036f
            canvas.drawTr(when { riding -> "타는 배 ✓"; owned -> "이 배 타기"; else -> "사기 · 루비 ${b.price}" }, br.centerX(), br.centerY() + h * 0.013f, text)
        }

        backRect.set(w * 0.03f, h * 0.885f, w * 0.03f + w * 0.16f, h * 0.885f + h * 0.085f)
        canvas.drawRoundRect(backRect, 22f, 22f, buyPaint); canvas.drawRoundRect(backRect, 22f, 22f, ink)
        text.textSize = h * 0.036f
        canvas.drawTr("◀ 나가기", backRect.centerX(), backRect.centerY() + h * 0.013f, text)
        if (maxScroll > 0f) { dim.textSize = h * 0.028f; canvas.drawTr("◀ 옆으로 밀어서 배 구경 ▶", w * 0.6f, h * 0.935f, dim) }

        if (noticeTimer > 0f) {
            text.textSize = h * 0.040f
            val nw = text.measureText(noticeText) + 60f
            rect.set(w / 2f - nw / 2f, h * 0.45f, w / 2f + nw / 2f, h * 0.53f)
            canvas.drawRoundRect(rect, 20f, 20f, card); canvas.drawRoundRect(rect, 20f, 20f, ink)
            canvas.drawTr(noticeText, w / 2f, rect.centerY() + h * 0.014f, text)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; lastX = event.x; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > 24f) dragging = true
                if (dragging) scrollX = (scrollX - (event.x - lastX)).coerceIn(0f, maxScroll)
                lastX = event.x
            }
            MotionEvent.ACTION_UP -> if (!dragging) tap(event.x, event.y)
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        if (backRect.contains(x, y)) { gameView.showShop(); return }
        val i = btnRects.indexOfFirst { !it.isEmpty && it.contains(x, y) }
        if (i < 0) return
        val b = boats[i]
        when {
            BoatCatalog.current?.id == b.id -> notice("지금 타는 배예요 — 낚시터에서 타요")
            BoatCatalog.owns(b.id) -> { GameProgress.selectBoat(b.id); notice("이제 ${b.name}을(를) 타요!"); SoundManager.playCatHit() }
            BoatCatalog.buy(b) -> { notice("${b.name}을(를) 샀어요! 부두에 매여 있어요"); SoundManager.playCatHit() }
            else -> notice("루비가 모자라요 (${b.name} 루비 ${b.price})")
        }
    }

    private fun notice(t: String) { noticeText = t; noticeTimer = 1.8f }

    override fun onBack(): Boolean { gameView.showShop(); return true }
}
