package com.jamcat.nayngapo.engine

object Constants {
    // Play area
    // 슈퍼마리오식 횡뷰 플랫포머: 모든 캐릭터는 지형 표면 위에서 좌우로만 이동
    const val PLAY_AREA_LEFT = 0f
    const val PLAY_AREA_RIGHT = 24000f   // 스테이지 길이 = 화면 약 10개 분량
    const val GROUND_Y = 870f            // 지면 기준선 (Terrain이 여기서 위로 언덕을 만든다)

    // Enemy
    const val ENEMY_FIRE_RANGE = 1200f       // 화면에 보일 만한 거리에서만 원거리 공격
    const val ENEMY_CATCHUP_DISTANCE = 1400f // 이보다 뒤에 남겨지면 플레이어를 쫓아온다
    const val ENEMY_CATCHUP_SPEED = 350f     // 따라잡기 속도 (플레이어 320보다 빠르게)

    // Player
    const val PLAYER_SPEED = 320f
    const val JUMP_GRAVITY = 1800f
    const val JUMP_VELOCITY = -850f
    const val JUMP_MAX_HEIGHT = 200f   // 최고점 높이(=v²/2g), 그림자 스케일 계산용
    const val AIR_CONTROL = 0.12f       // 공중에서 이동 입력이 관성에 미치는 영향(0=관성유지, 1=즉시전환)
    const val PLAYER_WIDTH = 60f
    const val PLAYER_HEIGHT = 110f
    const val INVINCIBILITY_DURATION = 1.5f
    const val ATTACK_DURATION = 0.25f
    const val KICK_DURATION = 0.34f        // 킥 마무리 모션이 좀 더 오래 보이도록
    const val COMBO_WINDOW = 0.5f          // 이 시간 안에 다시 공격하면 콤보(원→투→킥) 이어짐
    const val ATTACK_RANGE = 95f
    const val KICK_RANGE = 130f            // 킥 마무리 사거리
    const val SPECIAL_MAX = 100f
    const val SPECIAL_GAIN_PER_KILL = 12f
    const val SPECIAL_GAIN_PER_HIT = 5f

    // HUD
    const val HUD_HEIGHT = 60f
    const val HUD_COLOR = 0xFF333333.toInt()
}
