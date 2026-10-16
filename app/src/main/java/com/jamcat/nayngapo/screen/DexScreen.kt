package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CatDex
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView

/**
 * 고양이 도감 (기획1). 발견한 냥이는 그림과 이름이 보이고,
 * 아직 못 만난 냥이는 '?' 카드로 남는다. 새로 발견한 냥이에는 NEW 배지.
 */
class DexScreen(private val gameView: GameView) : Screen {

    private var timer = 0f
    private val backRect = RectF()

    /** 카드마다 한 개씩 떠오르는 음표의 진행도 (0 ~ NOTE_CYCLE) — 카드 순서대로 조금씩 어긋난다 */
    private val noteT = FloatArray(CatDex.entries.size) { it * 0.10f }

    private val bgPaint = Paint().apply { color = Color.rgb(252, 249, 240) }
    private val titlePaint = Paint().apply {
        color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val cardPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val cardUnknownPaint = Paint().apply { color = Color.rgb(233, 228, 216); isAntiAlias = true }
    private val cardEdgePaint = Paint().apply {
        color = Color.rgb(30, 28, 26); style = Paint.Style.STROKE
        strokeWidth = 4.5f; isAntiAlias = true
    }
    private val namePaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val hintPaint = Paint().apply {
        color = Color.rgb(140, 132, 122); textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val qPaint = Paint().apply {
        color = Color.rgb(168, 160, 148); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val newBadgePaint = Paint().apply { color = Color.rgb(232, 66, 56); isAntiAlias = true }
    private val newTextPaint = Paint().apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val backPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val backTextPaint = Paint().apply {
        color = Color.rgb(60, 44, 12); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val countPaint = Paint().apply {
        color = Color.rgb(120, 112, 100); textAlign = Paint.Align.RIGHT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val bitmapPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    /** 춤에 맞춰 떠오르는 희미한 음표 */
    private val notePaint = Paint().apply {
        color = Color.rgb(45, 40, 36); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val silhouettePaint = Paint().apply {
        isFilterBitmap = true; isAntiAlias = true
        colorFilter = PorterDuffColorFilter(Color.rgb(196, 189, 176), PorterDuff.Mode.SRC_IN)
    }

    private val cardRect = RectF()
    private val srcRect = Rect()
    private val dstRect = RectF()

    override fun update(deltaTime: Float) {
        timer += deltaTime
        // 음표는 카드마다 하나씩만 살아 있고, 다 떠오르면 처음부터 다시
        for (i in noteT.indices) {
            noteT[i] = (noteT[i] + deltaTime) % NOTE_CYCLE
        }
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        titlePaint.textSize = h * 0.075f
        canvas.drawTr("고양이 도감", w / 2f, h * 0.095f, titlePaint)

        val found = CatDex.entries.count { GameProgress.isDiscovered(it.id) }
        countPaint.textSize = h * 0.042f
        canvas.drawTr("$found / ${CatDex.entries.size}", w - 30f, h * 0.09f, countPaint)

        // 카드 격자: 8 x 3 (22종 — 빨강·주황·노랑냥이까지)
        val cols = 8
        val rows = 3
        val padX = w * 0.03f
        val gap = w * 0.018f
        val cw = (w - padX * 2f - gap * (cols - 1)) / cols
        val ch = h * 0.215f
        val top = h * 0.14f

        for ((i, entry) in CatDex.entries.withIndex()) {
            val col = i % cols
            val row = i / cols
            if (row >= rows) break
            val l = padX + col * (cw + gap)
            val t = top + row * (ch + h * 0.025f)
            cardRect.set(l, t, l + cw, t + ch)
            drawCard(canvas, entry, cardRect, h, i)
        }

        // 돌아가기 버튼
        val bw = w * 0.18f
        val bh = h * 0.10f
        backRect.set(w / 2f - bw / 2f, h * 0.875f, w / 2f + bw / 2f, h * 0.875f + bh)
        canvas.drawRoundRect(backRect, 20f, 20f, backPaint)
        canvas.drawRoundRect(backRect, 20f, 20f, cardEdgePaint)
        backTextPaint.textSize = bh * 0.42f
        canvas.drawTr("돌아가기", backRect.centerX(), backRect.centerY() + bh * 0.15f, backTextPaint)
    }

    private fun drawCard(canvas: Canvas, entry: CatDex.Entry, rect: RectF, h: Float, index: Int) {
        val known = GameProgress.isDiscovered(entry.id)
        canvas.drawRoundRect(rect, 14f, 14f, if (known) cardPaint else cardUnknownPaint)
        canvas.drawRoundRect(rect, 14f, 14f, cardEdgePaint)

        // 발견한 냥이는 연속 동작 시트로, 시트가 없으면 몸을 흔들어 춤춘다
        val frames = if (known) entry.frames?.invoke()?.takeIf { it.isNotEmpty() } else null
        val bmp =
            if (frames != null) frames[((timer * 7f).toInt() + index) % frames.size]
            else entry.sprite()

        val availW = rect.width() - 20f
        val availH = rect.height() * 0.58f
        val scale = minOf(availW / bmp.width, availH / bmp.height)
        val bw = bmp.width * scale
        val bh = bmp.height * scale
        val cx = rect.centerX()
        val cy = rect.top + rect.height() * 0.38f
        srcRect.set(0, 0, bmp.width, bmp.height)
        dstRect.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)

        if (known) {
            if (frames != null) {
                canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
            } else {
                com.jamcat.nayngapo.ui.drawDancing(
                    canvas, bmp, dstRect, timer, bitmapPaint, phase = index.toFloat()
                )
            }
            drawNote(canvas, rect, dstRect, h, index)
            namePaint.textSize = h * 0.040f
            canvas.drawTr(entry.name, cx, rect.bottom - rect.height() * 0.17f, namePaint)
            hintPaint.textSize = h * 0.027f
            canvas.drawTr(entry.hint, cx, rect.bottom - rect.height() * 0.06f, hintPaint)
        } else {
            // 실루엣 + 물음표
            canvas.drawBitmap(bmp, srcRect, dstRect, silhouettePaint)
            qPaint.textSize = rect.height() * 0.30f
            canvas.drawTr("?", cx, cy + rect.height() * 0.1f, qPaint)
            namePaint.textSize = h * 0.040f
            canvas.drawTr("???", cx, rect.bottom - rect.height() * 0.17f, namePaint)
        }

        // NEW 배지 (아직 도감에서 확인 안 한 새 발견)
        if (GameProgress.isNewInDex(entry.id)) {
            val pulse = (kotlin.math.sin(timer.toDouble() * 5.0) * 0.5 + 0.5).toFloat()
            val r = rect.width() * 0.14f
            val bx = rect.right - r * 0.9f
            val by = rect.top + r * 0.9f
            newBadgePaint.alpha = (200 + 55 * pulse).toInt()
            canvas.drawCircle(bx, by, r, newBadgePaint)
            newTextPaint.textSize = r * 0.72f
            canvas.drawTr("N", bx, by + r * 0.26f, newTextPaint)
        }
    }

    /** 카드 오른쪽 위로 떠올랐다 사라지는 음표 하나 */
    private fun drawNote(canvas: Canvas, card: RectF, sprite: RectF, h: Float, index: Int) {
        val p = noteT[index] / NOTE_CYCLE
        val x = sprite.centerX() + card.width() * 0.26f
        val y = sprite.top - card.height() * 0.02f - p * card.height() * 0.30f
        notePaint.textSize = h * 0.030f
        notePaint.alpha = (140 * (1f - p)).toInt().coerceIn(0, 255)
        canvas.drawTr(if (index % 2 == 0) "\u266a" else "\u266b", x, y, notePaint)
        notePaint.alpha = 255
    }

    private companion object {
        /** 음표 하나가 떠오르는 데 걸리는 시간 */
        const val NOTE_CYCLE = 1.2f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && backRect.contains(event.x, event.y)) {
            close()
        }
        return true
    }

    override fun onBack(): Boolean {
        close()
        return true
    }

    private fun close() {
        GameProgress.markDexSeen()   // NEW 배지 해제
        gameView.showTeam()          // 도감은 '고양이 관리'에서 들어오므로 그리로 되돌아간다
    }
}
