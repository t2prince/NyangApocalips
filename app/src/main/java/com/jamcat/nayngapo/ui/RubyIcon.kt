package com.jamcat.nayngapo.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

// 매 프레임 Path를 새로 만들지 않도록 재사용한다 (그리기는 게임 스레드 한 곳에서만 일어난다)
private val gemPath = Path()
private val gemFacet = Path()
private val gemBand = Path()

/**
 * 루비 알맹이를 직접 그린다 — 이모지 글꼴에 기대지 않도록 캔버스로 깎은 보석.
 * 로비·고양이 상점·루비 상점이 같은 모양을 쓴다.
 *
 * @param cx 보석 한가운데 x
 * @param cy 보석 한가운데 y
 * @param r 반지름(가로 폭의 절반)
 * @param paint 잠시 빌려 쓰는 페인트 (색·스타일·굵기는 원래대로 돌려놓는다)
 * @param alpha 0~255. 반짝임처럼 서서히 사라질 때 쓴다
 */
fun drawRubyIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint, alpha: Int = 255) {
    val savedColor = paint.color
    val savedStyle = paint.style
    val savedStroke = paint.strokeWidth
    val a = alpha.coerceIn(0, 255)
    paint.style = Paint.Style.FILL
    paint.isAntiAlias = true

    // 위는 넓은 육각 모양, 아래는 뾰족하게 — 흔한 보석 실루엣
    val top = cy - r * 0.85f
    val shoulder = cy - r * 0.30f
    val bottom = cy + r * 0.95f

    gemPath.rewind()
    gemPath.moveTo(cx - r * 0.55f, top)
    gemPath.lineTo(cx + r * 0.55f, top)
    gemPath.lineTo(cx + r, shoulder)
    gemPath.lineTo(cx, bottom)
    gemPath.lineTo(cx - r, shoulder)
    gemPath.close()

    paint.color = Color.argb(a, 232, 66, 96)
    canvas.drawPath(gemPath, paint)

    // 왼쪽 면에 밝은 빛, 위쪽 띠에 연한 면 — 밋밋한 오각형으로 보이지 않게
    gemFacet.rewind()
    gemFacet.moveTo(cx - r * 0.55f, top)
    gemFacet.lineTo(cx, top)
    gemFacet.lineTo(cx, bottom)
    gemFacet.lineTo(cx - r, shoulder)
    gemFacet.close()
    paint.color = Color.argb(a, 255, 128, 152)
    canvas.drawPath(gemFacet, paint)

    gemBand.rewind()
    gemBand.moveTo(cx - r * 0.55f, top)
    gemBand.lineTo(cx + r * 0.55f, top)
    gemBand.lineTo(cx + r * 0.30f, shoulder)
    gemBand.lineTo(cx - r * 0.30f, shoulder)
    gemBand.close()
    paint.color = Color.argb(a, 255, 178, 194)
    canvas.drawPath(gemBand, paint)

    // 검은 사인펜 테두리 (게임 전체 손그림 톤)
    paint.color = Color.argb(a, 30, 28, 26)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = (r * 0.22f).coerceIn(1.6f, 4f)
    canvas.drawPath(gemPath, paint)

    paint.color = savedColor
    paint.style = savedStyle
    paint.strokeWidth = savedStroke
}
