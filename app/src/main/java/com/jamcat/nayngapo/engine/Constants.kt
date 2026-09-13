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
    const val SPECIAL_COOLDOWN = 15f   // 필살기 재사용 대기시간(초)

    // 필살기: 내려찍기(스톰프) — 솟아올랐다가 두 발로 지면을 내려찍는다
    const val STOMP_RISE_VELOCITY = -1150f   // 도약 초기 속도 (일반 점프보다 높이 뜬다)
    const val STOMP_HANG_TIME = 0.14f        // 정점에서 발을 모으는 체공 시간
    const val STOMP_DROP_SPEED = 2600f       // 급강하 속도 (중력 무시, 일정 속도)
    const val STOMP_IMPACT_DURATION = 0.45f  // 착지 후 임팩트 포즈 + 충격파 지속
    const val STOMP_SHOCKWAVE_RADIUS = 340f  // 착지 충격파가 닿는 좌우 반경
    const val STOMP_DROP_RANGE = 45f         // 낙하 중 몸에 닿는 판정의 좌우 여유

    // 화면 전환 페이드 (한쪽 방향 기준 소요 시간)
    const val FADE_DURATION = 0.4f

    const val MAX_LIVES = 3        // 목숨 최대치 (시트: 인간 목숨 3)

    // 벽: 10초에 한 번 인간 앞을 가로막는다 (스테이지 테마에 맞는 재질로 솟아오른다)
    const val WALL_INTERVAL = 10f
    const val WALL_DISTANCE = 430f

    // 폭격: 표적이 스테이지에 나타나고 3~5초(랜덤) 뒤에 터진다
    const val BLAST_FUSE_MIN = 3f      // 표적이 보이고 터지기까지 최소 시간
    const val BLAST_FUSE_MAX = 5f      // 최대 시간
    const val BLAST_RESPAWN_GAP = 2f   // 터진 뒤 다음 표적이 잡히기까지
    const val BLAST_RADIUS = 240f      // 폭발이 닿는 반경

    // 사막 모래바람: 50초에 한 번, 12초 동안(15초 미만) 몰아쳤다 잦아든다
    const val SANDSTORM_INTERVAL = 50f
    const val SANDSTORM_DURATION = 12f

    // HUD
    const val HUD_HEIGHT = 60f
    const val HUD_COLOR = 0xFF333333.toInt()
}
