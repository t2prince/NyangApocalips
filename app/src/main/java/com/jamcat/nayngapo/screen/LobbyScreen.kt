package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.drawTr
import com.jamcat.nayngapo.engine.measureTr
import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.CatTeam
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SpriteManager
import com.jamcat.nayngapo.ui.drawRubyIcon
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * lobby 기획서(Plan/lobby.jpeg) 그대로의 로비 화면.
 * 도화지에 사인펜으로 칸을 나눈 손그림 레이아웃 — 왼쪽 3칸 / 가운데 지도 / 오른쪽 3칸 / 아래 5칸.
 *
 * 가운데 지도는 돌 하나가 스테이지 하나인 긴 길이며, 좌우로 밀어 100번째 돌까지 볼 수 있다.
 * 아직 만들지 않은 기능·스테이지는 눌러도 안내 쪽지만 뜬다.
 */
/** mapOnly: 상점 마을 '스테이지 깨기'에서 열면 지도만 화면 가득 (대표님 2026-10-09 「스테이지로 넘어갈 때 스테이지 지도가 나와, 그냥 숫자 말고」) */
class LobbyScreen(private val gameView: GameView, private val mapOnly: Boolean = false) : Screen {

    /** 화면 비율(0~1) 좌표로 정의한 칸 하나 */
    private data class Slot(
        val id: String,
        val label: String,
        val l: Float, val t: Float, val r: Float, val b: Float,
        /** 하단 가로줄처럼 납작한 칸 — 라벨을 작게 줄여 그림 자리를 넓힌다 */
        val compact: Boolean = false
    )

    private val slots = listOf(
        // 왼쪽 세로줄
        // '고양이 상점' 칸은 뺐다 — 고양이 상점은 상점 마을의 천막 중 하나 (대표님 2026-09-27)
        // '캐릭터 변경' 칸을 지우고 뽑기 (대표님 2026-10-17 「캐릭터 변경을 지우고 뽑기 만들어 줘」) — 상점 마을 집 앞 뽑기와 같은 뽑기
        Slot("gacha", "뽑기", 0.015f, 0.100f, 0.245f, 0.470f),
        Slot("cat_skill", "고양이 기술", 0.015f, 0.490f, 0.245f, 0.855f),
        // 가운데 스테이지 길
        Slot("worldmap", "", 0.260f, 0.100f, 0.735f, 0.855f),
        // 오른쪽 세로줄
        Slot("battle", "전투", 0.750f, 0.100f, 0.985f, 0.340f),
        Slot("cat_manage", "캣타워", 0.750f, 0.360f, 0.985f, 0.600f),
        Slot("shop", "상점", 0.750f, 0.620f, 0.985f, 0.855f),
        // 아래 가로줄 5칸
        Slot("internet", "인터넷", 0.015f, 0.868f, 0.203f, 0.992f, compact = true),
        Slot("settings", "설정", 0.213f, 0.868f, 0.401f, 0.992f, compact = true),
        Slot("ruby_shop", "루비 상점", 0.411f, 0.868f, 0.599f, 0.992f, compact = true),
        Slot("skill", "기술", 0.609f, 0.868f, 0.797f, 0.992f, compact = true),
        Slot("mission", "미션", 0.807f, 0.868f, 0.985f, 0.992f, compact = true),
    )

    private var timer = 0f
    private var pressedId: String? = null
    private val charRect = RectF()

    // 미구현 기능을 눌렀을 때 잠깐 떠오르는 쪽지
    private var noticeText = ""
    private var noticeTimer = 0f

    // ===== 가운데 스테이지 지도 =====
    /** 지도 그림이 실제로 그려진 자리 (돌 위치·터치 판정의 기준) */
    private val mapRect = RectF()

    /** 지도 그림 속 돌 하나하나의 한가운데 (그림 안에서의 비율 좌표, 진행 순서대로) */
    private data class StageNode(val nx: Float, val ny: Float)

    private val stageNodes = listOf(
        // 숲
        StageNode(0.166f, 0.283f), StageNode(0.203f, 0.275f), StageNode(0.240f, 0.286f),
        StageNode(0.272f, 0.310f), StageNode(0.296f, 0.344f), StageNode(0.327f, 0.381f),
        StageNode(0.350f, 0.415f), StageNode(0.372f, 0.454f),
        // 설산과 얼음 호수
        StageNode(0.403f, 0.483f), StageNode(0.444f, 0.502f), StageNode(0.486f, 0.513f),
        StageNode(0.530f, 0.503f), StageNode(0.569f, 0.482f), StageNode(0.604f, 0.458f),
        StageNode(0.634f, 0.426f), StageNode(0.669f, 0.404f),
        // 화산으로 오르는 길
        StageNode(0.713f, 0.411f), StageNode(0.743f, 0.441f), StageNode(0.769f, 0.471f),
        StageNode(0.793f, 0.495f), StageNode(0.823f, 0.522f), StageNode(0.845f, 0.558f),
        StageNode(0.858f, 0.601f), StageNode(0.849f, 0.642f), StageNode(0.830f, 0.680f),
        // 바닷가를 지나 사막으로
        StageNode(0.805f, 0.713f), StageNode(0.774f, 0.738f), StageNode(0.738f, 0.756f),
        StageNode(0.699f, 0.765f), StageNode(0.658f, 0.759f), StageNode(0.625f, 0.746f),
        StageNode(0.511f, 0.720f), StageNode(0.473f, 0.733f), StageNode(0.440f, 0.750f),
        StageNode(0.399f, 0.777f), StageNode(0.369f, 0.808f), StageNode(0.325f, 0.831f),
    )

    /**
     * 지도 쪽 나누기 (대표님 2026-10-09 「128 스테이지까지」): 지도 그림 한 장에 돌 128개는 너무 빽빽해서
     * 한 쪽에 돌 PAGE_SIZE 개(그림 속 앞쪽 32자리)씩 4쪽으로 나누고, 지도 양옆 화살표로 넘긴다. 처음엔 지금 스테이지가 있는 쪽.
     */
    private val PAGE_SIZE = 32
    private val pageCount = (GameProgress.TOTAL_STAGES + PAGE_SIZE - 1) / PAGE_SIZE
    private var mapPage = ((GameProgress.unlockedStage - 1) / PAGE_SIZE).coerceIn(0, pageCount - 1)
    private val prevPageRect = RectF()
    private val nextPageRect = RectF()
    private val arrowPaint = Paint().apply { color = Color.argb(200, 40, 36, 32); isAntiAlias = true }
    private val arrowPath = Path()

    private val paperPaint = Paint().apply { color = Color.rgb(252, 249, 240) }
    private val panelPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val panelPressPaint = Paint().apply { color = Color.rgb(255, 244, 200); isAntiAlias = true }
    private val borderPaint = Paint().apply {
        color = Color.rgb(30, 28, 26); style = Paint.Style.STROKE
        strokeWidth = 5f; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
    }
    private val titlePaint = Paint().apply {
        color = Color.rgb(40, 36, 32); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val hintPaint = Paint().apply {
        color = Color.rgb(90, 84, 78); textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val noticePaint = Paint().apply {
        color = Color.rgb(255, 250, 225); isAntiAlias = true
    }
    private val bitmapPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val noticeDotPaint = Paint().apply { color = Color.rgb(232, 66, 56); isAntiAlias = true }
    /** 루비 상점 칸의 보유 루비 표시 */
    private val rubyHintPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val gemPaint = Paint().apply { isAntiAlias = true }

    // 돌(스테이지) 표시
    private val stoneFillPaint = Paint().apply { isAntiAlias = true }
    private val stoneSidePaint = Paint().apply { color = Color.rgb(126, 126, 130); isAntiAlias = true }
    private val stoneOutlinePaint = Paint().apply {
        color = Color.rgb(40, 38, 36); style = Paint.Style.STROKE
        strokeWidth = 3.5f; isAntiAlias = true
    }
    private val stoneNumPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val stoneLockPaint = Paint().apply { color = Color.rgb(96, 96, 96); isAntiAlias = true }
    private val stoneRingPaint = Paint().apply {
        color = Color.rgb(255, 235, 130); style = Paint.Style.STROKE
        strokeWidth = 5f; isAntiAlias = true
    }
    private val hereTagPaint = Paint().apply {
        color = Color.rgb(35, 32, 30); textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        setShadowLayer(5f, 0f, 0f, Color.WHITE)
    }

    private val slotRect = RectF()
    private val iconSrc = Rect()
    private val iconDst = RectF()

    override fun update(deltaTime: Float) {
        timer += deltaTime
        if (noticeTimer > 0f) noticeTimer -= deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        canvas.drawRect(0f, 0f, w, h, paperPaint)

        if (mapOnly) { drawMapOnly(canvas, w, h); return }

        titlePaint.textSize = h * 0.072f
        canvas.drawTr("냥이 아포칼립스", w / 2f, h * 0.077f, titlePaint)

        hintPaint.textSize = h * 0.030f

        for (slot in slots) {
            slotRect.set(slot.l * w, slot.t * h, slot.r * w, slot.b * h)
            drawSlot(canvas, slot, slotRect, h)
        }
        // 뽑기 위 작은 '캐릭터 변경' (대표님 2026-10-24 「뽑기 위에는 캐릭터 변경이 작게 있는데」)
        charRect.set(0.015f * w, 0.022f * h, 0.245f * w, 0.088f * h)
        canvas.drawRoundRect(charRect, 14f, 14f, if (pressedId == "char") panelPressPaint else panelPaint)
        canvas.drawRoundRect(charRect, 14f, 14f, borderPaint)
        labelPaint.textSize = h * 0.032f
        canvas.drawTr("🙂 캐릭터 변경", charRect.centerX(), charRect.centerY() + h * 0.011f, labelPaint)

        drawNotice(canvas, w, h)
    }

    private fun drawSlot(canvas: Canvas, slot: Slot, rect: RectF, h: Float) {
        val pressed = slot.id == pressedId
        canvas.drawRoundRect(rect, 14f, 14f, if (pressed) panelPressPaint else panelPaint)

        // 라벨은 칸 위쪽에, 그림은 남은 자리에 꽉 차게
        var iconTop = rect.top + 8f
        if (slot.label.isNotEmpty()) {
            labelPaint.textSize = if (slot.compact) h * 0.030f else h * 0.042f
            val baseline = rect.top + if (slot.compact) h * 0.036f else h * 0.052f
            canvas.drawTr(slot.label, rect.centerX(), baseline, labelPaint)
            iconTop = baseline + h * 0.008f
        }

        // 가운데 칸은 그림 대신 '스테이지 길'을 그린다
        if (slot.id == "worldmap") {
            drawStageMap(canvas, rect, h)
            canvas.drawRoundRect(rect, 14f, 14f, borderPaint)
            return
        }

        val bottomInset = when (slot.id) {
            "battle" -> h * 0.038f
            "cat_manage" -> h * 0.038f  // 아래에 팀 냥이 수를 적어 준다
            "gacha" -> h * 0.038f       // 아래에 뽑기 값을 적어 준다
            "cat_skill" -> h * 0.038f   // 아래에 배운 기술 수를 적어 준다
            "ruby_shop" -> h * 0.030f   // 아래에 지금 가진 루비를 적어 준다
            "skill" -> h * 0.030f       // 아래에 배운 인간 기술 수를 적어 준다
            else -> 10f
        }

        val icon = SpriteManager.lobbyIcons[slot.id]
        if (icon != null) {
            iconSrc.set(0, 0, icon.width, icon.height)
            fitCenter(icon.width.toFloat(), icon.height.toFloat(), rect, iconTop, bottomInset, iconDst)
            canvas.drawBitmap(icon, iconSrc, iconDst, bitmapPaint)
        }

        // 새 냥이·새 도감 항목이 있거나, 배고프고 지저분해 손이 필요한 냥이가 있으면 빨간 알림 점
        if ((slot.id == "cat_manage" &&
            (CatTeam.hasNewCat() || CatTeam.needsCare() || GameProgress.hasNewDexEntry() ||
                // 되살릴 무덤이 있고 부활 약도 가지고 있으면 알려 준다
                (CatTeam.hasGrave() && GameProgress.itemCount("revive_potion") > 0))) ||
            // 받을 수 있는 미션 보상이 있으면 미션 칸에도 점
            (slot.id == "mission" && com.jamcat.nayngapo.engine.Missions.hasClaimable())
        ) {
            val pulse = (sin(timer.toDouble() * 4.0) * 0.5 + 0.5).toFloat()
            noticeDotPaint.alpha = (170 + 85 * pulse).toInt()
            canvas.drawCircle(rect.right - 22f, rect.top + 22f, 13f, noticeDotPaint)
        }

        // 기술(인간) 칸 아래에는 배운 기술 수를 적어 준다 (납작한 칸이라 글씨를 작게)
        if (slot.id == "skill") {
            hintPaint.textSize = h * 0.022f
            val hs = com.jamcat.nayngapo.engine.HumanSkills.all
            canvas.drawTr(
                if (hs.isEmpty()) "준비 중"
                else "배운 기술 ${com.jamcat.nayngapo.engine.HumanSkills.learnedCount()} / ${hs.size}",
                rect.centerX(), rect.bottom - h * 0.008f, hintPaint
            )
        }

        // 고양이 기술 칸 아래에는 배운 기술 수를 적어 준다
        if (slot.id == "cat_skill") {
            hintPaint.textSize = h * 0.028f
            val skills = com.jamcat.nayngapo.engine.CatSkills.all
            canvas.drawTr(
                if (skills.isEmpty()) "준비 중"
                else "배운 기술 ${com.jamcat.nayngapo.engine.CatSkills.learnedCount()} / ${skills.size}",
                rect.centerX(), rect.bottom - h * 0.014f, hintPaint
            )
        }

        // 뽑기 칸 아래에는 한 번 값을 적어 준다
        if (slot.id == "gacha") {
            hintPaint.textSize = h * 0.028f
            canvas.drawTr("한 번 루비 ${GachaScreen.PRICE}", rect.centerX(), rect.bottom - h * 0.014f, hintPaint)
        }

        // 고양이 관리 칸 아래에는 팀 냥이 수와 출전 인원을 적어 준다
        if (slot.id == "cat_manage") {
            hintPaint.textSize = h * 0.028f
            canvas.drawTr(
                "냥이 ${CatTeam.living.size}마리 · 출전 ${CatTeam.party.size}",
                rect.centerX(), rect.bottom - h * 0.014f, hintPaint
            )
        }

        // 루비 상점 칸 아래에는 지금 가진 루비를 보석 모양과 함께 적어 준다
        if (slot.id == "ruby_shop") {
            rubyHintPaint.textSize = h * 0.030f
            val text = "${GameProgress.rubies}"
            val gemR = h * 0.014f
            val textW = rubyHintPaint.measureTr(text)
            val blockW = gemR * 2f + 6f + textW
            val startX = rect.centerX() - blockW / 2f
            val baseY = rect.bottom - h * 0.008f
            drawRubyIcon(canvas, startX + gemR, baseY - h * 0.010f, gemR, gemPaint)
            canvas.drawTr(text, startX + gemR * 2f + 6f, baseY, rubyHintPaint)
        }

        // 전투 칸에는 지금 출격할 스테이지 번호를 적어 준다
        if (slot.id == "battle") {
            hintPaint.textSize = h * 0.028f
            val next = GameProgress.nextBattleStage()
            val text = if (next != null) "스테이지 $next 출격!" else "모두 깼다냥!"
            canvas.drawTr(text, rect.centerX(), rect.bottom - h * 0.014f, hintPaint)
        }

        canvas.drawRoundRect(rect, 14f, 14f, borderPaint)
    }

    // ===================== 스테이지 길 =====================

    /** 지도 그림 위에서 i번째 돌의 화면 좌표 */
    private fun stoneX(index: Int) = mapRect.left + stageNodes[index].nx * mapRect.width()
    private fun stoneY(index: Int) = mapRect.top + stageNodes[index].ny * mapRect.height()

    /**
     * 원안 지도 그림(숲·설산·사막·화산)을 한 화면에 다 보여 주고,
     * 그림 속 돌 하나하나 위에 스테이지를 얹는다. 스크롤은 없다.
     */
    private fun drawStageMap(canvas: Canvas, rect: RectF, h: Float) {
        val map = SpriteManager.lobbyIcons["worldmap"] ?: return
        val bottomInset = h * 0.036f
        iconSrc.set(0, 0, map.width, map.height)
        fitCenter(map.width.toFloat(), map.height.toFloat(), rect, rect.top + 6f, bottomInset, iconDst)
        canvas.drawBitmap(map, iconSrc, iconDst, bitmapPaint)
        mapRect.set(iconDst)

        val stoneR = (mapRect.height() * 0.038f).coerceIn(9f, 26f)
        stoneNumPaint.textSize = stoneR * 1.15f
        hereTagPaint.textSize = stoneR * 1.2f
        val current = GameProgress.unlockedStage

        val onPage = (GameProgress.TOTAL_STAGES - mapPage * PAGE_SIZE).coerceAtMost(PAGE_SIZE)
        for (i in 0 until onPage) {
            val stage = mapPage * PAGE_SIZE + i + 1
            val cx = stoneX(i)
            val cy = stoneY(i)

            stoneFillPaint.color = when {
                !GameProgress.hasData(stage) -> Color.argb(150, 200, 200, 204)  // 아직 만들지 않은 스테이지
                stage < current -> Color.rgb(120, 200, 120)                     // 지나온 곳
                stage == current -> Color.rgb(255, 190, 40)                     // 지금 있는 곳
                else -> Color.rgb(150, 150, 154)                                // 잠긴 곳
            }
            canvas.drawCircle(cx, cy, stoneR, stoneFillPaint)
            canvas.drawCircle(cx, cy, stoneR, stoneOutlinePaint)

            if (stage <= current) {
                canvas.drawTr("$stage", cx, cy + stoneR * 0.40f, stoneNumPaint)
            } else if (GameProgress.hasData(stage)) {
                canvas.drawCircle(cx, cy, stoneR * 0.30f, stoneLockPaint)
            }

            if (stage == current) {
                val pulse = (sin(timer.toDouble() * 4.0) * 0.5 + 0.5).toFloat()
                stoneRingPaint.alpha = (110 + 145 * pulse).toInt()
                canvas.drawCircle(cx, cy, stoneR + 6f + pulse * 5f, stoneRingPaint)
                canvas.drawTr("여기", cx, cy - stoneR - 12f, hereTagPaint)
            }
        }

        // 쪽 넘기기 화살표 (첫 쪽엔 ◀ 없음, 마지막 쪽엔 ▶ 없음)
        val aw = mapRect.height() * 0.09f
        prevPageRect.setEmpty(); nextPageRect.setEmpty()
        if (mapPage > 0) {
            prevPageRect.set(mapRect.left + 4f, mapRect.centerY() - aw, mapRect.left + 4f + aw * 1.1f, mapRect.centerY() + aw)
            drawArrow(canvas, prevPageRect, -1f)
        }
        if (mapPage < pageCount - 1) {
            nextPageRect.set(mapRect.right - 4f - aw * 1.1f, mapRect.centerY() - aw, mapRect.right - 4f, mapRect.centerY() + aw)
            drawArrow(canvas, nextPageRect, 1f)
        }

        hintPaint.textSize = h * 0.028f
        canvas.drawTr(
            "지도 ${mapPage + 1}/$pageCount (${mapPage * PAGE_SIZE + 1}~${minOf(GameProgress.TOTAL_STAGES, (mapPage + 1) * PAGE_SIZE)})  ·  스테이지 ${GameProgress.unlockedStage} / ${GameProgress.TOTAL_STAGES}  —  돌을 누르면 출발!",
            rect.centerX(), rect.bottom - h * 0.012f, hintPaint
        )
    }

    /** 둥근 반투명 판 위의 삼각 화살표 */
    private fun drawArrow(canvas: Canvas, r: RectF, dir: Float) {
        canvas.drawRoundRect(r, r.width() * 0.4f, r.width() * 0.4f, panelPaint)
        canvas.drawRoundRect(r, r.width() * 0.4f, r.width() * 0.4f, borderPaint)
        val cx = r.centerX(); val cy = r.centerY(); val s = r.width() * 0.28f
        arrowPath.reset()
        arrowPath.moveTo(cx - dir * s * 0.6f, cy - s)
        arrowPath.lineTo(cx + dir * s * 0.8f, cy)
        arrowPath.lineTo(cx - dir * s * 0.6f, cy + s)
        arrowPath.close()
        canvas.drawPath(arrowPath, arrowPaint)
    }

    /** 원본 비율을 지키면서 칸 안(라벨 아래)에 가운데 정렬로 채운다 */
    private fun fitCenter(
        srcW: Float, srcH: Float, rect: RectF, top: Float, bottomInset: Float, out: RectF
    ) {
        val availW = rect.width() - 16f
        val availH = rect.bottom - top - bottomInset
        val scale = min(availW / srcW, availH / srcH)
        val dw = srcW * scale
        val dh = srcH * scale
        val cx = rect.centerX()
        val cy = (top + rect.bottom - bottomInset) / 2f
        out.set(cx - dw / 2f, cy - dh / 2f, cx + dw / 2f, cy + dh / 2f)
    }

    /** 미구현 안내 쪽지 */
    private fun drawNotice(canvas: Canvas, w: Float, h: Float) {
        if (noticeTimer <= 0f) return
        val alpha = (255 * min(1f, noticeTimer / 0.4f)).toInt()
        val boxW = w * 0.42f
        val boxH = h * 0.11f
        val rect = RectF(w / 2f - boxW / 2f, h * 0.42f, w / 2f + boxW / 2f, h * 0.42f + boxH)

        noticePaint.alpha = alpha
        canvas.drawRoundRect(rect, 18f, 18f, noticePaint)
        borderPaint.alpha = alpha
        canvas.drawRoundRect(rect, 18f, 18f, borderPaint)
        borderPaint.alpha = 255

        labelPaint.alpha = alpha
        labelPaint.textSize = h * 0.042f
        canvas.drawTr(noticeText, rect.centerX(), rect.centerY() + h * 0.015f, labelPaint)
        labelPaint.alpha = 255
    }

    // ===== 상점 '스테이지 깨기' 지도 화면 (mapOnly) =====
    private val backRect = RectF()
    private val storyRect = RectF()
    private val mapOnlyRect = RectF()
    private val btnFill = Paint().apply { color = Color.rgb(255, 236, 190); isAntiAlias = true }
    private val btnInk = Paint().apply { color = Color.rgb(60, 44, 30); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }

    /** 로비와 같은 원안 지도(돌마다 스테이지)를 화면 가득 + 왼쪽 위 '상점 마을로' · 오른쪽 위 '스토리 보기' */
    private fun drawMapOnly(canvas: Canvas, w: Float, h: Float) {
        titlePaint.textSize = h * 0.06f
        canvas.drawTr("스테이지 지도", w / 2f, h * 0.07f, titlePaint)
        mapOnlyRect.set(w * 0.04f, h * 0.10f, w * 0.96f, h * 0.98f)
        drawStageMap(canvas, mapOnlyRect, h)
        labelPaint.textSize = h * 0.032f
        backRect.set(w * 0.02f, h * 0.015f, w * 0.02f + 300f, h * 0.015f + 80f)
        storyRect.set(w * 0.98f - 300f, h * 0.015f, w * 0.98f, h * 0.015f + 80f)
        for ((r, t) in listOf(backRect to "◀ 상점 마을로", storyRect to "📖 스토리 보기")) {
            canvas.drawRoundRect(r, 40f, 40f, btnFill); canvas.drawRoundRect(r, 40f, 40f, btnInk)
            canvas.drawTr(t, r.centerX(), r.centerY() + h * 0.011f, labelPaint)
        }
        drawNotice(canvas, w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = gameView.screenWidth.toFloat()
        val h = gameView.screenHeight.toFloat()
        if (mapOnly && event.actionMasked == MotionEvent.ACTION_UP) {
            when {
                backRect.contains(event.x, event.y) -> gameView.showShop()
                storyRect.contains(event.x, event.y) -> gameView.replayStory()
                !prevPageRect.isEmpty && prevPageRect.contains(event.x, event.y) -> mapPage--
                !nextPageRect.isEmpty && nextPageRect.contains(event.x, event.y) -> mapPage++
                else -> stageAt(event.x, event.y)?.let { enterStage(it) }
            }
            return true
        }
        if (mapOnly) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pressedId = if (charRect.contains(event.x, event.y)) "char" else slotAt(event.x, event.y, w, h)?.id
            MotionEvent.ACTION_UP -> {
                // 지도 쪽 넘기기 화살표를 먼저, 그다음 돌 — 돌 하나가 스테이지 하나
                val stage = stageAt(event.x, event.y)
                if (!prevPageRect.isEmpty && prevPageRect.contains(event.x, event.y)) {
                    mapPage--
                } else if (!nextPageRect.isEmpty && nextPageRect.contains(event.x, event.y)) {
                    mapPage++
                } else if (stage != null) {
                    enterStage(stage)
                } else if (charRect.contains(event.x, event.y)) {
                    if (pressedId == "char") gameView.showCharacterSelect()
                } else {
                    val hit = slotAt(event.x, event.y, w, h)
                    if (hit != null && hit.id == pressedId) onSlotSelected(hit)
                }
                pressedId = null
            }
            MotionEvent.ACTION_CANCEL -> pressedId = null
        }
        return true
    }

    /** 로비에서 뒤로가기 → 타이틀 화면 (거기서 한 번 더 누르면 앱이 닫힌다) */
    override fun onBack(): Boolean {
        if (mapOnly) { gameView.showShop(); return true }
        gameView.showTitle()
        return true
    }

    private fun slotAt(x: Float, y: Float, w: Float, h: Float): Slot? =
        slots.firstOrNull { x >= it.l * w && x <= it.r * w && y >= it.t * h && y <= it.b * h }

    /** 눌린 자리에 놓인 돌(스테이지 번호)을 찾는다 */
    private fun stageAt(x: Float, y: Float): Int? {
        if (mapRect.isEmpty) return null
        val touchRadius = (mapRect.height() * 0.055f).coerceAtLeast(24f)
        var best: Int? = null
        var bestDist = touchRadius
        val onPage = (GameProgress.TOTAL_STAGES - mapPage * PAGE_SIZE).coerceAtMost(PAGE_SIZE)
        for (i in 0 until onPage) {
            val dist = hypot(x - stoneX(i), y - stoneY(i))
            if (dist <= bestDist) { bestDist = dist; best = mapPage * PAGE_SIZE + i + 1 }
        }
        return best
    }

    /** 스테이지 진입: 아직 만들지 않은 스테이지는 안내만 띄운다 */
    private fun enterStage(stage: Int) {
        // 못 가는 까닭(잠김·날아냥이/배냥이 무덤 등)은 GameView.stageBlockReason 에 모아 두었다 — 상점 '스테이지 깨기'와 같다
        val why = gameView.stageBlockReason(stage)
        if (why != null) {
            showNotice(why)
            if (why.length > 20) noticeTimer = 2.8f
        } else gameView.startStage(stage, fromShop = mapOnly)   // 하늘 스테이지는 들어가면 날아냥이를 그냥 준다 · 상점 지도에서 왔으면 끝나고 상점 마을로
    }

    private fun onSlotSelected(slot: Slot) {
        when (slot.id) {
            // 전투 버튼은 다음에 깰 스테이지로 곧장 출격 (전부 깼으면 안내만)
            "battle" -> {
                val next = GameProgress.nextBattleStage()
                if (next != null) enterStage(next)
                else showNotice("모든 스테이지를 깼다냥! 다음 지역은 준비 중")
            }
            // 고양이 기술: 루비로 배우고 켜고 끄는 기술들
            "cat_skill" -> gameView.showCatSkills()
            // 기술: 인간이 직접 쓰는 기술
            "skill" -> gameView.showHumanSkills()
            // 뽑기 (나오면 로비로)
            "gacha" -> gameView.showGacha(fromLobby = true)
            // 고양이 관리 = 내 팀 냥이를 돌보는 곳 (도감은 그 화면에서 따로 들어간다)
            "cat_manage" -> gameView.showTeam()
            // 쌓아온 점수를 루비로 바꾸는 곳
            "ruby_shop" -> gameView.showRubyShop()
            // 설정: 데이터 리셋
            "settings" -> gameView.showSettings()
            // 미션: 해내면 루비
            "mission" -> gameView.showMissions()
            // 상점: 초원 위 천막들, 구한 인간 수만큼 문이 열린다
            "shop" -> gameView.showShop()
            else -> showNotice("${slot.label}은(는) 준비 중이다냥!")
        }
    }

    private fun showNotice(text: String) {
        noticeText = text
        noticeTimer = 1.6f
    }

    init {
        // 배고파서 떠난 냥이가 있으면 로비에 들어올 때 한 번 알려 준다
        val left = CatTeam.takePendingLeaveNotices()
        if (left.isNotEmpty()) {
            showNotice(
                if (left.size == 1) "${left[0]}이(가) 배고파서 떠났다냥..."
                else "${left[0]} 외 ${left.size - 1}마리가 배고파서 떠났다냥..."
            )
            noticeTimer = 2.6f   // 떠난 소식은 조금 더 오래 띄운다
        }
        // 팀 냥이끼리 새끼를 낳았으면 누구랑 누구 사이인지 알려 준다 (대표님 2026-09-27)
        val born = CatTeam.takePendingBirthNotices()
        if (born.isNotEmpty()) {
            showNotice(if (born.size == 1) born[0] else "${born[0]} (외 ${born.size - 1}마리)")
            noticeTimer = 3.2f
        }
    }
}
