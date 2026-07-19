package com.jamcat.nayngapo.engine

class Camera {
    var x = 0f
    var y = 0f

    /** 플랫포머식 한 방향 진행: true면 카메라가 오른쪽으로만 이동하고 뒤로 돌아가지 않는다. */
    var forwardOnly = false
    private var ratchetX = 0f

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

        // 한 방향 진행: 지나온 구간으로는 스크롤이 되돌아가지 않는다
        if (forwardOnly) {
            if (x < ratchetX) x = ratchetX else ratchetX = x
        }
    }
}
