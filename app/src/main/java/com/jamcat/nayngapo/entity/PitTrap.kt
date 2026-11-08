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

    // ===== 삽질하는 냥이 (대표님 2026-11-08 「구덩이 함정은 고양이가 몇 초 동안 파고 있다가 삽을 버리고 도망가」) =====
    // 화면에 보이면 왼쪽 턱에서 삽질하다가 DIG_TIME 뒤 삽을 던지고 인간 반대쪽으로 달아난다. 싸우지 않는다 (Claude 판단)
    private enum class Digger { WAIT, DIG, RUN, GONE }
    private var digger = Digger.WAIT
    private var digT = 0f
    private var diggerX = x - 70f
    private var runDir = -1f
    private var shovelDropped = false
    private val clods = mutableListOf<FloatArray>()   // 튀는 흙: x, y, vx, vy, 남은 시간

    /** 카메라 왼쪽·오른쪽 끝과 인간 x 를 받아 삽질 냥이를 움직인다 */
    fun update(dt: Float, playerX: Float, camLeft: Float, camRight: Float) {
        clods.removeAll { c -> c[4] -= dt; c[0] += c[2] * dt; c[1] += c[3] * dt; c[3] += 1400f * dt; c[4] <= 0f }
        when (digger) {
            Digger.WAIT -> if (x + width > camLeft && x < camRight) digger = Digger.DIG
            Digger.DIG -> {
                digT += dt
                // 삽을 꽂을 때마다 흙 한 줌이 구덩이 밖으로 튄다
                if ((digT * DIG_RATE).toInt() != ((digT - dt) * DIG_RATE).toInt())
                    repeat(3) { clods.add(floatArrayOf(x + 30f, y + 10f, -120f - (Math.random() * 160).toFloat(), -420f - (Math.random() * 200).toFloat(), 0.7f)) }
                if (digT >= DIG_TIME) {
                    digger = Digger.RUN
                    shovelDropped = true
                    runDir = if (playerX < x + width / 2f) 1f else -1f
                    if (runDir > 0f) diggerX = x + width + 20f   // 인간이 왼쪽이면 구덩이를 건너뛴 듯 오른쪽으로
                }
            }
            Digger.RUN -> {
                diggerX += runDir * RUN_SPEED * dt
                digT += dt
                if (diggerX < camLeft - 300f || diggerX > camRight + 300f) digger = Digger.GONE
            }
            Digger.GONE -> Unit
        }
    }

    private val clodPaint = Paint().apply { color = Color.rgb(120, 82, 50); isAntiAlias = true }
    private val digRect = RectF()

    private fun drawDigger(canvas: Canvas) {
        if (shovelDropped) com.jamcat.nayngapo.engine.SpriteManager.pitShovel?.let { s ->
            digRect.set(x - 150f, y - s.height.toFloat() + 6f, x - 150f + s.width, y + 6f)
            canvas.drawBitmap(s, null, digRect, null)
        }
        for (c in clods) canvas.drawCircle(c[0], c[1], 7f, clodPaint)
        when (digger) {
            Digger.DIG -> com.jamcat.nayngapo.engine.SpriteManager.pitDigger?.let { b ->
                // 삽질: 몸이 위아래로 꾸벅 (삽 날이 구덩이 턱에 닿게)
                val bob = kotlin.math.abs(kotlin.math.sin(digT * DIG_RATE * Math.PI.toFloat())) * 10f
                digRect.set(x + 40f - b.width, y - b.height + bob, x + 40f, y + bob)
                canvas.drawBitmap(b, null, digRect, null)
            }
            Digger.RUN -> {
                val frames = com.jamcat.nayngapo.engine.SpriteManager.catWalk
                val f = frames[((digT * 12f).toInt()) % frames.size]
                val bmp = if (runDir > 0f) f else com.jamcat.nayngapo.engine.SpriteManager.getFlipped(f)
                val h = 100f; val w = bmp.width * h / bmp.height
                digRect.set(diggerX - w / 2f, y - h, diggerX + w / 2f, y)
                canvas.drawBitmap(bmp, null, digRect, null)
            }
            else -> Unit
        }
    }

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

    private val pitRect = RectF()

    fun draw(canvas: Canvas) {
        // GPT 그림 (2026-11-08): 흙 틀 + 캄캄한 속 + 쇠꼬챙이. 위 가장자리 = 지면, 폭·깊이에 맞춰 늘린다
        com.jamcat.nayngapo.engine.SpriteManager.traps["trap_pit"]?.let { b ->
            pitRect.set(x - 26f, y - 10f, x + width + 26f, y + depth)
            canvas.drawBitmap(b, null, pitRect, null)
            drawWarnSign(canvas)
            drawDigger(canvas)
            return
        }
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

        drawWarnSign(canvas)
        drawDigger(canvas)
    }

    private fun drawWarnSign(canvas: Canvas) {
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
        const val DIG_TIME = 3f        // 삽질하는 시간 (Claude 판단)
        const val DIG_RATE = 2.5f      // 1초에 삽질 횟수
        const val RUN_SPEED = 520f
        const val WALL_STEPS = 10
    }
}
