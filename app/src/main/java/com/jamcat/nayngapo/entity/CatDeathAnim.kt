package com.jamcat.nayngapo.entity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.jamcat.nayngapo.engine.SpriteManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 냥이 사망 모션 (적·아군 공용).
 * 맞은 반대쪽으로 붕 떠올라 뒤집힌 채 떨어지고(X 눈, 머리 위 별), 바닥에 흙먼지가 일고, 서서히 사라진다.
 * 스프라이트를 새로 그리지 않고 마지막으로 그렸던 그림을 돌려서 쓴다.
 */
class CatDeathAnim {
    var timer = 0f
        private set

    val isActive: Boolean get() = timer > 0f

    fun start() { timer = DURATION }

    fun update(deltaTime: Float) {
        if (timer > 0f) timer -= deltaTime
    }

    /**
     * @param sprite     오른쪽을 보는 원본 스프라이트
     * @param facingRight 죽는 순간 바라보던 방향
     * @param cx         몸 가운데 x
     * @param footY      발끝 y (drawSprite 와 같은 기준)
     */
    /** scale: 묭묭 진화로 커진 냥이는 큰 채로 뒤집힌다 (2026-10-09) */
    fun draw(canvas: Canvas, sprite: Bitmap, facingRight: Boolean, cx: Float, footY: Float, scale: Float = 1f) {
        if (!isActive) return
        val t = (1f - timer / DURATION).coerceIn(0f, 1f)
        val flight = (t / FLIGHT_END).coerceAtMost(1f)          // 0~1: 떠올랐다 떨어지는 구간
        val lift = sin(flight * PI).toFloat() * LIFT_HEIGHT
        val dir = if (facingRight) 1f else -1f
        val drift = -dir * KNOCKBACK * flight                    // 맞은 반대쪽으로 밀려난다
        val angle = -dir * 180f * flight                         // 뒤로 넘어가 뒤집힌다
        val fade = ((t - FADE_START) / (1f - FADE_START)).coerceIn(0f, 1f)
        val alpha = (255 * (1f - fade)).toInt()

        val bmp = if (facingRight) sprite else SpriteManager.getFlipped(sprite)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val centerX = cx + drift
        val centerY = footY + 8f * scale - h * scale / 2f - lift

        spritePaint.alpha = alpha
        xPaint.alpha = alpha
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(angle)
        if (scale != 1f) canvas.scale(scale, scale)
        canvas.drawBitmap(bmp, -w / 2f, -h / 2f, spritePaint)

        // X 눈: 원안 얼굴의 작은 점 눈 자리(머리 위쪽) 위에 겹쳐 그린다
        val eyeY = -h / 2f + h * EYE_Y
        val arm = w * 0.055f
        for (fx in if (facingRight) EYES_RIGHT else EYES_LEFT) {
            val ex = -w / 2f + w * fx
            canvas.drawLine(ex - arm, eyeY - arm, ex + arm, eyeY + arm, xPaint)
            canvas.drawLine(ex - arm, eyeY + arm, ex + arm, eyeY - arm, xPaint)
        }
        canvas.restore()

        // 머리 위를 빙글빙글 도는 별
        starPaint.alpha = alpha
        starEdgePaint.alpha = alpha
        val orbitY = centerY - h * 0.55f
        for (i in 0 until 3) {
            val a = t * 9f + i * (2f * PI.toFloat() / 3f)
            val sx = centerX + cos(a) * w * 0.42f
            val sy = orbitY + sin(a) * h * 0.10f
            drawStar(canvas, sx, sy, 7f)
        }

        // 바닥에 떨어질 때 흙먼지
        if (t in DUST_START..DUST_END) {
            val k = (t - DUST_START) / (DUST_END - DUST_START)
            dustPaint.alpha = (150 * (1f - k)).toInt()
            for (i in -1..1) {
                val px = cx + drift + i * 34f * (0.4f + k)
                val py = footY - 4f - 12f * k
                canvas.drawCircle(px, py, 8f + 16f * k, dustPaint)
            }
        }
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        starPath.reset()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.45f
            val a = -PI.toFloat() / 2f + PI.toFloat() * i / 5f
            val px = cx + rr * cos(a)
            val py = cy + rr * sin(a)
            if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        canvas.drawPath(starPath, starPaint)
        canvas.drawPath(starPath, starEdgePaint)
    }

    companion object {
        const val DURATION = 1.0f
        private const val FLIGHT_END = 0.6f      // 이 시점(비율)에 바닥에 닿는다
        private const val FADE_START = 0.72f
        private const val DUST_START = 0.55f
        private const val DUST_END = 0.92f
        private const val LIFT_HEIGHT = 85f
        private const val KNOCKBACK = 55f

        // 오른쪽을 보는 스프라이트 기준 눈 위치 (가로 비율) — 왼쪽을 볼 때는 좌우 대칭
        private val EYES_RIGHT = floatArrayOf(0.60f, 0.74f)
        private val EYES_LEFT = floatArrayOf(0.40f, 0.26f)
        private const val EYE_Y = 0.30f

        private val spritePaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
        private val xPaint = Paint().apply {
            color = Color.rgb(25, 21, 19); style = Paint.Style.STROKE
            strokeWidth = 3.5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
        }
        private val starPaint = Paint().apply { color = Color.rgb(255, 220, 60); isAntiAlias = true }
        private val starEdgePaint = Paint().apply {
            color = Color.rgb(25, 21, 19); style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true
        }
        private val dustPaint = Paint().apply { color = Color.rgb(222, 210, 190); isAntiAlias = true }
        private val starPath = android.graphics.Path()
    }
}
