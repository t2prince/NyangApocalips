package com.jamcat.nayngapo.ui

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.InputState

class TouchControls {
    val inputState = InputState()

    // D-pad config (larger for easier touch)
    private val dpadRadius = 195f
    private val dpadInnerRadius = 50f
    private var dpadCenterX = 0f
    private var dpadCenterY = 0f

    // Action buttons config (larger)
    private val btnRadius = 90f
    private var jumpBtnX = 0f
    private var jumpBtnY = 0f
    private var attackBtnX = 0f
    private var attackBtnY = 0f
    private var specialBtnX = 0f
    private var specialBtnY = 0f

    private val pointerButtons = mutableMapOf<Int, String>()

    private val dpadBgPaint = Paint().apply {
        color = Color.argb(50, 255, 255, 255); isAntiAlias = true
    }
    private val dpadArrowPaint = Paint().apply {
        color = Color.argb(120, 255, 255, 255); isAntiAlias = true; style = Paint.Style.FILL
    }
    private val btnActivePaint = Paint().apply {
        color = Color.argb(140, 255, 200, 50); isAntiAlias = true
    }
    private val btnTextPaint = Paint().apply {
        color = Color.WHITE; textSize = 34f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val dpadCrossPaint = Paint().apply {
        color = Color.argb(35, 255, 255, 255); isAntiAlias = true
    }

    fun updateLayout(screenWidth: Int, screenHeight: Int) {
        dpadCenterX = 250f
        dpadCenterY = screenHeight - 240f

        val rightBase = screenWidth - 200f
        val bottomBase = screenHeight - 180f
        attackBtnX = rightBase
        attackBtnY = bottomBase
        jumpBtnX = rightBase - 190f
        jumpBtnY = bottomBase - 70f
        specialBtnX = rightBase - 40f
        specialBtnY = bottomBase - 200f
    }

    fun onTouchEvent(event: MotionEvent, screenWidth: Int, screenHeight: Int): Boolean {
        if (dpadCenterX == 0f) updateLayout(screenWidth, screenHeight)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                handlePress(event.getPointerId(idx), event.getX(idx), event.getY(idx))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    handlePress(event.getPointerId(i), event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                handleRelease(event.getPointerId(event.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerButtons.clear(); resetInput()
            }
        }
        return true
    }

    private fun handlePress(pointerId: Int, px: Float, py: Float) {
        val prevButton = pointerButtons[pointerId]

        val dx = px - dpadCenterX
        val dy = py - dpadCenterY
        val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (dist < dpadRadius + 30f) {
            pointerButtons[pointerId] = "dpad"
            inputState.moveLeft = false; inputState.moveRight = false
            inputState.moveUp = false; inputState.moveDown = false

            if (dist > dpadInnerRadius) {
                val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (angle > -60f && angle < 60f) inputState.moveRight = true
                if (angle > 120f || angle < -120f) inputState.moveLeft = true
                if (angle > 30f && angle < 150f) inputState.moveDown = true
                if (angle > -150f && angle < -30f) inputState.moveUp = true
            }
            return
        }

        val hitRadius = btnRadius + 15f
        if (kotlin.math.hypot((px - attackBtnX).toDouble(), (py - attackBtnY).toDouble()) < hitRadius) {
            pointerButtons[pointerId] = "attack"; inputState.attack = true; return
        }
        if (kotlin.math.hypot((px - jumpBtnX).toDouble(), (py - jumpBtnY).toDouble()) < hitRadius) {
            pointerButtons[pointerId] = "jump"; inputState.jump = true; return
        }
        if (kotlin.math.hypot((px - specialBtnX).toDouble(), (py - specialBtnY).toDouble()) < hitRadius) {
            pointerButtons[pointerId] = "special"; inputState.special = true; return
        }

        if (prevButton != null) { releaseButton(prevButton); pointerButtons.remove(pointerId) }
    }

    private fun handleRelease(pointerId: Int) {
        pointerButtons[pointerId]?.let { releaseButton(it); pointerButtons.remove(pointerId) }
    }

    private fun releaseButton(button: String) {
        when (button) {
            "dpad" -> { inputState.moveLeft = false; inputState.moveRight = false; inputState.moveUp = false; inputState.moveDown = false }
            "attack" -> inputState.attack = false
            "jump" -> inputState.jump = false
            "special" -> inputState.special = false
        }
    }

    private fun resetInput() {
        inputState.moveLeft = false; inputState.moveRight = false
        inputState.moveUp = false; inputState.moveDown = false
        inputState.jump = false; inputState.attack = false; inputState.special = false
    }

    fun draw(canvas: Canvas, screenWidth: Int, screenHeight: Int) {
        if (dpadCenterX == 0f) updateLayout(screenWidth, screenHeight)

        // === D-pad ===
        canvas.drawCircle(dpadCenterX, dpadCenterY, dpadRadius, dpadBgPaint)
        // Cross
        val crossW = 35f
        canvas.drawRect(dpadCenterX - crossW, dpadCenterY - dpadRadius + 15f,
            dpadCenterX + crossW, dpadCenterY + dpadRadius - 15f, dpadCrossPaint)
        canvas.drawRect(dpadCenterX - dpadRadius + 15f, dpadCenterY - crossW,
            dpadCenterX + dpadRadius - 15f, dpadCenterY + crossW, dpadCrossPaint)

        // Arrows (bigger)
        drawArrow(canvas, dpadCenterX, dpadCenterY - 110f, 0f, inputState.moveUp)
        drawArrow(canvas, dpadCenterX, dpadCenterY + 110f, 180f, inputState.moveDown)
        drawArrow(canvas, dpadCenterX - 110f, dpadCenterY, 270f, inputState.moveLeft)
        drawArrow(canvas, dpadCenterX + 110f, dpadCenterY, 90f, inputState.moveRight)

        // === Action Buttons ===
        drawButton(canvas, attackBtnX, attackBtnY, "ATK", inputState.attack, Color.argb(80, 255, 80, 80))
        drawButton(canvas, jumpBtnX, jumpBtnY, "JMP", inputState.jump, Color.argb(80, 80, 180, 255))
        drawButton(canvas, specialBtnX, specialBtnY, "SP", inputState.special, Color.argb(80, 255, 200, 50))
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, rotation: Float, active: Boolean) {
        val paint = if (active) btnActivePaint else dpadArrowPaint
        canvas.save()
        canvas.rotate(rotation, cx, cy)
        val path = Path().apply {
            moveTo(cx, cy - 28f)
            lineTo(cx - 22f, cy + 12f)
            lineTo(cx + 22f, cy + 12f)
            close()
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawButton(canvas: Canvas, cx: Float, cy: Float, label: String, active: Boolean, color: Int) {
        val paint = if (active) btnActivePaint else Paint().apply { this.color = color; isAntiAlias = true }
        canvas.drawCircle(cx, cy, btnRadius, paint)
        val borderPaint = Paint().apply {
            this.color = Color.argb(100, 255, 255, 255)
            style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true
        }
        canvas.drawCircle(cx, cy, btnRadius, borderPaint)
        canvas.drawText(label, cx, cy + 9f, btnTextPaint)
    }
}
