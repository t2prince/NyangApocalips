package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.Camera
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.entity.Player
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.level.Level
import com.jamcat.nayngapo.ui.HUD
import com.jamcat.nayngapo.ui.TouchControls

class GamePlayScreen(
    private val gameView: GameView,
    private val stageNumber: Int
) : Screen {

    private val level = Level(stageNumber)
    private val player = Player(level.playerStartX, level.playerStartY)
    private val camera = Camera()
    private val touchControls = TouchControls()
    private val hud = HUD()

    private var respawnTimer = 0f
    private var waveTextTimer = 0f
    private var waveTextMessage = "Wave 1"

    // Floor tile paints
    private val tilePaint1 = Paint().apply { color = Color.rgb(210, 195, 170) }
    private val tilePaint2 = Paint().apply { color = Color.rgb(200, 185, 160) }
    private val tileLinePaint = Paint().apply {
        color = Color.rgb(180, 165, 140); strokeWidth = 1f
    }

    // Wave text paint
    private val waveTextPaint = Paint().apply {
        color = Color.WHITE; textSize = 40f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    // Hit effect
    private data class HitEffect(var x: Float, var y: Float, var timer: Float, val text: String)
    private val hitEffects = mutableListOf<HitEffect>()

    init {
        showWaveText("Wave 1 - START!")
    }

    private fun showWaveText(msg: String) {
        waveTextMessage = msg
        waveTextTimer = 2f
    }

    override fun update(deltaTime: Float) {
        // Process input
        processInput()

        // Respawn logic
        if (player.needsRespawn()) {
            respawnTimer += deltaTime
            if (respawnTimer > 1.5f) {
                player.respawn(level.playerStartX, level.playerStartY)
                respawnTimer = 0f
            }
            return
        }

        if (player.isDead) {
            gameView.showGameOver(player.score)
            return
        }

        // Update player
        player.update(deltaTime)

        // Update enemies
        val deadBefore = level.enemies.count { it.isDead }
        for (enemy in level.enemies) {
            if (!enemy.isDead) {
                enemy.update(deltaTime, player.x, player.y)
                // Collect projectiles
                for (proj in enemy.getProjectiles()) {
                    level.projectiles.add(proj)
                }
            }
        }

        // Update projectiles
        level.projectiles.forEach { it.update(deltaTime) }
        level.projectiles.removeAll { !it.isAlive }

        // === Collision Detection ===

        // Player attack -> enemies
        val attackBox = player.getAttackHitBox()
        if (attackBox != null) {
            for (enemy in level.enemies) {
                if (!enemy.isDead && RectF.intersects(attackBox, enemy.hitBox)) {
                    val dmg = player.getCurrentAttackPower()
                    enemy.takeDamage(dmg)
                    player.specialGauge = (player.specialGauge + Constants.SPECIAL_GAIN_PER_HIT)
                        .coerceAtMost(Constants.SPECIAL_MAX)
                    hitEffects.add(HitEffect(enemy.x + enemy.width / 2f, enemy.y - enemy.height, 0.5f, "$dmg"))

                    if (enemy.isDead) {
                        player.score += 100
                        player.killCount++
                        player.specialGauge = (player.specialGauge + Constants.SPECIAL_GAIN_PER_KILL)
                            .coerceAtMost(Constants.SPECIAL_MAX)
                        level.onEnemyDefeated()

                        // Level up every 10 kills
                        if (player.killCount % 10 == 0) {
                            player.level++
                            player.maxHp++
                            player.hp = player.maxHp
                            hitEffects.add(HitEffect(player.x, player.y - player.height, 1f, "LEVEL UP!"))
                        }
                    }
                }
            }
        }

        // Enemy -> Player collision
        if (!player.isInvincible && !player.isJumping) {
            for (enemy in level.enemies) {
                if (!enemy.isDead && RectF.intersects(player.hitBox, enemy.hitBox)) {
                    player.takeDamage(enemy.attackPower)
                    hitEffects.add(HitEffect(player.x, player.y - player.height, 0.5f, "HIT!"))
                    break
                }
            }
        }

        // Projectile -> Player
        if (!player.isInvincible && !player.isJumping) {
            for (proj in level.projectiles) {
                if (!proj.isPlayerOwned && proj.isAlive && RectF.intersects(player.hitBox, proj.hitBox)) {
                    player.takeDamage(proj.damage)
                    proj.isAlive = false
                    hitEffects.add(HitEffect(player.x, player.y - player.height, 0.5f, "${proj.damage}!"))
                }
            }
        }

        // Player -> Prison collision (attack to open)
        if (attackBox != null) {
            for (prison in level.prisons) {
                if (!prison.isOpen && RectF.intersects(attackBox, prison.hitBox)) {
                    prison.open()
                    player.rescueCount += prison.rescueCount
                    player.score += 500
                    hitEffects.add(HitEffect(prison.x, prison.y - prison.height, 1f, "+${prison.rescueCount} RESCUED!"))
                }
            }
        }

        // Check wave completion
        val deadAfter = level.enemies.count { it.isDead }
        if (deadAfter > deadBefore || level.enemies.isEmpty()) {
            if (level.enemies.all { it.isDead }) {
                if (level.stageCleared) {
                    gameView.showStageClear(stageNumber, player.score, player.rescueCount)
                    return
                } else {
                    showWaveText("Wave ${level.currentWave + 1}")
                }
            }
        }

        // Update camera
        camera.update(
            player.x + player.width / 2f, player.y,
            level.areaWidth, level.areaHeight,
            gameView.screenWidth.toFloat(), gameView.screenHeight.toFloat()
        )

        // Update hit effects
        hitEffects.forEach { it.timer -= deltaTime; it.y -= 30f * deltaTime }
        hitEffects.removeAll { it.timer <= 0f }

        // Wave text timer
        if (waveTextTimer > 0f) waveTextTimer -= deltaTime
    }

    private fun processInput() {
        val input = touchControls.inputState

        // Horizontal
        if (input.moveLeft && input.moveRight) {
            player.stopHorizontal()
        } else if (input.moveLeft) {
            player.moveLeft()
        } else if (input.moveRight) {
            player.moveRight()
        } else {
            player.stopHorizontal()
        }

        // Vertical
        if (input.moveUp && input.moveDown) {
            player.stopVertical()
        } else if (input.moveUp) {
            player.moveUp()
        } else if (input.moveDown) {
            player.moveDown()
        } else {
            player.stopVertical()
        }

        if (input.jump) {
            player.jump()
            input.jump = false
        }
        if (input.attack) {
            player.performAttack()
            input.attack = false
        }
        if (input.special) {
            player.performSpecial()
            input.special = false
        }
    }

    override fun draw(canvas: Canvas) {
        val sw = gameView.screenWidth
        val sh = gameView.screenHeight

        // Draw floor tiles
        drawFloor(canvas)

        // Apply camera transform for game objects
        canvas.save()
        canvas.translate(-camera.x, -camera.y)

        // Collect all drawable entities and sort by y for depth
        data class Drawable(val y: Float, val draw: () -> Unit)
        val drawables = mutableListOf<Drawable>()

        // Prisons
        for (prison in level.prisons) {
            drawables.add(Drawable(prison.y) { prison.draw(canvas) })
        }

        // Enemies
        for (enemy in level.enemies) {
            if (!enemy.isDead) {
                drawables.add(Drawable(enemy.y) { enemy.draw(canvas) })
            }
        }

        // Player
        if (!player.isDead) {
            drawables.add(Drawable(player.y) { player.draw(canvas) })
        }

        // Sort by y (top to bottom)
        drawables.sortBy { it.y }
        for (d in drawables) d.draw()

        // Projectiles (on top)
        for (proj in level.projectiles) {
            if (proj.isAlive) proj.draw(canvas)
        }

        // Hit effects
        for (effect in hitEffects) {
            val effectPaint = Paint().apply {
                color = if (effect.text.contains("LEVEL") || effect.text.contains("RESCUED"))
                    Color.rgb(255, 220, 50) else Color.WHITE
                textSize = if (effect.text.length > 3) 22f else 26f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
                alpha = (effect.timer / 0.5f * 255).toInt().coerceIn(0, 255)
            }
            canvas.drawText(effect.text, effect.x, effect.y, effectPaint)
        }

        canvas.restore()

        // Draw HUD (screen space)
        hud.draw(canvas, player, level, sw, sh)

        // Draw controls (screen space)
        touchControls.draw(canvas, sw, sh)

        // Wave announcement text
        if (waveTextTimer > 0f) {
            waveTextPaint.alpha = ((waveTextTimer / 2f).coerceIn(0f, 1f) * 255).toInt()
            canvas.drawText(waveTextMessage, sw / 2f, sh / 2f - 50f, waveTextPaint)
        }
    }

    private fun drawFloor(canvas: Canvas) {
        val sw = gameView.screenWidth.toFloat()
        val sh = gameView.screenHeight.toFloat()
        val tileSize = Constants.TILE_SIZE

        // Perspective projection: tiles at top are narrower (vanishing point effect)
        val vanishX = sw / 2f  // Vanishing point X (center)
        val horizon = -sh * 0.3f  // Horizon line (above screen)
        val rows = 20

        for (row in 0 until rows) {
            val t0 = row.toFloat() / rows
            val t1 = (row + 1).toFloat() / rows
            // Y positions on screen (perspective mapped)
            val y0 = horizon + (sh - horizon) * t0 * t0
            val y1 = horizon + (sh - horizon) * t1 * t1
            // Width narrows toward top (perspective)
            val halfW0 = sw * 0.5f + (sw * 1.5f - sw * 0.5f) * t0
            val halfW1 = sw * 0.5f + (sw * 1.5f - sw * 0.5f) * t1
            val left0 = vanishX - halfW0
            val right0 = vanishX + halfW0
            val left1 = vanishX - halfW1
            val right1 = vanishX + halfW1

            val cols = 12
            for (col in 0 until cols) {
                val cx0 = col.toFloat() / cols
                val cx1 = (col + 1).toFloat() / cols
                val paint = if ((row + col) % 2 == 0) tilePaint1 else tilePaint2

                // Trapezoid for each tile
                val path = Path().apply {
                    moveTo(left0 + (right0 - left0) * cx0, y0)
                    lineTo(left0 + (right0 - left0) * cx1, y0)
                    lineTo(left1 + (right1 - left1) * cx1, y1)
                    lineTo(left1 + (right1 - left1) * cx0, y1)
                    close()
                }
                canvas.drawPath(path, paint)
                // Grid line on right edge
                canvas.drawLine(
                    left0 + (right0 - left0) * cx1, y0,
                    left1 + (right1 - left1) * cx1, y1,
                    tileLinePaint
                )
            }
            // Horizontal grid line
            canvas.drawLine(left1, y1, right1, y1, tileLinePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchControls.onTouchEvent(event, gameView.screenWidth, gameView.screenHeight)
    }
}
