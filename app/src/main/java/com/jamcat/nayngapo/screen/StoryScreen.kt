package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.R
import com.jamcat.nayngapo.engine.GameView

/**
 * 스토리 — 로딩이 끝나면 대표님이 그린 그림 3장이 차례로 나온다 (2026-09-27, 원화 `Plan/story/story1~3.jpg`).
 *  1. 인간과 고양이가 어울려져 평화롭게 사는 도시가 있었다.
 *  2. 하지만 외계인들이 복제 냥이를 만들어 우리의 도시를 파괴시켰다.
 *  3. 우리를 철창에 가두고, 우리의 물건을 훔쳐갔다.
 * 화면을 누르면 다음 장, 마지막 장 다음은 타이틀. 오른쪽 맨 위 '건너뛰기'는 곧장 타이틀로.
 * (장면 전환 0.35초 페이드 · 한 장이 떠 있는 최소 시간 0.4초는 Claude 판단)
 */
class StoryScreen(private val gameView: GameView) : Screen {

    private val pages: List<Bitmap> = listOf(R.drawable.story_1, R.drawable.story_2, R.drawable.story_3)
        .map { BitmapFactory.decodeResource(gameView.resources, it) }

    private var page = 0
    /** 이 장이 뜬 뒤 흐른 시간 — 들어오자마자 잘못 눌러 넘어가지 않게 */
    private var shown = 0f
    /** 다음 장으로 넘어가는 중이면 0→1 */
    private var turning = -1f
    private var done = false

    private val bgPaint = Paint().apply { color = Color.rgb(28, 26, 30) }
    private val picPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val framePaint = Paint().apply { color = Color.rgb(250, 248, 242); isAntiAlias = true }
    private val shadowPaint = Paint().apply { color = Color.argb(120, 0, 0, 0); isAntiAlias = true }
    private val skipBg = Paint().apply { color = Color.argb(170, 255, 255, 255); isAntiAlias = true }
    private val skipText = Paint().apply {
        color = Color.rgb(40, 36, 34); textSize = 38f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val hintPaint = Paint().apply {
        color = Color.argb(200, 255, 255, 255); textSize = 30f; textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val dotOn = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val dotOff = Paint().apply { color = Color.argb(90, 255, 255, 255); isAntiAlias = true }
    private val skipRect = RectF()
    private val dst = RectF()
    private val tmp = RectF()

    override fun update(deltaTime: Float) {
        shown += deltaTime
        if (turning >= 0f) {
            turning += deltaTime / FADE
            if (turning >= 1f) {
                turning = -1f
                page++
                shown = 0f
                if (page >= pages.size) finish()
            }
        }
    }

    private fun finish() {
        if (done) return
        done = true
        gameView.showTitle()
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        val idx = page.coerceAtMost(pages.size - 1)
        val bmp = pages[idx]

        // 그림: 화면 높이에 맞춰 가운데 — 종이처럼 흰 테두리와 그림자
        val ph = h * 0.86f
        val pw = ph * bmp.width / bmp.height
        dst.set((w - pw) / 2f, (h - ph) / 2f - h * 0.02f, (w + pw) / 2f, (h + ph) / 2f - h * 0.02f)
        // 장이 바뀔 때: 앞 장은 어두워지며 사라지고 새 장이 밝아진다
        val alpha = when {
            turning >= 0f -> ((1f - turning) * 255).toInt()
            shown < FADE -> (shown / FADE * 255).toInt()
            else -> 255
        }.coerceIn(0, 255)
        picPaint.alpha = alpha; framePaint.alpha = alpha; shadowPaint.alpha = alpha * 120 / 255
        tmp.set(dst.left - 10f + 12f, dst.top - 10f + 14f, dst.right + 10f + 12f, dst.bottom + 10f + 14f)
        canvas.drawRoundRect(tmp, 10f, 10f, shadowPaint)
        tmp.set(dst.left - 10f, dst.top - 10f, dst.right + 10f, dst.bottom + 10f)
        canvas.drawRoundRect(tmp, 10f, 10f, framePaint)
        canvas.drawBitmap(bmp, null, dst, picPaint)

        // 몇 번째 장인지 점으로
        val dy = h - h * 0.035f
        for (i in pages.indices) {
            canvas.drawCircle(w / 2f + (i - (pages.size - 1) / 2f) * 34f, dy, 9f, if (i == idx) dotOn else dotOff)
        }
        // 누르면 넘어간다는 안내 (그림 오른쪽 아래 빈 곳)
        if (shown > 1.2f && turning < 0f) {
            hintPaint.alpha = (150 + 80 * kotlin.math.sin(shown * 3.0)).toInt().coerceIn(0, 255)
            canvas.drawText("화면을 누르면 다음 ▶", (dst.right + w) / 2f, dy, hintPaint)
        }

        // 오른쪽 맨 위: 건너뛰기
        val bw = 220f; val bh = 84f; val m = 28f
        skipRect.set(w - m - bw, m, w - m, m + bh)
        canvas.drawRoundRect(skipRect, bh / 2f, bh / 2f, skipBg)
        canvas.drawText("건너뛰기 ▶▶", skipRect.centerX(), skipRect.centerY() + 13f, skipText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        // 건너뛰기 버튼은 손가락이 조금 빗나가도 눌리게 넉넉히
        if (event.x >= skipRect.left - 30f && event.y <= skipRect.bottom + 30f) {
            finish()
            return true
        }
        if (turning < 0f && shown >= MIN_SHOWN) turning = 0f
        return true
    }

    /** 뒤로가기 = 건너뛰기 */
    override fun onBack(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val FADE = 0.35f
        private const val MIN_SHOWN = 0.4f
    }
}
