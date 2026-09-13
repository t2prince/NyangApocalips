package com.jamcat.nayngapo.entity

import android.graphics.*

/**
 * 인간의 앞길을 막는 벽. 스테이지 테마에 맞는 재질로 땅에서 솟아오른다.
 * 공격 세 번으로 부수거나 점프로 뛰어넘어야 지나갈 수 있다.
 */
class Wall(val x: Float, val y: Float, val style: Style) {

    /** 스테이지 테마별 벽 재질 */
    enum class Style { BRICK, ICE, SANDSTONE, BASALT }

    val width = 70f
    val height = 150f          // 점프 최고 높이(200)보다 낮아 뛰어넘을 수 있다

    private var hp = 3
    var isBroken = false
        private set
    /** 부서진 뒤 잔해가 사라지기까지 남은 시간 */
    var debrisTimer = 0f
        private set
    private var hitFlash = 0f
    /** 땅에서 솟아오르는 연출에 남은 시간 */
    private var riseTimer = RISE_TIME

    /** 아직 다 올라오지 않았으면 막지 않는다 */
    val isSolid: Boolean get() = !isBroken && riseTimer <= 0f

    val hitBox: RectF get() = RectF(x, y - height, x + width, y + 6f)

    // 재질별 색 (밝은 면 / 어두운 면 / 줄눈 / 균열)
    private val light = when (style) {
        Style.BRICK -> Color.rgb(168, 92, 74)
        Style.ICE -> Color.rgb(176, 220, 244)
        Style.SANDSTONE -> Color.rgb(224, 190, 124)
        Style.BASALT -> Color.rgb(74, 70, 76)
    }
    private val dark = when (style) {
        Style.BRICK -> Color.rgb(138, 72, 58)
        Style.ICE -> Color.rgb(140, 196, 228)
        Style.SANDSTONE -> Color.rgb(198, 162, 100)
        Style.BASALT -> Color.rgb(50, 46, 52)
    }
    private val jointColor = when (style) {
        Style.BRICK -> Color.rgb(226, 216, 200)
        Style.ICE -> Color.rgb(240, 252, 255)
        Style.SANDSTONE -> Color.rgb(240, 224, 186)
        Style.BASALT -> Color.rgb(96, 90, 96)
    }
    private val crackColor = when (style) {
        Style.BRICK -> Color.rgb(60, 40, 34)
        Style.ICE -> Color.rgb(96, 150, 186)
        Style.SANDSTONE -> Color.rgb(120, 90, 50)
        Style.BASALT -> Color.rgb(230, 110, 40)      // 현무암은 균열에서 용암 빛이 샌다
    }

    private val facePaint = Paint().apply { isAntiAlias = true }
    private val jointPaint = Paint().apply {
        color = jointColor; style = Paint.Style.STROKE
        strokeWidth = 4f; isAntiAlias = true
    }
    private val crackPaint = Paint().apply {
        color = crackColor; style = Paint.Style.STROKE
        strokeWidth = 4f; isAntiAlias = true
    }
    private val glossPaint = Paint().apply { color = Color.argb(90, 255, 255, 255); isAntiAlias = true }
    private val flashPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val dustPaint = Paint().apply { color = Color.argb(150, 220, 210, 195); isAntiAlias = true }
    private val rect = RectF()

    fun update(deltaTime: Float) {
        if (riseTimer > 0f) riseTimer -= deltaTime
        if (hitFlash > 0f) hitFlash -= deltaTime
        if (isBroken && debrisTimer > 0f) debrisTimer -= deltaTime
    }

    /** 공격을 맞으면 금이 가고, 세 번 맞으면 무너진다 */
    fun takeHit() {
        if (isBroken) return
        hp--
        hitFlash = 0.12f
        if (hp <= 0) {
            isBroken = true
            debrisTimer = 0.5f
        }
    }

    fun draw(canvas: Canvas) {
        if (isBroken) {
            // 무너지는 잔해: 조각이 바닥으로 흩어진다
            val t = 1f - (debrisTimer / 0.5f).coerceIn(0f, 1f)
            facePaint.color = dark
            for (i in 0 until 6) {
                val bx = x + (i % 3) * 24f + t * ((i % 2) * 2 - 1) * 60f
                val by = y - 20f - (i / 3) * 26f + t * 40f
                canvas.drawRect(bx, by, bx + 20f, by + 16f, facePaint)
            }
            return
        }

        // 솟아오르는 중이면 땅속에 잠긴 만큼 잘라 그린다
        val rise = (1f - riseTimer / RISE_TIME).coerceIn(0f, 1f)
        val shownH = height * rise
        val top = y - shownH

        canvas.save()
        canvas.clipRect(x - 12f, top, x + width + 12f, y + 8f)

        // 블록 6단, 단마다 어긋나게 쌓는다
        val rows = 6
        val rowH = height / rows
        for (r in 0 until rows) {
            val rowTop = y - height + r * rowH
            facePaint.color = if (r % 2 == 0) light else dark
            rect.set(x, rowTop, x + width, rowTop + rowH)
            canvas.drawRect(rect, facePaint)

            val offset = if (r % 2 == 0) 0f else width / 4f
            canvas.drawLine(x + offset, rowTop, x + offset, rowTop + rowH, jointPaint)
            canvas.drawLine(x + offset + width / 2f, rowTop, x + offset + width / 2f, rowTop + rowH, jointPaint)
            canvas.drawLine(x, rowTop, x + width, rowTop, jointPaint)
        }
        canvas.drawLine(x, y, x + width, y, jointPaint)

        // 얼음은 표면에 빛나는 결이 있다
        if (style == Style.ICE) {
            canvas.drawLine(x + 16f, y - height + 24f, x + 30f, y - 20f, glossPaint)
            canvas.drawLine(x + 44f, y - height + 40f, x + 56f, y - 34f, glossPaint)
        }

        // 남은 체력만큼 금이 간다
        if (hp <= 2) canvas.drawLine(x + 14f, y - height + 20f, x + 44f, y - height * 0.45f, crackPaint)
        if (hp <= 1) canvas.drawLine(x + 52f, y - height * 0.9f, x + 26f, y - 18f, crackPaint)

        // 맞은 순간 번쩍
        if (hitFlash > 0f) {
            flashPaint.alpha = (hitFlash / 0.12f * 160).toInt().coerceIn(0, 255)
            canvas.drawRect(x, y - height, x + width, y, flashPaint)
        }
        canvas.restore()

        // 솟아오를 때 밑동에서 흙먼지가 인다
        if (riseTimer > 0f) {
            dustPaint.alpha = (150 * (riseTimer / RISE_TIME)).toInt().coerceIn(0, 255)
            for (i in 0 until 5) {
                val dx = x - 10f + i * 22f
                val r = 10f + (i % 3) * 6f
                canvas.drawCircle(dx, y - 4f - (i % 2) * 8f, r, dustPaint)
            }
        }
    }

    private companion object {
        /** 땅에서 다 솟아오르는 데 걸리는 시간 */
        const val RISE_TIME = 0.45f
    }
}
