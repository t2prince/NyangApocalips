package com.jamcat.nayngapo.engine

import android.graphics.Color

/**
 * 고를 수 있는 인간들 (로비 '캐릭터 변경').
 * 스프라이트는 모두 같은 졸라맨 시트를 쓰고, 선 색과 머리 장식·능력치만 다르다.
 */
object Heroes {

    /** 머리에 얹는 장식 */
    enum class Accessory { NONE, CAP, HEADBAND, GLASSES, GLOVES, NINJA_HOOD }

    data class Hero(
        val id: String,
        val name: String,
        /** 졸라맨 선 색 */
        val tint: Int,
        val desc: String,
        val accessory: Accessory,
        /** 최대 체력에 더하는 하트 수 */
        val hpBonus: Int = 0,
        val atkMul: Float = 1f,
        val speedMul: Float = 1f,
        val jumpMul: Float = 1f,
        /** 몸집 (1보다 작으면 키가 작다 — 발은 그대로 땅에 붙는다) */
        val scale: Float = 1f,
        /** 폭죽을 들고 다니며 공격할 때마다 던지는가 */
        val carriesFirework: Boolean = false,
        /** 공격할 때마다 표창을 던지는가 (닌자 인간) */
        val throwsShuriken: Boolean = false,
    ) {
        /** 카드에 찍는 점 세 개 (체력 / 공격 / 속도) */
        val hpPips: Int get() = if (hpBonus >= 2) 3 else 2
        val atkPips: Int get() = when {
            atkMul >= 1.2f -> 3
            atkMul >= 1.0f -> 2
            else -> 1
        }
        val speedPips: Int get() = when {
            speedMul >= 1.2f -> 3
            speedMul >= 1.0f -> 2
            else -> 1
        }
    }

    val all: List<Hero> = listOf(
        Hero(
            "black", "검은 인간", Color.rgb(20, 20, 20), "균형 잡힌 기본형",
            Accessory.NONE
        ),
        // 권투 인간: 양손에 권투 장갑 — 카드 점: 체력 2 / 공격 3 / 속도 1
        Hero(
            "boxer", "권투 인간", Color.rgb(214, 48, 40), "주먹 한 방이 세다",
            Accessory.GLOVES, hpBonus = 0, atkMul = 1.3f, speedMul = 0.9f
        ),
        Hero(
            "blue", "파란 인간", Color.rgb(40, 96, 214), "폭죽을 던진다",
            Accessory.HEADBAND, hpBonus = 2, atkMul = 0.9f, carriesFirework = true
        ),
        // 닌자 인간 (옛 초록 인간, 세이브 호환을 위해 id 는 "green" 그대로): 초록 두건, 표창을 던진다
        Hero(
            "green", "닌자 인간", Color.rgb(40, 140, 70), "표창을 던진다",
            Accessory.NINJA_HOOD, speedMul = 1.25f, jumpMul = 1.1f, throwsShuriken = true
        ),
    )

    /** 처음 시작할 때의 인간 */
    const val DEFAULT_ID = "black"

    /**
     * 고양이 (대표님 2026-10-24 「고양이 되기는 그냥 고양이가 주인공이 돼서 싸우는 거야」「로봇·자동차도 쓸 수 있어」):
     * 능력은 검은 인간과 같다 (Claude 판단). 그림은 ui/CharSkin.
     */
    const val CAT_ID = "cat"
    val cat = Hero(CAT_ID, "고양이", Color.rgb(20, 20, 20), "캣타워 냥이가 된다", Accessory.NONE)
    /** 내가 만든 캐릭터 id 앞머리 (engine/CustomChars) */
    const val CUSTOM_PREFIX = "custom_"

    /** 없어진 인간(예: 게임 인간 "red")·지운 캐릭터를 고른 세이브는 기본 인간으로 돌아간다 */
    /** 고양이 주인공이 된 캣타워 냥이 (떠나거나 무덤이면 없음) */
    fun playerCat(): CatTeam.TeamCat? = CatTeam.cat(GameProgress.playerCatId)?.takeIf { !it.gone }

    /** 고양이 주인공이 된 냥이의 종류 (아니면 null) — 특수 스테이지 냥이의 특징에 쓴다 */
    fun playerCatKind(): com.jamcat.nayngapo.entity.AllyCat.Kind? =
        if (GameProgress.selectedHero == CAT_ID) playerCat()?.kind else null

    fun byId(id: String): Hero = when {
        id == CAT_ID -> playerCat()?.let { cat.copy(name = it.name) } ?: all.first()   // 캣타워 냥이를 안 골랐거나 떠났으면 기본 인간
        id.startsWith(CUSTOM_PREFIX) -> CustomChars.byId(id)?.let { Hero(it.id, it.name, Color.rgb(20, 20, 20), "내가 만든 캐릭터", Accessory.NONE) } ?: all.first()
        else -> all.firstOrNull { it.id == id } ?: all.first()
    }

    /** 지금 고른 인간 */
    fun selected(): Hero = byId(GameProgress.selectedHero)
}
