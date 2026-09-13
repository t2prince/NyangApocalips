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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        gameView = GameView(this)
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
