package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.*
import com.jamcat.nayngapo.entity.Projectile
import com.jamcat.nayngapo.ui.TouchControls
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 하늘 스테이지 (11 하나뿐, 기획2·기획6 — 12~20은 정글이다).
 * "날개달린 냥이를 타고 날아다니며 적군을 죽인다" — 인간이 날아냥이를 타고
 * 8방향으로 날며 ATK로 냥냥탄을, SP로 로켓을 쏜다. 하늘의 냥이를 전부 무찌르면 클리어.
 * 스테이지 끝에는 하늘의 왕 '왕왕냥이'(기획4의 거대 냥이)가 기다린다.
 */
class SkyStageScreen(
    private val gameView: GameView,
    private val stageNumber: Int
) : Screen {

    // ===== 하늘의 적 냥이 =====
    private enum class SkyKind { FLYING, LASER, GIANT }

    private inner class SkyEnemy(val kind: SkyKind, var x: Float, var y: Float) {
        var hp = when (kind) {
            SkyKind.FLYING -> 10
            SkyKind.LASER -> 15
            SkyKind.GIANT -> 150
        }
        val maxHp = hp
        val contactDamage = if (kind == SkyKind.GIANT) 3 else 1
        var isDead = false
        var facingRight = false
        var shootTimer = 1.5f + (Math.random() * 1.5f).toFloat()
        private val wavePhase = (Math.random() * Math.PI * 2).toFloat()
        private val waveSpeed = 1.6f + (Math.random() * 1.2f).toFloat()
        private var time = 0f
        var discovered = false

        val bitmap: Bitmap
            get() = when (kind) {
                SkyKind.FLYING -> SpriteManager.flyingCat
                SkyKind.LASER -> SpriteManager.laserCat
                SkyKind.GIANT -> giantBitmap
            }

        val width: Float get() = bitmap.width.toFloat()
        val height: Float get() = bitmap.height.toFloat()
        val hitBox: RectF get() = RectF(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f)

        fun update(deltaTime: Float) {
            time += deltaTime
            facingRight = player.x > x
            val dx = player.x - x
            val dist = abs(dx)

            when (kind) {
                SkyKind.FLYING -> {
                    // 둥실둥실 파도치며 플레이어에게 다가온다
                    if (dist < 1500f) x += kotlin.math.sign(dx) * 150f * deltaTime
                    y += sin((time * waveSpeed + wavePhase).toDouble()).toFloat() * 60f * deltaTime
                    val dy = player.y - y
                    if (dist < 700f) y += kotlin.math.sign(dy) * 90f * deltaTime
                }
                SkyKind.LASER -> {
                    // 거리를 유지하며 수평 광선을 쏜다
                    if (dist < 550f) x += kotlin.math.sign(-dx) * 120f * deltaTime
                    else if (dist < 1400f) x += kotlin.math.sign(dx) * 90f * deltaTime
                    y += sin((time * waveSpeed + wavePhase).toDouble()).toFloat() * 45f * deltaTime
                    shootTimer -= deltaTime
                    if (shootTimer <= 0f && dist < 1300f) {
                        shootTimer = 2.4f
                        val dir = if (facingRight) 1f else -1f
                        projectiles.add(Projectile(x, y - 6f, dir * 620f, 0f, 1, false,
                            tint = Color.rgb(70, 220, 120), radius = 7f))
                    }
                }
                SkyKind.GIANT -> {
                    // 하늘의 왕: 천천히 다가오며 부채꼴 탄막을 뿌린다
                    if (dist > 420f) x += kotlin.math.sign(dx) * 95f * deltaTime
                    y = skyMidY + sin((time * 0.8f).toDouble()).toFloat() * (flyMaxY - flyMinY) * 0.32f
                    shootTimer -= deltaTime
                    if (shootTimer <= 0f && dist < 1500f) {
                        shootTimer = 2.1f
                        val dir = if (facingRight) 1f else -1f
                        for (vy in intArrayOf(-220, -110, 0, 110, 220)) {
                            projectiles.add(Projectile(x + dir * width * 0.4f, y, dir * 520f, vy.toFloat(),
                                1, false, tint = Color.rgb(255, 120, 60), radius = 9f))
                        }
                    }
                }
            }
            y = y.coerceIn(flyMinY, flyMaxY)

            // 처음 화면 가까이 들어오면 도감에 등록
            if (!discovered && dist < gameView.screenWidth) {
                discovered = true
                val dexId = when (kind) {
                    SkyKind.FLYING -> "flying"; SkyKind.LASER -> "laser"; SkyKind.GIANT -> "giant"
                }
                if (GameProgress.discover(dexId)) {
                    effects.add(Effect(x, y - height, 1.3f, "도감에 등록!"))
                }
            }
        }

        fun takeDamage(amount: Int) {
            if (isDead) return
            hp -= amount
            if (hp <= 0) {
                isDead = true
                defeated++
                GameProgress.addKill()   // 미션 통계
                score += if (kind == SkyKind.GIANT) 2000 else 150
                SoundManager.playCatDie()
                effects.add(Effect(x, y, 0.7f, if (kind == SkyKind.GIANT) "왕왕냥이 격추!" else "격추!"))

                // 떨어뜨린 루비 (왕왕냥이는 보스라 5개)
                val reward =
                    if (kind == SkyKind.GIANT) com.jamcat.nayngapo.engine.Rewards.RUBY_BOSS
                    else com.jamcat.nayngapo.engine.Rewards.RUBY_NORMAL
                GameProgress.addRubies(reward)
                effects.add(Effect(x, y - 40f, 0.8f, "+$reward 루비"))
            }
        }

        fun draw(canvas: Canvas) {
            if (isDead) return
            val bmp = if (facingRight) bitmap else SpriteManager.getFlipped(bitmap)
            canvas.drawBitmap(bmp, x - bmp.width / 2f, y - bmp.height / 2f + sin((time * 3f).toDouble()).toFloat() * 5f, bitmapPaint)
            // 체력 게이지
            if (hp < maxHp) {
                val bw = width * 0.9f
                val by = y - height / 2f - 16f
                hpBgPaint.alpha = 190
                canvas.drawRect(x - bw / 2f, by, x + bw / 2f, by + 7f, hpBgPaint)
                canvas.drawRect(x - bw / 2f, by, x - bw / 2f + bw * (hp / maxHp.toFloat()), by + 7f, hpFgPaint)
            }
        }
    }

    // ===== 플레이어 (날개냥이 탑승) =====
    /** 이 스테이지에서 날아냥이를 새로 받았는가 (시작할 때 한 번 알려 준다) */
    private var mountGranted = false

    /**
     * 타고 나는 날아냥이.
     * 하늘 스테이지는 날아냥이를 타는 곳이라, 아직 한 마리도 없으면 여기서 그냥 준다.
     */
    /** 로봇을 타면(로봇 상점 '로봇 타기') 날아냥이 대신 로봇이 발에서 불을 뿜으며 난다 — 날아냥이가 필요 없다 (대표님 2026-10-10) */
    private val useRobot = com.jamcat.nayngapo.engine.RobotCatalog.rideLevel > 0
    /** 날아냥이가 주인공이면 스스로 난다 — 따로 타지 않고, 쓰러지면 그 냥이가 무덤 (대표님 2026-10-24) */
    private val selfFlying = !useRobot && Heroes.playerCatKind().let { it == com.jamcat.nayngapo.entity.AllyCat.Kind.FLYING || it == com.jamcat.nayngapo.entity.AllyCat.Kind.BEE }   // 벌냥이도 난다 (2026-10-24)
    private val mount: com.jamcat.nayngapo.engine.CatTeam.TeamCat? =
        if (useRobot) null
        else if (selfFlying) Heroes.playerCat()
        else com.jamcat.nayngapo.engine.CatTeam.flyingMount() ?: run {
            mountGranted = true
            com.jamcat.nayngapo.engine.CatTeam.recruit(com.jamcat.nayngapo.entity.AllyCat.Kind.FLYING)
        }
    private val mountName: String get() = mount?.name ?: "로봇 Lv ${GameProgress.robotLevel}"
    private val flamePath = Path()
    private val flameOuter = Paint().apply { color = Color.rgb(255, 130, 30); isAntiAlias = true }
    private val flameInner = Paint().apply { color = Color.rgb(255, 236, 110); isAntiAlias = true }
    private val robotDst = RectF()

    private inner class Rider {
        /** 로비에서 고른 인간 */
        val hero = Heroes.selected()
        var x = 500f
        var y = 0f
        var hp = 5
        val maxHp = 5
        var facingRight = true
        var invincible = 0f
        var time = 0f

        val hitBox: RectF get() = RectF(x - 70f, y - 60f, x + 70f, y + 55f)
        /** 벌냥이 주인공: 덮치기(공격) 남은 시간 · 돌진(필살기) 남은 시간과 방향 */
        var lungeT = 0f
        var dashT = 0f
        var dashDir = 1f

        fun update(deltaTime: Float) {
            time += deltaTime
            if (invincible > 0f) invincible -= deltaTime

            val input = touchControls.inputState
            var dx = 0f
            var dy = 0f
            if (input.moveLeft) dx -= 1f
            if (input.moveRight) dx += 1f
            if (input.moveUp) dy -= 1f
            if (input.moveDown) dy += 1f
            if (dx != 0f) facingRight = dx > 0f
            if (lungeT > 0f) lungeT -= deltaTime
            if (dashT > 0f) { dashT -= deltaTime; x += dashDir * com.jamcat.nayngapo.entity.Player.CAT_DASH_SPEED * deltaTime }
            x = (x + dx * 430f * deltaTime).coerceIn(camera + 90f, courseLength - 90f)
            y = (y + dy * 390f * deltaTime).coerceIn(flyMinY, flyMaxY)
        }

        fun hurt(amount: Int) {
            if (invincible > 0f) return
            // 타고 있는 날아냥이가 갑옷을 입었으면 등에 탄 사람도 덜 다친다
            val taken =
                if (mount?.hasArmor == true)
                    Math.round(amount * com.jamcat.nayngapo.engine.CatTeam.ARMOR_DAMAGE_FACTOR)
                        .coerceAtLeast(1)
                else amount
            hp -= taken
            invincible = 1.5f
            SoundManager.playPlayerHurt()
            if (GameProgress.shakeEnabled) shake = 0.3f
            effects.add(Effect(x, y - 80f, 0.6f, "HIT!"))
        }

        fun draw(canvas: Canvas) {
            if (falling < 0f && invincible > 0f && ((invincible * 12).toInt() % 2 == 0)) return   // 무적 깜빡임
            val bob = if (falling >= 0f) 0f else sin((time * 5f).toDouble()).toFloat() * 7f
            if (falling >= 0f) { canvas.save(); canvas.rotate(fallSpin.coerceAtMost(75f) * (if (facingRight) 1f else -1f), x, y) }
            val robot = SpriteManager.robotSide
            if (useRobot && robot != null) { drawOnRobot(canvas, robot, bob); if (falling >= 0f) canvas.restore(); return }
            val mountBmp = if (mount?.hasArmor == true) SpriteManager.flyingCatAllyArmor else SpriteManager.flyingCatAlly
            val cat = if (facingRight) mountBmp else SpriteManager.getFlipped(mountBmp)
            val rider = SpriteManager.playerIdle[((time * 6).toInt()) % SpriteManager.playerIdle.size]
            val riderBmp = if (facingRight) rider else SpriteManager.getFlipped(rider)
            if (selfFlying) {   // 주인공 날아냥이·벌냥이: 등에 탄 사람 없이 그 냥이만
                val selfBmp = if (Heroes.playerCatKind() == com.jamcat.nayngapo.entity.AllyCat.Kind.BEE) SpriteManager.beeCat else mountBmp
                val sb = if (facingRight) selfBmp else SpriteManager.getFlipped(selfBmp)
                val fdir = if (facingRight) 1f else -1f
                // 돌진 바람 줄기 (땅의 고양이 돌진과 같은 모양)
                if (dashT > 0f) for (k in 0..3) {
                    val yy = y + bob - 40f + k * 26f; val x0 = x - dashDir * 40f; val x1 = x0 - dashDir * (130f + k * 25f)
                    canvas.drawLine(x0, yy, x1, yy, windInk); canvas.drawLine(x0, yy, x1, yy, windPaint)
                }
                val lunge = if (lungeT > 0f) sin((1f - lungeT / BEE_LUNGE_TIME) * Math.PI.toFloat()) * 50f * fdir else 0f
                canvas.drawBitmap(sb, x - sb.width / 2f + lunge, y + bob - sb.height / 2f, bitmapPaint)
                if (falling >= 0f) canvas.restore()
                return
            }
            // 인간이 날개냥이 등에 올라탄 모습 — 로비에서 고른 인간(색·머리 장식) 그대로 (대표님 2026-09-20)
            val rw = riderBmp.width * 0.62f
            val rh = riderBmp.height * 0.62f
            // 갑옷 그림은 투구만큼 위로 길다 — 몸통 기준(맨몸 높이)으로 탑승 위치를 잡는다
            val bodyH = SpriteManager.flyingCatAlly.height.toFloat()
            val catTop = y + bob - bodyH / 2f
            riderDst.set(x - rw / 2f, catTop - rh + 22f, x + rw / 2f, catTop + 22f)
            com.jamcat.nayngapo.ui.drawHero(canvas, riderBmp, riderDst, hero, facingRight, time)
            canvas.drawBitmap(cat, x - cat.width / 2f, catTop + bodyH - cat.height, bitmapPaint)
            if (falling >= 0f) canvas.restore()
        }

        /** 로봇을 타고 난다: 조종석에 앉은 인간(테두리 아래는 잘라 그림) + 로봇 + 두 발 불꽃 */
        private fun drawOnRobot(canvas: Canvas, robot: android.graphics.Bitmap, bob: Float) {
            val rh = 170f; val rw = robot.width * rh / robot.height
            val bmp = if (facingRight) robot else SpriteManager.getFlipped(robot)
            robotDst.set(x - rw / 2f, y + bob - rh * 0.55f, x + rw / 2f, y + bob + rh * 0.45f)
            for (fxr in floatArrayOf(0.30f, 0.66f)) {
                val fx = if (facingRight) robotDst.left + rw * fxr else robotDst.right - rw * fxr
                val len = 40f + kotlin.math.abs(sin(time * 30f + fxr * 7f)) * 34f
                flamePath.reset(); flamePath.moveTo(fx - 20f, robotDst.bottom - 5f); flamePath.lineTo(fx + 20f, robotDst.bottom - 5f); flamePath.lineTo(fx, robotDst.bottom + len); flamePath.close()
                canvas.drawPath(flamePath, flameOuter)
                flamePath.reset(); flamePath.moveTo(fx - 10f, robotDst.bottom - 5f); flamePath.lineTo(fx + 10f, robotDst.bottom - 5f); flamePath.lineTo(fx, robotDst.bottom + len * 0.6f); flamePath.close()
                canvas.drawPath(flamePath, flameInner)
            }
            val rider = SpriteManager.playerIdle[0]
            val riderBmp = if (facingRight) rider else SpriteManager.getFlipped(rider)
            val hw = riderBmp.width * 0.62f; val hh = riderBmp.height * 0.62f
            val rimY = robotDst.top + rh * 0.16f
            riderDst.set(x - hw / 2f, rimY - hh * 0.45f, x + hw / 2f, rimY + hh * 0.55f)
            canvas.save(); canvas.clipRect(-1e6f, -1e6f, 1e6f, rimY)
            com.jamcat.nayngapo.ui.drawHero(canvas, riderBmp, riderDst, hero, facingRight, time)
            canvas.restore()
            canvas.drawBitmap(bmp, null, robotDst, bitmapPaint)
        }
    }

    // ===== 로켓 (기획4의 보스전 무기 — 하늘에서는 SP 버튼) =====
    private inner class Rocket(var x: Float, var y: Float, val dir: Float) {
        var alive = true
        private var time = 0f
        fun update(deltaTime: Float) {
            time += deltaTime
            x += dir * 980f * deltaTime
            if (x < camera - 200f || x > camera + gameView.screenWidth + 600f) alive = false
        }
        fun draw(canvas: Canvas) {
            // 몸통 + 뾰족코 + 불꽃
            rocketBody.set(x - 34f, y - 12f, x + 26f, y + 12f)
            canvas.drawRoundRect(rocketBody, 10f, 10f, rocketPaint)
            rocketPath.reset()
            rocketPath.moveTo(x + 26f, y - 12f); rocketPath.lineTo(x + 48f, y); rocketPath.lineTo(x + 26f, y + 12f)
            rocketPath.close()
            canvas.drawPath(rocketPath, rocketTipPaint)
            val f = 14f + (time * 40f) % 10f
            canvas.drawCircle(x - 38f, y, f * 0.55f, flamePaint)
        }
    }

    private class Effect(var x: Float, var y: Float, var timer: Float, val text: String)

    /** 부서진 창살 조각 (열린 감옥에서 떨어져 나간다) */
    private class Debris(var x: Float, var y: Float, val vx: Float, var vy: Float, var timer: Float, val spin: Float)

    /**
     * 하늘에 떠 있는 감옥 (기획: "하늘 스테이지에서도 감옥이 나오는데 하늘을 날고 있어").
     * 냥냥탄 3발을 맞거나 날아가서 부딪히면 열린다. 땅의 감옥 그림을 그대로 쓴다.
     */
    private inner class SkyPrison(
        val kind: com.jamcat.nayngapo.entity.Prison.Kind,
        val x: Float,
        private val baseY: Float,
        /** 스테이지 편집에서 고른 갇힌 냥이 (null 이면 무작위) */
        chosenCat: com.jamcat.nayngapo.entity.AllyCat.Kind? = null,
    ) {
        /** 갇힌 냥이 종류 (무작위면 날아냥이는 10스테이지 전용이라 여기서는 안 나온다) */
        val catKind: com.jamcat.nayngapo.entity.AllyCat.Kind? =
            if (kind == com.jamcat.nayngapo.entity.Prison.Kind.CAT) {
                chosenCat ?: listOf(
                    com.jamcat.nayngapo.entity.AllyCat.Kind.GREEN,
                    com.jamcat.nayngapo.entity.AllyCat.Kind.BLUE,
                    com.jamcat.nayngapo.entity.AllyCat.Kind.NORMAL,
                    com.jamcat.nayngapo.entity.AllyCat.Kind.GUN,
                ).let { it[(Math.random() * it.size).toInt()] }
            } else null

        /** 그림은 땅의 감옥을 그대로 빌려 쓴다 (창살·울상·열린 모습까지 같다) */
        private val cage = com.jamcat.nayngapo.entity.Prison(0f, 0f, kind, catKind)
        /** 갇힌 치즈냥이의 털 무늬 — 구출하면 같은 무늬로 팀에 간다 */
        val catCoat: com.jamcat.nayngapo.engine.CatCoat get() = cage.catCoat
        val rescueKind: com.jamcat.nayngapo.entity.AllyCat.Kind get() = cage.rescueKind

        private val scale = 0.88f
        val width = 100f * scale
        val height = 140f * scale

        var y = baseY
            private set
        private var time = (Math.random() * 6.0).toFloat()
        private var hits = 0
        val isOpen: Boolean get() = cage.isOpen
        val debris = mutableListOf<Debris>()

        val hitBox: RectF
            get() = RectF(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f)

        fun update(deltaTime: Float) {
            time += deltaTime
            // 구름 위에서 천천히 오르내린다
            y = (baseY + sin(time * 0.9) .toFloat() * 22f).coerceIn(flyMinY + 40f, flyMaxY - 40f)
            val it = debris.iterator()
            while (it.hasNext()) {
                val d = it.next()
                d.x += d.vx * deltaTime
                d.vy += 900f * deltaTime
                d.y += d.vy * deltaTime
                d.timer -= deltaTime
                if (d.timer <= 0f) it.remove()
            }
        }

        /** 한 방 맞았다 — 세 방이면 열린다. 열리는 순간 true */
        fun hit(): Boolean {
            if (cage.isOpen) return false
            hits++
            if (hits < 3) return false
            return forceOpen()
        }

        /** 부딪혔다 — 바로 열린다. 열리는 순간 true */
        fun forceOpen(): Boolean {
            if (cage.isOpen) return false
            cage.open()
            repeat(3) { i ->
                debris.add(
                    Debris(
                        x + (i - 1) * width * 0.28f, y,
                        (Math.random().toFloat() - 0.5f) * 260f, -160f - Math.random().toFloat() * 120f,
                        0.8f, (Math.random().toFloat() - 0.5f) * 700f
                    )
                )
            }
            return true
        }

        fun draw(canvas: Canvas) {
            // 떠 있어 보이게 아래에 작은 구름 세 덩이
            val cy = y + height * 0.5f
            canvas.drawCircle(x - width * 0.34f, cy + 8f, width * 0.22f, skyCloudPaint)
            canvas.drawCircle(x, cy + 14f, width * 0.29f, skyCloudPaint)
            canvas.drawCircle(x + width * 0.34f, cy + 8f, width * 0.22f, skyCloudPaint)

            canvas.save()
            canvas.translate(x - width / 2f, y + height / 2f)
            canvas.scale(scale, scale)
            cage.draw(canvas)
            canvas.restore()

            // 부서진 창살 조각
            for (d in debris) {
                canvas.save()
                canvas.rotate(d.spin * (0.8f - d.timer), d.x, d.y)
                canvas.drawRect(d.x - 4f, d.y - 16f, d.x + 4f, d.y + 16f, debrisPaint)
                canvas.restore()
            }
        }
    }

    // ===== 아군 냥이 — 뒤에서 구름을 타고 날아오며 지시 없이 알아서 쏜다 (대표님 2026-09-20) =====
    private inner class CloudAlly(val cat: com.jamcat.nayngapo.engine.CatTeam.TeamCat, val order: Int) {
        var x = 0f; var y = 0f; var facingRight = true
        private var bobPhase = order * 1.3f
        /** 땅에서와 같은 공격력 (5 + 레벨 보너스 + Lv업 보너스) */
        private val baseShotDamage = 5 + com.jamcat.nayngapo.engine.CatTeam.damageBonusFor(cat.level) + cat.attackBonus
        /** 묭묭 진화 중이면 하늘에서도 커지고 공격력 최소 50 (대표님 2026-10-09 「하늘 스테이지에서도 묭묭 진화가 돼」) */
        private val evolved: Boolean get() = com.jamcat.nayngapo.engine.CatTeam.evolveSecondsLeft > 0f
        private val shotDamage: Int get() = if (evolved) maxOf(baseShotDamage, com.jamcat.nayngapo.entity.AllyCat.EVOLVE_POWER) else baseShotDamage
        private var shotTimer = 0.6f + 0.25f * order   // 서로 박자를 어긋나게
        /** 사거리 안 적을 보면 알아서 쏜다 (1.1초마다) */
        fun tryShoot(deltaTime: Float) {
            if (cloudGone) return
            shotTimer -= deltaTime
            if (shotTimer > 0f) return
            val target = enemies.filter { !it.isDead && abs(it.x - x) < ALLY_SHOT_RANGE && abs(it.y - y) < 260f }
                .minByOrNull { abs(it.x - x) } ?: return
            shotTimer = 1.1f
            val dir = if (target.x > x) 1f else -1f
            facingRight = dir > 0f
            val vy = ((target.y - y) / (abs(target.x - x).coerceAtLeast(1f))) * 820f
            projectiles.add(Projectile(x + dir * 40f, y - 10f, dir * 820f, vy.coerceIn(-500f, 500f),
                shotDamage, true, tint = Color.rgb(255, 214, 90), radius = 5f))
        }
        fun update(deltaTime: Float, leaderX: Float, leaderY: Float, leaderRight: Boolean) {
            bobPhase += deltaTime * 2.6f
            val dir = if (leaderRight) 1f else -1f
            val tx = leaderX - dir * (150f + 95f * order)
            val ty = leaderY + 28f + 22f * order
            if (x == 0f && y == 0f) { x = tx; y = ty }
            x += (tx - x) * (1f - Math.exp((-6.0 * deltaTime))).toFloat()
            y += (ty - y) * (1f - Math.exp((-5.0 * deltaTime))).toFloat()
            facingRight = leaderRight
        }
        /** 구름이 사라졌다 — 이제부터 떨어진다 */
        var cloudGone = false
        private var vy = 0f
        fun loseCloud() { cloudGone = true; vy = -80f - 60f * order }
        fun updateFall(deltaTime: Float) { vy += 1400f * deltaTime; y += vy * deltaTime }

        fun draw(canvas: Canvas) {
            val bob = if (cloudGone) 0f else sin(bobPhase.toDouble()).toFloat() * 6f
            val base = allySpriteFor(cat)
            val bmp = if (facingRight) base else SpriteManager.getFlipped(base)
            val bodyH = allyBodyHeight(cat)
            val cy = y + bob
            // 구름: 냥이 발밑 (탑승 냥이가 쓰러지면 구름도 사라진다)
            if (!cloudGone) drawCloud(canvas, x, cy + bodyH * 0.42f, 34f, skyCloudPaint)
            if (cloudGone) { canvas.save(); canvas.rotate(120f * (vy / 800f).coerceIn(0f, 1.5f), x, cy) }
            if (evolved) {
                val sc = com.jamcat.nayngapo.entity.AllyCat.EVOLVE_SCALE
                val foot = cy + bodyH * 0.45f
                evolveDst.set(x - bmp.width * sc / 2f, foot - bmp.height * sc, x + bmp.width * sc / 2f, foot)
                canvas.drawBitmap(bmp, null, evolveDst, bitmapPaint)
            } else canvas.drawBitmap(bmp, x - bmp.width / 2f, cy + bodyH * 0.45f - bmp.height, bitmapPaint)
            if (cloudGone) canvas.restore()
        }
    }
    private val evolveDst = RectF()
    private val cloudAllies: List<CloudAlly> =
        com.jamcat.nayngapo.engine.CatTeam.party.filter { it.id != mount?.id }.mapIndexed { i, c -> CloudAlly(c, i) }

    private fun allySpriteFor(cat: com.jamcat.nayngapo.engine.CatTeam.TeamCat): Bitmap {
        val armor = cat.hasArmor
        return when (cat.kind) {
            com.jamcat.nayngapo.entity.AllyCat.Kind.GREEN -> if (armor) SpriteManager.greenCatAllyArmor else SpriteManager.greenCatAlly
            com.jamcat.nayngapo.entity.AllyCat.Kind.BLUE -> if (armor) SpriteManager.blueCatAllyArmor else SpriteManager.blueCatAlly
            com.jamcat.nayngapo.entity.AllyCat.Kind.NORMAL -> if (armor) cat.coat.allyArmor else cat.coat.ally
            com.jamcat.nayngapo.entity.AllyCat.Kind.FLYING -> if (armor) SpriteManager.flyingCatAllyArmor else SpriteManager.flyingCatAlly
            com.jamcat.nayngapo.entity.AllyCat.Kind.GUN -> if (armor) SpriteManager.gunCatAllyArmor else SpriteManager.gunCatAlly
            com.jamcat.nayngapo.entity.AllyCat.Kind.BOAT -> cat.coat.boatAlly
            else -> com.jamcat.nayngapo.entity.AllyCat.extraSprite(cat.kind, cat.coat) ?: cat.coat.ally
        }
    }
    /** 갑옷 그림은 위로 길어서 발 위치는 맨몸 높이로 잡는다 */
    private fun allyBodyHeight(cat: com.jamcat.nayngapo.engine.CatTeam.TeamCat): Float = when (cat.kind) {
        com.jamcat.nayngapo.entity.AllyCat.Kind.GREEN -> SpriteManager.greenCatAlly.height.toFloat()
        com.jamcat.nayngapo.entity.AllyCat.Kind.BLUE -> SpriteManager.blueCatAlly.height.toFloat()
        com.jamcat.nayngapo.entity.AllyCat.Kind.NORMAL -> cat.coat.ally.height.toFloat()
        com.jamcat.nayngapo.entity.AllyCat.Kind.FLYING -> SpriteManager.flyingCatAlly.height.toFloat()
        com.jamcat.nayngapo.entity.AllyCat.Kind.GUN -> SpriteManager.gunCatAlly.height.toFloat()
        com.jamcat.nayngapo.entity.AllyCat.Kind.BOAT -> cat.coat.boatAlly.height.toFloat()
        else -> (com.jamcat.nayngapo.entity.AllyCat.extraSprite(cat.kind, cat.coat) ?: cat.coat.ally).height.toFloat()
    }

    // ===== 상태 =====
    private val player = Rider()
    private val enemies = mutableListOf<SkyEnemy>()
    private val projectiles = mutableListOf<Projectile>()
    private val rockets = mutableListOf<Rocket>()
    private val effects = mutableListOf<Effect>()
    /** 하늘에 떠 있는 감옥들 */
    private val skyPrisons = mutableListOf<SkyPrison>()
    /** 이 스테이지에서 구한 인간·냥이 수 (결과 화면으로 넘긴다) */
    private var rescued = 0
    private val touchControls = TouchControls().apply { skyMode = true }
    /** 땅 스테이지와 같은 HUD (대표님 2026-09-20 「화면 구성 똑같게」) — 버튼은 하늘용 그대로 */
    private val hud = com.jamcat.nayngapo.ui.HUD()

    private var camera = 0f
    private var score = 0
    private var defeated = 0
    private var totalEnemies = 0
    private var shake = 0f
    private var fireTimer = 0f
    /** 벌냥이 주인공 (하늘에서도 덮치기·돌진) */
    private val beeSelf get() = selfFlying && Heroes.playerCatKind() == com.jamcat.nayngapo.entity.AllyCat.Kind.BEE
    /** 덮치기 공격력 = 땅에서와 같은 고양이 공격력 (10 + 레벨 보너스 + Lv업 아이템) */
    private val beeAttack: Int = Heroes.playerCat()?.let { 10 + com.jamcat.nayngapo.engine.CatTeam.damageBonusFor(it.level) + it.attackBonus } ?: 10
    private val dashHit = HashSet<SkyEnemy>()
    private val windPaint = Paint().apply { color = Color.WHITE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val windInk = Paint().apply { color = Color.rgb(120, 170, 220); strokeWidth = 14f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private var rocketCooldown = 0f
    private var clearTimer = -1f
    private var paused = false
    private var time = 0f
    /** 탑승한 날아냥이가 쓰러진 뒤 떨어지는 장면 (0 이상이면 진행 중, 초) — 대표님 2026-09-20 */
    private var falling = -1f
    private var fallVy = 0f
    private var fallSpin = 0f

    private val courseLength = com.jamcat.nayngapo.level.SkyLayout.courseLength(stageNumber)
    /** 구름 탄 아군이 적을 노리는 가로 사거리 */
    private val ALLY_SHOT_RANGE = 760f
    private var flyMinY = 130f
    private var flyMaxY = 800f
    private var skyMidY = 450f

    // 거대 보스 비트맵 (한 번만 확대 생성)
    private val giantBitmap: Bitmap by lazy {
        val src = SpriteManager.bossEvilCat
        Bitmap.createScaledBitmap(src, (src.width * 3.1f).toInt(), (src.height * 3.1f).toInt(), true)
    }

    // 구름 (씨앗 고정 배치, 두 겹 시차 스크롤)
    private val cloudSeeds = List(26) { i ->
        Triple((i * 731 % 1000) / 1000f, (i * 397 % 1000) / 1000f, 0.55f + (i * 157 % 100) / 160f)
    }

    // ===== 페인트 =====
    private val bitmapPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val cloudPaint = Paint().apply { color = Color.argb(235, 255, 255, 255); isAntiAlias = true }
    private val cloudFarPaint = Paint().apply { color = Color.argb(150, 244, 250, 255); isAntiAlias = true }
    private val sunPaint = Paint().apply { color = Color.rgb(255, 226, 120); isAntiAlias = true }
    private val groundFarPaint = Paint().apply { color = Color.rgb(132, 196, 96); isAntiAlias = true }
    private val hpBgPaint = Paint().apply { color = Color.argb(190, 50, 50, 50); isAntiAlias = true }
    private val hpFgPaint = Paint().apply { color = Color.rgb(90, 210, 90); isAntiAlias = true }
    private val heartPaint = Paint().apply { color = Color.rgb(235, 80, 90); isAntiAlias = true }
    private val heartEmptyPaint = Paint().apply {
        color = Color.argb(120, 255, 255, 255); style = Paint.Style.STROKE
        strokeWidth = 3f; isAntiAlias = true
    }
    private val hudTextPaint = Paint().apply {
        color = Color.WHITE; textSize = 34f; typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true; setShadowLayer(4f, 2f, 2f, Color.argb(160, 20, 40, 70))
    }
    private val hudRightPaint = Paint().apply {
        color = Color.WHITE; textSize = 34f; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.RIGHT; isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.argb(160, 20, 40, 70))
    }
    private val effectPaint = Paint().apply {
        color = Color.rgb(255, 240, 120); textSize = 36f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.argb(180, 30, 30, 30))
    }
    private val rocketPaint = Paint().apply { color = Color.rgb(210, 214, 222); isAntiAlias = true }
    private val rocketTipPaint = Paint().apply { color = Color.rgb(226, 74, 60); isAntiAlias = true }
    private val flamePaint = Paint().apply { color = Color.rgb(255, 160, 40); isAntiAlias = true }
    private val blastPaint = Paint().apply { color = Color.argb(160, 255, 190, 70); isAntiAlias = true }
    private val pauseVeilPaint = Paint().apply { color = Color.argb(185, 8, 10, 16) }
    private val pauseTextPaint = Paint().apply {
        color = Color.WHITE; textSize = 64f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val pauseHintPaint = Paint().apply {
        color = Color.argb(200, 220, 226, 236); textSize = 32f; textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val skyCloudPaint = Paint().apply { color = Color.argb(215, 255, 255, 255); isAntiAlias = true }
    private val debrisPaint = Paint().apply { color = Color.rgb(120, 124, 130); isAntiAlias = true }
    private val heartPath = Path()
    private val riderDst = RectF()
    private val rocketBody = RectF()
    private val rocketPath = Path()
    private val blasts = mutableListOf<Effect>()   // text 미사용, timer만

    init {
        spawnEnemies()
        if (mountGranted) {
            effects.add(
                Effect(player.x, player.y - 120f, 2.4f, "날아냥이가 함께한다! (고양이 관리에 합류)")
            )
        }
    }

    /**
     * 배치 목록(SkyLayout)대로 냥이와 감옥을 띄운다. 스테이지 편집(디버그 빌드)에서 저장한 배치가 있으면 그것을,
     * 없으면 예전과 같은 무작위 기본 배치를 쓴다 (대표님 2026-10-31 「11 스태이지도 편집 할수있개 만들어줘」).
     */
    private fun spawnEnemies() {
        val layout = (if (StageEdits.enabled) StageEdits.load(stageNumber) else null)
            ?: com.jamcat.nayngapo.level.SkyLayout.defaultLayout(stageNumber)
        val h = gameView.screenHeight.toFloat().takeIf { it > 0f } ?: com.jamcat.nayngapo.level.SkyLayout.NOMINAL_H
        // 이 스테이지에서 이미 냥이를 구했으면 냥이 감옥은 다시 안 나온다 (2026-10-25) — 인간 감옥만
        // 이미 깬 스테이지도 같다 (땅 스테이지를 다시 하면 감옥이 깨져 있는 것처럼)
        val noCatPrison = GameProgress.skyCatRescued(stageNumber) || GameProgress.isCleared(stageNumber)
        for (p in layout) {
            val y = com.jamcat.nayngapo.level.SkyLayout.yAt(p.y ?: 0.5f, h)
            when (p.type) {
                com.jamcat.nayngapo.level.PlaceType.FLYING -> enemies.add(SkyEnemy(SkyKind.FLYING, p.x, y))
                com.jamcat.nayngapo.level.PlaceType.LASER -> enemies.add(SkyEnemy(SkyKind.LASER, p.x, y))
                com.jamcat.nayngapo.level.PlaceType.BOSS_GIANT -> enemies.add(SkyEnemy(SkyKind.GIANT, p.x, y))
                com.jamcat.nayngapo.level.PlaceType.PRISON_HUMAN ->
                    skyPrisons.add(SkyPrison(com.jamcat.nayngapo.entity.Prison.Kind.HUMAN, p.x, y))
                com.jamcat.nayngapo.level.PlaceType.PRISON_CAT -> if (!noCatPrison)
                    skyPrisons.add(SkyPrison(com.jamcat.nayngapo.entity.Prison.Kind.CAT, p.x, y, p.catKind))
                else -> {}   // 하늘에 못 놓는 것 (땅 스테이지용)
            }
        }
        totalEnemies = enemies.size
    }

    /** 하늘의 감옥이 열렸다 — 인간은 구조 점수, 냥이는 팀 명단에 오른다 */
    private fun onSkyPrisonOpened(prison: SkyPrison) {
        score += 500
        SoundManager.playCatHit()
        if (GameProgress.shakeEnabled) shake = 0.2f
        if (prison.kind == com.jamcat.nayngapo.entity.Prison.Kind.HUMAN) {
            rescued += 3
            GameProgress.addRescuedHumans(3)   // 한 명마다 상점 천막이 열린다
            effects.add(Effect(prison.x, prison.y - 70f, 1.2f, "인간 3명 구출!"))
        } else {
            rescued += 1
            val kind = prison.rescueKind
            val cat = CatTeam.recruit(kind, prison.catCoat)
            GameProgress.markSkyCatRescued(stageNumber)
            effects.add(
                Effect(prison.x, prison.y - 70f, 1.8f, "${cat.name} 구출! 고양이 관리에서 출전시켜 주세요")
            )
        }
    }

    override fun update(deltaTime: Float) {
        if (paused) return
        time += deltaTime
        if (shake > 0f) shake -= deltaTime
        if (falling >= 0f) { updateFalling(deltaTime); return }

        val h = gameView.screenHeight.toFloat()
        flyMaxY = h - 320f
        skyMidY = (flyMinY + flyMaxY) / 2f
        player.y = if (player.y == 0f) skyMidY else player.y

        player.update(deltaTime)
        for (a in cloudAllies) { a.update(deltaTime, player.x, player.y, player.facingRight); a.tryShoot(deltaTime) }

        // 카메라: 플레이어를 화면 1/3 지점에 둔다
        camera = (player.x - gameView.screenWidth * 0.35f).coerceIn(0f, courseLength - gameView.screenWidth)

        // 발사
        fireTimer -= deltaTime
        if (beeSelf && touchControls.inputState.attack && fireTimer <= 0f) {
            // 벌냥이 주인공: 하늘에서도 땅에서처럼 앞으로 덮친다 (대표님 2026-10-24 「벌냥이가 하늘 스테이지에 가면 근접이야, 공격은 원래랑 똑같고 필살기도 똑같아」)
            fireTimer = 0.35f
            player.lungeT = BEE_LUNGE_TIME
            val dir = if (player.facingRight) 1f else -1f
            val box = RectF(player.x + (if (dir > 0f) 0f else -BEE_REACH), player.y - 80f, player.x + (if (dir > 0f) BEE_REACH else 0f), player.y + 80f)
            for (enemy in enemies) if (!enemy.isDead && RectF.intersects(box, enemy.hitBox)) enemy.takeDamage(beeAttack)
            for (prison in skyPrisons) if (!prison.isOpen && RectF.intersects(box, prison.hitBox) && prison.hit()) onSkyPrisonOpened(prison)
            SoundManager.playAttack()
        } else if (!beeSelf && touchControls.inputState.attack && fireTimer <= 0f) {
            fireTimer = 0.28f
            val dir = if (player.facingRight) 1f else -1f
            projectiles.add(Projectile(player.x + dir * 80f, player.y - 10f, dir * 950f, 0f,
                5, true, tint = Color.rgb(60, 60, 66), radius = 6f))
            SoundManager.playAttack()
        }
        if (rocketCooldown > 0f) rocketCooldown -= deltaTime
        touchControls.specialCooldown = rocketCooldown
        if (beeSelf && touchControls.inputState.special) {
            // 벌냥이 주인공 필살기 = 땅과 같은 돌진 (바람, 공격력 10, 쿨타임 15초)
            touchControls.inputState.special = false
            if (rocketCooldown <= 0f) {
                rocketCooldown = Constants.SPECIAL_COOLDOWN * 0.5f
                player.dashT = com.jamcat.nayngapo.entity.Player.CAT_DASH_TIME; player.dashDir = if (player.facingRight) 1f else -1f
                dashHit.clear()
                SoundManager.playSpecial()
            }
        }
        if (beeSelf && player.dashT > 0f) {
            for (enemy in enemies) if (!enemy.isDead && enemy !in dashHit && RectF.intersects(enemy.hitBox, player.hitBox)) {
                dashHit.add(enemy); enemy.takeDamage(com.jamcat.nayngapo.entity.Player.CAT_DASH_POWER)
            }
        }
        if (!beeSelf && touchControls.inputState.special) {
            touchControls.inputState.special = false
            if (rocketCooldown <= 0f) {
                rocketCooldown = Constants.SPECIAL_COOLDOWN * 0.5f
                rockets.add(Rocket(player.x, player.y, if (player.facingRight) 1f else -1f))
                SoundManager.playSpecial()
            }
        }

        // 적 갱신 + 접촉
        for (enemy in enemies) {
            if (enemy.isDead) continue
            enemy.update(deltaTime)
            if (RectF.intersects(enemy.hitBox, player.hitBox)) player.hurt(enemy.contactDamage)
        }

        // 하늘의 감옥: 떠다니다가 냥냥탄 3발이나 몸통 박치기로 열린다
        for (prison in skyPrisons) {
            prison.update(deltaTime)
            if (!prison.isOpen && RectF.intersects(prison.hitBox, player.hitBox)) {
                if (prison.forceOpen()) onSkyPrisonOpened(prison)
            }
        }

        // 탄 갱신 + 명중
        projectiles.forEach { it.update(deltaTime) }
        for (proj in projectiles) {
            if (!proj.isAlive) continue
            if (proj.isPlayerOwned) {
                var used = false
                for (enemy in enemies) {
                    if (!enemy.isDead && RectF.intersects(proj.hitBox, enemy.hitBox)) {
                        enemy.takeDamage(proj.damage); proj.isAlive = false; used = true; break
                    }
                }
                if (!used) {
                    // 빗나간 탄이 감옥을 때리면 창살이 흔들린다 (세 발이면 열린다)
                    for (prison in skyPrisons) {
                        if (prison.isOpen || !RectF.intersects(proj.hitBox, prison.hitBox)) continue
                        proj.isAlive = false
                        if (prison.hit()) onSkyPrisonOpened(prison)
                        break
                    }
                }
            } else if (RectF.intersects(proj.hitBox, player.hitBox)) {
                player.hurt(1); proj.isAlive = false
            }
        }
        projectiles.removeAll { !it.isAlive }

        // 로켓: 맞으면 큰 폭발 — 반경 안의 적을 한꺼번에 태운다
        rockets.forEach { it.update(deltaTime) }
        for (rocket in rockets) {
            if (!rocket.alive) continue
            val hit = enemies.firstOrNull {
                !it.isDead && it.hitBox.contains(rocket.x + rocket.dir * 40f, rocket.y)
            }
            if (hit != null) {
                rocket.alive = false
                blasts.add(Effect(rocket.x, rocket.y, 0.4f, ""))
                if (GameProgress.shakeEnabled) shake = 0.25f
                for (enemy in enemies) {
                    if (!enemy.isDead && hypot((enemy.x - rocket.x).toDouble(), (enemy.y - rocket.y).toDouble()) < 300.0) {
                        enemy.takeDamage(15)
                    }
                }
            }
        }
        rockets.removeAll { !it.alive }

        blasts.forEach { it.timer -= deltaTime }
        blasts.removeAll { it.timer <= 0f }
        effects.forEach { it.timer -= deltaTime; it.y -= 40f * deltaTime }
        effects.removeAll { it.timer <= 0f }

        // 죽음: 탑승한 날아냥이가 쓰러진다 → 구름이 사라지고 다 같이 떨어지는 장면 뒤 게임 오버
        if (player.hp <= 0) {
            startFalling()
            return
        }
        if (enemies.all { it.isDead }) {
            if (clearTimer < 0f) clearTimer = 1.1f
            clearTimer -= deltaTime
            if (clearTimer <= 0f) {
                GameProgress.onStageCleared(stageNumber)
                gameView.showStageClear(stageNumber, score, rescued, defeated, totalEnemies, 1)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        // 하늘 그라데이션 (높은 하늘 → 아래로 밝아진다)
        skyShader ?: run {
            skyShader = LinearGradient(0f, 0f, 0f, h,
                Color.rgb(88, 158, 236), Color.rgb(196, 232, 252), Shader.TileMode.CLAMP)
            skyPaint.shader = skyShader
        }
        canvas.drawRect(0f, 0f, w, h, skyPaint)
        canvas.drawCircle(w - 150f, 130f, 78f, sunPaint)

        val shakeX = if (shake > 0f) ((Math.random() - 0.5) * 14).toFloat() else 0f
        val shakeY = if (shake > 0f) ((Math.random() - 0.5) * 14).toFloat() else 0f

        // 저 아래 아득한 땅 (우리가 아주 높이 있다는 느낌)
        canvas.drawRect(0f, h - 70f, w, h, groundFarPaint)

        // 구름 두 겹 (기획2의 뭉게구름)
        for ((i, seed) in cloudSeeds.withIndex()) {
            val (sx, sy, s) = seed
            val far = i % 2 == 0
            val speed = if (far) 0.35f else 0.7f
            val cx = ((sx * (courseLength + 2000f) - camera * speed) % (w + 700f) + w + 700f) % (w + 700f) - 350f
            val cy = 90f + sy * (h - 420f)
            drawCloud(canvas, cx, cy, 62f * s, if (far) cloudFarPaint else cloudPaint)
        }

        canvas.save()
        canvas.translate(-camera + shakeX, shakeY)

        // 떠 있는 감옥은 적보다 뒤에 그린다 (앞을 지나가는 냥이가 가려지지 않게)
        for (prison in skyPrisons) prison.draw(canvas)
        for (enemy in enemies) enemy.draw(canvas)
        for (a in cloudAllies) a.draw(canvas)   // 뒤에서 따라오는 아군 — 탑승자보다 뒤에 그린다
        player.draw(canvas)
        for (rocket in rockets) rocket.draw(canvas)
        for (proj in projectiles) proj.draw(canvas)
        for (b in blasts) {
            val t = 1f - b.timer / 0.4f
            blastPaint.alpha = (200 * (1f - t)).toInt()
            canvas.drawCircle(b.x, b.y, 90f + t * 240f, blastPaint)
        }
        for (e in effects) {
            effectPaint.alpha = (255 * (e.timer / 0.7f).coerceIn(0f, 1f)).toInt()
            canvas.drawTr(e.text, e.x, e.y, effectPaint)
        }
        canvas.restore()

        drawHud(canvas, w, h)
        touchControls.draw(canvas, gameView.screenWidth, gameView.screenHeight)
        com.jamcat.nayngapo.ui.MenuButtons.draw(canvas, paused)   // 땅과 같은 멈추기·나가기 버튼

        if (paused) {
            canvas.drawRect(0f, 0f, w, h, pauseVeilPaint)
            canvas.drawTr("PAUSED", w / 2f, h / 2f - 6f, pauseTextPaint)
            canvas.drawTr("멈추기 버튼을 다시 누르면 이어서 진행합니다", w / 2f, h / 2f + 44f, pauseHintPaint)
            com.jamcat.nayngapo.ui.MenuButtons.draw(canvas, paused)   // 막 위에 한 번 더 — 멈춘 상태에서도 또렷하게
        }
    }

    private var skyShader: LinearGradient? = null
    private val skyPaint = Paint()

    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        canvas.drawCircle(cx - r, cy + r * 0.25f, r * 0.75f, paint)
        canvas.drawCircle(cx, cy, r, paint)
        canvas.drawCircle(cx + r, cy + r * 0.28f, r * 0.8f, paint)
        canvas.drawRect(cx - r * 1.4f, cy + r * 0.35f, cx + r * 1.5f, cy + r * 0.9f, paint)
    }

    /** 탑승한 날아냥이 사망 — 영구 사망(무덤)으로 기록하고 추락 연출을 시작한다 */
    private fun startFalling() {
        falling = 0f; fallVy = -120f; fallSpin = 0f
        mount?.let { com.jamcat.nayngapo.engine.CatTeam.markDead(it.id, stageNumber) }   // 로봇은 무덤이 되지 않는다
        touchControls.releaseAll()
        SoundManager.playCatDie()
        effects.add(Effect(player.x, player.y - 120f, 2.2f, if (mount != null) "${mount.name}이(가) 쓰러졌다!" else "로봇이 고장 났다!"))
        for (a in cloudAllies) a.loseCloud()
    }

    private fun updateFalling(deltaTime: Float) {
        falling += deltaTime
        fallVy += 1500f * deltaTime
        player.y += fallVy * deltaTime
        fallSpin += 140f * deltaTime
        for (a in cloudAllies) a.updateFall(deltaTime)
        effects.forEach { it.timer -= deltaTime; it.y -= 40f * deltaTime }
        effects.removeAll { it.timer <= 0f }
        if (falling >= 2.4f) gameView.showGameOver(score)
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        // 땅과 같은 패널 구성: STAGE·SCORE·LEVEL / 목숨·구출·필살기·루비 / 하트 / CLEARED
        hud.allyOrderLabel = "탑승 $mountName" + if (mount?.hasArmor == true) " · 갑옷" else ""
        hud.draw(canvas, com.jamcat.nayngapo.ui.HUD.Data(
            stage = stageNumber, score = score, level = 1 + defeated / 10, lives = 1, rescueCount = rescued,
            specialFill = (1f - rocketCooldown / (Constants.SPECIAL_COOLDOWN * 0.5f)).coerceIn(0f, 1f),
            hp = player.hp, maxHp = player.maxHp, defeated = defeated, total = totalEnemies,
        ), gameView.screenWidth, gameView.screenHeight)
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        heartPath.reset()
        heartPath.moveTo(cx, cy + r)
        heartPath.cubicTo(cx - r * 1.6f, cy - r * 0.4f, cx - r * 0.7f, cy - r * 1.5f, cx, cy - r * 0.4f)
        heartPath.cubicTo(cx + r * 0.7f, cy - r * 1.5f, cx + r * 1.6f, cy - r * 0.4f, cx, cy + r)
        heartPath.close()
        canvas.drawPath(heartPath, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 멈추기·나가기 버튼을 먼저 본다 (두 번째 손가락으로 눌러도 먹히게 POINTER_DOWN 까지)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val idx = event.actionIndex
            when (com.jamcat.nayngapo.ui.MenuButtons.hitTest(event.getX(idx), event.getY(idx))) {
                com.jamcat.nayngapo.ui.MenuButtons.Hit.PAUSE -> { paused = !paused; touchControls.releaseAll(); return true }
                com.jamcat.nayngapo.ui.MenuButtons.Hit.EXIT -> { touchControls.releaseAll(); gameView.leaveStage(); return true }
                null -> {}
            }
        }
        if (paused) return true       // 멈춘 동안에는 조작이 먹지 않는다
        return touchControls.onTouchEvent(event, gameView.screenWidth, gameView.screenHeight)
    }

    override fun onBack(): Boolean {
        if (!paused) {
            paused = true
            touchControls.releaseAll()
        } else {
            gameView.leaveStage()
        }
        return true
    }

    private companion object {
        /** 벌냥이 덮치기 시간 · 닿는 거리 (Claude 판단) */
        const val BEE_LUNGE_TIME = 0.3f
        const val BEE_REACH = 170f
    }
}
