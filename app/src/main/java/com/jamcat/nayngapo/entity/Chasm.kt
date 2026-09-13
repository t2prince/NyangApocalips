package com.jamcat.nayngapo.entity

import android.graphics.*

/**
 * 바닥이 통째로 없는 낭떠러지. 스테이지 후반부에 나타나며 걸어 들어가면 그대로 떨어져 즉사한다.
 * 폭은 점프 사거리(약 300px)보다 좁게 잡아 반드시 뛰어넘을 수 있게 한다.
 */
class Chasm(val x: Float, val y: Float, seed: Int) {

    /** 낭떠러지마다 다른 폭 (160~215) */
    val width = 160f + (kotlin.math.abs(seed * 43) % 56)

    /** 발끝이 허공에 놓였는지 (가장자리 턱은 아직 안전) */
    fun swallows(centerX: Float): Boolean = centerX > x + 16f && centerX < x + width - 16f

    private val voidPaint = Paint().apply { isAntiAlias = true }
    private val cliffPaint = Paint().apply { color = Color.rgb(74, 58, 48); isAntiAlias = true }
    private val cliffEdgePaint = Paint().apply { color = Color.rgb(112, 88, 68); isAntiAlias = true }
    private val markPaint = Paint().apply { color = Color.rgb(226, 62, 52); isAntiAlias = true }
    private val markEdgePaint = Paint().apply {
        color = Color.rgb(90, 20, 16); style = Paint.Style.STROKE
        strokeWidth = 4f; isAntiAlias = true
    }
    private val markSignPaint = Paint().apply {
        color = Color.WHITE; strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val path = Path()
    private val markPath = Path()

    /** 양쪽 절벽 단면이 들쭉날쭉하게 깨져 보이도록 씨앗으로 굳혀 둔 값 */
    private val jag = FloatArray(STEPS + 1) { i -> ((seed * (i + 5) * 29) % 19 - 9) / 9f }

    /**
     * 바닥은 GamePlayScreen이 아예 그리지 않는다. 여기서는 그 빈자리에
     * 깊은 어둠과 양쪽 절벽 단면을 그려 '떨어지는 구멍'으로 보이게 한다.
     */
    fun draw(canvas: Canvas, bottomY: Float) {
        // 아래로 갈수록 캄캄해지는 구멍 속
        voidPaint.shader = LinearGradient(
            0f, y, 0f, bottomY,
            Color.rgb(64, 50, 44), Color.rgb(10, 8, 10), Shader.TileMode.CLAMP
        )
        canvas.drawRect(x, y - 2f, x + width, bottomY, voidPaint)
        voidPaint.shader = null

        // 왼쪽 절벽 단면: 위 가장자리에서 아래로 깨져 내려간다
        drawCliff(canvas, x, -1f)
        drawCliff(canvas, x + width, 1f)

        // 가장자리에 걸린 흙턱 (밟고 설 수 있는 부분)
        canvas.drawRect(x - 26f, y - 6f, x + 8f, y + 10f, cliffEdgePaint)
        canvas.drawRect(x + width - 8f, y - 6f, x + width + 26f, y + 10f, cliffEdgePaint)

        // 경고 표지 — 멀리서도 구멍인 걸 알아보게
        val cx = x + width / 2f
        markPath.reset()
        markPath.moveTo(cx, y - 78f)
        markPath.lineTo(cx + 23f, y - 36f)
        markPath.lineTo(cx - 23f, y - 36f)
        markPath.close()
        canvas.drawPath(markPath, markPaint)
        canvas.drawPath(markPath, markEdgePaint)
        canvas.drawLine(cx, y - 66f, cx, y - 49f, markSignPaint)
        canvas.drawPoint(cx, y - 42f, markSignPaint)
    }

    /** 구멍 안쪽을 향해 깨져 내려가는 절벽 단면 (dir: -1 = 왼쪽 벽, +1 = 오른쪽 벽) */
    private fun drawCliff(canvas: Canvas, edgeX: Float, dir: Float) {
        path.reset()
        path.moveTo(edgeX, y - 4f)
        for (i in 0..STEPS) {
            val t = i / STEPS.toFloat()
            val depth = y + 40f + t * 210f
            val inset = (14f + 26f * t) * (0.7f + 0.3f * jag[i])
            path.lineTo(edgeX - dir * inset, depth)
        }
        path.lineTo(edgeX - dir * 4f, y + 300f)
        path.lineTo(edgeX, y + 300f)
        path.close()
        canvas.drawPath(path, cliffPaint)
    }

    private companion object {
        const val STEPS = 8
    }
}
