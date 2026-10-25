package com.jamcat.nayngapo.engine

import com.jamcat.nayngapo.entity.Enemy
import com.jamcat.nayngapo.entity.EvilCatBoss
import com.jamcat.nayngapo.entity.HumanCatBoss
import com.jamcat.nayngapo.entity.TeleportCatBoss
import com.jamcat.nayngapo.entity.WizardCatBoss

/**
 * 적을 쓰러뜨렸을 때의 보상.
 * 보스는 어느 쪽 클래스인지로 가린다 — Enemy 쪽에 등급 값을 두지 않고 여기서만 판단한다.
 */
object Rewards {

    /** 일반 냥이는 루비 1개, 보스는 5개 */
    const val RUBY_NORMAL = 1
    const val RUBY_BOSS = 5

    fun isBoss(enemy: Enemy): Boolean =
        enemy is HumanCatBoss || enemy is EvilCatBoss ||
            enemy is WizardCatBoss || enemy is TeleportCatBoss || enemy is com.jamcat.nayngapo.entity.PirateCatBoss ||
            enemy is com.jamcat.nayngapo.entity.IcebergCatBoss   // 60 빙산냥이도 루비 5 (2026-10-25)

    fun rubyRewardOf(enemy: Enemy): Int = if (isBoss(enemy)) RUBY_BOSS else RUBY_NORMAL
}
