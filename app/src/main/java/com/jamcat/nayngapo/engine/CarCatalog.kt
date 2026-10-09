package com.jamcat.nayngapo.engine

/**
 * 자동차 (대표님 2026-10-09 「상점에서 자동차 팔게 해 줘, 맨 뒤 자동차 매장에서 팔아」 「스테이지에서는 자동차 선택에서 선택해 타고 다닐 수 있어」).
 * 상점 마을 맨 끝 자동차 매장에서 루비로 사고, 매장의 '자동차 선택'에서 고른 차를 땅 스테이지에서 타고 다닌다(빨라진다).
 * 바다(31)는 배냥이, 하늘은 날아냥이를 타므로 차를 안 탄다. 차 종류·값·빠르기는 Claude 판단, 그림 GPT (car_*).
 */
object CarCatalog {
    data class Car(val id: String, val name: String, val price: Int, val speedMul: Float)

    val cars = listOf(
        // 자동차는 빠르다 (대표님 2026-10-09) — 걷기의 2·2.5·3배
        Car("car_red", "빨간 오픈카", 100, 2.0f),
        Car("car_blue", "파란 지프", 200, 2.5f),
        Car("car_yellow", "노란 스포츠카", 300, 3.0f),
    )

    fun owned(id: String) = GameProgress.itemCount(id) > 0

    /** 지금 타기로 고른 차 (없거나 안 가졌으면 null) */
    val selected: Car? get() = cars.firstOrNull { it.id == GameProgress.selectedCar && owned(it.id) }

    /** 고른 차가 어느 스테이지에 방치돼 있으면 그 스테이지 번호 (아니면 0) */
    val abandonedStage: Int get() =
        if (selected != null && GameProgress.abandonedCarId == selected?.id) GameProgress.abandonedCarStage else 0

    /** 여기서 탈 수 있는 차 — 방치차는 그 스테이지에서만 (그 자리에 세워져 있다) */
    fun availableIn(stage: Int?): Car? = selected?.takeIf { abandonedStage == 0 || abandonedStage == stage }
}
