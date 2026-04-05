package com.jamcat.nayngapo.engine

class Camera {
    var x = 0f
    var y = 0f

    fun update(
        targetX: Float, targetY: Float,
        levelWidth: Float, levelHeight: Float,
        viewWidth: Float, viewHeight: Float
    ) {
        // Smooth follow
        val desiredX = targetX - viewWidth / 2f
        val desiredY = targetY - viewHeight / 2f
        x += (desiredX - x) * 0.1f
        y += (desiredY - y) * 0.1f

        // Clamp to level bounds
        if (x < 0f) x = 0f
        if (y < 0f) y = 0f
        if (x + viewWidth > levelWidth) x = levelWidth - viewWidth
        if (y + viewHeight > levelHeight) y = levelHeight - viewHeight
    }
}
