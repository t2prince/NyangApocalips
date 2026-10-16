package com.jamcat.nayngapo.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import org.json.JSONObject

/**
 * 언어 설정 (대표님 2026-10-17 「설정에 언어설정 넣어줘. 일단 샘플로 영어부터」).
 *
 * 코드 안의 한국어 글자는 그대로 두고, **화면에 그릴 때** 고른 언어로 바꾼다.
 * `assets/i18n/<언어>.json` = { "한국어 원문": "번역" }. 원문의 `{0}` `{1}` 은 코드의 `${...}` 자리.
 * 찾는 순서: ① 원문 그대로 ② 자리 표시가 있는 틀(정규식, 긴 틀부터) — 자리에 들어간 글자도 다시 번역
 * ③ 그래도 한글이 남으면 아는 낱말·구절을 긴 것부터 바꿔 끼운다. 모르는 글자는 한국어 그대로 보인다.
 * 원문 목록은 `scripts/i18n_extract.py` 로 뽑는다 (새 글자를 넣으면 다시 뽑아 번역을 채운다).
 */
object I18n {
    data class Lang(val code: String, val label: String)

    /** 고를 수 있는 언어 — 한국어가 기본, 영어는 샘플 */
    val languages = listOf(Lang("ko", "한국어"), Lang("en", "English"))

    var lang = "ko"
        private set

    private var appContext: Context? = null
    private val exact = HashMap<String, String>()
    private class Template(val regex: Regex, val out: String, val literal: Int)
    private val templates = mutableListOf<Template>()
    /** 바꿔 끼울 구절 (한글이 든 것, 긴 것부터) */
    private var phrases: List<Pair<String, String>> = emptyList()
    private val cache = HashMap<String, String>()
    private val HANGUL = Regex("[가-힣]")

    fun init(context: Context) {
        appContext = context.applicationContext
        load(GameProgress.language)
    }

    fun setLanguage(code: String) {
        GameProgress.language = code
        load(code)
    }

    private fun load(code: String) {
        lang = if (languages.any { it.code == code }) code else "ko"
        exact.clear(); templates.clear(); cache.clear(); phrases = emptyList()
        if (lang == "ko") return
        val ctx = appContext ?: return
        val raw = try { ctx.assets.open("i18n/$lang.json").bufferedReader().use { it.readText() } } catch (_: Exception) { return }
        val o = try { JSONObject(raw) } catch (_: Exception) { return }
        val plain = mutableListOf<Pair<String, String>>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = o.optString(k)
            if (v.isEmpty()) continue
            if (k.contains(PH)) {
                // 틀: 글자 부분은 그대로, {n} 자리는 아무 글자
                val sb = StringBuilder("^")
                var last = 0
                for (m in PH.findAll(k)) {
                    sb.append(Regex.escape(k.substring(last, m.range.first)))
                    sb.append("(.+?)")
                    last = m.range.last + 1
                }
                sb.append(Regex.escape(k.substring(last))).append("$")
                // 자리 순서대로 번호를 다시 매긴다 ({0} 다음 {2} 처럼 띄엄띄엄일 수 있다)
                val order = PH.findAll(k).map { it.groupValues[1] }.toList()
                var out = v
                order.forEachIndexed { idx, n -> out = out.replace("{$n}", "\u0000$idx\u0000") }
                templates.add(Template(Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL), out, literalLength(k)))
            } else {
                exact[k] = v
                if (HANGUL.containsMatchIn(k)) plain.add(k to v)
            }
        }
        templates.sortByDescending { it.literal }
        phrases = plain.sortedByDescending { it.first.length }
    }

    private fun literalLength(s: String) = s.replace(PH, "").length
    private val PH = Regex("\\{(\\d+)\\}")

    /** 화면에 보일 글자를 고른 언어로 */
    fun t(s: String): String {
        if (lang == "ko" || s.isEmpty() || !HANGUL.containsMatchIn(s)) return s
        cache[s]?.let { return it }
        val r = translate(s, 0)
        if (cache.size > CACHE_MAX) cache.clear()
        cache[s] = r
        return r
    }

    private fun translate(s: String, depth: Int): String {
        if (!HANGUL.containsMatchIn(s)) return s
        exact[s]?.let { return it }
        // 앞뒤 빈칸·줄바꿈은 그대로 두고 가운데만
        val trimmed = s.trim()
        if (trimmed != s) exact[trimmed]?.let { return s.replace(trimmed, it) }
        if (depth < 3) for (tp in templates) {
            val m = tp.regex.matchEntire(s) ?: continue
            var out = tp.out
            m.groupValues.drop(1).forEachIndexed { idx, g -> out = out.replace("\u0000$idx\u0000", translate(g, depth + 1)) }
            return out
        }
        // 구절 바꿔 끼우기
        var out = s
        for ((k, v) in phrases) {
            if (k.length < 2) continue   // 한 글자 낱말은 통째일 때만 (위에서 찾는다)
            if (out.contains(k)) out = out.replace(k, v)
            if (!HANGUL.containsMatchIn(out)) break
        }
        return out
    }

    private const val CACHE_MAX = 3000
}

/** 고른 언어로 바꿔서 그린다 (모든 화면 글자는 이것으로) */
fun Canvas.drawTr(text: String, x: Float, y: Float, paint: Paint) = drawText(I18n.t(text), x, y, paint)

/** 고른 언어로 바꾼 글자의 너비 (상자 크기를 맞출 때) */
fun Paint.measureTr(text: String): Float = measureText(I18n.t(text))
