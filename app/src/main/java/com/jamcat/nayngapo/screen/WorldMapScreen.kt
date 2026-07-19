package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameProgress
import com.jamcat.nayngapo.engine.GameView
import kotlin.math.hypot

/**
 * plan2 기획서 스타일 월드맵: 구불구불한 길을 따라 스테이지 노드가 이어진다.
 * 이전 스테이지를 클리어해야 다음 스테이지가 열린다.
 */
class WorldMapScreen(private val gameView: GameView) : Screen {

    private data class StageNode(
        val x: Float, val y: Float,   // 화면 비율 좌표 (0~1)
        val label: String,
        val stageNumber: Int
    )

    // 좌하단 마을에서 출발해 S자로 굽이치며 우상단 마법사의 탑까지
    private val nodes = listOf(
        StageNode(0.10f, 0.78f, "시작의 거리", 1),
        StageNode(0.24f, 0.62f, "골목길", 2),
        StageNode(0.20f, 0.38f, "공원", 3),
        StageNode(0.38f, 0.28f, "시장", 4),
        StageNode(0.52f, 0.45f, "지하철역", 5),
        StageNode(0.62f, 0.68f, "어둠의 골목", 6),
        StageNode(0.78f, 0.55f, "폐허", 7),
        StageNode(0.86f, 0.28f, "마법사의 탑", 8),
    )

    private var timer = 0f

    private val bgPaint = Paint().apply { color = Color.rgb(40, 35, 50) }
    private val roadPaint = Paint().apply {
        color = Color.rgb(120, 100, 80); strokeWidth = 26f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val roadEdgePaint = Paint().apply {
        color = Color.rgb(90, 75, 60); strokeWidth = 32f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val roadDotPaint = Paint().apply {
        color = Color.rgb(220, 205, 180); isAntiAlias = true
    }
    private val nodePaint = Paint().apply { color = Color.rgb(255, 200, 50); isAntiAlias = true }
    private val clearedPaint = Paint().apply { color = Color.rgb(130, 220, 130); isAntiAlias = true }
    private val lockedPaint = Paint().apply { color = Color.rgb(80, 80, 80); isAntiAlias = true }
    private val nodeOutlinePaint = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE
        strokeWidth = 3f; isAntiAlias = true
    }
    private val numPaint = Paint().apply {
        color = Color.BLACK; textSize = 26f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val lockTextPaint = Paint().apply {
        color = Color.rgb(160, 160, 160); textSize = 22f
        textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    private val titlePaint = Paint().apply {
        color = Color.rgb(255, 200, 50); textSize = 40f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val descPaint = Paint().apply {
        color = Color.rgb(200, 200, 200); textSize = 16f; textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val markerPaint = Paint().apply {
        color = Color.WHITE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    // 매 프레임 재사용하는 페인트들
    private val ringPaint = Paint().apply {
        color = Color.rgb(255, 230, 120); style = Paint.Style.STROKE
        strokeWidth = 4f; isAntiAlias = true
    }
    private val instrPaint = Paint().apply {
        color = Color.rgb(200, 200, 200); textSize = 22f; textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val houseBodyPaint = Paint().apply { color = Color.rgb(200, 180, 150); isAntiAlias = true }
    private val houseRoofPaint = Paint().apply { color = Color.rgb(170, 90, 70); isAntiAlias = true }
    private val towerBodyPaint = Paint().apply { color = Color.rgb(110, 90, 140); isAntiAlias = true }
    private val towerRoofPaint = Paint().apply { color = Color.rgb(80, 60, 110); isAntiAlias = true }
    private val flagPaint = Paint().apply { color = Color.rgb(255, 90, 90); isAntiAlias = true }

    override fun update(deltaTime: Float) {
        timer += deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)
        canvas.drawText("냥이 이동경로", w / 2f, 52f, titlePaint)

        // 구불구불한 길: 노드 사이를 곡선(quadTo)으로 연결
        val road = Path()
        road.moveTo(nodes[0].x * w, nodes[0].y * h)
        for (i in 0 until nodes.size - 1) {
            val n1 = nodes[i]; val n2 = nodes[i + 1]
            val midX = (n1.x + n2.x) / 2f * w
            val midY = (n1.y + n2.y) / 2f * h
            // 중간점을 살짝 옆으로 밀어 자연스러운 굽이 표현
            val bendX = midX + (if (i % 2 == 0) 1f else -1f) * w * 0.035f
            val bendY = midY + (if (i % 2 == 0) -1f else 1f) * h * 0.03f
            road.quadTo(bendX, bendY, n2.x * w, n2.y * h)
        }
        canvas.drawPath(road, roadEdgePaint)
        canvas.drawPath(road, roadPaint)

        // 길 위의 점(자갈) 장식: 곡선을 따라 일정 간격으로 찍는다
        val measure = PathMeasure(road, false)
        val pos = FloatArray(2)
        var dist = 30f
        while (dist < measure.length) {
            measure.getPosTan(dist, pos, null)
            canvas.drawCircle(pos[0], pos[1], 3.5f, roadDotPaint)
            dist += 46f
        }

        // 시작 마을 아이콘(집)과 최종 목적지(탑) 표시
        drawHouse(canvas, nodes.first().x * w - 55f, nodes.first().y * h - 40f)
        drawTower(canvas, nodes.last().x * w + 45f, nodes.last().y * h - 30f)

        // 스테이지 노드
        val unlocked = GameProgress.unlockedStage
        for (node in nodes) {
            val nx = node.x * w
            val ny = node.y * h
            val radius = 34f
            val paint = when {
                node.stageNumber < unlocked -> clearedPaint            // 클리어함
                node.stageNumber == unlocked -> nodePaint              // 도전 가능(현재)
                else -> lockedPaint                                    // 잠김
            }
            canvas.drawCircle(nx, ny, radius, paint)
            canvas.drawCircle(nx, ny, radius, nodeOutlinePaint)

            if (node.stageNumber <= unlocked) {
                canvas.drawText("${node.stageNumber}", nx, ny + 9f, numPaint)
            } else {
                canvas.drawText("🔒", nx, ny + 8f, lockTextPaint)
            }

            // 현재 도전 스테이지는 깜빡이는 링으로 강조
            if (node.stageNumber == unlocked) {
                val pulse = (kotlin.math.sin(timer * 4.0) * 0.5 + 0.5).toFloat()
                ringPaint.alpha = (100 + 155 * pulse).toInt()
                canvas.drawCircle(nx, ny, radius + 8f + pulse * 4f, ringPaint)
            }

            canvas.drawText(node.label, nx, ny + radius + 24f, descPaint)
        }

        canvas.drawText("스테이지를 선택하세요", w / 2f, h - 30f, instrPaint)
    }

    /** 시작 마을: 간단한 집 모양 */
    private fun drawHouse(canvas: Canvas, x: Float, y: Float) {
        canvas.drawRect(x - 16f, y, x + 16f, y + 22f, houseBodyPaint)
        val roof = Path().apply {
            moveTo(x - 20f, y); lineTo(x, y - 16f); lineTo(x + 20f, y); close()
        }
        canvas.drawPath(roof, houseRoofPaint)
        canvas.drawText("마을", x, y + 40f, descPaint)
    }

    /** 최종 목적지: 마법사의 탑 */
    private fun drawTower(canvas: Canvas, x: Float, y: Float) {
        canvas.drawRect(x - 10f, y - 10f, x + 10f, y + 30f, towerBodyPaint)
        val roof = Path().apply {
            moveTo(x - 14f, y - 10f); lineTo(x, y - 32f); lineTo(x + 14f, y - 10f); close()
        }
        canvas.drawPath(roof, towerRoofPaint)
        // 깃발
        canvas.drawLine(x, y - 32f, x, y - 44f, markerPaint)
        canvas.drawRect(x, y - 44f, x + 12f, y - 37f, flagPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val w = gameView.screenWidth.toFloat()
            val h = gameView.screenHeight.toFloat()

            for (node in nodes) {
                val nx = node.x * w
                val ny = node.y * h
                val dist = hypot((event.x - nx).toDouble(), (event.y - ny).toDouble()).toFloat()
                if (dist < 48f && node.stageNumber <= GameProgress.unlockedStage) {
                    gameView.startStage(node.stageNumber)
                    return true
                }
            }
        }
        return true
    }
}
