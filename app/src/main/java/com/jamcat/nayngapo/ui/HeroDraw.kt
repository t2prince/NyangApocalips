package com.jamcat.nayngapo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.jamcat.nayngapo.engine.Heroes

/*
 * 졸라맨 시트는 '검은 선 + 흰 얼굴'로 그려져 있다.
 * 그래서 SRC_IN(전체가 한 색으로 뭉개짐)도, MULTIPLY(선은 그대로고 흰 얼굴만 물듦)도 쓸 수 없다.
 * LightingColorFilter(곱셈, 덧셈)로 '검정 → 캐릭터 색, 흰색 → 그대로 흰색'이 되게 맞춘다.
 *   결과 = 원래색 * (255 - 색) / 255 + 색
 */
private val tintPaints = HashMap<Int, Paint>()

/** 선 색만 바꾸는 페인트 (색마다 한 번 만들어 두고 다시 쓴다) */
fun heroTintPaint(tint: Int): Paint = tintPaints.getOrPut(tint) {
    val r = Color.red(tint)
    val g = Color.green(tint)
    val b = Color.blue(tint)
    Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
        colorFilter = LightingColorFilter(
            Color.rgb(255 - r, 255 - g, 255 - b),   // 곱셈: 흰색은 흰색으로 남는다
            Color.rgb(r, g, b)                       // 덧셈: 검은 선이 이 색이 된다
        )
    }
}

// 매 프레임 새로 만들지 않도록 재사용 (그리기는 게임 스레드 한 곳에서만 일어난다)
private val accPath = Path()
private val accRect = RectF()
private val accFill = Paint().apply { isAntiAlias = true }
private val accInk = Paint().apply {
    color = Color.rgb(30, 28, 26); style = Paint.Style.STROKE
    strokeWidth = 2f; isAntiAlias = true
}

/**
 * 인간 하나를 색과 머리 장식까지 갖춰 그린다.
 * 시트는 오른쪽을 보고 있어서, 왼쪽을 볼 때는 이미 뒤집힌 그림이 들어온다 —
 * 장식 위치만 좌우로 바꿔 주면 된다.
 *
 * @param dst 그림이 놓일 자리
 * @param facingRight 바라보는 방향
 */
fun drawHero(
    canvas: Canvas,
    bmp: Bitmap,
    dst: RectF,
    hero: Heroes.Hero,
    facingRight: Boolean,
    t: Float = 0f
) {
    canvas.drawBitmap(bmp, null, dst, heroTintPaint(hero.tint))
    if (hero.accessory == Heroes.Accessory.GLOVES) {
        drawGloves(canvas, bmp, dst, facingRight)
        return
    }
    if (hero.carriesFirework) drawFirework(canvas, dst, facingRight)
    if (hero.accessory == Heroes.Accessory.NONE) return

    // 머리: 그림 가로 43%(오른쪽을 볼 때)·세로 24% 자리의 동그라미
    val w = dst.width()
    val h = dst.height()
    val hx = dst.left + w * (if (facingRight) 0.43f else 0.57f)
    val hy = dst.top + h * 0.24f
    val hr = w * 0.17f
    val dir = if (facingRight) 1f else -1f

    accFill.color = hero.tint

    when (hero.accessory) {
        Heroes.Accessory.CAP -> {
            // 머리 위 반달 + 앞으로 뻗은 챙
            accRect.set(hx - hr, hy - hr, hx + hr, hy + hr)
            canvas.drawArc(accRect, 180f, 180f, true, accFill)
            canvas.drawArc(accRect, 180f, 180f, true, accInk)
            accRect.set(
                if (dir > 0) hx else hx - hr * 1.45f, hy - hr * 0.22f,
                if (dir > 0) hx + hr * 1.45f else hx, hy + hr * 0.14f
            )
            canvas.drawRoundRect(accRect, hr * 0.16f, hr * 0.16f, accFill)
            canvas.drawRoundRect(accRect, hr * 0.16f, hr * 0.16f, accInk)
        }

        Heroes.Accessory.HEADBAND -> {
            // 머리 위쪽 1/3을 가로지르는 띠 + 뒤로 흘러내리는 끈 두 줄
            accRect.set(hx - hr * 1.02f, hy - hr * 0.62f, hx + hr * 1.02f, hy - hr * 0.14f)
            canvas.drawRoundRect(accRect, hr * 0.12f, hr * 0.12f, accFill)
            canvas.drawRoundRect(accRect, hr * 0.12f, hr * 0.12f, accInk)
            accInk.strokeWidth = 3f
            accInk.color = hero.tint
            val bx = hx - dir * hr           // 매듭은 뒤통수 쪽
            canvas.drawLine(bx, hy - hr * 0.38f, bx - dir * hr * 0.95f, hy + hr * 0.10f, accInk)
            canvas.drawLine(bx, hy - hr * 0.30f, bx - dir * hr * 0.80f, hy + hr * 0.45f, accInk)
            accInk.color = Color.rgb(30, 28, 26)
            accInk.strokeWidth = 2f
        }

        Heroes.Accessory.GLASSES -> {
            // 얼굴 앞쪽 절반에 동그란 안경 두 개 + 콧대
            val lr = hr * 0.40f
            val near = hx + dir * hr * 0.52f
            val far = hx + dir * hr * 0.02f
            canvas.drawCircle(near, hy, lr, accInk)
            canvas.drawCircle(far, hy, lr, accInk)
            accInk.strokeWidth = 3f
            accInk.color = hero.tint
            canvas.drawCircle(near, hy, lr, accInk)
            canvas.drawCircle(far, hy, lr, accInk)
            canvas.drawLine(far + dir * lr, hy, near - dir * lr, hy, accInk)
            accInk.color = Color.rgb(30, 28, 26)
            accInk.strokeWidth = 2f
        }

        Heroes.Accessory.NINJA_HOOD -> {
            // 머리 전체를 초록 두건으로 감싸고 눈 부분만 흰 띠로 드러낸다 + 뒤로 날리는 매듭 끈
            accFill.color = hero.tint
            canvas.drawCircle(hx, hy, hr * 1.05f, accFill)
            canvas.drawCircle(hx, hy, hr * 1.05f, accInk)
            accFill.color = Color.rgb(250, 248, 240)
            // 눈 띠는 얼굴 앞쪽 절반에 둔다
            val bandL = if (dir > 0) hx - hr * 0.15f else hx - hr * 0.98f
            val bandR = if (dir > 0) hx + hr * 0.98f else hx + hr * 0.15f
            accRect.set(bandL, hy - hr * 0.32f, bandR, hy + hr * 0.10f)
            canvas.drawRoundRect(accRect, hr * 0.18f, hr * 0.18f, accFill)
            canvas.drawRoundRect(accRect, hr * 0.18f, hr * 0.18f, accInk)
            // 날카로운 눈 두 개
            accFill.color = Color.rgb(30, 28, 26)
            val ey = hy - hr * 0.11f
            canvas.drawCircle(hx + dir * hr * 0.30f, ey, hr * 0.10f, accFill)
            canvas.drawCircle(hx + dir * hr * 0.70f, ey, hr * 0.10f, accFill)
            // 뒤통수 매듭 + 바람에 날리는 끈 두 가닥
            val kx = hx - dir * hr * 1.0f
            val ky = hy - hr * 0.10f
            accFill.color = hero.tint
            canvas.drawCircle(kx, ky, hr * 0.22f, accFill)
            canvas.drawCircle(kx, ky, hr * 0.22f, accInk)
            val sway = kotlin.math.sin(t * 9f) * hr * 0.25f
            accInk.strokeWidth = 4f
            accInk.color = hero.tint
            canvas.drawLine(kx, ky, kx - dir * hr * 1.1f, ky - hr * 0.35f + sway, accInk)
            canvas.drawLine(kx, ky, kx - dir * hr * 0.95f, ky + hr * 0.30f - sway, accInk)
            accInk.color = Color.rgb(30, 28, 26)
            accInk.strokeWidth = 2f
        }

        Heroes.Accessory.GLOVES, Heroes.Accessory.NONE -> Unit
    }
    accPath.reset()
}

/** 뒷손에 쥔 폭죽 묶음: 빨간 대 셋 + 노란 띠 + 도화선 불씨 */
private fun drawFirework(canvas: Canvas, dst: RectF, facingRight: Boolean) {
    val w = dst.width()
    val h = dst.height()
    val cx = dst.left + w * (if (facingRight) 0.30f else 0.70f)
    val cy = dst.top + h * 0.50f
    val tubeH = w * 0.16f
    val tubeW = tubeH * 0.30f

    // 빨간 대 세 개를 조금씩 어긋나게
    accFill.color = Color.rgb(214, 48, 40)
    for (k in -1..1) {
        val tx = cx + k * tubeW * 1.15f
        val ty = cy + kotlin.math.abs(k) * tubeH * 0.10f
        accRect.set(tx - tubeW / 2f, ty - tubeH / 2f, tx + tubeW / 2f, ty + tubeH / 2f)
        canvas.drawRoundRect(accRect, tubeW * 0.35f, tubeW * 0.35f, accFill)
        canvas.drawRoundRect(accRect, tubeW * 0.35f, tubeW * 0.35f, accInk)
    }

    // 노란 묶음 띠
    accFill.color = Color.rgb(255, 214, 74)
    accRect.set(cx - tubeW * 1.9f, cy - tubeH * 0.10f, cx + tubeW * 1.9f, cy + tubeH * 0.18f)
    canvas.drawRoundRect(accRect, tubeH * 0.06f, tubeH * 0.06f, accFill)
    canvas.drawRoundRect(accRect, tubeH * 0.06f, tubeH * 0.06f, accInk)

    // 도화선과 불씨
    val fx = cx
    val fy = cy - tubeH * 0.55f
    canvas.drawLine(fx, fy, fx, fy - tubeH * 0.32f, accInk)
    accFill.color = Color.rgb(255, 150, 40)
    canvas.drawCircle(fx, fy - tubeH * 0.40f, tubeW * 0.30f, accFill)
}


// ===================== 권투 장갑 =====================

/** 스트립 프레임(원본·뒤집힌 그림 모두) → 손 위치 표. 처음 쓸 때 한 번 만든다. */
private val handLookup: Map<Bitmap, FloatArray> by lazy {
    val m = HashMap<Bitmap, FloatArray>()
    fun add(frames: List<Bitmap>, table: Array<FloatArray>) {
        for (i in frames.indices) {
            val t = table[i.coerceAtMost(table.size - 1)]
            m[frames[i]] = t
            m[com.jamcat.nayngapo.engine.SpriteManager.getFlipped(frames[i])] = t
        }
    }
    val sm = com.jamcat.nayngapo.engine.SpriteManager
    add(sm.playerIdle, HandTable.IDLE)
    add(sm.playerWalk, HandTable.WALK)
    add(sm.playerJump, HandTable.JUMP)
    add(sm.playerPunch, HandTable.PUNCH)
    add(sm.playerCross, HandTable.CROSS)
    add(sm.playerKick, HandTable.KICK)
    add(sm.playerJumpKick, HandTable.JUMPKICK)
    add(sm.playerStomp, HandTable.STOMP)
    m
}

/** 앞손(바라보는 쪽 손)의 화면 자리 — 총 같은 걸 쥐여 줄 때 (프레임마다 미리 찾아 둔 손 자리) */
fun frontHand(bmp: Bitmap, dst: RectF, facingRight: Boolean, out: android.graphics.PointF): Boolean {
    val t = handLookup[bmp] ?: return false
    var best = -1f
    for (k in 0..1) {
        val fx = t[k * 2]
        if (fx > best) { best = fx; out.y = dst.top + dst.height() * t[k * 2 + 1] }
    }
    out.x = dst.left + dst.width() * (if (facingRight) best else 1f - best)
    return true
}

private val gloveFill = Paint().apply { color = Color.rgb(226, 40, 40); isAntiAlias = true }
private val gloveShine = Paint().apply { color = Color.argb(110, 255, 255, 255); isAntiAlias = true }
private val gloveCuff = Paint().apply { color = Color.rgb(250, 246, 236); isAntiAlias = true }
private val gloveRect = RectF()

/** 두 손에 빨간 권투 장갑 (프레임마다 미리 찾아 둔 손 자리에 붙는다) */
private fun drawGloves(canvas: Canvas, bmp: Bitmap, dst: RectF, facingRight: Boolean) {
    val t = handLookup[bmp] ?: return
    val r = dst.height() * 0.055f
    val dir = if (facingRight) 1f else -1f
    for (k in 0..1) {
        val fx = t[k * 2]
        val fy = t[k * 2 + 1]
        if (k == 1 && fx == t[0] && fy == t[1]) break          // 한 손이 가려진 프레임
        val x = dst.left + dst.width() * (if (facingRight) fx else 1f - fx)
        val y = dst.top + dst.height() * fy
        // 손목 쪽 흰 띠: 팔이 이어지는 쪽(몸 가운데 쪽)에 붙인다
        val toBody = if (x < dst.centerX()) 1f else -1f
        val cuffX = x + toBody * r * 1.15f
        gloveRect.set(cuffX - r * 0.35f, y - r * 0.55f, cuffX + r * 0.35f, y + r * 0.55f)
        canvas.drawRoundRect(gloveRect, r * 0.2f, r * 0.2f, gloveCuff)
        canvas.drawRoundRect(gloveRect, r * 0.2f, r * 0.2f, accInk)
        // 장갑 몸통 + 엄지
        gloveRect.set(x - r, y - r * 0.9f, x + r, y + r * 0.9f)
        canvas.drawOval(gloveRect, gloveFill)
        canvas.drawOval(gloveRect, accInk)
        val tx = x - dir * r * 0.25f
        val ty = y - r * 0.85f
        canvas.drawCircle(tx, ty, r * 0.38f, gloveFill)
        canvas.drawCircle(tx, ty, r * 0.38f, accInk)
        canvas.drawCircle(x + dir * r * 0.3f, y - r * 0.35f, r * 0.28f, gloveShine)
    }
}
