package com.jamcat.nayngapo.engine

import android.content.SharedPreferences

/**
 * 고양이 기술 (로비 '고양이 기술').
 * 루비로 한 번 배우면 계속 남고, 켜고 끌 수 있다. 켜진 기술은 출전한 팀 냥이에게 적용된다.
 */
object CatSkills : SkillBook {

    data class Skill(
        val id: String,
        val name: String,
        /** 배우는 값 (루비) */
        val price: Int,
        val desc: String,
        /** 기술 위력 */
        val power: Int,
        /** 다시 쓸 수 있게 되기까지 걸리는 시간(초) */
        val cooldown: Float,
    ) {
        /** 그림 파일 이름 규칙: drawable-nodpi/skill_<id>.png */
        val iconName: String get() = "skill_$id"
    }

    /**
     * 기술 목록 — 아직 정해진 기술이 없다.
     * 여기에 Skill을 넣으면 배우기·켜고 끄기·저장이 그대로 다시 살아난다.
     */
    override val all: List<Skill> = listOf(
        Skill(
            "tornado", "토네이도", 210,
            "출전한 냥이가 토네이도를 일으켜 화면의 적을 공격 (위력 90)",
            power = 90, cooldown = 20f
        ),
        Skill(
            "rapid_fire", "연속발사", 99,
            "출전한 냥이가 발바닥 탄을 연속 발사 (대미지 49 × 6발)",
            power = 49, cooldown = 12f
        ),
        Skill(
            "jelly_paw", "젤리발바닥", 36,
            "출전한 냥이가 하늘에서 젤리를 내리게 한다 (공격력 10)",
            power = 10, cooldown = 10f
        ),
    )

    fun byId(id: String): Skill? = all.firstOrNull { it.id == id }

    private const val KEY_LEARNED = "skills_learned"
    private const val KEY_ENABLED = "skills_enabled"

    private val learned = mutableSetOf<String>()
    private val enabled = mutableSetOf<String>()
    private var prefs: SharedPreferences? = null

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        learned.clear()
        enabled.clear()
        learned.addAll(prefs.getStringSet(KEY_LEARNED, emptySet()) ?: emptySet())
        enabled.addAll(prefs.getStringSet(KEY_ENABLED, emptySet()) ?: emptySet())
        // 목록에서 사라진 기술이 저장에 남아 있으면 흘려보낸다
        learned.retainAll { byId(it) != null }
        enabled.retainAll { it in learned }
    }

    private fun save() {
        prefs?.edit()
            ?.putStringSet(KEY_LEARNED, learned.toSet())
            ?.putStringSet(KEY_ENABLED, enabled.toSet())
            ?.apply()
    }

    override fun isLearned(id: String) = id in learned
    fun isEnabled(id: String) = id in enabled

    /** 지금 전투에 실제로 걸리는 기술인가 */
    fun isActive(id: String) = id in learned && id in enabled

    override fun learnedCount() = learned.size

    /** 루비를 내고 기술을 배운다 — 이미 배웠거나 루비가 모자라면 false */
    override fun learn(id: String): Boolean {
        val skill = byId(id) ?: return false
        if (id in learned) return false
        if (!GameProgress.spendRubies(skill.price)) return false
        learned.add(id)
        enabled.add(id)          // 배우면 바로 켜진다
        save()
        return true
    }

    /** 배운 기술을 켜고 끈다 — 켜졌으면 true */
    fun toggle(id: String): Boolean {
        if (id !in learned) return false
        val on = if (id in enabled) { enabled.remove(id); false } else { enabled.add(id); true }
        save()
        return on
    }

    // ===== 전투에 넘기는 묶음 =====

    /** 스테이지 시작할 때 한 번 읽어 party 냥이에게 넘기는 값들 */
    data class Loadout(
        val meleeMul: Float = 1f,
        val spitCooldownMul: Float = 1f,
        val speedMul: Float = 1f,
        val nineLives: Boolean = false,
        val hiss: Boolean = false,
        val purr: Boolean = false,
    ) {
        companion object {
            /** 아무 기술도 없는 상태 */
            val NONE = Loadout()
        }
    }

    fun loadout() = Loadout(
        meleeMul = if (isActive("punch")) 1.5f else 1f,
        spitCooldownMul = if (isActive("slime")) 0.5f else 1f,
        speedMul = if (isActive("sprint")) 1.3f else 1f,
        nineLives = isActive("nine_lives"),
        hiss = isActive("hiss"),
        purr = isActive("purr"),
    )
}
