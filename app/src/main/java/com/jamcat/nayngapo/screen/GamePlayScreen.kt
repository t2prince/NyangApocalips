package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.Camera
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.entity.Player
import com.jamcat.nayngapo.entity.Wall
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.engine.Terrain
import com.jamcat.nayngapo.level.Level
import com.jamcat.nayngapo.ui.HUD
import com.jamcat.nayngapo.ui.TouchControls

class GamePlayScreen(
    private val gameView: GameView,
    private val stageNumber: Int
) : Screen {

    /** 스테이지 테마: 지면 스프라이트·하늘색·원경 오브젝트가 함께 바뀐다 */
    private enum class Theme { GRASS, JUNGLE, HONEY, SNOW, DESERT, BEACH, VOLCANO }
    /** 지역별 테마. 1~10 초원, 11 하늘(SkyStageScreen), 12~20 정글, 그 뒤는 만들어지는 대로. */
    private val theme = when {
        stageNumber <= 10 -> Theme.GRASS   // 초원: 잔디 + 나무 + 구름
        stageNumber <= 20 -> Theme.JUNGLE  // 정글: 거대한 나무 + 늘어진 덩굴
        stageNumber <= 30 -> Theme.HONEY   // 꿀: 21~30 (대표님 2026-10-04) — 벌집 바닥 + 큰 꽃 (기획10)
        stageNumber == 31 -> Theme.BEACH   // 해적: 바닷가 모래밭 + 바다, 끝에 해적선 (2026-10-04)
        stageNumber <= 40 -> Theme.SNOW    // 32~40 빙산 (예정)
        stageNumber <= 40 -> Theme.DESERT
        else -> Theme.VOLCANO
    }

    /** 31 해적 바다 (2026-10-09): 바닥이 모두 물 — 인간은 30에서 구한 배냥이를 타고 다닌다. 목숨 1, 쓰러지면 배냥이는 무덤 */
    private val isSea = stageNumber == 31
    private val seaMount: com.jamcat.nayngapo.engine.CatTeam.TeamCat? =
        if (isSea) com.jamcat.nayngapo.engine.CatTeam.boatMount() else null
    private var seaMountMarked = false
    private var boatHintShown = false

    /** 이 시간을 넘겨 겹쳐 있으면 서로 밀어낸다(초) */
    private val CROWD_LIMIT = 4f

    /** 멈추기 상태 */
    private var paused = false

    /** 스테이지 클리어 후 결과 화면으로 넘어가기까지 남은 시간 (음수 = 아직 클리어 전) */
    private var clearTimer = -1f
    private val STAGE_CLEAR_DELAY = 3f

    // 상단 왼쪽 메뉴 버튼 배치
    private val MENU_BTN_R = 46f
    /** 손가락은 그려진 원보다 굵다 — 판정만 넓힌다 (두 버튼 간격 122f보다 작게) */
    private val MENU_HIT_R = 60f
    private val MENU_BTN_Y = 118f
    private val PAUSE_BTN_X = 110f
    private val EXIT_BTN_X = 232f

    private val menuBtnPaint = Paint().apply { color = Color.argb(165, 16, 18, 26); isAntiAlias = true }
    private val exitBtnPaint = Paint().apply { color = Color.argb(165, 34, 18, 22); isAntiAlias = true }
    private val menuBtnEdgePaint = Paint().apply {
        color = Color.argb(60, 255, 255, 255); style = Paint.Style.STROKE
        strokeWidth = 1.5f; isAntiAlias = true
    }
    private val menuIconPaint = Paint().apply { color = Color.argb(230, 238, 242, 250); isAntiAlias = true }
    private val menuIconStrokePaint = Paint().apply {
        color = Color.argb(230, 255, 150, 140); strokeWidth = 4.5f
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val menuLabelPaint = Paint().apply {
        color = Color.argb(150, 226, 232, 244); textSize = 20f; textAlign = Paint.Align.CENTER
        letterSpacing = 0.1f; isAntiAlias = true
    }
    private val pauseVeilPaint = Paint().apply { color = Color.argb(185, 8, 10, 16) }
    private val pauseTextPaint = Paint().apply {
        color = Color.WHITE; textSize = 68f; textAlign = Paint.Align.CENTER
        letterSpacing = 0.24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    private val pauseHintPaint = Paint().apply {
        color = Color.argb(150, 226, 232, 244); textSize = 24f; textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val level = Level(stageNumber)
    private val player = Player(level.playerStartX, Constants.GROUND_Y)
    private val camera = Camera()
    private val touchControls = TouchControls()
    private val hud = HUD()

    private var respawnTimer = 0f
    private var waveTextTimer = 0f
    private var waveTextMessage = ""

    // Wave text paint
    private val waveTextPaint = Paint().apply {
        color = Color.WHITE; textSize = 52f; textAlign = Paint.Align.CENTER
        letterSpacing = 0.2f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        setShadowLayer(10f, 0f, 3f, Color.argb(180, 0, 0, 0))
    }

    // Hit effect
    private data class HitEffect(var x: Float, var y: Float, var timer: Float, val text: String)
    private val hitEffects = mutableListOf<HitEffect>()

    /** 팀 냥이에게 내린 지시 (기본은 곁을 지키는 '따라와') */
    private var currentOrder = com.jamcat.nayngapo.entity.AllyOrder.FOLLOW
    /** 지시 버튼이 커졌다 돌아오는 연출에 남은 시간 */
    private var orderPulse = 0f

    /** 지금 매달려 있는 덩굴 (null이면 땅 위) */
    private var ridingVine: com.jamcat.nayngapo.entity.Vine? = null
    /** 놓은 직후 같은 누름으로 다시 잡히지 않게 막는 시간 */
    private var vineGrabLockout = 0f

    /** 손을 뻗어 덩굴로 다가가는 중인 덩굴 (다 닿으면 ridingVine이 된다) */
    private var vineGrabTarget: com.jamcat.nayngapo.entity.Vine? = null
    /** 다가가는 진행도 0~1 */
    private var vineGrabT = 0f
    /** 누른 순간의 자리 — 여기서 덩굴 끝까지 끌어당긴다 */
    private var vineGrabFromX = 0f
    private var vineGrabFromY = 0f
    private var vineGrabFromZ = 0f

    /** 덩굴을 잡으러 가는 데 걸리는 시간 */
    private val VINE_GRAB_TIME = 0.18f
    /** 덩굴 아래에 처음 섰을 때 한 번만 알려 준다 */
    private var vineHintShown = false

    /** 이 스테이지에서 쓸 수 있는 고양이 기술 (배우고 켜 둔 것만, 최대 4개 — 묭묭 진화로 4칸) */
    private val battleSkills =
        com.jamcat.nayngapo.engine.CatSkills.all
            .filter { sk ->
                com.jamcat.nayngapo.engine.CatSkills.isActive(sk.id) ||   // 로비에서 배우고 켜 둔 기술
                    com.jamcat.nayngapo.engine.CatTeam.party.any { sk.id in it.skills }   // 출전 냥이가 천막에서 배운 기술
            }
            .take(com.jamcat.nayngapo.ui.TouchControls.SKILL_SLOTS)

    /** 기술별 남은 쿨타임 (스테이지를 시작할 때는 모두 준비 완료) */
    private val skillCooldowns = FloatArray(battleSkills.size) { i ->
        // 묭묭 진화가 아직 이어지는 중이면 그만큼 기다려야 다시 쓴다
        if (battleSkills[i].id == "evolve") com.jamcat.nayngapo.engine.CatTeam.evolveSecondsLeft else 0f
    }

    /** 이 스테이지에서 인간이 직접 쓸 수 있는 기술 (배운 것만, 최대 4개) */
    private val humanBattleSkills =
        com.jamcat.nayngapo.engine.HumanSkills.all
            .filter { com.jamcat.nayngapo.engine.HumanSkills.isLearned(it.id) }
            .take(com.jamcat.nayngapo.ui.TouchControls.HUMAN_SKILL_SLOTS)   // 인간 기술 버튼 칸 수만큼

    /** 인간 기술별 남은 쿨타임 (시작할 때는 모두 준비 완료) */
    private val humanSkillCooldowns = FloatArray(humanBattleSkills.size)

    // ===== 파이어 펀치 =====
    /** 주먹이 아직 적에게 닿지 않았다 (타격 프레임에서 한 번 때린다) */
    private var firePunchPending = false
    private var firePunchPower = 0
    private var emberSpawnT = 0f
    private val fist = android.graphics.PointF()

    /** 불붙은 적 → 화상 대미지까지 남은 시간 (Enemy는 건드리지 않고 여기서만 센다) */
    private val burning = HashMap<com.jamcat.nayngapo.entity.Enemy, Float>()

    /** 불똥 하나 (위로 떠오르며 사라진다) */
    private class Ember(
        var x: Float, var y: Float, val vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val size: Float
    )

    private val embers = mutableListOf<Ember>()
    private val emberPaint = Paint().apply { isAntiAlias = true }
    private val flameOuterPaint = Paint().apply { color = Color.rgb(255, 120, 40); isAntiAlias = true }
    private val flameInnerPaint = Paint().apply { color = Color.rgb(255, 206, 60); isAntiAlias = true }
    private val flamePath = Path()

    /** '연속발사'로 예약된 한 발 (delay가 0이 되면 날아간다) */
    private class PendingShot(
        var delay: Float,
        /** 쏘는 냥이 — 매 발 그 냥이의 지금 자리에서 나간다 */
        val caster: com.jamcat.nayngapo.entity.AllyCat,
        /** 기술을 쓸 때 정한 방향 (여섯 발 모두 같은 쪽으로) */
        val dir: Float,
        val damage: Int,
        val index: Int
    )

    private val pendingShots = mutableListOf<PendingShot>()

    /** '젤리발바닥'으로 떨어지는 젤리 하나 (splatT가 0 이상이면 부딪혀 퍼지는 중) */
    private class JellyDrop(var x: Float, var y: Float, val phase: Float) {
        var splatT = -1f
    }

    /** 젤리가 터질 때 튀는 작은 방울 */
    private class JellyDroplet(var x: Float, var y: Float, val vx: Float, var vy: Float, var life: Float)

    private val jellies = mutableListOf<JellyDrop>()
    private val jellyDroplets = mutableListOf<JellyDroplet>()
    /** 젤리 비가 내리는 남은 시간 */
    private var jellyRainT = 0f
    private var jellySpawnT = 0f
    private var jellyPower = 0
    /** 젤리 비를 부른 냥이 — 그 머리 위에 분홍 구름이 먼저 뜬다 */
    private var jellyCaster: com.jamcat.nayngapo.entity.AllyCat? = null
    /** 구름만 떠 있는 남은 시간 (이게 끝나야 젤리가 떨어진다) */
    private var jellyCloudT = 0f
    /** 구름이 흩어지는 연출 */
    private var jellyCloudFade = 0f
    /** 이번 비에서 떨어뜨린 젤리 수 (처음 세 개는 부른 냥이 가까이) */
    private var jellySpawned = 0
    /** 소리를 세 번에 한 번만 내려고 센다 */
    private var jellySplatCount = 0

    /** 지금 화면을 쓸고 지나가는 토네이도들 */
    private val tornadoes = mutableListOf<com.jamcat.nayngapo.entity.Tornado>()
    /** 인간 기술 '돌벽'의 돌가시 줄 */
    private val stoneWalls = mutableListOf<com.jamcat.nayngapo.entity.StoneWall>()
    private val freshSpikes = mutableListOf<Int>()

    /** 폭죽이 터질 때 흩어지는 색 불꽃 */
    private class Spark(
        var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int,
        val maxLife: Float = life, val size: Float = 5f, val streak: Boolean = true,
        /** 반짝임 (돌 조각처럼 반짝이지 않는 가루는 false) */
        val twinkle: Boolean = true
    )

    private val sparks = mutableListOf<Spark>()

    /** 주황냥이 용암 덩이가 떨어진 자리의 끓는 웅덩이 — 밟으면 하트 1 (대표님 2026-09-27, 2.5초·폭은 Claude 판단) */
    private class LavaPool(val x: Float, val y: Float) { var t = 0f }
    private val lavaPools = mutableListOf<LavaPool>()
    /** '막았다!' 글자가 너무 자주 뜨지 않게 */
    private var blockFx = 0f
    private val lavaPoolPaint = Paint().apply { color = Color.rgb(255, 110, 20); isAntiAlias = true }
    private val lavaPoolCrust = Paint().apply { color = Color.rgb(120, 30, 20); isAntiAlias = true }
    private val lavaPoolGlow = Paint().apply { color = Color.rgb(255, 225, 90); isAntiAlias = true }
    private val lavaRect = RectF()

    /** 폭죽이 터지는 순간의 번쩍임 (x, y, 남은 시간) */
    private class Flash(val x: Float, val y: Float, var life: Float)
    private val flashes = mutableListOf<Flash>()
    private val flashPaint = Paint().apply { isAntiAlias = true }
    private val flashRingPaint = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true
    }
    private val sparkStreakPaint = Paint().apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }

    private val jellyRect = RectF()
    private val jellyPadPaint = Paint().apply { color = Color.rgb(255, 150, 190); isAntiAlias = true }
    private val jellyToePaint = Paint().apply { color = Color.rgb(255, 120, 170); isAntiAlias = true }
    private val jellyShinePaint = Paint().apply { color = Color.rgb(255, 228, 238); isAntiAlias = true }
    private val jellyInkPaint = Paint().apply {
        color = Color.rgb(40, 34, 38); style = Paint.Style.STROKE
        strokeWidth = 2f; isAntiAlias = true
    }
    private val sparkPaint = Paint().apply { isAntiAlias = true }

    /** 이 스테이지에서 쓰러진 팀 냥이가 잠든 자리 (x, 바닥 y) — 남은 판 동안 비석이 서 있다 */
    private val graves = mutableListOf<Pair<Float, Float>>()
    private val graveSrc = Rect()
    private val graveDst = RectF()

    init {
        camera.forwardOnly = true   // 플랫포머식: 카메라가 뒤로 돌아가지 않는다
        if (isSea) { player.lives = 1; player.rideLift = SEA_RIDE_LIFT }
        // 약국 물약: 스테이지를 시작할 때 하나씩 저절로 마신다 (바다는 목숨 1 규칙이라 생명 물약은 안 마신다)
        if (!isSea && GameProgress.consumeItem("h_life")) player.lives += 1
        if (GameProgress.consumeItem("h_heal")) { player.maxHp += 2; player.hp = player.maxHp }
        showWaveText("STAGE $stageNumber - START!")

        // 관리 화면에서 출전으로 골라 둔 팀 냥이들이 플레이어 뒤에 줄지어 함께 출발한다.
        // 여기서 쓰러져도 명단에서 사라지지는 않는다 (이번 판만 못 싸운다).
        // 날아냥이는 하늘 스테이지에만 있다 (대표님 2026-09-20) — 땅에는 데려오지 않는다
        // 배냥이도 바다(31)에서 인간이 타는 냥이라 함께 걷지 않는다 (2026-10-09)
        val groundParty = com.jamcat.nayngapo.engine.CatTeam.party.filter {
            it.kind != com.jamcat.nayngapo.entity.AllyCat.Kind.FLYING && it.kind != com.jamcat.nayngapo.entity.AllyCat.Kind.BOAT
        }
        for ((i, cat) in groundParty.withIndex()) {
            level.allies.add(
                com.jamcat.nayngapo.entity.AllyCat(
                    // 출발선이 왼쪽 끝에 가까워도 화면 밖으로는 나가지 않게 한다
                    (level.playerStartX - 90f - 70f * i).coerceAtLeast(40f), Constants.GROUND_Y,
                    cat.kind, level = cat.level, teamId = cat.id,
                    // 잘 돌본 냥이는 더 튼튼하게 출전한다
                    hpMultiplier = com.jamcat.nayngapo.engine.CatTeam.careMultiplier(cat),
                    armored = cat.hasArmor,
                    attackBonus = cat.attackBonus,
                    coat = cat.coat,
                    speedMul = com.jamcat.nayngapo.engine.CatTeam.speedMultiplier(cat),
                    hpBonus = com.jamcat.nayngapo.engine.CatTeam.hpBonusOf(cat)
                ).also { it.evolve(com.jamcat.nayngapo.engine.CatTeam.evolveSecondsLeft, grown = true) }   // 묭묭 진화 중이면 큰 채로
            )
        }
    }

    private var elapsed = 0f   // GO → 표시 깜빡임용

    // 타격감용 카메라 셰이크
    private var shakeTimer = 0f
    private var shakeAmp = 0f

    private fun shake(duration: Float, amp: Float) {
        if (!GameProgress.shakeEnabled) return       // 설정: 화면 흔들림 끔
        if (amp >= shakeAmp || shakeTimer <= 0f) { shakeTimer = duration; shakeAmp = amp }
    }

    private companion object {
        /** 용암 웅덩이가 남는 시간 · 반폭 (Claude 판단) */
        private const val LAVA_POOL_TIME = 2.5f
        private const val LAVA_POOL_HALF = 55f
        /** 꽃가루에 숨이 막혀 느려지는 시간 (Claude 판단) */
        /** 배냥이 위에 올라탄 인간을 그만큼 위로 그린다 (Claude 판단) */
        private const val SEA_RIDE_LIFT = 58f
        /** 해적선 항해 (Claude 판단) */
        private const val SHIP_SPEED = 45f
        private const val SHIP_MAX_TRAVEL = 1600f
        private const val SHIP_WAKE_RANGE = 2200f
        private const val SHIP_STOP_GAP = 60f
        private const val CHOKE_TIME = 1.5f
        /** 젤리 발바닥 반지름 */
        const val JELLY_R = 22f

        /** 파이어 펀치가 적에게 닿는 순간 (7프레임 중 주먹을 다 뻗은 4번째 프레임) */
        const val FIRE_PUNCH_STRIKE = 3f / 7f

        /** 화상 대미지가 들어가기까지 */
        const val BURN_DELAY = 10f

        /** 미사일이 날 수 있는 시간 — 다 되면 그 자리에서 터진다 */
        const val MISSILE_LIFETIME = 2.5f

        /** 미사일 폭발 반경 */
        const val MISSILE_BLAST_RADIUS = 120f

        /** 젤리 비 앞에 구름만 떠 있는 시간 */
        const val JELLY_CLOUD_TIME = 0.2f

        /** 폭죽 불꽃 색 */
        const val FLASH_LIFE = 0.16f
        val FIREWORK_COLORS = intArrayOf(
            Color.rgb(226, 62, 52), Color.rgb(255, 214, 74),
            Color.rgb(70, 130, 226), Color.rgb(90, 200, 100)
        )
    }

    /** 모래바람이 밀어 올리는 높이: 개체마다 위상을 달리해 들썩임이 겹치지 않게 한다 */
    private fun windLiftAt(worldX: Float): Float =
        -5f - 5f * (kotlin.math.sin(elapsed * 3.4 + worldX * 0.012).toFloat() * 0.5f + 0.5f)

    private fun showWaveText(msg: String) {
        waveTextMessage = msg
        waveTextTimer = 2f
    }

    override fun update(deltaTime: Float) {
        if (paused) return          // 멈추기 상태에서는 모든 것이 정지한다

        // Process input
        processInput()

        // Respawn logic
        if (player.needsRespawn()) {
            respawnTimer += deltaTime
            if (respawnTimer > 1.5f) {
                // 진행 지점(현재 화면 왼쪽)에서 부활: 한 방향 카메라라 시작점으로 돌아갈 수 없다
                player.respawn(camera.x + 150f, Constants.GROUND_Y)
                respawnTimer = 0f
            }
            return
        }

        if (player.isDead) {
            // 바다에서 쓰러지면 타고 있던 배냥이도 물에 빠져 무덤이 된다 (날아냥이처럼)
            if (isSea && !seaMountMarked) { seaMountMarked = true; seaMount?.let { com.jamcat.nayngapo.engine.CatTeam.markDead(it.id, stageNumber) } }
            gameView.showGameOver(player.score)
            return
        }

        // 기술 쿨타임 + 버튼에 넘길 값
        for (i in skillCooldowns.indices) {
            if (skillCooldowns[i] > 0f) skillCooldowns[i] -= deltaTime
        }
        touchControls.skills = battleSkills
        touchControls.skillCooldowns = skillCooldowns
        touchControls.skillsHaveCaster = level.allies.any { !it.isDead }
        for (i in humanSkillCooldowns.indices) {
            if (humanSkillCooldowns[i] > 0f) humanSkillCooldowns[i] -= deltaTime
        }
        touchControls.humanSkills = humanBattleSkills
        touchControls.humanSkillCooldowns = humanSkillCooldowns

        updateFirePunch(deltaTime)
        updateBurning(deltaTime)
        updateEmbers(deltaTime)
        updateSmokePuffs(deltaTime)

        // '연속발사': 예약된 발바닥 탄을 0.1초 간격으로 쏜다 (쏘는 건 기술을 쓴 냥이)
        run {
            val it = pendingShots.iterator()
            while (it.hasNext()) {
                val shot = it.next()
                shot.delay -= deltaTime
                if (shot.delay > 0f) continue
                it.remove()
                val c = shot.caster
                if (c.isDead) continue          // 쏘던 냥이가 쓰러지면 남은 탄은 사라진다
                val dir = shot.dir
                val hb = c.hitBox
                level.projectiles.add(
                    com.jamcat.nayngapo.entity.Projectile(
                        c.x + c.width / 2f + dir * 40f,
                        hb.top + (hb.bottom - hb.top) * 0.40f,
                        dir * 760f, 0f, shot.damage, true,
                        tint = Color.rgb(255, 214, 74), radius = 12f,
                        fuse = 1.2f, isPaw = true
                    )
                )
                // 소리는 한 발 건너 한 번만 (여섯 발이 겹쳐 시끄럽지 않게)
                if (shot.index % 2 == 0) SoundManager.playAttack()
            }
        }

        updateJellyRain(deltaTime)

        // 토네이도: 지나가는 자리의 적을 한 번씩 휘감는다
        run {
            val it = tornadoes.iterator()
            while (it.hasNext()) {
                val tor = it.next()
                tor.update(deltaTime)
                for (enemy in level.enemies) {
                    if (enemy.isDead || enemy in tor.hit) continue
                    if (kotlin.math.abs(enemy.x + enemy.width / 2f - tor.x) >
                        com.jamcat.nayngapo.entity.Tornado.REACH
                    ) continue
                    tor.hit.add(enemy)
                    enemy.jumpZ = -60f          // 휘말려 살짝 떠오른다
                    damageEnemy(enemy, tor.power)
                }
                if (tor.isDone) it.remove()
            }
        }

        // 돌벽: 솟은 돌가시에 닿은 적을 한 번씩 찌른다
        run {
            val it = stoneWalls.iterator()
            while (it.hasNext()) {
                val wall = it.next()
                wall.update(deltaTime)
                wall.takeFreshSpikes(freshSpikes)
                for (i in freshSpikes) spikeDust(wall.spikeXOf(i), wall.groundOf(i))
                for (enemy in level.enemies) {
                    if (enemy.isDead || enemy in wall.hit) continue
                    if (!wall.touches(enemy.hitBox)) continue
                    wall.hit.add(enemy)
                    enemy.jumpZ = -40f          // 찔려 살짝 튀어오른다
                    damageEnemy(enemy, wall.power)
                }
                if (wall.isDone) it.remove()
            }
        }

        // 닌자 인간: 공격할 때마다 표창이 곧게 날아간다 (처음 맞은 적 하나에게 공격력만큼)
        if (player.pendingShuriken) {
            player.pendingShuriken = false
            val dir = if (player.facingRight) 1f else -1f
            level.projectiles.add(
                com.jamcat.nayngapo.entity.Projectile(
                    player.x + player.width / 2f + dir * 40f,
                    player.y - player.height * 0.62f,
                    dir * 820f, 0f, player.attackPower, true,
                    radius = 9f, fuse = 0.8f, isShuriken = true
                )
            )
        }

        // 파란 인간: 공격할 때마다 폭죽을 앞으로 던진다
        if (player.pendingFirework) {
            player.pendingFirework = false
            val dir = if (player.facingRight) 1f else -1f
            level.projectiles.add(
                com.jamcat.nayngapo.entity.Projectile(
                    player.x + player.width / 2f + dir * 50f,
                    player.y - player.height * 0.55f,
                    dir * 520f, -180f, player.attackPower, true,
                    tint = Color.rgb(226, 62, 52), radius = 10f,
                    gravity = 500f, fuse = 0.7f, isFirework = true
                )
            )
            SoundManager.playAttack()
        }

        // 터진 폭죽의 불꽃
        run {
            val it = sparks.iterator()
            while (it.hasNext()) {
                val sp = it.next()
                val drag = (1f - 2.2f * deltaTime).coerceAtLeast(0f)
                sp.vx *= drag
                sp.vy = sp.vy * drag + 240f * deltaTime
                sp.x += sp.vx * deltaTime
                sp.y += sp.vy * deltaTime
                sp.life -= deltaTime
                if (sp.life <= 0f) it.remove()
            }
            val fi = flashes.iterator()
            while (fi.hasNext()) {
                val f = fi.next()
                f.life -= deltaTime
                if (f.life <= 0f) fi.remove()
            }
        }

        // 지시 버튼·HUD: 살아 있는 아군이 있을 때만 보여 준다
        if (orderPulse > 0f) orderPulse -= deltaTime
        val hasAllies = level.allies.any { !it.isDead }
        touchControls.showOrder = hasAllies
        touchControls.orderLabel = currentOrder.label
        touchControls.orderColor = when (currentOrder) {
            com.jamcat.nayngapo.entity.AllyOrder.ATTACK -> Color.rgb(255, 118, 106)
            com.jamcat.nayngapo.entity.AllyOrder.FOLLOW -> Color.rgb(255, 214, 74)
            com.jamcat.nayngapo.entity.AllyOrder.HOLD -> Color.rgb(150, 170, 200)
        }
        touchControls.orderPulse = orderPulse
        hud.allyOrderLabel = if (hasAllies) currentOrder.label else null

        // 덩굴 그네는 계속 흔들린다
        for (vine in level.vines) vine.update(deltaTime)
        if (vineGrabLockout > 0f) vineGrabLockout -= deltaTime

        // 31 해적선은 앞(왼쪽, 뱃머리 쪽)으로 나아간다 — 갑판 위의 인간·냥이도 함께 실려 간다
        if (isSea) sailPirateShip(deltaTime)

        // Update player
        player.update(deltaTime)

        // 덩굴로 다가가는 중: 누른 자리에서 덩굴 끝까지 0.18초 동안 미끄러져 간다
        vineGrabTarget?.let { vine ->
            vineGrabT = (vineGrabT + deltaTime / VINE_GRAB_TIME).coerceAtMost(1f)
            val t = vineGrabT
            val toX = vine.bottomX - player.width / 2f
            val toY = Terrain.groundY(vine.bottomX)
            val toZ = vine.bottomY - toY
            player.x = vineGrabFromX + (toX - vineGrabFromX) * t
            player.y = vineGrabFromY + (toY - vineGrabFromY) * t
            player.jumpZ = vineGrabFromZ + (toZ - vineGrabFromZ) * t
            player.rideAngleDeg = vine.angleDegrees * t      // 몸도 서서히 덩굴 기울기로
            if (t >= 1f) {
                ridingVine = vine        // 이제 완전히 매달렸다
                vineGrabTarget = null
                vineGrabT = 0f
            }
        }

        // 매달려 있으면 몸이 덩굴 끝을 따라간다
        ridingVine?.let { vine ->
            player.x = vine.bottomX - player.width / 2f
            player.y = Terrain.groundY(vine.bottomX)
            player.jumpZ = vine.bottomY - player.y
            player.rideAngleDeg = vine.angleDegrees
        }

        // 덩굴 아래에 처음 서면 잡는 방법을 알려 준다
        if (!vineHintShown && ridingVine == null && vineGrabTarget == null &&
            vineUnderPlayer() != null
        ) {
            vineHintShown = true
            hitEffects.add(HitEffect(player.x, player.y - player.height - 40f, 1.4f, "점프로 덩굴 잡기!"))
        }

        // Update enemies
        for (enemy in level.enemies) {
            if (enemy.isDead) {
                enemy.updateDeath(deltaTime)      // 쓰러진 냥이는 사망 모션만 재생
                continue
            }
            if (!enemy.isDead) {
                enemy.update(deltaTime, player.x, player.y)
                // Collect projectiles
                for (proj in enemy.getProjectiles()) {
                    level.projectiles.add(proj)
                }
                // 처음 가까이서 만난 냥이는 도감에 등록된다 (기획1)
                if (kotlin.math.abs(enemy.x - player.x) < 1400f) {
                    com.jamcat.nayngapo.engine.CatDex.idOf(enemy)?.let { id ->
                        if (GameProgress.discover(id)) {
                            hitEffects.add(HitEffect(enemy.x, enemy.y - 150f, 1.3f, "도감에 등록!"))
                        }
                    }
                }
            }
        }

        // 아군 냥이: 적을 쫓아가 공격하고, 적이 없으면 플레이어 곁을 지킨다.
        // 무적이 아니라 적과 부딪히거나 구덩이에 빠지면 피해를 입는다.
        for (ally in level.allies) {
            ally.update(deltaTime, player.x, player.y, level.enemies)
            if (ally.isDead) continue            // 쓰러진 아군은 사망 모션만 재생
            level.projectiles.addAll(ally.getProjectiles())
            // 냥이가 스스로 외친 한마디 ("따라간다냥!")
            ally.pendingCallout?.let { shout ->
                hitEffects.add(HitEffect(ally.x, ally.y - ally.height - 30f, 0.9f, shout))
                ally.pendingCallout = null
            }
            ally.pendingMeleeTarget?.let { target ->
                if (!target.isDead) damageEnemy(target, ally.meleeDamage)
            }

            // 적 냥이와 몸이 닿으면 물린다
            for (enemy in level.enemies) {
                if (enemy.isDead) continue
                if (RectF.intersects(ally.hitBox, enemy.hitBox)) {
                    ally.takeDamage(enemy.attackPower)
                    break
                }
            }

            // 구덩이에 빠지면 피해를 입고 가장자리로 밀려난다
            for (pit in level.pits) {
                if (!pit.swallows(ally.x + ally.width / 2f)) continue
                ally.takeDamage(6)
                ally.x = pit.x - ally.width - 8f
                break
            }
        }
        // 쓰러진 아군은 전장에서 사라진다. 팀 냥이라면 명단에 무덤이 남고 그 자리에 비석이 선다.
        for (ally in level.allies) {
            if (!ally.isGone) continue
            val teamId = ally.teamId ?: continue
            val cat = com.jamcat.nayngapo.engine.CatTeam.cat(teamId)
            if (cat != null && !cat.isDead) {
                com.jamcat.nayngapo.engine.CatTeam.markDead(teamId, stageNumber)
                hitEffects.add(HitEffect(ally.x + ally.width / 2f, ally.y - 150f, 1.6f, "${cat.name}... 잘 가"))
            }
            graves.add(ally.x + ally.width / 2f to ally.y)
        }
        level.allies.removeAll { it.isGone }

        // 가시 함정: 가시가 솟았을 때 밟으면 피해 (점프로 뛰어넘으면 안전)
        for (trap in level.traps) {
            trap.update(deltaTime)
            if (trap.isDangerous && !player.isInvincible && !player.isJumping &&
                !player.ridingVine && RectF.intersects(player.hitBox, trap.hitBox)
            ) {
                player.takeDamage(1)
                shake(0.2f, 12f)
                hitEffects.add(HitEffect(player.x, player.y - player.height, 0.6f, "TRAP!"))
            }
            if (trap.isDangerous) {
                for (ally in level.allies) {
                    if (RectF.intersects(ally.hitBox, trap.hitBox)) ally.takeDamage(5)
                }
                // 함정은 편을 가리지 않는다 — 적 냥이도 가시를 밟으면 다친다
                for (enemy in level.enemies) {
                    if (!enemy.isDead && enemy.isTangible &&
                        RectF.intersects(enemy.hitBox, trap.hitBox)
                    ) damageEnemy(enemy, 4)
                }
            }
        }

        // 적 냥이도 구덩이에 빠지면 다치고 가장자리로 밀려난다 (공중의 덩굴냥이는 제외)
        for (pit in level.pits) {
            for (enemy in level.enemies) {
                if (enemy.isDead || !enemy.isTangible) continue
                if (enemy is com.jamcat.nayngapo.entity.VineCat) continue
                if (!pit.swallows(enemy.x + enemy.width / 2f)) continue
                damageEnemy(enemy, 6)
                enemy.x = if (enemy.x + enemy.width / 2f < pit.x + pit.width / 2f)
                    pit.x - enemy.width - 8f else pit.x + pit.width + 8f
            }
        }

        // 늪: 걸어 들어간 냥이(적·아군)는 5배 느려진다 (공중의 덩굴냥이는 제외)
        for (swamp in level.swamps) {
            for (enemy in level.enemies) {
                if (enemy.isDead || enemy is com.jamcat.nayngapo.entity.VineCat || enemy is com.jamcat.nayngapo.entity.MudCat) continue
                if (swamp.swallows(enemy.x + enemy.width / 2f)) {
                    enemy.x -= enemy.velocityX * deltaTime * 0.8f
                }
            }
            for (ally in level.allies) {
                if (swamp.swallows(ally.x + ally.width / 2f)) {
                    ally.x -= ally.velocityX * deltaTime * 0.8f
                }
            }
        }

        // 구덩이 함정: 걸어서 안쪽까지 들어가면 빠져서 그대로 게임 오버 (점프로 넘어야 한다)
        for (pit in level.pits) {
            if (player.isJumping || player.ridingVine) continue   // 덩굴에 매달려 있으면 빠지지 않는다
            if (!pit.swallows(player.x + player.width / 2f)) continue

            shake(0.35f, 18f)
            hitEffects.add(HitEffect(player.x, player.y - player.height, 1f, "빠졌다!"))
            player.fallIntoPit()
            break
        }

        // 바닥이 없는 낭떠러지: 발이 허공에 닿는 순간 그대로 떨어져 즉사한다
        for (chasm in level.chasms) {
            if (player.isJumping || player.ridingVine || player.onPlatform != null) continue
            if (!chasm.swallows(player.x + player.width / 2f)) continue

            shake(0.4f, 20f)
            hitEffects.add(HitEffect(player.x, player.y - player.height, 1f, "떨어졌다!"))
            player.fallIntoPit()
            break
        }

        // Update projectiles
        // 미사일: 화면 안의 가장 가까운 적 쪽으로 방향을 튼다 (없으면 곧게 난다)
        steerMissiles(deltaTime)

        level.projectiles.forEach { it.update(deltaTime) }

        // 도화선이 다 탄 폭죽은 치우기 전에 그 자리에서 터뜨린다
        // (여기서 처리하지 않으면 아래 removeAll에 먼저 쓸려 나가 아무 일도 일어나지 않는다)
        for (proj in level.projectiles) {
            if (!proj.fuseBurnedOut || !(proj.isFirework || proj.isMissile)) continue
            proj.fuseBurnedOut = false
            val bx = proj.x + proj.width / 2f
            val by = proj.y + proj.height / 2f
            // 미사일은 2.5초가 다 되면 그 자리에서 터진다
            if (proj.isMissile) explodeMissile(bx, by, proj.damage) else explodeFirework(bx, by)
        }

        // 미사일이 땅에 닿으면 그 자리에서 터진다
        for (proj in level.projectiles) {
            if (!proj.isMissile || !proj.isAlive) continue
            val mx = proj.x + proj.width / 2f
            val ground = Terrain.groundY(mx)
            if (proj.y + proj.height >= ground) {
                proj.isAlive = false
                explodeMissile(mx, ground - 6f, proj.damage)
            }
        }

        // 먹구름 번개가 땅에 닿으면 불꽃을 튀기고 사라진다
        for (proj in level.projectiles) {
            if (!proj.isLightning || !proj.isAlive) continue
            val lx = proj.x + proj.width / 2f
            val ground = Terrain.groundY(lx)
            if (proj.y + proj.height >= ground) {
                proj.isAlive = false
                repeat(6) { i ->
                    val ang = (-Math.PI * (0.1 + 0.8 * i / 5.0)).toFloat()
                    val v = 180f + Math.random().toFloat() * 160f
                    sparks.add(
                        Spark(lx, ground - 4f, kotlin.math.cos(ang) * v, kotlin.math.sin(ang) * v, 0.3f,
                            Color.rgb(255, 230, 90), size = 3.5f)
                    )
                }
            }
        }

        // 용암 덩이가 땅에 닿으면 튀면서 끓는 웅덩이를 남긴다
        for (proj in level.projectiles) {
            if (!proj.isLava || !proj.isAlive) continue
            val lx = proj.x + proj.width / 2f
            val ground = Terrain.groundY(lx)
            if (proj.y + proj.height >= ground) {
                proj.isAlive = false
                // 낭떠러지 위에 떨어지면 웅덩이 없이 그냥 떨어져 사라진다
                if (level.chasms.none { lx > it.x && lx < it.x + it.width }) lavaPools.add(LavaPool(lx, ground))
                repeat(5) { i ->
                    val ang = (-Math.PI * (0.15 + 0.7 * i / 4.0)).toFloat()
                    val v = 140f + Math.random().toFloat() * 120f
                    sparks.add(Spark(lx, ground - 4f, kotlin.math.cos(ang) * v, kotlin.math.sin(ang) * v, 0.35f,
                        Color.rgb(255, 130, 30), size = 4f))
                }
            }
        }
        val poolIt = lavaPools.iterator()
        while (poolIt.hasNext()) {
            val pool = poolIt.next()
            pool.t += deltaTime
            if (pool.t >= LAVA_POOL_TIME) { poolIt.remove(); continue }
            val pcx = player.x + player.width / 2f
            if (!player.isJumping && kotlin.math.abs(pcx - pool.x) < LAVA_POOL_HALF + player.width * 0.3f) {
                if (!player.isInvincible) {
                    player.takeDamage(1)
                    hitEffects.add(HitEffect(player.x, player.y - player.height, 0.5f, "앗 뜨거!"))
                }
            }
        }

        // 돌이 땅에 닿으면 피해 없이 부서진다
        for (proj in level.projectiles) {
            if (!proj.isStone || !proj.isAlive) continue
            val sx = proj.x + proj.width / 2f
            val ground = Terrain.groundY(sx)
            if (proj.y + proj.height >= ground) {
                proj.isAlive = false
                chipStone(sx, ground - 4f)
            }
        }

        level.projectiles.removeAll { !it.isAlive }

        // === Collision Detection ===

        // Player attack -> enemies
        val attackBox = player.getAttackHitBox()
        if (attackBox != null) {
            for (enemy in level.enemies) {
                if (!enemy.isDead && RectF.intersects(attackBox, enemy.hitBox)) {
                    damageEnemy(enemy, player.getCurrentAttackPower())
                    player.triggerHitSpark()
                }
            }
        }

        // 필살기 내려찍기 착지: 좌우 반경 안의 모든 적에게 충격파 광역 피해 (1회)
        if (player.consumeShockwave()) {
            val cx = player.x + player.width / 2f
            shake(0.35f, 22f)   // 지면을 찍는 육중한 흔들림
            SoundManager.playSpecial()
            for (enemy in level.enemies) {
                if (enemy.isDead) continue
                val ex = enemy.x + enemy.width / 2f
                if (kotlin.math.abs(ex - cx) <= Constants.STOMP_SHOCKWAVE_RADIUS) {
                    damageEnemy(enemy, player.specialPower)
                }
            }
            // 충격파는 감옥도 부순다
            for (prison in level.prisons) {
                val px = prison.x + prison.width / 2f
                if (!prison.isOpen && kotlin.math.abs(px - cx) <= Constants.STOMP_SHOCKWAVE_RADIUS) {
                    openPrison(prison)
                }
            }
        }

        // Enemy -> Player collision (플레이어 히트박스는 프레임당 1회만 계산)
        val playerBox = player.hitBox
        if (!player.isInvincible && !player.isJumping) {
            for (enemy in level.enemies) {
                if (!enemy.isDead && enemy.isTangible && RectF.intersects(playerBox, enemy.hitBox)) {
                    if (enemy is com.jamcat.nayngapo.entity.ThiefCat) {
                        // 도둑냥이는 때리지 않고 루비를 훔쳐 달아난다 (잡으면 되찾는다)
                        if (enemy.tryStealOnTouch()) {
                            // 지갑에서 빼고 모자라면 빚으로 쌓인다 (루비는 0에서 멈추고 빚 칸이 는다 — 대표님 2026-09-20)
                            val debtBefore = GameProgress.rubyDebt
                            val taken = GameProgress.takeRubies(com.jamcat.nayngapo.entity.ThiefCat.STEAL_AMOUNT)
                            enemy.addStolen(taken)
                            val newDebt = GameProgress.rubyDebt - debtBefore
                            shake(0.12f, 7f)
                            hitEffects.add(HitEffect(player.x, player.y - player.height, 0.9f,
                                if (newDebt > 0) "루비 ${taken}개 도둑맞음! (빚 +$newDebt)" else "루비 ${taken}개 도둑맞음!"))
                        }
                        continue
                    }
                    // 각성 방어력: 공격력이 방어력 이하인 적은 막는다
                    if (enemy.attackPower <= player.defense) {
                        if (blockFx <= 0f) { hitEffects.add(HitEffect(player.x, player.y - player.height, 0.5f, "막았다!")); blockFx = 0.5f }
                        break
                    }
                    if (enemy.halfHeart) player.takeHalfDamage()      // 새끼 고양이: 하트 반 칸
                    else player.takeDamage(enemy.contactDamage)   // 잡몹 1, 보스 3 (기획4)
                    shake(0.18f, 11f)   // 피격: 크게 흔들림
                    hitEffects.add(HitEffect(player.x, player.y - player.height, 0.5f, "HIT!"))
                    break
                }
            }
        }

        // 아군 탄(슬라임) -> 적
        for (proj in level.projectiles) {
            if (!proj.isPlayerOwned || !proj.isAlive) continue
            for (enemy in level.enemies) {
                if (!enemy.isDead && RectF.intersects(proj.hitBox, enemy.hitBox)) {
                    proj.isAlive = false
                    if (proj.isFirework) {
                        // 폭죽은 한 마리만 때리지 않고 주변까지 휩쓴다
                        explodeFirework(proj.x + proj.width / 2f, proj.y + proj.height / 2f)
                    } else if (proj.isMissile) {
                        // 미사일은 닿은 자리에서 터져 주변까지 휩쓴다
                        explodeMissile(proj.x + proj.width / 2f, proj.y + proj.height / 2f, proj.damage)
                    } else {
                        damageEnemy(enemy, proj.damage)
                        // 돌은 맞고 부서진다
                        if (proj.isStone) chipStone(proj.x + proj.width / 2f, proj.y + proj.height / 2f)
                    }
                    break
                }
            }
        }

        // 적 탄 -> 아군 냥이
        for (proj in level.projectiles) {
            if (proj.isPlayerOwned || !proj.isAlive) continue
            for (ally in level.allies) {
                if (ally.isDead) continue
                if (RectF.intersects(proj.hitBox, ally.hitBox)) {
                    ally.takeDamage(proj.damage)
                    proj.isAlive = false
                    break
                }
            }
        }

        // Projectile -> Player
        if (!player.isInvincible && !player.isJumping) {
            for (proj in level.projectiles) {
                if (!proj.isPlayerOwned && proj.isAlive && RectF.intersects(playerBox, proj.hitBox)) {
                    // 각성 방어력: 쏜 냥이 공격력이 방어력 이하면 막는다
                    if (proj.damage <= player.defense) {
                        proj.isAlive = false
                        if (blockFx <= 0f) { hitEffects.add(HitEffect(player.x, player.y - player.height, 0.5f, "막았다!")); blockFx = 0.5f }
                        continue
                    }
                    // 적 탄에 스치면 하트 1. 노랑냥이 번개만 2 (대표님 2026-09-24 — 총냥이 총알은 2가 아니다)
                    player.takeDamage(if (proj.isLightning) 2 else 1)
                    proj.isAlive = false
                    if (proj.isPollen) {
                        // 꽃가루: 숨이 막혀 잠깐 느려진다 (기획10 「숨막힘」)
                        player.chokeTimer = CHOKE_TIME
                        hitEffects.add(HitEffect(player.x, player.y - player.height, 0.8f, "콜록! 숨막혀!"))
                    } else hitEffects.add(HitEffect(player.x, player.y - player.height, 0.5f, "HIT!"))
                }
            }
        }

        // Player -> Prison collision (attack to open)
        if (attackBox != null) {
            for (prison in level.prisons) {
                if (!prison.isOpen && RectF.intersects(attackBox, prison.hitBox)) openPrison(prison)
            }
        }

        // 클리어 판정: 필드의 냥이를 전부 무찌르면 스테이지 클리어
        // 곧바로 넘어가지 않고 3초 동안 여운을 준 뒤 페이드로 결과 화면에 간다
        // 30: 냥이를 다 잡았는데 배냥이를 아직 안 구했으면 한 번 알려 준다
        if (stageNumber == 30 && !boatHintShown && level.enemies.all { it.isDead || it is com.jamcat.nayngapo.entity.MudCat } &&
            level.prisons.any { it.catKind == com.jamcat.nayngapo.entity.AllyCat.Kind.BOAT && !it.isOpen }) {
            boatHintShown = true
            showWaveText("앞에 갇힌 배냥이를 구하자!")
        }
        if (level.stageCleared && clearTimer < 0f) {
            clearTimer = STAGE_CLEAR_DELAY
            GameProgress.onStageCleared(stageNumber)
            // 미션 '살아남아라': 목숨을 하나도 안 잃고 20스테이지를 깼다
            if (stageNumber == 20 && player.lives >= Constants.MAX_LIVES) GameProgress.markStage20NoDeath()
            showWaveText("STAGE CLEAR!")
        }
        if (clearTimer > 0f) {
            clearTimer -= deltaTime
            if (clearTimer <= 0f) {
                gameView.showStageClear(
                    stageNumber, player.score, player.rescueCount,
                    level.defeatedEnemies, level.totalEnemies, player.level
                )
                return
            }
        }

        // Update camera (횡뷰: 세로 스크롤 없음 — levelHeight를 화면 높이로 넘겨 y=0 고정)
        camera.update(
            player.x + player.width / 2f, gameView.screenHeight / 2f,
            level.areaWidth, gameView.screenHeight.toFloat(),
            gameView.screenWidth.toFloat(), gameView.screenHeight.toFloat()
        )

        // 한 방향 진행: 화면 왼쪽 밖(지나온 구간)으로는 되돌아갈 수 없다
        if (player.x < camera.x + 20f) {
            player.x = camera.x + 20f
        }

        // 10초에 한 번 인간 앞에 테마에 맞는 벽이 솟아오른다
        updateWalls(deltaTime)

        // 설원의 깊은 눈: 걸어 들어가면 허우적거린다 (뛰어넘으면 안전)
        player.inDeepSnow = !player.isJumping && !player.ridingVine &&
            level.snowPits.any { it.swallows(player.x + player.width / 2f) }

        if (blockFx > 0f) blockFx -= deltaTime

        // 토네이도 함정 (31): 닿으면 하트 1 + 뒤로 날아간다 (점프로 넘으면 안전)
        for (tt in level.tornadoTraps) {
            tt.update(deltaTime)
            val pcx = player.x + player.width / 2f
            if (!player.isJumping && !player.isInvincible && tt.hits(pcx)) {
                player.takeDamage(1)
                player.x += if (pcx < tt.x) -260f else 260f
                shake(0.2f, 10f)
                hitEffects.add(HitEffect(player.x, player.y - player.height, 0.7f, "휘이잉!"))
            }
        }

        // 늪(기획3): 빠지면 5배 느려진다. 점프로 빠져나올 수 있다.
        for (swamp in level.swamps) swamp.update(deltaTime)
        player.inSwamp = !player.isJumping && !player.ridingVine &&
            level.swamps.any { it.swallows(player.x + player.width / 2f) }
        // 머드냥이는 인간이 제 늪에 빠졌을 때만 튀어나온다 (기획9)
        for (enemy in level.enemies) {
            if (enemy !is com.jamcat.nayngapo.entity.MudCat) continue
            enemy.preyX = player.x + player.width / 2f
            enemy.preyInSwamp = player.inSwamp && enemy.inSwampOf(enemy.preyX)
        }

        // 비밀의 방(기획5·6): 숨은 입구를 밟으면 발견 — 하트 2개
        for (room in level.secretRooms) {
            if (room.found || player.isJumping) continue
            if (!room.covers(player.x + player.width / 2f)) continue
            room.found = true
            player.hp = (player.hp + 2).coerceAtMost(player.maxHp)
            player.score += 1000
            shake(0.15f, 8f)
            hitEffects.add(HitEffect(room.x, room.y - 90f, 1.2f, "비밀의 방 발견! +♥♥"))
        }

        // 40초에 한 번 인간 근처가 폭발한다
        updateBlast(deltaTime)

        // 냥이들도 함정·구덩이 앞에서는 뛰어넘는다
        makeCatsJumpOverHazards()

        // 냥이들이 한자리에 오래 겹쳐 있지 않게 서로 비켜서게 한다
        resolveCrowding(deltaTime)

        // 사막의 모래바람: 바람이 부는 동안만 냥이들이 살짝 들썩인다 (날아가진 않는다)
        if (theme == Theme.DESERT) {
            val power = sandstormPower
            for (enemy in level.enemies) enemy.windLift = windLiftAt(enemy.x) * power
            for (ally in level.allies) ally.windLift = windLiftAt(ally.x) * power
        }

        elapsed += deltaTime
        if (shakeTimer > 0f) {
            shakeTimer -= deltaTime
            if (shakeTimer <= 0f) shakeAmp = 0f
        }

        // 공중 발판 목록을 인간에게 (스테이지마다 한 번이면 되지만 값싼 대입이라 매 프레임 둔다)
        player.platforms = level.platforms

        // Update hit effects
        hitEffects.forEach { it.timer -= deltaTime; it.y -= 30f * deltaTime }
        hitEffects.removeAll { it.timer <= 0f }

        // Wave text timer
        if (waveTextTimer > 0f) waveTextTimer -= deltaTime
    }

    // ===== 벽: 스테이지가 오를수록 자주 앞을 가로막는다 (1스테이지는 없음) =====
    private val wallInterval = (Constants.WALL_INTERVAL + 8f - stageNumber * 1.2f).coerceAtLeast(8f)
    private var wallTimer = wallInterval

    /** 스테이지 테마에 맞는 벽 재질 */
    private val wallStyle = when (theme) {
        Theme.SNOW -> Wall.Style.ICE            // 설원: 얼음 블록
        Theme.DESERT -> Wall.Style.SANDSTONE    // 사막: 사암 블록
        Theme.VOLCANO -> Wall.Style.BASALT      // 화산: 현무암 (균열에서 용암 빛)
        else -> Wall.Style.BRICK                // 초원: 붉은 벽돌
    }

    private fun updateWalls(deltaTime: Float) {
        // 갑자기 앞을 막는 벽이 생기는 기능은 뺐다 (대표님 2026-09-20 「갑자기 폭발하고 벽이 생성되는 건 없애줘」).
        // 이미 있는 벽(있다면)만 갱신·정리한다.

        for (wall in level.walls) wall.update(deltaTime)
        // 다 부서진 잔해와 한참 뒤로 지나친 벽은 정리한다
        level.walls.removeAll { (it.isBroken && it.debrisTimer <= 0f) || it.x < camera.x - 800f }

        for (wall in level.walls) {
            if (!wall.isSolid) continue

            // 공격이 닿으면 금이 간다 (세 번이면 무너진다)
            val atk = player.getAttackHitBox()
            if (atk != null && RectF.intersects(atk, wall.hitBox)) {
                wall.takeHit()
                shake(0.08f, 4f)
                if (wall.isBroken) {
                    shake(0.2f, 12f)
                    hitEffects.add(HitEffect(wall.x, wall.y - wall.height, 0.6f, "부쉈다!"))
                }
                continue
            }

            // 벽보다 낮게 있으면 막힌다 (점프로 넘어가면 통과)
            if (-player.jumpZ > wall.height) continue
            if (player.x + player.width > wall.x && player.x < wall.x + wall.width) {
                player.x = wall.x - player.width
                player.velocityX = 0f
            }
        }
    }

    /** 진행 방향 앞쪽에 함정이나 구덩이가 있는지 */
    private fun hazardAhead(centerX: Float, dir: Float): Boolean {
        val probe = centerX + dir * 95f
        if (level.pits.any { probe > it.x - 24f && probe < it.x + it.width + 24f }) return true
        if (level.chasms.any { probe > it.x - 30f && probe < it.x + it.width + 30f }) return true
        if (level.swamps.any { probe > it.x - 24f && probe < it.x + it.width + 24f }) return true
        return level.traps.any {
            it.isDangerous && probe > it.x - 24f && probe < it.x + it.width + 24f
        }
    }

    /** 모든 냥이(적·아군)는 함정 앞에서 폴짝 뛰어 넘는다 */
    private fun makeCatsJumpOverHazards() {
        for (enemy in level.enemies) {
            if (enemy.isDead || enemy.velocityX == 0f) continue
            val dir = kotlin.math.sign(enemy.velocityX)
            if (hazardAhead(enemy.x + enemy.width / 2f, dir)) enemy.hopOverHazard()
        }
        for (ally in level.allies) {
            if (ally.velocityX == 0f) continue
            val dir = kotlin.math.sign(ally.velocityX)
            if (hazardAhead(ally.x + ally.width / 2f, dir)) ally.hopOverHazard()
        }
    }

    // ===== 폭격: 표적이 스테이지에 나타나고 10~15초(랜덤) 뒤에 터진다 =====
    private var blastFuse = -1f      // 표적이 터지기까지 남은 시간
    private var blastFuseTotal = 1f
    private var blastGap = 8f        // 다음 표적이 잡히기까지
    private var blastFx = 0f         // 0보다 크면 폭발 이펙트 재생 중
    private var blastX = 0f
    private var blastY = 0f

    private fun updateBlast(deltaTime: Float) {
        if (blastFx > 0f) blastFx -= deltaTime
        return   // 폭격(표적이 찍히고 터지는 것)은 뺐다 (대표님 2026-09-20 「갑자기 폭발하고 벽이 생성되는 건 없애줘」)
        @Suppress("UNREACHABLE_CODE")
        if (stageNumber < 3) return          // 폭격은 3스테이지부터

        // 표적이 떠 있는 동안 심지가 타들어간다
        if (blastFuse > 0f) {
            blastFuse -= deltaTime
            if (blastFuse <= 0f) {
                detonate()
                blastGap = (14f - stageNumber).coerceAtLeast(Constants.BLAST_RESPAWN_GAP)
            }
            return
        }

        blastGap -= deltaTime
        if (blastGap > 0f) return

        // 새 표적: 인간이 보이는 자리 근처에 찍히고 10~15초 뒤에 터진다
        blastFuseTotal = Constants.BLAST_FUSE_MIN +
            (Math.random() * (Constants.BLAST_FUSE_MAX - Constants.BLAST_FUSE_MIN)).toFloat()
        blastFuse = blastFuseTotal
        blastX = player.x + player.width / 2f + ((Math.random() - 0.5) * 420.0).toFloat()
        blastY = Terrain.groundY(blastX)
    }

    /** 표적 지점이 터진다 — 반경 안의 인간과 냥이 모두에게 피해 */
    private fun detonate() {
        blastFx = 0.6f
        shake(0.45f, 26f)
        SoundManager.playSpecial()
        hitEffects.add(HitEffect(blastX, blastY - 120f, 0.9f, "BOOM!"))

        val r = Constants.BLAST_RADIUS
        // 인간: 공중에 떠 있으면 폭풍을 피한다
        if (!player.isJumping && !player.isInvincible &&
            kotlin.math.abs(player.x + player.width / 2f - blastX) <= r
        ) {
            player.takeDamage(1)
        }
        // 냥이들도 휘말린다 (아군도 예외가 아니다)
        for (enemy in level.enemies) {
            if (enemy.isDead) continue
            if (kotlin.math.abs(enemy.x + enemy.width / 2f - blastX) <= r) damageEnemy(enemy, 30)
        }
        for (ally in level.allies) {
            if (kotlin.math.abs(ally.x + ally.width / 2f - blastX) <= r) ally.takeDamage(12)
        }
    }

    private val blastWarnPaint = Paint().apply {
        color = Color.argb(150, 235, 60, 50); style = Paint.Style.STROKE
        strokeWidth = 6f; isAntiAlias = true
    }
    private val blastCorePaint = Paint().apply { isAntiAlias = true }
    private val blastRingPaint = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 10f; isAntiAlias = true
    }

    /** 표적 표시(남은 시간이 줄수록 링이 좁혀지고 빨리 깜빡인다)와 폭발 화염 */
    private fun drawBlast(canvas: Canvas) {
        if (blastFuse > 0f) {
            val t = 1f - (blastFuse / blastFuseTotal).coerceIn(0f, 1f)   // 0 → 1
            val blink = (kotlin.math.sin(elapsed * (5.0 + 26.0 * t)) * 0.5 + 0.5).toFloat()
            blastWarnPaint.alpha = (70 + 175 * blink).toInt()

            val r = Constants.BLAST_RADIUS
            canvas.drawCircle(blastX, blastY - 10f, r, blastWarnPaint)
            canvas.drawCircle(blastX, blastY - 10f, r * (1f - 0.55f * t), blastWarnPaint)
            canvas.drawLine(blastX - r, blastY - 10f, blastX + r, blastY - 10f, blastWarnPaint)
            canvas.drawLine(blastX, blastY - 10f - r * 0.5f, blastX, blastY - 10f + r * 0.5f, blastWarnPaint)
        }

        if (blastFx > 0f) {
            val t = 1f - (blastFx / 0.6f).coerceIn(0f, 1f)        // 0 → 1
            val r = Constants.BLAST_RADIUS * (0.3f + 0.9f * t)
            blastCorePaint.color = Color.argb(
                ((1f - t) * 235).toInt().coerceIn(0, 255), 255, (200 - 120 * t).toInt(), 60
            )
            canvas.drawCircle(blastX, blastY - 40f, r * 0.8f, blastCorePaint)
            blastRingPaint.color = Color.argb(
                ((1f - t) * 200).toInt().coerceIn(0, 255), 255, 240, 180
            )
            canvas.drawCircle(blastX, blastY - 40f, r, blastRingPaint)
        }
    }

    /**
     * 냥이들이 4초를 넘겨 겹쳐 있으면 서서히 옆으로 비켜서게 한다.
     * (짧게 스치는 겹침은 자연스러우니 그대로 두고, 오래 뭉친 경우만 푼다)
     */
    private fun resolveCrowding(deltaTime: Float) {
        val cats = ArrayList<com.jamcat.nayngapo.entity.Crowdable>(
            level.enemies.size + level.allies.size
        )
        for (enemy in level.enemies) if (!enemy.isDead) cats.add(enemy)
        for (ally in level.allies) if (!ally.isDead) cats.add(ally)

        for (i in cats.indices) {
            val a = cats[i]
            var overlapping = false
            for (j in cats.indices) {
                if (i == j) continue
                val b = cats[j]
                val minGap = (a.width + b.width) * 0.42f
                val dx = (a.x + a.width / 2f) - (b.x + b.width / 2f)
                if (kotlin.math.abs(dx) >= minGap) continue

                overlapping = true
                if (a.crowdTimer > CROWD_LIMIT) {
                    // 겹친 상대의 반대쪽으로 천천히 밀려난다 (겹칠 땐 인덱스로 방향을 갈라준다)
                    val dir = if (dx == 0f) (if (i < j) -1f else 1f) else kotlin.math.sign(dx)
                    a.x += dir * 150f * deltaTime
                }
            }
            a.crowdTimer = if (overlapping) a.crowdTimer + deltaTime else 0f
        }
    }

    /** 적에게 피해를 주고 타격 연출·처치 보상·레벨업까지 한 곳에서 처리한다 */
    private fun damageEnemy(enemy: com.jamcat.nayngapo.entity.Enemy, dmg: Int) {
        if (enemy.isDead) return                 // 이미 쓰러진 냥이: 점수·루비·킬 수가 두 번 들어가지 않게
        val hpBefore = enemy.hp
        enemy.takeDamage(dmg)
        if (enemy.hp == hpBefore && !enemy.isDead) return   // 피격 무적이라 이번엔 안 맞았다

        shake(0.1f, 5f)                          // 타격감: 살짝 흔들림
        hitEffects.add(HitEffect(enemy.x + enemy.width / 2f, enemy.y - enemy.height, 0.5f, "$dmg"))
        if (enemy.isDead) SoundManager.playCatDie() else SoundManager.playCatHit()

        if (enemy.isDead) {
            shake(0.15f, 9f)                     // 처치 시 더 크게
            player.score += 100
            player.killCount++
            GameProgress.addKill(isThief = enemy is com.jamcat.nayngapo.entity.ThiefCat)   // 미션 통계
            level.onEnemyDefeated()

            // 쓰러진 냥이가 루비를 떨어뜨린다 (일반 1개, 보스 5개). 도둑냥이는 훔친 루비도 돌려준다
            val stolenBack = (enemy as? com.jamcat.nayngapo.entity.ThiefCat)?.stolen ?: 0
            val reward = com.jamcat.nayngapo.engine.Rewards.rubyRewardOf(enemy) + stolenBack
            GameProgress.addRubies(reward)
            hitEffects.add(
                HitEffect(
                    enemy.x + enemy.width / 2f, enemy.y - enemy.height - 40f, 0.8f, "+$reward 루비"
                )
            )

            // Level up every 10 kills
            if (player.killCount % 10 == 0) {
                player.level++
                player.maxHp++
                player.hp = player.maxHp
                player.attackPower += 2      // 뒤로 갈수록 단단해지는 냥이에 맞춰 공격력도 성장
                hitEffects.add(HitEffect(player.x, player.y - player.height, 1f, "LEVEL UP!"))
            }
        }
    }

    /** 지금 잡을 수 있는 덩굴 (사람 가운데가 덩굴 끝의 좌우 40 안에 있을 때) */
    private fun vineUnderPlayer(): com.jamcat.nayngapo.entity.Vine? {
        if (level.vines.isEmpty()) return null
        val cx = player.x + player.width / 2f
        return level.vines.firstOrNull { kotlin.math.abs(it.bottomX - cx) <= 40f }
    }

    /**
     * 고양이 기술을 쓴다 — 기술은 인간이 아니라 출전한 냥이가 쓴다.
     * 인간과 가장 가까운 살아 있는 냥이가 시전하며, 그런 냥이가 없으면 쿨타임도 돌지 않는다.
     */
    private fun useSkill(id: String) {
        val index = battleSkills.indexOfFirst { it.id == id }
        if (index < 0 || skillCooldowns[index] > 0f) return
        val skill = battleSkills[index]

        val playerCx = player.x + player.width / 2f
        // 로비에서 배운(모두가 쓰는) 기술이 아니면, 그 기술을 배운 냥이만 쓴다 (천막에서 1마리만 배운다)
        val global = com.jamcat.nayngapo.engine.CatSkills.isActive(skill.id)
        val caster = level.allies
            .filter { !it.isDead }
            .filter { a -> global || a.teamId?.let { id -> com.jamcat.nayngapo.engine.CatTeam.cat(id)?.skills?.contains(skill.id) } == true }
            .minByOrNull { kotlin.math.abs(it.x + it.width / 2f - playerCx) }
        if (caster == null) {
            hitEffects.add(
                HitEffect(player.x, player.y - player.height - 40f, 1.3f, "출전한 냥이가 없어서 기술을 못 쓴다냥!")
            )
            return
        }

        skillCooldowns[index] = skill.cooldown
        caster.startCast()

        // 화면 안의 가장 가까운 적 쪽으로 돌아서서, 기술이 끝날 때까지 그 방향을 지킨다
        val casterCx = caster.x + caster.width / 2f
        val viewLeft = camera.x
        val viewRight = camera.x + gameView.screenWidth
        val nearest = level.enemies
            .filter { !it.isDead && it.x + it.width / 2f in viewLeft..viewRight }
            .minByOrNull { kotlin.math.abs(it.x + it.width / 2f - casterCx) }
        val aimRight = nearest?.let { it.x + it.width / 2f >= casterCx } ?: caster.isFacingRight
        val aimDir = if (aimRight) 1f else -1f
        // 연속발사는 여섯 발(0.5초)이 다 나갈 때까지, 나머지는 시전 동작 동안
        caster.lockFacing(aimRight, if (skill.id == "rapid_fire") 0.7f else 0.3f)
        // 기술 이름은 시전한 냥이 머리 위에
        hitEffects.add(HitEffect(caster.x, caster.y - caster.height - 40f, 1.1f, skill.name + "!"))

        when (skill.id) {
            "evolve" -> {
                // 묭묭 진화: 전장의 출전 냥이 모두가 55분 동안 엄청 커진다 (공격력 50·방어력 50)
                com.jamcat.nayngapo.engine.CatTeam.startEvolve(skill.cooldown)   // 다음 스테이지·상점 마을에서도 이어진다
                for (ally in level.allies) if (!ally.isDead) {
                    ally.evolve(skill.cooldown)
                    hitEffects.add(HitEffect(ally.x, ally.y - ally.height - 70f, 1.3f, "묭묭!"))
                }
                SoundManager.playSpecial()
                shake(0.3f, 12f)
            }

            "rapid_fire" -> {
                // 발바닥 탄 여섯 발을 0.1초 간격으로 — 매 발 그 냥이의 지금 자리에서 나간다
                for (i in 0 until 6) pendingShots.add(PendingShot(i * 0.1f, caster, aimDir, skill.power, i))
            }

            "jelly_paw" -> {
                // 냥이 머리 위에 분홍 구름이 먼저 뜨고, 그다음 젤리가 쏟아진다
                jellyCaster = caster
                jellyCloudT = JELLY_CLOUD_TIME
                jellyCloudFade = 0f
                jellyRainT = 2.5f
                jellySpawnT = 0f
                jellySpawned = 0
                jellyPower = skill.power
            }

            "tornado" -> {
                // 냥이 발밑에서 솟아 냥이가 보는 쪽 화면 끝까지 쓸고 간다
                val cx = caster.x + caster.width / 2f
                val far =
                    if (aimRight) camera.x + gameView.screenWidth + 100f
                    else camera.x - 100f
                tornadoes.add(
                    com.jamcat.nayngapo.entity.Tornado(cx, far, Terrain.groundY(cx), skill.power)
                )
                shake(0.25f, 6f)
                SoundManager.playSpecial()
            }
        }
    }

    /** 인간 기술을 쓴다 — 쿨타임이 남아 있으면 아무 일도 없다 */
    private fun useHumanSkill(id: String) {
        val index = humanBattleSkills.indexOfFirst { it.id == id }
        if (index < 0 || humanSkillCooldowns[index] > 0f) return
        val skill = humanBattleSkills[index]
        humanSkillCooldowns[index] = skill.cooldown

        when (skill.id) {
            "awaken" -> {
                // 각성: 10초 동안 공격력 +15 · 방어력 +15 (몸에 금빛 오라)
                player.awakenTimer = com.jamcat.nayngapo.entity.Player.AWAKEN_TIME
                SoundManager.playSpecial()
                shake(0.2f, 8f)
                hitEffects.add(HitEffect(player.x, player.y - player.height - 40f, 1.2f, "각성!"))
            }

            "missile" -> {
                // 어깨 높이에서 앞으로 쏘아 올린다 — 날아가며 화면 안의 가장 가까운 적을 쫓는다
                val dir = if (player.facingRight) 1f else -1f
                val r = 10f
                level.projectiles.add(
                    com.jamcat.nayngapo.entity.Projectile(
                        player.x + player.width / 2f + dir * 34f - r,
                        player.y - player.height * 0.72f - r,
                        dir * 380f, -120f, skill.power, true,
                        tint = Color.WHITE, radius = r,
                        fuse = MISSILE_LIFETIME, isMissile = true
                    )
                )
                SoundManager.playAttack()
                hitEffects.add(HitEffect(player.x, player.y - player.height - 40f, 1.0f, skill.name + "!"))
            }

            "fire_punch" -> {
                // 불타는 주먹 — 쿨타임이 없다. 펀치가 끝나기 전에 다시 누르거나 내려찍기·덩굴 중이면 무시한다
                if (!player.startFirePunch()) {
                    humanSkillCooldowns[index] = 0f
                    return
                }
                firePunchPending = true
                firePunchPower = skill.power
                emberSpawnT = 0f
                SoundManager.playAttack()
                hitEffects.add(HitEffect(player.x, player.y - player.height - 40f, 1.0f, skill.name + "!"))
            }

            "stone_wall" -> {
                // 인간 앞 땅에서 돌가시가 바라보는 쪽으로 차례로 솟는다 — 낭떠러지 위에는 솟지 않는다
                val dir = if (player.facingRight) 1f else -1f
                val cx = player.x + player.width / 2f
                val xs = ArrayList<Float>()
                for (i in 0 until com.jamcat.nayngapo.entity.StoneWall.SPIKES) {
                    val sx = cx + dir * (com.jamcat.nayngapo.entity.StoneWall.FIRST_OFFSET +
                        i * com.jamcat.nayngapo.entity.StoneWall.SPACING)
                    if (level.chasms.any { sx > it.x - 20f && sx < it.x + it.width + 20f }) continue
                    xs.add(sx)
                }
                stoneWalls.add(
                    com.jamcat.nayngapo.entity.StoneWall(
                        xs.toFloatArray(), FloatArray(xs.size) { Terrain.groundY(xs[it]) }, skill.power
                    )
                )
                shake(0.3f, 8f)
                SoundManager.playSpecial()
                hitEffects.add(HitEffect(player.x, player.y - player.height - 40f, 1.0f, skill.name + "!"))
            }

            "stone_throw" -> {
                // 손 높이에서 바라보는 쪽으로 돌을 포물선으로 던진다
                val dir = if (player.facingRight) 1f else -1f
                val r = 11f
                level.projectiles.add(
                    com.jamcat.nayngapo.entity.Projectile(
                        player.x + player.width / 2f + dir * 30f - r,
                        player.y - player.height * 0.62f - r,
                        dir * 640f, -260f, skill.power, true,
                        tint = Color.rgb(150, 150, 158), radius = r,
                        gravity = 900f, fuse = 1.2f, isStone = true
                    )
                )
                SoundManager.playAttack()
                hitEffects.add(HitEffect(player.x, player.y - player.height - 40f, 1.0f, skill.name + "!"))
            }
        }
    }

    /** 돌가시가 땅을 뚫고 나올 때 흙·돌 부스러기가 튄다 */
    private fun spikeDust(x: Float, ground: Float) {
        repeat(5) {
            val a = (-Math.PI * (0.15 + Math.random() * 0.7)).toFloat()
            val sp = 140f + Math.random().toFloat() * 160f
            sparks.add(
                Spark(
                    x + (Math.random().toFloat() - 0.5f) * 30f, ground - 4f,
                    kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp, 0.4f,
                    if (it % 2 == 0) Color.rgb(140, 108, 74) else Color.rgb(150, 150, 158),
                    size = 4f, streak = false, twinkle = false
                )
            )
        }
    }

    // ===== 미사일 =====
    /** 폭발 뒤 피어오르는 회색 연기 */
    private class SmokePuff(
        var x: Float, var y: Float, val vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val size: Float
    )

    private val smokePuffs = mutableListOf<SmokePuff>()
    private val smokePuffPaint = Paint().apply { color = Color.rgb(150, 150, 156); isAntiAlias = true }

    /** 날고 있는 미사일마다 화면 안의 가장 가까운 적을 찾아 방향을 튼다 */
    private fun steerMissiles(deltaTime: Float) {
        if (level.projectiles.none { it.isMissile && it.isAlive }) return
        val viewL = camera.x
        val viewR = camera.x + gameView.screenWidth
        for (proj in level.projectiles) {
            if (!proj.isMissile || !proj.isAlive) continue
            val mx = proj.x + proj.width / 2f
            val my = proj.y + proj.height / 2f
            var bestX = 0f
            var bestY = 0f
            var bestD = Float.MAX_VALUE
            for (enemy in level.enemies) {
                if (enemy.isDead) continue
                val ex = enemy.x + enemy.width / 2f
                if (ex < viewL || ex > viewR) continue
                val hb = enemy.hitBox
                val d = kotlin.math.hypot(hb.centerX() - mx, hb.centerY() - my)
                if (d < bestD) { bestD = d; bestX = hb.centerX(); bestY = hb.centerY() }
            }
            if (bestD < Float.MAX_VALUE) proj.steerToward(bestX, bestY, deltaTime)
        }
    }

    /** 미사일이 터진다 — 반경 120 안의 적을 한 번씩 때리고, 번쩍임·불티·연기·'쾅!' */
    private fun explodeMissile(cx: Float, cy: Float, power: Int) {
        for (enemy in level.enemies) {
            if (enemy.isDead) continue
            val hb = enemy.hitBox
            // 적 몸통에서 폭발 중심과 가장 가까운 점까지 — 큰 적도 가장자리가 휘말린다
            val nx = cx.coerceIn(hb.left, hb.right)
            val ny = cy.coerceIn(hb.top, hb.bottom)
            if (kotlin.math.hypot(nx - cx, ny - cy) <= MISSILE_BLAST_RADIUS) damageEnemy(enemy, power)
        }
        flashes.add(Flash(cx, cy, FLASH_LIFE))
        repeat(10) { i ->
            val a = (i / 10f) * 2f * Math.PI.toFloat() + (Math.random().toFloat() - 0.5f) * 0.4f
            val sp = 300f + Math.random().toFloat() * 200f
            sparks.add(
                Spark(
                    cx, cy, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp, 0.5f,
                    if (i % 2 == 0) Color.rgb(255, 140, 40) else Color.rgb(255, 214, 74),
                    size = 4.5f, streak = true
                )
            )
        }
        repeat(5) {
            val a = (Math.random() * Math.PI * 2).toFloat()
            val sp = 90f + Math.random().toFloat() * 120f
            sparks.add(
                Spark(
                    cx, cy, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp - 80f, 0.55f,
                    Color.rgb(150, 150, 156), size = 3.5f, streak = false, twinkle = false
                )
            )
        }
        repeat(5) {
            smokePuffs.add(
                SmokePuff(
                    cx + (Math.random().toFloat() - 0.5f) * 30f,
                    cy + (Math.random().toFloat() - 0.5f) * 20f,
                    (Math.random().toFloat() - 0.5f) * 60f,
                    -(30f + Math.random().toFloat() * 50f),
                    0.9f, 0.9f, 14f + Math.random().toFloat() * 10f
                )
            )
        }
        hitEffects.add(HitEffect(cx, cy - 40f, 0.8f, "쾅!"))
        shake(0.2f, 10f)
        SoundManager.playSpecial()
    }

    private fun updateSmokePuffs(deltaTime: Float) {
        val it = smokePuffs.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * deltaTime
            p.y += p.vy * deltaTime
            p.vy *= (1f - 1.5f * deltaTime).coerceAtLeast(0f)
            p.life -= deltaTime
            if (p.life <= 0f) it.remove()
        }
    }

    /** 파이어 펀치 진행: 주먹에서 불똥이 튀고, 타격 프레임에서 한 번 때린다 */
    private fun updateFirePunch(deltaTime: Float) {
        if (!player.isFirePunching) {
            firePunchPending = false
            return
        }
        if (player.frontFist(fist)) {
            emberSpawnT -= deltaTime
            while (emberSpawnT <= 0f) {
                emberSpawnT += 0.03f
                spawnEmber(fist.x, fist.y, 7f)
            }
        }
        if (firePunchPending && player.attackProgress() >= FIRE_PUNCH_STRIKE) {
            firePunchPending = false
            strikeFirePunch()
        }
    }

    /** 주먹이 뻗는 순간: 앞쪽 주먹 사거리 안(주먹 높이 ±80)의 적을 모두 태운다 */
    private fun strikeFirePunch() {
        val right = player.facingRight
        val reach = Constants.ATTACK_RANGE
        val left = if (right) player.x + player.width else player.x - reach
        val rightEdge = if (right) player.x + player.width + reach else player.x
        val punchY = if (player.frontFist(fist)) fist.y else player.y - player.height * 0.6f

        for (enemy in level.enemies) {
            if (enemy.isDead) continue
            val hb = enemy.hitBox
            if (hb.right < left || hb.left > rightEdge) continue
            if (hb.bottom < punchY - 80f || hb.top > punchY + 80f) continue
            damageEnemy(enemy, firePunchPower)
            // 불이 옮겨붙는다 — 이미 타고 있으면 10초를 새로 센다
            if (!enemy.isDead) burning[enemy] = BURN_DELAY
            fireBurst(if (right) hb.left else hb.right, punchY.coerceIn(hb.top, hb.bottom))
        }
    }

    /** 불꽃이 확 터진다: 주황·노랑 불티 여덟 개 + '화르륵!' */
    private fun fireBurst(x: Float, y: Float) {
        repeat(8) { i ->
            val a = (i / 8f) * 2f * Math.PI.toFloat() + (Math.random().toFloat() - 0.5f) * 0.5f
            val sp = 220f + Math.random().toFloat() * 160f
            sparks.add(
                Spark(
                    x, y, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp - 60f, 0.4f,
                    if (i % 2 == 0) Color.rgb(255, 120, 40) else Color.rgb(255, 206, 60),
                    size = 4f, streak = true
                )
            )
        }
        hitEffects.add(HitEffect(x, y - 40f, 0.8f, "화르륵!"))
    }

    /** 화상: 10초가 지나면 대미지 1 (그 사이 쓰러진 적은 표시만 지운다) */
    private fun updateBurning(deltaTime: Float) {
        if (burning.isEmpty()) return
        val it = burning.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val enemy = entry.key
            if (enemy.isDead) { it.remove(); continue }
            val left = entry.value - deltaTime
            if (left > 0f) {
                entry.setValue(left)
                // 타는 동안 가끔 불똥이 오른다
                if (Math.random() < deltaTime * 5f) {
                    val hb = enemy.hitBox
                    spawnEmber(hb.centerX() + (Math.random().toFloat() - 0.5f) * 16f, hb.top - 6f, 4f)
                }
                continue
            }
            val hpBefore = enemy.hp
            damageEnemy(enemy, 1)
            if (enemy.hp == hpBefore && !enemy.isDead) {
                // 마침 피격 무적이라 안 들어갔다 — 화상은 사라지지 않고 조금 뒤 다시 시도한다
                entry.setValue(0.05f)
                continue
            }
            it.remove()
            hitEffects.add(HitEffect(enemy.x, enemy.y - enemy.height - 30f, 0.9f, "화상 1"))
        }
    }

    private fun spawnEmber(x: Float, y: Float, size: Float) {
        if (embers.size >= 140) return
        embers.add(
            Ember(
                x + (Math.random().toFloat() - 0.5f) * 10f,
                y + (Math.random().toFloat() - 0.5f) * 6f,
                (Math.random().toFloat() - 0.5f) * 40f,
                -(60f + Math.random().toFloat() * 60f),
                0.5f, 0.5f, size * (0.4f + Math.random().toFloat() * 0.3f)
            )
        )
    }

    private fun updateEmbers(deltaTime: Float) {
        val it = embers.iterator()
        while (it.hasNext()) {
            val em = it.next()
            em.x += em.vx * deltaTime
            em.vy += 40f * deltaTime
            em.y += em.vy * deltaTime
            em.life -= deltaTime
            if (em.life <= 0f) it.remove()
        }
    }

    /** 일렁이는 불꽃: 가운데 불덩이 + 위로 퍼진 불꽃 혀 일곱 개 (바깥 주황, 안쪽 노랑) */
    private fun drawFlame(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        canvas.drawCircle(cx, cy, 10f * scale, flameOuterPaint)
        for (k in 0 until 7) {
            val a = (-Math.PI / 2 + (k - 3) * 0.55).toFloat()
            val flick = kotlin.math.sin(elapsed * 30f + k * 1.9f)
            val len = (18f + 7f * flick) * scale
            drawFlameTongue(canvas, cx, cy, a, len, 6f * scale, flameOuterPaint)
            drawFlameTongue(canvas, cx, cy, a, len * 0.55f, 3.2f * scale, flameInnerPaint)
        }
        canvas.drawCircle(cx, cy, 5.5f * scale, flameInnerPaint)
    }

    private fun drawFlameTongue(
        canvas: Canvas, cx: Float, cy: Float, a: Float, len: Float, halfW: Float, paint: Paint
    ) {
        val ux = kotlin.math.cos(a)
        val uy = kotlin.math.sin(a)
        val px = -uy
        val py = ux
        flamePath.reset()
        flamePath.moveTo(cx + px * halfW, cy + py * halfW)
        flamePath.quadTo(
            cx + ux * len * 0.55f + px * halfW, cy + uy * len * 0.55f + py * halfW,
            cx + ux * len, cy + uy * len
        )
        flamePath.quadTo(
            cx + ux * len * 0.55f - px * halfW, cy + uy * len * 0.55f - py * halfW,
            cx - px * halfW, cy - py * halfW
        )
        flamePath.close()
        canvas.drawPath(flamePath, paint)
    }

    /** 돌이 부서져 회색 조각 네 개가 튀어 떨어진다 (반짝이지 않는다) */
    private fun chipStone(cx: Float, cy: Float) {
        repeat(4) { i ->
            val a = (-Math.PI * (0.15 + 0.7 * i / 3.0)).toFloat()   // 위쪽 부채꼴로 튄다
            val sp = 140f + Math.random().toFloat() * 120f
            sparks.add(
                Spark(
                    cx, cy, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp, 0.45f,
                    Color.rgb(140, 140, 148), size = 4f, streak = false, twinkle = false
                )
            )
        }
    }

    /** 젤리 비: 떨어뜨리고, 적이나 바닥에 닿으면 퍼뜨린다 */
    private fun updateJellyRain(deltaTime: Float) {
        if (jellyCloudT > 0f) {
            // 구름만 떠 있는 동안에는 아직 떨어지지 않는다
            jellyCloudT -= deltaTime
            if (jellyCloudT <= 0f) jellyCloudFade = 0.35f
        } else if (jellyRainT > 0f) {
            jellyRainT -= deltaTime
            jellySpawnT -= deltaTime
            while (jellySpawnT <= 0f && jellyRainT > 0f) {
                jellySpawnT += 0.12f
                val c = jellyCaster
                if (jellySpawned < 3 && c != null) {
                    // 처음 세 개는 구름 바로 아래, 부른 냥이 가까이 떨어진다
                    val ccx = c.x + c.width / 2f
                    jellies.add(
                        JellyDrop(
                            ccx + (Math.random().toFloat() - 0.5f) * 160f,
                            jellyCloudY(c), Math.random().toFloat() * 6f
                        )
                    )
                } else {
                    val span = (gameView.screenWidth - 120f).coerceAtLeast(1f)
                    val jx = camera.x + 60f + Math.random().toFloat() * span
                    jellies.add(JellyDrop(jx, camera.y - 60f, Math.random().toFloat() * 6f))
                }
                jellySpawned++
            }
        }
        if (jellyCloudFade > 0f) jellyCloudFade -= deltaTime

        val it = jellies.iterator()
        while (it.hasNext()) {
            val j = it.next()
            if (j.splatT >= 0f) {
                j.splatT += deltaTime
                if (j.splatT >= 0.15f) it.remove()
                continue
            }
            j.y += 700f * deltaTime
            val jx = j.x + kotlin.math.sin(j.y * 0.02f + j.phase) * 10f

            // 적에게 닿으면 한 번 때리고 퍼진다 (한 적이 여러 젤리를 맞을 수 있다)
            jellyRect.set(jx - JELLY_R, j.y - JELLY_R, jx + JELLY_R, j.y + JELLY_R)
            val target = level.enemies.firstOrNull {
                !it.isDead && RectF.intersects(jellyRect, it.hitBox)
            }
            if (target != null) {
                damageEnemy(target, jellyPower)
                splatJelly(j, jx)
                continue
            }
            // 바닥에 닿으면 피해 없이 퍼진다
            if (j.y >= Terrain.groundY(jx)) {
                j.y = Terrain.groundY(jx)
                splatJelly(j, jx)
            }
        }

        val d = jellyDroplets.iterator()
        while (d.hasNext()) {
            val p = d.next()
            p.x += p.vx * deltaTime
            p.vy += 900f * deltaTime
            p.y += p.vy * deltaTime
            p.life -= deltaTime
            if (p.life <= 0f) d.remove()
        }
    }

    /** 젤리 구름이 뜨는 높이 (부른 냥이 머리 위) */
    private fun jellyCloudY(c: com.jamcat.nayngapo.entity.AllyCat) = c.y - c.height - 110f

    /** 분홍 구름 한 덩이 */
    private fun drawJellyCloud(canvas: Canvas, cx: Float, cy: Float, grow: Float, alpha: Int) {
        jellyPadPaint.alpha = alpha
        jellyInkPaint.alpha = alpha
        val r = 30f * grow
        for (k in -1..1) {
            val px = cx + k * r * 0.95f
            val py = cy + (if (k == 0) -r * 0.35f else 0f)
            val rr = r * (if (k == 0) 1.15f else 0.9f)
            canvas.drawCircle(px, py, rr, jellyPadPaint)
            canvas.drawCircle(px, py, rr, jellyInkPaint)
        }
        jellyPadPaint.alpha = 255
        jellyInkPaint.alpha = 255
    }

    private fun splatJelly(j: JellyDrop, atX: Float) {
        j.x = atX
        j.splatT = 0f
        repeat(5) { i ->
            val a = (-Math.PI + (i / 4.0) * Math.PI).toFloat()   // 위쪽 반원으로 튄다
            val sp = 180f + Math.random().toFloat() * 120f
            jellyDroplets.add(
                JellyDroplet(atX, j.y, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp, 0.35f)
            )
        }
        if (jellySplatCount++ % 3 == 0) SoundManager.playCatHit()
    }

    /** 분홍 젤리 발바닥 (sx·sy로 찌그러뜨린다) */
    private fun drawJellyPaw(canvas: Canvas, cx: Float, cy: Float, sx: Float, sy: Float, alpha: Int) {
        val r = JELLY_R
        jellyPadPaint.alpha = alpha
        jellyToePaint.alpha = alpha
        jellyShinePaint.alpha = (alpha * 0.7f).toInt()
        jellyInkPaint.alpha = alpha

        // 발볼
        jellyRect.set(cx - r * 0.95f * sx, cy - r * 0.55f * sy, cx + r * 0.95f * sx, cy + r * 0.85f * sy)
        canvas.drawOval(jellyRect, jellyPadPaint)
        canvas.drawOval(jellyRect, jellyInkPaint)
        // 발가락 넷
        for (k in 0 until 4) {
            val tx = cx + (k - 1.5f) * r * 0.52f * sx
            val ty = cy - r * (if (k == 0 || k == 3) 0.78f else 1.02f) * sy
            canvas.drawCircle(tx, ty, r * 0.26f * kotlin.math.min(sx, 1.2f), jellyToePaint)
            canvas.drawCircle(tx, ty, r * 0.26f * kotlin.math.min(sx, 1.2f), jellyInkPaint)
        }
        // 말랑한 윤기
        canvas.drawCircle(cx - r * 0.35f * sx, cy - r * 0.05f * sy, r * 0.20f, jellyShinePaint)

        jellyPadPaint.alpha = 255; jellyToePaint.alpha = 255
        jellyShinePaint.alpha = 255; jellyInkPaint.alpha = 255
    }

    /** 폭죽이 터진다 — 반경 안의 적을 한꺼번에 때리고 색 불꽃을 흩뿌린다 */
    private fun explodeFirework(cx: Float, cy: Float) {
        val radius = 140f
        val damage = player.attackPower   // 매 공격마다 던지므로 한 발 위력은 공격력만큼
        for (enemy in level.enemies) {
            if (enemy.isDead) continue
            val ex = enemy.x + enemy.width / 2f
            val ey = enemy.y - enemy.height / 2f
            if (kotlin.math.hypot(ex - cx, ey - cy) <= radius) damageEnemy(enemy, damage)
        }
        flashes.add(Flash(cx, cy, FLASH_LIFE))
        // 바깥 고리: 사방으로 뻗는 꼬리 달린 불꽃 (한 발의 색을 정해 둘씩 섞는다)
        val base = (Math.random() * FIREWORK_COLORS.size).toInt()
        repeat(18) { i ->
            val a = (i / 18f) * 2f * Math.PI.toFloat() + (Math.random().toFloat() - 0.5f) * 0.18f
            val sp = 430f + Math.random().toFloat() * 110f
            sparks.add(
                Spark(
                    cx, cy, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp, 0.85f,
                    FIREWORK_COLORS[(base + (i % 2)) % FIREWORK_COLORS.size], size = 5f, streak = true
                )
            )
        }
        // 안쪽: 느리게 퍼지며 반짝이는 금빛 가루
        repeat(10) {
            val a = Math.random().toFloat() * 2f * Math.PI.toFloat()
            val sp = 120f + Math.random().toFloat() * 150f
            sparks.add(
                Spark(
                    cx, cy, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp, 0.65f,
                    android.graphics.Color.rgb(255, 236, 140), size = 4f, streak = false
                )
            )
        }
        hitEffects.add(HitEffect(cx, cy - 30f, 0.7f, "펑!"))
        SoundManager.playCatHit()
        shake(0.2f, 12f)
    }

    /** 잠든 냥이의 비석 (바닥선에 발을 딛게 세운다) */
    private fun drawGrave(canvas: Canvas, cx: Float, groundY: Float) {
        val bmp = SpriteManager.grave
        val targetH = 70f
        val scale = targetH / bmp.height
        val bw = bmp.width * scale
        graveSrc.set(0, 0, bmp.width, bmp.height)
        graveDst.set(cx - bw / 2f, groundY - targetH, cx + bw / 2f, groundY)
        canvas.drawBitmap(bmp, graveSrc, graveDst, null)
    }

    private fun openPrison(prison: com.jamcat.nayngapo.entity.Prison) {
        prison.open()
        player.rescueCount += prison.rescueCount
        player.score += 500
        if (prison.kind == com.jamcat.nayngapo.entity.Prison.Kind.CAT) {
            // 기획1: "고양이를 구해야지 아군이 된다" — 구출한 냥이가 아군으로 합류
            // 날아냥이는 10스테이지의 지정 감옥에서만 나온다 — 나머지는 세 종류에서 무작위
            val randomKinds = listOf(
                com.jamcat.nayngapo.entity.AllyCat.Kind.GREEN,
                com.jamcat.nayngapo.entity.AllyCat.Kind.BLUE,
                com.jamcat.nayngapo.entity.AllyCat.Kind.NORMAL,
                com.jamcat.nayngapo.entity.AllyCat.Kind.GUN,      // 총냥이도 팀이 된다 (2026-09-20)
            )
            // 새끼 고양이가 갇혀 있었으면 그 무늬의 새끼 치즈냥이가 팀에 들어온다 (대표님 2026-09-27)
            // 날아냥이는 「구출에 너무 안 나와, 많이(엄청 많이는 아니고)」(2026-10-09) — 무작위 냥이 감옥의 15% (Claude 판단)
            val kind = if (prison.holdsKitten) com.jamcat.nayngapo.entity.AllyCat.Kind.NORMAL
                else prison.catKind ?: if (Math.random() < 0.15) com.jamcat.nayngapo.entity.AllyCat.Kind.FLYING
                else randomKinds[(Math.random() * randomKinds.size).toInt()]
            // 구한 냥이는 팀 명단에만 올라간다 — 바로 싸우지 않고, 관리 화면에서 출전을 켜 줘야 한다
            val recruited = if (prison.holdsKitten) {
                // 새끼는 엄마(암컷)와 같이 갇혀 있다 — 둘 다 팀에 들어온다 (2026-10-09)
                val mom = com.jamcat.nayngapo.engine.CatTeam.recruit(kind, prison.catCoat, male = false)
                com.jamcat.nayngapo.engine.CatTeam.recruit(kind, prison.catCoat, isKitten = true, parents = "엄마 ${mom.name}").also {
                    hitEffects.add(HitEffect(prison.x, prison.y - prison.height - 50f, 1.6f, "엄마 ${mom.name}도 구출!"))
                }
            } else com.jamcat.nayngapo.engine.CatTeam.recruit(kind, prison.catCoat)
            hitEffects.add(
                HitEffect(
                    prison.x, prison.y - prison.height, 1.6f,
                    "${recruited.name} 구출! 캣타워에서 출전시켜 주세요"
                )
            )
        } else {
            com.jamcat.nayngapo.engine.GameProgress.addRescuedHumans(prison.rescueCount)   // 한 명마다 상점 천막이 열린다
            hitEffects.add(HitEffect(prison.x, prison.y - prison.height, 1f, "인간 ${prison.rescueCount}명 구출!"))
        }
    }

    private fun processInput() {
        val input = touchControls.inputState

        // 횡뷰 플랫포머: 좌우 이동만, 위 방향 입력은 점프로 사용
        var dirX = 0f
        if (input.moveLeft) dirX -= 1f
        if (input.moveRight) dirX += 1f
        player.setMovement(dirX, 0f)

        // 인간 기술 쓰기 (인간이 직접 쓴다 — 냥이가 없어도 된다)
        input.useHumanSkill?.let { id ->
            input.useHumanSkill = null
            useHumanSkill(id)
        }

        // 고양이 기술 쓰기
        input.useSkill?.let { id ->
            input.useSkill = null
            useSkill(id)
        }

        // 팀 냥이 지시 바꾸기 (따라와 → 공격 → 대기 → 따라와)
        if (input.cycleOrder) {
            input.cycleOrder = false
            currentOrder = when (currentOrder) {
                com.jamcat.nayngapo.entity.AllyOrder.FOLLOW -> com.jamcat.nayngapo.entity.AllyOrder.ATTACK
                com.jamcat.nayngapo.entity.AllyOrder.ATTACK -> com.jamcat.nayngapo.entity.AllyOrder.HOLD
                com.jamcat.nayngapo.entity.AllyOrder.HOLD -> com.jamcat.nayngapo.entity.AllyOrder.FOLLOW
            }
            orderPulse = 0.2f
            SoundManager.playCatHit()
            val shout = when (currentOrder) {
                com.jamcat.nayngapo.entity.AllyOrder.ATTACK -> "공격!"
                com.jamcat.nayngapo.entity.AllyOrder.FOLLOW -> "따라간다!"
                com.jamcat.nayngapo.entity.AllyOrder.HOLD -> "대기!"
            }
            for (ally in level.allies) {
                if (ally.isDead) continue
                ally.order = currentOrder
                hitEffects.add(HitEffect(ally.x, ally.y - ally.height - 30f, 0.9f, shout))
            }
        }

        // 십자 패드 아래: 공중 발판 위에서는 내려온다
        // 공중 발판에서는 아래로 못 내려간다 — 가장자리로 걸어 나가야만 떨어진다 (대표님 2026-09-24). 아래 패드는 땅에서 아무 일도 안 한다

        if (input.jump || input.moveUp) {
            // 정글의 덩굴: 아래에서 점프를 누르면 잡고, 매달린 채 누르면 놓는다
            val riding = ridingVine
            val reaching = vineGrabTarget
            val target = if (riding == null && reaching == null) vineUnderPlayer() else null
            when {
                // 손을 뻗는 중에는 아무 입력도 받지 않는다 (0.18초)
                reaching != null -> Unit
                riding != null -> {
                    player.releaseVine(riding.tangentialVelocityX())
                    ridingVine = null
                    vineGrabLockout = 0.3f      // 같은 누름으로 곧바로 다시 잡히지 않게
                    hitEffects.add(HitEffect(player.x, player.y - player.height, 0.6f, "휙!"))
                }
                target != null && vineGrabLockout <= 0f -> {
                    // 순간이동처럼 붙지 않고 덩굴 쪽으로 짧게 끌려간다
                    vineGrabTarget = target
                    vineGrabT = 0f
                    vineGrabFromX = player.x
                    vineGrabFromY = player.y
                    vineGrabFromZ = player.jumpZ
                    player.grabVine()          // 물리·조작은 여기서 이미 멈춘다
                }
                else -> player.jump()
            }
            input.jump = false
        }
        if (input.attack) {
            if (player.performAttack()) {
                SoundManager.playAttack()
                shake(0.07f, 4f)          // 휘두르는 순간의 가벼운 반동
            }
            input.attack = false
        }
        if (input.special) {
            if (stageNumber == 10) {
                // 기획4: 보스전에서는 SP가 로켓 발사 (거대 보스를 멀리서 때린다)
                if (player.specialCooldown <= 0f) {
                    player.specialCooldown = Constants.SPECIAL_COOLDOWN * 0.5f
                    val dir = if (player.facingRight) 1f else -1f
                    level.projectiles.add(
                        com.jamcat.nayngapo.entity.Projectile(
                            player.x + player.width / 2f + dir * 60f,
                            player.y - player.height * 0.55f,
                            dir * 900f, 0f, 15, true,
                            tint = Color.rgb(226, 74, 60), radius = 13f
                        )
                    )
                    SoundManager.playSpecial()
                    hitEffects.add(HitEffect(player.x, player.y - player.height, 0.6f, "로켓 발사!"))
                }
            } else if (player.performSpecial()) SoundManager.playSpecial()
            input.special = false
        }
    }

    override fun draw(canvas: Canvas) {
        val sw = gameView.screenWidth
        val sh = gameView.screenHeight

        // 배경(하늘) → 원경(나무/빙산/피라미드) → 바닥 순서로 그린다: 지면이 밑동을 덮도록
        drawSky(canvas)
        // 지평선 색은 각 지면 표면색과 맞춘다 → 배경과 바닥 사이에 틈(경계)이 보이지 않는다
        when (theme) {
            Theme.GRASS -> {
                drawClouds(canvas)                                  // 기획서의 뭉게구름
                // (초록 지평선 그라데이션은 하늘을 물들여 보여서 뺐다 — 사용자 요청)
                drawBackgroundTrees(canvas)
            }
            Theme.HONEY -> {
                drawHoneyFlowers(canvas)    // 멀리 선 커다란 꽃들
                drawHoneyCeiling(canvas)    // 위쪽은 벌꿀 (대표님 2026-10-09 「위에가 벌꿀」)
            }
            Theme.JUNGLE -> {
                drawClouds(canvas)
                drawJungleTrees(canvas)     // 초원보다 훨씬 큰 나무들
                drawJungleVines(canvas)     // 화면 위에서 늘어진 덩굴
            }
            Theme.SNOW -> {
                drawHorizonBand(canvas, Color.rgb(238, 246, 253))   // 눈 덮인 설원
                drawIcebergs(canvas)
            }
            Theme.DESERT -> {
                drawHorizonBand(canvas, Color.rgb(242, 210, 146))   // 멀리 이어지는 사구
                drawPyramids(canvas)
            }
            Theme.BEACH -> {
                if (isSea) {
                    // 31 해적: 전체가 바다 — 모래사장 없이 수평선부터 발밑까지 물 (대표님 2026-10-09)
                    val horizon = Constants.GROUND_Y - 150f
                    canvas.drawRect(0f, horizon - 96f, gameView.screenWidth.toFloat(), Constants.GROUND_Y, seaPaint)
                    canvas.drawRect(0f, horizon - 96f, gameView.screenWidth.toFloat(), horizon - 40f, seaFarPaint)
                } else drawHorizonBand(canvas, Color.rgb(246, 222, 168))   // 멀리 이어지는 모래사장
                drawSea(canvas)
            }
            Theme.VOLCANO -> {
                drawHorizonBand(canvas, Color.rgb(60, 58, 62))      // 검게 굳은 용암 대지
                drawVolcanoes(canvas)
                // 마지막 스테이지는 목적지인 마법사의 탑이 배경에 솟아 있다
                drawWizardTowers(canvas)
            }
        }
        drawFloor(canvas)
        drawStormSky(canvas)

        // Apply camera transform for game objects (+타격 셰이크)
        var shakeX = 0f
        var shakeY = 0f
        if (shakeTimer > 0f) {
            val k = (shakeTimer / 0.18f).coerceAtMost(1f)
            shakeX = (kotlin.math.sin(elapsed * 83.0) * shakeAmp * k).toFloat()
            shakeY = (kotlin.math.cos(elapsed * 97.0) * shakeAmp * 0.6 * k).toFloat()
        }
        canvas.save()
        canvas.translate(-camera.x + shakeX, -camera.y + shakeY)

        // Collect all drawable entities and sort by y for depth
        data class Drawable(val y: Float, val draw: () -> Unit)
        val drawables = mutableListOf<Drawable>()

        // 함정은 바닥에 붙어 있으므로 다른 개체보다 먼저 그린다
        for (snow in level.snowPits) snow.draw(canvas)
        for (room in level.secretRooms) room.draw(canvas)
        for (swamp in level.swamps) swamp.draw(canvas)
        for (pool in lavaPools) drawLavaPool(canvas, pool)
        level.pirateShipX?.let { drawPirateShip(canvas, it) }
        for (tt in level.tornadoTraps) tt.draw(canvas)
        for (pit in level.pits) pit.draw(canvas)
        for (trap in level.traps) trap.draw(canvas)
        for (wall in level.walls) wall.draw(canvas)

        // 정글의 덩굴 그네 — 개체보다 뒤에 그려 매달린 사람이 앞에 보이게 한다
        for (vine in level.vines) vine.draw(canvas)

        // Prisons
        for (platform in level.platforms) {
            if (platform.right < camera.x - 100f || platform.x > camera.x + sw + 100f) continue
            drawables.add(Drawable(platform.y + platform.height) { platform.draw(canvas) })
        }
        for (prison in level.prisons) {
            drawables.add(Drawable(prison.y) { prison.draw(canvas) })
        }

        // Enemies
        for (enemy in level.enemies) {
            if (!enemy.isDead) {
                drawables.add(Drawable(enemy.y) { enemy.draw(canvas) })
            } else if (enemy.isDying) {
                drawables.add(Drawable(enemy.y) { enemy.drawDeath(canvas) })
            }
        }

        // 아군 냥이
        for (ally in level.allies) {
            drawables.add(Drawable(ally.y) { ally.draw(canvas) })
        }

        // 쓰러진 팀 냥이의 무덤
        for (grave in graves) {
            drawables.add(Drawable(grave.second) { drawGrave(canvas, grave.first, grave.second) })
        }

        // Player (바다에서는 배냥이를 타고)
        if (!player.isDead) {
            drawables.add(Drawable(player.y) {
                if (isSea) drawSeaMount(canvas)
                player.draw(canvas)
            })
        }
        // 바다: 배가 없는 냥이들은 작은 뗏목 위에 서 있다
        if (isSea) {
            // (해적선 갑판 위에 선 냥이는 뗏목 없이)
            for (enemy in level.enemies) if (!enemy.isDead && enemy !is com.jamcat.nayngapo.entity.BoatCat && enemy.isTangible && !Terrain.onDeck(enemy.x + enemy.width / 2f))
                drawables.add(Drawable(enemy.y - 0.5f) { drawRaft(canvas, enemy.x + enemy.width / 2f, enemy.y, enemy.width) })
            for (ally in level.allies) if (!ally.isDead && !Terrain.onDeck(ally.x + ally.width / 2f))
                drawables.add(Drawable(ally.y - 0.5f) { drawRaft(canvas, ally.x + ally.width / 2f, ally.y, ally.width) })
        }

        // Sort by y (top to bottom)
        drawables.sortBy { it.y }
        for (d in drawables) d.draw()

        // 기술을 부른 냥이 머리 위의 분홍 구름
        jellyCaster?.let { c ->
            if (jellyCloudT > 0f || jellyCloudFade > 0f) {
                val a = if (jellyCloudT > 0f) 1f else (jellyCloudFade / 0.35f).coerceIn(0f, 1f)
                val grow =
                    if (jellyCloudT > 0f) 0.6f + 0.4f * (1f - jellyCloudT / JELLY_CLOUD_TIME) else 1f
                drawJellyCloud(canvas, c.x + c.width / 2f, jellyCloudY(c), grow, (255f * a).toInt())
            }
        }

        // 젤리 비
        for (j in jellies) {
            if (j.splatT < 0f) {
                val jx = j.x + kotlin.math.sin(j.y * 0.02f + j.phase) * 10f
                drawJellyPaw(canvas, jx, j.y, 1f, 1f, 255)
            } else {
                // 부딪혀 납작하게 퍼지며 사라진다
                val p = (j.splatT / 0.15f).coerceIn(0f, 1f)
                drawJellyPaw(canvas, j.x, j.y, 1.6f, 0.5f, (255 * (1f - p)).toInt())
            }
        }
        for (p in jellyDroplets) {
            jellyToePaint.alpha = (255f * (p.life / 0.35f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, 5f, jellyToePaint)
        }
        jellyToePaint.alpha = 255

        // 토네이도는 개체보다 앞에 그려 휘감는 느낌을 준다
        for (tor in tornadoes) tor.draw(canvas)
        // 돌벽도 적 앞에 그려 찌르는 느낌을 준다
        for (wall in stoneWalls) wall.draw(canvas)

        // 미사일 폭발 연기 (커지며 흐려진다)
        for (p in smokePuffs) {
            val k = 1f - (p.life / p.maxLife).coerceIn(0f, 1f)
            smokePuffPaint.alpha = (170f * (1f - k)).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.size * (0.6f + 0.9f * k), smokePuffPaint)
        }
        smokePuffPaint.alpha = 255

        // 파이어 펀치: 앞 주먹을 감싼 불꽃
        if (player.frontFist(fist)) drawFlame(canvas, fist.x, fist.y, 1f)
        // 불붙은 적 머리 위의 작은 불꽃
        for (enemy in burning.keys) {
            if (enemy.isDead) continue
            val hb = enemy.hitBox
            drawFlame(canvas, hb.centerX(), hb.top - 8f, 0.55f)
        }
        // 불똥
        for (em in embers) {
            val k = (em.life / em.maxLife).coerceIn(0f, 1f)
            emberPaint.color = if (k > 0.5f) Color.rgb(255, 206, 60) else Color.rgb(255, 120, 40)
            emberPaint.alpha = (230f * k).toInt()
            canvas.drawCircle(em.x, em.y, em.size * (0.4f + 0.6f * k), emberPaint)
        }
        emberPaint.alpha = 255

        // 폭죽 불꽃
        for (f in flashes) {
            val k = 1f - f.life / FLASH_LIFE                      // 0 → 1
            flashPaint.color = android.graphics.Color.rgb(255, 250, 215)
            flashPaint.alpha = (170f * (1f - k) * (1f - k)).toInt().coerceIn(0, 255)
            canvas.drawCircle(f.x, f.y, 16f + 34f * k, flashPaint)
            flashRingPaint.color = android.graphics.Color.rgb(255, 205, 70)
            flashRingPaint.alpha = (160f * (1f - k)).toInt().coerceIn(0, 255)
            canvas.drawCircle(f.x, f.y, 24f + 70f * k, flashRingPaint)
        }
        for (sp in sparks) {
            val fade = (sp.life / sp.maxLife).coerceIn(0f, 1f)
            if (sp.streak) {
                sparkStreakPaint.color = sp.color
                sparkStreakPaint.alpha = (255f * fade).toInt()
                sparkStreakPaint.strokeWidth = sp.size
                canvas.drawLine(sp.x, sp.y, sp.x - sp.vx * 0.05f, sp.y - sp.vy * 0.05f, sparkStreakPaint)
                sparkPaint.color = android.graphics.Color.WHITE
                sparkPaint.alpha = (255f * fade).toInt()
                canvas.drawCircle(sp.x, sp.y, sp.size * 0.55f, sparkPaint)
            } else {
                val twinkle = if (sp.twinkle) 0.5f + 0.5f * kotlin.math.sin(sp.life * 55f) else 1f
                sparkPaint.color = sp.color
                sparkPaint.alpha = (255f * fade * twinkle).toInt().coerceIn(0, 255)
                canvas.drawCircle(sp.x, sp.y, sp.size, sparkPaint)
            }
        }
        sparkPaint.alpha = 255

        // 눈밭 앞쪽 눈더미: 개체 위에 덮어 하반신이 파묻힌 것처럼 보이게 한다
        for (snow in level.snowPits) snow.drawFront(canvas)

        // 폭격 표적/화염 (개체 위)
        drawBlast(canvas)

        // Projectiles (on top)
        for (proj in level.projectiles) {
            if (proj.isAlive) proj.draw(canvas)
        }

        // Hit effects (재사용 페인트에 색/크기/알파만 갱신)
        for (effect in hitEffects) {
            effectPaint.color = if (effect.text.contains("LEVEL") || effect.text.contains("RESCUED"))
                Color.rgb(255, 220, 50) else Color.WHITE
            effectPaint.textSize = if (effect.text.length > 3) 22f else 26f
            effectPaint.alpha = (effect.timer / 0.5f * 255).toInt().coerceIn(0, 255)
            canvas.drawText(effect.text, effect.x, effect.y, effectPaint)
        }

        canvas.restore()

        // 사막 스테이지: 화면 전체를 가로지르는 모래바람 (월드 위, HUD 아래)
        if (theme == Theme.DESERT) drawSandstorm(canvas)

        // Draw HUD (screen space)
        hud.draw(canvas, player, level, sw, sh)

        // 멈추기 / 나가기 버튼
        drawMenuButtons(canvas, sw, sh)

        // Draw controls (screen space) — SP 버튼 카운트다운에 남은 쿨다운을 전달
        touchControls.specialCooldown = player.specialCooldown
        touchControls.draw(canvas, sw, sh)

        // 일시정지 막은 조작 버튼까지 덮어야 하므로 그 뒤에
        drawPauseOverlay(canvas, sw, sh)

        // Wave announcement text
        if (waveTextTimer > 0f) {
            waveTextPaint.alpha = ((waveTextTimer / 2f).coerceIn(0f, 1f) * 255).toInt()
            canvas.drawText(waveTextMessage, sw / 2f, sh / 2f - 50f, waveTextPaint)
        }

        // 진행 유도: 화면 안에 살아있는 냥이가 없고 앞쪽에 남아있으면 GO → 표시
        val viewRight = camera.x + sw
        val aliveOnScreen = level.enemies.any { !it.isDead && it.x + it.width > camera.x && it.x < viewRight }
        val aliveAhead = level.enemies.any { !it.isDead && it.x >= viewRight }
        if (!aliveOnScreen && aliveAhead) {
            val blink = (kotlin.math.sin(elapsed * 6.0) * 0.5 + 0.5).toFloat()
            goPaint.alpha = (110 + 145 * blink).toInt()
            canvas.drawText("GO →", sw - 40f, sh / 2f, goPaint)
        }
    }

    // 매 프레임 재사용하는 페인트들
    private val effectPaint = Paint().apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val goPaint = Paint().apply {
        color = Color.rgb(255, 205, 84); textSize = 44f; textAlign = Paint.Align.RIGHT
        letterSpacing = 0.14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        setShadowLayer(8f, 0f, 2f, Color.argb(160, 0, 0, 0))
    }
    private val floorSrcRect = Rect()
    private val floorDstRect = RectF()

    private var skyShader: Paint? = null

    /** 만화풍 맑은 하늘: 구름 없는 세로 그라데이션 (테마별 색) */
    private fun drawSky(canvas: Canvas) {
        val sw = gameView.screenWidth.toFloat()
        val sh = gameView.screenHeight.toFloat()
        if (skyShader == null) {
            val top = when (theme) {
                Theme.JUNGLE -> Color.rgb(64, 152, 192)     // 축축하고 무성한 정글 하늘
                Theme.HONEY -> Color.rgb(120, 62, 10)       // 어두운 벌꿀빛 하늘 (대표님 2026-10-09)
                Theme.SNOW -> Color.rgb(120, 175, 215)      // 창백하고 차가운 하늘
                Theme.DESERT -> Color.rgb(86, 168, 224)     // 쨍한 사막 하늘
                Theme.BEACH -> Color.rgb(70, 176, 232)      // 맑은 바닷가 하늘
                Theme.VOLCANO -> Color.rgb(58, 30, 42)      // 재로 뒤덮인 어두운 하늘
                else -> Color.rgb(64, 165, 235)
            }
            val bottom = when (theme) {
                Theme.JUNGLE -> Color.rgb(215, 238, 224)    // 옅은 물안개
                Theme.HONEY -> Color.rgb(214, 138, 30)      // 지평선은 조금 밝은 꿀빛
                Theme.SNOW -> Color.rgb(226, 240, 250)
                Theme.DESERT -> Color.rgb(250, 226, 170)    // 지평선의 모래 먼지
                Theme.BEACH -> Color.rgb(214, 244, 252)
                Theme.VOLCANO -> Color.rgb(200, 92, 48)     // 지평선을 물들인 용암 빛
                else -> Color.rgb(188, 230, 252)
            }
            skyShader = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, sh, top, bottom, Shader.TileMode.CLAMP)
            }
        }
        canvas.drawRect(0f, 0f, sw, sh, skyShader!!)
    }

    // 원경 나무: 하늘빛이 살짝 비치되 형태는 또렷하게 보이는 실루엣
    private val treeTrunkPaint = Paint().apply { color = Color.argb(190, 96, 74, 56); isAntiAlias = true }
    private val treeLeafPaint = Paint().apply { color = Color.argb(210, 74, 142, 88); isAntiAlias = true }
    private val treeLeafPaintFar = Paint().apply { color = Color.argb(165, 104, 168, 118); isAntiAlias = true }
    // 정글: 더 크고 짙은 나무 + 덩굴
    private val jungleTrunkPaint = Paint().apply { color = Color.argb(200, 84, 62, 46); isAntiAlias = true }
    private val jungleLeafPaint = Paint().apply { color = Color.argb(220, 46, 116, 62); isAntiAlias = true }
    private val jungleLeafPaintFar = Paint().apply { color = Color.argb(170, 74, 146, 92); isAntiAlias = true }
    private val jungleVinePaint = Paint().apply {
        color = Color.argb(200, 62, 124, 58); style = Paint.Style.STROKE
        strokeWidth = 9f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val jungleVineLeafPaint = Paint().apply { color = Color.argb(205, 70, 138, 66); isAntiAlias = true }

    /**
     * 스테이지 1~2 배경: 멀리 흐릿한 나무 몇 그루가 시차(parallax) 스크롤로 천천히 흐른다.
     * 위치·크기는 인덱스 기반 의사난수라 매 프레임 동일하게 재현된다.
     */
    private fun drawBackgroundTrees(canvas: Canvas) {
        val baseY = Constants.GROUND_Y + 10f
        forEachBackdrop(0.35f, 560f, 260f) { i, sx, scale, far ->
            val trunkH = 190f * scale
            val topY = baseY - trunkH
            val leaf = if (far) treeLeafPaintFar else treeLeafPaint

            // 밑동에서 위로 갈수록 가늘어지는 줄기 + 좌우로 뻗은 가지
            canvas.drawPath(backdropPath.apply {
                reset()
                moveTo(sx - 20f * scale, baseY + 40f)   // 지면 아래까지 내려 그려 틈이 없게
                lineTo(sx - 9f * scale, topY)
                lineTo(sx + 9f * scale, topY)
                lineTo(sx + 20f * scale, baseY + 40f)
                close()
            }, treeTrunkPaint)
            canvas.drawPath(backdropPath.apply {
                reset()
                moveTo(sx - 6f * scale, topY + 46f * scale)
                lineTo(sx - 44f * scale, topY + 6f * scale)
                lineTo(sx - 40f * scale, topY + 26f * scale)
                lineTo(sx + 6f * scale, topY + 62f * scale)
                lineTo(sx + 46f * scale, topY + 10f * scale)
                lineTo(sx + 42f * scale, topY + 30f * scale)
                close()
            }, treeTrunkPaint)

            // 잎: 가장자리가 들쭉날쭉한 수관 덩어리 (원이 아니라 잎이 뭉친 실루엣)
            buildCanopy(sx, topY - 18f * scale, 104f * scale, 78f * scale, i)
            canvas.drawPath(backdropPath, leaf)
            buildCanopy(sx - 34f * scale, topY + 26f * scale, 62f * scale, 46f * scale, i + 5)
            canvas.drawPath(backdropPath, leaf)
            buildCanopy(sx + 38f * scale, topY + 22f * scale, 58f * scale, 44f * scale, i + 11)
            canvas.drawPath(backdropPath, leaf)
        }
    }

    /**
     * 정글의 거대한 나무 (초원 나무의 두 배 가까운 키에 수관도 훨씬 크다).
     * 덩굴냥이들이 이 나무 사이를 날아다닌다.
     */
    private fun drawJungleTrees(canvas: Canvas) {
        val baseY = Constants.GROUND_Y + 10f
        forEachBackdrop(0.4f, 520f, 380f) { i, sx, scale, far ->
            val trunkH = 380f * scale
            val topY = baseY - trunkH
            val leaf = if (far) jungleLeafPaintFar else jungleLeafPaint

            // 굵은 밑동에서 위로 갈수록 가늘어지는 줄기
            canvas.drawPath(backdropPath.apply {
                reset()
                moveTo(sx - 34f * scale, baseY + 40f)
                lineTo(sx - 14f * scale, topY)
                lineTo(sx + 14f * scale, topY)
                lineTo(sx + 34f * scale, baseY + 40f)
                close()
            }, jungleTrunkPaint)
            // 옆으로 뻗은 큰 가지
            canvas.drawPath(backdropPath.apply {
                reset()
                moveTo(sx - 9f * scale, topY + 90f * scale)
                lineTo(sx - 78f * scale, topY + 22f * scale)
                lineTo(sx - 70f * scale, topY + 50f * scale)
                lineTo(sx + 9f * scale, topY + 116f * scale)
                lineTo(sx + 82f * scale, topY + 30f * scale)
                lineTo(sx + 74f * scale, topY + 58f * scale)
                close()
            }, jungleTrunkPaint)

            // 초원보다 훨씬 큰 수관 덩어리들
            buildCanopy(sx, topY - 30f * scale, 185f * scale, 128f * scale, i)
            canvas.drawPath(backdropPath, leaf)
            buildCanopy(sx - 92f * scale, topY + 46f * scale, 112f * scale, 82f * scale, i + 5)
            canvas.drawPath(backdropPath, leaf)
            buildCanopy(sx + 98f * scale, topY + 40f * scale, 106f * scale, 78f * scale, i + 11)
            canvas.drawPath(backdropPath, leaf)
        }
    }

    /** 화면 위(수관)에서 늘어져 바람에 흔들리는 배경 덩굴 */
    private fun drawJungleVines(canvas: Canvas) {
        forEachBackdrop(0.55f, 430f, 220f) { i, sx, scale, far ->
            val len = 210f + 190f * scale
            val sway = kotlin.math.sin(elapsed * 0.9 + i * 1.7).toFloat() * 26f
            val alpha = if (far) 0.6f else 1f

            vinePath.reset()
            vinePath.moveTo(sx, -12f)
            vinePath.cubicTo(
                sx - sway * 0.4f, len * 0.35f,
                sx + sway, len * 0.7f,
                sx + sway * 1.4f, len
            )
            jungleVinePaint.alpha = (200 * alpha).toInt()
            canvas.drawPath(vinePath, jungleVinePaint)

            // 덩굴을 따라 붙은 잎사귀들
            for (k in 1..3) {
                val t = k / 3.4f
                val lx = sx + sway * t * 1.2f
                val ly = len * t
                val side = if (k % 2 == 0) 1f else -1f
                jungleVineLeafPaint.alpha = (205 * alpha).toInt()
                canvas.drawOval(lx - 4f + side * 4f, ly - 7f, lx + 26f * side + 4f, ly + 7f, jungleVineLeafPaint)
            }
        }
    }

    private val vinePath = Path()

    /** 잎이 뭉친 수관 실루엣: 반지름을 사인파로 흔들어 들쭉날쭉하게 만든다 */
    private fun buildCanopy(cx: Float, cy: Float, rx: Float, ry: Float, seed: Int) {
        backdropPath.reset()
        val n = 30
        for (k in 0 until n) {
            val a = k / n.toFloat() * 6.2831855f
            val wob = 1f + 0.15f * kotlin.math.sin(a * 5f + seed).toFloat() +
                0.08f * kotlin.math.sin(a * 9f + seed * 2f).toFloat()
            val px = cx + kotlin.math.cos(a.toDouble()).toFloat() * rx * wob
            val py = cy + kotlin.math.sin(a.toDouble()).toFloat() * ry * wob
            if (k == 0) backdropPath.moveTo(px, py) else backdropPath.lineTo(px, py)
        }
        backdropPath.close()
    }

    // 마법사의 탑
    private val towerBodyPaint = Paint().apply { color = Color.argb(255, 58, 44, 72); isAntiAlias = true }
    private val towerEdgePaint = Paint().apply { color = Color.argb(255, 44, 32, 56); isAntiAlias = true }
    private val towerRoofPaint = Paint().apply { color = Color.argb(255, 96, 58, 132); isAntiAlias = true }
    private val towerWindowPaint = Paint().apply { color = Color.argb(255, 255, 210, 120); isAntiAlias = true }
    private val towerOrbPaint = Paint().apply { color = Color.argb(200, 190, 130, 255); isAntiAlias = true }

    /** 최종 목적지: 멀리 우뚝 솟은 마법사의 탑 (가장 느린 시차로 크게 보인다) */
    private fun drawWizardTowers(canvas: Canvas) {
        val baseY = Constants.GROUND_Y + 20f
        forEachBackdrop(0.18f, 1600f, 400f) { _, sx, scale, _ ->
            val h = 620f * scale
            val halfBottom = 66f * scale
            val halfTop = 46f * scale
            val topY = baseY - h

            // 탑 몸체: 아래가 넓은 사다리꼴
            canvas.drawPath(backdropPath.apply {
                reset()
                moveTo(sx - halfBottom, baseY)
                lineTo(sx - halfTop, topY)
                lineTo(sx + halfTop, topY)
                lineTo(sx + halfBottom, baseY)
                close()
            }, towerBodyPaint)
            // 오른쪽 그늘 면
            canvas.drawPath(backdropPath.apply {
                reset()
                moveTo(sx + halfTop * 0.35f, topY)
                lineTo(sx + halfTop, topY)
                lineTo(sx + halfBottom, baseY)
                lineTo(sx + halfBottom * 0.35f, baseY)
                close()
            }, towerEdgePaint)

            // 원뿔 지붕 + 첨탑 끝의 마력 구슬
            canvas.drawPath(backdropPath.apply {
                reset()
                moveTo(sx - halfTop * 1.5f, topY)
                lineTo(sx, topY - 150f * scale)
                lineTo(sx + halfTop * 1.5f, topY)
                close()
            }, towerRoofPaint)
            val pulse = 1f + 0.18f * kotlin.math.sin(elapsed * 2.4).toFloat()
            canvas.drawCircle(sx, topY - 162f * scale, 16f * scale * pulse, towerOrbPaint)

            // 층마다 불이 켜진 창문
            for (k in 0 until 5) {
                val wy = baseY - h * (0.16f + 0.16f * k)
                val ww = 11f * scale
                val wh = 20f * scale
                canvas.drawRect(sx - ww, wy - wh, sx + ww, wy, towerWindowPaint)
            }
        }
    }

    // 사막 모래바람
    private val sandGustPaint = Paint().apply {
        color = Color.argb(70, 236, 208, 150); style = Paint.Style.STROKE
        strokeWidth = 3f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val sandGrainPaint = Paint().apply { color = Color.argb(110, 246, 224, 172); isAntiAlias = true }
    private val sandHazePaint = Paint().apply { color = Color.argb(16, 240, 214, 158) }

    /**
     * 모래바람 세기(0~1). 50초에 한 번, 8초 동안 서서히 몰아쳤다 잦아든다.
     * 바람이 불지 않는 동안은 0이라 화면이 깨끗하다.
     */
    private val sandstormPower: Float
        get() {
            val cycle = elapsed % Constants.SANDSTORM_INTERVAL
            val start = Constants.SANDSTORM_INTERVAL - Constants.SANDSTORM_DURATION
            if (cycle < start) return 0f
            val t = (cycle - start) / Constants.SANDSTORM_DURATION      // 0 → 1
            return kotlin.math.sin(Math.PI * t).toFloat()               // 봉우리 하나
        }

    /**
     * 사막 스테이지의 모래바람: 옅은 모래 안개 위로 결과 알갱이가 왼쪽으로 흘러간다.
     * 위치는 시간과 인덱스로만 계산해 별도 파티클 배열 없이 매 프레임 재현된다.
     */
    private fun drawSandstorm(canvas: Canvas) {
        val power = sandstormPower
        if (power <= 0.01f) return                       // 바람이 불지 않는 시간대

        val sw = gameView.screenWidth.toFloat()
        val sh = gameView.screenHeight.toFloat()

        sandHazePaint.alpha = (16 * power).toInt()
        canvas.drawRect(0f, 0f, sw, sh, sandHazePaint)   // 전체를 감싸는 모래 안개

        // 길게 늘어진 바람 결
        for (i in 0 until 44) {
            val speed = 620f + (i % 5) * 190f
            val len = 90f + (i % 7) * 55f
            val y = ((i * 137) % (sh.toInt() - 40)).toFloat() + 20f
            val x = (sw + len) - ((elapsed * speed + i * 311f) % (sw + len * 2f))
            sandGustPaint.alpha = ((40 + (i % 4) * 22) * power).toInt()
            canvas.drawLine(x, y, x + len, y + 6f, sandGustPaint)
        }

        // 흩날리는 모래 알갱이
        for (i in 0 until 70) {
            val speed = 380f + (i % 6) * 130f
            val baseY = ((i * 197) % (sh.toInt() - 30)).toFloat() + 15f
            val y = baseY + kotlin.math.sin(elapsed * 2.2 + i).toFloat() * 14f
            val x = sw - ((elapsed * speed + i * 173f) % (sw + 60f))
            sandGrainPaint.alpha = ((70 + (i % 5) * 30) * power).toInt()
            canvas.drawCircle(x, y, 2f + (i % 3), sandGrainPaint)
        }
    }

    private val horizonPaint = Paint().apply { isAntiAlias = true }
    private var horizonShaderColor = 0

    /**
     * 원경 오브젝트 아래를 메우는 먼 능선.
     * 지면과 배경 사이로 하늘이 비쳐 '구멍'처럼 보이지 않도록 지평선을 채운다.
     */
    private fun drawHorizonBand(canvas: Canvas, color: Int) {
        val sw = gameView.screenWidth.toFloat()
        // Terrain이 솟을 수 있는 최대 높이보다 위에서 시작해야 어떤 언덕에서도 틈이 없다
        val maxRise = 18f + stageNumber * 17f
        val topBase = Constants.GROUND_Y - maxRise - 95f
        val scroll = camera.x * 0.3f

        // 경계선 없이 하늘로 서서히 스며드는 그라데이션 (윤곽선이 보이지 않게)
        if (horizonShaderColor != color) {
            horizonShaderColor = color
            horizonPaint.shader = LinearGradient(
                0f, topBase - 40f, 0f, topBase + 150f,
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
                color, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, topBase - 40f, sw, Constants.GROUND_Y + 300f, horizonPaint)
    }

    // 설원 원경 빙산
    private val icebergPaint = Paint().apply { color = Color.argb(255, 142, 188, 220); isAntiAlias = true }
    private val icebergLitPaint = Paint().apply { color = Color.argb(255, 228, 244, 254); isAntiAlias = true }
    private val icebergFarPaint = Paint().apply { color = Color.argb(215, 172, 206, 230); isAntiAlias = true }
    // 사막 원경 피라미드
    private val pyramidPaint = Paint().apply { color = Color.argb(255, 186, 142, 78); isAntiAlias = true }
    private val pyramidLitPaint = Paint().apply { color = Color.argb(255, 232, 194, 120); isAntiAlias = true }
    private val pyramidFarPaint = Paint().apply { color = Color.argb(205, 204, 168, 112); isAntiAlias = true }
    // 화산 원경
    private val volcanoPaint = Paint().apply { color = Color.argb(255, 46, 32, 38); isAntiAlias = true }
    private val volcanoLitPaint = Paint().apply { color = Color.argb(190, 74, 52, 54); isAntiAlias = true }
    private val lavaPaint = Paint().apply { color = Color.argb(235, 255, 130, 40); isAntiAlias = true }
    private val smokePaint = Paint().apply { color = Color.argb(60, 70, 60, 64); isAntiAlias = true }
    private val backdropPath = Path()

    /**
     * 원경 오브젝트 공통 배치: 시차(parallax) 스크롤 + 인덱스 기반 의사난수로
     * 매 프레임 같은 자리에 재현된다. draw(index, 화면x, 크기배율, 더멀리여부)
     */
    private inline fun forEachBackdrop(
        parallax: Float, spacing: Float, margin: Float,
        draw: (i: Int, sx: Float, scale: Float, far: Boolean) -> Unit
    ) {
        val scrollX = camera.x * parallax
        val first = kotlin.math.floor(scrollX / spacing).toInt() - 1
        val count = (gameView.screenWidth / spacing).toInt() + 3
        for (i in first until first + count) {
            val r = kotlin.math.abs(i * 73856093 xor 19349663)
            val jitter = (r % 200) - 100
            val sx = i * spacing + jitter - scrollX
            if (sx < -margin || sx > gameView.screenWidth + margin) continue
            draw(i, sx, 0.7f + (r / 7 % 55) / 100f, (r / 3 % 2) == 0)
        }
    }

    /** 설원 원경: 뾰족한 빙산 봉우리 (밝은 면 + 그늘 면) */
    private fun drawIcebergs(canvas: Canvas) {
        val baseY = Constants.GROUND_Y + 20f
        forEachBackdrop(0.3f, 640f, 320f) { i, sx, scale, far ->
            val h = 300f * scale
            val halfW = 150f * scale
            val body = if (far) icebergFarPaint else icebergPaint

            // 그루마다 조금씩 다른 방향으로 살짝 기운다 (똑바로 선 얼음이 없도록)
            val lean = ((kotlin.math.abs(i * 37 + 11) % 7) - 3) / 3f * 0.42f
            // 밑동은 그대로 두고 높이에 비례해 밀어 기울인다
            fun px(fx: Float, heightFrac: Float) = sx + halfW * fx + lean * halfW * heightFrac

            backdropPath.reset()
            backdropPath.moveTo(sx - halfW, baseY)
            backdropPath.lineTo(px(-0.32f, 0.62f), baseY - h * 0.62f)   // 어깨에서 한 번 꺾인다
            backdropPath.lineTo(px(0.1f, 1f), baseY - h)                // 정상
            backdropPath.lineTo(px(0.55f, 0.45f), baseY - h * 0.45f)
            backdropPath.lineTo(sx + halfW, baseY)
            backdropPath.close()
            canvas.drawPath(backdropPath, body)

            if (!far) {
                // 햇빛 받는 면
                backdropPath.reset()
                backdropPath.moveTo(px(0.1f, 1f), baseY - h)
                backdropPath.lineTo(px(0.55f, 0.45f), baseY - h * 0.45f)
                backdropPath.lineTo(sx + halfW, baseY)
                backdropPath.lineTo(sx + halfW * 0.35f, baseY)
                backdropPath.close()
                canvas.drawPath(backdropPath, icebergLitPaint)
            }
        }
    }

    /** 사막 원경: 모래빛 피라미드 (양지 면 + 음지 면) */
    private fun drawPyramids(canvas: Canvas) {
        val baseY = Constants.GROUND_Y + 20f
        forEachBackdrop(0.3f, 680f, 340f) { _, sx, scale, far ->
            val h = 260f * scale
            val halfW = 190f * scale
            canvas.drawPath(backdropPath.apply {
                reset()
                moveTo(sx, baseY - h)
                lineTo(sx + halfW, baseY)
                lineTo(sx - halfW, baseY)
                close()
            }, if (far) pyramidFarPaint else pyramidPaint)

            if (!far) {
                // 오른쪽 절반이 햇빛을 받는다
                canvas.drawPath(backdropPath.apply {
                    reset()
                    moveTo(sx, baseY - h)
                    lineTo(sx + halfW, baseY)
                    lineTo(sx, baseY)
                    close()
                }, pyramidLitPaint)
            }
        }
    }

    // 초원 하늘의 뭉게구름 (기획서 그림처럼 둥글둥글하게)
    private val cloudPaint = Paint().apply { color = Color.argb(235, 255, 255, 255); isAntiAlias = true }
    private val cloudEdgePaint = Paint().apply {
        color = Color.argb(120, 120, 160, 190); style = Paint.Style.STROKE
        strokeWidth = 3f; isAntiAlias = true
    }
    private val cloudPath = Path()
    private val cloudPart = Path()

    /** 구름에 동그라미 하나를 합쳐 붙인다 */
    private fun addToCloud(cx: Float, cy: Float, r: Float) {
        cloudPart.reset()
        cloudPart.addCircle(cx, cy, r, Path.Direction.CW)
        cloudPath.op(cloudPart, Path.Op.UNION)
    }

    /** 하늘에 떠 있는 구름 — 카메라보다 아주 느리게 흘러 멀리 있어 보인다 */
    // ===== 31 해적 바다: 물 바닥 · 인간이 탄 배냥이 · 뗏목 =====
    private val seaWater = Paint().apply { color = Color.rgb(46, 128, 196) }
    private val seaDeep = Paint().apply { color = Color.rgb(30, 96, 160) }
    private val seaFoam = Paint().apply { color = Color.argb(220, 230, 246, 255); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val raftPaint = Paint().apply { color = Color.rgb(150, 96, 52); isAntiAlias = true }
    private val raftInk = Paint().apply { color = Color.rgb(60, 36, 20); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val mountDst = RectF()

    /** 화면 아래를 채우는 바다 — 물결이 카메라를 따라 흐른다 */
    private fun drawSeaFloor(canvas: Canvas) {
        val sw = gameView.screenWidth.toFloat(); val sh = gameView.screenHeight.toFloat()
        val top = Constants.GROUND_Y - 18f
        canvas.drawRect(0f, top, sw, sh, seaWater)
        canvas.drawRect(0f, top + 70f, sw, sh, seaDeep)
        val drift = camera.x % 120f
        var x = -drift - 120f
        while (x < sw + 120f) {
            val y = top + 6f + kotlin.math.sin((x + camera.x) * 0.02f + elapsed * 2f) * 5f
            canvas.drawLine(x, y, x + 60f, y + 3f, seaFoam)
            canvas.drawLine(x + 40f, y + 46f, x + 90f, y + 48f, seaFoam)
            x += 120f
        }
    }

    /** 인간이 탄 배냥이: 인간 발밑에 무늬 그대로의 배냥이(내 편)를 그린다 */
    private fun drawSeaMount(canvas: Canvas) {
        val m = seaMount ?: return
        val base = m.coat.boatAlly
        val bmp = if (player.facingRight) base else SpriteManager.getFlipped(base)
        val s = 1.35f
        val cx = player.x + player.width / 2f
        val bob = kotlin.math.sin(elapsed * 3f) * 3f
        // 다리는 물에 잠겨 안 보인다 — 배 바닥이 물 위(발 높이)에 오게 그리고 그 아래는 자른다
        val water = player.y + player.jumpZ + 10f + bob
        val top = water - bmp.height * s * com.jamcat.nayngapo.entity.BoatCat.BOAT_HULL_BOTTOM
        mountDst.set(cx - bmp.width * s / 2f, top, cx + bmp.width * s / 2f, top + bmp.height * s)
        canvas.save()
        canvas.clipRect(mountDst.left - 2f, mountDst.top - 2f, mountDst.right + 2f, water)
        canvas.drawBitmap(bmp, null, mountDst, null)
        canvas.restore()
    }

    /** 물 위의 작은 뗏목 (배가 없는 냥이용) */
    private fun drawRaft(canvas: Canvas, cx: Float, y: Float, w: Float) {
        val hw = w * 0.7f
        mountDst.set(cx - hw, y - 6f, cx + hw, y + 16f)
        canvas.drawRoundRect(mountDst, 8f, 8f, raftPaint)
        canvas.drawRoundRect(mountDst, 8f, 8f, raftInk)
        var k = cx - hw + hw / 3f
        while (k < cx + hw) { canvas.drawLine(k, y - 6f, k, y + 16f, raftInk); k += hw / 1.5f }
    }

    /**
     * 해적선이 앞으로 움직인다 (대표님 2026-10-09). 인간이 가까이(2200 안) 오면 천천히 왼쪽으로 나아가고,
     * 갑판 위에 선 것들(인간·냥이·해적냥이)은 배와 함께 옮겨 간다.
     * 물 위의 인간 바로 앞에 뱃머리가 닿으면 멈춰 서서 점프로 올라탈 수 있게 한다. 해적냥이가 쓰러지면 멈춘다.
     * (속도 45·최대 1600 이동은 Claude 판단)
     */
    private var shipTravel = 0f
    private fun sailPirateShip(deltaTime: Float) {
        val sx = level.pirateShipX ?: return
        val pcx = player.x + player.width / 2f
        val boss = level.enemies.firstOrNull { it is com.jamcat.nayngapo.entity.PirateCatBoss && !it.isDead }
        if (boss == null || shipTravel >= SHIP_MAX_TRAVEL || Terrain.deckLeft - pcx > SHIP_WAKE_RANGE) return
        val playerOnDeck = Terrain.onDeck(pcx)
        // 물 위의 인간 바로 앞까지 오면 멈춘다 (뱃전에 부딪혀 밀리지 않게)
        if (!playerOnDeck && pcx < Terrain.deckLeft && Terrain.deckLeft - (player.x + player.width) < SHIP_STOP_GAP) return
        val d = -SHIP_SPEED * deltaTime
        // 갑판 위의 것들을 먼저 고른 뒤 배를 옮기고 같이 옮긴다
        val riders = level.enemies.filter { !it.isDead && Terrain.onDeck(it.x + it.width / 2f) }
        val allyRiders = level.allies.filter { !it.isDead && Terrain.onDeck(it.x + it.width / 2f) }
        level.pirateShipX = sx + d
        Terrain.deckLeft += d; Terrain.deckRight += d
        shipTravel -= d
        if (playerOnDeck) player.x += d
        for (e in riders) {
            e.x += d
            if (e is com.jamcat.nayngapo.entity.PirateCatBoss) { e.deckLeft += d; e.deckRight += d }
        }
        for (a in allyRiders) a.x += d
    }

    // ===== 31 해적: 해적선 (모래밭에 얹힌 커다란 배, 보스 뒤 배경) =====
    private val shipDst = RectF()
    private fun drawPirateShip(canvas: Canvas, sx: Float) {
        val bmp = SpriteManager.pirateShip
        val h = com.jamcat.nayngapo.level.SHIP_DRAW_H
        val w = bmp.width * h / bmp.height
        val gy = Constants.GROUND_Y          // 물 높이 (갑판 위는 Terrain 이 갑판 높이를 돌려준다)
        shipDst.set(sx - w / 2f, gy + 60f - h, sx + w / 2f, gy + 60f)
        canvas.drawBitmap(bmp, null, shipDst, null)
    }

    // ===== 꿀 스테이지 (기획10) =====
    private val stemPaint = Paint().apply { color = Color.rgb(96, 170, 80); strokeWidth = 14f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val petalPaint = Paint().apply { isAntiAlias = true }
    private val petalCore = Paint().apply { color = Color.rgb(255, 214, 70); isAntiAlias = true }

    private val honeyTop = Paint().apply { color = Color.rgb(196, 112, 12); isAntiAlias = true }
    private val honeyGloss = Paint().apply { color = Color.rgb(250, 190, 70); isAntiAlias = true }
    private val honeyPath = Path()

    /** 화면 위쪽을 덮은 벌꿀: 물결진 아랫자락에서 꿀 방울이 길게 늘어진다 (카메라를 따라 천천히 흐른다) */
    private fun drawHoneyCeiling(canvas: Canvas) {
        val sw = gameView.screenWidth.toFloat()
        val drift = camera.x * 0.2f
        val base = 120f
        honeyPath.reset()
        honeyPath.moveTo(0f, 0f)
        var x = 0f
        while (x <= sw + 30f) {
            val wx = x + drift
            honeyPath.lineTo(x, base + kotlin.math.sin(wx * 0.012f) * 18f + kotlin.math.sin(wx * 0.031f + 2f) * 8f)
            x += 20f
        }
        honeyPath.lineTo(sw + 30f, 0f)
        honeyPath.close()
        canvas.drawPath(honeyPath, honeyTop)
        // 늘어지는 꿀 방울 (자리마다 길이가 다르고 천천히 늘었다 줄었다)
        var k = kotlin.math.floor(drift / 140f).toInt() - 1
        while (k * 140f - drift < sw + 140f) {
            val dx = k * 140f - drift + 40f
            val seed = ((k % 7) + 7) % 7
            val len = 30f + seed * 14f + kotlin.math.sin(elapsed * 0.8f + k) * 10f
            val top = base + kotlin.math.sin((dx + drift) * 0.012f) * 18f - 6f
            canvas.drawRect(dx - 9f, top, dx + 9f, top + len, honeyTop)
            canvas.drawCircle(dx, top + len, 14f, honeyTop)
            canvas.drawCircle(dx - 4f, top + len - 4f, 4f, honeyGloss)
            k++
        }
        // 윗부분 광택 줄
        canvas.drawRect(0f, 26f, sw, 34f, honeyGloss)
    }

    /** 멀리 선 커다란 꽃들 (느린 원경) */
    private fun drawHoneyFlowers(canvas: Canvas) {
        val sw = gameView.screenWidth.toFloat()
        val gap = 520f
        val drift = camera.x * 0.25f
        var k = kotlin.math.floor(drift / gap).toInt() - 1
        while (k * gap - drift < sw + gap) {
            val sx = k * gap - drift
            val seed = ((k % 4) + 4) % 4
            val topY = Constants.GROUND_Y - 260f - seed * 50f
            canvas.drawLine(sx, Constants.GROUND_Y, sx, topY, stemPaint)
            petalPaint.color = when (seed) { 0 -> Color.rgb(250, 140, 180); 1 -> Color.rgb(255, 255, 255); 2 -> Color.rgb(255, 170, 90); else -> Color.rgb(200, 150, 240) }
            for (p in 0 until 6) {
                val a = Math.toRadians(p * 60.0)
                canvas.drawCircle(sx + (kotlin.math.cos(a) * 34).toFloat(), topY + (kotlin.math.sin(a) * 34).toFloat(), 26f, petalPaint)
            }
            canvas.drawCircle(sx, topY, 24f, petalCore)
            k++
        }
    }

    /** 끓는 용암 웅덩이: 납작한 검붉은 테 + 주황 속 + 부글거리는 노란 방울, 식을 때 흐려진다 */
    private fun drawLavaPool(canvas: Canvas, pool: LavaPool) {
        val grow = (pool.t / 0.15f).coerceIn(0f, 1f)
        val fade = (1f - (pool.t - (LAVA_POOL_TIME - 0.5f)) / 0.5f).coerceIn(0f, 1f)
        val a = (255 * fade).toInt()
        val hw = LAVA_POOL_HALF * grow
        lavaPoolCrust.alpha = a; lavaPoolPaint.alpha = a; lavaPoolGlow.alpha = a
        lavaRect.set(pool.x - hw - 6f, pool.y - 14f, pool.x + hw + 6f, pool.y + 8f)
        canvas.drawOval(lavaRect, lavaPoolCrust)
        lavaRect.set(pool.x - hw, pool.y - 10f, pool.x + hw, pool.y + 4f)
        canvas.drawOval(lavaRect, lavaPoolPaint)
        for (k in 0 until 3) {
            val ph = (pool.t * 2.2f + k * 0.33f) % 1f
            val bx = pool.x + (k - 1) * hw * 0.55f
            canvas.drawCircle(bx, pool.y - 6f - ph * 14f, 5f * (1f - ph) + 2f, lavaPoolGlow)
        }
    }

    // ===== 노랑냥이 먹구름 (대표님 그림 2026-09-27): 하늘 위쪽 전체가 먹구름으로 덮이고 거기서 번개가 떨어진다 =====
    private val stormFill = Paint().apply { color = Color.rgb(70, 72, 84); isAntiAlias = true }
    private val stormEdge = Paint().apply {
        color = Color.rgb(34, 34, 42); style = Paint.Style.STROKE; strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND; isAntiAlias = true
    }
    private val stormScribble = Paint().apply {
        color = Color.rgb(46, 46, 56); strokeWidth = 3f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val stormShade = Paint().apply { color = Color.BLACK }
    private val stormGlow = Paint().apply { color = Color.rgb(255, 230, 90); isAntiAlias = true }
    private val stormPath = Path()

    /** 먹구름 띠 아랫자락 높이(화면 y) — 물결치며 이보다 조금 오르내린다 (Claude 판단) */
    private val stormBottom = 230f

    private fun drawStormSky(canvas: Canvas) {
        val sw = gameView.screenWidth.toFloat()
        com.jamcat.nayngapo.entity.YellowCat.skyBottomY = camera.y + stormBottom
        var storm = 0f
        for (enemy in level.enemies) if (enemy is com.jamcat.nayngapo.entity.YellowCat) storm = maxOf(storm, enemy.storm)
        if (storm <= 0.01f) return
        val a = storm.coerceIn(0f, 1f)
        // 온 화면이 살짝 어두워진다
        stormShade.alpha = (70 * a).toInt()
        canvas.drawRect(0f, 0f, sw, gameView.screenHeight.toFloat(), stormShade)
        // 위에서 내려오는 띠 — 아랫자락은 큰 물결 (카메라를 따라 천천히 흐른다)
        val drop = (1f - a) * (stormBottom + 60f)            // 짙어지며 위에서 내려온다
        val drift = camera.x * 0.3f
        stormPath.reset()
        stormPath.moveTo(0f, -10f)
        stormPath.lineTo(0f, stormBottom - drop)
        var x = 0f
        while (x <= sw + 40f) {
            val wx = x + drift
            val y = stormBottom - drop + (kotlin.math.sin(wx * 0.009f) * 34f + kotlin.math.sin(wx * 0.021f + 1.7f) * 14f)
            stormPath.lineTo(x, y)
            x += 24f
        }
        stormPath.lineTo(sw + 40f, -10f)
        stormPath.close()
        stormFill.alpha = (235 * a).toInt()
        canvas.drawPath(stormPath, stormFill)
        // 손으로 칠한 듯한 비스듬한 빗금
        stormScribble.alpha = (200 * a).toInt()
        canvas.save()
        canvas.clipPath(stormPath)
        var k = -((drift % 46f))
        while (k < sw + 200f) {
            canvas.drawLine(k, stormBottom - drop + 30f, k + 160f, -20f, stormScribble)
            k += 46f
        }
        canvas.restore()
        stormEdge.alpha = (255 * a).toInt()
        canvas.drawPath(stormPath, stormEdge)
        // 번개가 떨어질 자리: 구름 속이 번쩍인다 (막바지일수록 자주) — 비켜 설 신호
        for (enemy in level.enemies) {
            if (enemy !is com.jamcat.nayngapo.entity.YellowCat) continue
            for ((tx, prog) in enemy.stormTargets) {
                if (prog >= 1f) continue
                val rate = 6f + 18f * prog
                if (kotlin.math.sin(elapsed * rate) > 0.4) {
                    stormGlow.alpha = (150 * a).toInt()
                    canvas.drawCircle(tx - camera.x, stormBottom - drop - 30f, 44f + 20f * prog, stormGlow)
                }
            }
        }
    }

    private fun drawClouds(canvas: Canvas) {
        val sw = gameView.screenWidth.toFloat()
        val gap = 620f
        val drift = camera.x * 0.12f
        var k = kotlin.math.floor(drift / gap).toInt() - 1
        while (k * gap - drift < sw + gap) {
            val sx = k * gap - drift
            val seed = ((k % 5) + 5) % 5
            val cy = 120f + seed * 62f
            val scale = 0.8f + seed * 0.12f
            // 동그라미 여러 개를 하나로 합쳐 이음새 없는 뭉게구름 실루엣을 만든다
            cloudPath.reset()
            cloudPath.addCircle(sx, cy, 46f * scale, Path.Direction.CW)
            addToCloud(sx + 52f * scale, cy - 18f * scale, 58f * scale)
            addToCloud(sx + 112f * scale, cy + 2f * scale, 44f * scale)
            cloudPart.reset()
            cloudPart.addRect(sx - 6f, cy, sx + 118f * scale, cy + 40f * scale, Path.Direction.CW)
            cloudPath.op(cloudPart, Path.Op.UNION)
            canvas.drawPath(cloudPath, cloudPaint)
            canvas.drawPath(cloudPath, cloudEdgePaint)
            k++
        }
    }

    // 바닷가 원경: 수평선까지 이어지는 바다와 잔물결
    private val seaPaint = Paint().apply { color = Color.rgb(56, 136, 200); isAntiAlias = true }
    private val seaFarPaint = Paint().apply { color = Color.rgb(96, 172, 216); isAntiAlias = true }
    private val wavePaint = Paint().apply {
        color = Color.argb(210, 245, 252, 255); strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }

    /** 바닷가 원경: 모래사장 너머로 파도가 밀려오는 바다 */
    private fun drawSea(canvas: Canvas) {
        val sw = gameView.screenWidth.toFloat()
        val horizon = Constants.GROUND_Y - 150f
        canvas.drawRect(0f, horizon - 96f, sw, horizon - 40f, seaFarPaint)
        canvas.drawRect(0f, horizon - 40f, sw, horizon + 24f, seaPaint)

        // 잔물결: 카메라를 따라 느리게 흐르며 좌우로 일렁인다
        val drift = camera.x * 0.25f
        var wx = -((drift % 190f) + 190f) % 190f
        var row = 0
        while (wx < sw + 190f) {
            for (r in 0 until 3) {
                val wy = horizon - 78f + r * 30f
                val sway = kotlin.math.sin((elapsed * 1.6 + row + r).toDouble()).toFloat() * 8f
                canvas.drawLine(wx + sway, wy, wx + 54f + sway, wy, wavePaint)
            }
            wx += 190f
            row++
        }
    }

    /** 화산 원경: 분화구에서 용암이 흘러내리고 연기가 피어오른다 */
    private fun drawVolcanoes(canvas: Canvas) {
        val baseY = Constants.GROUND_Y + 20f
        forEachBackdrop(0.3f, 720f, 380f) { i, sx, scale, far ->
            val h = 320f * scale
            val halfW = 230f * scale
            val craterHalf = halfW * 0.22f
            val topY = baseY - h

            // 연기 기둥: 분화구 바로 위에서 작게 시작해 위로 갈수록 옅게 퍼진다
            if (!far) {
                for (k in 0 until 4) {
                    val t = k / 3f
                    smokePaint.alpha = (60 * (1f - t * 0.75f)).toInt()
                    canvas.drawCircle(sx + 10f * k, topY - (26f + 46f * k) * scale,
                        (14f + 11f * k) * scale, smokePaint)
                }
            }

            // 산체: 분화구가 파인 사다리꼴
            canvas.drawPath(backdropPath.apply {
                reset()
                moveTo(sx - halfW, baseY)
                lineTo(sx - craterHalf, topY)
                lineTo(sx + craterHalf, topY)
                lineTo(sx + halfW, baseY)
                close()
            }, if (far) volcanoLitPaint else volcanoPaint)

            if (!far) drawLavaFlow(canvas, i, sx, topY, baseY, halfW, craterHalf, scale)
        }
    }

    private val lavaGlowPaint = Paint().apply { color = Color.argb(120, 255, 90, 30); isAntiAlias = true }
    private val lavaCorePaint = Paint().apply { color = Color.argb(255, 255, 214, 120); isAntiAlias = true }

    /**
     * 분화구에서 산기슭까지 부드럽게 흘러내리는 용암.
     * 중심선을 시간에 따라 일렁이게 하고, 그 위를 용암 방울이 타고 내려간다.
     */
    private fun drawLavaFlow(
        canvas: Canvas, seed: Int, sx: Float, topY: Float, baseY: Float,
        halfW: Float, craterHalf: Float, scale: Float
    ) {
        val phase = elapsed * 1.1f + seed * 1.7f
        val samples = 14

        // t(0=분화구, 1=산기슭)에서의 흐름 중심 x / 반너비
        fun flowX(t: Float): Float =
            sx - halfW * 0.42f * t * t + kotlin.math.sin(t * 4.2f + phase).toFloat() * 9f * scale * t
        fun flowY(t: Float): Float = topY + (baseY - topY) * t
        fun flowHalf(t: Float): Float = craterHalf * (0.26f + 0.34f * t)

        // 줄기: 왼쪽 가장자리를 따라 내려갔다가 오른쪽 가장자리로 되돌아오는 폴리곤
        fun buildStream(widen: Float) {
            backdropPath.reset()
            for (k in 0..samples) {
                val t = k / samples.toFloat()
                val cx = flowX(t); val w = flowHalf(t) * widen
                if (k == 0) backdropPath.moveTo(cx - w, flowY(t)) else backdropPath.lineTo(cx - w, flowY(t))
            }
            for (k in samples downTo 0) {
                val t = k / samples.toFloat()
                backdropPath.lineTo(flowX(t) + flowHalf(t) * widen, flowY(t))
            }
            backdropPath.close()
        }

        buildStream(2.4f)
        canvas.drawPath(backdropPath, lavaGlowPaint)   // 번지는 열기
        buildStream(1f)
        canvas.drawPath(backdropPath, lavaPaint)       // 용암 줄기
        buildStream(0.4f)
        canvas.drawPath(backdropPath, lavaCorePaint)   // 가장 뜨거운 심

        // 흘러내리는 용암 방울: 위상만 다르게 해서 끊임없이 이어진다
        for (k in 0 until 3) {
            val t = ((phase * 0.22f + k / 3f) % 1f)
            canvas.drawCircle(flowX(t), flowY(t), flowHalf(t) * 0.75f, lavaCorePaint)
        }

        // 분화구에 고인 용암 (천천히 부풀었다 가라앉는다)
        val pulse = 1f + 0.12f * kotlin.math.sin(elapsed * 2.0 + seed).toFloat()
        canvas.drawOval(
            sx - craterHalf * pulse, topY - 10f * pulse,
            sx + craterHalf * pulse, topY + 12f * pulse, lavaPaint
        )
        canvas.drawOval(
            sx - craterHalf * 0.55f, topY - 5f, sx + craterHalf * 0.55f, topY + 6f, lavaCorePaint
        )
    }

    /** 만화풍 잔디+흙 스트립을 세로 슬라이스로 잘라 울퉁불퉁한 지형 표면을 따라 깐다 */
    private fun drawFloor(canvas: Canvas) {
        if (isSea) { drawSeaFloor(canvas); return }   // 31 해적: 바닥이 모두 물
        val sw = gameView.screenWidth.toFloat()
        val sh = gameView.screenHeight.toFloat()
        val grassOverlap = 42f                 // 표면(잔디/눈/모래)이 지면 기준선 위로 솟는 높이
        val bmp = when (theme) {
            Theme.HONEY -> SpriteManager.honeyGround           // 벌집 바닥 + 흘러내리는 꿀
            Theme.SNOW -> SpriteManager.snowGround
            Theme.DESERT, Theme.BEACH -> SpriteManager.sandGround
            Theme.VOLCANO -> SpriteManager.ashGround
            else -> SpriteManager.groundTile
        }
        // 화면이 지면 기준선보다 낮은 기기에서도 루프가 무한 반복하지 않도록 최소 높이를 보장
        val baseStripH = (sh - (Constants.GROUND_Y - grassOverlap)).coerceAtLeast(80f)
        val tileW = baseStripH * bmp.width / bmp.height   // 가로 반복 주기 (기준 비율)

        // 비트맵을 세로 32조각으로 나눠, 각 조각을 해당 위치의 지형 높이에 맞춰 그린다
        val slices = 32
        val srcSliceW = bmp.width / slices
        val dstSliceW = tileW / slices

        var worldX = kotlin.math.floor(camera.x / dstSliceW) * dstSliceW
        val end = camera.x + sw + dstSliceW
        while (worldX < end) {
            // 낭떠러지 구간은 바닥을 아예 깔지 않는다 → 발밑이 뻥 뚫려 보인다
            val sliceMid = worldX + dstSliceW / 2f
            if (level.chasms.none { sliceMid > it.x && sliceMid < it.x + it.width }) {
                val gy = Terrain.groundY(sliceMid)
                val tileIndex = kotlin.math.floor(worldX / dstSliceW).toLong()
                val srcIdx = (((tileIndex % slices) + slices) % slices).toInt()
                floorSrcRect.set(srcIdx * srcSliceW, 0, (srcIdx + 1) * srcSliceW, bmp.height)
                floorDstRect.set(
                    worldX - camera.x, gy - grassOverlap, worldX - camera.x + dstSliceW + 1f, sh
                )
                canvas.drawBitmap(bmp, floorSrcRect, floorDstRect, null)
            }
            worldX += dstSliceW
        }

        // 빈 바닥 자리에 구멍 속(어둠·절벽 단면)을 그린다
        canvas.save()
        canvas.translate(-camera.x, 0f)
        for (chasm in level.chasms) {
            if (chasm.x + chasm.width < camera.x - 100f || chasm.x > camera.x + sw + 100f) continue
            chasm.draw(canvas, sh)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 멈추기·나가기 버튼을 먼저 확인한다.
        // 이동 패드를 누른 채로 두 번째 손가락으로 눌러도 먹히도록 ACTION_POINTER_DOWN까지 받고,
        // 좌표도 방금 내려온 손가락(actionIndex) 기준으로 읽는다.
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val idx = event.actionIndex
            val px = event.getX(idx)
            val py = event.getY(idx)
            if (hypot(px - PAUSE_BTN_X, py - MENU_BTN_Y) < MENU_HIT_R) {
                paused = !paused
                touchControls.releaseAll()   // 누르고 있던 이동·공격을 놓은 상태로 되돌린다
                return true
            }
            if (hypot(px - EXIT_BTN_X, py - MENU_BTN_Y) < MENU_HIT_R) {
                touchControls.releaseAll()
                gameView.leaveStage()   // 스테이지에서 나가 로비로 돌아간다
                return true
            }
        }
        if (paused) return true       // 멈춘 동안에는 조작이 먹지 않는다
        return touchControls.onTouchEvent(event, gameView.screenWidth, gameView.screenHeight)
    }

    /** 뒤로가기: 진행 중이면 먼저 멈추고, 멈춘 상태에서 한 번 더 누르면 로비로 나간다 */
    override fun onBack(): Boolean {
        if (!paused) {
            paused = true
            touchControls.releaseAll()
        } else {
            touchControls.releaseAll()
            gameView.leaveStage()
        }
        return true
    }

    private fun hypot(dx: Float, dy: Float) =
        kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

    /** 상단 왼쪽의 멈추기 / 나가기 버튼과 일시정지 오버레이 */
    private fun drawMenuButtons(canvas: Canvas, sw: Int, sh: Int) {
        // 멈추기
        canvas.drawCircle(PAUSE_BTN_X, MENU_BTN_Y, MENU_BTN_R, menuBtnPaint)
        canvas.drawCircle(PAUSE_BTN_X, MENU_BTN_Y, MENU_BTN_R, menuBtnEdgePaint)
        if (paused) {
            // ▶ (재개)
            val p = Path().apply {
                moveTo(PAUSE_BTN_X - 12f, MENU_BTN_Y - 18f)
                lineTo(PAUSE_BTN_X + 20f, MENU_BTN_Y)
                lineTo(PAUSE_BTN_X - 12f, MENU_BTN_Y + 18f)
                close()
            }
            canvas.drawPath(p, menuIconPaint)
        } else {
            // ▮▮ (멈추기)
            canvas.drawRect(PAUSE_BTN_X - 13f, MENU_BTN_Y - 16f,
                PAUSE_BTN_X - 5f, MENU_BTN_Y + 16f, menuIconPaint)
            canvas.drawRect(PAUSE_BTN_X + 5f, MENU_BTN_Y - 16f,
                PAUSE_BTN_X + 13f, MENU_BTN_Y + 16f, menuIconPaint)
        }
        canvas.drawText("멈추기", PAUSE_BTN_X, MENU_BTN_Y + MENU_BTN_R + 30f, menuLabelPaint)

        // 나가기
        canvas.drawCircle(EXIT_BTN_X, MENU_BTN_Y, MENU_BTN_R, exitBtnPaint)
        canvas.drawCircle(EXIT_BTN_X, MENU_BTN_Y, MENU_BTN_R, menuBtnEdgePaint)
        canvas.drawLine(EXIT_BTN_X - 14f, MENU_BTN_Y - 14f,
            EXIT_BTN_X + 14f, MENU_BTN_Y + 14f, menuIconStrokePaint)
        canvas.drawLine(EXIT_BTN_X + 14f, MENU_BTN_Y - 14f,
            EXIT_BTN_X - 14f, MENU_BTN_Y + 14f, menuIconStrokePaint)
        canvas.drawText("나가기", EXIT_BTN_X, MENU_BTN_Y + MENU_BTN_R + 30f, menuLabelPaint)
    }

    /** 멈춘 동안 화면을 덮는 막. 조작 버튼까지 가려야 하므로 맨 마지막에 그린다. */
    private fun drawPauseOverlay(canvas: Canvas, sw: Int, sh: Int) {
        if (!paused) return
        canvas.drawRect(0f, 0f, sw.toFloat(), sh.toFloat(), pauseVeilPaint)
        canvas.drawText("PAUSED", sw / 2f, sh / 2f - 6f, pauseTextPaint)
        canvas.drawText("멈추기 버튼을 다시 누르면 이어서 진행합니다",
            sw / 2f, sh / 2f + 44f, pauseHintPaint)

        // 막 위에 멈추기/나가기 버튼을 다시 올려, 멈춘 상태에서도 두 버튼이 또렷하게 보이도록
        drawMenuButtons(canvas, sw, sh)
    }
}
