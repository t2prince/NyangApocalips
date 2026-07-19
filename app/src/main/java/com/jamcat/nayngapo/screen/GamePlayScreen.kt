package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.Camera
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.entity.Player
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.engine.Terrain
import com.jamcat.nayngapo.level.Level
import com.jamcat.nayngapo.ui.HUD
import com.jamcat.nayngapo.ui.TouchControls

class GamePlayScreen(
    private val gameView: GameView,
    private val stageNumber: Int
) : Screen {

    private val level = Level(stageNumber)
    private val player = Player(level.playerStartX, Constants.GROUND_Y)
    private val camera = Camera()
    private val touchControls = TouchControls()
    private val hud = HUD()

    private var respawnTimer = 0f
    private var waveTextTimer = 0f
    private var waveTextMessage = ""

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
        camera.forwardOnly = true   // 플랫포머식: 카메라가 뒤로 돌아가지 않는다
        showWaveText("STAGE $stageNumber - START!")
    }

    private var elapsed = 0f   // GO → 표시 깜빡임용

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
                // 진행 지점(현재 화면 왼쪽)에서 부활: 한 방향 카메라라 시작점으로 돌아갈 수 없다
                player.respawn(camera.x + 150f, Constants.GROUND_Y)
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
                    player.triggerHitSpark()
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

        // Enemy -> Player collision (플레이어 히트박스는 프레임당 1회만 계산)
        val playerBox = player.hitBox
        if (!player.isInvincible && !player.isJumping) {
            for (enemy in level.enemies) {
                if (!enemy.isDead && RectF.intersects(playerBox, enemy.hitBox)) {
                    player.takeDamage(enemy.attackPower)
                    hitEffects.add(HitEffect(player.x, player.y - player.height, 0.5f, "HIT!"))
                    break
                }
            }
        }

        // Projectile -> Player
        if (!player.isInvincible && !player.isJumping) {
            for (proj in level.projectiles) {
                if (!proj.isPlayerOwned && proj.isAlive && RectF.intersects(playerBox, proj.hitBox)) {
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

        // 클리어 판정: 필드의 냥이를 전부 무찌르면 스테이지 클리어
        if (level.stageCleared) {
            GameProgress.onStageCleared(stageNumber)
            gameView.showStageClear(
                stageNumber, player.score, player.rescueCount,
                level.defeatedEnemies, level.totalEnemies, player.level
            )
            return
        }

        // Update camera (횡뷰: 세로 스크롤 없음 — levelHeight를 화면 높이로 넘겨 y=0 고정)
        camera.update(
            player.x + player.width / 2f, gameView.screenHeight / 2f,
            level.areaWidth, gameView.screenHeight.toFloat(),
            gameView.screenWidth.toFloat(), gameView.screenHeight.toFloat()
        )

        // 한 방향 진행: 화면 왼쪽 밖(지나온 구간)으로는 되돌아갈 수 없다
        if (player.x < camera.x + 20f) {
            player.x = camera.x + 20f
        }

        elapsed += deltaTime

        // Update hit effects
        hitEffects.forEach { it.timer -= deltaTime; it.y -= 30f * deltaTime }
        hitEffects.removeAll { it.timer <= 0f }

        // Wave text timer
        if (waveTextTimer > 0f) waveTextTimer -= deltaTime
    }

    private fun processInput() {
        val input = touchControls.inputState

        // 횡뷰 플랫포머: 좌우 이동만, 위 방향 입력은 점프로 사용
        var dirX = 0f
        if (input.moveLeft) dirX -= 1f
        if (input.moveRight) dirX += 1f
        player.setMovement(dirX, 0f)

        if (input.jump || input.moveUp) {
            player.jump()
            input.jump = false
        }
        if (input.attack) {
            if (player.performAttack()) SoundManager.playAttack()
            input.attack = false
        }
        if (input.special) {
            if (player.performSpecial()) SoundManager.playSpecial()
            input.special = false
        }
    }

    override fun draw(canvas: Canvas) {
        val sw = gameView.screenWidth
        val sh = gameView.screenHeight

        // 배경(하늘) → 바닥 순서로 그린다: 잔디가 하늘 위에 겹치도록
        drawSky(canvas)
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

        // Hit effects (재사용 페인트에 색/크기/알파만 갱신)
        for (effect in hitEffects) {
            effectPaint.color = if (effect.text.contains("LEVEL") || effect.text.contains("RESCUED"))
                Color.rgb(255, 220, 50) else Color.WHITE
            effectPaint.textSize = if (effect.text.length > 3) 22f else 26f
            effectPaint.alpha = (effect.timer / 0.5f * 255).toInt().coerceIn(0, 255)
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

        // 진행 유도: 화면 안에 살아있는 냥이가 없고 앞쪽에 남아있으면 GO → 표시
        val viewRight = camera.x + sw
        val aliveOnScreen = level.enemies.any { !it.isDead && it.x + it.width > camera.x && it.x < viewRight }
        val aliveAhead = level.enemies.any { !it.isDead && it.x >= viewRight }
        if (!aliveOnScreen && aliveAhead) {
            val blink = (kotlin.math.sin(elapsed * 6.0) * 0.5 + 0.5).toFloat()
            goPaint.alpha = (110 + 145 * blink).toInt()
            canvas.drawText("GO →", sw - 40f, sh / 2f, goPaint)
        }
    }

    // 매 프레임 재사용하는 페인트들
    private val effectPaint = Paint().apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val goPaint = Paint().apply {
        color = Color.rgb(255, 220, 50); textSize = 52f; textAlign = Paint.Align.RIGHT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }
    private val floorSrcRect = Rect()
    private val floorDstRect = RectF()

    private var skyShader: Paint? = null

    /** 만화풍 맑은 하늘: 구름 없는 세로 그라데이션 */
    private fun drawSky(canvas: Canvas) {
        val sw = gameView.screenWidth.toFloat()
        val sh = gameView.screenHeight.toFloat()
        if (skyShader == null) {
            skyShader = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, sh,
                    Color.rgb(64, 165, 235),    // 위: 진한 하늘색
                    Color.rgb(188, 230, 252),   // 지평선: 옅은 하늘색
                    Shader.TileMode.CLAMP
                )
            }
        }
        canvas.drawRect(0f, 0f, sw, sh, skyShader!!)
    }

    /** 만화풍 잔디+흙 스트립을 세로 슬라이스로 잘라 울퉁불퉁한 지형 표면을 따라 깐다 */
    private fun drawFloor(canvas: Canvas) {
        val sw = gameView.screenWidth.toFloat()
        val sh = gameView.screenHeight.toFloat()
        val grassOverlap = 42f                 // 잔디 이파리가 지면 표면 위로 솟는 높이
        val bmp = SpriteManager.groundTile
        // 화면이 지면 기준선보다 낮은 기기에서도 루프가 무한 반복하지 않도록 최소 높이를 보장
        val baseStripH = (sh - (Constants.GROUND_Y - grassOverlap)).coerceAtLeast(80f)
        val tileW = baseStripH * bmp.width / bmp.height   // 가로 반복 주기 (기준 비율)

        // 비트맵을 세로 32조각으로 나눠, 각 조각을 해당 위치의 지형 높이에 맞춰 그린다
        val slices = 32
        val srcSliceW = bmp.width / slices
        val dstSliceW = tileW / slices

        var worldX = kotlin.math.floor(camera.x / dstSliceW) * dstSliceW
        val end = camera.x + sw + dstSliceW
        while (worldX < end) {
            val gy = Terrain.groundY(worldX + dstSliceW / 2f)
            val tileIndex = kotlin.math.floor(worldX / dstSliceW).toLong()
            val srcIdx = (((tileIndex % slices) + slices) % slices).toInt()
            floorSrcRect.set(srcIdx * srcSliceW, 0, (srcIdx + 1) * srcSliceW, bmp.height)
            floorDstRect.set(worldX - camera.x, gy - grassOverlap, worldX - camera.x + dstSliceW + 1f, sh)
            canvas.drawBitmap(bmp, floorSrcRect, floorDstRect, null)
            worldX += dstSliceW
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchControls.onTouchEvent(event, gameView.screenWidth, gameView.screenHeight)
    }
}
