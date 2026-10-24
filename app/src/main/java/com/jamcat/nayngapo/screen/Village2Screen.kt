package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.ShopCatalog
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.engine.Terrain
import com.jamcat.nayngapo.entity.Player
import com.jamcat.nayngapo.entity.Prison
import com.jamcat.nayngapo.ui.TouchControls
import kotlin.math.abs
import kotlin.math.sin

/**
 * 상점마을2 (대표님 2026-10-17 「섬 가게가 아니라 또 다른 상점마을이야. 상점마을2 가기를 누르면 들어갈 수 있어」
 * 「끝까지 가면 새롭지만 닫혀 있는 다른 상점이 있어. 구출하면 사람이 생겨」).
 * 어선을 타고 바다 끝까지 가서 '상점마을2 가기'를 누르면 들어온다. 왼쪽 끝은 배를 댄 항구.
 * 바닷가 마을 — 오두막 가게들이 **모두 닫혀 있고**, 가게마다 앞에 **사람이 갇힌 감옥**이 있다.
 * 감옥을 때려 구하면 **사람이 생겨 그 가게가 문을 연다**(저장). 열린 가게에 들어가면 물건을 판다 (`ShopCatalog.village2Shops`, 2026-10-24).
 * 조작은 상점 마을·스테이지와 같다 (패드·공격·점프, 멈추기·나가기). 나가기 = 다시 어선으로.
 * 가게 수·간격·색은 Claude 판단.
 */
class Village2Screen(private val gameView: GameView) : Screen {

    private val touchControls = TouchControls()
    private val player: Player
    private var timer = 0f
    private var paused = false
    private var cameraX = 0f
    private var noticeText = ""
    private var noticeTimer = 0f
    private var nearShop = -1
    /** 열린 가게 말풍선 (화면 좌표) */
    private val enterRect = android.graphics.RectF()
    /** 있던 자리 저장 간격 */
    private var posSaveT = 0f

    private val shopX = FloatArray(SHOP_COUNT) { FIRST_SHOP_X + it * SHOP_GAP }
    private val worldWidth = FIRST_SHOP_X + (SHOP_COUNT - 1) * SHOP_GAP + 600f
    /** 가게마다 앞(왼쪽)에 사람이 갇힌 감옥 하나 — 이미 구한 가게는 감옥이 없다 */
    private val prisons: List<Prison?> = List(SHOP_COUNT) { i ->
        if (GameProgress.isVillage2Open(i)) null else Prison(shopX[i] - 330f, Constants.GROUND_Y, Prison.Kind.HUMAN)
    }

    init {
        Terrain.stage = Terrain.SHOP   // 평평한 땅
        // 나갔다 와도 있던 자리 그대로 (대표님 2026-10-24) — 처음 한 번만 항구 앞
        player = Player(GameProgress.village2X.takeIf { it > 0f } ?: (HARBOR_X + 200f), Constants.GROUND_Y)
        player.facingRight = true
        if ((0 until SHOP_COUNT).none { GameProgress.isVillage2Open(it) }) showNotice("상점마을2에 왔다! 가게가 다 닫혀 있다 — 갇힌 사람을 구하면 문을 연다")
    }

    private fun showNotice(t: String) { noticeText = t; noticeTimer = 2.4f }

    override fun update(deltaTime: Float) {
        if (paused) return
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime
        val input = touchControls.inputState
        if (input.useSkill != null || input.useHumanSkill != null) {
            input.useSkill = null; input.useHumanSkill = null
            showNotice("상점 마을에서는 기술을 쓸 수 없어요")
        }
        input.cycleOrder = false
        if (input.special) { if (player.performSpecial()) SoundManager.playSpecial(); input.special = false }
        var dirX = 0f
        if (input.moveLeft) dirX -= 1f
        if (input.moveRight) dirX += 1f
        player.setMovement(dirX, 0f)
        if (input.jump || input.moveUp) { player.jump(); input.jump = false }
        if (input.attack) { if (player.performAttack()) SoundManager.playAttack(); input.attack = false }
        player.update(deltaTime)
        player.x = player.x.coerceIn(HARBOR_X + 40f, worldWidth - player.width - 40f)
        posSaveT -= deltaTime
        if (posSaveT <= 0f) { posSaveT = 0.5f; GameProgress.saveVillageX(2, player.x) }
        touchControls.specialCooldown = player.specialCooldown

        // 감옥을 때리면 사람을 구하고 그 가게가 문을 연다
        player.getAttackHitBox()?.let { box ->
            for ((i, p) in prisons.withIndex()) {
                if (p == null || p.isOpen) continue
                if (RectF.intersects(box, p.hitBox)) {
                    p.open()
                    GameProgress.openVillage2(i)
                    SoundManager.playSpecial()
                    if (i == GameProgress.DOCTOR_SHOP) showNotice("의사를 구했다! 이제 야간 전투를 할 수 있어요 (출전 준비에서 🌙)")
                    else showNotice("인간 ${p.rescueCount}명 구출! 사람이 생겨 ${i + 1}번 가게가 문을 열었다")
                }
            }
        }

        val sw = gameView.screenWidth.toFloat()
        cameraX = (player.x + player.width / 2f - sw * 0.4f).coerceIn(0f, (worldWidth - sw).coerceAtLeast(0f))
        val pcx = player.x + player.width / 2f
        nearShop = shopX.indices.firstOrNull { abs(pcx - shopX[it]) < 150f } ?: -1
    }

    // ===== 그리기 =====
    private val rect = RectF()
    private val path = Path()
    private val skyPaint = Paint()
    private var skyShader: Shader? = null
    private val sea = Paint().apply { color = Color.rgb(90, 170, 230) }
    private val seaLight = Paint().apply { color = Color.rgb(150, 205, 245); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val sand = Paint().apply { color = Color.rgb(238, 216, 160) }
    private val sandDot = Paint().apply { color = Color.rgb(214, 188, 130); isAntiAlias = true }
    private val wood = Paint().apply { color = Color.rgb(170, 118, 70); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val fill = Paint().apply { isAntiAlias = true }
    private val board = Paint().apply { color = Color.rgb(120, 80, 46); strokeWidth = 18f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val sign = Paint().apply { color = Color.rgb(255, 250, 230); isAntiAlias = true }
    private val text = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val bubble = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val palmTrunk = Paint().apply { color = Color.rgb(150, 100, 60); strokeWidth = 24f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE; isAntiAlias = true }
    private val palmLeaf = Paint().apply { color = Color.rgb(70, 170, 80); isAntiAlias = true }
    private val pauseVeil = Paint().apply { color = Color.argb(185, 8, 10, 16) }
    private val pauseBig = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; textSize = 96f }
    private val pauseHint = Paint().apply { color = Color.argb(200, 230, 234, 244); textAlign = Paint.Align.CENTER; isAntiAlias = true; textSize = 34f }
    private val crossPaint = Paint().apply { color = Color.rgb(220, 40, 40); isAntiAlias = true }
    private val counter = Paint().apply { color = Color.argb(170, 20, 22, 30); isAntiAlias = true }
    private val counterText = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true; textSize = 36f }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
        val groundY = Constants.GROUND_Y
        if (skyShader == null) skyShader = LinearGradient(0f, 0f, 0f, groundY, Color.rgb(90, 190, 240), Color.rgb(230, 246, 255), Shader.TileMode.CLAMP)
        skyPaint.shader = skyShader
        canvas.drawRect(0f, 0f, w, h, skyPaint)
        // 멀리 바다 수평선
        canvas.drawRect(0f, groundY - 150f, w, groundY, sea)

        canvas.save()
        canvas.translate(-cameraX, 0f)
        // 모래 땅
        canvas.drawRect(HARBOR_X + 120f, groundY - 42f, worldWidth + 400f, h + 10f, sand)
        var dx = HARBOR_X + 160f
        var k = 0
        while (dx < worldWidth) { canvas.drawCircle(dx, groundY + 20f + (k % 4) * 46f, 4f, sandDot); dx += 63f; k++ }
        // 항구: 왼쪽 끝은 바다 + 부두 + 타고 온 어선
        canvas.drawRect(-400f, groundY - 42f, HARBOR_X + 120f, h + 10f, sea)
        for (j in 0 until 4) canvas.drawLine(-300f + j * 160f + sin(timer * 2f + j) * 12f, groundY + 30f + j % 2 * 50f, -230f + j * 160f + sin(timer * 2f + j) * 12f, groundY + 30f + j % 2 * 50f, seaLight)
        rect.set(HARBOR_X - 220f, groundY - 54f, HARBOR_X + 160f, groundY - 30f)
        canvas.drawRect(rect, wood); canvas.drawRect(rect, ink)
        val boat = SpriteManager.fishingBoat
        val bh = 230f; val bw = boat.width * bh / boat.height
        val bob = sin(timer * 1.8f) * 5f
        rect.set(HARBOR_X - 240f - bw, groundY - bh * 0.80f + 30f + bob, HARBOR_X - 240f, groundY + bh * 0.20f + 30f + bob)
        canvas.drawBitmap(boat, null, rect, null)
        // 마을 입구 팻말
        drawSign(canvas, HARBOR_X + 420f, groundY, "상점마을2")
        // 야자나무
        for (t in 0 until SHOP_COUNT + 1) drawPalm(canvas, FIRST_SHOP_X - SHOP_GAP / 2f + t * SHOP_GAP, groundY - 42f)
        // 가게들
        for (i in 0 until SHOP_COUNT) drawHut(canvas, i, shopX[i], groundY - 30f, GameProgress.isVillage2Open(i))
        for (p in prisons) if (p != null && !p.isOpen) p.draw(canvas)
        // 의사가 갇힌 감옥: 위에 빨간 십자 + '의사' 팻말 (대표님 2026-10-17 「의사를 구하면 야간 전투를 할 수 있어」)
        prisons.getOrNull(GameProgress.DOCTOR_SHOP)?.takeIf { !it.isOpen }?.let { p ->
            val cx = p.x + p.width / 2f; val top = p.y - p.height - 20f
            text.textSize = 34f
            rect.set(cx - 70f, top - 60f, cx + 70f, top - 8f)
            canvas.drawRoundRect(rect, 12f, 12f, sign); canvas.drawRoundRect(rect, 12f, 12f, ink)
            canvas.drawTr("의사", cx + 14f, rect.centerY() + 12f, text)
            canvas.drawRect(cx - 50f, rect.centerY() - 5f, cx - 26f, rect.centerY() + 5f, crossPaint)
            canvas.drawRect(cx - 43f, rect.centerY() - 12f, cx - 33f, rect.centerY() + 12f, crossPaint)
        }
        player.draw(canvas)
        // 가게 앞 말풍선 (열린 가게면 눌러서 들어간다)
        enterRect.setEmpty()
        if (nearShop >= 0) {
            val open = GameProgress.isVillage2Open(nearShop)
            val label = if (open) "▼ ${ShopCatalog.village2Shops[nearShop].first} 들어가기" else "${nearShop + 1}번 가게 · 닫힘 (감옥의 사람을 구해요)"
            text.textSize = 34f
            val tw = text.measureTr(label) + 50f
            rect.set(shopX[nearShop] - tw / 2f, groundY - 520f, shopX[nearShop] + tw / 2f, groundY - 456f)
            canvas.drawRoundRect(rect, 30f, 30f, bubble); canvas.drawRoundRect(rect, 30f, 30f, ink)
            canvas.drawTr(label, rect.centerX(), rect.centerY() + 12f, text)
            if (open) enterRect.set(rect.left - cameraX, rect.top - 20f, rect.right - cameraX, rect.bottom + 20f)
        }
        canvas.restore()

        // 위 가운데: 열린 가게 수
        val opened = (0 until SHOP_COUNT).count { GameProgress.isVillage2Open(it) }
        val t = "상점마을2 · 열린 가게 $opened / $SHOP_COUNT"
        val cw = counterText.measureTr(t) + 60f
        rect.set(w / 2f - cw / 2f, 24f, w / 2f + cw / 2f, 84f)
        canvas.drawRoundRect(rect, 30f, 30f, counter)
        canvas.drawTr(t, w / 2f, rect.centerY() + 13f, counterText)

        if (noticeTimer > 0f) {
            text.textSize = 38f
            val nw = text.measureTr(noticeText) + 60f
            rect.set(w / 2f - nw / 2f, h * 0.20f, w / 2f + nw / 2f, h * 0.20f + 76f)
            canvas.drawRoundRect(rect, 24f, 24f, sign); canvas.drawRoundRect(rect, 24f, 24f, ink)
            canvas.drawTr(noticeText, w / 2f, rect.centerY() + 13f, text)
        }
        touchControls.draw(canvas, gameView.screenWidth, gameView.screenHeight)
        com.jamcat.nayngapo.ui.MenuButtons.draw(canvas, paused)
        if (paused) {
            canvas.drawRect(0f, 0f, w, h, pauseVeil)
            canvas.drawTr("PAUSED", w / 2f, h / 2f - 6f, pauseBig)
            canvas.drawTr("멈추기 버튼을 다시 누르면 이어서 진행합니다", w / 2f, h / 2f + 44f, pauseHint)
            com.jamcat.nayngapo.ui.MenuButtons.draw(canvas, paused)
        }
    }

    private fun drawSign(canvas: Canvas, cx: Float, groundY: Float, label: String) {
        canvas.drawRect(cx - 8f, groundY - 220f, cx + 8f, groundY - 40f, wood)
        text.textSize = 44f
        val lw = text.measureTr(label) + 50f
        rect.set(cx - lw / 2f, groundY - 300f, cx + lw / 2f, groundY - 220f)
        canvas.drawRoundRect(rect, 12f, 12f, sign); canvas.drawRoundRect(rect, 12f, 12f, ink)
        canvas.drawTr(label, cx, rect.centerY() + 15f, text)
    }

    private fun drawPalm(canvas: Canvas, x: Float, baseY: Float) {
        path.reset(); path.moveTo(x, baseY); path.quadTo(x + 40f, baseY - 220f, x + 10f, baseY - 400f)
        canvas.drawPath(path, palmTrunk)
        for (k in 0 until 5) {
            canvas.save(); canvas.rotate(200f + k * 35f + 90f + sin(timer + k) * 3f, x + 10f, baseY - 400f)
            canvas.drawOval(x - 20f, baseY - 420f, x + 40f, baseY - 260f, palmLeaf); canvas.drawOval(x - 20f, baseY - 420f, x + 40f, baseY - 260f, ink)
            canvas.restore()
        }
    }

    /** 바닷가 오두막 가게: 나무 벽 + 색 지붕 + 문. 닫힌 가게는 널빤지로 X 막혀 있고 '닫힘', 연 가게는 문이 열리고 가게 사람 셋 */
    private fun drawHut(canvas: Canvas, i: Int, cx: Float, baseY: Float, open: Boolean) {
        val tw = 380f; val th = 320f
        fill.color = Color.rgb(214, 168, 112)
        rect.set(cx - tw / 2f, baseY - th, cx + tw / 2f, baseY)
        canvas.drawRect(rect, fill); canvas.drawRect(rect, ink)
        for (s in 1..3) canvas.drawLine(cx - tw / 2f, baseY - th * s / 4f, cx + tw / 2f, baseY - th * s / 4f, ink)
        fill.color = ROOF_COLORS[i % ROOF_COLORS.size]
        path.reset(); path.moveTo(cx - tw / 2f - 50f, baseY - th + 10f); path.lineTo(cx, baseY - th - 160f); path.lineTo(cx + tw / 2f + 50f, baseY - th + 10f); path.close()
        canvas.drawPath(path, fill); canvas.drawPath(path, ink)
        rect.set(cx - 70f, baseY - 210f, cx + 70f, baseY)
        fill.color = if (open) Color.rgb(64, 46, 40) else Color.rgb(150, 100, 60)
        canvas.drawRect(rect, fill); canvas.drawRect(rect, ink)
        if (!open) {
            canvas.drawLine(cx - 90f, baseY - 190f, cx + 90f, baseY - 20f, board)
            canvas.drawLine(cx + 90f, baseY - 190f, cx - 90f, baseY - 20f, board)
        } else {
            // 구해 준 사람들이 가게를 지킨다
            val man = SpriteManager.playerIdle[0]
            val mh = 200f; val mw = man.width * mh / man.height
            for (k in 0 until 3) {
                val mx = cx - 120f + k * 120f + sin(timer * 2f + k) * 5f
                rect.set(mx - mw / 2f, baseY + 30f - mh, mx + mw / 2f, baseY + 30f)
                canvas.drawBitmap(man, null, rect, null)
            }
        }
        text.textSize = 32f
        val label = if (open) "${i + 1}번 가게" else "${i + 1}번 가게 · 닫힘"
        val lw = text.measureTr(label) + 30f
        rect.set(cx - lw / 2f, baseY - th - 60f, cx + lw / 2f, baseY - th - 8f)
        canvas.drawRoundRect(rect, 10f, 10f, sign); canvas.drawRoundRect(rect, 10f, 10f, ink)
        canvas.drawTr(label, cx, rect.centerY() + 11f, text)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val idx = event.actionIndex
            when (com.jamcat.nayngapo.ui.MenuButtons.hitTest(event.getX(idx), event.getY(idx))) {
                com.jamcat.nayngapo.ui.MenuButtons.Hit.PAUSE -> { paused = !paused; touchControls.releaseAll(); return true }
                com.jamcat.nayngapo.ui.MenuButtons.Hit.EXIT -> { touchControls.releaseAll(); gameView.showFishing(atSeaEnd = true); return true }
                null -> Unit
            }
            val shop = nearShop
            if (!paused && shop >= 0 && !enterRect.isEmpty && enterRect.contains(event.getX(idx), event.getY(idx))) {
                touchControls.releaseAll(); GameProgress.saveVillageX(2, player.x)
                val (title, ids) = ShopCatalog.village2Shops[shop]
                gameView.showCatShop(title = title, ids = ids, onLeave = { gameView.showVillage2() })
                return true
            }
        }
        if (paused) return true
        return touchControls.onTouchEvent(event, gameView.screenWidth, gameView.screenHeight)
    }

    override fun onBack(): Boolean {
        paused = !paused
        touchControls.releaseAll()
        return true
    }

    companion object {
        /** 가게 수 · 항구 자리 · 첫 가게 · 간격 (Claude 판단) */
        const val SHOP_COUNT = 5
        private const val HARBOR_X = 300f
        private const val FIRST_SHOP_X = 1500f
        private const val SHOP_GAP = 900f
        private val ROOF_COLORS = intArrayOf(
            Color.rgb(90, 160, 220), Color.rgb(240, 120, 90), Color.rgb(120, 200, 120), Color.rgb(250, 190, 70), Color.rgb(190, 130, 220)
        )
    }
}
