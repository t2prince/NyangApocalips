package com.jamcat.nayngapo.screen

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.GameView
import kotlin.math.hypot

class WorldMapScreen(private val gameView: GameView) : Screen {

    data class StageNode(
        val x: Float, val y: Float,
        val label: String,
        val stageNumber: Int,
        val unlocked: Boolean
    )

    private val nodes = mutableListOf<StageNode>()
    private var timer = 0f

    private val bgPaint = Paint().apply { color = Color.rgb(40, 35, 50) }
    private val pathPaint = Paint().apply {
        color = Color.rgb(120, 100, 80); strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    private val nodePaint = Paint().apply { color = Color.rgb(255, 200, 50); isAntiAlias = true }
    private val lockedPaint = Paint().apply { color = Color.rgb(80, 80, 80); isAntiAlias = true }
    private val nodeOutlinePaint = Paint().apply {
        color = Color.WHITE; style = Paint.Style.STROKE
        strokeWidth = 3f; isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE; textSize = 20f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val titlePaint = Paint().apply {
        color = Color.rgb(255, 200, 50); textSize = 40f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val descPaint = Paint().apply {
        color = Color.rgb(180, 180, 180); textSize = 16f; textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    init {
        setupNodes()
    }

    private fun setupNodes() {
        nodes.clear()
        // Layout based on plan2.jpg node map
        nodes.add(StageNode(0.2f, 0.5f, "Stage 1\n시작의 거리", 1, true))
        nodes.add(StageNode(0.5f, 0.35f, "Stage 2\n어둠의 골목", 2, true))
        nodes.add(StageNode(0.8f, 0.5f, "Final\n마법사의 탑", 3, true))
    }

    override fun update(deltaTime: Float) {
        timer += deltaTime
    }

    override fun draw(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Title
        canvas.drawText("WORLD MAP", w / 2f, 50f, titlePaint)

        // Draw paths between nodes
        for (i in 0 until nodes.size - 1) {
            val n1 = nodes[i]
            val n2 = nodes[i + 1]
            canvas.drawLine(n1.x * w, n1.y * h, n2.x * w, n2.y * h, pathPaint)
        }

        // Draw nodes
        for (node in nodes) {
            val nx = node.x * w
            val ny = node.y * h
            val radius = 40f
            val paint = if (node.unlocked) nodePaint else lockedPaint

            canvas.drawCircle(nx, ny, radius, paint)
            canvas.drawCircle(nx, ny, radius, nodeOutlinePaint)

            // Stage number
            val numPaint = Paint().apply {
                color = Color.BLACK; textSize = 28f; textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
            }
            canvas.drawText("${node.stageNumber}", nx, ny + 10f, numPaint)

            // Label below
            val lines = node.label.split("\n")
            for ((idx, line) in lines.withIndex()) {
                canvas.drawText(line, nx, ny + radius + 22f + idx * 20f, descPaint)
            }

            // Lock icon
            if (!node.unlocked) {
                val lockPaint = Paint().apply {
                    color = Color.rgb(150, 150, 150); textSize = 24f
                    textAlign = Paint.Align.CENTER; isAntiAlias = true
                }
                canvas.drawText("🔒", nx, ny + 8f, lockPaint)
            }
        }

        // Instruction
        val instrPaint = Paint().apply {
            color = Color.rgb(200, 200, 200); textSize = 22f; textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("스테이지를 선택하세요", w / 2f, h - 40f, instrPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val w = gameView.screenWidth.toFloat()
            val h = gameView.screenHeight.toFloat()
            val tx = event.x
            val ty = event.y

            for (node in nodes) {
                val nx = node.x * w
                val ny = node.y * h
                val dist = hypot((tx - nx).toDouble(), (ty - ny).toDouble()).toFloat()
                if (dist < 50f && node.unlocked) {
                    gameView.startStage(node.stageNumber)
                    return true
                }
            }
        }
        return true
    }
}
