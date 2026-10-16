package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CatTeam
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SpriteManager
import kotlin.math.abs
import kotlin.math.sin

/**
 * 고양이 놀이방 — 상점 마을 14번 천막 (대표님 2026-10-10 「고양이 놀이방, 들어가려면 먼저 10루비, 출전시킨 냥이들이 놀고 있어,
 * 맡기기 버튼 — 맡기면 캣타워에서 없어져 거기서 밥을 줘서 포만감이 다 차 있어, 맡기지 않기를 누르면 데려올 수 있어」).
 * 들어올 때 루비 10개는 상점 마을이 받는다. 출전한 냥이·맡긴 냥이가 방 안을 뛰놀고, 냥이마다 아래 버튼:
 * 출전한 냥이 = '맡기기'(캣타워·출전에서 빠지고 늘 배부름), 맡긴 냥이 = '맡기지 않기'(캣타워로 돌아온다).
 * 방 그림(코드)·놀이 움직임은 Claude 판단.
 */
class PlayroomScreen(private val gameView: GameView) : Screen {

    private class Kitty(val id: Int, var x: Float, var dir: Float, var phase: Float, var wait: Float)
    private val kitties = mutableMapOf<Int, Kitty>()
    private var timer = 0f
    private var noticeText = ""
    private var noticeTimer = 0f
    private val buttons = mutableListOf<Pair<Int, RectF>>()
    private val exitRect = RectF()
    private val spriteCache = mutableMapOf<Int, Bitmap>()

    private val wall = Paint().apply { color = Color.rgb(255, 228, 236) }
    private val stripe = Paint().apply { color = Color.rgb(255, 210, 222) }
    private val floor = Paint().apply { color = Color.rgb(196, 226, 170) }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val card = Paint().apply { color = Color.argb(230, 255, 252, 244); isAntiAlias = true }
    private val leaveBtn = Paint().apply { color = Color.rgb(255, 200, 120); isAntiAlias = true }
    private val backBtn = Paint().apply { color = Color.rgb(150, 220, 140); isAntiAlias = true }
    private val nameBg = Paint().apply { color = Color.argb(170, 40, 30, 26); isAntiAlias = true }
    private val nameText = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val text = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val heart = Paint().apply { color = Color.rgb(236, 70, 90); isAntiAlias = true }
    private val rect = RectF()

    /** 방 안의 냥이: 출전한 냥이 + 맡긴 냥이 (떠나거나 무덤인 냥이 빼고) */
    private fun roomCats(): List<CatTeam.TeamCat> = (CatTeam.party + CatTeam.daycare).distinctBy { it.id }

    private fun spriteOf(c: CatTeam.TeamCat): Bitmap = spriteCache.getOrPut(c.id) {
        com.jamcat.nayngapo.entity.AllyCat(0f, 0f, c.kind, level = c.level, teamId = c.id,
            armored = c.hasArmor, attackBonus = c.attackBonus, coat = c.coat).spriteNow()
    }

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime
        val w = gameView.screenWidth.toFloat()
        val cats = roomCats()
        // 새로 들어온 냥이는 자리를 정해 주고, 나간 냥이는 지운다
        for ((i, c) in cats.withIndex()) kitties.getOrPut(c.id) { Kitty(c.id, w * (0.15f + 0.7f * (i + 0.5f) / cats.size.coerceAtLeast(1)), if (i % 2 == 0) 1f else -1f, i * 1.3f, 0f) }
        kitties.keys.retainAll(cats.map { it.id }.toSet())
        // 뛰놀기: 이리저리 뛰다가 멈췄다가, 가끔 폴짝
        for (k in kitties.values) {
            k.phase += deltaTime
            if (k.wait > 0f) { k.wait -= deltaTime; continue }
            k.x += k.dir * 140f * deltaTime
            if (k.x < w * 0.08f || k.x > w * 0.92f) { k.dir = -k.dir; k.x = k.x.coerceIn(w * 0.08f, w * 0.92f) }
            if (Math.random() < deltaTime * 0.5) { k.wait = 0.6f + (Math.random() * 1.4f).toFloat(); if (Math.random() < 0.5) k.dir = -k.dir }
        }
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        val floorY = h * 0.70f
        canvas.drawRect(0f, 0f, w, floorY, wall)
        var sx = 0f
        while (sx < w) { canvas.drawRect(sx, 0f, sx + 60f, floorY, stripe); sx += 120f }
        canvas.drawRect(0f, floorY, w, h, floor)
        canvas.drawLine(0f, floorY, w, floorY, ink)
        // 장난감들 (상점 물건 그림을 방에 놓는다)
        for ((id, fx, size) in TOYS) {
            val bmp = SpriteManager.shopIcons[id] ?: continue
            rect.set(w * fx - size / 2f, floorY + 10f - size, w * fx + size / 2f, floorY + 10f)
            canvas.drawBitmap(bmp, null, rect, null)
        }
        text.textSize = h * 0.065f
        canvas.drawTr("고양이 놀이방", w / 2f, h * 0.10f, text)
        text.textSize = h * 0.03f
        canvas.drawTr("출전한 냥이는 '맡기기' · 맡긴 냥이는 늘 배불러요 · '맡기지 않기'로 데려가요", w / 2f, h * 0.16f, text)

        val cats = roomCats()
        buttons.clear()
        if (cats.isEmpty()) {
            text.textSize = h * 0.045f
            canvas.drawTr("놀고 있는 냥이가 없어요 (캣타워에서 출전시키면 와요)", w / 2f, h * 0.45f, text)
        }
        for (c in cats) {
            val k = kitties[c.id] ?: continue
            val base = spriteOf(c)
            val bmp = if (k.dir > 0f) base else SpriteManager.getFlipped(base)
            val ch = h * 0.16f * (if (c.isKitten) com.jamcat.nayngapo.entity.AllyCat.KITTEN_SCALE else 1f)
            val cw = bmp.width * ch / bmp.height
            val hop = if (k.wait <= 0f) abs(sin(k.phase * 10f)) * h * 0.02f else 0f
            rect.set(k.x - cw / 2f, floorY - ch - hop, k.x + cw / 2f, floorY - hop)
            canvas.drawBitmap(bmp, null, rect, null)
            // 이름 (맡긴 냥이는 하트 = 배부름)
            nameText.textSize = h * 0.028f
            val label = if (c.inDaycare) "♥ ${c.name}" else c.name
            val nw = nameText.measureTr(label) + 24f
            val ny = floorY - ch - h * 0.035f
            rect.set(k.x - nw / 2f, ny - h * 0.035f, k.x + nw / 2f, ny + 6f)
            canvas.drawRoundRect(rect, 16f, 16f, nameBg)
            canvas.drawTr(label, k.x, ny - 4f, nameText)
            // 아래 버튼
            val bw = h * 0.21f; val bh = h * 0.07f
            val br = RectF(k.x - bw / 2f, floorY + h * 0.04f, k.x + bw / 2f, floorY + h * 0.04f + bh)
            canvas.drawRoundRect(br, bh / 2f, bh / 2f, if (c.inDaycare) backBtn else leaveBtn)
            canvas.drawRoundRect(br, bh / 2f, bh / 2f, ink)
            text.textSize = h * 0.028f
            canvas.drawTr(if (c.inDaycare) "맡기지 않기" else "맡기기", br.centerX(), br.centerY() + h * 0.01f, text)
            buttons.add(c.id to br)
        }

        exitRect.set(w - w * 0.03f - 240f, h * 0.04f, w - w * 0.03f, h * 0.04f + 90f)
        canvas.drawRoundRect(exitRect, 45f, 45f, card); canvas.drawRoundRect(exitRect, 45f, 45f, ink)
        text.textSize = 40f
        canvas.drawTr("나가기", exitRect.centerX(), exitRect.centerY() + 14f, text)

        if (noticeTimer > 0f) {
            text.textSize = 40f
            val nw = text.measureTr(noticeText) + 70f
            rect.set(w / 2f - nw / 2f, h * 0.24f, w / 2f + nw / 2f, h * 0.24f + 80f)
            canvas.drawRoundRect(rect, 24f, 24f, card); canvas.drawRoundRect(rect, 24f, 24f, ink)
            canvas.drawTr(noticeText, w / 2f, rect.centerY() + 14f, text)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val x = event.x; val y = event.y
        if (exitRect.contains(x, y)) { gameView.showShop(); return true }
        val hit = buttons.lastOrNull { it.second.contains(x, y) } ?: return true
        val c = CatTeam.cat(hit.first) ?: return true
        if (c.inDaycare) {
            CatTeam.takeFromDaycare(c.id)
            notice("${c.name}을(를) 데려왔어요! (캣타워)")
        } else {
            CatTeam.putInDaycare(c.id)
            notice("${c.name}을(를) 맡겼어요 · 늘 배불러요")
        }
        return true
    }

    private fun notice(t: String) { noticeText = t; noticeTimer = 2f }

    override fun onBack(): Boolean {
        gameView.showShop()
        return true
    }

    companion object {
        /** 들어올 때 내는 루비 */
        const val ENTRY_PRICE = 10
        /** 방에 놓인 장난감 (상점 물건 id, 가로 자리 비율, 크기) */
        private val TOYS = listOf(Triple("cat_tower", 0.07f, 300f), Triple("ball", 0.30f, 90f), Triple("cushion", 0.55f, 130f),
            Triple("scratcher", 0.78f, 140f), Triple("fish_toy", 0.94f, 110f))
    }
}
