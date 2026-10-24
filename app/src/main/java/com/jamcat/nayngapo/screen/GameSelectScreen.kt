package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameSlots
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import kotlin.math.abs

/**
 * 게임 고르기 — 시작 화면 다음 (대표님 2026-10-24 「게임 이름을 정하고, 편집하고, 다른 냥이 아포칼립스 게임을 할 수 있어」).
 * 게임 카드를 누르면 그 게임으로 시작, 카드마다 '✏ 이름 바꾸기' · '🗑 지우기'(두 번 눌러 확인), 끝에 '+ 새 게임'(이름을 정한다).
 * 카드가 많으면 옆으로 민다.
 */
class GameSelectScreen(private val gameView: GameView) : Screen {

    private val ctx = gameView.context
    private val cardRects = ArrayList<RectF>()
    private val editRects = ArrayList<RectF>()
    private val delRects = ArrayList<RectF>()
    private val newRect = RectF()
    private var scrollX = 0f
    private var maxScroll = 0f
    private var downX = 0f
    private var lastX = 0f
    private var dragging = false
    private var deleteArm = -1
    private var deleteArmT = 0f
    private var timer = 0f
    private var noticeText = ""
    private var noticeTimer = 0f
    /** 글자 입력창이 떠 있는 동안은 다른 터치를 막는다 */
    @Volatile private var prompting = false

    private val bg = Paint().apply { color = Color.rgb(252, 249, 240) }
    private val card = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val cur = Paint().apply { color = Color.rgb(255, 244, 200); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 4.5f; isAntiAlias = true }
    private val gold = Paint().apply { color = Color.rgb(240, 170, 20); style = Paint.Style.STROKE; strokeWidth = 8f; isAntiAlias = true }
    private val dash = Paint().apply { color = Color.rgb(150, 140, 128); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true; pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f) }
    private val plus = Paint().apply { color = Color.rgb(150, 140, 128); strokeWidth = 12f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val title = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val text = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val dim = Paint().apply { color = Color.rgb(130, 122, 112); textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val editPaint = Paint().apply { color = Color.rgb(160, 220, 255); isAntiAlias = true }
    private val delPaint = Paint().apply { color = Color.rgb(240, 140, 140); isAntiAlias = true }
    private val playPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val rect = RectF()

    init { GameSlots.load(ctx) }

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime
        if (deleteArmT > 0f) { deleteArmT -= deltaTime; if (deleteArmT <= 0f) deleteArm = -1 }
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bg)
        title.textSize = h * 0.068f
        canvas.drawTr("게임 고르기", w / 2f, h * 0.10f, title)
        val slots = GameSlots.all
        val cw = w * 0.24f; val gap = w * 0.02f; val padX = w * 0.04f
        maxScroll = ((slots.size + 1) * cw + slots.size * gap - (w - padX * 2f)).coerceAtLeast(0f)
        scrollX = scrollX.coerceIn(0f, maxScroll)
        while (cardRects.size < slots.size) { cardRects.add(RectF()); editRects.add(RectF()); delRects.add(RectF()) }
        for ((i, s) in slots.withIndex()) {
            val l = padX + i * (cw + gap) - scrollX
            val r = cardRects[i]
            r.set(l, h * 0.16f, l + cw, h * 0.90f)
            val isCur = s.id == GameSlots.currentId
            canvas.drawRoundRect(r, 22f, 22f, if (isCur) cur else card)
            canvas.drawRoundRect(r, 22f, 22f, if (isCur) gold else ink)
            text.textSize = h * 0.044f
            canvas.drawTr(s.name, r.centerX(), r.top + h * 0.10f, text)
            dim.textSize = h * 0.030f
            canvas.drawTr(GameSlots.summary(ctx, s.id), r.centerX(), r.top + h * 0.16f, dim)
            if (isCur) { dim.textSize = h * 0.028f; canvas.drawTr("지난번에 하던 게임", r.centerX(), r.top + h * 0.21f, dim) }
            // ▶ 하기
            rect.set(r.left + cw * 0.12f, r.top + h * 0.27f, r.right - cw * 0.12f, r.top + h * 0.40f)
            canvas.drawRoundRect(rect, 30f, 30f, playPaint); canvas.drawRoundRect(rect, 30f, 30f, ink)
            text.textSize = h * 0.046f
            canvas.drawTr("▶ 하기", rect.centerX(), rect.centerY() + h * 0.016f, text)
            // ✏ 이름 바꾸기 · 🗑 지우기
            val er = editRects[i]; er.set(r.left + cw * 0.10f, r.bottom - h * 0.24f, r.right - cw * 0.10f, r.bottom - h * 0.15f)
            canvas.drawRoundRect(er, 24f, 24f, editPaint); canvas.drawRoundRect(er, 24f, 24f, ink)
            text.textSize = h * 0.034f
            canvas.drawTr("✏ 이름 바꾸기", er.centerX(), er.centerY() + h * 0.012f, text)
            val dr = delRects[i]; dr.set(r.left + cw * 0.10f, r.bottom - h * 0.12f, r.right - cw * 0.10f, r.bottom - h * 0.03f)
            canvas.drawRoundRect(dr, 24f, 24f, delPaint); canvas.drawRoundRect(dr, 24f, 24f, ink)
            canvas.drawTr(if (deleteArm == s.id) "한 번 더 누르면 지워요" else "🗑 지우기", dr.centerX(), dr.centerY() + h * 0.012f, text)
        }
        // + 새 게임
        val nl = padX + slots.size * (cw + gap) - scrollX
        newRect.set(nl, h * 0.16f, nl + cw, h * 0.90f)
        canvas.drawRoundRect(newRect, 22f, 22f, card); canvas.drawRoundRect(newRect, 22f, 22f, dash)
        val cx = newRect.centerX(); val cy = h * 0.46f
        canvas.drawLine(cx - 50f, cy, cx + 50f, cy, plus); canvas.drawLine(cx, cy - 50f, cx, cy + 50f, plus)
        text.textSize = h * 0.044f
        canvas.drawTr("새 게임", cx, h * 0.62f, text)
        dim.textSize = h * 0.028f
        canvas.drawTr("처음부터 새로 시작", cx, h * 0.67f, dim)
        if (maxScroll > 0f) { dim.textSize = h * 0.028f; canvas.drawTr("◀ 옆으로 밀어서 더 보기 ▶", w / 2f, h * 0.96f, dim) }

        if (noticeTimer > 0f) {
            text.textSize = h * 0.040f
            val nw = text.measureText(noticeText) + 60f
            rect.set(w / 2f - nw / 2f, h * 0.44f, w / 2f + nw / 2f, h * 0.53f)
            canvas.drawRoundRect(rect, 20f, 20f, card); canvas.drawRoundRect(rect, 20f, 20f, ink)
            canvas.drawTr(noticeText, w / 2f, rect.centerY() + h * 0.014f, text)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (prompting) return true
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
        val slots = GameSlots.all.toList()
        if (newRect.contains(x, y)) {
            prompting = true
            gameView.promptText("새 게임 이름", "냥이 아포칼립스 ${(slots.maxOfOrNull { it.id } ?: 0) + 1}", 14) { name ->
                prompting = false
                if (name != null) { GameSlots.create(ctx, name); notice("'$name' 을(를) 만들었어요 — ▶ 하기로 시작") }
            }
            return
        }
        for ((i, s) in slots.withIndex()) {
            if (i >= cardRects.size) break
            if (editRects[i].contains(x, y)) {
                prompting = true
                gameView.promptText("게임 이름 바꾸기", s.name, 14) { name -> prompting = false; if (name != null) GameSlots.rename(ctx, s.id, name) }
                return
            }
            if (delRects[i].contains(x, y)) {
                if (slots.size <= 1) { notice("게임이 하나뿐이라 못 지워요"); return }
                if (deleteArm == s.id) {
                    GameSlots.delete(ctx, s.id); deleteArm = -1
                    com.jamcat.nayngapo.engine.GameProgress.init(ctx)   // 지운 게임이 지금 게임이었으면 다른 게임으로
                    notice("'${s.name}' 을(를) 지웠어요")
                } else { deleteArm = s.id; deleteArmT = 3f }
                return
            }
            if (cardRects[i].contains(x, y)) {
                GameSlots.switchTo(ctx, s.id)
                SoundManager.playCatHit()
                gameView.showLobby()
                return
            }
        }
    }

    private fun notice(t: String) { noticeText = t; noticeTimer = 2f }

    override fun onBack(): Boolean { gameView.showTitle(); return true }
}
