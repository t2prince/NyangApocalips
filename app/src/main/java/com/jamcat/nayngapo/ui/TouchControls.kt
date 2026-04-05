package com.jamcat.nayngapo.ui

import android.graphics.*
import android.view.MotionEvent
import com.jamcat.nayngapo.engine.InputState

class TouchControls {
    val inputState = InputState()

    // D-pad config
    private val dpadRadius = 90f
    private val dpadInnerRadius = 30f
    private var dpadCenterX = 0f
    private var dpadCenterY = 0f

    // Action buttons config
    private val btnRadius = 45f
    private var jumpBtnX = 0f
    private var jumpBtnY = 0f
    private var attackBtnX = 0f
    private var attackBtnY = 0f
    private var specialBtnX = 0f
    private var specialBtnY = 0f

    // Track active pointers
    private val pointerButtons = mutableMapOf<Int, String>()

    private val dpadBgPaint = Paint().apply {
        color = Color.argb(60, 255, 255, 255); isAntiAlias = true
    }
    private val dpadArrowPaint = Paint().apply {
        color = Color.argb(120, 255, 255, 255); isAntiAlias = true; style = Paint.Style.FILL
    }
    private val btnPaint = Paint().apply {
        color = Color.argb(70, 255, 255, 255); isAntiAlias = true
    }
    private val btnActivePaint = Paint().apply {
        color = Color.argb(130, 255, 200, 50); isAntiAlias = true
    }
    private val btnTextPaint = Paint().apply {
        color = Color.WHITE; textSize = 22f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
    }
    private val dpadCrossPaint = Paint().apply {
        color = Color.argb(40, 255, 255, 255); isAntiAlias = true
    }

    fun updateLayout(screenWidth: Int, screenHeight: Int) {
        val padX = 160f
        val padY = screenHeight - 160f
        dpadCenterX = padX
        dpadCenterY = padY

        val rightBase = screenWidth - 140f
        val bottomBase = screenHeight - 130f
        attackBtnX = rightBase
        attackBtnY = bottomBase
        jumpBtnX = rightBase - 110f
        jumpBtnY = bottomBase - 80f
        specialBtnX = rightBase - 10f
        specialBtnY = bottomBase - 160f
    }

    fun onTouchEvent(event: MotionEvent, screenWidth: Int, screenHeight: Int): Boolean {
        if (dpadCenterX == 0f) updateLayout(screenWidth, screenHeight)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val px = event.getX(idx)
                val py = event.getY(idx)
                val id = event.getPointerId(idx)
                handlePress(id, px, py)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val px = event.getX(i)
                    val py = event.getY(i)
                    handlePress(id, px, py)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                handleRelease(id)
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerButtons.clear()
                resetInput()
            }
        }
        return true
    }

    private fun handlePress(pointerId: Int, px: Float, py: Float) {
        val prevButton = pointerButtons[pointerId]

        // Check D-pad
        val dx = px - dpadCenterX
        val dy = py - dpadCenterY
        val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (dist < dpadRadius + 20f) {
            // Inside D-pad area
            pointerButtons[pointerId] = "dpad"
            // Reset dpad state first
            inputState.moveLeft = false
            inputState.moveRight = false
            inputState.moveUp = false
            inputState.moveDown = false

            if (dist > dpadInnerRadius) {
                val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                // Right: -45 to 45, Down: 45 to 135, Left: 135 to 180 or -180 to -135, Up: -135 to -45
                if (angle > -60f && angle < 60f) inputState.moveRight = true
                if (angle > 120f || angle < -120f) inputState.moveLeft = true
                if (angle > 30f && angle < 150f) inputState.moveDown = true
                if (angle > -150f && angle < -30f) inputState.moveUp = true
            }
            return
        }

        // Check action buttons
        if (kotlin.math.hypot((px - attackBtnX).toDouble(), (py - attackBtnY).toDouble()) < btnRadius + 10f) {
            pointerButtons[pointerId] = "attack"
            inputState.attack = true
            return
        }
        if (kotlin.math.hypot((px - jumpBtnX).toDouble(), (py - jumpBtnY).toDouble()) < btnRadius + 10f) {
            pointerButtons[pointerId] = "jump"
            inputState.jump = true
            return
        }
        if (kotlin.math.hypot((px - specialBtnX).toDouble(), (py - specialBtnY).toDouble()) < btnRadius + 10f) {
            pointerButtons[pointerId] = "special"
            inputState.special = true
            return
        }

        // Not in any button - if previously was on a button, release it
        if (prevButton != null) {
            releaseButton(prevButton)
            pointerButtons.remove(pointerId)
        }
    }

    private fun handleRelease(pointerId: Int) {
        val button = pointerButtons[pointerId]
        if (button != null) {
            releaseButton(button)
            pointerButtons.remove(pointerId)
        }
    }

    private fun releaseButton(button: String) {
        when (button) {
            "dpad" -> {
                inputState.moveLeft = false
                inputState.moveRight = false
                inputState.moveUp = false
                inputState.moveDown = false
            }
            "attack" -> inputState.attack = false
            "jump" -> inputState.jump = false
            "special" -> inputState.special = false
        }
    }

    private fun resetInput() {
        inputState.moveLeft = false
        inputState.moveRight = false
        inputState.moveUp = false
        inputState.moveDown = false
        inputState.jump = false
        inputState.attack = false
        inputState.special = false
    }

    fun draw(canvas: Canvas, screenWidth: Int, screenHeight: Int) {
        if (dpadCenterX == 0f) updateLayout(screenWidth, screenHeight)

        // === D-pad ===
        // Background circle
        canvas.drawCircle(dpadCenterX, dpadCenterY, dpadRadius, dpadBgPaint)
        // Cross shape
        canvas.drawRect(
            dpadCenterX - 25f, dpadCenterY - dpadRadius + 10f,
            dpadCenterX + 25f, dpadCenterY + dpadRadius - 10f, dpadCrossPaint
        )
        canvas.drawRect(
            dpadCenterX - dpadRadius + 10f, dpadCenterY - 25f,
            dpadCenterX + dpadRadius - 10f, dpadCenterY + 25f, dpadCrossPaint
        )

        // Arrows
        drawArrow(canvas, dpadCenterX, dpadCenterY - 55f, 0f, inputState.moveUp)   // Up
        drawArrow(canvas, dpadCenterX, dpadCenterY + 55f, 180f, inputState.moveDown)  // Down
        drawArrow(canvas, dpadCenterX - 55f, dpadCenterY, 270f, inputState.moveLeft)  // Left
        drawArrow(canvas, dpadCenterX + 55f, dpadCenterY, 90f, inputState.moveRight)  // Right

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
            moveTo(cx, cy - 14f)
            lineTo(cx - 12f, cy + 6f)
            lineTo(cx + 12f, cy + 6f)
            close()
        }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawButton(canvas: Canvas, cx: Float, cy: Float, label: String, active: Boolean, color: Int) {
        val paint = if (active) btnActivePaint else Paint().apply {
            this.color = color; isAntiAlias = true
        }
        canvas.drawCircle(cx, cy, btnRadius, paint)
        // Border
        val borderPaint = Paint().apply {
            this.color = Color.argb(100, 255, 255, 255)
            style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true
        }
        canvas.drawCircle(cx, cy, btnRadius, borderPaint)
        canvas.drawText(label, cx, cy + 8f, btnTextPaint)
    }
}
