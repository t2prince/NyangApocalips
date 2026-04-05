package com.jamcat.nayngapo.entity

import android.graphics.*
import com.jamcat.nayngapo.engine.Constants
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

    protected fun drawCatBody(canvas: Canvas, bodyColor: Int, earColor: Int, scale: Float = 1f) {
        val cx = x + width / 2f
        val drawY = y + jumpZ
        val cy = drawY - height / 2f
        val s = scale

        // Shadow
        val shadowPaint = Paint().apply { color = Color.argb(50, 0, 0, 0); isAntiAlias = true }
        canvas.drawOval(RectF(cx - 18f * s, y - 4f, cx + 18f * s, y + 6f), shadowPaint)

        canvas.save()
        if (facingRight) {
            canvas.scale(-1f, 1f, cx, cy)
        }

        val bodyPaint = Paint().apply { color = bodyColor; isAntiAlias = true }
        val earPaint = Paint().apply { color = earColor; isAntiAlias = true }
        val whitePaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
        val blackPaint = Paint().apply { color = Color.BLACK; isAntiAlias = true }
        val nosePaint = Paint().apply { color = Color.rgb(255, 150, 150); isAntiAlias = true }
        val whiskerPaint = Paint().apply {
            color = Color.DKGRAY; strokeWidth = 1.5f; isAntiAlias = true
        }
        val tailPaint = Paint().apply {
            color = bodyColor; strokeWidth = 4f * s; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; isAntiAlias = true
        }

        // Tail
        val tailPath = Path().apply {
            moveTo(cx + 18f * s, cy + 8f * s)
            quadTo(cx + 32f * s, cy - 10f * s, cx + 26f * s, cy - 22f * s)
        }
        canvas.drawPath(tailPath, tailPaint)

        // Body (oval)
        canvas.drawOval(RectF(cx - 18f * s, cy - 6f * s, cx + 18f * s, cy + 16f * s), bodyPaint)

        // Feet
        canvas.drawOval(RectF(cx - 14f * s, cy + 12f * s, cx - 4f * s, cy + 20f * s), bodyPaint)
        canvas.drawOval(RectF(cx + 4f * s, cy + 12f * s, cx + 14f * s, cy + 20f * s), bodyPaint)

        // Head
        canvas.drawCircle(cx, cy - 12f * s, 14f * s, bodyPaint)

        // Ears
        val leftEar = Path().apply {
            moveTo(cx - 12f * s, cy - 22f * s)
            lineTo(cx - 7f * s, cy - 36f * s)
            lineTo(cx - 1f * s, cy - 22f * s); close()
        }
        val rightEar = Path().apply {
            moveTo(cx + 1f * s, cy - 22f * s)
            lineTo(cx + 7f * s, cy - 36f * s)
            lineTo(cx + 12f * s, cy - 22f * s); close()
        }
        canvas.drawPath(leftEar, earPaint)
        canvas.drawPath(rightEar, earPaint)

        // Eyes
        canvas.drawCircle(cx - 6f * s, cy - 14f * s, 4f * s, whitePaint)
        canvas.drawCircle(cx + 6f * s, cy - 14f * s, 4f * s, whitePaint)
        canvas.drawCircle(cx - 5f * s, cy - 14f * s, 2.5f * s, blackPaint)
        canvas.drawCircle(cx + 5f * s, cy - 14f * s, 2.5f * s, blackPaint)

        // Nose
        canvas.drawCircle(cx, cy - 8f * s, 2f * s, nosePaint)

        // Whiskers
        canvas.drawLine(cx - 4f * s, cy - 7f * s, cx - 20f * s, cy - 11f * s, whiskerPaint)
        canvas.drawLine(cx - 4f * s, cy - 5f * s, cx - 20f * s, cy - 5f * s, whiskerPaint)
        canvas.drawLine(cx + 4f * s, cy - 7f * s, cx + 20f * s, cy - 11f * s, whiskerPaint)
        canvas.drawLine(cx + 4f * s, cy - 5f * s, cx + 20f * s, cy - 5f * s, whiskerPaint)

        // Label "cat"
        val labelPaint = Paint().apply {
            color = Color.rgb(100, 60, 30); textSize = 10f * s; textAlign = Paint.Align.CENTER
            isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("cat", cx, cy + 10f * s, labelPaint)

        canvas.restore()
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
        drawCatBody(canvas, Color.rgb(210, 170, 110), Color.rgb(230, 190, 130))
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
        drawCatBody(canvas, Color.rgb(50, 50, 50), Color.rgb(30, 30, 30))
        // Bandana
        val cx = x + width / 2f
        val cy = y + jumpZ - height / 2f
        val maskPaint = Paint().apply { color = Color.rgb(150, 0, 0); isAntiAlias = true }
        canvas.drawRect(cx - 10f, cy - 18f, cx + 10f, cy - 12f, maskPaint)
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
        val cx = x + width / 2f
        val cy = y - height / 2f
        val shadowP = Paint().apply { color = Color.argb(50, 0, 0, 0); isAntiAlias = true }
        canvas.drawOval(RectF(cx - 22f, y - 5f, cx + 22f, y + 7f), shadowP)

        val tankPaint = Paint().apply { color = Color.rgb(80, 80, 100); isAntiAlias = true }
        val barrelPaint = Paint().apply {
            color = Color.rgb(100, 100, 120); strokeWidth = 8f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(x, cy, x + width, y), 8f, 8f, tankPaint)
        canvas.drawCircle(cx, cy - 6f, 15f, tankPaint)
        val barrelDir = if (facingRight) 1f else -1f
        canvas.drawLine(cx, cy - 6f, cx + 35f * barrelDir, cy - 6f, barrelPaint)

        if (isCharging) {
            val chargePaint = Paint().apply {
                color = Color.RED
                alpha = (150 + (sin(chargeTimer.toDouble() * 20) * 100).toInt()).coerceIn(0, 255)
                isAntiAlias = true
            }
            canvas.drawCircle(cx + 35f * barrelDir, cy - 6f, 8f, chargePaint)
        }

        val eyePaint = Paint().apply { color = Color.RED; isAntiAlias = true }
        canvas.drawCircle(cx - 5f, cy - 10f, 3f, eyePaint)
        canvas.drawCircle(cx + 5f, cy - 10f, 3f, eyePaint)

        val earPaint = Paint().apply { color = Color.rgb(80, 80, 100); isAntiAlias = true }
        val ear1 = Path().apply { moveTo(cx - 12f, cy - 18f); lineTo(cx - 6f, cy - 30f); lineTo(cx, cy - 18f); close() }
        val ear2 = Path().apply { moveTo(cx, cy - 18f); lineTo(cx + 6f, cy - 30f); lineTo(cx + 12f, cy - 18f); close() }
        canvas.drawPath(ear1, earPaint); canvas.drawPath(ear2, earPaint)
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
        drawCatBody(canvas, Color.rgb(255, 180, 100), Color.rgb(255, 140, 60), 1.3f)
        val cx = x + width / 2f
        // Shirt
        val shirtPaint = Paint().apply { color = Color.rgb(200, 50, 50); isAntiAlias = true }
        canvas.drawRect(cx - 16f, y - height / 2f + 8f, cx + 16f, y - height / 2f + 22f, shirtPaint)
        // Phase & HP bar
        val textPaint = Paint().apply { color = Color.WHITE; textSize = 14f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        canvas.drawText("P$phase/$maxPhases", cx, y - height - 5f, textPaint)
        val barBg = Paint().apply { color = Color.DKGRAY }
        val barFg = Paint().apply { color = Color.RED }
        canvas.drawRect(cx - 25f, y - height, cx + 25f, y - height + 5f, barBg)
        canvas.drawRect(cx - 25f, y - height, cx - 25f + 50f * hp / 3f, y - height + 5f, barFg)
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
        drawCatBody(canvas, Color.rgb(50, 20, 50), Color.rgb(80, 0, 0), 1.7f)
        val cx = x + width / 2f
        val barBg = Paint().apply { color = Color.DKGRAY }
        val barFg = Paint().apply { color = Color.rgb(200, 0, 0) }
        canvas.drawRect(cx - 35f, y - height - 5f, cx + 35f, y - height, barBg)
        canvas.drawRect(cx - 35f, y - height - 5f, cx - 35f + 70f * hp / maxHp.toFloat(), y - height, barFg)
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
        val cy = y + jumpZ - height / 2f

        drawCatBody(canvas, Color.rgb(100, 50, 150), Color.rgb(130, 60, 180), 1.5f)

        val hatPaint = Paint().apply { color = Color.rgb(50, 0, 100); isAntiAlias = true }
        val starPaint = Paint().apply { color = Color.YELLOW; textSize = 16f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
        val hatPath = Path().apply {
            moveTo(cx - 18f, cy - 30f); lineTo(cx, cy - 60f); lineTo(cx + 18f, cy - 30f); close()
        }
        canvas.drawPath(hatPath, hatPaint)
        canvas.drawText("★", cx, cy - 38f, starPaint)

        val auraPaint = Paint().apply {
            color = Color.rgb(180, 100, 255)
            alpha = (80 + (sin(floatTimer.toDouble() * 4) * 40).toInt()).coerceIn(0, 255)
            style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true
        }
        canvas.drawCircle(cx, cy, 50f, auraPaint)

        val barBg = Paint().apply { color = Color.DKGRAY }
        val barFg = Paint().apply { color = Color.rgb(180, 0, 255) }
        canvas.drawRect(cx - 40f, y - height - 10f, cx + 40f, y - height - 3f, barBg)
        canvas.drawRect(cx - 40f, y - height - 10f, cx - 40f + 80f * hp / maxHp.toFloat(), y - height - 3f, barFg)
    }

    override fun getProjectiles(): List<Projectile> = pendingProjectiles.toList()
}
