package com.jamcat.nayngapo.engine

import kotlin.math.sin

/**
 * 지형 표면 — 슈퍼마리오처럼 오르내리는 언덕과 계단식 고원(대표님 2026-09-20 「맵이 평탄해서 재미가 덜해」).
 * · 큰 언덕: 사인파 조합, 스테이지가 오를수록 높아진다 (1스테이지 ~50px → 20스테이지 ~130px)
 * · 고원: 일정 구간마다 부드러운 계단(smoothstep)으로 땅이 한 단 올라갔다 내려온다 — 위치·높이는 스테이지 씨앗으로 고정
 * · 잔굴곡: 얕게
 * 표면은 어디서나 연속(급단차 없음)이라 걷는 것마다 y = groundY(x) 로 따라간다. 낭떠러지는 별도(Level.chasms).
 */
object Terrain {
    var stage = 1

    /** 최고 지점 — 이보다 위로는 안 솟는다 (HUD·하늘 여백 확보) */
    private const val MAX_RISE = 300f

    /** 월드 x 좌표에서의 지면 표면 y (기준선 GROUND_Y에서 위로 솟는다) */
    fun groundY(x: Float): Float {
        val amp = 50f + stage * 4f              // 큰 언덕 높이: 1스테이지 54, 20스테이지 130
        val rough = 4f + stage * 0.6f           // 잔굴곡

        val hills = (sin(x * 0.0031f) * 0.55f + sin(x * 0.0011f + 1.3f) * 0.45f) * 0.5f + 0.5f   // 0~1
        val bumps = sin(x * 0.013f + 0.7f) * 0.5f + 0.5f
        val rise = hills * amp + bumps * rough + plateau(x)
        return Constants.GROUND_Y - rise.coerceIn(0f, MAX_RISE)
    }

    /** 고원 — 1600px 마다 한 구간, 구간마다 씨앗으로 정한 높이(0 이면 평지)로 올라섰다가 내려온다 */
    private fun plateau(x: Float): Float {
        val period = 1600f
        val ramp = 220f                          // 오르내리는 경사 길이 (짧을수록 가파르다)
        val k = kotlin.math.floor(x / period).toLong()
        val local = x - k * period
        val h = plateauHeight(k)
        if (h <= 0f) return 0f
        // 구간의 앞 15% 는 오르막, 뒤 15% 는 내리막, 가운데는 평평한 고원
        val up = smooth((local - 120f) / ramp)
        val down = 1f - smooth((local - (period - 120f - ramp)) / ramp)
        return h * minOf(up, down).coerceIn(0f, 1f)
    }

    /** 구간별 고원 높이 — 셋 중 하나꼴로 평지, 나머지는 40~110px (스테이지가 오르면 더 높게) */
    private fun plateauHeight(k: Long): Float {
        val seed = ((k * 7919L + stage * 104729L) % 1000L + 1000L) % 1000L
        if (k <= 0L || seed % 3L == 0L) return 0f           // 첫 구간(시작 지점)은 평지
        return 40f + (seed % 70L) + stage * 2f
    }

    private fun smooth(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }
}
