package com.jamcat.nayngapo.level

import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.engine.Terrain
import com.jamcat.nayngapo.entity.*

/**
 * 플랫포머식 횡스크롤 스테이지: 왼쪽에서 출발해 오른쪽으로만 진행한다.
 * 냥이들은 진행 경로를 따라 구간 그룹으로 등장하고, 보스는 스테이지 맨 끝을 지킨다.
 * 필드의 고양이를 전부 무찌르면 클리어 (보스 게이지 없음).
 */
class Level(val stageNumber: Int) {
    val enemies = mutableListOf<Enemy>()
    val prisons = mutableListOf<Prison>()
    val projectiles = mutableListOf<Projectile>()
    var playerStartX = 200f
    var areaWidth = Constants.PLAY_AREA_RIGHT

    var totalEnemies = 0
    var defeatedEnemies = 0
    val stageCleared: Boolean get() = enemies.isNotEmpty() && enemies.all { it.isDead }

    init {
        // 지형 굴곡 설정: 스테이지가 오를수록 울퉁불퉁해진다
        Terrain.stage = stageNumber

        // 스테이지별 구성: (일반냥이, 도둑냥이, 레이저냥이, 보스들)
        // 보스는 3(인간냥이), 6(사악한냥이), 8(공포의 마법사냥이 3마리·최종)에 등장
        val comp = when (stageNumber) {
            1 -> Composition(12, 0, 0, null)
            2 -> Composition(15, 3, 0, null)
            3 -> Composition(15, 4, 0) { listOf(HumanCatBoss(areaWidth - 900f, Constants.GROUND_Y)) }
            4 -> Composition(18, 6, 0, null)
            5 -> Composition(16, 6, 3, null)
            6 -> Composition(18, 7, 3) { listOf(EvilCatBoss(areaWidth - 900f, Constants.GROUND_Y)) }
            7 -> Composition(20, 8, 5, null)
            else -> Composition(18, 8, 6) {
                listOf(
                    WizardCatBoss(areaWidth - 1500f, Constants.GROUND_Y),
                    WizardCatBoss(areaWidth - 900f, Constants.GROUND_Y),
                    WizardCatBoss(areaWidth - 500f, Constants.GROUND_Y),
                )
            }
        }

        // 잡몹을 섞은 뒤 진행 경로를 따라 3마리씩 구간 그룹으로 배치
        val factories = mutableListOf<(Float, Float) -> Enemy>()
        repeat(comp.normal) { factories.add { x, y -> NormalCat(x, y) } }
        repeat(comp.thief) { factories.add { x, y -> ThiefCat(x, y) } }
        repeat(comp.laser) { factories.add { x, y -> LaserCat(x, y) } }
        factories.shuffle()

        val groupSize = 3
        val groupCount = ((factories.size + groupSize - 1) / groupSize).coerceAtLeast(1)
        val pathStart = 1400f                 // 첫 조우 지점 (시작 지점에서 한 화면쯤 전진)
        val pathEnd = areaWidth - 2400f       // 마지막 잡몹 구간 (보스 구간 앞)
        for ((i, factory) in factories.withIndex()) {
            val g = i / groupSize
            val t = if (groupCount == 1) 0.5f else g / (groupCount - 1).toFloat()
            val gx = pathStart + (pathEnd - pathStart) * t
            val ex = gx + ((Math.random() - 0.5) * 200.0).toFloat()   // 그룹 내 냥이 간격을 좁게
            enemies.add(factory(ex, Constants.GROUND_Y))
        }
        comp.bosses?.let { enemies.addAll(it()) }

        // 감옥: 진행 경로 중간중간 배치 (스테이지가 깊어질수록 늘어난다)
        val prisonCount = 1 + (stageNumber - 1) / 3
        repeat(prisonCount) { i ->
            val px = areaWidth * (i + 1) / (prisonCount + 1).toFloat()
            prisons.add(Prison(px, Terrain.groundY(px)))
        }

        totalEnemies = enemies.size
    }

    private data class Composition(
        val normal: Int, val thief: Int, val laser: Int, val bosses: (() -> List<Enemy>)?
    )

    fun onEnemyDefeated() {
        defeatedEnemies++
    }
}
