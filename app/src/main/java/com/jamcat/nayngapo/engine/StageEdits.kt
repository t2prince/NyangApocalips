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
 *
 * 앱에 넣은 배치 (대표님 2026-11-08 「폰에 저장한 스테이지 데이터를 뽑아서 다른 빌드에도 동일하게」):
 * assets/stage_layouts/stage_N.json 이 있으면 모든 빌드(릴리스 포함)가 그 배치를 쓴다. 폰에서 뽑는 건 scripts/pull_stage_edits.sh.
 * 순서: 이 폰에서 편집한 배치(디버그만) → 앱에 넣은 배치 → 기본 생성.
 */
object StageEdits {
    private const val PREFS = "stage_edits"
    private fun key(stage: Int) = "stage_$stage"

    private const val ASSET_DIR = "stage_layouts"

    private var prefs: SharedPreferences? = null
    /** 앱에 넣은 배치 (스테이지 → 배치) */
    private val bundled = mutableMapOf<Int, List<Placement>>()

    /** 디버그(debuggable) 빌드인가 — 편집 화면·편집 배치는 이때만 쓴다 */
    var enabled = false
        private set

    fun init(context: Context) {
        enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        bundled.clear()
        val am = context.assets
        for (name in am.list(ASSET_DIR).orEmpty()) {
            val stage = Regex("""stage_(\d+)\.json""").matchEntire(name)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val layout = runCatching { am.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() } }.getOrNull()
                ?.let { StageLayoutJson.decode(it) } ?: continue
            bundled[stage] = layout
        }
    }

    /** 앱에 넣은 배치 (없으면 null) */
    fun bundled(stage: Int): List<Placement>? = bundled[stage]

    /** 게임이 쓸 배치 — 이 폰에서 편집한 것(디버그만) → 앱에 넣은 것 → null(기본 생성) */
    fun layoutFor(stage: Int): List<Placement>? = (if (enabled) load(stage) else null) ?: bundled[stage]

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
