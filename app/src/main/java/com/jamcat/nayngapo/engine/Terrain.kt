package com.jamcat.nayngapo.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * 지형 표면 — 슈퍼마리오처럼 오르내리는 땅(대표님 2026-09-20 「맵이 평탄해서 재미가 덜해」).
 * 2026-09-27 「스테이지 지형이 다 비슷하다, 다 다르게」: 스테이지마다 지형 모양(Shape)과 크기를 따로 정한다.
 *  · ROLLING  둥근 언덕이 이어진다
 *  · DUNES    길게 오르고 짧게 내려가는 모래언덕
 *  · MESA     가파른 벽으로 솟은 평평한 고원
 *  · PEAKS    뾰족한 봉우리가 줄지어 선다
 *  · STAIRS   계단처럼 한 단씩 올라갔다 내려온다
 *  · VALLEY   높은 땅 사이로 움푹 꺼진 골짜기
 *  · MOUNTAIN 평지 사이에 큰 산이 우뚝
 *  · TERRACES 넓고 평평한 단(보스 싸움터)
 * 표면은 어디서나 연속이라 걷는 것마다 y = groundY(x) 로 따라간다. 낭떠러지는 별도(Level.chasms).
 * 모양 배정·수치는 Claude 판단.
 */
object Terrain {
    var stage = 1

    /** 최고 지점 — 이보다 위로는 안 솟는다 (HUD·하늘 여백 확보) */
    private const val MAX_RISE = 300f

    /** 출발 지점은 평평하게 — 이 거리 동안 지형이 서서히 생긴다 */
    private const val START_FLAT = 500f
    private const val START_FADE = 600f

    private enum class Shape { FLAT, ROLLING, DUNES, MESA, PEAKS, STAIRS, VALLEY, MOUNTAIN, TERRACES }

    /** height: 모양의 최대 높이, period: 한 굽이의 길이, rough: 잔굴곡, phase: 스테이지마다 어긋나게 */
    private class Profile(val shape: Shape, val height: Float, val period: Float, val rough: Float, val phase: Float)

    /** 상점 마을(평평한 초원)은 stage = SHOP 으로 둔다 */
    const val SHOP = 0

    private fun profile(s: Int): Profile = when (s) {
        SHOP -> Profile(Shape.FLAT, 0f, 1000f, 0f, 0f)
        // 초원
        1 -> Profile(Shape.ROLLING, 60f, 1500f, 4f, 0.0f)
        2 -> Profile(Shape.DUNES, 80f, 1100f, 3f, 0.3f)
        3 -> Profile(Shape.MESA, 90f, 1400f, 3f, 0.5f)
        4 -> Profile(Shape.PEAKS, 90f, 700f, 2f, 0.2f)
        5 -> Profile(Shape.STAIRS, 150f, 1800f, 3f, 0.0f)
        6 -> Profile(Shape.VALLEY, 140f, 1600f, 5f, 0.4f)
        7 -> Profile(Shape.ROLLING, 150f, 2600f, 6f, 0.7f)
        8 -> Profile(Shape.MOUNTAIN, 220f, 2400f, 4f, 0.1f)
        9 -> Profile(Shape.MESA, 160f, 1200f, 5f, 0.8f)
        10 -> Profile(Shape.TERRACES, 110f, 2200f, 2f, 0.3f)
        // 정글
        12 -> Profile(Shape.PEAKS, 130f, 900f, 4f, 0.6f)
        13 -> Profile(Shape.VALLEY, 200f, 2000f, 6f, 0.1f)
        14 -> Profile(Shape.DUNES, 160f, 1500f, 5f, 0.5f)
        15 -> Profile(Shape.STAIRS, 220f, 2200f, 4f, 0.2f)
        16 -> Profile(Shape.MOUNTAIN, 260f, 1900f, 6f, 0.9f)
        17 -> Profile(Shape.MESA, 200f, 1600f, 6f, 0.3f)
        18 -> Profile(Shape.ROLLING, 170f, 1000f, 8f, 0.4f)
        19 -> Profile(Shape.PEAKS, 190f, 1300f, 6f, 0.7f)
        20 -> Profile(Shape.TERRACES, 160f, 2600f, 3f, 0.6f)
        // 꿀
        21 -> Profile(Shape.DUNES, 110f, 1300f, 4f, 0.2f)
        22 -> Profile(Shape.ROLLING, 120f, 1400f, 5f, 0.3f)
        23 -> Profile(Shape.MESA, 150f, 1500f, 4f, 0.6f)
        24 -> Profile(Shape.PEAKS, 150f, 1000f, 4f, 0.1f)
        25 -> Profile(Shape.STAIRS, 180f, 2000f, 3f, 0.4f)
        26 -> Profile(Shape.VALLEY, 170f, 1800f, 5f, 0.2f)
        27 -> Profile(Shape.MOUNTAIN, 230f, 2200f, 5f, 0.7f)
        28 -> Profile(Shape.DUNES, 170f, 1600f, 5f, 0.5f)
        29 -> Profile(Shape.MESA, 200f, 1300f, 6f, 0.8f)
        30 -> Profile(Shape.TERRACES, 150f, 2400f, 3f, 0.4f)
        // 해적 (바닷가 모래밭)
        31 -> Profile(Shape.FLAT, 0f, 1700f, 0f, 0f)        // 바다 — 물은 평평하다 (2026-10-09)
        else -> Profile(Shape.ROLLING, 60f + stage * 4f, 1500f, 4f, 0f)
    }

    private var cachedStage = -1
    private var p = profile(1)

    /** 월드 x 좌표에서의 지면 표면 y (기준선 GROUND_Y에서 위로 솟는다) */
    fun groundY(x: Float): Float {
        if (cachedStage != stage) { p = profile(stage); cachedStage = stage }
        val fade = smooth((x - START_FLAT) / START_FADE)
        val t = x / p.period + p.phase                  // 굽이 단위 좌표
        val k = floor(t).toLong()                        // 몇 번째 굽이인지
        val f = t - k                                    // 굽이 안 위치 0~1
        val shape = when (p.shape) {
            Shape.FLAT -> 0f
            Shape.ROLLING -> (sin(t * 6.2832f) * 0.6f + sin(t * 2.3f + 1.3f) * 0.4f) * 0.5f + 0.5f
            Shape.DUNES -> if (f < 0.75f) smooth(f / 0.75f) else 1f - smooth((f - 0.75f) / 0.25f)
            Shape.MESA -> if (seeded(k) % 3L == 0L) 0f
                else (smooth((f - 0.2f) / 0.1f) - smooth((f - 0.7f) / 0.1f)) * (0.6f + (seeded(k) % 5L) * 0.1f)
            Shape.PEAKS -> (1f - abs(f - 0.5f) * 2.6f).coerceAtLeast(0f) * (0.55f + (seeded(k) % 4L) * 0.15f)   // 곧은 비탈, 봉우리 사이는 평지
            Shape.STAIRS -> {
                // 앞 절반은 4단 오르막, 뒤 절반은 4단 내리막 — 한 단씩 짧은 경사로 잇고 꼭대기 단은 넓게
                val u = if (f < 0.5f) f * 2f else (1f - f) * 2f
                val step = u * 5f
                (floor(step) + smooth((step - floor(step) - 0.8f) / 0.2f)).coerceAtMost(4f) / 4f
            }
            Shape.VALLEY -> 1f - (smooth((f - 0.3f) / 0.12f) - smooth((f - 0.58f) / 0.12f)) * 0.85f
            Shape.MOUNTAIN -> {
                val d = (f - 0.5f) / 0.16f
                exp(-d * d) * (0.7f + (seeded(k) % 4L) * 0.1f)
            }
            Shape.TERRACES -> {
                val lv = (seeded(k) % 3L).toFloat() / 2f                 // 굽이마다 낮은·중간·높은 단
                val prev = (seeded(k - 1) % 3L).toFloat() / 2f
                prev + (lv - prev) * smooth(f / 0.12f)
            }
        }
        val bumps = (sin(x * 0.013f + 0.7f) * 0.5f + 0.5f) * p.rough
        val rise = (shape * p.height + bumps) * fade
        return Constants.GROUND_Y - rise.coerceIn(0f, MAX_RISE)
    }

    /** 굽이마다 고정된 무작위 값 (스테이지 씨앗) */
    private fun seeded(k: Long): Long {
        var h = k * 1103515245L + stage * 12345L + 0x9E3779B9L
        h = (h xor (h ushr 13)) * 1274126177L
        h = h xor (h ushr 16)
        return ((h % 1000L) + 1000L) % 1000L
    }

    private fun smooth(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }
}
