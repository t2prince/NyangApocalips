package com.jamcat.nayngapo.engine

/**
 * 전투에 가져갈 것 (대표님 2026-10-09 「전투 시 어떤 화면이 뜨는데, 거기에서 전투 때 쓸 아이템을 고를 수 있어」).
 * 땅 스테이지에 들어가기 전 출전 준비 화면(BattlePrepScreen)에서 고르고, GamePlayScreen 이 읽는다.
 * 고른 것은 다음 출전 때도 그대로 (앱을 끄면 처음으로 — 가진 건 다 고른 상태). 무엇을 고를 수 있게 할지는 Claude 판단.
 */
object BattleLoadout {
    enum class Ride { WALK, CAR, ROBOT }

    /** 생명 물약 · 하트 물약을 이번에 마실지 (고르면 스테이지를 시작할 때 하나씩) */
    var lifePotion = true
    var heartPotion = true
    /** 채집통을 가져갈지 — 가져가면 스테이지에서 냥이를 때려 내 팀으로 잡는다 (보스 빼고, 한 마리에 하나) */
    var bugbox = true
    /** 레이저 총을 들고 갈지 */
    var gun = true
    /** 야간 전투로 갈지 — 상점마을2에서 의사를 구해야 고를 수 있다 (2026-10-17). 기본은 낮 */
    var night = false
    /** 무엇을 타고 갈지 (없는 건 걷기로) */
    var ride: Ride? = null

    /** 아직 안 골랐으면 가진 것으로: 로봇 타기를 켰으면 로봇, 아니면 고른 차, 없으면 걷기 */
    fun rideNow(): Ride {
        val r = ride ?: when {
            RobotCatalog.rideLevel > 0 -> Ride.ROBOT
            CarCatalog.selected != null -> Ride.CAR
            else -> Ride.WALK
        }
        return when (r) {
            Ride.ROBOT -> if (RobotCatalog.owned) r else Ride.WALK
            Ride.CAR -> if (CarCatalog.selected != null) r else Ride.WALK
            Ride.WALK -> r
        }
    }
}
