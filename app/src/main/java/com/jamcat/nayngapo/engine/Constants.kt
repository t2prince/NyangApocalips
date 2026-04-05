package com.jamcat.nayngapo.engine

object Constants {
    const val REF_WIDTH = 1920f
    const val REF_HEIGHT = 1080f

    // Play area (tile floor region)
    const val PLAY_AREA_TOP = 100f
    const val PLAY_AREA_BOTTOM = 950f
    const val PLAY_AREA_LEFT = 0f
    const val PLAY_AREA_RIGHT = 3000f

    const val TILE_SIZE = 80f

    // Player
    const val PLAYER_SPEED = 320f
    const val JUMP_GRAVITY = 1800f
    const val JUMP_VELOCITY = -600f
    const val PLAYER_WIDTH = 40f
    const val PLAYER_HEIGHT = 70f
    const val INVINCIBILITY_DURATION = 1.5f
    const val ATTACK_DURATION = 0.25f
    const val ATTACK_RANGE = 60f
    const val SPECIAL_MAX = 100f
    const val SPECIAL_GAIN_PER_KILL = 12f
    const val SPECIAL_GAIN_PER_HIT = 5f

    // HUD
    const val HUD_HEIGHT = 60f
    const val HUD_COLOR = 0xFF333333.toInt()
}
