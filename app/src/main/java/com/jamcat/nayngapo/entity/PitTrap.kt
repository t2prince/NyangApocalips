package com.jamcat.nayngapo.entity

import android.graphics.*

/**
 * 땅에 파인 구덩이. 크기와 모양이 제각각이며, 걸어서 안쪽으로 들어가면 빠져서 게임 오버.
 * 폭은 점프 사거리(약 300px)보다 좁게 잡아 반드시 뛰어넘을 수 있게 한다.
 */
class PitTrap(val x: Float, val y: Float, seed: Int) {

    /** 구덩이마다 다른 폭 (170~225) — 점프 사거리(약 300)보다는 좁게 */
    val width = 170f + (kotlin.math.abs(seed * 37) % 56)
    private val depth = 170f + (kotlin.math.abs(seed * 53) % 70)

    /** 구덩이 안쪽으로 들어갔는지 (가장자리 턱은 아직 안전) */
    fun swallows(centerX: Float): Boolean = centerX > x + 22f && centerX < x + width - 22f

    private val holePaint = Paint().apply { color = Color.rgb(20, 17, 19); isAntiAlias = true }
    private val wallPaint = Paint().apply { color = Color.rgb(52, 42, 40); isAntiAlias = true }
    private val rimPaint = Paint().apply {
        color = Color.rgb(86, 68, 56); style = Paint.Style.STROKE
        strokeWidth = 7f; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
    }
    private val spikePaint = Paint().apply { color = Color.rgb(168, 172, 180); isAntiAlias = true }
    private val spikeTipPaint = Paint().apply { color = Color.rgb(168, 42, 40); isAntiAlias = true }
    private val glowPaint = Paint().apply { color = Color.argb(90, 190, 40, 30); isAntiAlias = true }
    private val warnPaint = Paint().apply { color = Color.rgb(226, 62, 52); isAntiAlias = true }
    private val warnEdgePaint = Paint().apply {
        color = Color.rgb(90, 20, 16); style = Paint.Style.STROKE
        strokeWidth = 4f; isAntiAlias = true
    }
    private val warnMarkPaint = Paint().apply {
        color = Color.WHITE; strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val path = Path()
    private val spikePath = Path()
    private val warnPath = Path()
    private val glowRect = RectF()

    /** 파헤쳐진 듯 울퉁불퉁한 구덩이 윤곽 (씨앗값으로 매번 같은 모양을 만든다) */
    private val outline: FloatArray = FloatArray(WALL_STEPS + 1) { i ->
        // 좌우 벽이 서로 다르게 기울고 바닥도 평평하지 않다
        val t = i / WALL_STEPS.toFloat()
        val jag = ((seed * (i + 3) * 17) % 23 - 11) / 11f      // -1 ~ 1
        (0.5f - kotlin.math.abs(t - 0.5f)) * 2f * (0.82f + 0.18f * jag)
    }

    fun draw(canvas: Canvas) {
        // 구덩이 벽: 위 가장자리에서 바닥까지 들쭉날쭉하게 파였다
        path.reset()
        path.moveTo(x, y - 4f)
        for (i in 0..WALL_STEPS) {
            val t = i / WALL_STEPS.toFloat()
            val px = x + width * t
            val py = y + depth * outline[i]
            path.lineTo(px, py)
        }
        path.lineTo(x + width, y - 4f)
        path.close()
        canvas.drawPath(path, wallPaint)

        // 안쪽 어둠
        path.reset()
        path.moveTo(x + 12f, y - 2f)
        for (i in 0..WALL_STEPS) {
            val t = i / WALL_STEPS.toFloat()
            path.lineTo(x + 12f + (width - 24f) * t, y + (depth - 18f) * outline[i])
        }
        path.lineTo(x + width - 12f, y - 2f)
        path.close()
        canvas.drawPath(path, holePaint)

        // 바닥에서 올라오는 붉은 열기 — 한눈에 위험해 보이게
        glowRect.set(x + 14f, y + depth * 0.45f, x + width - 14f, y + depth)
        canvas.drawOval(glowRect, glowPaint)

        // 바닥의 길고 날카로운 쇠꼬챙이 (빠지면 끝)
        val spikes = 6
        val w = (width - 40f) / spikes
        for (i in 0 until spikes) {
            val sx = x + 20f + i * w
            val floorY = y + depth * outline[(WALL_STEPS * (i + 1)) / (spikes + 1)] - 8f
            spikePath.reset()
            spikePath.moveTo(sx, floorY)
            spikePath.lineTo(sx + w / 2f, floorY - 78f)     // 훨씬 길고 뾰족하게
            spikePath.lineTo(sx + w, floorY)
            spikePath.close()
            canvas.drawPath(spikePath, spikePaint)
            // 끝부분의 핏빛
            spikePath.reset()
            spikePath.moveTo(sx + w * 0.28f, floorY - 52f)
            spikePath.lineTo(sx + w / 2f, floorY - 78f)
            spikePath.lineTo(sx + w * 0.72f, floorY - 52f)
            spikePath.close()
            canvas.drawPath(spikePath, spikeTipPaint)
        }

        // 입구 턱 + 경고 표시
        canvas.drawLine(x - 14f, y - 4f, x + 10f, y - 4f, rimPaint)
        canvas.drawLine(x + width - 10f, y - 4f, x + width + 14f, y - 4f, rimPaint)

        val cx = x + width / 2f
        warnPath.reset()
        warnPath.moveTo(cx, y - 74f)
        warnPath.lineTo(cx + 22f, y - 34f)
        warnPath.lineTo(cx - 22f, y - 34f)
        warnPath.close()
        canvas.drawPath(warnPath, warnPaint)
        canvas.drawPath(warnPath, warnEdgePaint)
        canvas.drawLine(cx, y - 62f, cx, y - 46f, warnMarkPaint)
        canvas.drawPoint(cx, y - 40f, warnMarkPaint)
    }

    private companion object {
        const val WALL_STEPS = 10
    }
}
