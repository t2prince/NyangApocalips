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

    /** 무덤까지 포함한 전체 명단 (칸 순서가 유지된다) */
    val cats: List<TeamCat> get() = roster

    /** 하늘 스테이지에 탈 수 있는, 살아 있는 날아냥이 (없으면 null) */
    fun flyingMount(): TeamCat? = roster.firstOrNull { it.kind == AllyCat.Kind.FLYING && !it.gone }

    /** 날아냥이를 한 마리라도 데리고 있는가 (11스테이지 입장 조건) */
    fun hasFlyingCat() = flyingMount() != null

    /** 아직 살아 있는 냥이들 */
    val living: List<TeamCat> get() = roster.filter { !it.gone }

    /** 이번 스테이지에 함께 나갈 냥이들 (최대 PARTY_MAX, 무덤은 빠진다) */
    val party: List<TeamCat> get() = roster.filter { it.inParty && !it.gone }.take(PARTY_MAX)

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        roster.clear()
        petCooldown.clear()
        pendingLeaveNotices.clear()
        nextId = prefs.getInt(KEY_NEXT_ID, 1)
        playSeconds = prefs.getInt(KEY_PLAY_SECONDS, 0).toFloat().coerceIn(0f, SECONDS_PER_HEART)
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
            )
        }
        val counts = JSONObject()
        for ((kind, n) in nameCounts) counts.put(kind.name, n)

        prefs?.edit()
            ?.putString(KEY_CATS, arr.toString())
            ?.putInt(KEY_NEXT_ID, nextId)
            ?.putInt(KEY_PLAY_SECONDS, playSeconds.toInt())
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
        if (decayed || saveDebt >= SAVE_EVERY_SECONDS) {
            saveDebt = 0f
            save()
        }
    }

    /** 하트 하나가 닳는 순간 — 살아 있는 냥이 모두가 조금씩 배고파지고 지저분해진다 */
    private fun decayOnce() {
        for (cat in roster) {
            if (cat.gone) continue
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

    /** 다음 하트가 닳기까지 남은 놀이 시간(초) */
    fun secondsToNextHeart(): Int =
        (SECONDS_PER_HEART - playSeconds).coerceAtLeast(0f).toInt()

    /** 앱이 내려갈 때 쌓인 놀이 시간을 저장한다 */
    fun flush() {
        if (prefs == null) return
        saveDebt = 0f
        save()
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
    fun recruit(kind: AllyCat.Kind, coat: CatCoat = CatCoat.CHEESE): TeamCat {
        val cat = TeamCat(
            id = nextId++,
            kind = kind,
            name = autoName(kind, coat),
            inParty = false,
            seen = false,
            coat = if (kind == AllyCat.Kind.NORMAL) coat else CatCoat.CHEESE
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
    }

    fun cat(id: Int): TeamCat? = roster.firstOrNull { it.id == id }

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
    )

    /** 간식별 포만감 */
    // 간식은 하트를 통째로 채워 준다 (하트 하나 = 20)
    private val foodFullness = mapOf(
        "churu" to 20,
        "tuna_can" to 40,
        "feed" to 40,
        "catnip" to 20,
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
        val rod = GameProgress.itemCount("fish_toy") > 0
        val ball = GameProgress.itemCount("ball") > 0
        val gain: Int
        val withWhat: String
        when {
            rod && ball -> { gain = 30; withWhat = " (낚싯대 장난감 + 방울 공)" }
            rod -> { gain = 25; withWhat = " (낚싯대 장난감)" }
            ball -> { gain = 18; withWhat = " (방울 공)" }
            else -> { gain = 8; withWhat = "" }
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
        val msg = if (leveled) "레벨 업! Lv ${cat.level}" else "냠냠! 포만감 +$fullGain · +$expGain exp"
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
            return CareResult(false, "공격력 Lv업 아이템이 없다냥! (고양이 상점 200루비)")
        }
        if (!GameProgress.consumeItem("attack_up")) {
            return CareResult(false, "공격력 Lv업 아이템이 없다냥! (고양이 상점 200루비)")
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
