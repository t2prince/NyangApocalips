package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import kotlin.math.sin

/**
 * 낚시터 — 상점 마을 맨 끝 (대표님 2026-10-10 「낚시터에서 낚시를 하면 물고기가 잡혀, 그걸로 고양이 먹이를 줄 수 있어」).
 * '던지기' → 찌가 물에 떠 있다가(2~5초) 쏙 들어가며 '!' → 1초 안에 '당기기'를 누르면 물고기가 잡혀 가방에 들어간다.
 * 늦거나 너무 일찍 당기면 놓친다. 잡은 물고기는 캣타워 '간식 주기'에서 냥이에게 준다.
 * **'낚시줄 조종하기'**(대표님 2026-10-16): 누르면 낚시줄이 물속으로 내려가고, 물속을 헤엄치는 물고기들 사이로 손가락을 대고 움직여
 * 낚싯바늘을 옮긴다 — 바늘이 물고기에 닿으면 그 물고기가 물려 끌려 올라온다(한 번에 한 마리만). 헤엄치는 마릿수·빠르기는 Claude 판단.
 * 포만감 = 작은 3 · 큰 6 · 황금 10 (대표님 2026-10-10). 확률·시간·공짜는 Claude 판단 (작은 물고기 60% · 큰 물고기 32% · 황금 물고기 8%).
 */
class FishingScreen(private val gameView: GameView) : Screen {

    private enum class State { READY, CAST, WAIT, BITE, CAUGHT, MISSED, CONTROL, REEL }

    // ===== 낚시줄 조종하기 =====
    private class Swimmer(val id: String, var x: Float, val y: Float, var dir: Float, val speed: Float)
    private val swimmers = mutableListOf<Swimmer>()
    private var hooked: Swimmer? = null
    private var hookX = 0f
    private var hookY = 0f
    private var hookTX = 0f
    private var hookTY = 0f
    private val controlRect = RectF()

    private fun randomFishId(): String {
        val r = Math.random()
        return when { r < 0.08 -> "fish_gold"; r < 0.40 -> "fish_big"; else -> "fish_small" }
    }

    /** 낚시줄을 물속으로 내리고 물고기들을 헤엄치게 한다 */
    private fun startControl() {
        val w = gameView.screenWidth.toFloat(); val h = gameView.screenHeight.toFloat()
        val waterY = h * 0.55f
        state = State.CONTROL; t = 0f; catchId = null; hooked = null
        hookX = w * 0.62f; hookY = waterY + 60f; hookTX = hookX; hookTY = hookY
        swimmers.clear()
        repeat(SWIMMERS) {
            swimmers.add(Swimmer(randomFishId(), w * (0.35f + Math.random().toFloat() * 0.6f),
                waterY + 90f + Math.random().toFloat() * (h * 0.24f), if (Math.random() < 0.5) 1f else -1f, 60f + Math.random().toFloat() * 90f))
        }
    }

    private fun fishSize(id: String) = when (id) { "fish_big" -> 120f; "fish_gold" -> 90f; else -> 75f }
    private var state = State.READY
    private var t = 0f
    private var waitTime = 3f
    private var timer = 0f
    private var catchId: String? = null
    private val btnRect = RectF()
    private val exitRect = RectF()
    private val rect = RectF()

    private val sky = Paint()
    private var skyShader: Shader? = null
    private val water = Paint().apply { color = Color.rgb(90, 170, 230) }
    private val waterDark = Paint().apply { color = Color.rgb(70, 145, 210); strokeWidth = 4f; isAntiAlias = true }
    private val wood = Paint().apply { color = Color.rgb(170, 118, 70); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val line = Paint().apply { color = Color.rgb(250, 250, 250); strokeWidth = 2.5f; isAntiAlias = true; style = Paint.Style.STROKE }
    private val rod = Paint().apply { color = Color.rgb(120, 80, 40); strokeWidth = 7f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val bobberRed = Paint().apply { color = Color.rgb(230, 60, 60); isAntiAlias = true }
    private val bobberWhite = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val card = Paint().apply { color = Color.argb(230, 255, 252, 244); isAntiAlias = true }
    private val btnPaint = Paint().apply { color = Color.rgb(255, 196, 90); isAntiAlias = true }
    private val bitePaint = Paint().apply { color = Color.rgb(240, 80, 80); isAntiAlias = true }
    private val text = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val big = Paint().apply {
        color = Color.rgb(255, 230, 80); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(6f, 0f, 3f, Color.argb(170, 0, 0, 0))
    }
    private val path = Path()

    override fun update(deltaTime: Float) {
        timer += deltaTime
        t += deltaTime
        when (state) {
            State.CAST -> if (t >= 0.5f) { state = State.WAIT; t = 0f; waitTime = 2f + (Math.random() * 3f).toFloat() }
            State.WAIT -> if (t >= waitTime) { state = State.BITE; t = 0f; SoundManager.playCatHit() }
            State.BITE -> if (t >= BITE_WINDOW) { state = State.MISSED; t = 0f }
            State.CONTROL -> {
                val w = gameView.screenWidth.toFloat()
                for (f in swimmers) {
                    f.x += f.dir * f.speed * deltaTime
                    if (f.x < w * 0.33f || f.x > w * 0.97f) { f.dir = -f.dir; f.x = f.x.coerceIn(w * 0.33f, w * 0.97f) }
                }
                // 바늘은 손가락 쪽으로 따라간다
                val dx = hookTX - hookX; val dy = hookTY - hookY
                val d = kotlin.math.hypot(dx, dy)
                val step = HOOK_SPEED * deltaTime
                if (d > step) { hookX += dx / d * step; hookY += dy / d * step } else { hookX = hookTX; hookY = hookTY }
                // 바늘이 물고기에 닿으면 문다 (한 마리만)
                swimmers.firstOrNull { kotlin.math.hypot(it.x - hookX, it.y - hookY) < fishSize(it.id) * 0.45f }?.let {
                    hooked = it; swimmers.remove(it); state = State.REEL; t = 0f; SoundManager.playCatHit()
                }
            }
            State.REEL -> {
                // 물고기를 문 바늘이 물 위로 끌려 올라온다
                val waterY = gameView.screenHeight * 0.55f
                hookY -= 420f * deltaTime
                if (hookY <= waterY - 40f) {
                    val id = hooked?.id ?: "fish_small"
                    GameProgress.addItem(id)
                    catchId = id; hooked = null; swimmers.clear()
                    state = State.CAUGHT; t = 0f
                    SoundManager.playSpecial()
                }
            }
            else -> Unit
        }
    }

    private fun press() {
        when (state) {
            State.READY, State.CAUGHT, State.MISSED -> { state = State.CAST; t = 0f; catchId = null }
            State.CONTROL, State.REEL -> Unit   // 조종 중에는 손가락으로 바늘을 움직인다
            State.CAST, State.WAIT -> { state = State.MISSED; t = 0f }   // 너무 일찍 당기면 놓친다
            State.BITE -> {
                val r = Math.random()
                val id = when { r < 0.08 -> "fish_gold"; r < 0.40 -> "fish_big"; else -> "fish_small" }
                GameProgress.addItem(id)
                catchId = id
                state = State.CAUGHT; t = 0f
                SoundManager.playSpecial()
            }
        }
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        val waterY = h * 0.55f
        if (skyShader == null) skyShader = LinearGradient(0f, 0f, 0f, waterY, Color.rgb(120, 190, 245), Color.rgb(220, 240, 255), Shader.TileMode.CLAMP)
        sky.shader = skyShader
        canvas.drawRect(0f, 0f, w, waterY, sky)
        canvas.drawRect(0f, waterY, w, h, water)
        var wx = -((timer * 40f) % 160f)
        while (wx < w) { canvas.drawLine(wx, waterY + 40f, wx + 60f, waterY + 42f, waterDark); canvas.drawLine(wx + 80f, waterY + 140f, wx + 140f, waterY + 142f, waterDark); wx += 160f }

        // 왼쪽 나무 부두 + 인간 + 낚싯대
        val pierTop = waterY - 20f
        rect.set(0f, pierTop, w * 0.30f, pierTop + 40f)
        canvas.drawRect(rect, wood); canvas.drawRect(rect, ink)
        for (k in 1..3) { val px = w * 0.30f * k / 4f; canvas.drawRect(px - 12f, pierTop + 40f, px + 12f, h, wood); canvas.drawRect(px - 12f, pierTop + 40f, px + 12f, h, ink) }
        val hero = SpriteManager.playerIdle[0]
        val hh = h * 0.30f; val hw = hero.width * hh / hero.height
        val hx = w * 0.18f
        rect.set(hx - hw / 2f, pierTop - hh + 6f, hx + hw / 2f, pierTop + 6f)
        canvas.drawBitmap(hero, null, rect, null)
        // 낚싯대 끝 → 찌
        val rodBaseX = hx + hw * 0.25f; val rodBaseY = pierTop - hh * 0.55f
        val tipX = hx + w * 0.16f; val tipY = pierTop - hh * 1.05f
        canvas.drawLine(rodBaseX, rodBaseY, tipX, tipY, rod)
        val bobX = w * 0.62f
        val bob = when (state) {
            State.READY -> null
            State.CAST -> { val u = (t / 0.5f).coerceIn(0f, 1f); Pair(tipX + (bobX - tipX) * u, tipY + (waterY - tipY) * u - sin(u * Math.PI.toFloat()) * h * 0.15f) }
            State.WAIT -> Pair(bobX, waterY + sin(timer * 3f) * 4f)
            State.BITE -> Pair(bobX, waterY + 26f + sin(timer * 40f) * 6f)   // 쏙 들어가 흔들린다
            State.CONTROL, State.REEL -> null
            else -> Pair(bobX, waterY)
        }
        // 낚시줄 조종: **강 물속**(대표님 2026-10-16 「참고로 강 물속이야」) — 강바닥 모래·조약돌·수초, 흘러가는 물살, 보글보글 거품
        if (state == State.CONTROL || state == State.REEL) {
            drawRiverUnderwater(canvas, w, h, waterY)
            for (f in swimmers) drawFish(canvas, f.id, f.x, f.y, f.dir)
            hooked?.let { drawFish(canvas, it.id, hookX, hookY + fishSize(it.id) * 0.3f, it.dir) }
            path.reset(); path.moveTo(tipX, tipY); path.quadTo((tipX + hookX) / 2f, (tipY + hookY) / 2f - 40f, hookX, hookY)
            canvas.drawPath(path, line)
            rect.set(hookX - 14f, hookY - 4f, hookX + 14f, hookY + 24f)
            canvas.drawArc(rect, 0f, 200f, false, hookPaint)
            canvas.drawLine(hookX + 14f, hookY + 10f, hookX + 14f, hookY - 14f, hookPaint)
        }
        if (state == State.CONTROL || state == State.REEL) {
            // (줄은 위에서 그렸다)
        } else if (bob != null) {
            path.reset(); path.moveTo(tipX, tipY); path.quadTo((tipX + bob.first) / 2f, (tipY + bob.second) / 2f + 60f, bob.first, bob.second)
            canvas.drawPath(path, line)
            rect.set(bob.first - 14f, bob.second - 18f, bob.first + 14f, bob.second + 10f)
            canvas.drawArc(rect, 180f, 180f, true, bobberRed)
            canvas.drawArc(rect, 0f, 180f, true, bobberWhite)
            canvas.drawOval(rect, ink)
            if (state == State.BITE) {
                text.textSize = h * 0.12f; text.color = Color.rgb(230, 50, 50)
                canvas.drawText("!", bob.first, bob.second - 50f, text)
                text.color = Color.rgb(40, 36, 32)
            }
        } else {
            path.reset(); path.moveTo(tipX, tipY); path.lineTo(tipX, tipY + 60f); canvas.drawPath(path, line)
        }

        // 잡은 물고기
        if (state == State.CAUGHT) catchId?.let { id ->
            val icon = SpriteManager.shopIcons[id]
            val size = h * 0.24f * (0.5f + 0.5f * (t / 0.3f).coerceAtMost(1f))
            if (icon != null) {
                rect.set(w * 0.62f - size / 2f, h * 0.30f - size / 2f, w * 0.62f + size / 2f, h * 0.30f + size / 2f)
                canvas.drawBitmap(icon, null, rect, null)
            }
            big.textSize = h * 0.06f
            canvas.drawText("${NAMES[id]} 잡았다! (${GameProgress.itemCount(id)}마리)", w * 0.62f, h * 0.48f - size * 0.3f, big)
        }
        if (state == State.MISSED) {
            big.textSize = h * 0.06f
            canvas.drawText("앗, 놓쳤다!", w * 0.62f, h * 0.35f, big)
        }

        text.textSize = h * 0.065f
        canvas.drawText("낚시터", w / 2f, h * 0.10f, text)
        text.textSize = h * 0.03f
        canvas.drawText("찌가 쏙 들어가고 '!'가 뜨면 바로 당기기! · 물고기는 캣타워에서 간식으로", w / 2f, h * 0.16f, text)

        // 가진 물고기
        var ix = w * 0.04f
        for (id in listOf("fish_small", "fish_big", "fish_gold")) {
            val icon = SpriteManager.shopIcons[id] ?: continue
            rect.set(ix, h * 0.84f, ix + h * 0.09f, h * 0.84f + h * 0.09f)
            canvas.drawBitmap(icon, null, rect, null)
            text.textAlign = Paint.Align.LEFT; text.textSize = h * 0.035f
            canvas.drawText("×${GameProgress.itemCount(id)}", rect.right + 6f, rect.centerY() + h * 0.012f, text)
            text.textAlign = Paint.Align.CENTER
            ix += h * 0.22f
        }

        // 버튼
        val label = when (state) {
            State.READY -> "🎣 던지기"
            State.CAST, State.WAIT -> "기다리는 중…"
            State.BITE -> "당기기!"
            State.CAUGHT, State.MISSED -> "🎣 다시 던지기"
            State.CONTROL -> "손가락으로 바늘 움직이기"
            State.REEL -> "끌어 올리는 중!"
        }
        btnRect.set(w * 0.75f - 230f, h * 0.80f, w * 0.75f + 230f, h * 0.80f + 110f)
        canvas.drawRoundRect(btnRect, 55f, 55f, if (state == State.BITE) bitePaint else btnPaint)
        canvas.drawRoundRect(btnRect, 55f, 55f, ink)
        text.textSize = h * 0.048f
        canvas.drawText(label, btnRect.centerX(), btnRect.centerY() + h * 0.017f, text)
        // 낚시줄 조종하기 버튼 (기다리지 않을 때)
        controlRect.setEmpty()
        if (state == State.READY || state == State.CAUGHT || state == State.MISSED) {
            controlRect.set(w * 0.42f - 240f, h * 0.80f, w * 0.42f + 240f, h * 0.80f + 110f)
            canvas.drawRoundRect(controlRect, 55f, 55f, controlPaint); canvas.drawRoundRect(controlRect, 55f, 55f, ink)
            text.textSize = h * 0.042f
            canvas.drawText("🎮 낚시줄 조종하기", controlRect.centerX(), controlRect.centerY() + h * 0.015f, text)
        }
        exitRect.set(w - w * 0.03f - 240f, h * 0.04f, w - w * 0.03f, h * 0.04f + 90f)
        canvas.drawRoundRect(exitRect, 45f, 45f, card); canvas.drawRoundRect(exitRect, 45f, 45f, ink)
        text.textSize = 40f
        canvas.drawText("나가기", exitRect.centerX(), exitRect.centerY() + 14f, text)
    }

    private val deepWater = Paint().apply { color = Color.rgb(60, 130, 180) }
    private val sand = Paint().apply { color = Color.rgb(214, 196, 150) }
    private val pebble = Paint().apply { color = Color.rgb(150, 146, 140); isAntiAlias = true }
    private val weed = Paint().apply { color = Color.rgb(70, 160, 90); strokeWidth = 10f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE; isAntiAlias = true }
    private val current = Paint().apply { color = Color.argb(110, 200, 235, 255); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val bubble = Paint().apply { color = Color.argb(150, 230, 245, 255); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }

    /** 강 물속: 물빛을 깊게, 바닥에 모래·조약돌·흔들리는 수초, 오른쪽으로 흘러가는 물살, 올라가는 거품 */
    private fun drawRiverUnderwater(canvas: Canvas, w: Float, h: Float, waterY: Float) {
        canvas.drawRect(w * 0.30f, waterY + 8f, w, h, deepWater)
        val bed = h * 0.92f
        canvas.drawRect(w * 0.30f, bed, w, h, sand)
        for (k in 0 until 9) {
            val px = w * (0.33f + k * 0.075f); val r = 14f + (k * 37 % 5) * 4f
            canvas.drawOval(px - r, bed - r * 0.6f, px + r, bed + r * 0.5f, pebble)
        }
        for (k in 0 until 6) {
            val bx = w * (0.36f + k * 0.11f)
            path.reset(); path.moveTo(bx, bed)
            val sway = sin(timer * 2f + k) * 18f
            path.quadTo(bx + sway, bed - 70f, bx + sway * 1.6f, bed - 140f - (k % 3) * 30f)
            canvas.drawPath(path, weed)
        }
        // 물살: 흐르는 짧은 줄 (강은 오른쪽으로 흐른다)
        for (k in 0 until 10) {
            val y = waterY + 60f + (k * 53 % 9) * (h * 0.035f)
            val x = w * 0.30f + ((timer * 260f + k * 210f) % (w * 0.72f))
            canvas.drawLine(x, y, x + 70f, y, current)
        }
        for (k in 0 until 6) {
            val u = (timer * 0.4f + k * 0.17f) % 1f
            val bx = w * (0.4f + k * 0.1f) + sin(timer * 3f + k) * 8f
            canvas.drawCircle(bx, bed - u * (bed - waterY - 20f), 6f + k % 3 * 2f, bubble)
        }
    }

    private fun drawFish(canvas: Canvas, id: String, cx: Float, cy: Float, dir: Float) {
        val icon = SpriteManager.shopIcons[id] ?: return
        val bmp = if (dir > 0f) icon else SpriteManager.getFlipped(icon)
        val s = fishSize(id)
        rect.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f)
        canvas.drawBitmap(bmp, null, rect, null)
    }
    private val controlPaint = Paint().apply { color = Color.rgb(160, 220, 255); isAntiAlias = true }
    private val hookPaint = Paint().apply { color = Color.rgb(90, 90, 100); strokeWidth = 5f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val h = gameView.screenHeight.toFloat()
        // 조종 중: 물속을 누르거나 끌면 바늘이 그쪽으로
        if (state == State.CONTROL && (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE)) {
            if (exitRect.contains(event.x, event.y)) { gameView.showShop(); return true }
            hookTX = event.x.coerceIn(gameView.screenWidth * 0.3f, gameView.screenWidth * 0.98f)
            hookTY = event.y.coerceIn(h * 0.58f, h * 0.95f)
            return true
        }
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true
        if (exitRect.contains(event.x, event.y)) { gameView.showShop(); return true }
        if (!controlRect.isEmpty && controlRect.contains(event.x, event.y)) { startControl(); return true }
        // 버튼이나 물을 누르면 던지기/당기기 (당기기는 빨리 눌러야 해서 누르는 순간 반응)
        if (btnRect.contains(event.x, event.y) || event.y > gameView.screenHeight * 0.45f) press()
        return true
    }

    override fun onBack(): Boolean {
        gameView.showShop()
        return true
    }

    companion object {
        /** '!' 뜨고 당길 수 있는 시간 */
        private const val BITE_WINDOW = 1.0f
        /** 낚시줄 조종: 헤엄치는 물고기 수 · 바늘 빠르기 */
        private const val SWIMMERS = 5
        private const val HOOK_SPEED = 700f
        private val NAMES = mapOf("fish_small" to "작은 물고기", "fish_big" to "큰 물고기", "fish_gold" to "✨황금 물고기")
    }
}
