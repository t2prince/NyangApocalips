package com.jamcat.nayngapo.engine

import kotlin.math.sin

/**
 * 울퉁불퉁한 지형 표면. 사인파 조합으로 언덕을 만들고,
 * 스테이지가 오를수록 큰 언덕(amp)과 잔굴곡(rough)이 함께 커진다.
 */
object Terrain {
    var stage = 1

    /** 월드 x 좌표에서의 지면 표면 y (기준선 GROUND_Y에서 위로 솟는다) */
    fun groundY(x: Float): Float {
        val amp = 18f + stage * 13f      // 큰 언덕 높이 (스테이지 1: ~31, 스테이지 8: ~122)
        val rough = stage * 4f           // 잔굴곡 높이

        val hills = (sin(x * 0.004f) * 0.55f + sin(x * 0.0013f + 1.3f) * 0.45f) * 0.5f + 0.5f
        val bumps = sin(x * 0.011f + 0.7f) * 0.5f + 0.5f
        return Constants.GROUND_Y - hills * amp - bumps * rough
    }
}
