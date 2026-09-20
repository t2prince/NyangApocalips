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

    val all: List<Mission> = listOf(
        Mission("stage1", "첫 걸음", "1스테이지 클리어", 1, 5) { if (GameProgress.isCleared(1)) 1 else 0 },
        Mission("stage5", "초원 절반", "5스테이지 클리어", 1, 15) { if (GameProgress.isCleared(5)) 1 else 0 },
        Mission("stage10", "초원 정복", "10스테이지 보스(순간냥이) 처치", 1, 30) { if (GameProgress.isCleared(10)) 1 else 0 },
        Mission("stage11", "하늘 정복", "11스테이지(하늘) 클리어", 1, 40) { if (GameProgress.isCleared(11)) 1 else 0 },
        Mission("stage15", "정글 깊숙이", "15스테이지 클리어", 1, 50) { if (GameProgress.isCleared(15)) 1 else 0 },
        Mission("stage20", "정글 정복", "20스테이지 마법사냥이 처치", 1, 100) { if (GameProgress.isCleared(20)) 1 else 0 },
        Mission("kill50", "냥이 사냥꾼", "냥이 50마리 처치", 50, 10) { GameProgress.totalKills },
        Mission("kill200", "냥이 사냥 명수", "냥이 200마리 처치", 200, 30) { GameProgress.totalKills },
        Mission("kill1000", "냥이 사냥 전설", "냥이 1000마리 처치", 1000, 100) { GameProgress.totalKills },
        Mission("thief5", "도둑 잡기", "도둑냥이 5마리 처치", 5, 20) { GameProgress.thiefKills },
        Mission("rescue5", "구조대", "냥이 5마리 구출", 5, 15) { CatTeam.rescuedCount() },
        Mission("rescue15", "냥이 대가족", "냥이 15마리 구출", 15, 40) { CatTeam.rescuedCount() },
        Mission("dex10", "도감 수집가", "도감 10종 등록", 10, 20) { GameProgress.discoveredCount() },
        Mission("dexall", "도감 완성", "도감 ${CatDex.entries.size}종 전부 등록", CatDex.entries.size, 100) { GameProgress.discoveredCount() },
        Mission("tower", "냥이 아파트", "캣타워 구매", 1, 10) { if (GameProgress.itemCount("cat_tower") > 0) 1 else 0 },
        Mission("lv10", "냥이 키우기", "팀 냥이 하나를 Lv 10까지", 1, 25) { if (CatTeam.maxLevel() >= 10) 1 else 0 },
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
