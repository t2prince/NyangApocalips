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
    val coat: com.jamcat.nayngapo.engine.CatCoat = com.jamcat.nayngapo.engine.CatCoat.CHEESE
) : Crowdable {

    enum class Kind {
        GREEN, BLUE, NORMAL,
        /** 날아냥이 — 11스테이지(하늘)에서 타고 다니려면 먼저 구해야 한다 */
        FLYING,
        /** 총냥이 — 등에 달린 총으로 보라색 총알을 쏜다 (대표님 2026-09-20 「총냥이도 자기 팀이 될 수 있어」) */
        GUN
    }

    /** 날아냥이는 땅 위에서도 살짝 떠서 다닌다 */
    private val hovers = kind == Kind.FLYING

    override val width = 80f
    override var crowdTimer = 0f
    val height = 92f

    // 아군도 무적이 아니다 — 맞으면 체력이 깎이고 쓰러진다.
    // 레벨만큼 단단해지고, 잘 돌본 냥이는 여기서 한 번 더 늘어난다.
    val maxHp = Math.round(
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
        if (isDead || hitCooldown > 0f) return
        hitCooldown = 0.35f
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
    private var hopVelocity = 0f

    /** 앞에 위험이 있을 때 호출 — 이미 떠 있으면 무시된다 */
    fun hopOverHazard() {
        if (hopOffset == 0f && hopVelocity == 0f) hopVelocity = -560f
    }
    private var jumpZ = 0f
    private var hopPhase = (Math.random() * Math.PI).toFloat()

    // 그린냥이만 슬라임을 뱉고, 파랑·일반 냥이는 달려들어 할퀸다
    private val isGunner = kind == Kind.GUN
    /** 원거리 (그린냥이 슬라임 / 총냥이 총알) */
    private val isSpitter = kind == Kind.GREEN || isGunner
    private val moveSpeed = if (isSpitter) 250f else 300f
    private val attackRange = if (isSpitter) 520f else 110f
    /** 레벨만큼 세진다 (그린냥이는 뱉는 슬라임, 나머지는 할퀴기) */
    private val damageBonus = com.jamcat.nayngapo.engine.CatTeam.damageBonusFor(level)
    private val attackDamage = (if (isGunner) 5 else if (isSpitter) 10 else 5) + damageBonus + attackBonus

    /** 달려들어 할퀴는 피해량 — 화면 쪽에서 적에게 적용한다 */
    val meleeDamage = 5 + damageBonus + attackBonus
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
    val hitBox: RectF get() = hitBoxRect.apply { set(x, y + jumpZ - height, x + width, y + 10f) }

    fun update(deltaTime: Float, playerX: Float, playerY: Float, enemies: List<Enemy>) {
        pendingMeleeTarget = null
        pendingProjectiles.clear()
        if (hitCooldown > 0f) hitCooldown -= deltaTime
        if (castTimer > 0f) castTimer -= deltaTime
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
            AllyOrder.ATTACK -> enemies.filter { !it.isDead && abs(it.x - x) < 1500f }
            // 따라와: 인간 가까이 온 적만 상대한다
            AllyOrder.FOLLOW -> enemies.filter { !it.isDead && abs(it.x - playerX) < 450f }
            // 대기: 코앞까지 온 적만
            AllyOrder.HOLD -> enemies.filter { !it.isDead && abs(it.x - x) < attackRange }
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
                attackCooldown = if (isSpitter) 1.4f else 0.7f
                if (!isSpitter) {
                    pendingMeleeTarget = target          // 달려들어 할퀸다
                } else {
                    val dir = if (facingRight) 1f else -1f
                    if (isGunner) {
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

        y = Terrain.groundY(x + width / 2f)

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

    fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    /** 모래바람 등 환경 효과로 몸이 살짝 들리는 양 (음수 = 위로) */
    var windLift = 0f

    fun draw(canvas: Canvas) {
        val cx = x + width / 2f
        // 기술을 쓰는 순간 콩 뛰어오른다 (0.3초 동안 최대 40)
        val castLift =
            if (castTimer > 0f) -40f * sin((1f - castTimer / CAST_TIME) * Math.PI.toFloat()) else 0f
        val drawY = y + jumpZ + windLift + hopOffset + castLift

        if (isDead) {
            death.draw(canvas, spriteFor(kind), facingRight, cx, y + jumpZ + windLift)
            return
        }

        val sw = width * 0.45f
        scratch.set(cx - sw, y - 5f, cx + sw, y + 7f)
        canvas.drawOval(scratch, shadowPaint)

        val base = spriteFor(kind)
        // 원안 스프라이트는 오른쪽을 본다 — 왼쪽을 볼 때만 뒤집는다 (머리 방향 = 이동 방향)
        val bmp = if (facingRight) base else SpriteManager.getFlipped(base)
        canvas.drawBitmap(bmp, cx - bmp.width / 2f, drawY - bmp.height + 8f, null)

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
            val barY = drawY - height - 30f
            canvas.drawRect(cx - barW / 2f, barY, cx + barW / 2f, barY + 7f, hpBgPaint)
            canvas.drawRect(cx - barW / 2f, barY,
                cx - barW / 2f + barW * (hp / maxHp.toFloat()), barY + 7f, hpFgPaint)
        }

        // 아군 표식: 머리 위 하트 (적과 구분)
        val my = drawY - height - 14f
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

    private companion object {
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
    }
}
