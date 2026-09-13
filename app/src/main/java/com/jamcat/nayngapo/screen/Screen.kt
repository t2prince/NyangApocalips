package com.jamcat.nayngapo.screen

import android.graphics.Canvas
import android.view.MotionEvent

interface Screen {
    fun update(deltaTime: Float)
    fun draw(canvas: Canvas)
    fun onTouchEvent(event: MotionEvent): Boolean

    /**
     * 기기의 뒤로가기(제스처/버튼)를 이 화면이 처리했는가.
     * false를 돌려주면 상위에서 앱을 종료한다.
     */
    fun onBack(): Boolean = false
}
