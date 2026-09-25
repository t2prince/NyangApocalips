package com.jamcat.nayngapo.engine

import android.graphics.Bitmap
import com.jamcat.nayngapo.entity.*

/**
 * 고양이 도감 목록 (기획1: 고양이를 찾으면 그 고양이가 도감에 생긴다).
 * id는 저장 키이므로 바꾸면 기존 발견 기록이 초기화된다 — 바꾸지 말 것.
 */
object CatDex {

    class Entry(
        val id: String,
        val name: String,
        val hint: String,               // 미발견 카드 밑에 살짝 보여줄 힌트
        val sprite: () -> Bitmap,
        /** 연속 동작 시트가 있는 냥이는 도감에서 그 동작으로 춤춘다 (없으면 몸을 흔든다) */
        val frames: (() -> List<Bitmap>)? = null,
    )

    val entries: List<Entry> = listOf(
        Entry("normal", "치즈냥이", "어디에나 있다", { SpriteManager.normalCat },
            frames = { SpriteManager.catIdle }),
        // 치즈냥이 무늬 변형 4종 (원안 Cats/cats4.jpeg) — 같은 냥이지만 도감엔 따로 칸이 있다 (2026-09-20)
        Entry("white", "하양이", "새하얀 치즈냥이", { CatCoat.WHITE.enemy }, frames = { CatCoat.WHITE.idle }),
        Entry("calico", "삼색이", "세 가지 색 얼룩", { CatCoat.CALICO.enemy }, frames = { CatCoat.CALICO.idle }),
        Entry("tuxedo", "턱시도", "정장을 차려입었다", { CatCoat.TUXEDO.enemy }, frames = { CatCoat.TUXEDO.idle }),
        Entry("mackerel", "고등어", "회색 줄무늬", { CatCoat.MACKEREL.enemy }, frames = { CatCoat.MACKEREL.idle }),
        Entry("hammer", "해머냥이", "망치를 내려친다", { CatCoat.BICOLOR.enemy }, frames = { CatCoat.BICOLOR.idle }),
        Entry("gun", "총냥이", "등의 총으로 쏜다", { SpriteManager.gunCat }, frames = { SpriteManager.gunIdleEnemy }),   // 등에 총 붙은 그림
        Entry("blue", "물냥이", "물을 뱉는다", { SpriteManager.blueCatEvil },
            frames = { SpriteManager.catIdleBlue }),
        Entry("green", "그린냥이", "슬라임을 뱉는다", { SpriteManager.greenCatEvil },
            frames = { SpriteManager.catIdleGreen }),
        Entry("red", "빨간냥이", "불처럼 빨갛다", { SpriteManager.redCatEvil }, frames = { SpriteManager.catIdleRed }),
        Entry("orange", "주황냥이", "귤처럼 주황", { SpriteManager.orangeCatEvil }, frames = { SpriteManager.catIdleOrange }),
        Entry("yellow", "노랑냥이", "먹구름에서 번개를 떨군다", { SpriteManager.yellowCatEvil }, frames = { SpriteManager.catIdleYellow }),
        Entry("poop", "똥냥이", "냄새가 지독하다", { SpriteManager.poopCat }),
        Entry("flying", "날아냥이", "하늘을 난다", { SpriteManager.flyingCat }),
        Entry("fluffy", "털뭉냥이", "털이 수북하다", { SpriteManager.fluffyCat }),
        Entry("anvil", "모루냥이", "쇳덩이처럼 단단하다", { SpriteManager.anvilCat }),
        Entry("laser", "레이저냥이", "고글에서 광선이!", { SpriteManager.laserCat },
            frames = { SpriteManager.laserIdle }),
        Entry("thief", "도둑냥이", "루비를 훔쳐간다", { SpriteManager.thiefCat },
            frames = { SpriteManager.thiefIdle }),   // 가면 쓴 채 춤춘다
        Entry("vine", "덩굴냥이", "덩굴을 타고 난다", { SpriteManager.vineCat }),
        Entry("teleport", "순간냥이", "순간이동하는 초원의 보스", { SpriteManager.teleportCat }),
        Entry("giant", "왕왕냥이", "하늘의 왕", { SpriteManager.bossEvilCat }),
        Entry("wizard", "마법사냥이", "정글의 보스", { SpriteManager.bossWizardCat }),   // 20스테이지 보스 (2026-09-20 「모두 도감에」)
    )

    fun entry(id: String): Entry? = entries.firstOrNull { it.id == id }

    /** 적 개체 → 도감 id (도감에 없는 개체는 null) */
    fun idOf(enemy: Enemy): String? = when (enemy) {
        is NormalCat -> enemy.coat.dexId    // 무늬별 칸
        is HammerCat -> "hammer"
        is GunCat -> "gun"
        is BlueCat -> "blue"
        is GreenCat -> "green"
        is RedCat -> "red"
        is OrangeCat -> "orange"
        is YellowCat -> "yellow"
        is PoopCat -> "poop"
        is FlyingCat -> "flying"
        is FluffyCat -> "fluffy"
        is AnvilCat -> "anvil"
        is LaserCat -> "laser"
        is ThiefCat -> "thief"
        is VineCat -> "vine"
        is TeleportCatBoss -> "teleport"
        is WizardCatBoss -> "wizard"
        else -> null
    }
}
