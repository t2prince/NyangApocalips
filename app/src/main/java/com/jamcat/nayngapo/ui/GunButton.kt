package com.jamcat.nayngapo.ui

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.jamcat.nayngapo.entity.Player

/**
 * 화면 위 '총 넣기 / 총 꺼내기' 버튼 (대표님 2026-10-10 「총 집어넣기도 만들어 줘」).
 * 총을 넣으면 손에서 사라지고 공격 버튼이 다시 주먹·발차기, 꺼내면 총알. 자동차 타기 버튼 오른쪽 (자리·모양은 Claude 판단).
 */
class GunButton {
    private val rect = RectF()
    private val fill = Paint().apply { color = Color.argb(200, 255, 214, 236); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(40, 34, 30); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
    private val text = Paint().apply {
        color = Color.rgb(40, 34, 30); textSize = 36f; textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD; isAntiAlias = true
    }

    private val small = Paint().apply { textSize = 26f; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; isAntiAlias = true }

    fun draw(canvas: Canvas, p: Player) {
        if (!p.hasGun) { rect.setEmpty(); return }
        rect.set(LEFT, CY - 44f, LEFT + W, CY + 44f)
        canvas.drawRoundRect(rect, 44f, 44f, fill)
        canvas.drawRoundRect(rect, 44f, 44f, ink)
        canvas.drawTr(if (p.gunOut) "🔫 총 넣기" else "🔫 총 꺼내기", rect.centerX(), rect.centerY() - 2f, text)
        // 남은 총알 (2026-10-24 — 총알이 없으면 안 나간다)
        val ammo = com.jamcat.nayngapo.engine.GameProgress.itemCount("h_ammo")
        small.color = if (ammo > 0) Color.rgb(40, 34, 30) else Color.rgb(200, 40, 40)
        canvas.drawTr("총알 $ammo", rect.centerX(), rect.centerY() + 32f, small)
    }

    /** 눌렀으면 총을 넣거나 꺼내고 true */
    fun tap(px: Float, py: Float, p: Player): Boolean {
        if (rect.isEmpty || !rect.contains(px, py)) return false
        p.gunOut = !p.gunOut
        return true
    }

    companion object {
        private const val LEFT = 590f
        private const val W = 250f
        private const val CY = 118f
    }
}
