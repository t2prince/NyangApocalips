package com.jamcat.nayngapo.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.jamcat.nayngapo.R

object SpriteManager {
    lateinit var normalCat: Bitmap
    lateinit var thiefCat: Bitmap
    lateinit var laserCat: Bitmap
    lateinit var bossHumanCat: Bitmap
    lateinit var bossEvilCat: Bitmap
    lateinit var bossWizardCat: Bitmap
    lateinit var prison: Bitmap
    lateinit var heart: Bitmap
    lateinit var tile: Bitmap
    lateinit var tileDark: Bitmap
    lateinit var groundTile: Bitmap  // 만화풍 잔디+흙 지면 스트립 (가로 반복)
    lateinit var snowGround: Bitmap  // 설원(3~4스테이지) 지면 스트립: 흙 위에 눈
    lateinit var sandGround: Bitmap  // 사막(5~6스테이지) 지면 스트립
    lateinit var ashGround: Bitmap   // 화산(7~8스테이지) 지면 스트립: 용암 균열

    // cats/ 디자인 시트의 적 냥이들
    lateinit var fluffyCat: Bitmap   // 털뭉냥이 HP50
    lateinit var greenCat: Bitmap    // 그린냥이 HP20
    lateinit var flyingCat: Bitmap   // 날아냥이 HP25
    lateinit var flyingCatAlly: Bitmap  // 아군 날아냥이 (꼬리가 얇다 — 타고 다니는 그 냥이)
    lateinit var anvilCat: Bitmap    // 모루냥이 HP75
    lateinit var blueCat: Bitmap     // 파랑냥이 HP20
    lateinit var poopCat: Bitmap     // 똥냥이 HP30
    // 같은 종이라도 나쁜 편은 꼬리에 털이 수북하다
    lateinit var greenCatEvil: Bitmap
    /** 빨강·주황·노랑냥이 — 일반 냥이 색만 바꾼 것 (2026-09-20). 적은 그린·파랑처럼 살짝 크게 */
    lateinit var redCatEvil: Bitmap
    lateinit var orangeCatEvil: Bitmap
    lateinit var yellowCatEvil: Bitmap
    /** 머드냥이 (기획9, 2026-09-27) — 늪 속에 숨어 있는 진흙 냥이 */
    lateinit var mudCatEvil: Bitmap
    lateinit var catIdleRed: List<Bitmap>
    lateinit var catIdleOrange: List<Bitmap>
    lateinit var catIdleYellow: List<Bitmap>
    lateinit var blueCatEvil: Bitmap
    lateinit var greenCatAlly: Bitmap   // 아군용 얇은 꼬리 변형
    lateinit var blueCatAlly: Bitmap
    lateinit var vineCat: Bitmap        // 나뭇잎 모자를 쓴 덩굴냥이 (정글)
    lateinit var teleportCat: Bitmap    // 순간냥이 = 그린냥이 + 가운데 분홍 줄 (초원 보스)
    lateinit var allyCat: Bitmap     // 인간 편의 평범한 냥이

    // 애니메이션 프레임 (cat4·cat5 시트: 대기/이동/점프/공격)
    lateinit var catIdle: List<Bitmap>
    /** 같은 대기 동작을 초록·파랑으로 물들인 시트 (도감에서 그린냥이·파랑냥이가 춤춘다) */
    lateinit var catIdleGreen: List<Bitmap>
    lateinit var catIdleBlue: List<Bitmap>
    /** 도둑냥이 대기(춤) — 가면을 쓴 채 (도감용) */
    lateinit var thiefIdle: List<Bitmap>
    lateinit var catWalk: List<Bitmap>
    lateinit var catJump: List<Bitmap>
    lateinit var catAttack: List<Bitmap>
    /** 치즈냥이 무늬 변형(하양이·삼색이·턱시도·고등어) — 노랑이는 위의 기본 그림을 그대로 쓴다 */
    val coatEnemy = mutableMapOf<CatCoat, Bitmap>()
    val coatAlly = mutableMapOf<CatCoat, Bitmap>()
    val coatIdle = mutableMapOf<CatCoat, List<Bitmap>>()
    val coatWalk = mutableMapOf<CatCoat, List<Bitmap>>()
    val coatJump = mutableMapOf<CatCoat, List<Bitmap>>()
    val coatAttack = mutableMapOf<CatCoat, List<Bitmap>>()
    /** 갑옷 입은 모습 (원안 Cats/cat_armor.jpeg) — 아군 정지 그림·대기 스트립. 무늬 5종 + 그린·파랑·날아냥이 */
    val coatAllyArmor = mutableMapOf<CatCoat, Bitmap>()
    val coatIdleArmor = mutableMapOf<CatCoat, List<Bitmap>>()
    lateinit var greenCatAllyArmor: Bitmap
    lateinit var blueCatAllyArmor: Bitmap
    lateinit var flyingCatAllyArmor: Bitmap
    lateinit var catIdleGreenArmor: List<Bitmap>
    /** 총냥이 아군 (등에 총) — scripts/make_gun_cat.py */
    lateinit var gunCatAlly: Bitmap
    lateinit var gunCatAllyArmor: Bitmap
    lateinit var gunIdle: List<Bitmap>
    lateinit var gunIdleArmor: List<Bitmap>
    /** 도감용 적 총냥이 (두꺼운 꼬리) 정지·대기 */
    lateinit var gunCat: Bitmap
    lateinit var gunIdleEnemy: List<Bitmap>
    lateinit var catIdleBlueArmor: List<Bitmap>
    lateinit var laserIdle: List<Bitmap>
    lateinit var laserWalk: List<Bitmap>
    lateinit var laserJump: List<Bitmap>
    lateinit var laserAttack: List<Bitmap>

    // 필살기 내려찍기: 낙하 포즈 / 착지 임팩트 포즈 / 지면 충격파
    lateinit var shockwave: Bitmap

    /** 로비 화면 칸 그림 (LobbyScreen의 슬롯 id → 그림). drawable-nodpi라 원본 크기 그대로 들어온다. */
    lateinit var lobbyIcons: Map<String, Bitmap>

    /** 고양이 상점 물건 그림 (ShopCatalog의 물건 id → 그림) */
    lateinit var shopIcons: Map<String, Bitmap>

    /** 쓰러진 팀 냥이 자리에 세우는 무덤 */
    lateinit var grave: Bitmap

    /** 고양이 기술 그림 (CatSkills의 기술 id → 그림) */
    lateinit var skillIcons: Map<String, Bitmap>

    /** 인간 기술 그림 (HumanSkills의 기술 id → 그림) */
    lateinit var humanSkillIcons: Map<String, Bitmap>

    // 플레이어(졸라맨) 애니메이션 — 한 장의 20포즈 시트에서 잘라낸 스트립들이라 체격이 일정하다.
    // 모든 프레임이 같은 크기·같은 바닥선을 쓰므로 그릴 때 아래쪽만 발끝에 맞추면 된다.
    lateinit var playerIdle: List<Bitmap>
    lateinit var playerWalk: List<Bitmap>
    lateinit var playerJump: List<Bitmap>      // 상승 / 정점 / 하강
    lateinit var playerPunch: List<Bitmap>     // 잽
    lateinit var playerCross: List<Bitmap>     // 크로스
    lateinit var playerKick: List<Bitmap>      // 하이킥
    lateinit var playerJumpKick: List<Bitmap>  // 드롭킥
    lateinit var playerStomp: List<Bitmap>     // 필살기 내려찍기(도약→급강하→착지)

    /** 동작 시트 한 장에 들어 있는 프레임 수 */
    const val PLAYER_FRAMES = 7

    private var initialized = false

    private fun loadCoat(res: android.content.res.Resources, coat: CatCoat, enemy: Int, ally: Int, idle: Int, walk: Int, jump: Int, attack: Int) {
        coatEnemy[coat] = BitmapFactory.decodeResource(res, enemy)
        coatAlly[coat] = BitmapFactory.decodeResource(res, ally)
        coatIdle[coat] = splitStrip(res, idle, PLAYER_FRAMES)
        coatWalk[coat] = splitStrip(res, walk, PLAYER_FRAMES)
        coatJump[coat] = splitStrip(res, jump, PLAYER_FRAMES)
        coatAttack[coat] = splitStrip(res, attack, PLAYER_FRAMES)
    }

    fun init(context: Context) {
        if (initialized) return
        val res = context.resources

        // 냥이들은 Cats/ 디자인 시트를 보고 다시 그린 그림 (drawable-nodpi, 이미 게임 크기)
        normalCat = BitmapFactory.decodeResource(res, R.drawable.cat_normal)
        thiefCat = BitmapFactory.decodeResource(res, R.drawable.cat_thief)   // 눈가리개 + 보따리 (scripts/make_thief_cat.py)
        laserCat = BitmapFactory.decodeResource(res, R.drawable.cat_laser)
        bossHumanCat = BitmapFactory.decodeResource(res, R.drawable.cat_boss_human)
        bossEvilCat = BitmapFactory.decodeResource(res, R.drawable.cat_boss_evil)
        bossWizardCat = BitmapFactory.decodeResource(res, R.drawable.cat_boss_wizard)
        prison = load(res, R.drawable.sprite_prison, 90, 120)
        heart = load(res, R.drawable.sprite_heart, 30, 30)
        tile = load(res, R.drawable.sprite_tile, 80, 80)
        tileDark = load(res, R.drawable.sprite_tile_dark, 80, 80)
        groundTile = BitmapFactory.decodeResource(res, R.drawable.grass_ground)   // 원본 크기, draw 시 스케일

        // 테마별 지면 스트립 (원본 크기, draw 시 지형을 따라 스케일)
        snowGround = BitmapFactory.decodeResource(res, R.drawable.snow_ground)
        sandGround = BitmapFactory.decodeResource(res, R.drawable.sand_ground)
        ashGround = BitmapFactory.decodeResource(res, R.drawable.ash_ground)

        fluffyCat = BitmapFactory.decodeResource(res, R.drawable.cat_fluffy)
        greenCat = BitmapFactory.decodeResource(res, R.drawable.cat_green)
        flyingCat = BitmapFactory.decodeResource(res, R.drawable.cat_flying)
        flyingCatAlly = BitmapFactory.decodeResource(res, R.drawable.cat_flying_ally)
        anvilCat = BitmapFactory.decodeResource(res, R.drawable.cat_anvil)
        blueCat = BitmapFactory.decodeResource(res, R.drawable.cat_blue)
        poopCat = BitmapFactory.decodeResource(res, R.drawable.cat_poop)
        allyCat = BitmapFactory.decodeResource(res, R.drawable.cat_ally)
        // 나쁜 편은 같은 종이라도 조금 더 크게 보이도록 (시트: 꼬리에 털이 수북한 개체)
        greenCatEvil = scaled(greenCat, 1.06f)
        redCatEvil = scaled(BitmapFactory.decodeResource(res, R.drawable.cat_red), 1.06f)
        orangeCatEvil = scaled(BitmapFactory.decodeResource(res, R.drawable.cat_orange), 1.06f)
        yellowCatEvil = scaled(BitmapFactory.decodeResource(res, R.drawable.cat_yellow), 1.06f)
        mudCatEvil = scaled(BitmapFactory.decodeResource(res, R.drawable.cat_mud), 1.06f)
        blueCatEvil = scaled(blueCat, 1.06f)
        // 아군은 꼬리가 얇다 (기획: 같은편 꼬리 얇음, 다른편 두꺼움)
        greenCatAlly = BitmapFactory.decodeResource(res, R.drawable.cat_green_ally)
        blueCatAlly = BitmapFactory.decodeResource(res, R.drawable.cat_blue_ally)
        // 덩굴냥이: 냥이에 나뭇잎 모자를 씌운 합성본 (정글 12~20)
        vineCat = makeVineCat(normalCat)
        teleportCat = scaled(BitmapFactory.decodeResource(res, R.drawable.cat_teleport), 1.25f)   // 보스라 조금 크게

        // 냥이도 동작마다 7프레임짜리 연속 동작 스트립
        catIdle = splitStrip(res, R.drawable.anim_cat_idle, PLAYER_FRAMES)
        catIdleGreen = splitStrip(res, R.drawable.anim_cat_idle_green, PLAYER_FRAMES)
        catIdleRed = splitStrip(res, R.drawable.anim_cat_idle_red, PLAYER_FRAMES)
        catIdleOrange = splitStrip(res, R.drawable.anim_cat_idle_orange, PLAYER_FRAMES)
        catIdleYellow = splitStrip(res, R.drawable.anim_cat_idle_yellow, PLAYER_FRAMES)
        catIdleBlue = splitStrip(res, R.drawable.anim_cat_idle_blue, PLAYER_FRAMES)
        thiefIdle = splitStrip(res, R.drawable.anim_cat_idle_thief, PLAYER_FRAMES)
        catWalk = splitStrip(res, R.drawable.anim_cat_walk, PLAYER_FRAMES)
        catJump = splitStrip(res, R.drawable.anim_cat_jump, PLAYER_FRAMES)
        catAttack = splitStrip(res, R.drawable.anim_cat_attack, PLAYER_FRAMES)
        // 무늬 변형 — 노랑이(CHEESE)는 맵에 안 넣고 기본 그림으로 떨어진다
        coatEnemy[CatCoat.CHEESE] = normalCat; coatAlly[CatCoat.CHEESE] = allyCat
        coatIdle[CatCoat.CHEESE] = catIdle; coatWalk[CatCoat.CHEESE] = catWalk
        coatJump[CatCoat.CHEESE] = catJump; coatAttack[CatCoat.CHEESE] = catAttack
        loadCoat(res, CatCoat.WHITE, R.drawable.cat_white, R.drawable.cat_white_ally,
            R.drawable.anim_cat_idle_white, R.drawable.anim_cat_walk_white, R.drawable.anim_cat_jump_white, R.drawable.anim_cat_attack_white)
        loadCoat(res, CatCoat.CALICO, R.drawable.cat_calico, R.drawable.cat_calico_ally,
            R.drawable.anim_cat_idle_calico, R.drawable.anim_cat_walk_calico, R.drawable.anim_cat_jump_calico, R.drawable.anim_cat_attack_calico)
        loadCoat(res, CatCoat.TUXEDO, R.drawable.cat_tuxedo, R.drawable.cat_tuxedo_ally,
            R.drawable.anim_cat_idle_tuxedo, R.drawable.anim_cat_walk_tuxedo, R.drawable.anim_cat_jump_tuxedo, R.drawable.anim_cat_attack_tuxedo)
        loadCoat(res, CatCoat.BICOLOR, R.drawable.cat_bicolor, R.drawable.cat_bicolor_ally,
            R.drawable.anim_cat_idle_bicolor, R.drawable.anim_cat_walk_bicolor, R.drawable.anim_cat_jump_bicolor, R.drawable.anim_cat_attack_bicolor)
        loadCoat(res, CatCoat.MACKEREL, R.drawable.cat_mackerel, R.drawable.cat_mackerel_ally,
            R.drawable.anim_cat_idle_mackerel, R.drawable.anim_cat_walk_mackerel, R.drawable.anim_cat_jump_mackerel, R.drawable.anim_cat_attack_mackerel)
        // 갑옷 입은 그림 — 입히면 그림 자체가 바뀐다 (판·투구·볏이 그려진 별도 스프라이트)
        coatAllyArmor[CatCoat.CHEESE] = BitmapFactory.decodeResource(res, R.drawable.cat_cheese_armor_ally)
        coatAllyArmor[CatCoat.WHITE] = BitmapFactory.decodeResource(res, R.drawable.cat_white_armor_ally)
        coatAllyArmor[CatCoat.CALICO] = BitmapFactory.decodeResource(res, R.drawable.cat_calico_armor_ally)
        coatAllyArmor[CatCoat.TUXEDO] = BitmapFactory.decodeResource(res, R.drawable.cat_tuxedo_armor_ally)
        coatAllyArmor[CatCoat.MACKEREL] = BitmapFactory.decodeResource(res, R.drawable.cat_mackerel_armor_ally)
        coatIdleArmor[CatCoat.CHEESE] = splitStrip(res, R.drawable.anim_cat_idle_cheese_armor, PLAYER_FRAMES)
        coatIdleArmor[CatCoat.WHITE] = splitStrip(res, R.drawable.anim_cat_idle_white_armor, PLAYER_FRAMES)
        coatIdleArmor[CatCoat.CALICO] = splitStrip(res, R.drawable.anim_cat_idle_calico_armor, PLAYER_FRAMES)
        coatIdleArmor[CatCoat.TUXEDO] = splitStrip(res, R.drawable.anim_cat_idle_tuxedo_armor, PLAYER_FRAMES)
        coatIdleArmor[CatCoat.MACKEREL] = splitStrip(res, R.drawable.anim_cat_idle_mackerel_armor, PLAYER_FRAMES)
        greenCatAllyArmor = BitmapFactory.decodeResource(res, R.drawable.cat_green_armor_ally)
        blueCatAllyArmor = BitmapFactory.decodeResource(res, R.drawable.cat_blue_armor_ally)
        flyingCatAllyArmor = BitmapFactory.decodeResource(res, R.drawable.cat_flying_armor_ally)
        catIdleGreenArmor = splitStrip(res, R.drawable.anim_cat_idle_green_armor, PLAYER_FRAMES)
        gunCatAlly = BitmapFactory.decodeResource(res, R.drawable.cat_gun_ally)
        gunCatAllyArmor = BitmapFactory.decodeResource(res, R.drawable.cat_gun_armor_ally)
        gunIdle = splitStrip(res, R.drawable.anim_cat_idle_gun, PLAYER_FRAMES)
        gunIdleArmor = splitStrip(res, R.drawable.anim_cat_idle_gun_armor, PLAYER_FRAMES)
        gunCat = BitmapFactory.decodeResource(res, R.drawable.cat_gun)
        gunIdleEnemy = splitStrip(res, R.drawable.anim_cat_idle_gun_enemy, PLAYER_FRAMES)
        catIdleBlueArmor = splitStrip(res, R.drawable.anim_cat_idle_blue_armor, PLAYER_FRAMES)
        laserIdle = splitStrip(res, R.drawable.anim_laser_idle, PLAYER_FRAMES)
        laserWalk = splitStrip(res, R.drawable.anim_laser_walk, PLAYER_FRAMES)
        laserJump = splitStrip(res, R.drawable.anim_laser_jump, PLAYER_FRAMES)
        laserAttack = splitStrip(res, R.drawable.anim_laser_attack, PLAYER_FRAMES)

        shockwave = BitmapFactory.decodeResource(res, R.drawable.sprite_shockwave)  // 충격파는 매 프레임 확대

        // 동작마다 7프레임짜리 연속 동작 스트립
        playerIdle = splitStrip(res, R.drawable.player_idle, PLAYER_FRAMES)
        playerWalk = splitStrip(res, R.drawable.player_walk, PLAYER_FRAMES)
        playerJump = splitStrip(res, R.drawable.player_jump, PLAYER_FRAMES)
        playerPunch = splitStrip(res, R.drawable.player_punch, PLAYER_FRAMES)
        playerCross = splitStrip(res, R.drawable.player_cross, PLAYER_FRAMES)
        playerKick = splitStrip(res, R.drawable.player_kick, PLAYER_FRAMES)
        playerJumpKick = splitStrip(res, R.drawable.player_jumpkick, PLAYER_FRAMES)
        playerStomp = splitStrip(res, R.drawable.player_stomp, PLAYER_FRAMES)

        lobbyIcons = mapOf(
            "character" to BitmapFactory.decodeResource(res, R.drawable.lobby_character),
            "cat_skill" to BitmapFactory.decodeResource(res, R.drawable.lobby_cat_skill),
            "worldmap" to BitmapFactory.decodeResource(res, R.drawable.lobby_map),
            "battle" to BitmapFactory.decodeResource(res, R.drawable.lobby_battle),
            "cat_manage" to BitmapFactory.decodeResource(res, R.drawable.lobby_cat_manage),
            "shop" to BitmapFactory.decodeResource(res, R.drawable.lobby_shop),
            "internet" to BitmapFactory.decodeResource(res, R.drawable.lobby_internet),
            "settings" to BitmapFactory.decodeResource(res, R.drawable.lobby_settings),
            "ruby_shop" to BitmapFactory.decodeResource(res, R.drawable.lobby_ruby_shop),
            "skill" to BitmapFactory.decodeResource(res, R.drawable.lobby_skill),
            "mission" to BitmapFactory.decodeResource(res, R.drawable.lobby_mission),
        )

        // 상점 물건 그림은 목록을 그대로 훑어 불러온다 — 물건을 늘리면 그림도 따라온다
        // (파일 이름 규칙: drawable-nodpi/shop_<물건 id>.png)
        shopIcons = ShopCatalog.items.mapNotNull { item ->
            val resId = res.getIdentifier("shop_${item.id}", "drawable", context.packageName)
            if (resId == 0) null
            else BitmapFactory.decodeResource(res, resId)?.let { item.id to it }
        }.toMap()

        grave = BitmapFactory.decodeResource(res, R.drawable.shop_grave)

        // 기술 그림도 목록을 훑어 불러온다 (drawable-nodpi/skill_<id>.png)
        skillIcons = CatSkills.all.mapNotNull { skill ->
            val resId = res.getIdentifier(skill.iconName, "drawable", context.packageName)
            if (resId == 0) null
            else BitmapFactory.decodeResource(res, resId)?.let { skill.id to it }
        }.toMap()

        // 인간 기술 그림 (drawable-nodpi/hskill_<id>.png)
        humanSkillIcons = HumanSkills.all.mapNotNull { skill ->
            val resId = res.getIdentifier(HumanSkills.iconName(skill.id), "drawable", context.packageName)
            if (resId == 0) null
            else BitmapFactory.decodeResource(res, resId)?.let { skill.id to it }
        }.toMap()

        initialized = true
    }

    private fun load(res: android.content.res.Resources, resId: Int, w: Int, h: Int): Bitmap {
        val raw = BitmapFactory.decodeResource(res, resId)
        return Bitmap.createScaledBitmap(raw, w, h, true)
    }

    /** 냥이 머리에 나뭇잎 모자를 그려 넣은 덩굴냥이 합성본 (얼굴 구조는 원안 그대로) */
    private fun makeVineCat(base: Bitmap): Bitmap {
        val bmp = base.copy(Bitmap.Config.ARGB_8888, true)
        val c = android.graphics.Canvas(bmp)
        val leaf = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(74, 148, 62); isAntiAlias = true
        }
        val leafDark = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(52, 116, 46); isAntiAlias = true
        }
        val w = bmp.width.toFloat()
        // 얼굴은 오른쪽 40% — 그 위에 잎사귀 세 장을 겹쳐 얹는다
        val hx = w * 0.72f
        val hy = bmp.height * 0.10f
        fun leafAt(cx: Float, cy: Float, angle: Float, len: Float, wid: Float, p: android.graphics.Paint) {
            c.save(); c.rotate(angle, cx, cy)
            c.drawOval(cx - len, cy - wid, cx + len, cy + wid, p)
            c.restore()
        }
        leafAt(hx - w * 0.09f, hy + 2f, 24f, w * 0.10f, w * 0.042f, leafDark)
        leafAt(hx + w * 0.07f, hy + 1f, -26f, w * 0.11f, w * 0.045f, leaf)
        leafAt(hx - w * 0.01f, hy - 2f, -4f, w * 0.09f, w * 0.040f, leaf)
        return bmp
    }

    /** 같은 그림을 비율만 키워 다른 개체처럼 보이게 한다 */
    private fun scaled(src: Bitmap, factor: Float): Bitmap =
        Bitmap.createScaledBitmap(
            src, (src.width * factor).toInt(), (src.height * factor).toInt(), true
        )

    /** 스트립을 프레임 수만큼 균등 분할한다 (크기 조정 없음 — 이미 게임 크기로 만들어진 시트용) */
    private fun splitStrip(
        res: android.content.res.Resources, resId: Int, frames: Int
    ): List<Bitmap> {
        val raw = BitmapFactory.decodeResource(res, resId)
        val fw = raw.width / frames
        return (0 until frames).map { i -> Bitmap.createBitmap(raw, i * fw, 0, fw, raw.height) }
    }

    // 좌우반전 비트맵 캐시: 매 프레임 createBitmap 호출로 인한 GC 압박을 막는다
    private val flippedCache = HashMap<Bitmap, Bitmap>()

    fun getFlipped(bitmap: Bitmap): Bitmap = flippedCache.getOrPut(bitmap) {
        val matrix = android.graphics.Matrix().apply { preScale(-1f, 1f) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
