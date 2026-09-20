package com.jamcat.nayngapo.engine

import android.content.Context

/** 스테이지 잠금 해제 진행도와 고양이 도감. SharedPreferences에 저장되어 앱 재시작 후에도 유지된다. */
object GameProgress {
    /** 지도 그림에 놓인 돌(스테이지) 수 */
    const val TOTAL_STAGES = 37

    /** 실제 스테이지 데이터가 만들어진 곳까지 — 그 뒤는 '준비 중' (1~10 초원, 11 하늘, 12~20 정글) */
    const val PLAYABLE_STAGES = 20

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
    private const val KEY_CLEARED = "cleared_stages"
    private const val KEY_LAST_CLEARED = "last_cleared_stage"
    private const val KEY_KILLS = "stat_kills"
    private const val KEY_THIEF_KILLS = "stat_thief_kills"
    private const val KEY_MISSIONS_CLAIMED = "missions_claimed"
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
        rubies = prefs?.getInt(KEY_RUBIES, 0) ?: 0
        rubyDebt = prefs?.getInt(KEY_RUBY_DEBT, 0) ?: 0
        totalKills = prefs?.getInt(KEY_KILLS, 0) ?: 0
        thiefKills = prefs?.getInt(KEY_THIEF_KILLS, 0) ?: 0
        bgmEnabled = prefs?.getBoolean(KEY_BGM, true) ?: true
        sfxEnabled = prefs?.getBoolean(KEY_SFX, true) ?: true
        shakeEnabled = prefs?.getBoolean(KEY_SHAKE, true) ?: true
        claimedMissions.clear()
        claimedMissions.addAll(prefs?.getStringSet(KEY_MISSIONS_CLAIMED, emptySet()) ?: emptySet())
        bankScore = prefs?.getInt(KEY_BANK_SCORE, 0) ?: 0
        inventory.clear()
        for (item in ShopCatalog.items) {
            val count = prefs?.getInt(KEY_ITEM_PREFIX + item.id, 0) ?: 0
            if (count > 0) inventory[item.id] = count
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
