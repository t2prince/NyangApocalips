package com.jamcat.nayngapo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.jamcat.nayngapo.R

object SpriteManager {
    lateinit var normalCat: Bitmap
    lateinit var thiefCat: Bitmap
    lateinit var laserCat: Bitmap
    lateinit var bossHumanCat: Bitmap
    lateinit var bossEvilCat: Bitmap
    lateinit var bossWizardCat: Bitmap
    lateinit var prison: Bitmap
    lateinit var heart: Bitmap
    lateinit var tile: Bitmap
    lateinit var tileDark: Bitmap
    lateinit var groundTile: Bitmap  // 만화풍 잔디+흙 지면 스트립 (가로 반복)

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val res = context.resources

        normalCat = load(res, R.drawable.sprite_normal_cat, 120, 120)
        thiefCat = load(res, R.drawable.sprite_thief_cat, 130, 130)
        laserCat = load(res, R.drawable.sprite_laser_cat, 145, 145)
        bossHumanCat = load(res, R.drawable.sprite_boss_human_cat, 160, 160)
        bossEvilCat = load(res, R.drawable.sprite_boss_evil_cat, 200, 200)
        bossWizardCat = load(res, R.drawable.sprite_boss_wizard_cat, 210, 210)
        prison = load(res, R.drawable.sprite_prison, 90, 120)
        heart = load(res, R.drawable.sprite_heart, 30, 30)
        tile = load(res, R.drawable.sprite_tile, 80, 80)
        tileDark = load(res, R.drawable.sprite_tile_dark, 80, 80)
        groundTile = BitmapFactory.decodeResource(res, R.drawable.grass_ground)   // 원본 크기, draw 시 스케일

        initialized = true
    }

    private fun load(res: android.content.res.Resources, resId: Int, w: Int, h: Int): Bitmap {
        val raw = BitmapFactory.decodeResource(res, resId)
        return Bitmap.createScaledBitmap(raw, w, h, true)
    }

    // 좌우반전 비트맵 캐시: 매 프레임 createBitmap 호출로 인한 GC 압박을 막는다
    private val flippedCache = HashMap<Bitmap, Bitmap>()

    fun getFlipped(bitmap: Bitmap): Bitmap = flippedCache.getOrPut(bitmap) {
        val matrix = android.graphics.Matrix().apply { preScale(-1f, 1f) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
