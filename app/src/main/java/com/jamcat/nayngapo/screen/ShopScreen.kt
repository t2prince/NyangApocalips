package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.engine.Terrain
import com.jamcat.nayngapo.entity.Player
import com.jamcat.nayngapo.ui.TouchControls
import kotlin.math.abs
import kotlin.math.sin

/**
 * 상점 마을 (로비 오른쪽 '상점' 칸, 대표님 2026-09-27):
 * **스테이지처럼 인간이 걸어 다니며 가게로 간다.** 평평한 초원 위에 천막 상점들이 줄지어 있고,
 * **인간을 한 명 구할 때마다 천막 하나의 문이 열린다** (구한 인간이 없으면 전부 닫힘).
 * 열린 천막 앞에 서면 '들어가기' 말풍선이 뜨고, 누르면 그 가게에서 물건을 산다.
 * **고양이 상점도 천막 중 하나** — 1번 천막.
 *
 * 조작·버튼 구성은 스테이지와 똑같다(패드·공격·점프·필살기·기술·지시·멈추기·나가기, 기술은 여기선 안 써진다). 구한 인간 수는 GameProgress.humansRescued.
 * 천막 수(TENT_COUNT)와 2번~ 천막의 물건은 대표님이 아직 정하지 않았다 — 열린 천막 안은 '준비 중'.
 * (천막 6개·간격·색·고양이 상점을 1번에 둔 것은 Claude 임시 판단)
 */
class ShopScreen(private val gameView: GameView) : Screen {

    private val touchControls = TouchControls()
    private val player: Player

    private var timer = 0f
    private var paused = false
    private var cameraX = 0f

    // 버튼 구성은 스테이지와 똑같이 (대표님 2026-09-27): 배운 고양이 기술·인간 기술 버튼, 팀 지시, 필살기, 멈추기·나가기
    private val catSkills = com.jamcat.nayngapo.engine.CatSkills.all
        .filter { com.jamcat.nayngapo.engine.CatSkills.isActive(it.id) }.take(com.jamcat.nayngapo.ui.TouchControls.SKILL_SLOTS)
    private val humanSkills = com.jamcat.nayngapo.engine.HumanSkills.all
        .filter { com.jamcat.nayngapo.engine.HumanSkills.isLearned(it.id) }.take(com.jamcat.nayngapo.ui.TouchControls.HUMAN_SKILL_SLOTS)
    private var order = com.jamcat.nayngapo.entity.AllyOrder.FOLLOW
    private var noticeText = ""
    private var noticeTimer = 0f
    /** 지금 앞에 서 있는 천막 (없으면 -1) */
    private var nearTent = -1

    /** 출전시킨 팀 냥이는 상점 마을에도 인간을 따라온다 (대표님 2026-09-27 「출전시키기를 누르면 고양이와 같이 상점에 갈 수 있어」) */
    private val allies = mutableListOf<com.jamcat.nayngapo.entity.AllyCat>()

    init {
        Terrain.stage = Terrain.SHOP            // 상점 마을은 평평한 초원
        player = Player(START_X, Constants.GROUND_Y)
        player.facingRight = true
        // 땅 스테이지와 같이 날아냥이는 빼고, 인간 뒤에 줄지어 선다
        val party = com.jamcat.nayngapo.engine.CatTeam.party.filter { it.kind != com.jamcat.nayngapo.entity.AllyCat.Kind.FLYING }
        for ((i, cat) in party.withIndex()) {
            allies.add(
                com.jamcat.nayngapo.entity.AllyCat(
                    (START_X - 90f - 70f * i).coerceAtLeast(20f), Constants.GROUND_Y,
                    cat.kind, level = cat.level, teamId = cat.id,
                    armored = cat.hasArmor, attackBonus = cat.attackBonus, coat = cat.coat
                ).also { it.evolve(com.jamcat.nayngapo.engine.CatTeam.evolveSecondsLeft, grown = true) }   // 상점 마을에서도 거대하게
            )
        }
    }

    /** 열린 천막 수 = 구한 인간 수 (천막 수까지) */
    private val openCount: Int get() = GameProgress.humansRescued.coerceIn(0, TENT_COUNT)

    private fun tentX(i: Int) = FIRST_TENT_X + i * TENT_GAP

    private val worldWidth = FIRST_TENT_X + (TENT_COUNT - 1) * TENT_GAP + 700f

    // ===== 그림 도구 =====
    private val skyPaint = Paint()
    private var skyShader: Shader? = null
    private val cloudPaint = Paint().apply { color = Color.argb(235, 255, 255, 255); isAntiAlias = true }
    private val hillFar = Paint().apply { color = Color.rgb(150, 206, 120); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 5f; strokeJoin = Paint.Join.ROUND; isAntiAlias = true }
    private val fill = Paint().apply { isAntiAlias = true }
    private val shadowPaint = Paint().apply { color = Color.argb(60, 0, 0, 0); isAntiAlias = true }
    private val signPaint = Paint().apply { color = Color.rgb(250, 240, 214); isAntiAlias = true }
    private val signText = Paint().apply { color = Color.rgb(70, 46, 24); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val hudBg = Paint().apply { color = Color.argb(150, 20, 18, 22); isAntiAlias = true }
    private val hudText = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val bubblePaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val bubbleText = Paint().apply { color = Color.rgb(60, 44, 12); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val lockedBubble = Paint().apply { color = Color.rgb(250, 246, 236); isAntiAlias = true }
    private val noticeBg = Paint().apply { color = Color.rgb(255, 250, 225); isAntiAlias = true }
    private val noticeTextPaint = Paint().apply { color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }

    private val path = Path()
    private val rect = RectF()
    private val src = Rect()
    /** 화면 좌표의 '들어가기' 말풍선 (없으면 비어 있다) */
    private val enterRect = RectF()
    /** 화면 좌표의 천막 자리 — 천막을 눌러도 들어간다 */
    private val tentScreenRects = List(TENT_COUNT) { RectF() }

    override fun update(deltaTime: Float) {
        if (paused) return
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime

        touchControls.skills = catSkills
        touchControls.skillCooldowns = FloatArray(catSkills.size)
        touchControls.skillsHaveCaster = allies.isNotEmpty()
        touchControls.humanSkills = humanSkills
        touchControls.humanSkillCooldowns = FloatArray(humanSkills.size)
        touchControls.showOrder = allies.isNotEmpty()
        touchControls.orderLabel = order.label
        touchControls.specialCooldown = player.specialCooldown

        val input = touchControls.inputState
        // 상점 마을은 싸우는 곳이 아니라 기술은 쓰지 않는다 (누르면 알려 준다)
        if (input.useSkill != null || input.useHumanSkill != null) {
            input.useSkill = null; input.useHumanSkill = null
            showNotice("상점 마을에서는 기술을 쓸 수 없어요")
        }
        if (input.cycleOrder) {
            input.cycleOrder = false
            order = when (order) {
                com.jamcat.nayngapo.entity.AllyOrder.FOLLOW -> com.jamcat.nayngapo.entity.AllyOrder.ATTACK
                com.jamcat.nayngapo.entity.AllyOrder.ATTACK -> com.jamcat.nayngapo.entity.AllyOrder.HOLD
                com.jamcat.nayngapo.entity.AllyOrder.HOLD -> com.jamcat.nayngapo.entity.AllyOrder.FOLLOW
            }
            for (ally in allies) ally.order = order
        }
        if (input.special) { if (player.performSpecial()) SoundManager.playSpecial(); input.special = false }
        var dirX = 0f
        if (input.moveLeft) dirX -= 1f
        if (input.moveRight) dirX += 1f
        player.setMovement(dirX, 0f)
        if (input.jump || input.moveUp) { player.jump(); input.jump = false }
        if (input.attack) { if (player.performAttack()) SoundManager.playAttack(); input.attack = false }
        player.update(deltaTime)
        player.x = player.x.coerceIn(40f, worldWidth - player.width - 40f)
        for (ally in allies) {
            ally.update(deltaTime, player.x, player.y, emptyList())
            ally.x = ally.x.coerceIn(10f, worldWidth - ally.width - 10f)
        }

        // 카메라: 인간을 화면 왼쪽 3분의 1쯤에 두고 따라간다
        val sw = gameView.screenWidth.toFloat()
        cameraX = (player.x + player.width / 2f - sw * 0.4f).coerceIn(0f, (worldWidth - sw).coerceAtLeast(0f))

        // 인간이 서 있는 천막
        val pcx = player.x + player.width / 2f
        nearTent = (0 until TENT_COUNT).firstOrNull { abs(pcx - tentX(it)) < TENT_W * 0.42f } ?: -1
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        val groundY = Constants.GROUND_Y

        // 하늘
        if (skyShader == null) skyShader = LinearGradient(0f, 0f, 0f, groundY, Color.rgb(64, 165, 235), Color.rgb(206, 236, 252), Shader.TileMode.CLAMP)
        skyPaint.shader = skyShader
        canvas.drawRect(0f, 0f, w, groundY, skyPaint)
        // 구름 (느린 원경)
        for (i in 0 until 6) {
            val cx = ((i * 520f - cameraX * 0.15f) % (w + 400f) + w + 400f) % (w + 400f) - 200f
            val cy = 120f + (i % 3) * 60f
            canvas.drawCircle(cx, cy, 46f, cloudPaint)
            canvas.drawCircle(cx + 52f, cy - 18f, 58f, cloudPaint)
            canvas.drawCircle(cx + 112f, cy, 44f, cloudPaint)
        }
        // 멀리 낮은 언덕 (원경)
        var hx = -((cameraX * 0.3f) % 900f) - 450f
        while (hx < w + 900f) {
            rect.set(hx, groundY - 170f, hx + 900f, groundY + 170f)
            canvas.drawOval(rect, hillFar)
            hx += 900f
        }
        // 평평한 초원 바닥 — 스테이지와 같은 잔디 타일
        val bmp = SpriteManager.groundTile
        val top = groundY - 42f
        val stripH = (h - top).coerceAtLeast(80f)
        val tileW = stripH * bmp.width / bmp.height
        var tx = -(cameraX % tileW)
        while (tx < w) {
            rect.set(tx, top, tx + tileW + 1f, top + stripH)
            canvas.drawBitmap(bmp, null, rect, null)
            tx += tileW
        }

        // ----- 월드 (카메라) -----
        canvas.save()
        canvas.translate(-cameraX, 0f)
        for (i in 0 until TENT_COUNT) {
            val cx = tentX(i)
            if (cx + TENT_W < cameraX - 50f || cx - TENT_W > cameraX + w + 50f) continue
            drawTent(canvas, i, cx, groundY, TENT_W, TENT_H, i < openCount)
        }
        // 열린 천막 앞에는 가게 주인(구해 온 인간)이 서서 광고를 한다 (대표님 2026-09-27)
        for (i in 0 until openCount) {
            val cx = tentX(i)
            if (cx + TENT_W < cameraX - 50f || cx - TENT_W > cameraX + w + 50f) continue
            drawOwner(canvas, i, cx + TENT_W * 0.40f, groundY)
        }
        for (ally in allies) ally.draw(canvas)
        player.draw(canvas)
        canvas.restore()

        for (i in 0 until TENT_COUNT) {
            val cx = tentX(i) - cameraX
            tentScreenRects[i].set(cx - TENT_W / 2f, groundY - TENT_H, cx + TENT_W / 2f, groundY)
        }

        // 천막 앞에 서면 머리 위 말풍선: 열렸으면 '들어가기', 닫혔으면 몇 명 더 구해야 하는지
        enterRect.setEmpty()
        if (nearTent >= 0) {
            val bx = tentX(nearTent) - cameraX
            val by = groundY - TENT_H - 70f
            if (nearTent < openCount) {
                val label = "▼ ${tentName(nearTent)} 들어가기"
                bubbleText.textSize = 44f
                val bw = bubbleText.measureText(label) + 60f
                val bob = sin(timer * 4f) * 6f
                enterRect.set(bx - bw / 2f, by - 46f + bob, bx + bw / 2f, by + 30f + bob)
                canvas.drawRoundRect(enterRect, 38f, 38f, bubblePaint)
                canvas.drawRoundRect(enterRect, 38f, 38f, ink)
                canvas.drawText(label, bx, enterRect.centerY() + 15f, bubbleText)
            } else {
                val need = nearTent + 1 - GameProgress.humansRescued
                val label = "잠겨 있어요 · 인간을 ${need}명 더 구하면 열려요"
                bubbleText.textSize = 36f
                val bw = bubbleText.measureText(label) + 50f
                rect.set(bx - bw / 2f, by - 40f, bx + bw / 2f, by + 24f)
                canvas.drawRoundRect(rect, 30f, 30f, lockedBubble)
                canvas.drawRoundRect(rect, 30f, 30f, ink)
                canvas.drawText(label, bx, rect.centerY() + 12f, bubbleText)
            }
        }

        // 위 가운데: 구한 인간 · 열린 가게
        hudText.textSize = 40f
        val info = if (openCount == 0) "인간을 구하면 가게 문이 열려요!"
            else "구한 인간 ${GameProgress.humansRescued}명 · 열린 가게 $openCount / $TENT_COUNT"
        val iw = hudText.measureText(info) + 60f
        rect.set(w / 2f - iw / 2f, 24f, w / 2f + iw / 2f, 92f)
        canvas.drawRoundRect(rect, 34f, 34f, hudBg)
        canvas.drawText(info, w / 2f, 72f, hudText)

        // 스테이지와 같은 패드·버튼
        touchControls.draw(canvas, w.toInt(), h.toInt())
        // 왼쪽 위: 스테이지와 같은 멈추기·나가기
        drawMenuButtons(canvas)

        if (paused) {
            canvas.drawRect(0f, 0f, w, h, pauseVeil)
            canvas.drawText("PAUSED", w / 2f, h / 2f - 6f, pauseText)
            canvas.drawText("멈추기 버튼을 다시 누르면 이어서 진행합니다", w / 2f, h / 2f + 44f, pauseHint)
            drawMenuButtons(canvas)
            return
        }
        if (noticeTimer > 0f) {
            noticeTextPaint.textSize = 42f
            val nw = noticeTextPaint.measureText(noticeText) + 70f
            rect.set(w / 2f - nw / 2f, h * 0.30f, w / 2f + nw / 2f, h * 0.30f + 80f)
            canvas.drawRoundRect(rect, 24f, 24f, noticeBg)
            canvas.drawRoundRect(rect, 24f, 24f, ink)
            canvas.drawText(noticeText, w / 2f, rect.centerY() + 15f, noticeTextPaint)
        }
    }

    // ===== 스테이지와 같은 멈추기·나가기 버튼 (GamePlayScreen 과 같은 자리·모양) =====
    private val menuBtnPaint = Paint().apply { color = Color.argb(165, 16, 18, 26); isAntiAlias = true }
    private val exitBtnPaint = Paint().apply { color = Color.argb(165, 34, 18, 22); isAntiAlias = true }
    private val menuBtnEdgePaint = Paint().apply {
        color = Color.argb(60, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1.5f; isAntiAlias = true
    }
    private val menuIconPaint = Paint().apply { color = Color.argb(230, 238, 242, 250); isAntiAlias = true }
    private val menuIconStrokePaint = Paint().apply {
        color = Color.argb(230, 255, 150, 140); strokeWidth = 4.5f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val menuLabelPaint = Paint().apply {
        color = Color.argb(150, 226, 232, 244); textSize = 20f; textAlign = Paint.Align.CENTER; letterSpacing = 0.1f; isAntiAlias = true
    }
    private val pauseVeil = Paint().apply { color = Color.argb(185, 8, 10, 16) }
    private val pauseText = Paint().apply { color = Color.WHITE; textSize = 68f; textAlign = Paint.Align.CENTER; letterSpacing = 0.24f; isAntiAlias = true }
    private val pauseHint = Paint().apply { color = Color.argb(200, 230, 234, 244); textSize = 30f; textAlign = Paint.Align.CENTER; isAntiAlias = true }

    private fun drawMenuButtons(canvas: Canvas) {
        canvas.drawCircle(PAUSE_BTN_X, MENU_BTN_Y, MENU_BTN_R, menuBtnPaint)
        canvas.drawCircle(PAUSE_BTN_X, MENU_BTN_Y, MENU_BTN_R, menuBtnEdgePaint)
        if (paused) {
            path.reset()
            path.moveTo(PAUSE_BTN_X - 12f, MENU_BTN_Y - 18f); path.lineTo(PAUSE_BTN_X + 20f, MENU_BTN_Y)
            path.lineTo(PAUSE_BTN_X - 12f, MENU_BTN_Y + 18f); path.close()
            canvas.drawPath(path, menuIconPaint)
        } else {
            canvas.drawRect(PAUSE_BTN_X - 13f, MENU_BTN_Y - 16f, PAUSE_BTN_X - 5f, MENU_BTN_Y + 16f, menuIconPaint)
            canvas.drawRect(PAUSE_BTN_X + 5f, MENU_BTN_Y - 16f, PAUSE_BTN_X + 13f, MENU_BTN_Y + 16f, menuIconPaint)
        }
        canvas.drawText("멈추기", PAUSE_BTN_X, MENU_BTN_Y + MENU_BTN_R + 30f, menuLabelPaint)
        canvas.drawCircle(EXIT_BTN_X, MENU_BTN_Y, MENU_BTN_R, exitBtnPaint)
        canvas.drawCircle(EXIT_BTN_X, MENU_BTN_Y, MENU_BTN_R, menuBtnEdgePaint)
        canvas.drawLine(EXIT_BTN_X - 14f, MENU_BTN_Y - 14f, EXIT_BTN_X + 14f, MENU_BTN_Y + 14f, menuIconStrokePaint)
        canvas.drawLine(EXIT_BTN_X + 14f, MENU_BTN_Y - 14f, EXIT_BTN_X - 14f, MENU_BTN_Y + 14f, menuIconStrokePaint)
        canvas.drawText("나가기", EXIT_BTN_X, MENU_BTN_Y + MENU_BTN_R + 30f, menuLabelPaint)
    }

    private fun tentName(i: Int) = TENT_NAMES[i] ?: "${i + 1}번 가게"

    private val ownerDst = RectF()
    private val adBubble = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val adText = Paint().apply { color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }

    /** 가게 주인: 천막 색 옷의 인간이 손님 쪽(왼쪽)을 보고 서서, 말풍선으로 광고 문구를 번갈아 외친다 */
    private fun drawOwner(canvas: Canvas, i: Int, x: Float, groundY: Float) {
        val (c1, _) = TENT_COLORS[i % TENT_COLORS.size]
        val frames = SpriteManager.playerIdle
        val bmp = SpriteManager.getFlipped(frames[((timer * 7f).toInt() + i * 2) % frames.size])
        val sh = 190f
        val sw = sh * bmp.width / bmp.height
        ownerDst.set(x - sw / 2f, groundY - sh + 10f, x + sw / 2f, groundY + 10f)
        rect.set(x - 26f, groundY - 6f, x + 26f, groundY + 8f)
        canvas.drawOval(rect, shadowPaint)
        canvas.drawBitmap(bmp, null, ownerDst, com.jamcat.nayngapo.ui.heroTintPaint(c1))

        // 광고 말풍선: 문구를 2.5초마다 바꾸고, 바뀔 때 살짝 튄다
        val ads = TENT_ADS[i] ?: DEFAULT_ADS
        val phase = timer / 2.5f + i * 0.37f
        val text = ads[phase.toInt() % ads.size]
        val pop = 1f + 0.08f * (1f - ((phase % 1f) * 6f).coerceAtMost(1f))
        adText.textSize = 30f * pop
        val bw = adText.measureText(text) + 36f
        val by = groundY - sh - 30f
        rect.set(x - bw / 2f, by - 50f * pop, x + bw / 2f, by)
        canvas.drawRoundRect(rect, 22f, 22f, adBubble)
        canvas.drawRoundRect(rect, 22f, 22f, ink)
        path.reset()
        path.moveTo(x - 12f, by - 2f); path.lineTo(x + 4f, by + 20f); path.lineTo(x + 14f, by - 2f); path.close()
        canvas.drawPath(path, adBubble)
        canvas.drawText(text, x, by - 25f * pop + 11f, adText)
    }

    /** 천막 하나: 줄무늬 지붕 + 깃발 + 이름 팻말 + 문(닫힘: 여민 천과 끈 매듭 / 열림: 걷어 묶은 천과 안쪽 팻말) */
    private fun drawTent(canvas: Canvas, i: Int, cx: Float, baseY: Float, tw: Float, th: Float, open: Boolean) {
        val (c1, c2) = TENT_COLORS[i % TENT_COLORS.size]
        val left = cx - tw / 2f; val right = cx + tw / 2f
        val eaveY = baseY - th * 0.52f
        val topY = baseY - th

        rect.set(left - 10f, baseY - 14f, right + 30f, baseY + 22f)
        canvas.drawOval(rect, shadowPaint)

        // 벽
        fill.color = c2
        rect.set(left + tw * 0.06f, eaveY, right - tw * 0.06f, baseY)
        canvas.drawRect(rect, fill)
        canvas.drawRect(rect, ink)

        // 문
        val dw = tw * 0.36f; val dTop = eaveY + th * 0.06f
        path.reset()
        path.moveTo(cx - dw / 2f, baseY)
        path.lineTo(cx - dw / 2f, dTop + th * 0.10f)
        path.quadTo(cx - dw / 2f, dTop, cx, dTop - th * 0.02f)
        path.quadTo(cx + dw / 2f, dTop, cx + dw / 2f, dTop + th * 0.10f)
        path.lineTo(cx + dw / 2f, baseY)
        path.close()
        if (open) {
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
            val inner = if (TENT_NAMES[i] != null) "어서 오세요" else "준비 중"
            signText.textSize = 30f
            val sw = signText.measureText(inner) + 30f
            rect.set(cx - sw / 2f, baseY - th * 0.22f, cx + sw / 2f, baseY - th * 0.22f + 50f)
            canvas.drawRoundRect(rect, 10f, 10f, signPaint)
            canvas.drawRoundRect(rect, 10f, 10f, ink)
            canvas.drawText(inner, cx, rect.centerY() + 11f, signText)
        } else {
            fill.color = c1
            canvas.drawPath(path, fill)
            canvas.drawPath(path, ink)
            canvas.drawLine(cx, dTop - th * 0.01f, cx, baseY, ink)
            fill.color = Color.rgb(150, 100, 50)
            val ky = dTop + th * 0.16f
            canvas.drawCircle(cx, ky, 13f, fill)
            canvas.drawCircle(cx, ky, 13f, ink)
            canvas.drawLine(cx, ky, cx - dw * 0.18f, ky + th * 0.07f, ink)
            canvas.drawLine(cx, ky, cx + dw * 0.18f, ky + th * 0.07f, ink)
        }

        // 줄무늬 지붕
        val stripes = 6
        for (s in 0 until stripes) {
            val a = left + tw * s / stripes; val b = left + tw * (s + 1) / stripes
            path.reset()
            path.moveTo(cx, topY); path.lineTo(a, eaveY); path.lineTo(b, eaveY); path.close()
            fill.color = if (s % 2 == 0) c1 else Color.WHITE
            canvas.drawPath(path, fill)
        }
        path.reset()
        path.moveTo(cx, topY); path.lineTo(left, eaveY); path.lineTo(right, eaveY); path.close()
        canvas.drawPath(path, ink)
        val scallops = 6
        for (s in 0 until scallops) {
            val a = left + tw * s / scallops
            rect.set(a, eaveY - tw / scallops * 0.35f, a + tw / scallops, eaveY + tw / scallops * 0.45f)
            fill.color = if (s % 2 == 0) c1 else Color.WHITE
            canvas.drawArc(rect, 0f, 180f, false, fill)
            canvas.drawArc(rect, 0f, 180f, false, ink)
        }

        // 깃발
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

        // 이름 팻말 (처마 위)
        signText.textSize = 34f
        val label = tentName(i)
        val lw = signText.measureText(label) + 36f
        rect.set(cx - lw / 2f, eaveY - th * 0.13f - 54f, cx + lw / 2f, eaveY - th * 0.13f)
        canvas.drawRoundRect(rect, 10f, 10f, signPaint)
        canvas.drawRoundRect(rect, 10f, 10f, ink)
        canvas.drawText(label, cx, rect.centerY() + 12f, signText)
    }

    private fun enterTent(i: Int) {
        touchControls.releaseAll()
        if (i == CAT_SHOP_TENT) gameView.showCatShop(fromTent = true)
        else showNotice("${tentName(i)}은(는) 아직 물건을 준비 중이에요")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val idx = event.actionIndex
            val px = event.getX(idx); val py = event.getY(idx)
            if (kotlin.math.hypot(px - PAUSE_BTN_X, py - MENU_BTN_Y) < MENU_HIT_R) {
                paused = !paused; touchControls.releaseAll(); return true
            }
            if (kotlin.math.hypot(px - EXIT_BTN_X, py - MENU_BTN_Y) < MENU_HIT_R) {
                touchControls.releaseAll(); gameView.showLobby(); return true
            }
            if (paused) return true
            if (!enterRect.isEmpty && enterRect.contains(px, py) && nearTent in 0 until openCount) {
                enterTent(nearTent); return true
            }
            // 서 있는 열린 천막 자체를 눌러도 들어간다
            if (nearTent in 0 until openCount && tentScreenRects[nearTent].contains(px, py)) {
                enterTent(nearTent); return true
            }
        }
        if (paused) return true
        return touchControls.onTouchEvent(event, gameView.screenWidth, gameView.screenHeight)
    }

    private fun showNotice(text: String) {
        noticeText = text
        noticeTimer = 2f
    }

    /** 뒤로가기: 스테이지처럼 먼저 멈추고, 멈춘 상태에서 한 번 더 누르면 로비로 */
    override fun onBack(): Boolean {
        touchControls.releaseAll()
        if (!paused) paused = true else gameView.showLobby()
        return true
    }

    companion object {
        /** 천막 수 — 대표님이 정할 때까지 임시 6개 (Claude 판단) */
        const val TENT_COUNT = 6
        private const val MENU_BTN_R = 46f
        private const val MENU_HIT_R = 60f
        private const val MENU_BTN_Y = 118f
        private const val PAUSE_BTN_X = 110f
        private const val EXIT_BTN_X = 232f
        /** 고양이 상점이 들어 있는 천막 (0 = 1번, 첫 인간을 구하면 열린다) */
        const val CAT_SHOP_TENT = 0
        private val TENT_NAMES: List<String?> = List(TENT_COUNT) { if (it == CAT_SHOP_TENT) "고양이 상점" else null }
        /** 가게 주인의 광고 문구 (Claude 판단 — 대표님이 정하면 바꾼다) */
        private val TENT_ADS: Map<Int, List<String>> = mapOf(
            CAT_SHOP_TENT to listOf("어서 오세요!", "냥이 간식 있어요~", "장난감·캣타워 팝니다!", "튼튼한 고양이 갑옷!"),
        )
        private val DEFAULT_ADS = listOf("어서 오세요!", "곧 좋은 물건 들어와요~")
        private const val START_X = 160f
        private const val FIRST_TENT_X = 900f
        private const val TENT_GAP = 760f
        private const val TENT_W = 460f
        private const val TENT_H = 520f
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
