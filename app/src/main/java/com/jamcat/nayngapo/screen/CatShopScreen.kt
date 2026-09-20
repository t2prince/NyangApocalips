package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.ShopCatalog
import com.jamcat.nayngapo.engine.SoundManager
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.ui.drawRubyIcon
import kotlin.math.min

/**
 * 고양이 상점. 루비로 냥이 물건(간식·장난감·살림살이)을 산다.
 * 도감 화면과 같은 도화지 + 흰 카드 손그림 톤.
 */
class CatShopScreen(private val gameView: GameView) : Screen {

    private val backRect = RectF()

    /** 물건 카드 자리 — 그릴 때 채워 두고 터치 판정에 쓴다 */
    private val itemRects = ShopCatalog.items.map { RectF() }

    private var pressedIndex = -1

    // 구매 결과를 알려 주는 쪽지 (로비와 같은 모양)
    private var noticeText = ""
    private var noticeTimer = 0f

    private val bgPaint = Paint().apply { color = Color.rgb(252, 249, 240) }
    private val titlePaint = Paint().apply {
        color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val cardPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val cardPressPaint = Paint().apply { color = Color.rgb(255, 244, 200); isAntiAlias = true }
    private val cardEdgePaint = Paint().apply {
        color = Color.rgb(30, 28, 26); style = Paint.Style.STROKE
        strokeWidth = 4.5f; isAntiAlias = true
    }
    private val namePaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val pricePaint = Paint().apply {
        color = Color.rgb(190, 50, 78); textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val hintPaint = Paint().apply {
        color = Color.rgb(140, 132, 122); textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val rubyCountPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val ownedBadgePaint = Paint().apply { color = Color.rgb(92, 176, 96); isAntiAlias = true }
    private val ownedTextPaint = Paint().apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val backPaint = Paint().apply { color = Color.rgb(255, 214, 74); isAntiAlias = true }
    private val backTextPaint = Paint().apply {
        color = Color.rgb(60, 44, 12); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val noticePaint = Paint().apply { color = Color.rgb(255, 250, 225); isAntiAlias = true }
    private val noticeTextPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val bitmapPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    /** 루비 모양 전용 — 다른 페인트 설정을 건드리지 않게 따로 둔다 */
    private val gemPaint = Paint().apply { isAntiAlias = true }

    private val srcRect = Rect()
    private val dstRect = RectF()
    private val badgeRect = RectF()
    private val noticeRect = RectF()

    override fun update(deltaTime: Float) {
        if (noticeTimer > 0f) noticeTimer -= deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        titlePaint.textSize = h * 0.075f
        canvas.drawText("고양이 상점", w / 2f, h * 0.095f, titlePaint)

        // 오른쪽 위: 지금 가진 루비
        rubyCountPaint.textSize = h * 0.048f
        val rubyText = "${GameProgress.rubies}"
        val gemR = h * 0.026f
        val textW = rubyCountPaint.measureText(rubyText)
        val gemCx = w - 30f - textW - gemR * 1.5f
        drawRubyIcon(canvas, gemCx, h * 0.075f, gemR, gemPaint)
        canvas.drawText(rubyText, gemCx + gemR * 1.4f, h * 0.092f, rubyCountPaint)

        // 카드 격자: 8 x 2 = 16칸 — 물건이 15개라 한 화면에 다 들어간다 (쪽 넘김 없음)
        val cols = 8
        val padX = w * 0.020f
        val gap = w * 0.011f
        val cw = (w - padX * 2f - gap * (cols - 1)) / cols
        val ch = h * 0.305f
        val top = h * 0.145f

        for ((i, item) in ShopCatalog.items.withIndex()) {
            val col = i % cols
            val row = i / cols
            val l = padX + col * (cw + gap)
            val t = top + row * (ch + h * 0.035f)
            itemRects[i].set(l, t, l + cw, t + ch)
            drawItemCard(canvas, item, itemRects[i], h, i == pressedIndex)
        }

        hintPaint.textSize = h * 0.030f
        canvas.drawText("루비는 루비 상점에서 점수로 바꿀 수 있어요", w / 2f, h * 0.855f, hintPaint)

        // 돌아가기 버튼
        val bw = w * 0.18f
        val bh = h * 0.10f
        backRect.set(w / 2f - bw / 2f, h * 0.878f, w / 2f + bw / 2f, h * 0.878f + bh)
        canvas.drawRoundRect(backRect, 20f, 20f, backPaint)
        canvas.drawRoundRect(backRect, 20f, 20f, cardEdgePaint)
        backTextPaint.textSize = bh * 0.42f
        canvas.drawText("돌아가기", backRect.centerX(), backRect.centerY() + bh * 0.15f, backTextPaint)

        drawNotice(canvas, w, h)
    }

    private fun drawItemCard(
        canvas: Canvas, item: ShopCatalog.Item, rect: RectF, h: Float, pressed: Boolean
    ) {
        canvas.drawRoundRect(rect, 14f, 14f, if (pressed) cardPressPaint else cardPaint)
        canvas.drawRoundRect(rect, 14f, 14f, cardEdgePaint)

        // 물건 그림은 카드 위쪽 58%에 비율 그대로
        val icon = SpriteManager.shopIcons[item.id]
        if (icon != null) {
            val availW = rect.width() - 20f
            val availH = rect.height() * 0.58f
            val scale = min(availW / icon.width, availH / icon.height)
            val bw = icon.width * scale
            val bh = icon.height * scale
            val cx = rect.centerX()
            val cy = rect.top + rect.height() * 0.36f
            srcRect.set(0, 0, icon.width, icon.height)
            dstRect.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
            canvas.drawBitmap(icon, srcRect, dstRect, bitmapPaint)
        }

        // 이름이 길면("낚싯대 장난감") 카드 폭에 맞게 글자를 줄인다
        namePaint.textSize = h * 0.030f
        val maxNameW = rect.width() - 14f
        while (namePaint.textSize > h * 0.020f && namePaint.measureText(item.name) > maxNameW) {
            namePaint.textSize -= h * 0.002f
        }
        canvas.drawText(item.name, rect.centerX(), rect.bottom - rect.height() * 0.18f, namePaint)

        // 값: 루비 모양 + "N 루비"
        pricePaint.textSize = h * 0.026f
        val priceText = "${item.price} 루비"
        val gemR = h * 0.017f
        val priceW = pricePaint.measureText(priceText)
        val blockW = gemR * 2f + h * 0.008f + priceW
        val startX = rect.centerX() - blockW / 2f
        val baseY = rect.bottom - rect.height() * 0.055f
        drawRubyIcon(canvas, startX + gemR, baseY - h * 0.011f, gemR, gemPaint)
        canvas.drawText(priceText, startX + gemR * 2f + h * 0.008f, baseY, pricePaint)

        // 이미 가지고 있으면 오른쪽 위에 개수 배지
        val owned = GameProgress.itemCount(item.id)
        if (owned > 0) {
            ownedTextPaint.textSize = h * 0.019f
            val label = "보유 $owned"
            val padding = h * 0.012f
            val bw = ownedTextPaint.measureText(label) + padding * 2f
            val bh = h * 0.038f
            badgeRect.set(rect.right - bw - 6f, rect.top + 6f, rect.right - 6f, rect.top + 6f + bh)
            canvas.drawRoundRect(badgeRect, bh / 2f, bh / 2f, ownedBadgePaint)
            canvas.drawText(
                label, badgeRect.centerX(), badgeRect.centerY() + bh * 0.30f, ownedTextPaint
            )
        }
    }

    /** 구매 결과 쪽지 — 로비의 안내 쪽지와 같은 모양 */
    private fun drawNotice(canvas: Canvas, w: Float, h: Float) {
        if (noticeTimer <= 0f) return
        val alpha = (255 * min(1f, noticeTimer / 0.4f)).toInt()
        val boxW = w * 0.42f
        val boxH = h * 0.11f
        noticeRect.set(w / 2f - boxW / 2f, h * 0.42f, w / 2f + boxW / 2f, h * 0.42f + boxH)

        noticePaint.alpha = alpha
        canvas.drawRoundRect(noticeRect, 18f, 18f, noticePaint)
        cardEdgePaint.alpha = alpha
        canvas.drawRoundRect(noticeRect, 18f, 18f, cardEdgePaint)
        cardEdgePaint.alpha = 255

        noticeTextPaint.alpha = alpha
        noticeTextPaint.textSize = h * 0.042f
        canvas.drawText(
            noticeText, noticeRect.centerX(), noticeRect.centerY() + h * 0.015f, noticeTextPaint
        )
        noticeTextPaint.alpha = 255
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressedIndex = itemAt(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                if (backRect.contains(event.x, event.y)) {
                    pressedIndex = -1
                    gameView.showLobby()
                    return true
                }
                val hit = itemAt(event.x, event.y)
                if (hit >= 0 && hit == pressedIndex) buy(hit)
                pressedIndex = -1
            }
            MotionEvent.ACTION_CANCEL -> pressedIndex = -1
        }
        return true
    }

    private fun itemAt(x: Float, y: Float): Int =
        itemRects.indexOfFirst { it.contains(x, y) }

    private fun buy(index: Int) {
        val item = ShopCatalog.items[index]
        if (item.id == "revive_all") {
            // 다부활: 물건이 아니라 사는 즉시 무덤 냥이 전부 부활 (되살릴 냥이가 없으면 안 판다)
            val dead = com.jamcat.nayngapo.engine.CatTeam.deadCount()
            when {
                dead == 0 -> showNotice("되살릴 냥이가 없다냥!")
                !GameProgress.spendRubies(item.price) -> showNotice("루비가 부족하다냥!")
                else -> {
                    val n = com.jamcat.nayngapo.engine.CatTeam.reviveAll()
                    showNotice("냥이 ${n}마리 다부활!")
                    SoundManager.playCatHit()
                }
            }
            return
        }
        if (GameProgress.buyItem(item.id, item.price)) {
            showNotice("${item.name} 구매!")
            SoundManager.playCatHit()   // '예에에용!' — 짧고 기분 좋은 소리
        } else {
            showNotice("루비가 부족하다냥!")
        }
    }

    private fun showNotice(text: String) {
        noticeText = text
        noticeTimer = 1.6f
    }

    override fun onBack(): Boolean {
        gameView.showLobby()
        return true
    }
}
