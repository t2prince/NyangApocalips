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
 *
 * **'냥이끼리 싸우기' 버튼** (대표님 2026-10-09): 데려온 내 냥이 두 마리를 골라 서로 싸우게 한다.
 * 번갈아 한 대씩 때리고(공격력만큼), 체력(레벨 체력 + HP 수술)이 먼저 0이 되는 쪽이 진다.
 * 연습 시합이라 진 냥이가 다치거나 무덤이 되지는 않고 보상도 없다, 싸우는 동안 가짜 냥이 훈련 시간은 멈춘다 (Claude 판단).
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

    // ===== 냥이끼리 싸우기 =====
    private enum class Mode { TRAIN, PICK, DUEL }
    private var mode = Mode.TRAIN
    private val duelBtn = RectF()
    /** 싸울 수 있는 냥이 = 데려온(출전) 냥이 (날아냥이 빼고, 상점 마을과 같게) */
    private val brought = CatTeam.party.filter { it.kind != com.jamcat.nayngapo.entity.AllyCat.Kind.FLYING && !it.gone }
    private val picked = mutableListOf<CatTeam.TeamCat>()
    private val pickRects = List(brought.size.coerceAtMost(6)) { RectF() }
    private var noticeText = ""
    private var noticeTimer = 0f

    private class Fighter(val cat: CatTeam.TeamCat, val sprite: Bitmap) {
        val maxHp = CatTeam.maxHpFor(cat.level) + CatTeam.hpBonusOf(cat)
        var hp = maxHp
        val power = CatTeam.totalAttackOf(cat)
        var lunge = -1f
        var shake = 0f
    }
    private var fighters: List<Fighter> = emptyList()
    private var turn = 0
    private var duelWait = 0.8f
    private var winner: Fighter? = null
    private var endTimer = 0f

    private val spriteCache = mutableMapOf<Int, Bitmap>()
    private fun spriteOf(c: CatTeam.TeamCat): Bitmap = spriteCache.getOrPut(c.id) {
        com.jamcat.nayngapo.entity.AllyCat(0f, 0f, c.kind, level = c.level, teamId = c.id,
            armored = c.hasArmor, attackBonus = c.attackBonus, coat = c.coat).spriteNow()
    }

    private fun startDuel(a: CatTeam.TeamCat, b: CatTeam.TeamCat) {
        fighters = listOf(Fighter(a, spriteOf(a)), Fighter(b, spriteOf(b)))
        turn = if (Math.random() < 0.5) 0 else 1
        duelWait = 0.8f; winner = null; endTimer = 0f
        pops.clear()
        mode = Mode.DUEL
    }

    private fun tapDuelButton() {
        when {
            brought.size < 2 -> { noticeText = "냥이 2마리를 데려와야 싸울 수 있어요! (캣타워에서 출전)"; noticeTimer = 2.2f }
            brought.size == 2 -> startDuel(brought[0], brought[1])
            else -> { picked.clear(); mode = Mode.PICK }
        }
    }

    private fun updateDuel(deltaTime: Float) {
        val it = pops.iterator()
        while (it.hasNext()) { val p = it.next(); p.life -= deltaTime; p.y -= deltaTime * 90f; if (p.life <= 0f) it.remove() }
        for (f in fighters) if (f.shake > 0f) f.shake = (f.shake - deltaTime * 1.6f).coerceAtLeast(0f)
        if (winner != null) { endTimer += deltaTime; return }
        val atk = fighters[turn]; val def = fighters[1 - turn]
        if (atk.lunge < 0f) {
            duelWait -= deltaTime
            if (duelWait <= 0f) atk.lunge = 0f
        } else {
            val before = atk.lunge
            atk.lunge += deltaTime / LUNGE_TIME
            if (before < 0.5f && atk.lunge >= 0.5f) {
                def.hp = (def.hp - atk.power).coerceAtLeast(0)
                def.shake = 1f
                SoundManager.playAttack()
                pops.add(Pop(0f, 0.8f, "-${atk.power}", turn == 0))
                if (def.hp <= 0) winner = atk
            }
            if (atk.lunge >= 1f) { atk.lunge = -1f; duelWait = HIT_INTERVAL - LUNGE_TIME; turn = 1 - turn }
        }
    }

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime
        if (mode == Mode.DUEL) { updateDuel(deltaTime); return }
        if (mode == Mode.PICK) return
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

        if (mode == Mode.DUEL) { drawDuel(canvas, w, h, floorY); drawExit(canvas, w, h); drawNotice(canvas, w, h); return }
        if (mode == Mode.PICK) { drawPick(canvas, w, h); drawExit(canvas, w, h); return }

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
        drawDuelButton(canvas, w, h)
        drawNotice(canvas, w, h)
    }

    private fun drawDuelButton(canvas: Canvas, w: Float, h: Float) {
        duelBtn.set(w - w * 0.03f - 360f, h * 0.05f + 120f, w - w * 0.03f, h * 0.05f + 216f)
        canvas.drawRoundRect(duelBtn, 48f, 48f, duelBtnPaint); canvas.drawRoundRect(duelBtn, 48f, 48f, ink)
        text.textSize = 40f; text.textAlign = Paint.Align.CENTER
        canvas.drawText("⚔ 냥이끼리 싸우기", duelBtn.centerX(), duelBtn.centerY() + 14f, text)
        text.textAlign = Paint.Align.LEFT
    }
    private val duelBtnPaint = Paint().apply { color = Color.rgb(255, 214, 120); isAntiAlias = true }

    private fun drawNotice(canvas: Canvas, w: Float, h: Float) {
        if (noticeTimer <= 0f) return
        text.textSize = 42f; text.textAlign = Paint.Align.CENTER
        val nw = text.measureText(noticeText) + 70f
        rect.set(w / 2f - nw / 2f, h * 0.30f, w / 2f + nw / 2f, h * 0.30f + 80f)
        canvas.drawRoundRect(rect, 24f, 24f, panelPaint); canvas.drawRoundRect(rect, 24f, 24f, ink)
        canvas.drawText(noticeText, w / 2f, rect.centerY() + 15f, text)
        text.textAlign = Paint.Align.LEFT
    }

    /** 데려온 냥이가 3마리 이상이면 싸울 두 마리를 고른다 */
    private fun drawPick(canvas: Canvas, w: Float, h: Float) {
        rect.set(w * 0.08f, h * 0.18f, w * 0.92f, h * 0.86f)
        canvas.drawRoundRect(rect, 28f, 28f, panelPaint); canvas.drawRoundRect(rect, 28f, 28f, ink)
        title.textSize = h * 0.05f
        canvas.drawText("싸울 냥이 두 마리를 고르세요 (${picked.size}/2)", w / 2f, rect.top + h * 0.08f, title)
        val n = pickRects.size
        val cw = (rect.width() - 40f) / n
        val top = rect.top + h * 0.13f; val bottom = rect.bottom - h * 0.04f
        for (k in 0 until n) {
            val c = brought[k]
            val r = pickRects[k]
            r.set(rect.left + 20f + k * cw + 8f, top, rect.left + 20f + (k + 1) * cw - 8f, bottom)
            fill.color = if (c in picked) Color.rgb(255, 214, 120) else Color.rgb(250, 244, 230)
            canvas.drawRoundRect(r, 20f, 20f, fill); canvas.drawRoundRect(r, 20f, 20f, ink)
            val bmp = spriteOf(c)
            val s = (r.width() * 0.75f / bmp.width).coerceAtMost(r.height() * 0.55f / bmp.height)
            val bx = r.centerX(); val by = r.top + r.height() * 0.12f
            rect2.set(bx - bmp.width * s / 2f, by, bx + bmp.width * s / 2f, by + bmp.height * s)
            canvas.drawBitmap(bmp, null, rect2, null)
            text.textSize = h * 0.038f; text.textAlign = Paint.Align.CENTER
            canvas.drawText(c.name, r.centerX(), r.bottom - r.height() * 0.18f, text)
            text.textSize = h * 0.03f
            canvas.drawText("공격력 ${CatTeam.totalAttackOf(c)}", r.centerX(), r.bottom - r.height() * 0.07f, text)
            text.textAlign = Paint.Align.LEFT
        }
    }
    private val fill = Paint().apply { isAntiAlias = true }
    private val rect2 = RectF()
    private val hpBack = Paint().apply { color = Color.rgb(80, 70, 66); isAntiAlias = true }
    private val hpFill = Paint().apply { color = Color.rgb(90, 200, 90); isAntiAlias = true }
    private val againBtn = RectF()
    private val backBtn = RectF()

    /** 냥이끼리 싸우기: 왼쪽 냥이는 오른쪽을, 오른쪽 냥이는 왼쪽을 보고 번갈아 뛰어들어 때린다 */
    private fun drawDuel(canvas: Canvas, w: Float, h: Float, floorY: Float) {
        val ch = h * 0.24f
        for ((i, f) in fighters.withIndex()) {
            val base = if (i == 0) f.sprite else SpriteManager.getFlipped(f.sprite)
            val cw = base.width * ch / base.height
            val home = if (i == 0) w * 0.30f else w * 0.70f
            val toward = if (i == 0) 1f else -1f
            val reach = if (f.lunge >= 0f) sin(f.lunge * PI.toFloat()) else 0f
            val cx = home + toward * reach * (w * 0.40f - cw)
            val knocked = f.hp <= 0
            rect.set(cx - cw * 0.45f, floorY - 10f, cx + cw * 0.45f, floorY + 12f)
            canvas.drawOval(rect, shadowPaint)
            canvas.save()
            if (knocked) canvas.rotate(-toward * 90f * (endTimer / 0.4f).coerceAtMost(1f), cx, floorY)
            else if (f.shake > 0f) canvas.rotate(sin(timer * 40f) * f.shake * 8f, cx, floorY)
            val hop = reach * h * 0.05f
            rect.set(cx - cw / 2f, floorY - ch - hop, cx + cw / 2f, floorY - hop)
            canvas.drawBitmap(base, null, rect, null)
            canvas.restore()
            // 이름 · 체력 막대
            val bw = w * 0.22f; val bx = home - bw / 2f; val by = floorY - ch - h * 0.10f
            text.textSize = h * 0.042f; text.textAlign = Paint.Align.CENTER
            canvas.drawText("${f.cat.name} (공격력 ${f.power})", home, by - h * 0.02f, text)
            text.textAlign = Paint.Align.LEFT
            rect.set(bx, by, bx + bw, by + h * 0.035f)
            canvas.drawRoundRect(rect, 12f, 12f, hpBack)
            rect.set(bx, by, bx + bw * f.hp / f.maxHp.coerceAtLeast(1), by + h * 0.035f)
            canvas.drawRoundRect(rect, 12f, 12f, hpFill)
            rect.set(bx, by, bx + bw, by + h * 0.035f)
            canvas.drawRoundRect(rect, 12f, 12f, ink)
        }
        for (p in pops) {
            popPaint.textSize = h * 0.06f; popPaint.color = Color.WHITE
            popPaint.alpha = (p.life.coerceAtMost(0.5f) / 0.5f * 255).toInt()
            // big = 왼쪽 냥이가 때렸다 → 오른쪽 냥이 위에
            canvas.drawText(p.text, if (p.big) w * 0.70f else w * 0.30f, floorY - ch - h * 0.17f + p.y, popPaint)
        }
        againBtn.setEmpty(); backBtn.setEmpty()
        val win = winner ?: return
        if (endTimer < 0.6f) return
        popPaint.textSize = h * 0.1f; popPaint.color = Color.rgb(255, 220, 60); popPaint.alpha = 255
        canvas.drawText("${win.cat.name} 승리!", w / 2f, h * 0.33f, popPaint)
        // 다시 싸우기 · 가짜 냥이로 돌아가기
        againBtn.set(w / 2f - 420f, h * 0.88f, w / 2f - 20f, h * 0.88f + 90f)
        backBtn.set(w / 2f + 20f, h * 0.88f, w / 2f + 420f, h * 0.88f + 90f)
        for ((r, label) in listOf(againBtn to "다시 싸우기", backBtn to "가짜 냥이 훈련")) {
            canvas.drawRoundRect(r, 45f, 45f, duelBtnPaint); canvas.drawRoundRect(r, 45f, 45f, ink)
            text.textSize = 40f; text.textAlign = Paint.Align.CENTER
            canvas.drawText(label, r.centerX(), r.centerY() + 14f, text)
            text.textAlign = Paint.Align.LEFT
        }
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
            val px = event.x; val py = event.y
            rect.set(exitRect.left - 30f, exitRect.top - 30f, exitRect.right + 30f, exitRect.bottom + 30f)
            if (rect.contains(px, py)) { gameView.showShop(); return true }
            when (mode) {
                Mode.TRAIN -> if (duelBtn.contains(px, py)) tapDuelButton()
                Mode.PICK -> {
                    val k = pickRects.indexOfFirst { it.contains(px, py) }
                    if (k >= 0) {
                        val c = brought[k]
                        if (c in picked) picked.remove(c) else picked.add(c)
                        if (picked.size == 2) startDuel(picked[0], picked[1])
                    }
                }
                Mode.DUEL -> {
                    if (againBtn.contains(px, py)) startDuel(fighters[0].cat, fighters[1].cat)
                    else if (backBtn.contains(px, py)) { mode = Mode.TRAIN; pops.clear() }
                }
            }
        }
        return true
    }

    /** 뒤로가기 = 나가기 (상점 마을 투기장 앞) */
    override fun onBack(): Boolean {
        if (mode != Mode.TRAIN) { mode = Mode.TRAIN; pops.clear(); return true }   // 싸우기·고르기부터 닫는다
        gameView.showShop()
        return true
    }

    companion object {
        private const val HIT_INTERVAL = 0.9f
        private const val LUNGE_TIME = 0.32f
    }
}
