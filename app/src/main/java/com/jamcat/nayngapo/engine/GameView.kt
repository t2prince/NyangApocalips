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

    // 화면 전환 페이드: 검은 화면으로 덮었다가(fade out) 새 화면에서 걷어낸다(fade in)
    private var fadeAlpha = 0f        // 0 = 투명, 1 = 완전 검정
    private var fadeDir = 0           // +1 = 어두워지는 중, -1 = 밝아지는 중, 0 = 전환 없음
    private var pendingScreen: (() -> Screen)? = null
    private val fadePaint = android.graphics.Paint().apply { color = Color.BLACK }

    val isTransitioning: Boolean get() = fadeDir != 0

    /** 페이드 아웃 → 화면 교체 → 페이드 인 순서로 부드럽게 전환한다 */
    private fun fadeTo(factory: () -> Screen) {
        if (fadeDir != 0) return      // 이미 전환 중이면 무시 (중복 진입 방지)
        pendingScreen = factory
        fadeDir = 1
        fadeAlpha = 0f
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        SpriteManager.init(context)
        SoundManager.init(context)
        SoundManager.startBgm(context)   // 밝은 배경음악을 계속 반복
        GameProgress.init(context)
    }

    fun setScreen(screen: Screen) {
        currentScreen = screen
    }

    /**
     * 글자를 직접 받아야 할 때 (냥이 이름 짓기) 소프트 키보드를 띄운다.
     * 게임은 SurfaceView라 입력창이 없으므로 UI 스레드에서 AlertDialog를 세운다.
     * onDone은 UI 스레드에서 불리며, 취소하거나 빈 글자면 null이 온다.
     */
    fun promptText(title: String, initial: String, maxLen: Int = 8, onDone: (String?) -> Unit) {
        post {
            val activity = context as? android.app.Activity
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                onDone(null)
                return@post
            }
            val input = android.widget.EditText(activity).apply {
                setSingleLine(true)
                filters = arrayOf(android.text.InputFilter.LengthFilter(maxLen))
                setText(initial)
                setSelection(0, text.length)
            }
            // 확인/취소/바깥 누르기가 겹쳐도 결과는 한 번만 전달한다
            var delivered = false
            fun deliver(value: String?) {
                if (delivered) return
                delivered = true
                onDone(value)
            }
            android.app.AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("확인") { _, _ ->
                    deliver(input.text.toString().trim().ifEmpty { null })
                }
                .setNegativeButton("취소") { _, _ -> deliver(null) }
                .setOnCancelListener { deliver(null) }
                .show()
        }
    }

    fun showTitle() {
        currentScreen = TitleScreen(this)
        SoundManager.resumeBgm()   // 홈으로 돌아오면 다시 노래가 흐른다
    }

    /** 로비(홈): 전투·상점·설정 등 모든 기능으로 갈라지는 중심 화면 */
    fun showLobby() {
        // 스테이지에서 '나가기'로 돌아올 때도 스테이지 진입과 같은 페이드로 넘어간다.
        // (즉시 교체하면 그리는 중인 게임 스레드와 겹쳐 한 프레임이 깨져 보인다)
        fadeTo {
            SoundManager.resumeBgm()
            LobbyScreen(this)
        }
    }

    /**
     * 기기의 뒤로가기. 현재 화면이 처리하면 true.
     * false면 MainActivity가 앱을 종료한다.
     */
    fun handleBack(): Boolean {
        synchronized(holder) {   // 뒤로가기도 UI 스레드 — 게임 루프와 같은 잠금
            if (fadeDir != 0) return true     // 전환 중에는 삼켜서 중복 진입을 막는다
            return currentScreen?.onBack() ?: false
        }
    }

    /** 스테이지 진입: 페이드 아웃/인으로 바로 시작한다 (11~20은 날개냥이를 타는 하늘 스테이지) */
    fun startStage(stageNumber: Int) {
        fadeTo {
            if (GameProgress.isSkyStage(stageNumber)) SkyStageScreen(this, stageNumber)
            else GamePlayScreen(this, stageNumber)
        }
    }

    /** 고양이 기술: 루비로 기술을 배우고 켜고 끈다 */
    fun showCatSkills() {
        fadeTo { CatSkillScreen(this) }
    }

    /** 기술: 인간이 직접 쓰는 기술을 루비로 배운다 */
    fun showHumanSkills() {
        fadeTo { HumanSkillScreen(this) }
    }

    /** 캐릭터 변경: 네 명의 인간 중 하나를 고른다 */
    fun showCharacterSelect() {
        fadeTo { CharacterSelectScreen(this) }
    }

    /** 고양이 관리: 내 팀 냥이를 돌보는 곳 (도감은 여기서 따로 들어간다) */
    fun showTeam() {
        fadeTo { TeamScreen(this) }
    }

    /** 고양이 도감 (기획1) */
    fun showDex() {
        fadeTo { DexScreen(this) }
    }

    /** 고양이 상점: 루비로 냥이 물건을 산다 */
    fun showCatShop() {
        fadeTo { CatShopScreen(this) }
    }

    /** 루비 상점: 쌓아온 점수를 루비로 바꾼다 */
    fun showRubyShop() {
        fadeTo { RubyShopScreen(this) }
    }

    /** 미션: 해내면 루비를 받는다 */
    fun showMissions() {
        fadeTo { com.jamcat.nayngapo.screen.MissionScreen(this) }
    }

    /** 설정: 데이터 리셋 등 */
    fun showSettings() {
        fadeTo { SettingsScreen(this) }
    }

    /** 저장 데이터를 전부 지우고 처음 상태로 (설정 화면의 '데이터 리셋') */
    fun resetAllData() {
        GameProgress.resetAll(context)
    }

    fun showGameOver(score: Int) {
        GameProgress.addScore(score)   // 이번 판에서 번 점수를 저금통에 넣는다
        currentScreen = GameOverScreen(this, score)
        SoundManager.pauseBgm()      // 게임 오버 동안은 노래를 멈추고
        SoundManager.playGameOver()  // '으어어어...' 비명만 들린다
    }

    /** 스테이지 클리어: 화면이 서서히 어두워졌다가 결과 화면으로 넘어간다 */
    fun showStageClear(
        stageNumber: Int, score: Int, rescueCount: Int,
        defeatedEnemies: Int, totalEnemies: Int, playerLevel: Int
    ) {
        GameProgress.addScore(score)   // 이번 판에서 번 점수를 저금통에 넣는다
        fadeTo {
            StageClearScreen(
                this, stageNumber, score, rescueCount, defeatedEnemies, totalEnemies, playerLevel
            )
        }
    }

    fun updateGame(deltaTime: Float) {
        if (fadeDir != 0) {
            fadeAlpha += fadeDir * deltaTime / Constants.FADE_DURATION
            if (fadeDir > 0) {
                if (fadeAlpha >= 1f) {
                    // 완전히 어두워진 순간에 화면 교체 → 전환이 보이지 않는다
                    fadeAlpha = 1f
                    pendingScreen?.let { currentScreen = it() }
                    pendingScreen = null
                    fadeDir = -1
                }
                return   // 페이드 아웃 중에는 이전 화면의 로직을 멈춘다
            }
            if (fadeAlpha <= 0f) { fadeAlpha = 0f; fadeDir = 0 }
        }
        // 냥이의 배고픔은 '게임을 켜 둔 시간'으로만 흐른다.
        // 이 루프는 앱이 내려가면 멈추므로 꺼 둔 시간은 세지 않는다.
        CatTeam.tickPlayTime(deltaTime)
        currentScreen?.update(deltaTime)
    }

    fun drawGame(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        currentScreen?.draw(canvas)
        if (fadeAlpha > 0f) {
            fadePaint.alpha = (fadeAlpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), fadePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 터치는 UI 스레드에서, 갱신·그리기는 게임 스레드에서 돈다.
        // 게임 루프가 쥐는 것과 같은 잠금(holder)을 쥐어야 화면이 그리는 도중 목록을 바꾸지 않는다
        // (루비 상점 반짝이를 추가하다 ConcurrentModificationException 으로 꺼지던 문제).
        synchronized(holder) {
            if (fadeDir != 0) return true   // 전환 중 입력은 무시 (중복 진입 방지)
            return currentScreen?.onTouchEvent(event) ?: super.onTouchEvent(event)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width
        screenHeight = height
        currentScreen = LoadingScreen(this)   // 로딩 화면 → 타이틀

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
        CatTeam.flush()
    }

    fun pause() {
        gameThread?.running = false
        try {
            gameThread?.join()
        } catch (_: InterruptedException) {}
        CatTeam.flush()   // 쌓인 놀이 시간을 저장 (게임 스레드가 멈춘 뒤에)
    }

    fun resume() {
        if (holder.surface.isValid) {
            gameThread = GameThread(holder, this)
            gameThread?.running = true
            gameThread?.start()
        }
    }
}
