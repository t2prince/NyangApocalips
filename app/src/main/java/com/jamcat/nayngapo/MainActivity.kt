package com.jamcat.nayngapo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SoundManager

class MainActivity : AppCompatActivity() {
    private lateinit var gameView: GameView

    /** 캐릭터 추가: 폰의 그림 고르기 (2026-10-24) — 고른 그림은 긴 쪽 900px 이하로 줄여 넘긴다 */
    private var pickDone: ((android.graphics.Bitmap?) -> Unit)? = null
    private val pickImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        val done = pickDone ?: return@registerForActivityResult
        pickDone = null
        if (uri == null) { done(null); return@registerForActivityResult }
        val bmp = runCatching {
            val src = android.graphics.ImageDecoder.createSource(contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val big = maxOf(info.size.width, info.size.height)
                if (big > 900) decoder.setTargetSize(info.size.width * 900 / big, info.size.height * 900 / big)
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        }.getOrNull()
        done(bmp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 크래시 기록 (2026-11-02): 잡지 못한 오류로 앱이 꺼질 때도 꺼지기 전에 crash_log.txt 에 남긴다
        com.jamcat.nayngapo.engine.CrashLog.init(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            com.jamcat.nayngapo.engine.CrashLog.record("uncaught:${t.name}", e)
            previous?.uncaughtException(t, e)
        }

        // Fullscreen immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = GameView(this)
        gameView.imagePicker = { done -> pickDone = done; pickImage.launch("image/*") }
        setContentView(gameView)

        // 뒤로가기: 스테이지 → 로비 → 타이틀 순으로 한 단계씩 물러나고,
        // 타이틀에서 한 번 더 누르면 그때 앱이 닫힌다 (게임 도중 갑자기 꺼지지 않도록)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (gameView.handleBack()) return
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
        SoundManager.pauseBgm()
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
        SoundManager.resumeBgm()
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.releaseBgm()
    }
}
