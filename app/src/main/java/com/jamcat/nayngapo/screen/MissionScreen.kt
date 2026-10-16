package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.Missions
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.ui.drawRubyIcon

/**
 * 미션 화면 — 두 줄 카드 목록을 위아래로 넘겨 보고, 다 한 미션은 [받기] 를 눌러 루비를 받는다.
 * 도감·상점과 같은 도화지 톤.
 */
class MissionScreen(private val gameView: GameView) : Screen {
    private val bgPaint = Paint().apply { color = Color.rgb(252, 249, 240) }
    private val titlePaint = Paint().apply {
        color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val cardPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val cardDonePaint = Paint().apply { color = Color.rgb(236, 248, 232); isAntiAlias = true }
    private val cardClaimedPaint = Paint().apply { color = Color.rgb(238, 235, 228); isAntiAlias = true }
    private val edgePaint = Paint().apply { color = Color.rgb(30, 28, 26); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
    private val namePaint = Paint().apply { color = Color.rgb(40, 36, 32); typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val descPaint = Paint().apply { color = Color.rgb(120, 112, 100); isAntiAlias = true }
    private val progPaint = Paint().apply { color = Color.rgb(90, 84, 76); typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val trackPaint = Paint().apply { color = Color.rgb(225, 220, 210); isAntiAlias = true }
    private val fillPaint = Paint().apply { color = Color.rgb(120, 200, 110); isAntiAlias = true }
    private val btnPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val btnOffPaint = Paint().apply { color = Color.rgb(214, 210, 200); isAntiAlias = true }
    private val btnTextPaint = Paint().apply { color = Color.rgb(60, 44, 12); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val btnTextOffPaint = Paint().apply { color = Color.rgb(148, 143, 134); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val rewardPaint = Paint().apply { color = Color.rgb(200, 40, 70); typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val rubyTextPaint = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.RIGHT; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val noticePaint = Paint().apply { color = Color.rgb(255, 250, 225); isAntiAlias = true }
    private val noticeTextPaint = Paint().apply { color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val gemPaint = Paint().apply { isAntiAlias = true }

    private val cardRects = Missions.all.map { RectF() }
    private val btnRects = Missions.all.map { RectF() }
    private val backRect = RectF()
    private val noticeRect = RectF()
    private var scroll = 0f
    private var maxScroll = 0f
    private var downY = 0f
    private var downScroll = 0f
    private var dragged = false
    private var noticeText = ""
    private var noticeTimer = 0f

    override fun update(deltaTime: Float) { if (noticeTimer > 0f) noticeTimer -= deltaTime }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        titlePaint.textSize = h * 0.075f
        canvas.drawTr("미션", w / 2f, h * 0.095f, titlePaint)
        // 오른쪽 위: 보유 루비
        rubyTextPaint.textSize = h * 0.05f
        drawRubyIcon(canvas, w - w * 0.03f - rubyTextPaint.measureTr("${GameProgress.rubies}") - h * 0.045f, h * 0.075f, h * 0.026f, gemPaint)
        canvas.drawTr("${GameProgress.rubies}", w - w * 0.03f, h * 0.093f, rubyTextPaint)

        // 카드 격자 2열, 위아래 스크롤
        val cols = 2
        val padX = w * 0.04f; val gap = w * 0.02f
        val cw = (w - padX * 2f - gap) / cols
        val ch = h * 0.15f; val rowGap = h * 0.025f
        val listTop = h * 0.14f; val listBottom = h * 0.84f
        val rows = (Missions.all.size + cols - 1) / cols
        maxScroll = (rows * (ch + rowGap) - rowGap - (listBottom - listTop)).coerceAtLeast(0f)
        scroll = scroll.coerceIn(0f, maxScroll)

        if (Missions.all.isEmpty()) {
            descPaint.textSize = h * 0.04f; descPaint.textAlign = Paint.Align.CENTER
            canvas.drawTr("아직 미션이 없어요", w / 2f, h * 0.5f, descPaint)
            descPaint.textAlign = Paint.Align.LEFT
        }
        canvas.save(); canvas.clipRect(0f, listTop, w, listBottom)
        namePaint.textSize = h * 0.038f; descPaint.textSize = h * 0.028f; progPaint.textSize = h * 0.028f
        btnTextPaint.textSize = h * 0.032f; btnTextOffPaint.textSize = h * 0.032f; rewardPaint.textSize = h * 0.03f
        for ((i, m) in Missions.all.withIndex()) {
            val l = padX + (i % cols) * (cw + gap)
            val t = listTop + (i / cols) * (ch + rowGap) - scroll
            val r = cardRects[i]; r.set(l, t, l + cw, t + ch)
            if (r.bottom < listTop || r.top > listBottom) { btnRects[i].setEmpty(); continue }
            canvas.drawRoundRect(r, 16f, 16f, when { m.claimed -> cardClaimedPaint; m.done -> cardDonePaint; else -> cardPaint })
            canvas.drawRoundRect(r, 16f, 16f, edgePaint)
            canvas.drawTr(m.title, l + cw * 0.04f, t + ch * 0.32f, namePaint)
            canvas.drawTr(m.desc, l + cw * 0.04f, t + ch * 0.55f, descPaint)
            // 진행 막대
            val bx0 = l + cw * 0.04f; val bx1 = l + cw * 0.62f; val by = t + ch * 0.74f
            canvas.drawRoundRect(bx0, by, bx1, by + ch * 0.1f, 6f, 6f, trackPaint)
            val frac = if (m.goal == 0) 1f else (m.current / m.goal.toFloat()).coerceIn(0f, 1f)
            if (frac > 0f) canvas.drawRoundRect(bx0, by, bx0 + (bx1 - bx0) * frac, by + ch * 0.1f, 6f, 6f, fillPaint)
            canvas.drawTr("${m.current} / ${m.goal}", bx0, t + ch * 0.96f, progPaint)
            // 보상 + 버튼
            val b = btnRects[i]; b.set(l + cw * 0.68f, t + ch * 0.52f, l + cw * 0.96f, t + ch * 0.9f)
            canvas.drawRoundRect(b, 14f, 14f, if (m.claimable) btnPaint else btnOffPaint)
            canvas.drawRoundRect(b, 14f, 14f, edgePaint)
            val label = when { m.claimed -> "받음"; m.done -> "받기"; else -> "진행 중" }
            canvas.drawTr(label, b.centerX(), b.centerY() + h * 0.012f, if (m.claimable) btnTextPaint else btnTextOffPaint)
            drawRubyIcon(canvas, l + cw * 0.72f, t + ch * 0.28f, h * 0.018f, gemPaint)
            canvas.drawTr("${m.reward}", l + cw * 0.72f + h * 0.026f, t + ch * 0.35f, rewardPaint)
        }
        canvas.restore()

        // 돌아가기
        val bw = w * 0.18f; val bh = h * 0.10f
        backRect.set(w / 2f - bw / 2f, h * 0.875f, w / 2f + bw / 2f, h * 0.875f + bh)
        canvas.drawRoundRect(backRect, 20f, 20f, btnPaint); canvas.drawRoundRect(backRect, 20f, 20f, edgePaint)
        btnTextPaint.textSize = bh * 0.42f
        canvas.drawTr("돌아가기", backRect.centerX(), backRect.centerY() + bh * 0.15f, btnTextPaint)

        if (noticeTimer > 0f) {
            val alpha = (255f * (noticeTimer / 0.4f).coerceIn(0f, 1f)).toInt()
            noticeRect.set(w * 0.3f, h * 0.42f, w * 0.7f, h * 0.56f)
            noticePaint.alpha = alpha; edgePaint.alpha = alpha; noticeTextPaint.alpha = alpha
            canvas.drawRoundRect(noticeRect, 18f, 18f, noticePaint); canvas.drawRoundRect(noticeRect, 18f, 18f, edgePaint)
            noticeTextPaint.textSize = h * 0.042f
            canvas.drawTr(noticeText, noticeRect.centerX(), noticeRect.centerY() + h * 0.015f, noticeTextPaint)
            edgePaint.alpha = 255; noticeTextPaint.alpha = 255; noticePaint.alpha = 255
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = event.y; downScroll = scroll; dragged = false }
            MotionEvent.ACTION_MOVE -> {
                val dy = downY - event.y
                if (Math.abs(dy) > 12f) dragged = true
                if (dragged) scroll = (downScroll + dy).coerceIn(0f, maxScroll)
            }
            MotionEvent.ACTION_UP -> {
                if (dragged) return true
                if (backRect.contains(event.x, event.y)) { gameView.showLobby(); return true }
                val hit = btnRects.indexOfFirst { !it.isEmpty && it.contains(event.x, event.y) }
                if (hit >= 0) {
                    val m = Missions.all[hit]
                    val got = Missions.claim(m)
                    if (got > 0) { showNotice("루비 ${got}개 획득!"); SoundManager.playCatHit() }
                    else showNotice(if (m.claimed) "이미 받았다냥!" else "아직 다 못 했다냥!")
                }
            }
        }
        return true
    }

    private fun showNotice(text: String) { noticeText = text; noticeTimer = 1.6f }

    override fun onBack(): Boolean { gameView.showLobby(); return true }
}
