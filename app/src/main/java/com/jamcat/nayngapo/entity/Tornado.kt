package com.jamcat.nayngapo.entity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * 고양이 기술 '토네이도' — 출전한 냥이가 일으킨다.
 * 냥이 발밑에서 작게 솟아 제 크기로 커진 뒤, 냥이가 보는 쪽 화면 끝까지 쓸고 가며 흩어진다.
 * 지나간 자리의 적은 한 번씩만 휘말린다.
 */
class Tornado(
    private val startX: Float,
    private val endX: Float,
    private val groundY: Float,
    /** 휘말린 적이 받는 피해 (기술의 위력) */
    val power: Int
) {
    companion object {
        /** 화면을 한 번 쓸고 지나가는 데 걸리는 시간 */
        const val DURATION = 1.4f

        /** 이 좌우 폭 안에 든 적이 휘말린다 */
        const val REACH = 75f

        private const val TOP_WIDTH = 150f
        private const val BASE_WIDTH = 40f
        private const val HEIGHT = 260f
        private const val RINGS = 6

        /** 냥이 발밑에서 30% 크기로 솟아 제 크기가 되기까지 */
        private const val GROW_TIME = 0.2f

        /** 끝에서 서서히 흩어지는 시간 */
        private const val FADE_TIME = 0.25f
    }

    private var t = 0f

    /** 지금 회오리의 한가운데 x */
    var x = startX
        private set

    val isDone: Boolean get() = t >= DURATION

    /** 이미 휘말린 적 — 한 번만 때린다 */
    val hit = HashSet<Enemy>()

    /** 솟아오르는 동안 커진다 */
    private val scale: Float
        get() = if (t < GROW_TIME) 0.3f + 0.7f * (t / GROW_TIME) else 1f

    /** 끝에서 서서히 흩어진다 */
    private val alpha: Int
        get() = if (t > DURATION - FADE_TIME)
            (255f * ((DURATION - t) / FADE_TIME)).toInt().coerceIn(0, 255) else 255

    fun update(deltaTime: Float) {
        t += deltaTime
        // 다 커질 때까지는 냥이 자리에 머물다가, 그다음 화면 끝까지 쓸고 간다
        x = if (t < GROW_TIME) startX
        else startX + (endX - startX) * ((t - GROW_TIME) / (DURATION - GROW_TIME)).coerceIn(0f, 1f)
    }

    private val ringLight = Paint().apply { color = Color.rgb(205, 210, 220); isAntiAlias = true }
    private val ringDark = Paint().apply { color = Color.rgb(170, 178, 192); isAntiAlias = true }
    private val inkPaint = Paint().apply {
        color = Color.rgb(60, 62, 70); style = Paint.Style.STROKE
        strokeWidth = 3f; isAntiAlias = true
    }
    private val leafPaint = Paint().apply { color = Color.rgb(86, 158, 70); isAntiAlias = true }
    private val dustPaint = Paint().apply { color = Color.argb(120, 190, 180, 160); isAntiAlias = true }
    private val oval = RectF()
    private val leafPath = Path()

    fun draw(canvas: Canvas) {
        val s = scale
        val a = alpha
        ringLight.alpha = a; ringDark.alpha = a; inkPaint.alpha = a; leafPaint.alpha = a
        dustPaint.alpha = 120 * a / 255
        val height = HEIGHT * s

        // 위가 넓고 아래가 좁은 회오리: 타원 여섯 장을 쌓고 좌우로 흔든다
        for (i in 0 until RINGS) {
            val f = i / (RINGS - 1f)                       // 0 = 위, 1 = 바닥
            val rw = (TOP_WIDTH + (BASE_WIDTH - TOP_WIDTH) * f) * s
            val rh = (46f - 14f * f) * s
            val cy = groundY - height + height * f
            val wob = sin(t * 18f + i) * (10f + 14f * (1f - f)) * s
            oval.set(x + wob - rw / 2f, cy - rh / 2f, x + wob + rw / 2f, cy + rh / 2f)
            canvas.drawOval(oval, if (i % 2 == 0) ringLight else ringDark)
            canvas.drawOval(oval, inkPaint)
        }

        // 휘말려 도는 잎사귀 세 장
        for (k in 0 until 3) {
            val ang = t * 9f + k * 2.1f
            val ry = groundY - height * (0.25f + 0.22f * k)
            val rx = x + cos(ang) * (52f + 18f * k) * s
            val sz = 13f * s
            leafPath.reset()
            leafPath.moveTo(rx, ry - sz)
            leafPath.lineTo(rx + sz * 0.9f, ry + sz * 0.6f)
            leafPath.lineTo(rx - sz * 0.9f, ry + sz * 0.6f)
            leafPath.close()
            canvas.save()
            canvas.rotate(ang * 60f, rx, ry)
            canvas.drawPath(leafPath, leafPaint)
            canvas.drawPath(leafPath, inkPaint)
            canvas.restore()
        }

        // 바닥에서 이는 흙먼지
        for (k in 0 until 3) {
            val dx = x + sin(t * 12f + k * 2f) * (30f + k * 16f) * s
            canvas.drawCircle(dx, groundY - 6f - k * 4f, (16f + k * 6f) * s, dustPaint)
        }

        ringLight.alpha = 255; ringDark.alpha = 255; inkPaint.alpha = 255; leafPaint.alpha = 255
        dustPaint.alpha = 120
    }
}
