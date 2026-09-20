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

    /** 순간이동 등으로 잠시 사라져 몸이 닿지 않는 상태인지 */
    open val isTangible: Boolean get() = true

    // 전신 히트박스 (부유 연출 jumpZ 포함): 경사 지형에서도 근접 공격이 빗나가지 않는다
    val hitBox: RectF get() = RectF(x, y + jumpZ - height, x + width, y + 10f)

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
        y = Terrain.groundY(x + width / 2f)

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
        val drawY = y + jumpZ + windLift + hopOffset

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
    private var lastSpriteHeight = 0f

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
class NormalCat(x: Float, y: Float, val coat: com.jamcat.nayngapo.engine.CatCoat = com.jamcat.nayngapo.engine.CatCoat.random())
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
    /** 무늬는 삼색이·고등어·턱시도·하양이 중 무작위 (대표님 2026-09-20 — 노랑이는 목록에 없어 제외) */
    private val coat = HAMMER_COATS[(Math.random() * HAMMER_COATS.size).toInt().coerceIn(0, HAMMER_COATS.size - 1)]

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

// ============ Thief Cat ============
class ThiefCat(x: Float, y: Float) : Enemy(x, y, 50, 15) {
    override val width = 85f
    override val height = 100f
    private val moveSpeed = 180f
    private val detectRange = 300f
    private var shootTimer = 2f
    private val pendingProjectiles = mutableListOf<Projectile>()
    private var hopPhase = (Math.random() * Math.PI).toFloat()
    val specialPower = 25

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x

        val dx = playerX - x
        val dist = abs(dx)

        if (dist < detectRange && dist > 50f) {
            velocityX = sign(dx) * moveSpeed
        } else if (dist <= 50f) {
            velocityX = 0f
        } else {
            velocityX = if (facingRight) moveSpeed * 0.3f else -moveSpeed * 0.3f
        }

        shootTimer -= deltaTime
        if (shootTimer <= 0f && dist < 500f) {
            shootTimer = 2f
            // 횡뷰: 수평으로만 발사
            val dir = if (facingRight) 1f else -1f
            pendingProjectiles.add(
                Projectile(x + width / 2f, y - height * 0.4f, dir * 450f, 0f, 15, false)
            )
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

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.thiefCat)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
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
