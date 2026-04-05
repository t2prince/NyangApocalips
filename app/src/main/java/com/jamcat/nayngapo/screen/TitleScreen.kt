package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameView
import kotlin.math.sin

class TitleScreen(private val gameView: GameView) : Screen {
    private var timer = 0f

    private val bgPaint = Paint().apply { color = Color.rgb(30, 20, 40) }
    private val titlePaint = Paint().apply {
        color = Color.rgb(255, 200, 50); textSize = 80f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(6f, 3f, 3f, Color.BLACK)
    }
    private val subtitlePaint = Paint().apply {
        color = Color.rgb(200, 200, 200); textSize = 32f; textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val tapPaint = Paint().apply {
        color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val catBodyPaint = Paint().apply {
        color = Color.rgb(210, 170, 110); isAntiAlias = true
    }
    private val catEarPaint = Paint().apply {
        color = Color.rgb(230, 190, 130); isAntiAlias = true
    }
    private val eyePaint = Paint().apply { color = Color.RED; isAntiAlias = true }
    private val outlinePaint = Paint().apply {
        color = Color.rgb(255, 200, 50); style = Paint.Style.STROKE
        strokeWidth = 3f; isAntiAlias = true
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
        val silhouettePaint = Paint().apply { color = Color.argb(30, 255, 200, 100); isAntiAlias = true }
        for (i in 0..5) {
            val cx = w * (i + 0.5f) / 6f
            val cy = h * 0.7f + sin((timer + i).toDouble() * 1.5).toFloat() * 15f
            canvas.drawCircle(cx, cy, 35f, silhouettePaint)
            canvas.drawCircle(cx, cy - 30f, 22f, silhouettePaint)
        }

        // Big cat in center
        val catCx = w / 2f
        val catCy = h * 0.42f
        val catScale = 2.5f
        // Body
        canvas.drawOval(
            RectF(catCx - 40f * catScale, catCy - 10f * catScale,
                  catCx + 40f * catScale, catCy + 35f * catScale), catBodyPaint
        )
        // Head
        canvas.drawCircle(catCx, catCy - 25f * catScale, 30f * catScale, catBodyPaint)
        // Ears
        val ear1 = Path().apply {
            moveTo(catCx - 25f * catScale, catCy - 45f * catScale)
            lineTo(catCx - 14f * catScale, catCy - 70f * catScale)
            lineTo(catCx - 3f * catScale, catCy - 45f * catScale); close()
        }
        val ear2 = Path().apply {
            moveTo(catCx + 3f * catScale, catCy - 45f * catScale)
            lineTo(catCx + 14f * catScale, catCy - 70f * catScale)
            lineTo(catCx + 25f * catScale, catCy - 45f * catScale); close()
        }
        canvas.drawPath(ear1, catEarPaint)
        canvas.drawPath(ear2, catEarPaint)
        // Evil eyes
        canvas.drawCircle(catCx - 12f * catScale, catCy - 30f * catScale, 6f * catScale, eyePaint)
        canvas.drawCircle(catCx + 12f * catScale, catCy - 30f * catScale, 6f * catScale, eyePaint)

        // Title
        canvas.drawText("냥이 아포칼립스", w / 2f, h * 0.15f, titlePaint)
        canvas.drawText("Cat Apocalypse", w / 2f, h * 0.15f + 45f, subtitlePaint)

        // Tap to start (blinking)
        val alpha = ((sin(timer.toDouble() * 3.0) * 0.5 + 0.5) * 255).toInt()
        tapPaint.alpha = alpha
        canvas.drawText("TAP TO START", w / 2f, h * 0.88f, tapPaint)

        // Decorative border
        canvas.drawRect(10f, 10f, w - 10f, h - 10f, outlinePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            gameView.showWorldMap()
        }
        return true
    }
}
