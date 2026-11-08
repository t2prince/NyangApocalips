package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SpriteManager
import kotlin.math.min
import kotlin.math.sin

/**
 * 스테이지 클리어 화면: 해치운 냥이들이 하나씩 떨어져 뒤에 수북이 쌓이고,
 * 그 위에 '스테이지 클리어' 표시가 뜬다. 아무 데나 누르면 로비로 돌아간다.
 */
class StageClearScreen(
    private val gameView: GameView,
    private val stageNumber: Int,
    private val score: Int,
    private val rescueCount: Int,
    private val defeatedEnemies: Int,
    private val totalEnemies: Int,
    private val playerLevel: Int,
    /** 야간 전투 승리로 더 받은 루비 (0 이면 낮 전투) */
    private val nightBonus: Int = 0
) : Screen {
    private var timer = 0f

    /** 쌓아 올릴 냥이 수 — 너무 많으면 화면을 덮으므로 위쪽을 잘라 둔다 */
    private val pileCount = min(defeatedEnemies, MAX_PILE)

    private val bgPaint = Paint().apply { color = Color.rgb(24, 34, 54) }
    private val scrimPaint = Paint().apply { color = Color.argb(120, 12, 18, 32) }
    private val bitmapPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val titlePaint = Paint().apply {
        color = Color.rgb(255, 220, 50); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(8f, 3f, 3f, Color.BLACK)
    }
    private val subPaint = Paint().apply {
        color = Color.rgb(255, 240, 190); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(6f, 2f, 2f, Color.BLACK)
    }
    private val tapPaint = Paint().apply {
        color = Color.rgb(215, 220, 230); textAlign = Paint.Align.CENTER; isAntiAlias = true
        setShadowLayer(5f, 2f, 2f, Color.BLACK)
    }
    private val starPaint = Paint().apply {
        color = Color.rgb(255, 220, 50); textAlign = Paint.Align.CENTER; isAntiAlias = true
        setShadowLayer(6f, 2f, 2f, Color.BLACK)
    }
    private val labelPaint = Paint().apply {
        color = Color.rgb(185, 195, 215); textAlign = Paint.Align.RIGHT; isAntiAlias = true
        setShadowLayer(5f, 2f, 2f, Color.BLACK)
    }
    private val numPaint = Paint().apply {
        color = Color.rgb(255, 220, 80); textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(5f, 2f, 2f, Color.BLACK)
    }
    private val endPaint = Paint().apply {
        color = Color.rgb(130, 255, 130); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(6f, 2f, 2f, Color.BLACK)
    }
    private val dustPaint = Paint().apply { color = Color.argb(70, 0, 0, 0); isAntiAlias = true }

    // 디지털 전광판
    private val boardRect = RectF()
    private val boardBgPaint = Paint().apply { color = Color.rgb(16, 18, 20); isAntiAlias = true }
    private val boardFramePaint = Paint().apply {
        color = Color.rgb(90, 96, 104); style = Paint.Style.STROKE
        strokeWidth = 6f; isAntiAlias = true
    }
    private val boardLabelPaint = Paint().apply {
        color = Color.rgb(120, 200, 120); textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE; isAntiAlias = true
    }
    private val digitPaint = Paint().apply {
        color = Color.rgb(255, 210, 40); textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); isAntiAlias = true
        setShadowLayer(14f, 0f, 0f, Color.rgb(255, 170, 20))   // 전광판 번짐 빛
    }

    // STAGE CLEAR 버튼
    private val buttonRect = RectF()
    private val buttonPaint = Paint().apply { isAntiAlias = true }
    private val buttonEdgePaint = Paint().apply {
        color = Color.rgb(120, 70, 20); style = Paint.Style.STROKE
        strokeWidth = 6f; isAntiAlias = true
    }
    private val buttonTextPaint = Paint().apply {
        color = Color.rgb(70, 40, 10); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }

    // 쓰러진 냥이의 X 눈
    private val xEyePaint = Paint().apply {
        color = Color.rgb(30, 26, 24); strokeWidth = 4.5f
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }

    private val catMatrix = Matrix()

    override fun update(deltaTime: Float) {
        timer += deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 뒤쪽에 해치운 냥이들이 수북이 쌓인다
        drawCatPile(canvas, w, h)

        // 글씨가 묻히지 않도록 살짝 어둡게 덮는다
        canvas.drawRect(0f, 0f, w, h, scrimPaint)

        // 기획6: 맵 끝의 디지털 점수 전광판
        drawScoreBoard(canvas, w, h)

        titlePaint.textSize = h * 0.085f
        canvas.drawTr("스테이지 $stageNumber 클리어!", w / 2f, h * 0.36f, titlePaint)
        if (nightBonus > 0) {
            val ts = subPaint.textSize
            subPaint.textSize = h * 0.042f
            canvas.drawTr("🌙 야간 전투 승리! 루비 두 배 (+$nightBonus)", w / 2f, h * 0.27f, subPaint)
            subPaint.textSize = ts
        }

        // 결과 정리: 처치·구출·레벨
        labelPaint.textSize = h * 0.040f
        numPaint.textSize = h * 0.044f
        val cxL = w / 2f - 20f
        val cxR = w / 2f + 20f
        var rowY = h * 0.45f
        val rowGap = h * 0.060f
        val rows = listOf(
            "무찌른 냥이" to "$defeatedEnemies / $totalEnemies",
            "구출한 인간" to "${rescueCount}명",
            "도달 레벨" to "Lv.$playerLevel",
        )
        for ((label, value) in rows) {
            canvas.drawTr(label, cxL, rowY, labelPaint)
            canvas.drawTr(value, cxR, rowY, numPaint)
            rowY += rowGap
        }

        when (stageNumber) {
            10 -> {
                endPaint.textSize = h * 0.048f
                canvas.drawTr("순간냥이를 타도했습니다!", w / 2f, h * 0.66f, endPaint)
                canvas.drawTr("다음은 하늘이다 — 날개냥이를 타고 날아오르자!", w / 2f, h * 0.72f, endPaint)
            }
            11 -> {
                endPaint.textSize = h * 0.048f
                canvas.drawTr("하늘의 먼지냥이를 타도했습니다!", w / 2f, h * 0.66f, endPaint)
                canvas.drawTr("저 아래 울창한 정글이 기다린다!", w / 2f, h * 0.72f, endPaint)
            }
            20 -> {
                endPaint.textSize = h * 0.048f
                canvas.drawTr("마법사 냥이 삼형제를 타도했습니다!", w / 2f, h * 0.66f, endPaint)
                canvas.drawTr("정글에 평화가 찾아왔다!", w / 2f, h * 0.72f, endPaint)
            }
        }

        // 기획6: 'STAGE CLEAR' 영문 글자를 눌러야 스테이지가 끝난다
        drawClearButton(canvas, w, h)
    }

    /** 디지털 전광판: 검은 판 위에 번쩍이는 노란 숫자 (기획6의 '점수 100' 전광판) */
    private fun drawScoreBoard(canvas: Canvas, w: Float, h: Float) {
        val bw = w * 0.34f
        val bh = h * 0.17f
        boardRect.set(w / 2f - bw / 2f, h * 0.055f, w / 2f + bw / 2f, h * 0.055f + bh)
        canvas.drawRoundRect(boardRect, 16f, 16f, boardBgPaint)
        canvas.drawRoundRect(boardRect, 16f, 16f, boardFramePaint)

        boardLabelPaint.textSize = bh * 0.24f
        canvas.drawTr("점수", boardRect.centerX(), boardRect.top + bh * 0.30f, boardLabelPaint)

        // 켜지는 연출: 잠깐 깜빡이다가 점수가 올라간다
        val shown = if (timer < 0.5f && (timer * 8).toInt() % 2 == 0) ""
        else "${(score * ((timer - 0.3f) / 1.2f).coerceIn(0f, 1f)).toInt()}"
        digitPaint.textSize = bh * 0.46f
        canvas.drawTr(shown, boardRect.centerX(), boardRect.bottom - bh * 0.18f, digitPaint)
    }

    /** 눌러야 끝나는 STAGE CLEAR 버튼 */
    private fun drawClearButton(canvas: Canvas, w: Float, h: Float) {
        val bw = w * 0.36f
        val bh = h * 0.105f
        val pulse = (sin(timer.toDouble() * 3.0) * 0.5 + 0.5).toFloat()
        buttonRect.set(w / 2f - bw / 2f, h * 0.845f, w / 2f + bw / 2f, h * 0.845f + bh)
        buttonPaint.color = Color.rgb(255, (200 + 40 * pulse).toInt(), 40)
        canvas.drawRoundRect(buttonRect, 22f, 22f, buttonPaint)
        canvas.drawRoundRect(buttonRect, 22f, 22f, buttonEdgePaint)
        buttonTextPaint.textSize = bh * 0.42f
        canvas.drawTr("STAGE CLEAR", buttonRect.centerX(),
            buttonRect.centerY() + bh * 0.15f, buttonTextPaint)
    }

    /**
     * 해치운 냥이들이 하나씩 위에서 툭툭 떨어져 피라미드처럼 쌓인다.
     * i번째 냥이는 i * DROP_GAP 초에 떨어지기 시작해 짧게 튕기며 자리를 잡는다.
     */
    private fun drawCatPile(canvas: Canvas, w: Float, h: Float) {
        if (pileCount <= 0) return

        val baseY = h * 0.99f
        val catH = h * 0.155f
        val rowH = catH * 0.52f          // 아래 냥이 몸에 반쯤 파묻히도록 겹쳐 쌓는다
        val stepX = catH * 0.86f

        var index = 0
        var row = 0
        while (index < pileCount) {
            val inRow = (ROW_WIDTH - row).coerceAtLeast(1)
            val count = min(inRow, pileCount - index)
            val rowY = baseY - row * rowH
            // 줄이 올라갈수록 가운데로 모인다
            val startX = w / 2f - (count - 1) * stepX / 2f
            for (k in 0 until count) {
                drawPiledCat(canvas, index, startX + k * stepX, rowY, catH)
                index++
            }
            row++
        }

        // 꼭대기에서 인간이 신나게 춤을 춘다 (기획6)
        val topRow = row - 1
        val topY = baseY - (topRow + 1) * rowH
        if (timer > pileCount * DROP_GAP + 0.2f) {
            val frames = SpriteManager.playerWalk
            val beat = (timer * 5f).toInt()
            val hero = frames[beat % frames.size]
            val flip = if ((beat / 2) % 2 == 0) 1f else -1f     // 박자마다 좌우로 돌며
            val hop = kotlin.math.abs(sin(timer.toDouble() * 6.0)).toFloat() * h * 0.018f
            val sway = sin(timer.toDouble() * 6.0).toFloat() * 8f
            val scale = (h * 0.20f) / hero.height
            catMatrix.reset()
            catMatrix.postTranslate(-hero.width / 2f, -hero.height.toFloat())
            catMatrix.postScale(scale * flip, scale)
            catMatrix.postRotate(sway)
            catMatrix.postTranslate(w / 2f, topY + 12f - hop)
            canvas.drawBitmap(hero, catMatrix, bitmapPaint)
        }
    }

    /** 냥이 한 마리를 떨어뜨려 제자리에 눕힌다 */
    private fun drawPiledCat(canvas: Canvas, index: Int, cx: Float, cy: Float, targetH: Float) {
        val start = index * DROP_GAP
        if (timer < start) return

        val bmp = PILE_SPRITES[index % PILE_SPRITES.size]()
        val t = ((timer - start) / DROP_TIME).coerceIn(0f, 1f)
        // 위에서 떨어져 살짝 튕겼다가 멈춘다
        val fall = (1f - t) * (1f - t) * 260f
        val bounce = if (t > 0.75f) sin((t - 0.75f) * 12.0).toFloat() * 7f * (1f - t) * 4f else 0f
        val y = cy - fall + bounce

        // 널브러진 느낌으로 제각기 다르게 기울인다
        val tilt = ((index * 47) % 61 - 30).toFloat()
        val flip = if (index % 2 == 0) 1f else -1f
        val scale = targetH / bmp.height

        catMatrix.reset()
        catMatrix.postTranslate(-bmp.width / 2f, -bmp.height.toFloat())
        catMatrix.postScale(scale * flip, scale)
        catMatrix.postRotate(tilt)
        catMatrix.postTranslate(cx, y)

        // 바닥에 닿은 뒤에는 옅은 그림자를 깔아 쌓인 느낌을 준다
        if (t > 0.9f) {
            canvas.drawOval(
                cx - targetH * 0.34f, y - 10f, cx + targetH * 0.34f, y + 8f, dustPaint
            )
        }
        canvas.drawBitmap(bmp, catMatrix, bitmapPaint)

        // 기절한 X 눈 (기획6: 쓰러진 냥이들은 눈이 엑스표)
        if (t >= 1f) {
            val ex = cx + flip * targetH * 0.16f
            val ey = y - targetH * 0.62f
            for (side in intArrayOf(-1, 1)) {
                val ox = ex + side * targetH * 0.11f
                val r = targetH * 0.055f
                canvas.drawLine(ox - r, ey - r, ox + r, ey + r, xEyePaint)
                canvas.drawLine(ox + r, ey - r, ox - r, ey + r, xEyePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 기획6: 'STAGE CLEAR' 글자를 눌러야 스테이지가 끝난다
        if (event.actionMasked == MotionEvent.ACTION_DOWN && timer > 1.2f &&
            buttonRect.contains(event.x, event.y)
        ) {
            gameView.leaveStage()
        }
        return true
    }

    override fun onBack(): Boolean {
        gameView.leaveStage()
        return true
    }

    private companion object {
        const val MAX_PILE = 21          // 화면을 덮지 않을 만큼만 쌓는다
        const val ROW_WIDTH = 6          // 맨 아래 줄에 놓이는 냥이 수
        const val DROP_GAP = 0.09f       // 다음 냥이가 떨어지기까지 간격(초)
        const val DROP_TIME = 0.42f      // 한 마리가 떨어져 자리 잡는 시간(초)

        /** 쌓기에 쓰는 냥이들 (SpriteManager가 초기화된 뒤 읽도록 지연 평가) */
        val PILE_SPRITES: List<() -> Bitmap> = listOf(
            { SpriteManager.normalCat }, { SpriteManager.blueCat }, { SpriteManager.greenCat },
            { SpriteManager.poopCat }, { SpriteManager.fluffyCat }, { SpriteManager.flyingCat },
            { SpriteManager.anvilCat }, { SpriteManager.laserCat }, { SpriteManager.thiefCat },
        )
    }
}
