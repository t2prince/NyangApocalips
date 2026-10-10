package com.jamcat.nayngapo.engine

import android.content.SharedPreferences
import com.jamcat.nayngapo.entity.AllyCat
import org.json.JSONArray
import org.json.JSONObject

/**
 * 내 팀 냥이 명단. 스테이지에서 감옥을 열어 구한 냥이가 여기에 쌓이고,
 * 쓰다듬고 놀아주고 간식을 먹여 돌보면 포만감·청결도·즐거움·애정이 오른다.
 * 최대 3마리를 골라 함께 출전한다. GameProgress와 같은 SharedPreferences에 JSON으로 저장된다.
 */
object CatTeam {

    /** 함께 출전할 수 있는 최대 마리 수 */
    const val PARTY_MAX = 3

    /** 냥이가 더 자라지 않는 한계 */
    const val MAX_LEVEL = 20

    /** 돌봄 수치의 최대치 */
    const val CARE_MAX = 100

    /** 직접 지어 줄 수 있는 이름의 최대 글자 수 */
    const val NAME_MAX = 8

    /** 'Lv업' 아이템 하나가 올려 주는 공격력 */
    const val ATTACK_UP_AMOUNT = 60

    /** 갑옷을 입은 냥이가 받는 피해 비율 */
    const val ARMOR_DAMAGE_FACTOR = 0.4f

    /** 포만감 하트 한 개에 해당하는 점수 */
    const val FULLNESS_PER_HEART = 20

    /** 포만감 하트 최대 개수 */
    const val FULLNESS_HEARTS = 5

    data class TeamCat(
        val id: Int,
        val kind: AllyCat.Kind,
        var name: String,
        var level: Int = 1,
        var exp: Int = 0,
        var inParty: Boolean = false,
        /** 관리 화면에서 이미 확인한 냥이인가 (로비 알림 점) */
        var seen: Boolean = false,
        // ===== 돌봄 수치 (0~100) =====
        /** 포만감 — 간식을 먹으면 오른다. 새 냥이는 하트 다섯 개로 시작한다 */
        var fullness: Int = 100,
        /** 청결도 — 빗질을 하면 오른다 */
        var cleanliness: Int = 60,
        /** 즐거움 — 놀아주면 오른다 */
        var joy: Int = 60,
        /** 애정 — 쓰다듬을 때마다 쌓이고 줄어들지 않는다 */
        var affection: Int = 20,
        /** 마지막으로 시간 경과를 반영한 시각 (epoch ms) */
        var lastCareAt: Long = System.currentTimeMillis(),
        /** 갑옷을 입고 있는가 — 쓰러질 때까지 유지된다 */
        var hasArmor: Boolean = false,
        /** 전장에서 쓰러져 돌아오지 못한 냥이 (칸에 무덤이 남는다) */
        var isDead: Boolean = false,
        /** 잠든 스테이지 번호 */
        var diedAtStage: Int = 0,
        /** 너무 배고파서 팀을 떠난 냥이 */
        var hasLeft: Boolean = false,
        /** 'Lv업' 아이템으로 영구히 올린 공격력 (되살아나도 남는다) */
        var attackBonus: Int = 0,
        /** 새끼인가 — 교배로 태어나거나 감옥에서 구한 새끼 (대표님 2026-09-27). 키워서 GROW_UP_LEVEL 이 되면 어른이 된다 */
        var isKitten: Boolean = false,
        /** 새끼의 부모 이름 "A × B" (태어날 때의 이름) */
        val parents: String = "",
        /** 성별 — true 수컷 ♂, false 암컷 ♀ (대표님 2026-10-09). 구출·출생 때 무작위 */
        val male: Boolean = Math.random() < 0.5,
        /** 이 냥이가 상점 마을 '고양이 기술' 천막에서 배운 기술 id들 (데려온 냥이 1마리만 배운다, 2026-10-09) */
        val skills: MutableSet<String> = mutableSetOf(),
        /** 중성화 수술을 받았나(0/1) — 빨라진다. 중성화한 냥이는 새끼를 낳지 않는다 (2026-10-09) */
        var surgeries: Int = 0,
        /** HP 수술 횟수 — 한 번에 체력 +HP_SURGERY_HP (2026-10-09) */
        var hpSurgeries: Int = 0,
        /** 달래기 수술 횟수 — 3번을 다 해야 공격력 +4 (2026-10-09) */
        var soothes: Int = 0,
        /** 체력 훈련장에서 늘린 체력 (대표님 2026-10-10 「고양이 체력 훈련장」) */
        var hpTraining: Int = 0,
        /** 고양이 놀이방에 맡겨 두었다 — 캣타워에서 빠지고, 거기서 밥을 줘서 포만감이 늘 가득 (대표님 2026-10-10) */
        var inDaycare: Boolean = false,
        /** 치즈냥이의 털 무늬 (다른 종은 CHEESE 로 두고 무시) */
        val coat: CatCoat = CatCoat.CHEESE
    ) {
        /** 더는 돌볼 수 없는 냥이 (무덤이거나 떠났다) */
        val gone: Boolean get() = isDead || hasLeft
    }

    /** 돌봄 행동의 결과. ok = 실제로 수치가 올랐는가, message = 화면에 띄울 안내 */
    data class CareResult(val ok: Boolean, val message: String, val leveledUp: Boolean = false)

    private const val KEY_CATS = "team_cats"
    private const val KEY_NEXT_ID = "team_next_id"
    private const val KEY_PLAY_SECONDS = "team_play_seconds"
    private const val KEY_NAME_COUNTS = "team_name_counts"
    private const val KEY_BREED_SECONDS = "team_breed_seconds"

    /**
     * 교배 (대표님 2026-09-27 「고양이 관리에 있는 고양이들이 스스로 교배해 가끔 새끼를 낳고, 누구랑 교배했는지 나와」):
     * 게임을 켜 둔 놀이 시간 BREED_EVERY 초마다, 살아 있는 냥이가 둘 이상이면 BREED_CHANCE 확률로
     * 무작위 두 마리 사이에서 새끼 한 마리가 태어난다. 새끼는 부모 한쪽의 종류·무늬를 닮은 Lv1 냥이.
     * 20분·50%·닮는 방식은 Claude 판단.
     */
    const val BREED_EVERY = 1200f
    /** 새끼를 키워 이 레벨이 되면 다 자란다 (대표님 「새끼 고양이를 키울 수 있어」, 레벨 5 는 Claude 판단) */
    const val GROW_UP_LEVEL = 5
    private const val BREED_CHANCE = 0.5
    private var breedSeconds = 0f
    private val pendingBirthNotices = mutableListOf<String>()

    /** 묭묭 진화 남은 놀이 시간(초) — 스테이지가 바뀌어도, 상점 마을에서도 이어진다 (2026-10-09 「상점에서도 거대해야」) */
    var evolveSecondsLeft = 0f
        private set
    private const val KEY_EVOLVE = "team_evolve_seconds"
    fun startEvolve(seconds: Float) { evolveSecondsLeft = seconds; save() }

    /** 게임을 켜 둔 채 한 시간을 보내면 줄어드는 양 (애정은 줄지 않는다) */
    private const val DECAY_FULLNESS = FULLNESS_PER_HEART   // 놀이 시간 한 시간에 하트 하나
    private const val DECAY_CLEANLINESS = 3
    private const val DECAY_JOY = 4

    /** 하트 하나가 닳는 데 걸리는 '게임 안에서 보낸' 시간(초) */
    const val SECONDS_PER_HEART = 3600f

    /** 저장은 이 정도 놀이 시간마다 한 번씩 (매 프레임 쓰지 않는다) */
    private const val SAVE_EVERY_SECONDS = 30f

    /** 마지막 하트가 닳은 뒤로 게임 안에서 보낸 시간 */
    private var playSeconds = 0f
    private var saveDebt = 0f

    private val roster = mutableListOf<TeamCat>()
    private var nextId = 1

    /**
     * 종류마다 몇 번째 냥이까지 이름을 붙였는가.
     * 명단에서 세지 않고 따로 쌓아 두므로, 보내준 냥이의 번호를 다시 쓰지 않는다.
     */
    private val nameCounts = mutableMapOf<AllyCat.Kind, Int>()
    private var prefs: SharedPreferences? = null

    /** 방금 떠난 냥이 이름 — 로비에서 한 번 알려 주고 비운다 (저장하지 않는다) */
    private val pendingLeaveNotices = mutableListOf<String>()

    /** 쓰다듬기 연타 방지 (냥이 id → 마지막으로 쓰다듬은 시각 ms). 저장하지 않는다. */
    private val petCooldown = mutableMapOf<Int, Long>()
    private const val PET_COOLDOWN_MS = 800L

    /** 무덤까지 포함한 전체 명단 (칸 순서가 유지된다) — 놀이방에 맡긴 냥이는 빠진다 */
    val cats: List<TeamCat> get() = roster.filter { !it.inDaycare }

    /** 고양이 놀이방에 맡긴 냥이들 */
    val daycare: List<TeamCat> get() = roster.filter { it.inDaycare && !it.gone }

    /** 놀이방에 맡기기 — 캣타워·출전에서 빠지고 포만감 가득 */
    fun putInDaycare(id: Int) {
        val c = cat(id) ?: return
        c.inDaycare = true; c.inParty = false; c.fullness = CARE_MAX
        save()
    }

    /** 맡기지 않기 — 다시 캣타워로 데려온다 */
    fun takeFromDaycare(id: Int) {
        val c = cat(id) ?: return
        c.inDaycare = false; c.fullness = CARE_MAX
        save()
    }

    /** 하늘 스테이지에 탈 수 있는, 살아 있는 날아냥이 (없으면 null) */
    fun flyingMount(): TeamCat? = roster.firstOrNull { it.kind == AllyCat.Kind.FLYING && !it.gone && !it.inDaycare }

    /** 날아냥이를 한 마리라도 데리고 있는가 (11스테이지 입장 조건) */
    fun hasFlyingCat() = flyingMount() != null

    /** 31 해적 바다에서 타는 배냥이 (30스테이지에서 구한다, 2026-10-09) */
    fun boatMount(): TeamCat? = roster.firstOrNull { it.kind == AllyCat.Kind.BOAT && !it.gone && !it.inDaycare }
    /** 배냥이가 죽어 무덤에 있고 살아 있는 배냥이가 없다 */
    fun boatDead(): Boolean = boatMount() == null && roster.any { it.kind == AllyCat.Kind.BOAT && it.isDead }

    /** 날아냥이가 죽어 무덤에 있고 살아 있는 날아냥이가 없다 — 이러면 하늘 스테이지에 못 간다 (대표님 2026-10-04 「날아냥이가 죽으면 하늘 스테이지를 깰 수 없어」) */
    fun flyingDead(): Boolean = flyingMount() == null && roster.any { it.kind == AllyCat.Kind.FLYING && it.isDead }

    /** 아직 살아 있는 냥이들 */
    val living: List<TeamCat> get() = roster.filter { !it.gone && !it.inDaycare }

    /** 이번 스테이지에 함께 나갈 냥이들 (최대 PARTY_MAX, 무덤은 빠진다) */
    val party: List<TeamCat> get() = roster.filter { it.inParty && !it.gone && !it.inDaycare }.take(PARTY_MAX)

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        roster.clear()
        petCooldown.clear()
        pendingLeaveNotices.clear()
        nextId = prefs.getInt(KEY_NEXT_ID, 1)
        playSeconds = prefs.getInt(KEY_PLAY_SECONDS, 0).toFloat().coerceIn(0f, SECONDS_PER_HEART)
        breedSeconds = prefs.getInt(KEY_BREED_SECONDS, 0).toFloat().coerceIn(0f, BREED_EVERY)
        evolveSecondsLeft = prefs.getInt(KEY_EVOLVE, 0).toFloat()
        pendingBirthNotices.clear()
        saveDebt = 0f
        nameCounts.clear()

        var migratedCoat = false
        val raw = prefs.getString(KEY_CATS, null)
        if (raw != null) {
            val arr = try { JSONArray(raw) } catch (_: Exception) { null }
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val kind = kindOf(o.optString("kind", "NORMAL")) ?: continue
                    roster.add(
                        TeamCat(
                            id = o.optInt("id", nextId++),
                            kind = kind,
                            name = o.optString("name", "치즈냥이"),
                            level = o.optInt("level", 1).coerceIn(1, MAX_LEVEL),
                            exp = o.optInt("exp", 0).coerceAtLeast(0),
                            inParty = o.optBoolean("inParty", false),
                            seen = o.optBoolean("seen", true),
                            fullness = o.optInt("fullness", 100).coerceIn(0, CARE_MAX),
                            cleanliness = o.optInt("cleanliness", 60).coerceIn(0, CARE_MAX),
                            joy = o.optInt("joy", 60).coerceIn(0, CARE_MAX),
                            affection = o.optInt("affection", 20).coerceIn(0, CARE_MAX),
                            lastCareAt = o.optLong("lastCareAt", System.currentTimeMillis()),
                            hasArmor = o.optBoolean("hasArmor", false),
                            isDead = o.optBoolean("isDead", false),
                            diedAtStage = o.optInt("diedAtStage", 0),
                            hasLeft = o.optBoolean("hasLeft", false),
                            attackBonus = o.optInt("attackBonus", 0).coerceAtLeast(0),
                            isKitten = o.optBoolean("isKitten", false),
                            parents = o.optString("parents", ""),
                            // 성별 도입 전 냥이는 한 번 무작위로 정해 바로 저장한다
                            male = if (o.has("male")) o.optBoolean("male") else { migratedCoat = true; Math.random() < 0.5 },
                            surgeries = o.optInt("surgeries", 0).coerceIn(0, SURGERY_MAX),
                            hpSurgeries = o.optInt("hpSurgeries", 0).coerceIn(0, HP_SURGERY_MAX),
                            soothes = o.optInt("soothes", 0).coerceIn(0, SOOTHE_NEED),
                            hpTraining = o.optInt("hpTraining", 0).coerceAtLeast(0),
                            inDaycare = o.optBoolean("inDaycare", false),
                            skills = o.optJSONArray("skills")?.let { arr -> MutableList(arr.length()) { arr.optString(it) }.toMutableSet() } ?: mutableSetOf(),
                            // 무늬 도입(2026-09-20) 전에 저장된 치즈냥이는 무늬가 없다 → 한 번 무작위로 뽑아 준다 (대표님 지시)
                            coat = if (o.has("coat")) CatCoat.of(o.optString("coat"))
                                   else if (kind == AllyCat.Kind.NORMAL) { migratedCoat = true; CatCoat.random() }
                                   else CatCoat.CHEESE
                        )
                    )
                }
            }
        }
        // 저장이 깨져 출전이 3마리를 넘겼더라도 앞의 3마리만 남긴다
        roster.filter { it.inParty }.drop(PARTY_MAX).forEach { it.inParty = false }

        // 이름 번호표: 저장된 값이 있으면 그대로, 없는 예전 저장은 명단에서 한 번만 끌어온다
        val countsRaw = prefs.getString(KEY_NAME_COUNTS, null)
        if (countsRaw != null) {
            val o = try { JSONObject(countsRaw) } catch (_: Exception) { null }
            if (o != null) {
                for (kind in AllyCat.Kind.values()) {
                    val n = o.optInt(kind.name, -1)
                    if (n >= 0) nameCounts[kind] = n
                }
            }
        }
        for (kind in AllyCat.Kind.values()) {
            if (kind !in nameCounts) nameCounts[kind] = roster.count { it.kind == kind }
        }
        if (migratedCoat) save()   // 새로 뽑은 무늬를 바로 적어 두어 다음 실행에 또 바뀌지 않게
        // 앱을 꺼 둔 시간은 흐르지 않는다 — 배고픔은 게임을 하는 동안에만 진행된다
    }

    private fun kindOf(name: String): AllyCat.Kind? =
        AllyCat.Kind.values().firstOrNull { it.name == name }

    private fun save() {
        val arr = JSONArray()
        for (c in roster) {
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("kind", c.kind.name)
                    .put("coat", c.coat.name)
                    .put("name", c.name)
                    .put("level", c.level)
                    .put("exp", c.exp)
                    .put("inParty", c.inParty)
                    .put("seen", c.seen)
                    .put("fullness", c.fullness)
                    .put("cleanliness", c.cleanliness)
                    .put("joy", c.joy)
                    .put("affection", c.affection)
                    .put("lastCareAt", c.lastCareAt)
                    .put("hasArmor", c.hasArmor)
                    .put("isDead", c.isDead)
                    .put("diedAtStage", c.diedAtStage)
                    .put("hasLeft", c.hasLeft)
                    .put("attackBonus", c.attackBonus)
                    .put("isKitten", c.isKitten)
                    .put("parents", c.parents)
                    .put("male", c.male)
                    .put("skills", JSONArray(c.skills.toList()))
                    .put("surgeries", c.surgeries)
                    .put("hpSurgeries", c.hpSurgeries)
                    .put("soothes", c.soothes)
                    .put("hpTraining", c.hpTraining)
                    .put("inDaycare", c.inDaycare)
            )
        }
        val counts = JSONObject()
        for ((kind, n) in nameCounts) counts.put(kind.name, n)

        prefs?.edit()
            ?.putString(KEY_CATS, arr.toString())
            ?.putInt(KEY_NEXT_ID, nextId)
            ?.putInt(KEY_PLAY_SECONDS, playSeconds.toInt())
            ?.putInt(KEY_BREED_SECONDS, breedSeconds.toInt())
            ?.putInt(KEY_EVOLVE, evolveSecondsLeft.toInt())
            ?.putString(KEY_NAME_COUNTS, counts.toString())
            ?.apply()
    }

    // ===== 시간이 지나면 배고프고 지저분해진다 =====

    /**
     * 게임 안에서 흐른 시간을 쌓는다 (매 프레임 호출).
     * 앱이 내려가면 게임 루프도 멈추므로 꺼 둔 시간은 세지 않는다.
     * 놀이 시간 한 시간마다 하트 하나가 닳고, 하트가 다 떨어진 냥이는 떠난다.
     */
    fun tickPlayTime(deltaSeconds: Float) {
        if (deltaSeconds <= 0f || roster.isEmpty()) return
        val dt = deltaSeconds.coerceAtMost(1f)   // 멈췄다 돌아온 큰 간격은 잘라낸다
        playSeconds += dt
        saveDebt += dt

        var decayed = false
        while (playSeconds >= SECONDS_PER_HEART) {
            playSeconds -= SECONDS_PER_HEART
            decayOnce()
            decayed = true
        }
        if (evolveSecondsLeft > 0f) evolveSecondsLeft = (evolveSecondsLeft - dt).coerceAtLeast(0f)
        breedSeconds += dt
        while (breedSeconds >= BREED_EVERY) {
            breedSeconds -= BREED_EVERY
            if (Math.random() < BREED_CHANCE && breed() != null) decayed = true   // 태어나면 바로 저장
        }
        if (decayed || saveDebt >= SAVE_EVERY_SECONDS) {
            saveDebt = 0f
            save()
        }
    }

    /** 하트 하나가 닳는 순간 — 살아 있는 냥이 모두가 조금씩 배고파지고 지저분해진다 */
    private fun decayOnce() {
        for (cat in roster) {
            if (cat.gone) continue
            if (cat.inDaycare) { cat.fullness = CARE_MAX; continue }   // 놀이방에서 밥을 줘서 늘 배부르다
            cat.fullness = drop(cat.fullness, DECAY_FULLNESS)
            cat.cleanliness = drop(cat.cleanliness, DECAY_CLEANLINESS)
            cat.joy = drop(cat.joy, DECAY_JOY)
            // 하트가 다 떨어지면 배고픔을 견디지 못하고 떠난다
            if (cat.fullness <= 0) {
                cat.hasLeft = true
                cat.inParty = false
                cat.hasArmor = false
                pendingLeaveNotices.add(cat.name)
            }
        }
    }

    /** 살아 있는 냥이 둘을 골라 새끼를 낳는다. 둘이 안 되면 null */
    private fun breed(): TeamCat? {
        // 수컷과 암컷이 한 마리씩 있어야 새끼를 낳는다 (성별 2026-10-09)
        // 중성화한 냥이는 새끼를 낳지 않는다
        val males = living.filter { it.male && it.surgeries == 0 }
        val females = living.filter { !it.male && it.surgeries == 0 }
        if (males.isEmpty() || females.isEmpty()) return null
        val a = males.random()
        val b = females.random()
        val like = if (Math.random() < 0.5) a else b           // 닮는 쪽
        val coat = if (like.kind == AllyCat.Kind.NORMAL) like.coat else CatCoat.CHEESE
        val kid = TeamCat(
            id = nextId++,
            kind = like.kind,
            name = autoName(like.kind, coat),
            inParty = false,
            seen = false,
            coat = coat,
            isKitten = true,
            parents = "${a.name} × ${b.name}"
        )
        roster.add(kid)
        pendingBirthNotices.add("${a.name}와(과) ${b.name} 사이에서 ${kid.name}이(가) 태어났다냥!")
        return kid
    }

    /** 새로 태어난 새끼 소식 — 로비에서 한 번 알려 준 뒤 비운다 */
    fun takePendingBirthNotices(): List<String> {
        if (pendingBirthNotices.isEmpty()) return emptyList()
        val out = pendingBirthNotices.toList()
        pendingBirthNotices.clear()
        return out
    }

    /** 다음 하트가 닳기까지 남은 놀이 시간(초) */
    fun secondsToNextHeart(): Int =
        (SECONDS_PER_HEART - playSeconds).coerceAtLeast(0f).toInt()

    /** 앱이 내려갈 때 쌓인 놀이 시간을 저장한다 */
    fun flush() {
        if (prefs == null) return
        saveDebt = 0f
        save()
    }

    /** 다 먹이기 (상점, 대표님 2026-10-10): 내 팀 냥이(떠나거나 무덤인 냥이 빼고) 모두 포만감이 가득 찬다. 먹인 마릿수 */
    fun feedAll(): Int {
        val cats = roster.filter { !it.gone }
        for (c in cats) c.fullness = CARE_MAX
        if (cats.isNotEmpty()) save()
        return cats.size
    }

    /** 포만감을 하트 개수로 (모자란 하트는 버림) */
    fun heartsOf(cat: TeamCat) = (cat.fullness / FULLNESS_PER_HEART).coerceIn(0, FULLNESS_HEARTS)

    /** 배고파서 떠난 냥이 수 */
    fun leftCount() = roster.count { it.hasLeft }

    /** 떠난 냥이 이름들 — 로비에서 한 번 알려 준 뒤 비운다 */
    fun takePendingLeaveNotices(): List<String> {
        if (pendingLeaveNotices.isEmpty()) return emptyList()
        val out = pendingLeaveNotices.toList()
        pendingLeaveNotices.clear()
        return out
    }

    private fun drop(value: Int, amount: Int) = (value - amount).coerceAtLeast(0)
    private fun bump(value: Int, amount: Int) = (value + amount).coerceIn(0, CARE_MAX)

    // ===== 성장 수치 =====

    /** 레벨에 따른 최대 체력 */
    fun maxHpFor(level: Int) = 20 + 5 * (level.coerceAtLeast(1) - 1)

    /** 레벨에 따라 더해지는 공격력 */
    fun damageBonusFor(level: Int) = 2 * (level.coerceAtLeast(1) - 1)

    /** 다음 레벨까지 필요한 경험치 */
    fun expToNext(level: Int) = 50 + 25 * (level.coerceAtLeast(1) - 1)

    /** 포만감·청결도·즐거움의 평균 — 얼마나 잘 돌봤는가 */
    fun careScore(cat: TeamCat) = (cat.fullness + cat.cleanliness + cat.joy) / 3

    /** 잘 돌본 냥이는 전투에서 더 튼튼하다 (최대 +25%) */
    fun careMultiplier(cat: TeamCat) = 1f + 0.25f * careScore(cat) / 100f

    // ===== 명단 =====

    /**
     * 구출한 냥이를 팀에 넣는다.
     * 바로 싸우지는 않는다 — 관리 화면에서 직접 출전을 켜 줘야 한다.
     */
    fun recruit(kind: AllyCat.Kind, coat: CatCoat = CatCoat.CHEESE, isKitten: Boolean = false,
                male: Boolean = Math.random() < 0.5, parents: String = ""): TeamCat {
        val cat = TeamCat(
            id = nextId++,
            kind = kind,
            name = autoName(kind, coat),
            inParty = false,
            seen = false,
            coat = if (kind == AllyCat.Kind.NORMAL) coat else CatCoat.CHEESE,
            isKitten = isKitten,
            male = male,
            parents = parents
        )
        roster.add(cat)
        save()
        return cat
    }

    /**
     * "그린냥이 3호" 처럼 종류마다 번호를 붙인다.
     * 번호표는 따로 쌓이므로 무덤이나 보내준 냥이의 번호가 다시 나오지 않는다.
     */
    private fun autoName(kind: AllyCat.Kind, coat: CatCoat = CatCoat.CHEESE): String {
        val next = (nameCounts[kind] ?: 0) + 1
        nameCounts[kind] = next
        // 치즈냥이는 무늬 이름으로 부른다 (삼색이 2호 …). 노랑이는 그대로 치즈냥이
        val base = if (kind == AllyCat.Kind.NORMAL && coat != CatCoat.CHEESE) coat.label else baseNameOf(kind)
        return "$base ${next}호"
    }

    /** 냥이 이름을 새로 지어 준다 (최대 8자). 빈 이름은 거절 */
    fun rename(id: Int, name: String): Boolean {
        val cat = cat(id) ?: return false
        if (cat.gone) return false
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        cat.name = if (trimmed.length > NAME_MAX) trimmed.substring(0, NAME_MAX) else trimmed
        save()
        return true
    }

    /** 전장에서 쓰러져 돌아오지 못했다 — 칸에 무덤이 남는다 */
    fun markDead(id: Int, stage: Int) {
        val cat = cat(id) ?: return
        if (cat.isDead) return
        cat.isDead = true
        cat.inParty = false
        cat.hasArmor = false
        cat.diedAtStage = stage
        save()
    }

    /**
     * 무덤이나 떠난 냥이의 칸을 비운다 (보내주기).
     * 살아 있는 냥이는 보낼 수 없다.
     */
    fun release(id: Int): Boolean {
        val cat = cat(id) ?: return false
        if (!cat.gone) return false
        roster.remove(cat)
        petCooldown.remove(id)
        save()
        return true
    }

    /** 갑옷을 입힌다 — 가진 갑옷 하나를 쓴다 */
    fun equipArmor(id: Int): CareResult? {
        val cat = cat(id) ?: return null
        if (cat.gone) return null
        if (cat.hasArmor) return CareResult(false, "이미 갑옷을 입고 있다냥!")
        if (GameProgress.itemCount("cat_armor") <= 0) {
            return CareResult(false, "갑옷이 없다냥! 고양이 상점에서 사 오자")
        }
        if (!GameProgress.consumeItem("cat_armor")) {
            return CareResult(false, "갑옷이 없다냥! 고양이 상점에서 사 오자")
        }
        cat.hasArmor = true
        save()
        return CareResult(true, "갑옷 장착! 피해 -60%")
    }

    private fun baseNameOf(kind: AllyCat.Kind) = when (kind) {
        AllyCat.Kind.GREEN -> "그린냥이"
        AllyCat.Kind.BLUE -> "파랑냥이"
        AllyCat.Kind.NORMAL -> "치즈냥이"
        AllyCat.Kind.FLYING -> "날아냥이"
        AllyCat.Kind.GUN -> "총냥이"
        AllyCat.Kind.BOAT -> "배냥이"
        AllyCat.Kind.HAMMER -> "해머냥이"
        AllyCat.Kind.THIEF -> "도둑냥이"
        AllyCat.Kind.LASER -> "레이저냥이"
        AllyCat.Kind.RED -> "빨강냥이"
        AllyCat.Kind.ORANGE -> "주황냥이"
        AllyCat.Kind.YELLOW -> "노랑냥이"
        AllyCat.Kind.BEE -> "벌냥이"
        AllyCat.Kind.FLOWER -> "꽃냥이"
        AllyCat.Kind.MUD -> "머드냥이"
        AllyCat.Kind.HONEY -> "꿀냥이"
        AllyCat.Kind.FLUFFY -> "털뭉냥이"
        AllyCat.Kind.ANVIL -> "모루냥이"
        AllyCat.Kind.POOP -> "똥냥이"
        AllyCat.Kind.VINE -> "덩굴냥이"
    }

    fun cat(id: Int): TeamCat? = roster.firstOrNull { it.id == id }

    // ===== 동물 병원 (상점가 6번 천막, 대표님 2026-10-09 「루비를 내고 수술하면 고양이가 더 빨라져」) =====
    /** 수술 종류 (대표님 2026-10-09): 중성화 수술 = 빨라진다 · HP 수술 = 피가 5 늘어난다. 값은 57루비(대표님).
     *  중성화는 한 번만·+20%, HP 수술은 최대 5번 — Claude 판단 */
    const val SURGERY_PRICE = 57   // 대표님 2026-10-09 「루비는 57」
    const val SURGERY_SPEED = 0.2f
    const val SURGERY_MAX = 1
    const val HP_SURGERY_HP = 5
    const val HP_SURGERY_MAX = 5

    /** 달래기 수술 (대표님 2026-10-09): 3번을 해야지만 공격력이 4 늘어난다. 값은 다른 수술과 같게(Claude 판단) */
    const val SOOTHE_NEED = 3
    const val SOOTHE_ATTACK = 4

    /** 달래기 — 3번째에 공격력 +4 (attackBonus 에 영구로). 끝났거나 루비가 모자라면 false */
    fun soothe(catId: Int): Boolean {
        val c = cat(catId) ?: return false
        if (c.gone || c.soothes >= SOOTHE_NEED) return false
        if (!GameProgress.spendRubies(SURGERY_PRICE)) return false
        c.soothes++
        if (c.soothes == SOOTHE_NEED) c.attackBonus += SOOTHE_ATTACK
        save()
        return true
    }

    /** 투기장 (대표님 2026-10-09): 밀짚 가짜 냥이를 30초 때릴 때마다 공격력 +3 (attackBonus 에 영구로, 무료·끝 없음은 Claude 판단) */
    const val ARENA_SECONDS = 30f
    const val ARENA_ATTACK = 3

    fun arenaTrain(catId: Int, amount: Int = ARENA_ATTACK): Boolean {
        val c = cat(catId) ?: return false
        if (c.gone) return false
        // 투기장 Lv 가 아주 높으면 숫자가 넘치지 않게 멈춘다
        c.attackBonus = (c.attackBonus.toLong() + amount).coerceAtMost(Int.MAX_VALUE / 2L).toInt()
        save()
        return true
    }

    /** HP 수술로 늘어난 체력 */
    fun hpBonusOf(c: TeamCat) = HP_SURGERY_HP * c.hpSurgeries + c.hpTraining

    /** 체력 훈련장: 러닝머신을 30초 달릴 때마다 체력 +5 (영구, 값·끝 없음은 Claude 판단) */
    const val HP_TRAIN_SECONDS = 30f
    const val HP_TRAIN_GAIN = 5

    fun trainHp(catId: Int): Boolean {
        val c = cat(catId) ?: return false
        if (c.gone) return false
        c.hpTraining = (c.hpTraining + HP_TRAIN_GAIN).coerceAtMost(1_000_000)
        save()
        return true
    }

    /** HP 수술 — 체력 +5 */
    fun operateHp(catId: Int): Boolean {
        val c = cat(catId) ?: return false
        if (c.gone || c.hpSurgeries >= HP_SURGERY_MAX) return false
        if (!GameProgress.spendRubies(SURGERY_PRICE)) return false
        c.hpSurgeries++
        save()
        return true
    }

    /** 이 냥이의 이동 속도 배율 (수술 한 번에 +20%) */
    fun speedMultiplier(c: TeamCat) = 1f + SURGERY_SPEED * c.surgeries

    /** 수술 — 루비를 내고 그 냥이가 빨라진다. 이미 최대거나 루비가 모자라면 false */
    fun operate(catId: Int): Boolean {
        val c = cat(catId) ?: return false
        if (c.gone || c.surgeries >= SURGERY_MAX) return false
        if (!GameProgress.spendRubies(SURGERY_PRICE)) return false
        c.surgeries++
        save()
        return true
    }

    /** 이 냥이에게 기술을 가르친다 — 루비를 내고 그 냥이만 배운다. 이미 배웠거나 루비가 모자라면 false */
    fun teachSkill(catId: Int, skillId: String): Boolean {
        val c = cat(catId) ?: return false
        val skill = CatSkills.byId(skillId) ?: return false
        if (c.gone || skillId in c.skills) return false
        if (!GameProgress.spendRubies(skill.price)) return false
        c.skills.add(skillId)
        save()
        return true
    }

    /** 출전 명단에 넣고 빼기 — 3마리를 넘기려 하면 false */
    fun setInParty(id: Int, on: Boolean): Boolean {
        val cat = cat(id) ?: return false
        if (cat.gone) return false       // 무덤과 떠난 냥이는 출전할 수 없다
        if (on && !cat.inParty && roster.filter { !it.gone }.count { it.inParty } >= PARTY_MAX) return false
        cat.inParty = on
        save()
        return true
    }

    // ===== 돌보기 =====

    /** 간식별 경험치 */
    private val foodExp = mapOf(
        "churu" to 30,
        "tuna_can" to 40,
        "feed" to 25,
        "catnip" to 35,
        "milk" to 15,        // 상점가 새 간식 (2026-10-09, Claude 판단)
        "salmon" to 45,
        "cat_cake" to 80,
        // 낚시터에서 잡은 물고기도 먹이 (대표님 2026-10-10 「낚시하면 물고기가 잡혀, 그걸로 고양이 먹이를 줄 수 있어」 — 값은 Claude 판단)
        "fish_small" to 20,
        "fish_big" to 45,
        "fish_gold" to 100,
    )

    /** 간식별 포만감 */
    // 간식은 하트를 통째로 채워 준다 (하트 하나 = 20)
    private val foodFullness = mapOf(
        "churu" to 20,
        "tuna_can" to 40,
        "feed" to 40,
        "catnip" to 20,
        "milk" to 20,
        "salmon" to 40,
        "cat_cake" to 60,
        "fish_small" to 20,
        "fish_big" to 40,
        "fish_gold" to 100,
    )

    /** 먹이로 줄 수 있는 물건 목록 (간식 고르기 줄) */
    val foodIds: List<String> get() = foodExp.keys.toList()

    fun expOfFood(itemId: String) = foodExp[itemId] ?: 0

    fun fullnessOfFood(itemId: String) = foodFullness[itemId] ?: 0

    fun isFood(itemId: String) = itemId in foodExp

    /** 쓰다듬기 — 애정이 쌓인다. 연타하면(0.8초) null */
    fun pet(id: Int): CareResult? {
        val cat = cat(id) ?: return null
        if (cat.gone) return null
        val now = System.currentTimeMillis()
        val last = petCooldown[id] ?: 0L
        if (now - last < PET_COOLDOWN_MS) return null
        petCooldown[id] = now
        cat.affection = bump(cat.affection, 6)
        cat.joy = bump(cat.joy, 4)
        save()
        return CareResult(true, "쓰담쓰담! 애정 +6 · 즐거움 +4")
    }

    /**
     * 놀아주기 — 장난감이 있으면 훨씬 신난다 (장난감은 없어지지 않는다).
     * 뛰어노느라 포만감은 조금 준다.
     */
    fun play(id: Int): CareResult? {
        val cat = cat(id) ?: return null
        if (cat.gone) return null
        // 가진 장난감 중 가장 좋은 것 + 다른 장난감마다 3씩 더 (최대 40) — 새 장난감 2026-10-09
        val toyJoy = linkedMapOf("laser_pointer" to 28, "fish_toy" to 25, "feather_wand" to 22, "mouse_toy" to 20, "ball" to 18)
        val owned = toyJoy.filterKeys { GameProgress.itemCount(it) > 0 }
        val gain: Int
        val withWhat: String
        if (owned.isEmpty()) { gain = 8; withWhat = "" }
        else {
            gain = (owned.values.max() + 3 * (owned.size - 1)).coerceAtMost(40)
            withWhat = " (" + owned.keys.joinToString(" + ") { id -> ShopCatalog.items.first { it.id == id }.name } + ")"
        }
        cat.joy = bump(cat.joy, gain)
        cat.fullness = drop(cat.fullness, 5)
        cat.affection = bump(cat.affection, 3)
        save()
        return CareResult(true, "즐거움 +$gain$withWhat")
    }

    /**
     * 간식 하나를 먹여 경험치와 포만감을 준다.
     * 배가 이미 가득하면 먹이지 않고(간식도 쓰지 않고) 안내만 돌려준다.
     */
    fun feed(id: Int, itemId: String): CareResult? {
        val cat = cat(id) ?: return null
        if (cat.gone) return null
        val expGain = foodExp[itemId] ?: return null
        if (cat.fullness >= CARE_MAX) return CareResult(false, "배불러서 더 못 먹는다냥!")
        if (GameProgress.itemCount(itemId) <= 0) {
            return CareResult(false, "간식이 없다냥! 고양이 상점에서 사 오자")
        }
        if (!GameProgress.consumeItem(itemId)) {
            return CareResult(false, "간식이 없다냥! 고양이 상점에서 사 오자")
        }
        val beforeLevel = cat.level
        gainExp(cat, expGain)
        val fullGain = foodFullness[itemId] ?: 0
        cat.fullness = bump(cat.fullness, fullGain)
        cat.affection = bump(cat.affection, 2)
        save()
        val leveled = cat.level > beforeLevel
        // 새끼가 충분히 자라면 어른 냥이가 된다
        val grewUp = cat.isKitten && cat.level >= GROW_UP_LEVEL
        if (grewUp) { cat.isKitten = false; save() }
        val msg = when {
            grewUp -> "레벨 업! Lv ${cat.level} · 다 자랐다냥!"
            leveled -> "레벨 업! Lv ${cat.level}" + if (cat.isKitten) " (Lv $GROW_UP_LEVEL 이면 다 자라요)" else ""
            else -> "냠냠! 포만감 +$fullGain · +$expGain exp"
        }
        return CareResult(true, msg, leveled)
    }

    /** 빗질하기 — 빗이 있어야 한다 (빗은 없어지지 않는다). 물그릇까지 있으면 더 뽀얗다. */
    fun groom(id: Int): CareResult? {
        val cat = cat(id) ?: return null
        if (cat.gone) return null
        if (GameProgress.itemCount("brush") <= 0) {
            return CareResult(false, "빗이 없다냥! 고양이 상점에서 사 오자")
        }
        var gain = 30
        var extra = ""
        if (GameProgress.itemCount("water_bowl") > 0) { gain += 5; extra = " (물그릇 +5)" }
        cat.cleanliness = bump(cat.cleanliness, gain)
        cat.affection = bump(cat.affection, 2)
        save()
        return CareResult(true, "청결도 +$gain$extra")
    }

    /**
     * 부활 약으로 무덤이 된 냥이를 되살린다.
     * 레벨·경험치·애정은 그대로 두고, 배와 몸은 새로 챙겨 준다.
     * 출전과 갑옷은 풀린 상태로 돌아온다.
     */
    fun revive(id: Int): Boolean {
        val cat = cat(id) ?: return false
        if (!cat.isDead) return false
        cat.isDead = false
        cat.diedAtStage = 0
        cat.fullness = CARE_MAX
        cat.cleanliness = 60
        cat.joy = 60
        cat.inParty = false
        cat.hasArmor = false
        cat.lastCareAt = System.currentTimeMillis()
        save()
        return true
    }

    /** 지금까지 구한(명단에 오른) 냥이 수 — 무덤·떠난 냥이도 센다 (미션용) */
    fun rescuedCount(): Int = roster.size
    /** 팀에서 가장 높은 레벨 */
    fun maxLevel(): Int = roster.maxOfOrNull { it.level } ?: 0

    /** 무덤이 된 팀 냥이 수 */
    fun deadCount(): Int = roster.count { it.isDead }

    /** '다부활' — 무덤이 된 팀 냥이를 전부 되살린다. 되살린 마릿수를 돌려준다 (대표님 2026-09-20) */
    fun reviveAll(): Int {
        val dead = roster.filter { it.isDead }
        for (c in dead) revive(c.id)
        return dead.size
    }

    /** 'Lv업' 아이템 한 개를 써서 공격력을 영구히 올린다 */
    fun raiseAttack(id: Int): CareResult? {
        val cat = cat(id) ?: return null
        if (cat.gone) return null
        if (GameProgress.itemCount("attack_up") <= 0) {
            return CareResult(false, "공격력 Lv업 아이템이 없다냥! (고양이 상점 ${ShopCatalog.priceOf("attack_up")}루비)")
        }
        if (!GameProgress.consumeItem("attack_up")) {
            return CareResult(false, "공격력 Lv업 아이템이 없다냥! (고양이 상점 ${ShopCatalog.priceOf("attack_up")}루비)")
        }
        cat.attackBonus += ATTACK_UP_AMOUNT
        save()
        return CareResult(true, "${cat.name} 공격력 +$ATTACK_UP_AMOUNT!")
    }

    /** 기본 공격력 (그린냥이는 슬라임을 뱉어 더 세다) */
    fun baseAttackOf(kind: AllyCat.Kind) = if (kind == AllyCat.Kind.GREEN) 10 else 5

    /** 화면에 보여 줄 총 공격력 */
    fun totalAttackOf(cat: TeamCat) =
        baseAttackOf(cat.kind) + damageBonusFor(cat.level) + cat.attackBonus

    /** 되살릴 수 있는 무덤이 하나라도 있는가 */
    fun hasGrave() = roster.any { it.isDead }

    /** 갑옷을 벗긴다 — 벗긴 갑옷은 그대로 가방으로 돌아온다 */
    fun unequipArmor(id: Int): CareResult? {
        val cat = cat(id) ?: return null
        if (cat.gone) return null
        if (!cat.hasArmor) return CareResult(false, "갑옷을 입고 있지 않다냥")
        cat.hasArmor = false
        GameProgress.addItem("cat_armor", 1)
        save()
        return CareResult(true, "갑옷을 벗겼다냥")
    }

    /** 경험치를 넣고 넘치면 레벨을 올린다 (남은 경험치는 다음 레벨로 이어진다) */
    private fun gainExp(cat: TeamCat, amount: Int) {
        if (cat.level >= MAX_LEVEL) { cat.exp = 0; return }
        cat.exp += amount
        while (cat.level < MAX_LEVEL && cat.exp >= expToNext(cat.level)) {
            cat.exp -= expToNext(cat.level)
            cat.level++
        }
        if (cat.level >= MAX_LEVEL) cat.exp = 0
    }

    // ===== 로비 알림 =====

    /** 아직 관리 화면에서 보지 못한 새 냥이가 있는가 */
    fun hasNewCat() = roster.any { !it.seen && !it.gone }

    /** 배고프거나 지저분해서 손이 필요한 냥이가 있는가 */
    fun needsCare() = roster.any { !it.gone && careScore(it) < 30 }

    /** 관리 화면을 닫을 때 — 지금까지의 냥이를 전부 '확인함'으로 */
    fun markSeen() {
        if (roster.none { !it.seen }) return
        roster.forEach { it.seen = true }
        save()
    }
}
