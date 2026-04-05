package com.jamcat.nayngapo.entity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class Projectile(
    var x: Float,
    var y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val damage: Int,
    val isPlayerOwned: Boolean
) {
    val width = 12f
    val height = 12f
    var isAlive = true

    val hitBox: RectF get() = RectF(x, y, x + width, y + height)

    private val paint = Paint().apply {
        color = if (isPlayerOwned) Color.CYAN else Color.RED
    }

    fun update(deltaTime: Float) {
        x += velocityX * deltaTime
        y += velocityY * deltaTime

        if (x < -500f || x > 20000f || y < -500f || y > 2000f) {
            isAlive = false
        }
    }

    fun draw(canvas: Canvas) {
        if (isAlive) {
            canvas.drawCircle(x + width / 2f, y + height / 2f, width / 2f, paint)
        }
    }
}
