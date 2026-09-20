package com.jamcat.nayngapo.level

import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.Terrain
import com.jamcat.nayngapo.entity.*

/**
 * 플랫포머식 횡스크롤 스테이지: 왼쪽에서 출발해 오른쪽으로만 진행한다.
 * 냥이들은 진행 경로를 따라 구간 그룹으로 등장하고, 보스는 스테이지 맨 끝을 지킨다.
 * 필드의 고양이를 전부 무찌르면 클리어 (보스 게이지 없음).
 *
 * 1~10 스테이지 구성은 기획서 표(Plan/기획5.jpeg)를 그대로 따른다:
 *   냥이 종류        | 함정                 | 비밀의 방 | 구출
 *   1  냥이          | X                    | 0        | X
 *   2  냥이          | 일반 함정            | 0        | X
 *   3  물냥이+냥이   | 일반 함정            | 0        | 인간x1
 *   4  물냥이+냥이   | 일반 함정x2          | 1        | 인간x1 고양이x1
 *   5  물냥이+냥이   | 일반 함정x2          | 1        | 고양이x1 인간x1
 *   6  물냥이+냥이   | 일반 함정x2          | 1        | 고양이x1 인간x1
 *   7  물냥이+냥이   | 일반 함정x3          | 2        | 고양이x2
 *   8  +그린냥이     | 일반 함정+위험한 함정 | 2        | 인간x2
 *   9  +그린냥이     | 위험한 함정x2+일반    | 2        | 고양이x2 인간x1
 *   10 +그린냥이+순간냥이 Boss | X          | 3        | X
 * 여기에 기획3의 늪(빠지면 5배 감속, 점프로 통과)이 2스테이지부터 깔린다.
 */
class Level(val stageNumber: Int) {
    val enemies = mutableListOf<Enemy>()
    /** 인간 편에 선 냥이들 (그린냥이·파랑냥이) */
    val allies = mutableListOf<AllyCat>()
    val prisons = mutableListOf<Prison>()
    /** 일반 함정: 가시 (밟으면 아프지만 죽지는 않는다) */
    val traps = mutableListOf<Trap>()
    /** 위험한 함정: 구덩이 (빠지면 그대로 게임 오버, 점프로 넘을 것) */
    val pits = mutableListOf<PitTrap>()
    /** 늪: 빠지면 5배 느려진다 (점프로 빠져나올 수 있다) */
    val swamps = mutableListOf<Swamp>()
    /** 비밀의 방: 숨은 사다리 구멍 — 밟으면 발견되고 하트를 준다 */
    val secretRooms = mutableListOf<SecretRoom>()
    /** 푹 빠지는 눈밭 — 설원 지역(11스테이지 이후)에서 쓴다 */
    val snowPits = mutableListOf<SnowPit>()
    /** 바닥이 통째로 없는 낭떠러지 — 이후 지역용 (초원 1~10에는 없다) */
    val chasms = mutableListOf<Chasm>()
    /** 10초마다 인간 앞에 솟아오르는 벽 (부수거나 뛰어넘어야 한다) */
    val walls = mutableListOf<Wall>()
    /** 정글의 덩굴 그네 — 인간도 점프로 잡고 타서 건널 수 있다 (12~20) */
    val vines = mutableListOf<Vine>()
    val projectiles = mutableListOf<Projectile>()
    var playerStartX = 200f
    var areaWidth = Constants.PLAY_AREA_RIGHT

    var totalEnemies = 0
    var defeatedEnemies = 0
    val stageCleared: Boolean get() = enemies.isNotEmpty() && enemies.all { it.isDead }

    init {
        // 지형 굴곡 설정: 스테이지가 오를수록 울퉁불퉁해진다
        Terrain.stage = stageNumber

        // ===== 기획5 표: 냥이 구성 · 함정 · 비밀의 방 · 구출 =====
        val plan = when (stageNumber) {
            1 -> StagePlan(mapOf(Kind.NORMAL to 8))
            2 -> StagePlan(mapOf(Kind.NORMAL to 10), spikes = 1)
            3 -> StagePlan(mapOf(Kind.NORMAL to 8, Kind.BLUE to 4), spikes = 1, humans = 1)
            4 -> StagePlan(mapOf(Kind.NORMAL to 9, Kind.BLUE to 5),
                spikes = 1, secrets = 1, humans = 1, cats = 1)
            5 -> StagePlan(mapOf(Kind.NORMAL to 10, Kind.BLUE to 6),
                spikes = 1, secrets = 1, humans = 1, cats = 1)
            6 -> StagePlan(mapOf(Kind.NORMAL to 11, Kind.BLUE to 7),
                spikes = 2, secrets = 1, humans = 1, cats = 1)
            7 -> StagePlan(mapOf(Kind.NORMAL to 9, Kind.BLUE to 6),
                spikes = 2, secrets = 2, cats = 2)
            8 -> StagePlan(mapOf(Kind.NORMAL to 9, Kind.BLUE to 6, Kind.GREEN to 3),
                spikes = 1, dangerPits = 1, secrets = 2, humans = 2)
            9 -> StagePlan(mapOf(Kind.NORMAL to 9, Kind.BLUE to 7, Kind.GREEN to 4),
                spikes = 1, dangerPits = 1, secrets = 2, humans = 1, cats = 2)
            10 -> StagePlan(mapOf(Kind.NORMAL to 12, Kind.BLUE to 9, Kind.GREEN to 8),
                secrets = 3,
                boss = { baseX -> listOf(TeleportCatBoss(baseX, Constants.GROUND_Y)) })

            // ===== 12~20 정글: 거대한 나무 사이로 덩굴냥이가 날아다닌다 (11은 하늘 스테이지) =====
            12 -> StagePlan(mapOf(Kind.NORMAL to 6, Kind.BLUE to 4, Kind.VINE to 3),
                spikes = 1, secrets = 1, humans = 1)
            13 -> StagePlan(mapOf(Kind.NORMAL to 6, Kind.BLUE to 4, Kind.VINE to 4, Kind.POOP to 2),
                spikes = 1, secrets = 1, cats = 1)
            14 -> StagePlan(mapOf(Kind.NORMAL to 5, Kind.BLUE to 4, Kind.VINE to 4,
                Kind.POOP to 2, Kind.FLYING to 2),
                spikes = 1, secrets = 1, humans = 1, cats = 1)
            15 -> StagePlan(mapOf(Kind.NORMAL to 5, Kind.BLUE to 4, Kind.GREEN to 3,
                Kind.VINE to 5, Kind.FLYING to 2),
                spikes = 2, secrets = 2, humans = 1, cats = 1)
            16 -> StagePlan(mapOf(Kind.NORMAL to 4, Kind.BLUE to 4, Kind.GREEN to 3,
                Kind.VINE to 5, Kind.POOP to 2, Kind.FLUFFY to 2),
                spikes = 1, dangerPits = 1, secrets = 2, cats = 2)
            17 -> StagePlan(mapOf(Kind.NORMAL to 4, Kind.BLUE to 3, Kind.GREEN to 3,
                Kind.VINE to 5, Kind.FLYING to 3, Kind.FLUFFY to 2, Kind.THIEF to 1),
                spikes = 2, dangerPits = 1, secrets = 2, humans = 2)
            18 -> StagePlan(mapOf(Kind.NORMAL to 4, Kind.BLUE to 3, Kind.GREEN to 3,
                Kind.VINE to 6, Kind.POOP to 2, Kind.FLUFFY to 2, Kind.THIEF to 1, Kind.ANVIL to 2),
                spikes = 1, dangerPits = 1, secrets = 2, humans = 1, cats = 2)
            19 -> StagePlan(mapOf(Kind.NORMAL to 3, Kind.GREEN to 3, Kind.VINE to 6,
                Kind.FLYING to 3, Kind.FLUFFY to 3, Kind.THIEF to 1, Kind.ANVIL to 2, Kind.LASER to 3),
                spikes = 2, dangerPits = 2, secrets = 3, humans = 1, cats = 2)
            // 정글의 끝: 마법사 냥이 삼형제가 신전을 지킨다 (원안 마지막 보스 디자인)
            else -> StagePlan(mapOf(Kind.GREEN to 3, Kind.VINE to 6, Kind.FLUFFY to 3,
                Kind.ANVIL to 3, Kind.LASER to 3, Kind.FLYING to 3, Kind.THIEF to 1),
                secrets = 3,
                boss = { baseX ->
                    listOf(
                        WizardCatBoss(baseX, Constants.GROUND_Y),
                        WizardCatBoss(baseX + 450f, Constants.GROUND_Y),
                        WizardCatBoss(baseX + 900f, Constants.GROUND_Y),
                    )
                })
        }
        // 늪(기획3): 2스테이지부터 하나, 7스테이지부터 둘 (함정은 과하지 않게)
        val swampCount = if (stageNumber < 2) 0 else (1 + (stageNumber - 2) / 5).coerceAtMost(2)

        // 해머냥이는 모루냥이가 나오는 스테이지에만, 1~3마리 무작위로 섞인다 (대표님 2026-09-20)
        val mobs = plan.mobs.toMutableMap()
        if (Kind.ANVIL in mobs) mobs[Kind.HAMMER] = Kind.HAMMER_MIN + (Math.random() * (Kind.HAMMER_MAX - Kind.HAMMER_MIN + 1)).toInt()

        // 잡몹을 섞은 뒤 일정한 간격의 구간 그룹(3마리)으로 배치
        val factories = mutableListOf<(Float, Float) -> Enemy>()
        for ((kind, count) in mobs) {
            repeat(count) { factories.add(kind.factory) }
        }
        factories.shuffle()

        val groupSize = 3
        val groupCount = ((factories.size + groupSize - 1) / groupSize).coerceAtLeast(1)
        val pathStart = 1300f          // 첫 조우 지점 (시작 지점에서 반 화면쯤 전진)
        val groupSpacing = 1250f       // 그룹 간 간격 — 조우가 끊기지 않고 이어진다
        for ((i, factory) in factories.withIndex()) {
            val g = i / groupSize
            val gx = pathStart + g * groupSpacing
            // 그룹 안에서도 서로 떨어져 있게 (사용자: 냥이들이 붙어 있으면 안 된다)
            val ex = gx + ((Math.random() - 0.5) * 460.0).toFloat()
            enemies.add(factory(ex, Constants.GROUND_Y))
        }
        // 보스는 마지막 잡몹 그룹 바로 뒤를 지킨다
        val bossBaseX = pathStart + groupCount * groupSpacing + 800f
        plan.boss?.let { enemies.addAll(it(bossBaseX)) }

        // 실제 스테이지 길이: 보스 자리 뒤로 여유 두 화면 (감옥·카메라가 이 안에 들어온다)
        areaWidth = bossBaseX + 1800f

        // 아군 냥이는 처음부터 따라오지 않는다 —
        // 기획1: "고양이를 구해야지 아군이 된다" (냥이 감옥을 부수면 그때 합류)

        // ===== 함정 배치: 그룹과 그룹 사이의 빈 구간 한가운데에 심는다 =====
        // (그룹 위에 겹치면 싸우다 억울하게 밟게 되므로 반드시 사이 공간에만)
        val gapCenters = (1..groupCount).map { pathStart + it * groupSpacing - groupSpacing / 2f }
        val hazardOrder = List(swampCount) { Hazard.SWAMP } +
            List(plan.spikes) { Hazard.SPIKE } +
            List(plan.dangerPits) { Hazard.PIT }
        val hazardX = mutableListOf<Float>()
        if (hazardOrder.isNotEmpty()) {
            val n = hazardOrder.size.coerceAtMost(gapCenters.size)
            for (i in 0 until n) {
                // 쉬운 함정이 앞, 위험한 함정이 뒤로 가도록 순서대로 고르게 배치
                val slot = gapCenters[(i * gapCenters.size) / n]
                val hx = slot - 80f
                hazardX.add(hx)
                when (hazardOrder[i]) {
                    Hazard.SWAMP -> swamps.add(Swamp(hx, Terrain.groundY(hx + 150f), seed = stageNumber * 13 + i * 5))
                    Hazard.SPIKE -> traps.add(Trap(hx, Terrain.groundY(hx + 75f)))
                    Hazard.PIT -> pits.add(PitTrap(hx, Terrain.groundY(hx + 85f), seed = stageNumber * 31 + i * 7))
                }
            }
        }

        // ===== 비밀의 방: 함정이 없는 빈 구간에 숨긴다 =====
        val freeGaps = gapCenters.filter { gc -> hazardX.none { kotlin.math.abs(it - gc) < 400f } }
        repeat(plan.secrets) { i ->
            val gx = if (freeGaps.isNotEmpty()) freeGaps[(i * freeGaps.size) / plan.secrets.coerceAtLeast(1) % freeGaps.size]
            else pathStart + (i + 1) * 900f
            val sx = gx + 220f
            secretRooms.add(SecretRoom(sx, Terrain.groundY(sx + 45f)))
        }

        // ===== 감옥: 기획5의 구출 표 (인간/고양이 감옥을 번갈아 배치) =====
        val prisonKinds = mutableListOf<Prison.Kind>()
        repeat(plan.humans) { prisonKinds.add(Prison.Kind.HUMAN) }
        repeat(plan.cats) { prisonKinds.add(Prison.Kind.CAT) }
        prisonKinds.shuffle()
        for ((i, kind) in prisonKinds.withIndex()) {
            var px = pathStart + (i + 1) * (bossBaseX - pathStart) / (prisonKinds.size + 1).toFloat()
            // 함정 위에 겹치면 옆으로 비켜 놓는다
            while (hazardX.any { px > it - 220f && px < it + 500f }) px += 340f
            prisons.add(Prison(px, Terrain.groundY(px), kind))
        }

        // ===== 정글(12~20)의 덩굴 그네: 그룹 사이 빈 구간에 매달아 둔다 =====
        if (stageNumber in 12..20) {
            for ((i, gc) in gapCenters.withIndex()) {
                val vx = gc + 120f
                if (vx > bossBaseX - 300f) break
                val ground = Terrain.groundY(vx)
                // 끝이 사람 머리 위에 오도록 — 점프로 잡는다
                vines.add(
                    Vine(
                        anchorX = vx,
                        topY = ground - 560f,
                        length = 330f,
                        phase = i * 0.7f
                    )
                )
            }
        }

        totalEnemies = enemies.size
    }

    /** 잡몹 종류와 생성 방법 (cats/ 시트의 냥이들) */
    private enum class Kind(val factory: (Float, Float) -> Enemy) {
        NORMAL({ x, y -> NormalCat(x, y) }),     // 냥이 HP15
        BLUE({ x, y -> BlueCat(x, y) }),         // 물냥이(파랑냥이) HP20
        GREEN({ x, y -> GreenCat(x, y) }),       // 그린냥이 HP20 (슬라임)
        POOP({ x, y -> PoopCat(x, y) }),         // 똥냥이 HP30 (이후 지역)
        FLYING({ x, y -> FlyingCat(x, y) }),     // 날아냥이 HP25 (이후 지역)
        FLUFFY({ x, y -> FluffyCat(x, y) }),     // 털뭉냥이 HP50 (이후 지역)
        ANVIL({ x, y -> AnvilCat(x, y) }),       // 모루냥이 HP75 (이후 지역)
        LASER({ x, y -> LaserCat(x, y) }),       // 레이저냥이 HP15 (이후 지역)
        THIEF({ x, y -> ThiefCat(x, y) }),       // 도둑냥이 — 스테이지마다 1마리, 루비 2개 훔침
        VINE({ x, y -> VineCat(x, y) }),         // 덩굴냥이 HP15 (정글 — 덩굴 그네)
        HAMMER({ x, y -> HammerCat(x, y) });     // 해머냥이 HP10 공격3 (기획7 — 모루냥이가 나오는 스테이지에만, 마릿수 무작위)

        companion object {
            /** 모루냥이 스테이지에 섞이는 해머냥이 마릿수 범위 (무작위) */
            const val HAMMER_MIN = 1
            const val HAMMER_MAX = 3
        }
    }

    /** 경로에 심는 함정 종류 (앞에서 뒤로 갈수록 위험해진다) */
    private enum class Hazard { SWAMP, SPIKE, PIT }

    private class StagePlan(
        val mobs: Map<Kind, Int>,
        val spikes: Int = 0,        // 일반 함정 (가시)
        val dangerPits: Int = 0,    // 위험한 함정 (구덩이)
        val secrets: Int = 0,       // 비밀의 방
        val humans: Int = 0,        // 구출: 인간 감옥
        val cats: Int = 0,          // 구출: 고양이 감옥 (부수면 아군 합류)
        val boss: ((Float) -> List<Enemy>)? = null,
    )

    fun onEnemyDefeated() {
        defeatedEnemies++
    }
}
