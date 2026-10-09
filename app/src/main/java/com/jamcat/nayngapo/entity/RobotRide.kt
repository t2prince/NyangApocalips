package com.jamcat.nayngapo.entity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.jamcat.nayngapo.engine.RobotCatalog
import com.jamcat.nayngapo.engine.SpriteManager
import kotlin.math.abs
import kotlin.math.sin

/**
 * 인간이 타는 로봇 (로봇 상점, 대표님 2026-10-09). 땅 스테이지에서 처음부터 타고 시작한다.
 * 로봇 피(Lv1 100)가 있는 동안 인간 대신 맞고(하트 1 = 피 10), 공격은 로봇 주먹(Lv1 50). 피가 다 닳으면 부서져 인간이 내린다.
 * 인간은 맨 위 조종석에 앉아(자동차처럼 고정된 자세, 조종석 테두리 아래는 잘라 그림) 있다.
 */
class RobotRide(val level: Int) {

    val maxHp = RobotCatalog.hpOf(level)
    val attack = RobotCatalog.attackOf(level)
    var riding = false
        private set
    private var savedAttack = 0
    private var anim = 0f
    private val dst = RectF()

    fun mount(p: Player) {
        riding = true
        p.robotHp = maxHp; p.robotMaxHp = maxHp; p.robotJustBroke = false
        p.seatedInCar = true
        p.rideLift = SEAT_LIFT
        savedAttack = p.attackPower
        p.attackPower = attack
    }

    /** 부서졌다 — 인간이 내린다 (공격력도 원래대로) */
    fun breakDown(p: Player) {
        if (!riding) return
        riding = false
        p.robotHp = 0; p.robotJustBroke = false
        p.seatedInCar = false
        p.rideLift = 0f
        p.attackPower = savedAttack
    }

    /** 발에서 불을 뿜으며 나는 중 (대표님 2026-10-10 「로봇을 타고 있으면 발에서 불이 나오면서 날 수 있어」) — 화면이 매 프레임 넣어 준다 */
    var thrusting = false
    /** 날개 기술을 켰는가 — 켜면 공중에 떠서 날고(▲▼로 높이), 끄면 내려온다 (대표님 2026-10-10 「이름: 날개, 특성: 하늘을 난다」) */
    var wings = false
    /** 날고 있는 높이 · 목표 높이 */
    var flyHeight = 0f
        private set
    private var flyTarget = 0f
    /** 필살기 '팔 회전' 남은 시간 (팔이 빙글빙글 돈다) */
    var spinLeft = 0f
        private set

    /** 날개를 켜고 끈다 */
    fun toggleWings() { wings = !wings; flyTarget = if (wings) HOVER_HEIGHT else 0f }

    /** 날기: ▲▼로 목표 높이를 바꾸고 천천히 따라간다. 땅에 닿으면 0 */
    fun updateFlight(deltaTime: Float, up: Boolean, down: Boolean) {
        if (wings) {
            if (up) flyTarget = (flyTarget + 360f * deltaTime).coerceAtMost(MAX_FLY_HEIGHT)
            if (down) flyTarget = (flyTarget - 360f * deltaTime).coerceAtLeast(60f)
        }
        flyHeight += (flyTarget - flyHeight) * (deltaTime * 4f).coerceAtMost(1f)
        if (!wings && flyHeight < 2f) flyHeight = 0f
        thrusting = flyHeight > 4f
    }

    /** 필살기 시작 — 1초 동안 팔이 돈다 */
    fun startSpin() { spinLeft = SPIN_TIME }

    /** 발사한 주먹이 돌아오기까지 남은 시간 — 그동안 팔 끝에 주먹이 없다 */
    var fistAway = 0f
        private set

    fun update(deltaTime: Float) {
        anim += deltaTime
        if (fistAway > 0f) fistAway -= deltaTime
        if (spinLeft > 0f) spinLeft -= deltaTime
    }

    /** 주먹 발사 (대표님 2026-10-10 「로봇이 주먹 발사」): 앞 주먹이 불꽃 꼬리를 달고 날아가 로봇 공격력만큼 때린다. 0.8초 뒤 다시 생긴다 */
    fun launchFist(p: Player, damage: Int = attack): Projectile? {
        if (!riding || fistAway > 0f) return null
        val fist = SpriteManager.robotFist ?: return null
        fistAway = FIST_RELOAD
        place(p)
        val dir = if (p.facingRight) 1f else -1f
        val r = 28f
        val fx = if (wristX != 0f) wristX else dst.centerX() + dir * dst.width() * 0.42f
        val fy = if (wristY != 0f) wristY else dst.top + DRAW_H * 0.47f
        return Projectile(fx - r, fy - r, dir * FIST_SPEED, 0f, damage, true, radius = r, sprite = fist)
    }

    /** 조종석 테두리 아래로는 인간을 안 그린다 (화면이 인간을 그리기 전에 부르고, 끝나면 restore) */
    fun beginSeat(canvas: Canvas, p: Player) {
        canvas.save()
        if (!riding) return
        place(p)
        canvas.clipRect(-1e6f, -1e6f, 1e6f, dst.top + DRAW_H * RIM_LINE)
    }

    /** 로봇 그리기 (인간 앞) + 머리 위 피 막대 */
    fun drawRiding(canvas: Canvas, p: Player) {
        if (!riding) return
        val bmp = place(p) ?: return
        // 날 때: 두 발에서 불꽃 (깜빡깜빡 길이가 바뀐다)
        if (thrusting) {
            for (fxr in floatArrayOf(0.30f, 0.66f)) {
                val fx = if (p.facingRight) dst.left + dst.width() * fxr else dst.right - dst.width() * fxr
                val len = 50f + abs(sin(anim * 30f + fxr * 7f)) * 40f
                flamePath.reset()
                flamePath.moveTo(fx - 26f, dst.bottom - 6f); flamePath.lineTo(fx + 26f, dst.bottom - 6f); flamePath.lineTo(fx, dst.bottom + len); flamePath.close()
                canvas.drawPath(flamePath, flameOuter)
                flamePath.reset()
                flamePath.moveTo(fx - 13f, dst.bottom - 6f); flamePath.lineTo(fx + 13f, dst.bottom - 6f); flamePath.lineTo(fx, dst.bottom + len * 0.6f); flamePath.close()
                canvas.drawPath(flamePath, flameInner)
            }
        }
        if (drawParts(canvas, p)) { drawHpBar(canvas, p); return }
        // (부품 그림이 없을 때) 통그림 — 주먹을 쏘고 나면 팔 끝 주먹 자리는 그리지 않는다
        canvas.save()
        if (fistAway > 0f) {
            val fl = if (p.facingRight) dst.left + dst.width() * FIST_L else dst.right - dst.width() * FIST_R
            val fr = if (p.facingRight) dst.left + dst.width() * FIST_R else dst.right - dst.width() * FIST_L
            canvas.clipOutRect(fl, dst.top + DRAW_H * 0.32f, fr, dst.top + DRAW_H * 0.62f)
        }
        canvas.drawBitmap(bmp, null, dst, null)
        canvas.restore()
        drawHpBar(canvas, p)
    }

    private fun drawHpBar(canvas: Canvas, p: Player) {
        val bw = dst.width() * 0.7f
        val by = dst.top - 40f - (p.height - SEAT_LIFT).coerceAtLeast(0f) * 0.6f
        val left = dst.centerX() - bw / 2f
        bar.set(left, by, left + bw, by + 12f)
        canvas.drawRoundRect(bar, 6f, 6f, barBack)
        bar.set(left, by, left + bw * p.robotHp / maxHp.toFloat(), by + 12f)
        canvas.drawRoundRect(bar, 6f, 6f, barFill)
        hpText.textSize = 24f
        canvas.drawText("로봇 ${p.robotHp}/$maxHp", dst.centerX(), by - 8f, hpText)
    }

    /** 걸으면 쿵쿵 들썩이고, 때리면 앞으로 내지른다 */
    private fun place(p: Player): android.graphics.Bitmap? {
        val base = SpriteManager.robotSide ?: return null
        val bmp = if (p.facingRight) base else SpriteManager.getFlipped(base)
        val w = bmp.width * DRAW_H / bmp.height
        val bob = if (abs(p.velocityX) > 1f) abs(sin(anim * 9f)) * 7f else 0f
        val punch = if (p.isAttacking) (if (p.facingRight) 1f else -1f) * 16f else 0f
        val cx = p.x + p.width / 2f + punch
        val bottom = p.y + p.jumpZ + 8f - bob
        dst.set(cx - w / 2f, bottom - DRAW_H, cx + w / 2f, bottom)
        return bmp
    }

    // ===== 부품으로 그리기: 팔·다리가 관절을 축으로 움직인다 (대표님 2026-10-10 「팔·다리가 움직여」) =====
    /** 앞 손목 자리 (월드 좌표) — 주먹이 여기서 날아간다 */
    private var wristX = 0f
    private var wristY = 0f
    private val partDst = RectF()
    private val backPaint = Paint().apply {
        isFilterBitmap = true
        colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setScale(0.78f, 0.78f, 0.82f, 1f) })
    }
    private val partPaint = Paint().apply { isFilterBitmap = true }

    /** 부품 하나를 관절(px,py — 월드)에 맞춰 angle(도, 시계 방향 +, 아래로 늘어진 게 0) 만큼 돌려 그린다 */
    private fun drawLimb(canvas: Canvas, bmp: android.graphics.Bitmap, h: Float, pivFx: Float, pivFy: Float, px: Float, py: Float, angle: Float, paint: Paint) {
        val w = bmp.width * h / bmp.height
        canvas.save()
        canvas.rotate(angle, px, py)
        partDst.set(px - w * pivFx, py - h * pivFy, px - w * pivFx + w, py - h * pivFy + h)
        canvas.drawBitmap(bmp, null, partDst, paint)
        canvas.restore()
    }

    /** 부품이 있으면 조립해 그리고 true. 오른쪽을 보는 모습으로 그리고 왼쪽이면 통째로 뒤집는다 */
    private fun drawParts(canvas: Canvas, p: Player): Boolean {
        val body = SpriteManager.robotBody ?: return false
        val arm = SpriteManager.robotArm ?: return false
        val leg = SpriteManager.robotLeg ?: return false
        val fist = SpriteManager.robotFist
        val bottom = dst.bottom; val top = dst.top
        val cx = dst.centerX()
        val bw = body.width * BODY_H / body.height
        val bl = cx - bw / 2f
        fun bx(f: Float) = bl + bw * f
        fun by(f: Float) = top + BODY_H * f
        val moving = abs(p.velocityX) > 1f
        val swing = if (moving) sin(anim * 9f) else 0f
        val legBase = if (thrusting) 18f else 0f
        val atk = p.isAttacking
        canvas.save()
        if (!p.facingRight) canvas.scale(-1f, 1f, cx, 0f)
        // 뒤 다리 · 뒤 팔 (조금 어둡게) → 몸통 → 앞 다리 → 앞 팔 · 주먹
        drawLimb(canvas, leg, LEG_H, LEG_PX, LEG_PY, bx(HIP_BACK_X), by(HIP_Y), legBase + swing * 18f, backPaint)
        // 필살기: 두 팔이 어깨를 축으로 빙글빙글 (한 바퀴 0.25초)
        val spin = if (spinLeft > 0f) anim * 1440f else Float.NaN
        drawLimb(canvas, arm, ARM_H, ARM_PX, ARM_PY, bx(SHOULDER_BACK_X), by(SHOULDER_BACK_Y), if (spin.isNaN()) 12f - swing * 14f else spin + 180f, backPaint)
        // 날개를 켜면 등 뒤에 쇠 날개가 펼쳐지고 살짝 퍼덕인다
        if (wings) SpriteManager.humanSkillIcons["robot_wing"]?.let { wb ->
            val wh = BODY_H * 0.9f; val ww = wb.width * wh / wb.height
            val flap = sin(anim * 10f) * 4f
            partDst.set(bx(0.2f) - ww / 2f, by(0.05f) - wh * 0.35f + flap, bx(0.2f) + ww / 2f, by(0.05f) + wh * 0.65f + flap)
            canvas.drawBitmap(wb, null, partDst, partPaint)
        }
        partDst.set(bl, top, bl + bw, top + BODY_H)
        canvas.drawBitmap(body, null, partDst, partPaint)
        drawLimb(canvas, leg, LEG_H, LEG_PX, LEG_PY, bx(HIP_FRONT_X), by(HIP_Y), legBase - swing * 18f, partPaint)
        // 앞 팔: 평소엔 앞으로 내밀고(-70°) 걸으면 살짝 흔들리고, 칠 때는 곧게 내지른다(-95°)
        val armAngle = if (!spin.isNaN()) spin else if (atk) -95f else -70f + swing * 8f
        val sx = bx(SHOULDER_FRONT_X); val sy = by(SHOULDER_FRONT_Y)
        drawLimb(canvas, arm, ARM_H, ARM_PX, ARM_PY, sx, sy, armAngle, partPaint)
        // 손목 = 어깨에서 팔 길이만큼, 팔 방향으로
        val len = ARM_H * (ARM_WRIST_Y - ARM_PY)
        val rad = Math.toRadians(armAngle.toDouble())
        val wx = sx - (len * kotlin.math.sin(rad)).toFloat()
        val wy = sy + (len * kotlin.math.cos(rad)).toFloat()
        if (fist != null && fistAway <= 0f) drawLimb(canvas, fist, FIST_H, FIST_PX, FIST_PY, wx, wy, armAngle + 90f, partPaint)
        canvas.restore()
        // 뒤집었으면 손목 x 도 거울 자리
        wristX = if (p.facingRight) wx else 2f * cx - wx
        wristY = wy
        return true
    }

    private val flamePath = android.graphics.Path()
    private val flameOuter = Paint().apply { color = Color.rgb(255, 130, 30); isAntiAlias = true }
    private val flameInner = Paint().apply { color = Color.rgb(255, 236, 110); isAntiAlias = true }
    private val bar = RectF()
    private val barBack = Paint().apply { color = Color.argb(200, 40, 36, 40); isAntiAlias = true }
    private val barFill = Paint().apply { color = Color.rgb(90, 190, 250); isAntiAlias = true }
    private val hpText = Paint().apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(4f, 0f, 2f, Color.argb(160, 0, 0, 0))
    }

    companion object {
        /** 로봇 그림 높이 · 인간을 조종석 높이로 들어 올림 · 그림 위에서부터 조종석 테두리까지 (그림에 맞춰 Claude 판단) */
        const val DRAW_H = 230f
        const val SEAT_LIFT = 76f
        const val RIM_LINE = 0.20f
        /** 부품 크기·관절 자리 (robot_p_* 그림에서 잰 비율 — 몸통 442×320, 팔 85×240, 다리 142×280, 주먹 247×160) */
        const val BODY_H = 150f
        const val LEG_H = 112f
        const val ARM_H = 92f
        const val FIST_H = 56f
        const val SHOULDER_BACK_X = 42f / 442f; const val SHOULDER_BACK_Y = 157f / 320f
        const val SHOULDER_FRONT_X = 418f / 442f; const val SHOULDER_FRONT_Y = 168f / 320f
        const val HIP_BACK_X = 127f / 442f; const val HIP_FRONT_X = 313f / 442f; const val HIP_Y = 277f / 320f
        const val ARM_PX = 42f / 85f; const val ARM_PY = 42f / 240f; const val ARM_WRIST_Y = 232f / 240f
        const val LEG_PX = 40f / 142f; const val LEG_PY = 30f / 280f
        const val FIST_PX = 20f / 247f; const val FIST_PY = 75f / 160f
        /** 주먹 발사: 빠르기 · 다시 생기는 시간 · 그림에서 주먹 자리(가로 비율) */
        const val FIST_SPEED = 950f
        const val FIST_RELOAD = 0.8f
        const val FIST_L = 0.76f
        const val FIST_R = 1.0f
        /** 날기: 위로 미는 힘 · 가장 빠른 상승 · 가장 높이 (Claude 판단) */
        const val THRUST = 2600f
        const val MAX_RISE_SPEED = 520f
        const val MAX_FLY_HEIGHT = 480f
        /** 날개를 켜면 떠 있는 높이 · 필살기 팔 회전 시간·범위·공격력 · 로켓 펀치 공격력 (대표님: 로켓 펀치 42, 필살기 99) */
        const val HOVER_HEIGHT = 300f
        const val SPIN_TIME = 1.0f
        const val SPIN_RANGE = 260f
        const val SPIN_POWER = 99
        const val ROCKET_POWER = 42
    }
}
