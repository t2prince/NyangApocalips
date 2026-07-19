package com.jamcat.nayngapo.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.jamcat.nayngapo.R

/**
 * SoundPool 기반 효과음 재생 매니저.
 * 짧은 SFX(공격/특수)를 저지연으로 여러 번 겹쳐 재생한다.
 */
object SoundManager {
    private var soundPool: SoundPool? = null
    private var attackId = 0
    private var specialId = 0
    private val loadedIds = mutableSetOf<Int>()   // 샘플별 로드 완료 추적
    private var initialized = false

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
        soundPool = pool
    }

    fun playAttack() = play(attackId, 1.0f)
    fun playSpecial() = play(specialId, 1.0f)

    private fun play(soundId: Int, volume: Float) {
        if (soundId !in loadedIds) return
        soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        loadedIds.clear()
        initialized = false
    }
}
