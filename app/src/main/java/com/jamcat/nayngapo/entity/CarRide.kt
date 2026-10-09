package com.jamcat.nayngapo.entity

import android.graphics.Canvas
import android.graphics.RectF
import com.jamcat.nayngapo.engine.CarCatalog
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.engine.Terrain

/**
 * 인간이 타는 자동차 (상점 마을·땅 스테이지가 같이 쓴다).
 * **자동차를 누르면 내리고 탈 수 있다** (대표님 2026-10-09): 타고 있을 때 차를 누르면 그 자리에 세워 두고 내려 걷는다.
 * 세워 둔 차 가까이(200 안) 가서 차를 누르면 다시 탄다. 멀면 '가까이 가서 누르세요'. 점프 중에는 못 내린다 (거리·점프 중 금지는 Claude 판단).
 */
class CarRide(val car: CarCatalog.Car) {

    var riding = true
        private set
    private var parkedX = 0f
    /** 세워 둔 차의 바닥 높이 (그리는 순서 정하기용) */
    val parkedY: Float get() = Terrain.groundY(parkedX)
    private var parkedRight = true
    /** 마지막으로 그린 자리 (월드 좌표) — 누른 곳이 차인지 가린다 */
    private val dst = RectF()
    private val hit = RectF()

    enum class Tap { NONE, GOT_OFF, GOT_ON, TOO_FAR }

    fun mount(p: Player) {
        riding = true
        p.carSpeedMul = car.speedMul
        p.rideLift = RIDE_LIFT
    }

    private fun dismount(p: Player) {
        riding = false
        parkedX = p.x + p.width / 2f
        parkedRight = p.facingRight
        p.carSpeedMul = 1f
        p.rideLift = 0f
    }

    /**
     * 인간은 차에 **앉아 있다** (대표님 2026-10-09): 앉은 그림이 따로 없어서, 인간을 차 속으로 내려 앉히고
     * 차 문 줄 아래(다리)는 잘라 그린다 — 문 위로 윗몸만 보인다. 화면이 인간을 그리기 전에 부른다(끝나면 restore).
     */
    fun beginSeat(canvas: Canvas, p: Player) {
        canvas.save()
        if (riding) canvas.clipRect(-1e6f, -1e6f, 1e6f, p.y + p.jumpZ + 14f - DRAW_H * DOOR_LINE)
    }

    /** 탄 차: 바퀴가 땅에 닿게, 바라보는 쪽으로 — 인간 앞에 그린다 */
    fun drawRiding(canvas: Canvas, p: Player) {
        if (!riding) return
        place(p.x + p.width / 2f, p.y + p.jumpZ, p.facingRight)?.let { canvas.drawBitmap(it, null, dst, null) }
    }

    /** 세워 둔 차 (인간보다 뒤에 그린다) */
    fun drawParked(canvas: Canvas) {
        if (riding) return
        place(parkedX, Terrain.groundY(parkedX), parkedRight)?.let { canvas.drawBitmap(it, null, dst, null) }
    }

    private fun place(cx: Float, feetY: Float, right: Boolean): android.graphics.Bitmap? {
        val base = SpriteManager.cars[car.id] ?: return null
        val bmp = if (right) base else SpriteManager.getFlipped(base)
        val cw = bmp.width * DRAW_H / bmp.height
        val bottom = feetY + 14f
        dst.set(cx - cw / 2f, bottom - DRAW_H, cx + cw / 2f, bottom)
        return bmp
    }

    /** 누른 곳(월드 좌표)이 차면 내리거나 탄다 */
    fun tap(wx: Float, wy: Float, p: Player): Tap {
        hit.set(dst.left - 20f, dst.top - 30f, dst.right + 20f, dst.bottom + 20f)
        if (dst.isEmpty || !hit.contains(wx, wy)) return Tap.NONE
        if (riding) {
            if (p.isJumping) return Tap.NONE
            dismount(p)
            return Tap.GOT_OFF
        }
        if (kotlin.math.abs(p.x + p.width / 2f - parkedX) > NEAR) return Tap.TOO_FAR
        p.x = parkedX - p.width / 2f
        p.facingRight = parkedRight
        mount(p)
        return Tap.GOT_ON
    }

    companion object {
        /** 차 그림 높이 · 인간이 앉는 만큼 들어 올림 · 다시 탈 수 있는 거리 */
        const val DRAW_H = 120f
        /** 앉으면 인간을 이만큼 내린다(음수) — 엉덩이가 차 자리에 오게 */
        const val RIDE_LIFT = -40f
        /** 차 그림 위에서부터 문 줄까지 (높이 비율) — 이 아래 인간 다리는 안 그린다 */
        const val DOOR_LINE = 0.55f
        const val NEAR = 200f
    }
}
