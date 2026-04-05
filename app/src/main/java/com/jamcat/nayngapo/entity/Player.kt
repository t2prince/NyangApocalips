package com.jamcat.nayngapo.entity

import android.graphics.*
import com.jamcat.nayngapo.engine.Constants

class Player(var x: Float, var y: Float) {
    val width = Constants.PLAYER_WIDTH
    val height = Constants.PLAYER_HEIGHT

    var hp = 5
    var maxHp = 5
    var lives = 5
    var attackPower = 10
    var specialPower = 20
    var specialGauge = 0f
    var level = 1
    var killCount = 0

    var velocityX = 0f
    var velocityY = 0f
    var jumpZ = 0f
    var jumpVelocity = 0f
    var isJumping = false
    var facingRight = true

    var isAttacking = false
    var attackTimer = 0f
    private var useSpecial = false

    var isInvincible = false
    var invincibilityTimer = 0f

    var isDead = false
    var score = 0
    var rescueCount = 0

    val hitBox: RectF get() = RectF(x, y - 20f, x + width, y + 10f)

    // Stick figure paints
    private val bodyPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
        isAntiAlias = true; style = Paint.Style.STROKE
    }
    private val headPaint = Paint().apply {
        color = Color.BLACK; isAntiAlias = true; style = Paint.Style.FILL
    }
    private val punchPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val kickPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val specialGlowPaint = Paint().apply {
        color = Color.rgb(255, 200, 50); strokeWidth = 10f; strokeCap = Paint.Cap.ROUND
        isAntiAlias = true; alpha = 200
    }
    private val impactPaint = Paint().apply {
        color = Color.rgb(255, 255, 200); isAntiAlias = true; style = Paint.Style.FILL
    }
    private val shadowPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0); isAntiAlias = true
    }

    fun update(deltaTime: Float) {
        if (isDead) return

        x += velocityX * deltaTime
        y += velocityY * deltaTime
        x = x.coerceIn(Constants.PLAY_AREA_LEFT + 10f, Constants.PLAY_AREA_RIGHT - width - 10f)
        y = y.coerceIn(Constants.PLAY_AREA_TOP + 40f, Constants.PLAY_AREA_BOTTOM - 10f)

        if (isJumping) {
            jumpVelocity += Constants.JUMP_GRAVITY * deltaTime
            jumpZ += jumpVelocity * deltaTime
            if (jumpZ >= 0f) {
                jumpZ = 0f; jumpVelocity = 0f; isJumping = false
            }
        }

        if (isAttacking) {
            attackTimer -= deltaTime
            if (attackTimer <= 0f) { isAttacking = false; useSpecial = false }
        }

        if (isInvincible) {
            invincibilityTimer -= deltaTime
            if (invincibilityTimer <= 0f) isInvincible = false
        }
    }

    fun draw(canvas: Canvas) {
        if (isDead) return
        if (isInvincible && ((invincibilityTimer * 8).toInt() % 2 == 0)) return

        val drawY = y + jumpZ
        val cx = x + width / 2f

        // Shadow
        canvas.drawOval(RectF(cx - 15f, y - 3f, cx + 15f, y + 5f), shadowPaint)

        canvas.save()
        if (!facingRight) {
            canvas.scale(-1f, 1f, cx, drawY - height / 2f)
        }

        val headY = drawY - height + 10f
        val shoulderY = headY + 14f
        val hipY = drawY - height / 2f + 12f
        val footY = drawY

        // Head
        canvas.drawCircle(cx, headY, 9f, headPaint)

        // Body
        canvas.drawLine(cx, shoulderY, cx, hipY, bodyPaint)

        if (isAttacking && useSpecial) {
            // === KICK (special attack) ===
            // Back arm relaxed
            canvas.drawLine(cx, shoulderY + 2f, cx - 12f, hipY - 5f, bodyPaint)
            // Front arm guard
            canvas.drawLine(cx, shoulderY + 2f, cx + 18f, shoulderY + 8f, bodyPaint)

            // Standing leg
            canvas.drawLine(cx, hipY, cx - 8f, footY, bodyPaint)
            // Kicking leg - extended forward and up
            val kickProgress = (attackTimer / (Constants.ATTACK_DURATION * 2f)).coerceIn(0f, 1f)
            val kickExtend = if (kickProgress > 0.5f) (1f - kickProgress) * 2f else kickProgress * 2f
            val kickX = cx + 15f + kickExtend * 30f
            val kickY = hipY - 5f + kickExtend * 5f
            canvas.drawLine(cx, hipY, kickX, kickY, kickPaint)
            // Foot
            canvas.drawCircle(kickX, kickY, 4f, headPaint)

            // Glow effect on kick
            canvas.drawLine(cx + 10f, hipY, kickX + 5f, kickY, specialGlowPaint)
            // Impact star
            if (kickExtend > 0.7f) {
                drawImpactStar(canvas, kickX + 8f, kickY, 12f)
            }
        } else if (isAttacking) {
            // === PUNCH (normal attack) ===
            val punchProgress = (attackTimer / Constants.ATTACK_DURATION).coerceIn(0f, 1f)
            val punchExtend = if (punchProgress > 0.5f) (1f - punchProgress) * 2f else punchProgress * 2f

            // Punching arm - extends forward
            val fistX = cx + 12f + punchExtend * 28f
            val fistY = shoulderY + 5f
            canvas.drawLine(cx, shoulderY + 2f, fistX, fistY, punchPaint)
            // Fist
            canvas.drawCircle(fistX, fistY, 4f, headPaint)

            // Back arm guard position
            canvas.drawLine(cx, shoulderY + 2f, cx - 10f, shoulderY + 15f, bodyPaint)

            // Impact effect
            if (punchExtend > 0.7f) {
                drawImpactStar(canvas, fistX + 8f, fistY, 10f)
            }

            // Legs (fighting stance)
            canvas.drawLine(cx, hipY, cx + 12f, footY, bodyPaint)
            canvas.drawLine(cx, hipY, cx - 10f, footY, bodyPaint)
        } else {
            // === IDLE ===
            // Arms
            canvas.drawLine(cx, shoulderY + 2f, cx + 15f, hipY, bodyPaint)
            canvas.drawLine(cx, shoulderY + 2f, cx - 15f, hipY, bodyPaint)

            // Legs
            canvas.drawLine(cx, hipY, cx + 10f, footY, bodyPaint)
            canvas.drawLine(cx, hipY, cx - 10f, footY, bodyPaint)
        }

        canvas.restore()

        // HP label
        val hpPaint = Paint().apply {
            color = Color.BLACK; textSize = 16f; textAlign = Paint.Align.CENTER
            isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("$hp/$maxHp", cx + 30f, drawY - height + 5f, hpPaint)
    }

    private fun drawImpactStar(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        // Small impact burst
        val path = Path()
        for (i in 0 until 6) {
            val angle = Math.toRadians((i * 60.0))
            val outerX = cx + (kotlin.math.cos(angle) * size).toFloat()
            val outerY = cy + (kotlin.math.sin(angle) * size).toFloat()
            val innerAngle = Math.toRadians((i * 60.0 + 30.0))
            val innerX = cx + (kotlin.math.cos(innerAngle) * size * 0.4f).toFloat()
            val innerY = cy + (kotlin.math.sin(innerAngle) * size * 0.4f).toFloat()
            if (i == 0) path.moveTo(outerX, outerY) else path.lineTo(outerX, outerY)
            path.lineTo(innerX, innerY)
        }
        path.close()
        canvas.drawPath(path, impactPaint)
    }

    fun moveLeft() { velocityX = -Constants.PLAYER_SPEED; facingRight = false }
    fun moveRight() { velocityX = Constants.PLAYER_SPEED; facingRight = true }
    fun moveUp() { velocityY = -Constants.PLAYER_SPEED }
    fun moveDown() { velocityY = Constants.PLAYER_SPEED }
    fun stopHorizontal() { velocityX = 0f }
    fun stopVertical() { velocityY = 0f }
    fun stopMoving() { velocityX = 0f; velocityY = 0f }

    fun jump() {
        if (!isJumping) { isJumping = true; jumpVelocity = Constants.JUMP_VELOCITY }
    }

    fun performAttack() {
        if (!isAttacking) { isAttacking = true; attackTimer = Constants.ATTACK_DURATION; useSpecial = false }
    }

    fun performSpecial() {
        if (specialGauge >= Constants.SPECIAL_MAX && !isAttacking) {
            isAttacking = true; attackTimer = Constants.ATTACK_DURATION * 2f
            specialGauge = 0f; useSpecial = true
        }
    }

    fun isUsingSpecial(): Boolean = useSpecial
    fun getCurrentAttackPower(): Int = if (useSpecial) specialPower else attackPower

    fun getAttackHitBox(): RectF? {
        if (!isAttacking) return null
        val range = if (useSpecial) 90f else Constants.ATTACK_RANGE
        val atkHeight = 40f
        return if (facingRight) {
            RectF(x + width, y - atkHeight / 2, x + width + range, y + atkHeight / 2)
        } else {
            RectF(x - range, y - atkHeight / 2, x, y + atkHeight / 2)
        }
    }

    fun takeDamage(amount: Int) {
        if (isInvincible || isDead || isJumping) return
        hp -= 1
        isInvincible = true
        invincibilityTimer = Constants.INVINCIBILITY_DURATION
        velocityX = (if (facingRight) -1f else 1f) * 200f

        if (hp <= 0) { lives--; if (lives <= 0) isDead = true }
    }

    fun needsRespawn(): Boolean = hp <= 0 && lives > 0

    fun respawn(startX: Float, startY: Float) {
        x = startX; y = startY; hp = maxHp
        velocityX = 0f; velocityY = 0f; jumpZ = 0f; jumpVelocity = 0f
        isJumping = false; isAttacking = false
        isInvincible = true; invincibilityTimer = Constants.INVINCIBILITY_DURATION * 2f
    }

    fun reset(startX: Float, startY: Float) {
        respawn(startX, startY)
        lives = 5; score = 0; rescueCount = 0; specialGauge = 0f
        isDead = false; isInvincible = false; level = 1; killCount = 0
    }
}
