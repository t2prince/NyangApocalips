package com.jamcat.nayngapo.entity

import android.graphics.*

/**
 * 얼음 구덩이 (51~60 빙산 표의 「얼음 구덩이」 — 뜻은 기획에 없어 Claude 판단, 2026-10-25):
 * 구덩이 위를 얇은 얼음이 덮고 있다. 인간이 밟으면 금이 가고(금이 점점 퍼진다) 0.5초 뒤 깨진다.
 * 깨진 뒤에는 구덩이 함정(PitTrap)과 같다 — 걸어 들어가면 빠진다. 냥이들은 가벼워서 얼음을 깨지 않는다(무시).
 */
class IceHole(val x: Float, val y: Float) {
    val width = WIDTH
    /** 금이 가기 시작한 뒤 흐른 시간 (-1 = 아직 멀쩡) */
    private var crackT = -1f
    val broken: Boolean get() = crackT >= BREAK_TIME

    /** 발이 얼음 위(가장자리 턱 빼고)에 있는가 */
    fun covers(centerX: Float): Boolean = centerX > x + 18f && centerX < x + width - 18f
    /** 깨진 구멍에 빠졌는가 */
    fun swallows(centerX: Float): Boolean = broken && covers(centerX)

    /** 인간이 얼음 위에 서 있으면 금이 가기 시작한다 — 한 번 금이 가면 그대로 깨진다 */
    fun update(deltaTime: Float, playerOn: Boolean) {
        if (crackT < 0f) { if (playerOn) crackT = 0f; return }
        if (!broken) crackT += deltaTime
    }

    private val icePaint = Paint().apply { color = Color.argb(235, 196, 232, 252); isAntiAlias = true }
    private val iceLit = Paint().apply { color = Color.argb(200, 255, 255, 255); strokeWidth = 4f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val edgePaint = Paint().apply { color = Color.rgb(60, 110, 160); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val crackPaint = Paint().apply { color = Color.rgb(40, 70, 110); strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val holePaint = Paint().apply { color = Color.rgb(10, 16, 30); isAntiAlias = true }
    private val wallPaint = Paint().apply { color = Color.rgb(110, 170, 215); isAntiAlias = true }
    private val shardPaint = Paint().apply { color = Color.rgb(196, 232, 252); isAntiAlias = true }
    private val rect = RectF()

    fun draw(canvas: Canvas) {
        // GPT 그림 (2026-11-08): 깨진 구멍 / 얼음판. 얼음판 위 금은 코드가 그대로 그린다
        val art = com.jamcat.nayngapo.engine.SpriteManager.traps
        if (broken) art["trap_ice_hole_broken"]?.let { b ->
            val h = 190f
            rect.set(x - 10f, y - h * BROKEN_GROUND, x + width + 10f, y - h * BROKEN_GROUND + h)
            canvas.drawBitmap(b, null, rect, null)
            return
        }
        if (!broken) art["trap_ice_hole"]?.let { b ->
            val h = width * b.height / b.width
            rect.set(x, y - h * 0.45f, x + width, y + h * 0.55f)
            canvas.drawBitmap(b, null, rect, null)
            if (crackT >= 0f) drawCracks(canvas)
            return
        }
        if (broken) {
            // 깨진 구멍: 얼음 벽 + 캄캄한 속 + 가장자리에 남은 얼음 조각
            rect.set(x + 6f, y - 10f, x + width - 6f, y + 150f)
            canvas.drawRoundRect(rect, 20f, 20f, wallPaint)
            rect.set(x + 18f, y - 6f, x + width - 18f, y + 140f)
            canvas.drawRoundRect(rect, 16f, 16f, holePaint)
            for (k in 0 until 3) {
                canvas.drawCircle(x + 14f + k * 8f, y - 4f + k * 3f, 6f, shardPaint)
                canvas.drawCircle(x + width - 14f - k * 8f, y - 4f + k * 3f, 6f, shardPaint)
            }
            return
        }
        // 얇은 얼음판 (바닥에 살짝 얹힌 납작한 타원)
        rect.set(x, y - 12f, x + width, y + 12f)
        canvas.drawOval(rect, icePaint)
        canvas.drawOval(rect, edgePaint)
        canvas.drawLine(x + width * 0.2f, y - 4f, x + width * 0.38f, y - 6f, iceLit)
        drawCracks(canvas)
    }

    private fun drawCracks(canvas: Canvas) {
        // 금: 밟힌 뒤 점점 퍼진다 (멀쩡해도 잔금 하나 — 수상한 얼음)
        val k = if (crackT < 0f) 0.15f else 0.3f + 0.7f * (crackT / BREAK_TIME)
        val cx = x + width / 2f
        val r = width * 0.45f * k
        for (i in 0 until 6) {
            val a = i / 6f * 6.283f + 0.4f
            val ex = cx + kotlin.math.cos(a) * r
            val ey = y + kotlin.math.sin(a) * r * 0.22f
            canvas.drawLine(cx, y, ex, ey, crackPaint)
            if (crackT >= 0f) canvas.drawLine(ex, ey, ex + kotlin.math.cos(a + 0.6f) * r * 0.3f, ey + kotlin.math.sin(a + 0.6f) * r * 0.08f, crackPaint)
        }
    }

    companion object {
        /** 얼음판 폭 · 밟고 깨질 때까지 (Claude 판단) */
        const val WIDTH = 190f
        const val BREAK_TIME = 0.5f
        /** 깨진 구멍 그림에서 지면선 높이 비율 (위 얼음 조각 아래 가로선, 그림에서 잰 값) */
        private const val BROKEN_GROUND = 0.19f
    }
}
