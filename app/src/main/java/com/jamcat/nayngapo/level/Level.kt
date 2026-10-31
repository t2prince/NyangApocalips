package com.jamcat.nayngapo.level

import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.StageEdits
import com.jamcat.nayngapo.engine.Terrain
import com.jamcat.nayngapo.entity.*

/** 31 해적선 갑판 — 배 그림(pirate_ship.png 1200×760을 높이 560으로, 바닥은 물 아래 60)의 갑판 줄. 배 가운데 x 기준 */
const val SHIP_DRAW_H = 560f
/** 냥이 감옥 수 = 표의 수 × 2 + 2 (2026-10-10) */
private const val CAT_PRISON_MUL = 2
private const val CAT_PRISON_EXTRA = 2
private const val SHIP_DECK_BACK = 200f    // 뱃머리 쪽(왼쪽) 갑판 끝
private const val SHIP_DECK_FRONT = 400f   // 고물 쪽(오른쪽) 갑판 끝
private const val SHIP_DECK_RISE = 90f     // 물 위로 갑판 높이 (점프 최고 200 이라 한 번에 오른다)
/** 기획 표(StagePlan)에서 쓰는 잡몹·보스 이름 — 예전 Level.Kind 는 배치 종류(PlaceType)로 옮겼다 (2026-10-31) */
private typealias Kind = PlaceType

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
 *
 * 51~60 빙산 (대표님 2026-10-25 「51-60까지 만들어」 — 기획11 `Plan/기획11_빙산_32-40.jpg` 의 32~40 표를 52~60 으로 옮김, 51 은 Claude 판단).
 * 32~50 은 아직 없다('준비 중'). 마릿수·비밀의 방은 Claude 판단:
 *   냥이 종류                                   | 구출            | 함정
 *   51 냥이·얼음냥이 (Claude 판단)               | 인간x1          | 수직 구덩이
 *   52 그린냥이·냥이·얼음냥이·총냥이               | 고양이x1        | 수직 구덩이·구덩이 함정
 *   53 그린냥이·냥이·얼음냥이·총냥이·순간냥이       | 인간x1          | 52와 같음
 *   54 53과 같음                                 | 총냥이x1        | 52와 같음
 *   55 53과 같음                                 | -               | 52와 같음
 *   56 53의 냥이 + 해머냥이                       | -               | 52와 같음
 *   57 53과 같음                                 | -               | 수직 구덩이x3
 *   58 얼음냥이·물냥이                            | -               | 수직 구덩이
 *   59 얼음냥이                                  | 얼음냥이x5      | 얼음 구덩이
 *   60 얼음냥이·순간냥이·냥이·빙산냥이 Boss         | 순간냥이x1      | 얼음 구덩이·수직 구덩이·구덩이 함정
 * 수직 구덩이 = 폭 130 의 깊은 얼음 구멍(낭떠러지처럼 떨어짐), 얼음 구덩이 = 밟으면 0.5초 뒤 깨지는 얇은 얼음(깨지면 구덩이 함정) — 뜻은 Claude 판단.
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
    /** 공중 발판 (슈퍼마리오식, 2026-09-20) */
    val platforms = mutableListOf<Platform>()
    /** 10초마다 인간 앞에 솟아오르는 벽 (부수거나 뛰어넘어야 한다) */
    val walls = mutableListOf<Wall>()
    /** 정글의 덩굴 그네 — 인간도 점프로 잡고 타서 건널 수 있다 (12~20) */
    val vines = mutableListOf<Vine>()
    /** 31 해적 스테이지: 토네이도 함정 · 끝의 해적선 자리(x, 없으면 null) */
    val tornadoTraps = mutableListOf<TornadoTrap>()
    var pirateShipX: Float? = null
    /** 51~60 빙산: 얼음 구덩이 (밟으면 깨진다) */
    val iceHoles = mutableListOf<IceHole>()
    val projectiles = mutableListOf<Projectile>()
    var playerStartX = 200f
    var areaWidth = Constants.PLAY_AREA_RIGHT

    var totalEnemies = 0
    var defeatedEnemies = 0
    // 늪 속에 숨은 머드냥이는 인간이 늪에 빠져야만 나오므로 클리어 조건에서 뺀다 (대표님 2026-09-27 기획9)
    /**
     * 한 번 깬 스테이지를 다시 하는 중 (대표님 2026-10-17 「한 번 클리어한 스테이지는 고양이가 없어, 감옥도 깨져 있지」):
     * 냥이(보스 포함)가 없고 감옥은 이미 깨져 있다. 잡을 냥이가 없으니 끝까지 가면(reachedEnd) 깬다 (Claude 판단).
     */
    var replay = false
    var reachedEnd = false

    /**
     * 다시 하는 스테이지: 감옥은 다 깨져 있다. 냥이들은 그대로 있다 (2026-10-17 「한 번 깬 스테이지에도 고양이들이 있어」 —
     * 처음엔 냥이도 없앴다가 되돌림). 깨는 법은 처음과 같다(냥이를 다 잡기).
     */
    fun emptyForReplay() {
        for (p in prisons) p.isOpen = true
    }

    val stageCleared: Boolean get() = if (replay) reachedEnd else enemies.isNotEmpty() && enemies.all { it.isDead || it is MudCat || it is StrayTeamCat } &&   // 떠난 냥이는 세지 않는다
        // 30: 끝에 갇힌 배냥이를 구해야 끝난다 (31 바다에 타고 나갈 배냥이, 2026-10-09)
        prisons.none { it.catKind == AllyCat.Kind.BOAT && !it.isOpen }

    init {
        // 지형 굴곡 설정: 스테이지가 오를수록 울퉁불퉁해진다
        Terrain.stage = stageNumber
        // 편집 화면에서 저장한 배치가 있으면 그것으로, 없으면 기본 생성으로 (대표님 2026-10-31 「스테이지들 편집할 수 있는 화면」).
        // 편집 배치는 디버그 빌드에서만 쓴다 (대표님 2026-10-31 「디버그 모드에서만야」) — 릴리스 빌드는 늘 기본 생성
        val edited = if (StageEdits.enabled) StageEdits.load(stageNumber) else null
        build(edited ?: defaultLayout(stageNumber))
        totalEnemies = enemies.size
    }

    /** 배치 목록(Placement)을 실제 개체로 만든다 — 기본 생성이든 편집한 배치든 같은 길 */
    private fun build(layout: List<Placement>) {
        // 냥이 감옥은 냥이를 다 만든 뒤에 (갇힌 냥이를 정하지 않은 감옥은 이 스테이지 냥이 중에서 고른다)
        for (p in layout) {
            if (p.type == PlaceType.PRISON_CAT) continue
            when (val e = createEntity(p, stageNumber)) {
                is Enemy -> enemies.add(e)
                // 늪마다 한 마리가 숨어 있다 (기획9) — 꿀 늪은 꿀냥이 (2026-10-09)
                is Swamp -> { swamps.add(e); enemies.add(if (e.honey) HoneyCat(e) else MudCat(e)) }
                is Trap -> traps.add(e)
                is PitTrap -> pits.add(e)
                is IceHole -> iceHoles.add(e)
                is Chasm -> chasms.add(e)
                is TornadoTrap -> tornadoTraps.add(e)
                is SecretRoom -> secretRooms.add(e)
                is Prison -> prisons.add(e)
            }
        }

        // 냥이 감옥: 갇힌 냥이를 정했으면(표의 '총냥이x1'·편집 화면의 냥이 고르기) 꼭 그 냥이.
        // 정하지 않았으면(무작위) 70%는 이 스테이지에 나오는 냥이 중 하나 — 모든 냥이를 구출해 우리 팀으로 (보스·배냥이 빼고, 늪 속 냥이 포함, 2026-10-10).
        // 나머지 30%는 예전처럼(새끼+엄마 또는 구할 때 무작위). 비율은 Claude 판단
        val prisonPool = enemies.filter { !it.isBoss && it !is BoatCat }.mapNotNull { AllyCat.kindForEnemy(it) }
        for (p in layout) {
            if (p.type != PlaceType.PRISON_CAT) continue
            // 편집 화면에서 '엄마+새끼'로 정한 감옥은 꼭 엄마+새끼
            if (p.withKitten) { prisons.add(Prison(p.x, Terrain.groundY(p.x), Prison.Kind.CAT, forceKitten = true)); continue }
            val catKind = p.catKind ?: if (Math.random() < 0.7) prisonPool.randomOrNull() else null
            prisons.add(Prison(p.x, Terrain.groundY(p.x), Prison.Kind.CAT, catKind = catKind))
        }

        // 실제 스테이지 길이: 맨 끝에 놓인 것 뒤로 여유 (감옥·카메라가 이 안에 들어온다)
        areaWidth = areaWidthFor(stageNumber, layout)

        // 아군 냥이는 처음부터 따라오지 않는다 —
        // 기획1: "고양이를 구해야지 아군이 된다" (냥이 감옥을 부수면 그때 합류)

        // 공중 발판(벽돌판)은 뺐다 (대표님 2026-09-27 「위에 떠 있는 벽돌 모두 없애」) — platforms 는 늘 비어 있다
        // 벌집 구조물은 뺐다 (대표님 2026-10-04 「벌집을 없애줘」) — 벌냥이는 다른 냥이처럼 표에서 바로 배치되어 날아다닌다

        // 해적선은 바다의 맨 끝 (대표님 2026-10-09) — 해적냥이가 그 앞을 지킨다 (배는 편집으로 놓지 않고 해적냥이 자리에 따라 저절로)
        if (stageNumber == 31) {
            val shipX = shipXFor(layout)
            pirateShipX = shipX
            setupDeck(shipX)
            // 해적냥이는 해적선 위에 타고 있다 — 갑판 밖으로는 안 나온다
            for (b in enemies.filterIsInstance<PirateCatBoss>()) {
                b.x = shipX + 60f
                b.deckLeft = Terrain.deckLeft; b.deckRight = Terrain.deckRight
            }
        }

        // ===== 정글(12~20)의 덩굴 그네: 냥이 무리 사이 빈 구간에 매달아 둔다 (편집해도 저절로) =====
        // 끝은 마지막 잡몹 + 1000 까지 (기본 배치의 '그룹 사이' 자리와 같아지게 — Claude 판단)
        if (stageNumber in 12..20) {
            val lastMob = layout.filter { it.type.group == PlaceGroup.CAT }.maxOfOrNull { it.x } ?: 0f
            var i = 0
            while (true) {
                val vx = PATH_START + (i + 1) * GROUP_SPACING - GROUP_SPACING / 2f + 120f
                if (vx > lastMob + 1000f || vx > areaWidth - END_MARGIN - 300f) break
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
                i++
            }
        }
    }

    companion object {
        private const val PATH_START = 1300f          // 첫 조우 지점 (시작 지점에서 반 화면쯤 전진)
        private const val GROUP_SPACING = 1250f       // 그룹 간 간격 — 조우가 끊기지 않고 이어진다
        /** 맨 끝에 놓인 것 뒤로 남기는 길이 — 예전 '보스 자리 뒤로 여유 두 화면'(1800)과 같다 */
        private const val END_MARGIN = 1800f
        /** 31: 해적선 가운데에서 스테이지 끝까지 (예전 보스 자리 + 1800 = 배 + 650) */
        private const val SHIP_END = 650f
        /** 31: 기본 배치에서 해적선은 보스 자리 + 1150, 해적냥이는 배 + 60 */
        private const val SHIP_FROM_BOSS_BASE = 1150f
        private const val BOSS_ON_SHIP = 60f

        /** 편집 화면에서 놓을 수 있는 x 범위 (출발점 바로 앞·스테이지 최대 길이 안 — Claude 판단) */
        const val MIN_PLACE_X = 500f
        const val MAX_PLACE_X = Constants.PLAY_AREA_RIGHT - END_MARGIN - 400f

        /** 배치 하나를 실제 개체로 (냥이 = Enemy, 함정·물건 = 각자의 클래스). 편집 화면 미리보기도 이것을 그대로 그린다 */
        fun createEntity(p: Placement, stage: Int): Any? {
            val x = p.x
            return when (p.type) {
                PlaceType.SWAMP -> Swamp(x, Terrain.groundY(x + 150f), seed = p.seed, honey = stage in 21..30)   // 꿀 지역은 꿀 늪 (2026-10-09)
                PlaceType.SPIKE -> Trap(x, Terrain.groundY(x + 75f))
                PlaceType.PIT -> PitTrap(x, Terrain.groundY(x + 85f), seed = p.seed)
                PlaceType.ICE_HOLE -> IceHole(x, Terrain.groundY(x + IceHole.WIDTH / 2f))
                PlaceType.CHASM -> {
                    val w = Chasm(x, 0f, p.seed).width
                    Chasm(x, Terrain.groundY(x + w / 2f), p.seed)
                }
                PlaceType.SHAFT -> Chasm(x, Terrain.groundY(x + Chasm.SHAFT_W / 2f), p.seed, shaft = true)
                PlaceType.TORNADO -> TornadoTrap(x, Terrain.groundY(x), seed = p.seed)
                PlaceType.SECRET -> SecretRoom(x, Terrain.groundY(x + 45f))
                PlaceType.PRISON_HUMAN -> Prison(x, Terrain.groundY(x), Prison.Kind.HUMAN)
                PlaceType.PRISON_CAT -> Prison(x, Terrain.groundY(x), Prison.Kind.CAT, catKind = p.catKind, forceKitten = p.withKitten)
                else -> p.type.factory?.invoke(x, Constants.GROUND_Y)
            }
        }

        /** 31 해적선 자리: 해적냥이가 있으면 그 발밑(냥이 - 60), 없으면 맨 끝 + 1150 */
        fun shipXFor(layout: List<Placement>): Float =
            layout.firstOrNull { it.type == PlaceType.BOSS_PIRATE }?.let { it.x - BOSS_ON_SHIP }
                ?: ((layout.maxOfOrNull { it.x } ?: PATH_START) + SHIP_FROM_BOSS_BASE)

        /**
         * 스테이지 길이 = 맨 끝에 놓인 것 + 1800. 보스가 없으면 마지막 냥이 + 2050 + 1800 까지는 둔다 (예전 길이와 같게).
         * 31 은 해적선 뒤 650 까지.
         */
        private const val NO_BOSS_TAIL = 2050f

        fun areaWidthFor(stage: Int, layout: List<Placement>): Float {
            val far = layout.filter { !(stage == 31 && it.type == PlaceType.BOSS_PIRATE) }.maxOfOrNull { it.x } ?: PATH_START
            var w = far + END_MARGIN
            // 보스가 없으면 예전처럼 마지막 냥이 무리 뒤에 빈 길(보스 자리 몫 약 2050)을 남긴다 — 길이가 예전과 같게 (2026-10-31)
            if (layout.none { it.type.group == PlaceGroup.BOSS }) {
                layout.filter { it.type.group == PlaceGroup.CAT }.maxOfOrNull { it.x }?.let { w = maxOf(w, it + NO_BOSS_TAIL + END_MARGIN) }
            }
            if (stage == 31) w = maxOf(w, shipXFor(layout) + SHIP_END)
            return w.coerceIn(3000f, Constants.PLAY_AREA_RIGHT)
        }

        /** 31 갑판 = 배 그림(높이 SHIP_DRAW_H, 바닥이 물 아래 60)에서 가운데 낮은 갑판 줄 — 그림 기준 비율로 잡았다 */
        fun setupDeck(shipX: Float) {
            Terrain.deckStage = 31
            Terrain.deckLeft = shipX - SHIP_DECK_BACK
            Terrain.deckRight = shipX + SHIP_DECK_FRONT
            Terrain.deckY = Constants.GROUND_Y - SHIP_DECK_RISE
        }

        /**
         * 기본 생성기 — 기획 표(StagePlan)로 배치 목록을 만든다. 무작위가 섞여 있어 부를 때마다 조금씩 다르다
         * (편집 화면은 편집하지 않은 스테이지를 열 때 한 번 만들어 보여 준다).
         */
        fun defaultLayout(stageNumber: Int): List<Placement> {
            // ===== 기획5 표: 냥이 구성 · 함정 · 비밀의 방 · 구출 =====
            // 새끼 고양이는 2~5 에 2, 6~10·12~20 에 3 마리 (대표님 2026-09-27, 마릿수 Claude 판단)
            // 노랑냥이는 「너무 안 나와」(대표님 2026-09-27)로 초원 4~10 에 2~4, 정글 12~20 에 2~4 마리 (마릿수는 Claude 판단)
            val plan = when (stageNumber) {
                1 -> StagePlan(mapOf(Kind.NORMAL to 8))
                2 -> StagePlan(mapOf(Kind.KITTEN to 2, Kind.NORMAL to 10), spikes = 1)
                3 -> StagePlan(mapOf(Kind.KITTEN to 2, Kind.NORMAL to 8, Kind.BLUE to 4), spikes = 1, humans = 1)
                4 -> StagePlan(mapOf(Kind.KITTEN to 2, Kind.YELLOW to 2, Kind.NORMAL to 9, Kind.BLUE to 5, Kind.RED to 2),
                    spikes = 1, secrets = 1, humans = 1, cats = 1)
                5 -> StagePlan(mapOf(Kind.KITTEN to 2, Kind.YELLOW to 2, Kind.NORMAL to 10, Kind.BLUE to 6, Kind.ORANGE to 2),
                    spikes = 1, secrets = 1, humans = 1, cats = 1)
                6 -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.NORMAL to 11, Kind.BLUE to 7, Kind.YELLOW to 4),
                    spikes = 2, secrets = 1, humans = 1, cats = 1)
                7 -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.YELLOW to 3, Kind.NORMAL to 9, Kind.BLUE to 6, Kind.RED to 2, Kind.ORANGE to 2),
                    spikes = 2, secrets = 2, cats = 2)
                8 -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.NORMAL to 9, Kind.BLUE to 6, Kind.GREEN to 3, Kind.YELLOW to 4),
                    spikes = 1, dangerPits = 1, secrets = 2, humans = 2)
                9 -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.NORMAL to 9, Kind.BLUE to 7, Kind.GREEN to 4, Kind.RED to 2, Kind.ORANGE to 2, Kind.YELLOW to 4),
                    spikes = 1, dangerPits = 1, secrets = 2, humans = 1, cats = 2)
                10 -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.NORMAL to 12, Kind.BLUE to 9, Kind.GREEN to 8, Kind.RED to 2, Kind.ORANGE to 2, Kind.YELLOW to 4),
                    secrets = 3,
                    bosses = listOf(Kind.BOSS_TELEPORT to 0f))

                // ===== 12~20 정글: 거대한 나무 사이로 덩굴냥이가 날아다닌다 (11은 하늘 스테이지) =====
                12 -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.YELLOW to 2, Kind.NORMAL to 6, Kind.BLUE to 4, Kind.VINE to 3),
                    spikes = 1, secrets = 1, humans = 1)
                13 -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.YELLOW to 2, Kind.NORMAL to 6, Kind.BLUE to 4, Kind.VINE to 4, Kind.POOP to 2),
                    spikes = 1, secrets = 1, cats = 1)
                14 -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.YELLOW to 3, Kind.NORMAL to 5, Kind.BLUE to 4, Kind.VINE to 4,
                    Kind.POOP to 2),
                    spikes = 1, secrets = 1, humans = 1, cats = 1)
                15 -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.YELLOW to 3, Kind.GUN to 2, Kind.NORMAL to 5, Kind.BLUE to 4, Kind.GREEN to 3,
                    Kind.VINE to 5),
                    spikes = 2, secrets = 2, humans = 1, cats = 1)
                16 -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.YELLOW to 3, Kind.GUN to 2, Kind.NORMAL to 4, Kind.BLUE to 4, Kind.GREEN to 3,
                    Kind.VINE to 5, Kind.POOP to 2, Kind.FLUFFY to 2),
                    spikes = 1, dangerPits = 1, secrets = 2, cats = 2)
                17 -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.YELLOW to 3, Kind.GUN to 3, Kind.NORMAL to 4, Kind.BLUE to 3, Kind.GREEN to 3,
                    Kind.VINE to 5, Kind.FLUFFY to 2, Kind.THIEF to 1),
                    spikes = 2, dangerPits = 1, secrets = 2, humans = 2)
                18 -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.YELLOW to 4, Kind.GUN to 3, Kind.NORMAL to 4, Kind.BLUE to 3, Kind.GREEN to 3,
                    Kind.VINE to 6, Kind.POOP to 2, Kind.FLUFFY to 2, Kind.THIEF to 1, Kind.ANVIL to 2),
                    spikes = 1, dangerPits = 1, secrets = 2, humans = 1, cats = 2)
                19 -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.YELLOW to 4, Kind.GUN to 3, Kind.NORMAL to 3, Kind.GREEN to 3, Kind.VINE to 6,
                    Kind.FLUFFY to 3, Kind.THIEF to 1, Kind.ANVIL to 2, Kind.LASER to 3),
                    spikes = 2, dangerPits = 2, secrets = 3, humans = 1, cats = 2)
                // ===== 21 꿀 스테이지 (기획10, 2026-09-27): 바닥의 벌집에서 벌냥이가 나오고 꽃냥이가 꽃가루를 뿜는다 =====
                // 특수 스테이지 표(기획12 `Plan/기획12_특수스테이지_21_31.jpg`, 2026-10-04): 벌냥이·냥이·꽃냥이 / 비밀의 방 1 / 구덩이 함정.
                // (마릿수·구출은 표에 없어 Claude 판단)
                21 -> StagePlan(mapOf(Kind.BEE to 16, Kind.FLOWER to 6, Kind.NORMAL to 4),   // 벌냥이 「많이」(2026-10-04) 8 → 16
                    dangerPits = 1, secrets = 1, humans = 1, cats = 1)
                // ===== 22~30 꿀 지역 (대표님 2026-10-04 「그냥 너가 21-30 만들어줘」 — 구성 전부 Claude 판단) =====
                // 벌냥이·꽃냥이가 중심, 다른 냥이를 조금씩 섞고 뒤로 갈수록 많아진다. 보스는 아직 없다(대표님이 정하면 넣는다).
                22 -> StagePlan(mapOf(Kind.BEE to 10, Kind.FLOWER to 4, Kind.NORMAL to 5, Kind.KITTEN to 3),
                    spikes = 1, secrets = 1, humans = 1)
                23 -> StagePlan(mapOf(Kind.BEE to 10, Kind.FLOWER to 5, Kind.NORMAL to 4, Kind.YELLOW to 2, Kind.KITTEN to 3),
                    spikes = 1, dangerPits = 1, secrets = 1, cats = 1)
                24 -> StagePlan(mapOf(Kind.BEE to 12, Kind.FLOWER to 5, Kind.GREEN to 3, Kind.NORMAL to 4),
                    spikes = 2, secrets = 2, humans = 1)
                25 -> StagePlan(mapOf(Kind.BEE to 12, Kind.FLOWER to 6, Kind.RED to 2, Kind.ORANGE to 2, Kind.NORMAL to 3, Kind.THIEF to 1),
                    spikes = 1, dangerPits = 1, secrets = 2, cats = 1)
                26 -> StagePlan(mapOf(Kind.BEE to 14, Kind.FLOWER to 6, Kind.GUN to 2, Kind.NORMAL to 3, Kind.KITTEN to 3),
                    spikes = 2, secrets = 2, humans = 1)
                27 -> StagePlan(mapOf(Kind.BEE to 14, Kind.FLOWER to 7, Kind.YELLOW to 3, Kind.ANVIL to 1, Kind.NORMAL to 3),
                    spikes = 1, dangerPits = 1, secrets = 2, cats = 1)
                28 -> StagePlan(mapOf(Kind.BEE to 16, Kind.FLOWER to 7, Kind.BLUE to 3, Kind.GREEN to 3, Kind.KITTEN to 3),
                    spikes = 2, dangerPits = 1, secrets = 2, humans = 2)
                29 -> StagePlan(mapOf(Kind.BEE to 16, Kind.FLOWER to 8, Kind.GUN to 3, Kind.RED to 2, Kind.ORANGE to 2, Kind.THIEF to 1),
                    spikes = 2, dangerPits = 1, secrets = 3, humans = 1, cats = 1)
                30 -> StagePlan(mapOf(Kind.BEE to 18, Kind.FLOWER to 8, Kind.YELLOW to 3, Kind.ANVIL to 2, Kind.NORMAL to 4),
                    dangerPits = 1, secrets = 3, cats = 2,
                    // 30 보스 = 여왕 벌냥이 (대표님 2026-10-10)
                    bosses = listOf(Kind.BOSS_QUEEN_BEE to 0f))
                // ===== 31 해적 스테이지 (특수 스테이지 표 기획12): 해적냥이 Boss·냥이·물냥이·배냥이 / 토네이도 함정 =====
                // 마릿수·구출·비밀의 방은 Claude 판단. 끝에 해적선이 있고 해적냥이가 그 앞을 지킨다.
                31 -> StagePlan(mapOf(Kind.NORMAL to 8, Kind.BLUE to 6, Kind.BOAT to 6),
                    secrets = 1, humans = 1, cats = 1, tornadoes = 2,
                    bosses = listOf(Kind.BOSS_PIRATE to 0f))
                // ===== 51~60 빙산 (대표님 2026-10-25, 위 머리말 표) — 마릿수·비밀의 방은 Claude 판단 =====
                51 -> StagePlan(mapOf(Kind.NORMAL to 8, Kind.ICE to 4),
                    secrets = 1, humans = 1, shafts = 1)
                52 -> StagePlan(mapOf(Kind.GREEN to 3, Kind.NORMAL to 8, Kind.ICE to 6, Kind.GUN to 2),
                    dangerPits = 1, secrets = 1, cats = 1, shafts = 1)
                53 -> StagePlan(mapOf(Kind.GREEN to 3, Kind.NORMAL to 8, Kind.ICE to 6, Kind.GUN to 2, Kind.TELEPORT to 2),
                    dangerPits = 1, secrets = 1, humans = 1, shafts = 1)
                54 -> StagePlan(mapOf(Kind.GREEN to 3, Kind.NORMAL to 8, Kind.ICE to 7, Kind.GUN to 3, Kind.TELEPORT to 2),
                    dangerPits = 1, secrets = 2, shafts = 1, fixedCats = listOf(AllyCat.Kind.GUN))
                55 -> StagePlan(mapOf(Kind.GREEN to 4, Kind.NORMAL to 8, Kind.ICE to 8, Kind.GUN to 3, Kind.TELEPORT to 3),
                    dangerPits = 1, secrets = 2, shafts = 1)
                56 -> StagePlan(mapOf(Kind.GREEN to 4, Kind.NORMAL to 7, Kind.ICE to 8, Kind.GUN to 3, Kind.TELEPORT to 3, Kind.HAMMER to 3),
                    dangerPits = 1, secrets = 2, shafts = 1)
                57 -> StagePlan(mapOf(Kind.GREEN to 4, Kind.NORMAL to 8, Kind.ICE to 9, Kind.GUN to 3, Kind.TELEPORT to 3),
                    secrets = 2, shafts = 3)
                58 -> StagePlan(mapOf(Kind.ICE to 12, Kind.BLUE to 8),
                    secrets = 2, shafts = 1)
                59 -> StagePlan(mapOf(Kind.ICE to 16),
                    secrets = 3, iceHoles = 2, fixedCats = List(5) { AllyCat.Kind.ICE })
                60 -> StagePlan(mapOf(Kind.ICE to 10, Kind.TELEPORT to 4, Kind.NORMAL to 8),
                    dangerPits = 1, secrets = 3, shafts = 1, iceHoles = 1, fixedCats = listOf(AllyCat.Kind.TELEPORT),
                    // 60 보스 = 빙산냥이 (기획15, 표의 'Boss')
                    bosses = listOf(Kind.BOSS_ICEBERG to 0f))
                // 정글의 끝: 마법사 냥이 삼형제가 신전을 지킨다 (원안 마지막 보스 디자인)
                else -> StagePlan(mapOf(Kind.KITTEN to 3, Kind.YELLOW to 4, Kind.GUN to 4, Kind.NORMAL to 4, Kind.GREEN to 3, Kind.VINE to 6, Kind.FLUFFY to 3,   // 20: 치즈냥이도 넣는다 (대표님 2026-09-20, 마릿수는 Claude 판단)
                    Kind.ANVIL to 3, Kind.LASER to 3, Kind.THIEF to 1),
                    secrets = 3,
                    bosses = listOf(Kind.BOSS_WIZARD to 0f, Kind.BOSS_WIZARD to 450f, Kind.BOSS_WIZARD to 900f))
            }
            // 늪(기획3): 2스테이지부터 하나, 7스테이지부터 둘 (함정은 과하지 않게)
            val icy = stageNumber in 51..60   // 빙산: 늪·낭떠러지 대신 수직 구덩이 (Claude 판단)
            val swampCount = if (stageNumber < 2 || icy) 0 else (1 + (stageNumber - 2) / 5).coerceAtMost(if (stageNumber == 31) 0 else if (stageNumber == 20 || stageNumber in 21..30) 1 else 2)   // 꿀 지역도 1개, 31 바다는 없음   // 20 은 「함정이 너무 많아」(2026-10-04)로 1개

            // 해머냥이는 모루냥이가 나오는 스테이지에만, 1~3마리 무작위로 섞인다 (대표님 2026-09-20)
            val mobs = plan.mobs.toMutableMap()
            if (Kind.ANVIL in mobs) mobs[Kind.HAMMER] = Kind.HAMMER_MIN + (Math.random() * (Kind.HAMMER_MAX - Kind.HAMMER_MIN + 1)).toInt()

            val out = mutableListOf<Placement>()
            // 잡몹을 섞은 뒤 일정한 간격의 구간 그룹(3마리)으로 배치
            val types = mutableListOf<PlaceType>()
            for ((kind, count) in mobs) {
                // 레이저냥이 자리의 절반은 총냥이 (대표님 2026-09-20 「원래 레이저냥이가 있던 곳에 50:50」)
                repeat(count) { types.add(if (kind == Kind.LASER && Math.random() < 0.5) Kind.GUN else kind) }
            }
            types.shuffle()

            val groupSize = 3
            val groupCount = ((types.size + groupSize - 1) / groupSize).coerceAtLeast(1)
            val pathStart = PATH_START
            val groupSpacing = GROUP_SPACING
            for ((i, type) in types.withIndex()) {
                val g = i / groupSize
                val gx = pathStart + g * groupSpacing
                // 그룹 안에서도 서로 떨어져 있게 (사용자: 냥이들이 붙어 있으면 안 된다)
                val ex = gx + ((Math.random() - 0.5) * 460.0).toFloat()
                out.add(Placement(type, ex))
            }
            // 보스는 마지막 잡몹 그룹 바로 뒤를 지킨다 (31 해적냥이는 그 뒤 해적선 위 — 배 = 보스 자리 + 1150)
            val bossBaseX = pathStart + groupCount * groupSpacing + 800f
            for ((type, dx) in plan.bosses) {
                out.add(Placement(type, if (stageNumber == 31 && type == Kind.BOSS_PIRATE) bossBaseX + SHIP_FROM_BOSS_BASE + BOSS_ON_SHIP else bossBaseX + dx))
            }

            // ===== 함정 배치: 그룹과 그룹 사이의 빈 구간 한가운데에 심는다 =====
            // (그룹 위에 겹치면 싸우다 억울하게 밟게 되므로 반드시 사이 공간에만)
            val gapCenters = (1..groupCount).map { pathStart + it * groupSpacing - groupSpacing / 2f }
            val hazardOrder = List(swampCount) { Hazard.SWAMP } +
                List(plan.spikes) { Hazard.SPIKE } +
                List(plan.dangerPits) { Hazard.PIT } +
                List(plan.iceHoles) { Hazard.ICE_HOLE }
            val hazardX = mutableListOf<Float>()
            if (hazardOrder.isNotEmpty()) {
                val n = hazardOrder.size.coerceAtMost(gapCenters.size)
                for (i in 0 until n) {
                    // 쉬운 함정이 앞, 위험한 함정이 뒤로 가도록 순서대로 고르게 배치
                    val slot = gapCenters[(i * gapCenters.size) / n]
                    val hx = slot - 80f
                    hazardX.add(hx)
                    when (hazardOrder[i]) {
                        Hazard.SWAMP -> out.add(Placement(PlaceType.SWAMP, hx, seed = stageNumber * 13 + i * 5))
                        Hazard.SPIKE -> out.add(Placement(PlaceType.SPIKE, hx))
                        Hazard.PIT -> out.add(Placement(PlaceType.PIT, hx, seed = stageNumber * 31 + i * 7))
                        Hazard.ICE_HOLE -> out.add(Placement(PlaceType.ICE_HOLE, hx))
                    }
                }
            }

            // ===== 낭떠러지: 3스테이지부터, 그룹 사이 빈 구간 중 함정이 없는 곳에 (대표님 2026-09-20 「슈퍼마리오처럼 낭떠러지」) =====
            // 폭 160~215 는 점프 사거리(약 300) 안이라 뛰어넘을 수 있다. 냥이들도 hazardAhead 로 뛰어넘는다.
            val chasmCount = if (stageNumber < 3 || icy) 0 else (1 + (stageNumber - 3) / 4).coerceAtMost(if (stageNumber == 31) 0 else if (stageNumber == 20) 2 else if (stageNumber in 21..30) 3 else 5)   // 꿀 지역 최대 3 (Claude 판단), 31 바다는 없음   // 20 은 5 → 2 (2026-10-04)
            val chasmFreeGaps = gapCenters.filter { gc -> hazardX.none { kotlin.math.abs(it - gc) < 500f } }
            val chasmX = mutableListOf<Float>()
            repeat(chasmCount) { i ->
                if (chasmFreeGaps.isEmpty()) return@repeat
                val gc = chasmFreeGaps[((i + 1) * chasmFreeGaps.size) / (chasmCount + 1) % chasmFreeGaps.size]
                val cx = gc + 260f                      // 빈 구간 뒤쪽(다음 그룹 앞)에
                if (chasmX.any { kotlin.math.abs(it - cx) < 700f }) return@repeat
                out.add(Placement(PlaceType.CHASM, cx, seed = stageNumber * 17 + i * 11))
                chasmX.add(cx)
                hazardX.add(cx - 80f)                    // 비밀의 방·감옥이 위에 안 놓이게
            }

            // ===== 수직 구덩이 (51~60 빙산): 함정이 없는 빈 구간 뒤쪽에 =====
            val shaftGaps = gapCenters.filter { gc -> hazardX.none { kotlin.math.abs(it - gc) < 500f } }
            repeat(plan.shafts) { i ->
                if (shaftGaps.isEmpty()) return@repeat
                val gc = shaftGaps[((i + 1) * shaftGaps.size) / (plan.shafts + 1) % shaftGaps.size]
                val cx = gc + 260f
                if (chasmX.any { kotlin.math.abs(it - cx) < 600f }) return@repeat
                out.add(Placement(PlaceType.SHAFT, cx, seed = stageNumber * 19 + i * 7))
                chasmX.add(cx)
                hazardX.add(cx - 80f)
            }

            // ===== 비밀의 방: 함정이 없는 빈 구간에 숨긴다 =====
            val freeGaps = gapCenters.filter { gc -> hazardX.none { kotlin.math.abs(it - gc) < 400f } }
            repeat(plan.secrets) { i ->
                val gx = if (freeGaps.isNotEmpty()) freeGaps[(i * freeGaps.size) / plan.secrets.coerceAtLeast(1) % freeGaps.size]
                else pathStart + (i + 1) * 900f
                out.add(Placement(PlaceType.SECRET, gx + 220f))
            }

            // ===== 감옥: 기획5의 구출 표 (인간/고양이 감옥을 번갈아 배치) =====
            // (감옥 종류, 꼭 갇혀 있을 냥이 — 표에서 정한 냥이면 그 종류, 아니면 null)
            val prisonKinds = mutableListOf<Pair<Prison.Kind, AllyCat.Kind?>>()
            repeat(plan.humans) { prisonKinds.add(Prison.Kind.HUMAN to null) }
            // 냥이 감옥을 많이 (대표님 2026-10-10 「냥이 감옥을 많이 넣어 줘」): 표의 마릿수 × 2 + 2 (배율은 Claude 판단)
            repeat(plan.cats * CAT_PRISON_MUL + CAT_PRISON_EXTRA) { prisonKinds.add(Prison.Kind.CAT to null) }
            // 표에서 갇힌 냥이를 정한 감옥 (54 총냥이·59 얼음냥이 5·60 순간냥이 — 2026-10-25): 꼭 그 냥이가 갇혀 있다
            for (k in plan.fixedCats) prisonKinds.add(Prison.Kind.CAT to k)
            prisonKinds.shuffle()
            for ((i, slot) in prisonKinds.withIndex()) {
                val (kind, fixedCat) = slot
                var px = pathStart + (i + 1) * (bossBaseX - pathStart) / (prisonKinds.size + 1).toFloat()
                // 함정 위에 겹치면 옆으로 비켜 놓는다
                while (hazardX.any { px > it - 220f && px < it + 500f }) px += 340f
                // 표에서 정한 냥이만 적어 둔다 — 나머지는 '무작위'(null): build 에서 70% 이 스테이지 냥이 / 30% 무작위
                out.add(Placement(if (kind == Prison.Kind.HUMAN) PlaceType.PRISON_HUMAN else PlaceType.PRISON_CAT, px, catKind = fixedCat))
            }
            // 30: 바다(31)에 나가려면 배냥이가 있어야 한다 — 배냥이가 갇힌 감옥을 하나 꼭 둔다 (2026-10-09)
            if (stageNumber == 30) {
                // 스테이지 끝 = 마지막 냥이 무리 바로 뒤 (더 멀리 두면 마지막 냥이를 잡는 순간 끝나 버려 못 구한다)
                var px = pathStart + (groupCount - 1) * groupSpacing + 420f
                while (hazardX.any { px > it - 220f && px < it + 500f }) px += 340f
                out.add(Placement(PlaceType.PRISON_CAT, px, catKind = AllyCat.Kind.BOAT))
            }

            // ===== 31 해적: 토네이도 함정을 빈 구간에 (해적선은 build 에서 해적냥이 자리에 맞춰 놓는다) =====
            if (plan.tornadoes > 0) {
                val free = gapCenters.filter { gc -> hazardX.none { kotlin.math.abs(it - gc) < 450f } }
                for (i in 0 until plan.tornadoes) {
                    if (free.isEmpty()) break
                    val tx = free[((i + 1) * free.size) / (plan.tornadoes + 1) % free.size]
                    out.add(Placement(PlaceType.TORNADO, tx, seed = stageNumber + i * 3))
                    hazardX.add(tx - 80f)
                }
            }
            return out
        }
    }

    /** 경로에 심는 함정 종류 (앞에서 뒤로 갈수록 위험해진다) */
    private enum class Hazard { SWAMP, SPIKE, PIT, ICE_HOLE }

    private class StagePlan(
        val mobs: Map<PlaceType, Int>,
        val spikes: Int = 0,        // 일반 함정 (가시)
        val dangerPits: Int = 0,    // 위험한 함정 (구덩이)
        val secrets: Int = 0,       // 비밀의 방
        val humans: Int = 0,        // 구출: 인간 감옥
        val cats: Int = 0,          // 구출: 고양이 감옥 (부수면 아군 합류)
        /** 보스 (종류, 보스 자리에서 얼마나 뒤) — 20 마법사냥이 삼형제는 0·450·900 */
        val bosses: List<Pair<PlaceType, Float>> = emptyList(),
        val tornadoes: Int = 0,     // 토네이도 함정 (31 해적)
        val shafts: Int = 0,        // 수직 구덩이 (51~60 빙산)
        val iceHoles: Int = 0,      // 얼음 구덩이 (51~60 빙산)
        val fixedCats: List<AllyCat.Kind> = emptyList(),   // 꼭 이 냥이가 갇힌 감옥 (표의 '총냥이x1' 등)
    )

    fun onEnemyDefeated() {
        defeatedEnemies++
    }
}
