package com.jamcat.nayngapo.engine

data class InputState(
    var moveLeft: Boolean = false,
    var moveRight: Boolean = false,
    var moveUp: Boolean = false,
    var moveDown: Boolean = false,
    var jump: Boolean = false,
    var attack: Boolean = false,
    var special: Boolean = false
)
