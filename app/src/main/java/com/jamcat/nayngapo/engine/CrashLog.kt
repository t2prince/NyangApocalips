package com.jamcat.nayngapo.engine

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 크래시 기록 (대표님 2026-11-02 「크래시가 많이 나」「날 수 있는 곳들 틀어막아봐」).
 * 화면 갱신·그리기·터치에서 난 오류는 GameView 가 잡아 여기에 적고 게임은 계속 돈다.
 * 잡지 못하고 앱이 꺼지는 오류도 MainActivity 의 기본 처리기가 꺼지기 전에 적는다.
 * 파일: 앱 내부 저장소 crash_log.txt (adb shell run-as com.jamcat.nayngapo cat files/crash_log.txt) + logcat 태그 CRASH.
 * 세이브(nyang_progress)와는 따로라 플레이 데이터에 섞이지 않는다.
 */
object CrashLog {
    private const val TAG = "CRASH"
    /** 파일이 이보다 커지면 앞쪽 절반을 버린다 */
    private const val MAX_BYTES = 256 * 1024
    private var file: File? = null
    /** 같은 오류가 매 프레임 쌓이지 않게 — 같은 자리·같은 오류는 처음 한 번만 파일에 적는다 */
    private val seen = HashSet<String>()

    fun init(context: Context) {
        file = File(context.filesDir, "crash_log.txt")
    }

    @Synchronized
    fun record(where: String, e: Throwable, screen: String = "-") {
        val trace = Log.getStackTraceString(e)
        Log.e(TAG, "[$where] screen=$screen", e)
        val key = where + "|" + trace.lineSequence().take(4).joinToString("|")
        if (!seen.add(key)) return
        val f = file ?: return
        runCatching {
            if (f.exists() && f.length() > MAX_BYTES) {
                val text = f.readText()
                f.writeText(text.substring(text.length / 2))
            }
            val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            f.appendText("===== $time [$where] screen=$screen\n$trace\n")
        }
    }
}
