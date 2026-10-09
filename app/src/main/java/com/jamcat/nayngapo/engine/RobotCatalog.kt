package com.jamcat.nayngapo.engine

/**
 * 로봇 (대표님 2026-10-09 「11 상점은 로봇 상점. 로봇 피 100, 인간이 탈 수 있어, 공격 50, 루비 500, Lv2 는 두 배, 다 똑같다」).
 * Lv1 = 피 100 · 공격 50 · 500루비. Lv 이 하나 오를 때마다 피·공격·값이 모두 두 배 (Lv2 = 200 · 100 · 1000루비 …).
 * 로봇 상점에서 '로봇 타기'를 골라 두면 땅 스테이지에서 로봇을 타고 시작한다(바다·하늘 빼고, 차보다 로봇이 먼저).
 * 최대 Lv 10 · 맞으면 하트 하나당 로봇 피 10 · 부서지면 인간이 내린다는 Claude 판단.
 */
object RobotCatalog {
    const val BASE_HP = 100
    const val BASE_ATTACK = 50
    const val BASE_PRICE = 500
    const val MAX_LEVEL = 10
    /** 인간이 하트 1개 잃을 피해 = 로봇 피 10 */
    const val HP_PER_HEART = 10

    private fun pow2(n: Int) = 1 shl (n - 1).coerceAtLeast(0)
    fun hpOf(level: Int) = BASE_HP * pow2(level)
    fun attackOf(level: Int) = BASE_ATTACK * pow2(level)
    /** 그 Lv 로봇의 값 (Lv1 = 사기, Lv2~ = 올리기) */
    fun priceOf(level: Int) = BASE_PRICE * pow2(level)

    val owned: Boolean get() = GameProgress.robotLevel > 0
    /** 스테이지에서 탈 로봇 Lv (안 탈 거면 0) */
    val rideLevel: Int get() = if (owned && GameProgress.rideRobot) GameProgress.robotLevel else 0
}
