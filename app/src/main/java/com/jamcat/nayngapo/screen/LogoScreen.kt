package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameView

/**
 * 게임을 켜면 맨 처음: **잼캣 로고 화면** → 로딩 화면 → (처음 한 번만 스토리) → 시작 화면 (대표님 2026-10-09).
 * 하얀 바탕에 잼병을 안은 고양이 엠블럼(GPT 그림 jamcat_logo)이 톡 튀어나오고 그 아래 'JAMCAT' 글씨.
 * 2.4초 뒤(누르면 바로) 로딩 화면으로. 시간·움직임은 Claude 판단.
 */
class LogoScreen(private val gameView: GameView) : Screen {

    private var timer = 0f
    private var done = false
    private val logo: Bitmap? = gameView.resources.getIdentifier("jamcat_logo", "drawable", gameView.context.packageName)
        .takeIf { it != 0 }?.let { BitmapFactory.decodeResource(gameView.resources, it) }

    private val paint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val titlePaint = Paint().apply {
        color = Color.rgb(214, 46, 60); textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true; letterSpacing = 0.18f
    }
    private val subPaint = Paint().apply {
        color = Color.rgb(90, 80, 76); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val veil = Paint().apply { color = Color.WHITE }
    private val dst = RectF()

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (timer >= DURATION) finish()
    }

    private fun finish() {
        if (done) return
        done = true
        gameView.showLoading()
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        canvas.drawColor(Color.WHITE)
        // 엠블럼: 0.45초 동안 작게 시작해 살짝 넘쳤다 제자리 (톡 튀어나온다)
        val t = (timer / 0.45f).coerceIn(0f, 1f)
        val pop = 0.6f + 0.4f * t + 0.12f * kotlin.math.sin(t * Math.PI.toFloat())
        logo?.let { bmp ->
            val lh = h * 0.52f * pop
            val lw = bmp.width * lh / bmp.height
            dst.set(w / 2f - lw / 2f, h * 0.40f - lh / 2f, w / 2f + lw / 2f, h * 0.40f + lh / 2f)
            paint.alpha = (t * 255).toInt()
            canvas.drawBitmap(bmp, null, dst, paint)
        }
        // 글씨는 조금 늦게 떠오른다
        val ta = ((timer - 0.35f) / 0.4f).coerceIn(0f, 1f)
        titlePaint.textSize = h * 0.12f
        titlePaint.alpha = (ta * 255).toInt()
        canvas.drawTr("JAMCAT", w / 2f, h * 0.80f + (1f - ta) * 30f, titlePaint)
        subPaint.textSize = h * 0.04f
        subPaint.alpha = (ta * 255).toInt()
        canvas.drawTr("잼 캣", w / 2f, h * 0.88f + (1f - ta) * 30f, subPaint)
        // 끝날 때 하얗게 사라진다
        val out = ((timer - (DURATION - 0.35f)) / 0.35f).coerceIn(0f, 1f)
        if (out > 0f) { veil.alpha = (out * 255).toInt(); canvas.drawRect(0f, 0f, w, h, veil) }
    }

    /** 누르면 바로 로딩으로 */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && timer > 0.5f) finish()
        return true
    }

    companion object {
        private const val DURATION = 2.4f
    }
}
