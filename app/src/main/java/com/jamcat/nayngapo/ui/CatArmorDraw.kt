package com.jamcat.nayngapo.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * 고양이 갑옷 — 원안 `Cats/cats4.jpeg` 의 큰 고양이: 머리부터 몸통까지 통째로 덮는 둥근 철판 갑옷.
 * 앞쪽(얼굴 쪽)에 눈이 보이는 네모 창, 그 아래 주둥이를 가리는 세로 창살, 판 이음새와 리벳.
 * 꼬리는 뒤로, 다리는 아래로 삐져나온다. 비트맵 없이 냥이 그림 위에 도형으로 그린다.
 *
 * @param dst 냥이 비트맵이 놓인 사각형 (그림은 오른쪽 40%가 얼굴)
 * @param facingRight 오른쪽을 보고 있는가 (왼쪽이면 좌우 대칭으로 그린다)
 * @param wide 날아냥이처럼 날개 때문에 그림이 넓은 종 — 몸통이 가운데라 판을 좁혀 가운데에 둔다
 */
object CatArmorDraw {
    private val platePaint = Paint().apply { color = Color.rgb(178, 184, 194); isAntiAlias = true; alpha = 238 }
    private val darkPaint = Paint().apply { color = Color.rgb(60, 62, 72); isAntiAlias = true }
    private val edgePaint = Paint().apply {
        color = Color.rgb(48, 48, 56); isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val seamPaint = Paint().apply {
        color = Color.rgb(96, 100, 112); isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val shadePaint = Paint().apply { color = Color.rgb(140, 146, 158); isAntiAlias = true; alpha = 150 }
    private val path = Path()
    private val rect = RectF()

    fun draw(canvas: Canvas, dst: RectF, facingRight: Boolean, wide: Boolean = false) {
        val w = dst.width(); val h = dst.height()
        // 몸통이 차지하는 가로 구간 (꼬리 쪽은 비운다)
        val bodyL = if (wide) 0.26f else 0.14f
        val bodyR = if (wide) 0.80f else 0.97f
        fun ux(u: Float) = dst.left + w * (if (facingRight) u else 1f - u)
        fun vy(v: Float) = dst.top + h * v
        val sw = edgePaint.strokeWidth
        edgePaint.strokeWidth = (h * 0.035f).coerceIn(2f, 4f)
        seamPaint.strokeWidth = (h * 0.02f).coerceIn(1.5f, 3f)

        // 1) 둥근 통 갑옷 — 머리 꼭대기부터 다리 위까지. 얼굴 창은 구멍(EVEN_ODD)으로 뚫는다.
        path.reset(); path.fillType = Path.FillType.EVEN_ODD
        rect.set(minOf(ux(bodyL), ux(bodyR)), vy(0.06f), maxOf(ux(bodyL), ux(bodyR)), vy(0.80f))
        path.addRoundRect(rect, w * (bodyR - bodyL) * 0.42f, h * 0.40f, Path.Direction.CW)
        rect.set(minOf(ux(0.60f), ux(0.93f)), vy(0.16f), maxOf(ux(0.60f), ux(0.93f)), vy(0.62f))   // 눈(0.2)·주둥이(0.35~0.55) 가 보이는 창
        path.addRoundRect(rect, w * 0.03f, w * 0.03f, Path.Direction.CW)
        canvas.drawPath(path, platePaint)
        // 아래쪽 그늘 — 통이 둥글어 보이게
        rect.set(minOf(ux(bodyL + 0.02f), ux(bodyR - 0.02f)), vy(0.62f), maxOf(ux(bodyL + 0.02f), ux(bodyR - 0.02f)), vy(0.80f))
        canvas.save(); canvas.clipPath(path); canvas.drawRect(rect, shadePaint); canvas.restore()
        canvas.drawPath(path, edgePaint)

        // 2) 얼굴 창 아래 창살 — 주둥이가 창살 사이로 보인다
        val barTop = vy(0.40f); val barBot = vy(0.62f)
        for (i in 0..4) {
            val u = 0.62f + 0.29f * i / 4f
            canvas.drawLine(ux(u), barTop, ux(u), barBot, edgePaint)
        }
        canvas.drawLine(ux(0.60f), vy(0.40f), ux(0.93f), vy(0.40f), edgePaint)

        // 3) 판 이음새 — 세로 하나, 가로 하나
        canvas.drawLine(ux(0.40f), vy(0.10f), ux(0.40f), vy(0.78f), seamPaint)
        canvas.drawLine(ux(bodyL + 0.03f), vy(0.46f), ux(0.58f), vy(0.46f), seamPaint)

        // 4) 리벳
        val r = (h * 0.022f).coerceAtLeast(1.5f)
        for ((u, v) in listOf(0.22f to 0.30f, 0.34f to 0.16f, 0.46f to 0.16f, 0.50f to 0.60f, 0.24f to 0.66f, 0.56f to 0.32f))
            canvas.drawCircle(ux(u), vy(v), r, darkPaint)
        edgePaint.strokeWidth = sw
    }
}
