package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CatTeam
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import kotlin.math.min

/**
 * 설정 화면. 지금은 '데이터 리셋' 하나 — 루비·점수·물건·깬 스테이지·도감·팀 냥이·배운 기술을
 * 전부 지우고 처음 상태로 되돌린다 (밸런스 테스트용).
 * 실수로 눌러 날리지 않도록, 3초 안에 한 번 더 눌러야 실제로 지운다 (팀 화면의 '보내주기'와 같은 방식).
 */
class SettingsScreen(private val gameView: GameView) : Screen {

    private val infoPanel = RectF()
    private val resetRect = RectF()
    private val backRect = RectF()

    private var timer = 0f
    private var resetPressed = false

    /** '데이터 리셋'을 한 번 누른 뒤 확인을 기다리는 시각 (timer 기준). 3초 안에 다시 눌러야 한다 */
    private var confirmUntil = -1f
    /** 확인을 받기 시작한 시각 — 손가락이 튀어 곧바로 두 번 눌린 실수는 무시한다 */
    private var armedAt = -1f

    private var noticeText = ""
    private var noticeTimer = 0f
    private val noticeRect = RectF()

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
    private val linePaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val warnPaint = Paint().apply {
        color = Color.rgb(200, 50, 40); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val resetPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val resetPressPaint = Paint().apply { color = Color.rgb(255, 236, 160); isAntiAlias = true }
    private val resetArmedPaint = Paint().apply { color = Color.rgb(232, 66, 56); isAntiAlias = true }
    private val btnTextPaint = Paint().apply {
        color = Color.rgb(60, 44, 12); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val btnTextArmedPaint = Paint().apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val backPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val noticePaint = Paint().apply { color = Color.rgb(255, 250, 225); isAntiAlias = true }
    private val noticeTextPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        titlePaint.textSize = h * 0.075f
        canvas.drawText("설정", w / 2f, h * 0.095f, titlePaint)

        // 지금 저장된 것들을 한눈에 — 리셋하면 무엇이 사라지는지 보여 준다
        infoPanel.set(w * 0.10f, h * 0.16f, w * 0.90f, h * 0.50f)
        canvas.drawRoundRect(infoPanel, 18f, 18f, panelPaint)
        canvas.drawRoundRect(infoPanel, 18f, 18f, edgePaint)
        panelLabelPaint.textSize = h * 0.038f
        canvas.drawText("지금 저장된 데이터", infoPanel.centerX(), infoPanel.top + h * 0.065f, panelLabelPaint)

        linePaint.textSize = h * 0.040f
        val cleared = (1..GameProgress.PLAYABLE_STAGES).count { GameProgress.isCleared(it) }
        val lines = listOf(
            "루비 ${GameProgress.rubies}  ·  점수 ${String.format("%,d", GameProgress.bankScore)}",
            "깬 스테이지 ${cleared}개  ·  열린 스테이지 ${GameProgress.unlockedStage}",
            "팀 냥이 ${CatTeam.living.size}마리 (무덤·떠난 냥이 포함 ${CatTeam.cats.size})",
        )
        var y = infoPanel.top + h * 0.135f
        for (line in lines) {
            canvas.drawText(line, infoPanel.centerX(), y, linePaint)
            y += h * 0.062f
        }

        // 데이터 리셋 버튼 — 확인 대기 중이면 빨갛게 '정말 지우기?'
        val armed = confirmUntil > timer
        val bw = w * 0.40f
        val bh = h * 0.13f
        resetRect.set(w / 2f - bw / 2f, h * 0.56f, w / 2f + bw / 2f, h * 0.56f + bh)
        val fill = when {
            armed -> resetArmedPaint
            resetPressed -> resetPressPaint
            else -> resetPaint
        }
        canvas.drawRoundRect(resetRect, 20f, 20f, fill)
        canvas.drawRoundRect(resetRect, 20f, 20f, edgePaint)
        val textPaint = if (armed) btnTextArmedPaint else btnTextPaint
        textPaint.textSize = bh * 0.36f
        canvas.drawText(
            if (armed) "정말 지우기?" else "데이터 리셋",
            resetRect.centerX(), resetRect.centerY() + bh * 0.13f, textPaint
        )

        warnPaint.textSize = h * 0.032f
        canvas.drawText(
            "재화 · 진행한 스테이지 · 획득한 냥이 · 배운 기술이 전부 지워집니다 (되돌릴 수 없음)",
            w / 2f, resetRect.bottom + h * 0.055f, warnPaint
        )

        // 돌아가기 버튼
        val bbw = w * 0.18f
        val bbh = h * 0.10f
        backRect.set(w / 2f - bbw / 2f, h * 0.878f, w / 2f + bbw / 2f, h * 0.878f + bbh)
        canvas.drawRoundRect(backRect, 20f, 20f, backPaint)
        canvas.drawRoundRect(backRect, 20f, 20f, edgePaint)
        btnTextPaint.textSize = bbh * 0.42f
        canvas.drawText("돌아가기", backRect.centerX(), backRect.centerY() + bbh * 0.15f, btnTextPaint)

        drawNotice(canvas, w, h)
    }

    private fun drawNotice(canvas: Canvas, w: Float, h: Float) {
        if (noticeTimer <= 0f) return
        val alpha = (255 * min(1f, noticeTimer / 0.4f)).toInt()
        val boxW = w * 0.50f
        val boxH = h * 0.11f
        noticeRect.set(w / 2f - boxW / 2f, h * 0.40f, w / 2f + boxW / 2f, h * 0.40f + boxH)

        noticePaint.alpha = alpha
        canvas.drawRoundRect(noticeRect, 18f, 18f, noticePaint)
        edgePaint.alpha = alpha
        canvas.drawRoundRect(noticeRect, 18f, 18f, edgePaint)
        edgePaint.alpha = 255

        noticeTextPaint.alpha = alpha
        noticeTextPaint.textSize = h * 0.040f
        canvas.drawText(noticeText, noticeRect.centerX(), noticeRect.centerY() + h * 0.014f, noticeTextPaint)
        noticeTextPaint.alpha = 255
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> resetPressed = resetRect.contains(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                if (backRect.contains(event.x, event.y)) {
                    resetPressed = false
                    gameView.showLobby()
                    return true
                }
                if (resetPressed && resetRect.contains(event.x, event.y)) doReset()
                resetPressed = false
            }
            MotionEvent.ACTION_CANCEL -> resetPressed = false
        }
        return true
    }

    /** 데이터 리셋 — 3초 안에 한 번 더 눌러야 실제로 지운다 */
    private fun doReset() {
        if (confirmUntil <= timer) {
            confirmUntil = timer + 3f
            armedAt = timer
            showNotice("한 번 더 누르면 전부 지워요")
            return
        }
        // 손가락이 튀어 두 번 눌린 경우는 확인으로 세지 않는다
        if (timer - armedAt < 0.3f) return
        confirmUntil = -1f
        armedAt = -1f
        gameView.resetAllData()
        SoundManager.playCatHit()
        showNotice("처음 상태로 되돌렸다냥!")
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
