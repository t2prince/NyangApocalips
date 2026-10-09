package com.jamcat.nayngapo.entity

import android.graphics.*
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.engine.Terrain
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin

/** 팀 냥이에게 내리는 지시 (전투 중 버튼으로 바꾼다) */
enum class AllyOrder(val label: String) {
    /** 공격: 멀리까지 달려가 싸운다 */
    ATTACK("공격"),
    /** 따라와: 인간 곁을 지키며 가까이 온 적만 상대한다 */
    FOLLOW("따라와"),
    /** 대기: 제자리에서 다가온 적만 상대한다 */
    HOLD("대기"),
}

/**
 * 인간 편에 서서 함께 싸우는 냥이 (cats/ 시트의 그린냥이·파랑냥이).
 * 플레이어를 따라다니다가 가까운 적 냥이를 공격한다. 무적이 아니라 맞으면 쓰러진다.
 */
class AllyCat(
    override var x: Float,
    var y: Float,
    val kind: Kind,
    /** 팀 냥이의 레벨 — 체력과 공격력이 함께 오른다 (구출 직후의 떠돌이 냥이는 1) */
    val level: Int = 1,
    /** 팀 명단(CatTeam)에서의 id. 명단에 없는 냥이는 null */
    val teamId: Int? = null,
    /** 잘 돌본 냥이는 체력이 더 많다 (관리 화면의 돌봄 수치에서 온다) */
    val hpMultiplier: Float = 1f,
    /** 고양이 갑옷을 입고 나왔는가 — 받는 피해가 크게 줄어든다 */
    val armored: Boolean = false,
    /** 'Lv업' 아이템으로 영구히 올린 공격력 */
    val attackBonus: Int = 0,
    /** 치즈냥이(NORMAL)의 털 무늬 — 다른 종은 무시 */
    val coat: com.jamcat.nayngapo.engine.CatCoat = com.jamcat.nayngapo.engine.CatCoat.CHEESE,
    /** 동물 병원 수술로 빨라진 배율 (2026-10-09) */
    val speedMul: Float = 1f,
    /** HP 수술로 늘어난 체력 */
    val hpBonus: Int = 0,
    /** 새끼 냥이 — 적 새끼 고양이처럼 작게(0.6배) 그린다 (대표님 2026-10-09 「같은편 새끼냥이가 다른 편이랑 똑같아야」) */
    val kitten: Boolean = false
) : Crowdable {

    enum class Kind {
        GREEN, BLUE, NORMAL,
        /** 날아냥이 — 11스테이지(하늘)에서 타고 다니려면 먼저 구해야 한다 */
        FLYING,
        /** 총냥이 — 등에 달린 총으로 보라색 총알을 쏜다 (대표님 2026-09-20 「총냥이도 자기 팀이 될 수 있어」) */
        GUN,
        /** 배냥이 — 31 해적 바다에서 인간이 타고 다닌다 (30스테이지에서 구한다, 2026-10-09). 무늬별 그림 */
        BOAT,
        // ===== 모든 냥이가 우리 팀이 될 수 있다 (보스만 빼고, 구출·채집통 — 대표님 2026-10-10) =====
        // 우리 팀 그림이 따로 없어서 그 냥이(적) 그림을 그대로 쓰고, 싸우는 법은 치즈냥이처럼 가까이 가서 친다 (Claude 판단)
        HAMMER, THIEF, LASER, RED, ORANGE, YELLOW, BEE, FLOWER, MUD, HONEY, FLUFFY, ANVIL, POOP, VINE
    }

    /** 날아냥이는 땅 위에서도 살짝 떠서 다닌다 */
    private val hovers = kind == Kind.FLYING
    /** 그림 크기 배율 (새끼면 작게) */
    private val sizeMul = if (kitten) KITTEN_SCALE else 1f

    override val width = 80f
    override var crowdTimer = 0f
    val height = 92f

    // 아군도 무적이 아니다 — 맞으면 체력이 깎이고 쓰러진다.
    // 레벨만큼 단단해지고, 잘 돌본 냥이는 여기서 한 번 더 늘어난다.
    val maxHp = hpBonus + Math.round(
        com.jamcat.nayngapo.engine.CatTeam.maxHpFor(level) * hpMultiplier
    ).coerceAtLeast(1)
    var hp = maxHp
        private set
    var isDead = false
        private set
    /** 사망 모션 — 다 재생되면 전장에서 치운다 */
    val death = CatDeathAnim()
    val isGone: Boolean get() = isDead && !death.isActive
    private var hitCooldown = 0f

    /** 피해를 입는다 (짧은 피격 무적이 있어 한 공격에 한 번만 맞는다) */
    fun takeDamage(amount: Int) {
        if (inCar) return
        if (isDead || hitCooldown > 0f) return
        hitCooldown = 0.35f
        // 묭묭 진화: 방어력 50 — 받는 피해에서 50을 뺀다 (0이면 막아 낸다)
        val amount = if (evolved) (amount - EVOLVE_POWER).coerceAtLeast(0) else amount
        if (amount <= 0) return
        // 갑옷을 입었으면 피해가 크게 줄어든다 (그래도 최소 1은 들어온다)
        val taken =
            if (armored) Math.round(amount * com.jamcat.nayngapo.engine.CatTeam.ARMOR_DAMAGE_FACTOR)
                .coerceAtLeast(1)
            else amount
        hp -= taken
        if (hp <= 0) { hp = 0; isDead = true; death.start() }
    }

    var velocityX = 0f
        private set
    private var facingRight = true

    /** 바라보는 쪽 (기술이 나가는 방향) */
    val isFacingRight: Boolean get() = facingRight

    /** 기술을 쓰는 동작에 남은 시간 — 콩 뛰고 주위가 반짝인다 */
    var castTimer = 0f
        private set

    /** 고양이 기술을 쓴다 (동작만 — 효과는 화면 쪽에서 낸다) */
    fun startCast() {
        castTimer = CAST_TIME
    }

    /** 기술이 끝날 때까지 바라보는 쪽을 고정한다 (그동안 스스로 돌아서지 않는다) */
    private var faceLockTimer = 0f
    private var lockedRight = true

    fun lockFacing(right: Boolean, seconds: Float) {
        lockedRight = right
        facingRight = right
        faceLockTimer = seconds
    }

    // 함정·구덩이를 뛰어넘는 도약
    private var hopOffset = 0f
    private var climbOffset = 0f
    private var hopVelocity = 0f

    /** 앞에 위험이 있을 때 호출 — 이미 떠 있으면 무시된다 */
    fun hopOverHazard() {
        if (hopOffset == 0f && hopVelocity == 0f) hopVelocity = -560f
    }
    private var jumpZ = 0f
    private var hopPhase = (Math.random() * Math.PI).toFloat()

    // 그린냥이만 슬라임을 뱉고, 파랑·일반 냥이는 달려들어 할퀸다
    private val isGunner = kind == Kind.GUN
    /**
     * 적이었다가 우리 팀이 된 냥이도 공격은 적일 때와 같다 (대표님 2026-10-10 「같은 팀 된 고양이도 공격은 같아」):
     * 빨강 불 뿜기 · 주황 용암 던지기 · 노랑 번개 · 레이저 · 꽃가루 · 벌 침 · 똥 던지기는 멀리서, 해머·모루는 세게 내려친다(2배·3배).
     * 나머지(도둑·털뭉·머드·꿀·덩굴)는 할퀴기. 피해량은 우리 팀 공격력 기준(배율은 Claude 판단).
     */
    private val ranged = kind in setOf(Kind.RED, Kind.ORANGE, Kind.YELLOW, Kind.LASER, Kind.FLOWER, Kind.BEE, Kind.POOP)
    /** 원거리 (그린냥이 슬라임 / 총냥이 총알 / 위의 원거리 냥이들) */
    private val isSpitter = kind == Kind.GREEN || isGunner || ranged
    private val moveSpeed = (if (isSpitter) 250f else 300f) * speedMul
    private val attackRange = if (isSpitter) 520f else 110f
    /** 레벨만큼 세진다 (그린냥이는 뱉는 슬라임, 나머지는 할퀴기) */
    private val damageBonus = com.jamcat.nayngapo.engine.CatTeam.damageBonusFor(level)
    private val baseAttackDamage = (if (isGunner) 5 else if (isSpitter) 10 else 5) + damageBonus + attackBonus
    private val attackDamage: Int get() {
        val d = if (evolved) maxOf(baseAttackDamage, EVOLVE_POWER) else baseAttackDamage
        return when (kind) { Kind.HAMMER -> d * 2; Kind.ANVIL -> d * 3; else -> d }   // 해머로 쾅, 모루로 쿵
    }

    /** 달려들어 할퀴는 피해량 — 화면 쪽에서 적에게 적용한다 (묭묭 진화 중엔 최소 50) */
    private val baseMeleeDamage = 5 + damageBonus + attackBonus
    val meleeDamage: Int get() = if (evolved) maxOf(baseMeleeDamage, EVOLVE_POWER) else baseMeleeDamage

    /** 고양이 기술 '묭묭 진화' 남은 시간 (2026-10-09) — 엄청 커지고 공격력 50·방어력 50 */
    var evolveTimer = 0f
    /** 진화한 뒤 흐른 시간 (커지는 연출용) */
    private var evolveAge = 0f
    val evolved: Boolean get() = evolveTimer > 0f

    /** grown: 이미 커진 상태로 시작(스테이지·상점 마을에 들어올 때 — 커지는 연출 없이) */
    fun evolve(duration: Float, grown: Boolean = false) {
        if (isDead || duration <= 0f) return
        if (!evolved) evolveAge = if (grown) 1f else 0f
        evolveTimer = duration
    }
    private var attackCooldown = 0f

    /** 지금 받은 지시 (기본은 인간 곁을 지키는 '따라와') */
    var order: AllyOrder = AllyOrder.FOLLOW

    /** 냥이가 스스로 외치는 한마디 (화면 쪽에서 띄우고 지운다) */
    var pendingCallout: String? = null

    /** 이번 프레임에 근접 공격으로 맞힌 적 (화면 쪽에서 점수·연출 처리 후 소비) */
    var pendingMeleeTarget: Enemy? = null
        private set
    private val pendingProjectiles = mutableListOf<Projectile>()

    // 아군 표식 하트 (플레이어 하트와 같은 빨간색)
    private val markPaint = Paint().apply { color = Color.rgb(230, 60, 80); isAntiAlias = true }
    private val markStrokePaint = Paint().apply {
        color = Color.rgb(120, 20, 30); style = Paint.Style.STROKE
        strokeWidth = 2f; isAntiAlias = true
    }
    private val shadowPaint = Paint().apply { color = Color.argb(50, 0, 0, 0); isAntiAlias = true }
    private val armorPaint = Paint().apply { color = Color.rgb(200, 206, 214); isAntiAlias = true }
    private val castSparkPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val orderMarkPaint = Paint().apply {
        color = Color.rgb(40, 36, 32); strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val armorEdgePaint = Paint().apply {
        color = Color.rgb(30, 28, 26); style = Paint.Style.STROKE
        strokeWidth = 2f; isAntiAlias = true
    }
    private val hpBgPaint = Paint().apply { color = Color.argb(190, 50, 50, 50); isAntiAlias = true }
    private val hpFgPaint = Paint().apply { color = Color.rgb(110, 220, 130); isAntiAlias = true }
    private val scratch = RectF()

    private val hitBoxRect = RectF()
    val hitBox: RectF get() = hitBoxRect.apply {
        if (inCar) set(-1e6f, -1e6f, -1e6f + 1f, -1e6f + 1f)   // 차에 탄 동안은 아무것도 닿지 않는다
        else set(x, y + jumpZ - height, x + width, y + 10f)
    }

    /** 인간과 같이 자동차에 타 있다 (대표님 2026-10-09 「자동차를 타면 고양이들도 자동차에 있어」) — 걷기·싸우기·맞기·그리기를 쉰다, 차가 그린다 */
    var inCar = false

    fun update(deltaTime: Float, playerX: Float, playerY: Float, enemies: List<Enemy>) {
        pendingMeleeTarget = null
        pendingProjectiles.clear()
        if (inCar) return
        if (hitCooldown > 0f) hitCooldown -= deltaTime
        if (castTimer > 0f) castTimer -= deltaTime
        if (evolveTimer > 0f) { evolveTimer -= deltaTime; evolveAge += deltaTime }
        if (isDead) { death.update(deltaTime); return }
        if (attackCooldown > 0f) attackCooldown -= deltaTime

        // 대기 중이라도 인간이 너무 멀어지면 스스로 따라나선다 (길을 잃지 않게)
        if (order == AllyOrder.HOLD && abs(playerX - x) > 1400f) {
            order = AllyOrder.FOLLOW
            pendingCallout = "따라간다냥!"
        }

        // 지시에 따라 노리는 범위가 달라진다
        val target = when (order) {
            // 공격: 멀리 있는 적까지 찾아간다
            AllyOrder.ATTACK -> enemies.filter { !it.isDead && it.isTangible && abs(it.x - x) < 1500f }
            // 따라와: 인간 가까이 온 적만 상대한다
            AllyOrder.FOLLOW -> enemies.filter { !it.isDead && it.isTangible && abs(it.x - playerX) < 450f }
            // 대기: 코앞까지 온 적만
            AllyOrder.HOLD -> enemies.filter { !it.isDead && it.isTangible && abs(it.x - x) < attackRange }
        }.minByOrNull { abs(it.x - x) }

        val speed = if (order == AllyOrder.ATTACK) moveSpeed * 1.15f else moveSpeed

        if (target != null) {
            val dx = target.x - x
            facingRight = dx > 0
            velocityX = when {
                order == AllyOrder.HOLD -> 0f          // 제자리에서 맞이한다
                abs(dx) > attackRange * 0.7f -> sign(dx) * speed
                else -> 0f
            }

            if (attackCooldown <= 0f && abs(dx) < attackRange) {
                attackCooldown = if (isSpitter) 1.4f else if (kind == Kind.ANVIL) 1.2f else 0.7f
                if (!isSpitter) {
                    pendingMeleeTarget = target          // 달려들어 할퀸다 (해머·모루는 내려친다)
                } else {
                    val dir = if (facingRight) 1f else -1f
                    if (ranged) shootLikeEnemy(dir, target)
                    else if (isGunner) {
                        pendingProjectiles.add(          // 등의 총에서 보라색 총알
                            Projectile(x + width / 2f + dir * 34f, y - height * 0.84f, 620f * dir, 0f,
                                attackDamage, true, tint = Color.rgb(170, 70, 220), radius = 5f)
                        )
                    } else {
                        pendingProjectiles.add(          // 슬라임을 뱉는다 (아군 탄)
                            Projectile(x + width / 2f, y - height * 0.45f, 520f * dir, 0f,
                                attackDamage, true, tint = Color.rgb(140, 220, 110), radius = 13f)
                        )
                    }
                }
            }
        } else if (order == AllyOrder.HOLD) {
            velocityX = 0f                              // 대기: 움직이지 않는다
        } else {
            // 적이 없으면 플레이어 곁을 지킨다
            val standOff = if (order == AllyOrder.FOLLOW) 160f else 120f
            val dx = playerX - standOff - x
            facingRight = dx > 0
            velocityX = if (abs(dx) > 60f) sign(dx) * speed else 0f
        }

        // 기술을 쓰는 동안에는 적을 쫓아 돌아서더라도 고정한 쪽을 계속 본다
        if (faceLockTimer > 0f) {
            faceLockTimer -= deltaTime
            facingRight = lockedRight
        }

        x += velocityX * deltaTime

        // 지시별로 인간보다 얼마나 앞서 나갈 수 있는지 제한한다
        when (order) {
            AllyOrder.ATTACK -> x = x.coerceAtMost(playerX + 900f)
            AllyOrder.FOLLOW -> x = x.coerceIn(playerX - 250f, playerX + 300f)
            AllyOrder.HOLD -> Unit
        }

        val oldY = y
        y = Terrain.groundY(x + width / 2f)
        // 해적선 갑판처럼 높이가 확 바뀐 곳: 그림만 원래 높이에서 새 높이로 따라간다 (인간 따라 배에 오른다)
        if (kotlin.math.abs(y - oldY) > 40f) climbOffset += oldY - y
        if (climbOffset != 0f) {
            val step = 700f * deltaTime
            climbOffset = if (kotlin.math.abs(climbOffset) <= step) 0f else climbOffset - kotlin.math.sign(climbOffset) * step
        }

        val hoverLift = if (hovers) HOVER_LIFT else 0f
        if (abs(velocityX) > 1f) {
            hopPhase += deltaTime * 12f
            jumpZ = hoverLift - abs(sin(hopPhase.toDouble())).toFloat() * 14f
        } else {
            // 떠 있는 냥이는 멈춰도 제자리에서 오르내린다
            if (hovers) hopPhase += deltaTime * 4f
            jumpZ = hoverLift - if (hovers) abs(sin(hopPhase.toDouble())).toFloat() * 6f else 0f
        }

        // 함정을 넘는 도약
        if (hopOffset != 0f || hopVelocity != 0f) {
            hopVelocity += 1800f * deltaTime
            hopOffset += hopVelocity * deltaTime
            if (hopOffset >= 0f) { hopOffset = 0f; hopVelocity = 0f }
        }
    }

    /** 적일 때와 같은 원거리 공격 (우리 편 탄) */
    private fun shootLikeEnemy(dir: Float, target: Enemy) {
        val cx = x + width / 2f
        val mouthY = y - height * 0.5f
        val d = attackDamage
        when (kind) {
            Kind.RED -> repeat(4) { i ->      // 불을 뿜는다: 불꽃 네 덩이가 퍼지며 짧게 날아간다
                pendingProjectiles.add(Projectile(cx + dir * 30f, mouthY, dir * (360f + i * 30f), (i - 1.5f) * 50f, d, true,
                    tint = Color.rgb(255, 140, 30), radius = 12f, fuse = 0.55f, isFlame = true))
            }
            Kind.ORANGE -> pendingProjectiles.add(Projectile(cx, mouthY - 20f, dir * 430f, -560f, d, true,   // 용암을 포물선으로 던진다
                tint = Color.rgb(255, 110, 20), radius = 14f, gravity = 1100f, isLava = true))
            Kind.YELLOW -> {                    // 번개: 노린 냥이 머리 위 하늘에서 내리꽂는다
                val tx = target.x + target.width / 2f
                val top = target.y - 620f
                pendingProjectiles.add(Projectile(tx - 10f, top + 20f, 0f, 3400f, d, true,
                    tint = Color.rgb(255, 214, 40), radius = 10f, isLightning = true, boltTopY = top))
            }
            Kind.LASER -> pendingProjectiles.add(Projectile(cx, mouthY, dir * 800f, 0f, d * 2, true,   // 레이저 (세다)
                tint = Color.rgb(92, 208, 108), radius = 9f))
            Kind.FLOWER -> repeat(3) {          // 꽃가루를 뿜는다
                pendingProjectiles.add(Projectile(cx + dir * 30f, mouthY, dir * (300f + Math.random().toFloat() * 120f),
                    60f + (Math.random().toFloat() - 0.5f) * 180f, d, true, tint = Color.rgb(250, 220, 80), radius = 9f, fuse = 0.8f, isPollen = true))
            }
            Kind.BEE -> pendingProjectiles.add(Projectile(cx + dir * 30f, mouthY + 10f, dir * 720f, 0f, d, true,   // 침을 쏜다
                tint = Color.rgb(40, 34, 30), radius = 5f))
            Kind.POOP -> pendingProjectiles.add(Projectile(cx, mouthY - 20f, dir * 400f, -420f, d, true,   // 똥을 던진다
                tint = Color.rgb(130, 86, 50), radius = 12f, gravity = 1000f))
            else -> Unit
        }
    }

    fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    /** 모래바람 등 환경 효과로 몸이 살짝 들리는 양 (음수 = 위로) */
    var windLift = 0f

    /** 지금 모습 그림 (상점 마을 고르기 창에서 쓴다) */
    fun spriteNow(): android.graphics.Bitmap = spriteFor(kind)

    fun draw(canvas: Canvas) {
        if (inCar) return   // 차에 탄 냥이는 차가 그린다
        val cx = x + width / 2f
        // 기술을 쓰는 순간 콩 뛰어오른다 (0.3초 동안 최대 40)
        val castLift =
            if (castTimer > 0f) -40f * sin((1f - castTimer / CAST_TIME) * Math.PI.toFloat()) else 0f
        val drawY = y + jumpZ + windLift + hopOffset + castLift + climbOffset

        if (isDead) {
            death.draw(canvas, spriteFor(kind), facingRight, cx, y + jumpZ + windLift, (if (evolved) EVOLVE_SCALE else 1f) * sizeMul)
            return
        }

        val sw = width * 0.45f
        scratch.set(cx - sw, y - 5f, cx + sw, y + 7f)
        canvas.drawOval(scratch, shadowPaint)

        val base = spriteFor(kind)
        // 원안 스프라이트는 오른쪽을 본다 — 왼쪽을 볼 때만 뒤집는다 (머리 방향 = 이동 방향)
        val bmp = if (facingRight) base else SpriteManager.getFlipped(base)
        if (evolved) {
            // 묭묭 진화: 0.6초에 걸쳐 쑥 커지고(살짝 넘쳤다 돌아옴) 발끝을 그대로 둔 채 엄청 크게 그린다
            val g = (evolveAge / 0.6f).coerceAtMost(1f)
            val s = (1f + (EVOLVE_SCALE - 1f) * g + 0.25f * kotlin.math.sin(g * Math.PI.toFloat())) * sizeMul
            val bw = bmp.width * s; val bh = bmp.height * s
            scratch.set(cx - bw / 2f, drawY - bh + 8f * s, cx + bw / 2f, drawY + 8f * s)
            canvas.drawBitmap(bmp, null, scratch, evolvePaint)
        } else if (kitten) {
            val bw = bmp.width * KITTEN_SCALE; val bh = bmp.height * KITTEN_SCALE
            scratch.set(cx - bw / 2f, drawY - bh + 8f * KITTEN_SCALE, cx + bw / 2f, drawY + 8f * KITTEN_SCALE)
            canvas.drawBitmap(bmp, null, scratch, evolvePaint)
        } else canvas.drawBitmap(bmp, cx - bmp.width / 2f, drawY - bmp.height + 8f, null)

        // 기술을 쓰는 동안 주위에 노란 반짝임 여섯 개
        if (castTimer > 0f) {
            val p = 1f - castTimer / CAST_TIME
            castSparkPaint.alpha = (255f * (1f - p)).toInt().coerceIn(0, 255)
            for (k in 0 until 6) {
                val ang = k / 6f * 2f * Math.PI.toFloat() + p * 3f
                val rr = width * (0.55f + 0.35f * p)
                canvas.drawCircle(
                    cx + kotlin.math.cos(ang) * rr,
                    drawY - height * 0.5f + sin(ang) * rr * 0.8f,
                    5f, castSparkPaint
                )
            }
            castSparkPaint.alpha = 255
        }

        // 체력이 깎이면 머리 위에 게이지가 보인다
        if (hp < maxHp) {
            val barW = width
            val barY = drawY - height * sizeMul - 30f
            canvas.drawRect(cx - barW / 2f, barY, cx + barW / 2f, barY + 7f, hpBgPaint)
            canvas.drawRect(cx - barW / 2f, barY,
                cx - barW / 2f + barW * (hp / maxHp.toFloat()), barY + 7f, hpFgPaint)
        }

        // 아군 표식: 머리 위 하트 (적과 구분)
        val my = drawY - height * sizeMul - 14f
        val path = Path().apply {
            moveTo(cx, my + 8f)
            cubicTo(cx - 14f, my - 4f, cx - 6f, my - 14f, cx, my - 6f)
            cubicTo(cx + 6f, my - 14f, cx + 14f, my - 4f, cx, my + 8f)
            close()
        }
        canvas.drawPath(path, markPaint)
        canvas.drawPath(path, markStrokePaint)

        // 하트 옆에 지시 표시: 공격은 엇갈린 칼, 대기는 멈춤 표시, 따라와는 없음
        val ox = cx + 20f
        when (order) {
            AllyOrder.ATTACK -> {
                canvas.drawLine(ox - 6f, my - 8f, ox + 6f, my + 4f, orderMarkPaint)
                canvas.drawLine(ox + 6f, my - 8f, ox - 6f, my + 4f, orderMarkPaint)
            }
            AllyOrder.HOLD -> {
                canvas.drawLine(ox - 3f, my - 8f, ox - 3f, my + 4f, orderMarkPaint)
                canvas.drawLine(ox + 3f, my - 8f, ox + 3f, my + 4f, orderMarkPaint)
            }
            AllyOrder.FOLLOW -> Unit
        }
    }

    companion object {
        /** 새로 우리 팀이 된 냥이들의 그림 = 그 냥이(적) 그림 (해머냥이는 무늬 그림) */
        fun extraSprite(kind: Kind, coat: com.jamcat.nayngapo.engine.CatCoat = com.jamcat.nayngapo.engine.CatCoat.CHEESE): android.graphics.Bitmap? = when (kind) {
            Kind.HAMMER -> hammerSprite(coat)   // 해머냥이는 다 해머를 들고 있다 (대표님 2026-10-10)
            Kind.THIEF -> SpriteManager.thiefCat
            Kind.LASER -> SpriteManager.laserIdle.firstOrNull()
            Kind.RED -> SpriteManager.redCatEvil
            Kind.ORANGE -> SpriteManager.orangeCatEvil
            Kind.YELLOW -> SpriteManager.yellowCatEvil
            Kind.BEE -> SpriteManager.beeCat
            Kind.FLOWER -> SpriteManager.flowerCat
            Kind.MUD -> SpriteManager.mudCatEvil
            Kind.HONEY -> SpriteManager.honeyCat
            Kind.FLUFFY -> SpriteManager.fluffyCat
            Kind.ANVIL -> SpriteManager.anvilCat
            Kind.POOP -> SpriteManager.poopCat
            Kind.VINE -> SpriteManager.vineCat
            else -> null
        }

        private val hammerCache = HashMap<com.jamcat.nayngapo.engine.CatCoat, android.graphics.Bitmap>()
        /**
         * 해머를 든 해머냥이 그림 (무늬별로 한 번 만들어 둔다): 무늬 그림 + 얼굴 앞에서 앞발로 세워 든 망치(흰 앞발, 앞으로 12° 기울임).
         * 망치 모양은 적 해머냥이와 같다. 얼굴을 가리지 않게 몸 앞쪽 아래에서 쥔다. 냥이 발은 그림 아래·가운데 그대로.
         */
        fun hammerSprite(coat: com.jamcat.nayngapo.engine.CatCoat): android.graphics.Bitmap = hammerCache.getOrPut(coat) {
            val cat = coat.ally
            val padX = (cat.width * 0.32f).toInt(); val padTop = (cat.height * 0.05f).toInt()
            val out = android.graphics.Bitmap.createBitmap(cat.width + padX * 2, cat.height + padTop, android.graphics.Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawBitmap(cat, padX.toFloat(), padTop.toFloat(), null)
            val handX = padX + cat.width / 2f + cat.width * 0.47f
            val baseY = padTop + cat.height * 0.80f
            val len = cat.height * 0.62f
            val handle = android.graphics.Paint().apply { color = android.graphics.Color.rgb(150, 100, 60); isAntiAlias = true }
            val head = android.graphics.Paint().apply { color = android.graphics.Color.rgb(120, 124, 134); isAntiAlias = true }
            val edge = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(40, 40, 48); isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = 2.5f
            }
            c.save(); c.translate(handX, baseY); c.rotate(12f)
            c.drawRoundRect(-3f, -len, 3f, 0f, 3f, 3f, handle); c.drawRoundRect(-3f, -len, 3f, 0f, 3f, 3f, edge)
            val hw = cat.width * 0.30f; val hh = cat.height * 0.18f
            val r = android.graphics.RectF(-hw * 0.35f, -len - hh * 0.5f, hw * 0.65f, -len + hh * 0.5f)
            c.drawRoundRect(r, 3f, 3f, head); c.drawRoundRect(r, 3f, 3f, edge)
            c.restore()
            // 손잡이를 쥔 흰 앞발
            val paw = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; isAntiAlias = true }
            c.drawOval(handX - 7f, baseY - 6f, handX + 7f, baseY + 6f, paw)
            c.drawOval(handX - 7f, baseY - 6f, handX + 7f, baseY + 6f, edge)
            out
        }

        /** 적 냥이 → 우리 팀이 될 때의 종류 (보스는 null) */
        fun kindForEnemy(e: Enemy): Kind? = when (e) {
            is GreenCat -> Kind.GREEN
            is BlueCat -> Kind.BLUE
            is FlyingCat -> Kind.FLYING
            is GunCat -> Kind.GUN
            is BoatCat -> Kind.BOAT
            is HammerCat -> Kind.HAMMER
            is ThiefCat -> Kind.THIEF
            is LaserCat -> Kind.LASER
            is RedCat -> Kind.RED
            is OrangeCat -> Kind.ORANGE
            is YellowCat -> Kind.YELLOW
            is BeeCat -> Kind.BEE
            is FlowerCat -> Kind.FLOWER
            is HoneyCat -> Kind.HONEY
            is MudCat -> Kind.MUD
            is FluffyCat -> Kind.FLUFFY
            is AnvilCat -> Kind.ANVIL
            is PoopCat -> Kind.POOP
            is VineCat -> Kind.VINE
            else -> if (e.isBoss) null else Kind.NORMAL   // 치즈냥이·새끼 고양이 등
        }

        /** 묭묭 진화: 공격력·방어력 50, 크기 2.2배 (Claude 판단) */
        const val EVOLVE_POWER = 50
        const val EVOLVE_SCALE = 2.2f
        /** 새끼 냥이 크기 — 적 새끼 고양이(KittenCat)와 같은 0.6배 */
        const val KITTEN_SCALE = 0.6f
        private val evolvePaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
        /** 날아냥이가 땅에서 떠 있는 높이 */
        const val HOVER_LIFT = -18f

        /** 기술을 쓰는 동작 길이 */
        const val CAST_TIME = 0.3f
    }

    /** 갑옷을 입으면 그림 자체가 갑옷 입은 모습(원안 Cats/cat_armor.jpeg)으로 바뀐다 */
    private fun spriteFor(kind: Kind) = when (kind) {
        Kind.BLUE -> if (armored) SpriteManager.blueCatAllyArmor else SpriteManager.blueCatAlly     // 아군은 꼬리가 얇다
        Kind.GREEN -> if (armored) SpriteManager.greenCatAllyArmor else SpriteManager.greenCatAlly
        Kind.NORMAL -> if (armored) coat.allyArmor else coat.ally
        Kind.FLYING -> if (armored) SpriteManager.flyingCatAllyArmor else SpriteManager.flyingCatAlly   // 아군은 꼬리가 얇다
        Kind.GUN -> if (armored) SpriteManager.gunCatAllyArmor else SpriteManager.gunCatAlly
        Kind.BOAT -> coat.boatAlly
        else -> extraSprite(kind, coat) ?: coat.ally
    }
}
