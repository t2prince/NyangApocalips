package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CustomChars
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.Heroes
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.ui.drawHero
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * 캐릭터 변경 (대표님 2026-10-24, 로비 뽑기 위 작은 칸에서 들어온다):
 * 인간 4명(공짜 — 「인간은 안 사도 돼」) · 고양이 · 내가 만든 캐릭터들 · '추가 +'(사진으로 새 캐릭터) 가 옆으로 늘어서고,
 * 카드를 누르면 [되기]. 카드가 많으면 옆으로 밀어서 본다. 내가 만든 캐릭터는 '지우기'(두 번 눌러 확인).
 * 고양이는 캣타워에 있는 냥이 중 하나를 골라 그 냥이가 된다 (2026-10-24 「고양이가 되려면 먼저 캣타워에 있는 냥이로 돼」).
 */
class CharacterSelectScreen(private val gameView: GameView) : Screen {

    private var timer = 0f
    private var hopT = 0f
    private var hopId: String? = null

    /** 카드 목록: 인간들 + 고양이 + 내가 만든 캐릭터 (마지막에 '추가' 카드가 따로 붙는다) */
    private fun heroes(): List<Heroes.Hero> = Heroes.all + Heroes.cat + CustomChars.all.map { Heroes.byId(it.id) }

    private val cardRects = ArrayList<RectF>()
    private val delRects = HashMap<String, RectF>()
    private val addRect = RectF()
    private val backRect = RectF()
    private var scrollX = 0f
    private var maxScroll = 0f
    private var downX = 0f
    private var dragging = false
    private var lastX = 0f
    private var deleteArm: String? = null
    private var deleteArmT = 0f

    private val bgPaint = Paint().apply { color = Color.rgb(252, 249, 240) }
    private val titlePaint = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val cardPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val highlightPaint = Paint().apply { color = Color.rgb(255, 244, 200); isAntiAlias = true }
    private val edgePaint = Paint().apply { color = Color.rgb(30, 28, 26); style = Paint.Style.STROKE; strokeWidth = 4.5f; isAntiAlias = true }
    private val selectEdgePaint = Paint().apply { color = Color.rgb(240, 170, 20); style = Paint.Style.STROKE; strokeWidth = 8f; isAntiAlias = true }
    private val groundPaint = Paint().apply { color = Color.rgb(196, 189, 176); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val namePaint = Paint().apply { color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val descPaint = Paint().apply { color = Color.rgb(140, 132, 122); textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val tagPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val tagOffPaint = Paint().apply { color = Color.rgb(160, 220, 255); isAntiAlias = true }
    private val delPaint = Paint().apply { color = Color.rgb(240, 120, 120); isAntiAlias = true }
    private val tagTextPaint = Paint().apply { color = Color.rgb(60, 44, 12); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val plusPaint = Paint().apply { color = Color.rgb(150, 140, 128); strokeWidth = 12f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val dashPaint = Paint().apply { color = Color.rgb(150, 140, 128); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true; pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f) }
    private val heroDst = RectF()
    private val tagRect = RectF()

    // ===== 캣타워 냥이 고르기 창 =====
    private var choosing = false
    /** 냥이가 18마리보다 많으면 옆으로 넘겨 본다 (대표님 2026-10-25 「넘기면 자기 팀에 있는 모든 고양이가 될 수 있어」) */
    private var choosePage = 0
    private val prevPageRect = RectF()
    private val nextPageRect = RectF()
    private val chooseRects = ArrayList<Pair<Int, RectF>>()
    private val closeRect = RectF()
    private var noticeText = ""
    private var noticeTimer = 0f
    private val dimPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val bmpPaint = Paint().apply { isFilterBitmap = true }

    override fun update(deltaTime: Float) {
        if (noticeTimer > 0f) noticeTimer -= deltaTime
        timer += deltaTime
        if (hopT > 0f) hopT -= deltaTime
        if (deleteArmT > 0f) { deleteArmT -= deltaTime; if (deleteArmT <= 0f) deleteArm = null }
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        titlePaint.textSize = h * 0.070f
        canvas.drawTr("캐릭터 변경", w / 2f, h * 0.090f, titlePaint)

        val list = heroes()
        val cw = w * 0.19f; val gap = w * 0.015f; val padX = w * 0.03f
        val total = (list.size + 1) * cw + list.size * gap
        maxScroll = (total - (w - padX * 2f)).coerceAtLeast(0f)
        scrollX = scrollX.coerceIn(0f, maxScroll)
        val groundY = h * 0.66f
        val selectedId = Heroes.byId(GameProgress.selectedHero).id
        while (cardRects.size < list.size) cardRects.add(RectF())
        delRects.clear()

        for ((i, hero) in list.withIndex()) {
            val l = padX + i * (cw + gap) - scrollX
            val r = cardRects[i]
            r.set(l, h * 0.14f, l + cw, h * 0.86f)
            if (r.right < 0f || r.left > w) continue
            val chosen = hero.id == selectedId
            canvas.drawRoundRect(r, 20f, 20f, if (chosen) highlightPaint else cardPaint)
            canvas.drawRoundRect(r, 20f, 20f, if (chosen) selectEdgePaint else edgePaint)
            val cx = r.centerX()
            canvas.drawLine(l + cw * 0.10f, groundY, l + cw * 0.90f, groundY, groundPaint)

            val frames = SpriteManager.playerIdle
            val bmp = frames[((timer * 6f).toInt() + i) % frames.size]
            val scale = min(cw * 0.72f / bmp.width, h * 0.40f / bmp.height)
            val bw = bmp.width * scale * hero.scale; val bh = bmp.height * scale * hero.scale
            val hop = if (hopId == hero.id && hopT > 0f) -sin((1f - hopT / HOP_TIME).coerceIn(0f, 1f) * Math.PI.toFloat()) * h * 0.035f else 0f
            heroDst.set(cx - bw / 2f, groundY - bh + hop, cx + bw / 2f, groundY + hop)
            drawHero(canvas, bmp, heroDst, hero, true, timer)

            namePaint.textSize = h * 0.038f
            canvas.drawTr(hero.name, cx, groundY + h * 0.055f, namePaint)
            descPaint.textSize = h * 0.026f
            canvas.drawTr(hero.desc, cx, groundY + h * 0.092f, descPaint)

            // [되기] / 선택됨
            val th = h * 0.06f; val tw = cw * 0.62f
            tagRect.set(cx - tw / 2f, r.top + h * 0.014f, cx + tw / 2f, r.top + h * 0.014f + th)
            canvas.drawRoundRect(tagRect, th / 2f, th / 2f, if (chosen) tagPaint else tagOffPaint)
            canvas.drawRoundRect(tagRect, th / 2f, th / 2f, edgePaint)
            tagTextPaint.textSize = th * 0.50f
            canvas.drawTr(if (chosen) "선택됨" else "되기", cx, tagRect.centerY() + th * 0.18f, tagTextPaint)

            // 내가 만든 캐릭터: 지우기
            if (hero.id.startsWith(Heroes.CUSTOM_PREFIX)) {
                val dr = RectF(cx - tw / 2f, r.bottom - th - h * 0.014f, cx + tw / 2f, r.bottom - h * 0.014f)
                delRects[hero.id] = dr
                canvas.drawRoundRect(dr, th / 2f, th / 2f, delPaint); canvas.drawRoundRect(dr, th / 2f, th / 2f, edgePaint)
                canvas.drawTr(if (deleteArm == hero.id) "한 번 더!" else "지우기", cx, dr.centerY() + th * 0.18f, tagTextPaint)
            }
        }
        // '추가 +' 카드
        val al = padX + list.size * (cw + gap) - scrollX
        addRect.set(al, h * 0.14f, al + cw, h * 0.86f)
        canvas.drawRoundRect(addRect, 20f, 20f, cardPaint); canvas.drawRoundRect(addRect, 20f, 20f, dashPaint)
        val acx = addRect.centerX(); val acy = h * 0.42f
        canvas.drawLine(acx - 50f, acy, acx + 50f, acy, plusPaint); canvas.drawLine(acx, acy - 50f, acx, acy + 50f, plusPaint)
        namePaint.textSize = h * 0.042f
        canvas.drawTr("추가", acx, groundY + h * 0.055f, namePaint)
        descPaint.textSize = h * 0.024f
        canvas.drawTr("사진으로 캐릭터 만들기", acx, groundY + h * 0.092f, descPaint)

        // 돌아가기
        val bw2 = w * 0.17f; val bh2 = h * 0.08f
        backRect.set(w / 2f - bw2 / 2f, h * 0.895f, w / 2f + bw2 / 2f, h * 0.895f + bh2)
        canvas.drawRoundRect(backRect, 20f, 20f, tagPaint); canvas.drawRoundRect(backRect, 20f, 20f, edgePaint)
        tagTextPaint.textSize = bh2 * 0.42f
        canvas.drawTr("돌아가기", backRect.centerX(), backRect.centerY() + bh2 * 0.15f, tagTextPaint)
        if (maxScroll > 0f) {
            descPaint.textSize = h * 0.026f
            canvas.drawTr("◀ 옆으로 밀어서 더 보기 ▶", w * 0.82f, h * 0.94f, descPaint)
        }
        if (choosing) drawChooser(canvas, w, h)
        if (noticeTimer > 0f) {
            namePaint.textSize = h * 0.040f
            val nw = namePaint.measureText(noticeText) + 60f
            tagRect.set(w / 2f - nw / 2f, h * 0.44f, w / 2f + nw / 2f, h * 0.53f)
            canvas.drawRoundRect(tagRect, 20f, 20f, cardPaint); canvas.drawRoundRect(tagRect, 20f, 20f, edgePaint)
            canvas.drawTr(noticeText, w / 2f, tagRect.centerY() + h * 0.014f, namePaint)
        }
    }

    /** 우리 팀 냥이들을 한 줄에 여섯씩, 한 쪽에 18마리 — 누르면 그 냥이가 된다. 옆으로 밀거나 ◀ ▶ 로 넘긴다 */
    private fun drawChooser(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, dimPaint)
        val panel = RectF(w * 0.06f, h * 0.08f, w * 0.94f, h * 0.92f)
        canvas.drawRoundRect(panel, 24f, 24f, cardPaint); canvas.drawRoundRect(panel, 24f, 24f, edgePaint)
        titlePaint.textSize = h * 0.050f
        canvas.drawTr("어느 냥이가 될까요?", w / 2f, panel.top + h * 0.075f, titlePaint)
        chooseRects.clear()
        val cats = com.jamcat.nayngapo.engine.CatTeam.living
        val cols = 6; val cw = (panel.width() - 40f) / cols; val ch = h * 0.22f
        val pages = ((cats.size + PAGE_CATS - 1) / PAGE_CATS).coerceAtLeast(1)
        choosePage = choosePage.coerceIn(0, pages - 1)
        for ((i, c) in cats.drop(choosePage * PAGE_CATS).take(PAGE_CATS).withIndex()) {
            val l = panel.left + 20f + (i % cols) * cw; val t = panel.top + h * 0.11f + (i / cols) * (ch + 10f)
            val r = RectF(l + 6f, t, l + cw - 6f, t + ch)
            chooseRects.add(c.id to r)
            val mine = c.id == GameProgress.playerCatId && GameProgress.selectedHero == Heroes.CAT_ID
            canvas.drawRoundRect(r, 16f, 16f, if (mine) highlightPaint else cardPaint)
            canvas.drawRoundRect(r, 16f, 16f, if (mine) selectEdgePaint else edgePaint)
            val bmp = com.jamcat.nayngapo.ui.CharSkin.teamCatSprite(c)
            val sc = min((r.width() - 20f) / bmp.width, (r.height() * 0.70f) / bmp.height)
            val bw = bmp.width * sc; val bh = bmp.height * sc
            heroDst.set(r.centerX() - bw / 2f, r.top + r.height() * 0.76f - bh, r.centerX() + bw / 2f, r.top + r.height() * 0.76f)
            canvas.drawBitmap(bmp, null, heroDst, bmpPaint)
            namePaint.textSize = h * 0.030f
            canvas.drawTr("${c.name} Lv${c.level}", r.centerX(), r.bottom - h * 0.02f, namePaint)
        }
        val bw2 = w * 0.16f; val bh2 = h * 0.075f
        closeRect.set(w / 2f - bw2 / 2f, panel.bottom - bh2 - 16f, w / 2f + bw2 / 2f, panel.bottom - 16f)
        canvas.drawRoundRect(closeRect, 20f, 20f, tagPaint); canvas.drawRoundRect(closeRect, 20f, 20f, edgePaint)
        tagTextPaint.textSize = bh2 * 0.42f
        canvas.drawTr("닫기", closeRect.centerX(), closeRect.centerY() + bh2 * 0.15f, tagTextPaint)
        prevPageRect.setEmpty(); nextPageRect.setEmpty()
        if (pages > 1) {
            val aw = w * 0.11f
            prevPageRect.set(panel.left + 30f, closeRect.top, panel.left + 30f + aw, closeRect.bottom)
            nextPageRect.set(panel.right - 30f - aw, closeRect.top, panel.right - 30f, closeRect.bottom)
            for ((r, label, on) in listOf(Triple(prevPageRect, "◀", choosePage > 0), Triple(nextPageRect, "▶", choosePage < pages - 1))) {
                canvas.drawRoundRect(r, 20f, 20f, if (on) tagPaint else tagOffPaint); canvas.drawRoundRect(r, 20f, 20f, edgePaint)
                canvas.drawTr(label, r.centerX(), r.centerY() + bh2 * 0.15f, tagTextPaint)
            }
            namePaint.textSize = h * 0.032f
            canvas.drawTr("${choosePage + 1} / $pages", w / 2f, closeRect.top - h * 0.02f, namePaint)
        }
    }

    private fun turnPage(d: Int) {
        val pages = ((com.jamcat.nayngapo.engine.CatTeam.living.size + PAGE_CATS - 1) / PAGE_CATS).coerceAtLeast(1)
        val np = (choosePage + d).coerceIn(0, pages - 1)
        if (np != choosePage) { choosePage = np; SoundManager.playCatHit() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; lastX = event.x; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > 24f) dragging = true
                if (dragging && !choosing) { scrollX = (scrollX - (event.x - lastX)).coerceIn(0f, maxScroll) }
                lastX = event.x
            }
            MotionEvent.ACTION_UP -> when {
                !dragging -> tap(event.x, event.y)
                choosing -> turnPage(if (event.x < downX) 1 else -1)   // 냥이 고르기 창: 밀면 다음/앞 쪽
            }
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        if (choosing) {
            if (closeRect.contains(x, y)) { choosing = false; return }
            if (prevPageRect.contains(x, y)) { turnPage(-1); return }
            if (nextPageRect.contains(x, y)) { turnPage(1); return }
            val hit = chooseRects.firstOrNull { it.second.contains(x, y) } ?: return
            GameProgress.setPlayerCat(hit.first)
            GameProgress.selectHero(Heroes.CAT_ID)
            SoundManager.playCatHit()
            hopId = Heroes.CAT_ID; hopT = HOP_TIME
            choosing = false
            return
        }
        if (backRect.contains(x, y)) { gameView.showLobby(); return }
        if (addRect.contains(x, y)) { gameView.showCharacterCreate(); return }
        for ((id, r) in delRects) {
            if (!r.contains(x, y)) continue
            if (deleteArm == id) {
                CustomChars.remove(id); com.jamcat.nayngapo.ui.CharSkin.forget(id)
                if (GameProgress.selectedHero == id) GameProgress.selectHero(Heroes.DEFAULT_ID)
                deleteArm = null
            } else { deleteArm = id; deleteArmT = 3f }
            return
        }
        val list = heroes()
        for ((i, hero) in list.withIndex()) {
            if (i >= cardRects.size || !cardRects[i].contains(x, y)) continue
            // 고양이: 캣타워 냥이 중에서 고른다
            if (hero.id == Heroes.CAT_ID) {
                if (com.jamcat.nayngapo.engine.CatTeam.living.isEmpty()) { noticeText = "캣타워에 냥이가 없어요 — 먼저 냥이를 구해요"; noticeTimer = 2f }
                else { choosing = true; choosePage = 0 }
                return
            }
            if (hero.id != GameProgress.selectedHero) {
                GameProgress.selectHero(hero.id)
                SoundManager.playCatHit()
                hopId = hero.id; hopT = HOP_TIME
            }
            return
        }
    }

    override fun onBack(): Boolean {
        if (choosing) { choosing = false; return true }
        gameView.showLobby()
        return true
    }

    private companion object {
        const val HOP_TIME = 0.4f
        const val PAGE_CATS = 18
    }
}
