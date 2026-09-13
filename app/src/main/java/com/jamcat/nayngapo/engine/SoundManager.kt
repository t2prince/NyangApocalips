package com.jamcat.nayngapo.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.jamcat.nayngapo.R

/**
 * 효과음(SoundPool)과 배경음악(MediaPlayer) 재생 매니저.
 * 짧은 SFX(공격/특수)는 저지연으로 겹쳐 재생하고, 밝은 분위기의 BGM은 계속 반복한다.
 */
object SoundManager {
    private var soundPool: SoundPool? = null
    private var attackId = 0
    private var specialId = 0
    private var gameOverId = 0
    private var catHitId = 0
    private var catDieId = 0
    private var playerHurtId = 0
    private val loadedIds = mutableSetOf<Int>()   // 샘플별 로드 완료 추적
    private var initialized = false

    private var bgm: MediaPlayer? = null

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val pool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build()

        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loadedIds.add(sampleId) }

        attackId = pool.load(context, R.raw.attack, 1)
        specialId = pool.load(context, R.raw.special, 1)
        gameOverId = pool.load(context, R.raw.gameover, 1)
        catHitId = pool.load(context, R.raw.cat_hit, 1)
        catDieId = pool.load(context, R.raw.cat_die, 1)
        playerHurtId = pool.load(context, R.raw.player_hurt, 1)
        soundPool = pool
    }

    fun playAttack() = play(attackId, 1.0f)
    fun playSpecial() = play(specialId, 1.0f)

    /** 죽을 때의 '으어어어...' 비명 */
    fun playGameOver() = play(gameOverId, 1.0f)

    /** 냥이가 맞을 때 '예에에용!' */
    fun playCatHit() = play(catHitId, 0.85f)

    /** 냥이가 쓰러질 때 '애애앵용...' */
    fun playCatDie() = play(catDieId, 0.9f)

    /** 주인공이 맞을 때의 신음 '윽!' */
    fun playPlayerHurt() = play(playerHurtId, 1.0f)

    /** 밝은 분위기의 배경음악을 무한 반복 재생 (이미 재생 중이면 무시) */
    fun startBgm(context: Context) {
        if (bgm != null) return
        bgm = MediaPlayer.create(context, R.raw.bgm_stage)?.apply {
            isLooping = true
            setVolume(BGM_VOLUME, BGM_VOLUME)
            start()
        }
    }

    fun pauseBgm() {
        bgm?.let { if (it.isPlaying) it.pause() }
    }

    fun resumeBgm() {
        bgm?.let { if (!it.isPlaying) it.start() }
    }

    fun releaseBgm() {
        bgm?.release()
        bgm = null
    }

    private fun play(soundId: Int, volume: Float) {
        if (soundId !in loadedIds) return
        soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        loadedIds.clear()
        initialized = false
        releaseBgm()
    }

    private const val BGM_VOLUME = 0.45f   // 효과음이 묻히지 않을 정도의 배경음 크기
}
