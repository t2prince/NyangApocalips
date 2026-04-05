package com.jamcat.nayngapo.entity

import android.graphics.*
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.SpriteManager
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

abstract class Enemy(var x: Float, var y: Float, val maxHp: Int, val attackPower: Int) {
    open val width = 50f
    open val height = 50f
    var hp = maxHp
    var velocityX = 0f
    var velocityY = 0f
    var facingRight = false
    var isDead = false
    var jumpZ = 0f

    val hitBox: RectF get() = RectF(x, y - 20f, x + width, y + 10f)

    open fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        x += velocityX * deltaTime
        y += velocityY * deltaTime
        x = x.coerceIn(Constants.PLAY_AREA_LEFT + 5f, Constants.PLAY_AREA_RIGHT - width - 5f)
        y = y.coerceIn(Constants.PLAY_AREA_TOP + 40f, Constants.PLAY_AREA_BOTTOM - 5f)
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

        // Shadow
        val shadowPaint = Paint().apply { color = Color.argb(50, 0, 0, 0); isAntiAlias = true }
        canvas.drawOval(RectF(cx - 20f, y - 4f, cx + 20f, y + 6f), shadowPaint)

        val drawBmp = if (facingRight) SpriteManager.getFlipped(sprite) else sprite
        val sx = cx - drawBmp.width / 2f
        val sy = drawY - drawBmp.height + 8f
        canvas.drawBitmap(drawBmp, sx, sy, null)
    }

    protected fun drawHpBar(canvas: Canvas) {
        val cx = x + width / 2f
        val barY = y + jumpZ - height - 12f
        val barW = 50f
        val barBg = Paint().apply { color = Color.DKGRAY }
        val barFg = Paint().apply { color = Color.RED }
        canvas.drawRect(cx - barW / 2, barY, cx + barW / 2, barY + 5f, barBg)
        canvas.drawRect(cx - barW / 2, barY, cx - barW / 2 + barW * hp / maxHp.toFloat(), barY + 5f, barFg)
    }
}

// ============ Normal Cat ============
class NormalCat(x: Float, y: Float) : Enemy(x, y, 2, 8) {
    override val width = 45f
    override val height = 55f
    private val patrolCenterX = x
    private val patrolCenterY = y
    private val moveSpeed = 60f
    private var wanderTimer = 0f
    private var wanderDirX = 0f
    private var wanderDirY = 0f

    init { pickNewDirection() }

    private fun pickNewDirection() {
        wanderTimer = 1f + (Math.random() * 2f).toFloat()
        val angle = Math.random() * Math.PI * 2
        wanderDirX = (kotlin.math.cos(angle) * moveSpeed).toFloat()
        wanderDirY = (kotlin.math.sin(angle) * moveSpeed).toFloat()
    }

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        wanderTimer -= deltaTime

        val dist = hypot((playerX - x).toDouble(), (playerY - y).toDouble()).toFloat()
        if (dist < 200f) {
            val dx = playerX - x
            val dy = playerY - y
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
            velocityX = dx / len * moveSpeed * 1.5f
            velocityY = dy / len * moveSpeed * 1.5f
        } else {
            if (wanderTimer <= 0f) pickNewDirection()
            velocityX = wanderDirX
            velocityY = wanderDirY
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.normalCat)
    }
}

// ============ Thief Cat ============
class ThiefCat(x: Float, y: Float) : Enemy(x, y, 50, 15) {
    override val width = 50f
    override val height = 58f
    private val moveSpeed = 180f
    private val detectRange = 300f
    private var shootTimer = 2f
    private val pendingProjectiles = mutableListOf<Projectile>()
    val specialPower = 25

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x

        val dx = playerX - x
        val dy = playerY - y
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (dist < detectRange && dist > 50f) {
            val len = dist.coerceAtLeast(1f)
            velocityX = dx / len * moveSpeed
            velocityY = dy / len * moveSpeed
        } else if (dist <= 50f) {
            velocityX = 0f; velocityY = 0f
        } else {
            velocityX = if (facingRight) moveSpeed * 0.3f else -moveSpeed * 0.3f
            velocityY = 0f
        }

        shootTimer -= deltaTime
        if (shootTimer <= 0f && dist < 400f) {
            shootTimer = 2f
            val len = dist.coerceAtLeast(1f)
            pendingProjectiles.add(
                Projectile(x + width / 2f, y, dx / len * 450f, dy / len * 450f, 15, false)
            )
        }
        super.update(deltaTime, playerX, playerY)
    }

    override fun draw(canvas: Canvas) {
        drawSprite(canvas, SpriteManager.thiefCat)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}

// ============ Laser Cat ============
class LaserCat(x: Float, y: Float) : Enemy(x, y, 70, 100) {
    override val width = 65f
    override val height = 60f
    private var shootTimer = 3f
    private var isCharging = false
    private var chargeTimer = 0f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        velocityX = 0f; velocityY = 0f
        facingRight = playerX > x

        shootTimer -= deltaTime
        if (shootTimer <= 0f && !isCharging) {
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
        if (isCharging) {
            val cx = x + width / 2f
            val barrelDir = if (facingRight) 1f else -1f
            val chargePaint = Paint().apply {
                color = Color.RED
                alpha = (150 + (sin(chargeTimer.toDouble() * 20) * 100).toInt()).coerceIn(0, 255)
                isAntiAlias = true
            }
            canvas.drawCircle(cx + 40f * barrelDir, y - height / 2f, 8f, chargePaint)
        }
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}

// ============ Human Cat Boss (Stage 1) ============
class HumanCatBoss(x: Float, y: Float) : Enemy(x, y, 3, 5) {
    override val width = 65f
    override val height = 72f
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
            val dx = playerX - x; val dy = playerY - y
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
            velocityX = dx / len * speed
            velocityY = dy / len * speed
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
    override val width = 85f
    override val height = 95f
    private val moveSpeed = 90f
    private var chargeTimer = 3f
    private var isCharging = false

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        facingRight = playerX > x
        chargeTimer -= deltaTime

        if (chargeTimer <= 0f && !isCharging) {
            isCharging = true
            val dx = playerX - x; val dy = playerY - y
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
            velocityX = dx / len * moveSpeed * 4f
            velocityY = dy / len * moveSpeed * 4f
            chargeTimer = 0.5f
        } else if (isCharging) {
            if (chargeTimer <= 0f) { isCharging = false; chargeTimer = 3f; velocityX = 0f; velocityY = 0f }
        } else {
            val dx = playerX - x; val dy = playerY - y
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
            velocityX = dx / len * moveSpeed
            velocityY = dy / len * moveSpeed
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
    override val width = 75f
    override val height = 85f
    private var floatTimer = 0f
    private var shootTimer = 1.5f
    private val pendingProjectiles = mutableListOf<Projectile>()

    override fun update(deltaTime: Float, playerX: Float, playerY: Float) {
        pendingProjectiles.clear()
        facingRight = playerX > x
        floatTimer += deltaTime
        jumpZ = sin(floatTimer.toDouble() * 2.0).toFloat() * 20f - 30f

        val dx = playerX - x; val dy = playerY - y
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist > 150f) {
            val len = dist.coerceAtLeast(1f)
            velocityX = dx / len * 70f; velocityY = dy / len * 70f
        } else { velocityX = 0f; velocityY = 0f }

        shootTimer -= deltaTime
        if (shootTimer <= 0f) {
            shootTimer = 1.2f
            val cx = x + width / 2f
            for (angle in intArrayOf(-30, -15, 0, 15, 30)) {
                val rad = Math.toRadians(angle.toDouble())
                val dir = if (facingRight) 1f else -1f
                val vx = (kotlin.math.cos(rad) * 350.0 * dir).toFloat()
                val vy = (kotlin.math.sin(rad) * 350.0).toFloat()
                pendingProjectiles.add(Projectile(cx, y, vx, vy, 50, false))
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
        canvas.drawCircle(cx, y + jumpZ - height / 2f, 50f, auraPaint)
        drawHpBar(canvas)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}
