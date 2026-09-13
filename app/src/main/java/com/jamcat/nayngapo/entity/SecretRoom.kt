package com.jamcat.nayngapo.entity

import android.graphics.*

/**
 * 비밀의 방 (기획1·5·6): 땅에 숨은 사다리 구멍. 위를 지나가면 발견되어
 * 하트(체력)를 얻는다. 발견 전에는 흙과 거의 같은 색이라 잘 안 보인다.
 */
class SecretRoom(val x: Float, val y: Float) {
    val width = 90f
    var found = false

    /** 플레이어 중심이 입구 위에 있는지 */
    fun covers(centerX: Float): Boolean = centerX > x && centerX < x + width

    private val holePaint = Paint().apply { color = Color.rgb(44, 36, 30); isAntiAlias = true }
    private val hiddenPaint = Paint().apply { color = Color.rgb(96, 78, 52); isAntiAlias = true }
    private val ladderPaint = Paint().apply {
        color = Color.rgb(150, 108, 60); strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val roomPaint = Paint().apply { color = Color.rgb(58, 48, 40); isAntiAlias = true }
    private val heartPaint = Paint().apply { color = Color.rgb(235, 80, 90); isAntiAlias = true }
    private val heartPath = Path()

    fun draw(canvas: Canvas) {
        if (!found) {
            // 발견 전: 흙색 널빤지 틈 — 아주 미묘한 힌트만
            canvas.drawRect(x, y - 3f, x + width, y + 5f, hiddenPaint)
            return
        }
        // 발견 후: 뻥 뚫린 입구 + 아래로 내려가는 사다리와 작은 방
        canvas.drawRect(x, y - 4f, x + width, y + 150f, roomPaint)
        canvas.drawRect(x + 6f, y - 2f, x + width - 6f, y + 8f, holePaint)
        val lx0 = x + width * 0.30f
        val lx1 = x + width * 0.70f
        canvas.drawLine(lx0, y, lx0, y + 140f, ladderPaint)
        canvas.drawLine(lx1, y, lx1, y + 140f, ladderPaint)
        for (i in 0 until 5) {
            val ly = y + 20f + i * 26f
            canvas.drawLine(lx0, ly, lx1, ly, ladderPaint)
        }
        // 방 안의 하트
        drawHeart(canvas, x + width / 2f, y + 132f, 11f)
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        heartPath.reset()
        heartPath.moveTo(cx, cy + r)
        heartPath.cubicTo(cx - r * 1.6f, cy - r * 0.4f, cx - r * 0.7f, cy - r * 1.4f, cx, cy - r * 0.5f)
        heartPath.cubicTo(cx + r * 0.7f, cy - r * 1.4f, cx + r * 1.6f, cy - r * 0.4f, cx, cy + r)
        heartPath.close()
        canvas.drawPath(heartPath, heartPaint)
    }
}
