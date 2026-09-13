package com.jamcat.nayngapo.engine

/** 루비로 배우는 기술 목록 — 고양이 기술과 인간 기술이 같은 틀을 쓴다 */
interface SkillBook {
    val all: List<CatSkills.Skill>
    fun isLearned(id: String): Boolean
    fun learn(id: String): Boolean
    fun learnedCount(): Int
}
