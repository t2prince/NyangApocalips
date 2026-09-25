package com.jamcat.nayngapo.ui

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.InputState

class TouchControls {
    val inputState = InputState()

    /** 필살기 남은 쿨다운(초). GamePlayScreen이 매 프레임 갱신 → SP 버튼에 카운트다운 표시 */
    var specialCooldown = 0f

    /**
     * 하늘 모드 (날개냥이 탑승): 패드가 상하좌우 8방향이 되고 JUMP 버튼은 사라진다.
     * SP 버튼은 로켓 발사에 쓴다.
     */
    var skyMode = false

    // ===== 팀 냥이 지시 버튼 =====
    /** 살아 있는 아군이 있을 때만 보인다 */
    var showOrder = false
    var orderLabel = "따라와"
    var orderColor = Color.rgb(255, 214, 74)
    /** 지시를 막 바꿨을 때 살짝 커지는 연출에 남은 시간 (0 ~ 0.2초) */
    var orderPulse = 0f

    // ===== 고양이 기술 버튼 (배운 기술만, 최대 3개) =====
    var skills: List<com.jamcat.nayngapo.engine.CatSkills.Skill> = emptyList()
    /** skills와 같은 순서로 남은 쿨타임 */
    var skillCooldowns: FloatArray = FloatArray(0)
    /** 기술을 대신 써 줄 살아 있는 냥이가 전장에 있는가 (없으면 버튼이 흐려진다) */
    var skillsHaveCaster = true

    // ===== 인간 기술 버튼 (배운 기술만, 최대 3개 — JUMP 위 한 줄, 냥이가 없어도 쓴다) =====
    var humanSkills: List<com.jamcat.nayngapo.engine.CatSkills.Skill> = emptyList()
    /** humanSkills와 같은 순서로 남은 쿨타임 */
    var humanSkillCooldowns: FloatArray = FloatArray(0)

    // D-pad config (larger for easier touch)
    private val dpadRadius = 240f
    private val dpadInnerRadius = 55f
    private var dpadCenterX = 0f
    private var dpadCenterY = 0f

    // Action buttons config (larger)
    private val btnRadius = 125f
    private var jumpBtnX = 0f
    private var jumpBtnY = 0f
    private var attackBtnX = 0f
    private var attackBtnY = 0f
    private var specialBtnX = 0f
    private var specialBtnY = 0f

    private val pointerButtons = mutableMapOf<Int, String>()

    // ── 세련된 톤: 어두운 반투명 + 얇은 링 + 강조색 하나 ──
    private val padPaint = Paint().apply { color = Color.argb(48, 12, 14, 20); isAntiAlias = true }
    private val padEdgePaint = Paint().apply {
        color = Color.argb(45, 255, 255, 255); style = Paint.Style.STROKE
        strokeWidth = 2f; isAntiAlias = true
    }
    private val arrowPaint = Paint().apply {
        color = Color.argb(130, 235, 240, 250); style = Paint.Style.STROKE
        strokeWidth = 7f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val arrowActivePaint = Paint().apply {
        color = ACCENT; style = Paint.Style.STROKE
        strokeWidth = 8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val btnFillPaint = Paint().apply { color = Color.argb(90, 14, 16, 24); isAntiAlias = true }
    private val btnRingPaint = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true
    }
    private val btnTextPaint = Paint().apply {
        color = Color.argb(235, 240, 244, 252); textSize = 40f; textAlign = Paint.Align.CENTER
        letterSpacing = 0.1f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    // 필살기 쿨다운 표시용
    private val cooldownDimPaint = Paint().apply {
        color = Color.argb(150, 10, 12, 18); isAntiAlias = true
    }
    private val cooldownArcPaint = Paint().apply {
        color = ACCENT; style = Paint.Style.STROKE
        strokeWidth = 7f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val cooldownTextPaint = Paint().apply {
        color = Color.argb(230, 240, 244, 252); textSize = 54f; textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    private val cooldownArcRect = RectF()
    private val padRect = RectF()
    private val orderRect = RectF()
    private val orderFillPaint = Paint().apply { color = Color.argb(110, 14, 16, 24); isAntiAlias = true }
    private val orderEdgePaint = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 3.5f; isAntiAlias = true
    }
    private val orderTextPaint = Paint().apply {
        color = Color.WHITE; textSize = 34f; textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    private val orderCatPaint = Paint().apply { isAntiAlias = true }
    /** 기술 버튼 자리 (최대 3칸) */
    private val skillCenters = FloatArray(SKILL_SLOTS * 2)
    private val humanSkillCenters = FloatArray(HUMAN_SKILL_SLOTS * 2)
    private val skillIconDst = RectF()

    fun updateLayout(screenWidth: Int, screenHeight: Int) {
        dpadCenterX = 300f
        dpadCenterY = screenHeight - 290f

        val rightBase = screenWidth - 250f
        val bottomBase = screenHeight - 230f
        attackBtnX = rightBase
        attackBtnY = bottomBase
        jumpBtnX = rightBase - 255f
        jumpBtnY = bottomBase - 90f
        specialBtnX = rightBase - 50f
        specialBtnY = bottomBase - 270f

        // 지시 버튼: SP 버튼 위의 납작한 알약
        val ow = 170f
        val oh = 64f
        val ocx = specialBtnX - 50f
        val ocy = specialBtnY - 150f
        orderRect.set(ocx - ow / 2f, ocy - oh / 2f, ocx + ow / 2f, ocy + oh / 2f)

        // 기술 버튼: 지시 알약 왼쪽으로 줄지어 놓는다
        for (i in 0 until SKILL_SLOTS) {
            skillCenters[i * 2] = specialBtnX - 300f - i * 110f
            skillCenters[i * 2 + 1] = specialBtnY - 150f
        }

        // 인간 기술 버튼: JUMP 위 한 줄.
        // 2400·1920 x 1080에서 모든 버튼과 겹치지 않게 잰 자리 — 가장 가까운 것은
        // 고양이 기술 줄의 '냥이 없음' 글씨(15px)와 JUMP 터치 범위(5.7px)다.
        // 네 번째 칸은 셋째 칸 왼쪽 — 고양이 기술 줄(가장 왼쪽 가장자리)보다 더 왼쪽이라 겹치지 않는다.
        for (i in 0 until HUMAN_SKILL_SLOTS) {
            humanSkillCenters[i * 2] = jumpBtnX - 90f - i * 110f
            humanSkillCenters[i * 2 + 1] = jumpBtnY - 185f
        }
    }

    fun onTouchEvent(event: MotionEvent, screenWidth: Int, screenHeight: Int): Boolean {
        if (dpadCenterX == 0f) updateLayout(screenWidth, screenHeight)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                handlePress(event.getPointerId(idx), event.getX(idx), event.getY(idx))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    handlePress(event.getPointerId(i), event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                handleRelease(event.getPointerId(event.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerButtons.clear(); resetInput()
            }
        }
        return true
    }

    private fun handlePress(pointerId: Int, px: Float, py: Float) {
        val prevButton = pointerButtons[pointerId]

        val dx = px - dpadCenterX
        val dy = py - dpadCenterY
        val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (dist < dpadRadius + 30f) {
            pointerButtons[pointerId] = "dpad"
            inputState.moveLeft = false; inputState.moveRight = false
            inputState.moveUp = false; inputState.moveDown = false

            if (dist > dpadInnerRadius) {
                if (skyMode) {
                    // 하늘: 상하좌우 자유 비행 (대각선 허용)
                    if (kotlin.math.abs(dx) > dpadInnerRadius * 0.55f) {
                        if (dx < 0f) inputState.moveLeft = true else inputState.moveRight = true
                    }
                    if (kotlin.math.abs(dy) > dpadInnerRadius * 0.55f) {
                        if (dy < 0f) inputState.moveUp = true else inputState.moveDown = true
                    }
                } else {
                    // 땅: 상하좌우 십자 (대표님 2026-09-20). 위 = 점프, 아래 = 발판에서 내려오기. 대각선이면 좌우+위 동시
                    if (kotlin.math.abs(dx) > dpadInnerRadius * 0.55f || kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
                        if (dx < 0f) inputState.moveLeft = true else inputState.moveRight = true
                    }
                    if (kotlin.math.abs(dy) > dpadInnerRadius * 0.55f && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 0.6f) {
                        if (dy < 0f) inputState.moveUp = true else inputState.moveDown = true
                    }
                }
            }
            return
        }

        if (skyMode) {
            // 하늘 모드에는 JUMP 버튼이 없다
            val hitR = btnRadius + 15f
            if (kotlin.math.hypot((px - attackBtnX).toDouble(), (py - attackBtnY).toDouble()) < hitR) {
                pointerButtons[pointerId] = "attack"; inputState.attack = true; return
            }
            if (kotlin.math.hypot((px - specialBtnX).toDouble(), (py - specialBtnY).toDouble()) < hitR) {
                pointerButtons[pointerId] = "special"; inputState.special = true; return
            }
            if (prevButton != null) { releaseButton(prevButton); pointerButtons.remove(pointerId) }
            return
        }

        // 인간 기술 버튼: 냥이가 없어도 언제나 쓸 수 있다 (쿨타임이 끝난 기술만, 누를 때 한 번만)
        for (i in humanSkills.indices) {
            if (i >= HUMAN_SKILL_SLOTS) break
            val sx = humanSkillCenters[i * 2]
            val sy = humanSkillCenters[i * 2 + 1]
            if (kotlin.math.hypot((px - sx).toDouble(), (py - sy).toDouble()) > SKILL_RADIUS + 12f) continue
            val key = "hskill$i"
            if (prevButton != key) {
                pointerButtons[pointerId] = key
                if ((humanSkillCooldowns.getOrNull(i) ?: 0f) <= 0f) inputState.useHumanSkill = humanSkills[i].id
            }
            return
        }

        // 고양이 기술 버튼: 쿨타임이 끝난 기술만, 누를 때 한 번만
        for (i in skills.indices) {
            if (i >= SKILL_SLOTS) break
            val sx = skillCenters[i * 2]
            val sy = skillCenters[i * 2 + 1]
            if (kotlin.math.hypot((px - sx).toDouble(), (py - sy).toDouble()) > SKILL_RADIUS + 12f) continue
            val key = "skill$i"
            if (prevButton != key) {
                pointerButtons[pointerId] = key
                if ((skillCooldowns.getOrNull(i) ?: 0f) <= 0f) inputState.useSkill = skills[i].id
            }
            return
        }

        // 지시 버튼: 누를 때 딱 한 번만 지시를 바꾼다 (손을 얹고 있어도 계속 돌지 않게)
        if (showOrder && orderRect.contains(px, py)) {
            if (prevButton != "order") {
                pointerButtons[pointerId] = "order"
                inputState.cycleOrder = true
            }
            return
        }

        val hitRadius = btnRadius + 15f
        if (kotlin.math.hypot((px - attackBtnX).toDouble(), (py - attackBtnY).toDouble()) < hitRadius) {
            pointerButtons[pointerId] = "attack"; inputState.attack = true; return
        }
        if (kotlin.math.hypot((px - jumpBtnX).toDouble(), (py - jumpBtnY).toDouble()) < hitRadius) {
            pointerButtons[pointerId] = "jump"; inputState.jump = true; return
        }
        if (kotlin.math.hypot((px - specialBtnX).toDouble(), (py - specialBtnY).toDouble()) < hitRadius) {
            pointerButtons[pointerId] = "special"; inputState.special = true; return
        }

        if (prevButton != null) { releaseButton(prevButton); pointerButtons.remove(pointerId) }
    }

    private fun handleRelease(pointerId: Int) {
        pointerButtons[pointerId]?.let { releaseButton(it); pointerButtons.remove(pointerId) }
    }

    private fun releaseButton(button: String) {
        when (button) {
            "dpad" -> {
                inputState.moveLeft = false; inputState.moveRight = false
                inputState.moveUp = false; inputState.moveDown = false
            }
            "attack" -> inputState.attack = false
            "jump" -> inputState.jump = false
            "special" -> inputState.special = false
            "order" -> Unit          // 한 번 누름으로 끝나는 버튼이라 놓을 때 할 일이 없다
            // 기술 버튼도 한 번 누름 — 놓을 때 되돌릴 것이 없다
            "skill0", "skill1", "skill2", "hskill0", "hskill1", "hskill2", "hskill3" -> Unit
        }
    }

    /**
     * 누르고 있던 조작을 모두 놓은 것으로 처리한다.
     * 멈추기·나가기처럼 조작을 가로채는 상황에서 부르지 않으면
     * ACTION_UP이 오지 않아 '계속 오른쪽으로 걷는' 상태로 굳는다.
     */
    fun releaseAll() {
        pointerButtons.clear()
        resetInput()
    }

    private fun resetInput() {
        inputState.moveLeft = false; inputState.moveRight = false
        inputState.moveUp = false; inputState.moveDown = false
        inputState.jump = false; inputState.attack = false; inputState.special = false
        inputState.cycleOrder = false
        inputState.useSkill = null
        inputState.useHumanSkill = null
    }

    fun draw(canvas: Canvas, screenWidth: Int, screenHeight: Int) {
        if (dpadCenterX == 0f) updateLayout(screenWidth, screenHeight)

        if (skyMode) {
            // === 하늘: 둥근 8방향 패드 ===
            canvas.drawCircle(dpadCenterX, dpadCenterY, dpadRadius * 0.72f, padPaint)
            canvas.drawCircle(dpadCenterX, dpadCenterY, dpadRadius * 0.72f, padEdgePaint)
            drawChevron(canvas, dpadCenterX - 108f, dpadCenterY, -1f, inputState.moveLeft)
            drawChevron(canvas, dpadCenterX + 108f, dpadCenterY, 1f, inputState.moveRight)
            drawChevronV(canvas, dpadCenterX, dpadCenterY - 108f, -1f, inputState.moveUp)
            drawChevronV(canvas, dpadCenterX, dpadCenterY + 108f, 1f, inputState.moveDown)

            drawButton(canvas, attackBtnX, attackBtnY, "attack", inputState.attack, Color.rgb(255, 118, 106))
            drawSpecialButton(canvas, "rocket")
            return
        }

        // === 땅: 둥근 상하좌우 십자 패드 (위 = 점프, 아래 = 발판에서 내려오기) ===
        canvas.drawCircle(dpadCenterX, dpadCenterY, dpadRadius * 0.72f, padPaint)
        canvas.drawCircle(dpadCenterX, dpadCenterY, dpadRadius * 0.72f, padEdgePaint)
        drawChevron(canvas, dpadCenterX - 108f, dpadCenterY, -1f, inputState.moveLeft)
        drawChevron(canvas, dpadCenterX + 108f, dpadCenterY, 1f, inputState.moveRight)
        drawChevronV(canvas, dpadCenterX, dpadCenterY - 108f, -1f, inputState.moveUp)
        drawChevronV(canvas, dpadCenterX, dpadCenterY + 108f, 1f, inputState.moveDown)

        // === 액션 버튼 (글자 대신 아이콘 — 대표님 2026-09-20) ===
        drawButton(canvas, attackBtnX, attackBtnY, "attack", inputState.attack, Color.rgb(255, 118, 106))
        drawButton(canvas, jumpBtnX, jumpBtnY, "jump", inputState.jump, Color.rgb(120, 190, 255))
        drawSpecialButton(canvas)
        if (showOrder) drawOrderButton(canvas)
        drawSkillButtons(canvas)
        drawHumanSkillButtons(canvas)
    }

    /** 배운 고양이 기술 버튼: 그림 + 쿨타임 링 + 남은 초 */
    private fun drawSkillButtons(canvas: Canvas) {
        drawSkillRow(
            canvas, skills, skillCooldowns, skillCenters,
            com.jamcat.nayngapo.engine.SpriteManager.skillIcons, ACCENT, requiresCaster = true
        )
    }

    /** 배운 인간 기술 버튼 — 고양이 기술과 같은 모양, 파란 테두리로 구분한다 */
    private fun drawHumanSkillButtons(canvas: Canvas) {
        drawSkillRow(
            canvas, humanSkills, humanSkillCooldowns, humanSkillCenters,
            com.jamcat.nayngapo.engine.SpriteManager.humanSkillIcons, HUMAN_RING, requiresCaster = false
        )
    }

    /** 기술 버튼 한 줄: 그림 + 준비 테두리 + 쿨타임 링 + 남은 초 (고양이·인간 기술이 함께 쓴다) */
    private fun drawSkillRow(
        canvas: Canvas,
        list: List<com.jamcat.nayngapo.engine.CatSkills.Skill>,
        cooldowns: FloatArray,
        centers: FloatArray,
        icons: Map<String, android.graphics.Bitmap>,
        readyRing: Int,
        requiresCaster: Boolean
    ) {
        for (i in list.indices) {
            if (i * 2 >= centers.size) break      // 줄마다 칸 수가 다르다 (고양이 3, 인간 4)
            val skill = list[i]
            val cx = centers[i * 2]
            val cy = centers[i * 2 + 1]
            val cool = cooldowns.getOrNull(i) ?: 0f
            // 쿨타임이 없는 기술(파이어 펀치)은 언제나 준비된 모습
            val ready = cool <= 0f || skill.cooldown <= 0f

            canvas.drawCircle(cx, cy, SKILL_RADIUS, btnFillPaint)
            btnRingPaint.style = Paint.Style.STROKE
            btnRingPaint.color = if (ready) readyRing else Color.argb(120, 180, 180, 190)
            canvas.drawCircle(cx, cy, SKILL_RADIUS, btnRingPaint)

            val icon = icons[skill.id]
            if (icon != null) {
                val side = SKILL_RADIUS * 1.30f
                val sc = kotlin.math.min(side / icon.width, side / icon.height)
                val iw = icon.width * sc
                val ih = icon.height * sc
                skillIconDst.set(cx - iw / 2f, cy - ih / 2f, cx + iw / 2f, cy + ih / 2f)
                canvas.drawBitmap(icon, null, skillIconDst, skillIconPaint)
            }

            // 고양이 기술은 출전한 냥이가 없으면 흐리게 두고 이유를 적는다 (눌러도 기술은 나가지 않는다)
            if (requiresCaster && !skillsHaveCaster) {
                canvas.drawCircle(cx, cy, SKILL_RADIUS, cooldownDimPaint)
                canvas.drawText("냥이 없음", cx, cy + SKILL_RADIUS + 26f, noCasterPaint)
                continue
            }

            if (ready) continue

            // 쿨타임: 어둡게 덮고 12시부터 링이 차오른다
            canvas.drawCircle(cx, cy, SKILL_RADIUS, cooldownDimPaint)
            val filled = 1f - (cool / skill.cooldown).coerceIn(0f, 1f)
            cooldownArcRect.set(
                cx - SKILL_RADIUS + 8f, cy - SKILL_RADIUS + 8f,
                cx + SKILL_RADIUS - 8f, cy + SKILL_RADIUS - 8f
            )
            canvas.drawArc(cooldownArcRect, -90f, 360f * filled, false, cooldownArcPaint)
            canvas.drawText(
                kotlin.math.ceil(cool).toInt().toString(), cx, cy + 20f, cooldownTextPaint
            )
        }
    }

    /** 팀 냥이 지시 버튼: 고양이 얼굴 + 지금 지시 (누르면 다음 지시로 돌아간다) */
    private fun drawOrderButton(canvas: Canvas) {
        val scale = 1f + (orderPulse / ORDER_PULSE_TIME).coerceIn(0f, 1f) * 0.15f
        canvas.save()
        canvas.scale(scale, scale, orderRect.centerX(), orderRect.centerY())

        val r = orderRect.height() / 2f
        canvas.drawRoundRect(orderRect, r, r, orderFillPaint)
        orderEdgePaint.color = orderColor
        canvas.drawRoundRect(orderRect, r, r, orderEdgePaint)

        // 왼쪽에 작은 고양이 얼굴 (귀 둘 + 머리)
        val fx = orderRect.left + orderRect.height() * 0.62f
        val fy = orderRect.centerY()
        val hr = orderRect.height() * 0.24f
        orderCatPaint.color = orderColor
        canvas.drawCircle(fx, fy + hr * 0.2f, hr, orderCatPaint)
        earPath.reset()
        earPath.moveTo(fx - hr * 0.95f, fy - hr * 0.45f)
        earPath.lineTo(fx - hr * 0.35f, fy - hr * 1.35f)
        earPath.lineTo(fx - hr * 0.1f, fy - hr * 0.55f)
        earPath.close()
        earPath.moveTo(fx + hr * 0.95f, fy - hr * 0.45f)
        earPath.lineTo(fx + hr * 0.35f, fy - hr * 1.35f)
        earPath.lineTo(fx + hr * 0.1f, fy - hr * 0.55f)
        earPath.close()
        canvas.drawPath(earPath, orderCatPaint)

        // 지시 이름
        canvas.drawText(
            orderLabel,
            (orderRect.left + orderRect.height() * 1.15f + orderRect.right) / 2f,
            orderRect.centerY() + 12f, orderTextPaint
        )
        canvas.restore()
    }

    /** 세로 방향 화살표 (하늘 패드의 위/아래) */
    private fun drawChevronV(canvas: Canvas, cx: Float, cy: Float, dir: Float, active: Boolean) {
        val paint = if (active) arrowActivePaint else arrowPaint
        val w = 30f
        val h = 22f
        canvas.drawLine(cx - w, cy - dir * h * 0.5f, cx, cy + dir * h * 0.5f, paint)
        canvas.drawLine(cx, cy + dir * h * 0.5f, cx + w, cy - dir * h * 0.5f, paint)
    }

    /** 방향 표시: 꺾인 선 두 개로 그린 얇은 화살표 */
    private fun drawChevron(canvas: Canvas, cx: Float, cy: Float, dir: Float, active: Boolean) {
        val paint = if (active) arrowActivePaint else arrowPaint
        val w = 22f
        val h = 30f
        canvas.drawLine(cx + dir * w * 0.5f, cy - h, cx - dir * w * 0.5f, cy, paint)
        canvas.drawLine(cx - dir * w * 0.5f, cy, cx + dir * w * 0.5f, cy + h, paint)
    }

    /** SP(필살기) 버튼: 쿨다운 중에는 어둡게 덮고 남은 초를 카운트다운으로 보여준다 */
    private fun drawSpecialButton(canvas: Canvas, label: String = "special") {
        drawCooldownButton(canvas, specialBtnX, specialBtnY, label, inputState.special,
            Color.argb(80, 255, 200, 50), specialCooldown, Constants.SPECIAL_COOLDOWN)
    }

    /** 쿨다운이 있는 기술 버튼 공통 그리기 (SP · FG) */
    private fun drawCooldownButton(
        canvas: Canvas, cx: Float, cy: Float, label: String, pressed: Boolean,
        color: Int, cooldown: Float, cooldownTotal: Float
    ) {
        val ready = cooldown <= 0f
        drawButton(canvas, cx, cy, if (ready) label else "", pressed && ready, color)
        if (ready) return

        canvas.drawCircle(cx, cy, btnRadius, cooldownDimPaint)

        // 남은 시간만큼 링이 시계방향으로 차오른다 (가득 차면 사용 가능)
        val filled = 1f - (cooldown / cooldownTotal).coerceIn(0f, 1f)
        cooldownArcRect.set(
            cx - btnRadius + 10f, cy - btnRadius + 10f,
            cx + btnRadius - 10f, cy + btnRadius - 10f
        )
        canvas.drawArc(cooldownArcRect, -90f, 360f * filled, false, cooldownArcPaint)

        // 남은 초(올림)
        canvas.drawText(
            kotlin.math.ceil(cooldown).toInt().toString(), cx, cy + 24f, cooldownTextPaint
        )
    }

    private fun drawButton(canvas: Canvas, cx: Float, cy: Float, label: String, active: Boolean, color: Int) {
        // 평소엔 어두운 원 + 색 링, 누르면 그 색이 은은하게 차오른다
        canvas.drawCircle(cx, cy, btnRadius, btnFillPaint)
        if (active) {
            btnRingPaint.style = Paint.Style.FILL
            btnRingPaint.color = Color.argb(70, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawCircle(cx, cy, btnRadius, btnRingPaint)
        }
        btnRingPaint.style = Paint.Style.STROKE
        btnRingPaint.color = if (active) color else
            Color.argb(150, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(cx, cy, btnRadius, btnRingPaint)

        if (label.isNotEmpty()) drawIcon(canvas, cx, cy, label, active)
    }

    private val iconPaint = Paint().apply {
        color = Color.argb(235, 240, 244, 252); style = Paint.Style.STROKE
        strokeWidth = 7f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
    }
    private val iconFillPaint = Paint().apply { color = Color.argb(235, 240, 244, 252); isAntiAlias = true }
    private val iconPath = android.graphics.Path()

    /** 버튼 아이콘: attack = 발톱 세 줄, jump = 위 화살표, special = 별, rocket = 로켓. 모르는 이름은 글자로. */
    private fun drawIcon(canvas: Canvas, cx: Float, cy: Float, kind: String, active: Boolean) {
        iconPaint.color = if (active) ACCENT else Color.argb(235, 240, 244, 252)
        iconFillPaint.color = iconPaint.color
        when (kind) {
            "attack" -> {                                   // 발톱 자국 세 줄
                for (i in -1..1) {
                    val ox = i * 16f
                    canvas.drawLine(cx + ox - 8f, cy - 22f, cx + ox + 8f, cy + 22f, iconPaint)
                }
            }
            "jump" -> {                                     // 위 화살표
                canvas.drawLine(cx, cy + 22f, cx, cy - 22f, iconPaint)
                canvas.drawLine(cx - 18f, cy - 4f, cx, cy - 22f, iconPaint)
                canvas.drawLine(cx + 18f, cy - 4f, cx, cy - 22f, iconPaint)
            }
            "special" -> {                                  // 별
                iconPath.reset()
                for (k in 0 until 10) {
                    val r = if (k % 2 == 0) 26f else 11f
                    val a = Math.toRadians((-90 + k * 36).toDouble())
                    val px = cx + (r * Math.cos(a)).toFloat(); val py = cy + (r * Math.sin(a)).toFloat()
                    if (k == 0) iconPath.moveTo(px, py) else iconPath.lineTo(px, py)
                }
                iconPath.close()
                canvas.drawPath(iconPath, iconFillPaint)
            }
            "rocket" -> {                                   // 로켓 (위로)
                iconPath.reset()
                iconPath.moveTo(cx, cy - 26f); iconPath.lineTo(cx + 12f, cy - 4f); iconPath.lineTo(cx + 12f, cy + 14f)
                iconPath.lineTo(cx - 12f, cy + 14f); iconPath.lineTo(cx - 12f, cy - 4f); iconPath.close()
                canvas.drawPath(iconPath, iconFillPaint)
                canvas.drawLine(cx - 12f, cy + 6f, cx - 22f, cy + 20f, iconPaint)
                canvas.drawLine(cx + 12f, cy + 6f, cx + 22f, cy + 20f, iconPaint)
                canvas.drawLine(cx - 6f, cy + 16f, cx, cy + 28f, iconPaint); canvas.drawLine(cx + 6f, cy + 16f, cx, cy + 28f, iconPaint)
            }
            else -> canvas.drawText(kind, cx, cy + 14f, btnTextPaint)
        }
    }

    private val earPath = android.graphics.Path()
    private val skillIconPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val noCasterPaint = Paint().apply {
        color = Color.argb(230, 236, 240, 248); textSize = 22f; textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        setShadowLayer(3f, 0f, 1f, Color.argb(160, 0, 0, 0))
    }

    private companion object {
        val ACCENT = Color.rgb(255, 205, 84)

        /** 지시를 바꿨을 때 버튼이 커졌다 돌아오는 시간 */
        const val ORDER_PULSE_TIME = 0.2f

        /** 화면에 놓을 기술 버튼 칸 수 */
        const val SKILL_SLOTS = 3

        /** 인간 기술 버튼 칸 수 */
        const val HUMAN_SKILL_SLOTS = 4

        /** 기술 버튼 반지름 (지름 96px) */
        const val SKILL_RADIUS = 48f

        /** 인간 기술 버튼 테두리 (고양이 기술의 노란 테두리와 구분) */
        val HUMAN_RING = Color.rgb(110, 170, 255)
    }
}
