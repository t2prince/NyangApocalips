package com.jamcat.nayngapo.engine

import android.graphics.Canvas
import android.view.SurfaceHolder

class GameThread(
    private val surfaceHolder: SurfaceHolder,
    private val gameView: GameView
) : Thread() {

    var running = false
    private var perfFrames = 0; private var perfUpdate = 0L; private var perfDraw = 0L; private var perfLock = 0L
    private var perfTotal = 0L; private var perfMax = 0L; private var perfSince = System.nanoTime()
    private val targetFPS = 60
    private val targetTime = 1000L / targetFPS

    override fun run() {
        var lastTime = System.nanoTime()

        while (running) {
            val now = System.nanoTime()
            val deltaTime = ((now - lastTime) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
            lastTime = now

            var canvas: Canvas? = null
            var tUpdate = 0L; var tDraw = 0L; var tLock = 0L
            try {
                // GPU 캔버스 — 2400x1080 을 CPU 로 그리면 한 프레임에 70ms 가 넘어 게임이 느려진다 (2026-09-20 최적화).
                // 하드웨어 캔버스를 못 얻는 경우만 예전처럼 소프트웨어로.
                val t0 = System.nanoTime()
                canvas = try { surfaceHolder.lockHardwareCanvas() } catch (_: Exception) { null } ?: surfaceHolder.lockCanvas()
                tLock = System.nanoTime() - t0
                if (canvas != null) {
                    synchronized(surfaceHolder) {
                        val t1 = System.nanoTime()
                        gameView.updateGame(deltaTime)
                        val t2 = System.nanoTime()
                        gameView.drawGame(canvas)
                        tUpdate = t2 - t1; tDraw = System.nanoTime() - t2
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
            // 성능 측정 (2026-09-20 「너무 느려」): 2초마다 평균/최대 프레임 시간을 logcat 에 남긴다 — adb logcat -s PERF
            perfFrames++; perfUpdate += tUpdate; perfDraw += tDraw; perfLock += tLock; perfTotal += elapsed
            if (elapsed > perfMax) perfMax = elapsed
            if (System.nanoTime() - perfSince > 2_000_000_000L) {
                android.util.Log.i("PERF", "frames=%d avg=%.1fms max=%dms update=%.1fms draw=%.1fms lock=%.1fms screen=%s".format(
                    perfFrames, perfTotal.toFloat() / perfFrames, perfMax,
                    perfUpdate / 1e6f / perfFrames, perfDraw / 1e6f / perfFrames, perfLock / 1e6f / perfFrames,
                    gameView.currentScreenName()))
                perfFrames = 0; perfUpdate = 0L; perfDraw = 0L; perfLock = 0L; perfTotal = 0L; perfMax = 0L
                perfSince = System.nanoTime()
            }
            val sleepTime = targetTime - elapsed
            if (sleepTime > 0) {
                try {
                    sleep(sleepTime)
                } catch (_: InterruptedException) {}
            }
        }
    }
}
