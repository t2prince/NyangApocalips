package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CatCoat
import com.jamcat.nayngapo.engine.CatTeam
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.entity.AllyCat
import com.jamcat.nayngapo.ui.drawRubyIcon
import kotlin.math.sin

/**
 * 뽑기 가게 — 들어가면 상자 세 개 중 하나를 돈을 내고 뽑는다 (대표님 2026-10-25 「뽑기는 들어가면 이거 중 하나를 돈을 내고 뽑을 수 있어. 좋은 게 제일 비싸」).
 * 상자가 덜컹거리다 열리고 상품이 튀어나온다. 상품은 사는 순간 바로 적용한다 (화면을 나가도 안 사라진다).
 * 상자 값·상품·확률은 Claude 판단 (BOXES), 상자 그림 GPT(gacha_box_bad / normal / good).
 */
class GachaScreen(private val gameView: GameView, private val fromLobby: Boolean = false) : Screen {

    private enum class Kind { ITEM, HUMAN, CAT, ENEMY_CATS, BOMB, EVOLVE }

    /** weight: 뽑힐 몫(한 상자 안에서 합 100), id: 물건 id (ITEM일 때만) */
    private class Prize(val kind: Kind, val name: String, val weight: Int, val id: String? = null, val rare: Boolean = false)

    private class Box(val name: String, val price: Int, val res: String, val color: Int, val prizes: List<Prize>)

    private val boxBitmaps: List<Bitmap?> = BOXES.map { b ->
        gameView.resources.getIdentifier(b.res, "drawable", gameView.context.packageName)
            .takeIf { it != 0 }?.let { BitmapFactory.decodeResource(gameView.resources, it) }
    }

    private var phase = Phase.IDLE
    private enum class Phase { IDLE, SHAKE, OPEN }
    private var t = 0f
    private var chosen = 0
    private var prize: Prize? = null
    private var resultText = ""
    private var resultCoat: CatCoat = CatCoat.CHEESE
    private var resultCatKind: AllyCat.Kind = AllyCat.Kind.NORMAL
    private var noticeText = ""
    private var noticeTimer = 0f

    private val boxRects = Array(BOXES.size) { RectF() }
    private val btnRects = Array(BOXES.size) { RectF() }
    private val backRect = RectF()
    private val rect = RectF()
    private val bg = Paint().apply { color = Color.rgb(255, 238, 214) }
    private val card = Paint().apply { color = Color.rgb(255, 252, 244); isAntiAlias = true }
    private val ink = Paint().apply { color = Color.rgb(34, 30, 28); style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
    private val fill = Paint().apply { isAntiAlias = true }
    private val glow = Paint().apply { color = Color.argb(140, 255, 236, 120); isAntiAlias = true }
    private val boom = Paint().apply { color = Color.argb(200, 255, 150, 40); isAntiAlias = true }
    private val gemPaint = Paint().apply { isAntiAlias = true }
    private val title = Paint().apply { color = Color.rgb(60, 36, 30); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val white = Paint().apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }

    override fun update(deltaTime: Float) {
        if (noticeTimer > 0f) noticeTimer -= deltaTime
        if (phase == Phase.IDLE) return
        t += deltaTime
        if (phase == Phase.SHAKE && t >= SHAKE_TIME) {
            phase = Phase.OPEN; t = 0f
            if (prize?.kind == Kind.BOMB) SoundManager.playSpecial() else SoundManager.playCatHit()
        }
    }

    private fun buy(index: Int) {
        if (phase == Phase.SHAKE) return   // 열리는 중에는 누르는 걸 받지 않는다
        val box = BOXES[index]
        if (!GameProgress.spendRubies(box.price)) { notice("루비가 모자라요 (루비 ${GameProgress.rubies})"); return }
        // 상품을 먼저 정해 바로 적용한다 (화면을 나가도 안 사라진다)
        var r = (Math.random() * box.prizes.sumOf { it.weight }).toInt()
        val p = box.prizes.first { r -= it.weight; r < 0 }
        noticeText = ""; noticeTimer = 0f
        when (p.kind) {
            Kind.ITEM -> {
                GameProgress.addItem(p.id!!)
                resultText = "${p.name} 나왔다!"
                notice("가방에 넣었어요")
            }
            Kind.HUMAN -> {
                GameProgress.addRescuedHumans(1)
                resultText = "인간 1명 나왔다!"
                notice("인간을 구했어요")
            }
            Kind.CAT -> {
                val kinds = listOf(AllyCat.Kind.NORMAL, AllyCat.Kind.GREEN, AllyCat.Kind.BLUE, AllyCat.Kind.GUN)
                resultCatKind = kinds[(Math.random() * kinds.size).toInt()]
                resultCoat = if (resultCatKind == AllyCat.Kind.NORMAL) CatCoat.random() else CatCoat.CHEESE
                val cat = CatTeam.recruit(resultCatKind, resultCoat)
                resultText = "${cat.name} 나왔다!"
                notice("${cat.name} 이(가) 고양이 관리에 왔어요!")
            }
            Kind.ENEMY_CATS -> {
                val stolen = GameProgress.rubies.coerceAtMost(STEAL_MAX)
                GameProgress.spendRubies(stolen)
                resultText = "다른 편 고양이 3마리! 루비 ${stolen}개를 훔쳐 갔다"
            }
            Kind.BOMB -> {
                val lost = GameProgress.rubies.coerceAtMost(BOMB_LOSS)
                GameProgress.spendRubies(lost)
                resultText = "폭탄! 루비 -$BOMB_LOSS"
                if (lost < BOMB_LOSS) notice("가진 루비 ${lost}개가 날아갔어요")
            }
            Kind.EVOLVE -> {
                CatTeam.startEvolve(EVOLVE_MINUTES * 60f)
                resultText = "진화 쿠폰! 냥이들이 ${EVOLVE_MINUTES}분 동안 묭묭 진화"
            }
        }
        prize = p
        chosen = index
        phase = Phase.SHAKE; t = 0f
        SoundManager.playAttack()
    }

    override fun draw(canvas: Canvas) {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        canvas.save()
        // 폭탄이 터지면 화면이 흔들린다
        if (phase == Phase.OPEN && prize?.kind == Kind.BOMB && t < 0.5f && GameProgress.shakeEnabled) {
            canvas.translate((Math.random().toFloat() - 0.5f) * 30f, (Math.random().toFloat() - 0.5f) * 30f)
        }
        canvas.drawRect(0f, 0f, w, h, bg)
        title.textSize = h * 0.065f
        canvas.drawTr("뽑기 가게", w / 2f, h * 0.10f, title)
        drawRubyIcon(canvas, w * 0.06f, h * 0.09f, h * 0.025f, gemPaint)
        title.textAlign = Paint.Align.LEFT; title.textSize = h * 0.04f
        canvas.drawTr("${GameProgress.rubies}", w * 0.06f + h * 0.04f, h * 0.105f, title)
        title.textAlign = Paint.Align.CENTER

        val bh = h * 0.30f
        val boxTop = h * 0.20f
        for (i in BOXES.indices) {
            val box = BOXES[i]
            val cx = w * (0.2f + 0.3f * i)
            val shake = if (phase == Phase.SHAKE && i == chosen) sin(t * 60f) * 10f else 0f
            val bmp = boxBitmaps[i]
            val bw = if (bmp != null) bmp.width * bh / bmp.height else bh * 1.2f
            boxRects[i].set(cx - bw / 2f + shake, boxTop, cx + bw / 2f + shake, boxTop + bh)
            // 열린 상자의 뒤가 빛난다
            if (phase == Phase.OPEN && i == chosen && (prize?.rare == true || i == BOXES.size - 1)) {
                canvas.drawCircle(cx, boxTop + bh * 0.3f, bh * 0.75f * (0.8f + 0.2f * sin(t * 6f)), glow)
            }
            if (bmp != null) canvas.drawBitmap(bmp, null, boxRects[i], null) else drawFallbackBox(canvas, boxRects[i], box.color)

            title.textSize = h * 0.042f
            canvas.drawTr(box.name, cx, boxTop + bh + h * 0.07f, title)
            // 값 버튼: 루비 그림 + 숫자
            btnRects[i].set(cx - w * 0.11f, boxTop + bh + h * 0.10f, cx + w * 0.11f, boxTop + bh + h * 0.20f)
            fill.color = Color.rgb(240, 90, 120)
            canvas.drawRoundRect(btnRects[i], 50f, 50f, fill); canvas.drawRoundRect(btnRects[i], 50f, 50f, ink)
            white.textSize = h * 0.045f
            drawRubyIcon(canvas, btnRects[i].centerX() - h * 0.06f, btnRects[i].centerY(), h * 0.025f, gemPaint)
            white.textAlign = Paint.Align.LEFT
            canvas.drawTr("${box.price}", btnRects[i].centerX() - h * 0.02f, btnRects[i].centerY() + h * 0.016f, white)
            white.textAlign = Paint.Align.CENTER
        }

        if (phase == Phase.OPEN) drawResult(canvas, w, h, boxTop, bh)

        backRect.set(w * 0.04f, h * 0.84f, w * 0.04f + 240f, h * 0.84f + 90f)
        canvas.drawRoundRect(backRect, 45f, 45f, card); canvas.drawRoundRect(backRect, 45f, 45f, ink)
        title.textSize = h * 0.032f
        canvas.drawTr("◀ 나가기", backRect.centerX(), backRect.centerY() + h * 0.012f, title)
        if (phase == Phase.IDLE) {
            title.textSize = h * 0.04f
            canvas.drawTr("상자 하나를 골라 열어 보세요! 좋은 상자일수록 비싸요", w / 2f, h * 0.80f, title)
        }

        if (noticeTimer > 0f) {
            title.textSize = 40f
            val nw = title.measureTr(noticeText) + 70f
            rect.set(w / 2f - nw / 2f, h * 0.86f, w / 2f + nw / 2f, h * 0.86f + 80f)
            canvas.drawRoundRect(rect, 24f, 24f, card); canvas.drawRoundRect(rect, 24f, 24f, ink)
            canvas.drawTr(noticeText, w / 2f, rect.centerY() + 14f, title)
        }
        canvas.restore()
    }

    /** 상자 그림이 없을 때 쓰는 간단한 상자 */
    private fun drawFallbackBox(canvas: Canvas, r: RectF, color: Int) {
        fill.color = color
        canvas.drawRoundRect(r, 24f, 24f, fill); canvas.drawRoundRect(r, 24f, 24f, ink)
        val lidY = r.top + r.height() * 0.3f
        canvas.drawLine(r.left, lidY, r.right, lidY, ink)
    }

    /** 열린 뒤의 연출: 상품이 튀어나오고, 결과 글자가 크게 뜬다 */
    private fun drawResult(canvas: Canvas, w: Float, h: Float, boxTop: Float, bh: Float) {
        val p = prize ?: return
        val r = boxRects[chosen]
        val cx = r.centerX()
        val u = (t / 0.4f).coerceIn(0f, 1f)
        when (p.kind) {
            Kind.ENEMY_CATS -> {
                // 다른 편 고양이 3마리가 상자에서 뛰어나와 화면 밖으로 달아난다
                val dir = if (cx < w / 2f) -1f else 1f
                val ch = h * 0.14f; val cw = SpriteManager.normalCat.width * ch / SpriteManager.normalCat.height
                for (i in 0 until 3) {
                    val lt = t - 0.2f - i * 0.25f
                    if (lt <= 0f) continue
                    val x = cx + dir * lt * w * 0.4f
                    if (x < -cw || x > w + cw) continue
                    val j = (lt / 0.5f).coerceIn(0f, 1f)
                    val y = r.bottom - ch - sin(j * Math.PI.toFloat()) * h * 0.18f
                    canvas.save()
                    if (dir > 0f) canvas.scale(-1f, 1f, x, 0f)   // 그림은 왼쪽을 보고 있다
                    rect.set(x - cw / 2f, y, x + cw / 2f, y + ch)
                    canvas.drawBitmap(SpriteManager.normalCat, null, rect, null)
                    canvas.restore()
                }
            }
            Kind.BOMB -> {
                val b = (t / 0.6f).coerceIn(0f, 1f)
                if (b < 1f) {
                    boom.alpha = ((1f - b) * 200).toInt()
                    canvas.drawCircle(cx, r.centerY(), bh * 0.9f * b, boom)
                }
                title.textSize = h * 0.12f * (0.6f + 0.4f * u)
                canvas.drawTr("펑!", cx, r.centerY(), title)
            }
            else -> {
                // 상품이 상자 위로 튀어 오른다
                val size = h * 0.2f * (0.4f + 0.6f * u)
                val py = r.top + bh * 0.2f - u * bh * 0.45f
                val icon = when (p.kind) {
                    Kind.ITEM -> p.id?.let { SpriteManager.shopIcons[it] }
                    Kind.CAT -> when (resultCatKind) {
                        AllyCat.Kind.NORMAL -> resultCoat.ally
                        AllyCat.Kind.GREEN -> SpriteManager.greenCat
                        AllyCat.Kind.BLUE -> SpriteManager.blueCat
                        else -> SpriteManager.allyCat
                    }
                    else -> null
                }
                if (icon != null) {
                    rect.set(cx - size / 2f, py - size / 2f, cx + size / 2f, py + size / 2f)
                    canvas.drawBitmap(icon, null, rect, null)
                }
            }
        }
        // 결과 글자 (크게)
        title.textSize = h * 0.055f
        canvas.drawTr((if (p.rare) "✨ " else "") + resultText, w / 2f, h * 0.78f, title)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        if (backRect.contains(event.x, event.y)) { leave(); return true }
        for (i in BOXES.indices) {
            if (boxRects[i].contains(event.x, event.y) || btnRects[i].contains(event.x, event.y)) { buy(i); break }
        }
        return true
    }

    private fun notice(t: String) { noticeText = t; noticeTimer = 2.2f }

    private fun leave() { if (fromLobby) gameView.showLobby() else gameView.showShop() }

    override fun onBack(): Boolean {
        leave()
        return true
    }

    companion object {
        /** 제일 싼 상자 값 — 상점 마을·로비 안내 글자가 쓴다 */
        const val PRICE = 10
        private const val SHAKE_TIME = 0.9f
        /** 다른 편 고양이 3마리가 훔쳐 가는 루비 (한 마리 3개) — Claude 판단 */
        private const val STEAL_MAX = 9
        /** 폭탄에 잃는 루비 — Claude 판단 */
        private const val BOMB_LOSS = 5
        /** 진화 쿠폰으로 묭묭 진화하는 시간(분) */
        private const val EVOLVE_MINUTES = 55

        /** 상자 세 개: 값·상품·확률은 Claude 판단 (각 상자 안에서 몫의 합 100). 좋은 상자일수록 비싸다 */
        private val BOXES = listOf(
            Box("안 좋은 상자", PRICE, "gacha_box_bad", Color.rgb(90, 90, 90), listOf(
                Prize(Kind.ENEMY_CATS, "다른 편 고양이 3마리", 25),
                Prize(Kind.ITEM, "츄르", 30, "churu"),
                Prize(Kind.ITEM, "고양이 통조림", 25, "tuna_can"),
                Prize(Kind.BOMB, "폭탄", 20),
            )),
            Box("그냥 상자", 30, "gacha_box_normal", Color.rgb(150, 100, 60), listOf(
                Prize(Kind.HUMAN, "인간", 15),
                Prize(Kind.CAT, "아군 고양이", 15),
                Prize(Kind.ITEM, "츄르", 20, "churu"),
                Prize(Kind.ITEM, "고양이 통조림", 20, "tuna_can"),
                Prize(Kind.ITEM, "캣닢", 15, "catnip"),
                Prize(Kind.ITEM, "사료 한 봉", 15, "feed"),
            )),
            Box("좋은 상자", 80, "gacha_box_good", Color.rgb(40, 36, 36), listOf(
                Prize(Kind.ITEM, "공격력 Lv업", 10, "attack_up", rare = true),
                Prize(Kind.CAT, "아군 고양이", 15),
                Prize(Kind.ITEM, "츄르", 18, "churu"),
                Prize(Kind.ITEM, "고양이 통조림", 17, "tuna_can"),
                Prize(Kind.ITEM, "캣닢", 15, "catnip"),
                Prize(Kind.ITEM, "고양이 부활 약", 15, "revive_potion"),
                Prize(Kind.EVOLVE, "진화 쿠폰", 10, rare = true),
            )),
        )
    }
}
