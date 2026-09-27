package com.jamcat.nayngapo.entity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.sin

class Projectile(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    val damage: Int,
    val isPlayerOwned: Boolean,
    /** 탄 색 (슬라임=초록, 표창=회색 등). 지정하지 않으면 기본색 */
    tint: Int? = null,
    /** 탄 크기 (표창은 작고 슬라임은 크다) */
    val radius: Float = 6f,
    /** 매 초 아래로 끌어당기는 힘 (0이면 직선으로 날아간다) */
    val gravity: Float = 0f,
    /** 도화선 길이(초). 0보다 크면 이 시간이 지나면 스스로 터진다 */
    val fuse: Float = 0f,
    /** 폭죽인가 — 닿거나 도화선이 다 타면 터진다 (화면 쪽에서 처리) */
    val isFirework: Boolean = false,
    /** 발바닥 모양으로 그리는 탄 ('연속발사') */
    val isPaw: Boolean = false,
    /** 빙글빙글 도는 표창 (닌자 인간) */
    val isShuriken: Boolean = false,
    /** 데굴데굴 구르며 날아가는 돌 (인간 기술 '돌던지기') */
    val isStone: Boolean = false,
    /** 번개 — 노랑냥이가 부른 먹구름에서 떨어진다 (대표님 2026-09-24·25). 지그재그 노란 줄기 */
    val isLightning: Boolean = false,
    /** 번개 줄기가 시작되는 하늘 y (먹구름 띠 아랫자락). 0보다 크면 줄기가 거기까지 이어진다 */
    val boltTopY: Float = 0f,
    /** 화면 안의 가장 가까운 적을 쫓아가 터지는 미사일 (인간 기술 '미사일') */
    val isMissile: Boolean = false,
    /** 불꽃 한 덩이 — 빨간냥이가 뱉는 불 (대표님 2026-09-27). fuse 동안 커지며 노랑→주황→빨강으로 식어 사라진다 */
    val isFlame: Boolean = false,
    /** 용암 덩이 — 주황냥이가 포물선으로 던진다 (대표님 2026-09-27). 땅에 닿으면 화면이 용암 웅덩이를 남긴다 */
    val isLava: Boolean = false
) {
    val width = radius * 2f
    val height = radius * 2f
    var isAlive = true

    /** 남은 도화선 */
    var fuseLeft = fuse
        private set

    /** 도화선이 다 타서 스스로 터졌다 — 화면이 한 번 처리하고 내린다 */
    var fuseBurnedOut = false

    val hitBox: RectF get() = RectF(x, y, x + width, y + height)

    private val paint = Paint().apply {
        color = tint ?: if (isPlayerOwned) Color.CYAN else Color.RED
        isAntiAlias = true
    }

    // ===== 폭죽 연출용 =====
    private var animTime = 0f
    /** 지나온 자리 (꼬리 불꽃) — 원형 버퍼 */
    private val trailX = FloatArray(TRAIL_LEN)
    private val trailY = FloatArray(TRAIL_LEN)
    private var trailCount = 0
    private var trailHead = 0

    // ===== 미사일 연출용 =====
    private val missileStartSpeed = kotlin.math.hypot(velocityX, velocityY)
    private var missileAge = 0f
    /** 뒤로 남기는 연기 모금 — 원형 버퍼 (자리 + 태어난 시각) */
    private val smokeX = FloatArray(SMOKE_LEN)
    private val smokeY = FloatArray(SMOKE_LEN)
    private val smokeBorn = FloatArray(SMOKE_LEN)
    private var smokeCount = 0
    private var smokeHead = 0
    private var smokeTimer = 0f

    fun update(deltaTime: Float) {
        if (gravity != 0f) velocityY += gravity * deltaTime
        x += velocityX * deltaTime
        y += velocityY * deltaTime

        if (isShuriken || isStone || isFlame || isLava) animTime += deltaTime
        if (isMissile) {
            missileAge += deltaTime
            // 방향은 그대로 두고 속력만 0.5초에 걸쳐 900까지 올린다
            val want = missileStartSpeed +
                (MISSILE_MAX_SPEED - missileStartSpeed) * (missileAge / MISSILE_ACCEL_TIME).coerceIn(0f, 1f)
            val cur = kotlin.math.hypot(velocityX, velocityY)
            if (cur > 0.01f) {
                velocityX = velocityX / cur * want
                velocityY = velocityY / cur * want
                // 꽁무니에서 연기를 한 모금씩 남긴다
                smokeTimer -= deltaTime
                if (smokeTimer <= 0f) {
                    smokeTimer = SMOKE_EVERY
                    val ux = velocityX / want
                    val uy = velocityY / want
                    smokeX[smokeHead] = x + width / 2f - ux * MISSILE_LEN * 0.55f
                    smokeY[smokeHead] = y + height / 2f - uy * MISSILE_LEN * 0.55f
                    smokeBorn[smokeHead] = missileAge
                    smokeHead = (smokeHead + 1) % SMOKE_LEN
                    if (smokeCount < SMOKE_LEN) smokeCount++
                }
            }
        }
        if (isFirework) {
            animTime += deltaTime
            trailX[trailHead] = x + width / 2f
            trailY[trailHead] = y + height / 2f
            trailHead = (trailHead + 1) % TRAIL_LEN
            if (trailCount < TRAIL_LEN) trailCount++
        }

        if (fuse > 0f && isAlive) {
            fuseLeft -= deltaTime
            if (fuseLeft <= 0f) { fuseBurnedOut = true; isAlive = false }
        }

        if (x < -500f || x > 25000f || y < -500f || y > 2000f) {
            isAlive = false
        }
    }

    fun draw(canvas: Canvas) {
        if (!isAlive) return
        val cx = x + width / 2f
        val cy = y + height / 2f

        if (isFirework) {
            drawFirecracker(canvas, cx, cy)
            return
        }

        if (isMissile) {
            drawMissile(canvas, cx, cy)
            return
        }

        if (isStone) {
            drawStone(canvas, cx, cy)
            return
        }

        if (isShuriken) {
            drawShuriken(canvas, cx, cy)
            return
        }

        if (isFlame) {
            drawFlame(canvas, cx, cy)
            return
        }

        if (isLava) {
            drawLava(canvas, cx, cy)
            return
        }

        if (isLightning) {
            drawLightning(canvas, cx, cy)
            return
        }

        if (isPaw) {
            // 발바닥: 큰 발볼 + 나아가는 쪽에 발가락 세 개
            canvas.drawCircle(cx, cy, width * 0.40f, paint)
            val d = if (velocityX >= 0f) 1f else -1f
            val tr = width * 0.16f
            canvas.drawCircle(cx + d * width * 0.40f, cy - width * 0.26f, tr, paint)
            canvas.drawCircle(cx + d * width * 0.50f, cy, tr, paint)
            canvas.drawCircle(cx + d * width * 0.40f, cy + width * 0.26f, tr, paint)
            return
        }

        // 둥근 탄(그린냥이 슬라임 등): 배경에 묻히지 않게 진한 외곽선 + 작은 윤기
        val r = width / 2f
        canvas.drawCircle(cx, cy, r, paint)
        canvas.drawCircle(cx - r * 0.35f, cy - r * 0.35f, r * 0.28f, shineDotPaint)
        outlinePaint.strokeWidth = (r * 0.28f).coerceIn(2f, 4f)
        canvas.drawCircle(cx, cy, r, outlinePaint)
    }

    /**
     * 폭죽: 빨간 종이 통에 금색 띠 두 줄, 뒤쪽 도화선 끝에서 불꽃이 튀고 지나온 길에 불똥이 남는다.
     * 날아가는 방향으로 통이 기울어진다.
     */
    private fun drawFirecracker(canvas: Canvas, cx: Float, cy: Float) {
        // 1) 꼬리 불똥 (오래된 것일수록 작고 흐리게)
        for (i in 0 until trailCount) {
            val idx = (trailHead - 1 - i + TRAIL_LEN * 2) % TRAIL_LEN
            val k = 1f - i / TRAIL_LEN.toFloat()
            trailPaint.color = if (i % 2 == 0) EMBER_YELLOW else EMBER_ORANGE
            trailPaint.alpha = (200 * k).toInt()
            canvas.drawCircle(trailX[idx], trailY[idx], 1.5f + 3f * k, trailPaint)
        }

        val len = radius * 3.4f
        val thick = radius * 1.5f
        val angle = Math.toDegrees(atan2(velocityY, velocityX).toDouble()).toFloat()

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angle)

        // 2) 도화선 (통 뒤쪽) + 끝에서 튀는 불꽃
        val tailX = -len / 2f
        val fuseTipX = tailX - radius * 1.0f
        val fuseTipY = -radius * 0.45f
        canvas.drawLine(tailX, 0f, fuseTipX, fuseTipY, fusePaint)
        val flicker = 0.75f + 0.25f * sin(animTime * 42f)
        canvas.drawCircle(fuseTipX, fuseTipY, radius * 0.55f * flicker, sparkOuterPaint)
        canvas.drawCircle(fuseTipX, fuseTipY, radius * 0.28f * flicker, sparkCorePaint)
        val ray = radius * 0.9f * flicker
        for (r in 0 until 4) {
            val a = animTime * 9f + r * (Math.PI.toFloat() / 2f)
            canvas.drawLine(
                fuseTipX, fuseTipY,
                fuseTipX + kotlin.math.cos(a) * ray, fuseTipY + sin(a) * ray, sparkRayPaint
            )
        }

        // 3) 빨간 종이 통
        bodyRect.set(-len / 2f, -thick / 2f, len / 2f, thick / 2f)
        canvas.drawRoundRect(bodyRect, 3f, 3f, paint)
        // 위쪽 밝은 줄 (둥근 통 느낌)
        canvas.drawRect(-len / 2f + 2f, -thick / 2f + 2f, len / 2f - 2f, -thick / 2f + thick * 0.28f, shinePaint)
        // 금색 띠 두 줄
        val band = radius * 0.4f
        canvas.drawRect(-len * 0.24f - band / 2f, -thick / 2f, -len * 0.24f + band / 2f, thick / 2f, bandPaint)
        canvas.drawRect(len * 0.18f - band / 2f, -thick / 2f, len * 0.18f + band / 2f, thick / 2f, bandPaint)
        // 앞쪽 흰 종이 마개
        canvas.drawRect(len / 2f - radius * 0.45f, -thick / 2f, len / 2f, thick / 2f, capPaint)
        canvas.drawRoundRect(bodyRect, 3f, 3f, inkPaint)

        canvas.restore()
    }

    /** 미사일이 목표 쪽으로 방향을 튼다 (초당 최대 260도) — 화면이 매 프레임 불러 준다 */
    fun steerToward(tx: Float, ty: Float, deltaTime: Float) {
        if (!isMissile) return
        val cur = atan2(velocityY, velocityX)
        val want = atan2(ty - (y + height / 2f), tx - (x + width / 2f))
        val pi = Math.PI.toFloat()
        var diff = want - cur
        while (diff > pi) diff -= 2f * pi
        while (diff < -pi) diff += 2f * pi
        val maxTurn = MISSILE_TURN_RATE * deltaTime
        val a = cur + diff.coerceIn(-maxTurn, maxTurn)
        val speed = kotlin.math.hypot(velocityX, velocityY)
        velocityX = kotlin.math.cos(a) * speed
        velocityY = sin(a) * speed
    }

    /** 미사일: 흰 몸통 + 빨간 뾰족 머리·띠·꼬리 날개 둘, 꽁무니 불꽃과 뒤로 남는 회색 연기 */
    private fun drawMissile(canvas: Canvas, cx: Float, cy: Float) {
        // 1) 연기: 오래된 모금일수록 크고 흐리다
        for (i in 0 until smokeCount) {
            val idx = (smokeHead - 1 - i + SMOKE_LEN * 2) % SMOKE_LEN
            val age = missileAge - smokeBorn[idx]
            if (age > SMOKE_LIFE) continue
            val k = age / SMOKE_LIFE
            missileSmokePaint.alpha = (150f * (1f - k)).toInt().coerceIn(0, 255)
            canvas.drawCircle(smokeX[idx], smokeY[idx], 3f + 9f * k, missileSmokePaint)
        }

        val half = MISSILE_LEN / 2f
        val bh = 12f
        val angle = Math.toDegrees(atan2(velocityY, velocityX).toDouble()).toFloat()
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(angle)

        // 2) 꽁무니 불꽃 (일렁인다)
        val flick = 0.75f + 0.25f * sin(missileAge * 48f)
        exhaustRect.set(-half - 18f * flick, -5f, -half + 3f, 5f)
        canvas.drawOval(exhaustRect, exhaustOuterPaint)
        exhaustRect.set(-half - 10f * flick, -2.6f, -half + 3f, 2.6f)
        canvas.drawOval(exhaustRect, exhaustInnerPaint)

        // 3) 꼬리 날개 둘
        for (s in intArrayOf(-1, 1)) {
            missileFinPath.reset()
            missileFinPath.moveTo(-half + 3f, s * bh / 2f)
            missileFinPath.lineTo(-half - 5f, s * (bh / 2f + 9f))
            missileFinPath.lineTo(-half + 13f, s * bh / 2f)
            missileFinPath.close()
            canvas.drawPath(missileFinPath, missileRedPaint)
            canvas.drawPath(missileFinPath, inkPaint)
        }

        // 4) 흰 몸통 + 빨간 띠
        bodyRect.set(-half + 2f, -bh / 2f, half - 10f, bh / 2f)
        canvas.drawRoundRect(bodyRect, 4f, 4f, missileBodyPaint)
        canvas.drawRect(-3f, -bh / 2f, 3f, bh / 2f, missileRedPaint)

        // 5) 빨간 뾰족 머리
        missileNosePath.reset()
        missileNosePath.moveTo(half - 10f, -bh / 2f)
        missileNosePath.lineTo(half + 2f, 0f)
        missileNosePath.lineTo(half - 10f, bh / 2f)
        missileNosePath.close()
        canvas.drawPath(missileNosePath, missileRedPaint)
        canvas.drawPath(missileNosePath, inkPaint)
        canvas.drawRoundRect(bodyRect, 4f, 4f, inkPaint)

        canvas.restore()
    }

    /** 돌: 울퉁불퉁한 일곱 모서리 회색 돌에 밝은 면 하나, 날아가며 데굴데굴 돈다 (초당 540도) */
    private fun drawStone(canvas: Canvas, cx: Float, cy: Float) {
        val r = radius * 1.1f
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(animTime * 540f * (if (velocityX >= 0f) 1f else -1f))
        stonePath.reset()
        for (i in 0 until 7) {
            val a = i * (2f * Math.PI.toFloat() / 7f)
            val rr = r * STONE_LUMPS[i]
            val px = kotlin.math.cos(a) * rr
            val py = sin(a) * rr
            if (i == 0) stonePath.moveTo(px, py) else stonePath.lineTo(px, py)
        }
        stonePath.close()
        canvas.drawPath(stonePath, stonePaint)
        facetPath.reset()
        facetPath.moveTo(-r * 0.55f, -r * 0.15f)
        facetPath.lineTo(-r * 0.10f, -r * 0.62f)
        facetPath.lineTo(r * 0.22f, -r * 0.22f)
        facetPath.close()
        canvas.drawPath(facetPath, stoneFacetPaint)
        canvas.drawPath(stonePath, inkPaint)
        canvas.restore()
    }

    /** 표창: 은색 네 날 별 + 가운데 구멍, 날아가는 동안 빠르게 돈다 */
    private val boltPath = Path()
    private val boltOuter = Paint().apply {
        color = Color.rgb(255, 214, 40); style = Paint.Style.STROKE; strokeWidth = 9f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
    }
    private val boltInner = Paint().apply {
        color = Color.rgb(255, 255, 220); style = Paint.Style.STROKE; strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
    }
    private var boltPhase = 0f

    /** 번개: 나아가는 방향으로 길게 뻗은 지그재그 줄기 (노란 테 + 흰 심). 프레임마다 살짝 흔들린다 */
    private fun drawLightning(canvas: Canvas, cx: Float, cy: Float) {
        boltPhase += 0.9f
        // 나아가는 방향(ux, uy)과 그 옆 방향(-uy, ux) — 먹구름에서 내리꽂히면 세로로 선다
        val sp = kotlin.math.hypot(velocityX, velocityY)
        val ux = if (sp > 0.01f) velocityX / sp else 1f
        val uy = if (sp > 0.01f) velocityY / sp else 0f
        // 하늘 먹구름에서 떨어지는 번개는 구름 밑부터 탄 자리까지 한 줄기로 이어진다
        val len = if (boltTopY > 0f && uy > 0.5f) (cy - boltTopY).coerceAtLeast(150f) else 150f
        val segs = (len / 22f).toInt().coerceIn(7, 30)
        boltPath.reset()
        boltPath.moveTo(cx - ux * len, cy - uy * len)
        for (i in 1..segs) {
            val t = i / segs.toFloat()
            val jag = if (i == segs) 0f else (if (i % 2 == 0) 1f else -1f) * (12f + 5f * kotlin.math.sin(boltPhase + i))
            val along = -len + len * t      // 끝(맞는 곳)이 탄 자리, 줄기는 뒤로 뻗는다
            boltPath.lineTo(cx + ux * along - uy * jag, cy + uy * along + ux * jag)
        }
        canvas.drawPath(boltPath, boltOuter)
        canvas.drawPath(boltPath, boltInner)
    }

    private val flameOuter = Paint().apply { isAntiAlias = true }
    private val flameInner = Paint().apply { isAntiAlias = true }

    /** 불꽃: 갓 뱉은 건 작고 노랗고, 날아가며 커지고 주황·빨강으로 식어 흐려진다. 살짝 일렁인다 */
    private fun drawFlame(canvas: Canvas, cx: Float, cy: Float) {
        val life = if (fuse > 0f) (1f - fuseLeft / fuse).coerceIn(0f, 1f) else 0f
        val r = radius * (0.8f + 1.4f * life)
        val wob = kotlin.math.sin(animTime * 40f + cx * 0.05f) * r * 0.12f
        val a = ((1f - life * life) * 255).toInt().coerceIn(0, 255)
        val g = (200 - 150 * life).toInt().coerceIn(40, 200)
        flameOuter.color = Color.argb(a * 3 / 4, 240, g, 30)
        flameInner.color = Color.argb(a, 255, (245 - 90 * life).toInt(), (150 - 150 * life).toInt().coerceAtLeast(0))
        canvas.drawCircle(cx, cy + wob, r, flameOuter)
        // 불꽃 끝이 위로 날린다
        canvas.drawCircle(cx - velocityX * 0.02f, cy - r * 0.55f - wob, r * 0.6f, flameOuter)
        canvas.drawCircle(cx, cy + wob * 0.5f, r * 0.55f, flameInner)
    }

    private val lavaCrust = Paint().apply { color = Color.rgb(120, 30, 20); isAntiAlias = true }
    private val lavaBody = Paint().apply { color = Color.rgb(255, 110, 20); isAntiAlias = true }
    private val lavaCore = Paint().apply { color = Color.rgb(255, 225, 90); isAntiAlias = true }
    private val lavaGlow = Paint().apply { color = Color.argb(90, 255, 120, 30); isAntiAlias = true }

    /** 용암 덩이: 달아오른 빛 + 검붉은 껍질 + 주황 속 + 노란 심, 뒤로 방울이 떨어진다 */
    private fun drawLava(canvas: Canvas, cx: Float, cy: Float) {
        val r = radius
        val pulse = 1f + 0.08f * kotlin.math.sin(animTime * 18f)
        canvas.drawCircle(cx, cy, r * 1.7f * pulse, lavaGlow)
        canvas.drawCircle(cx, cy, r * 1.08f, lavaCrust)
        canvas.drawCircle(cx - r * 0.08f, cy - r * 0.05f, r * 0.9f, lavaBody)
        canvas.drawCircle(cx - r * 0.25f, cy - r * 0.25f, r * 0.42f, lavaCore)
        // 지나온 쪽으로 방울 두 개
        val sp = kotlin.math.hypot(velocityX, velocityY).coerceAtLeast(1f)
        val bx = -velocityX / sp; val by = -velocityY / sp
        canvas.drawCircle(cx + bx * r * 1.5f, cy + by * r * 1.5f, r * 0.45f, lavaBody)
        canvas.drawCircle(cx + bx * r * 2.4f, cy + by * r * 2.4f, r * 0.28f, lavaBody)
    }

    private fun drawShuriken(canvas: Canvas, cx: Float, cy: Float) {
        val r = radius * 1.6f
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(animTime * 1440f * (if (velocityX >= 0f) 1f else -1f))   // 초당 네 바퀴
        starPath.reset()
        for (i in 0 until 8) {
            val a = i * (Math.PI.toFloat() / 4f)
            val rr = if (i % 2 == 0) r else r * 0.34f
            val px = kotlin.math.cos(a) * rr
            val py = sin(a) * rr
            if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        canvas.drawPath(starPath, steelPaint)
        canvas.drawPath(starPath, inkPaint)
        // 날마다 밝은 면
        canvas.drawLine(0f, 0f, r * 0.8f, 0f, edgePaint)
        canvas.drawLine(0f, 0f, 0f, r * 0.8f, edgePaint)
        canvas.drawCircle(0f, 0f, r * 0.2f, holePaint)
        canvas.drawCircle(0f, 0f, r * 0.2f, inkPaint)
        canvas.restore()
    }

    private companion object {
        const val TRAIL_LEN = 8
        val outlinePaint = Paint().apply {
            color = Color.rgb(28, 40, 24); style = Paint.Style.STROKE; isAntiAlias = true
        }
        val shineDotPaint = Paint().apply { color = Color.argb(150, 255, 255, 255); isAntiAlias = true }
        val starPath = android.graphics.Path()

        /** 미사일 전체 길이 (머리 끝~꼬리) */
        const val MISSILE_LEN = 46f
        const val MISSILE_MAX_SPEED = 900f
        const val MISSILE_ACCEL_TIME = 0.5f
        /** 초당 260도 */
        const val MISSILE_TURN_RATE = 4.5379f
        const val SMOKE_LEN = 14
        const val SMOKE_EVERY = 0.035f
        const val SMOKE_LIFE = 0.45f
        val missileFinPath = android.graphics.Path()
        val missileNosePath = android.graphics.Path()
        val exhaustRect = RectF()
        val missileBodyPaint = Paint().apply { color = Color.rgb(248, 248, 246); isAntiAlias = true }
        val missileRedPaint = Paint().apply { color = Color.rgb(214, 48, 40); isAntiAlias = true }
        val exhaustOuterPaint = Paint().apply { color = Color.rgb(255, 140, 40); isAntiAlias = true }
        val exhaustInnerPaint = Paint().apply { color = Color.rgb(255, 222, 96); isAntiAlias = true }
        val missileSmokePaint = Paint().apply { color = Color.rgb(160, 160, 166); isAntiAlias = true }
        val stonePath = android.graphics.Path()
        val facetPath = android.graphics.Path()
        val stonePaint = Paint().apply { color = Color.rgb(150, 150, 158); isAntiAlias = true }
        val stoneFacetPaint = Paint().apply { color = Color.rgb(198, 198, 206); isAntiAlias = true }
        /** 모서리마다 조금씩 다른 거리 — 매끈한 원이 아니라 울퉁불퉁한 돌로 보이게 */
        val STONE_LUMPS = floatArrayOf(1.0f, 0.82f, 0.97f, 0.78f, 1.05f, 0.86f, 0.93f)
        val steelPaint = Paint().apply { color = Color.rgb(176, 184, 196); isAntiAlias = true }
        val edgePaint = Paint().apply {
            color = Color.argb(170, 255, 255, 255); strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
        }
        val holePaint = Paint().apply { color = Color.rgb(250, 248, 240); isAntiAlias = true }
        val EMBER_YELLOW = Color.rgb(255, 214, 74)
        val EMBER_ORANGE = Color.rgb(255, 140, 40)

        val bodyRect = RectF()
        val trailPaint = Paint().apply { isAntiAlias = true }
        val shinePaint = Paint().apply { color = Color.argb(90, 255, 255, 255); isAntiAlias = true }
        val bandPaint = Paint().apply { color = Color.rgb(255, 204, 60); isAntiAlias = true }
        val capPaint = Paint().apply { color = Color.rgb(250, 244, 230); isAntiAlias = true }
        val inkPaint = Paint().apply {
            color = Color.rgb(35, 28, 26); style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true
        }
        val fusePaint = Paint().apply {
            color = Color.rgb(60, 50, 44); strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
        }
        val sparkOuterPaint = Paint().apply { color = Color.argb(200, 255, 150, 40); isAntiAlias = true }
        val sparkCorePaint = Paint().apply { color = Color.rgb(255, 250, 200); isAntiAlias = true }
        val sparkRayPaint = Paint().apply {
            color = Color.rgb(255, 220, 90); strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
        }
    }
}
