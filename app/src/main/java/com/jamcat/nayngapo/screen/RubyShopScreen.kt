package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.ui.drawRubyIcon
import kotlin.math.min
import kotlin.random.Random

/**
 * 루비 상점. 그동안 쌓아온 점수를 루비로 바꿔 준다 (500점 = 1루비).
 * 도감·고양이 상점과 같은 도화지 손그림 톤.
 */
class RubyShopScreen(private val gameView: GameView, private val fromShop: Boolean = false) : Screen {

    /** 환전 버튼 한 개 */
    private class ExchangeButton(val label: String, val amount: () -> Int)

    private val buttons = listOf(
        ExchangeButton("1루비 환전") { 1 },
        ExchangeButton("10루비 환전") { 10 },
        ExchangeButton("전부 환전") { GameProgress.maxExchangeable() },
    )
    private val buttonRects = buttons.map { RectF() }

    private val scorePanel = RectF()
    private val debtPaint = Paint().apply {
        color = Color.rgb(214, 70, 70); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val rubyPanel = RectF()
    private val backRect = RectF()

    private var pressedIndex = -1

    private var noticeText = ""
    private var noticeTimer = 0f

    /** 환전 성공 때 루비 칸에서 위로 튀어 오르는 작은 보석들 */
    private class Sparkle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val r: Float)

    private val sparkles = mutableListOf<Sparkle>()
    private val random = Random(System.nanoTime())

    private val bgPaint = Paint().apply { color = Color.rgb(252, 249, 240) }
    private val titlePaint = Paint().apply {
        color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val panelPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val edgePaint = Paint().apply {
        color = Color.rgb(30, 28, 26); style = Paint.Style.STROKE
        strokeWidth = 4.5f; isAntiAlias = true
    }
    private val panelLabelPaint = Paint().apply {
        color = Color.rgb(120, 112, 100); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val bigNumPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val ratePaint = Paint().apply {
        color = Color.rgb(140, 132, 122); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val btnPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val btnPressPaint = Paint().apply { color = Color.rgb(255, 236, 160); isAntiAlias = true }
    private val btnOffPaint = Paint().apply { color = Color.rgb(214, 210, 200); isAntiAlias = true }
    private val btnTextPaint = Paint().apply {
        color = Color.rgb(60, 44, 12); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val btnTextOffPaint = Paint().apply {
        color = Color.rgb(148, 143, 134); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val backPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val noticePaint = Paint().apply { color = Color.rgb(255, 250, 225); isAntiAlias = true }
    private val noticeTextPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val gemPaint = Paint().apply { isAntiAlias = true }

    private val noticeRect = RectF()

    override fun update(deltaTime: Float) {
        if (noticeTimer > 0f) noticeTimer -= deltaTime

        val iter = sparkles.iterator()
        while (iter.hasNext()) {
            val s = iter.next()
            s.x += s.vx * deltaTime
            s.y += s.vy * deltaTime
            s.vy += 240f * deltaTime      // 살짝 떨어지며 사라진다
            s.life -= deltaTime
            if (s.life <= 0f) iter.remove()
        }
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        titlePaint.textSize = h * 0.075f
        canvas.drawTr("루비 상점", w / 2f, h * 0.095f, titlePaint)

        // 정보 칸 둘: 왼쪽 쌓아온 점수 / 오른쪽 보유 루비
        val gap = w * 0.03f
        val panelW = (w * 0.94f - gap) / 2f
        val panelH = h * 0.26f
        val left = w * 0.03f
        val top = h * 0.165f
        scorePanel.set(left, top, left + panelW, top + panelH)
        rubyPanel.set(left + panelW + gap, top, left + panelW * 2f + gap, top + panelH)

        panelLabelPaint.textSize = h * 0.040f
        bigNumPaint.textSize = h * 0.085f

        canvas.drawRoundRect(scorePanel, 18f, 18f, panelPaint)
        canvas.drawRoundRect(scorePanel, 18f, 18f, edgePaint)
        canvas.drawTr("쌓아온 점수", scorePanel.centerX(), scorePanel.top + panelH * 0.28f, panelLabelPaint)
        canvas.drawTr(
            String.format("%,d", GameProgress.bankScore),
            scorePanel.centerX(), scorePanel.top + panelH * 0.72f, bigNumPaint
        )

        canvas.drawRoundRect(rubyPanel, 18f, 18f, panelPaint)
        canvas.drawRoundRect(rubyPanel, 18f, 18f, edgePaint)
        canvas.drawTr("보유 루비", rubyPanel.centerX(), rubyPanel.top + panelH * 0.28f, panelLabelPaint)

        // 보석 모양 + 개수를 한 덩어리로 가운데 맞춤
        val rubyText = "${GameProgress.rubies}"
        val gemR = h * 0.038f
        val numW = bigNumPaint.measureTr(rubyText)
        val blockW = gemR * 2f + h * 0.014f + numW
        val blockLeft = rubyPanel.centerX() - blockW / 2f
        val numBase = rubyPanel.top + panelH * 0.72f
        drawRubyIcon(canvas, blockLeft + gemR, numBase - h * 0.026f, gemR, gemPaint)
        bigNumPaint.textAlign = Paint.Align.LEFT
        canvas.drawTr(rubyText, blockLeft + gemR * 2f + h * 0.014f, numBase, bigNumPaint)
        bigNumPaint.textAlign = Paint.Align.CENTER
        // 빚 칸: 도둑냥이한테 뺏긴 게 지갑보다 많으면 여기 쌓인다 (새 루비로 먼저 갚는다)
        if (GameProgress.rubyDebt > 0) {
            debtPaint.textSize = h * 0.034f
            canvas.drawTr("빚 ${GameProgress.rubyDebt} 루비", rubyPanel.centerX(), rubyPanel.bottom + h * 0.045f, debtPaint)
        }

        ratePaint.textSize = h * 0.040f
        canvas.drawTr("${GameProgress.SCORE_PER_RUBY}점 = 1루비", w / 2f, h * 0.505f, ratePaint)

        // 환전 버튼 세 개
        val btnGap = w * 0.03f
        val btnW = (w * 0.90f - btnGap * 2f) / 3f
        val btnH = h * 0.14f
        val btnTop = h * 0.56f
        btnTextPaint.textSize = btnH * 0.34f
        btnTextOffPaint.textSize = btnH * 0.34f
        for ((i, btn) in buttons.withIndex()) {
            val l = w * 0.05f + i * (btnW + btnGap)
            buttonRects[i].set(l, btnTop, l + btnW, btnTop + btnH)
            val amount = btn.amount()
            val enabled = amount > 0 && GameProgress.bankScore >= amount * GameProgress.SCORE_PER_RUBY
            val fill = when {
                !enabled -> btnOffPaint
                i == pressedIndex -> btnPressPaint
                else -> btnPaint
            }
            canvas.drawRoundRect(buttonRects[i], 20f, 20f, fill)
            canvas.drawRoundRect(buttonRects[i], 20f, 20f, edgePaint)
            canvas.drawTr(
                btn.label, buttonRects[i].centerX(),
                buttonRects[i].centerY() + btnH * 0.12f,
                if (enabled) btnTextPaint else btnTextOffPaint
            )
        }

        drawSparkles(canvas)

        // 돌아가기 버튼
        val bw = w * 0.18f
        val bh = h * 0.10f
        backRect.set(w / 2f - bw / 2f, h * 0.878f, w / 2f + bw / 2f, h * 0.878f + bh)
        canvas.drawRoundRect(backRect, 20f, 20f, backPaint)
        canvas.drawRoundRect(backRect, 20f, 20f, edgePaint)
        btnTextPaint.textSize = bh * 0.42f
        canvas.drawTr("돌아가기", backRect.centerX(), backRect.centerY() + bh * 0.15f, btnTextPaint)

        drawNotice(canvas, w, h)
    }

    private fun drawSparkles(canvas: Canvas) {
        for (s in sparkles) {
            val alpha = (255 * min(1f, s.life / 0.4f)).toInt()
            drawRubyIcon(canvas, s.x, s.y, s.r, gemPaint, alpha)
        }
    }

    private fun drawNotice(canvas: Canvas, w: Float, h: Float) {
        if (noticeTimer <= 0f) return
        val alpha = (255 * min(1f, noticeTimer / 0.4f)).toInt()
        val boxW = w * 0.44f
        val boxH = h * 0.11f
        noticeRect.set(w / 2f - boxW / 2f, h * 0.40f, w / 2f + boxW / 2f, h * 0.40f + boxH)

        noticePaint.alpha = alpha
        canvas.drawRoundRect(noticeRect, 18f, 18f, noticePaint)
        edgePaint.alpha = alpha
        canvas.drawRoundRect(noticeRect, 18f, 18f, edgePaint)
        edgePaint.alpha = 255

        noticeTextPaint.alpha = alpha
        noticeTextPaint.textSize = h * 0.042f
        canvas.drawTr(
            noticeText, noticeRect.centerX(), noticeRect.centerY() + h * 0.015f, noticeTextPaint
        )
        noticeTextPaint.alpha = 255
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressedIndex = buttonRects.indexOfFirst { it.contains(event.x, event.y) }
            MotionEvent.ACTION_UP -> {
                if (backRect.contains(event.x, event.y)) {
                    pressedIndex = -1
                    if (fromShop) gameView.showShop() else gameView.showLobby()
                    return true
                }
                val hit = buttonRects.indexOfFirst { it.contains(event.x, event.y) }
                if (hit >= 0 && hit == pressedIndex) exchange(hit)
                pressedIndex = -1
            }
            MotionEvent.ACTION_CANCEL -> pressedIndex = -1
        }
        return true
    }

    private fun exchange(index: Int) {
        val amount = buttons[index].amount()
        if (amount > 0 && GameProgress.exchangeRubies(amount)) {
            showNotice("루비 ${amount}개 획득!")
            spawnSparkles()
            SoundManager.playCatHit()
        } else {
            showNotice("점수가 부족하다냥!")
        }
    }

    /** 루비 칸에서 보석 여덟 개가 위로 흩어진다 */
    private fun spawnSparkles() {
        if (rubyPanel.isEmpty) return
        val r = rubyPanel.height() * 0.10f
        repeat(8) {
            sparkles.add(
                Sparkle(
                    x = rubyPanel.centerX() + (random.nextFloat() - 0.5f) * rubyPanel.width() * 0.6f,
                    y = rubyPanel.centerY(),
                    vx = (random.nextFloat() - 0.5f) * rubyPanel.width() * 0.7f,
                    vy = -rubyPanel.height() * (1.1f + random.nextFloat() * 0.6f),
                    life = 0.8f,
                    r = r * (0.7f + random.nextFloat() * 0.5f)
                )
            )
        }
    }

    private fun showNotice(text: String) {
        noticeText = text
        noticeTimer = 1.6f
    }

    override fun onBack(): Boolean {
        if (fromShop) gameView.showShop() else gameView.showLobby()
        return true
    }
}
