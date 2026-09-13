package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameView
import kotlin.math.sin

class GameOverScreen(
    private val gameView: GameView,
    private val finalScore: Int
) : Screen {
    private var timer = 0f

    private val bgPaint = Paint().apply { color = Color.rgb(20, 10, 10) }
    private val titlePaint = Paint().apply {
        color = Color.RED; textSize = 72f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }
    private val scorePaint = Paint().apply {
        color = Color.WHITE; textSize = 30f; textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val tapPaint = Paint().apply {
        color = Color.rgb(200, 200, 200); textSize = 24f; textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    override fun update(deltaTime: Float) {
        timer += deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawText("GAME OVER", w / 2f, h * 0.35f, titlePaint)
        canvas.drawText("Score: $finalScore", w / 2f, h * 0.50f, scorePaint)

        val alpha = ((sin(timer.toDouble() * 3.0) * 0.5 + 0.5) * 255).toInt()
        tapPaint.alpha = alpha
        canvas.drawText("TAP TO CONTINUE", w / 2f, h * 0.72f, tapPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && timer > 1f) {
            gameView.showLobby()   // 스테이지 클리어와 같이 로비로 돌아간다
        }
        return true
    }

    override fun onBack(): Boolean {
        gameView.showLobby()
        return true
    }
}
