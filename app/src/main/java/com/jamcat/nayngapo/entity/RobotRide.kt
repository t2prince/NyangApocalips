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

    fun update(deltaTime: Float) { anim += deltaTime }

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
        canvas.drawBitmap(bmp, null, dst, null)
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
        const val SEAT_LIFT = 85f
        const val RIM_LINE = 0.16f
    }
}
