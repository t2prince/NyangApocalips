package com.jamcat.nayngapo.screen

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
    private val mount: com.jamcat.nayngapo.engine.CatTeam.TeamCat =
        com.jamcat.nayngapo.engine.CatTeam.flyingMount() ?: run {
            mountGranted = true
            com.jamcat.nayngapo.engine.CatTeam.recruit(com.jamcat.nayngapo.entity.AllyCat.Kind.FLYING)
        }

    private inner class Rider {
        var x = 500f
        var y = 0f
        var hp = 5
        val maxHp = 5
        var facingRight = true
        var invincible = 0f
        var time = 0f

        val hitBox: RectF get() = RectF(x - 70f, y - 60f, x + 70f, y + 55f)

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
            x = (x + dx * 430f * deltaTime).coerceIn(camera + 90f, courseLength - 90f)
            y = (y + dy * 390f * deltaTime).coerceIn(flyMinY, flyMaxY)
        }

        fun hurt(amount: Int) {
            if (invincible > 0f) return
            // 타고 있는 날아냥이가 갑옷을 입었으면 등에 탄 사람도 덜 다친다
            val taken =
                if (mount.hasArmor)
                    Math.round(amount * com.jamcat.nayngapo.engine.CatTeam.ARMOR_DAMAGE_FACTOR)
                        .coerceAtLeast(1)
                else amount
            hp -= taken
            invincible = 1.5f
            SoundManager.playPlayerHurt()
            shake = 0.3f
            effects.add(Effect(x, y - 80f, 0.6f, "HIT!"))
        }

        fun draw(canvas: Canvas) {
            if (invincible > 0f && ((invincible * 12).toInt() % 2 == 0)) return   // 무적 깜빡임
            val bob = sin((time * 5f).toDouble()).toFloat() * 7f
            val cat = if (facingRight) SpriteManager.flyingCat else SpriteManager.getFlipped(SpriteManager.flyingCat)
            val rider = SpriteManager.playerIdle[((time * 6).toInt()) % SpriteManager.playerIdle.size]
            val riderBmp = if (facingRight) rider else SpriteManager.getFlipped(rider)
            // 인간이 날개냥이 등에 올라탄 모습
            val rw = riderBmp.width * 0.62f
            val rh = riderBmp.height * 0.62f
            riderDst.set(x - rw / 2f, y + bob - cat.height / 2f - rh + 22f, x + rw / 2f, y + bob - cat.height / 2f + 22f)
            canvas.drawBitmap(riderBmp, null, riderDst, bitmapPaint)
            canvas.drawBitmap(cat, x - cat.width / 2f, y + bob - cat.height / 2f, bitmapPaint)
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
        private val baseY: Float
    ) {
        /** 갇힌 냥이 종류 (날아냥이는 10스테이지 전용이라 여기서는 안 나온다) */
        val catKind: com.jamcat.nayngapo.entity.AllyCat.Kind? =
            if (kind == com.jamcat.nayngapo.entity.Prison.Kind.CAT) {
                listOf(
                    com.jamcat.nayngapo.entity.AllyCat.Kind.GREEN,
                    com.jamcat.nayngapo.entity.AllyCat.Kind.BLUE,
                    com.jamcat.nayngapo.entity.AllyCat.Kind.NORMAL,
                ).let { it[(Math.random() * it.size).toInt()] }
            } else null

        /** 그림은 땅의 감옥을 그대로 빌려 쓴다 (창살·울상·열린 모습까지 같다) */
        private val cage = com.jamcat.nayngapo.entity.Prison(0f, 0f, kind, catKind)
        /** 갇힌 치즈냥이의 털 무늬 — 구출하면 같은 무늬로 팀에 간다 */
        val catCoat: com.jamcat.nayngapo.engine.CatCoat get() = cage.catCoat

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

    private var camera = 0f
    private var score = 0
    private var defeated = 0
    private var totalEnemies = 0
    private var shake = 0f
    private var fireTimer = 0f
    private var rocketCooldown = 0f
    private var clearTimer = -1f
    private var paused = false
    private var time = 0f

    private val courseLength = 11000f + (stageNumber - 11) * 700f
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

    private fun spawnEnemies() {
        val flyingCount = 10 + (stageNumber - 11) * 2
        val laserCount = ((stageNumber - 12) * 2).coerceAtLeast(0)
        val spots = mutableListOf<Pair<SkyKind, Float>>()
        repeat(flyingCount) { i ->
            spots.add(SkyKind.FLYING to (1400f + i * (courseLength - 2600f) / flyingCount))
        }
        repeat(laserCount) { i ->
            spots.add(SkyKind.LASER to (2200f + i * (courseLength - 3400f) / laserCount.coerceAtLeast(1)))
        }
        for ((kind, ex) in spots) {
            val ey = 180f + (Math.random() * 520f).toFloat()
            enemies.add(SkyEnemy(kind, ex + (Math.random() * 260f - 130f).toFloat(), ey))
        }
        // 하늘 스테이지(11)의 끝에는 하늘의 왕 '왕왕냥이'가 기다린다
        enemies.add(SkyEnemy(SkyKind.GIANT, courseLength - 900f, 400f))
        totalEnemies = enemies.size

        spawnSkyPrisons()
    }

    /** 코스 중간중간에 감옥 셋을 띄운다 (인간·냥이 둘 다 나온다) */
    private fun spawnSkyPrisons() {
        val kinds = mutableListOf(
            com.jamcat.nayngapo.entity.Prison.Kind.CAT,
            com.jamcat.nayngapo.entity.Prison.Kind.HUMAN,
            if (Math.random() < 0.5) com.jamcat.nayngapo.entity.Prison.Kind.CAT
            else com.jamcat.nayngapo.entity.Prison.Kind.HUMAN,
        )
        kinds.shuffle()
        for ((i, kind) in kinds.withIndex()) {
            val px = courseLength * (0.25f + 0.25f * i) + (Math.random().toFloat() - 0.5f) * 600f
            // 위아래 15%는 비워 둔다 — 너무 높거나 낮으면 닿기 어렵다
            val band = flyMaxY - flyMinY
            val py = flyMinY + band * (0.15f + Math.random().toFloat() * 0.70f)
            skyPrisons.add(SkyPrison(kind, px, py))
        }
    }

    /** 하늘의 감옥이 열렸다 — 인간은 구조 점수, 냥이는 팀 명단에 오른다 */
    private fun onSkyPrisonOpened(prison: SkyPrison) {
        score += 500
        SoundManager.playCatHit()
        shake = 0.2f
        if (prison.kind == com.jamcat.nayngapo.entity.Prison.Kind.HUMAN) {
            rescued += 3
            effects.add(Effect(prison.x, prison.y - 70f, 1.2f, "인간 3명 구출!"))
        } else {
            rescued += 1
            val kind = prison.catKind ?: com.jamcat.nayngapo.entity.AllyCat.Kind.NORMAL
            val cat = CatTeam.recruit(kind, prison.catCoat)
            effects.add(
                Effect(prison.x, prison.y - 70f, 1.8f, "${cat.name} 구출! 고양이 관리에서 출전시켜 주세요")
            )
        }
    }

    override fun update(deltaTime: Float) {
        if (paused) return
        time += deltaTime
        if (shake > 0f) shake -= deltaTime

        val h = gameView.screenHeight.toFloat()
        flyMaxY = h - 320f
        skyMidY = (flyMinY + flyMaxY) / 2f
        player.y = if (player.y == 0f) skyMidY else player.y

        player.update(deltaTime)

        // 카메라: 플레이어를 화면 1/3 지점에 둔다
        camera = (player.x - gameView.screenWidth * 0.35f).coerceIn(0f, courseLength - gameView.screenWidth)

        // 발사
        fireTimer -= deltaTime
        if (touchControls.inputState.attack && fireTimer <= 0f) {
            fireTimer = 0.28f
            val dir = if (player.facingRight) 1f else -1f
            projectiles.add(Projectile(player.x + dir * 80f, player.y - 10f, dir * 950f, 0f,
                5, true, tint = Color.rgb(60, 60, 66), radius = 6f))
            SoundManager.playAttack()
        }
        if (rocketCooldown > 0f) rocketCooldown -= deltaTime
        touchControls.specialCooldown = rocketCooldown
        if (touchControls.inputState.special) {
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
                shake = 0.25f
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

        // 죽음 / 클리어
        if (player.hp <= 0) {
            gameView.showGameOver(score)
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
            canvas.drawText(e.text, e.x, e.y, effectPaint)
        }
        canvas.restore()

        drawHud(canvas, w, h)
        touchControls.draw(canvas, gameView.screenWidth, gameView.screenHeight)

        if (paused) {
            canvas.drawRect(0f, 0f, w, h, pauseVeilPaint)
            canvas.drawText("PAUSED", w / 2f, h / 2f - 6f, pauseTextPaint)
            canvas.drawText("한 번 더 누르면 계속 · 뒤로가기를 또 누르면 나가기", w / 2f, h / 2f + 44f, pauseHintPaint)
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

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        // 하트
        for (i in 0 until player.maxHp) {
            val hx = 46f + i * 52f
            val hy = 52f
            if (i < player.hp) drawHeart(canvas, hx, hy, 19f, heartPaint)
            else drawHeart(canvas, hx, hy, 19f, heartEmptyPaint)
        }
        hudTextPaint.textSize = h * 0.040f
        canvas.drawText("스테이지 $stageNumber — 하늘", 36f, 128f, hudTextPaint)
        hudRightPaint.textSize = h * 0.040f
        canvas.drawText("점수 $score", w - 36f, 60f, hudRightPaint)
        canvas.drawText("남은 냥이 ${totalEnemies - defeated}", w - 36f, 118f, hudRightPaint)
        canvas.drawText("구출 $rescued", w - 36f, 176f, hudRightPaint)

        // 지금 타고 있는 날아냥이 이름 (갑옷을 입었으면 표시)
        hudTextPaint.textSize = h * 0.034f
        val armor = if (mount.hasArmor) " · 갑옷" else ""
        canvas.drawText("탑승: ${mount.name}$armor", 36f, 176f, hudTextPaint)
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
        if (paused) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) paused = false
            return true
        }
        return touchControls.onTouchEvent(event, gameView.screenWidth, gameView.screenHeight)
    }

    override fun onBack(): Boolean {
        if (!paused) {
            paused = true
            touchControls.releaseAll()
        } else {
            gameView.showLobby()
        }
        return true
    }
}
