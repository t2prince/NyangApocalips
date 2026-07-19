package com.jamcat.nayngapo.engine

import android.content.Context

/** 스테이지 잠금 해제 진행도. SharedPreferences에 저장되어 앱 재시작 후에도 유지된다. */
object GameProgress {
    const val TOTAL_STAGES = 8

    private const val PREFS = "nyang_progress"
    private const val KEY_UNLOCKED = "unlocked_stage"

    var unlockedStage = 1
        private set

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        unlockedStage = prefs?.getInt(KEY_UNLOCKED, 1) ?: 1
    }

    /** 스테이지 클리어 → 다음 스테이지 잠금 해제 */
    fun onStageCleared(stageNumber: Int) {
        val next = (stageNumber + 1).coerceAtMost(TOTAL_STAGES)
        if (next > unlockedStage) {
            unlockedStage = next
            prefs?.edit()?.putInt(KEY_UNLOCKED, unlockedStage)?.apply()
        }
    }
}
