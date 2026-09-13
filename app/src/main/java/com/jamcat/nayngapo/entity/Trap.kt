package com.jamcat.nayngapo.entity

import android.graphics.*

/**
 * 바닥에 설치된 가시 함정. 스테이지마다 한 개만 놓인다.
 * 가시가 올라왔을 때 밟으면 피해를 입고, 점프로 뛰어넘으면 안전하다.
 */
class Trap(val x: Float, val y: Float) {
    val width = 150f

    private var phase = (Math.random() * Math.PI).toFloat()

    /** 가시가 솟은 정도 (0=완전히 들어감, 1=끝까지 솟음) */
    private val extend: Float
        get() = (kotlin.math.sin(phase.toDouble()).toFloat() * 0.5f + 0.5f)

    /** 가시가 충분히 솟아 있어 위험한 상태인지 */
    val isDangerous: Boolean get() = extend > 0.55f

    private val spikeHeight = 46f

    val hitBox: RectF
        get() = RectF(x, y - spikeHeight * extend, x + width, y + 8f)

    private val basePaint = Paint().apply { color = Color.rgb(72, 66, 62); isAntiAlias = true }
    private val baseEdgePaint = Paint().apply {
        color = Color.rgb(40, 36, 34); style = Paint.Style.STROKE
        strokeWidth = 4f; isAntiAlias = true
    }
    private val spikePaint = Paint().apply { color = Color.rgb(198, 202, 208); isAntiAlias = true }
    private val spikeEdgePaint = Paint().apply {
        color = Color.rgb(60, 56, 54); style = Paint.Style.STROKE
        strokeWidth = 3f; isAntiAlias = true
    }
    private val warnPaint = Paint().apply {
        color = Color.rgb(230, 70, 60); isAntiAlias = true
        style = Paint.Style.STROKE; strokeWidth = 5f
    }
    private val path = Path()
    private val holeRect = RectF()

    private companion object {
        /** 가시가 숨는 얕은 구덩이의 깊이 */
        const val PIT_DEPTH = 30f
    }

    fun update(deltaTime: Float) {
        phase += deltaTime * 1.9f     // 천천히 솟았다 들어간다
    }

    fun draw(canvas: Canvas) {
        val e = extend
        val top = y - spikeHeight * e
        val floorY = y + PIT_DEPTH          // 얕은 구덩이 바닥

        // 가시가 숨는 얕은 구덩이
        holeRect.set(x - 8f, y - 4f, x + width + 8f, floorY)
        canvas.drawRoundRect(holeRect, 14f, 14f, basePaint)

        // 가시: 구덩이 바닥에서 올라와 지면 위로 솟는다
        val spikes = 5
        val w = width / spikes
        for (i in 0 until spikes) {
            val sx = x + i * w
            path.reset()
            path.moveTo(sx + 3f, floorY - 4f)
            path.lineTo(sx + w / 2f, top)
            path.lineTo(sx + w - 3f, floorY - 4f)
            path.close()
            canvas.drawPath(path, spikePaint)
            canvas.drawPath(path, spikeEdgePaint)
        }

        // 구덩이 입구 테두리 (파헤쳐진 흙)
        canvas.drawLine(x - 8f, y - 3f, x + width + 8f, y - 3f, baseEdgePaint)

        // 가시가 솟았을 때 위험 표시
        if (isDangerous) {
            val cx = x + width / 2f
            canvas.drawLine(cx, top - 34f, cx, top - 16f, warnPaint)
            canvas.drawPoint(cx, top - 8f, warnPaint)
        }
    }
}
