package com.jamcat.nayngapo.engine

import android.content.SharedPreferences

/**
 * 인간 기술 (로비 아래 줄 '기술').
 * 인간이 전투 중에 직접 쓰는 기술 — 출전한 냥이가 쓰는 고양이 기술(CatSkills)과는 따로 간다.
 * 루비로 한 번 배우면 계속 남는다.
 */
object HumanSkills : SkillBook {

    override val all: List<CatSkills.Skill> = listOf(
        CatSkills.Skill(
            "stone_throw", "돌던지기", 100,
            "인간이 돌을 던진다 (공격력 5)",
            power = 5, cooldown = 2f
        ),
        CatSkills.Skill(
            "fire_punch", "파이어 펀치", 30,
            "불타는 주먹 (공격력 11) · 맞은 적은 10초 뒤 화상 대미지 1",
            power = 11, cooldown = 0f   // 파이어 펀치는 쿨타임이 없다
        ),
        CatSkills.Skill(
            "missile", "미사일", 190,
            "가장 가까운 적을 쫓아가 터지는 미사일 (공격력 12)",
            power = 12, cooldown = 6f
        ),
    )

    fun byId(id: String): CatSkills.Skill? = all.firstOrNull { it.id == id }

    /** 그림 파일 이름 규칙: drawable-nodpi/hskill_<id>.png */
    fun iconName(id: String) = "hskill_$id"

    private const val KEY_LEARNED = "hskills_learned"

    private val learned = mutableSetOf<String>()
    private var prefs: SharedPreferences? = null

    fun init(prefs: SharedPreferences) {
        this.prefs = prefs
        learned.clear()
        learned.addAll(prefs.getStringSet(KEY_LEARNED, emptySet()) ?: emptySet())
        // 목록에서 사라진 기술이 저장에 남아 있으면 흘려보낸다
        learned.retainAll { byId(it) != null }
    }

    private fun save() {
        prefs?.edit()?.putStringSet(KEY_LEARNED, learned.toSet())?.apply()
    }

    override fun isLearned(id: String) = id in learned

    override fun learnedCount() = learned.size

    /** 루비를 내고 기술을 배운다 — 이미 배웠거나 루비가 모자라면 false */
    override fun learn(id: String): Boolean {
        val skill = byId(id) ?: return false
        if (id in learned) return false
        if (!GameProgress.spendRubies(skill.price)) return false
        learned.add(id)
        save()
        return true
    }
}
