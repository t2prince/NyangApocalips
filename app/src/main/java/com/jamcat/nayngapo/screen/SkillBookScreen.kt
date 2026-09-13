package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CatSkills
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SkillBook
import com.jamcat.nayngapo.ui.drawRubyIcon
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 기술 배우기 화면의 공통 틀 — 루비로 기술을 배운다.
 * 고양이 기술(CatSkillScreen)과 인간 기술(HumanSkillScreen)이 같은 모양·동작을 쓴다.
 */
open class SkillBookScreen(
    private val gameView: GameView,
    /** 어떤 기술 목록을 보여 줄지 */
    private val book: SkillBook,
    /** 기술 그림 (id → 그림) */
    private val icons: () -> Map<String, Bitmap>,
    private val title: String,
    private val hint: String
) : Screen {

    private companion object {
        const val COLS = 3
    }

    private var timer = 0f
    private var pressedIndex = -1

    private var noticeText = ""
    private var noticeTimer = 0f

    private val backRect = RectF()
    private val cardRects = book.all.map { RectF() }
    private val toggleRects = book.all.map { RectF() }

    /** 기술을 배울 때 튀는 반짝임 */
    private class Spark(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val size: Float)

    private val sparks = mutableListOf<Spark>()

    private val bgPaint = Paint().apply { color = Color.rgb(252, 249, 240) }
    private val titlePaint = Paint().apply {
        color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val cardPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val cardLearnedPaint = Paint().apply { color = Color.rgb(255, 250, 232); isAntiAlias = true }
    private val cardPressPaint = Paint().apply { color = Color.rgb(255, 244, 200); isAntiAlias = true }
    private val edgePaint = Paint().apply {
        color = Color.rgb(30, 28, 26); style = Paint.Style.STROKE
        strokeWidth = 4.5f; isAntiAlias = true
    }
    private val namePaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val descPaint = Paint().apply {
        color = Color.rgb(140, 132, 122); textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val pricePaint = Paint().apply {
        color = Color.rgb(190, 50, 78); textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val rubyCountPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val onPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val offPaint = Paint().apply { color = Color.rgb(214, 210, 200); isAntiAlias = true }
    private val toggleTextPaint = Paint().apply {
        color = Color.rgb(60, 44, 12); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val badgePaint = Paint().apply { color = Color.rgb(92, 176, 96); isAntiAlias = true }
    private val badgeTextPaint = Paint().apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val emptyPaint = Paint().apply {
        color = Color.rgb(150, 142, 132); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val hintPaint = Paint().apply {
        color = Color.rgb(140, 132, 122); textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val backPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val noticePaint = Paint().apply { color = Color.rgb(255, 250, 225); isAntiAlias = true }
    private val noticeTextPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val bitmapPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val gemPaint = Paint().apply { isAntiAlias = true }
    private val sparkPaint = Paint().apply { isAntiAlias = true }

    private val srcRect = Rect()
    private val dstRect = RectF()
    private val badgeRect = RectF()
    private val noticeRect = RectF()
    private val sparkPath = Path()

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime
        val it = sparks.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.x += s.vx * deltaTime
            s.y += s.vy * deltaTime
            s.life -= deltaTime
            if (s.life <= 0f) it.remove()
        }
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        titlePaint.textSize = h * 0.072f
        canvas.drawText(title, w / 2f, h * 0.092f, titlePaint)

        // 오른쪽 위: 가진 루비
        rubyCountPaint.textSize = h * 0.046f
        val rubyText = "${GameProgress.rubies}"
        val gemR = h * 0.025f
        val textW = rubyCountPaint.measureText(rubyText)
        val gemCx = w - 30f - textW - gemR * 1.5f
        drawRubyIcon(canvas, gemCx, h * 0.072f, gemR, gemPaint)
        canvas.drawText(rubyText, gemCx + gemR * 1.4f, h * 0.089f, rubyCountPaint)

        if (book.all.isEmpty()) {
            // 아직 정해진 기술이 없다 — 껍데기만 남겨 둔다
            emptyPaint.textSize = h * 0.055f
            canvas.drawText("아직 기술이 없어요", w / 2f, h * 0.44f, emptyPaint)
            emptyPaint.textSize = h * 0.038f
            canvas.drawText("곧 새로운 기술이 생길 거예요", w / 2f, h * 0.52f, emptyPaint)
            drawBackButton(canvas, w, h)
            drawNotice(canvas, w, h)
            return
        }

        // 카드 3 x 2
        val padX = w * 0.06f
        val gap = w * 0.03f
        val cw = (w - padX * 2f - gap * (COLS - 1)) / COLS
        val ch = h * 0.310f
        val top = h * 0.140f

        for ((i, skill) in book.all.withIndex()) {
            val col = i % COLS
            val row = i / COLS
            val l = padX + col * (cw + gap)
            val t = top + row * (ch + h * 0.036f)
            cardRects[i].set(l, t, l + cw, t + ch)
            drawCard(canvas, i, skill, cardRects[i], h)
        }

        hintPaint.textSize = h * 0.030f
        canvas.drawText(hint, w / 2f, h * 0.856f, hintPaint)

        drawBackButton(canvas, w, h)
        drawSparks(canvas)
        drawNotice(canvas, w, h)
    }

    private fun drawBackButton(canvas: Canvas, w: Float, h: Float) {
        val bw = w * 0.17f
        val bh = h * 0.095f
        backRect.set(w / 2f - bw / 2f, h * 0.880f, w / 2f + bw / 2f, h * 0.880f + bh)
        canvas.drawRoundRect(backRect, 20f, 20f, backPaint)
        canvas.drawRoundRect(backRect, 20f, 20f, edgePaint)
        toggleTextPaint.textSize = bh * 0.42f
        canvas.drawText("돌아가기", backRect.centerX(), backRect.centerY() + bh * 0.15f, toggleTextPaint)
    }

    private fun drawCard(canvas: Canvas, index: Int, skill: CatSkills.Skill, rect: RectF, h: Float) {
        val learned = book.isLearned(skill.id)
        val fill = when {
            index == pressedIndex -> cardPressPaint
            learned -> cardLearnedPaint
            else -> cardPaint
        }
        canvas.drawRoundRect(rect, 16f, 16f, fill)
        canvas.drawRoundRect(rect, 16f, 16f, edgePaint)

        // 기술 그림
        val icon = icons()[skill.id]
        if (icon != null) {
            val availW = rect.width() - 24f
            val availH = rect.height() * 0.46f
            val scale = min(availW / icon.width, availH / icon.height)
            val bw = icon.width * scale
            val bh = icon.height * scale
            val cx = rect.centerX()
            val cy = rect.top + rect.height() * 0.30f
            srcRect.set(0, 0, icon.width, icon.height)
            dstRect.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
            canvas.drawBitmap(icon, srcRect, dstRect, bitmapPaint)
        }

        namePaint.textSize = h * 0.036f
        canvas.drawText(skill.name, rect.centerX(), rect.bottom - rect.height() * 0.29f, namePaint)
        descPaint.textSize = h * 0.026f
        canvas.drawText(skill.desc, rect.centerX(), rect.bottom - rect.height() * 0.19f, descPaint)

        if (learned) {
            // 배운 기술은 전투 중 버튼으로 쓴다 (켜고 끄지 않는다)
            toggleRects[index].setEmpty()
            hintPaint.textSize = h * 0.030f
            canvas.drawText(
                "전투에서 사용", rect.centerX(), rect.bottom - rect.height() * 0.075f, hintPaint
            )

            // 오른쪽 위 '배움' 배지
            badgeTextPaint.textSize = h * 0.024f
            val label = "배움"
            val pad = h * 0.012f
            val bwid = badgeTextPaint.measureText(label) + pad * 2f
            val bhei = h * 0.038f
            badgeRect.set(rect.right - bwid - 8f, rect.top + 8f, rect.right - 8f, rect.top + 8f + bhei)
            canvas.drawRoundRect(badgeRect, bhei / 2f, bhei / 2f, badgePaint)
            canvas.drawText(label, badgeRect.centerX(), badgeRect.centerY() + bhei * 0.30f, badgeTextPaint)
        } else {
            toggleRects[index].setEmpty()
            // 아직 못 배운 기술은 값을 보여 준다
            pricePaint.textSize = h * 0.032f
            val priceText = "${skill.price} 루비"
            val gemR = h * 0.017f
            val priceW = pricePaint.measureText(priceText)
            val blockW = gemR * 2f + h * 0.008f + priceW
            val startX = rect.centerX() - blockW / 2f
            val baseY = rect.bottom - rect.height() * 0.07f
            drawRubyIcon(canvas, startX + gemR, baseY - h * 0.011f, gemR, gemPaint)
            canvas.drawText(priceText, startX + gemR * 2f + h * 0.008f, baseY, pricePaint)
        }
    }

    private fun drawSparks(canvas: Canvas) {
        for (s in sparks) {
            sparkPaint.color = Color.argb(
                (255f * (s.life / 0.8f)).toInt().coerceIn(0, 255), 255, 225, 130
            )
            sparkPath.rewind()
            val step = Math.PI / 4
            var a = -Math.PI / 2.0
            for (k in 0 until 8) {
                val rad = if (k % 2 == 0) s.size else s.size * 0.35f
                val px = s.x + (cos(a) * rad).toFloat()
                val py = s.y + (sin(a) * rad).toFloat()
                if (k == 0) sparkPath.moveTo(px, py) else sparkPath.lineTo(px, py)
                a += step
            }
            sparkPath.close()
            canvas.drawPath(sparkPath, sparkPaint)
        }
    }

    private fun drawNotice(canvas: Canvas, w: Float, h: Float) {
        if (noticeTimer <= 0f) return
        val alpha = (255 * min(1f, noticeTimer / 0.4f)).toInt()
        val boxW = w * 0.46f
        val boxH = h * 0.105f
        noticeRect.set(w / 2f - boxW / 2f, h * 0.42f, w / 2f + boxW / 2f, h * 0.42f + boxH)
        noticePaint.alpha = alpha
        canvas.drawRoundRect(noticeRect, 18f, 18f, noticePaint)
        edgePaint.alpha = alpha
        canvas.drawRoundRect(noticeRect, 18f, 18f, edgePaint)
        edgePaint.alpha = 255
        noticeTextPaint.alpha = alpha
        noticeTextPaint.textSize = h * 0.038f
        canvas.drawText(noticeText, noticeRect.centerX(), noticeRect.centerY() + h * 0.013f, noticeTextPaint)
        noticeTextPaint.alpha = 255
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val h = gameView.screenHeight.toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressedIndex = cardAt(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                val was = pressedIndex
                pressedIndex = -1
                if (backRect.contains(event.x, event.y)) {
                    gameView.showLobby()
                    return true
                }
                val hit = cardAt(event.x, event.y)
                if (hit >= 0 && hit == was) onCardTapped(hit, h)
            }
            MotionEvent.ACTION_CANCEL -> pressedIndex = -1
        }
        return true
    }

    private fun cardAt(x: Float, y: Float): Int =
        cardRects.indexOfFirst { it.contains(x, y) }

    /** 못 배운 기술은 배우고, 배운 기술은 전투에서 쓰라고 알려 준다 */
    private fun onCardTapped(index: Int, h: Float) {
        val skill = book.all[index]
        if (book.isLearned(skill.id)) {
            // 이미 배운 기술 — 전투 중에 버튼으로 쓴다
            showNotice("전투 중 버튼으로 쓴다냥!")
            return
        }
        if (book.learn(skill.id)) {
            SoundManager.playSpecial()
            spawnSparks(cardRects[index].centerX(), cardRects[index].centerY(), h)
            showNotice("${skill.name} 배웠다냥!")
        } else {
            showNotice("루비가 부족하다냥!")
        }
    }

    private fun spawnSparks(cx: Float, cy: Float, h: Float) {
        repeat(10) {
            val a = (Math.random() * Math.PI * 2).toFloat()
            val sp = h * (0.10f + Math.random().toFloat() * 0.14f)
            sparks.add(
                Spark(cx, cy, cos(a) * sp, sin(a) * sp, 0.8f, h * (0.010f + Math.random().toFloat() * 0.008f))
            )
        }
    }

    private fun showNotice(text: String) {
        noticeText = text
        noticeTimer = 1.6f
    }

    override fun onBack(): Boolean {
        gameView.showLobby()
        return true
    }
}
