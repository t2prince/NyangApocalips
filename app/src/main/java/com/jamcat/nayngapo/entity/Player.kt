package com.jamcat.nayngapo.entity

import android.graphics.*
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.SpriteManager

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

        // Select sprite based on state
        val sprite = when {
            isAttacking && useSpecial -> SpriteManager.playerKick
            isAttacking -> SpriteManager.playerPunch
            else -> SpriteManager.playerIdle
        }

        val drawSprite = if (!facingRight) SpriteManager.getFlipped(sprite) else sprite
        val sx = cx - drawSprite.width / 2f
        val sy = drawY - drawSprite.height + 10f
        canvas.drawBitmap(drawSprite, sx, sy, null)

        // Hit effect overlay when attacking
        if (isAttacking) {
            val hitSprite = SpriteManager.hitEffect
            val hx = if (facingRight) cx + 30f else cx - 30f - hitSprite.width
            canvas.drawBitmap(hitSprite, hx, drawY - height / 2f - 10f, null)
        }

        // HP label
        val hpPaint = Paint().apply {
            color = Color.BLACK; textSize = 16f; textAlign = Paint.Align.CENTER
            isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("$hp/$maxHp", cx + 30f, drawY - drawSprite.height + 5f, hpPaint)
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
