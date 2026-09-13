package com.jamcat.nayngapo.ui

/**
 * 인간 스트립 프레임마다 두 손의 위치 (프레임 안 비율, 오른쪽을 볼 때).
 * scripts/find_hands.py 가 생성한다 — 손으로 고치지 말 것.
 * 각 프레임 = floatArrayOf(손1 x, 손1 y, 손2 x, 손2 y)
 */
object HandTable {
    val IDLE = arrayOf(
        floatArrayOf(0.729f, 0.355f, 0.812f, 0.421f),
        floatArrayOf(0.542f, 0.335f, 0.667f, 0.335f),
        floatArrayOf(0.458f, 0.401f, 0.594f, 0.391f),
        floatArrayOf(0.802f, 0.330f, 0.906f, 0.315f),
        floatArrayOf(0.823f, 0.259f, 0.927f, 0.294f),
        floatArrayOf(0.521f, 0.401f, 0.646f, 0.391f),
        floatArrayOf(0.531f, 0.360f, 0.604f, 0.431f)
    )
    val WALK = arrayOf(
        floatArrayOf(0.800f, 0.500f, 0.378f, 0.606f),
        floatArrayOf(0.837f, 0.596f, 0.459f, 0.612f),
        floatArrayOf(0.778f, 0.537f, 0.356f, 0.612f),
        floatArrayOf(0.956f, 0.574f, 0.533f, 0.676f),
        floatArrayOf(0.889f, 0.516f, 0.459f, 0.574f),
        floatArrayOf(0.881f, 0.574f, 0.504f, 0.601f),
        floatArrayOf(0.852f, 0.553f, 0.407f, 0.601f)
    )
    val JUMP = arrayOf(
        floatArrayOf(0.319f, 0.695f, 0.270f, 0.655f),
        floatArrayOf(0.142f, 0.705f, 0.142f, 0.705f),
        floatArrayOf(0.468f, 0.309f, 0.468f, 0.373f),
        floatArrayOf(0.830f, 0.473f, 0.957f, 0.414f),
        floatArrayOf(0.135f, 0.568f, 0.865f, 0.564f),
        floatArrayOf(0.553f, 0.532f, 0.603f, 0.495f),
        floatArrayOf(0.426f, 0.777f, 0.468f, 0.745f)
    )
    val PUNCH = arrayOf(
        floatArrayOf(0.341f, 0.401f, 0.432f, 0.243f),
        floatArrayOf(0.386f, 0.390f, 0.144f, 0.407f),
        floatArrayOf(0.235f, 0.395f, 0.606f, 0.282f),
        floatArrayOf(0.955f, 0.277f, 0.955f, 0.277f),
        floatArrayOf(0.386f, 0.395f, 0.652f, 0.299f),
        floatArrayOf(0.379f, 0.401f, 0.424f, 0.260f),
        floatArrayOf(0.258f, 0.401f, 0.303f, 0.390f)
    )
    val CROSS = arrayOf(
        floatArrayOf(0.594f, 0.271f, 0.650f, 0.309f),
        floatArrayOf(0.700f, 0.276f, 0.188f, 0.475f),
        floatArrayOf(0.694f, 0.309f, 0.694f, 0.309f),
        floatArrayOf(0.950f, 0.331f, 0.950f, 0.331f),
        floatArrayOf(0.963f, 0.337f, 0.963f, 0.337f),
        floatArrayOf(0.588f, 0.420f, 0.606f, 0.287f),
        floatArrayOf(0.562f, 0.282f, 0.606f, 0.309f)
    )
    val KICK = arrayOf(
        floatArrayOf(0.477f, 0.298f, 0.477f, 0.298f),
        floatArrayOf(0.261f, 0.288f, 0.335f, 0.298f),
        floatArrayOf(0.233f, 0.293f, 0.233f, 0.293f),
        floatArrayOf(0.597f, 0.283f, 0.597f, 0.283f),
        floatArrayOf(0.528f, 0.283f, 0.528f, 0.283f),
        floatArrayOf(0.528f, 0.288f, 0.528f, 0.288f),
        floatArrayOf(0.455f, 0.304f, 0.455f, 0.304f)
    )
    val JUMPKICK = arrayOf(
        floatArrayOf(0.511f, 0.418f, 0.301f, 0.622f),
        floatArrayOf(0.369f, 0.342f, 0.369f, 0.342f),
        floatArrayOf(0.256f, 0.316f, 0.256f, 0.316f),
        floatArrayOf(0.585f, 0.439f, 0.585f, 0.439f),
        floatArrayOf(0.460f, 0.607f, 0.460f, 0.607f),
        floatArrayOf(0.557f, 0.342f, 0.557f, 0.342f),
        floatArrayOf(0.347f, 0.577f, 0.347f, 0.577f)
    )
    val STOMP = arrayOf(
        floatArrayOf(0.492f, 0.547f, 0.492f, 0.547f),
        floatArrayOf(0.288f, 0.440f, 0.697f, 0.440f),
        floatArrayOf(0.212f, 0.436f, 0.773f, 0.436f),
        floatArrayOf(0.212f, 0.440f, 0.773f, 0.440f),
        floatArrayOf(0.212f, 0.436f, 0.773f, 0.436f),
        floatArrayOf(0.492f, 0.547f, 0.492f, 0.547f),
        floatArrayOf(0.492f, 0.508f, 0.492f, 0.508f)
    )
}
