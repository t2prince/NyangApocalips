package com.jamcat.nayngapo.entity

import android.graphics.*
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.engine.Terrain

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
    var jumpZ = 0f
    var jumpVelocity = 0f
    var isJumping = false
    var facingRight = true

    var isAttacking = false
    var attackTimer = 0f
    private var attackTotal = Constants.ATTACK_DURATION
    private var useSpecial = false
    var isJumpAttacking = false

    // 콤보: 0=원(잽), 1=투(크로스), 2=킥(마무리). COMBO_WINDOW 안에 다시 치면 다음 단계로.
    var comboStep = 0
        private set
    private var comboResetTimer = 0f

    // 애니메이션 위상
    private var idlePhase = 0f    // 대기 숨쉬기
    private var walkPhase = 0f    // 걷기 사이클

    // 피격 이펙트: 실제로 적을 맞췄을 때만 짧게 표시
    private var hitSparkTimer = 0f

    var isInvincible = false
    var invincibilityTimer = 0f

    var isDead = false
    var score = 0
    var rescueCount = 0

    // 횡뷰: 발끝(y)부터 머리까지 전신 히트박스 (투사체가 몸통 높이로 날아온다)
    val hitBox: RectF get() = RectF(x, y - 150f, x + width, y + 10f)

    // Stick figure paints
    private val bodyPaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
        isAntiAlias = true; style = Paint.Style.STROKE
    }
    // 주먹/발 끝 강조용 검은 점
    private val headPaint = Paint().apply {
        color = Color.BLACK; isAntiAlias = true; style = Paint.Style.FILL
    }
    // 머리: 흰색으로 비우고 검은 테두리
    private val headFillPaint = Paint().apply {
        color = Color.WHITE; isAntiAlias = true; style = Paint.Style.FILL
    }
    private val headStrokePaint = Paint().apply {
        color = Color.BLACK; strokeWidth = 4f; isAntiAlias = true; style = Paint.Style.STROKE
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

        // 횡뷰 플랫포머: 좌우로만 이동하고 발은 항상 지형 표면 위 (수직은 jumpZ가 담당)
        x += velocityX * deltaTime
        x = x.coerceIn(Constants.PLAY_AREA_LEFT + 10f, Constants.PLAY_AREA_RIGHT - width - 10f)
        y = Terrain.groundY(x + width / 2f)

        if (isJumping) {
            jumpVelocity += Constants.JUMP_GRAVITY * deltaTime
            jumpZ += jumpVelocity * deltaTime
            if (jumpZ >= 0f) {
                jumpZ = 0f; jumpVelocity = 0f; isJumping = false
            }
        }

        if (isAttacking) {
            attackTimer -= deltaTime
            if (attackTimer <= 0f) { isAttacking = false; useSpecial = false; isJumpAttacking = false }
        }

        // 애니메이션 위상 갱신
        idlePhase += deltaTime * 3f
        val moving = velocityX != 0f
        if (moving && !isJumping) {
            walkPhase += deltaTime * 11f          // 걷기 다리/팔 스윙 속도
        } else {
            walkPhase = 0f
        }

        if (hitSparkTimer > 0f) hitSparkTimer -= deltaTime

        // 콤보 창: 만료되면 다음 공격은 다시 '원(잽)'부터 시작
        if (comboResetTimer > 0f) {
            comboResetTimer -= deltaTime
            if (comboResetTimer <= 0f) comboStep = 0
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
        val dir = if (facingRight) 1f else -1f   // 바라보는 방향(+1 오른쪽, -1 왼쪽)

        // Shadow: 점프 높이에 따라 그림자를 축소·감쇄해 공중 높이감을 준다.
        val heightFrac = (-jumpZ / Constants.JUMP_MAX_HEIGHT).coerceIn(0f, 1f)
        val shadowScale = 1f - heightFrac * 0.45f
        val sw = 15f * shadowScale
        shadowPaint.alpha = (60 * (1f - heightFrac * 0.5f)).toInt()
        canvas.drawOval(RectF(cx - sw, y - 3f * shadowScale, cx + sw, y + 5f * shadowScale), shadowPaint)

        // 캐릭터 크기 스케일(전투화면 목업 비율). drawY = 발바닥 기준선.
        val s = 2.2f
        val headR = 8.5f * s
        val armLen = 22f * s     // 어깨→손 (모든 상태 동일 길이)
        val legLen = 28f * s     // 엉덩이→발 (모든 상태 동일 길이)

        // 관절 기준점(기본 대기 자세)
        var shX = cx;  var shY = drawY - 54f * s   // 어깨/목
        var hipX = cx; var hipY = drawY - 26f * s  // 엉덩이

        // 팔·다리는 '각도'로만 자세를 표현한다 → 길이는 항상 일정.
        // 각도 단위: 도(°), 화면 좌표계(0°=정면, 90°=아래, 음수=위). 정면은 dir로 좌우 반전.
        var frontArmDeg = 82f; var backArmDeg = 98f
        var frontLegDeg = 80f; var backLegDeg = 100f

        var frontArmThick = false; var backArmThick = false; var frontLegThick = false
        var strike = 0        // 0=없음, 1=앞손, 2=뒷손, 3=앞발  (피격 이펙트 위치용)
        val prog = 1f - (attackTimer / attackTotal).coerceIn(0f, 1f)  // 공격 진행 0→1
        val ext = kotlin.math.sin(prog * Math.PI.toFloat())          // 뻗었다 접힘 0→1→0

        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        when {
            // ===== 점프 공중 공격: 드롭킥 =====
            isAttacking && isJumpAttacking -> {
                frontLegThick = true
                frontLegDeg = lerp(70f, 40f, ext)   // 앞다리 아래·앞으로 뻗기
                backLegDeg = 125f
                frontArmDeg = 65f; backArmDeg = 90f   // 팔은 앞쪽 가드 유지
                strike = 3
            }
            // ===== 특수 공격: 강력한 회전 킥 =====
            isAttacking && useSpecial -> {
                frontLegThick = true
                frontLegDeg = lerp(78f, 2f, ext)    // 다리 완전 수평까지
                backLegDeg = 96f
                frontArmDeg = 62f; backArmDeg = 86f   // 팔은 앞쪽 가드 유지 (뒤로 젖혀지지 않게)
                shX = cx - dir * ext * 3f * s
                strike = 3
            }
            // ===== 콤보 2단계: 킥 마무리 =====
            isAttacking && comboStep == 2 -> {
                frontLegThick = true
                frontLegDeg = lerp(78f, 12f, ext)   // 앞으로 차올림(발이 엉덩이 높이까지)
                backLegDeg = 96f
                frontArmDeg = 66f; backArmDeg = 88f   // 팔은 앞쪽 가드 유지 (뒤로 젖혀지지 않게)
                shX = cx - dir * ext * 2f * s        // 반동으로 상체 살짝 뒤로
                strike = 3
            }
            // ===== 콤보 1단계: 투(크로스 펀치, 뒷손) =====
            isAttacking && comboStep == 1 -> {
                backArmThick = true
                backArmDeg = lerp(100f, 4f, ext)    // 뒷손이 앞으로 크로스
                frontArmDeg = 60f                    // 앞손은 가드
                shX = cx + dir * ext * 5f * s; hipX = cx + dir * ext * 2f * s   // 허리 회전
                frontLegDeg = 78f; backLegDeg = 104f
                strike = 2
            }
            // ===== 콤보 0단계: 원(잽, 앞손) =====
            isAttacking -> {
                frontArmThick = true
                frontArmDeg = lerp(58f, 5f, ext)    // 앞손 잽
                backArmDeg = 98f
                shX = cx + dir * ext * 3f * s
                strike = 1
            }
            // ===== 점프(비공격) =====
            isJumping -> {
                val f = heightFrac
                frontArmDeg = 82f - f * 52f; backArmDeg = 98f - f * 22f   // 팔 살짝 올림
                frontLegDeg = 80f - f * 30f; backLegDeg = 100f + f * 30f  // 다리 접기(발이 위로)
            }
            // ===== 이동(걷기 사이클) — 발을 높이 들어올리는 활기찬 걸음 =====
            velocityX != 0f -> {
                val ph = walkPhase
                val swing = kotlin.math.sin(ph)
                val bob = kotlin.math.abs(swing) * 3f * s
                shY = drawY - 54f * s - bob; hipY = drawY - 26f * s - bob
                frontLegDeg = 90f - swing * 46f   // 스윙 폭을 키워 발이 높이 올라온다
                backLegDeg  = 90f + swing * 46f
                frontArmDeg = 90f + swing * 32f   // 팔은 다리와 반대로 스윙
                backArmDeg  = 90f - swing * 32f
            }
            // ===== 대기(숨쉬기) =====
            else -> {
                val breath = kotlin.math.sin(idlePhase)
                shY = drawY - 54f * s + breath * 1.2f * s
                frontArmDeg = 82f + breath * 2f; backArmDeg = 98f - breath * 2f
            }
        }

        // 각도+고정 길이로 끝점 계산 (dir로 좌우 반전)
        fun endX(ox: Float, len: Float, deg: Float) = ox + dir * len * kotlin.math.cos(Math.toRadians(deg.toDouble())).toFloat()
        fun endY(oy: Float, len: Float, deg: Float) = oy + len * kotlin.math.sin(Math.toRadians(deg.toDouble())).toFloat()

        val shoulderY = shY + 2f * s
        val frontHandX = endX(shX, armLen, frontArmDeg); val frontHandY = endY(shoulderY, armLen, frontArmDeg)
        val backHandX  = endX(shX, armLen, backArmDeg);  val backHandY  = endY(shoulderY, armLen, backArmDeg)
        val frontFootX = endX(hipX, legLen, frontLegDeg); val frontFootY = endY(hipY, legLen, frontLegDeg)
        val backFootX  = endX(hipX, legLen, backLegDeg);  val backFootY  = endY(hipY, legLen, backLegDeg)

        // ----- 그리기 (뒤쪽 팔·다리 먼저) -----
        canvas.drawLine(shX, shY, hipX, hipY, bodyPaint)                                    // 척추
        canvas.drawLine(hipX, hipY, backFootX, backFootY, bodyPaint)                        // 뒷다리
        canvas.drawLine(hipX, hipY, frontFootX, frontFootY, if (frontLegThick) kickPaint else bodyPaint)

        canvas.drawLine(shX, shoulderY, backHandX, backHandY, if (backArmThick) punchPaint else bodyPaint)
        canvas.drawLine(shX, shoulderY, frontHandX, frontHandY, if (frontArmThick) punchPaint else bodyPaint)

        // 특수 공격 글로우
        if (isAttacking && useSpecial) canvas.drawLine(shX, shY, frontFootX, frontFootY, specialGlowPaint)

        // 머리(흰색으로 비우고 검은 테두리)
        val headCX = shX + dir * 1f
        val headCY = shY - headR - 3f * s
        canvas.drawCircle(headCX, headCY, headR, headFillPaint)
        canvas.drawCircle(headCX, headCY, headR, headStrokePaint)

        // 피격 이펙트: 실제로 맞췄을 때만, 타격 부위에 표시
        if (hitSparkTimer > 0f && strike != 0) {
            val (px, py) = when (strike) {
                1 -> frontHandX to frontHandY
                2 -> backHandX to backHandY
                else -> frontFootX to frontFootY
            }
            drawImpactStar(canvas, px + dir * 4f, py, 15f * s)
        }

        // HP 하트 (머리 위) — 목업(전투화면)처럼 하트 아이콘으로 표시
        val heart = SpriteManager.heart
        val gap = 2f
        val step = heart.width + gap
        val startX = cx - (hp * step - gap) / 2f
        val heartY = headCY - headR - heart.height - 6f
        for (i in 0 until hp) {
            canvas.drawBitmap(heart, startX + i * step, heartY, null)
        }
    }

    /** 공격이 실제로 적에게 명중했을 때 호출 → 짧게 피격 이펙트 표시 */
    fun triggerHitSpark() { hitSparkTimer = 0.14f }

    private fun drawImpactStar(canvas: Canvas, sx: Float, sy: Float, size: Float) {
        val path = Path()
        for (i in 0 until 6) {
            val angle = Math.toRadians(i * 60.0)
            val outerX = sx + (kotlin.math.cos(angle) * size).toFloat()
            val outerY = sy + (kotlin.math.sin(angle) * size).toFloat()
            val innerAngle = Math.toRadians(i * 60.0 + 30.0)
            val innerX = sx + (kotlin.math.cos(innerAngle) * size * 0.4f).toFloat()
            val innerY = sy + (kotlin.math.sin(innerAngle) * size * 0.4f).toFloat()
            if (i == 0) path.moveTo(outerX, outerY) else path.lineTo(outerX, outerY)
            path.lineTo(innerX, innerY)
        }
        path.close()
        canvas.drawPath(path, impactPaint)
    }

    /** 횡뷰 플랫포머: 좌우 이동만 처리한다 (dirY는 무시). */
    fun setMovement(dirX: Float, dirY: Float) {
        if (isJumping) {
            // 공중: 이륙 관성을 유지하고, 입력이 있을 때만 약하게 조향한다.
            if (dirX != 0f) {
                val tx = kotlin.math.sign(dirX) * Constants.PLAYER_SPEED
                velocityX += (tx - velocityX) * Constants.AIR_CONTROL
                facingRight = dirX > 0f
            }
            // 입력이 없으면 현재 속도를 유지 → 공중에서 멈추지 않고 포물선을 그린다.
            return
        }

        if (dirX == 0f) {
            velocityX = 0f
            return
        }
        velocityX = kotlin.math.sign(dirX) * Constants.PLAYER_SPEED
        facingRight = dirX > 0f
    }

    fun jump() {
        if (!isJumping) { isJumping = true; jumpVelocity = Constants.JUMP_VELOCITY }
    }

    /** 공격 발동 시 true, 이미 공격 중이라 무시되면 false 반환 */
    fun performAttack(): Boolean {
        if (!isAttacking) {
            isAttacking = true
            useSpecial = false
            isJumpAttacking = isJumping
            if (isJumpAttacking) {
                // 점프 공격은 콤보와 무관
                comboStep = 0
                attackTimer = Constants.ATTACK_DURATION
            } else {
                // 지상 콤보: 창이 열려 있으면 원→투→킥 순서로 진행
                comboStep = if (comboResetTimer > 0f) (comboStep + 1) % 3 else 0
                comboResetTimer = Constants.COMBO_WINDOW
                attackTimer = if (comboStep == 2) Constants.KICK_DURATION else Constants.ATTACK_DURATION
            }
            attackTotal = attackTimer
            return true
        }
        return false
    }

    /** 특수 공격 발동 시 true, 게이지 부족/공격 중이면 false 반환 */
    fun performSpecial(): Boolean {
        if (specialGauge >= Constants.SPECIAL_MAX && !isAttacking) {
            isAttacking = true; attackTimer = Constants.ATTACK_DURATION * 2f
            attackTotal = attackTimer
            specialGauge = 0f; useSpecial = true; comboStep = 0
            return true
        }
        return false
    }

    fun isUsingSpecial(): Boolean = useSpecial
    fun getCurrentAttackPower(): Int = when {
        isJumpAttacking -> (attackPower * 1.5f).toInt()
        useSpecial -> specialPower
        comboStep == 2 -> (attackPower * 1.4f).toInt()   // 킥 마무리
        else -> attackPower
    }

    fun getAttackHitBox(): RectF? {
        if (!isAttacking) return null
        val range = when {
            isJumpAttacking -> 125f
            useSpecial -> 140f
            comboStep == 2 -> Constants.KICK_RANGE       // 킥 마무리 사거리
            else -> Constants.ATTACK_RANGE
        }
        // 울퉁불퉁한 지형에서 경사 위/아래 적도 맞도록 세로 판정을 넉넉하게 잡는다
        val atkHeight = 170f
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
        velocityX = 0f; jumpZ = 0f; jumpVelocity = 0f
        isJumping = false; isAttacking = false
        comboStep = 0; comboResetTimer = 0f; walkPhase = 0f
        isInvincible = true; invincibilityTimer = Constants.INVINCIBILITY_DURATION * 2f
    }

    fun reset(startX: Float, startY: Float) {
        respawn(startX, startY)
        lives = 5; score = 0; rescueCount = 0; specialGauge = 0f
        isDead = false; isInvincible = false; level = 1; killCount = 0
    }
}
