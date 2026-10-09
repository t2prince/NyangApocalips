package com.jamcat.nayngapo.engine

/**
 * 고양이 상점에서 파는 물건 목록.
 * id는 그림 파일 이름과 짝이 맞다 — drawable-nodpi/shop_<id>.png
 */
object ShopCatalog {

    data class Item(
        val id: String,
        val name: String,
        /** 값 (루비) */
        val price: Int,
        val desc: String
    )

    /** 진열 순서 그대로 (간식 → 장난감 → 살림살이). 값은 2026-10-04 에 10배로 올렸다가 2026-10-09 「싸게, 츄르 30→3」으로 원래대로 */
    val items: List<Item> = listOf(
        Item("churu", "츄르", 3, "냥이가 제일 좋아하는 간식"),
        Item("tuna_can", "참치캔", 5, "든든한 한 끼"),
        Item("feed", "사료 한 봉지", 8, "매일 먹는 밥"),
        Item("catnip", "캣닢", 6, "냄새만 맡아도 신난다"),
        Item("ball", "방울 공", 4, "굴리면 딸랑딸랑"),
        Item("fish_toy", "낚싯대 장난감", 10, "흔들면 펄쩍 뛴다"),
        Item("brush", "빗", 7, "털을 곱게 빗어 준다"),
        Item("water_bowl", "물그릇", 5, "시원한 물 한 그릇"),
        Item("cushion", "방석", 15, "폭신한 낮잠 자리"),
        Item("scratcher", "스크래처", 12, "발톱 갈기 좋은 판"),
        Item("litter", "모래 화장실", 20, "깨끗한 냥이 화장실"),
        Item("cat_tower", "캣타워", 30, "냥이들의 아파트"),
        Item("cat_armor", "고양이 갑옷", 25, "출전한 냥이가 받는 피해를 크게 줄인다"),
        Item("revive_potion", "고양이 부활 약", 200, "무덤이 된 냥이를 되살린다"),
        Item("revive_all", "다부활", 500, "내 팀 냥이를 전부 되살린다 (사면 바로)"),   // 대표님 2026-09-20
        Item("attack_up", "공격력 Lv업", 200, "냥이 공격력 +60 (영구)"),
    )

    /** 물건 값 — 안내 문구에 값을 박아 두지 말고 여기서 읽는다 */
    fun priceOf(id: String): Int = items.firstOrNull { it.id == id }?.price ?: 0
}
