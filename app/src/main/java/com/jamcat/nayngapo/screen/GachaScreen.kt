package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.ui.drawRubyIcon
import kotlin.math.sin

/**
 * 뽑기 가게 — 상점 마을 집 앞의 뽑기 기계 (대표님 2026-10-10 「집 앞에 또 뽑기하는 상점도 만들어」).
 * 루비 10개로 한 번 뽑는다: 기계가 덜컹거리고 손잡이가 돌아가 캡슐이 굴러 나오고, 열리면 상품이 나온다(가방에 들어간다).
 * 값·상품·확률은 Claude 판단 (PRIZES), 기계 그림 GPT(gacha_machine).
 */
class GachaScreen(private val gameView: GameView) : Screen {

    /** id: 물건 id (루비면 null), rubies: 루비 상품 개수, weight: 뽑힐 몫(합 100) */
    private class Prize(val id: String?, val name: String, val rubies: Int, val weight: Int, val rare: Boolean = false)

    private val machine: Bitmap? = gameView.resources.getIdentifier("gacha_machine", "drawable", gameView.context.packageName)
        .takeIf { it != 0 }?.let { BitmapFactory.decodeResource(gameView.resources, it) }

    private var phase = Phase.IDLE
    private enum class Phase { IDLE, SPIN, DROP, OPEN }
    private var t = 0f
    private var prize: Prize? = null
    private var capsuleColor = Color.RED
    private var noticeText = ""
    private var noticeTimer = 0f

    private val pullRect = RectF()
    private val backRect = RectF()
    private val machineRect = RectF()
    private val rect = RectF()
    private val bg = Paint().apply { color = Color.rgb(255, 238, 214) }
    private val card = Paint().apply { color = Color.rgb(255, 252, 244); isAntiAlias = true }
    private val pullPaint = Paint().apply { color = Color.rgb(240, 90, 120); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val fill = Paint().apply { isAntiAlias = true }
    private val glow = Paint().apply { color = Color.argb(140, 255, 236, 120); isAntiAlias = true }
    private val gemPaint = Paint().apply { isAntiAlias = true }
    private val title = Paint().apply { color = Color.rgb(60, 36, 30); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val white = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }

    override fun update(deltaTime: Float) {
        if (noticeTimer > 0f) noticeTimer -= deltaTime
        if (phase == Phase.IDLE) return
        t += deltaTime
        when (phase) {
            Phase.SPIN -> if (t >= SPIN_TIME) { phase = Phase.DROP; t = 0f }
            Phase.DROP -> if (t >= DROP_TIME) { phase = Phase.OPEN; t = 0f; SoundManager.playCatHit() }
            else -> Unit
        }
    }

    private fun pull() {
        if (phase == Phase.SPIN || phase == Phase.DROP) return
        if (!GameProgress.spendRubies(PRICE)) { notice("루비가 모자라요 (한 번에 루비 $PRICE)"); return }
        // 상품을 먼저 정해 가방에 넣는다 (화면을 나가도 안 사라진다)
        var r = (Math.random() * PRIZES.sumOf { it.weight }).toInt()
        val p = PRIZES.first { r -= it.weight; r < 0 }
        if (p.id != null) GameProgress.addItem(p.id) else GameProgress.addRubies(p.rubies)
        prize = p
        capsuleColor = CAPSULE_COLORS[(Math.random() * CAPSULE_COLORS.size).toInt()]
        phase = Phase.SPIN; t = 0f
        SoundManager.playAttack()
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        canvas.drawRect(0f, 0f, w, h, bg)
        title.textSize = h * 0.065f
        canvas.drawText("뽑기 가게", w / 2f, h * 0.10f, title)
        drawRubyIcon(canvas, w * 0.06f, h * 0.09f, h * 0.025f, gemPaint)
        title.textAlign = Paint.Align.LEFT; title.textSize = h * 0.04f
        canvas.drawText("${GameProgress.rubies}", w * 0.06f + h * 0.04f, h * 0.105f, title)
        title.textAlign = Paint.Align.CENTER

        // 기계: 돌리는 동안 덜컹덜컹
        val shake = if (phase == Phase.SPIN) sin(t * 60f) * 10f else 0f
        machine?.let { bmp ->
            val mh = h * 0.62f; val mw = bmp.width * mh / bmp.height
            machineRect.set(w * 0.30f - mw / 2f + shake, h * 0.18f, w * 0.30f + mw / 2f + shake, h * 0.18f + mh)
            canvas.drawBitmap(bmp, null, machineRect, null)
        }

        // 캡슐이 나오는 구멍에서 굴러 나와 오른쪽 가운데로
        val outX = machineRect.centerX(); val outY = machineRect.bottom - machineRect.height() * 0.18f
        val cr = h * 0.07f
        when (phase) {
            Phase.DROP -> {
                val u = (t / DROP_TIME).coerceIn(0f, 1f)
                val cx = outX + (w * 0.68f - outX) * u
                val cy = outY + (h * 0.48f - outY) * u - sin(u * Math.PI.toFloat()) * h * 0.15f
                drawCapsule(canvas, cx, cy, cr, 0f)
            }
            Phase.OPEN -> drawPrize(canvas, w, h, cr)
            else -> Unit
        }

        // 버튼
        pullRect.set(w * 0.68f - 260f, h * 0.80f, w * 0.68f + 260f, h * 0.80f + 110f)
        canvas.drawRoundRect(pullRect, 55f, 55f, pullPaint); canvas.drawRoundRect(pullRect, 55f, 55f, ink)
        white.textSize = h * 0.045f
        canvas.drawText(if (phase == Phase.OPEN) "한 번 더! · 루비 $PRICE" else "뽑기! · 루비 $PRICE", pullRect.centerX(), pullRect.centerY() + h * 0.016f, white)
        backRect.set(w * 0.04f, h * 0.84f, w * 0.04f + 240f, h * 0.84f + 90f)
        canvas.drawRoundRect(backRect, 45f, 45f, card); canvas.drawRoundRect(backRect, 45f, 45f, ink)
        title.textSize = h * 0.032f
        canvas.drawText("◀ 나가기", backRect.centerX(), backRect.centerY() + h * 0.012f, title)
        if (phase == Phase.IDLE) {
            title.textSize = h * 0.034f
            canvas.drawText("무엇이 나올까요?", w * 0.68f, h * 0.48f, title)
        }

        if (noticeTimer > 0f) {
            title.textSize = 40f
            val nw = title.measureText(noticeText) + 70f
            rect.set(w / 2f - nw / 2f, h * 0.40f, w / 2f + nw / 2f, h * 0.40f + 80f)
            canvas.drawRoundRect(rect, 24f, 24f, card); canvas.drawRoundRect(rect, 24f, 24f, ink)
            canvas.drawText(noticeText, w / 2f, rect.centerY() + 14f, title)
        }
    }

    /** 반은 투명(흰), 반은 색깔인 캡슐 — open 0→1 이면 위아래로 갈라진다 */
    private fun drawCapsule(canvas: Canvas, cx: Float, cy: Float, r: Float, open: Float) {
        val gapY = open * r * 1.4f
        rect.set(cx - r, cy - r - gapY, cx + r, cy + r - gapY)
        fill.color = Color.argb(220, 240, 250, 255)
        canvas.drawArc(rect, 180f, 180f, true, fill); canvas.drawArc(rect, 180f, 180f, true, ink)
        rect.set(cx - r, cy - r + gapY, cx + r, cy + r + gapY)
        fill.color = capsuleColor
        canvas.drawArc(rect, 0f, 180f, true, fill); canvas.drawArc(rect, 0f, 180f, true, ink)
    }

    private fun drawPrize(canvas: Canvas, w: Float, h: Float, cr: Float) {
        val p = prize ?: return
        val cx = w * 0.68f; val cy = h * 0.48f
        val u = (t / 0.4f).coerceIn(0f, 1f)
        // 귀한 상품은 뒤가 반짝 빛난다
        if (p.rare) canvas.drawCircle(cx, cy - h * 0.04f, h * 0.18f * (0.8f + 0.2f * sin(t * 6f)), glow)
        drawCapsule(canvas, cx, cy + h * 0.10f, cr, u)
        val icon = p.id?.let { SpriteManager.shopIcons[it] }
        val size = h * 0.22f * (0.4f + 0.6f * u)
        if (icon != null) {
            rect.set(cx - size / 2f, cy - h * 0.06f - size / 2f, cx + size / 2f, cy - h * 0.06f + size / 2f)
            canvas.drawBitmap(icon, null, rect, null)
        } else drawRubyIcon(canvas, cx, cy - h * 0.06f, size * 0.35f, gemPaint)
        title.textSize = h * 0.05f
        canvas.drawText((if (p.rare) "✨ " else "") + p.name + " 나왔다!", cx, cy + h * 0.25f, title)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        if (backRect.contains(event.x, event.y)) { gameView.showShop(); return true }
        if (pullRect.contains(event.x, event.y) || machineRect.contains(event.x, event.y)) pull()
        return true
    }

    private fun notice(t: String) { noticeText = t; noticeTimer = 1.8f }

    override fun onBack(): Boolean {
        gameView.showShop()
        return true
    }

    companion object {
        /** 한 번 뽑는 값 */
        const val PRICE = 10
        private const val SPIN_TIME = 0.9f
        private const val DROP_TIME = 0.6f
        private val CAPSULE_COLORS = intArrayOf(Color.rgb(236, 64, 64), Color.rgb(60, 130, 230), Color.rgb(250, 200, 40), Color.rgb(70, 190, 90))
        /** 상품과 몫 (합 100) */
        private val PRIZES = listOf(
            Prize(null, "루비 5개", 5, 20),
            Prize("churu", "츄르", 0, 20),
            Prize("tuna_can", "참치캔", 0, 15),
            Prize("milk", "고양이 우유", 0, 10),
            Prize("h_heal", "하트 물약", 0, 10),
            Prize("h_life", "생명 물약", 0, 8),
            Prize("cat_armor", "고양이 갑옷", 0, 7),
            Prize(null, "루비 50개", 50, 5, rare = true),
            Prize("attack_up", "공격력 Lv업", 0, 3, rare = true),
            Prize("revive_potion", "고양이 부활 약", 0, 2, rare = true),
        )
    }
}
