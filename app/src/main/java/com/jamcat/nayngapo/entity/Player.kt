package com.jamcat.nayngapo.entity

import android.graphics.*
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.engine.Terrain

class Player(var x: Float, var y: Float) {
    val width = Constants.PLAYER_WIDTH
    val height = Constants.PLAYER_HEIGHT

    /** 로비에서 고른 인간 — 선 색·머리 장식·능력치가 여기서 온다 */
    val hero = com.jamcat.nayngapo.engine.Heroes.selected()
    /**
     * 나는 냥이가 주인공이면 우리 팀일 때처럼 떠서 다닌다 (대표님 2026-10-24 「같은 팀 벌냥이는 날아다녀」):
     * 벌냥이는 높이(150) 떠서 오르내리고, 날아냥이는 살짝(18). 맞는 자리(발밑)는 그대로 — 우리 팀 냥이와 같다.
     */
    private fun hoverLift(): Float = when {
        isFlyer -> beeLift * (1f + kotlin.math.abs(kotlin.math.sin(animTime * 4f)) * 0.08f)
        else -> when (playerCat?.kind) {
        com.jamcat.nayngapo.entity.AllyCat.Kind.BEE -> beeLift * (1f + kotlin.math.abs(kotlin.math.sin(animTime * 4f)) * 0.08f)
        com.jamcat.nayngapo.entity.AllyCat.Kind.FLYING -> com.jamcat.nayngapo.entity.AllyCat.HOVER_LIFT - kotlin.math.abs(kotlin.math.sin(animTime * 4f)) * 6f
        else -> 0f
        }
    }

    /** 31 바다처럼 날아서만 갈 수 있는 곳 — 날아냥이도 벌냥이처럼 높이 날고 '날기' 버튼으로 오르내린다 (2026-10-24) */
    var seaFlight = false
    /** 높이 나는 냥이 (벌냥이, 바다의 날아냥이) */
    val isFlyer: Boolean get() = playerCat?.kind == com.jamcat.nayngapo.entity.AllyCat.Kind.BEE ||
        (seaFlight && playerCat?.kind == com.jamcat.nayngapo.entity.AllyCat.Kind.FLYING)
    /** 처음부터 날고 있다 (바다에서 시작할 때) */
    fun startFlying() { beeFlying = true; beeLift = com.jamcat.nayngapo.entity.AllyCat.BEE_HOVER_LIFT }

    /** 벌냥이 주인공이 날고 있는가 (처음엔 땅) · 지금 떠 있는 높이 (부드럽게 오르내린다) — '날기' 버튼으로 바꾼다 */
    var beeFlying = false
        private set
    fun toggleBeeFly(): Boolean { beeFlying = !beeFlying; return beeFlying }
    /**
     * 땅의 함정(구덩이·낭떠러지·가시·늪·눈구덩이·용암 웅덩이·토네이도 함정·폭발)을 피하는 상태: 점프 중이거나,
     * 벌냥이 주인공이 날고 있을 때 (대표님 2026-10-24 「벌냥이가 날아서 함정 위를 지나가면 죽는데, 날고 있으면 죽지 않아」 — 절반 넘게 떠오르면)
     */
    val isAirborne: Boolean get() = isJumping || (beeFlying && beeLift < com.jamcat.nayngapo.entity.AllyCat.BEE_HOVER_LIFT * 0.5f)
    private var beeLift = 0f

    /** 주인공이 된 캣타워 냥이 (고양이 주인공일 때만) */
    val playerCat: com.jamcat.nayngapo.engine.CatTeam.TeamCat? =
        if (hero.id == com.jamcat.nayngapo.engine.Heroes.CAT_ID) com.jamcat.nayngapo.engine.Heroes.playerCat() else null
    private var catSpeedMul = 1f

    var hp = 5
    /** 하트 반 칸을 잃은 상태 — 새끼 고양이에게 물리면 반 칸씩 (대표님 2026-09-27). 반 칸 두 번 = 한 칸 */
    var halfLost = false
    var maxHp = 5
    var lives = Constants.MAX_LIVES
    var attackPower = 10
    /** 인간 기술 '각성' 남은 시간 — 그동안 공격력 +AWAKEN_BONUS, 방어력 +AWAKEN_BONUS (2026-10-09) */
    var awakenTimer = 0f
    /** 지금 방어력: 이 값 이하 공격력의 적 공격은 막는다 (각성 중에만 생긴다) */
    val defense: Int get() = maxOf(if (awakenTimer > 0f) AWAKEN_BONUS else 0, if (evolved) EVOLVE_POWER else 0)
    /**
     * 묭묭 진화 중인 고양이 주인공 (대표님 2026-10-24 「묭묭 진화도 자기가 쓸 수 있어」) — 출전 냥이와 같은 진화 시간을 함께 쓴다.
     * 엄청 커지고 공격력 최소 50 · 방어력 50 (공격력 50 이하인 적의 공격을 막는다).
     */
    val evolved: Boolean get() = hero.id == com.jamcat.nayngapo.engine.Heroes.CAT_ID && com.jamcat.nayngapo.engine.CatTeam.evolveSecondsLeft > 0f
    var specialPower = 20
    var specialCooldown = 0f   // 0 이하면 필살기 사용 가능
    var level = 1
    var killCount = 0

    var velocityX = 0f
    var jumpZ = 0f
    var jumpVelocity = 0f
    var isJumping = false
    /** 이 스테이지의 공중 발판 (화면이 넣어 준다). 올라서 있으면 onPlatform 에 기억한다 */
    var platforms: List<com.jamcat.nayngapo.entity.Platform> = emptyList()
    var onPlatform: com.jamcat.nayngapo.entity.Platform? = null
    var facingRight = true

    var isAttacking = false
    var attackTimer = 0f
    private var attackTotal = Constants.ATTACK_DURATION
    var isJumpAttacking = false

    /** 필살기 '내려찍기' 단계: 도약 → 체공 → 급강하 → 착지 임팩트 */
    enum class StompPhase { NONE, RISE, HANG, DROP, IMPACT }
    var stompPhase = StompPhase.NONE
        private set
    private var stompTimer = 0f
    /** 착지 순간 1회만 소비되는 충격파 이벤트 플래그 */
    private var shockwavePending = false

    // 콤보: 0=원(잽), 1=투(크로스), 2=킥(마무리). COMBO_WINDOW 안에 다시 치면 다음 단계로.
    var comboStep = 0
        private set
    private var comboResetTimer = 0f

    // 애니메이션 위상
    /** 콤보 마무리에서 던질 폭죽이 대기 중인가 (화면이 집어서 날린다) */
    var pendingFirework = false
    /** 닌자 인간이 이번 공격에 표창을 던졌다 — 화면이 한 번 받아 탄을 만든다 */
    var pendingShuriken = false

    /** 지금 공격이 '파이어 펀치'인가 — 공격이 끝나면 저절로 풀린다 */
    private var firePunch = false
    val isFirePunching: Boolean get() = firePunch && isAttacking

    /** 흐른 시간 — 바람개비 모자가 도는 데 쓴다 */
    private var animTime = 0f
    private var idlePhase = 0f    // 대기 숨쉬기
    private var walkPhase = 0f    // 걷기 사이클

    // 피격 이펙트: 실제로 적을 맞췄을 때만 짧게 표시
    private var hitSparkTimer = 0f

    // 착지 스쿼시 타이머 (짧게 웅크렸다 펴는 게임적 과장)
    private var landTimer = 0f

    /** 깊은 눈에 빠진 상태 — 느릿느릿해지고 점프할 수 없다 */
    var inDeepSnow = false
    /** 늪에 빠져 있는지 — 5배 느려지지만 점프로 빠져나올 수 있다 */
    var inSwamp = false
    /** 31 해적 바다: 배냥이 위에 올라탄 만큼 그림을 위로 (2026-10-09) */
    var rideLift = 0f
    /** 자동차에 앉아 있다 — 인간이 차에 고정되어 걷기·대기·공격 동작 없이 한 자세로 그린다 (대표님 2026-10-09) */
    var seatedInCar = false
    /** 탄 로봇의 피 (0 = 로봇 없음/부서짐) — 로봇을 타면 맞는 피해를 로봇이 대신 받는다 (2026-10-09) */
    var robotHp = 0
    var robotMaxHp = 0
    /** 로봇이 방금 부서졌다 (화면이 보고 내리게 한다) */
    var robotJustBroke = false
    /** 여왕 벌냥이 독 (대표님 2026-10-16 「한 번 때리면 독 때문에 계속 2초에 하트 1칸 없어짐」) — 목숨을 잃거나 여왕 벌냥이가 쓰러지면 풀린다(Claude 판단) */
    var poisoned = false
    private var poisonT = 0f
    /** 무기점에서 산 레이저 총 — 들고 다니고, 차를 타면 차에 단다 (2026-10-09) */
    var hasGun: Boolean = com.jamcat.nayngapo.engine.GunCatalog.equipped() != null   // 스테이지에서는 출전 준비에서 고른 대로
    /** 들고 있는 총 id (null = 총 없음) — 레이저 총 h_gun 또는 실제 총 (2026-10-25) */
    var gunKind: String? = com.jamcat.nayngapo.engine.GunCatalog.equipped()?.id
    /** 총을 꺼내 들고 있는가 — '총 넣기'로 넣으면 손에서 사라지고 주먹으로 싸운다 (2026-10-10) */
    var gunOut = true
    private val handPt = android.graphics.PointF()
    private val gunDst = RectF()
    /** 꽃가루에 숨이 막혀 느려진 남은 시간 (꽃냥이, 2026-09-27) */
    var chokeTimer = 0f
    /**
     * 얼어서 못 움직이는 남은 시간 (얼음냥이 1초 · 빙산냥이 3초, 2026-10-25) — 걷기·점프·돌진이 안 된다(공격은 된다, Claude 판단).
     * 얼어 있는 동안은 얼음이 막아 줘서 더 맞지 않는다 (Claude 판단 — 보스 옆에서 얼면 계속 맞지 않게).
     */
    var frozenTimer = 0f
        private set
    fun freeze(seconds: Float) {
        if (isDead || seconds <= 0f) return
        frozenTimer = maxOf(frozenTimer, seconds)
        velocityX = 0f; dashT = 0f
        isInvincible = true
        invincibilityTimer = maxOf(invincibilityTimer, frozenTimer + 0.3f)
    }
    /** 눈에 잠긴 깊이 (0 = 평지, 최대치면 몸이 완전히 파묻힌다) */
    private var snowSink = 0f

    /** 덩굴에 매달려 있는가 — 매달린 동안에는 중력·좌우 이동이 멈춘다 */
    var ridingVine = false
    /** 매달린 덩굴의 기울기(도) — 그릴 때 몸을 같이 기울인다 */
    var rideAngleDeg = 0f
    /** 덩굴에서 튕겨 나간 뒤 조작을 잠깐 막는 시간 (관성을 살리려고) */
    var flingTimer = 0f

    var isInvincible = false
    var invincibilityTimer = 0f

    var isDead = false
    var score = 0
    var rescueCount = 0

    init {
        // 고른 인간만큼 체력·공격력·발이 달라진다 (레벨업은 여기에 계속 얹힌다)
        maxHp += hero.hpBonus
        hp = maxHp
        attackPower = Math.round(attackPower * hero.atkMul)
        specialPower = Math.round(specialPower * hero.atkMul)
        // 인간 가게(무기점·갑옷점)에서 사 둔 물건 (2026-10-09)
        attackPower += com.jamcat.nayngapo.engine.ShopCatalog.humanAttackBonus()
        maxHp += com.jamcat.nayngapo.engine.ShopCatalog.humanHeartBonus()
        // 캣타워 냥이가 주인공이면 그 냥이가 키운 만큼 (대표님 2026-10-24 「훈련한 것도 적용된다」):
        // 공격력 = 10 + 레벨 보너스 + Lv업 아이템, 하트 = 5 + (냥이 체력 - 20) / 10 (체력 훈련·HP 수술·돌봄 포함), 빠르기 = 수술 배율 — 환산은 Claude 판단
        playerCat?.let { c ->
            val t = com.jamcat.nayngapo.engine.CatTeam
            attackPower = 10 + t.damageBonusFor(c.level) + c.attackBonus
            val catHp = t.hpBonusOf(c) + Math.round(t.maxHpFor(c.level) * t.careMultiplier(c))
            maxHp = 5 + ((catHp - 20) / 10).coerceAtLeast(0)
            catSpeedMul = t.speedMultiplier(c)
        }
        hp = maxHp
    }


    /** 이 인간의 달리기 속도 */
    /** 자동차를 타면 빨라진다 (자동차 매장에서 고른 차, 2026-10-09) */
    var carSpeedMul = 1f
    private val moveSpeed: Float get() = Constants.PLAYER_SPEED * hero.speedMul * carSpeedMul * catSpeedMul

    // 횡뷰: 발끝(y)부터 머리까지 전신 히트박스 (투사체가 몸통 높이로 날아온다)
    private val hitBoxRect = RectF()
    val hitBox: RectF get() = hitBoxRect.apply { set(x, y - 150f, x + width, y + 10f) }

    // 졸라맨 스프라이트 (GPT가 그린 20포즈 시트에서 잘라 온 프레임들)
    private val spritePaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val heroDst = RectF()

    /** 그림이 놓일 자리 — 키가 작은 인간은 발을 땅에 붙인 채로 줄어든다 */
    private fun setHeroDst(bmp: Bitmap, cx: Float, drawY: Float) {
        val sw = bmp.width * hero.scale
        val sh = bmp.height * hero.scale
        heroDst.set(cx - sw / 2f, drawY - sh + FOOT_PAD, cx + sw / 2f, drawY + FOOT_PAD)
        // 묭묭 진화한 고양이 주인공은 발끝을 그대로 둔 채 엄청 크게
        if (evolved) {
            val k = com.jamcat.nayngapo.entity.AllyCat.EVOLVE_SCALE
            heroDst.set(cx - sw * k / 2f, heroDst.bottom - sh * k, cx + sw * k / 2f, heroDst.bottom)
        }
    }
    // 착지 충격파: 매 프레임 알파/크기만 갱신해 재사용
    private val shockwavePaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
    private val shockwaveDst = RectF()
    private val impactPaint = Paint().apply {
        color = Color.rgb(255, 255, 200); isAntiAlias = true; style = Paint.Style.FILL
    }
    private val auraPaint = Paint().apply { color = Color.rgb(255, 214, 60); isAntiAlias = true }
    private val auraRect = RectF()
    private val shadowPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0); isAntiAlias = true
    }

    fun update(deltaTime: Float) {
        if (isDead) return
        // 독: 2초마다 하트 한 칸 (무적이어도 닳는다, 로봇을 타면 로봇 피가 닳는다)
        if (poisoned) {
            poisonT += deltaTime
            if (poisonT >= POISON_EVERY) {
                poisonT -= POISON_EVERY
                if (robotHp > 0) hitRobot(com.jamcat.nayngapo.engine.RobotCatalog.HP_PER_HEART)
                else {
                    hp -= 1
                    com.jamcat.nayngapo.engine.SoundManager.playPlayerHurt()
                    if (hp <= 0) { lives--; poisoned = false; if (lives <= 0) isDead = true }
                }
            }
        } else poisonT = 0f
        if (chokeTimer > 0f) chokeTimer -= deltaTime
        if (frozenTimer > 0f) { frozenTimer -= deltaTime; velocityX = 0f; dashT = 0f }
        if (awakenTimer > 0f) awakenTimer -= deltaTime
        if (dashT > 0f) dashT -= deltaTime
        if (isFlyer) {
            val target = if (beeFlying) com.jamcat.nayngapo.entity.AllyCat.BEE_HOVER_LIFT else 0f
            val step = 600f * deltaTime
            beeLift = if (kotlin.math.abs(target - beeLift) <= step) target else beeLift + kotlin.math.sign(target - beeLift) * step
        }
        if (windT > 0f) windT -= deltaTime

        // 덩굴에 매달린 동안은 위치를 덩굴이 정한다 (화면 쪽에서 매 프레임 넣어 준다)
        if (!ridingVine) {
            // 횡뷰 플랫포머: 좌우로만 이동하고 발은 항상 지형 표면 위 (수직은 jumpZ가 담당)
            val oldX = x; val oldY = y; val oldFeet = y + jumpZ
            x += (velocityX + (if (dashT > 0f) dashDir * CAT_DASH_SPEED else 0f)) * deltaTime
            x = x.coerceIn(Constants.PLAY_AREA_LEFT + 10f, Constants.PLAY_AREA_RIGHT - width - 10f)
            y = Terrain.groundY(x + width / 2f)
            // 해적선 갑판처럼 확 높은 턱은 걸어서 못 오른다 — 점프해서 발이 갑판보다 높을 때만 올라탄다 (대표님 2026-10-09)
            if (oldY - y > STEP_MAX && oldFeet > y + 2f) { x = oldX; y = oldY }
            // 높이가 확 바뀌면(갑판에 오르거나 내려오면) 발 높이를 그대로 두고 떨어지거나 착지한다
            if (kotlin.math.abs(y - oldY) > STEP_MAX && onPlatform == null && stompPhase == StompPhase.NONE) {
                jumpZ = oldFeet - y
                if (jumpZ < -1f && !isJumping) { isJumping = true; jumpVelocity = 0f }
            }

            val feetBefore = y + jumpZ
            if (stompPhase != StompPhase.NONE) {
                updateStomp(deltaTime)
            } else if (isJumping) {
                jumpVelocity += Constants.JUMP_GRAVITY * deltaTime
                jumpZ += jumpVelocity * deltaTime
                // 내려오는 중에 발이 발판 윗면을 지나면 그 위에 선다 (공중 발판, 2026-09-20)
                if (jumpVelocity > 0f) {
                    val cx = x + width / 2f
                    val feetNow = y + jumpZ
                    val landing = platforms.firstOrNull { p -> p.holds(cx) && feetBefore <= p.y + 2f && feetNow >= p.y }
                    if (landing != null) {
                        onPlatform = landing
                        jumpZ = landing.y - y; jumpVelocity = 0f; isJumping = false
                        landTimer = 0.12f
                    }
                }
                if (isJumping && jumpZ >= 0f) {
                    jumpZ = 0f; jumpVelocity = 0f; isJumping = false
                    landTimer = 0.12f   // 착지 스쿼시 연출
                }
            } else {
                val p = onPlatform
                if (p != null) {
                    val cx = x + width / 2f
                    if (p.holds(cx)) {
                        jumpZ = p.y - y                 // 발판 높이를 지형 대비 오프셋으로 유지
                    } else {
                        onPlatform = null                // 가장자리를 벗어나면 떨어진다
                        isJumping = true; jumpVelocity = 0f
                    }
                }
            }
        }

        if (flingTimer > 0f) flingTimer -= deltaTime

        if (landTimer > 0f) landTimer -= deltaTime

        if (isAttacking) {
            attackTimer -= deltaTime
            if (attackTimer <= 0f) { isAttacking = false; isJumpAttacking = false }
        }

        // 애니메이션 위상 갱신
        animTime += deltaTime
        idlePhase += deltaTime * 3f
        val moving = velocityX != 0f
        if (moving && !isJumping) {
            walkPhase += deltaTime * 13f          // 걷기 다리/팔 스윙 속도
        } else {
            walkPhase = 0f
        }

        // 눈에 빠지면 몸이 서서히 잠기고, 빠져나오면 다시 올라온다
        val sinkTarget = if (inDeepSnow) SNOW_SINK_MAX else 0f
        snowSink += (sinkTarget - snowSink).coerceIn(-420f * deltaTime, 420f * deltaTime)

        if (hitSparkTimer > 0f) hitSparkTimer -= deltaTime
        if (specialCooldown > 0f) specialCooldown -= deltaTime

        // 콤보 창: 만료되면 다음 공격은 다시 '원(잽)'부터 시작
        if (comboResetTimer > 0f) {
            comboResetTimer -= deltaTime
            if (comboResetTimer <= 0f) comboStep = 0
        }

        if (isInvincible) {
            invincibilityTimer -= deltaTime
            if (invincibilityTimer <= 0f) isInvincible = false
        }
    }

    /** 내려찍기 진행: 도약(중력) → 정점 체공 → 등속 급강하 → 착지 임팩트 */
    private fun updateStomp(deltaTime: Float) {
        when (stompPhase) {
            StompPhase.RISE -> {
                jumpVelocity += Constants.JUMP_GRAVITY * deltaTime
                jumpZ += jumpVelocity * deltaTime
                if (jumpVelocity >= 0f) {   // 정점 도달
                    jumpVelocity = 0f
                    stompPhase = StompPhase.HANG
                    stompTimer = Constants.STOMP_HANG_TIME
                }
            }
            StompPhase.HANG -> {
                stompTimer -= deltaTime
                if (stompTimer <= 0f) stompPhase = StompPhase.DROP
            }
            StompPhase.DROP -> {
                jumpZ += Constants.STOMP_DROP_SPEED * deltaTime
                if (jumpZ >= 0f) {
                    jumpZ = 0f; jumpVelocity = 0f; isJumping = false
                    stompPhase = StompPhase.IMPACT
                    stompTimer = Constants.STOMP_IMPACT_DURATION
                    shockwavePending = true   // 착지 프레임에 1회만 충격파 발생
                }
            }
            StompPhase.IMPACT -> {
                stompTimer -= deltaTime
                if (stompTimer <= 0f) {
                    stompPhase = StompPhase.NONE
                    landTimer = 0.12f
                }
            }
            StompPhase.NONE -> {}
        }
    }

    /**
     * 착지 충격파가 이번 프레임에 터졌는지 1회만 알려준다 (호출 즉시 소비).
     * GamePlayScreen이 이걸 받아 광역 피해·화면 흔들림을 적용한다.
     */
    fun consumeShockwave(): Boolean {
        if (!shockwavePending) return false
        shockwavePending = false
        return true
    }

    fun draw(canvas: Canvas) {
        if (isDead) return
        if (isInvincible && frozenTimer <= 0f && ((invincibilityTimer * 8).toInt() % 2 == 0)) return

        val drawY = y + jumpZ + snowSink - rideLift + hoverLift()   // 눈에 잠긴 만큼 아래로, 배냥이를 타면 그만큼 위로, 나는 냥이는 떠서
        val cx = x + width / 2f
        val dir = if (facingRight) 1f else -1f   // 바라보는 방향(+1 오른쪽, -1 왼쪽)

        // 덩굴에 매달렸을 때는 덩굴 기울기만큼 몸을 기울여 그린다 (그림자는 생략)
        if (ridingVine) {
            canvas.save()
            canvas.rotate(rideAngleDeg, cx, drawY - height * 0.9f)
            drawSprite(canvas, cx, drawY, dir)
            canvas.restore()
            return
        }

        // Shadow: 점프 높이에 따라 그림자를 축소·감쇄해 공중 높이감을 준다.
        val heightFrac = (-jumpZ / Constants.JUMP_MAX_HEIGHT).coerceIn(0f, 1f)
        val shadowScale = 1f - heightFrac * 0.45f
        val sw = 15f * shadowScale
        shadowPaint.alpha = (60 * (1f - heightFrac * 0.5f)).toInt()
        canvas.drawOval(RectF(cx - sw, y - 3f * shadowScale, cx + sw, y + 5f * shadowScale), shadowPaint)

        // ===== 필살기 내려찍기: 전용 스프라이트로 대체 =====
        if (stompPhase != StompPhase.NONE) {
            drawStomp(canvas, cx, drawY)
            return
        }

        if (windT > 0f) drawDashWind(canvas, cx, drawY)

        // 각성 중: 몸 둘레에 금빛 오라가 일렁인다
        if (awakenTimer > 0f) {
            val pulse = (kotlin.math.sin(awakenTimer * 12f) * 0.5f + 0.5f)
            val fade = (awakenTimer / 0.5f).coerceAtMost(1f)
            auraPaint.alpha = ((90 + 70 * pulse) * fade).toInt()
            auraRect.set(cx - 52f - pulse * 6f, drawY - height - 30f - pulse * 8f, cx + 52f + pulse * 6f, drawY + 8f)
            canvas.drawOval(auraRect, auraPaint)
        }

        // ===== 평소 동작: GPT가 그린 졸라맨 스프라이트로 그린다 =====
        drawSprite(canvas, cx, drawY, dir)
        if (frozenTimer > 0f) drawFrozen(canvas, cx, drawY)
    }

    private val frozenFill = Paint().apply { color = Color.argb(120, 170, 225, 255); isAntiAlias = true }
    private val frozenEdge = Paint().apply { color = Color.argb(220, 80, 150, 215); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
    private val frozenShine = Paint().apply { color = Color.argb(230, 255, 255, 255); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val frozenRect = RectF()
    /** 얼어 있으면 몸을 옅은 하늘색 얼음 덩어리가 감싼다 (2026-10-25) */
    private fun drawFrozen(canvas: Canvas, cx: Float, drawY: Float) {
        frozenRect.set(cx - 46f, drawY - height - 22f, cx + 46f, drawY + 8f)
        canvas.drawRoundRect(frozenRect, 18f, 18f, frozenFill)
        canvas.drawRoundRect(frozenRect, 18f, 18f, frozenEdge)
        canvas.drawLine(frozenRect.left + 12f, frozenRect.top + 16f, frozenRect.left + 30f, frozenRect.top + 40f, frozenShine)
        canvas.drawLine(frozenRect.right - 14f, frozenRect.bottom - 40f, frozenRect.right - 26f, frozenRect.bottom - 20f, frozenShine)
    }

    /** 앞손에 레이저 총 — 손잡이(그림 가로 30%·세로 70%)가 손에 오게, 바라보는 쪽으로 */
    private fun drawGunInHand(canvas: Canvas, bmp: Bitmap) {
        val spec = com.jamcat.nayngapo.engine.GunCatalog.byId(gunKind)
        val real = spec?.let { SpriteManager.realGuns[it.id] }
        val gun = real ?: SpriteManager.gunSide ?: return
        // 고양이·내가 만든 캐릭터는 방금 그린 앞손 자리에
        if (com.jamcat.nayngapo.ui.CharSkin.handles(hero)) handPt.set(com.jamcat.nayngapo.ui.CharSkin.lastHand)
        else if (!com.jamcat.nayngapo.ui.frontHand(bmp, heroDst, facingRight, handPt)) return
        var gh = heroDst.height() * 0.17f; var gw = gun.width * gh / gun.height
        if (real != null && spec != null) { gw = heroDst.height() * spec.handLen; gh = gun.height * gw / gun.width }   // 총마다 길이가 다르다 (권총 짧게, 저격총 길게)
        val g = if (facingRight) gun else SpriteManager.getFlipped(gun)
        val gripX = if (facingRight) 0.30f else 0.70f
        gunDst.set(handPt.x - gw * gripX, handPt.y - gh * 0.70f, handPt.x - gw * gripX + gw, handPt.y + gh * 0.30f)
        canvas.drawBitmap(g, null, gunDst, null)
    }

    /** 상태에 맞는 졸라맨 프레임을 골라 발끝(drawY)에 맞춰 그린다 */
    private fun drawSprite(canvas: Canvas, cx: Float, drawY: Float, dir: Float) {
        // 공격 진행 0→1 을 7프레임 연속 동작에 그대로 태운다
        val prog = 1f - (attackTimer / attackTotal).coerceIn(0f, 1f)
        fun attackFrame(frames: List<Bitmap>) =
            frames[(prog * frames.size).toInt().coerceIn(0, frames.size - 1)]

        // 타격 프레임(동작의 한가운데)에서만 피격 이펙트를 띄운다
        var strikeHigh = false
        var striking = false

        val skin = com.jamcat.nayngapo.ui.CharSkin.handles(hero)
        val pose = when {
            seatedInCar -> com.jamcat.nayngapo.ui.CharSkin.Pose.IDLE
            dashT > 0f -> com.jamcat.nayngapo.ui.CharSkin.Pose.PUNCH
            isAttacking && (isJumpAttacking || comboStep == 2) -> com.jamcat.nayngapo.ui.CharSkin.Pose.KICK
            isAttacking -> com.jamcat.nayngapo.ui.CharSkin.Pose.PUNCH
            isJumping -> if (jumpVelocity < 0f) com.jamcat.nayngapo.ui.CharSkin.Pose.JUMP_UP else com.jamcat.nayngapo.ui.CharSkin.Pose.JUMP_DOWN
            velocityX != 0f -> com.jamcat.nayngapo.ui.CharSkin.Pose.WALK
            else -> com.jamcat.nayngapo.ui.CharSkin.Pose.IDLE
        }
        val frame: Bitmap = when {
            seatedInCar -> SpriteManager.playerIdle[0]   // 차에 고정: 늘 같은 자세 (공격 판정은 그대로)
            isAttacking && isJumpAttacking -> { striking = true; attackFrame(SpriteManager.playerJumpKick) }
            isAttacking && comboStep == 2 -> { striking = true; strikeHigh = true; attackFrame(SpriteManager.playerKick) }
            isAttacking && comboStep == 1 -> { striking = true; attackFrame(SpriteManager.playerCross) }
            isAttacking -> { striking = true; attackFrame(SpriteManager.playerPunch) }
            isJumping -> {
                // 점프 7프레임: 웅크림 → 도약 → 상승 → 정점 → 하강 → 착지 직전 → 착지
                val jump = SpriteManager.playerJump
                val up = jumpVelocity < 0f
                val f = (-jumpZ / Constants.JUMP_MAX_HEIGHT).coerceIn(0f, 1f)  // 0=지면, 1=최고점
                if (up) jump[(1 + (f * 2.4f).toInt()).coerceIn(1, 3)]
                else jump[(5 - (f * 2.4f).toInt()).coerceIn(3, 5)]
            }
            landTimer > 0f -> SpriteManager.playerJump[6]          // 착지 스쿼시
            velocityX != 0f -> {
                val walk = SpriteManager.playerWalk
                walk[((walkPhase / 0.9f).toInt().coerceAtLeast(0)) % walk.size]
            }
            else -> {
                val idle = SpriteManager.playerIdle
                idle[((idlePhase / 0.8f).toInt().coerceAtLeast(0)) % idle.size]
            }
        }

        // 시트는 오른쪽을 보고 있다 → 왼쪽을 볼 때만 뒤집는다
        val bmp = if (facingRight) frame else SpriteManager.getFlipped(frame)
        setHeroDst(bmp, cx, drawY)
        com.jamcat.nayngapo.ui.drawHero(canvas, bmp, heroDst, hero, facingRight, if (skin && velocityX != 0f) walkPhase / 13f else animTime, pose, if (dashT > 0f) 0.5f else prog)
        // 총을 들고 있으면 앞손에 쥔다 (차를 타면 총은 차에 달려 있다)
        if (hasGun && gunOut && !seatedInCar) drawGunInHand(canvas, bmp)

        // 피격 이펙트: 실제로 맞췄고 타격 프레임일 때만 뻗은 팔·다리 끝에
        if (hitSparkTimer > 0f && striking && prog in 0.45f..0.80f) {
            val px = cx + dir * bmp.width * 0.42f
            val py = drawY - bmp.height * (if (strikeHigh) 0.72f else 0.60f)
            drawImpactStar(canvas, px, py, 40f)
        }
    }


    companion object {
        /** 한 프레임에 이보다 높이 바뀌는 턱은 걸어서 못 오른다 (지형 언덕은 이보다 훨씬 완만하다) */
        const val STEP_MAX = 40f
        /** 독: 하트 한 칸이 닳는 간격(초) */
        const val POISON_EVERY = 2f
        /** 각성: 공격력·방어력 증가량 (대표님 2026-10-09) · 지속 시간 10초 (Claude 판단) */
        const val AWAKEN_BONUS = 15
        /** 묭묭 진화 공격력·방어력 (냥이와 같다) */
        const val EVOLVE_POWER = 50
        /** 고양이 돌진: 공격력 10 (대표님 2026-10-24, 처음엔 3) · 시간·빠르기는 Claude 판단 */
        const val CAT_DASH_POWER = 10
        const val CAT_DASH_TIME = 0.35f
        const val CAT_DASH_SPEED = 1500f
        const val AWAKEN_TIME = 10f
        /** 눈에 완전히 파묻히는 깊이 (몸 전체가 잠긴다) */
        const val SNOW_SINK_MAX = 155f

        /** 스프라이트 아래쪽 여백만큼 살짝 내려 그려 발이 지면에 붙어 보이게 한다 */
        const val FOOT_PAD = 6f

        /** 파이어 펀치 동작 길이 — 평소 주먹(0.25초)보다 길게 해서 불꽃이 보이게 */
        const val FIRE_PUNCH_DURATION = 0.4f
    }

    /** 내려찍기 전용 스프라이트 + 착지 충격파 렌더링 (drawY = 발바닥 기준선) */
    private fun drawStomp(canvas: Canvas, cx: Float, drawY: Float) {
        // 내려찍기 7프레임: 웅크림 → 솟아오름 → 정점 → 급강하 → 직전 → 임팩트 → 일어남
        val stomp = SpriteManager.playerStomp
        val base = when (stompPhase) {
            StompPhase.RISE -> if (jumpVelocity < -500f) stomp[1] else stomp[2]
            StompPhase.HANG -> stomp[2]
            StompPhase.DROP -> if (jumpZ < -120f) stomp[3] else stomp[4]
            StompPhase.IMPACT ->
                if (stompTimer > Constants.STOMP_IMPACT_DURATION * 0.45f) stomp[5] else stomp[6]
            StompPhase.NONE -> stomp[0]
        }
        val bmp = if (facingRight) base else SpriteManager.getFlipped(base)
        // 내려찍기 프레임도 다른 동작과 같은 시트에서 나와 바닥선이 프레임 아래쪽에 맞춰져 있다
        setHeroDst(bmp, cx, drawY)
        com.jamcat.nayngapo.ui.drawHero(canvas, bmp, heroDst, hero, facingRight, animTime)

        if (stompPhase == StompPhase.IMPACT) {
            // 충격파: 시간이 지날수록 넓게 퍼지며 옅어진다
            val t = 1f - (stompTimer / Constants.STOMP_IMPACT_DURATION).coerceIn(0f, 1f)   // 0→1
            val sw = SpriteManager.shockwave
            val halfW = Constants.STOMP_SHOCKWAVE_RADIUS * (0.4f + 0.6f * t)
            val dstH = halfW * 2f * sw.height / sw.width
            // 스프라이트의 링 중심(세로 78%)이 발바닥 위치에 오도록 배치
            shockwaveDst.set(cx - halfW, drawY - dstH * 0.78f, cx + halfW, drawY + dstH * 0.22f)
            shockwavePaint.alpha = ((1f - t * t) * 255f).toInt().coerceIn(0, 255)
            canvas.drawBitmap(sw, null, shockwaveDst, shockwavePaint)
        }
    }

    /** 공격이 실제로 적에게 명중했을 때 호출 → 짧게 피격 이펙트 표시 */
    fun triggerHitSpark() { hitSparkTimer = 0.14f }

    private fun drawImpactStar(canvas: Canvas, sx: Float, sy: Float, size: Float) {
        val path = Path()
        for (i in 0 until 6) {
            val angle = Math.toRadians(i * 60.0)
            val outerX = sx + (kotlin.math.cos(angle) * size).toFloat()
            val outerY = sy + (kotlin.math.sin(angle) * size).toFloat()
            val innerAngle = Math.toRadians(i * 60.0 + 30.0)
            val innerX = sx + (kotlin.math.cos(innerAngle) * size * 0.4f).toFloat()
            val innerY = sy + (kotlin.math.sin(innerAngle) * size * 0.4f).toFloat()
            if (i == 0) path.moveTo(outerX, outerY) else path.lineTo(outerX, outerY)
            path.lineTo(innerX, innerY)
        }
        path.close()
        canvas.drawPath(path, impactPaint)
    }

    /** 횡뷰 플랫포머: 좌우 이동만 처리한다 (dirY는 무시). */
    fun setMovement(dirX: Float, dirY: Float) {
        // 덩굴에 매달려 있으면 좌우 조작이 듣지 않는다 (덩굴이 몸을 끌고 간다)
        if (ridingVine) { velocityX = 0f; return }
        // 덩굴에서 튕겨 나간 직후에는 날아가던 힘을 그대로 살린다
        if (flingTimer > 0f) return
        // 내려찍기 중에는 조작 불가 — 제자리에서 수직으로만 움직인다
        if (stompPhase != StompPhase.NONE) { velocityX = 0f; return }
        // 얼어 있으면 꼼짝 못 한다
        if (frozenTimer > 0f && !isJumping) { velocityX = 0f; return }

        if (isJumping) {
            // 공중: 이륙 관성을 유지하고, 입력이 있을 때만 약하게 조향한다.
            if (dirX != 0f) {
                val tx = kotlin.math.sign(dirX) * moveSpeed
                velocityX += (tx - velocityX) * Constants.AIR_CONTROL
                facingRight = dirX > 0f
            }
            // 입력이 없으면 현재 속도를 유지 → 공중에서 멈추지 않고 포물선을 그린다.
            return
        }

        if (dirX == 0f) {
            velocityX = 0f
            return
        }
        // 깊은 눈·늪에 빠지면 허우적거리며 아주 느리게 움직인다 (늪은 기획대로 5배 감속)
        val speed = when {
            chokeTimer > 0f -> moveSpeed * 0.4f      // 콜록콜록 — 숨이 막혀 느리다
            inDeepSnow -> moveSpeed * 0.3f
            inSwamp -> moveSpeed * 0.2f
            else -> moveSpeed
        }
        velocityX = kotlin.math.sign(dirX) * speed
        facingRight = dirX > 0f
    }

    /** 공중 발판 위에서 아래를 누르면 내려온다 (십자 패드 아래, 2026-09-20) */
    fun dropFromPlatform(): Boolean {
        if (onPlatform == null || isJumping) return false
        onPlatform = null
        isJumping = true; jumpVelocity = 0f
        return true
    }

    /** 덩굴을 잡는다 — 중력과 조작을 멈추고 덩굴에 몸을 맡긴다 */
    fun grabVine() {
        ridingVine = true
        isJumping = false
        jumpVelocity = 0f
        velocityX = 0f
        flingTimer = 0f
        isAttacking = false
        isJumpAttacking = false
        stompPhase = StompPhase.NONE
    }

    /**
     * 덩굴을 놓는다 — 흔들리던 방향으로 튕겨 나가며 위로도 한 번 솟는다.
     * @param swingVelocityX 놓는 순간 덩굴 끝이 가로로 움직이던 속도
     */
    fun releaseVine(swingVelocityX: Float) {
        ridingVine = false
        rideAngleDeg = 0f
        velocityX = swingVelocityX.coerceIn(-820f, 820f)
        if (velocityX != 0f) facingRight = velocityX > 0f
        isJumping = true
        jumpVelocity = Constants.JUMP_VELOCITY * 0.72f   // 덩굴에서 앞으로 날아가는 도약
        flingTimer = 0.45f
    }

    fun jump() {
        if (ridingVine) return          // 매달린 동안의 점프는 '놓기'라서 화면 쪽에서 처리한다
        if (inDeepSnow) return          // 눈에 발이 묶여 뛸 수 없다
        if (frozenTimer > 0f) return    // 얼어서 뛸 수 없다
        if (!isJumping && stompPhase == StompPhase.NONE) {
            onPlatform = null                                // 발판 위에서도 그대로 뛴다 (jumpZ 오프셋에서 시작)
            isJumping = true; jumpVelocity = Constants.JUMP_VELOCITY * hero.jumpMul
        }
    }

    /** 공격 발동 시 true, 이미 공격 중이라 무시되면 false 반환 */
    /** 지금 공격 동작이 얼마나 진행됐는가 (0 → 1) */
    fun attackProgress(): Float = 1f - (attackTimer / attackTotal).coerceIn(0f, 1f)

    /**
     * 인간 기술 '파이어 펀치' — 평소의 원(잽) 동작을 조금 길게 내지른다.
     * 폭죽·표창 같은 캐릭터 부가 효과나 콤보 진행은 없다.
     * 내려찍기 중이거나 덩굴에 매달려 있으면 쓸 수 없다.
     */
    fun startFirePunch(): Boolean {
        if (isDead || ridingVine || stompPhase != StompPhase.NONE) return false
        // 이미 불주먹을 내지르는 중이면 새로 시작하지 않는다 — 끝나는 즉시 다시 쓸 수 있다
        if (isFirePunching) return false
        isAttacking = true
        isJumpAttacking = false
        comboStep = 0
        comboResetTimer = 0f
        attackTimer = FIRE_PUNCH_DURATION
        attackTotal = attackTimer
        firePunch = true
        return true
    }

    /**
     * 파이어 펀치 불꽃이 따라갈 앞 주먹의 월드 좌표.
     * 그릴 때와 같은 계산(발끝 기준·몸집 배율)으로 HandTable의 손 위치를 옮긴다.
     * 앞 주먹 = 오른쪽을 볼 때 x가 더 큰 손. 펀치 중이 아니면 false.
     */
    fun frontFist(out: android.graphics.PointF): Boolean {
        if (!isFirePunching) return false
        val frames = SpriteManager.playerPunch
        val table = com.jamcat.nayngapo.ui.HandTable.PUNCH
        val idx = (attackProgress() * frames.size).toInt().coerceIn(0, minOf(frames.size, table.size) - 1)
        val bmp = frames[idx]
        val row = table[idx]
        val front = if (row[0] >= row[2]) 0 else 2
        val fx = if (facingRight) row[front] else 1f - row[front]
        val fy = row[front + 1]
        val drawY = y + jumpZ + snowSink - rideLift
        val sw = bmp.width * hero.scale
        val sh = bmp.height * hero.scale
        val left = x + width / 2f - sw / 2f
        val top = drawY - sh + FOOT_PAD
        out.set(left + sw * fx, top + sh * fy)
        return true
    }

    fun performAttack(): Boolean {
        if (!isAttacking && stompPhase == StompPhase.NONE) {
            isAttacking = true
            isJumpAttacking = isJumping
            firePunch = false
            // 파란 인간은 공격할 때마다 폭죽을 던진다 (지상·점프 모두)
            if (hero.carriesFirework) pendingFirework = true
            // 닌자 인간은 공격할 때마다 표창을 던진다
            if (hero.throwsShuriken) pendingShuriken = true
            if (isJumpAttacking) {
                // 점프 공격은 콤보와 무관
                comboStep = 0
                attackTimer = Constants.ATTACK_DURATION
            } else {
                // 지상 콤보: 창이 열려 있으면 원→투→킥 순서로 진행
                comboStep = if (comboResetTimer > 0f) (comboStep + 1) % 3 else 0
                comboResetTimer = Constants.COMBO_WINDOW
                attackTimer = if (comboStep == 2) Constants.KICK_DURATION else Constants.ATTACK_DURATION
            }
            attackTotal = attackTimer
            return true
        }
        return false
    }

    /**
     * 필살기 '내려찍기' 발동 시 true, 쿨다운 중이거나 이미 동작 중이면 false 반환.
     * 발동하면 높이 솟아올랐다가 두 발로 지면을 내려찍는다 (쿨다운 15초).
     */
    fun performSpecial(): Boolean {
        // 고양이가 주인공이면 필살기 = 앞으로 빨리 돌진해 바람을 일으킨다 (대표님 2026-10-24 「필살기는 고양이가 앞으로 빨리 돌진해서 바람이 생겨, 공격력 3」)
        if (hero.id == com.jamcat.nayngapo.engine.Heroes.CAT_ID) {
            if (specialCooldown > 0f || isAttacking || dashT > 0f) return false
            dashT = CAT_DASH_TIME; windT = CAT_DASH_TIME + 0.25f
            dashDir = if (facingRight) 1f else -1f
            dashId++
            specialCooldown = Constants.SPECIAL_COOLDOWN
            return true
        }
        if (specialCooldown > 0f || isAttacking || stompPhase != StompPhase.NONE) return false
        if (inDeepSnow) return false      // 눈에 파묻혀 있으면 솟구칠 수 없다

        stompPhase = StompPhase.RISE
        isJumping = true
        jumpVelocity = Constants.STOMP_RISE_VELOCITY
        velocityX = 0f          // 발동 후엔 제자리에서 수직으로만 움직인다
        comboStep = 0; comboResetTimer = 0f
        specialCooldown = Constants.SPECIAL_COOLDOWN   // 사용 후 15초 대기
        return true
    }

    fun isUsingSpecial(): Boolean = stompPhase != StompPhase.NONE || dashT > 0f

    // ===== 고양이 돌진 (필살기) =====
    private var dashT = 0f
    private var dashDir = 1f
    private var windT = 0f
    /** 돌진마다 새 번호 — 화면 쪽에서 한 번 돌진에 적 하나를 한 번만 때리려고 */
    var dashId = 0
        private set
    val isDashing: Boolean get() = dashT > 0f
    /** 돌진하며 바람이 닿는 곳 (몸 앞뒤로 넉넉히) */
    fun dashHitBox(): RectF = RectF(x - 60f, y - 150f, x + width + 60f, y + 30f)
    private val windPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 9f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val windInk = Paint().apply { color = Color.rgb(120, 170, 220); style = Paint.Style.STROKE; strokeWidth = 15f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val windPath = Path()

    /** 돌진 뒤로 휘날리는 바람 줄기 */
    private fun drawDashWind(canvas: Canvas, cx: Float, drawY: Float) {
        val fade = (windT / (CAT_DASH_TIME + 0.25f)).coerceIn(0f, 1f)
        windPaint.alpha = (255 * fade).toInt(); windInk.alpha = (200 * fade).toInt()
        val back = -dashDir
        for (k in 0..3) {
            val yy = drawY - 30f - k * 34f
            val x0 = cx + back * 30f
            val x1 = x0 + back * (140f + k * 30f)
            windPath.reset()
            windPath.moveTo(x0, yy)
            windPath.quadTo((x0 + x1) / 2f, yy - 22f * (if (k % 2 == 0) 1f else -1f), x1, yy)
            canvas.drawPath(windPath, windInk); canvas.drawPath(windPath, windPaint)
        }
    }

    fun getCurrentAttackPower(): Int = when {
        stompPhase != StompPhase.NONE -> specialPower
        isJumpAttacking -> (attackPower * 1.5f).toInt()
        comboStep == 2 -> (attackPower * 1.4f).toInt()   // 킥 마무리
        else -> attackPower
    }.let { (it + (if (awakenTimer > 0f) AWAKEN_BONUS else 0)).let { p -> if (evolved) maxOf(p, EVOLVE_POWER) else p } }
        .let { p -> when (playerCat?.kind) {   // 해머냥이는 2배, 모루냥이는 3배로 내려친다 (냥이일 때와 같다)
            com.jamcat.nayngapo.entity.AllyCat.Kind.HAMMER -> p * 2
            com.jamcat.nayngapo.entity.AllyCat.Kind.ANVIL -> p * 3
            else -> p
        } }   // 각성: 공격력 +15 · 묭묭 진화: 최소 50

    fun getAttackHitBox(): RectF? {
        // 내려찍기 낙하 중: 몸 주변(발밑 포함)에 닿는 적을 그대로 밟는다
        if (stompPhase == StompPhase.DROP) {
            val r = Constants.STOMP_DROP_RANGE
            val drawY = y + jumpZ
            return RectF(x - r, drawY - 120f, x + width + r, drawY + 30f)
        }
        if (!isAttacking) return null
        // 파이어 펀치는 화면 쪽에서 따로 때린다 (평소 주먹 판정까지 겹쳐 두 번 맞지 않게)
        if (isFirePunching) return null
        val range = when {
            isJumpAttacking -> 125f
            comboStep == 2 -> Constants.KICK_RANGE       // 킥 마무리 사거리
            else -> Constants.ATTACK_RANGE
        }
        // 울퉁불퉁한 지형에서 경사 위/아래 적도 맞도록 세로 판정을 넉넉하게 잡는다
        val atkHeight = 170f
        return if (facingRight) {
            RectF(x + width, y - atkHeight / 2, x + width + range, y + atkHeight / 2)
        } else {
            RectF(x - range, y - atkHeight / 2, x, y + atkHeight / 2)
        }
    }

    fun takeDamage(amount: Int) {
        // 내려찍기 중에는 슈퍼아머: 착지 임팩트까지 방해받지 않는다
        if (isInvincible || isDead || isJumping || stompPhase != StompPhase.NONE) return
        if (robotHp > 0) { hitRobot(amount * com.jamcat.nayngapo.engine.RobotCatalog.HP_PER_HEART); return }
        hp -= amount            // 잡몹은 1, 보스는 3 (기획: 보스에 맞으면 데미지 3)
        com.jamcat.nayngapo.engine.SoundManager.playPlayerHurt()   // 맞는 순간 신음
        isInvincible = true
        invincibilityTimer = Constants.INVINCIBILITY_DURATION
        velocityX = (if (facingRight) -1f else 1f) * 200f

        if (hp <= 0) { lives--; if (lives <= 0) isDead = true }
    }

    /** 하트 반 칸 피해 — 이미 반 칸을 잃었으면 그 하트가 다 닳는다 */
    fun takeHalfDamage() {
        if (isInvincible || isDead || isJumping || stompPhase != StompPhase.NONE) return
        if (robotHp > 0) { hitRobot(com.jamcat.nayngapo.engine.RobotCatalog.HP_PER_HEART / 2); return }
        if (halfLost) {
            halfLost = false
            takeDamage(1)
            return
        }
        halfLost = true
        com.jamcat.nayngapo.engine.SoundManager.playPlayerHurt()
        isInvincible = true
        invincibilityTimer = Constants.INVINCIBILITY_DURATION
        velocityX = (if (facingRight) -1f else 1f) * 200f
    }

    /** 로봇이 대신 맞는다 — 피가 다 닳으면 부서진다 */
    private fun hitRobot(dmg: Int) {
        robotHp = (robotHp - dmg).coerceAtLeast(0)
        com.jamcat.nayngapo.engine.SoundManager.playCatHit()
        isInvincible = true
        invincibilityTimer = Constants.INVINCIBILITY_DURATION
        if (robotHp <= 0) robotJustBroke = true
    }

    /** 구덩이에 빠지면 남은 목숨과 상관없이 그대로 게임 오버 */
    fun fallIntoPit() {
        hp = 0
        lives = 0
        isDead = true
        velocityX = 0f
    }

    fun needsRespawn(): Boolean = hp <= 0 && lives > 0

    fun respawn(startX: Float, startY: Float) {
        x = startX; y = startY; hp = maxHp; halfLost = false; awakenTimer = 0f; poisoned = false; frozenTimer = 0f
        velocityX = 0f; jumpZ = 0f; jumpVelocity = 0f
        isJumping = false; isAttacking = false
        stompPhase = StompPhase.NONE; stompTimer = 0f; shockwavePending = false
        comboStep = 0; comboResetTimer = 0f; walkPhase = 0f
        isInvincible = true; invincibilityTimer = Constants.INVINCIBILITY_DURATION * 2f
    }

    fun reset(startX: Float, startY: Float) {
        respawn(startX, startY)
        lives = Constants.MAX_LIVES; score = 0; rescueCount = 0; specialCooldown = 0f
        isDead = false; isInvincible = false; level = 1; killCount = 0
    }
}
