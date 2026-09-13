package com.jamcat.nayngapo.engine

data class InputState(
    var moveLeft: Boolean = false,
    var moveRight: Boolean = false,
    var moveUp: Boolean = false,   // 지상에서는 점프 별칭, 하늘에서는 상승
    var moveDown: Boolean = false, // 하늘 스테이지 하강
    var jump: Boolean = false,
    var attack: Boolean = false,
    var special: Boolean = false,
    /** 팀 냥이 지시 바꾸기 — 한 번 누를 때 한 번만 (processInput이 지운다) */
    var cycleOrder: Boolean = false,
    /** 쓰려고 누른 고양이 기술 id — 한 번만 (processInput이 지운다) */
    var useSkill: String? = null,
    /** 쓰려고 누른 인간 기술 id — 한 번만 (processInput이 지운다) */
    var useHumanSkill: String? = null
)
