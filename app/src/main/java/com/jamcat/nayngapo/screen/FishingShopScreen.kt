package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.ui.drawRubyIcon

/**
 * 낚시 상점 — 상점 마을 낚시터 옆 (대표님 2026-10-17 「낚시 상점도 만들어 줘, 낚싯대나 미끼 등을 팔아」).
 * 낚싯대 Lv업(Lv = 한 번에 잡는 마릿수) · 미끼 세 가지. 미끼는 낚시 한 번에 하나씩, 가진 것 중 좋은 것부터 저절로 쓴다.
 * 미끼 값·효과는 Claude 판단: 지렁이 2(빨리 문다·물고기가 바늘로 모인다) · 새우 6(큰 물고기가 잘 잡힌다) · 황금 20(귀한 것이 잘 잡힌다).
 */
class FishingShopScreen(private val gameView: GameView) : Screen {

    private class Row(val id: String, val name: String, val desc: String, val price: Int)
    private val baits = listOf(
        Row("bait_worm", "지렁이 미끼", "빨리 문다 · 물고기가 바늘로 모인다", 2),
        Row("bait_shrimp", "새우 미끼", "큰 물고기가 잘 잡힌다", 6),
        Row("bait_gold", "황금 미끼", "황금 물고기·고래·상어가 잘 잡힌다", 20),
    )
    private val rowRects = List(baits.size + 1) { RectF() }
    private val btnRects = List(baits.size + 1) { RectF() }
    private val backRect = RectF()
    private var noticeText = ""
    private var noticeTimer = 0f

    private val bg = Paint().apply { color = Color.rgb(220, 238, 250) }
    private val card = Paint().apply { color = Color.rgb(255, 252, 244); isAntiAlias = true }
    private val btn = Paint().apply { color = Color.rgb(255, 196, 90); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val gem = Paint().apply { isAntiAlias = true }
    private val text = Paint().apply { color = Color.rgb(40, 36, 32); typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val rect = RectF()

    override fun update(deltaTime: Float) { if (noticeTimer > 0f) noticeTimer -= deltaTime }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat(); val h = gameView.screenHeight.toFloat()
        canvas.drawRect(0f, 0f, w, h, bg)
        text.textAlign = Paint.Align.CENTER; text.textSize = h * 0.065f
        canvas.drawText("낚시 상점", w / 2f, h * 0.10f, text)
        drawRubyIcon(canvas, w * 0.06f, h * 0.09f, h * 0.025f, gem)
        text.textAlign = Paint.Align.LEFT; text.textSize = h * 0.04f
        canvas.drawText("${GameProgress.rubies}", w * 0.06f + h * 0.04f, h * 0.105f, text)

        val top = h * 0.17f; val rowH = h * 0.155f
        for (i in 0..baits.size) {
            val r = rowRects[i]
            r.set(w * 0.08f, top + i * (rowH + 12f), w * 0.92f, top + i * (rowH + 12f) + rowH)
            canvas.drawRoundRect(r, 24f, 24f, card); canvas.drawRoundRect(r, 24f, 24f, ink)
            val id = if (i == 0) "fish_rod" else baits[i - 1].id
            SpriteManager.shopIcons[id]?.let { bmp ->
                val s = r.height() * 0.8f
                rect.set(r.left + 20f, r.centerY() - s / 2f, r.left + 20f + s, r.centerY() + s / 2f)
                canvas.drawBitmap(bmp, null, rect, null)
            }
            val tx = r.left + r.height() + 40f
            text.textAlign = Paint.Align.LEFT
            text.textSize = h * 0.042f
            val lv = GameProgress.fishingLineLevel
            canvas.drawText(if (i == 0) "낚싯대 Lv $lv (한 번에 ${lv}마리)" else "${baits[i - 1].name}  (가진 수 ${GameProgress.itemCount(id)})", tx, r.top + r.height() * 0.42f, text)
            text.textSize = h * 0.03f
            canvas.drawText(if (i == 0) "Lv업 하면 한 번에 잡는 물고기가 한 마리 늘어난다" else baits[i - 1].desc, tx, r.top + r.height() * 0.75f, text)
            val b = btnRects[i]
            b.set(r.right - 380f, r.centerY() - 50f, r.right - 24f, r.centerY() + 50f)
            canvas.drawRoundRect(b, 50f, 50f, btn); canvas.drawRoundRect(b, 50f, 50f, ink)
            text.textAlign = Paint.Align.CENTER; text.textSize = h * 0.034f
            val label = when {
                i == 0 && lv >= GameProgress.LINE_LEVEL_MAX -> "최대 Lv"
                i == 0 -> "Lv업 · 루비 ${GameProgress.lineLevelUpPrice()}"
                else -> "사기 · 루비 ${baits[i - 1].price}"
            }
            canvas.drawText(label, b.centerX(), b.centerY() + h * 0.012f, text)
        }
        backRect.set(w / 2f - 180f, h * 0.88f, w / 2f + 180f, h * 0.88f + 90f)
        canvas.drawRoundRect(backRect, 45f, 45f, card); canvas.drawRoundRect(backRect, 45f, 45f, ink)
        text.textSize = h * 0.035f
        canvas.drawText("나가기", backRect.centerX(), backRect.centerY() + h * 0.012f, text)
        if (noticeTimer > 0f) {
            text.textSize = 40f
            val nw = text.measureText(noticeText) + 70f
            rect.set(w / 2f - nw / 2f, h * 0.44f, w / 2f + nw / 2f, h * 0.44f + 80f)
            canvas.drawRoundRect(rect, 24f, 24f, btn); canvas.drawRoundRect(rect, 24f, 24f, ink)
            canvas.drawText(noticeText, w / 2f, rect.centerY() + 14f, text)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val x = event.x; val y = event.y
        if (backRect.contains(x, y)) { gameView.showShop(); return true }
        val i = btnRects.indexOfFirst { it.contains(x, y) }
        if (i == 0) {
            val lv = GameProgress.fishingLineLevel
            notice(when {
                lv >= GameProgress.LINE_LEVEL_MAX -> "낚싯대가 이미 최대 Lv 이에요"
                GameProgress.levelUpFishingLine() -> { SoundManager.playCatHit(); "낚싯대 Lv ${GameProgress.fishingLineLevel}! 한 번에 ${GameProgress.fishingLineLevel}마리" }
                else -> "루비가 모자라요 (루비 ${GameProgress.lineLevelUpPrice()})"
            })
        } else if (i > 0) {
            val b = baits[i - 1]
            if (GameProgress.buyItem(b.id, b.price)) { SoundManager.playCatHit(); notice("${b.name} 샀어요!") }
            else notice("루비가 모자라요 (루비 ${b.price})")
        }
        return true
    }

    private fun notice(t: String) { noticeText = t; noticeTimer = 1.8f }

    override fun onBack(): Boolean { gameView.showShop(); return true }
}
