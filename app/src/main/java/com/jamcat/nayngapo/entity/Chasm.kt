package com.jamcat.nayngapo.entity

import android.graphics.*

/**
 * 바닥이 통째로 없는 낭떠러지. 스테이지 후반부에 나타나며 걸어 들어가면 그대로 떨어져 즉사한다.
 * 폭은 점프 사거리(약 300px)보다 좁게 잡아 반드시 뛰어넘을 수 있게 한다.
 */
class Chasm(
    val x: Float, val y: Float, seed: Int,
    /**
     * 수직 구덩이 (51~60 빙산 표의 「수직 구덩이」 — 뜻은 기획에 없어 Claude 판단, 2026-10-25):
     * 폭 130 의 좁고 아주 깊은 얼음 구멍. 벽이 얼음이고, 빠지면 낭떠러지처럼 떨어진다.
     */
    val shaft: Boolean = false
) {

    /** 낭떠러지마다 다른 폭 (160~215), 수직 구덩이는 130 */
    val width = if (shaft) SHAFT_W else 160f + (kotlin.math.abs(seed * 43) % 56)

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
        if (shaft) { drawShaft(canvas, bottomY); return }
        // 아래로 갈수록 캄캄해지는 구멍 속
        voidPaint.shader = LinearGradient(
            0f, y, 0f, bottomY,
            Color.rgb(64, 50, 44), Color.rgb(10, 8, 10), Shader.TileMode.CLAMP
        )
        canvas.drawRect(x, y - 2f, x + width, bottomY, voidPaint)
        voidPaint.shader = null

        // 왼쪽·오른쪽 절벽 벽 — GPT 그림이 있으면 그 그림(오른쪽은 좌우 뒤집기), 없으면 코드 그림 (2026-11-08)
        val cliff = com.jamcat.nayngapo.engine.SpriteManager.traps["trap_cliff_left"]
        if (cliff != null) {
            val h = bottomY - y + 40f
            val w = 180f
            val top = y - h * CLIFF_GROUND - 4f   // 그림 위 풀 줄(6%)이 지면에 오게
            artRect.set(x - w + 34f, top, x + 34f, top + h)
            canvas.drawBitmap(cliff, null, artRect, null)
            artRect.set(x + width - 34f, top, x + width - 34f + w, top + h)
            canvas.drawBitmap(com.jamcat.nayngapo.engine.SpriteManager.getFlipped(cliff), null, artRect, null)
        } else {
            drawCliff(canvas, x, -1f)
            drawCliff(canvas, x + width, 1f)
        }

        // 가장자리에 걸린 흙턱 (밟고 설 수 있는 부분)
        canvas.drawRect(x - 26f, y - 6f, x + 8f, y + 10f, cliffEdgePaint)
        canvas.drawRect(x + width - 8f, y - 6f, x + width + 26f, y + 10f, cliffEdgePaint)

        drawMark(canvas)
    }

    /** 경고 표지 — 멀리서도 구멍인 걸 알아보게 */
    private fun drawMark(canvas: Canvas) {
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

    private val shaftWall = Paint().apply { color = Color.rgb(150, 205, 240); isAntiAlias = true }
    private val shaftWallLit = Paint().apply { color = Color.rgb(215, 240, 255); isAntiAlias = true }
    private val shaftEdge = Paint().apply { color = Color.rgb(30, 50, 80); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val shaftRim = Paint().apply { color = Color.rgb(245, 250, 255); isAntiAlias = true }

    /** 수직 구덩이: 곧게 떨어지는 얼음 벽 사이로 끝이 안 보이게 캄캄해진다 + 눈 덮인 가장자리 + 경고 표지 */
    private val artRect = RectF()

    private fun drawShaft(canvas: Canvas, bottomY: Float) {
        // GPT 그림 (2026-11-08): 곧게 내려가는 얼음 벽 사이 깊은 구멍 — 폭에 맞춰 바닥까지 늘린다
        com.jamcat.nayngapo.engine.SpriteManager.traps["trap_shaft"]?.let { b ->
            artRect.set(x - 12f, y - 10f, x + width + 12f, bottomY)
            canvas.drawBitmap(b, null, artRect, null)
            canvas.drawRoundRect(x - 26f, y - 8f, x + 12f, y + 8f, 8f, 8f, shaftRim)
            canvas.drawRoundRect(x + width - 12f, y - 8f, x + width + 26f, y + 8f, 8f, 8f, shaftRim)
            drawMark(canvas)
            return
        }
        voidPaint.shader = LinearGradient(0f, y, 0f, y + 260f, Color.rgb(70, 120, 170), Color.rgb(6, 10, 22), Shader.TileMode.CLAMP)
        canvas.drawRect(x, y - 2f, x + width, bottomY, voidPaint)
        voidPaint.shader = null
        // 양쪽 얼음 벽 (두께 18, 아래로 곧게)
        canvas.drawRect(x, y, x + 18f, bottomY, shaftWall)
        canvas.drawRect(x + width - 18f, y, x + width, bottomY, shaftWall)
        canvas.drawRect(x + 3f, y, x + 8f, bottomY, shaftWallLit)
        canvas.drawRect(x + width - 15f, y, x + width - 10f, bottomY, shaftWallLit)
        canvas.drawLine(x + 18f, y, x + 18f, bottomY, shaftEdge)
        canvas.drawLine(x + width - 18f, y, x + width - 18f, bottomY, shaftEdge)
        // 벽에 금 몇 줄
        for (k in 0 until 4) {
            val cy = y + 50f + k * 70f
            canvas.drawLine(x + 4f, cy, x + 16f, cy + 18f, shaftEdge)
            canvas.drawLine(x + width - 4f, cy + 30f, x + width - 16f, cy + 46f, shaftEdge)
        }
        // 눈 덮인 가장자리
        canvas.drawRoundRect(x - 26f, y - 8f, x + 12f, y + 8f, 8f, 8f, shaftRim)
        canvas.drawRoundRect(x + width - 12f, y - 8f, x + width + 26f, y + 8f, 8f, 8f, shaftRim)
        val cx = x + width / 2f
        markPath.reset()
        markPath.moveTo(cx, y - 78f); markPath.lineTo(cx + 23f, y - 36f); markPath.lineTo(cx - 23f, y - 36f); markPath.close()
        canvas.drawPath(markPath, markPaint)
        canvas.drawPath(markPath, markEdgePaint)
        canvas.drawLine(cx, y - 66f, cx, y - 49f, markSignPaint)
        canvas.drawPoint(cx, y - 42f, markSignPaint)
    }

    companion object {
        private const val STEPS = 8
        /** 수직 구덩이 폭 (Claude 판단) */
        const val SHAFT_W = 130f
        /** 낭떠러지 벽 그림에서 지면(풀 줄)까지 비율 (그림에서 잰 값) */
        private const val CLIFF_GROUND = 0.06f
    }
}
