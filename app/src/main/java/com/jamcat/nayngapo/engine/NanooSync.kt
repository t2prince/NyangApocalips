package com.jamcat.nayngapo.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.jamcat.nayngapo.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 나누(PlayNANOO) 세이브 동기화 (대표님 2026-11-02 「나누로 옮길거야 데이터저장」 「키 없으면 동작안하게하고」).
 *
 * 나누에는 안드로이드 SDK 가 없어서 유니티 플러그인과 같은 REST 를 코틀린으로 부른다.
 * - 헤더: X-Playnanoo-Key(Service Key) · X-Playnanoo-Id(Game ID) · 로그인 뒤 Authorization: Bearer
 * - 게스트 로그인: 기기 ID 를 linked_id 로 → 같은 기기(재설치 포함)는 같은 계정
 * - 저장소: storage_key "save" 에 게임 저장소 전부를 JSON 한 덩어리로
 *
 * 규칙 (Claude 판단, 대표님께 알림):
 * - 키(build.env 의 NANOO_GAME_ID·NANOO_SERVICE_KEY)가 없으면 아무것도 하지 않는다
 * - 계정이 기기 ID 에 묶여 있으니 서버 세이브는 언제나 '이 기기가 마지막으로 올린 것'이다. 그래서
 *   · 나누와 한 번이라도 맞춘 기기(저장소 "nanoo" 의 synced — 백업·복원에서 빠진다)는 기기 세이브가 기준: 앱을 내릴 때·3분마다 바뀌면 올린다
 *   · 맞춘 적이 없는 기기(새로 설치·앱 데이터 삭제)인데 서버에 세이브가 있으면 서버 세이브로 되살린다
 *     (덮기 전 기기 세이브는 files/nanoo_backup_before_restore.json). 되살리기는 켠 직후 화면(로고·로딩·스토리·시작)에서만
 * - 서버 세이브를 받기 전에는 올리지 않는다. 받기에 실패하면 3분마다(그리고 앱을 내릴 때) 다시 받는다
 * - 모든 오류는 삼킨다 — 통신이 게임을 막지 않는다
 */
object NanooSync {
    private const val TAG = "Nanoo"
    private const val HOST_ACCOUNT = "https://service-account.playnanoo.com"
    private const val HOST_STORAGE = "https://service-storage-api.playnanoo.com"
    private const val URL_GUEST_SIGNIN = "$HOST_ACCOUNT/api/v20240401/guest/signin"
    private const val URL_STORAGE_SAVE = "$HOST_STORAGE/storage/v20221001/save"
    private const val URL_STORAGE_LOAD = "$HOST_STORAGE/storage/v20221001/load"
    private const val STORAGE_KEY = "save"
    private const val GAMES_PREFS = "nyang_games"
    /** 동기화 상태 (기기 ID 대체값·synced) — backup_rules·data_extraction_rules 에서 뺀다 */
    private const val STATE_PREFS = "nanoo"
    private const val UPLOAD_EVERY_SEC = 180L
    private const val TIMEOUT_MS = 10_000
    /** 서버에 세이브가 아직 없다 (NotFoundDataException) */
    private const val ERR_NO_DATA = "10006"
    /** 토큰 끝남·종류 틀림·없음 — 다시 로그인한다 */
    private val TOKEN_ERRORS = setOf("30002", "30003", "30005")

    val enabled: Boolean get() = BuildConfig.NANOO_GAME_ID.isNotBlank() && BuildConfig.NANOO_SERVICE_KEY.isNotBlank()

    /**
     * 서버 세이브로 되살려야 할 때 (UI 스레드 아님). 받은 쪽은 지금 되살려도 되는 화면이면 게임을 멈춘 뒤
     * (메모리의 옛 세이브가 다시 써지지 않게) apply 를 부르고 저장소를 다시 읽는다 — MainActivity 가 한다.
     * 안 부르면 다음 차례(3분 뒤·앱을 내릴 때)에 다시 묻는다. apply 는 실패하면 기기 세이브를 그대로 되돌려 놓고 던진다
     */
    var onServerNewer: ((apply: () -> Unit) -> Unit)? = null

    private lateinit var app: Context
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "nanoo").apply { isDaemon = true } }
    private var started = false
    private var accessToken: String? = null
    private var uuid: String? = null
    private var nickname: String = ""
    /** 첫 받기를 마쳤는가 — 그 전에는 올리지 않는다 (받기 전에 올리면 서버의 더 먼 세이브를 덮을 수 있다) */
    @Volatile private var ready = false
    @Volatile private var lastUploaded: String? = null

    fun start(context: Context) {
        if (!enabled || started) return
        started = true
        app = context.applicationContext
        worker.execute { guard("start") { firstSync() } }
        worker.scheduleWithFixedDelay({ guard("periodic") { if (ready) uploadIfChanged() else firstSync() } }, UPLOAD_EVERY_SEC, UPLOAD_EVERY_SEC, TimeUnit.SECONDS)
    }

    /** 앱을 내릴 때 — 바뀐 게 있으면 바로 올린다 */
    fun saveSoon() {
        if (!enabled || !started) return
        worker.execute { guard("pause") { if (ready) uploadIfChanged() else firstSync() } }
    }

    /** 이 기기가 나누와 맞춘 적이 있는가 — 백업에서 빠지는 저장소라 새로 설치하거나 데이터를 지우면 사라진다 */
    private var synced: Boolean
        get() = app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getBoolean("synced", false)
        set(v) { app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit().putBoolean("synced", v).apply() }

    // ===== 동기화 =====

    private fun firstSync() {
        if (accessToken == null && !signIn()) return
        var res = request(URL_STORAGE_LOAD, JSONObject().put("storage_key", STORAGE_KEY), auth = true) ?: return
        if (field(res, "ErrorCode") in TOKEN_ERRORS) {
            if (!signIn()) return
            res = request(URL_STORAGE_LOAD, JSONObject().put("storage_key", STORAGE_KEY), auth = true) ?: return
        }
        val err = field(res, "ErrorCode")
        // 받기 실패(서버에 세이브가 없는 것 말고) — 서버 세이브를 모르는 채로 올리면 덮어쓸 수 있으니 다음 차례에 다시 받는다
        if (!err.isNullOrBlank() && err != ERR_NO_DATA) { Log.w(TAG, "세이브 받기 실패 $err ${field(res, "Message")}"); return }
        val server = field(res, "StorageValue")?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (server == null || synced) {
            // 서버에 아직 없거나, 이 기기가 기준 — 바뀐 게 있으면 올린다
            ready = true
            if (server != null) lastUploaded = snapshotText(server)
            uploadIfChanged()
            return
        }
        // 맞춘 적이 없는 기기 + 서버 세이브 있음 → 되살린다 (지금 안 되면 다음 차례에 다시)
        val receiver = onServerNewer ?: return
        receiver {
            val before = snapshot()
            File(app.filesDir, "nanoo_backup_before_restore.json").writeText(before.toString())
            try {
                restore(server)
            } catch (e: Throwable) {
                runCatching { restore(before) }   // 반쯤 바뀐 세이브를 남기지 않는다
                throw e
            }
            lastUploaded = snapshotText(snapshot())
            synced = true
            ready = true
            Log.i(TAG, "서버 세이브로 되살림")
        }
    }

    private fun uploadIfChanged() {
        if (!ready) return
        val snap = snapshot()
        if (!hasProgress(snap)) return
        val text = snapshotText(snap)
        if (text == lastUploaded) { synced = true; return }
        if (accessToken == null && !signIn()) return   // 지난번 다시 로그인이 실패했으면 여기서 다시
        val body = JSONObject().put("storage_key", STORAGE_KEY).put("storage_value", text).put("private", "on")
        var res = request(URL_STORAGE_SAVE, body, auth = true) ?: return
        if (field(res, "ErrorCode") in TOKEN_ERRORS) {
            // 토큰이 끝났다 (하루) — 한 번 다시 로그인하고 다시 올린다
            if (!signIn()) return
            res = request(URL_STORAGE_SAVE, body, auth = true) ?: return
        }
        field(res, "ErrorCode")?.takeIf { it.isNotBlank() }?.let { Log.w(TAG, "올리기 실패 $it ${field(res, "Message")}"); return }
        lastUploaded = text
        synced = true
        Log.i(TAG, "세이브 올림 (${text.length}자)")
    }

    private fun signIn(): Boolean {
        accessToken = null
        val res = request(URL_GUEST_SIGNIN, JSONObject().put("linked_id", deviceId()), auth = false) ?: return false
        val err = field(res, "ErrorCode")
        val token = obj(res, "Token")?.let { field(it, "AccessToken") } ?: field(res, "access_token")
        if (token.isNullOrBlank()) {
            Log.w(TAG, "로그인 실패 $err ${field(res, "Message")}")
            return false
        }
        accessToken = token
        val player = obj(res, "Player")
        uuid = player?.let { field(it, "UserUniqueID") } ?: field(res, "uuid")
        nickname = player?.let { field(it, "Nickname") } ?: ""
        Log.i(TAG, "게스트 로그인 완료")
        return true
    }

    // ===== 세이브 묶기 =====

    /** 이 게임이 쓰는 저장소 이름들 — 게임 목록과 게임마다의 진행 (내가 만든 캐릭터·편집 맵은 기기 것) */
    private fun prefsNames(): List<String> {
        val ids = mutableSetOf(1)
        val list = app.getSharedPreferences(GAMES_PREFS, Context.MODE_PRIVATE).getString("games", "[]")
        runCatching { JSONArray(list) }.getOrNull()?.let { arr -> for (i in 0 until arr.length()) arr.optJSONObject(i)?.optInt("id")?.let(ids::add) }
        return listOf(GAMES_PREFS) + ids.sorted().map { GameSlots.prefsName(it) }
    }

    private fun snapshot(): JSONObject {
        val all = JSONObject()
        for (name in prefsNames()) {
            val o = JSONObject()
            for ((k, v) in app.getSharedPreferences(name, Context.MODE_PRIVATE).all.toSortedMap()) {
                val e = when (v) {
                    is Int -> JSONObject().put("t", "i").put("v", v)
                    is Long -> JSONObject().put("t", "l").put("v", v)
                    is Float -> if (v.isFinite()) JSONObject().put("t", "f").put("v", v.toDouble()) else null
                    is Boolean -> JSONObject().put("t", "b").put("v", v)
                    is String -> JSONObject().put("t", "s").put("v", v)
                    is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(v.map { it.toString() }.sorted()))
                    else -> null
                } ?: continue
                o.put(k, e)
            }
            all.put(name, o)
        }
        return JSONObject().put("v", 1).put("prefs", all)
    }

    private fun snapshotText(snap: JSONObject) = snap.toString()

    /** 세이브를 통째로 바꾼다 — 먼저 다 읽어 본 뒤에 쓰고, 세이브에 없는 게임 칸 저장소는 지운다 (새 게임 번호가 옛 진행을 이어받지 않게) */
    private fun restore(snap: JSONObject) {
        val all = snap.optJSONObject("prefs") ?: error("세이브에 prefs 가 없다")
        val plan = mutableMapOf<String, List<Pair<String, JSONObject>>>()
        for (name in all.keys()) {
            if (name != GAMES_PREFS && !name.startsWith("nyang_progress")) continue   // 모르는 저장소는 건드리지 않는다
            val o = all.getJSONObject(name)
            plan[name] = o.keys().asSequence().map { k -> k to o.getJSONObject(k) }.toList()
        }
        File(app.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { f ->
            val name = f.name.removeSuffix(".xml")
            if (name.startsWith("nyang_progress") && name !in plan) app.deleteSharedPreferences(name)
        }
        for ((name, entries) in plan) {
            val ed = app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for ((k, e) in entries) {
                when (e.optString("t")) {
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "s" -> ed.putString(k, e.getString("v"))
                    "ss" -> e.getJSONArray("v").let { arr -> ed.putStringSet(k, (0 until arr.length()).map { arr.getString(it) }.toSet()) }
                }
            }
            if (!ed.commit()) error("저장소 쓰기 실패 $name")
        }
    }

    private fun hasProgress(snap: JSONObject): Boolean {
        val all = snap.optJSONObject("prefs") ?: return false
        return all.keys().asSequence().any { it.startsWith("nyang_progress") && (all.optJSONObject(it)?.length() ?: 0) > 0 }
    }

    // ===== 통신 =====

    /** 나누 API 하나 — 공통 값(기기 정보)을 붙여 PUT 으로 보낸다. 통신이 안 되면 null */
    private fun request(url: String, body: JSONObject, auth: Boolean): JSONObject? {
        if (auth && accessToken == null) return null
        commonValues().let { c -> for (k in c.keys()) if (!body.has(k)) body.put(k, c.get(k)) }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("X-Playnanoo-Key", BuildConfig.NANOO_SERVICE_KEY)
            setRequestProperty("X-Playnanoo-Id", BuildConfig.NANOO_GAME_ID)
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            if (auth) setRequestProperty("Authorization", "Bearer $accessToken")
        }
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) Log.w(TAG, "HTTP $code ${url.substringAfterLast(".com")}")
            runCatching { JSONObject(text) }.getOrElse { JSONObject().put("ErrorCode", "http_$code") }
        } catch (e: Exception) {
            Log.w(TAG, "통신 실패 ${url.substringAfterLast(".com")}: ${e.javaClass.simpleName}")
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun commonValues(): JSONObject {
        val o = JSONObject()
            .put("nickname", nickname)
            .put("device_id", deviceId())
            .put("platform", "aos")
            .put("version", BuildConfig.VERSION_CODE)
            .put("device_model", Build.MODEL)
            .put("device_os", "Android OS ${Build.VERSION.RELEASE} / API-${Build.VERSION.SDK_INT}")
            .put("device_language", Locale.getDefault().language.uppercase(Locale.ROOT))
            .put("device_country", Locale.getDefault().country.uppercase(Locale.ROOT))
            .put("device_timeoffset", (TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000).toString())
        uuid?.let { o.put("uuid", it) }
        return o
    }

    @SuppressLint("HardwareIds")
    private fun deviceId(): String {
        val id = runCatching { Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull()
        if (!id.isNullOrBlank()) return id
        // 기기 ID 를 못 읽으면 처음 한 번 만든 값을 쓴다
        val p = app.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        return p.getString("device_id", null) ?: java.util.UUID.randomUUID().toString().also { p.edit().putString("device_id", it).apply() }
    }

    // 나누 응답은 유니티 JsonUtility 이름(AccessToken)이 기본 — 혹시 다른 표기(access_token)여도 읽는다
    private fun norm(s: String) = s.replace("_", "").lowercase(Locale.ROOT)

    private fun field(o: JSONObject, name: String): String? {
        val key = o.keys().asSequence().firstOrNull { norm(it) == norm(name) } ?: return null
        return o.opt(key)?.takeIf { it != JSONObject.NULL }?.toString()
    }

    private fun obj(o: JSONObject, name: String): JSONObject? {
        val key = o.keys().asSequence().firstOrNull { norm(it) == norm(name) } ?: return null
        return o.optJSONObject(key)
    }

    private inline fun guard(where: String, block: () -> Unit) {
        try { block() } catch (e: Throwable) { CrashLog.record("nanoo:$where", e) }
    }
}
