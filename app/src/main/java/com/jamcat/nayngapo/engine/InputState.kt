package com.jamcat.nayngapo.engine

data class InputState(
    var moveLeft: Boolean = false,
    var moveRight: Boolean = false,
    var moveUp: Boolean = false,   // 점프 별칭으로 사용
    var jump: Boolean = false,
    var attack: Boolean = false,
    var special: Boolean = false
)
