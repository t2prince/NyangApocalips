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
        // ===== 상점가 새 물건 (대표님 2026-10-09 「상점가 물건 늘려줘, 너가 그냥 만들면 돼」 — 이름·값·효과 Claude 판단, 그림 GPT) =====
        Item("milk", "고양이 우유", 4, "부드러운 우유 한 그릇"),
        Item("salmon", "연어구이", 9, "고소한 연어 한 토막"),
        Item("cat_cake", "고양이 케이크", 18, "특별한 날의 케이크"),
        Item("mouse_toy", "쥐 인형", 6, "태엽을 감으면 달린다"),
        Item("feather_wand", "깃털 막대", 9, "알록달록 깃털이 살랑"),
        Item("laser_pointer", "레이저 포인터", 14, "빨간 점을 쫓아 신난다"),
    )

    /** 처음 고양이 상점(1번 천막)에 진열하는 물건 — 예전 그대로 */
    val catShopIds: List<String> = listOf("churu", "tuna_can", "feed", "catnip", "ball", "fish_toy", "brush", "water_bowl",
        "cushion", "scratcher", "litter", "cat_tower", "cat_armor", "revive_potion", "revive_all", "attack_up")

    /** 상점가 천막별 가게 (천막 번호 0부터) → (가게 이름, 진열 물건). 1번(0) 고양이 상점, 2번(1) 고양이 기술, 6번(5) 동물 병원은 따로 */
    val tentShops: Map<Int, Pair<String, List<String>>> = mapOf(
        2 to ("간식 가게" to listOf("churu", "tuna_can", "feed", "catnip", "milk", "salmon", "cat_cake")),
        3 to ("장난감 가게" to listOf("ball", "fish_toy", "mouse_toy", "feather_wand", "laser_pointer")),
        4 to ("살림 가게" to listOf("brush", "water_bowl", "cushion", "scratcher", "litter", "cat_tower")),
    )

    /** 물건 값 — 안내 문구에 값을 박아 두지 말고 여기서 읽는다 */
    fun priceOf(id: String): Int = items.firstOrNull { it.id == id }?.price ?: 0
}
