package com.jamcat.nayngapo.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.jamcat.nayngapo.screen.*

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    var screenWidth = 0
        private set
    var screenHeight = 0
        private set

    private var gameThread: GameThread? = null
    private var currentScreen: Screen? = null

    init {
        holder.addCallback(this)
        isFocusable = true
        SpriteManager.init(context)
        SoundManager.init(context)
        GameProgress.init(context)
    }

    fun setScreen(screen: Screen) {
        currentScreen = screen
    }

    fun showTitle() {
        currentScreen = TitleScreen(this)
    }

    fun showWorldMap() {
        currentScreen = WorldMapScreen(this)
    }

    fun startStage(stageNumber: Int) {
        currentScreen = GamePlayScreen(this, stageNumber)
    }

    fun showGameOver(score: Int) {
        currentScreen = GameOverScreen(this, score)
    }

    fun showStageClear(
        stageNumber: Int, score: Int, rescueCount: Int,
        defeatedEnemies: Int, totalEnemies: Int, playerLevel: Int
    ) {
        currentScreen = StageClearScreen(
            this, stageNumber, score, rescueCount, defeatedEnemies, totalEnemies, playerLevel
        )
    }

    fun updateGame(deltaTime: Float) {
        currentScreen?.update(deltaTime)
    }

    fun drawGame(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        currentScreen?.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return currentScreen?.onTouchEvent(event) ?: super.onTouchEvent(event)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width
        screenHeight = height
        showTitle()

        gameThread = GameThread(holder, this)
        gameThread?.running = true
        gameThread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        gameThread?.running = false
        var retry = true
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (_: InterruptedException) {}
        }
    }

    fun pause() {
        gameThread?.running = false
        try {
            gameThread?.join()
        } catch (_: InterruptedException) {}
    }

    fun resume() {
        if (holder.surface.isValid) {
            gameThread = GameThread(holder, this)
            gameThread?.running = true
            gameThread?.start()
        }
    }
}
