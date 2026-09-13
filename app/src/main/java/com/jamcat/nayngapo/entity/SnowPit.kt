package com.jamcat.nayngapo.entity

import android.graphics.*

/**
 * 설원(3·4스테이지)의 푹 꺼진 눈밭. 걸어 들어가면 허리까지 빠져
 * 느릿느릿해지고 점프도 할 수 없다. 뛰어넘으면 안전하다.
 */
class SnowPit(val x: Float, val y: Float, seed: Int) {

    val width = 230f + (kotlin.math.abs(seed * 41) % 110)   // 230 ~ 340
    private val depth = 54f

    /** 눈에 빠지는 구간인지 (가장자리는 안전) */
    fun swallows(centerX: Float): Boolean = centerX > x + 20f && centerX < x + width - 20f

    private val holePaint = Paint().apply { color = Color.rgb(196, 214, 232); isAntiAlias = true }
    private val shadePaint = Paint().apply { color = Color.rgb(168, 192, 216); isAntiAlias = true }
    private val snowPaint = Paint().apply { color = Color.rgb(248, 252, 255); isAntiAlias = true }
    private val sparklePaint = Paint().apply { color = Color.rgb(255, 255, 255); isAntiAlias = true }
    private val path = Path()

    /** 움푹 꺼진 눈구덩이 바닥 (개체보다 먼저 그린다) */
    fun draw(canvas: Canvas) {
        // 지면 높이에서 시작해 완만하게 파여 내려가므로 주변 눈밭과 이어져 보인다
        path.reset()
        path.moveTo(x - 40f, y)
        path.cubicTo(
            x + width * 0.22f, y + depth,
            x + width * 0.78f, y + depth,
            x + width + 40f, y
        )
        path.lineTo(x + width + 40f, y + depth + 90f)
        path.lineTo(x - 40f, y + depth + 90f)
        path.close()
        canvas.drawPath(path, holePaint)

        path.reset()
        path.moveTo(x + 8f, y + 4f)
        path.cubicTo(
            x + width * 0.3f, y + depth * 0.85f,
            x + width * 0.7f, y + depth * 0.85f,
            x + width - 8f, y + 4f
        )
        path.close()
        canvas.drawPath(path, shadePaint)
    }

    /**
     * 앞쪽에 두툼하게 쌓인 눈. 개체 위에 덮어 그려 몸이 완전히 파묻힌 것처럼 보이게 하고,
     * 양옆은 지면 높이로 흘러내려 주변 눈밭과 자연스럽게 이어진다.
     */
    fun drawFront(canvas: Canvas) {
        path.reset()
        path.moveTo(x - 90f, y + 10f)                       // 왼쪽 끝은 지면 높이
        path.cubicTo(
            x + width * 0.18f, y - 120f,                    // 가운데는 높이 솟은 눈더미
            x + width * 0.82f, y - 120f,
            x + width + 90f, y + 10f                        // 오른쪽 끝도 지면 높이
        )
        path.lineTo(x + width + 90f, y + 140f)
        path.lineTo(x - 90f, y + 140f)
        path.close()
        canvas.drawPath(path, snowPaint)

        // 눈 반짝임
        for (i in 0 until 6) {
            val sx = x + 24f + i * (width - 48f) / 5f
            canvas.drawCircle(sx, y - 30f + (i % 3) * 14f, 3.5f, sparklePaint)
        }
    }
}
