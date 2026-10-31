package com.jamcat.nayngapo.level

import com.jamcat.nayngapo.entity.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * 스테이지 배치 설명 (대표님 2026-10-31 「현재 있는 스테이지들 편집할 수 있는 화면 만들어줘」).
 * 스테이지에 놓이는 냥이·함정·물건 하나하나를 {종류, x, 씨앗, 갇힌 냥이} 로 적는다.
 * Level 은 기본 생성기(defaultLayout)로 이 목록을 만들거나, 편집해 저장한 목록(StageEdits)을 읽어서 실제 개체로 만든다.
 * y 는 적지 않는다 — 모두 그 x 의 땅(Terrain.groundY) 위에 놓인다.
 */
data class Placement(
    val type: PlaceType,
    val x: Float,
    /** 폭이 들쭉날쭉한 함정(구덩이·늪·낭떠러지·토네이도)의 모양 씨앗 */
    val seed: Int = 0,
    /** 냥이 감옥에 꼭 갇혀 있을 냥이 (null 이면 구할 때 무작위) */
    val catKind: AllyCat.Kind? = null,
)

/** 배치할 수 있는 것의 무리 — 편집 화면 목록의 탭 */
enum class PlaceGroup { CAT, BOSS, HAZARD }

/**
 * 놓을 수 있는 것의 종류. 냥이는 factory 로 만든다 (예전 Level.Kind 를 옮겨 왔다).
 * name 은 저장(JSON)에 그대로 쓰이므로 바꾸지 않는다.
 */
enum class PlaceType(
    val label: String,
    val group: PlaceGroup,
    val factory: ((Float, Float) -> Enemy)? = null,
    /** 편집 화면 목록에 나오는가 */
    val inPalette: Boolean = true,
) {
    NORMAL("냥이", PlaceGroup.CAT, { x, y -> NormalCat(x, y) }),           // 냥이 HP15
    KITTEN("새끼 고양이", PlaceGroup.CAT, { x, y -> KittenCat(x, y) }),     // 새끼 고양이 HP3, 하트 반 칸 (2026-09-27)
    BLUE("물냥이", PlaceGroup.CAT, { x, y -> BlueCat(x, y) }),             // 물냥이(파랑냥이) HP20
    RED("빨강냥이", PlaceGroup.CAT, { x, y -> RedCat(x, y) }),             // 빨강냥이 HP20 (2026-09-20)
    ORANGE("주황냥이", PlaceGroup.CAT, { x, y -> OrangeCat(x, y) }),       // 주황냥이 HP20
    YELLOW("노랑냥이", PlaceGroup.CAT, { x, y -> YellowCat(x, y) }),       // 노랑냥이 HP20
    GREEN("그린냥이", PlaceGroup.CAT, { x, y -> GreenCat(x, y) }),         // 그린냥이 HP20 (슬라임)
    POOP("똥냥이", PlaceGroup.CAT, { x, y -> PoopCat(x, y) }),             // 똥냥이 HP30
    // 날아냥이 HP25 — 하늘 스테이지(11)에만 나온다 (대표님 2026-09-20), 땅 스테이지 목록에는 안 넣는다
    FLYING("날아냥이", PlaceGroup.CAT, { x, y -> FlyingCat(x, y) }, inPalette = false),
    FLUFFY("털뭉냥이", PlaceGroup.CAT, { x, y -> FluffyCat(x, y) }),       // 털뭉냥이 HP50
    ANVIL("모루냥이", PlaceGroup.CAT, { x, y -> AnvilCat(x, y) }),         // 모루냥이 HP75
    // 레이저냥이 HP15. 기본 생성에서는 레이저냥이 자리의 절반이 총냥이 (대표님 2026-09-20 「원래 레이저냥이가 있던 곳에 50:50」) —
    // 그 50:50 은 배치 목록을 만들 때 정해 두고, 여기서는 레이저냥이만 만든다
    LASER("레이저냥이", PlaceGroup.CAT, { x, y -> LaserCat(x, y) }),
    THIEF("도둑냥이", PlaceGroup.CAT, { x, y -> ThiefCat(x, y) }),         // 도둑냥이 — 루비 2개 훔침
    VINE("덩굴냥이", PlaceGroup.CAT, { x, y -> VineCat(x, y) }),           // 덩굴냥이 HP15 (정글)
    HAMMER("해머냥이", PlaceGroup.CAT, { x, y -> HammerCat(x, y) }),       // 해머냥이 HP10 공격3 (기획7)
    GUN("총냥이", PlaceGroup.CAT, { x, y -> GunCat(x, y) }),               // 총냥이 HP15 공격2 (기획8)
    FLOWER("꽃냥이", PlaceGroup.CAT, { x, y -> FlowerCat(x, y) }),         // 꽃냥이 HP15 공격10 (기획10)
    BEE("벌냥이", PlaceGroup.CAT, { x, y -> BeeCat(x, y) }),               // 벌냥이 HP3 (기획10) — 처음부터 날아다닌다
    BOAT("배냥이", PlaceGroup.CAT, { x, y -> BoatCat(x, y) }),             // 배냥이 HP20 (31 해적)
    ICE("얼음냥이", PlaceGroup.CAT, { x, y -> IceCat(x, y) }),             // 얼음냥이 HP20 (51~60 빙산)
    TELEPORT("순간냥이", PlaceGroup.CAT, { x, y -> TeleportCat(x, y) }),   // 잡몹 순간냥이 HP20 (53~60 빙산)

    // ===== 보스 =====
    BOSS_TELEPORT("순간냥이 보스", PlaceGroup.BOSS, { x, y -> TeleportCatBoss(x, y) }),   // 10
    BOSS_QUEEN_BEE("여왕벌냥이", PlaceGroup.BOSS, { x, y -> QueenBeeCatBoss(x, y) }),     // 30
    BOSS_PIRATE("해적냥이", PlaceGroup.BOSS, { x, y -> PirateCatBoss(x, y) }),             // 31 — 31 에서는 해적선 위에 탄다
    BOSS_ICEBERG("빙산냥이", PlaceGroup.BOSS, { x, y -> IcebergCatBoss(x, y) }),           // 60
    BOSS_WIZARD("마법사냥이", PlaceGroup.BOSS, { x, y -> WizardCatBoss(x, y) }),           // 20 (삼형제)

    // ===== 함정·물건 =====
    SPIKE("가시 함정", PlaceGroup.HAZARD),
    PIT("구덩이 함정", PlaceGroup.HAZARD),
    SWAMP("늪", PlaceGroup.HAZARD),             // 늪마다 머드냥이(꿀 지역은 꿀 늪 + 꿀냥이)가 한 마리 숨어 있다 (기획9)
    CHASM("낭떠러지", PlaceGroup.HAZARD),
    SHAFT("수직 구덩이", PlaceGroup.HAZARD),
    ICE_HOLE("얼음 구덩이", PlaceGroup.HAZARD),
    TORNADO("토네이도 함정", PlaceGroup.HAZARD),
    SECRET("비밀의 방", PlaceGroup.HAZARD),
    PRISON_HUMAN("인간 감옥", PlaceGroup.HAZARD),
    PRISON_CAT("냥이 감옥", PlaceGroup.HAZARD);

    val isEnemy: Boolean get() = factory != null

    companion object {
        /** 모루냥이 스테이지에 섞이는 해머냥이 마릿수 범위 (무작위) */
        const val HAMMER_MIN = 1
        const val HAMMER_MAX = 3
    }
}

/** 배치 목록 ↔ JSON (편집 저장소 "stage_edits" 에 스테이지마다 한 줄) */
object StageLayoutJson {
    fun encode(layout: List<Placement>): String {
        val arr = JSONArray()
        for (p in layout) {
            val o = JSONObject()
            o.put("t", p.type.name)
            o.put("x", p.x.toDouble())
            if (p.seed != 0) o.put("s", p.seed)
            p.catKind?.let { o.put("c", it.name) }
            arr.put(o)
        }
        return arr.toString()
    }

    /** 읽을 수 없는 글이면 null. 모르는 종류(옛 버전·새 버전 차이)는 건너뛴다 */
    fun decode(text: String): List<Placement>? = try {
        val arr = JSONArray(text)
        val out = mutableListOf<Placement>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val type = runCatching { PlaceType.valueOf(o.getString("t")) }.getOrNull() ?: continue
            val cat = if (o.has("c")) runCatching { AllyCat.Kind.valueOf(o.getString("c")) }.getOrNull() else null
            out.add(Placement(type, o.getDouble("x").toFloat(), o.optInt("s", 0), cat))
        }
        out
    } catch (_: Exception) {
        null
    }
}
