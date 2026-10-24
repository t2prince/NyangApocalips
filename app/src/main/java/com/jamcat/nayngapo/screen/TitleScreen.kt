package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SpriteManager
import kotlin.math.sin

class TitleScreen(private val gameView: GameView) : Screen {
    private var timer = 0f

    // 시작 화면은 하얀 바탕
    private val bgPaint = Paint().apply { color = Color.WHITE }
    private val titlePaint = Paint().apply {
        color = Color.rgb(240, 160, 30); textSize = 80f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(5f, 2f, 2f, Color.argb(90, 90, 60, 20))
    }
    private val subtitlePaint = Paint().apply {
        color = Color.rgb(120, 110, 105); textSize = 32f; textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val tapPaint = Paint().apply {
        color = Color.rgb(60, 54, 50); textSize = 28f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val catBodyPaint = Paint().apply {
        color = Color.rgb(210, 170, 110); isAntiAlias = true
    }
    private val catEarPaint = Paint().apply {
        color = Color.rgb(230, 190, 130); isAntiAlias = true
    }
    private val eyePaint = Paint().apply { color = Color.RED; isAntiAlias = true }
    private val outlinePaint = Paint().apply {
        color = Color.rgb(240, 180, 60); style = Paint.Style.STROKE
        strokeWidth = 4f; isAntiAlias = true
    }

    override fun update(deltaTime: Float) {
        timer += deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        // Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Subtle background cats silhouettes
        val silhouettePaint = Paint().apply { color = Color.argb(38, 200, 160, 90); isAntiAlias = true }
        for (i in 0..5) {
            val cx = w * (i + 0.5f) / 6f
            val cy = h * 0.7f + sin((timer + i).toDouble() * 1.5).toFloat() * 15f
            canvas.drawCircle(cx, cy, 35f, silhouettePaint)
            canvas.drawCircle(cx, cy - 30f, 22f, silhouettePaint)
        }

        // Title
        canvas.drawTr("냥이 아포칼립스", w / 2f, h * 0.15f, titlePaint)
        canvas.drawTr("Cat Apocalypse", w / 2f, h * 0.15f + 45f, subtitlePaint)

        // 양 끝에서 춤추는 냥이 두 마리 (몸을 좌우로 흔들며 폴짝폴짝)
        drawDancingCat(canvas, w * 0.13f, h * 0.66f, timer * 7f)
        drawDancingCat(canvas, w * 0.87f, h * 0.66f, timer * 7f + Math.PI.toFloat())

        // Tap to start (blinking)
        val alpha = ((sin(timer.toDouble() * 3.0) * 0.5 + 0.5) * 255).toInt()
        tapPaint.alpha = alpha
        canvas.drawTr("TAP TO START", w / 2f, h * 0.88f, tapPaint)

        // Decorative border
        canvas.drawRect(10f, 10f, w - 10f, h - 10f, outlinePaint)
    }

    private val danceMatrix = Matrix()
    private val dancePaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }

    /** 제자리에서 몸을 좌우로 흔들며 폴짝이는 냥이 */
    private fun drawDancingCat(canvas: Canvas, cx: Float, cy: Float, phase: Float) {
        val frames = SpriteManager.catWalk
        val bmp = frames[((phase / 1.2f).toInt()) % frames.size]

        val hop = -kotlin.math.abs(sin(phase.toDouble())).toFloat() * 34f   // 폴짝
        val tilt = sin(phase.toDouble() * 0.5).toFloat() * 16f              // 좌우로 갸웃
        val scale = 2.0f

        danceMatrix.reset()
        danceMatrix.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
        danceMatrix.postScale(scale, scale)
        danceMatrix.postRotate(tilt)
        danceMatrix.postTranslate(cx, cy + hop)
        canvas.drawBitmap(bmp, danceMatrix, dancePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            gameView.showGameSelect()   // 시작 화면 다음은 게임 고르기 (2026-10-24)
        }
        return true
    }
}
