package com.jamcat.nayngapo.level

import com.jamcat.nayngapo.engine.Constants
import com.jamcat.nayngapo.entity.*

class Level(val stageNumber: Int) {
    val enemies = mutableListOf<Enemy>()
    val prisons = mutableListOf<Prison>()
    val projectiles = mutableListOf<Projectile>()
    var playerStartX = 200f
    var playerStartY = 500f
    var areaWidth = Constants.PLAY_AREA_RIGHT
    var areaHeight = Constants.PLAY_AREA_BOTTOM

    var currentWave = 0
    var totalWaves = 1
    var totalEnemies = 0
    var defeatedEnemies = 0
    var stageCleared = false
    var bossSpawned = false

    private val waveDefinitions = mutableListOf<List<() -> Enemy>>()

    init {
        when (stageNumber) {
            1 -> setupStage1()
            2 -> setupStage2()
            3 -> setupFinalStage()
        }
    }

    private fun setupStage1() {
        totalWaves = 5
        // Wave 1: 4 normal cats
        waveDefinitions.add(List(4) { { NormalCat(randX(), randY()) } })
        // Wave 2: 5 normal cats
        waveDefinitions.add(List(5) { { NormalCat(randX(), randY()) } })
        // Wave 3: 4 normal + 1 thief
        waveDefinitions.add(
            List(4) { { NormalCat(randX(), randY()) as Enemy } } +
            listOf { ThiefCat(randX(), randY()) as Enemy }
        )
        // Wave 4: 6 normal + 1 thief
        waveDefinitions.add(
            List(6) { { NormalCat(randX(), randY()) as Enemy } } +
            listOf { ThiefCat(randX(), randY()) as Enemy }
        )
        // Wave 5 (Boss): HumanCatBoss
        waveDefinitions.add(listOf { HumanCatBoss(areaWidth / 2f, 300f) as Enemy })

        // Prison
        prisons.add(Prison(areaWidth / 2f - 30f, 350f))

        totalEnemies = waveDefinitions.sumOf { it.size }
        spawnWave()
    }

    private fun setupStage2() {
        totalWaves = 6
        waveDefinitions.add(List(5) { { NormalCat(randX(), randY()) as Enemy } })
        waveDefinitions.add(
            List(4) { { NormalCat(randX(), randY()) as Enemy } } +
            List(2) { { ThiefCat(randX(), randY()) as Enemy } }
        )
        waveDefinitions.add(
            List(3) { { NormalCat(randX(), randY()) as Enemy } } +
            listOf { LaserCat(randX(), randY()) as Enemy }
        )
        waveDefinitions.add(
            List(5) { { NormalCat(randX(), randY()) as Enemy } } +
            List(2) { { ThiefCat(randX(), randY()) as Enemy } }
        )
        waveDefinitions.add(
            List(3) { { ThiefCat(randX(), randY()) as Enemy } } +
            listOf { LaserCat(randX(), randY()) as Enemy }
        )
        // Boss wave
        waveDefinitions.add(listOf { EvilCatBoss(areaWidth / 2f, 300f) as Enemy })

        prisons.add(Prison(300f, 400f))
        prisons.add(Prison(areaWidth - 400f, 600f))

        totalEnemies = waveDefinitions.sumOf { it.size }
        spawnWave()
    }

    private fun setupFinalStage() {
        totalWaves = 7
        waveDefinitions.add(List(6) { { NormalCat(randX(), randY()) as Enemy } })
        waveDefinitions.add(
            List(4) { { ThiefCat(randX(), randY()) as Enemy } }
        )
        waveDefinitions.add(
            List(4) { { NormalCat(randX(), randY()) as Enemy } } +
            List(2) { { LaserCat(randX(), randY()) as Enemy } }
        )
        waveDefinitions.add(
            List(6) { { ThiefCat(randX(), randY()) as Enemy } }
        )
        waveDefinitions.add(
            List(3) { { NormalCat(randX(), randY()) as Enemy } } +
            List(2) { { ThiefCat(randX(), randY()) as Enemy } } +
            List(2) { { LaserCat(randX(), randY()) as Enemy } }
        )
        waveDefinitions.add(
            List(5) { { ThiefCat(randX(), randY()) as Enemy } } +
            List(2) { { LaserCat(randX(), randY()) as Enemy } }
        )
        // Final Boss
        waveDefinitions.add(listOf { WizardCatBoss(areaWidth / 2f, 250f) as Enemy })

        prisons.add(Prison(200f, 350f))
        prisons.add(Prison(areaWidth / 2f, 700f))
        prisons.add(Prison(areaWidth - 300f, 400f))

        totalEnemies = waveDefinitions.sumOf { it.size }
        spawnWave()
    }

    fun spawnWave() {
        if (currentWave >= waveDefinitions.size) {
            stageCleared = true
            return
        }
        val wave = waveDefinitions[currentWave]
        for (factory in wave) {
            enemies.add(factory())
        }
        if (currentWave == waveDefinitions.size - 1) {
            bossSpawned = true
        }
    }

    fun checkWaveComplete(): Boolean {
        if (enemies.all { it.isDead }) {
            currentWave++
            if (currentWave >= waveDefinitions.size) {
                stageCleared = true
            } else {
                spawnWave()
            }
            return true
        }
        return false
    }

    fun onEnemyDefeated() {
        defeatedEnemies++
    }

    private fun randX(): Float =
        Constants.PLAY_AREA_LEFT + 100f + (Math.random() * (areaWidth - 200f)).toFloat()

    private fun randY(): Float =
        Constants.PLAY_AREA_TOP + 80f + (Math.random() * (Constants.PLAY_AREA_BOTTOM - Constants.PLAY_AREA_TOP - 120f)).toFloat()
}
