package com.jamcat.nayngapo.entity

import android.graphics.*
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.engine.Terrain
import kotlin.math.sign
import kotlin.math.abs
import kotlin.math.sin

abstract class Enemy(
    override var x: Float, var y: Float, val maxHp: Int, val attackPower: Int
) : Crowdable {
    override var crowdTimer = 0f
    override val width = 50f
    open val height = 50f
    var hp = maxHp
    var velocityX = 0f
    var facingRight = false
    var isDead = false
    var jumpZ = 0f

    /** 사망 모션 (쓰러진 뒤 잠깐 재생되고 나면 화면에서 사라진다) */
    val death = CatDeathAnim()
    /** 죽었지만 아직 사망 모션이 재생 중인가 */
    val isDying: Boolean get() = isDead && death.isActive
    /** 마지막으로 그린 스프라이트 — 사망 모션에서 뒤집어 재사용한다 */
    protected var lastSprite: Bitmap? = null

    /** 채집통에 잡혔다 (대표님 2026-10-10) — 쓰러지는 모습 없이 사라지고 내 팀 냥이가 된다 */
    var captured = false
        private set
    fun capture() { if (!isDead) { isDead = true; captured = true } }

    /** 보스인가 — 보스는 채집통으로 못 잡는다 (보스 클래스는 이름이 …Boss) */
    val isBoss: Boolean get() = this::class.java.simpleName.endsWith("Boss")

    /** 체력이 다했을 때 호출: 사망 상태로 바꾸고 사망 모션을 시작한다 */
    protected fun die() {
        if (isDead) return
        isDead = true
        death.start()
    }

    fun updateDeath(deltaTime: Float) = death.update(deltaTime)

    fun drawDeath(canvas: Canvas) {
        death.draw(canvas, lastSprite ?: SpriteManager.normalCat, facingRight,
            x + width / 2f, y + jumpZ + windLift)
    }

    /** 몸에 닿았을 때 플레이어가 잃는 체력 (잡몹 1, 보스 3 — 기획4) */
    open val contactDamage = 1

    /** 닿으면 하트 반 칸만 깎는가 (새끼 고양이) */
    open val halfHeart: Boolean get() = false

    /** 순간이동 등으로 잠시 사라져 몸이 닿지 않는 상태인지 */
    open val isTangible: Boolean get() = true

    // 전신 히트박스 (부유 연출 jumpZ 포함): 경사 지형에서도 근접 공격이 빗나가지 않는다
    private val hitBoxRect = RectF()
    /** 전신 히트박스 — 매 프레임 새로 만들지 않고 하나를 갱신해 돌려준다 (호출 직후에만 유효) */
    open val hitBox: RectF get() = hitBoxRect.apply { set(x, y + jumpZ - height, x + width, y + 10f) }

    /**
     * 피격 무적 시간. 공격 판정이 여러 프레임에 걸쳐 겹쳐도 한 번의 공격은 한 번만 적중시킨다
     * (디자인 시트의 HP 수치가 의미를 갖도록).
     */
    protected var hitCooldown = 0f

    /** 애니메이션 진행 시간 (프레임 선택용) */
    protected var animTime = 0f

    open fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        if (hitCooldown > 0f) hitCooldown -= deltaTime
        animTime += deltaTime
        updateHop(deltaTime)

        // 뒤에 멀리 남겨진 냥이는 플레이어를 쫓아온다 (한 방향 진행에서 클리어 불가 방지)
        if (x + width < playerX - Constants.ENEMY_CATCHUP_DISTANCE) {
            velocityX = Constants.ENEMY_CATCHUP_SPEED
        }
        // 횡뷰 플랫포머: 좌우로만 이동, 발은 항상 지형 표면 위 (공중 연출은 jumpZ)
        x += velocityX * deltaTime
        x = x.coerceIn(Constants.PLAY_AREA_LEFT + 5f, Constants.PLAY_AREA_RIGHT - width - 5f)
        val oldY = y
        y = Terrain.groundY(x + width / 2f)
        updateClimb(oldY, deltaTime)

        // 냥이는 자기 머리(바라보는) 방향으로만 걷는다 — 이동 중에는 시선이 이동 방향을 따라간다.
        // 멈춰 있을 때만 각 냥이가 정한 시선(보통 플레이어 쪽)을 유지한다.
        if (velocityX != 0f) facingRight = velocityX > 0f
    }

    abstract fun draw(canvas: Canvas)

    open fun takeDamage(amount: Int) {
        if (isDead || hitCooldown > 0f) return
        hitCooldown = HIT_COOLDOWN
        hp -= amount
        if (hp <= 0) die()
    }

    /** 애니메이션 프레임 선택 (fps 간격으로 순환) */
    protected fun frame(frames: List<Bitmap>, fps: Float): Bitmap =
        frames[((animTime * fps).toInt()) % frames.size]

    open fun getProjectiles(): List<Projectile> = emptyList()

    /** 모래바람 등 환경 효과로 몸이 살짝 들리는 양 (음수 = 위로) */
    var windLift = 0f

    // 함정·구덩이를 뛰어넘는 도약 (연출용 오프셋)
    private var hopOffset = 0f
    /** 갑판처럼 높이가 확 바뀐 곳: 그림만 원래 높이에서 새 높이로 0.15초쯤 따라간다 */
    protected var climbOffset = 0f
    protected fun updateClimb(oldY: Float, deltaTime: Float) {
        if (abs(y - oldY) > 40f) climbOffset += oldY - y
        if (climbOffset != 0f) {
            val step = 700f * deltaTime
            climbOffset = if (abs(climbOffset) <= step) 0f else climbOffset - sign(climbOffset) * step
        }
    }
    private var hopVelocity = 0f

    /** 앞에 위험이 있을 때 호출 — 이미 떠 있으면 무시된다 */
    fun hopOverHazard() {
        if (hopOffset == 0f && hopVelocity == 0f) hopVelocity = HOP_VELOCITY
    }

    private fun updateHop(deltaTime: Float) {
        if (hopOffset == 0f && hopVelocity == 0f) return
        hopVelocity += HOP_GRAVITY * deltaTime
        hopOffset += hopVelocity * deltaTime
        if (hopOffset >= 0f) { hopOffset = 0f; hopVelocity = 0f }
    }

    protected fun drawSprite(canvas: Canvas, sprite: Bitmap) {
        val cx = x + width / 2f
        val drawY = y + jumpZ + windLift + hopOffset + climbOffset

        // Shadow (몸 크기에 비례)
        val sw = width * 0.45f
        scratchRect.set(cx - sw, y - 5f, cx + sw, y + 7f)
        canvas.drawOval(scratchRect, shadowPaint)

        // 원안 스프라이트는 오른쪽을 본다 — 왼쪽을 볼 때만 뒤집는다 (머리 방향 = 이동 방향)
        val drawBmp = if (facingRight) sprite else SpriteManager.getFlipped(sprite)
        val sx = cx - drawBmp.width / 2f
        val sy = drawY - drawBmp.height + 8f
        canvas.drawBitmap(drawBmp, sx, sy, null)
        lastSpriteHeight = drawBmp.height.toFloat()
        lastSprite = sprite
    }

    /** 방금 그린 스프라이트의 높이 — 체력 게이지를 머리 위로 띄우는 데 쓴다 */
    protected var lastSpriteHeight = 0f

    /** 적 체력 게이지: 머리 위에 몸 너비만큼 표시 */
    protected fun drawHpBar(canvas: Canvas) {
        val cx = x + width / 2f
        // 그림이 논리 높이보다 크면 그림 위로 올려 겹치지 않게 한다
        val barY = y + jumpZ - maxOf(height, lastSpriteHeight) - 16f
        val barW = width
        val barH = 7f
        val frac = (hp / maxHp.toFloat()).coerceIn(0f, 1f)
        // 체력 비율에 따라 초록 → 노랑 → 빨강
        barFgPaint.color = when {
            frac > 0.5f -> Color.rgb(90, 210, 90)
            frac > 0.25f -> Color.rgb(240, 200, 60)
            else -> Color.rgb(230, 70, 70)
        }
        canvas.drawRect(cx - barW / 2, barY, cx + barW / 2, barY + barH, barBgPaint)
        canvas.drawRect(cx - barW / 2, barY, cx - barW / 2 + barW * frac, barY + barH, barFgPaint)
        canvas.drawRect(cx - barW / 2, barY, cx + barW / 2, barY + barH, barOutlinePaint)
    }

    companion object {
        /** 한 번의 공격이 한 번만 적중하도록 하는 피격 간격(초) */
        const val HIT_COOLDOWN = 0.35f

        // 함정을 뛰어넘는 도약
        const val HOP_VELOCITY = -560f
        const val HOP_GRAVITY = 1800f

        // draw 경로에서 매 프레임 재사용하는 페인트/사각형 (게임 스레드 단일 접근)
        private val shadowPaint = Paint().apply { color = Color.argb(50, 0, 0, 0); isAntiAlias = true }
        private val scratchRect = RectF()
        private val barBgPaint = Paint().apply { color = Color.argb(190, 50, 50, 50); isAntiAlias = true }
        private val barFgPaint = Paint().apply { isAntiAlias = true }
        private val barOutlinePaint = Paint().apply {
            color = Color.argb(200, 20, 20, 20); style = Paint.Style.STROKE
            strokeWidth = 1.5f; isAntiAlias = true
        }
    }
}

// ============ Normal Cat ============
class NormalCat(x: Float, y: Float, val coat: com.jamcat.nayngapo.engine.CatCoat = com.jamcat.nayngapo.engine.CatCoat.randomForEnemy())
    : Enemy(x, y, 15, 3) {   // 시트: 냥이 HP15, 냥냥펀치 3 — 무늬(노랑이·하양이·삼색이·턱시도·고등어)만 다르다
    override val width = 80f
    override val height = 95f
    private val moveSpeed = 60f
    private var wanderTimer = 0f
    private var wanderDirX = 0f
    private var hopPhase = (Math.random() * Math.PI).toFloat()   // 개체마다 박자를 다르게
    private var attackAnim = 0f      // 0보다 크면 냥냥펀치 모션 재생 중

    init { pickNewDirection() }

    private fun pickNewDirection() {
        wanderTimer = 1f + (Math.random() * 2f).toFloat()
        wanderDirX = (if (Math.random() < 0.5) -1f else 1f) * moveSpeed   // 횡뷰: 좌우 배회만
    }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        wanderTimer -= deltaTime

        val dx = playerX - x
        if (abs(dx) < 250f) {
            velocityX = sign(dx) * moveSpeed * 1.5f   // 추격
        } else {
            if (wanderTimer <= 0f) pickNewDirection()
            velocityX = wanderDirX
        }

        // 코앞까지 붙으면 냥냥펀치 모션
        if (attackAnim > 0f) attackAnim -= deltaTime
        else if (abs(dx) < 110f) attackAnim = 0.5f

        // 이동 중엔 총총 뛰는 귀여운 홉 연출.
        // 걷기 프레임 자체에 몸이 위아래로 출렁이는 움직임이 들어 있으므로
        // 코드 홉은 살짝만 얹는다 (크게 주면 두 번 튀어 보인다)
        if (abs(velocityX) > 1f) {
            hopPhase += deltaTime * 9f
            jumpZ = -abs(kotlin.math.sin(hopPhase)) * 5f
        } else {
            jumpZ = 0f
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        // 시트(cat5)의 공격 / 점프 / 이동 / 대기 애니메이션을 상태에 따라 재생.
        // 이동 판정을 점프보다 먼저 본다 — 걸을 때의 총총 홉(jumpZ)까지 점프로 잡으면
        // 걷는 내내 걷기↔점프 프레임이 번갈아 튀어 애니메이션이 덜덜거린다.
        val frames = when {
            attackAnim > 0f -> coat.attack
            abs(velocityX) > 1f -> coat.walk
            jumpZ < -4f -> coat.jump
            else -> coat.idle
        }
        val fps = if (attackAnim > 0f) 8f else 7f
        drawSprite(canvas, frame(frames, fps))
        drawHpBar(canvas)
    }
}

// ============ Hammer Cat ============
/**
 * 해머냥이 (기획7: 피 10, 공격력 3). 얼룩 냥이가 큰 망치를 들고 다가와 내려친다.
 * 몸은 삼색이 그림을 빌려 쓰고 망치는 코드로 그린다(들어올렸다가 내려치는 회전).
 */
class HammerCat(x: Float, y: Float) : Enemy(x, y, 10, 3) {
    override val width = 80f
    override val height = 95f
    private val moveSpeed = 50f            // 망치가 무거워 치즈냥이보다 느리다
    private var wanderTimer = 0f
    private var wanderDirX = 0f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var swing = 0f                 // 0보다 크면 내려치는 중 (SWING_TIME 에서 0으로)
    /** 기본은 흑백 얼룩이(절반). 나머지는 삼색이·고등어·턱시도·하양이 (대표님 2026-09-20) */
    val coat = if (Math.random() < 0.5) com.jamcat.nayngapo.engine.CatCoat.BICOLOR
                       else HAMMER_COATS[(Math.random() * HAMMER_COATS.size).toInt().coerceIn(0, HAMMER_COATS.size - 1)]

    init { pickNewDirection() }

    private fun pickNewDirection() {
        wanderTimer = 1f + (Math.random() * 2f).toFloat()
        wanderDirX = (if (Math.random() < 0.5) -1f else 1f) * moveSpeed
    }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        wanderTimer -= deltaTime
        val dx = playerX - x
        if (abs(dx) < 260f) velocityX = sign(dx) * moveSpeed * 1.4f
        else { if (wanderTimer <= 0f) pickNewDirection(); velocityX = wanderDirX }

        if (swing > 0f) swing -= deltaTime
        else if (abs(dx) < 120f) swing = SWING_TIME    // 코앞이면 망치를 치켜들었다 내려친다

        if (abs(velocityX) > 1f) { hopPhase += deltaTime * 8f; jumpZ = -abs(kotlin.math.sin(hopPhase)) * 4f }
        else jumpZ = 0f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        val frames = when {
            swing > 0f -> coat.attack
            abs(velocityX) > 1f -> coat.walk
            else -> coat.idle
        }
        val bmp = frame(frames, 7f)
        drawSprite(canvas, bmp)
        drawHammer(canvas, bmp)
        drawHpBar(canvas)
    }

    /** 망치: 앞발 자리에서 나가는 손잡이 + 네모 머리. 내려칠 땐 뒤로 치켜들었다가 앞으로 쾅. */
    private fun drawHammer(canvas: Canvas, bmp: Bitmap) {
        val cx = x + width / 2f
        val baseY = y + jumpZ + windLift - bmp.height * 0.45f     // 앞발 높이
        val dir = if (facingRight) 1f else -1f
        val handX = cx + dir * bmp.width * 0.30f
        // 진행률 0→1: 뒤로 들었다가(−70°) 앞으로 내려친다(+35°)
        val angle = if (swing > 0f) {
            val t = 1f - swing / SWING_TIME
            if (t < 0.45f) -70f * (t / 0.45f) else -70f + 105f * ((t - 0.45f) / 0.55f)
        } else -25f
        val len = bmp.height * 0.55f
        canvas.save()
        canvas.translate(handX, baseY)
        canvas.scale(dir, 1f)
        canvas.rotate(angle)
        // 손잡이 (위로 뻗음)
        canvas.drawRoundRect(-3f, -len, 3f, 0f, 3f, 3f, handlePaint)
        canvas.drawRoundRect(-3f, -len, 3f, 0f, 3f, 3f, hammerEdgePaint)
        // 망치 머리
        val hw = bmp.width * 0.30f; val hh = bmp.height * 0.18f
        headRect.set(-hw * 0.35f, -len - hh * 0.5f, hw * 0.65f, -len + hh * 0.5f)
        canvas.drawRoundRect(headRect, 3f, 3f, headPaint)
        canvas.drawRoundRect(headRect, 3f, 3f, hammerEdgePaint)
        canvas.restore()
    }

    companion object {
        private const val SWING_TIME = 0.6f
        private val HAMMER_COATS = listOf(
            com.jamcat.nayngapo.engine.CatCoat.CALICO, com.jamcat.nayngapo.engine.CatCoat.MACKEREL,
            com.jamcat.nayngapo.engine.CatCoat.TUXEDO, com.jamcat.nayngapo.engine.CatCoat.WHITE,
        )
        private val headRect = RectF()
        private val handlePaint = Paint().apply { color = Color.rgb(150, 100, 60); isAntiAlias = true }
        private val headPaint = Paint().apply { color = Color.rgb(120, 124, 134); isAntiAlias = true }
        private val hammerEdgePaint = Paint().apply {
            color = Color.rgb(40, 40, 48); isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 2.5f
        }
    }
}

// ============ Gun Cat ============
/**
 * 총냥이 (기획8 `Plan/기획8_총냥이.jpeg`: 공격 2, 피 15). 일반(노란) 냥이 등에 총이 달려 있고 보라색 총알을 쏜다.
 * 몸은 치즈냥이 그림, 등의 총은 코드로 그린다. 탄은 사거리 안에서 2초마다 수평으로.
 */
class GunCat(x: Float, y: Float) : Enemy(x, y, 15, 2) {
    override val width = 80f
    override val height = 95f
    private val moveSpeed = 55f
    private var wanderTimer = 0f
    private var wanderDirX = 0f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var shootTimer = 1.5f
    private var recoil = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()
    private val coat = com.jamcat.nayngapo.engine.CatCoat.CHEESE

    init { pickNewDirection() }

    private fun pickNewDirection() {
        wanderTimer = 1f + (Math.random() * 2f).toFloat()
        wanderDirX = (if (Math.random() < 0.5) -1f else 1f) * moveSpeed
    }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x
        wanderTimer -= deltaTime
        val dx = playerX - x
        val dist = abs(dx)
        // 사거리 안이면 거리를 두고 서서 쏜다, 너무 가까우면 물러난다
        velocityX = when {
            dist < 180f -> -sign(dx) * moveSpeed
            dist < FIRE_RANGE -> 0f
            else -> { if (wanderTimer <= 0f) pickNewDirection(); wanderDirX }
        }
        shootTimer -= deltaTime
        if (recoil > 0f) recoil -= deltaTime
        if (shootTimer <= 0f && dist < FIRE_RANGE) {
            shootTimer = 2f; recoil = 0.25f
            val dir = if (facingRight) 1f else -1f
            pendingProjectiles.add(
                Projectile(x + width / 2f + dir * 34f, y - height * 0.92f, dir * 620f, 0f, attackPower, false,
                    tint = Color.rgb(170, 70, 220), radius = 5f)   // 보라색 총알, 등 위 총열 끝에서
            )
        }
        if (abs(velocityX) > 1f) { hopPhase += deltaTime * 9f; jumpZ = -abs(kotlin.math.sin(hopPhase)) * 5f }
        else jumpZ = 0f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        val frames = if (abs(velocityX) > 1f) coat.walk else coat.idle
        val bmp = frame(frames, 7f)
        drawSprite(canvas, bmp)
        drawGun(canvas, bmp)
        drawHpBar(canvas)
    }

    /** 등에 달린 총(대표님 2026-09-20 「일반 냥이의 등에 총이 달린 모습」): 등 위 받침 + 앞으로 뻗은 총열. 쏠 때 반동으로 살짝 들린다. */
    private fun drawGun(canvas: Canvas, bmp: Bitmap) {
        val cx = x + width / 2f
        val dir = if (facingRight) 1f else -1f
        val backX = cx - dir * bmp.width * 0.14f                   // 등(머리 반대쪽) 위
        // 등 윤곽에 딱 붙인다 — 받침이 몸 안으로 조금 들어가게 (대표님 2026-09-20 「몸과 총 사이가 붙어 있어야」)
        val backY = y + jumpZ + windLift - bmp.height * 0.78f
        val kick = if (recoil > 0f) -12f * (recoil / 0.25f) else 0f
        canvas.save()
        canvas.translate(backX, backY)
        canvas.scale(dir, 1f)
        // 받침(등에 고정)
        gunRect.set(-bmp.width * 0.09f, -2f, bmp.width * 0.09f, bmp.height * 0.11f)   // 받침: 몸 안까지 내려 틈이 없게
        canvas.drawRoundRect(gunRect, 2f, 2f, gunPaint); canvas.drawRoundRect(gunRect, 2f, 2f, gunEdgePaint)
        canvas.rotate(-GUN_ANGLE + kick)   // 총열은 머리를 안 가리게 위로 든다 (구운 아군 그림과 같은 각도)
        val bh = bmp.height * 0.10f
        gunRect.set(-bmp.width * 0.12f, -bh, bmp.width * 0.10f, 0f)            // 총 몸통
        canvas.drawRoundRect(gunRect, 3f, 3f, gunPaint); canvas.drawRoundRect(gunRect, 3f, 3f, gunEdgePaint)
        val bl = bmp.width * 0.42f
        gunRect.set(bmp.width * 0.08f, -bh * 0.8f, bmp.width * 0.08f + bl, -bh * 0.2f)   // 총열(앞으로)
        canvas.drawRoundRect(gunRect, 2f, 2f, gunPaint); canvas.drawRoundRect(gunRect, 2f, 2f, gunEdgePaint)
        if (recoil > 0.15f) canvas.drawCircle(bmp.width * 0.08f + bl + 6f, -bh * 0.5f, 6f, flashPaint)   // 총구 불꽃
        canvas.restore()
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    companion object {
        private const val FIRE_RANGE = 520f
        /** 총열을 위로 드는 각도 — scripts/make_gun_cat.py 의 ANGLE 과 같게 */
        private const val GUN_ANGLE = 25f
        private val gunRect = RectF()
        private val gunPaint = Paint().apply { color = Color.rgb(70, 72, 80); isAntiAlias = true }
        private val gunEdgePaint = Paint().apply {
            color = Color.rgb(30, 30, 36); isAntiAlias = true; style = Paint.Style.STROKE; strokeWidth = 2f
        }
        private val flashPaint = Paint().apply { color = Color.rgb(230, 120, 255); isAntiAlias = true }
    }
}

// ============ Thief Cat ============
/**
 * 도둑냥이 (대표님 2026-09-20): 도둑처럼 생겼고(눈가리개·보따리), 레이저는 쏘지 않는다.
 * 인간에게 닿으면 루비 2개를 훔쳐 달아나고, 잠시 뒤 다시 노린다. 잡으면 훔친 루비를 되찾는다.
 * 스테이지마다 한 마리뿐이다.
 */
class ThiefCat(x: Float, y: Float) : Enemy(x, y, 50, 15) {
    override val width = 85f
    override val height = 100f
    private val moveSpeed = 180f
    private val detectRange = 300f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    /** 훔쳐서 들고 있는 루비 — 쓰러지면 돌려준다 */
    var stolen = 0
        private set
    /** 훔친 직후 달아나는 시간 */
    private var fleeTimer = 0f
    /** 훔칠 수 있을 때까지 남은 시간 (연속으로 털어가지 않게) */
    private var stealCooldown = 0f

    /** 도둑은 때리지 않고 훔친다 */
    override val contactDamage = 0

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        val dx = playerX - x
        val dist = abs(dx)
        if (fleeTimer > 0f) fleeTimer -= deltaTime
        if (stealCooldown > 0f) stealCooldown -= deltaTime

        if (fleeTimer > 0f) {
            velocityX = -sign(dx) * moveSpeed * 1.3f         // 훔친 뒤엔 반대쪽으로 줄행랑
            facingRight = velocityX > 0f
        } else {
            facingRight = playerX > x
            velocityX = when {
                dist < detectRange && dist > 30f -> sign(dx) * moveSpeed
                dist <= 30f -> 0f
                else -> if (facingRight) moveSpeed * 0.3f else -moveSpeed * 0.3f
            }
        }

        // 빠르게 총총 뛰는 홉 연출
        if (abs(velocityX) > 1f) {
            hopPhase += deltaTime * 12f
            jumpZ = -abs(kotlin.math.sin(hopPhase)) * 14f
        } else {
            jumpZ = 0f
        }
        super.update(deltaTime, playerX, playerY)
    }

    /** 인간에게 닿았다 — 지금 훔칠 수 있으면 true 를 돌려주고 달아나기 시작한다 */
    fun tryStealOnTouch(): Boolean {
        if (stealCooldown > 0f || fleeTimer > 0f) return false
        stealCooldown = 4f
        fleeTimer = 2.5f
        return true
    }

    fun addStolen(n: Int) { stolen += n }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.thiefCat)
        drawHpBar(canvas)
    }

    companion object {
        /** 한 번에 훔쳐 가는 루비 */
        const val STEAL_AMOUNT = 2
    }
}

// ============ Laser Cat ============
class LaserCat(x: Float, y: Float) : Enemy(x, y, 15, 30) {   // 시트: 레이저냥이 HP15, 레이저 30
    override val width = 110f
    override val height = 100f
    private var shootTimer = 3f
    private var isCharging = false
    private var chargeTimer = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        velocityX = 0f
        facingRight = playerX > x

        // 화면에 보일 만한 거리에서만 발사 준비 (보이지 않는 곳에서 날아오는 레이저 방지)
        val playerDist = abs(playerX - x)
        shootTimer -= deltaTime
        if (shootTimer <= 0f && !isCharging && playerDist < Constants.ENEMY_FIRE_RANGE) {
            isCharging = true
            chargeTimer = 1f
        }
        if (isCharging) {
            chargeTimer -= deltaTime
            if (chargeTimer <= 0f) {
                isCharging = false
                shootTimer = 3.5f
                val dir = if (facingRight) 1f else -1f
                pendingProjectiles.add(
                    Projectile(x + width / 2f, y - height * 0.5f, 800f * dir, 0f, 30, false,
                        tint = Color.rgb(92, 208, 108), radius = 9f)
                )
            }
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        // 시트(cat4)의 레이저 냥이: 충전/발사 중에는 공격 프레임
        val frames = if (isCharging) SpriteManager.laserAttack else SpriteManager.laserIdle
        drawSprite(canvas, frame(frames, 6f))
        drawHpBar(canvas)
        if (isCharging) {
            val cx = x + width / 2f
            val barrelDir = if (facingRight) 1f else -1f
            val chargePaint = Paint().apply {
                color = Color.RED
                alpha = (150 + (sin(chargeTimer.toDouble() * 20) * 100).toInt()).coerceIn(0, 255)
                isAntiAlias = true
            }
            canvas.drawCircle(cx + 68f * barrelDir, y - height / 2f, 12f, chargePaint)
        }
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}

// ============ 나쁜 파랑냥이 (HP 20, 대미지 5) ============
/** 수북한 꼬리에 털이 부슬한 나쁜 편 파랑냥이: 빠르게 달려들어 문다 */
class BlueCat(x: Float, y: Float) : Enemy(x, y, 20, 5) {
    override val width = 92f
    override val height = 104f
    private var hopPhase = (Math.random() * Math.PI).toFloat()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        val dx = playerX - x
        velocityX = if (abs(dx) < 500f) sign(dx) * 200f else sign(dx) * 90f

        hopPhase += deltaTime * 13f
        jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 16f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.blueCatEvil)
        drawHpBar(canvas)
    }
}

// ============ 빨강·주황·노랑냥이 (파랑냥이 색만 바꾼 냥이 — 대표님 2026-09-20). 각자 공격이 다르다 ============
/**
 * 빨간냥이 — 불을 뱉는다 (대표님 2026-09-27 「빨간 냥이는 불을 뱉어」).
 * 다가오다 인간이 불 사거리 안에 들면 멈춰 서서, 입에서 불꽃 줄기를 0.8초 동안 뿜는다(3초마다).
 * 불꽃 하나하나가 적 탄이라 인간은 스치면 하트 1(무적 시간이 있어 한 번 뿜는 동안 한 번만), 아군 냥이는 불꽃마다 1.
 * 사거리 약 300·뿜는 시간·간격·불꽃 수는 Claude 판단. HP 20.
 */
class RedCat(x: Float, y: Float) : Enemy(x, y, 20, 5) {
    override val width = 92f
    override val height = 104f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var breathCooldown = 1.5f
    /** 뿜는 중이면 남은 시간 */
    private var breathing = 0f
    private var puffTimer = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        val dx = playerX - x
        if (breathing <= 0f) facingRight = playerX > x      // 뿜는 동안은 방향 고정
        val dist = abs(dx)
        breathCooldown -= deltaTime
        if (breathing <= 0f && breathCooldown <= 0f && dist < BREATH_RANGE) {
            breathing = BREATH_TIME; breathCooldown = BREATH_EVERY; puffTimer = 0f
        }
        if (breathing > 0f) {
            breathing -= deltaTime
            velocityX = 0f
            jumpZ = 0f
            puffTimer -= deltaTime
            if (puffTimer <= 0f) {
                puffTimer = PUFF_EVERY
                val dir = if (facingRight) 1f else -1f
                val mouthX = x + width / 2f + dir * width * 0.45f
                val mouthY = y - height * 0.45f
                val spread = (Math.random().toFloat() - 0.5f) * 120f
                val r = 13f
                pendingProjectiles.add(
                    Projectile(mouthX - r, mouthY - r, dir * FLAME_SPEED, spread, 1, false,
                        tint = Color.rgb(255, 140, 30), radius = r, fuse = FLAME_LIFE, isFlame = true)
                )
            }
        } else {
            velocityX = when {
                dist < BREATH_RANGE * 0.6f -> 0f                  // 충분히 가까우면 멈춰 불을 기다린다
                dist < 500f -> sign(dx) * 200f
                else -> sign(dx) * 90f
            }
            if (abs(velocityX) > 1f) { hopPhase += deltaTime * 13f; jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 16f }
            else jumpZ = 0f
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.redCatEvil)
        if (breathing > 0f) {
            // 입가에 불씨가 이글거린다
            val dir = if (facingRight) 1f else -1f
            val mx = x + width / 2f + dir * width * 0.45f
            val my = y + jumpZ - height * 0.45f
            emberPaint.alpha = (180 + 70 * sin(breathing * 50.0)).toInt().coerceIn(0, 255)
            canvas.drawCircle(mx, my, 14f, emberPaint)
        }
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    companion object {
        private const val BREATH_RANGE = 300f
        private const val BREATH_TIME = 0.8f
        private const val BREATH_EVERY = 3f
        private const val PUFF_EVERY = 0.08f
        private const val FLAME_SPEED = 480f
        /** 불꽃이 살아 있는 시간 — 480 × 0.55 ≈ 260px 날아가 식는다 */
        private const val FLAME_LIFE = 0.55f
        private val emberPaint = Paint().apply { color = Color.rgb(255, 170, 40); isAntiAlias = true }
    }
}
/**
 * 주황냥이 — 용암으로 공격한다 (대표님 2026-09-27 「주황 냥이는 용암으로 공격해」).
 * 거리를 두고 2.2초마다 용암 덩이를 인간 쪽으로 포물선으로 던진다. 덩이에 맞으면 하트 1(적 탄 규칙),
 * 땅에 떨어지면 2.5초 동안 끓는 용암 웅덩이가 남아 밟으면 하트 1 (웅덩이는 GamePlayScreen 이 관리).
 * 사거리 480·간격·비행 시간·웅덩이 시간은 Claude 판단. HP 20.
 */
class OrangeCat(x: Float, y: Float) : Enemy(x, y, 20, 5) {
    override val width = 92f
    override val height = 104f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var throwTimer = 1.2f
    private var windup = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x
        val dx = playerX - x
        val dist = abs(dx)
        velocityX = when {
            dist < 220f -> -sign(dx) * 150f          // 너무 가까우면 물러난다
            dist < THROW_RANGE -> 0f                  // 사거리 안: 서서 던진다
            else -> sign(dx) * 160f
        }
        throwTimer -= deltaTime
        if (windup > 0f) windup -= deltaTime
        if (throwTimer <= 0f && dist < THROW_RANGE) {
            throwTimer = THROW_EVERY; windup = 0.25f
            // 인간 발밑에 떨어지게 포물선 속도를 거꾸로 계산한다
            val r = 16f
            val sx = x + width / 2f + (if (facingRight) 1f else -1f) * width * 0.4f
            val sy = y - height * 0.7f
            val tx = playerX + Constants.PLAYER_WIDTH / 2f
            val ty = playerY - 20f
            val t = FLIGHT_TIME
            val vx = (tx - sx) / t
            val vy = (ty - sy) / t - GRAVITY * t / 2f
            pendingProjectiles.add(
                Projectile(sx - r, sy - r, vx, vy, 1, false, tint = Color.rgb(255, 110, 20), radius = r,
                    gravity = GRAVITY, isLava = true)
            )
        }
        if (abs(velocityX) > 1f) { hopPhase += deltaTime * 13f; jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 16f }
        else jumpZ = 0f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.orangeCatEvil)
        if (windup > 0f) {
            // 던지는 순간 입가에 용암이 부글
            val dir = if (facingRight) 1f else -1f
            glow.alpha = (220 * windup / 0.25f).toInt().coerceIn(0, 255)
            canvas.drawCircle(x + width / 2f + dir * width * 0.4f, y + jumpZ - height * 0.7f, 18f, glow)
        }
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    companion object {
        private const val THROW_RANGE = 480f
        private const val THROW_EVERY = 2.2f
        private const val FLIGHT_TIME = 0.75f
        private const val GRAVITY = 1500f
        private val glow = Paint().apply { color = Color.rgb(255, 140, 30); isAntiAlias = true }
    }
}
/**
 * 노랑냥이 — 번개를 부른다 (대표님 2026-09-24). 번개는 **먹구름에서 떨어진다** (대표님 2026-09-25).
 * 2026-09-27 그림(「이게 노랑냥이 공격이야」): 부르면 **하늘 위쪽 전체가 먹구름으로 덮이고** 거기서 번개가 인간에게 내리꽂힌다.
 * 먹구름 띠는 화면이 그린다(GamePlayScreen.drawStormSky — storm 세기·stormTargets 를 읽는다).
 * 거리를 두고 2.5초마다 인간 자리를 노리고, 구름이 모이면(그 자리 구름 속이 번쩍인다) 번개가 곧장 내리꽂힌다.
 * 노랑냥이가 쓰러지면 떠 있던 먹구름도 사라진다. HP 20 · 공격(번개 대미지) 2
 */
class YellowCat(x: Float, y: Float) : Enemy(x, y, 20, 2) {
    override val width = 92f
    override val height = 104f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var shootTimer = 0.8f
    private var flash = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()

    /** 먹구름 하나: 자리 + 생긴 뒤 흐른 시간. 모이는 동안 비켜 설 틈이 있다 */
    private class StormCloud(val x: Float, val y: Float) {
        var t = 0f
        var struck = false
    }

    private val clouds = mutableListOf<StormCloud>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x
        val dx = playerX - x
        val dist = abs(dx)
        velocityX = when {
            dist < 200f -> -sign(dx) * 150f          // 너무 가까우면 물러난다
            dist < FIRE_RANGE -> 0f                   // 사거리 안: 서서 부른다
            else -> sign(dx) * 160f
        }
        shootTimer -= deltaTime
        if (flash > 0f) flash -= deltaTime
        if (shootTimer <= 0f && dist < FIRE_RANGE) {
            shootTimer = SHOOT_EVERY; flash = 0.2f
            // 부르는 순간 하늘이 먹구름으로 덮이고, 인간이 선 자리를 노린다
            clouds.add(StormCloud(playerX + Constants.PLAYER_WIDTH / 2f, skyBottomY))
        }

        val it = clouds.iterator()
        while (it.hasNext()) {
            val c = it.next()
            c.t += deltaTime
            if (!c.struck && c.t >= GATHER_TIME) {
                // 구름이 다 모였다 — 구름 밑에서 번개가 곧장 아래로 내리꽂힌다
                c.struck = true
                val r = 8f
                pendingProjectiles.add(
                    Projectile(c.x - r, skyBottomY + 20f, 0f, BOLT_SPEED, attackPower, false,
                        tint = Color.rgb(255, 214, 40), radius = r, isLightning = true, boltTopY = skyBottomY - 20f)
                )
            }
            if (c.t >= GATHER_TIME + LINGER_TIME) it.remove()
        }

        if (abs(velocityX) > 1f) { hopPhase += deltaTime * 13f; jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 16f }
        else jumpZ = 0f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.yellowCatEvil)
        if (flash > 0f) {                              // 부르는 순간 몸 주위 번쩍
            val cx = x + width / 2f; val cy = y + jumpZ - height * 0.5f
            sparkPaint.alpha = (200 * flash / 0.2f).toInt().coerceIn(0, 255)
            for (k in 0 until 6) {
                val a = k * 60.0 + flash * 400.0
                canvas.drawLine(cx, cy, cx + (60 * Math.cos(Math.toRadians(a))).toFloat(), cy + (60 * Math.sin(Math.toRadians(a))).toFloat(), sparkPaint)
            }
        }
        drawHpBar(canvas)
    }

    /** 하늘 먹구름 세기 0~1 — 부르면 빠르게 짙어지고 번개를 친 뒤 흩어진다. 쓰러지면 0 */
    val storm: Float
        get() {
            if (isDead) return 0f
            var m = 0f
            for (c in clouds) {
                val v = if (c.struck) 1f - (c.t - GATHER_TIME) / LINGER_TIME else (c.t / 0.2f)
                m = maxOf(m, v.coerceIn(0f, 1f))
            }
            return m
        }

    /** 번개가 떨어질 자리들: (월드 x, 모인 정도 0~1 — 1 이면 이미 쳤음) */
    val stormTargets: List<Pair<Float, Float>>
        get() = if (isDead) emptyList() else clouds.map { it.x to (it.t / GATHER_TIME).coerceAtMost(1f) }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    companion object {
        private const val FIRE_RANGE = 560f
        /** 번개를 부르는 간격 — 「공격이 너무 느려」(대표님 2026-09-27)로 2.5초 → 1.5초 */
        private const val SHOOT_EVERY = 1.5f
        /** 먹구름이 모여 번개가 떨어지기까지 — 이 사이에 비켜 서면 피한다 (Claude 판단) */
        private const val GATHER_TIME = 0.5f
        /** 번개를 친 뒤 구름이 흩어지는 시간 */
        private const val LINGER_TIME = 0.4f
        /** 하늘 먹구름 띠의 아랫자락 (월드 y) — 화면이 카메라에 맞춰 매 프레임 알려 준다 */
        var skyBottomY = 240f
        private val sparkPaint = Paint().apply { color = Color.rgb(255, 240, 120); strokeWidth = 4f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
        private const val BOLT_SPEED = 3400f   // 하늘에서 땅까지 약 0.25초
    }
}

// ============ 꿀 스테이지(21) — 기획10 `Plan/기획10_꿀스테이지_벌냥이_꽃냥이.jpg` (대표님 2026-09-27) ============
/**
 * 벌냥이 — HP 3. 노랑 몸에 둥근 검은 띠, 하늘색 날개 한 쌍, 엉덩이 침, 꼬리 없음. 공중에 떠 다닌다(벌집 구조물은 2026-10-04 삭제):
 * 인간이 다가오면 머리 위로 날아와 떠 있다가, 뒤로 돌아 **엉덩이 침으로 내려와 찌른다**(닿으면 하트 1).
 * 떠 있는 동안은 높이 있어 닿지 않고, 찌르러 내려왔을 때 때릴 수 있다. 공격력(아군 피해)은 기획서 판독 중 — 임시 10.
 * 쫓아오는 거리·높이·찌르기 간격은 Claude 판단.
 */
class BeeCat(x: Float, y: Float) : Enemy(x, y, 3, 10) {
    override val width = 84f
    override val height = 80f
    private enum class State { FLY, DIVE }
    private var state = State.FLY
    private var stingT = 0f
    private var cooldown = 0.8f
    private var bob = (Math.random() * 6.0).toFloat()
    private val box = RectF()

    init { jumpZ = HOVER_Z }

    /** 떠 있는 동안은 높이 있어 닿지 않는다 — 찌르러 내려왔을 때만 때리고 맞는다 */
    override val isTangible: Boolean get() = state == State.DIVE || jumpZ > HOVER_Z * 0.6f
    override val hitBox: RectF get() = box.apply { set(x, y + jumpZ - height, x + width, y + jumpZ + 6f) }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        if (hitCooldown > 0f) hitCooldown -= deltaTime
        animTime += deltaTime
        bob += deltaTime * 4f
        y = com.jamcat.nayngapo.engine.Terrain.groundY(x + width / 2f)
        val pcx = playerX + Constants.PLAYER_WIDTH / 2f
        val cx = x + width / 2f
        when (state) {
            State.FLY -> {
                // 공중에 떠서 파닥이다가, 인간이 가까이 오면 머리 위로 날아간다
                jumpZ += (HOVER_Z + sin(bob) * 14f - jumpZ) * (deltaTime * 3f).coerceAtMost(1f)
                val dx = pcx - cx
                facingRight = dx > 0f
                val chase = abs(dx) < CHASE_RANGE
                x += sign(dx) * (if (chase && abs(dx) > 60f) FLY_SPEED else 0f) * deltaTime
                cooldown -= deltaTime
                if (cooldown <= 0f && abs(dx) < 110f && jumpZ < HOVER_Z * 0.7f) { state = State.DIVE; stingT = 0f }
            }
            State.DIVE -> {
                // 뒤로 돌아 엉덩이 침을 인간에게 — 내려왔다가 다시 떠오른다
                stingT += deltaTime
                facingRight = pcx < cx            // 엉덩이(침)가 인간 쪽
                jumpZ = when {
                    stingT < 0.35f -> HOVER_Z + (STING_Z - HOVER_Z) * (stingT / 0.35f)
                    stingT < 0.6f -> STING_Z
                    else -> STING_Z + (HOVER_Z - STING_Z) * ((stingT - 0.6f) / 0.4f).coerceAtMost(1f)
                }
                if (stingT >= 1.0f) { state = State.FLY; cooldown = STING_EVERY }
            }
        }
        x = x.coerceIn(Constants.PLAY_AREA_LEFT + 5f, Constants.PLAY_AREA_RIGHT - width - 5f)
    }

    override fun takeDamage(amount: Int) {
        if (isTangible) super.takeDamage(amount)
    }

    override fun draw(canvas: Canvas) {
        // 날개가 파닥이게: 그림을 살짝 위아래로 떨게 한다 (날개는 그림 속 기획서 모양 그대로)
        val flapLift = sin(animTime * 40f) * 2f
        jumpZ += flapLift
        drawSprite(canvas, SpriteManager.beeCat)
        jumpZ -= flapLift
        drawHpBar(canvas)
    }

    companion object {
        /** 인간이 이 안에 오면 쫓아온다 (Claude 판단) */
        private const val CHASE_RANGE = 800f
        private const val HOVER_Z = -210f
        private const val STING_Z = -70f
        private const val FLY_SPEED = 230f
        private const val STING_EVERY = 1.6f
    }
}

/**
 * 꽃냥이 — HP 15 · 공격 10. 주황 줄무늬 노란 몸, 얼굴에 초록 잎, 머리에서 분홍 꽃대 두 개.
 * 거리를 두고 서서 **꽃에서 꽃가루를 뿜고**, 꽃가루를 맞은 인간은 **숨이 막힌다**(하트 1 + 잠깐 느려짐).
 * 사거리 320·3초마다 0.9초 뿜기·느려짐 1.5초는 Claude 판단.
 */
class FlowerCat(x: Float, y: Float) : Enemy(x, y, 15, 10) {
    override val width = 92f
    override val height = 104f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var sprayCooldown = 1.5f
    private var spraying = 0f
    private var puff = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        val dx = playerX - x
        if (spraying <= 0f) facingRight = dx > 0f
        val dist = abs(dx)
        sprayCooldown -= deltaTime
        if (spraying <= 0f && sprayCooldown <= 0f && dist < SPRAY_RANGE) { spraying = SPRAY_TIME; sprayCooldown = SPRAY_EVERY; puff = 0f }
        if (spraying > 0f) {
            spraying -= deltaTime
            velocityX = 0f; jumpZ = 0f
            puff -= deltaTime
            if (puff <= 0f) {
                puff = 0.07f
                val dir = if (facingRight) 1f else -1f
                // 꽃대 끝(머리 위 앞쪽)에서 인간 쪽으로 흩날린다
                val fx = x + width / 2f + dir * width * 0.35f
                val fy = y - height * 1.15f
                val r = 7f
                pendingProjectiles.add(
                    Projectile(fx - r, fy - r, dir * (300f + Math.random().toFloat() * 120f), 60f + (Math.random().toFloat() - 0.5f) * 180f,
                        1, false, tint = Color.rgb(250, 220, 80), radius = r, fuse = 0.8f, isPollen = true)
                )
            }
        } else {
            velocityX = when {
                dist < SPRAY_RANGE * 0.7f -> 0f
                dist < 500f -> sign(dx) * 150f
                else -> sign(dx) * 80f
            }
            if (abs(velocityX) > 1f) { hopPhase += deltaTime * 12f; jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 12f } else jumpZ = 0f
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.flowerCat)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()

    companion object {
        private const val SPRAY_RANGE = 320f
        private const val SPRAY_TIME = 0.9f
        private const val SPRAY_EVERY = 3f
    }
}

// ============ 31 해적 스테이지 (기획12·13, 2026-10-04 「해적 스테이지 만들어줘」 — 공격은 Claude 판단) ============
/**
 * 배냥이 — HP 20. 나무배를 타고 모래 위를 미끄러지듯 빠르게 다가와 **배로 들이받는다**(닿으면 하트 1).
 * 배가 출렁이며 다가오고, 한 번 들이받으면 뒤로 물러났다가 다시 온다. 속도·물러나는 시간은 Claude 판단.
 */
class BoatCat(x: Float, y: Float) : Enemy(x, y, 20, 5) {
    companion object {
        /** 배냥이 그림에서 배 바닥 높이 (그 아래는 다리 — 물에 잠긴다) */
        const val BOAT_HULL_BOTTOM = 0.88f
    }
    /** 배냥이 종류 = 무늬 (하양이·턱시도·삼색이·고등어·치즈, 대표님 2026-10-09) — 무작위 */
    val coat: com.jamcat.nayngapo.engine.CatCoat = listOf(
        com.jamcat.nayngapo.engine.CatCoat.CHEESE, com.jamcat.nayngapo.engine.CatCoat.WHITE, com.jamcat.nayngapo.engine.CatCoat.CALICO,
        com.jamcat.nayngapo.engine.CatCoat.TUXEDO, com.jamcat.nayngapo.engine.CatCoat.MACKEREL).random()
    override val width = 110f
    override val height = 90f
    private var rock = (Math.random() * 6.0).toFloat()
    private var backoff = 0f

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        rock += deltaTime * 5f
        val dx = playerX - x
        if (backoff > 0f) {
            backoff -= deltaTime
            velocityX = -sign(dx) * 160f
        } else {
            velocityX = if (abs(dx) < 700f) sign(dx) * 240f else sign(dx) * 90f
            if (abs(dx) < 50f) backoff = 0.8f              // 들이받고 물러난다
        }
        facingRight = dx > 0f
        jumpZ = -abs(sin(rock.toDouble())).toFloat() * 6f
        super.update(deltaTime, playerX, playerY)
        // 배냥이는 배라 해적선 갑판에는 못 오른다 — 뱃전 앞 물에서 멈춘다
        if (Terrain.onDeck(x + width / 2f)) {
            x = if (x + width / 2f < (Terrain.deckLeft + Terrain.deckRight) / 2f) Terrain.deckLeft - width / 2f - 1f
                else Terrain.deckRight - width / 2f + 1f
            y = Terrain.groundY(x + width / 2f)
            climbOffset = 0f
        }
        if (velocityX != 0f && backoff > 0f) facingRight = dx > 0f   // 물러날 때도 인간 쪽을 본다
    }

    override fun draw(canvas: Canvas) {
        // 바다라 배 밑의 발은 물에 잠겨 안 보인다 (대표님 2026-10-09): 배 바닥(그림 높이의 88%)이 물 위에 오게 그리고 그 아래는 자른다
        val base = coat.boatEnemy
        val bmp = if (facingRight) base else SpriteManager.getFlipped(base)
        val cx = x + width / 2f
        val water = y + jumpZ + 6f
        val top = water - bmp.height * BOAT_HULL_BOTTOM
        canvas.save()
        canvas.clipRect(cx - bmp.width / 2f - 2f, top - 4f, cx + bmp.width / 2f + 2f, water)
        canvas.drawBitmap(bmp, cx - bmp.width / 2f, top, null)
        canvas.restore()
        lastSpriteHeight = bmp.height * BOAT_HULL_BOTTOM
        lastSprite = base
        drawHpBar(canvas)
    }
}

/**
 * 해적냥이 — 31 보스, HP 40 (기획13). 해적선 앞을 지키며 다가와 **갈고리로 할퀴며 돌진**한다(보스라 닿으면 하트 3).
 * 2.5초마다 살짝 웅크렸다가 인간 쪽으로 빠르게 돌진. 간격·돌진 속도는 Claude 판단.
 */
class PirateCatBoss(x: Float, y: Float) : Enemy(x, y, 40, 8) {
    override val width = 120f
    override val height = 120f
    override val contactDamage = 3
    private var dashTimer = 2.0f
    private var windup = 0f
    private var dashing = 0f
    private var dir = 1f
    private var hopPhase = 0f
    /** 해적선 갑판 범위 — 이 밖으로는 안 나온다 (Level 이 정한다, 대표님 2026-10-09) */
    var deckLeft = Float.NEGATIVE_INFINITY
    var deckRight = Float.POSITIVE_INFINITY

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        val dx = playerX - x
        dashTimer -= deltaTime
        when {
            dashing > 0f -> { dashing -= deltaTime; velocityX = dir * 620f; jumpZ = 0f }
            windup > 0f -> {
                windup -= deltaTime; velocityX = 0f; jumpZ = 0f
                if (windup <= 0f) { dashing = 0.45f; dir = sign(dx) }
            }
            else -> {
                facingRight = dx > 0f
                velocityX = if (abs(dx) > 160f) sign(dx) * 110f else 0f
                if (abs(velocityX) > 1f) { hopPhase += deltaTime * 10f; jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 8f } else jumpZ = 0f
                if (dashTimer <= 0f && abs(dx) < 700f) { windup = 0.45f; dashTimer = 2.5f }
            }
        }
        super.update(deltaTime, playerX, playerY)
        if (x < deckLeft || x + width > deckRight) {
            x = x.coerceIn(deckLeft, (deckRight - width).coerceAtLeast(deckLeft))
            y = Terrain.groundY(x + width / 2f)
        }
        if (dashing > 0f) facingRight = dir > 0f
    }

    override fun draw(canvas: Canvas) {
        // 돌진 직전엔 몸을 웅크리며 떤다
        if (windup > 0f) jumpZ = sin(animTime * 60f) * 3f
        drawSprite(canvas, SpriteManager.pirateCat)
        drawHpBar(canvas)
    }
}

// ============ 새끼 고양이 (대표님 2026-09-27) ============
/**
 * 새끼 고양이 — HP 3 (처음 6 → 대표님 정정) · 닿으면 **하트 반 칸**. 치즈냥이를 작게 줄인 모습(무늬는 치즈냥이처럼 무작위).
 * 종종걸음으로 빠르게 쫓아와 문다. 속도·크기(치즈냥이의 60%)·배치는 Claude 판단. 아군 냥이에게는 1 피해.
 */
class KittenCat(x: Float, y: Float, val coat: com.jamcat.nayngapo.engine.CatCoat = com.jamcat.nayngapo.engine.CatCoat.randomForEnemy())
    : Enemy(x, y, 3, 1) {
    override val width = 52f
    override val height = 58f
    override val halfHeart: Boolean get() = true
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var attackAnim = 0f      // 0보다 크면 냥냥펀치 모션 (치즈냥이와 똑같이 — 대표님 2026-09-27)

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        val dx = playerX - x
        velocityX = if (abs(dx) < 600f) sign(dx) * 190f else sign(dx) * 80f
        // 코앞까지 붙으면 냥냥펀치 모션
        if (attackAnim > 0f) attackAnim -= deltaTime
        else if (abs(dx) < 80f) attackAnim = 0.5f
        if (abs(velocityX) > 1f) {
            hopPhase += deltaTime * 12f
            jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 4f
        } else jumpZ = 0f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        // 치즈냥이와 같은 공격·이동·대기 애니메이션을 작게 줄여 재생
        val frames = when {
            attackAnim > 0f -> coat.attack
            abs(velocityX) > 1f -> coat.walk
            else -> coat.idle
        }
        val fps = if (attackAnim > 0f) 8f else 9f
        drawSprite(canvas, small(frame(frames, fps)))
        drawHpBar(canvas)
    }

    companion object {
        private val cache = HashMap<Bitmap, Bitmap>()
        /** 무늬별 치즈냥이 그림을 60% 로 줄여 한 번만 만들어 둔다 */
        fun small(src: Bitmap): Bitmap = cache.getOrPut(src) {
            Bitmap.createScaledBitmap(src, (src.width * 0.6f).toInt(), (src.height * 0.6f).toInt(), true)
        }
    }
}

// ============ 머드냥이 (기획9 `Plan/기획9_머드냥이.jpg`, 대표님 2026-09-27) ============
/**
 * 머드냥이 — HP 20 · 공격력 2. **늪 속에 숨어 있어 평소엔 안 보인다**(늪 위로 등만 살짝 볼록).
 * **인간이 늪에 빠졌을 때만 공격**한다: 늪에서 튀어 올라 인간 머리 위를 넘어 반대편으로 뛰어들며, 닿으면 하트 2.
 * **늪 밖으로는 못 나온다** — 공격할 때만 잠깐 튀어나오고, 그때만 때릴 수 있다.
 * 늪마다 한 마리 (스테이지 배치는 Claude 판단). 숨어 있으면 스테이지 클리어 조건에서 빠진다(Level.stageCleared).
 * 도약 시간 0.9초·높이·다시 뛰기까지 1.6초는 Claude 판단.
 */
open class MudCat(private val swamp: Swamp) : Enemy(swamp.x + swamp.width / 2f - 46f, swamp.y, 20, 2) {
    /** 튀어나올 때 그림 (꿀냥이는 꿀색) */
    protected open val sprite: android.graphics.Bitmap get() = SpriteManager.mudCatEvil

    override val width = 92f
    override val height = 90f
    override val contactDamage = 2

    /** 화면이 매 프레임 알려 준다: 인간이 이 늪에 빠져 있는가, 인간 가운데 x */
    var preyInSwamp = false
    var preyX = 0f

    private var leapT = -1f           // 뛰는 중이면 0~1
    private var fromX = 0f
    private var toX = 0f
    private var rest = 0.6f           // 다시 뛰기까지
    private var bubble = (Math.random() * 3.0).toFloat()

    val isHidden: Boolean get() = leapT < 0f

    /** 이 x 가 제 늪 안쪽인가 */
    fun inSwampOf(cx: Float) = swamp.swallows(cx)
    override val isTangible: Boolean get() = !isHidden

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        if (hitCooldown > 0f) hitCooldown -= deltaTime
        animTime += deltaTime
        bubble += deltaTime
        y = swamp.y
        if (leapT >= 0f) {
            leapT += deltaTime / LEAP_TIME
            val t = leapT.coerceAtMost(1f)
            x = fromX + (toX - fromX) * t
            jumpZ = -4f * LEAP_HEIGHT * t * (1f - t)            // 포물선
            facingRight = toX > fromX
            if (leapT >= 1f) { leapT = -1f; jumpZ = 0f; rest = REST_TIME }
            return
        }
        jumpZ = 0f
        if (rest > 0f) rest -= deltaTime
        if (preyInSwamp && rest <= 0f) {
            // 인간 머리 위를 넘어 반대편으로 — 도착점도 늪 안쪽
            val c = x + width / 2f
            val side = if (c < preyX) 1f else -1f
            val land = (preyX + side * 150f).coerceIn(swamp.x + 30f, swamp.x + swamp.width - 30f)
            fromX = x
            toX = land - width / 2f
            leapT = 0f
        }
    }

    override fun takeDamage(amount: Int) {
        if (isTangible) super.takeDamage(amount)
    }

    override fun draw(canvas: Canvas) {
        if (isHidden) {
            // 늪 위로 진흙 등만 볼록 — 가끔 거품이 올라온다
            val cx = x + width / 2f
            val by = y + 12f
            moundRect.set(cx - 34f, by - 20f, cx + 34f, by + 14f)
            canvas.drawArc(moundRect, 180f, 180f, true, if (swamp.honey) honeyMoundPaint else moundPaint)
            canvas.drawArc(moundRect, 180f, 180f, false, moundInk)
            val t = bubble % 2.2f
            if (t < 1.2f) canvas.drawCircle(cx + 22f, by - 8f - t * 12f, 4f + t * 3f, bubbleInk)
            return
        }
        drawSprite(canvas, sprite)
        // 몸에서 진흙 방울이 떨어진다
        val cx = x + width / 2f
        for (k in 0 until 3) {
            val ph = (animTime * 3f + k * 0.33f) % 1f
            canvas.drawCircle(cx + (k - 1) * 22f, y + jumpZ + 4f + ph * 40f, 5f * (1f - ph) + 2f, moundPaint)
        }
        drawHpBar(canvas)
    }

    companion object {
        private const val LEAP_TIME = 0.9f
        private const val LEAP_HEIGHT = 120f
        private const val REST_TIME = 1.6f
        private val moundPaint = Paint().apply { color = Color.rgb(150, 102, 60); isAntiAlias = true }
        private val honeyMoundPaint = Paint().apply { color = Color.rgb(240, 160, 32); isAntiAlias = true }
        private val moundInk = Paint().apply { color = Color.rgb(40, 30, 22); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
        private val bubbleInk = Paint().apply { color = Color.rgb(132, 118, 74); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
        private val moundRect = RectF()
    }
}

/**
 * 꿀냥이 — 꿀 지역(21~30)에서는 머드냥이 대신 **꿀 늪에 꿀냥이**가 숨어 있다 (대표님 2026-10-09).
 * 동작·체력·공격은 머드냥이와 같다(HP 20 · 튀어 오를 때 하트 2). 그림만 황금빛 꿀색(GPT).
 */
class HoneyCat(swamp: Swamp) : MudCat(swamp) {
    override val sprite: android.graphics.Bitmap get() = SpriteManager.honeyCat
}

// ============ 나쁜 그린냥이 (HP 20, 슬라임 대미지 10) ============
/** 수북한 꼬리의 나쁜 편 그린냥이: 멈춰 서서 슬라임을 뱉는다 */
class GreenCat(x: Float, y: Float) : Enemy(x, y, 20, 10) {
    override val width = 96f
    override val height = 104f
    private var spitTimer = 1.6f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x

        val dist = abs(playerX - x)
        velocityX = if (dist > 420f) sign(playerX - x) * 70f else 0f

        spitTimer -= deltaTime
        if (spitTimer <= 0f && dist < Constants.ENEMY_FIRE_RANGE) {
            spitTimer = 2.2f
            val dir = if (facingRight) 1f else -1f
            pendingProjectiles.add(
                Projectile(x + width / 2f, y - height * 0.45f, 260f * dir, 0f, 10, false,
                    tint = Color.rgb(120, 190, 90), radius = 13f)
            )
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.greenCatEvil)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}

// ============ 날아냥이 (HP 25, 표창 대미지 15) ============
/** 땅을 총총 뛰어다니며 표창을 던진다 (고양이는 날지 않는다) */
class FlyingCat(x: Float, y: Float) : Enemy(x, y, 25, 15) {
    override val width = 86f
    override val height = 90f
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    private var throwTimer = 1.2f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x

        val dx = playerX - x
        velocityX = if (abs(dx) > 340f) sign(dx) * 150f else sign(dx) * 40f

        // 땅을 딛고 총총 뛴다
        hopPhase += deltaTime * 11f
        jumpZ = -abs(sin(hopPhase.toDouble())).toFloat() * 14f

        throwTimer -= deltaTime
        if (throwTimer <= 0f && abs(dx) < Constants.ENEMY_FIRE_RANGE) {
            throwTimer = 1.8f
            val dir = if (facingRight) 1f else -1f
            pendingProjectiles.add(
                Projectile(x + width / 2f, y + jumpZ - height * 0.35f, 620f * dir, 0f, 15, false,
                    tint = Color.rgb(150, 154, 160), radius = 7f)
            )
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.flyingCat)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}

// ============ 털뭉냥이 (HP 50, 대미지 10) ============
/** 털이 두꺼워 단단하다. 몸을 부풀렸다가 뾰족한 털을 발사한다 */
class FluffyCat(x: Float, y: Float) : Enemy(x, y, 50, 10) {
    override val width = 100f
    override val height = 105f
    private var rollPhase = (Math.random() * Math.PI).toFloat()
    private var shootTimer = 2f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x
        val dx = playerX - x
        velocityX = sign(dx) * 95f

        // 털 발사: 높이가 다른 세 가닥을 한 번에 흩뿌린다
        shootTimer -= deltaTime
        if (shootTimer <= 0f && abs(dx) < Constants.ENEMY_FIRE_RANGE) {
            shootTimer = 2.4f
            val dir = if (facingRight) 1f else -1f
            for ((k, dy) in floatArrayOf(-0.7f, -0.45f, -0.2f).withIndex()) {
                pendingProjectiles.add(
                    Projectile(x + width / 2f, y + height * dy, (520f + k * 60f) * dir, 0f, 10, false,
                        tint = Color.rgb(226, 190, 88), radius = 6f)
                )
            }
        }

        rollPhase += deltaTime * 7f
        jumpZ = -abs(sin(rollPhase.toDouble())).toFloat() * 8f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.fluffyCat)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}

// ============ 모루냥이 (HP 75, 대미지 10) ============
/** 쇳덩이 같은 몸. 아주 느리지만 가장 단단하다 */
class AnvilCat(x: Float, y: Float) : Enemy(x, y, 75, 10) {   // 시트: 모루냥이 HP75, 대미지 10
    override val width = 112f
    override val height = 110f

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        velocityX = sign(playerX - x) * 55f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.anvilCat)
        drawHpBar(canvas)
    }
}

// ============ 똥냥이 (HP 30, 대미지 3) ============
/** 느릿느릿 다가와 고약한 냄새를 풍긴다 */
class PoopCat(x: Float, y: Float) : Enemy(x, y, 30, 3) {
    override val width = 92f
    override val height = 100f
    private var wobble = (Math.random() * Math.PI).toFloat()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        velocityX = sign(playerX - x) * 70f

        wobble += deltaTime * 4.5f
        jumpZ = -abs(sin(wobble.toDouble())).toFloat() * 6f
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.poopCat)
        drawHpBar(canvas)
    }
}

// ============ Human Cat Boss (Stage 1) ============
class HumanCatBoss(x: Float, y: Float) : Enemy(x, y, 3, 5) {
    override val width = 110f
    override val height = 120f
    var phase = 1
    val maxPhases = 10
    private var actionTimer = 1.5f
    private val baseSpeed = 100f

    init { facingRight = false }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        actionTimer -= deltaTime

        if (actionTimer <= 0f) {
            actionTimer = when {
                phase <= 3 -> 1.5f; phase <= 6 -> 1.0f; phase <= 9 -> 0.7f; else -> 0.4f
            }
            val speed = baseSpeed + phase * 20f
            velocityX = sign(playerX - x) * speed
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun takeDamage(amount: Int) {
        hp -= amount
        if (hp <= 0) {
            if (phase < maxPhases) { phase++; hp = 3 } else die()
        }
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.bossHumanCat)
        val cx = x + width / 2f
        val textPaint = Paint().apply { color = Color.WHITE; textSize = 14f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        canvas.drawText("P$phase/$maxPhases", cx, y - height - 5f, textPaint)
        drawHpBar(canvas)
    }
}

// ============ Evil Cat Boss (Stage 2) ============
class EvilCatBoss(x: Float, y: Float) : Enemy(x, y, 30, 20) {
    override val width = 145f
    override val height = 160f
    private val moveSpeed = 90f
    private var chargeTimer = 3f
    private var isCharging = false

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        chargeTimer -= deltaTime

        if (chargeTimer <= 0f && !isCharging) {
            isCharging = true
            velocityX = sign(playerX - x) * moveSpeed * 4f   // 돌진
            chargeTimer = 0.5f
        } else if (isCharging) {
            if (chargeTimer <= 0f) { isCharging = false; chargeTimer = 3f; velocityX = 0f }
        } else {
            velocityX = sign(playerX - x) * moveSpeed
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.bossEvilCat)
        drawHpBar(canvas)
    }
}

// ============ Wizard Cat Boss (Final) ============
class WizardCatBoss(x: Float, y: Float) : Enemy(x, y, 3, 250) {
    override val width = 130f
    override val height = 145f
    private var floatTimer = 0f
    private var shootTimer = 1.5f
    private val pendingProjectiles = mutableListOf<Projectile>()

    // 마법사 냥이는 데미지 크기와 상관없이 '세 번' 맞아야 쓰러진다
    override fun takeDamage(amount: Int) {
        if (hitCooldown > 0f) return
        hitCooldown = HIT_COOLDOWN
        hp -= 1
        if (hp <= 0) die()
    }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x
        floatTimer += deltaTime
        jumpZ = sin(floatTimer.toDouble() * 2.0).toFloat() * 20f - 30f

        val dx = playerX - x
        val dist = abs(dx)
        if (dist > 150f) {
            velocityX = sign(dx) * 70f
        } else {
            velocityX = 0f
        }

        shootTimer -= deltaTime
        if (shootTimer <= 0f && dist < Constants.ENEMY_FIRE_RANGE) {   // 화면에 보일 거리에서만 발사
            shootTimer = 1.2f
            val cx = x + width / 2f
            // 횡뷰: 속도가 다른 수평 탄막 5발
            val dir = if (facingRight) 1f else -1f
            for (speed in intArrayOf(250, 350, 450, 550, 650)) {
                pendingProjectiles.add(Projectile(cx, y - height * 0.3f, speed * dir, 0f, 50, false))
            }
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        val cx = x + width / 2f
        drawSprite(canvas, SpriteManager.bossWizardCat)

        // Aura effect
        val auraPaint = Paint().apply {
            color = Color.rgb(180, 100, 255)
            alpha = (80 + (sin(floatTimer.toDouble() * 4) * 40).toInt()).coerceIn(0, 255)
            style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true
        }
        canvas.drawCircle(cx, y + jumpZ - height / 2f, 85f, auraPaint)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}

// ============ 순간냥이 보스 (10스테이지 — 기획4·5) ============
/**
 * 초원의 최종 보스. 순간이동으로 사라졌다가 플레이어 근처에 다시 나타난다.
 * 몸에 닿으면 데미지 3 (기획4: "보스에 맞으면 데미지 3").
 */
class TeleportCatBoss(x: Float, y: Float) : Enemy(x, y, 60, 3) {
    override val width = 150f
    override val height = 160f
    override val contactDamage = 3

    private var teleportTimer = 2.8f
    private var vanishTimer = 0f          // 0보다 크면 사라져 있는 중
    private var floatPhase = 0f
    private var appearFlash = 0f          // 나타난 직후의 반짝임 연출

    override val isTangible: Boolean get() = vanishTimer <= 0f

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        floatPhase += deltaTime

        if (vanishTimer > 0f) {
            vanishTimer -= deltaTime
            if (vanishTimer <= 0f) {
                // 플레이어의 앞 또는 뒤, 화면 안쪽에 다시 나타난다
                val side = if (Math.random() < 0.5) -1f else 1f
                x = playerX + side * (320f + (Math.random() * 160f).toFloat())
                teleportTimer = 2.5f
                appearFlash = 0.35f
            }
            return                        // 사라진 동안은 움직이지도, 닿지도 않는다
        }

        if (appearFlash > 0f) appearFlash -= deltaTime
        facingRight = playerX > x
        teleportTimer -= deltaTime
        if (teleportTimer <= 0f) {
            vanishTimer = 0.55f
            velocityX = 0f
            return
        }

        velocityX = sign(playerX - x) * 120f
        jumpZ = sin(floatPhase.toDouble() * 3.0).toFloat() * 6f
        super.update(deltaTime, playerX, playerY)
    }

    override fun takeDamage(amount: Int) {
        if (isTangible) super.takeDamage(amount)
    }

    override fun draw(canvas: Canvas) {
        if (!isTangible) return
        drawSprite(canvas, SpriteManager.teleportCat)
        if (appearFlash > 0f) {
            val cx = x + width / 2f
            val p = Paint().apply {
                color = Color.argb((200 * appearFlash / 0.35f).toInt(), 255, 255, 255)
                style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true
            }
            canvas.drawCircle(cx, y + jumpZ - height / 2f, 90f * (1.3f - appearFlash / 0.35f), p)
        }
        drawHpBar(canvas)
    }
}

// ============ 덩굴냥이 (정글 12~20) ============
/**
 * 나뭇잎 모자를 쓰고 나무 덩굴에 매달려 그네처럼 날아다니는 냥이.
 * 덩굴 뿌리가 플레이어 쪽으로 천천히 미끄러져 오고, 흔들림이 가장 낮아질 때
 * 점프 공격이나 로켓으로 맞출 수 있다. 공중에 있으므로 바닥 함정은 무시한다.
 */
class VineCat(x: Float, y: Float) : Enemy(x, y, 15, 2) {
    override val width = 84f
    override val height = 92f

    private var anchorX = x
    private val anchorY = Constants.GROUND_Y - 700f
    private val vineLen = 580f + (Math.random() * 100f).toFloat()
    private var phase = (Math.random() * Math.PI * 2).toFloat()
    private val swingSpeed = 1.4f + (Math.random() * 0.6f).toFloat()
    private val amp = 0.85f + (Math.random() * 0.35f).toFloat()   // 흔들림 폭(라디안)

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        // 공중 그네: 지면 스냅을 하는 super.update는 부르지 않는다
        if (hitCooldown > 0f) hitCooldown -= deltaTime
        animTime += deltaTime

        phase += deltaTime * swingSpeed
        anchorX += sign(playerX - anchorX) * 60f * deltaTime

        val angle = sin(phase.toDouble()).toFloat() * amp
        x = anchorX + sin(angle.toDouble()).toFloat() * vineLen - width / 2f
        y = anchorY + kotlin.math.cos(angle.toDouble()).toFloat() * vineLen

        // 머리 방향 = 그네가 나아가는 방향
        facingRight = kotlin.math.cos(phase.toDouble()) * amp >= 0
    }

    override fun draw(canvas: Canvas) {
        val cx = x + width / 2f
        val topY = y - height + 6f

        // 저 아래 지면의 그림자
        val gy = Terrain.groundY(cx)
        vineShadow.set(cx - 34f, gy - 5f, cx + 34f, gy + 7f)
        canvas.drawOval(vineShadow, vineShadowPaint)

        // 덩굴 줄 + 잎사귀
        canvas.drawLine(anchorX, anchorY, cx, topY, vinePaint)
        for (k in 1..3) {
            val t = k / 4f
            val lx = anchorX + (cx - anchorX) * t
            val ly = anchorY + (topY - anchorY) * t
            val side = if (k % 2 == 0) 1f else -1f
            canvas.drawOval(lx, ly - 6f, lx + 24f * side, ly + 6f, vineLeafPaint)
        }

        // 나뭇잎 모자를 쓴 냥이 (원안 스프라이트 + 모자 합성본)
        val bmp = if (facingRight) SpriteManager.vineCat
            else SpriteManager.getFlipped(SpriteManager.vineCat)
        canvas.drawBitmap(bmp, cx - bmp.width / 2f, y - bmp.height + 8f, null)

        drawHpBar(canvas)
    }

    private companion object {
        val vinePaint = Paint().apply {
            color = Color.rgb(62, 124, 58); style = Paint.Style.STROKE
            strokeWidth = 8f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
        }
        val vineLeafPaint = Paint().apply { color = Color.rgb(74, 142, 66); isAntiAlias = true }
        val vineShadowPaint = Paint().apply { color = Color.argb(40, 0, 0, 0); isAntiAlias = true }
        val vineShadow = RectF()
    }
}
