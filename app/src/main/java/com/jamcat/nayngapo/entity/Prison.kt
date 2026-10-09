package com.jamcat.nayngapo.entity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.jamcat.nayngapo.engine.SpriteManager

/**
 * 감옥 (기획1: "고양이를 구해야지 아군이 된다", 기획5의 구출 표).
 * 인간 감옥을 부수면 구조 점수, 냥이 감옥을 부수면 그 냥이가 아군으로 합류한다.
 */
class Prison(
    val x: Float,
    val y: Float,
    val kind: Kind = Kind.HUMAN,
    /** 이 감옥에 갇힌 냥이의 종류를 못박는다 (null이면 구할 때 무작위) */
    val catKind: AllyCat.Kind? = null
) {
    /** 치즈냥이가 갇혀 있을 때의 털 무늬 — 감옥마다 정해 두어 구출 뒤 팀에도 같은 무늬로 간다 */
    val catCoat: com.jamcat.nayngapo.engine.CatCoat = com.jamcat.nayngapo.engine.CatCoat.random()

    /** 새끼 고양이가 갇혀 있다 — 구하면 새끼 고양이가 우리 팀이 된다 (대표님 2026-09-27). 무작위 냥이 감옥의 25% (Claude 판단).
     *  새끼는 **엄마(암컷) 냥이와 같이** 갇혀 있다 — 구하면 둘 다 팀에 들어온다 (대표님 2026-10-09) */
    val holdsKitten: Boolean = kind == Kind.CAT && catKind == null && Math.random() < 0.25

    enum class Kind { HUMAN, CAT }

    val width = 100f
    val height = 140f
    var isOpen = false
    var rescueCount = if (kind == Kind.HUMAN) 3 else 1

    private val hitBoxRect = RectF()
    val hitBox: RectF get() = hitBoxRect.apply { set(x, y - height, x + width, y) }

    private val cagePaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val barPaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 3f
    }
    private val starPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val humanPaint = Paint().apply {
        color = Color.rgb(255, 200, 150)
    }
    private val facePaint = Paint().apply {
        color = Color.rgb(40, 34, 30); style = Paint.Style.STROKE
        strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val tearPaint = Paint().apply { color = Color.rgb(110, 175, 240); isAntiAlias = true }
    private val sadArc = RectF()
    private val bitmapPaint = Paint().apply { isFilterBitmap = true }
    private val openPaint = Paint().apply {
        color = Color.rgb(255, 215, 0)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val srcRect = android.graphics.Rect()
    private val dstRect = RectF()

    fun open() {
        isOpen = true
    }

    fun draw(canvas: Canvas) {
        val top = y - height
        if (isOpen) {
            canvas.drawRect(x, top, x + width, y, openPaint)
            canvas.drawText("★", x + width / 2, top - 10f, starPaint)
            return
        }

        // 갇힌 쪽(울상)을 먼저 그리고 창살을 위에 덮는다
        if (kind == Kind.CAT) {
            val bmp = when (catKind) {
                AllyCat.Kind.FLYING -> SpriteManager.flyingCatAlly
                AllyCat.Kind.GREEN -> SpriteManager.greenCatAlly
                AllyCat.Kind.BLUE -> SpriteManager.blueCatAlly
                AllyCat.Kind.GUN -> SpriteManager.gunCatAlly
                AllyCat.Kind.BOAT -> catCoat.boatAlly
                AllyCat.Kind.NORMAL -> catCoat.ally
                else -> catKind?.let { AllyCat.extraSprite(it) } ?: catCoat.ally   // 모든 냥이가 갇혀 있을 수 있다 (2026-10-10)
            }
            srcRect.set(0, 0, bmp.width, bmp.height)
            if (holdsKitten) {
                // 엄마 냥이(뒤쪽, 조금 작게) + 그 앞에 새끼(작게)
                val ms = (width - 30f) / bmp.width * 0.85f
                dstRect.set(x + 4f, y - 12f - bmp.height * ms, x + 4f + bmp.width * ms, y - 12f)
                canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
                val ks = (width - 24f) / bmp.width * 0.5f
                dstRect.set(x + width - 8f - bmp.width * ks, y - 8f - bmp.height * ks, x + width - 8f, y - 8f)
                canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
            } else {
                val scale = (width - 24f) / bmp.width
                val bw = bmp.width * scale
                val bh = bmp.height * scale
                dstRect.set(x + width / 2f - bw / 2f, y - 12f - bh, x + width / 2f + bw / 2f, y - 12f)
                canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
            }
            // 훌쩍이는 눈물 (얼굴은 오른쪽 40% 언저리)
            val fx = dstRect.left + dstRect.width() * 0.72f
            val fy = dstRect.top + dstRect.height() * 0.45f
            canvas.drawCircle(fx, fy, 3.5f, tearPaint)
            canvas.drawCircle(fx + 4f, fy + 9f, 2.5f, tearPaint)
        } else {
            val cx = x + width / 2f
            val cy = top + height / 2f
            canvas.drawCircle(cx, cy - 15f, 9f, humanPaint)
            canvas.drawRect(cx - 6f, cy - 5f, cx + 6f, cy + 15f, humanPaint)
            // 울상: 처진 눈썹 + 뒤집힌 입 + 눈물
            canvas.drawLine(cx - 5f, cy - 19f, cx - 2f, cy - 17f, facePaint)
            canvas.drawLine(cx + 5f, cy - 19f, cx + 2f, cy - 17f, facePaint)
            sadArc.set(cx - 4f, cy - 13f, cx + 4f, cy - 7f)
            canvas.drawArc(sadArc, 200f, 140f, false, facePaint)
            canvas.drawCircle(cx - 6f, cy - 12f, 2.5f, tearPaint)
            canvas.drawCircle(cx - 8f, cy - 6f, 2f, tearPaint)
        }

        canvas.drawRect(x, top, x + width, y, cagePaint)
        val barSpacing = width / 5f
        for (i in 1..4) {
            canvas.drawLine(x + i * barSpacing, top, x + i * barSpacing, y, barPaint)
        }
    }
}
