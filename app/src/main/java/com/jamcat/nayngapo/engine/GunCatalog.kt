package com.jamcat.nayngapo.engine

/**
 * 총 종류 (대표님 2026-10-25 「총이랑 총알 실제처럼 만들어. 실제로 있는 총이랑 총알을 여러 종류 만들어」).
 * 레이저 총(h_gun)은 그대로 두고 실제 총 다섯 자루를 더했다. 값·피해·간격·사거리·탄속은 전부 Claude 판단.
 * 총알은 팩으로 산다 — 한 번 사면 한 발 (레이저 총알 h_ammo 와 같다).
 */
object GunCatalog {
    class Spec(
        /** 상점 물건 id 이자 총 id */
        val id: String,
        val name: String,
        /** 총알 물건 id · 이름 */
        val ammoId: String,
        val ammoName: String,
        /** 한 발(산탄은 알갱이 하나) 피해. 0 = 인간 공격력 (레이저 총) */
        val damage: Int,
        /** 쏘는 간격 (초) */
        val interval: Float,
        val speed: Float,
        /** 한 번에 나가는 알갱이 수 (산탄총 5, 권총 7) · 부채꼴 반각(도) */
        val pellets: Int = 1,
        val spreadDeg: Float = 0f,
        /** 사거리 (0 = 제한 없음) */
        val range: Float = 0f,
        /** 적을 뚫고 나간다 (저격총) */
        val pierce: Boolean = false,
        /** 탄 그림 종류: 0 레이저(기존 탄), 1 탄알, 2 산탄 알갱이, 3 저격 예광탄 */
        val shot: Int = 0,
        val bulletRadius: Float = 6f,
        /** 인간 키 대비 손에 든 총 길이 · 총구까지 거리(px) */
        val handLen: Float = 0.2f,
        val muzzleDx: Float = 75f,
        /** 옵션 그림 이름 (drawable) */
        val bitmapName: String? = null
    )

    const val LASER = "h_gun"

    val all: List<Spec> = listOf(
        Spec(LASER, "레이저 총", "h_ammo", "총알", 0, 0.28f, 1100f),
        Spec("h_pistol", "권총", "h_ammo_9mm", "9mm", 8, 0.40f, 1500f, pellets = 7, spreadDeg = 8f, shot = 1, range = 1100f, bulletRadius = 7f,
            handLen = 0.14f, muzzleDx = 70f, bitmapName = "gun_pistol"),
        Spec("h_smg", "기관단총", "h_ammo_9mm", "9mm", 5, 0.12f, 1600f, shot = 1, range = 1000f, bulletRadius = 6f,
            handLen = 0.20f, muzzleDx = 85f, bitmapName = "gun_smg"),
        Spec("h_shotgun", "산탄총", "h_ammo_shell", "산탄", 6, 0.90f, 1300f, pellets = 5, spreadDeg = 12f, range = 550f, shot = 2,
            bulletRadius = 5f, handLen = 0.30f, muzzleDx = 110f, bitmapName = "gun_shotgun"),
        Spec("h_rifle", "돌격소총", "h_ammo_rifle", "소총탄", 12, 0.18f, 1800f, shot = 1, range = 1500f, bulletRadius = 7f,
            handLen = 0.30f, muzzleDx = 110f, bitmapName = "gun_rifle"),
        Spec("h_sniper", "저격총", "h_ammo_sniper", "저격탄", 40, 1.4f, 3600f, range = 3500f, pierce = true, shot = 3,
            bulletRadius = 16f, handLen = 0.38f, muzzleDx = 140f, bitmapName = "gun_sniper")
    )

    fun byId(id: String?): Spec? = all.firstOrNull { it.id == id }

    fun owned(): List<Spec> = all.filter { GameProgress.itemCount(it.id) > 0 }

    /** 지금 들 총: 고른 총이 내 것이면 그것, 아니면 레이저 총, 그것도 없으면 가진 첫 총. '안 가져감'이면 null */
    fun equipped(): Spec? {
        val pick = BattleLoadout.gunId
        if (pick == BattleLoadout.NO_GUN) return null
        val own = owned()
        return own.firstOrNull { it.id == pick } ?: own.firstOrNull { it.id == LASER } ?: own.firstOrNull()
    }
}
