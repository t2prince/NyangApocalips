package com.jamcat.nayngapo.entity

import android.graphics.*

/**
 * 늪 (기획3: "스테이지는 늪이 있고, 빠지면 5배 느려진다. 점프로 건너뛸 수 있다.")
 * 걸어 들어가면 걸음이 5배 느려지지만, 눈밭과 달리 점프로 빠져나올 수 있다.
 */
/** honey: 꿀 지역(21~30)의 꿀 늪 — 색만 황금빛 (대표님 2026-10-09) */
class Swamp(val x: Float, val y: Float, seed: Int, val honey: Boolean = false) {

    val width = 240f + (kotlin.math.abs(seed * 47) % 120)   // 240 ~ 360

    /** 늪 안쪽에 발이 있는지 (가장자리 턱은 안전) */
    fun swallows(centerX: Float): Boolean = centerX > x + 24f && centerX < x + width - 24f

    private val mudPaint = Paint().apply { color = if (honey) Color.rgb(226, 150, 30) else Color.rgb(94, 82, 48); isAntiAlias = true }
    private val darkPaint = Paint().apply { color = if (honey) Color.rgb(176, 100, 14) else Color.rgb(70, 60, 34); isAntiAlias = true }
    private val glossPaint = Paint().apply { color = if (honey) Color.rgb(255, 214, 110) else Color.rgb(118, 104, 62); isAntiAlias = true }
    private val reedPaint = Paint().apply {
        color = Color.rgb(64, 112, 52); strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val bubblePaint = Paint().apply {
        color = Color.rgb(132, 118, 74); style = Paint.Style.STROKE
        strokeWidth = 3f; isAntiAlias = true
    }
    private val path = Path()

    private var wave = (seed % 7) * 0.6f

    fun update(deltaTime: Float) {
        wave += deltaTime
    }

    private val swampRect = android.graphics.RectF()

    fun draw(canvas: Canvas) {
        // GPT 그림 (2026-11-08): 진흙 늪 / 꿀 늪 웅덩이를 폭에 맞춰 깐다 + 갈대는 그대로
        com.jamcat.nayngapo.engine.SpriteManager.traps[if (honey) "trap_swamp_honey" else "trap_swamp"]?.let { b ->
            val w = width + 40f
            val h = w * b.height / b.width
            swampRect.set(x - 20f, y - h * 0.35f, x + width + 20f, y + h * 0.65f)
            canvas.drawBitmap(b, null, swampRect, null)
            drawReeds(canvas)
            return
        }
        // 진흙 웅덩이: 지면에 얕게 파인 타원
        path.reset()
        path.moveTo(x - 20f, y)
        path.cubicTo(x + width * 0.25f, y + 42f, x + width * 0.75f, y + 42f, x + width + 20f, y)
        path.cubicTo(x + width * 0.75f, y + 14f, x + width * 0.25f, y + 14f, x - 20f, y)
        path.close()
        canvas.drawPath(path, mudPaint)

        // 가운데 깊은 색 + 번들거림
        path.reset()
        path.moveTo(x + 30f, y + 4f)
        path.cubicTo(x + width * 0.35f, y + 30f, x + width * 0.65f, y + 30f, x + width - 30f, y + 4f)
        path.close()
        canvas.drawPath(path, darkPaint)
        canvas.drawOval(x + width * 0.2f, y + 2f, x + width * 0.45f, y + 12f, glossPaint)

        // 부글거리는 거품 (천천히 자리를 바꾼다)
        for (i in 0 until 3) {
            val t = (wave * 0.5f + i * 1.3f) % 3f
            if (t < 2f) {
                val bx = x + width * (0.25f + 0.25f * i)
                val br = 4f + t * 3f
                canvas.drawCircle(bx, y + 10f - t * 3f, br, bubblePaint)
            }
        }

        drawReeds(canvas)
    }

    private fun drawReeds(canvas: Canvas) {
        // 가장자리 갈대
        for ((i, side) in listOf(x - 6f, x + width + 6f).withIndex()) {
            val sway = kotlin.math.sin(wave * 1.4 + i * 2).toFloat() * 4f
            canvas.drawLine(side, y + 2f, side + sway, y - 34f, reedPaint)
            canvas.drawLine(side + 10f, y + 2f, side + 10f + sway, y - 24f, reedPaint)
        }
    }
}
