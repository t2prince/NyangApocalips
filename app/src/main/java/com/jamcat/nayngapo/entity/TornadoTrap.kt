package com.jamcat.nayngapo.entity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sin

/**
 * 토네이도 함정 — 31 해적 스테이지 (특수 스테이지 표 기획12, 2026-10-04).
 * 제자리 근처를 좌우로 천천히 오가는 회오리. 인간이 닿으면 하트 1 + 뒤로 날려 보낸다(점프로 넘으면 안전).
 * 오가는 폭 300·속도·날려 보내는 거리는 Claude 판단 (대표님이 함정 모양을 정하지 않아 정함).
 */
class TornadoTrap(private val homeX: Float, val groundY: Float, seed: Int) {
    var x = homeX
        private set
    private var t = (seed % 7) * 0.9f
    val width = 90f
    val height = 230f

    fun update(deltaTime: Float) {
        t += deltaTime
        x = homeX + sin(t * 0.8f) * RANGE
    }

    /** 이 x(인간 가운데)가 회오리에 닿는가 */
    fun hits(cx: Float) = cx > x - width / 2f && cx < x + width / 2f

    fun draw(canvas: Canvas) {
        // GPT 그림 (2026-11-08): 회오리 그림을 발밑 기준으로 좌우로 살짝 흔들고, 빙글 도는 느낌으로 짧게 뒤집는다
        com.jamcat.nayngapo.engine.SpriteManager.traps["trap_tornado"]?.let { b ->
            val bmp = if ((t * 6f).toInt() % 2 == 0) b else com.jamcat.nayngapo.engine.SpriteManager.getFlipped(b)
            val h = height; val w = h * b.width / b.height
            canvas.save()
            canvas.rotate(sin(t * 5f) * 5f, x, groundY)
            rect.set(x - w / 2f, groundY - h, x + w / 2f, groundY + 6f)
            canvas.drawBitmap(bmp, null, rect, null)
            canvas.restore()
            return
        }
        // 아래는 가늘고 위로 갈수록 넓어지는 회오리 고리들 — 계속 돈다
        for (k in 0 until RINGS) {
            val f = k / (RINGS - 1f)
            val w = 26f + (width * 1.3f - 26f) * f
            val y = groundY - 14f - f * (height - 30f)
            val sway = sin(t * 7f + k * 0.9f) * 10f * f
            rect.set(x - w / 2f + sway, y - 10f - 6f * f, x + w / 2f + sway, y + 10f + 6f * f)
            fill.alpha = 150 + (k * 12)
            canvas.drawOval(rect, fill)
            canvas.drawArc(rect, (t * 600f + k * 60f) % 360f, 220f, false, ink)
        }
        // 바닥 흙먼지
        dust.alpha = 120
        rect.set(x - 50f, groundY - 10f, x + 50f, groundY + 8f)
        canvas.drawOval(rect, dust)
    }

    companion object {
        private const val RANGE = 150f
        private const val RINGS = 7
        private val rect = RectF()
        private val fill = Paint().apply { color = Color.rgb(176, 186, 198); isAntiAlias = true }
        private val ink = Paint().apply { color = Color.rgb(70, 76, 88); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
        private val dust = Paint().apply { color = Color.rgb(200, 180, 140); isAntiAlias = true }
    }
}
