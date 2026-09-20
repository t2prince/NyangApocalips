package com.jamcat.nayngapo.engine

import android.graphics.Canvas
import android.view.SurfaceHolder

class GameThread(
    private val surfaceHolder: SurfaceHolder,
    private val gameView: GameView
) : Thread() {

    var running = false
    private val targetFPS = 60
    private val targetTime = 1000L / targetFPS

    override fun run() {
        var lastTime = System.nanoTime()

        while (running) {
            val now = System.nanoTime()
            val deltaTime = ((now - lastTime) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
            lastTime = now

            var canvas: Canvas? = null
            try {
                // GPU 캔버스 — 2400x1080 을 CPU 로 그리면 한 프레임에 70ms 가 넘어 게임이 느려진다 (2026-09-20 최적화).
                // 하드웨어 캔버스를 못 얻는 경우만 예전처럼 소프트웨어로.
                canvas = try { surfaceHolder.lockHardwareCanvas() } catch (_: Exception) { null } ?: surfaceHolder.lockCanvas()
                if (canvas != null) {
                    synchronized(surfaceHolder) {
                        gameView.updateGame(deltaTime)
                        gameView.drawGame(canvas)
                    }
                }
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {}
                }
            }

            val elapsed = (System.nanoTime() - now) / 1_000_000
            val sleepTime = targetTime - elapsed
            if (sleepTime > 0) {
                try {
                    sleep(sleepTime)
                } catch (_: InterruptedException) {}
            }
        }
    }
}
