package com.jamcat.nayngapo.entity

import android.graphics.*
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.engine.Terrain
import kotlin.math.sign
import kotlin.math.abs
import kotlin.math.sin

abstract class Enemy(var x: Float, var y: Float, val maxHp: Int, val attackPower: Int) {
    open val width = 50f
    open val height = 50f
    var hp = maxHp
    var velocityX = 0f
    var facingRight = false
    var isDead = false
    var jumpZ = 0f

    // 전신 히트박스 (부유 연출 jumpZ 포함): 경사 지형에서도 근접 공격이 빗나가지 않는다
    val hitBox: RectF get() = RectF(x, y + jumpZ - height, x + width, y + 10f)

    open fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        // 뒤에 멀리 남겨진 냥이는 플레이어를 쫓아온다 (한 방향 진행에서 클리어 불가 방지)
        if (x + width < playerX - Constants.ENEMY_CATCHUP_DISTANCE) {
            velocityX = Constants.ENEMY_CATCHUP_SPEED
        }
        // 횡뷰 플랫포머: 좌우로만 이동, 발은 항상 지형 표면 위 (공중 연출은 jumpZ)
        x += velocityX * deltaTime
        x = x.coerceIn(Constants.PLAY_AREA_LEFT + 5f, Constants.PLAY_AREA_RIGHT - width - 5f)
        y = Terrain.groundY(x + width / 2f)
    }

    abstract fun draw(canvas: Canvas)

    open fun takeDamage(amount: Int) {
        hp -= amount
        if (hp <= 0) isDead = true
    }

    open fun getProjectiles(): List<Projectile> = emptyList()

    protected fun drawSprite(canvas: Canvas, sprite: Bitmap) {
        val cx = x + width / 2f
        val drawY = y + jumpZ

        // Shadow (몸 크기에 비례)
        val sw = width * 0.45f
        scratchRect.set(cx - sw, y - 5f, cx + sw, y + 7f)
        canvas.drawOval(scratchRect, shadowPaint)

        val drawBmp = if (facingRight) SpriteManager.getFlipped(sprite) else sprite
        val sx = cx - drawBmp.width / 2f
        val sy = drawY - drawBmp.height + 8f
        canvas.drawBitmap(drawBmp, sx, sy, null)
    }

    /** 적 체력 게이지: 머리 위에 몸 너비만큼 표시 */
    protected fun drawHpBar(canvas: Canvas) {
        val cx = x + width / 2f
        val barY = y + jumpZ - height - 16f
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
class NormalCat(x: Float, y: Float) : Enemy(x, y, 2, 8) {
    override val width = 80f
    override val height = 95f
    private val moveSpeed = 60f
    private var wanderTimer = 0f
    private var wanderDirX = 0f

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
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.normalCat)
        drawHpBar(canvas)
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
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.thiefCat)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}

// ============ Laser Cat ============
class LaserCat(x: Float, y: Float) : Enemy(x, y, 70, 100) {
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
                    Projectile(x + width / 2f, y, 800f * dir, 0f, 100, false)
                )
            }
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.laserCat)
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
            if (phase < maxPhases) { phase++; hp = 3 } else isDead = true
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
class WizardCatBoss(x: Float, y: Float) : Enemy(x, y, 50, 250) {
    override val width = 130f
    override val height = 145f
    private var floatTimer = 0f
    private var shootTimer = 1.5f
    private val pendingProjectiles = mutableListOf<Projectile>()

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
