package com.jamcat.nayngapo.screen

import android.graphics.Canvas
import android.view.MotionEvent

interface Screen {
    fun update(deltaTime: Float)
    fun draw(canvas: Canvas)
    fun onTouchEvent(event: MotionEvent): Boolean
}
