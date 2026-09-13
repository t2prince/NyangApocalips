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
        // 살짝만 굴곡지게: 큰 언덕도 완만하고 잔굴곡은 아주 얕다
        val amp = 10f + stage * 3.5f     // 스테이지 1: ~14, 스테이지 8: ~38
        val rough = stage * 1.2f         // 잔굴곡 높이

        val hills = (sin(x * 0.004f) * 0.55f + sin(x * 0.0013f + 1.3f) * 0.45f) * 0.5f + 0.5f
        val bumps = sin(x * 0.011f + 0.7f) * 0.5f + 0.5f
        return Constants.GROUND_Y - hills * amp - bumps * rough
    }
}
