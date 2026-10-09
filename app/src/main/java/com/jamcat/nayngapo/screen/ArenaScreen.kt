package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CatTeam
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import kotlin.math.PI
import kotlin.math.sin

/**
 * 투기장 — 상점 마을 7번 천막 (대표님 2026-10-09).
 * 데려온 냥이 중 싸울 냥이를 고르면, 밀짚으로 된 가짜 냥이(GPT 그림 straw_cat)가 나오고 냥이가 계속 때린다.
 * **30초 때릴 때마다 공격력 +3** (CatTeam.arenaTrain, 영구). 조작 없이 지켜보다가 '나가기'로 상점 마을(투기장 앞)로.
 * (한 대 간격 0.9초·무료·횟수 제한 없음·나가면 채우던 30초는 처음부터는 Claude 판단)
 */
class ArenaScreen(private val gameView: GameView, catId: Int) : Screen {

    private val cat = CatTeam.cat(catId)
    /** 냥이 그림만 빌려 쓴다 (무늬·갑옷 그대로) */
    private val sprite: Bitmap? = cat?.let {
        com.jamcat.nayngapo.entity.AllyCat(0f, 0f, it.kind, level = it.level, teamId = it.id,
            armored = it.hasArmor, attackBonus = it.attackBonus, coat = it.coat).spriteNow()
    }

    /** 이번 30초에서 흐른 시간 · 이번에 늘어난 공격력 */
    private var trained = 0f
    private var gained = 0
    private var timer = 0f

    /** 한 대 때리기: 0→1 (앞으로 뛰어들었다가 돌아온다), 가운데쯤 맞는다 */
    private var hitWait = 0.6f
    private var lunge = -1f
    private var struck = false
    /** 가짜 냥이 흔들림 (맞으면 1에서 줄어든다) */
    private var wobble = 0f

    private class Pop(var y: Float, var life: Float, val text: String, val big: Boolean)
    private val pops = mutableListOf<Pop>()

    private val skyPaint = Paint()
    private var skyShader: Shader? = null
    private val sandPaint = Paint().apply { color = Color.rgb(232, 200, 140); isAntiAlias = true }
    private val sandRing = Paint().apply { color = Color.rgb(208, 170, 108); isAntiAlias = true }
    private val fenceFill = Paint().apply { color = Color.rgb(170, 110, 64); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 5f; strokeJoin = Paint.Join.ROUND; isAntiAlias = true }
    private val shadowPaint = Paint().apply { color = Color.argb(60, 0, 0, 0); isAntiAlias = true }
    private val panelPaint = Paint().apply { color = Color.argb(225, 255, 250, 236); isAntiAlias = true }
    private val barBack = Paint().apply { color = Color.rgb(90, 80, 74); isAntiAlias = true }
    private val barFill = Paint().apply { color = Color.rgb(240, 90, 60); isAntiAlias = true }
    private val title = Paint().apply { color = Color.rgb(60, 36, 20); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val text = Paint().apply { color = Color.rgb(50, 42, 38); typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val popPaint = Paint().apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(5f, 0f, 2f, Color.argb(170, 0, 0, 0))
    }
    private val btnPaint = Paint().apply { color = Color.rgb(250, 240, 214); isAntiAlias = true }
    private val rect = RectF()
    private val exitRect = RectF()

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (cat == null || cat.gone) return
        // 30초마다 공격력 +3 (영구)
        trained += deltaTime
        if (trained >= CatTeam.ARENA_SECONDS) {
            trained -= CatTeam.ARENA_SECONDS
            if (CatTeam.arenaTrain(cat.id)) {
                gained += CatTeam.ARENA_ATTACK
                pops.add(Pop(0f, 2.0f, "공격력 +${CatTeam.ARENA_ATTACK}!", true))
            }
        }
        // 한 대씩 때린다
        if (lunge < 0f) {
            hitWait -= deltaTime
            if (hitWait <= 0f) { lunge = 0f; struck = false }
        } else {
            lunge += deltaTime / LUNGE_TIME
            if (!struck && lunge >= 0.5f) {
                struck = true
                wobble = 1f
                SoundManager.playAttack()
                pops.add(Pop(0f, 0.8f, "퍽! ${CatTeam.totalAttackOf(cat)}", false))
            }
            if (lunge >= 1f) { lunge = -1f; hitWait = HIT_INTERVAL - LUNGE_TIME }
        }
        if (wobble > 0f) wobble = (wobble - deltaTime * 1.6f).coerceAtLeast(0f)
        val it = pops.iterator()
        while (it.hasNext()) { val p = it.next(); p.life -= deltaTime; p.y -= deltaTime * 90f; if (p.life <= 0f) it.remove() }
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        val floorY = h * 0.80f

        // 하늘 · 나무 울타리 · 모래 바닥
        if (skyShader == null) skyShader = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.rgb(250, 196, 120), Color.rgb(255, 236, 196), Shader.TileMode.CLAMP)
        skyPaint.shader = skyShader
        canvas.drawRect(0f, 0f, w, h, skyPaint)
        val fenceTop = h * 0.40f; val fenceBot = h * 0.56f
        canvas.drawRect(0f, fenceTop + h * 0.03f, w, fenceTop + h * 0.06f, fenceFill)
        canvas.drawRect(0f, fenceBot - h * 0.06f, w, fenceBot - h * 0.03f, fenceFill)
        var fx = 20f
        while (fx < w) {
            rect.set(fx, fenceTop, fx + 34f, fenceBot)
            canvas.drawRoundRect(rect, 8f, 8f, fenceFill); canvas.drawRoundRect(rect, 8f, 8f, ink)
            fx += 90f
        }
        canvas.drawRect(0f, fenceBot, w, h, sandPaint)
        rect.set(w * 0.12f, floorY - h * 0.12f, w * 0.88f, floorY + h * 0.12f)
        canvas.drawOval(rect, sandRing)

        title.textSize = h * 0.08f
        canvas.drawText("투기장", w / 2f, h * 0.13f, title)

        val c = cat
        if (c == null || c.gone || sprite == null) {
            text.textSize = h * 0.05f; text.textAlign = Paint.Align.CENTER
            canvas.drawText("싸울 냥이가 없어요", w / 2f, h / 2f, text)
            text.textAlign = Paint.Align.LEFT
            drawExit(canvas, w, h)
            return
        }

        // 밀짚 가짜 냥이: 받침을 축으로 맞으면 흔들린다
        val dummy = SpriteManager.strawCat
        val dh = h * 0.42f; val dw = dummy.width * dh / dummy.height
        val dx = w * 0.64f
        rect.set(dx - dw * 0.45f, floorY - 12f, dx + dw * 0.45f, floorY + 14f)
        canvas.drawOval(rect, shadowPaint)
        canvas.save()
        canvas.rotate(sin(timer * 22f) * wobble * 14f, dx, floorY)
        rect.set(dx - dw / 2f, floorY - dh, dx + dw / 2f, floorY)
        canvas.drawBitmap(dummy, null, rect, null)
        canvas.restore()

        // 냥이: 가짜 냥이 쪽으로 뛰어들었다 돌아온다
        val ch = h * 0.24f; val cw = sprite.width * ch / sprite.height
        val reach = if (lunge >= 0f) sin(lunge * PI.toFloat()) else 0f
        val catCx = w * 0.30f + reach * (dx - dw * 0.35f - cw / 2f - w * 0.30f)
        val hop = reach * h * 0.05f
        rect.set(catCx - cw * 0.45f, floorY - 10f, catCx + cw * 0.45f, floorY + 12f)
        canvas.drawOval(rect, shadowPaint)
        rect.set(catCx - cw / 2f, floorY - ch - hop, catCx + cw / 2f, floorY - hop)
        canvas.drawBitmap(sprite, null, rect, null)

        // 맞을 때·공격력이 오를 때 떠오르는 글씨
        for (p in pops) {
            popPaint.alpha = (p.life.coerceAtMost(0.5f) / 0.5f * 255).toInt()
            if (p.big) {
                popPaint.textSize = h * 0.09f; popPaint.color = Color.rgb(255, 220, 60); popPaint.alpha = (p.life.coerceAtMost(0.5f) / 0.5f * 255).toInt()
                canvas.drawText(p.text, w / 2f, h * 0.34f + p.y, popPaint)
            } else {
                popPaint.textSize = h * 0.055f; popPaint.color = Color.WHITE; popPaint.alpha = (p.life.coerceAtMost(0.5f) / 0.5f * 255).toInt()
                canvas.drawText(p.text, dx, floorY - dh * 0.95f + p.y, popPaint)
            }
        }

        // 왼쪽 위: 이름 · 공격력 · 이번에 오른 양
        rect.set(w * 0.03f, h * 0.05f, w * 0.33f, h * 0.27f)
        canvas.drawRoundRect(rect, 22f, 22f, panelPaint); canvas.drawRoundRect(rect, 22f, 22f, ink)
        text.textSize = h * 0.05f
        canvas.drawText(c.name, rect.left + 28f, rect.top + h * 0.065f, text)
        text.textSize = h * 0.042f
        canvas.drawText("공격력 ${CatTeam.totalAttackOf(c)}", rect.left + 28f, rect.top + h * 0.125f, text)
        if (gained > 0) canvas.drawText("이번에 +$gained", rect.left + 28f, rect.top + h * 0.185f, text)

        // 아래: 다음 공격력 +3 까지
        val left = (CatTeam.ARENA_SECONDS - trained).toInt().coerceAtLeast(0) + 1
        val bw = w * 0.5f; val bx = (w - bw) / 2f; val by = h * 0.89f; val bh = h * 0.045f
        rect.set(bx, by, bx + bw, by + bh)
        canvas.drawRoundRect(rect, bh / 2f, bh / 2f, barBack)
        rect.set(bx, by, bx + bw * (trained / CatTeam.ARENA_SECONDS).coerceIn(0f, 1f), by + bh)
        canvas.drawRoundRect(rect, bh / 2f, bh / 2f, barFill)
        rect.set(bx, by, bx + bw, by + bh)
        canvas.drawRoundRect(rect, bh / 2f, bh / 2f, ink)
        text.textSize = h * 0.038f; text.textAlign = Paint.Align.CENTER
        canvas.drawText("다음 공격력 +${CatTeam.ARENA_ATTACK}까지 ${left}초", w / 2f, by - h * 0.02f, text)
        text.textAlign = Paint.Align.LEFT

        drawExit(canvas, w, h)
    }

    private fun drawExit(canvas: Canvas, w: Float, h: Float) {
        exitRect.set(w - w * 0.03f - 240f, h * 0.05f, w - w * 0.03f, h * 0.05f + 96f)
        canvas.drawRoundRect(exitRect, 48f, 48f, btnPaint); canvas.drawRoundRect(exitRect, 48f, 48f, ink)
        text.textSize = 42f; text.textAlign = Paint.Align.CENTER
        canvas.drawText("나가기", exitRect.centerX(), exitRect.centerY() + 15f, text)
        text.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            rect.set(exitRect.left - 30f, exitRect.top - 30f, exitRect.right + 30f, exitRect.bottom + 30f)
            if (rect.contains(event.x, event.y)) gameView.showShop()
        }
        return true
    }

    /** 뒤로가기 = 나가기 (상점 마을 투기장 앞) */
    override fun onBack(): Boolean {
        gameView.showShop()
        return true
    }

    companion object {
        private const val HIT_INTERVAL = 0.9f
        private const val LUNGE_TIME = 0.32f
    }
}
