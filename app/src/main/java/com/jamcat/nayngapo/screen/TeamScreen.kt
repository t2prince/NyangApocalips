package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CatTeam
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.ShopCatalog
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.entity.AllyCat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 고양이 관리 — 내 팀 냥이를 돌보는 곳 (도감이 아니다).
 * 아래 줄에서 냥이를 고르면 가운데에 그 냥이가 크게 그려진다.
 * 큰 냥이를 누르면 쓰다듬고, 오른쪽 버튼으로 놀아주고 간식을 주고 빗질을 한다.
 * 쓰러진 냥이는 무덤이 되어 칸에 남는다.
 */
class TeamScreen(private val gameView: GameView) : Screen {

    private companion object {
        /** 아래 줄에 한 번에 보이는 냥이 칸 수 */
        /** 아래 명단: 두 줄 × 8칸 = 한 페이지 16마리 (대표님 2026-09-20 「고양이도 컬럼 형태로」) */
        const val COLS = 8
        const val ROWS = 2
        const val VISIBLE = COLS * ROWS

        /** 부활 연출 길이 */
        const val REVIVE_POP_TIME = 0.6f
    }

    private var timer = 0f
    private var stripPage = 0

    private var selectedId: Int? = null
    private var snackOpen = false

    private var pressedKey: String? = null
    private var pressedCard = -1

    /** '보내주기'를 한 번 누른 뒤 확인을 기다리는 시각 (timer 기준). 3초 안에 다시 눌러야 한다 */
    private var releaseConfirmUntil = -1f
    /** 확인을 받기 시작한 시각 — 곧바로 두 번 누른 실수는 무시한다 */
    private var releaseArmedAt = -1f

    /** 부활 직후 '뿅' 하고 커지는 연출에 남은 시간 */
    private var revivePopT = 0f

    // 이름 짓기 결과는 UI 스레드에서 들어온다 — 게임 스레드는 읽기만 한다
    @Volatile private var noticeText = ""
    @Volatile private var noticeTimer = 0f

    private val backRect = RectF()
    private val dexRect = RectF()
    private val prevRect = RectF()
    private val nextRect = RectF()
    private val nameRect = RectF()
    private val bigRect = RectF()
    private val actionRects = (0 until 4).map { RectF() }
    private val toggleRect = RectF()
    private val armorRect = RectF()
    private val releaseRect = RectF()
    private val reviveRect = RectF()
    private val atkUpRect = RectF()
    private val snackBoxRect = RectF()
    private val snackCellRects = CatTeam.foodIds.map { RectF() }
    private val cardRects = (0 until VISIBLE).map { RectF() }

    private var pageCats: List<CatTeam.TeamCat> = emptyList()

    /** 큰 냥이 그림의 한가운데 — 하트·별이 여기서 피어오른다 */
    private var bigCx = 0f
    private var bigCy = 0f

    /** 음표를 다음에 띄울 때까지 남은 시간 */
    private var noteTimer = 0f
    private var noteCount = 0
    /** 이번 프레임의 간격 — 그리는 중에 음표 시간을 재는 데 쓴다 */
    private var lastDelta = 0f

    private enum class Fx { HEART, STAR, SPARKLE, TEXT, NOTE, SLASH }

    private class Particle(
        var x: Float, var y: Float,
        val vx: Float, val vy: Float,
        var life: Float, val life0: Float,
        val kind: Fx, val size: Float, val text: String = ""
    )

    private val particles = mutableListOf<Particle>()

    private val bgPaint = Paint().apply { color = Color.rgb(252, 249, 240) }
    private val titlePaint = Paint().apply {
        color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val cardPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val cardPartyPaint = Paint().apply { color = Color.rgb(255, 244, 200); isAntiAlias = true }
    private val cardDeadPaint = Paint().apply { color = Color.rgb(233, 228, 216); isAntiAlias = true }
    private val cardPressPaint = Paint().apply { color = Color.rgb(236, 246, 255); isAntiAlias = true }
    private val popupPaint = Paint().apply { color = Color.rgb(255, 250, 232); isAntiAlias = true }
    private val edgePaint = Paint().apply {
        color = Color.rgb(30, 28, 26); style = Paint.Style.STROKE
        strokeWidth = 4.5f; isAntiAlias = true
    }
    private val selectEdgePaint = Paint().apply {
        color = Color.rgb(240, 170, 20); style = Paint.Style.STROKE
        strokeWidth = 8f; isAntiAlias = true
    }
    private val dividerPaint = Paint().apply { color = Color.rgb(222, 216, 204) }
    private val nameCenterPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val leftBoldPaint = Paint().apply {
        color = Color.rgb(60, 54, 48); textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val rightBoldPaint = Paint().apply {
        color = Color.rgb(60, 54, 48); textAlign = Paint.Align.RIGHT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val rightSmallPaint = Paint().apply {
        color = Color.rgb(150, 142, 132); textAlign = Paint.Align.RIGHT; isAntiAlias = true
    }
    private val centerSmallPaint = Paint().apply {
        color = Color.rgb(140, 132, 122); textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val leftSmallPaint = Paint().apply {
        color = Color.rgb(140, 132, 122); textAlign = Paint.Align.LEFT; isAntiAlias = true
    }
    private val partyCountPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val emptyPaint = Paint().apply {
        color = Color.rgb(150, 142, 132); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val barBgPaint = Paint().apply { color = Color.rgb(226, 221, 210); isAntiAlias = true }
    private val expFgPaint = Paint().apply { color = Color.rgb(255, 204, 60); isAntiAlias = true }
    private val gaugeFgPaint = Paint().apply { isAntiAlias = true }
    private val dotPaint = Paint().apply { isAntiAlias = true }
    private val dotEdgePaint = Paint().apply {
        color = Color.rgb(60, 56, 52); style = Paint.Style.STROKE
        strokeWidth = 2f; isAntiAlias = true
    }
    private val heartPaint = Paint().apply { color = Color.rgb(230, 60, 80); isAntiAlias = true }
    /** 포만감 하트: 채워진 것 / 빈 것 */
    private val heartFullPaint = Paint().apply { color = Color.rgb(230, 60, 80); isAntiAlias = true }
    private val heartEmptyPaint = Paint().apply {
        color = Color.rgb(186, 180, 170); style = Paint.Style.STROKE
        strokeWidth = 2.5f; isAntiAlias = true
    }
    /** 떠난 냥이는 회색 그림자로만 남는다 */
    private val silhouettePaint = Paint().apply {
        isFilterBitmap = true; isAntiAlias = true
        colorFilter = PorterDuffColorFilter(Color.rgb(196, 189, 176), PorterDuff.Mode.SRC_IN)
    }
    private val shieldPaint = Paint().apply { color = Color.rgb(200, 206, 214); isAntiAlias = true }
    private val shieldEdgePaint = Paint().apply {
        color = Color.rgb(30, 28, 26); style = Paint.Style.STROKE
        strokeWidth = 2f; isAntiAlias = true
    }
    private val btnPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val btnWhitePaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val btnPressPaint = Paint().apply { color = Color.rgb(255, 236, 160); isAntiAlias = true }
    private val btnOffPaint = Paint().apply { color = Color.rgb(220, 216, 208); isAntiAlias = true }
    /** 확인을 기다리는 '정말 보내기?' 버튼 */
    private val btnDangerPaint = Paint().apply { color = Color.rgb(232, 86, 76); isAntiAlias = true }
    private val btnTextPaint = Paint().apply {
        color = Color.rgb(60, 44, 12); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val btnTextOffPaint = Paint().apply {
        color = Color.rgb(150, 145, 137); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val noticePaint = Paint().apply { color = Color.rgb(255, 250, 225); isAntiAlias = true }
    private val noticeTextPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val bitmapPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val fxPaint = Paint().apply { isAntiAlias = true }
    /** 공격력이 오를 때 튀는 붉은 베기 자국 */
    private val slashPaint = Paint().apply {
        color = Color.rgb(226, 62, 52); strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val fxTextPaint = Paint().apply {
        color = Color.rgb(215, 90, 60); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    /** 캣타워 위 춤에 따라 떠오르는 음표 */
    private val notePaint = Paint().apply {
        color = Color.rgb(45, 40, 36); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val pencilPaint = Paint().apply {
        color = Color.rgb(120, 112, 100); strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE; isAntiAlias = true
    }

    private val srcRect = Rect()
    private val dstRect = RectF()
    private val barRect = RectF()
    private val scratch = RectF()
    private val noticeRect = RectF()
    private val fxPath = Path()

    init {
        // 배고픔은 게임을 하는 동안에만 흐른다 (GameView가 매 프레임 시간을 쌓는다)
        selectedId = CatTeam.cats.firstOrNull()?.id
    }

    private val selected: CatTeam.TeamCat?
        get() = selectedId?.let { CatTeam.cat(it) }

    override fun update(deltaTime: Float) {
        timer += deltaTime
        lastDelta = deltaTime
        if (revivePopT > 0f) revivePopT -= deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime

        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * deltaTime
            p.y += p.vy * deltaTime
            p.life -= deltaTime
            if (p.life <= 0f) it.remove()
        }
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        drawHeader(canvas, w, h)

        val cats = CatTeam.cats
        if (cats.isEmpty()) {
            clearBodyRects()
            emptyPaint.textSize = h * 0.055f
            canvas.drawText("아직 팀 냥이가 없어요", w / 2f, h * 0.44f, emptyPaint)
            emptyPaint.textSize = h * 0.042f
            canvas.drawText("스테이지에서 감옥에 갇힌 냥이를 구해 보세요!", w / 2f, h * 0.52f, emptyPaint)
        } else {
            val cat = selected
            if (cat != null) {
                drawIdentity(canvas, w, h, cat)      // 왼쪽 위: 이름·레벨·이름 바꾸기
                drawCenterStage(canvas, w, h, cat)   // 가운데: 그림과 연출만
                if (cat.gone) clearGaugeArea() else drawGauges(canvas, w, h, cat)
                drawActionColumn(canvas, w, h, cat)
            }
            drawStrip(canvas, w, h, cats)
            if (snackOpen) drawSnackPicker(canvas, w, h) else clearSnackRects()
        }

        drawParticles(canvas, h)
        drawNotice(canvas, w, h)
    }

    private fun clearBodyRects() {
        nameRect.setEmpty(); bigRect.setEmpty(); toggleRect.setEmpty(); armorRect.setEmpty()
        releaseRect.setEmpty()
        reviveRect.setEmpty()
        atkUpRect.setEmpty()
        for (r in actionRects) r.setEmpty()
        for (r in cardRects) r.setEmpty()
        prevRect.setEmpty(); nextRect.setEmpty()
        clearSnackRects()
        pageCats = emptyList()
    }

    private fun clearSnackRects() {
        snackBoxRect.setEmpty()
        for (r in snackCellRects) r.setEmpty()
    }

    private fun clearGaugeArea() { /* 무덤은 수치를 보여 주지 않는다 */ }

    // ===================== 위쪽 =====================

    private fun drawHeader(canvas: Canvas, w: Float, h: Float) {
        titlePaint.textSize = h * 0.058f
        canvas.drawText("고양이 관리", w / 2f, h * 0.058f, titlePaint)

        partyCountPaint.textSize = h * 0.036f
        canvas.drawText(
            "출전 ${CatTeam.party.size} / ${CatTeam.PARTY_MAX}", w * 0.022f, h * 0.045f, partyCountPaint
        )

        backRect.set(w * 0.022f, h * 0.060f, w * 0.135f, h * 0.128f)
        drawButton(canvas, backRect, "돌아가기", h * 0.030f, btnPaint, pressedKey == "back", true)

        dexRect.set(w - w * 0.11f, h * 0.018f, w - w * 0.02f, h * 0.082f)
        drawButton(canvas, dexRect, "도감", h * 0.030f, btnPaint, pressedKey == "dex", true)
    }

    /**
     * 버튼 하나.
     * @param fillFirst 눌린 색보다 fill 색을 앞세운다 ('정말 보내기?'의 빨강이 가려지지 않게)
     */
    private fun drawButton(
        canvas: Canvas, rect: RectF, label: String, textSize: Float,
        fill: Paint, pressed: Boolean, enabled: Boolean, fillFirst: Boolean = false
    ) {
        val body = when {
            !enabled -> btnOffPaint
            fillFirst -> fill
            pressed -> btnPressPaint
            else -> fill
        }
        canvas.drawRoundRect(rect, 18f, 18f, body)
        canvas.drawRoundRect(rect, 18f, 18f, edgePaint)
        val paint = if (enabled) btnTextPaint else btnTextOffPaint
        paint.textSize = textSize
        // 긴 글씨는 버튼 폭에 맞게 줄인다
        while (paint.textSize > textSize * 0.6f && paint.measureText(label) > rect.width() - 16f) {
            paint.textSize -= textSize * 0.05f
        }
        canvas.drawText(label, rect.centerX(), rect.centerY() + paint.textSize * 0.36f, paint)
    }

    // ===================== 가운데: 크게 그린 냥이 =====================

    private fun drawCenterStage(canvas: Canvas, w: Float, h: Float, cat: CatTeam.TeamCat) {
        val cx = w * 0.50f

        // 캣타워를 사 두면 냥이가 그 위에 올라가 춤을 춘다
        val hasTower = GameProgress.itemCount("cat_tower") > 0
        val tower = if (hasTower) SpriteManager.shopIcons["cat_tower"] else null
        if (tower != null) {
            drawTowerStage(canvas, w, h, cat, cx, tower)
        } else {
            drawPlainStage(canvas, w, h, cat, cx)
        }

        // 교배로 태어난 새끼는 누구랑 누구 사이인지 보여 준다 (대표님 2026-09-27)
        if (cat.isKitten) {
            centerSmallPaint.textSize = h * 0.028f
            val from = if (cat.parents.isNotEmpty()) "부모 ${cat.parents}" else "감옥에서 구함"
            val label = "새끼 · $from · Lv ${CatTeam.GROW_UP_LEVEL} 이면 다 자라요"
            canvas.drawText(label, cx, h * 0.140f, centerSmallPaint)
        }

        // 타워가 가운데를 채우면 안내 문구는 생략한다 (그림과 겹친다)
        if (!cat.gone && tower == null) {
            centerSmallPaint.textSize = h * 0.026f
            canvas.drawText("냥이를 누르면 쓰다듬어요", cx, h * 0.690f, centerSmallPaint)
        }
    }

    /**
     * 왼쪽 칸 위: 이름 · 레벨/체력 · 이름 바꾸기.
     * 가운데는 그림만 두어 춤추는 냥이의 머리와 글씨가 겹치지 않게 한다.
     */
    private fun drawIdentity(canvas: Canvas, w: Float, h: Float, cat: CatTeam.TeamCat) {
        val l = w * 0.030f
        val r = w * 0.270f

        leftBoldPaint.textSize = h * 0.042f
        // 칸을 넘치는 긴 이름은 줄인다
        while (leftBoldPaint.textSize > h * 0.026f &&
            leftBoldPaint.measureText(cat.name) > (r - l) - h * 0.045f
        ) {
            leftBoldPaint.textSize -= h * 0.002f
        }
        canvas.drawText(cat.name, l, h * 0.164f, leftBoldPaint)

        if (!cat.gone) {
            val nameW = leftBoldPaint.measureText(cat.name)
            drawPencil(canvas, l + nameW + h * 0.026f, h * 0.152f, h * 0.017f)
            leftSmallPaint.textSize = h * 0.022f
            canvas.drawText("이름 바꾸기", l, h * 0.258f, leftSmallPaint)
            nameRect.set(l, h * 0.136f, r, h * 0.266f)
        } else {
            nameRect.setEmpty()
        }

        // 잘 돌본 냥이는 전투에서 체력이 더 붙는다 — 그 몫을 (+N)으로 보여 준다
        val info = when {
            cat.isDead -> "스테이지 ${cat.diedAtStage}에서 잠들다"
            cat.hasLeft -> "배고파서 떠났다..."
            else -> {
                val base = CatTeam.maxHpFor(cat.level)
                val bonus = Math.round(base * CatTeam.careMultiplier(cat)) - base
                "Lv ${cat.level} · 체력 $base" + if (bonus > 0) " (+$bonus)" else ""
            }
        }
        leftBoldPaint.textSize = h * 0.030f
        canvas.drawText(info, l, h * 0.198f, leftBoldPaint)

        // 지금 공격력 (Lv업으로 올린 몫은 따로 덧붙인다)
        if (!cat.gone) {
            val total = CatTeam.totalAttackOf(cat)
            val extra = if (cat.attackBonus > 0) " (+${cat.attackBonus})" else ""
            leftBoldPaint.textSize = h * 0.028f
            canvas.drawText("공격 $total$extra", l, h * 0.228f, leftBoldPaint)
        }
    }

    /** 캣타워가 없을 때: 지금까지처럼 냥이만 가운데에 크게 (방석이 있으면 깔아 준다) */
    private fun drawPlainStage(canvas: Canvas, w: Float, h: Float, cat: CatTeam.TeamCat, cx: Float) {
        val bmp = if (cat.isDead) SpriteManager.grave else spriteFor(cat)
        val availH = h * 0.45f
        val availW = w * 0.34f
        // 갑옷 그림은 투구·볏만큼 위로 길다 — 크기는 맨몸 그림 기준으로 잡아 냥이 몸집이 그대로여야 한다
        val refH = refHeight(cat, bmp)
        val scale = min(availW / bmp.width, availH / refH) * (if (cat.isKitten) 0.7f else 1f)   // 새끼는 작게
        val bw = bmp.width * scale
        val bh = bmp.height * scale
        val refBh = refH * scale                 // 맨몸 높이 — 발 위치·방석 기준
        val bob = if (cat.gone) 0f else sin(timer * 2.0f) * h * 0.010f
        bigCx = cx
        bigCy = h * 0.475f + bob

        // 방석을 사 두면 냥이가 그 위에 앉는다 (춤은 캣타워에서만)
        val cushion = if (!cat.gone) SpriteManager.shopIcons["cushion"] else null
        if (cushion != null) {
            val chH = h * 0.085f
            val chScale = chH / cushion.height
            val chW = cushion.width * chScale
            val feet = bigCy + refBh / 2f
            srcRect.set(0, 0, cushion.width, cushion.height)
            dstRect.set(cx - chW / 2f, feet - chH * 0.45f, cx + chW / 2f, feet + chH * 0.55f)
            canvas.drawBitmap(cushion, srcRect, dstRect, bitmapPaint)
        }

        srcRect.set(0, 0, bmp.width, bmp.height)
        val feetY = bigCy + refBh / 2f
        dstRect.set(cx - bw / 2f, feetY - bh, cx + bw / 2f, feetY)   // 발을 맞추고 투구·볏은 위로
        applyRevivePop(dstRect)
        canvas.drawBitmap(bmp, srcRect, dstRect, if (cat.hasLeft) silhouettePaint else bitmapPaint)
        bigRect.set(dstRect)
        drawArmorPlate(canvas, cat, bigRect)
    }

    /**
     * 캣타워 위에서 춤추는 냥이.
     * 타워 그림(448x448)의 맨 위 발판은 세로 20~28%, 가로 29~71% 자리에 있다.
     */
    private fun drawTowerStage(
        canvas: Canvas, w: Float, h: Float, cat: CatTeam.TeamCat, cx: Float, tower: Bitmap
    ) {
        val towerH = h * 0.48f
        val towerScale = towerH / tower.height
        val towerW = tower.width * towerScale
        val towerBottom = h * 0.725f   // 아래 명단이 두 줄이 되어 조금 올린다
        val towerTop = towerBottom - towerH
        srcRect.set(0, 0, tower.width, tower.height)
        dstRect.set(cx - towerW / 2f, towerTop, cx + towerW / 2f, towerBottom)
        canvas.drawBitmap(tower, srcRect, dstRect, bitmapPaint)

        // 맨 위 발판의 윗면 — 여기에 발을 딛는다
        val platformY = towerTop + towerH * 0.205f

        if (cat.gone) {
            // 무덤과 떠난 냥이는 춤추지 않는다 — 타워 밑에 가만히 있다
            val bmp = if (cat.isDead) SpriteManager.grave else spriteFor(cat)
            val bh = h * 0.20f
            val sc = bh / bmp.height
            val bw = bmp.width * sc
            bigCx = cx
            bigCy = towerBottom - bh / 2f
            srcRect.set(0, 0, bmp.width, bmp.height)
            dstRect.set(cx - bw / 2f, towerBottom - bh, cx + bw / 2f, towerBottom)
            canvas.drawBitmap(bmp, srcRect, dstRect, if (cat.hasLeft) silhouettePaint else bitmapPaint)
            bigRect.set(dstRect)
            return
        }

        // 춤: 냥이는 7프레임짜리 대기 동작이 그대로 춤이고, 나머지는 몸을 흔든다
        val dancing = cat.kind == AllyCat.Kind.NORMAL || cat.kind == AllyCat.Kind.GUN
        val armorOn = cat.hasArmor && !cat.gone
        val idle = when (cat.kind) {
            AllyCat.Kind.GUN -> if (armorOn) SpriteManager.gunIdleArmor else SpriteManager.gunIdle
            else -> if (armorOn) cat.coat.idleArmor else cat.coat.idle
        }
        val bmp =
            if (dancing) idle[((timer * 7f).toInt()) % idle.size]
            else spriteFor(cat)
        val catH = h * 0.30f
        val sc = catH / (if (dancing) (if (cat.kind == AllyCat.Kind.GUN) SpriteManager.gunIdle[0] else cat.coat.idle[0]).height.toFloat() else refHeight(cat, bmp))   // 갑옷을 입어도 몸집은 같게
        val bw = bmp.width * sc
        val bh = bmp.height * sc
        bigCx = cx
        bigCy = platformY - bh / 2f

        srcRect.set(0, 0, bmp.width, bmp.height)
        dstRect.set(cx - bw / 2f, platformY - bh, cx + bw / 2f, platformY)
        applyRevivePop(dstRect)

        if (dancing) {
            canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
        } else {
            // 연속 동작이 없는 냥이는 몸을 흔들어 춤춘다 (도감과 같은 춤)
            com.jamcat.nayngapo.ui.drawDancing(canvas, bmp, dstRect, timer, bitmapPaint)
        }
        bigRect.set(dstRect)
        drawArmorPlate(canvas, cat, bigRect)

        // 음표가 뽕뽕 떠오른다
        noteTimer -= lastDelta
        if (noteTimer <= 0f) {
            noteTimer = 0.6f
            spawnNote(cx, platformY - bh - h * 0.025f, h)   // 머리 위에서 피어오른다
        }
    }

    /** 부활 직후 발끝을 축으로 작았다가 제 크기로 커지는 '뿅' 연출 */
    private fun applyRevivePop(dst: RectF) {
        if (revivePopT <= 0f) return
        val p = (1f - revivePopT / REVIVE_POP_TIME).coerceIn(0f, 1f)
        val sc = 0.45f + 0.55f * p + sin(p * Math.PI.toFloat()) * 0.12f
        val cxx = dst.centerX()
        val bottom = dst.bottom
        val nw = dst.width() * sc
        val nh = dst.height() * sc
        dst.set(cxx - nw / 2f, bottom - nh, cxx + nw / 2f, bottom)
    }

    /**
     * 갑옷을 입고 있으면 몸통에 은색 가슴판을 대어 준다.
     * 냥이 그림은 오른쪽 40%가 얼굴이라, 판은 왼쪽 몸통 쪽(가로 34%·세로 62%)에 놓는다.
     * 왼쪽을 보는(뒤집힌) 그림에서는 반대쪽 66% 자리다.
     */
    private fun drawArmorPlate(
        canvas: Canvas, cat: CatTeam.TeamCat, dst: RectF, facingRight: Boolean = true
    ) {
        // 갑옷은 이제 그림 자체가 바뀐다(spriteFor 가 갑옷 입은 스프라이트를 고른다) — 덧그릴 게 없다
        if (!cat.hasArmor || cat.gone) return
    }

    /**
     * 춤추는 냥이 머리 위로 떠오르는 음표 하나.
     * 가운데 제목("고양이 관리") 위로 지나가지 않도록 좌우로 번갈아 띄운다.
     */
    private fun spawnNote(x: Float, y: Float, h: Float) {
        val n = noteCount++
        val glyph = if (n % 2 == 0) "\u266a" else "\u266b"
        val side = if (n % 2 == 0) -1f else 1f
        particles.add(
            Particle(
                x = x + side * h * (0.17f + Math.random().toFloat() * 0.05f),
                y = y,
                vx = (Math.random().toFloat() - 0.5f) * h * 0.06f,
                vy = -h * 0.11f,
                life = 1.5f, life0 = 1.5f,
                kind = Fx.NOTE, size = 0f, text = glyph
            )
        )
    }

    private fun drawPencil(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        canvas.drawLine(cx - s, cy + s, cx + s * 0.6f, cy - s * 0.6f, pencilPaint)
        canvas.drawLine(cx - s, cy + s, cx - s * 0.5f, cy + s * 0.45f, pencilPaint)
    }

    // ===================== 왼쪽: 돌봄 수치 =====================

    private fun drawGauges(canvas: Canvas, w: Float, h: Float, cat: CatTeam.TeamCat) {
        val l = w * 0.030f
        val r = w * 0.270f
        leftBoldPaint.textSize = h * 0.030f
        rightBoldPaint.textSize = h * 0.030f

        // 포만감은 막대가 아니라 하트 다섯 개 (게임 안에서 한 시간을 보내면 하나씩 닳는다)
        val fTop = h * 0.282f
        canvas.drawText("포만감", l, fTop + h * 0.028f, leftBoldPaint)
        drawHeartRow(
            canvas, l + (r - l) * 0.36f, r, fTop + h * 0.020f,
            CatTeam.heartsOf(cat), (r - l) * 0.055f
        )
        // 다음 하트가 닳기까지 남은 시간 — 규칙이 눈에 보이도록
        val left = CatTeam.secondsToNextHeart()
        leftSmallPaint.textSize = h * 0.022f
        canvas.drawText(
            "다음 하트까지 %d:%02d".format(left / 60, left % 60), l, fTop + h * 0.056f, leftSmallPaint
        )

        val gauges = listOf(
            "청결도" to cat.cleanliness,
            "즐거움" to cat.joy,
            "애정" to cat.affection,
        )
        for ((i, g) in gauges.withIndex()) {
            val top = h * (0.354f + 0.072f * i)
            canvas.drawText(g.first, l, top + h * 0.028f, leftBoldPaint)
            canvas.drawText("${g.second}", r, top + h * 0.028f, rightBoldPaint)
            val gTop = top + h * 0.036f
            val gH = h * 0.020f
            barRect.set(l, gTop, r, gTop + gH)
            canvas.drawRoundRect(barRect, gH / 2f, gH / 2f, barBgPaint)
            val ratio = (g.second / CatTeam.CARE_MAX.toFloat()).coerceIn(0f, 1f)
            if (ratio > 0f) {
                gaugeFgPaint.color = careColor(g.second)
                scratch.set(l, gTop, l + (r - l) * ratio, gTop + gH)
                canvas.drawRoundRect(scratch, gH / 2f, gH / 2f, gaugeFgPaint)
            }
        }

        // 경험치 막대
        leftBoldPaint.textSize = h * 0.028f
        canvas.drawText("경험치", l, h * 0.618f, leftBoldPaint)
        rightSmallPaint.textSize = h * 0.026f
        val expLabel =
            if (cat.level >= CatTeam.MAX_LEVEL) "MAX" else "${cat.exp}/${CatTeam.expToNext(cat.level)}"
        canvas.drawText(expLabel, r, h * 0.618f, rightSmallPaint)
        val eTop = h * 0.627f
        val eH = h * 0.020f
        barRect.set(l, eTop, r, eTop + eH)
        canvas.drawRoundRect(barRect, eH / 2f, eH / 2f, barBgPaint)
        val expRatio =
            if (cat.level >= CatTeam.MAX_LEVEL) 1f
            else (cat.exp / CatTeam.expToNext(cat.level).toFloat()).coerceIn(0f, 1f)
        if (expRatio > 0f) {
            scratch.set(l, eTop, l + (r - l) * expRatio, eTop + eH)
            canvas.drawRoundRect(scratch, eH / 2f, eH / 2f, expFgPaint)
        }
    }

    // ===================== 오른쪽: 돌봄 버튼 =====================

    private fun drawActionColumn(canvas: Canvas, w: Float, h: Float, cat: CatTeam.TeamCat) {
        val l = w * 0.720f
        val r = w * 0.970f
        val bh = h * 0.070f      // 버튼이 하나 늘어 살짝 납작해졌다
        val gap = h * 0.011f
        val alive = !cat.gone

        // 무덤이 된 냥이에게는 부활 약 칸 하나뿐이다
        if (cat.isDead) {
            for (rr in actionRects) rr.setEmpty()
            toggleRect.setEmpty(); armorRect.setEmpty(); releaseRect.setEmpty()
            atkUpRect.setEmpty()
            val top = h * 0.150f
            reviveRect.set(l, top, r, top + bh)
            drawButton(
                canvas, reviveRect, "부활 약 먹이기", bh * 0.34f,
                btnWhitePaint, pressedKey == "revive", true
            )
            leftSmallPaint.textSize = h * 0.024f
            canvas.drawText(
                "부활 약 보유 ${GameProgress.itemCount("revive_potion")}개",
                l, top + bh + h * 0.032f, leftSmallPaint
            )
            return
        }
        reviveRect.setEmpty()

        val labels = listOf("쓰다듬기", "놀아주기", "간식 주기", "빗질하기")
        for (i in 0 until 4) {
            val top = h * 0.150f + i * (bh + gap)
            actionRects[i].set(l, top, r, top + bh)
            // 떠난 냥이에게는 아무것도 해 줄 수 없다 (무덤은 위에서 따로 처리한다)
            val enabled = alive
            drawButton(canvas, actionRects[i], labels[i], bh * 0.36f, btnWhitePaint, pressedKey == "a$i", enabled)
        }

        val dividerY = h * 0.478f
        canvas.drawRect(l, dividerY, r, dividerY + 2f, dividerPaint)

        // 무덤·떠난 냥이는 출전·갑옷 대신 칸을 비우는 '보내주기' 하나만
        if (!alive) {
            toggleRect.setEmpty()
            armorRect.setEmpty()
            atkUpRect.setEmpty()
            val rTop = h * 0.533f
            releaseRect.set(l, rTop, r, rTop + bh)
            val waiting = releaseConfirmUntil > timer
            drawButton(
                canvas, releaseRect, if (waiting) "정말 보내기?" else "보내주기", bh * 0.34f,
                if (waiting) btnDangerPaint else btnWhitePaint, pressedKey == "release", true,
                fillFirst = waiting
            )
            leftSmallPaint.textSize = h * 0.022f
            canvas.drawText(
                if (waiting) "3초 안에 한 번 더 누르면 칸이 비어요" else "칸을 비우고 작별해요",
                l, rTop + bh + h * 0.030f, leftSmallPaint
            )
            return
        }
        releaseRect.setEmpty()

        val tTop = h * 0.490f
        toggleRect.set(l, tTop, r, tTop + bh)
        val toggleLabel = if (cat.inParty) "출전 중 (빼기)" else "출전시키기"
        drawButton(
            canvas, toggleRect, toggleLabel, bh * 0.34f,
            if (cat.inParty) btnPaint else btnWhitePaint, pressedKey == "toggle", alive
        )

        val aTop = h * 0.571f
        armorRect.set(l, aTop, r, aTop + bh)
        // 입히면 벗기는 버튼이 된다
        val armorLabel = if (cat.hasArmor) "갑옷 벗기기" else "갑옷 입히기"
        drawButton(
            canvas, armorRect, armorLabel, bh * 0.34f,
            if (cat.hasArmor) btnPaint else btnWhitePaint, pressedKey == "armor", alive
        )
        // 공격력 Lv업 (영구히 +60)
        val uTop = h * 0.652f
        atkUpRect.set(l, uTop, r, uTop + bh)
        drawButton(
            canvas, atkUpRect, "공격력 Lv업", bh * 0.34f,
            btnWhitePaint, pressedKey == "atkup", alive
        )

        // 가진 아이템 수를 한 줄로
        leftSmallPaint.textSize = h * 0.023f
        canvas.drawText(
            "갑옷 ${GameProgress.itemCount("cat_armor")} · Lv업 ${GameProgress.itemCount("attack_up")}",
            l, uTop + bh + h * 0.030f, leftSmallPaint
        )
    }

    // ===================== 아래: 냥이 줄 =====================

    private fun drawStrip(canvas: Canvas, w: Float, h: Float, cats: List<CatTeam.TeamCat>) {
        val pages = (cats.size + VISIBLE - 1) / VISIBLE
        stripPage = stripPage.coerceIn(0, pages - 1)
        pageCats = cats.drop(stripPage * VISIBLE).take(VISIBLE)

        val stripT = h * 0.735f
        val stripB = h * 0.978f
        val aw = w * 0.040f

        if (pages > 1) {
            prevRect.set(w * 0.010f, stripT + h * 0.040f, w * 0.010f + aw, stripB - h * 0.040f)
            nextRect.set(w - w * 0.010f - aw, prevRect.top, w - w * 0.010f, prevRect.bottom)
            drawButton(canvas, prevRect, "◀", h * 0.032f, btnPaint, pressedKey == "prev", stripPage > 0)
            drawButton(
                canvas, nextRect, "▶", h * 0.032f, btnPaint, pressedKey == "next",
                stripPage < pages - 1
            )
            centerSmallPaint.textSize = h * 0.022f
            canvas.drawText("${stripPage + 1}/$pages", w / 2f, stripB + h * 0.018f, centerSmallPaint)
        } else {
            prevRect.setEmpty(); nextRect.setEmpty()
        }

        val cardsL = w * 0.062f
        val cardsR = w - w * 0.062f
        val gap = w * 0.008f
        val rowGap = h * 0.012f
        val cw = (cardsR - cardsL - gap * (COLS - 1)) / COLS
        val ch = (stripB - stripT - rowGap * (ROWS - 1)) / ROWS

        for (r in cardRects) r.setEmpty()
        for ((i, cat) in pageCats.withIndex()) {
            val col = i % COLS; val row = i / COLS
            val l = cardsL + col * (cw + gap)
            val t = stripT + row * (ch + rowGap)
            cardRects[i].set(l, t, l + cw, t + ch)
            drawStripCard(canvas, cat, cardRects[i], h, i == pressedCard)
        }
    }

    private fun drawStripCard(
        canvas: Canvas, cat: CatTeam.TeamCat, rect: RectF, h: Float, pressed: Boolean
    ) {
        val fill = when {
            cat.gone -> cardDeadPaint
            pressed -> cardPressPaint
            cat.inParty -> cardPartyPaint
            else -> cardPaint
        }
        canvas.drawRoundRect(rect, 14f, 14f, fill)
        canvas.drawRoundRect(rect, 14f, 14f, if (cat.id == selectedId) selectEdgePaint else edgePaint)

        // 무덤은 비석, 떠난 냥이는 회색 그림자, 나머지는 본래 그림
        val bmp = if (cat.isDead) SpriteManager.grave else spriteFor(cat)
        val availW = rect.width() * 0.44f
        val availH = rect.height() * 0.38f
        val refH = refHeight(cat, bmp)                 // 갑옷을 입어도 몸집은 같게
        val scale = min(availW / bmp.width, availH / refH)
        val bw = bmp.width * scale
        val bh = bmp.height * scale
        val cx = rect.centerX()
        val cy = rect.top + rect.height() * 0.24f
        val feet = cy + refH * scale / 2f
        srcRect.set(0, 0, bmp.width, bmp.height)
        dstRect.set(cx - bw / 2f, feet - bh, cx + bw / 2f, feet)
        canvas.drawBitmap(bmp, srcRect, dstRect, if (cat.hasLeft) silhouettePaint else bitmapPaint)

        // 이름 (칸 폭에 맞게 줄인다)
        val k = (rect.height() / (h * 0.178f)).coerceIn(0.6f, 1f)   // 예전 한 줄 카드 높이 대비 비율
        nameCenterPaint.textSize = h * 0.026f * k
        while (nameCenterPaint.textSize > h * 0.016f &&
            nameCenterPaint.measureText(cat.name) > rect.width() - 12f
        ) {
            nameCenterPaint.textSize -= h * 0.002f
        }
        canvas.drawText(cat.name, cx, rect.bottom - rect.height() * 0.44f, nameCenterPaint)

        if (cat.gone) {
            // 무덤·떠난 냥이는 수치 대신 한 줄 설명
            centerSmallPaint.textSize = h * 0.022f * k
            val line = if (cat.isDead) "스테이지 ${cat.diedAtStage}에서 잠들다" else "배고파서 떠났다..."
            while (centerSmallPaint.textSize > h * 0.014f &&
                centerSmallPaint.measureText(line) > rect.width() - 10f
            ) {
                centerSmallPaint.textSize -= h * 0.002f
            }
            canvas.drawText(line, cx, rect.bottom - rect.height() * 0.18f, centerSmallPaint)
            return
        }

        // Lv 과 '포만감' 라벨을 한 줄에
        leftBoldPaint.textSize = h * 0.023f * k
        canvas.drawText("Lv ${cat.level}", rect.left + rect.width() * 0.07f,
            rect.bottom - rect.height() * 0.27f, leftBoldPaint)
        rightSmallPaint.textSize = h * 0.021f * k
        canvas.drawText("포만감", rect.right - rect.width() * 0.07f,
            rect.bottom - rect.height() * 0.27f, rightSmallPaint)

        // 포만감 하트 다섯 개
        drawHeartRow(
            canvas,
            rect.left + rect.width() * 0.10f, rect.right - rect.width() * 0.10f,
            rect.bottom - rect.height() * 0.095f,
            CatTeam.heartsOf(cat), rect.width() * 0.055f
        )

        // 돌봄 상태 색 점 — 하트와 겹치지 않게 오른쪽 위로
        dotPaint.color = careColor(CatTeam.careScore(cat))
        val dr = rect.width() * 0.050f
        canvas.drawCircle(rect.right - dr * 1.8f, rect.top + dr * 1.8f, dr, dotPaint)
        canvas.drawCircle(rect.right - dr * 1.8f, rect.top + dr * 1.8f, dr, dotEdgePaint)

        if (cat.inParty) {
            val s = rect.width() * 0.070f
            drawHeart(canvas, rect.left + s * 1.5f, rect.top + s * 1.6f, s, heartPaint)
        }
        if (cat.hasArmor) {
            drawShield(canvas, rect.left + rect.width() * 0.30f, rect.top + rect.height() * 0.13f, rect.width() * 0.070f)
        }
    }

    /**
     * 포만감 하트 다섯 개를 한 줄로. 하나만 남으면 깜박여서 눈에 띈다.
     * @param l,r 하트가 놓일 가로 구간
     */
    private fun drawHeartRow(canvas: Canvas, l: Float, r: Float, cy: Float, hearts: Int, size: Float) {
        val step = (r - l) / CatTeam.FULLNESS_HEARTS
        val pulse =
            if (hearts == 1) (sin(timer * 6f) * 0.5f + 0.5f) else 1f
        for (i in 0 until CatTeam.FULLNESS_HEARTS) {
            val cx = l + step * (i + 0.5f)
            if (i < hearts) {
                heartFullPaint.alpha = (140 + 115 * pulse).toInt().coerceIn(0, 255)
                drawHeart(canvas, cx, cy, size, heartFullPaint)
                heartFullPaint.alpha = 255
            } else {
                drawHeart(canvas, cx, cy, size, heartEmptyPaint)
            }
        }
    }

    /** 작은 방패 표식 (갑옷을 입은 냥이) */
    private fun drawShield(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        fxPath.rewind()
        fxPath.moveTo(cx - s, cy - s * 0.8f)
        fxPath.lineTo(cx + s, cy - s * 0.8f)
        fxPath.lineTo(cx + s, cy + s * 0.2f)
        fxPath.quadTo(cx, cy + s * 1.3f, cx - s, cy + s * 0.2f)
        fxPath.close()
        canvas.drawPath(fxPath, shieldPaint)
        canvas.drawPath(fxPath, shieldEdgePaint)
    }

    // ===================== 간식 고르기 =====================

    private fun drawSnackPicker(canvas: Canvas, w: Float, h: Float) {
        val boxL = w * 0.200f
        val boxR = w * 0.800f
        val boxT = h * 0.500f
        val boxB = h * 0.675f
        snackBoxRect.set(boxL, boxT, boxR, boxB)
        canvas.drawRoundRect(snackBoxRect, 18f, 18f, popupPaint)
        canvas.drawRoundRect(snackBoxRect, 18f, 18f, edgePaint)

        nameCenterPaint.textSize = h * 0.028f
        canvas.drawText("간식 주기 (빈 곳을 누르면 닫혀요)", (boxL + boxR) / 2f, boxT + h * 0.032f, nameCenterPaint)

        val ids = CatTeam.foodIds
        val innerL = boxL + w * 0.015f
        val innerR = boxR - w * 0.015f
        val gap = w * 0.008f
        val cw = (innerR - innerL - gap * (ids.size - 1)) / ids.size
        val cTop = boxT + h * 0.048f
        val ch = h * 0.115f

        for ((i, id) in ids.withIndex()) {
            val l = innerL + i * (cw + gap)
            snackCellRects[i].set(l, cTop, l + cw, cTop + ch)
            val rect = snackCellRects[i]
            canvas.drawRoundRect(rect, 14f, 14f, if (pressedKey == "s$i") btnPressPaint else cardPaint)
            canvas.drawRoundRect(rect, 14f, 14f, edgePaint)

            val icon = SpriteManager.shopIcons[id]
            if (icon != null) {
                val iw0 = cw * 0.34f
                val ih0 = ch * 0.62f
                val s = min(iw0 / icon.width, ih0 / icon.height)
                val iw = icon.width * s
                val ih = icon.height * s
                val icx = rect.left + cw * 0.24f
                srcRect.set(0, 0, icon.width, icon.height)
                dstRect.set(icx - iw / 2f, rect.centerY() - ih / 2f, icx + iw / 2f, rect.centerY() + ih / 2f)
                canvas.drawBitmap(icon, srcRect, dstRect, bitmapPaint)
            }

            val name = ShopCatalog.items.firstOrNull { it.id == id }?.name ?: id
            leftBoldPaint.textSize = h * 0.026f
            canvas.drawText(name, rect.left + cw * 0.44f, rect.centerY() - h * 0.002f, leftBoldPaint)
            leftSmallPaint.textSize = h * 0.023f
            canvas.drawText(
                "x${GameProgress.itemCount(id)} · +${CatTeam.fullnessOfFood(id)}",
                rect.left + cw * 0.44f, rect.centerY() + h * 0.028f, leftSmallPaint
            )
        }
    }

    // ===================== 연출 =====================

    private fun drawParticles(canvas: Canvas, h: Float) {
        for (p in particles) {
            val a = (255f * (p.life / p.life0)).toInt().coerceIn(0, 255)
            when (p.kind) {
                Fx.HEART -> { fxPaint.color = Color.argb(a, 230, 60, 80); drawHeart(canvas, p.x, p.y, p.size, fxPaint) }
                Fx.STAR -> { fxPaint.color = Color.argb(a, 255, 205, 60); drawStar(canvas, p.x, p.y, p.size, 5, fxPaint) }
                Fx.SPARKLE -> { fxPaint.color = Color.argb(a, 170, 220, 245); drawStar(canvas, p.x, p.y, p.size, 4, fxPaint) }
                Fx.TEXT -> {
                    fxTextPaint.textSize = h * 0.044f
                    fxTextPaint.alpha = a
                    canvas.drawText(p.text, p.x, p.y, fxTextPaint)
                    fxTextPaint.alpha = 255
                }
                Fx.SLASH -> {
                    slashPaint.alpha = a
                    val len = p.size
                    val n = kotlin.math.hypot(p.vx, p.vy).coerceAtLeast(1f)
                    val ux = p.vx / n * len
                    val uy = p.vy / n * len
                    canvas.drawLine(p.x - ux, p.y - uy, p.x + ux, p.y + uy, slashPaint)
                    slashPaint.alpha = 255
                }
                Fx.NOTE -> {
                    notePaint.textSize = h * 0.040f
                    notePaint.alpha = a
                    canvas.drawText(p.text, p.x, p.y, notePaint)
                    notePaint.alpha = 255
                }
            }
        }
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        fxPath.rewind()
        fxPath.moveTo(cx, cy + s * 0.62f)
        fxPath.cubicTo(cx - s * 1.2f, cy - s * 0.3f, cx - s * 0.5f, cy - s * 1.1f, cx, cy - s * 0.45f)
        fxPath.cubicTo(cx + s * 0.5f, cy - s * 1.1f, cx + s * 1.2f, cy - s * 0.3f, cx, cy + s * 0.62f)
        fxPath.close()
        canvas.drawPath(fxPath, paint)
    }

    /** points개 뿔이 난 별 (4뿔은 반짝임, 5뿔은 별) */
    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, r: Float, points: Int, paint: Paint) {
        fxPath.rewind()
        val inner = r * if (points == 4) 0.32f else 0.45f
        val step = Math.PI / points
        var angle = -Math.PI / 2.0
        for (i in 0 until points * 2) {
            val rad = if (i % 2 == 0) r else inner
            val px = cx + (cos(angle) * rad).toFloat()
            val py = cy + (sin(angle) * rad).toFloat()
            if (i == 0) fxPath.moveTo(px, py) else fxPath.lineTo(px, py)
            angle += step
        }
        fxPath.close()
        canvas.drawPath(fxPath, paint)
    }

    /** 큰 냥이 주변에서 피어오르는 하트·별 (fromX/fromY를 주면 그 자리에서) */
    private fun spawnFx(kind: Fx, count: Int, h: Float, fromX: Float = Float.NaN, fromY: Float = Float.NaN) {
        val ox = if (fromX.isNaN()) bigCx else fromX
        val oy = if (fromY.isNaN()) bigCy else fromY
        repeat(count) {
            val spread = h * 0.09f
            particles.add(
                Particle(
                    x = ox + (Math.random().toFloat() - 0.5f) * spread * 2f,
                    y = oy + (Math.random().toFloat() - 0.5f) * spread,
                    vx = (Math.random().toFloat() - 0.5f) * h * 0.12f,
                    vy = -h * (0.20f + Math.random().toFloat() * 0.12f),
                    life = 0.8f, life0 = 0.8f,
                    kind = kind,
                    size = h * (0.016f + Math.random().toFloat() * 0.010f)
                )
            )
        }
    }

    private fun spawnText(text: String, h: Float) {
        particles.add(
            Particle(
                x = bigCx, y = bigCy - h * 0.10f,
                vx = 0f, vy = -h * 0.18f,
                life = 0.9f, life0 = 0.9f,
                kind = Fx.TEXT, size = 0f, text = text
            )
        )
    }

    private fun drawNotice(canvas: Canvas, w: Float, h: Float) {
        if (noticeTimer <= 0f) return
        val alpha = (255 * min(1f, noticeTimer / 0.4f)).toInt()
        val boxW = w * 0.40f
        val boxH = h * 0.085f
        noticeRect.set(w / 2f - boxW / 2f, h * 0.712f, w / 2f + boxW / 2f, h * 0.712f + boxH)

        noticePaint.alpha = alpha
        canvas.drawRoundRect(noticeRect, 18f, 18f, noticePaint)
        edgePaint.alpha = alpha
        canvas.drawRoundRect(noticeRect, 18f, 18f, edgePaint)
        edgePaint.alpha = 255

        noticeTextPaint.alpha = alpha
        noticeTextPaint.textSize = h * 0.032f
        while (noticeTextPaint.textSize > h * 0.020f &&
            noticeTextPaint.measureText(noticeText) > boxW - 24f
        ) {
            noticeTextPaint.textSize -= h * 0.002f
        }
        canvas.drawText(
            noticeText, noticeRect.centerX(), noticeRect.centerY() + h * 0.012f, noticeTextPaint
        )
        noticeTextPaint.alpha = 255
    }

    private fun careColor(value: Int): Int = when {
        value >= 60 -> Color.rgb(110, 200, 120)
        value >= 30 -> Color.rgb(245, 200, 60)
        else -> Color.rgb(230, 90, 80)
    }

    /** 크기 기준 높이 — 갑옷 그림은 투구·볏 때문에 위로 길어서, 맨몸 그림 높이로 배율을 잡아야 몸집이 안 줄어든다 */
    private fun refHeight(cat: CatTeam.TeamCat, bmp: Bitmap): Float =
        if (cat.hasArmor && !cat.gone) plainSpriteFor(cat).height.toFloat() else bmp.height.toFloat()

    private fun plainSpriteFor(cat: CatTeam.TeamCat): Bitmap = when (cat.kind) {
        AllyCat.Kind.GREEN -> SpriteManager.greenCatAlly
        AllyCat.Kind.BLUE -> SpriteManager.blueCatAlly
        AllyCat.Kind.NORMAL -> cat.coat.ally
        AllyCat.Kind.FLYING -> SpriteManager.flyingCatAlly
        AllyCat.Kind.GUN -> SpriteManager.gunCatAlly
    }

    /** 갑옷을 입은 냥이는 갑옷 입은 그림(원안 Cats/cat_armor.jpeg) */
    private fun spriteFor(cat: CatTeam.TeamCat): Bitmap {
        val armor = cat.hasArmor && !cat.gone
        return when (cat.kind) {
            AllyCat.Kind.GREEN -> if (armor) SpriteManager.greenCatAllyArmor else SpriteManager.greenCatAlly
            AllyCat.Kind.BLUE -> if (armor) SpriteManager.blueCatAllyArmor else SpriteManager.blueCatAlly
            AllyCat.Kind.NORMAL -> if (armor) cat.coat.allyArmor else cat.coat.ally      // 치즈냥이는 무늬별 그림
            AllyCat.Kind.FLYING -> if (armor) SpriteManager.flyingCatAllyArmor else SpriteManager.flyingCatAlly
            AllyCat.Kind.GUN -> if (armor) SpriteManager.gunCatAllyArmor else SpriteManager.gunCatAlly
        }
    }

    // ===================== 입력 =====================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val h = gameView.screenHeight.toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedKey = keyAt(event.x, event.y)
                pressedCard = if (pressedKey == null) cardAt(event.x, event.y) else -1
            }
            MotionEvent.ACTION_UP -> {
                val key = keyAt(event.x, event.y)
                val card = if (key == null) cardAt(event.x, event.y) else -1
                val wasKey = pressedKey
                val wasCard = pressedCard
                pressedKey = null
                pressedCard = -1

                if (snackOpen) {
                    when {
                        key != null && key.startsWith("s") -> {
                            val idx = key.substring(1).toIntOrNull()
                            if (idx != null) feedSelected(CatTeam.foodIds[idx], h)
                        }
                        // 빈 곳을 누르면 닫힌다
                        !snackBoxRect.contains(event.x, event.y) -> snackOpen = false
                    }
                    return true
                }

                if (key != null && key == wasKey) {
                    onKey(key, h, event.x, event.y)
                } else if (card >= 0 && card == wasCard) {
                    selectedId = pageCats[card].id
                    releaseConfirmUntil = -1f      // 다른 냥이를 고르면 확인은 취소된다
                    releaseArmedAt = -1f
                }
            }
            MotionEvent.ACTION_CANCEL -> { pressedKey = null; pressedCard = -1 }
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): String? {
        if (snackOpen) {
            for (i in snackCellRects.indices) {
                if (!snackCellRects[i].isEmpty && snackCellRects[i].contains(x, y)) return "s$i"
            }
            return null
        }
        if (backRect.contains(x, y)) return "back"
        if (dexRect.contains(x, y)) return "dex"
        if (!prevRect.isEmpty && prevRect.contains(x, y)) return "prev"
        if (!nextRect.isEmpty && nextRect.contains(x, y)) return "next"
        for (i in actionRects.indices) {
            if (!actionRects[i].isEmpty && actionRects[i].contains(x, y)) return "a$i"
        }
        if (!toggleRect.isEmpty && toggleRect.contains(x, y)) return "toggle"
        if (!armorRect.isEmpty && armorRect.contains(x, y)) return "armor"
        if (!releaseRect.isEmpty && releaseRect.contains(x, y)) return "release"
        if (!reviveRect.isEmpty && reviveRect.contains(x, y)) return "revive"
        if (!atkUpRect.isEmpty && atkUpRect.contains(x, y)) return "atkup"
        if (!nameRect.isEmpty && nameRect.contains(x, y)) return "rename"
        if (!bigRect.isEmpty && bigRect.contains(x, y)) return "big"
        return null
    }

    private fun onKey(key: String, h: Float, x: Float, y: Float) {
        when (key) {
            "back" -> close()
            "dex" -> openDex()
            "prev" -> if (stripPage > 0) stripPage--
            "next" -> if ((stripPage + 1) * VISIBLE < CatTeam.cats.size) stripPage++
            "big" -> doPet(h, x, y)          // 큰 냥이를 직접 누르면 쓰다듬기
            "a0" -> doPet(h)
            "a1" -> doPlay(h)
            "a2" -> if (selected?.gone == false) snackOpen = true
            "a3" -> doGroom(h)
            "toggle" -> doToggleParty()
            "armor" -> doArmor()
            "release" -> doRelease()
            "revive" -> doRevive(h)
            "atkup" -> doAttackUp(h)
            "rename" -> doRename()
        }
    }

    private fun cardAt(x: Float, y: Float): Int {
        for (i in pageCats.indices) {
            if (!cardRects[i].isEmpty && cardRects[i].contains(x, y)) return i
        }
        return -1
    }

    // ===================== 돌봄 행동 =====================

    private fun doPet(h: Float, atX: Float = Float.NaN, atY: Float = Float.NaN) {
        val cat = selected ?: return
        if (cat.hasLeft) return                 // 떠난 냥이는 손이 닿지 않는다
        if (cat.isDead) {
            showNotice("무덤을 쓰다듬었다...")
            return
        }
        val result = CatTeam.pet(cat.id) ?: return   // 연타 중이면 조용히 무시
        spawnFx(Fx.HEART, 3, h, atX, atY)
        SoundManager.playCatHit()
        showNotice(result.message)
    }

    private fun doPlay(h: Float) {
        val cat = selected ?: return
        if (cat.gone) return
        val result = CatTeam.play(cat.id) ?: return
        spawnFx(Fx.STAR, 4, h)
        SoundManager.playCatHit()
        showNotice(result.message)
    }

    private fun doGroom(h: Float) {
        val cat = selected ?: return
        if (cat.gone) return
        val result = CatTeam.groom(cat.id) ?: return
        if (result.ok) {
            spawnFx(Fx.SPARKLE, 4, h)
            SoundManager.playCatHit()
        }
        showNotice(result.message)
    }

    private fun feedSelected(itemId: String, h: Float) {
        val cat = selected ?: return
        val result = CatTeam.feed(cat.id, itemId) ?: return
        if (result.ok) {
            spawnText("냠냠!", h)
            SoundManager.playCatHit()
            if (GameProgress.itemCount(itemId) <= 0) snackOpen = false
        }
        showNotice(result.message)
    }

    private fun doToggleParty() {
        val cat = selected ?: return
        if (cat.gone) return
        if (CatTeam.setInParty(cat.id, !cat.inParty)) {
            SoundManager.playCatHit()
        } else {
            showNotice("출전은 ${CatTeam.PARTY_MAX}마리까지다냥!")
        }
    }

    private fun doArmor() {
        val cat = selected ?: return
        val result =
            (if (cat.hasArmor) CatTeam.unequipArmor(cat.id) else CatTeam.equipArmor(cat.id)) ?: return
        if (result.ok) SoundManager.playCatHit()
        showNotice(result.message)
    }

    /** 공격력 Lv업 아이템을 쓴다 — 영구히 +60 */
    private fun doAttackUp(h: Float) {
        val cat = selected ?: return
        if (cat.gone) return
        val result = CatTeam.raiseAttack(cat.id) ?: return
        if (result.ok) {
            spawnFx(Fx.SLASH, 6, h)
            SoundManager.playAttack()
        }
        showNotice(result.message)
    }

    /** 부활 약을 먹인다 — 무덤이 다시 냥이가 된다 */
    private fun doRevive(h: Float) {
        val cat = selected ?: return
        if (!cat.isDead) return
        if (GameProgress.itemCount("revive_potion") <= 0) {
            showNotice("부활 약이 없다냥! 고양이 상점에서 사 오자 (200루비)")
            return
        }
        val name = cat.name
        // 되살린 뒤에 약을 쓴다 (실패하면 약이 사라지지 않게)
        if (!CatTeam.revive(cat.id)) return
        GameProgress.consumeItem("revive_potion")
        revivePopT = REVIVE_POP_TIME
        spawnFx(Fx.HEART, 8, h)
        spawnFx(Fx.SPARKLE, 6, h)
        SoundManager.playCatHit()
        showNotice("${name}이(가) 살아났다냥!")
    }

    /** 무덤·떠난 냥이의 칸 비우기 — 3초 안에 한 번 더 눌러야 실제로 사라진다 */
    private fun doRelease() {
        val cat = selected ?: return
        if (!cat.gone) return
        if (releaseConfirmUntil <= timer) {
            releaseConfirmUntil = timer + 3f
            releaseArmedAt = timer
            showNotice("한 번 더 누르면 ${cat.name}을(를) 보내요")
            return
        }
        // 손가락이 튀어 두 번 눌린 경우는 확인으로 세지 않는다
        if (timer - releaseArmedAt < 0.3f) return
        releaseConfirmUntil = -1f
        releaseArmedAt = -1f
        val name = cat.name
        if (CatTeam.release(cat.id)) {
            selectedId = CatTeam.cats.firstOrNull()?.id
            showNotice("${name}을(를) 보내주었다...")
        }
    }

    /** 이름 짓기 — 소프트 키보드를 띄운다 (결과는 UI 스레드에서 돌아온다) */
    private fun doRename() {
        val cat = selected ?: return
        if (cat.gone) return
        val id = cat.id
        gameView.promptText("냥이 이름 짓기", cat.name, CatTeam.NAME_MAX) { typed ->
            if (typed != null && CatTeam.rename(id, typed)) {
                showNotice("이름을 ${CatTeam.cat(id)?.name}(으)로 지었다냥!")
            }
        }
    }

    private fun showNotice(text: String) {
        noticeText = text
        noticeTimer = 1.6f
    }

    private fun openDex() {
        CatTeam.markSeen()
        gameView.showDex()
    }

    private fun close() {
        CatTeam.markSeen()
        gameView.showLobby()
    }

    override fun onBack(): Boolean {
        if (snackOpen) { snackOpen = false; return true }
        close()
        return true
    }
}
