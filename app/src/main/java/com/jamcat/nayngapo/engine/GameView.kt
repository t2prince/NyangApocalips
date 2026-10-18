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
        I18n.init(context)   // 언어 설정 (저장된 언어를 읽는다)
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

    /** 처음 켰을 때 한 번만: 스토리 그림 3장 (건너뛰기 가능) → 타이틀 */
    fun showStory() {
        currentScreen = StoryScreen(this)
    }

    /** 상점가 끝 '스테이지 깨기' — 로비와 같은 스테이지 지도 (2026-10-09) */
    fun showStageMap() {
        fadeTo { LobbyScreen(this, mapOnly = true) }
    }

    /** 상점가 끝 '스테이지 깨기'에서 스토리 다시 보기 — 끝나면 상점 마을로 (대표님 2026-10-09) */
    fun replayStory() {
        fadeTo { StoryScreen(this, replay = true) }
    }

    /** 잼캣 로고 다음: 로딩 화면 */
    fun showLoading() {
        currentScreen = LoadingScreen(this)
    }

    fun showTitle() {
        currentScreen = TitleScreen(this)
        SoundManager.resumeBgm()   // 홈으로 돌아오면 다시 노래가 흐른다
    }

    /** 로비(홈): 전투·상점·설정 등 모든 기능으로 갈라지는 중심 화면 */
    /** 성능 로그용 — 지금 화면 이름 */
    fun currentScreenName(): String = currentScreen?.javaClass?.simpleName ?: "-"

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
    /** 상점 마을 끝 '스테이지 깨기'에서 들어간 스테이지면, 끝나고(깨거나 지거나 나가면) 상점 마을로 돌아간다 (대표님 2026-10-09) */
    var stageFromShop = false

    /** 스테이지가 끝났을 때 돌아갈 곳 — 상점에서 들어왔으면 상점 마을, 아니면 로비 */
    fun leaveStage() {
        if (stageFromShop) { stageFromShop = false; showShop() } else showLobby()
    }

    /** 못 들어가는 스테이지면 그 까닭(안내 문구), 들어갈 수 있으면 null — 로비 지도와 상점 '스테이지 깨기'가 같이 쓴다 */
    fun stageBlockReason(stage: Int): String? = when {
        !GameProgress.hasData(stage) -> "준비 중입니다"
        stage > GameProgress.unlockedStage -> "아직 잠겨 있다냥!"
        // 날아냥이가 죽었으면 하늘 스테이지에 못 간다 — 부활 약으로 깨워야 한다
        // 로봇을 타면 날 수 있어 날아냥이가 필요 없다 (2026-10-10)
        GameProgress.isSkyStage(stage) && CatTeam.flyingDead() && RobotCatalog.rideLevel == 0 -> "날아냥이가 잠들어 있다냥! 캣타워에서 부활 약을 먹여야 하늘에 갈 수 있다 (로봇을 타면 날아갈 수 있어요)"
        // 31 해적 바다: 맨몸으로는 못 들어간다 — 30에서 구한 배냥이를 타야 한다 (2026-10-09)
        stage == 31 && CatTeam.boatDead() -> "배냥이가 잠들어 있다냥! 캣타워에서 부활 약을 먹여야 바다에 나갈 수 있다"
        stage == 31 && CatTeam.boatMount() == null -> "바다라서 맨몸으로는 못 간다냥! 30스테이지에서 배냥이를 구해 오자"
        else -> null
    }

    /** 스테이지 출발 — 땅 스테이지는 먼저 출전 준비 화면(가져갈 아이템 고르기)을 띄운다 (2026-10-09). 하늘은 바로 */
    fun startStage(stageNumber: Int, fromShop: Boolean = false) {
        stageFromShop = fromShop
        if (GameProgress.isSkyStage(stageNumber)) { launchStage(stageNumber); return }
        fadeTo { BattlePrepScreen(this, stageNumber) }
    }

    /** 출전 준비를 마치고 진짜로 스테이지에 들어간다 */
    fun launchStage(stageNumber: Int) {
        fadeTo {
            if (GameProgress.isSkyStage(stageNumber)) SkyStageScreen(this, stageNumber)
            else {
                // 야간 전투: 이 스테이지의 냥이들은 공격력·체력 두 배, 눈이 하얗다 (Level 이 냥이를 만들기 전에 켠다)
                com.jamcat.nayngapo.entity.Enemy.nightBattle = BattleLoadout.night && GameProgress.doctorRescued && stageNumber != 31
                GamePlayScreen(this, stageNumber)
            }
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
    /** fromHouse: 상점 마을 집 안의 캣타워로 들어왔으면 닫을 때 집 안(캣타워 앞)으로 돌아간다 */
    fun showTeam(fromHouse: Boolean = false) {
        fadeTo { TeamScreen(this, fromHouse) }
    }

    /** 집 안 (상점 마을 맨 앞 집): floor 0 = 1층(캣타워·계단), 1 = 위층(내 냥이들), x 없으면 문 앞 */
    fun showHouse(floor: Int = 0, x: Float? = null) {
        fadeTo { HouseScreen(this, floor, x) }
    }

    /** 고양이 도감 (기획1) */
    fun showDex() {
        fadeTo { DexScreen(this) }
    }

    /** 고양이 상점: 루비로 냥이 물건을 산다 */
    fun showCatShop(fromTent: Boolean = false, title: String = "고양이 상점", ids: List<String> = ShopCatalog.catShopIds) {
        fadeTo { CatShopScreen(this, fromTent, title, ids) }
    }

    /** 투기장 (상점 마을 7번 천막): 고른 냥이가 밀짚 가짜 냥이와 싸운다 — 나오면 상점 마을(천막 앞) */
    fun showArena(catId: Int) {
        fadeTo { ArenaScreen(this, catId) }
    }

    /** 고양이 체력 훈련장 (상점 마을 13번 천막) — 고른 냥이가 러닝머신을 달린다 */
    fun showHpTraining(catId: Int) {
        fadeTo { HpTrainingScreen(this, catId) }
    }

    /** 고양이 놀이방 (상점 마을 14번 천막) — 루비 10개를 내고 들어간다 */
    fun showPlayroom() {
        fadeTo { PlayroomScreen(this) }
    }

    /** 낚시 상점 (상점 마을 낚시터 옆) — 낚싯대 Lv업·미끼 */
    fun showFishingShop() {
        fadeTo { FishingShopScreen(this) }
    }

    /** 낚시터 (상점 마을 맨 끝) — 잡은 물고기는 냥이 먹이 */
    /** atSeaEnd: 상점마을2에서 나오면 바다 끝의 어선 위로 (배 값은 다시 안 낸다) */
    fun showFishing(atSeaEnd: Boolean = false) {
        fadeTo { FishingScreen(this, atSeaEnd) }
    }

    /** 상점마을2 — 바다 끝에서 '상점마을2 가기' (2026-10-17) */
    fun showVillage2() {
        fadeTo { Village2Screen(this) }
    }

    /** 뽑기 가게 (상점 마을 집 앞) — 나오면 상점 마을 기계 앞 */
    fun showGacha(fromLobby: Boolean = false) {
        fadeTo { GachaScreen(this, fromLobby) }
    }

    /** 로봇 상점 (상점 마을 11번 천막) — 나오면 상점 마을 그 천막 앞 */
    fun showRobotShop() {
        fadeTo { RobotShopScreen(this) }
    }

    /** 자동차 매장 (상점 마을 맨 끝) — 사고 고르기, 나오면 상점 마을 매장 앞 */
    fun showCarShop() {
        fadeTo { CarShopScreen(this) }
    }

    /** 상점: 평평한 초원 위 천막 상점들 — 인간을 한 명 구할 때마다 천막 하나가 열린다 */
    fun showShop() {
        fadeTo { ShopScreen(this) }
    }

    /** 루비 상점: 쌓아온 점수를 루비로 바꾼다 */
    /** fromShop: 상점 마을 집 뒤 돈쟁이 아저씨에게서 왔으면 닫을 때 상점 마을(아저씨 앞)로 */
    fun showRubyShop(fromShop: Boolean = false) {
        fadeTo { RubyShopScreen(this, fromShop) }
    }

    /** 미션: 해내면 루비를 받는다 */
    fun showMissions() {
        fadeTo { com.jamcat.nayngapo.screen.MissionScreen(this) }
    }

    /** 설정: 데이터 리셋 등 */
    /** fromShop: 상점 마을 오른쪽 위 설정 버튼에서 왔으면 닫을 때 상점 마을로 */
    fun showSettings(fromShop: Boolean = false) {
        fadeTo { SettingsScreen(this, fromShop) }
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
        defeatedEnemies: Int, totalEnemies: Int, playerLevel: Int, nightBonus: Int = 0
    ) {
        GameProgress.addScore(score)   // 이번 판에서 번 점수를 저금통에 넣는다
        fadeTo {
            StageClearScreen(
                this, stageNumber, score, rescueCount, defeatedEnemies, totalEnemies, playerLevel, nightBonus
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
                    pendingScreen?.let { com.jamcat.nayngapo.entity.Enemy.nightBattle = false; currentScreen = it() }   // 야간 전투는 그 스테이지에서만
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
        currentScreen = LogoScreen(this)   // 잼캣 로고 → 로딩 화면 → (처음 한 번만 스토리) → 시작 화면 (대표님 2026-10-09)

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
