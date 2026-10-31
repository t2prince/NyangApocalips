package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.engine.StageEdits
import com.jamcat.nayngapo.engine.Terrain
import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import com.jamcat.nayngapo.entity.*
import com.jamcat.nayngapo.level.Level
import com.jamcat.nayngapo.level.PlaceGroup
import com.jamcat.nayngapo.level.PlaceType
import com.jamcat.nayngapo.level.Placement
import com.jamcat.nayngapo.level.SHIP_DRAW_H
import kotlin.math.abs

/**
 * 스테이지 편집 (대표님 2026-10-31 「현재 있는 스테이지들 편집할 수 있는 화면 만들어줘. 스테이지를 고르면 그 맵이 나오고
 * 배치할 수 있는 함정들 고양이들 리스트로 나와서 드래그로 배치할 수 있어야 해」). 디버그 빌드에서만 열린다 (「디버그 모드에서만야」).
 *
 *  1) 스테이지 고르기: 땅 스테이지(1~31, 51~60) 버튼. 11 은 하늘 스테이지(다른 화면)라 뺐다 — Claude 판단. 편집해 저장한 스테이지는 ✏️.
 *  2) 지도: 그 스테이지의 땅(Terrain)과 놓인 것들을 진짜 그림으로 그린다. 빈 곳이나 아래 막대를 밀면 옆으로 움직인다.
 *  3) 아래 목록(고양이 / 함정·물건 탭)에서 위로 끌어 지도에 놓는다. 놓인 것을 끌면 옮겨지고, 목록이나 🗑 위에 놓으면 지워진다.
 *     높이는 늘 땅 위로 맞추고 x 만 자유롭다.
 *  4) 저장 · 처음대로(두 번 눌러 확인) · 해 보기(저장하고 그 스테이지 출발) · 나가기.
 * 저장은 게임 세이브와 따로인 "stage_edits" (engine/StageEdits.kt), Level 이 그 배치를 쓴다.
 */
class StageEditorScreen(private val gameView: GameView) : Screen {

    /** 지도에 놓인 것 하나 — 배치(Placement)와 그것을 그리는 진짜 개체(미리보기) */
    private class Item(var p: Placement, var view: Any?)

    /** 고를 수 있는 땅 스테이지 (11 하늘 스테이지 빼고 — Claude 판단) */
    private val stages = GameProgress.playableStages.filter { !GameProgress.isSkyStage(it) }

    private var stage: Int? = null
    private val items = mutableListOf<Item>()
    private var dirty = false
    private var timer = 0f

    // ===== 지도 보기 =====
    /** 지도에 보이는 세계 y 범위 (땅 기준선 위 720 ~ 아래 110) */
    private val worldTop = Constants.GROUND_Y - 720f
    private val worldBottom = Constants.GROUND_Y + 110f
    private var camX = 0f
    private var scale = 1f

    // ===== 아래 목록 =====
    private var tab = 0   // 0 = 고양이, 1 = 함정·물건
    private var paletteScroll = 0f
    private val iconCache = HashMap<PlaceType, Bitmap?>()

    // ===== 손가락 =====
    private enum class Mode { NONE, MAP_PAN, BAR, PALETTE_PENDING, PALETTE_SCROLL, DRAG_NEW, DRAG_ITEM, BUTTON }
    private var mode = Mode.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var fingerX = 0f
    private var fingerY = 0f
    private var dragType: PlaceType? = null
    private var dragItem: Item? = null
    private var dragView: Any? = null
    /** 끄는 것의 x 와 손가락(세계 x) 사이 거리 */
    private var grabOffset = 0f
    private var pressedButton: String? = null

    // 처음대로·저장 안 하고 나가기 — 3초 안에 두 번 눌러 확인 (CharacterSelectScreen 지우기와 같은 방식)
    private var armed: String? = null
    private var armedT = 0f

    private var noticeText = ""
    private var noticeTimer = 0f

    // ===== 화면 칸 (draw 에서 정해 터치가 같이 쓴다) =====
    private val mapRect = RectF()
    private val barRect = RectF()
    private val paletteRect = RectF()
    private val itemsRect = RectF()
    private val trashRect = RectF()
    private val tabRects = arrayOf(RectF(), RectF())
    private val buttons = linkedMapOf("save" to RectF(), "reset" to RectF(), "play" to RectF(), "exit" to RectF())
    private val stageRects = mutableListOf<Pair<Int, RectF>>()
    private val pickBackRect = RectF()

    // ===== 그리기 도구 =====
    private val paperPaint = Paint().apply { color = Color.rgb(252, 249, 240) }
    private val panelPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val pressPaint = Paint().apply { color = Color.rgb(255, 244, 200); isAntiAlias = true }
    private val editedPaint = Paint().apply { color = Color.rgb(255, 232, 170); isAntiAlias = true }
    private val borderPaint = Paint().apply { color = Color.rgb(30, 28, 26); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
    private val titlePaint = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.LEFT; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val labelPaint = Paint().apply { color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val hintPaint = Paint().apply { color = Color.rgb(90, 84, 78); textAlign = Paint.Align.LEFT; isAntiAlias = true }
    private val skyPaint = Paint()
    private val groundPaint = Paint()
    private val groundTopPaint = Paint()
    private val worldTextPaint = Paint().apply { color = Color.rgb(30, 28, 26); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 44f; isAntiAlias = true }
    private val polePaint = Paint().apply { color = Color.rgb(70, 60, 50); strokeWidth = 8f; isAntiAlias = true }
    private val flagPaint = Paint().apply { color = Color.rgb(232, 66, 56); isAntiAlias = true }
    private val endFlagPaint = Paint().apply { color = Color.rgb(40, 40, 40); isAntiAlias = true }
    private val endLinePaint = Paint().apply { color = Color.argb(150, 40, 40, 40); strokeWidth = 6f; pathEffect = DashPathEffect(floatArrayOf(30f, 20f), 0f) }
    private val selectPaint = Paint().apply { color = Color.rgb(240, 170, 20); style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true }
    private val trackPaint = Paint().apply { color = Color.rgb(222, 216, 204); isAntiAlias = true }
    private val viewPaint = Paint().apply { color = Color.argb(90, 255, 190, 40); isAntiAlias = true }
    private val tickCat = Paint().apply { color = Color.rgb(226, 80, 60); strokeWidth = 3f }
    private val tickBoss = Paint().apply { color = Color.rgb(140, 40, 160); strokeWidth = 5f }
    private val tickHazard = Paint().apply { color = Color.rgb(90, 90, 96); strokeWidth = 3f }
    private val tickPrison = Paint().apply { color = Color.rgb(230, 170, 20); strokeWidth = 4f }
    private val trashPaint = Paint().apply { color = Color.rgb(255, 214, 210); isAntiAlias = true }
    private val trashHotPaint = Paint().apply { color = Color.rgb(250, 140, 130); isAntiAlias = true }
    private val ghostPaint = Paint().apply { alpha = 200; isFilterBitmap = true }
    private val bitmapPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val noticePaint = Paint().apply { color = Color.rgb(255, 250, 225); isAntiAlias = true }
    private val shipDst = RectF()
    private val tmpRect = RectF()

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime
        if (armedT > 0f) { armedT -= deltaTime; if (armedT <= 0f) armed = null }
        // 끌고 가다 지도 가장자리에 대면 그쪽으로 저절로 넘어간다 (Claude 판단: 가장자리 8%, 초당 화면 0.9개)
        if ((mode == Mode.DRAG_NEW || mode == Mode.DRAG_ITEM) && mapRect.contains(fingerX, fingerY)) {
            val edge = mapRect.width() * 0.08f
            val speed = mapRect.width() / scale * 0.9f * deltaTime
            if (fingerX < mapRect.left + edge) camX -= speed
            else if (fingerX > mapRect.right - edge) camX += speed
            clampCam()
        }
    }

    // =====================================================================
    // 그리기
    // =====================================================================

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, paperPaint)
        if (stage == null) drawStagePick(canvas, w, h) else { drawEditor(canvas, w, h); drawChooser(canvas, w, h) }
        drawNotice(canvas, w, h)
    }

    /** 스테이지 고르기: 10칸씩 줄지어 */
    private fun drawStagePick(canvas: Canvas, w: Float, h: Float) {
        titlePaint.textSize = h * 0.06f
        titlePaint.textAlign = Paint.Align.CENTER
        canvas.drawTr("🛠 스테이지 편집 — 고칠 스테이지를 고르세요", w / 2f, h * 0.085f, titlePaint)
        titlePaint.textAlign = Paint.Align.LEFT

        pickBackRect.set(w * 0.015f, h * 0.015f, w * 0.16f, h * 0.10f)
        drawButton(canvas, pickBackRect, "◀ 로비로", pressedButton == "pick_back", h)

        val cols = 10
        val rows = (stages.size + cols - 1) / cols
        val left = w * 0.04f
        val top = h * 0.16f
        val cellW = (w * 0.92f) / cols
        val cellH = ((h * 0.76f) / rows).coerceAtMost(h * 0.19f)
        stageRects.clear()
        labelPaint.textSize = cellH * 0.34f
        hintPaint.textAlign = Paint.Align.CENTER
        hintPaint.textSize = cellH * 0.17f
        for ((i, s) in stages.withIndex()) {
            val r = RectF(left + (i % cols) * cellW + 6f, top + (i / cols) * cellH + 6f,
                left + (i % cols + 1) * cellW - 6f, top + (i / cols + 1) * cellH - 6f)
            stageRects.add(s to r)
            val edited = StageEdits.isEdited(s)
            canvas.drawRoundRect(r, 14f, 14f, if (pressedButton == "stage_$s") pressPaint else if (edited) editedPaint else panelPaint)
            canvas.drawRoundRect(r, 14f, 14f, borderPaint)
            canvas.drawTr("$s", r.centerX(), r.centerY() + cellH * 0.08f, labelPaint)
            if (edited) canvas.drawTr("✏️ 편집함", r.centerX(), r.bottom - cellH * 0.08f, hintPaint)
        }
        hintPaint.textAlign = Paint.Align.LEFT
        hintPaint.textSize = h * 0.028f
        canvas.drawTr("11 은 하늘 스테이지라 편집할 수 없어요 · 저장한 배치는 이 기기(디버그 빌드)에서만 쓰여요", left, h * 0.97f, hintPaint)
    }

    private fun drawEditor(canvas: Canvas, w: Float, h: Float) {
        val s = stage ?: return
        layoutEditor(w, h)

        // 위 줄: 제목 · 냥이 수 · 버튼
        titlePaint.textSize = h * 0.048f
        canvas.drawTr("스테이지 $s 편집${if (StageEdits.isEdited(s)) " ✏️" else ""}${if (dirty) " *" else ""}", w * 0.015f, h * 0.058f, titlePaint)
        hintPaint.textSize = h * 0.026f
        val cats = items.count { it.p.type.isEnemy }
        val bosses = items.count { it.p.type.group == PlaceGroup.BOSS }
        val swamps = items.count { it.p.type == PlaceType.SWAMP }
        canvas.drawTr("냥이 ${cats}마리 (보스 $bosses)${if (swamps > 0) " + 늪 속 $swamps" else ""} · 함정·물건 ${items.size - cats}개",
            w * 0.015f, h * 0.097f, hintPaint)
        for ((id, r) in buttons) {
            val text = when (id) {
                "save" -> "💾 저장"
                "reset" -> if (armed == "reset") "한 번 더!" else "↺ 처음대로"
                "play" -> "▶ 해 보기"
                else -> if (armed == "exit") "한 번 더!" else "나가기"
            }
            drawButton(canvas, r, text, pressedButton == id, h)
        }

        drawMap(canvas, s)
        drawBar(canvas)
        drawPalette(canvas, h)

        // 끄는 중: 지도 밖이면 손가락에 아이콘을 붙여 그린다
        val t = dragType ?: dragItem?.p?.type
        if (t != null && !mapRect.contains(fingerX, fingerY)) {
            iconFor(t)?.let { ic ->
                val ih = h * 0.14f
                val iw = ic.width * ih / ic.height
                tmpRect.set(fingerX - iw / 2f, fingerY - ih, fingerX + iw / 2f, fingerY)
                canvas.drawBitmap(ic, null, tmpRect, ghostPaint)
            }
        }
    }

    private fun layoutEditor(w: Float, h: Float) {
        mapRect.set(0f, h * 0.115f, w, h * 0.665f)
        barRect.set(w * 0.02f, h * 0.675f, w * 0.98f, h * 0.715f)
        paletteRect.set(0f, h * 0.728f, w, h)
        tabRects[0].set(w * 0.01f, h * 0.742f, w * 0.12f, h * 0.862f)
        tabRects[1].set(w * 0.01f, h * 0.874f, w * 0.12f, h * 0.99f)
        itemsRect.set(w * 0.13f, h * 0.738f, w * 0.865f, h * 0.995f)
        trashRect.set(w * 0.875f, h * 0.742f, w * 0.99f, h * 0.99f)
        val bw = w * 0.115f
        var x = w * 0.99f
        for (id in listOf("exit", "play", "reset", "save")) {
            buttons[id]!!.set(x - bw, h * 0.015f, x, h * 0.10f)
            x -= bw + w * 0.008f
        }
        scale = mapRect.height() / (worldBottom - worldTop)
    }

    private fun drawButton(canvas: Canvas, r: RectF, text: String, pressed: Boolean, h: Float) {
        canvas.drawRoundRect(r, 14f, 14f, if (pressed) pressPaint else panelPaint)
        canvas.drawRoundRect(r, 14f, 14f, borderPaint)
        labelPaint.textSize = h * 0.032f
        canvas.drawTr(text, r.centerX(), r.centerY() + h * 0.011f, labelPaint)
    }

    private val viewW: Float get() = mapRect.width() / scale

    private fun drawMap(canvas: Canvas, s: Int) {
        setColors(s)
        canvas.save()
        canvas.clipRect(mapRect)
        canvas.drawRect(mapRect, skyPaint)
        canvas.translate(mapRect.left - camX * scale, mapRect.top - worldTop * scale)
        canvas.scale(scale, scale)

        val dragging = if (mode == Mode.DRAG_ITEM) dragItem else null
        val chasms = items.filter { it !== dragging }.mapNotNull { it.view as? Chasm }
        // 땅: 16 단위 조각마다 Terrain 높이로 (낭떠러지·수직 구덩이 자리는 비운다)
        val step = 16f
        var wx = kotlin.math.floor(camX / step) * step
        val end = camX + viewW + step
        while (wx < end) {
            val mid = wx + step / 2f
            if (chasms.none { mid > it.x && mid < it.x + it.width }) {
                val gy = Terrain.groundY(mid)
                canvas.drawRect(wx, gy, wx + step + 1f, worldBottom + 60f, groundPaint)
                canvas.drawRect(wx, gy, wx + step + 1f, gy + 14f, groundTopPaint)
            }
            wx += step
        }
        for (c in chasms) c.draw(canvas, worldBottom + 60f)

        // 31 해적선 (편집으로 놓지 않는다 — 해적냥이 자리에 따라 저절로)
        if (s == 31) {
            val sx = Level.shipXFor(items.map { it.p })
            val bmp = SpriteManager.pirateShip
            val sw = bmp.width * SHIP_DRAW_H / bmp.height
            shipDst.set(sx - sw / 2f, Constants.GROUND_Y + 60f - SHIP_DRAW_H, sx + sw / 2f, Constants.GROUND_Y + 60f)
            canvas.drawBitmap(bmp, null, shipDst, bitmapPaint)
        }

        // 출발 깃발 · 끝 표시
        val startX = 200f
        val sg = Terrain.groundY(startX)
        canvas.drawLine(startX, sg, startX, sg - 220f, polePaint)
        canvas.drawRect(startX, sg - 220f, startX + 90f, sg - 160f, flagPaint)
        canvas.drawTr("출발", startX + 20f, sg - 240f, worldTextPaint)
        val endX = Level.areaWidthFor(s, items.map { it.p })
        canvas.drawLine(endX, worldTop, endX, worldBottom, endLinePaint)
        canvas.drawRect(endX, worldTop + 40f, endX + 90f, worldTop + 100f, endFlagPaint)
        canvas.drawTr("스테이지 끝", endX, worldTop + 150f, worldTextPaint)

        // 바닥 함정 → 감옥 → 냥이 순서 (게임과 같은 겹침)
        for (layer in 0..2) {
            for (it in items) {
                if (it === dragging || it.view is Chasm || layerOf(it.view) != layer) continue
                drawView(canvas, it.view)
                if (it.p.type == PlaceType.PRISON_CAT) drawPrisonTag(canvas, it)
            }
        }

        // 끄는 것: 손가락 자리의 땅 위에 그린다
        if ((mode == Mode.DRAG_NEW || mode == Mode.DRAG_ITEM) && mapRect.contains(fingerX, fingerY)) {
            val v = dragView
            val baseX = (dragItem?.p?.x) ?: refX
            if (v != null) {
                val nx = dropX()
                val dy = Terrain.groundY(nx + centerOffset(v)) - Terrain.groundY(baseX + centerOffset(v))
                canvas.save()
                canvas.translate(nx - baseX, dy)
                if (v is Chasm) v.draw(canvas, worldBottom + 60f) else drawView(canvas, v)
                spanOf(v, tmpRect)
                canvas.drawRoundRect(tmpRect, 12f, 12f, selectPaint)
                canvas.restore()
            }
        }
        // 눌러서 고른 것: 테두리 + 위에 '지우기' 단추 (대표님 2026-10-31 「스테이지에 있는것도 클릭해서 없앨 수 있어야지」)
        val sel = selected?.takeIf { it in items && mode != Mode.DRAG_ITEM }
        if (sel != null) { spanOf(sel.view, tmpRect); canvas.drawRoundRect(tmpRect, 12f, 12f, selectPaint) }
        canvas.restore()
        delBtnRect.setEmpty()
        if (sel != null) {
            val cx = mapRect.left + (tmpRect.centerX() - camX) * scale
            val top = mapRect.top + (tmpRect.top - worldTop) * scale
            val bw = mapRect.height() * 0.30f; val bh = mapRect.height() * 0.11f
            // 냥이 감옥이면 옆에 '냥이 고르기' 단추도 (대표님 2026-10-31 「감옥에 넣을 냥이도 고를 수 있게 해줘」)
            val choosable = canChooseCat(sel)
            val totalW = if (choosable) bw * 2f + 12f else bw
            val bx = cx.coerceIn(mapRect.left + totalW / 2f + 4f, mapRect.right - totalW / 2f - 4f)
            val by = (top - bh - 8f).coerceAtLeast(mapRect.top + 4f)
            delBtnRect.set(bx - totalW / 2f, by, bx - totalW / 2f + bw, by + bh)
            canvas.drawRoundRect(delBtnRect, 14f, 14f, trashHotPaint)
            canvas.drawRoundRect(delBtnRect, 14f, 14f, borderPaint)
            labelPaint.textSize = bh * 0.45f
            canvas.drawTr("🗑 지우기", delBtnRect.centerX(), delBtnRect.centerY() + bh * 0.16f, labelPaint)
            chooseBtnRect.setEmpty()
            if (choosable) {
                chooseBtnRect.set(delBtnRect.right + 12f, by, delBtnRect.right + 12f + bw, by + bh)
                canvas.drawRoundRect(chooseBtnRect, 14f, 14f, editedPaint)
                canvas.drawRoundRect(chooseBtnRect, 14f, 14f, borderPaint)
                canvas.drawTr("🐱 냥이 고르기", chooseBtnRect.centerX(), chooseBtnRect.centerY() + bh * 0.16f, labelPaint)
            }
        } else chooseBtnRect.setEmpty()
        canvas.drawRect(mapRect, borderPaint)
    }

    /** 눌러서 고른 놓인 것 (지우기 단추가 뜬다) */
    private var selected: Item? = null
    private val delBtnRect = RectF()
    private val chooseBtnRect = RectF()

    // ===== 감옥 냥이 고르기 (대표님 2026-10-31 「감옥에 넣을 냥이도 고를 수 있게 해줘」) =====
    /** 고르기 창이 열린 감옥 (null = 닫힘) */
    private var chooserFor: Item? = null
    /** 고를 수 있는 것 (냥이 종류, 엄마+새끼인가): 무작위(null) · 엄마+새끼 + 우리 팀이 될 수 있는 모든 냥이 */
    private val chooserKinds: List<Pair<AllyCat.Kind?, Boolean>> =
        listOf<Pair<AllyCat.Kind?, Boolean>>(null to false, null to true) + AllyCat.Kind.values().map { it to false }
    private val chooserRects = mutableListOf<Pair<Pair<AllyCat.Kind?, Boolean>, RectF>>()
    private val chooserCloseRect = RectF()
    private var chooserDown = false
    private val veilPaint = Paint().apply { color = Color.argb(170, 20, 18, 16) }
    private val tagPaint = Paint().apply { color = Color.rgb(30, 28, 26); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 30f; isAntiAlias = true }
    private val tagBgPaint = Paint().apply { color = Color.argb(225, 255, 250, 225); isAntiAlias = true }

    /** 30 의 배냥이 감옥은 배냥이 그대로 (Claude 판단: 바꿀 수 없다), 인간 감옥은 고를 냥이가 없다 */
    private fun canChooseCat(it: Item) = it.p.type == PlaceType.PRISON_CAT && !undeletable(it)

    private fun catName(k: AllyCat.Kind?, kitten: Boolean = false): String = when {
        kitten -> "👩‍👧 엄마+새끼"
        k == null -> "🎲 무작위"
        else -> com.jamcat.nayngapo.engine.CatTeam.baseNameOf(k)
    }

    /** 지도의 냥이 감옥 위에 갇힌 냥이 이름표 (무작위면 '🎲 무작위') — 그림은 감옥이 그 냥이로 그린다 */
    private fun drawPrisonTag(canvas: Canvas, it: Item) {
        val pr = it.view as? Prison ?: return
        val text = catName(it.p.catKind, it.p.withKitten)
        val tw = tagPaint.measureTr(text)
        val cx = pr.x + pr.width / 2f
        val ty = pr.y - pr.height - 18f
        tmpRect2.set(cx - tw / 2f - 10f, ty - 32f, cx + tw / 2f + 10f, ty + 8f)
        canvas.drawRoundRect(tmpRect2, 10f, 10f, tagBgPaint)
        canvas.drawTr(text, cx, ty, tagPaint)
    }

    /** 고르기 창: 한 화면에 8칸씩 4줄 (25칸 = 무작위·엄마+새끼 + 23종이라 쪽 넘기기는 필요 없다) */
    private fun drawChooser(canvas: Canvas, w: Float, h: Float) {
        val target = chooserFor ?: return
        canvas.drawRect(0f, 0f, w, h, veilPaint)
        val panel = RectF(w * 0.04f, h * 0.06f, w * 0.96f, h * 0.95f)
        canvas.drawRoundRect(panel, 22f, 22f, paperPaint)
        canvas.drawRoundRect(panel, 22f, 22f, borderPaint)
        titlePaint.textSize = h * 0.045f
        canvas.drawTr("냥이 감옥에 넣을 냥이 고르기", panel.left + w * 0.02f, panel.top + h * 0.07f, titlePaint)
        hintPaint.textSize = h * 0.026f
        canvas.drawTr("무작위 = 70% 이 스테이지에 나오는 냥이, 30% 아무 냥이 (예전과 같음)", panel.left + w * 0.02f, panel.top + h * 0.115f, hintPaint)
        chooserCloseRect.set(panel.right - w * 0.13f, panel.top + h * 0.02f, panel.right - w * 0.015f, panel.top + h * 0.095f)
        drawButton(canvas, chooserCloseRect, "닫기", false, h)

        val cols = 8
        val rows = (chooserKinds.size + cols - 1) / cols
        val gridTop = panel.top + h * 0.14f
        val cellW = (panel.width() - w * 0.03f) / cols
        val cellH = (panel.bottom - gridTop - h * 0.02f) / rows
        chooserRects.clear()
        for ((i, opt) in chooserKinds.withIndex()) {
            val (k, kitten) = opt
            val l = panel.left + w * 0.015f + (i % cols) * cellW
            val t = gridTop + (i / cols) * cellH
            val r = RectF(l + 5f, t + 5f, l + cellW - 5f, t + cellH - 5f)
            chooserRects.add(opt to r)
            val chosen = target.p.catKind == k && target.p.withKitten == kitten
            canvas.drawRoundRect(r, 14f, 14f, if (chosen) pressPaint else panelPaint)
            canvas.drawRoundRect(r, 14f, 14f, if (chosen) selectPaint else borderPaint)
            val nameY = r.bottom - cellH * 0.08f
            if (k != null || kitten) {
                val bmp = Prison.spriteFor(k ?: AllyCat.Kind.NORMAL, com.jamcat.nayngapo.engine.CatCoat.CHEESE)
                val boxT = r.top + 8f; val boxB = nameY - cellH * 0.16f
                val sc = minOf((r.width() - 16f) / bmp.width, (boxB - boxT) / bmp.height)
                val bw = bmp.width * sc; val bh = bmp.height * sc
                val cx = r.centerX(); val cy = (boxT + boxB) / 2f
                if (kitten) {
                    // 엄마(왼쪽) + 새끼(오른쪽 아래, 작게) — 감옥 그림과 같은 짝
                    canvas.drawBitmap(bmp, null, RectF(cx - bw * 0.62f, cy - bh * 0.42f, cx + bw * 0.22f, cy + bh * 0.42f), bitmapPaint)
                    canvas.drawBitmap(bmp, null, RectF(cx + bw * 0.02f, cy, cx + bw * 0.52f, cy + bh * 0.5f), bitmapPaint)
                } else canvas.drawBitmap(bmp, null, RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f), bitmapPaint)
            } else {
                labelPaint.textSize = cellH * 0.38f
                canvas.drawTr("🎲", r.centerX(), r.centerY(), labelPaint)
            }
            val name = catName(k, kitten)
            labelPaint.textSize = cellH * 0.13f
            val tw = labelPaint.measureTr(name)
            if (tw > r.width() - 10f) labelPaint.textSize *= (r.width() - 10f) / tw
            canvas.drawTr(name, r.centerX(), nameY, labelPaint)
        }
    }

    /** 고르기 창이 열려 있으면 터치는 여기서만 받는다 */
    private fun onChooserTouch(event: MotionEvent, x: Float, y: Float): Boolean {
        // 창을 연 손가락을 뗄 때 바로 아래 칸이 골라지지 않게, 창 안에서 새로 누른 것만 받는다
        if (event.actionMasked == MotionEvent.ACTION_DOWN) { chooserDown = true; return true }
        if (event.actionMasked != MotionEvent.ACTION_UP || !chooserDown) return true
        chooserDown = false
        val target = chooserFor ?: return true
        if (chooserCloseRect.contains(x, y)) { chooserFor = null; return true }
        val hit = chooserRects.firstOrNull { it.second.contains(x, y) } ?: return true
        val (k, kitten) = hit.first
        if (target.p.catKind != k || target.p.withKitten != kitten) {
            target.p = target.p.copy(catKind = k, withKitten = kitten)
            target.view = makeView(target.p)
            dirty = true
        }
        chooserFor = null
        return true
    }

    /** 0 = 바닥 함정, 1 = 감옥, 2 = 냥이 */
    private fun layerOf(v: Any?): Int = when (v) { is Enemy -> 2; is Prison -> 1; else -> 0 }

    private fun drawView(canvas: Canvas, v: Any?) {
        when (v) {
            // 냥이 그림은 게임 안 그대로 — 한 번도 update 하지 않은 냥이라 혹시 그리다 문제가 생겨도 편집 화면이 꺼지지 않게 감싼다
            is Enemy -> runCatching { v.draw(canvas) }
            is Trap -> v.draw(canvas)
            is PitTrap -> v.draw(canvas)
            is Swamp -> v.draw(canvas)
            is IceHole -> v.draw(canvas)
            is TornadoTrap -> v.draw(canvas)
            is SecretRoom -> v.draw(canvas)
            is Prison -> v.draw(canvas)
            is Chasm -> v.draw(canvas, v.y + 260f)
        }
    }

    /** 지역마다 하늘·땅 색 (지도에서 어느 지역인지 알아보게 — 색은 Claude 판단) */
    private fun setColors(s: Int) {
        val (sky, ground, top) = when (s) {
            in 12..20 -> Triple(Color.rgb(196, 226, 200), Color.rgb(104, 78, 50), Color.rgb(60, 130, 50))
            in 21..30 -> Triple(Color.rgb(255, 236, 190), Color.rgb(196, 128, 30), Color.rgb(250, 190, 70))
            31 -> Triple(Color.rgb(196, 228, 250), Color.rgb(60, 140, 200), Color.rgb(140, 200, 240))
            in 51..60 -> Triple(Color.rgb(222, 238, 252), Color.rgb(170, 205, 235), Color.rgb(250, 252, 255))
            else -> Triple(Color.rgb(200, 230, 252), Color.rgb(140, 100, 64), Color.rgb(110, 180, 70))
        }
        skyPaint.color = sky; groundPaint.color = ground; groundTopPaint.color = top
    }

    /** 아래 막대: 스테이지 전체 — 놓인 것 눈금 + 지금 보이는 자리 */
    private fun drawBar(canvas: Canvas) {
        canvas.drawRoundRect(barRect, 10f, 10f, trackPaint)
        val total = worldMax()
        for (it in items) {
            val bx = barRect.left + (it.p.x / total) * barRect.width()
            val paint = when {
                it.p.type.group == PlaceGroup.BOSS -> tickBoss
                it.p.type.isEnemy -> tickCat
                it.view is Prison -> tickPrison
                else -> tickHazard
            }
            canvas.drawLine(bx, barRect.top + 4f, bx, barRect.bottom - 4f, paint)
        }
        tmpRect.set(barRect.left + camX / total * barRect.width(), barRect.top,
            barRect.left + (camX + viewW) / total * barRect.width(), barRect.bottom)
        canvas.drawRoundRect(tmpRect, 8f, 8f, viewPaint)
        canvas.drawRoundRect(tmpRect, 8f, 8f, selectPaint)
        canvas.drawRoundRect(barRect, 10f, 10f, borderPaint)
    }

    private fun paletteTypes(): List<PlaceType> =
        if (tab == 0) PlaceType.values().filter { it.inPalette && it.group != PlaceGroup.HAZARD }
        else PlaceType.values().filter { it.group == PlaceGroup.HAZARD }

    private fun paletteLabel(t: PlaceType): String = when {
        t == PlaceType.SWAMP && stage in 21..30 -> "꿀 늪 (+꿀냥이)"
        t == PlaceType.SWAMP -> "늪 (+머드냥이)"
        else -> t.label
    }

    private val cardW: Float get() = itemsRect.height() * 0.78f
    private val cardGap = 10f

    private fun drawPalette(canvas: Canvas, h: Float) {
        canvas.drawRect(paletteRect, trackPaint)
        for ((i, name) in listOf("🐱 고양이", "⚠ 함정·물건").withIndex()) {
            drawButton(canvas, tabRects[i], name, tab == i, h)
            if (tab == i) canvas.drawRoundRect(tabRects[i], 14f, 14f, selectPaint)
        }
        val types = paletteTypes()
        paletteScroll = paletteScroll.coerceIn(0f, (types.size * (cardW + cardGap) - itemsRect.width()).coerceAtLeast(0f))
        canvas.save()
        canvas.clipRect(itemsRect)
        labelPaint.textSize = h * 0.024f
        for ((i, t) in types.withIndex()) {
            val l = itemsRect.left + i * (cardW + cardGap) - paletteScroll
            if (l > itemsRect.right || l + cardW < itemsRect.left) continue
            tmpRect.set(l, itemsRect.top + 4f, l + cardW, itemsRect.bottom - 4f)
            canvas.drawRoundRect(tmpRect, 12f, 12f, if (dragType == t) pressPaint else panelPaint)
            canvas.drawRoundRect(tmpRect, 12f, 12f, borderPaint)
            val nameY = tmpRect.bottom - h * 0.014f
            iconFor(t)?.let { ic ->
                val boxT = tmpRect.top + 8f
                val boxB = nameY - h * 0.03f
                val sc = minOf((cardW - 16f) / ic.width, (boxB - boxT) / ic.height)
                val iw = ic.width * sc; val ih = ic.height * sc
                val cx = tmpRect.centerX(); val cy = (boxT + boxB) / 2f
                canvas.drawBitmap(ic, null, RectF(cx - iw / 2f, cy - ih / 2f, cx + iw / 2f, cy + ih / 2f), bitmapPaint)
            }
            // 이름이 칸보다 길면 글씨를 줄인다
            val label = paletteLabel(t)
            val base = h * 0.024f
            labelPaint.textSize = base
            val tw = labelPaint.measureTr(label)
            if (tw > cardW - 8f) labelPaint.textSize = base * (cardW - 8f) / tw
            canvas.drawTr(label, tmpRect.centerX(), nameY, labelPaint)
        }
        canvas.restore()

        val hot = (mode == Mode.DRAG_ITEM) && (trashRect.contains(fingerX, fingerY) || paletteRect.contains(fingerX, fingerY))
        canvas.drawRoundRect(trashRect, 14f, 14f, if (hot) trashHotPaint else trashPaint)
        canvas.drawRoundRect(trashRect, 14f, 14f, borderPaint)
        labelPaint.textSize = h * 0.06f
        canvas.drawTr("🗑", trashRect.centerX(), trashRect.centerY(), labelPaint)
        labelPaint.textSize = h * 0.024f
        canvas.drawTr("여기 놓으면 지움", trashRect.centerX(), trashRect.bottom - h * 0.025f, labelPaint)
    }

    private fun drawNotice(canvas: Canvas, w: Float, h: Float) {
        if (noticeTimer <= 0f) return
        labelPaint.textSize = h * 0.036f
        val tw = labelPaint.measureTr(noticeText)
        val boxW = (tw + w * 0.05f).coerceAtLeast(w * 0.3f)
        val r = RectF(w / 2f - boxW / 2f, h * 0.40f, w / 2f + boxW / 2f, h * 0.49f)
        canvas.drawRoundRect(r, 18f, 18f, noticePaint)
        canvas.drawRoundRect(r, 18f, 18f, borderPaint)
        canvas.drawTr(noticeText, r.centerX(), r.centerY() + h * 0.013f, labelPaint)
    }

    // =====================================================================
    // 개체 크기 · 아이콘
    // =====================================================================

    /** 놓인 것의 세계 좌표 테두리 (누르기·선택 표시용) */
    private fun spanOf(v: Any?, out: RectF) {
        when (v) {
            is Enemy -> {
                out.set(v.hitBox)
                out.top = minOf(out.top, v.y - 90f); out.bottom = v.y + 10f
                out.inset(-25f, -10f)
            }
            is Trap -> out.set(v.x, v.y - 60f, v.x + v.width, v.y + 15f)
            is PitTrap -> out.set(v.x, v.y - 40f, v.x + v.width, v.y + 40f)
            is Swamp -> out.set(v.x - 20f, v.y - 40f, v.x + v.width + 20f, v.y + 40f)
            is Chasm -> out.set(v.x, v.y - 85f, v.x + v.width, v.y + 120f)
            is IceHole -> out.set(v.x, v.y - 30f, v.x + v.width, v.y + 30f)
            is TornadoTrap -> out.set(v.x - 60f, v.groundY - v.height, v.x + 60f, v.groundY + 10f)
            is SecretRoom -> out.set(v.x, v.y - 60f, v.x + v.width, v.y + 20f)
            is Prison -> out.set(v.hitBox)
            else -> out.setEmpty()
        }
    }

    /** 놓는 x(배치 x)에서 그 개체 가운데까지 — 손가락 밑에 가운데가 오게 */
    private fun centerOffset(v: Any?): Float {
        spanOf(v, tmpRect2)
        val x = when (v) { is Enemy -> v.x; is TornadoTrap -> v.x; is Trap -> v.x; is PitTrap -> v.x; is Swamp -> v.x; is Chasm -> v.x
            is IceHole -> v.x; is SecretRoom -> v.x; is Prison -> v.x; else -> 0f }
        return tmpRect2.centerX() - x
    }
    private val tmpRect2 = RectF()

    /** 목록 아이콘: 진짜 개체를 따로 된 그림판에 한 번 그리고 빈 테두리를 잘라 둔다 */
    private fun iconFor(t: PlaceType): Bitmap? = iconCache.getOrPut(t) {
        val view = makeView(Placement(t, ICON_X, seed = 7)) ?: return@getOrPut null
        val size = 900
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // 세계 (300, 땅 기준선) → 그림판 (300, 650). 출발점 근처(x<500)는 늘 평평하다
        c.translate(0f, 650f - Constants.GROUND_Y)
        if (view is Enemy) view.y = Constants.GROUND_Y
        drawView(c, view)
        val px = IntArray(size * size)
        bmp.getPixels(px, 0, size, 0, 0, size, size)
        var l = size; var tp = size; var r = -1; var b = -1
        for (y in 0 until size) for (x in 0 until size) {
            if ((px[y * size + x] ushr 24) > 8) {
                if (x < l) l = x; if (x > r) r = x; if (y < tp) tp = y; if (y > b) b = y
            }
        }
        if (r < l || b < tp) { bmp.recycle(); return@getOrPut null }
        val cut = Bitmap.createBitmap(bmp, l, tp, r - l + 1, b - tp + 1)
        if (cut !== bmp) bmp.recycle()
        cut
    }

    // =====================================================================
    // 편집 동작
    // =====================================================================

    private fun openStage(s: Int) {
        stage = s
        Terrain.stage = s
        iconCache.clear()   // 꿀 늪처럼 스테이지마다 모습이 다른 것
        val layout = StageEdits.load(s) ?: Level.defaultLayout(s)   // 편집 안 한 스테이지는 기본 배치를 한 번 만들어 보여 준다
        items.clear()
        for (p in layout) items.add(Item(p, null))
        rebuildAll()
        camX = 0f
        dirty = false
        armed = null
        paletteScroll = 0f
    }

    /** 31: 해적선 갑판 높이가 해적냥이 자리에 따라 바뀌므로 갑판을 다시 정하고 모두 새로 만든다 */
    private fun rebuildAll() {
        val s = stage ?: return
        if (s == 31) Level.setupDeck(Level.shipXFor(items.map { it.p }))
        for (it in items) it.view = makeView(it.p)
    }

    private fun makeView(p: Placement): Any? {
        val v = Level.createEntity(p, stage ?: 1)
        if (v is Enemy) v.y = Terrain.groundY(v.x + v.width / 2f)   // 게임에서는 첫 update 에 땅으로 붙는다
        return v
    }

    private fun worldMax(): Float =
        maxOf(Level.areaWidthFor(stage ?: 1, items.map { it.p }) + 600f, camX + viewW).coerceAtMost(Constants.PLAY_AREA_RIGHT + 600f)

    private fun clampCam() {
        camX = camX.coerceIn(0f, (worldMax() - viewW).coerceAtLeast(0f))
    }

    private fun toWorldX(sx: Float) = camX + (sx - mapRect.left) / scale
    private fun toWorldY(sy: Float) = worldTop + (sy - mapRect.top) / scale

    /** 새로 끌어 온 것의 기준 x (미리보기를 만든 자리) */
    private var refX = 0f

    /** 지금 손가락 자리에 놓으면 될 배치 x */
    private fun dropX(): Float = (toWorldX(fingerX) - grabOffset).coerceIn(Level.MIN_PLACE_X, Level.MAX_PLACE_X)

    private fun itemAt(sx: Float, sy: Float): Item? {
        val wx = toWorldX(sx); val wy = toWorldY(sy)
        // 위에 그려진 것(냥이 → 감옥 → 함정)부터
        for (layer in 2 downTo 0) {
            for (it in items.asReversed()) {
                if (layerOf(it.view) != layer) continue
                spanOf(it.view, tmpRect)
                if (tmpRect.contains(wx, wy)) return it
            }
        }
        return null
    }

    private fun paletteTypeAt(sx: Float, sy: Float): PlaceType? {
        if (!itemsRect.contains(sx, sy)) return null
        val i = ((sx - itemsRect.left + paletteScroll) / (cardW + cardGap)).toInt()
        val within = (sx - itemsRect.left + paletteScroll) - i * (cardW + cardGap) <= cardW
        return paletteTypes().getOrNull(i)?.takeIf { within }
    }

    /** 30 의 배냥이 감옥은 31 바다에 나갈 배냥이라 지울 수 없다 (옮기기만 — Claude 판단) */
    private fun undeletable(it: Item) = stage == 30 && it.p.type == PlaceType.PRISON_CAT && it.p.catKind == AllyCat.Kind.BOAT

    private fun finishDrag() {
        val overMap = mapRect.contains(fingerX, fingerY)
        val overDelete = paletteRect.contains(fingerX, fingerY) || trashRect.contains(fingerX, fingerY)
        when (mode) {
            Mode.DRAG_NEW -> if (overMap) {
                val t = dragType!!
                val p = Placement(t, dropX(), seed = (dropX().toInt() * 7 + items.size) and 0xFFFF)
                items.add(Item(p, null))
                dirty = true
                rebuildAfterChange(items.last())
            }
            Mode.DRAG_ITEM -> {
                val it = dragItem!!
                if (overMap) {
                    it.p = it.p.copy(x = dropX())
                    dirty = true
                    rebuildAfterChange(it)
                } else if (overDelete) {
                    if (undeletable(it)) showNotice("30 의 배냥이 감옥은 지울 수 없어요 (31 바다에 갈 배냥이)")
                    else { items.remove(it); dirty = true; if (stage == 31) rebuildAll() }
                }
            }
            else -> {}
        }
        dragType = null; dragItem = null; dragView = null
    }

    private fun rebuildAfterChange(it: Item) {
        if (stage == 31) rebuildAll() else it.view = makeView(it.p)
    }

    private fun save() {
        val s = stage ?: return
        StageEdits.save(s, items.map { it.p })
        dirty = false
        showNotice("저장했어요! 이제 스테이지 $s 는 이 배치로 나와요")
    }

    private fun onButton(id: String) {
        val s = stage ?: return
        when (id) {
            "save" -> save()
            "reset" -> if (armed == "reset") {
                armed = null
                StageEdits.clear(s)
                openStage(s)
                showNotice("처음 배치로 돌렸어요 (기본 배치는 할 때마다 조금씩 달라요)")
            } else { armed = "reset"; armedT = 3f }
            "play" -> {
                // 해 보기 = 저장하고 그 스테이지로 출발 (저장해야 Level 이 이 배치를 쓴다 — Claude 판단). 못 가는 스테이지는 까닭만 띄운다
                save()
                val why = gameView.stageBlockReason(s)
                if (why != null) showNotice("저장했지만 지금은 못 들어가요: $why")
                else gameView.startStage(s)
            }
            "exit" -> leaveEditor()
        }
    }

    /** 나가기: 고친 게 저장 안 됐으면 한 번 더 눌러야 나간다 */
    private fun leaveEditor() {
        if (dirty && armed != "exit") {
            armed = "exit"; armedT = 3f
            showNotice("저장하지 않았어요! 한 번 더 누르면 저장 없이 나가요")
            return
        }
        armed = null
        stage = null
        items.clear()
        dirty = false
    }

    private fun showNotice(text: String) {
        noticeText = text
        noticeTimer = 2.4f
    }

    // =====================================================================
    // 터치
    // =====================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        if (stage == null) return onPickTouch(event, x, y)
        if (chooserFor != null) return onChooserTouch(event, x, y)
        fingerX = x; fingerY = y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x; lastY = y; downX = x; downY = y
                // 고른 것의 '지우기' 단추
                val sel = selected
                if (sel != null && !chooseBtnRect.isEmpty && chooseBtnRect.contains(x, y)) {
                    chooserFor = sel
                    chooserDown = false
                    mode = Mode.NONE
                    return true
                }
                if (sel != null && !delBtnRect.isEmpty && delBtnRect.contains(x, y)) {
                    if (undeletable(sel)) showNotice("30 의 배냥이 감옥은 지울 수 없어요 (31 바다에 갈 배냥이)")
                    else { items.remove(sel); dirty = true; if (stage == 31) rebuildAll() }
                    selected = null
                    mode = Mode.NONE
                    return true
                }
                // 빈 곳이나 지도 밖을 누르면 고르기 풀림
                if (!mapRect.contains(x, y)) selected = null
                else if (itemAt(x, y) == null) selected = null
                pressedButton = buttons.entries.firstOrNull { it.value.contains(x, y) }?.key
                mode = when {
                    pressedButton != null -> Mode.BUTTON
                    tabRects[0].contains(x, y) -> { tab = 0; paletteScroll = 0f; Mode.NONE }
                    tabRects[1].contains(x, y) -> { tab = 1; paletteScroll = 0f; Mode.NONE }
                    barRect.contains(x, y) -> { jumpBar(x); Mode.BAR }
                    itemsRect.contains(x, y) -> { dragType = paletteTypeAt(x, y); Mode.PALETTE_PENDING }
                    mapRect.contains(x, y) -> {
                        val hit = itemAt(x, y)
                        if (hit != null) {
                            dragItem = hit
                            dragView = hit.view
                            grabOffset = toWorldX(x) - hit.p.x
                            Mode.DRAG_ITEM
                        } else Mode.MAP_PAN
                    }
                    else -> Mode.NONE
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                when (mode) {
                    Mode.MAP_PAN -> { camX -= dx / scale; clampCam() }
                    Mode.BAR -> jumpBar(x)
                    Mode.PALETTE_PENDING -> {
                        val t = dragType
                        // 위로 움직인 게 더 크면 지도에 놓기 시작, 옆으로 움직인 게 더 크면 목록 넘기기
                        // (전에는 옆으로 14 만 움직여도 넘기기가 돼서 비스듬히 끌면 배치가 안 됐다 — 2026-10-31 「드래그 하면 배치가 안되는데?」)
                        val mx = abs(x - downX); val up = downY - y
                        if (t != null && up > 20f && up >= mx) startDragNew(t, x)
                        else if (mx > 20f && mx > up) { mode = Mode.PALETTE_SCROLL; dragType = null }
                    }
                    Mode.PALETTE_SCROLL -> paletteScroll -= dx
                    else -> {}
                }
                lastX = x; lastY = y
            }
            MotionEvent.ACTION_UP -> {
                when (mode) {
                    Mode.BUTTON -> pressedButton?.let { id -> if (buttons[id]!!.contains(x, y)) onButton(id) }
                    // 거의 안 움직이고 뗐으면 '누르기' — 그것을 고르고 지우기 단추를 띄운다 (한 번 더 누르면 고르기 풀림)
                    Mode.DRAG_ITEM -> if (abs(x - downX) < 15f && abs(y - downY) < 15f) {
                        selected = if (selected === dragItem) null else dragItem
                    } else { selected = null; finishDrag() }
                    Mode.DRAG_NEW -> finishDrag()
                    Mode.PALETTE_PENDING -> if (dragType != null) showNotice("위로 끌어서 지도에 놓으세요")
                    else -> {}
                }
                mode = Mode.NONE; pressedButton = null; dragType = null; dragItem = null; dragView = null
            }
            MotionEvent.ACTION_CANCEL -> {
                mode = Mode.NONE; pressedButton = null; dragType = null; dragItem = null; dragView = null
            }
        }
        return true
    }

    private fun startDragNew(t: PlaceType, sx: Float) {
        mode = Mode.DRAG_NEW
        refX = toWorldX(sx).coerceIn(Level.MIN_PLACE_X, Level.MAX_PLACE_X)
        val v = makeView(Placement(t, refX, seed = 7))
        dragView = v
        grabOffset = centerOffset(v)
    }

    private fun jumpBar(sx: Float) {
        val total = worldMax()
        camX = ((sx - barRect.left) / barRect.width()) * total - viewW / 2f
        clampCam()
    }

    private fun onPickTouch(event: MotionEvent, x: Float, y: Float): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressedButton = when {
                pickBackRect.contains(x, y) -> "pick_back"
                else -> stageRects.firstOrNull { it.second.contains(x, y) }?.let { "stage_${it.first}" }
            }
            MotionEvent.ACTION_UP -> {
                val id = pressedButton
                pressedButton = null
                if (id == "pick_back" && pickBackRect.contains(x, y)) gameView.showLobby()
                else stageRects.firstOrNull { it.second.contains(x, y) }?.let { (s, _) -> if (id == "stage_$s") openStage(s) }
            }
            MotionEvent.ACTION_CANCEL -> pressedButton = null
        }
        return true
    }

    override fun onBack(): Boolean {
        if (chooserFor != null) chooserFor = null
        else if (stage != null) leaveEditor() else gameView.showLobby()
        return true
    }

    companion object {
        /** 아이콘을 그릴 세계 x — 출발점 근처라 땅이 평평하다 */
        private const val ICON_X = 300f
    }
}
