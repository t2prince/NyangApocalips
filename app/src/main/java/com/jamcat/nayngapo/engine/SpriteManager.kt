package com.jamcat.nayngapo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.jamcat.nayngapo.R

object SpriteManager {
    lateinit var playerIdle: Bitmap
    lateinit var playerPunch: Bitmap
    lateinit var playerKick: Bitmap
    lateinit var normalCat: Bitmap
    lateinit var thiefCat: Bitmap
    lateinit var laserCat: Bitmap
    lateinit var bossHumanCat: Bitmap
    lateinit var bossEvilCat: Bitmap
    lateinit var bossWizardCat: Bitmap
    lateinit var prison: Bitmap
    lateinit var hitEffect: Bitmap
    lateinit var heart: Bitmap
    lateinit var tile: Bitmap
    lateinit var tileDark: Bitmap

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val res = context.resources

        playerIdle = load(res, R.drawable.sprite_player, 80, 128)
        playerPunch = load(res, R.drawable.sprite_player_punch, 100, 128)
        playerKick = load(res, R.drawable.sprite_player_kick, 100, 128)
        normalCat = load(res, R.drawable.sprite_normal_cat, 70, 70)
        thiefCat = load(res, R.drawable.sprite_thief_cat, 75, 75)
        laserCat = load(res, R.drawable.sprite_laser_cat, 85, 85)
        bossHumanCat = load(res, R.drawable.sprite_boss_human_cat, 95, 95)
        bossEvilCat = load(res, R.drawable.sprite_boss_evil_cat, 120, 120)
        bossWizardCat = load(res, R.drawable.sprite_boss_wizard_cat, 120, 120)
        prison = load(res, R.drawable.sprite_prison, 60, 80)
        hitEffect = load(res, R.drawable.sprite_hit, 48, 48)
        heart = load(res, R.drawable.sprite_heart, 24, 24)
        tile = load(res, R.drawable.sprite_tile, 80, 80)
        tileDark = load(res, R.drawable.sprite_tile_dark, 80, 80)

        initialized = true
    }

    private fun load(res: android.content.res.Resources, resId: Int, w: Int, h: Int): Bitmap {
        val raw = BitmapFactory.decodeResource(res, resId)
        return Bitmap.createScaledBitmap(raw, w, h, true)
    }

    fun getFlipped(bitmap: Bitmap): Bitmap {
        val matrix = android.graphics.Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
