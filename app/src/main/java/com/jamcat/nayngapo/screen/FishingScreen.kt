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
class FishingScreen(private val gameView: GameView, atSeaEnd: Boolean = false) : Screen {

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
    /** 배 위에서 걸어 다닌다 (대표님 2026-10-17 「배 위에서 걸어다닐 수 있고, 조타실에 가야만 조종할 수 있어」):
     *  heroU = 배 그림 가로 비율로 본 인간 자리. 앞 갑판(DECK_MIN~DECK_MAX)을 ◀ ▶ 로 걷고, 조타실 문 가까이에서 '조타실'에 들어가면
     *  ◀ ▶ 가 배를 움직인다. 조타실에서는 낚시를 못 한다 (Claude 판단) */
    private var heroU = BOAT_HERO_U
    private var atHelm = false
    private var walkDir = 1f
    /** 배가 보는 쪽 (1 = 오른쪽, -1 = 왼쪽) — 조타실에서 왼쪽으로 몰면 뱃머리가 왼쪽으로 돈다 (대표님 2026-10-17 「배의 방향을 바꿀 수 있고」) */
    private var boatFacing = 1f
    private var walking = false
    private val helmRect = RectF()

    // ===== 어선 = 스테이지 같은 구성 (대표님 2026-10-17 「어선은 스테이지와 같은 방식으로 나가기, 멈추기 등등」) =====
    // 위 왼쪽 멈추기·나가기, 아래 왼쪽 십자 패드(걷기·조타실에선 배 조종), 오른쪽 공격·점프·SP(그물) — 땅 스테이지와 같은 버튼.
    private val touchControls = com.jamcat.nayngapo.ui.TouchControls().apply { specialLabel = "그물"; specialCooldownTotal = NET_GAP }
    private var paused = false
    /** 인간이 있는 곳: 어선 갑판 · 점프 중 · 해적선 갑판 (대표님 2026-10-17 「해적선이 나오면 뛰어 건너가서만 공격할 수 있어」) */
    private enum class Pos { DECK, AIR, PIRATE, ISLAND }
    /** 배는 앞으로 계속 멀리 갈 수 있다 (대표님 2026-10-17 「배는 계속 앞으로 갈 수 있고, 끝까지 가면 새롭지만 닫혀 있는 다른 상점이 있어」).
     *  seaDist = 부두에서 온 거리. 조타실에서 오른쪽으로 계속 가면 화면이 흘러가고, ISLAND_AT 에 섬이 있다 */
    private var seaDist = 0f
    /** 섬 위 자리 (섬 왼쪽 끝에서의 거리) */
    private var islandOff = 0f
    /** 바다 끝 = 상점마을2 항구 (대표님 2026-10-17 「섬 가게가 아니라 또 다른 상점마을이야. 상점마을2 가기를 누르면 들어갈 수 있어」) */
    private val village2Rect = RectF()
    private val village1Rect = RectF()
    private var pos = Pos.DECK
    private var airX = 0f; private var airY = 0f; private var airVX = 0f; private var airVY = 0f
    /** 해적선 갑판 위 자리 (해적선 그림 가로 비율) */
    private var pirU = 0.5f
    private var punchT = 0f
    private var scratchT = 0f
    private var jumpHeld = false
    private var attackHeld = false
    private var specialHeld = false
    private val pauseVeil = Paint().apply { color = Color.argb(185, 8, 10, 16) }
    private val pauseBig = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; textSize = 96f }
    private val pauseHint = Paint().apply { color = Color.argb(200, 230, 234, 244); textAlign = Paint.Align.CENTER; isAntiAlias = true; textSize = 34f }
    private val heartOn = Paint().apply { color = Color.rgb(240, 70, 80); isAntiAlias = true }
    private val heartOff = Paint().apply { color = Color.argb(120, 60, 50, 50); isAntiAlias = true }
    private val heartPath = Path()
    /** 배 튼튼함 — 대포알에 3번 맞으면 가라앉아 부두로 돌아온다 (Claude 판단) */
    /** 배 용품 가게(상점마을2)의 배 철판·배 작살만큼 튼튼하고 세다 (2026-10-24) */
    private val boatMaxHp: Int get() = BOAT_HP + 2 * GameProgress.itemCount("boat_plate")
    private val boatAttack: Int get() = BOAT_ATTACK + GameProgress.itemCount("boat_harpoon")
    private var boatHp = boatMaxHp
    private var hurtT = 0f
    /** 다음 해적선이 올 때까지 (배를 탄 동안만 흐른다) */
    private var pirateWait = 0f
    /** 해적선 x (NaN = 없음) · 머무는 시간 · 다음 대포까지 */
    private var pirateX = Float.NaN
    private var pirateStay = 0f
    private var pirateFire = 0f
    private var pirateLeaving = false
    /** 해적냥이 체력 — **해적냥이만 쓰러뜨리면 해적선은 안전**(대표님 2026-10-17 「해적선은 해적냥이만 죽이면 안전해」「31 스테이지랑 비슷한 형식」):
     *  대포가 멈추고 빈 해적선은 그대로 떠 있다가 떠나간다 */
    private var pirateHp = PIRATE_HP
    private var pirateCatDead = false
    private var catBoomT = 0f
    /** 그물: 배 뒤 크레인에서 던져 해적선에 맞으면 배 공격력만큼 */
    private class Net(var x: Float, var y: Float, var vx: Float, var vy: Float, var spin: Float = 0f)
    private val nets = mutableListOf<Net>()
    private var netCool = 0f
    private class Ball(var x: Float, var y: Float, var vx: Float, var vy: Float)
    private val balls = mutableListOf<Ball>()
    private class Splash(val x: Float, val y: Float, var t: Float, val boom: Boolean)
    private val splashes = mutableListOf<Splash>()

    /** 상점마을2에서 나오면 바다 끝에 매어 둔 어선 위 (배 값은 다시 안 낸다) — 모든 필드가 준비된 뒤 첫 프레임에 */
    private var startAtSeaEnd = atSeaEnd
    private fun ensureStart() {
        if (!startAtSeaEnd) return
        startAtSeaEnd = false
        boardBoat(); seaDist = ISLAND_AT; boatX = gameView.screenWidth * 0.60f; pirateWait = PIRATE_FIRST + (Math.random() * PIRATE_EVERY).toFloat()
    }

    private fun boardBoat() {
        onBoat = true; boatHp = boatMaxHp; boatX = gameView.screenWidth * 0.24f; heroU = BOAT_HERO_U; atHelm = false; seaDist = 0f
        pos = Pos.DECK; paused = false; touchControls.releaseAll(); nets.clear(); boatFacing = 1f; boardMates()
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
        val seaBefore = seaDist
        updateHero(dt, w, h, waterY)
        if (onBoat) { updateCrew(dt, w, h); updateMates(dt, w) }
        if (!onBoat) return
        val shipW = shipWidth(h)
        if (pirateX.isNaN()) {
            pirateWait -= dt
            if (pirateWait <= 0f && seaDist < ISLAND_AT - gameView.screenWidth * 1.2f) {
                pirateX = w + 20f; pirateStay = PIRATE_STAY; pirateFire = 1.2f; pirateLeaving = false; pirateHp = PIRATE_HP; pirateCatDead = false; spawnCrew()
                noticeText = "🏴‍☠️ 해적선이다! 점프로 뛰어 건너가 때리거나 그물(SP)을 던져요!"; noticeTimer = 2.5f
                SoundManager.playPlayerHurt()
            }
        } else {
            val stopX = w - shipW * 0.95f
            if (catBoomT > 0f) catBoomT -= dt
            if (pirateCatDead) {
                // 선장(해적냥이)이 없는 해적선은 가만히 있다 (대표님 2026-10-17) — 떠나지 않고, 배가 멀리 가면 바다와 함께 뒤로 흘러간다
                pirateX -= seaDist - seaBefore
                if (pirateX + shipW < -100f || pirateX > w * 3f) {
                    if (pos == Pos.PIRATE) swimBack("헤엄쳐서 배로 돌아왔다")
                    pirateX = Float.NaN; pirateWait = PIRATE_EVERY * (0.6f + Math.random().toFloat() * 0.8f)
                }
            } else if (pirateLeaving) {
                pirateX += 260f * dt
                if (pirateX > w + 40f && balls.isEmpty()) {
                    pirateX = Float.NaN; pirateWait = PIRATE_EVERY * (0.6f + Math.random().toFloat() * 0.8f)
                    if (!pirateCatDead) { noticeText = "해적선이 물러갔다!"; noticeTimer = 1.6f }
                }
            } else {
                if (pirateX > stopX) pirateX = (pirateX - 300f * dt).coerceAtLeast(stopX)
                else {
                    pirateStay -= dt; pirateFire -= dt
                    if (pirateFire <= 0f && !pirateCatDead) {
                        // 뱃전의 대포 구멍에서 배를 겨누고 쏜다 (조금씩 빗나가게)
                        val sx = pirateX + shipW * 0.20f; val sy = waterY - shipHeight(h) * 0.30f
                        val tx = boatCenter(w) + (Math.random().toFloat() - 0.5f) * w * 0.16f
                        val tt = 1.5f
                        balls.add(Ball(sx, sy, (tx - sx) / tt, (waterY - 30f - sy) / tt - 0.5f * BALL_G * tt))
                        pirateFire = PIRATE_FIRE_GAP
                        SoundManager.playSpecial()
                    }
                    if (pirateStay <= 0f) { pirateLeaving = true; if (pos == Pos.PIRATE) swimBack("해적선이 떠난다! 헤엄쳐서 배로 돌아왔다") }
                }
            }
        }
        if (netCool > 0f) netCool -= dt
        val ni = nets.iterator()
        while (ni.hasNext()) {
            val n = ni.next()
            n.vy += BALL_G * dt; n.x += n.vx * dt; n.y += n.vy * dt; n.spin += dt * 4f
            val sh = shipHeight(h); val sw = shipWidth(h)
            val shipHit = !pirateX.isNaN() && !pirateCatDead && n.x in pirateX..(pirateX + sw) && n.y >= waterY - sh * 0.45f
            if (shipHit) {
                pirateHp -= boatAttack
                splashes.add(Splash(n.x, n.y, 0f, true))
                SoundManager.playCatHit()
                if (pirateHp <= 0) pirateDefeated()
                else { noticeText = "그물 명중! 해적냥이 ♥ $pirateHp/$PIRATE_HP"; noticeTimer = 1f }
                ni.remove()
            } else if (n.vy > 0f && n.y >= waterY) { splashes.add(Splash(n.x, waterY, 0f, false)); ni.remove() }
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
                    if (boatHp <= 0) { sinkBoat(); return }
                    noticeText = "쾅! 배가 맞았다 (배 ♥ $boatHp/$boatMaxHp)"; noticeTimer = 1.2f
                } else splashes.add(Splash(b.x, waterY, 0f, false))
                it.remove()
            }
        }
    }

    private fun sinkBoat() {
        onBoat = false; pirateX = Float.NaN; balls.clear(); nets.clear(); crew.clear(); pos = Pos.DECK; atHelm = false; touchControls.releaseAll()
        if (state != State.CONTROL && state != State.REEL) { state = State.READY; catchId = null }
        GameProgress.consumeItem(ShopScreen.BOAT_ID)   // 가라앉으면 배를 잃는다 (대표님 2026-10-24) — 조선소에서 다시 사야 한다
        noticeText = "배가 가라앉았다! 배를 잃고 겨우 부두로 돌아왔어요"; noticeTimer = 2.5f
    }

    /** 배에서 내려 부두로 (나가기) */
    private fun leaveBoat() {
        onBoat = false; pirateX = Float.NaN; balls.clear(); nets.clear(); crew.clear(); pos = Pos.DECK; atHelm = false; paused = false
        touchControls.releaseAll()
        state = State.READY; catchId = null
        noticeText = "부두로 돌아왔어요"; noticeTimer = 2f
    }

    private fun swimBack(msg: String) {
        pos = Pos.DECK; heroU = DECK_MAX
        noticeText = msg; noticeTimer = 1.8f
    }

    private fun pirateDefeated() {
        pirateCatDead = true; catBoomT = 0.7f; balls.clear()
        SoundManager.playCatDie(); SoundManager.playSpecial()
        // 선장이 쓰러지면 선원 냥이들도 바다로 뛰어들어 도망간다 — 그래서 해적냥이만 쓰러뜨리면 안전
        crewFlee()
        noticeText = "해적냥이를 물리쳤다! 선원 냥이들도 도망갔다 — 이제 해적선은 안전해"; noticeTimer = 2.4f
    }

    // ===== 해적선 선원 냥이 (대표님 2026-10-17 「해적선에 해적냥이 빼고 다른 냥이가 있어, 해적냥이랑 같이 있다.
    //  다른 고양이들이 우리 배에 올라타서 공격할 수 있다」) =====
    // 해적선마다 선원 냥이 CREW_COUNT 마리(치즈냥이 모습). 해적선이 멈춰 있는 동안 BOARD_GAP 초마다 한 마리씩 우리 배로 뛰어 건너와
    // 인간 쪽으로 걸어와 할퀸다(배 하트 −1). 주먹으로 때려 쓰러뜨린다 (선원 ♥3, 주먹 2 — Claude 판단).
    // 해적선에 올라탄 인간도 남은 선원이 할퀸다. 해적냥이가 쓰러지면 모두 바다로 도망간다.
    private enum class CrewAt { SHIP, AIR, BOAT }
    private class Crew(var at: CrewAt, var u: Float) {
        var hp = CREW_HP
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var t = 0f; var targetU = 0f
        var scratch = 0.8f
        var dir = -1f
        var boom = 0f
    }
    private val crew = mutableListOf<Crew>()
    private var boardT = 0f

    // ===== 같이 탄 우리 팀 냥이 (대표님 2026-10-17 「고양이와 같이 탈 수 있어」) =====
    // 캣타워에서 출전시킨 냥이들이 어선에 같이 탄다 — 갑판에 서 있다가, 건너온 선원 냥이에게 다가가 때린다 (1초마다 1, Claude 판단).
    private class Mate(val cat: com.jamcat.nayngapo.engine.CatTeam.TeamCat, val sprite: Bitmap, val home: Float) {
        var u = home; var cool = 0f; var dir = 1f
        /** 어디 있나: 어선 · 점프 중 · 해적선 (선원 냥이와 같은 구분을 쓴다) */
        var at = CrewAt.BOAT
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var t = 0f; var toShip = false; var landU = 0f
        /** 스스로 낚시: 다음 입질까지 */
        var fishT = 0f
    }
    private val mates = mutableListOf<Mate>()

    private fun allySprite(c: com.jamcat.nayngapo.engine.CatTeam.TeamCat): Bitmap = when (c.kind) {
        com.jamcat.nayngapo.entity.AllyCat.Kind.GREEN -> SpriteManager.greenCatAlly
        com.jamcat.nayngapo.entity.AllyCat.Kind.BLUE -> SpriteManager.blueCatAlly
        com.jamcat.nayngapo.entity.AllyCat.Kind.FLYING -> SpriteManager.flyingCatAlly
        com.jamcat.nayngapo.entity.AllyCat.Kind.GUN -> SpriteManager.gunCatAlly
        com.jamcat.nayngapo.entity.AllyCat.Kind.NORMAL, com.jamcat.nayngapo.entity.AllyCat.Kind.BOAT -> c.coat.ally
        else -> com.jamcat.nayngapo.entity.AllyCat.extraSprite(c.kind, c.coat) ?: c.coat.ally
    }

    private fun boardMates() {
        mates.clear()
        val homes = floatArrayOf(0.84f, 0.76f, 0.63f)
        for ((k, c) in com.jamcat.nayngapo.engine.CatTeam.party.filter { !it.gone }.take(homes.size).withIndex())
            mates.add(Mate(c, allySprite(c), homes[k]).apply { fishT = MATE_FISH_MIN + k * 4f })
    }

    /**
     * 같이 탄 냥이들 (대표님 2026-10-17 「아군들도 스스로 낚시를 하고 해적을 공격해」):
     * - 해적이 없으면 갑판 자리에서 **스스로 낚시** — MATE_FISH_MIN~MAX 초마다 한 마리 낚아 가방에 (루비 안 듦, 바다 생물 반 — Claude 판단)
     * - 선원 냥이가 우리 배에 올라오면 다가가 때린다
     * - 해적선이 멈춰 있으면 **해적선으로 뛰어 건너가** 선원 냥이부터, 다 쓰러지면 해적냥이(선장)를 때린다 (1초마다 1)
     * - 해적냥이가 쓰러지거나 해적선이 떠나면 다시 어선으로 (떠날 때는 헤엄쳐서)
     */
    private fun updateMates(dt: Float, w: Float) {
        val h = gameView.screenHeight.toFloat()
        val pirateHere = !pirateX.isNaN() && !pirateCatDead && !pirateLeaving
        val stopped = pirateHere && pirateX <= w - shipWidth(h) * 0.95f + 1f
        for (m in mates) {
            if (m.cool > 0f) m.cool -= dt
            when (m.at) {
                CrewAt.AIR -> {
                    m.t += dt; m.vy += JUMP_G * dt; m.x += m.vx * dt; m.y += m.vy * dt
                    if (m.t >= CREW_JUMP_T) {
                        if (m.toShip && pirateHere) { m.at = CrewAt.SHIP; m.u = m.landU }
                        else { m.at = CrewAt.BOAT; m.u = m.home }
                    }
                }
                CrewAt.SHIP -> {
                    if (!pirateHere) {
                        // 선장이 쓰러졌으면 뛰어서, 해적선이 떠나면 헤엄쳐서 돌아온다
                        if (!pirateX.isNaN() && pirateCatDead) mateJump(m, w, h, toShip = false)
                        else { m.at = CrewAt.BOAT; m.u = m.home; splashes.add(Splash(deckScreenX(w, m.home), h * 0.55f, 0f, false)) }
                        continue
                    }
                    val foe = crew.filter { it.at == CrewAt.SHIP && it.boom <= 0f }.minByOrNull { kotlin.math.abs(it.u - m.u) }
                    val goal = foe?.u ?: PIRATE_CAT_U
                    val d = goal - m.u
                    if (kotlin.math.abs(d) > 0.06f) { m.dir = if (d > 0f) 1f else -1f; m.u = (m.u + m.dir * CREW_WALK_U * 0.6f * dt).coerceIn(PIRATE_DECK_MIN, PIRATE_DECK_MAX) }
                    else if (m.cool <= 0f) {
                        m.cool = MATE_HIT_GAP
                        SoundManager.playCatHit()
                        if (foe != null) {
                            foe.hp -= MATE_DAMAGE
                            if (foe.hp <= 0) { foe.boom = 0.5f; noticeText = "${m.cat.name}이(가) 선원 냥이를 쓰러뜨렸다!"; noticeTimer = 1.2f }
                        } else {
                            pirateHp -= MATE_DAMAGE
                            if (pirateHp <= 0) pirateDefeated()
                            else { noticeText = "${m.cat.name}의 공격! 해적냥이 ♥ $pirateHp/$PIRATE_HP"; noticeTimer = 0.8f }
                        }
                    }
                }
                CrewAt.BOAT -> {
                    val target = crew.filter { it.at == CrewAt.BOAT && it.boom <= 0f }.minByOrNull { kotlin.math.abs(it.u - m.u) }
                    if (target == null && stopped) { mateJump(m, w, h, toShip = true); continue }
                    val goal = target?.u ?: m.home
                    val d = goal - m.u
                    if (kotlin.math.abs(d) > 0.05f) { m.dir = if (d > 0f) 1f else -1f; m.u = (m.u + m.dir * CREW_WALK_U * 1.2f * dt).coerceIn(DECK_MIN, DECK_MAX) }
                    else if (target == null) m.dir = 1f
                    if (target != null && kotlin.math.abs(target.u - m.u) <= 0.07f && m.cool <= 0f) {
                        m.cool = MATE_HIT_GAP
                        target.hp -= MATE_DAMAGE
                        SoundManager.playCatHit()
                        if (target.hp <= 0) { target.boom = 0.5f; noticeText = "${m.cat.name}이(가) 선원 냥이를 쓰러뜨렸다!"; noticeTimer = 1.2f }
                    }
                    // 스스로 낚시 (해적이 없고 제자리에 있을 때)
                    if (target == null && pirateX.isNaN() && kotlin.math.abs(m.u - m.home) <= 0.05f) {
                        m.fishT -= dt
                        if (m.fishT <= 0f) {
                            m.fishT = MATE_FISH_MIN + (Math.random() * (MATE_FISH_MAX - MATE_FISH_MIN)).toFloat()
                            val id = if (Math.random() < 0.5) seaCreature() else fishIdAtDepth(0.1f)
                            GameProgress.addItem(id)
                            val first = GameProgress.recordCatch(id)
                            noticeText = "${m.cat.name}이(가) ${NAMES[id]}을(를) 낚았다!" + (if (first) " (처음! 루비 +${GameProgress.FIRST_CATCH_RUBIES})" else "")
                            noticeTimer = 1.8f
                            SoundManager.playCatHit()
                        }
                    }
                }
            }
        }
    }

    /** 같이 탄 냥이가 해적선으로(또는 어선으로) 뛴다 */
    private fun mateJump(m: Mate, w: Float, h: Float, toShip: Boolean) {
        m.x = if (m.at == CrewAt.SHIP) pirateX + shipWidth(h) * m.u else deckScreenX(w, m.u)
        m.y = if (m.at == CrewAt.SHIP) pirateFootY(h) + 12f else deckFootY(h) - 55f
        val tx: Float; val ty: Float
        if (toShip) {
            m.landU = 0.40f + Math.random().toFloat() * 0.45f
            tx = pirateX + shipWidth(h) * m.landU; ty = pirateFootY(h) + 12f
        } else { tx = deckScreenX(w, m.home); ty = deckFootY(h) - 55f }
        val tt = CREW_JUMP_T
        m.vx = (tx - m.x) / tt; m.vy = (ty - m.y) / tt - 0.5f * JUMP_G * tt
        m.t = 0f; m.toShip = toShip; m.at = CrewAt.AIR; m.dir = if (tx > m.x) 1f else -1f
    }

    private val mateRod = Paint().apply { color = Color.rgb(120, 80, 40); strokeWidth = 4f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }

    /** 같이 탄 냥이 그리기 — where 에 있는 냥이만 (AIR 면 footY 는 쓰지 않는다) */
    private fun drawMates(canvas: Canvas, w: Float, h: Float, footY: Float, where: CrewAt = CrewAt.BOAT) {
        for (m in mates) {
            if (m.at != where) continue
            val ch = h * 0.12f * (if (m.cat.isKitten) com.jamcat.nayngapo.entity.AllyCat.KITTEN_SCALE else 1f)
            val cw = m.sprite.width * ch / m.sprite.height
            val x = when (where) { CrewAt.BOAT -> deckScreenX(w, m.u); CrewAt.SHIP -> pirateX + shipWidth(h) * m.u; CrewAt.AIR -> m.x }
            val fy = if (where == CrewAt.AIR) m.y else footY
            val sd = if (where == CrewAt.BOAT) m.dir * boatFacing else m.dir
            rect.set(x - cw / 2f, fy - ch, x + cw / 2f, fy)
            canvas.drawBitmap(if (sd < 0f) SpriteManager.getFlipped(m.sprite) else m.sprite, null, rect, null)
            // 스스로 낚시하는 중이면 작은 낚싯대와 줄
            if (where == CrewAt.BOAT && pirateX.isNaN() && kotlin.math.abs(m.u - m.home) <= 0.05f && crew.none { it.at == CrewAt.BOAT }) {
                val side = boatFacing
                val tipX = x + side * cw * 0.9f; val tipY = fy - ch * 1.1f
                canvas.drawLine(x + side * cw * 0.3f, fy - ch * 0.5f, tipX, tipY, mateRod)
                canvas.drawLine(tipX, tipY, tipX + side * 10f, h * 0.55f + sin(timer * 3f + m.home * 10f) * 4f, line)
            }
        }
    }

    private fun spawnCrew() {
        crew.clear()
        for (k in 0 until CREW_COUNT) crew.add(Crew(CrewAt.SHIP, 0.45f + k * 0.15f))
        boardT = 2.5f
    }

    private fun crewFlee() {
        val h = gameView.screenHeight.toFloat(); val w = gameView.screenWidth.toFloat()
        for (c in crew) if (c.boom <= 0f) splashes.add(Splash(crewX(c, w, h), h * 0.55f, 0f, false))
        crew.clear()
    }

    private fun crewX(c: Crew, w: Float, h: Float): Float = when (c.at) {
        CrewAt.SHIP -> pirateX + shipWidth(h) * c.u
        CrewAt.AIR -> c.x
        CrewAt.BOAT -> deckScreenX(w, c.u)
    }

    private fun updateCrew(dt: Float, w: Float, h: Float) {
        if (pirateX.isNaN() || pirateCatDead) { if (crew.isNotEmpty() && pirateX.isNaN()) crewFlee(); return }
        if (pirateLeaving) {
            // 해적선이 떠나면 우리 배에 있던 선원도 바다로 뛰어들어 돌아간다
            if (crew.any { it.at != CrewAt.SHIP }) { crew.filter { it.at != CrewAt.SHIP }.forEach { splashes.add(Splash(crewX(it, w, h), h * 0.55f, 0f, false)) }; crew.removeAll { it.at != CrewAt.SHIP } }
            return
        }
        val stopped = pirateX <= w - shipWidth(h) * 0.95f + 1f
        // 한 마리씩 우리 배로 뛰어 건너온다
        if (stopped) {
            boardT -= dt
            if (boardT <= 0f) {
                boardT = BOARD_GAP
                crew.firstOrNull { it.at == CrewAt.SHIP && it.boom <= 0f }?.let { c ->
                    c.x = crewX(c, w, h); c.y = pirateFootY(h)
                    c.targetU = DECK_MIN + Math.random().toFloat() * (DECK_MAX - DECK_MIN)
                    val tx = deckScreenX(w, c.targetU)
                    val tt = CREW_JUMP_T
                    c.vx = (tx - c.x) / tt; c.vy = (deckFootY(h) - 55f - c.y) / tt - 0.5f * JUMP_G * tt
                    c.t = 0f; c.at = CrewAt.AIR; c.dir = -1f
                }
            }
        }
        val heroX = heroWorldX(w, h)
        val it = crew.iterator()
        while (it.hasNext()) {
            val c = it.next()
            if (c.boom > 0f) { c.boom -= dt; if (c.boom <= 0f) it.remove(); continue }
            when (c.at) {
                CrewAt.AIR -> {
                    c.t += dt; c.vy += JUMP_G * dt; c.x += c.vx * dt; c.y += c.vy * dt
                    if (c.t >= CREW_JUMP_T) {
                        val bl = boatLeft(w); val bw = boatWidth(w)
                        val r = deckRangeScreen(w)
                        if (c.x in (r.start - 40f)..(r.endInclusive + 40f)) {
                            c.at = CrewAt.BOAT; c.u = screenToU(w, c.x).coerceIn(DECK_MIN, DECK_MAX)
                            noticeText = "선원 냥이가 우리 배에 올라탔다! 공격으로 때려요"; noticeTimer = 1.4f
                        } else { splashes.add(Splash(c.x, h * 0.55f, 0f, false)); it.remove() }   // 배가 움직여 빗나가면 바다에 빠진다
                    }
                }
                CrewAt.BOAT -> {
                    // 인간이 갑판에 있으면 인간 쪽으로, 없으면(조타실·해적선) 조타실 쪽으로 가서 배를 할퀸다
                    val targetU = if (pos == Pos.DECK && !atHelm) heroU else DECK_MIN
                    val d = targetU - c.u
                    if (kotlin.math.abs(d) > 0.06f) { c.dir = if (d > 0f) 1f else -1f; c.u = (c.u + c.dir * CREW_WALK_U * dt).coerceIn(DECK_MIN, DECK_MAX) }
                    c.scratch -= dt
                    if (c.scratch <= 0f && kotlin.math.abs(d) <= 0.08f) {
                        c.scratch = CREW_SCRATCH_GAP
                        hurtBoat(if (pos == Pos.DECK && !atHelm) "선원 냥이 할퀴기!" else "선원 냥이가 배를 할퀸다!")
                        if (!onBoat) return
                    }
                }
                CrewAt.SHIP -> {
                    // 해적선에 올라탄 인간에게 다가가 할퀸다
                    if (pos == Pos.PIRATE) {
                        val d = pirU - c.u
                        if (kotlin.math.abs(d) > 0.08f) { c.dir = if (d > 0f) 1f else -1f; c.u = (c.u + c.dir * CREW_WALK_U * 0.5f * dt).coerceIn(PIRATE_DECK_MIN, PIRATE_DECK_MAX) }
                        c.scratch -= dt
                        if (c.scratch <= 0f && kotlin.math.abs(crewX(c, w, h) - heroX) < 140f) {
                            c.scratch = CREW_SCRATCH_GAP
                            hurtBoat("선원 냥이 할퀴기!")
                            if (!onBoat) return
                        }
                    } else c.dir = -1f
                }
            }
        }
    }

    /** 주먹이 닿는 선원 냥이를 때린다 (맞으면 true) */
    private fun punchCrew(w: Float, h: Float): Boolean {
        val heroX = heroWorldX(w, h)
        val where = if (pos == Pos.PIRATE) CrewAt.SHIP else CrewAt.BOAT
        val c = crew.filter { it.at == where && it.boom <= 0f }.minByOrNull { kotlin.math.abs(crewX(it, w, h) - heroX) } ?: return false
        if (kotlin.math.abs(crewX(c, w, h) - heroX) > 200f) return false
        c.hp -= boatAttack
        SoundManager.playCatHit()
        if (c.hp <= 0) { c.boom = 0.5f; SoundManager.playCatDie(); noticeText = "선원 냥이를 쓰러뜨렸다!"; noticeTimer = 1f }
        else { noticeText = "퍽! 선원 냥이 ♥ ${c.hp}/$CREW_HP"; noticeTimer = 0.8f }
        return true
    }

    /** 선원 냥이 그리기 (치즈냥이 걷는 그림, 가는 쪽을 본다) — onWhere 에 있는 것만 */
    private fun drawCrew(canvas: Canvas, w: Float, h: Float, onWhere: CrewAt, footY: Float) {
        val frames = SpriteManager.catWalk
        for (c in crew) {
            if (c.at != onWhere) continue
            val x = crewX(c, w, h)
            val fy = if (onWhere == CrewAt.AIR) c.y else footY
            if (c.boom > 0f) { boomPaint.alpha = (c.boom / 0.5f * 255).toInt(); canvas.drawCircle(x, fy - 60f, 40f + (0.5f - c.boom) * 120f, boomPaint); continue }
            val f = frames[((timer * 8f).toInt() + crew.indexOf(c)) % frames.size]
            val sd = if (c.at == CrewAt.BOAT) c.dir * boatFacing else c.dir   // 배 위에서는 u 쪽 → 화면 쪽
            val bmp = if (sd < 0f) SpriteManager.getFlipped(f) else f
            val ch = h * 0.09f; val cw = bmp.width * ch / bmp.height   // 선원은 선장(해적냥이)보다 작다 (대표님 2026-10-17)
            rect.set(x - cw / 2f, fy - ch, x + cw / 2f, fy)
            canvas.drawBitmap(bmp, null, rect, null)
        }
    }

    private fun hurtBoat(msg: String) {
        if (hurtT > 0f) return
        boatHp--; hurtT = 0.8f
        SoundManager.playPlayerHurt()
        if (boatHp <= 0) { sinkBoat(); return }
        noticeText = "$msg (♥ $boatHp/$boatMaxHp)"; noticeTimer = 1.2f
    }

    private fun deckFootY(h: Float) = h * 0.55f + 90f
    /** 섬: 바다 끝까지 오면 화면 오른쪽에 나타난다 (왼쪽 끝 = 화면 74%) */
    private fun islandLeft(w: Float) = w * 0.74f + (ISLAND_AT - seaDist)
    private fun islandFootY(h: Float) = h * 0.55f - 40f
    private fun pirateTop(h: Float) = h * 0.55f - shipHeight(h) * 0.68f
    private fun pirateFootY(h: Float) = pirateTop(h) + shipHeight(h) * PIRATE_DECK_V

    /** 인간의 화면 x (발 가운데) */
    private fun heroWorldX(w: Float, h: Float): Float = when (pos) {
        Pos.DECK -> deckScreenX(w, if (atHelm) HELM_U else heroU)
        Pos.AIR -> airX
        Pos.PIRATE -> pirateX + shipWidth(h) * pirU
        Pos.ISLAND -> islandLeft(w) + islandOff
    }

    /** 십자 패드 = 걷기(조타실에선 배 조종) · 점프 = 뛰어 건너기 · 공격 = 해적선 위에서 주먹 · SP = 그물 */
    private fun updateHero(dt: Float, w: Float, h: Float, waterY: Float) {
        val input = touchControls.inputState
        val dir = (if (input.moveRight) 1f else 0f) - (if (input.moveLeft) 1f else 0f)
        if (punchT > 0f) punchT -= dt
        walking = false
        val canAct = state == State.READY || state == State.CAUGHT || state == State.MISSED   // 낚싯줄을 던져 놓은 동안은 갑판에 서 있는다
        when (pos) {
            Pos.DECK -> if (atHelm) {
                    // 화면 오른쪽 60% 를 넘으면 배 대신 바다가 흘러간다 — 멀리 간다
                    if (dir != 0f) boatFacing = dir   // 모는 쪽으로 뱃머리가 돈다
                    val nx = boatX + dir * BOAT_SPEED * dt
                    when {
                        nx > w * 0.60f && seaDist < ISLAND_AT -> { seaDist = (seaDist + nx - w * 0.60f).coerceAtMost(ISLAND_AT); boatX = w * 0.60f }
                        nx < w * 0.22f && seaDist > 0f -> { seaDist = (seaDist - (w * 0.22f - nx)).coerceAtLeast(0f); boatX = w * 0.22f }
                        else -> boatX = nx.coerceIn(w * 0.22f, w * 0.60f)
                    }
                }
                else if (dir != 0f && canAct) { heroU = (heroU + dir * boatFacing * WALK_U * dt).coerceIn(DECK_MIN, DECK_MAX); walking = true; walkDir = dir }
            Pos.ISLAND -> if (dir != 0f) {
                islandOff = (islandOff + dir * WALK_U * boatWidth(w) * dt).coerceIn(40f, (w - 40f - islandLeft(w)).coerceAtMost(w * ISLAND_W))
                walking = true; walkDir = dir
            }
            Pos.PIRATE -> if (dir != 0f) {
                pirU = (pirU + dir * WALK_U * dt * boatWidth(w) / shipWidth(h)).coerceIn(PIRATE_DECK_MIN, PIRATE_DECK_MAX)
                walking = true; walkDir = dir
            }
            Pos.AIR -> {
                // 공중에서도 패드로 좌우를 고친다 (손을 떼면 그 자리로 떨어진다 — 스테이지 점프처럼)
                airVX = dir * JUMP_VX; if (dir != 0f) walkDir = dir
                airVY += JUMP_G * dt; airX = (airX + airVX * dt).coerceIn(0f, w); airY += airVY * dt
                if (airVY > 0f) {
                    val bl = boatLeft(w); val bw = boatWidth(w)
                    val sw = shipWidth(h)
                    val pf = pirateFootY(h)
                    when {
                        airX in deckRangeScreen(w) && airY >= deckFootY(h) && airY < deckFootY(h) + 90f -> {
                            pos = Pos.DECK; heroU = screenToU(w, airX)
                        }
                        !pirateX.isNaN() && !pirateLeaving &&
                            airX in (pirateX + sw * PIRATE_DECK_MIN)..(pirateX + sw * PIRATE_DECK_MAX) && airY >= pf && airY < pf + 90f -> {
                            pos = Pos.PIRATE; pirU = (airX - pirateX) / sw
                            noticeText = "해적선에 올라탔다! 공격 버튼으로 해적냥이를 때려요"; noticeTimer = 1.6f
                        }
                        islandLeft(w) < w && airX in (islandLeft(w) + 30f)..(islandLeft(w) + w * ISLAND_W) &&
                            airY >= islandFootY(h) && airY < islandFootY(h) + 90f -> {
                            pos = Pos.ISLAND; islandOff = airX - islandLeft(w)
                        }
                        airY >= waterY + 40f -> {
                            splashes.add(Splash(airX, waterY, 0f, false))
                            pos = Pos.DECK; heroU = DECK_MAX
                            hurtBoat("풍덩! 바다에 빠졌다 — 배로 기어 올라왔다")
                            if (!onBoat) return
                        }
                    }
                }
            }
        }
        // 점프: 누르는 순간 한 번 (조타실 안·낚시 중에는 못 뛴다)
        val jumpNow = input.jump || input.moveUp
        if (jumpNow && !jumpHeld && pos != Pos.AIR && !atHelm && canAct) {
            airX = heroWorldX(w, h); airY = when (pos) { Pos.PIRATE -> pirateFootY(h); Pos.ISLAND -> islandFootY(h); else -> deckFootY(h) }
            airVX = (if (dir != 0f) dir else walkDir) * JUMP_VX; airVY = -JUMP_VY
            if (dir != 0f) walkDir = dir
            pos = Pos.AIR
        }
        jumpHeld = jumpNow
        // 공격: 해적선 위에서만 (어선에서는 해적선을 못 때린다 — 그물은 SP)
        if (input.attack && !attackHeld) {
            if ((pos == Pos.DECK && !atHelm || pos == Pos.PIRATE) && punchT <= 0f && crew.isNotEmpty() &&
                crew.any { c -> c.at == (if (pos == Pos.PIRATE) CrewAt.SHIP else CrewAt.BOAT) && c.boom <= 0f &&
                    kotlin.math.abs(crewX(c, w, h) - heroWorldX(w, h)) <= 200f } &&
                (pos == Pos.DECK || kotlin.math.abs(heroWorldX(w, h) - (pirateX + shipWidth(h) * PIRATE_CAT_U)) >= 230f || pirateCatDead)) {
                // 가까운 선원 냥이부터 (해적선 위에서 해적냥이가 더 가까우면 해적냥이)
                punchT = PUNCH_TIME
                punchCrew(w, h)
            } else if (pos == Pos.PIRATE && punchT <= 0f) {
                punchT = PUNCH_TIME
                val catX = pirateX + shipWidth(h) * PIRATE_CAT_U
                if (kotlin.math.abs(heroWorldX(w, h) - catX) < 230f && !pirateCatDead) {
                    pirateHp -= boatAttack
                    splashes.add(Splash(catX, pirateFootY(h) - 120f, 0f, true))
                    SoundManager.playCatHit()
                    if (pirateHp <= 0) pirateDefeated()
                    else { noticeText = "퍽! 해적냥이 ♥ $pirateHp/$PIRATE_HP"; noticeTimer = 0.8f }
                } else SoundManager.playAttack()
            } else if (pos == Pos.DECK && !atHelm && !pirateX.isNaN() && punchT <= 0f) {
                punchT = PUNCH_TIME; SoundManager.playAttack()
                if (crew.none { it.at == CrewAt.BOAT }) { noticeText = "해적냥이는 해적선으로 뛰어 건너가야 때릴 수 있어요 (그물은 SP)"; noticeTimer = 1.4f }
            }
        }
        attackHeld = input.attack
        // SP = 그물 던지기 (어선 위에서)
        if (input.special && !specialHeld && pos == Pos.DECK) throwNet()
        specialHeld = input.special
        touchControls.specialCooldown = netCool
        // 해적냥이 할퀴기: 해적선 위에서 가까이 있으면 1.5초마다
        if (pos == Pos.PIRATE && !pirateCatDead) {
            scratchT -= dt
            if (scratchT <= 0f && kotlin.math.abs(heroWorldX(w, h) - (pirateX + shipWidth(h) * PIRATE_CAT_U)) < 200f) {
                scratchT = SCRATCH_GAP
                hurtBoat("해적냥이 할퀴기!")
            }
        } else scratchT = 0.6f
    }

    /** 그물 던지기: 배 뒤 크레인 끝에서 해적선(없으면 앞쪽 바다)을 향해 포물선으로 (약 1초) */
    private fun throwNet() {
        if (netCool > 0f) return
        val w = gameView.screenWidth.toFloat(); val h = gameView.screenHeight.toFloat()
        val waterY = h * 0.55f
        val bw = boatWidth(w); val bh = bw * SpriteManager.fishingBoat.height / SpriteManager.fishingBoat.width
        val sx = deckScreenX(w, 0.08f)
        val sy = waterY + 90f - bh * 0.80f + bh * 0.30f
        val tx = if (!pirateX.isNaN() && !pirateCatDead) pirateX + shipWidth(h) * PIRATE_CAT_U else w * 0.8f
        val ty = waterY - shipHeight(h) * 0.25f
        val tt = 1.0f
        nets.add(Net(sx, sy, (tx - sx) / tt, (ty - sy) / tt - 0.5f * BALL_G * tt))
        netCool = NET_GAP
        SoundManager.playAttack()
    }

    private val netLine = Paint().apply { color = Color.rgb(70, 60, 50); strokeWidth = 3f; style = Paint.Style.STROKE; isAntiAlias = true }
    private val netKnot = Paint().apply { color = Color.rgb(230, 140, 60); isAntiAlias = true }
    private val hpBack = Paint().apply { color = Color.argb(200, 40, 36, 32); isAntiAlias = true }
    private val hpFill = Paint().apply { color = Color.rgb(230, 60, 70); isAntiAlias = true }

    private fun boatWidth(w: Float) = w * 0.36f
    /** 배 그림 왼쪽 끝 (boatX = 배가 처음 설 때 인간 자리 기준) */
    private fun boatLeft(w: Float) = boatX - boatWidth(w) * BOAT_HERO_U
    private fun boatCenter(w: Float) = boatLeft(w) + boatWidth(w) / 2f
    /** 배 그림 안 자리(u, 뱃머리가 오른쪽일 때 기준) → 화면 x. 배가 왼쪽을 보면 거울처럼 뒤집힌다 */
    private fun deckScreenX(w: Float, u: Float) = boatLeft(w) + boatWidth(w) * (if (boatFacing > 0f) u else 1f - u)
    private fun screenToU(w: Float, x: Float): Float { val v = (x - boatLeft(w)) / boatWidth(w); return if (boatFacing > 0f) v else 1f - v }
    private fun deckRangeScreen(w: Float): ClosedFloatingPointRange<Float> {
        val a = deckScreenX(w, DECK_MIN); val b = deckScreenX(w, DECK_MAX)
        return minOf(a, b)..maxOf(a, b)
    }

    private fun shipHeight(h: Float) = h * 0.46f
    private fun shipWidth(h: Float) = shipHeight(h) * SpriteManager.pirateShip.width / SpriteManager.pirateShip.height

    private val ballPaint = Paint().apply { color = Color.rgb(40, 40, 46); isAntiAlias = true }
    private val splashPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val boomPaint = Paint().apply { color = Color.rgb(255, 170, 40); isAntiAlias = true }
    private val steerPaint = Paint().apply { color = Color.argb(200, 255, 252, 244); isAntiAlias = true }
    private val heartPaint = Paint().apply { color = Color.rgb(230, 60, 70); textAlign = Paint.Align.LEFT; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }

    private val islandSand = Paint().apply { color = Color.rgb(238, 216, 160); isAntiAlias = true }
    private val palmTrunk = Paint().apply { color = Color.rgb(150, 100, 60); strokeWidth = 26f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE; isAntiAlias = true }
    private val palmLeaf = Paint().apply { color = Color.rgb(70, 170, 80); isAntiAlias = true }
    private val hutWall = Paint().apply { color = Color.rgb(200, 150, 96); isAntiAlias = true }
    private val hutRoof = Paint().apply { color = Color.rgb(90, 160, 200); isAntiAlias = true }
    private val board = Paint().apply { color = Color.rgb(130, 86, 50); strokeWidth = 16f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val islandPath = Path()

    /** 바다 끝: 상점마을2 항구 — 모래 땅 + 야자나무 + 부두 + 멀리 보이는 오두막 지붕들 + '상점마을2' 팻말 */
    private fun drawIsland(canvas: Canvas, w: Float, h: Float, waterY: Float, hh: Float) {
        val left = islandLeft(w); val foot = islandFootY(h)
        islandPath.reset()
        islandPath.moveTo(left, waterY + 30f)
        islandPath.quadTo(left + 30f, foot, left + 120f, foot)
        islandPath.lineTo(w + 400f, foot); islandPath.lineTo(w + 400f, waterY + 30f); islandPath.close()
        canvas.drawPath(islandPath, islandSand); canvas.drawPath(islandPath, ink)
        // 마을 오두막 지붕들 (안쪽)
        for (k in 0 until 3) {
            val rx = left + w * 0.10f + k * w * 0.09f
            rect.set(rx - 80f, foot - 150f, rx + 80f, foot)
            canvas.drawRect(rect, hutWall); canvas.drawRect(rect, ink)
            path.reset(); path.moveTo(rx - 110f, foot - 140f); path.lineTo(rx, foot - 230f); path.lineTo(rx + 110f, foot - 140f); path.close()
            hutRoof.color = intArrayOf(Color.rgb(90, 160, 220), Color.rgb(240, 120, 90), Color.rgb(120, 200, 120))[k]
            canvas.drawPath(path, hutRoof); canvas.drawPath(path, ink)
        }
        // 야자나무
        val tx = left + w * 0.02f
        path.reset(); path.moveTo(tx, foot); path.quadTo(tx + 40f, foot - 200f, tx + 10f, foot - 360f)
        canvas.drawPath(path, palmTrunk)
        for (k in 0 until 5) {
            canvas.save(); canvas.rotate(200f + k * 35f + 90f, tx + 10f, foot - 360f)
            canvas.drawOval(tx - 20f, foot - 380f, tx + 40f, foot - 230f, palmLeaf); canvas.drawOval(tx - 20f, foot - 380f, tx + 40f, foot - 230f, ink)
            canvas.restore()
        }
        // 팻말
        text.textSize = 40f
        rect.set(left + w * 0.04f, foot - 330f, left + w * 0.04f + 260f, foot - 262f)
        canvas.drawRoundRect(rect, 12f, 12f, card); canvas.drawRoundRect(rect, 12f, 12f, ink)
        canvas.drawTr("상점마을2", rect.centerX(), rect.centerY() + 14f, text)
        if (pos == Pos.ISLAND) drawHero(canvas, left + islandOff, foot, hh)
    }

    /** 출발한 상점 마을 바닷가 (배가 처음 자리 가까이 오면 왼쪽에 보인다) — 오른쪽 끝 화면 x */
    private fun villageShoreX(w: Float) = w * 0.10f - seaDist
    private val grass = Paint().apply { color = Color.rgb(120, 190, 90) }

    private fun drawVillageShore(canvas: Canvas, w: Float, h: Float, waterY: Float) {
        val r = villageShoreX(w)
        canvas.drawRect(-10f, waterY - 50f, r, waterY - 20f, grass)
        islandPath.reset()
        islandPath.moveTo(-10f, waterY - 20f); islandPath.lineTo(r, waterY - 20f); islandPath.lineTo(r + 60f, waterY + 40f); islandPath.lineTo(-10f, waterY + 40f); islandPath.close()
        canvas.drawPath(islandPath, islandSand); canvas.drawPath(islandPath, ink)
        // 부두
        rect.set(r - 40f, waterY - 40f, r + 160f, waterY - 18f)
        canvas.drawRect(rect, wood); canvas.drawRect(rect, ink)
        // 마을 천막 하나 (멀리)
        val tx = r - 160f
        rect.set(tx - 90f, waterY - 190f, tx + 90f, waterY - 50f)
        canvas.drawRect(rect, hutWall); canvas.drawRect(rect, ink)
        path.reset(); path.moveTo(tx - 110f, waterY - 180f); path.lineTo(tx, waterY - 280f); path.lineTo(tx + 110f, waterY - 180f); path.close()
        hutRoof.color = Color.rgb(230, 70, 70)
        canvas.drawPath(path, hutRoof); canvas.drawPath(path, ink)
    }

    /** 인간 그리기: 서 있기·걷기·점프·주먹 (왼쪽으로 가면 뒤집는다) */
    private fun drawHero(canvas: Canvas, x: Float, footY: Float, hh: Float) {
        val body = when {
            pos == Pos.AIR -> SpriteManager.playerJump[if (airVY < -300f) 0 else if (airVY < 300f) 1 else 2]
            punchT > 0f -> SpriteManager.playerPunch[((1f - punchT / PUNCH_TIME) * (SpriteManager.playerPunch.size - 1)).toInt().coerceIn(0, SpriteManager.playerPunch.size - 1)]
            walking -> SpriteManager.playerWalk[((timer * 10f).toInt()) % SpriteManager.playerWalk.size]
            else -> SpriteManager.playerIdle[0]
        }
        val bmp = if (walkDir < 0f && (walking || pos != Pos.DECK || punchT > 0f)) SpriteManager.getFlipped(body) else body
        val hw = bmp.width * hh / bmp.height
        rect.set(x - hw / 2f, footY - hh, x + hw / 2f, footY)
        canvas.drawBitmap(bmp, null, rect, null)
    }

    /** 바다 위: 해적선(해적냥이가 타고 있다)·대포알·물보라 */
    private fun drawSea(canvas: Canvas, w: Float, h: Float, waterY: Float) {
        if (!pirateX.isNaN()) {
            val sh = shipHeight(h); val sw = shipWidth(h)
            val bob = sin(timer * 1.6f) * 8f
            val top = waterY - sh * 0.68f + bob
            // 해적냥이가 갑판 위에서 이쪽을 본다
            val pc = SpriteManager.getFlipped(SpriteManager.pirateCat)
            val ch = sh * 0.30f; val cw = pc.width * ch / pc.height
            val cx2 = pirateX + sw * PIRATE_CAT_U
            rect.set(cx2 - cw / 2f, top + sh * PIRATE_DECK_V - ch, cx2 + cw / 2f, top + sh * PIRATE_DECK_V)
            if (!pirateCatDead) canvas.drawBitmap(pc, null, rect, null)
            else if (catBoomT > 0f) { boomPaint.alpha = (catBoomT / 0.7f * 255).toInt(); canvas.drawCircle(cx2, rect.centerY(), 60f + (0.7f - catBoomT) * 120f, boomPaint) }
            // 해적선에 올라탄 인간 (다리는 뱃전이 가린다)
            drawCrew(canvas, w, h, CrewAt.SHIP, top + sh * PIRATE_DECK_V + 12f)
            drawMates(canvas, w, h, top + sh * PIRATE_DECK_V + 12f, CrewAt.SHIP)
            if (pos == Pos.PIRATE) drawHero(canvas, pirateX + sw * pirU, top + sh * PIRATE_DECK_V + 40f, h * 0.30f)
            rect.set(pirateX, top, pirateX + sw, top + sh)
            canvas.drawBitmap(SpriteManager.pirateShip, null, rect, null)
            if (!pirateCatDead) {
                // 해적냥이 체력 줄
                val bx = pirateX + sw * 0.2f; val by = top - 30f
                canvas.drawRoundRect(bx, by, bx + sw * 0.6f, by + 22f, 11f, 11f, hpBack)
                canvas.drawRoundRect(bx + 3f, by + 3f, bx + 3f + (sw * 0.6f - 6f) * pirateHp.coerceAtLeast(0) / PIRATE_HP, by + 19f, 8f, 8f, hpFill)
            }
        }
        // 날아가는 그물: 빙글 도는 그물망 (격자 + 둘레 매듭)
        for (n in nets) {
            canvas.save(); canvas.rotate(n.spin * 57f, n.x, n.y)
            val r = 46f
            canvas.drawCircle(n.x, n.y, r, netLine)
            for (k in -2..2) {
                val o = k * r * 0.4f
                val half = kotlin.math.sqrt((r * r - o * o).coerceAtLeast(0f))
                canvas.drawLine(n.x + o, n.y - half, n.x + o, n.y + half, netLine)
                canvas.drawLine(n.x - half, n.y + o, n.x + half, n.y + o, netLine)
            }
            for (k in 0 until 8) {
                val a = k * Math.PI / 4
                canvas.drawCircle(n.x + (kotlin.math.cos(a) * r).toFloat(), n.y + (kotlin.math.sin(a) * r).toFloat(), 6f, netKnot)
            }
            canvas.restore()
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
        canvas.drawBitmap(if (boatFacing > 0f) bmp else SpriteManager.getFlipped(bmp), null, rect, null)
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

    /** 낚시는 돈이 들지 않는다 (대표님 2026-10-24) — 던지기·낚시줄 조종하기 둘 다 */
    private fun payForFishing(): Boolean {
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
        ensureStart()
        if (onBoat && paused) return
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
        ensureStart()
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
        var wx = -((timer * 40f + seaDist) % 160f)
        while (wx < w) { canvas.drawLine(wx, waterY + 40f, wx + 60f, waterY + 42f, waterDark); canvas.drawLine(wx + 80f, waterY + 140f, wx + 140f, waterY + 142f, waterDark); wx += 160f }

        // 왼쪽 나무 부두(또는 바다 위 작은 배) + 인간 + 낚싯대
        val boatBob = if (onBoat) sin(timer * 2f) * 6f else 0f
        val pierTop = waterY - 20f + boatBob
        val hero = SpriteManager.playerIdle[0]
        val hh = h * 0.30f; val hw = hero.width * hh / hero.height
        val hx = if (onBoat && boatX > 0f) heroWorldX(w, h) else w * 0.18f
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
            drawMates(canvas, w, h, footY - 55f)
            drawCrew(canvas, w, h, CrewAt.BOAT, footY - 55f)
            if (pos == Pos.DECK) drawHero(canvas, hx, footY, hh)
            drawFishingBoat(canvas, w, h, footY)
            if (atHelm) { text.textSize = h * 0.05f; canvas.drawText("🛞", hx, footY - hh * 1.05f, text) }
        }
        if (onBoat && state != State.CONTROL && state != State.REEL && islandLeft(w) < w) drawIsland(canvas, w, h, waterY, hh)
        if (onBoat && state != State.CONTROL && state != State.REEL && villageShoreX(w) > 0f) drawVillageShore(canvas, w, h, waterY)
        if (state != State.CONTROL && state != State.REEL) drawSea(canvas, w, h, waterY)
        // 점프 중인 인간은 배들 앞에
        if (onBoat) { drawCrew(canvas, w, h, CrewAt.AIR, 0f); drawMates(canvas, w, h, 0f, CrewAt.AIR) }
        if (onBoat && pos == Pos.AIR) drawHero(canvas, airX, airY, hh)
        // 낚싯대 끝 → 찌
        val rodBaseX = hx + hw * 0.25f; val rodBaseY = footY - hh * 0.61f
        val tipX = hx + w * 0.16f; val tipY = footY - hh * 1.11f
        if (!onBoat || (pos == Pos.DECK && !atHelm)) canvas.drawLine(rodBaseX, rodBaseY, tipX, tipY, rod)
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
        if (!onBoat) canvas.drawTr("찌가 쏙 들어가고 '!'가 뜨면 바로 당기기! · 물고기는 캣타워에서 간식으로", w / 2f, h * 0.16f, text)

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
            State.READY -> "🎣 던지기"
            State.CAST, State.WAIT -> "기다리는 중…"
            State.BITE -> "당기기!"
            State.CAUGHT, State.MISSED -> "🎣 다시 던지기"
            State.CONTROL -> "↔ 바늘 움직이기 (내려가는 중)"
            State.REEL -> "끌어 올리는 중!"
        }
        // 어선 위에서는 패드와 버튼 사이 가운데 아래에 두 줄짜리 버튼으로
        val midX = (570f + (w - 650f)) / 2f
        if (onBoat) btnRect.set(midX - 330f, h - 150f, midX - 10f, h - 20f)
        else btnRect.set(w * 0.75f - 230f, h * 0.80f, w * 0.75f + 230f, h * 0.80f + 110f)
        canvas.drawRoundRect(btnRect, 55f, 55f, if (state == State.BITE) bitePaint else btnPaint)
        canvas.drawRoundRect(btnRect, 55f, 55f, ink)
        drawBtnLabel(canvas, label, btnRect, h, h * 0.048f)
        // 배 타기 / 돌아오기 (왼쪽 아래, 낚시줄 조종 버튼 왼쪽 — 기다리지 않을 때만)
        boatRect.setEmpty()
        if (!onBoat && (state == State.READY || state == State.CAUGHT || state == State.MISSED)) {
            boatRect.set(w * 0.04f, h * 0.80f, w * 0.04f + 400f, h * 0.80f + 110f)
            canvas.drawRoundRect(boatRect, 55f, 55f, if (onBoat) btnPaint else controlPaint); canvas.drawRoundRect(boatRect, 55f, 55f, ink)
            text.textSize = h * 0.04f
            canvas.drawTr(if (onBoat) "🚤 돌아오기" else (if (GameProgress.itemCount(ShopScreen.BOAT_ID) > 0) "🚤 어선 타기" else "🚤 어선 없음 · 조선소"), boatRect.centerX(), boatRect.centerY() + h * 0.014f, text)
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
            if (onBoat) controlRect.set(midX + 10f, h - 150f, midX + 330f, h - 20f)
            else controlRect.set(w * 0.42f - 240f, h * 0.80f, w * 0.42f + 240f, h * 0.80f + 110f)
            canvas.drawRoundRect(controlRect, 55f, 55f, controlPaint); canvas.drawRoundRect(controlRect, 55f, 55f, ink)
            drawBtnLabel(canvas, "🎮 낚시줄 조종", controlRect, h, h * 0.042f)
        }
        // 어선: 땅 스테이지와 같은 조작 버튼 + 위 가운데 배 하트 + 멈추기·나가기 (물속을 볼 때 빼고)
        helmRect.setEmpty()
        if (onBoat && state != State.CONTROL && state != State.REEL) {
            touchControls.draw(canvas, gameView.screenWidth, gameView.screenHeight)
            // 조타실 들어가기/나오기 — 조타실 문 가까이에서만 (패드 위)
            if (pos == Pos.DECK && (atHelm || heroU <= DECK_MIN + 0.04f)) {
                helmRect.set(130f, h - 290f - 390f, 470f, h - 290f - 280f)
                canvas.drawRoundRect(helmRect, 40f, 40f, if (atHelm) btnPaint else steerPaint); canvas.drawRoundRect(helmRect, 40f, 40f, ink)
                text.textSize = h * 0.036f
                canvas.drawTr(if (atHelm) "🚶 갑판으로" else "🛞 조타실", helmRect.centerX(), helmRect.centerY() + h * 0.013f, text)
            }
            // 배 하트 (HP 10)
            val r = 17f; val gap = 46f
            val hx0 = w / 2f - (boatMaxHp - 1) * gap / 2f
            for (k in 0 until boatMaxHp) drawHeartShape(canvas, hx0 + k * gap, h * 0.165f, r, if (k < boatHp) heartOn else heartOff)
            text.textSize = h * 0.028f
            canvas.drawTr(if (atHelm) "어선 · 공격력 $boatAttack · 조타실: 패드로 배 조종" else "어선 · 공격력 $boatAttack · 패드 걷기 · 점프로 해적선에", w / 2f, h * 0.215f, text)
            // 섬까지 남은 거리
            val u = (seaDist / ISLAND_AT).coerceIn(0f, 1f)
            rect.set(w / 2f - 260f, h * 0.24f, w / 2f + 260f, h * 0.24f + 16f)
            canvas.drawRoundRect(rect, 8f, 8f, steerPaint); canvas.drawRoundRect(rect, 8f, 8f, ink)
            rect.right = rect.left + 520f * u
            canvas.drawRoundRect(rect, 8f, 8f, btnPaint)
            text.textSize = h * 0.026f
            canvas.drawTr(if (u >= 1f) "🏝 상점마을2 도착" else "🏝 상점마을2까지 ${((1f - u) * 100).toInt()}%", w / 2f, h * 0.24f + 50f, text)
            // 바다 끝에 오면 '상점마을2 가기'
            village2Rect.setEmpty()
            if (u >= 1f && pos != Pos.AIR) {
                village2Rect.set(w * 0.74f, h * 0.30f, w * 0.74f + 440f, h * 0.30f + 110f)
                canvas.drawRoundRect(village2Rect, 55f, 55f, btnPaint); canvas.drawRoundRect(village2Rect, 55f, 55f, ink)
                text.textSize = h * 0.042f
                canvas.drawTr("▼ 상점마을2 가기", village2Rect.centerX(), village2Rect.centerY() + h * 0.015f, text)
            }
            // 처음 자리(상점 마을 바닷가)로 돌아오면 '상점마을 들어가기' (대표님 2026-10-17)
            village1Rect.setEmpty()
            if (seaDist <= 0f && boatX <= w * 0.30f && pos != Pos.AIR) {
                village1Rect.set(w * 0.97f - 460f, h * 0.30f, w * 0.97f, h * 0.30f + 110f)
                canvas.drawRoundRect(village1Rect, 55f, 55f, btnPaint); canvas.drawRoundRect(village1Rect, 55f, 55f, ink)
                text.textSize = h * 0.042f
                canvas.drawTr("▼ 상점마을 들어가기", village1Rect.centerX(), village1Rect.centerY() + h * 0.015f, text)
            }
            com.jamcat.nayngapo.ui.MenuButtons.draw(canvas, paused)
        }
        // 낚시터 나가기 (어선 위에서는 위 왼쪽 '나가기'가 배에서 내리기)
        exitRect.setEmpty()
        if (!onBoat) {
            exitRect.set(w - w * 0.03f - 240f, h * 0.04f, w - w * 0.03f, h * 0.04f + 90f)
            canvas.drawRoundRect(exitRect, 45f, 45f, card); canvas.drawRoundRect(exitRect, 45f, 45f, ink)
            text.textSize = 40f
            canvas.drawTr("나가기", exitRect.centerX(), exitRect.centerY() + 14f, text)
        }
        if (onBoat && paused) {
            canvas.drawRect(0f, 0f, w, h, pauseVeil)
            canvas.drawTr("PAUSED", w / 2f, h / 2f - 6f, pauseBig)
            canvas.drawTr("멈추기 버튼을 다시 누르면 이어서 진행합니다", w / 2f, h / 2f + 44f, pauseHint)
            com.jamcat.nayngapo.ui.MenuButtons.draw(canvas, paused)
        }
    }

    /** 버튼 글자: 어선 위의 좁은 버튼은 ' · ' 앞뒤를 두 줄로 */
    private fun drawBtnLabel(canvas: Canvas, label: String, r: RectF, h: Float, size: Float) {
        val parts = label.split(" · ")
        if (onBoat && parts.size == 2) {
            text.textSize = h * 0.034f
            canvas.drawTr(parts[0], r.centerX(), r.centerY() - h * 0.008f, text)
            text.textSize = h * 0.028f
            canvas.drawTr(parts[1], r.centerX(), r.centerY() + h * 0.034f, text)
        } else {
            text.textSize = if (onBoat) h * 0.032f else size
            canvas.drawTr(label, r.centerX(), r.centerY() + h * 0.012f, text)
        }
    }

    private fun drawHeartShape(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        heartPath.reset()
        heartPath.moveTo(cx, cy + r)
        heartPath.cubicTo(cx - r * 1.6f, cy - r * 0.4f, cx - r * 0.7f, cy - r * 1.5f, cx, cy - r * 0.4f)
        heartPath.cubicTo(cx + r * 0.7f, cy - r * 1.5f, cx + r * 1.6f, cy - r * 0.4f, cx, cy + r)
        heartPath.close()
        canvas.drawPath(heartPath, paint)
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
        // 어선: 멈추기·나가기를 먼저 (두 번째 손가락도), 멈춘 동안은 다른 조작이 안 먹는다
        if (onBoat && state != State.CONTROL && state != State.REEL) {
            val act = event.actionMasked
            if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
                val idx = event.actionIndex
                when (com.jamcat.nayngapo.ui.MenuButtons.hitTest(event.getX(idx), event.getY(idx))) {
                    com.jamcat.nayngapo.ui.MenuButtons.Hit.PAUSE -> { paused = !paused; touchControls.releaseAll(); return true }
                    com.jamcat.nayngapo.ui.MenuButtons.Hit.EXIT -> { leaveBoat(); return true }
                    null -> Unit
                }
                if (paused) return true
                val px = event.getX(idx); val py = event.getY(idx)
                if (!village2Rect.isEmpty && village2Rect.contains(px, py)) { touchControls.releaseAll(); gameView.showVillage2(); return true }
                if (!village1Rect.isEmpty && village1Rect.contains(px, py)) { leaveBoat(); gameView.showShop(); return true }
                if (!helmRect.isEmpty && helmRect.contains(px, py)) {
                    atHelm = !atHelm
                    if (!atHelm) heroU = DECK_MIN
                    touchControls.releaseAll()
                    noticeText = if (atHelm) "조타실에 들어왔다! 패드로 배를 움직여요" else "갑판으로 나왔다 · 여기서 낚시해요"; noticeTimer = 1.6f
                    return true
                }
                if (btnRect.contains(px, py) || controlRect.contains(px, py)) {
                    if (atHelm && (state == State.READY || state == State.CAUGHT || state == State.MISSED)) {
                        noticeText = "조타실에서는 낚시를 못 해요 · 갑판으로 나가요"; noticeTimer = 1.6f
                    } else if (pos != Pos.DECK) {
                        noticeText = "어선 갑판에서 낚시해요"; noticeTimer = 1.4f
                    } else if (controlRect.contains(px, py)) { if (payForFishing()) { touchControls.releaseAll(); startControl() } }
                    else press()
                    return true
                }
            }
            if (paused) return true
            return touchControls.onTouchEvent(event, gameView.screenWidth, gameView.screenHeight)
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
        if (!boatRect.isEmpty && boatRect.contains(event.x, event.y)) {
            if (onBoat) leaveBoat()
            else if (GameProgress.itemCount(ShopScreen.BOAT_ID) > 0) { boardBoat(); noticeText = "어선을 타고 바다로! 바다 생물이 잡혀요 · 가끔 해적선이 와요" }
            else noticeText = "배가 없어요 — 상점 마을 조선소에서 어선을 사요"
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
        if (onBoat && state != State.CONTROL && state != State.REEL) { paused = !paused; touchControls.releaseAll(); return true }   // 스테이지처럼 뒤로가기 = 멈추기
        gameView.showShop()
        return true
    }

    companion object {
        /** '!' 뜨고 당길 수 있는 시간 */
        private const val BITE_WINDOW = 1.0f
        /** 어선: 튼튼함 · 빠르기 · 그림에서 인간이 서는 곳(가로 비율)·갑판 높이(세로 비율) */
        private const val BOAT_HP = 10   // 대표님 2026-10-17
        private const val BOAT_ATTACK = 2   // 대표님 2026-10-17 (그물 한 번에)
        /** 해적냥이 체력 · 그물 다시 던지기까지 (Claude 판단) */
        private const val PIRATE_HP = 20
        private const val NET_GAP = 0.8f
        /** 점프 (Claude 판단: 약 0.8초 동안 약 700 앞으로) · 주먹 · 해적냥이 할퀴기 간격 */
        private const val JUMP_VX = 850f
        private const val JUMP_VY = 1550f
        private const val JUMP_G = 3800f
        private const val PUNCH_TIME = 0.35f
        private const val SCRATCH_GAP = 1.5f
        /** 해적선 그림 안: 갑판 높이(세로 비율) · 걸을 수 있는 곳 · 해적냥이 자리 (가로 비율) */
        private const val PIRATE_DECK_V = 0.64f
        private const val PIRATE_DECK_MIN = 0.22f
        private const val PIRATE_DECK_MAX = 0.92f
        private const val PIRATE_CAT_U = 0.30f
        /** 선원 냥이: 마릿수 · ♥ · 우리 배로 건너오는 간격 · 뛰는 시간 · 걷는 빠르기 · 할퀴기 간격 (Claude 판단) */
        private const val CREW_COUNT = 3
        private const val CREW_HP = 3
        private const val BOARD_GAP = 3.5f
        private const val CREW_JUMP_T = 0.9f
        private const val CREW_WALK_U = 0.18f
        private const val CREW_SCRATCH_GAP = 1.2f
        /** 같이 탄 팀 냥이가 선원을 때리는 힘 · 간격 */
        private const val MATE_DAMAGE = 1
        private const val MATE_HIT_GAP = 1.0f
        /** 같이 탄 냥이가 스스로 한 마리 낚는 간격 (초) */
        private const val MATE_FISH_MIN = 15f
        private const val MATE_FISH_MAX = 30f
        /** 바다 끝(상점마을2 항구)까지의 거리 (배 빠르기 420 으로 약 30초) · 항구 땅에서 걷는 폭 (화면 폭 비율) — Claude 판단 */
        private const val ISLAND_AT = 12600f
        private const val ISLAND_W = 0.26f
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
