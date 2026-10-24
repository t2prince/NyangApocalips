package com.jamcat.nayngapo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 내가 만든 캐릭터 (대표님 2026-10-24 「추가는 이미지 파일을 첨부한 다음, 관절 부분을 직접 붙여 인간의 움직임대로 할 수 있어」).
 * 그림은 앱 저장소 chars/<id>.png, 관절 11개(그림 안 픽셀 좌표)는 SharedPreferences 에 JSON 으로.
 * 관절 순서는 [JOINT_NAMES].
 */
object CustomChars {

    class Char(val id: String, val name: String, val joints: FloatArray)

    /** 관절 순서 — 찍는 순서이자 저장 순서 */
    val JOINT_NAMES = listOf("머리", "목", "골반", "뒤 팔꿈치", "뒤 손", "앞 팔꿈치", "앞 손", "뒤 무릎", "뒤 발", "앞 무릎", "앞 발")
    const val JOINTS = 11

    private const val PREFS = "nyang_custom_chars"
    private const val KEY = "chars"
    private var prefs: android.content.SharedPreferences? = null
    private var dir: File? = null
    private val list = mutableListOf<Char>()
    private val bitmaps = HashMap<String, Bitmap>()

    val all: List<Char> get() = list

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        dir = File(context.filesDir, "chars").apply { mkdirs() }
        list.clear()
        val arr = runCatching { JSONArray(prefs?.getString(KEY, "[]") ?: "[]") }.getOrNull() ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val js = o.optJSONArray("j") ?: continue
            if (js.length() != JOINTS * 2) continue
            val id = o.optString("id"); if (id.isEmpty() || !File(dir, "$id.png").exists()) continue
            list.add(Char(id, o.optString("name", "내 캐릭터"), FloatArray(JOINTS * 2) { js.optDouble(it).toFloat() }))
        }
    }

    fun byId(id: String): Char? = list.firstOrNull { it.id == id }

    /** 그림 (처음 한 번 읽어 둔다) */
    fun bitmap(id: String): Bitmap? = bitmaps[id] ?: run {
        val f = File(dir ?: return null, "$id.png")
        if (!f.exists()) return null
        BitmapFactory.decodeFile(f.absolutePath)?.also { bitmaps[id] = it }
    }

    /** 새 캐릭터 저장 — 이름은 '내 캐릭터 N' */
    fun add(bmp: Bitmap, joints: FloatArray): Char {
        val id = "custom_" + System.currentTimeMillis()
        File(dir, "$id.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var n = list.size + 1
        while (list.any { it.name == "내 캐릭터 $n" }) n++
        val c = Char(id, "내 캐릭터 $n", joints.copyOf())
        list.add(c); bitmaps[id] = bmp
        save()
        return c
    }

    fun remove(id: String) {
        list.removeAll { it.id == id }
        bitmaps.remove(id)
        dir?.let { File(it, "$id.png").delete() }
        save()
    }

    private fun save() {
        val arr = JSONArray()
        for (c in list) arr.put(JSONObject().put("id", c.id).put("name", c.name).put("j", JSONArray().apply { c.joints.forEach { put(it.toDouble()) } }))
        prefs?.edit()?.putString(KEY, arr.toString())?.apply()
    }
}
