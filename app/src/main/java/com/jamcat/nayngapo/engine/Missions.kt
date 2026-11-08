package com.jamcat.nayngapo.engine

/**
 * 미션 — 로비 '미션' 칸. 해내면 루비를 받는다 (대표님 2026-09-20 「미션을 누르면 많은 미션이 있고, 수행하면 루비를 받아」).
 * 목표·보상 값은 Claude 가 정했다 (docs/GAME_RULES.md 표). 진행도는 이미 저장되는 수치에서 계산하고, 수령 여부만 따로 저장한다.
 */
object Missions {
    data class Mission(val id: String, val title: String, val desc: String, val goal: Int, val reward: Int, val progress: () -> Int) {
        val current: Int get() = progress().coerceAtMost(goal)
        val done: Boolean get() = progress() >= goal
        val claimed: Boolean get() = id in GameProgress.claimedMissions
        val claimable: Boolean get() = done && !claimed
    }

    /** 미션 목록 — 대표님 2026-09-21 「미션에 있는 걸 다 없애줘」: 전부 비웠다. 새 미션은 여기에 한 줄씩 넣는다.
     *  예) Mission("stage1", "첫 걸음", "1스테이지 클리어", 1, 5) { if (GameProgress.isCleared(1)) 1 else 0 } */
    val all: List<Mission> = listOf(
        // 대표님 2026-09-21: 이름 '살아남아라', 목적 '한 번도 죽지 않고 20스테이지 클리어'. 보상 200루비는 Claude 판단
        Mission("survive20", "살아남아라", "한 번도 죽지 않고 20스테이지 클리어", 1, 200) { if (GameProgress.stage20NoDeath) 1 else 0 },
        // 대표님 2026-11-08: 이름 '냥이 사냥수', 종목 '냥이 1000마리 처치', 보상 1000루비
        Mission("kills1000", "냥이 사냥수", "냥이 1000마리 처치", 1000, 1000) { GameProgress.totalKills },
    )

    fun hasClaimable() = all.any { it.claimable }

    /** 보상 받기 — 받았으면 루비 개수, 못 받으면 0 */
    fun claim(m: Mission): Int {
        if (!m.claimable) return 0
        GameProgress.claimMission(m.id)
        GameProgress.addRubies(m.reward)
        return m.reward
    }
}
