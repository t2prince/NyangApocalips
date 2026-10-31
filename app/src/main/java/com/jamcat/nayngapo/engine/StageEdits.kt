package com.jamcat.nayngapo.engine

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import com.jamcat.nayngapo.level.Placement
import com.jamcat.nayngapo.level.StageLayoutJson

/**
 * 스테이지 편집 저장소 (대표님 2026-10-31 「현재 있는 스테이지들 편집할 수 있는 화면 만들어줘」).
 * 게임 세이브(nyang_progress)와 따로 "stage_edits" 파일에 스테이지마다 배치 목록(JSON)을 둔다 — 실제 세이브에 테스트 값이 섞이지 않게.
 * 디버그 빌드에서만 쓴다 (대표님 2026-10-31 「디버그 모드에서만야」): 릴리스 빌드면 편집 화면도 없고, 저장된 편집도 Level 이 무시한다.
 */
object StageEdits {
    private const val PREFS = "stage_edits"
    private fun key(stage: Int) = "stage_$stage"

    private var prefs: SharedPreferences? = null

    /** 디버그(debuggable) 빌드인가 — 편집 화면·편집 배치는 이때만 쓴다 */
    var enabled = false
        private set

    fun init(context: Context) {
        enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun isEdited(stage: Int): Boolean = prefs?.contains(key(stage)) == true

    /** 저장된 배치 (없거나 깨졌으면 null) */
    fun load(stage: Int): List<Placement>? =
        prefs?.getString(key(stage), null)?.let { StageLayoutJson.decode(it) }

    fun save(stage: Int, layout: List<Placement>) {
        prefs?.edit()?.putString(key(stage), StageLayoutJson.encode(layout))?.apply()
    }

    /** 처음대로 — 저장한 편집을 지우면 다시 기본 생성으로 돌아간다 */
    fun clear(stage: Int) {
        prefs?.edit()?.remove(key(stage))?.apply()
    }
}
