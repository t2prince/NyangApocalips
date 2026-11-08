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
    /** 냥이 감옥에 엄마+새끼가 같이 갇혀 있다 (catKind 는 null — 대표님 2026-10-31 「냥이랑 새끼 고양이 같이 넣기」) */
    val withKitten: Boolean = false,
    /** 하늘 스테이지(11)만: 나는 높이 — 0 = 날 수 있는 맨 위, 1 = 맨 아래 (화면 높이가 기기마다 달라 비율로 적는다). 땅 스테이지는 null */
    val y: Float? = null,
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
    FLUFFY("털북숭냥이", PlaceGroup.CAT, { x, y -> FluffyCat(x, y) }),       // 털북숭냥이 HP50
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
    // 11 하늘의 왕 — 하늘 스테이지(SkyStageScreen)에만 있어 땅 개체가 없다
    BOSS_GIANT("왕왕냥이", PlaceGroup.BOSS, inPalette = false),

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

    val isEnemy: Boolean get() = factory != null || this == BOSS_GIANT

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
            if (p.withKitten) o.put("k", 1)
            p.y?.let { o.put("y", it.toDouble()) }
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
            out.add(Placement(type, o.getDouble("x").toFloat(), o.optInt("s", 0), cat, withKitten = o.optInt("k", 0) == 1,
                y = if (o.has("y")) o.getDouble("y").toFloat() else null))
        }
        out
    } catch (_: Exception) {
        null
    }
}

/**
 * 하늘 스테이지(11) 배치 (대표님 2026-10-31 「11 스태이지도 편집 할수있개 만들어줘」).
 * 하늘은 땅이 없어 x 와 함께 높이(Placement.y, 0~1)도 적는다. 놓을 수 있는 것: 날아냥이·레이저냥이·왕왕냥이·인간 감옥·냥이 감옥.
 * 기본 배치는 예전 SkyStageScreen 의 무작위 배치를 그대로 옮겨 왔다.
 */
object SkyLayout {
    /** 편집 화면 목록 (고양이 탭 / 함정·물건 탭) */
    val CATS = listOf(PlaceType.FLYING, PlaceType.LASER, PlaceType.BOSS_GIANT)
    val THINGS = listOf(PlaceType.PRISON_HUMAN, PlaceType.PRISON_CAT)

    /** 날 수 있는 높이 — 화면 높이 1080 일 때의 위·아래 (SkyStageScreen: 130 ~ 화면 높이 - 320) */
    const val NOMINAL_H = 1080f
    const val FLY_TOP = 130f
    const val FLY_BOTTOM_GAP = 320f
    /** 놓을 수 있는 x — 출발(500) 바로 앞과 코스 끝은 비운다 (Claude 판단) */
    const val MIN_X = 900f
    const val END_GAP = 300f

    fun courseLength(stage: Int): Float = 11000f + (stage - 11) * 700f

    /** 화면 높이 h 에서 높이 비율 → y */
    fun yAt(frac: Float, h: Float): Float = FLY_TOP + frac.coerceIn(0f, 1f) * (h - FLY_BOTTOM_GAP - FLY_TOP)

    private fun fracOf(y: Float): Float = ((y - FLY_TOP) / (NOMINAL_H - FLY_BOTTOM_GAP - FLY_TOP)).coerceIn(0f, 1f)

    fun defaultLayout(stage: Int): List<Placement> {
        val course = courseLength(stage)
        val out = mutableListOf<Placement>()
        val flyingCount = 10 + (stage - 11) * 2
        val laserCount = ((stage - 12) * 2).coerceAtLeast(0)
        val spots = mutableListOf<Pair<PlaceType, Float>>()
        repeat(flyingCount) { i -> spots.add(PlaceType.FLYING to (1400f + i * (course - 2600f) / flyingCount)) }
        repeat(laserCount) { i -> spots.add(PlaceType.LASER to (2200f + i * (course - 3400f) / laserCount.coerceAtLeast(1))) }
        for ((type, ex) in spots) {
            val ey = 180f + (Math.random() * 520f).toFloat()
            out.add(Placement(type, ex + (Math.random() * 260f - 130f).toFloat(), y = fracOf(ey)))
        }
        // 끝에는 하늘의 왕 '왕왕냥이' (높이는 게임에서 저절로 오르내린다)
        out.add(Placement(PlaceType.BOSS_GIANT, course - 900f, y = 0.5f))
        // 코스 중간중간에 감옥 셋 (인간·냥이 둘 다, 셋째는 반반)
        val kinds = mutableListOf(PlaceType.PRISON_CAT, PlaceType.PRISON_HUMAN,
            if (Math.random() < 0.5) PlaceType.PRISON_CAT else PlaceType.PRISON_HUMAN)
        kinds.shuffle()
        for ((i, type) in kinds.withIndex()) {
            val px = course * (0.25f + 0.25f * i) + (Math.random().toFloat() - 0.5f) * 600f
            // 위아래 15%는 비워 둔다 — 너무 높거나 낮으면 닿기 어렵다
            out.add(Placement(type, px, y = 0.15f + Math.random().toFloat() * 0.70f))
        }
        return out
    }
}
