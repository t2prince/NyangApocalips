package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CatTeam
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 투기장 — 상점 마을 7번 천막 (대표님 2026-10-09).
 * 데려온 냥이 중 싸울 냥이를 고르면, 밀짚으로 된 가짜 냥이(GPT 그림 straw_cat)가 나오고 냥이가 계속 때린다.
 * **30초 때릴 때마다 공격력 +3** (CatTeam.arenaTrain, 영구). 조작 없이 지켜보다가 '나가기'로 상점 마을(투기장 앞)로.
 * (한 대 간격 0.9초·무료·횟수 제한 없음·나가면 채우던 30초는 처음부터는 Claude 판단)
 *
 * **Lv 버튼** (대표님 2026-10-09): 루비 10개로 가짜 냥이 Lv업 — Lv업 할 때마다 가짜 냥이가 **두 배로 딴딴해진다**
 * (딴딴함 = 100 × 2^(Lv-1), 다 깎이면 부서졌다가 다시 선다). **최대 Lv 1,000,000,000**(대표님 2026-10-09).
 * 딴딴함이 너무 커지면 숫자를 억·조·지수로 줄여 보여 주고, 끝없이 크면 '무한'. 30초마다 오르는 공격력은 Lv 와 상관없이 +3.
 * 재료는 Lv1 밀짚 → 나무 → 돌 → 철 → 금 → Lv6 이상 다이아.
 * **쓰는 값** (대표님 2026-10-09): 루비 1개를 내면 1분 쓸 수 있다. 남은 시간이 있을 때만 훈련·싸움이 흘러가고, 다 쓰면 멈춘다.
 * 남은 시간은 투기장을 나갔다 와도 남는다(앱을 끄면 사라짐) — Claude 판단.
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
    /** 가짜 냥이 Lv · 딴딴함 — Lv업마다 두 배 */
    private val dummyLevel: Int get() = com.jamcat.nayngapo.engine.GameProgress.dummyLevel
    private val dummyMax: Double get() = 100.0 * Math.pow(2.0, (dummyLevel - 1).toDouble())
    private val trainGain: Int get() = CatTeam.ARENA_ATTACK
    private var dummyHp = 100.0 * Math.pow(2.0, (com.jamcat.nayngapo.engine.GameProgress.dummyLevel - 1).toDouble())

    /** 큰 수를 짧게 (억·조·지수, 끝없으면 '무한') */
    private val material: String get() = MATERIALS[dummyLevel.coerceIn(1, MATERIALS.size) - 1]

    private fun big(v: Double): String = when {
        v.isInfinite() -> "무한"
        v >= 1e16 -> "%.1e".format(v)
        v >= 1e12 -> "%.1f조".format(v / 1e12)
        v >= 1e8 -> "%.1f억".format(v / 1e8)
        else -> "%,d".format(v.toLong())
    }
    /** 부서졌다 다시 서기까지 남은 시간 */
    private var broken = 0f
    private val lvBtn = RectF()
    private var gained = 0
    private var timer = 0f

    /** 한 대 때리기: 0→1 (앞으로 뛰어들었다가 돌아온다), 가운데쯤 맞는다 */
    private var hitWait = 0.6f
    private var lunge = -1f
    private var struck = false
    /** 가짜 냥이 흔들림 (맞으면 1에서 줄어든다) */
    private var wobble = 0f

    /** x: 냥이끼리 싸울 때 글씨가 뜰 자리(화면 가로 비율), 훈련 때는 쓰지 않는다 */
    private class Pop(var y: Float, var life: Float, val text: String, val big: Boolean, val x: Float = 0f)
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

    /** home: 화면 가로 비율 자리 */
    private class Fighter(val cat: CatTeam.TeamCat, val sprite: Bitmap, val home: Float) {
        val maxHp = CatTeam.maxHpFor(cat.level) + CatTeam.hpBonusOf(cat)
        var hp = maxHp
        val power = CatTeam.totalAttackOf(cat)
        var lunge = -1f
        var shake = 0f
        /** 지금 때리러 가는 상대 */
        var target: Fighter? = null
        /** 쓰러진 뒤 흐른 시간 (넘어지는 모습) */
        var downTime = 0f
        val down: Boolean get() = hp <= 0
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

    /** 2마리면 맞대결, 3마리면 서로 아무나 때리는 난투 (대표님 2026-10-09 「고양이 3마리가 투기장에서 싸울 수 있어」) */
    private fun startDuel(cats: List<CatTeam.TeamCat>) {
        val homes = if (cats.size >= 3) listOf(0.20f, 0.50f, 0.80f) else listOf(0.30f, 0.70f)
        fighters = cats.take(3).mapIndexed { i, c -> Fighter(c, spriteOf(c), homes[i]) }
        turn = (Math.random() * fighters.size).toInt()
        duelWait = 0.8f; winner = null; endTimer = 0f
        pops.clear()
        mode = Mode.DUEL
    }

    private fun tapDuelButton() {
        when {
            brought.size < 2 -> { noticeText = "냥이 2마리를 데려와야 싸울 수 있어요! (캣타워에서 출전)"; noticeTimer = 2.2f }
            brought.size == 2 -> startDuel(brought)
            else -> { picked.clear(); mode = Mode.PICK }
        }
    }

    private fun updateDuel(deltaTime: Float) {
        val it = pops.iterator()
        while (it.hasNext()) { val p = it.next(); p.life -= deltaTime; p.y -= deltaTime * 90f; if (p.life <= 0f) it.remove() }
        for (f in fighters) {
            if (f.shake > 0f) f.shake = (f.shake - deltaTime * 1.6f).coerceAtLeast(0f)
            if (f.down) f.downTime += deltaTime
        }
        if (winner != null) { endTimer += deltaTime; return }
        val atk = fighters[turn]
        if (atk.lunge < 0f) {
            duelWait -= deltaTime
            if (duelWait <= 0f) {
                // 서 있는 상대 중 아무나 (맞대결이면 남은 하나)
                atk.target = fighters.filter { f -> f !== atk && !f.down }.randomOrNull()
                atk.lunge = 0f
            }
        } else {
            val before = atk.lunge
            atk.lunge += deltaTime / LUNGE_TIME
            val def = atk.target
            if (def != null && before < 0.5f && atk.lunge >= 0.5f) {
                def.hp = (def.hp - atk.power).coerceAtLeast(0)
                def.shake = 1f
                SoundManager.playAttack()
                pops.add(Pop(0f, 0.8f, "-${atk.power}", false, def.home))
                val standing = fighters.filter { f -> !f.down }
                if (standing.size == 1) winner = standing[0]
            }
            if (atk.lunge >= 1f) {
                atk.lunge = -1f; duelWait = HIT_INTERVAL - LUNGE_TIME
                // 다음 차례 = 서 있는 다음 냥이
                var t = turn
                do { t = (t + 1) % fighters.size } while (fighters[t].down && t != turn)
                turn = t
            }
        }
    }

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime
        if (mode == Mode.PICK) return
        // 루비 1개 = 1분: 시간이 없으면 멈춰 있다
        if (timeLeft <= 0f) return
        timeLeft = (timeLeft - deltaTime).coerceAtLeast(0f)
        if (mode == Mode.DUEL) { updateDuel(deltaTime); return }
        if (cat == null || cat.gone) return
        // 30초마다 공격력 +3 (영구)
        trained += deltaTime
        if (trained >= CatTeam.ARENA_SECONDS) {
            trained -= CatTeam.ARENA_SECONDS
            val gain = trainGain
            if (CatTeam.arenaTrain(cat.id, gain)) {
                gained += gain
                pops.add(Pop(0f, 2.0f, "공격력 +$gain!", true))
            }
        }
        // 부서진 가짜 냥이는 1초 뒤 다시 선다 (딴딴함 가득)
        if (broken > 0f) { broken -= deltaTime; if (broken <= 0f) dummyHp = dummyMax }
        // 한 대씩 때린다
        if (lunge < 0f) {
            hitWait -= deltaTime
            if (hitWait <= 0f && broken <= 0f) { lunge = 0f; struck = false }
        } else {
            lunge += deltaTime / LUNGE_TIME
            if (!struck && lunge >= 0.5f) {
                struck = true
                // 딴딴할수록 덜 흔들린다
                wobble = 1f / (1f + (dummyLevel - 1) * 0.35f)
                SoundManager.playAttack()
                val power = CatTeam.totalAttackOf(cat)
                dummyHp = (dummyHp - power).coerceAtLeast(0.0)
                pops.add(Pop(0f, 0.8f, "퍽! $power", false))
                if (dummyHp <= 0.0) { broken = 1f; pops.add(Pop(0f, 1.0f, "와장창!", false)) }
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

        if (mode == Mode.DUEL) { drawDuel(canvas, w, h, floorY); drawExit(canvas, w, h); drawTime(canvas, w, h); drawNotice(canvas, w, h); return }
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
        // Lv 마다 재료가 바뀐다: 밀짚 → 나무 → 돌 → 철 → 금 → 다이아 (2026-10-09)
        val dummy = SpriteManager.dummyByLevel.getOrElse(dummyLevel.coerceAtMost(SpriteManager.dummyByLevel.size) - 1) { SpriteManager.strawCat }
        val dh = h * 0.42f; val dw = dummy.width * dh / dummy.height
        val dx = w * 0.64f
        rect.set(dx - dw * 0.45f, floorY - 12f, dx + dw * 0.45f, floorY + 14f)
        canvas.drawOval(rect, shadowPaint)
        canvas.save()
        if (broken > 0f) canvas.rotate(70f * (1f - broken).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) } + 20f, dx, floorY)   // 부서져 넘어진다
        else canvas.rotate(sin(timer * 22f) * wobble * 14f, dx, floorY)
        rect.set(dx - dw / 2f, floorY - dh, dx + dw / 2f, floorY)
        canvas.drawBitmap(dummy, null, rect, null)
        canvas.restore()
        // 가짜 냥이 Lv · 딴딴함 막대
        val hbw = dw * 0.8f; val hbx = dx - hbw / 2f; val hby = floorY - dh - h * 0.06f
        text.textSize = h * 0.036f; text.textAlign = Paint.Align.CENTER
        canvas.drawText("$material 냥이 Lv ${"%,d".format(dummyLevel)} · 딴딴함 ${big(dummyHp)}/${big(dummyMax)}", dx, hby - h * 0.015f, text)
        text.textAlign = Paint.Align.LEFT
        rect.set(hbx, hby, hbx + hbw, hby + h * 0.03f)
        canvas.drawRoundRect(rect, 10f, 10f, barBack)
        val ratio = if (dummyMax.isInfinite()) (if (dummyHp > 0.0) 1f else 0f) else (dummyHp / dummyMax).toFloat()
        rect.set(hbx, hby, hbx + hbw * ratio.coerceIn(0f, 1f), hby + h * 0.03f)
        canvas.drawRoundRect(rect, 10f, 10f, strawBar)
        rect.set(hbx, hby, hbx + hbw, hby + h * 0.03f)
        canvas.drawRoundRect(rect, 10f, 10f, ink)

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
        canvas.drawText("다음 공격력 +${trainGain}까지 ${left}초", w / 2f, by - h * 0.02f, text)
        text.textAlign = Paint.Align.LEFT

        drawExit(canvas, w, h)
        drawDuelButton(canvas, w, h)
        drawLvButton(canvas, w, h)
        drawTime(canvas, w, h)
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
    private val strawBar = Paint().apply { color = Color.rgb(240, 190, 60); isAntiAlias = true }
    private val lvBtnPaint = Paint().apply { color = Color.rgb(160, 220, 255); isAntiAlias = true }

    /** 가짜 냥이 Lv업 버튼 (루비 10) — 냥이끼리 싸우기 버튼 아래 */
    private fun drawLvButton(canvas: Canvas, w: Float, h: Float) {
        lvBtn.set(duelBtn.left, duelBtn.bottom + 24f, duelBtn.right, duelBtn.bottom + 120f)
        canvas.drawRoundRect(lvBtn, 48f, 48f, lvBtnPaint); canvas.drawRoundRect(lvBtn, 48f, 48f, ink)
        text.textSize = 38f; text.textAlign = Paint.Align.CENTER
        val gp = com.jamcat.nayngapo.engine.GameProgress
        val label = if (dummyLevel >= gp.DUMMY_LEVEL_MAX) "Lv ${"%,d".format(dummyLevel)} (최대)" else "Lv 업 ▲  루비 ${gp.DUMMY_LEVEL_PRICE}"
        canvas.drawText(label, lvBtn.centerX(), lvBtn.centerY() + 13f, text)
        text.textAlign = Paint.Align.LEFT
    }

    // ===== 루비 1개 = 1분 =====
    private val timeBtn = RectF()
    private val timeBtnPaint = Paint().apply { color = Color.rgb(190, 240, 170); isAntiAlias = true }
    private val veil = Paint().apply { color = Color.argb(120, 0, 0, 0) }

    /** 위 가운데(제목 아래): 남은 시간 · '+1분 (루비 1)' 버튼. 시간이 없으면 화면을 어둡게 하고 알려 준다 */
    private fun drawTime(canvas: Canvas, w: Float, h: Float) {
        val sec = kotlin.math.ceil(timeLeft).toInt()
        if (timeLeft <= 0f) {
            canvas.drawRect(0f, 0f, w, h, veil)
            popPaint.textSize = h * 0.06f; popPaint.color = Color.WHITE; popPaint.alpha = 255
            canvas.drawText("루비 1개를 내면 1분 쓸 수 있어요", w / 2f, h * 0.45f, popPaint)
        }
        timeBtn.set(w / 2f - 300f, h * 0.17f, w / 2f + 300f, h * 0.17f + 96f)
        canvas.drawRoundRect(timeBtn, 48f, 48f, timeBtnPaint); canvas.drawRoundRect(timeBtn, 48f, 48f, ink)
        text.textSize = 40f; text.textAlign = Paint.Align.CENTER
        canvas.drawText("⏱ ${sec / 60}:${"%02d".format(sec % 60)}   +1분 (루비 1)", timeBtn.centerX(), timeBtn.centerY() + 14f, text)
        text.textAlign = Paint.Align.LEFT
    }

    private fun tapTimeButton() {
        val gp = com.jamcat.nayngapo.engine.GameProgress
        if (gp.spendRubies(ARENA_MINUTE_PRICE)) {
            timeLeft += 60f
            noticeText = "1분 늘었어요! (루비 ${gp.rubies}개 남음)"; noticeTimer = 1.8f
        } else { noticeText = "루비가 모자라요 (1분 = 루비 $ARENA_MINUTE_PRICE)"; noticeTimer = 2f }
    }

    private fun tapLvButton() {
        val gp = com.jamcat.nayngapo.engine.GameProgress
        when {
            dummyLevel >= gp.DUMMY_LEVEL_MAX -> { noticeText = "가짜 냥이가 이미 최대 Lv 이에요"; noticeTimer = 2f }
            gp.levelUpDummy() -> {
                dummyHp = dummyMax; broken = 0f
                noticeText = "$material 냥이 Lv ${"%,d".format(dummyLevel)}! 두 배로 딴딴해졌다냥 (루비 ${gp.rubies}개 남음)"; noticeTimer = 2.2f
            }
            else -> { noticeText = "루비가 ${gp.DUMMY_LEVEL_PRICE}개 있어야 Lv업 할 수 있어요 (지금 ${gp.rubies}개)"; noticeTimer = 2.2f }
        }
    }

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
        canvas.drawText("싸울 냥이를 2~3마리 고르세요 (${picked.size}/3)", w / 2f, rect.top + h * 0.08f, title)
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
        // 두 마리를 골랐으면 '싸우기 시작' (세 마리째를 고르면 바로 시작)
        startBtn.setEmpty()
        if (picked.size == 2) {
            startBtn.set(w / 2f - 230f, h * 0.88f, w / 2f + 230f, h * 0.88f + 90f)
            canvas.drawRoundRect(startBtn, 45f, 45f, duelBtnPaint); canvas.drawRoundRect(startBtn, 45f, 45f, ink)
            text.textSize = 40f; text.textAlign = Paint.Align.CENTER
            canvas.drawText("두 마리로 싸우기 시작", startBtn.centerX(), startBtn.centerY() + 14f, text)
            text.textAlign = Paint.Align.LEFT
        }
    }
    private val startBtn = RectF()
    private val fill = Paint().apply { isAntiAlias = true }
    private val rect2 = RectF()
    private val hpBack = Paint().apply { color = Color.rgb(80, 70, 66); isAntiAlias = true }
    private val hpFill = Paint().apply { color = Color.rgb(90, 200, 90); isAntiAlias = true }
    private val againBtn = RectF()
    private val backBtn = RectF()

    /** 냥이끼리 싸우기: 차례가 된 냥이가 상대 쪽으로 뛰어들어 때린다 (2~3마리) */
    private fun drawDuel(canvas: Canvas, w: Float, h: Float, floorY: Float) {
        val ch = h * (if (fighters.size >= 3) 0.20f else 0.24f)
        for (f in fighters) {
            val home = w * f.home
            val tgt = f.target
            // 때리러 갈 때는 상대 쪽을 보고, 그 밖엔 가운데를 본다
            val toward = when {
                f.lunge >= 0f && tgt != null -> if (tgt.home > f.home) 1f else -1f
                else -> if (f.home < 0.5f) 1f else -1f
            }
            val base = if (toward > 0f) f.sprite else SpriteManager.getFlipped(f.sprite)
            val cw = base.width * ch / base.height
            val reach = if (f.lunge >= 0f) sin(f.lunge * PI.toFloat()) else 0f
            val dist = if (tgt != null) abs(tgt.home - f.home) * w - cw else 0f
            val cx = home + toward * reach * dist.coerceAtLeast(0f)
            rect.set(cx - cw * 0.45f, floorY - 10f, cx + cw * 0.45f, floorY + 12f)
            canvas.drawOval(rect, shadowPaint)
            canvas.save()
            if (f.down) canvas.rotate(-toward * 90f * (f.downTime / 0.4f).coerceAtMost(1f), cx, floorY)
            else if (f.shake > 0f) canvas.rotate(sin(timer * 40f) * f.shake * 8f, cx, floorY)
            val hop = reach * h * 0.05f
            rect.set(cx - cw / 2f, floorY - ch - hop, cx + cw / 2f, floorY - hop)
            canvas.drawBitmap(base, null, rect, null)
            canvas.restore()
            // 이름 · 체력 막대
            val bw = w * (if (fighters.size >= 3) 0.2f else 0.22f); val bx = home - bw / 2f; val by = floorY - ch - h * 0.10f
            text.textSize = h * 0.038f; text.textAlign = Paint.Align.CENTER
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
            canvas.drawText(p.text, w * p.x, floorY - ch - h * 0.17f + p.y, popPaint)
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
            if (mode != Mode.PICK && timeBtn.contains(px, py)) { tapTimeButton(); return true }
            when (mode) {
                Mode.TRAIN -> if (duelBtn.contains(px, py)) tapDuelButton() else if (lvBtn.contains(px, py)) tapLvButton()
                Mode.PICK -> {
                    val k = pickRects.indexOfFirst { it.contains(px, py) }
                    if (k >= 0) {
                        val c = brought[k]
                        if (c in picked) picked.remove(c) else picked.add(c)
                        if (picked.size == 3) startDuel(picked.toList())
                    } else if (picked.size == 2 && startBtn.contains(px, py)) {
                        startDuel(picked.toList())
                    }
                }
                Mode.DUEL -> {
                    if (againBtn.contains(px, py)) startDuel(fighters.map { f -> f.cat })
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
        /** 투기장 남은 시간 (초) — 루비 1개에 60초 */
        private var timeLeft = 0f
        /** Lv 별 가짜 냥이 재료 이름 */
        private val MATERIALS = listOf("밀짚", "나무", "돌", "철", "금", "다이아")
        private const val ARENA_MINUTE_PRICE = 1
        private const val HIT_INTERVAL = 0.9f
        private const val LUNGE_TIME = 0.32f
    }
}
