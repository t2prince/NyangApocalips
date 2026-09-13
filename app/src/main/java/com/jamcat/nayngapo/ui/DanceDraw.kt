package com.jamcat.nayngapo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sin

/**
 * 연속 동작 그림이 없는 냥이를 '춤추는 것처럼' 보이게 그린다.
 * 발끝을 축으로 좌우로 기울이고 살짝 눌렀다 늘린다.
 * 캣타워(고양이 관리)와 도감이 같은 춤을 쓰도록 여기 한 군데에 둔다.
 *
 * @param dst 그림이 놓일 자리 (아래쪽 변이 발끝)
 * @param t 흐른 시간(초)
 * @param phase 카드마다 다른 값을 주면 한꺼번에 같은 동작을 하지 않는다
 */
fun drawDancing(
    canvas: Canvas,
    bmp: Bitmap,
    dst: RectF,
    t: Float,
    paint: Paint?,
    phase: Float = 0f
) {
    val a = t * 6f + phase
    val tilt = sin(a) * 8f
    val squash = 1f + sin(a * 2f) * 0.06f
    canvas.save()
    canvas.rotate(tilt, dst.centerX(), dst.bottom)
    canvas.scale(1f, squash, dst.centerX(), dst.bottom)
    canvas.drawBitmap(bmp, null, dst, paint)
    canvas.restore()
}
