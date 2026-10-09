package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.BattleLoadout
import com.jamcat.nayngapo.engine.BattleLoadout.Ride
import com.jamcat.nayngapo.engine.CarCatalog
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.RobotCatalog
import com.jamcat.nayngapo.engine.SpriteManager

/**
 * 출전 준비 — 땅 스테이지에 들어가기 전에 뜬다 (대표님 2026-10-09 「전투 시 어떤 화면이 뜨는데, 전투 때 쓸 아이템을 고를 수 있어」).
 * 가져갈 것: 생명 물약 · 하트 물약(고르면 시작할 때 하나씩 마신다) · 레이저 총, 그리고 탈 것(걷기 · 자동차 · 로봇).
 * 없는 물건은 흐리게 '없음'. 바다(31)는 배냥이를 타고 목숨 1 규칙이라 탈 것·생명 물약을 못 고른다.
 * '출발!'로 들어가고, 뒤로가기는 온 곳(로비/상점 마을)으로. 고를 수 있는 것·화면 모양은 Claude 판단.
 */
class BattlePrepScreen(private val gameView: GameView, private val stage: Int) : Screen {

    private val isSea = stage == 31

    private class Item(val id: String, val name: String, val desc: String)
    private val items = listOf(
        Item("h_life", "생명 물약", "목숨 +1"),
        Item("h_heal", "하트 물약", "하트 +2"),
        Item("h_gun", "레이저 총", "총알을 쏜다"),
    )
    private val itemRects = items.map { RectF() }
    private val rideRects = Ride.values().map { RectF() }
    private val goRect = RectF()
    private val backRect = RectF()
    private var noticeText = ""
    private var noticeTimer = 0f

    private val bg = Paint().apply { color = Color.rgb(36, 40, 56) }
    private val card = Paint().apply { color = Color.rgb(255, 252, 244); isAntiAlias = true }
    private val chosen = Paint().apply { color = Color.rgb(255, 224, 130); isAntiAlias = true }
    private val off = Paint().apply { color = Color.rgb(170, 170, 176); isAntiAlias = true }
    private val goPaint = Paint().apply { color = Color.rgb(240, 90, 60); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val title = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val text = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val dim = Paint().apply { alpha = 90; isFilterBitmap = true }
    private val rect = RectF()

    private fun owned(id: String) = GameProgress.itemCount(id) > 0
    private fun selected(id: String) = when (id) {
        "h_life" -> BattleLoadout.lifePotion
        "h_heal" -> BattleLoadout.heartPotion
        else -> BattleLoadout.gun
    }
    private fun usable(id: String) = owned(id) && !(isSea && id == "h_life")

    override fun update(deltaTime: Float) {
        if (noticeTimer > 0f) noticeTimer -= deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        canvas.drawRect(0f, 0f, w, h, bg)
        title.textSize = h * 0.07f
        canvas.drawText("STAGE $stage · 출전 준비", w / 2f, h * 0.11f, title)
        title.textSize = h * 0.032f
        canvas.drawText("전투 때 쓸 아이템을 고르세요", w / 2f, h * 0.17f, title)

        // 아이템 카드 세 장
        val cw = w * 0.22f; val gap = (w - cw * items.size) / (items.size + 1)
        val top = h * 0.22f; val bottom = h * 0.56f
        for ((i, it) in items.withIndex()) {
            val r = itemRects[i]
            r.set(gap + i * (cw + gap), top, gap + i * (cw + gap) + cw, bottom)
            val can = usable(it.id)
            val on = can && selected(it.id)
            canvas.drawRoundRect(r, 24f, 24f, if (!can) off else if (on) chosen else card)
            canvas.drawRoundRect(r, 24f, 24f, ink)
            SpriteManager.shopIcons[it.id]?.let { bmp ->
                val s = minOf(r.width() * 0.45f / bmp.width, r.height() * 0.42f / bmp.height)
                rect.set(r.centerX() - bmp.width * s / 2f, r.top + 16f, r.centerX() + bmp.width * s / 2f, r.top + 16f + bmp.height * s)
                canvas.drawBitmap(bmp, null, rect, if (can) null else dim)
            }
            text.textSize = h * 0.036f
            canvas.drawText(it.name, r.centerX(), r.top + r.height() * 0.64f, text)
            text.textSize = h * 0.027f
            val sub = when {
                isSea && it.id == "h_life" -> "바다에서는 못 써요"
                !owned(it.id) -> "없음 · 상점에서 사요"
                it.id == "h_gun" -> if (on) "들고 간다 ✓" else "안 가져간다"
                else -> "${it.desc} · ${GameProgress.itemCount(it.id)}개" + if (on) " ✓" else ""
            }
            canvas.drawText(sub, r.centerX(), r.top + r.height() * 0.80f, text)
            canvas.drawText(if (!can) "" else if (on) "가져가기" else "안 가져가기", r.centerX(), r.top + r.height() * 0.92f, text)
        }

        // 탈 것
        title.textSize = h * 0.035f
        canvas.drawText(if (isSea) "탈 것: 바다에서는 배냥이를 타요" else "탈 것", w / 2f, h * 0.63f, title)
        val now = BattleLoadout.rideNow()
        val rw = w * 0.2f; val rg = (w - rw * 3) / 4f
        for ((k, ride) in Ride.values().withIndex()) {
            val r = rideRects[k]
            r.set(rg + k * (rw + rg), h * 0.66f, rg + k * (rw + rg) + rw, h * 0.66f + 90f)
            val can = !isSea && when (ride) {
                Ride.WALK -> true
                Ride.CAR -> CarCatalog.selected != null
                Ride.ROBOT -> RobotCatalog.owned
            }
            canvas.drawRoundRect(r, 45f, 45f, if (!can) off else if (now == ride) chosen else card)
            canvas.drawRoundRect(r, 45f, 45f, ink)
            text.textSize = h * 0.032f
            val label = when (ride) {
                Ride.WALK -> "🚶 걷기"
                Ride.CAR -> CarCatalog.selected?.let { "🚗 ${it.name}" } ?: "🚗 차 없음"
                Ride.ROBOT -> if (RobotCatalog.owned) "🤖 로봇 Lv ${GameProgress.robotLevel}" else "🤖 로봇 없음"
            }
            canvas.drawText(label + if (can && now == ride) " ✓" else "", r.centerX(), r.centerY() + h * 0.012f, text)
        }

        // 출발 · 뒤로
        goRect.set(w / 2f - 260f, h * 0.82f, w / 2f + 260f, h * 0.82f + 110f)
        canvas.drawRoundRect(goRect, 55f, 55f, goPaint); canvas.drawRoundRect(goRect, 55f, 55f, ink)
        title.textSize = h * 0.055f
        canvas.drawText("출발!", goRect.centerX(), goRect.centerY() + h * 0.02f, title)
        backRect.set(w * 0.04f, h * 0.84f, w * 0.04f + 240f, h * 0.84f + 90f)
        canvas.drawRoundRect(backRect, 45f, 45f, card); canvas.drawRoundRect(backRect, 45f, 45f, ink)
        text.textSize = h * 0.032f
        canvas.drawText("◀ 돌아가기", backRect.centerX(), backRect.centerY() + h * 0.012f, text)

        if (noticeTimer > 0f) {
            text.textSize = 40f
            val nw = text.measureText(noticeText) + 70f
            rect.set(w / 2f - nw / 2f, h * 0.44f, w / 2f + nw / 2f, h * 0.44f + 80f)
            canvas.drawRoundRect(rect, 24f, 24f, chosen); canvas.drawRoundRect(rect, 24f, 24f, ink)
            canvas.drawText(noticeText, w / 2f, rect.centerY() + 14f, text)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val x = event.x; val y = event.y
        if (goRect.contains(x, y)) { gameView.launchStage(stage); return true }
        if (backRect.contains(x, y)) { gameView.leaveStage(); return true }
        val i = itemRects.indexOfFirst { it.contains(x, y) }
        if (i >= 0) {
            val id = items[i].id
            when {
                isSea && id == "h_life" -> notice("바다는 목숨 1 — 생명 물약을 못 써요")
                !owned(id) -> notice("${items[i].name}이(가) 없어요 · 상점에서 사요")
                id == "h_life" -> BattleLoadout.lifePotion = !BattleLoadout.lifePotion
                id == "h_heal" -> BattleLoadout.heartPotion = !BattleLoadout.heartPotion
                else -> BattleLoadout.gun = !BattleLoadout.gun
            }
            return true
        }
        val k = rideRects.indexOfFirst { it.contains(x, y) }
        if (k >= 0 && !isSea) {
            val ride = Ride.values()[k]
            when {
                ride == Ride.CAR && CarCatalog.selected == null -> notice("자동차가 없어요 · 자동차 매장에서 사요")
                ride == Ride.ROBOT && !RobotCatalog.owned -> notice("로봇이 없어요 · 로봇 상점에서 사요")
                else -> BattleLoadout.ride = ride
            }
        }
        return true
    }

    private fun notice(t: String) { noticeText = t; noticeTimer = 1.8f }

    /** 뒤로가기 = 온 곳으로 (스테이지 깨기에서 왔으면 상점 마을) */
    override fun onBack(): Boolean {
        gameView.leaveStage()
        return true
    }
}
