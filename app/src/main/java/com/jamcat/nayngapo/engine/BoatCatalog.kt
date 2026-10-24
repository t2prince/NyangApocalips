package com.jamcat.nayngapo.engine

import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint

/**
 * 조선소에서 파는 배들 (대표님 2026-10-24 「조선소에서는 여러 종류의 배를 팔아」「배가 이미 있어도 들어가서 배를 구경하며 살 수 있어」「조선소에 배는 많아」).
 * 산 배는 가방의 물건 하나(id)로 센다 — 여러 척 가지고 조선소에서 탈 배를 고른다. 가라앉으면 그 배를 잃는다.
 * 종류·값·하트·공격력·빠르기는 Claude 판단. 그림은 어선 그림(fishing_boat)에 색을 입혀 쓴다 (흰 배 몸통이 그 색이 된다).
 */
object BoatCatalog {

    class Boat(
        val id: String, val name: String, val price: Int,
        /** 배 하트 · 그물/주먹 공격력 · 조타 빠르기 배수 */
        val hp: Int, val attack: Int, val speed: Float,
        /** 몸통 색 (흰색 → 이 색), null 이면 그림 그대로 */
        val tint: Int?, val desc: String,
    ) {
        val paint: Paint? = tint?.let { c -> Paint().apply { isFilterBitmap = true; colorFilter = LightingColorFilter(c, 0) } }
    }

    val boats: List<Boat> = listOf(
        Boat("fishing_boat", "어선", 30, 10, 2, 1.0f, null, "흰 배에 파란 줄 · 기본 배"),
        Boat("boat_rowboat", "나룻배", 15, 6, 1, 0.8f, Color.rgb(214, 170, 110), "작고 싸다 · 느리다"),
        Boat("boat_motor", "모터보트", 80, 8, 2, 1.6f, Color.rgb(250, 120, 110), "아주 빠르다"),
        Boat("boat_yacht", "요트", 120, 12, 2, 1.3f, Color.rgb(170, 220, 255), "빠르고 튼튼하다"),
        Boat("boat_sail", "범선", 150, 14, 3, 1.0f, Color.rgb(240, 214, 150), "그물이 세다"),
        Boat("boat_cruise", "유람선", 250, 18, 2, 1.1f, Color.rgb(255, 240, 200), "아주 튼튼하다"),
        Boat("boat_police", "경비정", 320, 16, 4, 1.4f, Color.rgb(150, 170, 255), "빠르고 세다"),
        Boat("boat_iron", "철갑선", 400, 25, 4, 0.9f, Color.rgb(160, 166, 176), "가장 단단한 배"),
        Boat("boat_pirate", "해적선", 600, 20, 5, 1.2f, Color.rgb(120, 90, 70), "해적에게서 빼앗은 듯한 배"),
        Boat("boat_gold", "황금배", 1000, 30, 6, 1.5f, Color.rgb(255, 210, 70), "제일 좋은 배"),
    )

    fun byId(id: String) = boats.firstOrNull { it.id == id }

    fun owns(id: String) = GameProgress.itemCount(id) > 0
    val ownsAny: Boolean get() = boats.any { owns(it.id) }

    /** 지금 탈 배 — 고른 배를 안 가졌으면 가진 배 중 첫 번째 */
    val current: Boat? get() = byId(GameProgress.selectedBoat)?.takeIf { owns(it.id) } ?: boats.firstOrNull { owns(it.id) }

    fun buy(b: Boat): Boolean {
        if (owns(b.id)) return false
        if (!GameProgress.buyItem(b.id, b.price)) return false
        GameProgress.selectBoat(b.id)
        return true
    }

    /** 가라앉았다 — 탄 배를 잃는다 */
    fun lose(b: Boat) { GameProgress.consumeItem(b.id) }
}
