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
        val desc: String,
        /** 가질 수 있는 최대 개수 (0 = 끝없이) */
        val max: Int = 0
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
        // 낚시터 물고기 (팔지 않는다 — 낚시로만 얻는다. 그림·간식 줄에 쓰려고 목록에 둔다, 2026-10-10)
        Item("fish_small", "작은 물고기", 0, "낚시터에서 잡은 물고기"),
        Item("fish_big", "큰 물고기", 0, "낚시터에서 잡은 커다란 물고기"),
        Item("fish_gold", "황금 물고기", 0, "아주 드물게 잡히는 황금 물고기"),
        // 바다 생물 — 낚시줄이 바다(깊이 1500 아래)까지 내려가면 잡힌다 (대표님 2026-10-16)
        Item("sea_jelly", "해파리", 0, "바다에서 잡은 해파리"),
        Item("sea_whale", "고래", 0, "바다에서 잡은 고래!"),
        Item("sea_flounder", "광어", 0, "바다에서 잡은 광어"),
        Item("sea_bream", "돔", 0, "바다에서 잡은 돔"),
        Item("sea_trash", "쓰레기", 0, "바다에서 건진 쓰레기"),
        Item("sea_starfish", "불가사리", 0, "바다에서 잡은 불가사리"),   // 2026-10-16 추가
        Item("sea_clam", "조개", 0, "바다에서 잡은 조개"),
        Item("sea_rockfish", "우럭", 0, "바다에서 잡은 우럭"),
        Item("sea_turtle", "거북이", 0, "바다에서 잡은 거북이"),
        Item("sea_shark", "상어", 0, "바다에서 잡은 상어!"),
        // 고양이 GPS (대표님 2026-10-17 「그게 있으면 고양이가 떠나도 어디에 있는지 알 수 있어, 없으면 몰라」 「캣타워에서는 고양이에 GPS를 달 수 있어」)
        // — 캣타워에서 냥이 하나에 하나씩 단다. 값 50은 Claude 판단
        Item("cat_gps", "고양이 GPS", 50, "캣타워에서 냥이에게 단다 · 떠나도 어디 있는지 알 수 있다"),
        // 낚시 상점 (2026-10-17) — 미끼와 낚싯대 그림 (낚싯대 Lv업은 낚시 상점이 따로 판다)
        Item("bait_worm", "지렁이 미끼", 2, "빨리 문다 · 물고기가 바늘로 모인다"),
        Item("bait_shrimp", "새우 미끼", 6, "큰 물고기가 잘 잡힌다"),
        Item("bait_gold", "황금 미끼", 20, "귀한 것이 잘 잡힌다"),
        // 조선소의 어선 (대표님 2026-10-24) — 기습에 지면 다른 산 물건처럼 잃는다. 값 30은 Claude 판단 (ShopScreen.BOAT_PRICE 와 같게)
        Item("fishing_boat", "어선", 30, "낚시터에서 언제든 탄다 · 가라앉으면 잃는다", max = 1),
        Item("fish_rod", "낚싯대", 0, "낚싯대 Lv 이 한 번에 잡는 마릿수"),
        Item("feed_all", "다 먹이기", 10, "내 팀 냥이들 포만감이 다 찬다 (사면 바로)"),   // 대표님 2026-10-10
        Item("revive_all", "다부활", 500, "내 팀 냥이를 전부 되살린다 (사면 바로)"),   // 대표님 2026-09-20
        Item("attack_up", "공격력 Lv업", 200, "냥이 공격력 +60 (영구)"),
        // ===== 상점가 새 물건 (대표님 2026-10-09 「상점가 물건 늘려줘, 너가 그냥 만들면 돼」 — 이름·값·효과 Claude 판단, 그림 GPT) =====
        Item("milk", "고양이 우유", 4, "부드러운 우유 한 그릇"),
        Item("salmon", "연어구이", 9, "고소한 연어 한 토막"),
        Item("cat_cake", "고양이 케이크", 18, "특별한 날의 케이크"),
        Item("mouse_toy", "쥐 인형", 6, "태엽을 감으면 달린다"),
        Item("feather_wand", "깃털 막대", 9, "알록달록 깃털이 살랑"),
        Item("laser_pointer", "레이저 포인터", 14, "빨간 점을 쫓아 신난다"),
        // ===== 인간이 가는 가게 (대표님 2026-10-09 「인간이 가는 상점도 몇 개 만들어 줘」 — 가게·물건·값·효과·최대 개수 Claude 판단, 그림 GPT) =====
        // 장갑·투구·조끼는 사 두면 계속 효과(영구), 물약은 다음 땅 스테이지를 시작할 때 하나씩 저절로 마신다
        Item("h_glove", "권투 장갑", 20, "인간 공격력 +$H_GLOVE_ATK (영구)", max = 10),
        Item("h_gauntlet", "강철 주먹", 45, "인간 공격력 +$H_GAUNTLET_ATK (영구)", max = 5),
        // 총 (대표님 2026-10-09 「무기 상점에는 총이 있고, 그걸 들고 있다가 자동차에 달 수 있어」) — 값·한 개만·장난감 레이저 총 모양은 Claude 판단
        Item("h_gun", "레이저 총", 80, "들고 다니며 공격 버튼으로 총알을 쏜다 · 차를 타면 차에 단다", max = 1),
        // 채집통 (대표님 2026-10-10 「채집통을 가지고 스테이지에 가서 냥이를 때리면 자기 팀 냥이가 돼, 보스만 빼고」) — 한 마리에 하나 쓴다. 값 15·최대 3개는 Claude 판단
        Item("h_bugbox", "채집통", 15, "스테이지에서 냥이를 때리면 내 팀이 된다 (보스 빼고)", max = 3),
        Item("h_helmet", "투구", 40, "인간 하트 +1 (영구)", max = 3),
        Item("h_vest", "가죽 조끼", 60, "인간 하트 +1 (영구)", max = 2),
        Item("h_life", "생명 물약", 15, "다음 스테이지 목숨 +1", max = 3),
        Item("h_heal", "하트 물약", 10, "다음 스테이지 하트 +2", max = 3),
    )

    const val H_GLOVE_ATK = 2
    const val H_GAUNTLET_ATK = 5
    /** 사 둔 장갑·투구·조끼만큼 인간이 세진다 (Player 가 만들어질 때 얹는다) */
    fun humanAttackBonus() = GameProgress.itemCount("h_glove") * H_GLOVE_ATK + GameProgress.itemCount("h_gauntlet") * H_GAUNTLET_ATK
    fun humanHeartBonus() = GameProgress.itemCount("h_helmet") + GameProgress.itemCount("h_vest")

    /** 처음 고양이 상점(1번 천막)에 진열하는 물건 — 예전 그대로 */
    val catShopIds: List<String> = listOf("churu", "tuna_can", "feed", "catnip", "ball", "fish_toy", "brush", "water_bowl",
        "cushion", "scratcher", "litter", "cat_tower", "cat_armor", "revive_potion", "revive_all", "attack_up", "feed_all", "cat_gps")

    /** 상점가 천막별 가게 (천막 번호 0부터) → (가게 이름, 진열 물건). 1번(0) 고양이 상점, 2번(1) 고양이 기술, 6번(5) 동물 병원은 따로 */
    val tentShops: Map<Int, Pair<String, List<String>>> = mapOf(
        2 to ("간식 가게" to listOf("churu", "tuna_can", "feed", "catnip", "milk", "salmon", "cat_cake", "feed_all")),
        3 to ("장난감 가게" to listOf("ball", "fish_toy", "mouse_toy", "feather_wand", "laser_pointer")),
        4 to ("살림 가게" to listOf("brush", "water_bowl", "cushion", "scratcher", "litter", "cat_tower")),
        // 8~10번 천막 = 인간이 가는 가게 (2026-10-09)
        7 to ("무기점" to listOf("h_glove", "h_gauntlet", "h_gun")),
        8 to ("갑옷점" to listOf("h_helmet", "h_vest")),
        9 to ("약국" to listOf("h_life", "h_heal")),
        // 12번 천막 (11번은 로봇 상점)
        11 to ("채집 가게" to listOf("h_bugbox")),
    )

    /**
     * 상점마을2 가게 5곳 (가게 번호 0부터) → (가게 이름, 진열 물건) — 대표님 2026-10-24 「상점2에 물건들 만들어줘」.
     * 가게·물건 배정은 Claude 판단, 물건은 상점 마을1에 있는 것(그림·효과 그대로). 3번(2) 가게는 의사가 있는 병원.
     */
    val village2Shops: List<Pair<String, List<String>>> = listOf(
        "바닷가 간식 가게" to listOf("churu", "tuna_can", "milk", "salmon", "cat_cake", "feed_all"),
        "낚시 미끼 가게" to listOf("bait_worm", "bait_shrimp", "bait_gold"),
        "병원" to listOf("revive_potion", "revive_all", "h_heal", "h_life"),
        "해변 장난감 가게" to listOf("ball", "fish_toy", "mouse_toy", "feather_wand", "laser_pointer"),
        "무기·갑옷 가게" to listOf("h_glove", "h_gauntlet", "h_helmet", "h_vest", "cat_armor"),
    )

    /** 물건 값 — 안내 문구에 값을 박아 두지 말고 여기서 읽는다 */
    fun priceOf(id: String): Int = items.firstOrNull { it.id == id }?.price ?: 0
}
