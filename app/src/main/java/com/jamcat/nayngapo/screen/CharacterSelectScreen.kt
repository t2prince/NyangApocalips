package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.Heroes
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.ui.drawHero
import kotlin.math.min
import kotlin.math.sin

/**
 * 캐릭터 변경 — 네 명의 인간이 나란히 서 있고, 누르면 그 인간으로 바꾼다.
 * 그림은 모두 같은 졸라맨 시트를 쓰고 선 색과 머리 장식만 다르다 (ui/HeroDraw.kt).
 */
class CharacterSelectScreen(private val gameView: GameView) : Screen {

    private var timer = 0f

    /** 방금 고른 인간이 콩 뛰는 연출에 남은 시간 */
    private var hopT = 0f
    private var hopId: String? = null

    private val backRect = RectF()
    private val cardRects = Heroes.all.map { RectF() }

    private val bgPaint = Paint().apply { color = Color.rgb(252, 249, 240) }
    private val titlePaint = Paint().apply {
        color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val highlightPaint = Paint().apply { color = Color.rgb(255, 244, 200); isAntiAlias = true }
    private val edgePaint = Paint().apply {
        color = Color.rgb(30, 28, 26); style = Paint.Style.STROKE
        strokeWidth = 4.5f; isAntiAlias = true
    }
    private val selectEdgePaint = Paint().apply {
        color = Color.rgb(240, 170, 20); style = Paint.Style.STROKE
        strokeWidth = 8f; isAntiAlias = true
    }
    private val groundPaint = Paint().apply {
        color = Color.rgb(196, 189, 176); strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val namePaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val descPaint = Paint().apply {
        color = Color.rgb(140, 132, 122); textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val pipLabelPaint = Paint().apply {
        color = Color.rgb(120, 112, 100); textAlign = Paint.Align.RIGHT; isAntiAlias = true
    }
    private val pipFullPaint = Paint().apply { isAntiAlias = true }
    private val pipEmptyPaint = Paint().apply {
        color = Color.rgb(214, 208, 198); isAntiAlias = true
    }
    private val tagPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val tagTextPaint = Paint().apply {
        color = Color.rgb(60, 44, 12); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val backPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val backTextPaint = Paint().apply {
        color = Color.rgb(60, 44, 12); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }

    private val heroDst = RectF()
    private val tagRect = RectF()

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (hopT > 0f) hopT -= deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        titlePaint.textSize = h * 0.070f
        canvas.drawText("캐릭터 변경", w / 2f, h * 0.090f, titlePaint)

        val groundY = h * 0.700f
        val selectedId = GameProgress.selectedHero

        // 네 칸을 고르게 나눈다
        val padX = w * 0.02f
        val gap = w * 0.014f
        val cw = (w - padX * 2f - gap * (Heroes.all.size - 1)) / Heroes.all.size

        for ((i, hero) in Heroes.all.withIndex()) {
            val l = padX + i * (cw + gap)
            cardRects[i].set(l, h * 0.135f, l + cw, h * 0.870f)
            val chosen = hero.id == selectedId

            if (chosen) {
                canvas.drawRoundRect(cardRects[i], 20f, 20f, highlightPaint)
                canvas.drawRoundRect(cardRects[i], 20f, 20f, selectEdgePaint)
            }

            // 바닥선
            val cx = cardRects[i].centerX()
            canvas.drawLine(l + cw * 0.10f, groundY, l + cw * 0.90f, groundY, groundPaint)

            // 인간: 6프레임/초, 칸마다 조금씩 어긋나게
            val frames = SpriteManager.playerIdle
            val bmp = frames[((timer * 6f).toInt() + i) % frames.size]
            val figH = h * 0.55f
            val scale = min(cw * 0.72f / bmp.width, figH / bmp.height)
            // 키가 작은 인간은 발을 바닥선에 붙인 채 작게 선다
            val bw = bmp.width * scale * hero.scale
            val bh = bmp.height * scale * hero.scale
            // 방금 고른 인간은 콩 뛴다
            val hop =
                if (hopId == hero.id && hopT > 0f)
                    -sin((1f - hopT / HOP_TIME).coerceIn(0f, 1f) * Math.PI.toFloat()) * h * 0.035f
                else 0f
            heroDst.set(cx - bw / 2f, groundY - bh + hop, cx + bw / 2f, groundY + hop)
            drawHero(canvas, bmp, heroDst, hero, true, timer)

            // 이름 · 한 줄 설명
            namePaint.textSize = h * 0.040f
            canvas.drawText(hero.name, cx, groundY + h * 0.050f, namePaint)
            descPaint.textSize = h * 0.028f
            canvas.drawText(hero.desc, cx, groundY + h * 0.085f, descPaint)

            // 능력 점 세 줄
            pipLabelPaint.textSize = h * 0.024f
            pipFullPaint.color = hero.tint
            drawPips(canvas, cx, groundY + h * 0.112f, cw, h, "체력", hero.hpPips)
            drawPips(canvas, cx, groundY + h * 0.138f, cw, h, "공격", hero.atkPips)
            drawPips(canvas, cx, groundY + h * 0.164f, cw, h, "속도", hero.speedPips)

            if (chosen) {
                val tw = cw * 0.40f
                val th = h * 0.048f
                tagRect.set(cx - tw / 2f, cardRects[i].top + h * 0.012f, cx + tw / 2f,
                    cardRects[i].top + h * 0.012f + th)
                canvas.drawRoundRect(tagRect, th / 2f, th / 2f, tagPaint)
                canvas.drawRoundRect(tagRect, th / 2f, th / 2f, edgePaint)
                tagTextPaint.textSize = th * 0.52f
                canvas.drawText("선택됨", cx, tagRect.centerY() + th * 0.19f, tagTextPaint)
            }
        }

        // 돌아가기
        val bw2 = w * 0.17f
        val bh2 = h * 0.095f
        backRect.set(w / 2f - bw2 / 2f, h * 0.882f, w / 2f + bw2 / 2f, h * 0.882f + bh2)
        canvas.drawRoundRect(backRect, 20f, 20f, backPaint)
        canvas.drawRoundRect(backRect, 20f, 20f, edgePaint)
        backTextPaint.textSize = bh2 * 0.42f
        canvas.drawText("돌아가기", backRect.centerX(), backRect.centerY() + bh2 * 0.15f, backTextPaint)
    }

    /** "공격 ●●○" 처럼 이름 뒤에 점 세 개 */
    private fun drawPips(
        canvas: Canvas, cx: Float, baseY: Float, cw: Float, h: Float, label: String, filled: Int
    ) {
        val dotR = h * 0.009f
        val step = dotR * 3f
        val dotsW = step * 3f
        val labelRight = cx - dotsW / 2f - h * 0.010f
        canvas.drawText(label, labelRight, baseY, pipLabelPaint)
        for (k in 0 until 3) {
            val dx = cx - dotsW / 2f + step * (k + 0.5f)
            canvas.drawCircle(dx, baseY - dotR * 0.9f, dotR, if (k < filled) pipFullPaint else pipEmptyPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true

        if (backRect.contains(event.x, event.y)) {
            gameView.showLobby()
            return true
        }
        for ((i, hero) in Heroes.all.withIndex()) {
            if (!cardRects[i].contains(event.x, event.y)) continue
            if (hero.id != GameProgress.selectedHero) {
                GameProgress.selectHero(hero.id)
                SoundManager.playCatHit()
                hopId = hero.id
                hopT = HOP_TIME
            }
            break
        }
        return true
    }

    override fun onBack(): Boolean {
        gameView.showLobby()
        return true
    }

    private companion object {
        /** 고른 인간이 콩 뛰는 시간 */
        const val HOP_TIME = 0.4f
    }
}
