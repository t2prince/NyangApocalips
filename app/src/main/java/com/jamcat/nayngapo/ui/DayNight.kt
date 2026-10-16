package com.jamcat.nayngapo.ui

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.jamcat.nayngapo.engine.GameProgress
import kotlin.math.sin

/**
 * 밤과 낮 (대표님 2026-10-17 「1시간마다 밤과 낮이 바뀌는데, 밤에는 하늘이 어두워지고, 밤이라는 글자가 떠」).
 * 게임을 켜 둔 플레이 시간 1시간마다 낮 ↔ 밤 (GameProgress.dayClock). 밤에는 화면 위쪽(하늘)이 짙게, 아래로 갈수록 덜 어둡게
 * 남색을 덮고 별·달을 띄우고, 위 가운데 아래에 '🌙 밤' 글자. 스테이지·상점 마을에 쓴다 (어둡기·별은 Claude 판단).
 */
object DayNight {
    private val veil = Paint()
    private var shaderH = 0f
    private val star = Paint().apply { color = Color.argb(220, 255, 255, 230); isAntiAlias = true }
    private val moon = Paint().apply { color = Color.rgb(255, 244, 190); isAntiAlias = true }
    private val moonCut = Paint().apply { color = Color.rgb(28, 34, 74); isAntiAlias = true }
    private val labelBg = Paint().apply { color = Color.argb(170, 20, 22, 50); isAntiAlias = true }
    private val label = Paint().apply {
        color = Color.rgb(255, 240, 170); textAlign = Paint.Align.CENTER; textSize = 40f
        typeface = android.graphics.Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val rect = RectF()

    /** 밤이면 화면 위에 덮어 그린다 (HUD·버튼 그리기 전에). t = 반짝임용 시간 */
    fun drawNight(canvas: Canvas, w: Float, h: Float, t: Float, labelY: Float = 150f) {
        if (!GameProgress.isNight) return
        if (shaderH != h) {
            shaderH = h
            veil.shader = android.graphics.LinearGradient(0f, 0f, 0f, h,
                Color.argb(170, 14, 18, 52), Color.argb(90, 14, 18, 52), android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, veil)
        // 별: 위쪽 하늘에 반짝반짝
        for (k in 0 until 40) {
            val sx = ((k * 211) % 1000) / 1000f * w
            val sy = ((k * 137) % 1000) / 1000f * h * 0.42f
            val tw = 0.5f + 0.5f * sin(t * 2f + k)
            star.alpha = (120 + 120 * tw).toInt()
            canvas.drawCircle(sx, sy, 2f + (k % 3), star)
        }
        // 초승달
        val mx = w * 0.82f; val my = h * 0.12f
        canvas.drawCircle(mx, my, 46f, moon)
        canvas.drawCircle(mx + 20f, my - 10f, 40f, moonCut)
        // '밤' 글자
        val text = "🌙 밤"
        val tw = label.measureTr(text) + 40f
        rect.set(w / 2f - tw / 2f, labelY - 40f, w / 2f + tw / 2f, labelY + 16f)
        canvas.drawRoundRect(rect, 28f, 28f, labelBg)
        canvas.drawTr(text, w / 2f, labelY + 2f, label)
    }
}
