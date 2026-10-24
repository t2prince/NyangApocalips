package com.jamcat.nayngapo.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 게임 고르기 (대표님 2026-10-24 「시작 화면 다음에는 게임 고르기가 나와. 거기에서는 게임 이름을 정하고, 편집하고, 다른 냥이 아포칼립스 게임을 할 수 있어」).
 * 게임마다 저장소(SharedPreferences)가 따로다 — 1번 게임은 예전부터 쓰던 "nyang_progress" 그대로(지금까지의 세이브),
 * 새 게임은 "nyang_progress_<번호>". 게임 목록·지금 게임은 "nyang_games" 에. (내가 만든 캐릭터는 모든 게임이 같이 쓴다)
 */
object GameSlots {

    class Slot(val id: Int, var name: String)

    private const val PREFS = "nyang_games"
    private const val KEY_LIST = "games"
    private const val KEY_CURRENT = "current"

    private val list = mutableListOf<Slot>()
    var currentId = 1
        private set
    private var loaded = false

    val all: List<Slot> get() = list

    /** 게임 번호 → 저장소 이름 (1번은 예전 이름 그대로) */
    fun prefsName(id: Int) = if (id == 1) "nyang_progress" else "nyang_progress_$id"

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        list.clear()
        val arr = runCatching { JSONArray(p.getString(KEY_LIST, "[]")) }.getOrNull() ?: JSONArray()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { o -> list.add(Slot(o.optInt("id"), o.optString("name", "냥이 아포칼립스"))) }
        if (list.isEmpty()) list.add(Slot(1, "냥이 아포칼립스 1"))   // 지금까지 하던 게임
        currentId = p.getInt(KEY_CURRENT, list.first().id).takeIf { id -> list.any { it.id == id } } ?: list.first().id
        save(context)
    }

    fun currentPrefsName(context: Context): String { load(context); return prefsName(currentId) }

    val current: Slot? get() = list.firstOrNull { it.id == currentId }

    /** 새 게임 — 번호는 지금까지 쓴 것 다음 */
    fun create(context: Context, name: String): Slot {
        val id = (list.maxOfOrNull { it.id } ?: 0) + 1
        val s = Slot(id, name)
        list.add(s); save(context)
        return s
    }

    fun rename(context: Context, id: Int, name: String) {
        list.firstOrNull { it.id == id }?.name = name
        save(context)
    }

    /** 게임 지우기 — 마지막 하나는 못 지운다. 그 게임의 저장소도 지운다 */
    fun delete(context: Context, id: Int): Boolean {
        if (list.size <= 1) return false
        list.removeAll { it.id == id }
        context.deleteSharedPreferences(prefsName(id))
        if (currentId == id) currentId = list.first().id
        save(context)
        return true
    }

    /** 이 게임으로 바꿔서 읽는다 */
    fun switchTo(context: Context, id: Int) {
        currentId = id
        save(context)
        GameProgress.init(context)
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        for (s in list) arr.put(JSONObject().put("id", s.id).put("name", s.name))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LIST, arr.toString()).putInt(KEY_CURRENT, currentId).apply()
    }

    /** 게임 카드에 적을 짧은 요약 (그 게임 저장소를 읽기만 한다) */
    fun summary(context: Context, id: Int): String {
        val p = context.getSharedPreferences(prefsName(id), Context.MODE_PRIVATE)
        val stage = p.getInt("unlocked_stage", 1)
        val rubies = p.getInt("rubies", 0)
        return "스테이지 $stage · 루비 $rubies"
    }
}
