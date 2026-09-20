package com.jamcat.nayngapo.engine

import android.graphics.Bitmap

/**
 * 치즈냥이의 털 무늬 (기획 원안 `Cats/cats4.jpeg`: 하양이·삼색이·턱시도·노랑이·고등어).
 * 같은 치즈냥이(체력·공격력·행동 동일)이고 그림만 다르다. 노랑이 = 기존 치즈냥이 그대로.
 * 그림은 `scripts/make_coat_variants.py` 가 치즈냥이 그림에서 만든다.
 */
enum class CatCoat(val label: String) {
    CHEESE("노랑이"),
    WHITE("하양이"),
    CALICO("삼색이"),
    TUXEDO("턱시도"),
    MACKEREL("고등어");

    /** 도감 id — 노랑이는 기존 치즈냥이 칸("normal"), 나머지는 무늬별 칸 */
    val dexId: String get() = if (this == CHEESE) "normal" else name.lowercase()

    /** 적(두꺼운 꼬리) 정지 그림 */
    val enemy: Bitmap get() = SpriteManager.coatEnemy[this] ?: SpriteManager.normalCat
    /** 아군(가는 꼬리) 정지 그림 */
    val ally: Bitmap get() = SpriteManager.coatAlly[this] ?: SpriteManager.allyCat
    val idle: List<Bitmap> get() = SpriteManager.coatIdle[this] ?: SpriteManager.catIdle
    val walk: List<Bitmap> get() = SpriteManager.coatWalk[this] ?: SpriteManager.catWalk
    val jump: List<Bitmap> get() = SpriteManager.coatJump[this] ?: SpriteManager.catJump
    val attack: List<Bitmap> get() = SpriteManager.coatAttack[this] ?: SpriteManager.catAttack
    /** 갑옷 입은 아군 정지 그림 / 대기(춤) 스트립 — scripts/make_armor_sprites.py */
    val allyArmor: Bitmap get() = SpriteManager.coatAllyArmor[this] ?: ally
    val idleArmor: List<Bitmap> get() = SpriteManager.coatIdleArmor[this] ?: idle

    companion object {
        /** 무작위 무늬 — 5종 똑같이 20%씩 (대표님 2026-09-20). 감옥(구출 냥이)·기존 팀 냥이 무늬 배정에 쓴다 */
        fun random(): CatCoat {
            val all = values()
            return all[(Math.random() * all.size).toInt().coerceIn(0, all.size - 1)]
        }

        /**
         * 스테이지 적 치즈냥이용 — 노랑이(치즈냥이)가 절반, 나머지 넷이 나머지 절반을 나눠 갖는다.
         * 5종 균등으로 하니 8마리 판에서 노랑이가 한두 마리뿐이라 "치즈냥이가 없다"고 보였다 (대표님 2026-09-20).
         */
        fun randomForEnemy(): CatCoat {
            val r = Math.random()
            if (r < 0.5) return CHEESE
            val others = values().filter { it != CHEESE }
            return others[((r - 0.5) / 0.5 * others.size).toInt().coerceIn(0, others.size - 1)]
        }

        fun of(name: String?): CatCoat = values().firstOrNull { it.name == name } ?: CHEESE
    }
}
