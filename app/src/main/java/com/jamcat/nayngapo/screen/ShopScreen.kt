package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import kotlin.math.sin

/**
 * 상점 (로비 오른쪽 '상점' 칸, 대표님 2026-09-27):
 * 평평한 초원 위에 천막 상점들이 줄지어 있고, **인간을 한 명 구할 때마다 천막 하나의 문이 열린다**.
 * 구한 인간이 없으면 모든 천막이 닫혀 있다. 구한 수는 GameProgress.humansRescued (감옥을 여는 순간 저장).
 *
 * 열린 천막을 누르면 그 가게에서 물건을 산다. **고양이 상점도 여기 천막 중 하나다** (대표님 2026-09-27) — 1번 천막.
 * 나머지 천막의 물건과 천막 수(TENT_COUNT)는 아직 대표님이 정하지 않았다 — 열린 천막 안은 '준비 중' 팻말만 둔다.
 * (천막 6개·색·고양이 상점을 1번에 둔 것은 Claude 임시 판단)
 */
class ShopScreen(private val gameView: GameView) : Screen {

    private var timer = 0f
    private var noticeText = ""
    private var noticeTimer = 0f

    private val skyTop = Paint()
    private val groundPaint = Paint().apply { color = Color.rgb(126, 196, 92); isAntiAlias = true }
    private val groundDark = Paint().apply { color = Color.rgb(98, 168, 70); isAntiAlias = true }
    private val grassPaint = Paint().apply { color = Color.rgb(78, 146, 56); strokeWidth = 3f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val cloudPaint = Paint().apply { color = Color.argb(235, 255, 255, 255); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 4f; strokeJoin = Paint.Join.ROUND; isAntiAlias = true }
    private val fill = Paint().apply { isAntiAlias = true }
    private val shadowPaint = Paint().apply { color = Color.argb(60, 0, 0, 0); isAntiAlias = true }
    private val titlePaint = Paint().apply {
        color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val infoPaint = Paint().apply { color = Color.rgb(60, 56, 50); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val signPaint = Paint().apply { color = Color.rgb(250, 240, 214); isAntiAlias = true }
    private val signText = Paint().apply { color = Color.rgb(70, 46, 24); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val btnPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val btnText = Paint().apply { color = Color.rgb(60, 44, 12); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val noticeBg = Paint().apply { color = Color.rgb(255, 250, 225); isAntiAlias = true }
    private val noticeTextPaint = Paint().apply { color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }

    private val path = Path()
    private val rect = RectF()
    private val backRect = RectF()
    private val tentRects = List(TENT_COUNT) { RectF() }

    /** 열린 천막 수 = 구한 인간 수 (천막 수까지) */
    private val openCount: Int get() = GameProgress.humansRescued.coerceIn(0, TENT_COUNT)

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        val groundY = h * 0.70f

        // 하늘 + 뭉게구름
        skyTop.shader = LinearGradient(0f, 0f, 0f, groundY, Color.rgb(132, 200, 246), Color.rgb(214, 238, 252), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, groundY, skyTop)
        for (i in 0 until 4) {
            val cx = ((i * 0.29f + timer * 0.006f) % 1.1f) * w * 1.05f - w * 0.05f
            val cy = h * (0.10f + (i % 2) * 0.07f)
            canvas.drawCircle(cx, cy, h * 0.045f, cloudPaint)
            canvas.drawCircle(cx + h * 0.05f, cy - h * 0.02f, h * 0.06f, cloudPaint)
            canvas.drawCircle(cx + h * 0.11f, cy, h * 0.042f, cloudPaint)
        }
        // 평평한 초원 (지평선 곧게) + 풀 포기
        canvas.drawRect(0f, groundY, w, h, groundPaint)
        canvas.drawRect(0f, groundY, w, groundY + h * 0.018f, groundDark)
        var gx = 20f
        var k = 0
        while (gx < w) {
            val gy = groundY + h * (0.06f + ((k * 37) % 23) / 23f * 0.24f)
            canvas.drawLine(gx, gy, gx - 6f, gy - 16f, grassPaint)
            canvas.drawLine(gx, gy, gx + 5f, gy - 18f, grassPaint)
            gx += 70f + (k * 53) % 60
            k++
        }

        // 제목 + 구한 인간 수
        titlePaint.textSize = h * 0.075f
        canvas.drawText("상점", w / 2f, h * 0.10f, titlePaint)
        infoPaint.textSize = h * 0.038f
        val info = if (openCount == 0) "인간을 구하면 가게 문이 열려요!"
            else "구한 인간 ${GameProgress.humansRescued}명 · 열린 가게 $openCount / $TENT_COUNT"
        canvas.drawText(info, w / 2f, h * 0.17f, infoPaint)

        // 천막들: 지평선 위에 한 줄
        val margin = w * 0.05f
        val slot = (w - margin * 2f) / TENT_COUNT
        val tw = slot * 0.84f
        val th = h * 0.40f
        for (i in 0 until TENT_COUNT) {
            val cx = margin + slot * (i + 0.5f)
            drawTent(canvas, i, cx, groundY + h * 0.03f, tw, th, i < openCount, h)
            tentRects[i].set(cx - tw / 2f, groundY + h * 0.03f - th, cx + tw / 2f, groundY + h * 0.03f)
        }

        // 돌아가기
        val bw = w * 0.16f; val bh = h * 0.09f
        backRect.set(w * 0.03f, h - bh - h * 0.04f, w * 0.03f + bw, h - h * 0.04f)
        canvas.drawRoundRect(backRect, 20f, 20f, btnPaint)
        canvas.drawRoundRect(backRect, 20f, 20f, ink)
        btnText.textSize = h * 0.042f
        canvas.drawText("돌아가기", backRect.centerX(), backRect.centerY() + h * 0.015f, btnText)

        // 안내 한 줄
        if (noticeTimer > 0f) {
            noticeTextPaint.textSize = h * 0.042f
            val nw = noticeTextPaint.measureText(noticeText) + w * 0.06f
            rect.set(w / 2f - nw / 2f, h * 0.84f, w / 2f + nw / 2f, h * 0.94f)
            noticeBg.alpha = (255 * noticeTimer.coerceAtMost(0.3f) / 0.3f).toInt()
            canvas.drawRoundRect(rect, 22f, 22f, noticeBg)
            canvas.drawRoundRect(rect, 22f, 22f, ink)
            canvas.drawText(noticeText, w / 2f, rect.centerY() + h * 0.015f, noticeTextPaint)
        }
    }

    /** 천막 하나: 줄무늬 지붕 + 깃발 + 번호 팻말 + 문(닫힘: 여민 천과 끈 매듭 / 열림: 걷어 묶은 천과 안쪽 '준비 중') */
    private fun drawTent(canvas: Canvas, i: Int, cx: Float, baseY: Float, tw: Float, th: Float, open: Boolean, h: Float) {
        val (c1, c2) = TENT_COLORS[i % TENT_COLORS.size]
        val left = cx - tw / 2f; val right = cx + tw / 2f
        val eaveY = baseY - th * 0.52f          // 지붕 처마
        val topY = baseY - th                   // 꼭대기
        ink.strokeWidth = h * 0.006f

        // 그림자
        rect.set(left - tw * 0.02f, baseY - h * 0.012f, right + tw * 0.06f, baseY + h * 0.02f)
        canvas.drawOval(rect, shadowPaint)

        // 몸통 벽 (처마 아래 네모)
        fill.color = c2
        rect.set(left + tw * 0.06f, eaveY, right - tw * 0.06f, baseY)
        canvas.drawRect(rect, fill)
        canvas.drawRect(rect, ink)

        // 문: 벽 가운데 뾰족한 아치
        val dw = tw * 0.34f; val dTop = eaveY + th * 0.06f
        path.reset()
        path.moveTo(cx - dw / 2f, baseY)
        path.lineTo(cx - dw / 2f, dTop + th * 0.10f)
        path.quadTo(cx - dw / 2f, dTop, cx, dTop - th * 0.02f)
        path.quadTo(cx + dw / 2f, dTop, cx + dw / 2f, dTop + th * 0.10f)
        path.lineTo(cx + dw / 2f, baseY)
        path.close()
        if (open) {
            // 안쪽은 어둑하고, 걷어 올린 천이 양옆에 묶여 있다
            fill.color = Color.rgb(64, 46, 40)
            canvas.drawPath(path, fill)
            canvas.drawPath(path, ink)
            fill.color = c1
            for (side in intArrayOf(-1, 1)) {
                val sx = cx + side * dw / 2f
                path.reset()
                path.moveTo(sx, dTop + th * 0.06f)
                path.quadTo(sx - side * dw * 0.05f, dTop + th * 0.20f, sx + side * dw * 0.02f, baseY)
                path.lineTo(sx + side * dw * 0.20f, baseY)
                path.quadTo(sx + side * dw * 0.08f, dTop + th * 0.22f, sx + side * dw * 0.14f, dTop + th * 0.06f)
                path.close()
                canvas.drawPath(path, fill)
                canvas.drawPath(path, ink)
            }
            // 안에 팻말 — 고양이 상점은 '들어오세요', 물건이 안 정해진 가게는 '준비 중'
            val inner = if (TENT_NAMES[i] != null) "들어오세요" else "준비 중"
            signText.textSize = h * 0.026f
            val sw = signText.measureText(inner) + tw * 0.06f
            rect.set(cx - sw / 2f, baseY - th * 0.20f, cx + sw / 2f, baseY - th * 0.10f)
            canvas.drawRoundRect(rect, 8f, 8f, signPaint)
            canvas.drawRoundRect(rect, 8f, 8f, ink)
            canvas.drawText(inner, cx, rect.centerY() + h * 0.009f, signText)
        } else {
            // 여민 천 두 폭 + 가운데 이음 + 끈 매듭
            fill.color = c1
            canvas.drawPath(path, fill)
            canvas.drawPath(path, ink)
            canvas.drawLine(cx, dTop - th * 0.01f, cx, baseY, ink)
            fill.color = Color.rgb(150, 100, 50)
            val ky = dTop + th * 0.16f
            canvas.drawCircle(cx, ky, h * 0.012f, fill)
            canvas.drawCircle(cx, ky, h * 0.012f, ink)
            canvas.drawLine(cx, ky, cx - dw * 0.18f, ky + th * 0.07f, ink)
            canvas.drawLine(cx, ky, cx + dw * 0.18f, ky + th * 0.07f, ink)
        }

        // 지붕: 꼭대기에서 처마로 퍼지는 줄무늬 삼각 + 물결 처마
        val stripes = 6
        for (s in 0 until stripes) {
            val a = left + tw * s / stripes; val b = left + tw * (s + 1) / stripes
            path.reset()
            path.moveTo(cx, topY)
            path.lineTo(a, eaveY)
            path.lineTo(b, eaveY)
            path.close()
            fill.color = if (s % 2 == 0) c1 else Color.WHITE
            canvas.drawPath(path, fill)
        }
        path.reset()
        path.moveTo(cx, topY); path.lineTo(left, eaveY); path.lineTo(right, eaveY); path.close()
        canvas.drawPath(path, ink)
        // 물결 처마
        val scallops = 6
        for (s in 0 until scallops) {
            val a = left + tw * s / scallops
            rect.set(a, eaveY - tw / scallops * 0.35f, a + tw / scallops, eaveY + tw / scallops * 0.45f)
            fill.color = if (s % 2 == 0) c1 else Color.WHITE
            canvas.drawArc(rect, 0f, 180f, false, fill)
            canvas.drawArc(rect, 0f, 180f, false, ink)
        }

        // 깃대 + 나부끼는 깃발
        canvas.drawLine(cx, topY, cx, topY - th * 0.14f, ink)
        val wave = sin((timer * 5f + i).toDouble()).toFloat() * th * 0.012f
        path.reset()
        path.moveTo(cx, topY - th * 0.14f)
        path.lineTo(cx + tw * 0.16f, topY - th * 0.11f + wave)
        path.lineTo(cx, topY - th * 0.08f)
        path.close()
        fill.color = c1
        canvas.drawPath(path, fill)
        canvas.drawPath(path, ink)

        // 번호 팻말 (처마 아래)
        signText.textSize = h * 0.03f
        val label = TENT_NAMES[i] ?: "${i + 1}번 가게"
        val lw = signText.measureText(label) + tw * 0.08f
        rect.set(cx - lw / 2f, eaveY + th * 0.02f - h * 0.045f, cx + lw / 2f, eaveY + th * 0.02f)
        rect.offset(0f, -th * 0.11f)
        canvas.drawRoundRect(rect, 8f, 8f, signPaint)
        canvas.drawRoundRect(rect, 8f, 8f, ink)
        canvas.drawText(label, cx, rect.centerY() + h * 0.011f, signText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        if (backRect.contains(event.x, event.y)) { gameView.showLobby(); return true }
        for (i in 0 until TENT_COUNT) {
            if (!tentRects[i].contains(event.x, event.y)) continue
            if (i < openCount) {
                if (i == CAT_SHOP_TENT) gameView.showCatShop(fromTent = true)
                else showNotice("${i + 1}번 가게는 아직 물건을 준비 중이에요")
            }
            else {
                val need = i + 1 - GameProgress.humansRescued
                showNotice("인간을 ${need}명 더 구하면 ${i + 1}번 가게 문이 열려요")
            }
            return true
        }
        return true
    }

    private fun showNotice(text: String) {
        noticeText = text
        noticeTimer = 2f
    }

    override fun onBack(): Boolean { gameView.showLobby(); return true }

    companion object {
        /** 천막 수 — 대표님이 정할 때까지 임시 6개 (Claude 판단) */
        const val TENT_COUNT = 6
        /** 고양이 상점이 들어 있는 천막 (0 = 1번, 첫 인간을 구하면 열린다) */
        const val CAT_SHOP_TENT = 0
        /** 천막 팻말 — 정해진 가게만 이름, 나머지는 'N번 가게' */
        private val TENT_NAMES: List<String?> = List(TENT_COUNT) { if (it == CAT_SHOP_TENT) "고양이 상점" else null }
        /** 천막마다 줄무늬 색 · 벽 색 */
        private val TENT_COLORS = listOf(
            Color.rgb(226, 72, 64) to Color.rgb(250, 232, 214),
            Color.rgb(58, 128, 214) to Color.rgb(232, 240, 250),
            Color.rgb(246, 176, 40) to Color.rgb(252, 244, 222),
            Color.rgb(84, 170, 88) to Color.rgb(234, 246, 228),
            Color.rgb(170, 90, 200) to Color.rgb(244, 232, 250),
            Color.rgb(240, 120, 160) to Color.rgb(252, 234, 240),
        )
    }
}
