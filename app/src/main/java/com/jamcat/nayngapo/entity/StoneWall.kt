package com.jamcat.nayngapo.entity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * 인간 기술 '돌벽' — 인간 앞 땅에서 길고 뾰족한 돌이 차례로 솟는다.
 * 솟은 돌가시에 닿은 적은 한 번씩만 찔린다. 잠시 서 있다가 땅속으로 가라앉는다.
 */
class StoneWall(
    /** 돌가시 자리 (월드 x, 그 자리의 지면 y) — 낭떠러지 위는 부르는 쪽에서 빼고 넘긴다 */
    private val spikeX: FloatArray,
    private val spikeGround: FloatArray,
    /** 찔린 적이 받는 피해 (기술의 위력) */
    val power: Int
) {
    companion object {
        /** 인간 앞으로 늘어서는 돌가시 수와 간격 */
        const val SPIKES = 6
        const val SPACING = 64f
        /** 인간 몸 중심에서 첫 돌가시까지 */
        const val FIRST_OFFSET = 90f

        /** 돌가시 하나가 솟기 시작하는 간격 (앞으로 번져 나간다) */
        private const val STAGGER = 0.06f
        private const val RISE_TIME = 0.12f
        /** 다 솟은 뒤 서 있는 시간 */
        private const val HOLD_TIME = 0.7f
        private const val SINK_TIME = 0.25f

        private const val BASE_WIDTH = 48f
        /** 돌가시 높이 — 가운데 것일수록 조금 더 길다 */
        private val HEIGHTS = floatArrayOf(150f, 180f, 205f, 215f, 195f, 170f)

        private val DURATION = STAGGER * (SPIKES - 1) + RISE_TIME + HOLD_TIME + SINK_TIME
    }

    private var t = 0f

    val isDone: Boolean get() = t >= DURATION

    /** 이미 찔린 적 — 한 번만 때린다 */
    val hit = HashSet<Enemy>()

    /** 돌가시가 막 솟았을 때 흙먼지를 한 번만 뿜는다 */
    private val dustDone = BooleanArray(spikeX.size)

    fun update(deltaTime: Float) {
        t += deltaTime
    }

    /** i번 돌가시가 지금 솟은 비율 (0 = 땅속, 1 = 다 솟음) */
    private fun raise(i: Int): Float {
        val local = t - i * STAGGER
        if (local <= 0f) return 0f
        if (local < RISE_TIME) return local / RISE_TIME
        val sinkStart = STAGGER * (SPIKES - 1) + RISE_TIME + HOLD_TIME
        if (t > sinkStart) return (1f - (t - sinkStart) / SINK_TIME).coerceIn(0f, 1f)
        return 1f
    }

    private fun height(i: Int) = HEIGHTS[i % HEIGHTS.size] * raise(i)

    /** 막 솟아오른 돌가시 자리 — 부르는 쪽이 흙먼지를 뿜는다 (한 번씩만 돌려준다) */
    fun takeFreshSpikes(out: MutableList<Int>) {
        out.clear()
        for (i in spikeX.indices) {
            if (!dustDone[i] && raise(i) > 0f) { dustDone[i] = true; out.add(i) }
        }
    }

    fun spikeXOf(i: Int) = spikeX[i]
    fun groundOf(i: Int) = spikeGround[i]

    /** 이 적 몸통이 지금 반쯤 이상 솟은 돌가시에 닿았나 (가라앉는 중에는 찌르지 않는다) */
    fun touches(box: RectF): Boolean {
        val sinking = t > STAGGER * (SPIKES - 1) + RISE_TIME + HOLD_TIME
        if (sinking) return false
        for (i in spikeX.indices) {
            if (raise(i) < 0.5f) continue
            val half = BASE_WIDTH / 2f
            if (box.right < spikeX[i] - half || box.left > spikeX[i] + half) continue
            val top = spikeGround[i] - height(i)
            if (box.bottom >= top && box.top <= spikeGround[i]) return true
        }
        return false
    }

    private val stoneDark = Paint().apply { color = Color.rgb(118, 116, 124); isAntiAlias = true }
    private val stoneLight = Paint().apply { color = Color.rgb(172, 170, 178); isAntiAlias = true }
    private val inkPaint = Paint().apply {
        color = Color.rgb(40, 36, 34); style = Paint.Style.STROKE
        strokeWidth = 4f; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
    }
    private val crackPaint = Paint().apply {
        color = Color.rgb(70, 68, 74); style = Paint.Style.STROKE
        strokeWidth = 2.5f; isAntiAlias = true
    }
    private val moundPaint = Paint().apply { color = Color.rgb(120, 92, 62); isAntiAlias = true }
    private val path = Path()
    private val oval = RectF()

    fun draw(canvas: Canvas) {
        for (i in spikeX.indices) {
            val h = height(i)
            if (h <= 1f) continue
            val x = spikeX[i]
            val g = spikeGround[i]
            val half = BASE_WIDTH / 2f
            // 끝이 살짝 기운 길쭉한 돌가시 (짝·홀로 기우는 쪽을 바꾼다)
            val lean = if (i % 2 == 0) 6f else -6f

            path.reset()
            path.moveTo(x - half, g)
            path.lineTo(x - half * 0.55f, g - h * 0.55f)
            path.lineTo(x + lean, g - h)
            path.lineTo(x + half * 0.6f, g - h * 0.5f)
            path.lineTo(x + half, g)
            path.close()
            canvas.drawPath(path, stoneDark)

            // 밝은 면 (왼쪽 절반)
            path.reset()
            path.moveTo(x - half, g)
            path.lineTo(x - half * 0.55f, g - h * 0.55f)
            path.lineTo(x + lean, g - h)
            path.lineTo(x - 2f, g)
            path.close()
            canvas.drawPath(path, stoneLight)

            // 윤곽
            path.reset()
            path.moveTo(x - half, g)
            path.lineTo(x - half * 0.55f, g - h * 0.55f)
            path.lineTo(x + lean, g - h)
            path.lineTo(x + half * 0.6f, g - h * 0.5f)
            path.lineTo(x + half, g)
            canvas.drawPath(path, inkPaint)

            // 금 두 줄
            canvas.drawLine(x + 4f, g - h * 0.35f, x + 12f, g - h * 0.22f, crackPaint)
            canvas.drawLine(x - 10f, g - h * 0.7f, x - 4f, g - h * 0.6f, crackPaint)

            // 뚫고 나온 자리의 흙 둔덕
            oval.set(x - half - 10f, g - 10f, x + half + 10f, g + 8f)
            canvas.drawOval(oval, moundPaint)
            canvas.drawOval(oval, inkPaint)
        }
    }
}
