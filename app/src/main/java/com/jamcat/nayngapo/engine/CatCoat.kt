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
        /** 무작위 무늬 — 노랑이(기준 디자인)가 절반, 나머지 넷이 나머지를 나눠 갖는다 */
        fun random(): CatCoat {
            val r = Math.random()
            if (r < 0.5) return CHEESE
            val others = values().filter { it != CHEESE }
            return others[((r - 0.5) / 0.5 * others.size).toInt().coerceIn(0, others.size - 1)]
        }

        fun of(name: String?): CatCoat = values().firstOrNull { it.name == name } ?: CHEESE
    }
}
