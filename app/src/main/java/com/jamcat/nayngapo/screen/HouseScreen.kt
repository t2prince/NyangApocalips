package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CatTeam
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.engine.Terrain
import com.jamcat.nayngapo.entity.Player
import com.jamcat.nayngapo.ui.TouchControls
import kotlin.math.abs
import kotlin.math.sin

/**
 * 집 안 — 상점 마을 맨 앞 집에서 '집 들어가기'로 들어온다 (대표님 2026-10-09).
 * **집 안에서도 걸어 다닌다**(패드·점프). 1층: 왼쪽 문(밖으로) · 가운데 캣타워(누르면 캣타워 = 고양이 관리) · 오른쪽 계단(위층).
 * **위층에는 내 냥이들이 있는 만큼 다 있고, 머리 위에 이름이 적혀 있다.** 냥이들은 제자리에서 어슬렁거린다.
 * **출전 중인 냥이는 위층에 없고, 대신 집 안에서 인간 뒤를 졸졸 따라다닌다** (대표님 2026-10-09).
 * **집 안에는 가구들도 있다**(2026-10-09, GPT 그림 furn_*): 1층 책장·소파·스탠드·식탁·벽난로·화분, 위층 냥이 침대·소파·스탠드·책장·화분.
 * (방 크기·배치·벽지 색·어슬렁거림·가구 고르기는 Claude 판단, 벽·바닥·문·계단은 코드로 그렸다)
 */
class HouseScreen(private val gameView: GameView, private val floor: Int, startX: Float?) : Screen {

    private val touchControls = TouchControls()
    private val player: Player
    private var timer = 0f
    private var cameraX = 0f

    /** 위층의 냥이들 — 있는 만큼 (떠났거나 무덤인 냥이는 없다) */
    private class Resident(val cat: CatTeam.TeamCat, val sprite: Bitmap, var x: Float, val home: Float) {
        var dir = if (Math.random() < 0.5) 1f else -1f
        var wait = (Math.random() * 2.0).toFloat()
        var phase = (Math.random() * 6.0).toFloat()
    }
    private val residents = mutableListOf<Resident>()

    private val worldWidth: Float

    /** 출전 중인 냥이 — 인간 뒤를 졸졸 따라다닌다 */
    private val followers = mutableListOf<com.jamcat.nayngapo.entity.AllyCat>()

    /** 문·캣타워·계단 — 앞에 서면 머리 위에 말풍선이 뜨고 눌러야 쓴다 */
    private class Spot(val x: Float, val label: String, val action: () -> Unit)
    private val spots = mutableListOf<Spot>()
    private var nearSpot: Spot? = null

    init {
        Terrain.stage = Terrain.SHOP   // 집 안 바닥은 평평하다
        if (floor == 0) {
            worldWidth = GROUND_W
            spots.add(Spot(DOOR_X, "◀ 밖으로 나가기") { gameView.showShop() })
            spots.add(Spot(TOWER_X, "▼ 캣타워") { returnX = player.x; returnFloor = 0; gameView.showTeam(fromHouse = true) })
            spots.add(Spot(STAIRS_X, "▲ 위층 올라가기") { gameView.showHouse(1, UP_STAIRS_X) })
        } else {
            val cats = CatTeam.cats.filter { !it.gone && it !in CatTeam.party }   // 출전 중인 냥이는 위층에 없다
            worldWidth = maxOf(GROUND_W, 420f + cats.size * CAT_GAP + 520f)
            // 다 큰 냥이는 줄지어, 새끼 냥이들은 맨 뒤에 서로 모여 있다 (대표님 2026-10-09 「새끼 냥이들은 집에서 서로 모여 떠들어」)
            val adults = cats.filter { !it.isKitten }; val kittens = cats.filter { it.isKitten }
            var hx = 420f
            for (c in adults + kittens) {
                val sprite = com.jamcat.nayngapo.entity.AllyCat(0f, 0f, c.kind, level = c.level, teamId = c.id,
                    armored = c.hasArmor, attackBonus = c.attackBonus, coat = c.coat).spriteNow()
                residents.add(Resident(c, sprite, hx, hx))
                hx += if (c.isKitten) KITTEN_GAP else CAT_GAP
            }
            spots.add(Spot(UP_STAIRS_X, "▼ 아래층 내려가기") { gameView.showHouse(0, STAIRS_X - 60f) })
        }
        // 소파에 앉을 수 있다 (대표님 2026-10-10 「집에 있는 소파에 앉을 수 있어」)
        spots.add(Spot(sofaX, "▼ 소파에 앉기") { sit() })
        player = Player(startX ?: (DOOR_X + 220f), Constants.GROUND_Y)
        player.facingRight = true
        for ((i, c) in CatTeam.party.withIndex()) {
            followers.add(com.jamcat.nayngapo.entity.AllyCat(
                (player.x - 90f - 70f * i).coerceAtLeast(20f), Constants.GROUND_Y,
                c.kind, level = c.level, teamId = c.id, armored = c.hasArmor, attackBonus = c.attackBonus, coat = c.coat,
                speedMul = CatTeam.speedMultiplier(c), hpBonus = CatTeam.hpBonusOf(c), kitten = c.isKitten))
        }
    }

    // ===== 소파에 앉기 =====
    /** 이 층 소파 가운데 x · 그림 높이 */
    private val sofaX: Float get() = (if (floor == 0) GROUND_FURNITURE else UP_FURNITURE).first { it.first == "sofa" }.second
    private val sofaH: Float get() = (if (floor == 0) GROUND_FURNITURE else UP_FURNITURE).first { it.first == "sofa" }.third
    private var sitting = false
    private val standSpot by lazy { Spot(sofaX, "▲ 일어나기") { stand() } }

    /** 소파 가운데에 앉는다: 자세는 고정(차에 앉을 때처럼), 다리는 소파 앞판이 가린다 */
    private fun sit() {
        sitting = true
        touchControls.releaseAll()
        player.setMovement(0f, 0f)
        player.x = sofaX - player.width / 2f
        player.facingRight = true
        player.seatedInCar = true
        player.rideLift = SIT_LIFT
    }

    private fun stand() {
        sitting = false
        player.seatedInCar = false
        player.rideLift = 0f
    }

    override fun update(deltaTime: Float) {
        timer += deltaTime
        touchControls.skills = emptyList()
        touchControls.skillCooldowns = FloatArray(0)
        touchControls.humanSkills = emptyList()
        touchControls.humanSkillCooldowns = FloatArray(0)
        touchControls.showOrder = false
        touchControls.specialCooldown = player.specialCooldown

        val input = touchControls.inputState
        input.useSkill = null; input.useHumanSkill = null; input.cycleOrder = false; input.special = false
        var dirX = 0f
        if (input.moveLeft) dirX -= 1f
        if (input.moveRight) dirX += 1f
        // 앉아 있을 때 좌우를 누르면 일어난다
        if (sitting && dirX != 0f) stand()
        if (sitting) { dirX = 0f; input.jump = false }
        player.setMovement(dirX, 0f)
        if (input.jump || input.moveUp) { player.jump(); input.jump = false }
        if (input.attack) { player.performAttack(); input.attack = false }
        player.update(deltaTime)
        player.x = player.x.coerceIn(40f, worldWidth - player.width - 40f)
        for (f in followers) {
            f.update(deltaTime, player.x, player.y, emptyList())
            f.x = f.x.coerceIn(10f, worldWidth - f.width - 10f)
        }

        // 냥이들은 제 자리 근처를 천천히 오가다 멈췄다 한다
        for (r in residents) {
            r.phase += deltaTime
            if (r.wait > 0f) { r.wait -= deltaTime; continue }
            // 새끼들은 모인 자리에서 조금만 꼼지락
            val range = if (r.cat.isKitten) 25f else 60f
            r.x += r.dir * 60f * deltaTime
            if (abs(r.x - r.home) > range) { r.x = r.home + range * kotlin.math.sign(r.x - r.home); r.dir = -r.dir; r.wait = 1f + (Math.random() * 2.0).toFloat() }
            else if (Math.random() < deltaTime * 0.3) r.wait = 1f + (Math.random() * 1.5).toFloat()
        }
        // 새끼 냥이 수다: 하나씩 돌아가며 '내가 ○○를 했다냥!' 하고 떠든다
        val kittens = residents.filter { it.cat.isKitten }
        if (kittens.size >= 1) {
            chatTimer -= deltaTime
            if (chatTimer <= 0f) {
                chatTimer = CHAT_TIME
                chatter = kittens[(kittens.indexOf(chatter) + 1) % kittens.size]
                // 한 마리면 혼잣말, 여럿이면 번갈아 — 문구는 무작위 (그 냥이 레벨 자랑도 섞인다)
                val c = chatter!!.cat
                val pool = CHATS + listOf("내가 레벨 ${c.level}이 됐다냥!")
                chatText = pool[(Math.random() * pool.size).toInt()]
            }
        }

        val sw = gameView.screenWidth.toFloat()
        cameraX = (player.x + player.width / 2f - sw * 0.4f).coerceIn(0f, (worldWidth - sw).coerceAtLeast(0f))
        val pcx = player.x + player.width / 2f
        nearSpot = if (sitting) standSpot
            else spots.firstOrNull { abs(pcx - it.x) < (if (it.x == sofaX) 110f else 170f) }
    }

    // ===== 그림 =====
    private val wallPaint = Paint()
    private val wainscot = Paint().apply { isAntiAlias = true }
    private val floorPaint = Paint().apply { color = Color.rgb(178, 120, 72) }
    private val floorLine = Paint().apply { color = Color.rgb(140, 90, 52); strokeWidth = 3f }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 5f; strokeJoin = Paint.Join.ROUND; isAntiAlias = true }
    private val fill = Paint().apply { isAntiAlias = true }
    private val glass = Paint().apply { color = Color.rgb(170, 216, 246); isAntiAlias = true }
    private val shadowPaint = Paint().apply { color = Color.argb(60, 0, 0, 0); isAntiAlias = true }
    private val namePaint = Paint().apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 30f; isAntiAlias = true
    }
    private val nameBg = Paint().apply { color = Color.argb(170, 40, 30, 26); isAntiAlias = true }
    private val bubblePaint = Paint().apply { color = Color.rgb(255, 250, 230); isAntiAlias = true }
    private val bubbleText = Paint().apply { color = Color.rgb(50, 40, 36); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 44f; isAntiAlias = true }
    private val hudBg = Paint().apply { color = Color.argb(150, 20, 16, 14); isAntiAlias = true }
    private val hudText = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 40f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val btnPaint = Paint().apply { color = Color.argb(165, 34, 18, 22); isAntiAlias = true }
    private val btnIcon = Paint().apply { color = Color.argb(230, 255, 150, 140); strokeWidth = 4.5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true }
    private val btnLabel = Paint().apply { color = Color.argb(150, 226, 232, 244); textSize = 20f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    private val rect = RectF()
    private val enterRect = RectF()
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        val gy = Constants.GROUND_Y

        // 벽지 (1층 크림, 위층 하늘빛) · 아래 판자 · 나무 바닥
        wallPaint.color = if (floor == 0) Color.rgb(250, 238, 210) else Color.rgb(224, 236, 248)
        canvas.drawRect(0f, 0f, w, gy, wallPaint)
        wainscot.color = if (floor == 0) Color.rgb(214, 170, 120) else Color.rgb(190, 160, 200)
        canvas.drawRect(0f, gy - 130f, w, gy - 30f, wainscot)
        canvas.drawRect(0f, gy - 30f, w, h, floorPaint)
        var lx = -(cameraX % 160f)
        while (lx < w) { canvas.drawLine(lx, gy - 30f, lx, h, floorLine); lx += 160f }
        canvas.drawLine(0f, gy - 30f, w, gy - 30f, ink)

        canvas.save()
        canvas.translate(-cameraX, 0f)
        // 창문 (벽 위쪽에 띄엄띄엄)
        var wx = 600f
        while (wx < worldWidth - 200f) {
            if (abs(wx - TOWER_X) > 260f && abs(wx - STAIRS_X) > 300f) drawWindow(canvas, wx, gy - 520f)
            wx += 760f
        }
        // 가구 (벽 앞, 냥이·인간보다 뒤)
        for ((name, fx, fh) in if (floor == 0) GROUND_FURNITURE else UP_FURNITURE) {
            if (fx > worldWidth - 150f) continue
            val bmp = SpriteManager.furniture[name] ?: continue
            val fw = bmp.width * fh / bmp.height
            if (fx + fw / 2f < cameraX - 50f || fx - fw / 2f > cameraX + w + 50f) continue
            rect.set(fx - fw / 2f, gy - 20f - fh, fx + fw / 2f, gy - 20f)
            canvas.drawBitmap(bmp, null, rect, null)
        }
        if (floor == 0) {
            drawDoor(canvas, DOOR_X, gy)
            SpriteManager.shopIcons["cat_tower"]?.let { bmp ->
                val th = 520f; val tw = bmp.width * th / bmp.height
                rect.set(TOWER_X - tw / 2f, gy + 10f - th, TOWER_X + tw / 2f, gy + 10f)
                canvas.drawBitmap(bmp, null, rect, null)
            }
            drawStairsUp(canvas, STAIRS_X, gy)
        } else {
            drawStairsDown(canvas, UP_STAIRS_X, gy)
            for (r in residents) drawResident(canvas, r, gy)
        }
        for (f in followers) f.draw(canvas)
        // 앉아 있으면 앉는 자리 아래(다리)는 안 그린다
        canvas.save()
        if (sitting) canvas.clipRect(-1e6f, -1e6f, 1e6f, gy - 20f - sofaH + sofaH * SOFA_SEAT + 12f)
        player.draw(canvas)
        canvas.restore()
        // 그리고 소파 앞판(앉는 자리 아래)을 인간 위에 한 번 더 그린다
        if (sitting) SpriteManager.furniture["sofa"]?.let { bmp ->
            val fh = sofaH; val fw = bmp.width * fh / bmp.height
            rect.set(sofaX - fw / 2f, gy - 20f - fh, sofaX + fw / 2f, gy - 20f)
            canvas.save()
            canvas.clipRect(rect.left, rect.top + fh * SOFA_SEAT, rect.right, rect.bottom)
            canvas.drawBitmap(bmp, null, rect, null)
            canvas.restore()
        }
        canvas.restore()

        // 말풍선 — 눌러야 들어간다
        enterRect.setEmpty()
        nearSpot?.let { s ->
            val bx = s.x - cameraX
            val bw = bubbleText.measureText(s.label) + 60f
            val by = gy - 360f + sin(timer * 4f) * 6f
            enterRect.set(bx - bw / 2f, by - 46f, bx + bw / 2f, by + 30f)
            canvas.drawRoundRect(enterRect, 38f, 38f, bubblePaint)
            canvas.drawRoundRect(enterRect, 38f, 38f, ink)
            canvas.drawText(s.label, bx, enterRect.centerY() + 15f, bubbleText)
        }

        // 위 가운데: 어느 층인지
        val info = if (floor == 0) "집 · 1층" else "집 · 위층 — 냥이 ${residents.size}마리 (출전 중인 냥이는 따라다녀요)"
        val iw = hudText.measureText(info) + 60f
        rect.set(w / 2f - iw / 2f, 24f, w / 2f + iw / 2f, 92f)
        canvas.drawRoundRect(rect, 34f, 34f, hudBg)
        canvas.drawText(info, w / 2f, 72f, hudText)

        touchControls.draw(canvas, w.toInt(), h.toInt())
        // 왼쪽 위: 나가기 (상점 마을 집 앞으로)
        canvas.drawCircle(EXIT_BTN_X, MENU_BTN_Y, MENU_BTN_R, btnPaint)
        canvas.drawLine(EXIT_BTN_X - 14f, MENU_BTN_Y - 14f, EXIT_BTN_X + 14f, MENU_BTN_Y + 14f, btnIcon)
        canvas.drawLine(EXIT_BTN_X + 14f, MENU_BTN_Y - 14f, EXIT_BTN_X - 14f, MENU_BTN_Y + 14f, btnIcon)
        canvas.drawText("나가기", EXIT_BTN_X, MENU_BTN_Y + MENU_BTN_R + 30f, btnLabel)
    }

    private fun drawResident(canvas: Canvas, r: Resident, gy: Float) {
        val moving = r.wait <= 0f
        val bmp = if (r.dir > 0f) r.sprite else SpriteManager.getFlipped(r.sprite)
        val hop = if (moving) abs(sin(r.phase * 10f)) * 8f else 0f
        val k = if (r.cat.isKitten) com.jamcat.nayngapo.entity.AllyCat.KITTEN_SCALE else 1f   // 새끼는 작게
        val cw = bmp.width * k; val ch = bmp.height * k
        rect.set(r.x - cw * 0.4f, gy - 8f, r.x + cw * 0.4f, gy + 8f)
        canvas.drawOval(rect, shadowPaint)
        rect.set(r.x - cw / 2f, gy - ch - hop, r.x + cw / 2f, gy - hop)
        canvas.drawBitmap(bmp, null, rect, null)
        // 머리 위 이름
        val name = r.cat.name
        val nw = namePaint.measureText(name) + 28f
        val ny = gy - ch - 26f
        rect.set(r.x - nw / 2f, ny - 34f, r.x + nw / 2f, ny + 8f)
        canvas.drawRoundRect(rect, 18f, 18f, nameBg)
        canvas.drawText(name, r.x, ny - 2f, namePaint)
        // 떠드는 새끼 냥이: 이름 위 말풍선 (나타날 때 살짝 튄다)
        if (r === chatter && chatTimer > 0.3f) {
            val pop = 1f + 0.12f * ((chatTimer - (CHAT_TIME - 0.25f)) / 0.25f).coerceIn(0f, 1f)
            chatPaint.textSize = 30f * pop
            val bw = chatPaint.measureText(chatText) + 36f
            val by = ny - 48f
            rect.set(r.x - bw / 2f, by - 50f * pop, r.x + bw / 2f, by)
            canvas.drawRoundRect(rect, 22f, 22f, bubblePaint)
            canvas.drawRoundRect(rect, 22f, 22f, ink)
            path.reset()
            path.moveTo(r.x - 12f, by - 2f); path.lineTo(r.x + 2f, by + 18f); path.lineTo(r.x + 14f, by - 2f); path.close()
            canvas.drawPath(path, bubblePaint)
            canvas.drawText(chatText, r.x, by - 25f * pop + 11f, chatPaint)
        }
    }
    private var chatter: Resident? = null
    private var chatText = ""
    private var chatTimer = 0.5f
    private val chatPaint = Paint().apply { color = Color.rgb(50, 40, 36); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }

    private fun drawWindow(canvas: Canvas, cx: Float, top: Float) {
        rect.set(cx - 110f, top, cx + 110f, top + 200f)
        fill.color = Color.rgb(150, 100, 60)
        canvas.drawRect(rect.left - 12f, rect.top - 12f, rect.right + 12f, rect.bottom + 12f, fill)
        canvas.drawRect(rect, glass)
        canvas.drawRect(rect, ink)
        canvas.drawLine(cx, rect.top, cx, rect.bottom, ink)
        canvas.drawLine(rect.left, rect.centerY(), rect.right, rect.centerY(), ink)
    }

    private fun drawDoor(canvas: Canvas, cx: Float, gy: Float) {
        val dw = 180f; val dh = 330f
        path.reset()
        path.moveTo(cx - dw / 2f, gy - 30f); path.lineTo(cx - dw / 2f, gy - dh + 60f)
        path.quadTo(cx - dw / 2f, gy - dh, cx, gy - dh); path.quadTo(cx + dw / 2f, gy - dh, cx + dw / 2f, gy - dh + 60f)
        path.lineTo(cx + dw / 2f, gy - 30f); path.close()
        fill.color = Color.rgb(140, 84, 44)
        canvas.drawPath(path, fill); canvas.drawPath(path, ink)
        canvas.drawLine(cx, gy - dh, cx, gy - 30f, ink)
        fill.color = Color.rgb(250, 210, 60)
        canvas.drawCircle(cx + dw * 0.32f, gy - dh * 0.45f, 10f, fill)
    }

    /** 1층 계단: 오른쪽 위로 올라가 천장으로 사라진다 */
    private fun drawStairsUp(canvas: Canvas, cx: Float, gy: Float) {
        val steps = 9; val sw = 46f; val sh = 62f
        val left = cx - 160f
        fill.color = Color.rgb(196, 140, 86)
        for (i in 0 until steps) {
            rect.set(left + i * sw, gy - 30f - (i + 1) * sh, left + (i + 1) * sw + 60f, gy - 30f - i * sh)
            canvas.drawRect(rect.left, rect.top, rect.right, gy - 30f, fill)
            canvas.drawLine(rect.left, rect.top, rect.right, rect.top, ink)
            canvas.drawLine(rect.left, rect.top, rect.left, rect.bottom, ink)
        }
        // 난간
        canvas.drawLine(left + 20f, gy - 30f - sh * 2.4f, left + steps * sw + 40f, gy - 30f - (steps + 1.4f) * sh, ink)
    }

    /** 위층 계단: 바닥에 뚫린 구멍으로 내려간다 */
    private fun drawStairsDown(canvas: Canvas, cx: Float, gy: Float) {
        rect.set(cx - 150f, gy - 34f, cx + 150f, gy - 4f)
        fill.color = Color.rgb(70, 46, 30)
        canvas.drawRect(rect, fill)
        canvas.drawRect(rect, ink)
        fill.color = Color.rgb(196, 140, 86)
        for (i in 0 until 3) canvas.drawRect(cx - 140f + i * 40f, gy - 30f + i * 7f, cx + 140f, gy - 24f + i * 7f, fill)
        // 난간 기둥
        canvas.drawLine(cx + 150f, gy - 34f, cx + 150f, gy - 160f, ink)
        canvas.drawLine(cx - 150f, gy - 160f, cx + 150f, gy - 160f, ink)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val idx = event.actionIndex
            val px = event.getX(idx); val py = event.getY(idx)
            if (kotlin.math.hypot(px - EXIT_BTN_X, py - MENU_BTN_Y) < MENU_HIT_R) {
                touchControls.releaseAll(); gameView.showShop(); return true
            }
            val s = nearSpot
            if (s != null && !enterRect.isEmpty && enterRect.contains(px, py)) {
                touchControls.releaseAll(); s.action(); return true
            }
        }
        return touchControls.onTouchEvent(event, gameView.screenWidth, gameView.screenHeight)
    }

    /** 뒤로가기 = 상점 마을 집 앞으로 */
    override fun onBack(): Boolean {
        touchControls.releaseAll()
        gameView.showShop()
        return true
    }

    companion object {
        /** 소파: 그림 위에서부터 앉는 자리(방석 윗면)까지 비율 · 앉으면 인간을 들어 올리는 양 (그림에 맞춰 Claude 판단) */
        private const val SOFA_SEAT = 0.52f
        private const val SIT_LIFT = 60f
        private const val GROUND_W = 2800f
        private const val DOOR_X = 220f
        private const val TOWER_X = 1120f
        private const val STAIRS_X = 2400f
        /** 가구 (이름, 가운데 x, 그림 높이) */
        private val GROUND_FURNITURE = listOf(
            Triple("shelf", 480f, 330f), Triple("sofa", 730f, 230f), Triple("lamp", 915f, 340f),
            Triple("table", 1480f, 220f), Triple("fireplace", 1840f, 300f), Triple("plant", 2100f, 260f),
        )
        private val UP_FURNITURE = listOf(
            Triple("catbeds", 650f, 250f), Triple("sofa", 1160f, 230f), Triple("lamp", 1400f, 340f),
            Triple("shelf", 1720f, 330f), Triple("plant", 2020f, 260f),
        )
        private const val UP_STAIRS_X = 200f
        private const val CAT_GAP = 190f
        /** 새끼 냥이들은 다닥다닥 모여 있다 · 한 마리가 말하는 시간 */
        private const val KITTEN_GAP = 95f
        private const val CHAT_TIME = 2.6f
        /** 새끼 냥이 수다 문구 (대표님 예: 「내가 __를 했다냥!」 — 채우는 말은 Claude 판단) */
        private val CHATS = listOf(
            "내가 쥐를 잡았다냥!", "내가 츄르를 먹었다냥!", "내가 공을 굴렸다냥!", "내가 낮잠을 잤다냥!",
            "내가 나비를 쫓았다냥!", "내가 캣타워에 올랐다냥!", "내가 털실을 풀었다냥!", "내가 꼬리를 잡았다냥!",
            "내가 상자에 숨었다냥!", "내가 엄마랑 놀았다냥!", "내가 생선을 훔쳤다냥!", "내가 제일 빨리 뛰었다냥!",
        )
        private const val MENU_BTN_R = 46f
        private const val MENU_HIT_R = 60f
        private const val MENU_BTN_Y = 118f
        private const val EXIT_BTN_X = 110f

        /** 캣타워 화면에서 돌아오면 그 자리(층·x)부터 */
        var returnFloor = 0
        var returnX: Float? = null
    }
}
