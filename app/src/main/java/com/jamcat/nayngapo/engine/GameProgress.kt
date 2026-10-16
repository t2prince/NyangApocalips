package com.jamcat.nayngapo.engine

import android.content.Context

/** 스테이지 잠금 해제 진행도와 고양이 도감. SharedPreferences에 저장되어 앱 재시작 후에도 유지된다. */
object GameProgress {
    /** 지도 그림에 놓인 돌(스테이지) 수 */
    const val TOTAL_STAGES = 128   // 지도 돌 128개 (대표님 2026-10-09) — 데이터가 없는 곳은 '준비 중'

    /** 실제 스테이지 데이터가 만들어진 곳까지 — 그 뒤는 '준비 중' (1~10 초원, 11 하늘, 12~20 정글) */
    const val PLAYABLE_STAGES = 31   // 21~30 꿀 지역, 31 해적 (2026-10-04)

    /** 해당 스테이지를 실제로 플레이할 수 있는가 */
    fun hasData(stageNumber: Int) = stageNumber in 1..PLAYABLE_STAGES

    /** 하늘 지역(날개냥이를 타고 나는 스테이지)인가 — 11 하나뿐이다 */
    fun isSkyStage(stageNumber: Int) = stageNumber == 11

    private const val PREFS = "nyang_progress"
    private const val KEY_UNLOCKED = "unlocked_stage"
    private const val KEY_DEX = "dex_found"
    private const val KEY_DEX_SEEN = "dex_seen"
    private const val KEY_RUBIES = "rubies"
    private const val KEY_RUBY_DEBT = "ruby_debt"
    private const val KEY_BANK_SCORE = "bank_score"
    private const val KEY_ITEM_PREFIX = "item_"
    private const val KEY_HERO = "hero"
    private const val KEY_OWNED_HEROES = "owned_heroes"
    /** 새 인간(검은 인간 말고) 값 — 대표님 2026-09-20 「새 캐릭터들은 루비 3천으로 사는 걸로」 */
    const val HERO_PRICE = 3000
    private const val KEY_CLEARED = "cleared_stages"
    private const val KEY_LAST_CLEARED = "last_cleared_stage"
    private const val KEY_KILLS = "stat_kills"
    private const val KEY_THIEF_KILLS = "stat_thief_kills"
    private const val KEY_HUMANS_RESCUED = "stat_humans_rescued"
    private const val KEY_STORY_SEEN = "story_seen"
    private const val KEY_DUMMY_LEVEL = "dummy_level"
    private const val KEY_SELECTED_CAR = "selected_car"
    private const val KEY_ROBOT_LEVEL = "robot_level"
    private const val KEY_LINE_LEVEL = "fishing_line_level"
    private const val KEY_CAUGHT_KINDS = "caught_kinds"
    private const val KEY_DAY_CLOCK = "day_clock"
    private const val KEY_RIDE_ROBOT = "ride_robot"
    private const val KEY_AB_CAR = "abandoned_car"
    private const val KEY_AB_STAGE = "abandoned_car_stage"
    private const val KEY_AB_X = "abandoned_car_x"
    private const val KEY_MISSIONS_CLAIMED = "missions_claimed"
    private const val KEY_STAGE20_NO_DEATH = "stat_stage20_no_death"
    private const val KEY_BGM = "setting_bgm"
    private const val KEY_SFX = "setting_sfx"
    private const val KEY_SHAKE = "setting_shake"

    /** 루비 한 개로 바꾸는 데 필요한 점수 */
    const val SCORE_PER_RUBY = 500

    var unlockedStage = 1
        private set

    /** 실제로 깬 스테이지 번호들 (이 기록이 생기기 전의 세이브는 비어 있다) */
    private val clearedStages = mutableSetOf<Int>()

    /** 가장 최근에 깬 스테이지 (0 = 기록 없음) */
    var lastClearedStage = 0
        private set

    fun isCleared(stageNumber: Int) = stageNumber in clearedStages

    /**
     * 로비 '전투' 버튼이 들어갈 스테이지 = 다음에 깰 스테이지.
     * 1) 방금 깬 스테이지의 바로 다음 스테이지가 아직 안 깼으면 거기
     * 2) 아니면 플레이할 수 있는 스테이지 중 아직 안 깬 가장 앞 번호
     * 3) 전부 깼으면 null (다음 지역 준비 중)
     */
    fun nextBattleStage(): Int? {
        val afterLast = lastClearedStage + 1
        if (lastClearedStage > 0 && hasData(afterLast) && afterLast <= unlockedStage && afterLast !in clearedStages) {
            return afterLast
        }
        return (1..PLAYABLE_STAGES).firstOrNull { it <= unlockedStage && it !in clearedStages }
    }

    /** 지금 고른 인간의 id (로비 '캐릭터 변경'에서 바꾼다) */
    var selectedHero = Heroes.DEFAULT_ID
        private set
    /** 산 인간들 (기본 인간은 항상 포함) */
    val ownedHeroes = mutableSetOf(Heroes.DEFAULT_ID)
    fun ownsHero(id: String) = id in ownedHeroes
    /** 인간 구매 — 루비가 모자라면 false */
    fun buyHero(id: String): Boolean {
        if (ownsHero(id)) return true
        if (!spendRubies(HERO_PRICE)) return false
        ownedHeroes.add(id)
        prefs?.edit()?.putStringSet(KEY_OWNED_HEROES, ownedHeroes.toSet())?.apply()
        return true
    }

    /** 인간을 바꾼다 */
    fun selectHero(id: String) {
        if (id == selectedHero) return
        selectedHero = id
        prefs?.edit()?.putString(KEY_HERO, id)?.apply()
    }

    /** 보유 루비 — 고양이 상점에서 물건을 사는 데 쓴다 */
    var rubies = 0
    /** 루비 빚 — 도둑냥이가 가진 것보다 많이 훔치면 루비는 0이 되고 나머지가 여기 쌓인다. 새로 얻는 루비로 먼저 갚는다 (대표님 2026-09-20) */
    var rubyDebt = 0
        private set
    /** 누적 처치 수 (미션용, 2026-09-20) */
    /** 스토리 화면을 이미 봤는가 — 게임을 처음 켰을 때 한 번만 나온다 (대표님 2026-10-08) */
    var storySeen = false
        private set

    /**
     * 투기장 가짜 냥이 Lv (대표님 2026-10-09): 루비 10개로 Lv업, Lv업 할 때마다 가짜 냥이가 두 배로 딴딴해진다.
     * 최대 Lv 1,000,000,000 (대표님 2026-10-09).
     */
    var dummyLevel = 1
        private set

    /** 스테이지에서 타고 다닐 자동차 id (자동차 매장 '자동차 선택', 없으면 "") */
    var selectedCar = ""
        private set

    /**
     * 방치차 (대표님 2026-10-09 「스테이지에서 내리고 나간 다음 다시 들어오면 방치차가 돼」):
     * 스테이지에서 차에서 내린 채 나가면 그 차는 그 스테이지 그 자리에 남는다. 다시 가서 타기 전까지 다른 곳에서는 못 탄다.
     */
    var abandonedCarId = ""
        private set
    var abandonedCarStage = 0
        private set
    var abandonedCarX = 0f
        private set

    fun abandonCar(id: String, stage: Int, x: Float) {
        abandonedCarId = id; abandonedCarStage = stage; abandonedCarX = x
        prefs?.edit()?.putString(KEY_AB_CAR, id)?.putInt(KEY_AB_STAGE, stage)?.putFloat(KEY_AB_X, x)?.apply()
    }

    fun clearAbandonedCar() {
        if (abandonedCarStage == 0) return
        abandonedCarId = ""; abandonedCarStage = 0; abandonedCarX = 0f
        prefs?.edit()?.remove(KEY_AB_CAR)?.remove(KEY_AB_STAGE)?.remove(KEY_AB_X)?.apply()
    }

    /**
     * 낚시줄 Lv (대표님 2026-10-16 「낚시줄 Lv업이 있어, 누르면 Lv2가 되면서 두 배가 돼」) — Lv 마다 낚시줄 길이(내려가는 깊이)가 두 배.
     * 값 = 루비 20 × 지금 Lv · 최대 Lv 4 는 Claude 판단.
     */
    var fishingLineLevel = 1
        private set
    const val LINE_LEVEL_MAX = 4

    fun lineLevelUpPrice() = 20 * fishingLineLevel

    fun levelUpFishingLine(): Boolean {
        if (fishingLineLevel >= LINE_LEVEL_MAX) return false
        if (!spendRubies(lineLevelUpPrice())) return false
        fishingLineLevel++
        prefs?.edit()?.putInt(KEY_LINE_LEVEL, fishingLineLevel)?.apply()
        return true
    }

    /** 밤낮 시계 — 플레이 시간 1시간마다 낮 ↔ 밤 (대표님 2026-10-17). 0~3600 낮, 3600~7200 밤 */
    var dayClock = 0f
        private set
    private var dayClockSaveT = 0f
    const val DAY_LENGTH = 3600f
    val isNight: Boolean get() = dayClock >= DAY_LENGTH

    fun tickDayClock(dt: Float) {
        dayClock = (dayClock + dt.coerceIn(0f, 1f)) % (DAY_LENGTH * 2)
        dayClockSaveT += dt
        if (dayClockSaveT >= 30f) { dayClockSaveT = 0f; prefs?.edit()?.putFloat(KEY_DAY_CLOCK, dayClock)?.apply() }
    }

    /** 낚시로 한 번이라도 잡아 본 종류 — 처음 잡으면 루비 50 (대표님 2026-10-16) */
    private val caughtKinds = mutableSetOf<String>()
    const val FIRST_CATCH_RUBIES = 50

    /** 잡았다고 적는다. 처음 잡은 종류면 루비 50을 주고 true */
    fun recordCatch(id: String): Boolean {
        if (!caughtKinds.add(id)) return false
        prefs?.edit()?.putString(KEY_CAUGHT_KINDS, caughtKinds.joinToString(","))?.apply()
        addRubies(FIRST_CATCH_RUBIES)
        return true
    }

    /** 로봇 Lv (0 = 없음) · 스테이지에서 로봇을 탈지 (로봇 상점, 2026-10-09) */
    var robotLevel = 0
        private set
    var rideRobot = false
        private set

    /** 로봇을 사거나 Lv 올리기 — 루비가 모자라거나 최대면 false */
    fun upgradeRobot(): Boolean {
        if (robotLevel >= RobotCatalog.MAX_LEVEL) return false
        if (!spendRubies(RobotCatalog.priceOf(robotLevel + 1))) return false
        robotLevel++
        if (robotLevel == 1) rideRobot = true   // 사면 바로 탄다
        prefs?.edit()?.putInt(KEY_ROBOT_LEVEL, robotLevel)?.putBoolean(KEY_RIDE_ROBOT, rideRobot)?.apply()
        return true
    }

    fun setRideRobot(on: Boolean) {
        rideRobot = on
        prefs?.edit()?.putBoolean(KEY_RIDE_ROBOT, on)?.apply()
    }

    fun selectCar(id: String) {
        selectedCar = id
        prefs?.edit()?.putString(KEY_SELECTED_CAR, id)?.apply()
    }
    const val DUMMY_LEVEL_PRICE = 10
    const val DUMMY_LEVEL_MAX = 1_000_000_000

    fun levelUpDummy(): Boolean {
        if (dummyLevel >= DUMMY_LEVEL_MAX) return false
        if (!spendRubies(DUMMY_LEVEL_PRICE)) return false
        dummyLevel++
        prefs?.edit()?.putInt(KEY_DUMMY_LEVEL, dummyLevel)?.apply()
        return true
    }

    fun markStorySeen() {
        if (storySeen) return
        storySeen = true
        prefs?.edit()?.putBoolean(KEY_STORY_SEEN, true)?.apply()
    }

    /** 지금까지 구한 인간 수 — 한 명마다 상점 천막 하나가 열린다 (대표님 2026-09-27). 이 기록은 2026-09-27 부터 센다 */
    var humansRescued = 0
        private set

    var totalKills = 0
        private set
    var thiefKills = 0
        private set
    // ===== 설정 (설정 화면, 2026-09-20) =====
    var bgmEnabled = true
        private set
    var sfxEnabled = true
        private set
    var shakeEnabled = true
        private set
    fun setBgm(on: Boolean) { bgmEnabled = on; prefs?.edit()?.putBoolean(KEY_BGM, on)?.apply(); SoundManager.applyBgmSetting() }
    fun setSfx(on: Boolean) { sfxEnabled = on; prefs?.edit()?.putBoolean(KEY_SFX, on)?.apply() }
    fun setShake(on: Boolean) { shakeEnabled = on; prefs?.edit()?.putBoolean(KEY_SHAKE, on)?.apply() }

    /** 한 번도 죽지 않고 20스테이지를 깬 적이 있는가 (미션 '살아남아라') */
    var stage20NoDeath = false
        private set
    fun markStage20NoDeath() {
        if (stage20NoDeath) return
        stage20NoDeath = true
        prefs?.edit()?.putBoolean(KEY_STAGE20_NO_DEATH, true)?.apply()
    }

    /** 보상을 받은 미션 id */
    val claimedMissions = mutableSetOf<String>()

    /** 그동안 쌓아온(아직 루비로 환전하지 않은) 점수 */
    var bankScore = 0
        private set

    /** 구매한 물건 개수 (물건 id → 개수) */
    private val inventory = mutableMapOf<String, Int>()

    // ===== 고양이 도감 (기획1: 고양이를 찾으면 도감에 생긴다) =====
    private val discovered = mutableSetOf<String>()
    /** 도감 화면에서 이미 확인한 냥이들 — 아직 못 봤으면 NEW 배지가 붙는다 */
    private val seenInDex = mutableSetOf<String>()

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        unlockedStage = prefs?.getInt(KEY_UNLOCKED, 1) ?: 1
        clearedStages.clear()
        prefs?.getStringSet(KEY_CLEARED, emptySet())?.forEach { it.toIntOrNull()?.let(clearedStages::add) }
        lastClearedStage = prefs?.getInt(KEY_LAST_CLEARED, 0) ?: 0
        discovered.clear()
        discovered.addAll(prefs?.getStringSet(KEY_DEX, emptySet()) ?: emptySet())
        seenInDex.clear()
        seenInDex.addAll(prefs?.getStringSet(KEY_DEX_SEEN, emptySet()) ?: emptySet())

        selectedHero = prefs?.getString(KEY_HERO, Heroes.DEFAULT_ID) ?: Heroes.DEFAULT_ID
        ownedHeroes.clear(); ownedHeroes.add(Heroes.DEFAULT_ID)
        ownedHeroes.addAll(prefs?.getStringSet(KEY_OWNED_HEROES, emptySet()) ?: emptySet())
        ownedHeroes.add(selectedHero)   // 구매 도입 전에 고른 인간은 가진 것으로 (기존 세이브 보호)
        rubies = prefs?.getInt(KEY_RUBIES, 0) ?: 0
        rubyDebt = prefs?.getInt(KEY_RUBY_DEBT, 0) ?: 0
        totalKills = prefs?.getInt(KEY_KILLS, 0) ?: 0
        thiefKills = prefs?.getInt(KEY_THIEF_KILLS, 0) ?: 0
        humansRescued = prefs?.getInt(KEY_HUMANS_RESCUED, 0) ?: 0
        storySeen = prefs?.getBoolean(KEY_STORY_SEEN, false) ?: false
        dummyLevel = (prefs?.getInt(KEY_DUMMY_LEVEL, 1) ?: 1).coerceIn(1, DUMMY_LEVEL_MAX)
        selectedCar = prefs?.getString(KEY_SELECTED_CAR, "") ?: ""
        robotLevel = (prefs?.getInt(KEY_ROBOT_LEVEL, 0) ?: 0).coerceIn(0, RobotCatalog.MAX_LEVEL)
        fishingLineLevel = (prefs?.getInt(KEY_LINE_LEVEL, 1) ?: 1).coerceIn(1, LINE_LEVEL_MAX)
        dayClock = (prefs?.getFloat(KEY_DAY_CLOCK, 0f) ?: 0f).coerceIn(0f, DAY_LENGTH * 2)
        caughtKinds.clear(); prefs?.getString(KEY_CAUGHT_KINDS, "")?.split(',')?.filter { it.isNotBlank() }?.let { caughtKinds.addAll(it) }
        rideRobot = prefs?.getBoolean(KEY_RIDE_ROBOT, false) ?: false
        abandonedCarId = prefs?.getString(KEY_AB_CAR, "") ?: ""
        abandonedCarStage = prefs?.getInt(KEY_AB_STAGE, 0) ?: 0
        abandonedCarX = prefs?.getFloat(KEY_AB_X, 0f) ?: 0f
        bgmEnabled = prefs?.getBoolean(KEY_BGM, true) ?: true
        sfxEnabled = prefs?.getBoolean(KEY_SFX, true) ?: true
        shakeEnabled = prefs?.getBoolean(KEY_SHAKE, true) ?: true
        stage20NoDeath = prefs?.getBoolean(KEY_STAGE20_NO_DEATH, false) ?: false
        claimedMissions.clear()
        claimedMissions.addAll(prefs?.getStringSet(KEY_MISSIONS_CLAIMED, emptySet()) ?: emptySet())
        bankScore = prefs?.getInt(KEY_BANK_SCORE, 0) ?: 0
        inventory.clear()
        // 저장된 물건을 모두 읽는다 — 상점 목록(ShopCatalog)에 없는 자동차(car_*) 같은 것도 (2026-10-09 「빌드하면 자동차가 없어지는 버그」:
        // 예전엔 ShopCatalog.items 만 읽어서 앱을 다시 켜면 산 차가 사라졌다. 저장은 되어 있었으므로 이제 다시 보인다)
        for ((key, value) in prefs?.all.orEmpty()) {
            if (!key.startsWith(KEY_ITEM_PREFIX)) continue
            val count = (value as? Int) ?: continue
            if (count > 0) inventory[key.removePrefix(KEY_ITEM_PREFIX)] = count
        }

        // 팀 냥이 명단·고양이 기술도 같은 저장소를 쓴다
        prefs?.let {
            CatTeam.init(it)
            CatSkills.init(it)
            HumanSkills.init(it)
        }
    }

    /**
     * 데이터 리셋 (설정 화면) — 루비·점수·물건·깬 스테이지·도감·팀 냥이·배운 기술을 전부 지우고
     * 처음 상태로 되돌린다. 밸런스 테스트용. 되돌릴 수 없으니 호출 전에 반드시 사용자 확인을 받는다.
     */
    fun resetAll(context: Context) {
        prefs?.edit()?.clear()?.commit()
        init(context)   // 비워진 저장소를 다시 읽어 메모리 상태도 처음으로
    }

    // ===== 점수 저금통 · 루비 · 고양이 상점 =====

    /** 냥이 한 마리 처치 — 미션 통계 (몇 초에 한 번 꼴이라 바로 저장해도 부담 없다) */
    fun addKill(isThief: Boolean = false) {
        totalKills++
        if (isThief) thiefKills++
        prefs?.edit()?.putInt(KEY_KILLS, totalKills)?.putInt(KEY_THIEF_KILLS, thiefKills)?.apply()
    }

    /** 감옥에서 인간을 구했다 — 구하는 순간 바로 저장 (스테이지를 못 깨도 구한 건 구한 것) */
    fun addRescuedHumans(n: Int) {
        if (n <= 0) return
        humansRescued += n
        prefs?.edit()?.putInt(KEY_HUMANS_RESCUED, humansRescued)?.apply()
    }

    /** 미션 보상 수령 기록 */
    fun claimMission(id: String) {
        claimedMissions.add(id)
        prefs?.edit()?.putStringSet(KEY_MISSIONS_CLAIMED, claimedMissions.toSet())?.apply()
    }

    /** 한 판이 끝나면 얻은 점수를 저금통에 쌓는다 (루비 상점에서 환전) */
    fun addScore(score: Int) {
        if (score <= 0) return
        bankScore += score
        prefs?.edit()?.putInt(KEY_BANK_SCORE, bankScore)?.apply()
    }

    /** 루비를 그대로 얻는다 (적을 쓰러뜨렸을 때의 드롭) */
    fun addRubies(n: Int) {
        if (n <= 0) return
        // 빚이 있으면 먼저 갚고 남는 만큼만 지갑에
        val repay = n.coerceAtMost(rubyDebt)
        rubyDebt -= repay
        rubies += n - repay
        prefs?.edit()?.putInt(KEY_RUBIES, rubies)?.putInt(KEY_RUBY_DEBT, rubyDebt)?.apply()
    }

    /** 루비를 쓴다 (기술 배우기) — 모자라면 false */
    /** 도둑냥이가 훔쳐 간다 — 지갑에서 빼고, 모자라는 만큼은 빚으로. 훔친 총량(n)을 돌려준다 */
    fun takeRubies(n: Int): Int {
        if (n <= 0) return 0
        val fromWallet = n.coerceAtMost(rubies)
        rubies -= fromWallet
        rubyDebt += n - fromWallet
        prefs?.edit()?.putInt(KEY_RUBIES, rubies)?.putInt(KEY_RUBY_DEBT, rubyDebt)?.apply()
        return n
    }

    fun spendRubies(n: Int): Boolean {
        if (n < 0 || rubies < n) return false
        rubies -= n
        prefs?.edit()?.putInt(KEY_RUBIES, rubies)?.apply()
        return true
    }

    /** 지금 점수로 바꿀 수 있는 루비 개수 */
    fun maxExchangeable() = bankScore / SCORE_PER_RUBY

    /** 점수를 루비로 환전 — 점수가 모자라면 false */
    fun exchangeRubies(count: Int): Boolean {
        if (count <= 0) return false
        val cost = count * SCORE_PER_RUBY
        if (bankScore < cost) return false
        bankScore -= cost
        prefs?.edit()?.putInt(KEY_BANK_SCORE, bankScore)?.apply()
        addRubies(count)   // 빚이 있으면 먼저 갚는다
        return true
    }

    /** 가지고 있는 물건 개수 */
    fun itemCount(id: String) = inventory[id] ?: 0

    /** 물건을 되돌려 받는다 (갑옷을 벗기면 다시 가방으로) */
    fun addItem(id: String, count: Int = 1) {
        if (count <= 0) return
        val next = itemCount(id) + count
        inventory[id] = next
        prefs?.edit()?.putInt(KEY_ITEM_PREFIX + id, next)?.apply()
    }

    /** 물건 하나를 써 없앤다 (간식 먹이기) — 남은 게 없으면 false */
    fun consumeItem(id: String): Boolean {
        val count = itemCount(id)
        if (count <= 0) return false
        val next = count - 1
        if (next == 0) inventory.remove(id) else inventory[id] = next
        prefs?.edit()?.putInt(KEY_ITEM_PREFIX + id, next)?.apply()
        return true
    }

    /** 루비로 물건 구매 — 루비가 모자라면 false */
    fun buyItem(id: String, price: Int): Boolean {
        if (price < 0 || rubies < price) return false
        rubies -= price
        val next = itemCount(id) + 1
        inventory[id] = next
        prefs?.edit()
            ?.putInt(KEY_RUBIES, rubies)
            ?.putInt(KEY_ITEM_PREFIX + id, next)
            ?.apply()
        return true
    }

    /** 스테이지 클리어 → 다음 스테이지 잠금 해제 */
    fun onStageCleared(stageNumber: Int) {
        clearedStages.add(stageNumber)
        lastClearedStage = stageNumber
        val next = (stageNumber + 1).coerceAtMost(TOTAL_STAGES)
        if (next > unlockedStage) unlockedStage = next
        prefs?.edit()
            ?.putInt(KEY_UNLOCKED, unlockedStage)
            ?.putStringSet(KEY_CLEARED, clearedStages.map { it.toString() }.toSet())
            ?.putInt(KEY_LAST_CLEARED, lastClearedStage)
            ?.apply()
    }

    /** 냥이 발견 — 처음 만난 냥이면 true (도감에 새로 등록됨) */
    fun discover(dexId: String): Boolean {
        if (dexId in discovered) return false
        discovered.add(dexId)
        prefs?.edit()?.putStringSet(KEY_DEX, discovered.toSet())?.apply()
        return true
    }

    fun isDiscovered(dexId: String) = dexId in discovered
    fun discoveredCount() = discovered.count { id -> CatDex.entries.any { it.id == id } }

    /** 도감에서 아직 확인하지 않은 새 냥이인가 (NEW 배지) */
    fun isNewInDex(dexId: String) = dexId in discovered && dexId !in seenInDex

    /** 도감에 새로 등록된 냥이가 하나라도 있는가 (로비 알림 점) */
    fun hasNewDexEntry() = discovered.any { it !in seenInDex }

    /** 도감 화면을 닫을 때 호출 — 지금까지 발견한 냥이를 전부 '확인함'으로 */
    fun markDexSeen() {
        seenInDex.addAll(discovered)
        prefs?.edit()?.putStringSet(KEY_DEX_SEEN, seenInDex.toSet())?.apply()
    }
}
