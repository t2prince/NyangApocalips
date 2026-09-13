package com.jamcat.nayngapo.entity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/**
 * 정글의 덩굴 그네 (기획: "인간도 덩굴 아래에서 점프를 누르면 덩굴을 탈 수 있어").
 * 위쪽 한 점에 매달려 좌우로 흔들리며, 인간이 끝을 잡고 매달릴 수 있다.
 *
 * @param anchorX 덩굴이 매달린 곳의 x (월드 좌표)
 * @param topY 덩굴이 매달린 곳의 y
 * @param length 덩굴 길이
 * @param phase 덩굴마다 흔들리는 시점을 달리해 한꺼번에 움직이지 않게 한다
 */
class Vine(
    val anchorX: Float,
    val topY: Float,
    val length: Float,
    private val phase: Float = 0f
) {
    companion object {
        /** 한 번 왕복하는 데 걸리는 시간(초) */
        const val PERIOD = 2.4f

        /** 수직에서 최대로 벌어지는 각도 */
        val MAX_ANGLE = Math.toRadians(35.0).toFloat()

        private const val TWO_PI = (Math.PI * 2).toFloat()
    }

    private var time = phase

    /** 수직에서 벌어진 각도(라디안). 오른쪽이 양수 */
    var angle = 0f
        private set

    /** 각속도 — 놓을 때의 튕겨 나가는 속도를 여기서 뽑는다 */
    var angularVelocity = 0f
        private set

    /** 덩굴 끝(잡는 곳)의 월드 좌표 */
    val bottomX: Float get() = anchorX + length * sin(angle)
    val bottomY: Float get() = topY + length * cos(angle)

    /** 그릴 때 쓰는 기울기(도) */
    val angleDegrees: Float get() = Math.toDegrees(angle.toDouble()).toFloat()

    fun update(deltaTime: Float) {
        time += deltaTime
        val w = TWO_PI / PERIOD
        angle = MAX_ANGLE * sin(time * w)
        angularVelocity = MAX_ANGLE * w * cos(time * w)
    }

    /** 덩굴 끝이 지금 가로로 움직이는 속도 (놓으면 이 속도로 날아간다) */
    fun tangentialVelocityX(): Float = length * cos(angle) * angularVelocity

    private val vinePaint = Paint().apply {
        color = Color.rgb(74, 132, 62); style = Paint.Style.STROKE
        strokeWidth = 9f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val leafPaint = Paint().apply { color = Color.rgb(86, 158, 70); isAntiAlias = true }
    private val gripPaint = Paint().apply { color = Color.rgb(120, 86, 48); isAntiAlias = true }
    private val path = Path()

    fun draw(canvas: Canvas) {
        val bx = bottomX
        val by = bottomY
        // 살짝 늘어진 곡선 — 가운데가 흔들리는 쪽으로 처진다
        val midX = anchorX + (bx - anchorX) * 0.45f
        val midY = topY + length * 0.5f
        path.reset()
        path.moveTo(anchorX, topY)
        path.quadTo(midX, midY, bx, by)
        canvas.drawPath(path, vinePaint)

        // 덩굴을 따라 붙은 잎사귀
        for (k in 1..3) {
            val t = k / 4f
            val lx = anchorX + (bx - anchorX) * t
            val ly = topY + length * t
            val side = if (k % 2 == 0) 1f else -1f
            canvas.drawOval(lx - 5f, ly - 7f, lx + 24f * side, ly + 7f, leafPaint)
        }

        // 손으로 잡는 끝 매듭
        canvas.drawCircle(bx, by, 11f, gripPaint)
    }
}
