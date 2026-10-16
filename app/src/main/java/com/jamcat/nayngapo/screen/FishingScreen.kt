package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
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
 * **낚시줄이 심해까지 내려간다**(2026-10-16): 화면이 바늘을 따라 아래로 내려가고(깊이 3000), 깊을수록 물빛이 어두운 심해·빛나는 알갱이,
 * 맨 아래 바닥엔 바위. 깊은 곳일수록 큰 물고기·황금 물고기가 많다 (깊이·비율은 Claude 판단).
 * 포만감 = 작은 3 · 큰 6 · 황금 10 (대표님 2026-10-10). 확률·시간은 Claude 판단 · 낚시 한 번 루비 2(대표님) (작은 물고기 60% · 큰 물고기 32% · 황금 물고기 8%).
 */
class FishingScreen(private val gameView: GameView) : Screen {

    private enum class State { READY, CAST, WAIT, BITE, CAUGHT, MISSED, CONTROL, REEL }

    // ===== 낚시줄 조종하기 =====
    private class Swimmer(val id: String, var x: Float, val y: Float, var dir: Float, val speed: Float)
    private val swimmers = mutableListOf<Swimmer>()
    /** 바늘에 물린 물고기들 — 낚싯대 Lv 만큼 물 때까지 내려간다 */
    private val hookedList = mutableListOf<Swimmer>()
    private var reelCount = 0
    private var hookX = 0f
    private var hookY = 0f
    private var hookTX = 0f
    private var hookTY = 0f
    private val controlRect = RectF()
    /** 배를 타고 바다로 나왔다 (대표님 2026-10-17 「낚시터에는 배를 탈 수 있어, 5루비를 주면 타고 낚시를 갈 수 있어」) —
     *  배 위에서는 얕은 곳부터 바다 생물이 나오고 던지기로도 절반은 바다 생물이 잡힌다. 한 번 타면 '돌아오기'까지 (Claude 판단) */
    private var onBoat = false
    private val boatRect = RectF()

    // ===== 배 조종 · 해적선 (대표님 2026-10-17 「배는 일반적인 어선」「가끔 해적선도 나와서 공격하고, 배를 조종할 수 있어」) =====
    /** 배(인간)의 화면 x — ◀ ▶ 로 바다 위를 오간다 */
    private var boatX = -1f
    private var steer = 0f
    /** 배 위에서 걸어 다닌다 (대표님 2026-10-17 「배 위에서 걸어다닐 수 있고, 조타실에 가야만 조종할 수 있어」):
     *  heroU = 배 그림 가로 비율로 본 인간 자리. 앞 갑판(DECK_MIN~DECK_MAX)을 ◀ ▶ 로 걷고, 조타실 문 가까이에서 '조타실'에 들어가면
     *  ◀ ▶ 가 배를 움직인다. 조타실에서는 낚시를 못 한다 (Claude 판단) */
    private var heroU = BOAT_HERO_U
    private var atHelm = false
    private var walkDir = 1f
    private var walking = false
    private val helmRect = RectF()
    private val leftRect = RectF()
    private val rightRect = RectF()
    /** 배 튼튼함 — 대포알에 3번 맞으면 가라앉아 부두로 돌아온다 (Claude 판단) */
    private var boatHp = BOAT_HP
    private var hurtT = 0f
    /** 다음 해적선이 올 때까지 (배를 탄 동안만 흐른다) */
    private var pirateWait = 0f
    /** 해적선 x (NaN = 없음) · 머무는 시간 · 다음 대포까지 */
    private var pirateX = Float.NaN
    private var pirateStay = 0f
    private var pirateFire = 0f
    private var pirateLeaving = false
    private class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float)
    private val balls = mutableListOf<Ball>()
    private class Splash(val x: Float, val y: Float, var t: Float, val boom: Boolean)
    private val splashes = mutableListOf<Splash>()

    private fun boardBoat() {
        onBoat = true; boatHp = BOAT_HP; boatX = gameView.screenWidth * 0.24f; heroU = BOAT_HERO_U; atHelm = false
        pirateX = Float.NaN; balls.clear(); pirateWait = PIRATE_FIRST + (Math.random() * PIRATE_EVERY).toFloat()
    }

    private fun updateSea(dt: Float) {
        val w = gameView.screenWidth.toFloat(); val h = gameView.screenHeight.toFloat()
        val waterY = h * 0.55f
        if (boatX < 0f) boatX = w * 0.24f
        if (hurtT > 0f) hurtT -= dt
        for (s in splashes) s.t += dt
        splashes.removeAll { it.t > 0.7f }
        if (!onBoat) { pirateX = Float.NaN; balls.clear(); return }
        // 물속을 보는 동안(낚시줄 조종)에는 바다 위가 멈춘다
        if (state == State.CONTROL || state == State.REEL) return
        walking = false
        if (atHelm) boatX = (boatX + steer * BOAT_SPEED * dt).coerceIn(w * 0.22f, w * 0.60f)
        else if (steer != 0f) {
            heroU = (heroU + steer * WALK_U * dt).coerceIn(DECK_MIN, DECK_MAX)
            walking = true; walkDir = steer
        }
        val shipW = shipWidth(h)
        if (pirateX.isNaN()) {
            pirateWait -= dt
            if (pirateWait <= 0f) {
                pirateX = w + 20f; pirateStay = PIRATE_STAY; pirateFire = 1.2f; pirateLeaving = false
                noticeText = "🏴‍☠️ 해적선이다! ◀ ▶ 로 배를 움직여 대포알을 피해요!"; noticeTimer = 2.5f
                SoundManager.playPlayerHurt()
            }
        } else {
            val stopX = w - shipW * 0.95f
            if (pirateLeaving) {
                pirateX += 260f * dt
                if (pirateX > w + 40f && balls.isEmpty()) {
                    pirateX = Float.NaN; pirateWait = PIRATE_EVERY * (0.6f + Math.random().toFloat() * 0.8f)
                    noticeText = "해적선이 물러갔다!"; noticeTimer = 1.6f
                }
            } else {
                if (pirateX > stopX) pirateX = (pirateX - 300f * dt).coerceAtLeast(stopX)
                else {
                    pirateStay -= dt; pirateFire -= dt
                    if (pirateFire <= 0f) {
                        // 뱃전의 대포 구멍에서 배를 겨누고 쏜다 (조금씩 빗나가게)
                        val sx = pirateX + shipW * 0.20f; val sy = waterY - shipHeight(h) * 0.30f
                        val tx = boatCenter(w) + (Math.random().toFloat() - 0.5f) * w * 0.16f
                        val tt = 1.5f
                        balls.add(Ball(sx, sy, (tx - sx) / tt, (waterY - 30f - sy) / tt - 0.5f * BALL_G * tt))
                        pirateFire = PIRATE_FIRE_GAP
                        SoundManager.playSpecial()
                    }
                    if (pirateStay <= 0f) pirateLeaving = true
                }
            }
        }
        val it = balls.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.vy += BALL_G * dt; b.x += b.vx * dt; b.y += b.vy * dt
            if (b.vy > 0f && b.y >= waterY - 30f) {
                if (kotlin.math.abs(b.x - boatCenter(w)) < w * 0.16f && hurtT <= 0f) {
                    boatHp--; hurtT = 0.8f
                    splashes.add(Splash(b.x, waterY - 40f, 0f, true))
                    SoundManager.playPlayerHurt()
                    if (boatHp <= 0) {
                        onBoat = false; pirateX = Float.NaN; balls.clear()
                        if (state != State.CONTROL && state != State.REEL) { state = State.READY; catchId = null }
                        noticeText = "배가 가라앉았다! 겨우 부두로 돌아왔어요"; noticeTimer = 2.5f
                        return
                    }
                    noticeText = "쾅! 배가 맞았다 (배 ♥ $boatHp)"; noticeTimer = 1.2f
                } else splashes.add(Splash(b.x, waterY, 0f, false))
                it.remove()
            }
        }
    }

    private fun boatWidth(w: Float) = w * 0.36f
    /** 배 그림 왼쪽 끝 (boatX = 배가 처음 설 때 인간 자리 기준) */
    private fun boatLeft(w: Float) = boatX - boatWidth(w) * BOAT_HERO_U
    private fun boatCenter(w: Float) = boatLeft(w) + boatWidth(w) / 2f

    private fun shipHeight(h: Float) = h * 0.46f
    private fun shipWidth(h: Float) = shipHeight(h) * SpriteManager.pirateShip.width / SpriteManager.pirateShip.height

    private val ballPaint = Paint().apply { color = Color.rgb(40, 40, 46); isAntiAlias = true }
    private val splashPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val boomPaint = Paint().apply { color = Color.rgb(255, 170, 40); isAntiAlias = true }
    private val steerPaint = Paint().apply { color = Color.argb(200, 255, 252, 244); isAntiAlias = true }
    private val heartPaint = Paint().apply { color = Color.rgb(230, 60, 70); textAlign = Paint.Align.LEFT; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }

    /** 바다 위: 해적선(해적냥이가 타고 있다)·대포알·물보라 */
    private fun drawSea(canvas: Canvas, w: Float, h: Float, waterY: Float) {
        if (!pirateX.isNaN()) {
            val sh = shipHeight(h); val sw = shipWidth(h)
            val bob = sin(timer * 1.6f) * 8f
            val top = waterY - sh * 0.68f + bob
            // 해적냥이가 갑판 위에서 이쪽을 본다
            val pc = SpriteManager.getFlipped(SpriteManager.pirateCat)
            val ch = sh * 0.30f; val cw = pc.width * ch / pc.height
            val cx = pirateX + sw * 0.30f
            rect.set(cx - cw / 2f, top + sh * 0.66f - ch, cx + cw / 2f, top + sh * 0.66f)
            canvas.drawBitmap(pc, null, rect, null)
            rect.set(pirateX, top, pirateX + sw, top + sh)
            canvas.drawBitmap(SpriteManager.pirateShip, null, rect, null)
        }
        for (b in balls) canvas.drawCircle(b.x, b.y, 16f, ballPaint)
        for (s in splashes) {
            val u = s.t / 0.7f
            if (s.boom) { boomPaint.alpha = ((1f - u) * 255).toInt(); canvas.drawCircle(s.x, s.y, 40f + u * 70f, boomPaint) }
            else { splashPaint.alpha = ((1f - u) * 230).toInt(); for (k in -2..2) canvas.drawCircle(s.x + k * 18f, s.y - (1f - kotlin.math.abs(k) * 0.3f) * u * 120f, 12f, splashPaint) }
        }
    }

    /** 어선: 흰 배에 파란 줄, 조타실 (그림이 있으면 그림) — 인간은 앞 갑판에 서 있다 */
    private fun drawFishingBoat(canvas: Canvas, w: Float, h: Float, deckY: Float) {
        val bmp = SpriteManager.fishingBoat
        val bw = boatWidth(w); val bh = bw * bmp.height / bmp.width
        val left = boatLeft(w)
        rect.set(left, deckY - bh * BOAT_DECK_V, left + bw, deckY - bh * BOAT_DECK_V + bh)
        if (hurtT > 0f && ((hurtT * 12f).toInt() % 2 == 0)) return   // 맞으면 깜빡
        canvas.drawBitmap(bmp, null, rect, null)
    }
    private val lvRect = RectF()
    private var noticeText = ""
    private var noticeTimer = 0f
    /** 물속을 내려다보는 화면 높이 (바늘을 따라 심해까지 내려간다) */
    private var camY = 0f
    /** 낚시줄 길이 = 내려가는 깊이: Lv1 1500 → Lv 마다 두 배 (Lv4 12000) */
    private val depth: Float get() = BASE_DEPTH * 2f   // 깊이는 3000 그대로 (Lv 은 잡는 마릿수)
    /** 가라앉는 빠르기도 Lv 마다 두 배 — 바닥까지 걸리는 시간은 같다 */
    private val sinkSpeed: Float get() = SINK_SPEED * 2f
    private val seabedY: Float get() = gameView.screenHeight * 0.55f + depth + gameView.screenHeight * 0.35f

    private fun randomFishId(): String {
        val r = Math.random()
        return when { r < 0.08 -> "fish_gold"; r < 0.40 -> "fish_big"; else -> "fish_small" }
    }

    /**
     * 바다에는 바다 생물이 산다 (대표님 2026-10-16): 깊이 SEA_START 아래는 바다 — 바다 생물이 70%, 물고기가 30%.
     * 해파리·고래·광어·돔·쓰레기, 그리고 불가사리·조개·우럭·거북이·상어(2026-10-16 추가). **고래는 아주 크다.**
     * 비율은 Claude 판단: 쓰레기 18 · 광어 14 · 돔 12 · 우럭 12 · 조개 12 · 불가사리 10 · 해파리 9 · 거북이 6 · 상어 4 · 고래 3.
     */
    private fun seaCreature(): String {
        // 황금 미끼면 거북이·상어·고래가 세 배 잘 나온다
        val table = SEA_TABLE.map { (id, wgt) -> id to (if (bait == "bait_gold" && id in RARE_SEA) wgt * 3 else wgt) }
        val total = table.sumOf { it.second }.toDouble()
        val rr = Math.random() * total
        var acc = 0.0
        for ((id, wgt) in table) { acc += wgt; if (rr < acc) return id }
        return "sea_trash"
    }

    /** 잡았다: 가방에 넣고, 처음 잡은 종류면 루비 50 */
    private fun onCatch(id: String) {
        GameProgress.addItem(id)
        catchId = id
        firstCatch = GameProgress.recordCatch(id)
        if (firstCatch) { noticeText = "처음 잡았다! 루비 +${GameProgress.FIRST_CATCH_RUBIES}"; noticeTimer = 2.5f }
    }
    private var firstCatch = false

    /** 깊이(0 얕음 → 1 심해)에 따라: 깊을수록 큰 물고기·황금 물고기가 많다 */
    private fun fishIdAtDepth(d: Float): String {
        val r = Math.random()
        // 미끼: 새우 = 큰 물고기가 잘, 황금 = 황금 물고기가 잘
        val gold = 0.05 + 0.30 * d + (if (bait == "bait_gold") 0.25 else 0.0)
        val bigger = gold + 0.25 + 0.30 * d + (if (bait == "bait_shrimp") 0.30 else 0.0)
        return when { r < gold -> "fish_gold"; r < bigger -> "fish_big"; else -> "fish_small" }
    }

    /** 낚시는 한 번에 루비 2개를 내고 한다 (대표님 2026-10-16) — 던지기·낚시줄 조종하기 둘 다 */
    private fun payForFishing(): Boolean {
        if (!GameProgress.spendRubies(FISHING_PRICE)) {
            noticeText = "낚시는 루비 ${FISHING_PRICE}개를 내고 해요 (지금 ${GameProgress.rubies}개)"; noticeTimer = 2f
            return false
        }
        // 미끼: 가진 것 중 좋은 것부터 하나 쓴다 (낚시 상점, 2026-10-17)
        bait = listOf("bait_gold", "bait_shrimp", "bait_worm").firstOrNull { GameProgress.consumeItem(it) }
        return true
    }
    /** 이번 낚시에 쓴 미끼 (없으면 null) */
    private var bait: String? = null
    /** 바늘이 바닥에 박혔다 — 못 움직인다 (대표님 2026-10-17 「낚싯대는 땅에 박히면 못 움직여」) */
    private var stuck = false
    private var stuckT = 0f

    /** 낚시줄을 물속으로 내리고 물고기들을 헤엄치게 한다 */
    private fun startControl() {
        val w = gameView.screenWidth.toFloat(); val h = gameView.screenHeight.toFloat()
        val waterY = h * 0.55f
        state = State.CONTROL; t = 0f; catchId = null; hookedList.clear(); stuck = false; stuckT = 0f
        hookX = w * 0.62f; hookY = waterY + 60f; hookTX = hookX; hookTY = hookY
        swimmers.clear()
        val bottom = seabedY - 60f
        repeat(SWIMMERS) { k ->
            // 얕은 곳부터 심해까지 고르게 흩어 놓는다
            val y = waterY + 90f + (bottom - waterY - 90f) * ((k + Math.random().toFloat()) / SWIMMERS)
            // 깊이 비율은 가장 긴 낚시줄(Lv4) 기준 — 줄이 길수록 더 깊은 곳의 좋은 물고기를 만난다
            val d = ((y - waterY) / (BASE_DEPTH * 8f)).coerceIn(0f, 1f)
            val inSea = (onBoat || y - waterY > SEA_START) && Math.random() < 0.7   // 배를 타면 얕은 곳도 바다
            val id = if (inSea) seaCreature() else fishIdAtDepth(d)
            // 광어는 바닥에 붙어 있다 — 바닥에서 아주 천천히 기어 다닌다 (대표님 2026-10-17)
            val onFloor = id == "sea_flounder"
            swimmers.add(Swimmer(id, w * (0.35f + Math.random().toFloat() * 0.6f), if (onFloor) seabedY - 30f else y,
                if (Math.random() < 0.5) 1f else -1f, if (onFloor) 12f else 60f + Math.random().toFloat() * 90f))
        }
    }

    private fun fishSize(id: String) = when (id) {
        "fish_big" -> 120f; "fish_gold" -> 90f; "sea_whale" -> 520f; "sea_jelly" -> 100f   // 고래는 아주 크다
        "sea_flounder" -> 110f; "sea_bream" -> 105f; "sea_trash" -> 90f
        "sea_starfish" -> 80f; "sea_clam" -> 75f; "sea_rockfish" -> 105f; "sea_turtle" -> 150f; "sea_shark" -> 260f
        else -> 75f
    }
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
        if (noticeTimer > 0f) noticeTimer -= deltaTime
        updateSea(deltaTime)
        // 화면이 바늘을 따라 물속으로 내려간다 (조종할 때만)
        val h = gameView.screenHeight.toFloat()
        val camTarget = if (state == State.CONTROL || state == State.REEL) (hookY - h * 0.55f).coerceIn(0f, seabedY - h * 0.92f) else 0f
        camY += (camTarget - camY) * (deltaTime * 5f).coerceAtMost(1f)
        when (state) {
            State.CAST -> if (t >= 0.5f) { state = State.WAIT; t = 0f; waitTime = if (bait == "bait_worm") 1f + Math.random().toFloat() else 2f + (Math.random() * 3f).toFloat() }
            State.WAIT -> if (t >= waitTime) { state = State.BITE; t = 0f; SoundManager.playCatHit() }
            State.BITE -> if (t >= BITE_WINDOW) { state = State.MISSED; t = 0f }
            State.CONTROL -> {
                val w = gameView.screenWidth.toFloat()
                for (f in swimmers) {
                    if (bait == "bait_worm" && kotlin.math.abs(f.y - hookY) < 500f) f.dir = if (hookX > f.x) 1f else -1f   // 지렁이 냄새에 모인다
                    f.x += f.dir * f.speed * deltaTime
                    if (f.x < w * 0.33f || f.x > w * 0.97f) { f.dir = -f.dir; f.x = f.x.coerceIn(w * 0.33f, w * 0.97f) }
                }
                // 바늘은 물고기가 잡힐 때까지 저절로 가라앉는다 (대표님 2026-10-16 「심해까지 내려가, 물고기가 잡힐 때까지」) — 좌우는 손가락으로
                hookTY = (hookTY + sinkSpeed * deltaTime).coerceAtMost(seabedY - 30f)
                val dx = hookTX - hookX; val dy = hookTY - hookY
                val d = kotlin.math.hypot(dx, dy)
                val step = HOOK_SPEED * deltaTime
                if (d > step) { hookX += dx / d * step; hookY += dy / d * step } else { hookX = hookTX; hookY = hookTY }
                // 바늘이 물고기에 닿으면 문다 — 낚싯대 Lv 만큼 물면(또는 바닥에 닿으면) 끌어 올린다 (2026-10-17)
                val cap = GameProgress.catchesPerCast(SWIMMERS)
                swimmers.firstOrNull { kotlin.math.hypot(it.x - hookX, it.y - hookY) < fishSize(it.id) * 0.45f }?.let {
                    hookedList.add(it); swimmers.remove(it); SoundManager.playCatHit()
                }
                val atBottom = hookY >= seabedY - 40f
                // 낚싯대가 땅에 박히면 못 움직인다 — 물린 게 있으면 그대로 끌어 올리고, 없으면 잠깐 뒤 놓친다
                if (atBottom && !stuck) { stuck = true; stuckT = 0f; hookTX = hookX }
                if (stuck) {
                    stuckT += deltaTime
                    if (hookedList.isEmpty() && stuckT >= 1.5f) {
                        state = State.MISSED; t = 0f; swimmers.clear()
                        noticeText = "바늘이 땅에 박혔다…"; noticeTimer = 2f
                    }
                }
                if (hookedList.size >= cap || (stuck && hookedList.isNotEmpty()) || (hookedList.isNotEmpty() && swimmers.isEmpty())) { state = State.REEL; t = 0f }
            }
            State.REEL -> {
                // 물고기를 문 바늘이 물 위로 끌려 올라온다
                val waterY = gameView.screenHeight * 0.55f
                hookY -= 900f * deltaTime   // 심해에서 끌어 올린다
                if (hookY <= waterY - 40f) {
                    for (f in hookedList) onCatch(f.id)
                    reelCount = hookedList.size
                    hookedList.clear(); swimmers.clear()
                    state = State.CAUGHT; t = 0f
                    SoundManager.playSpecial()
                }
            }
            else -> Unit
        }
    }

    private fun press() {
        when (state) {
            State.READY, State.CAUGHT, State.MISSED -> {
                if (!payForFishing()) return
                state = State.CAST; t = 0f; catchId = null
            }
            State.CONTROL, State.REEL -> Unit   // 조종 중에는 손가락으로 바늘을 움직인다
            State.CAST, State.WAIT -> { state = State.MISSED; t = 0f }   // 너무 일찍 당기면 놓친다
            State.BITE -> {
                // 낚싯대 Lv 만큼 한꺼번에 잡는다 (한 번에 최대 CAST_CAP 마리)
                reelCount = GameProgress.catchesPerCast(CAST_CAP)
                repeat(reelCount) {
                        val id = if (onBoat && Math.random() < 0.5) seaCreature()   // 배 위에서는 바다 생물도 문다
                        else fishIdAtDepth(0.1f)   // 물 위에서 문 물고기 (미끼 효과가 들어간다)
                    onCatch(id)
                }
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
        canvas.save()
        canvas.translate(0f, -camY)
        canvas.drawRect(0f, -h, w, waterY, sky)
        canvas.drawRect(0f, waterY, w, h + camY, water)
        if (camY > 1f || state == State.CONTROL || state == State.REEL) {
            // 얕은 강물 → 깊은 심해로 점점 어두워진다
            if (deepShader == null) deepShader = LinearGradient(0f, waterY, 0f, seabedY,
                intArrayOf(Color.rgb(90, 170, 230), Color.rgb(40, 90, 160), Color.rgb(12, 24, 60)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            deepPaint.shader = deepShader
            canvas.drawRect(0f, waterY, w, seabedY + h, deepPaint)
        }
        var wx = -((timer * 40f) % 160f)
        while (wx < w) { canvas.drawLine(wx, waterY + 40f, wx + 60f, waterY + 42f, waterDark); canvas.drawLine(wx + 80f, waterY + 140f, wx + 140f, waterY + 142f, waterDark); wx += 160f }

        // 왼쪽 나무 부두(또는 바다 위 작은 배) + 인간 + 낚싯대
        val boatBob = if (onBoat) sin(timer * 2f) * 6f else 0f
        val pierTop = waterY - 20f + boatBob
        val hero = SpriteManager.playerIdle[0]
        val hh = h * 0.30f; val hw = hero.width * hh / hero.height
        val hx = if (onBoat && boatX > 0f) boatLeft(w) + boatWidth(w) * (if (atHelm) HELM_U else heroU) else w * 0.18f
        // 인간 발 높이: 부두 위 / 어선 갑판 (어선은 물에 잠기게 조금 낮다)
        val footY = if (onBoat) pierTop + 110f else pierTop + 6f
        if (!onBoat) {
            rect.set(0f, pierTop, w * 0.30f, pierTop + 40f)
            canvas.drawRect(rect, wood); canvas.drawRect(rect, ink)
            for (k in 1..3) { val px = w * 0.30f * k / 4f; canvas.drawRect(px - 12f, pierTop + 40f, px + 12f, h, wood); canvas.drawRect(px - 12f, pierTop + 40f, px + 12f, h, ink) }
            rect.set(hx - hw / 2f, pierTop - hh + 6f, hx + hw / 2f, pierTop + 6f)
            canvas.drawBitmap(hero, null, rect, null)
        } else {
            // 어선: 인간은 앞 갑판에 서 있고(다리는 뱃전이 가린다) 물결 따라 출렁인다 · ◀ ▶ 로 움직인다
            // 걸을 때는 걷는 그림 (왼쪽으로 가면 뒤집어서) · 조타실 안에서는 조타실 벽이 가린다
            val body = if (walking) SpriteManager.playerWalk[((timer * 10f).toInt()) % SpriteManager.playerWalk.size] else hero
            val bmp = if (walking && walkDir < 0f) SpriteManager.getFlipped(body) else body
            rect.set(hx - hw / 2f, footY - hh, hx + hw / 2f, footY)
            canvas.drawBitmap(bmp, null, rect, null)
            drawFishingBoat(canvas, w, h, footY)
            if (atHelm) { text.textSize = h * 0.05f; canvas.drawText("🛞", hx, footY - hh * 1.05f, text) }
        }
        if (state != State.CONTROL && state != State.REEL) drawSea(canvas, w, h, waterY)
        // 낚싯대 끝 → 찌
        val rodBaseX = hx + hw * 0.25f; val rodBaseY = footY - hh * 0.61f
        val tipX = hx + w * 0.16f; val tipY = footY - hh * 1.11f
        if (!(onBoat && atHelm)) canvas.drawLine(rodBaseX, rodBaseY, tipX, tipY, rod)
        val bobX = (tipX + w * 0.28f).coerceAtMost(w * 0.95f)
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
            for ((k, f) in hookedList.withIndex()) drawFish(canvas, f.id, hookX + (k % 3 - 1) * 30f, hookY + fishSize(f.id) * 0.3f + k * 40f, f.dir)
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
                canvas.drawTr("!", bob.first, bob.second - 50f, text)
                text.color = Color.rgb(40, 36, 32)
            }
        } else {
            path.reset(); path.moveTo(tipX, tipY); path.lineTo(tipX, tipY + 60f); canvas.drawPath(path, line)
        }

        canvas.restore()   // 여기까지 물속(camY)과 함께 움직인다

        // 잡은 물고기
        if (state == State.CAUGHT) catchId?.let { id ->
            val icon = SpriteManager.shopIcons[id]
            val size = h * 0.24f * (0.5f + 0.5f * (t / 0.3f).coerceAtMost(1f))
            if (icon != null) {
                rect.set(w * 0.62f - size / 2f, h * 0.30f - size / 2f, w * 0.62f + size / 2f, h * 0.30f + size / 2f)
                canvas.drawBitmap(icon, null, rect, null)
            }
            big.textSize = h * 0.06f
            canvas.drawTr(if (reelCount > 1) "${reelCount}마리 잡았다! (마지막 ${NAMES[id]})" else "${NAMES[id]} 잡았다! (${GameProgress.itemCount(id)}마리)", w * 0.62f, h * 0.48f - size * 0.3f, big)
        }
        if (state == State.MISSED) {
            big.textSize = h * 0.06f
            canvas.drawTr("앗, 놓쳤다!", w * 0.62f, h * 0.35f, big)
        }

        text.textSize = h * 0.065f
        canvas.drawTr("낚시터", w / 2f, h * 0.10f, text)
        text.textSize = h * 0.03f
        canvas.drawTr("찌가 쏙 들어가고 '!'가 뜨면 바로 당기기! · 물고기는 캣타워에서 간식으로", w / 2f, h * 0.16f, text)

        // 가진 물고기·바다 생물 (왼쪽 위 한 줄 — 아래 버튼과 안 겹치게, 한 번도 안 잡은 건 흐리게)
        for ((n, id) in CATCH_IDS.withIndex()) {
            val icon = SpriteManager.shopIcons[id] ?: continue
            // 7개씩 두 줄
            val ix = w * 0.03f + (n % 7) * h * 0.11f
            val iy = h * 0.19f + (n / 7) * h * 0.075f
            rect.set(ix, iy, ix + h * 0.06f, iy + h * 0.06f)
            canvas.drawBitmap(icon, null, rect, if (GameProgress.itemCount(id) > 0) null else dimIcon)
            text.textAlign = Paint.Align.LEFT; text.textSize = h * 0.028f
            canvas.drawTr("×${GameProgress.itemCount(id)}", rect.right + 4f, rect.centerY() + h * 0.01f, text)
            text.textAlign = Paint.Align.CENTER
        }

        // 버튼
        val label = when (state) {
            State.READY -> "🎣 던지기 · 루비 $FISHING_PRICE"
            State.CAST, State.WAIT -> "기다리는 중…"
            State.BITE -> "당기기!"
            State.CAUGHT, State.MISSED -> "🎣 다시 던지기 · 루비 $FISHING_PRICE"
            State.CONTROL -> "↔ 바늘 움직이기 (내려가는 중)"
            State.REEL -> "끌어 올리는 중!"
        }
        btnRect.set(w * 0.75f - 230f, h * 0.80f, w * 0.75f + 230f, h * 0.80f + 110f)
        canvas.drawRoundRect(btnRect, 55f, 55f, if (state == State.BITE) bitePaint else btnPaint)
        canvas.drawRoundRect(btnRect, 55f, 55f, ink)
        text.textSize = h * 0.048f
        canvas.drawTr(label, btnRect.centerX(), btnRect.centerY() + h * 0.017f, text)
        // 배 타기 / 돌아오기 (왼쪽 아래, 낚시줄 조종 버튼 왼쪽 — 기다리지 않을 때만)
        boatRect.setEmpty()
        if (state == State.READY || state == State.CAUGHT || state == State.MISSED) {
            boatRect.set(w * 0.04f, h * 0.80f, w * 0.04f + 400f, h * 0.80f + 110f)
            canvas.drawRoundRect(boatRect, 55f, 55f, if (onBoat) btnPaint else controlPaint); canvas.drawRoundRect(boatRect, 55f, 55f, ink)
            text.textSize = h * 0.04f
            canvas.drawTr(if (onBoat) "🚤 돌아오기" else "🚤 어선 타기 · 루비 $BOAT_PRICE", boatRect.centerX(), boatRect.centerY() + h * 0.014f, text)
        }
        // 낚시줄 Lv업 (오른쪽 위, 나가기 아래)
        // 낚싯대 Lv·미끼 표시 (Lv업·미끼는 낚시 상점에서)
        lvRect.set(w - w * 0.03f - 420f, h * 0.04f + 110f, w - w * 0.03f, h * 0.04f + 200f)
        canvas.drawRoundRect(lvRect, 45f, 45f, card); canvas.drawRoundRect(lvRect, 45f, 45f, ink)
        text.textSize = 30f
        val lv = GameProgress.fishingLineLevel
        val baitNow = listOf("bait_gold" to "황금", "bait_shrimp" to "새우", "bait_worm" to "지렁이").firstOrNull { GameProgress.itemCount(it.first) > 0 }
        canvas.drawTr("낚싯대 Lv $lv (${lv}마리씩) · 미끼 ${baitNow?.let { "${it.second} ${GameProgress.itemCount(it.first)}" } ?: "없음"}",
            lvRect.centerX(), lvRect.centerY() + 11f, text)
        if (noticeTimer > 0f) {
            text.textSize = 38f
            val nw = text.measureTr(noticeText) + 60f
            rect.set(w / 2f - nw / 2f, h * 0.22f, w / 2f + nw / 2f, h * 0.22f + 76f)
            canvas.drawRoundRect(rect, 24f, 24f, card); canvas.drawRoundRect(rect, 24f, 24f, ink)
            canvas.drawTr(noticeText, w / 2f, rect.centerY() + 13f, text)
        }
        // 낚시줄 조종하기 버튼 (기다리지 않을 때)
        controlRect.setEmpty()
        if (state == State.READY || state == State.CAUGHT || state == State.MISSED) {
            controlRect.set(w * 0.42f - 240f, h * 0.80f, w * 0.42f + 240f, h * 0.80f + 110f)
            canvas.drawRoundRect(controlRect, 55f, 55f, controlPaint); canvas.drawRoundRect(controlRect, 55f, 55f, ink)
            text.textSize = h * 0.042f
            canvas.drawTr("🎮 낚시줄 조종 · 루비 $FISHING_PRICE", controlRect.centerX(), controlRect.centerY() + h * 0.015f, text)
        }
        // 배 조종 ◀ ▶ (배를 탄 동안, 물속을 볼 때 빼고) + 배 튼튼함
        leftRect.setEmpty(); rightRect.setEmpty()
        if (onBoat && state != State.CONTROL && state != State.REEL) {
            leftRect.set(w * 0.04f, h * 0.62f, w * 0.04f + 150f, h * 0.62f + 130f)
            rightRect.set(leftRect.right + 30f, leftRect.top, leftRect.right + 180f, leftRect.bottom)
            for ((r, s) in listOf(leftRect to "◀", rightRect to "▶")) {
                canvas.drawRoundRect(r, 40f, 40f, steerPaint); canvas.drawRoundRect(r, 40f, 40f, ink)
                text.textSize = h * 0.07f; canvas.drawTr(s, r.centerX(), r.centerY() + h * 0.025f, text)
            }
            // 조타실 들어가기/나오기 (조타실 문 가까이에서만 들어갈 수 있다)
            helmRect.setEmpty()
            if (atHelm || heroU <= DECK_MIN + 0.04f) {
                helmRect.set(leftRect.left, leftRect.top - 120f, rightRect.right, leftRect.top - 20f)
                canvas.drawRoundRect(helmRect, 40f, 40f, if (atHelm) btnPaint else steerPaint); canvas.drawRoundRect(helmRect, 40f, 40f, ink)
                text.textSize = h * 0.036f
                canvas.drawTr(if (atHelm) "🚶 갑판으로" else "🛞 조타실", helmRect.centerX(), helmRect.centerY() + h * 0.013f, text)
            }
            text.textSize = h * 0.028f
            canvas.drawTr(if (atHelm) "◀ ▶ 배 조종" else "◀ ▶ 걷기", (leftRect.left + rightRect.right) / 2f, leftRect.bottom + h * 0.035f, text)
            heartPaint.textSize = h * 0.045f
            canvas.drawTr("배 " + "♥".repeat(boatHp.coerceAtLeast(0)) + "♡".repeat((BOAT_HP - boatHp).coerceAtLeast(0)), rightRect.right + 30f, rightRect.centerY() + h * 0.015f, heartPaint)
        }
        exitRect.set(w - w * 0.03f - 240f, h * 0.04f, w - w * 0.03f, h * 0.04f + 90f)
        canvas.drawRoundRect(exitRect, 45f, 45f, card); canvas.drawRoundRect(exitRect, 45f, 45f, ink)
        text.textSize = 40f
        canvas.drawTr("나가기", exitRect.centerX(), exitRect.centerY() + 14f, text)
    }

    private val deepWater = Paint().apply { color = Color.rgb(60, 130, 180) }
    private val sand = Paint().apply { color = Color.rgb(214, 196, 150) }
    private val pebble = Paint().apply { color = Color.rgb(150, 146, 140); isAntiAlias = true }
    private val weed = Paint().apply { color = Color.rgb(70, 160, 90); strokeWidth = 10f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE; isAntiAlias = true }
    private val current = Paint().apply { color = Color.argb(110, 200, 235, 255); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val bubble = Paint().apply { color = Color.argb(150, 230, 245, 255); style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }

    private val deepPaint = Paint()
    private var deepShader: Shader? = null
    private val glow = Paint().apply { color = Color.argb(170, 160, 255, 230); isAntiAlias = true }
    private val rock = Paint().apply { color = Color.rgb(60, 60, 72); isAntiAlias = true }

    /** 강 물속 → 심해: 얕은 곳엔 흐르는 물살·수초, 깊은 곳엔 빛나는 알갱이, 맨 아래 바닥엔 바위 (월드 좌표, camY 로 옮겨 그린다) */
    private fun drawRiverUnderwater(canvas: Canvas, w: Float, h: Float, waterY: Float) {
        val bed = seabedY
        // 얕은 강: 물살 (오른쪽으로 흐른다)
        for (k in 0 until 10) {
            val y = waterY + 60f + (k * 53 % 9) * (h * 0.04f)
            val x = w * 0.30f + ((timer * 260f + k * 210f) % (w * 0.72f))
            canvas.drawLine(x, y, x + 70f, y, current)
        }
        // 강가 수초 (얕은 곳 양옆)
        for (k in 0 until 4) {
            val bx = w * (0.34f + k * 0.2f); val by = waterY + h * 0.42f
            path.reset(); path.moveTo(bx, by)
            val sway = sin(timer * 2f + k) * 18f
            path.quadTo(bx + sway, by - 70f, bx + sway * 1.6f, by - 140f)
            canvas.drawPath(path, weed)
        }
        // 심해: 빛나는 알갱이가 천천히 떠오른다
        for (k in 0 until 24) {
            val gx = w * (0.32f + (k * 97 % 66) / 100f)
            val gy = waterY + depth * 0.35f + ((k * 131) % 100) / 100f * (bed - waterY - depth * 0.35f) - (timer * 20f + k * 13f) % 120f
            canvas.drawCircle(gx, gy, 3f + (k % 3), glow)
        }
        // 맨 아래 바닥: 모래와 바위
        canvas.drawRect(0f, bed, w, bed + h, sand)
        for (k in 0 until 9) {
            val px = w * (0.33f + k * 0.075f); val r = 26f + (k * 37 % 5) * 8f
            canvas.drawOval(px - r, bed - r * 0.7f, px + r, bed + r * 0.4f, rock)
        }
        for (k in 0 until 6) {
            val u = (timer * 0.4f + k * 0.17f) % 1f
            val bx = w * (0.4f + k * 0.1f) + sin(timer * 3f + k) * 8f
            canvas.drawCircle(bx, bed - u * 400f, 6f + k % 3 * 2f, bubble)
        }
    }

    private fun drawFish(canvas: Canvas, id: String, cx: Float, cy: Float, dir: Float) {
        val icon = SpriteManager.shopIcons[id] ?: return
        val bmp = if (dir > 0f) icon else SpriteManager.getFlipped(icon)
        val s = fishSize(id)
        rect.set(cx - s / 2f, cy - s / 2f, cx + s / 2f, cy + s / 2f)
        canvas.drawBitmap(bmp, null, rect, null)
    }
    private val dimIcon = Paint().apply { alpha = 70 }
    private val controlPaint = Paint().apply { color = Color.rgb(160, 220, 255); isAntiAlias = true }
    private val hookPaint = Paint().apply { color = Color.rgb(90, 90, 100); strokeWidth = 5f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val h = gameView.screenHeight.toFloat()
        // 배 조종: ◀ ▶ 를 누르고 있는 동안 움직인다 (여러 손가락 중 하나라도)
        if (onBoat) {
            steer = 0f
            val up = event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL
            var onSteer = false
            for (i in 0 until event.pointerCount) {
                if (up || (event.actionMasked == MotionEvent.ACTION_POINTER_UP && i == event.actionIndex)) continue
                val px = event.getX(i); val py = event.getY(i)
                if (leftRect.contains(px, py)) { steer -= 1f; onSteer = true }
                if (rightRect.contains(px, py)) { steer += 1f; onSteer = true }
            }
            val ax = event.getX(event.actionIndex); val ay = event.getY(event.actionIndex)
            if (onSteer || leftRect.contains(ax, ay) || rightRect.contains(ax, ay)) return true
        }
        // 조종 중: 물속을 누르거나 끌면 바늘이 그쪽으로
        if (state == State.CONTROL && (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE)) {
            if (exitRect.contains(event.x, event.y)) { gameView.showShop(); return true }
            // 좌우만 손가락으로 (깊이는 저절로 가라앉는다) — 땅에 박히면 못 움직인다
            if (!stuck) hookTX = event.x.coerceIn(gameView.screenWidth * 0.3f, gameView.screenWidth * 0.98f)
            return true
        }
        if (event.actionMasked != MotionEvent.ACTION_DOWN && event.actionMasked != MotionEvent.ACTION_POINTER_DOWN) return true
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) return true
        if (exitRect.contains(event.x, event.y)) { gameView.showShop(); return true }
        if (onBoat && !helmRect.isEmpty && helmRect.contains(event.x, event.y)) {
            atHelm = !atHelm
            if (!atHelm) heroU = DECK_MIN
            noticeText = if (atHelm) "조타실에 들어왔다! ◀ ▶ 로 배를 움직여요" else "갑판으로 나왔다 · 여기서 낚시해요"; noticeTimer = 1.6f
            return true
        }
        if (onBoat && atHelm && (state == State.READY || state == State.CAUGHT || state == State.MISSED) &&
            (controlRect.contains(event.x, event.y) || btnRect.contains(event.x, event.y) || event.y > gameView.screenHeight * 0.45f)) {
            noticeText = "조타실에서는 낚시를 못 해요 · 갑판으로 나가요"; noticeTimer = 1.6f
            return true
        }
        if (!boatRect.isEmpty && boatRect.contains(event.x, event.y)) {
            if (onBoat) { onBoat = false; pirateX = Float.NaN; balls.clear(); noticeText = "부두로 돌아왔어요" }
            else if (GameProgress.spendRubies(BOAT_PRICE)) { boardBoat(); noticeText = "어선을 타고 바다로! 바다 생물이 잡혀요 · 가끔 해적선이 와요" }
            else noticeText = "배를 타려면 루비 ${BOAT_PRICE}개가 있어야 해요"
            noticeTimer = 2f
            state = State.READY; catchId = null
            return true
        }
        if (!controlRect.isEmpty && controlRect.contains(event.x, event.y)) { if (payForFishing()) startControl(); return true }
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
        /** 낚시 한 번 값 (루비) */
        const val FISHING_PRICE = 2
        /** 배 타기 값 (루비) */
        const val BOAT_PRICE = 5
        /** 어선: 튼튼함 · 빠르기 · 그림에서 인간이 서는 곳(가로 비율)·갑판 높이(세로 비율) */
        private const val BOAT_HP = 3
        private const val BOAT_SPEED = 420f
        private const val BOAT_HERO_U = 0.70f
        /** 앞 갑판에서 걸을 수 있는 곳 · 조타실 자리 (배 그림 가로 비율) · 걷는 빠르기 */
        private const val DECK_MIN = 0.56f
        private const val DECK_MAX = 0.88f
        private const val HELM_U = 0.40f
        private const val WALK_U = 0.35f
        private const val BOAT_DECK_V = 0.80f
        /** 해적선: 처음엔 배를 타고 15~35초 뒤, 그 뒤로는 약 12~28초마다 · 12초 머물며 1.6초마다 대포 (Claude 판단) */
        private const val PIRATE_FIRST = 15f
        private const val PIRATE_EVERY = 20f
        private const val PIRATE_STAY = 12f
        private const val PIRATE_FIRE_GAP = 1.6f
        private const val BALL_G = 1400f
        /** 던지기로 한 번에 잡는 최대 마릿수 (Lv 이 아무리 커도) */
        private const val CAST_CAP = 100
        /** 낚시줄 조종: 헤엄치는 물고기 수 · 바늘 빠르기 */
        private const val SWIMMERS = 12
        /** Lv1 낚시줄 길이(물 위에서 바닥까지) · Lv1 가라앉는 빠르기 (바닥까지 약 7초) */
        private const val BASE_DEPTH = 1500f
        private const val SINK_SPEED = 220f
        private const val HOOK_SPEED = 700f
        private val NAMES = mapOf("fish_small" to "작은 물고기", "fish_big" to "큰 물고기", "fish_gold" to "✨황금 물고기",
            "sea_jelly" to "해파리", "sea_whale" to "🐋고래", "sea_flounder" to "광어", "sea_bream" to "돔", "sea_trash" to "쓰레기",
            "sea_starfish" to "불가사리", "sea_clam" to "조개", "sea_rockfish" to "우럭", "sea_turtle" to "거북이", "sea_shark" to "🦈상어")
        /** 아래쪽에 가진 개수를 보여 줄 종류 */
        private val CATCH_IDS = listOf("fish_small", "fish_big", "fish_gold", "sea_flounder", "sea_bream", "sea_rockfish", "sea_clam",
            "sea_starfish", "sea_jelly", "sea_turtle", "sea_shark", "sea_whale", "sea_trash")
        /** 황금 미끼로 잘 나오는 귀한 바다 생물 */
        private val RARE_SEA = setOf("sea_turtle", "sea_shark", "sea_whale")
        private val SEA_TABLE = listOf("sea_trash" to 18, "sea_flounder" to 14, "sea_bream" to 12, "sea_rockfish" to 12, "sea_clam" to 12,
            "sea_starfish" to 10, "sea_jelly" to 9, "sea_turtle" to 6, "sea_shark" to 4, "sea_whale" to 3)
        /** 이 깊이보다 아래는 바다 (낚시줄 Lv2 부터 닿는다) */
        private const val SEA_START = 1500f
    }
}
