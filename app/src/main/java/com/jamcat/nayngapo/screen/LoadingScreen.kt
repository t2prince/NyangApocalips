package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SpriteManager

/**
 * 게임을 켰을 때 보이는 로딩 화면.
 * 하얀 바탕에 'LOADING' 글자, 그리고 거대한 일반 냥이가 시계방향으로 빙글빙글 돈다.
 */
class LoadingScreen(private val gameView: GameView) : Screen {

    private var timer = 0f
    private var done = false

    private val textPaint = Paint().apply {
        color = Color.rgb(40, 36, 34); textSize = 76f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        letterSpacing = 0.22f
    }
    private val dotPaint = Paint().apply { color = Color.rgb(40, 36, 34); isAntiAlias = true }
    private val matrix = Matrix()
    private val paint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (timer >= DURATION && !done) {
            done = true
            gameView.showStory()   // 로딩이 끝나면 스토리 그림 (대표님 2026-09-27)
        }
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        canvas.drawColor(Color.WHITE)

        // 거대한 냥이가 시계방향으로 회전
        val bmp = SpriteManager.catIdle[0]
        val scale = (h * 0.55f) / bmp.height
        val angle = timer * 180f            // 초당 반 바퀴, 시계방향
        matrix.reset()
        matrix.postTranslate(-bmp.width / 2f, -bmp.height / 2f)
        matrix.postScale(scale, scale)
        matrix.postRotate(angle)
        matrix.postTranslate(w / 2f, h * 0.46f)
        canvas.drawBitmap(bmp, matrix, paint)

        // LOADING (점이 하나씩 늘어난다)
        val dots = ((timer * 2.5f).toInt() % 4)
        canvas.drawText("LOADING", w / 2f, h * 0.88f, textPaint)
        for (i in 0 until dots) {
            canvas.drawCircle(w / 2f + 190f + i * 26f, h * 0.88f - 22f, 7f, dotPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = true   // 로딩 중에는 입력 무시

    private companion object {
        const val DURATION = 1.8f
    }
}
