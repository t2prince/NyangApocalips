package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.CatSkills
import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.SpriteManager

/**
 * 고양이 기술 — 루비로 배우고, 출전한 냥이가 전투 중 버튼으로 쓴다.
 * 모양과 동작은 SkillBookScreen에 있다.
 */
class CatSkillScreen(gameView: GameView) : SkillBookScreen(
    gameView, CatSkills, { SpriteManager.skillIcons },
    title = "고양이 기술",
    hint = "출전한 냥이가 전투 중 버튼으로 기술을 써요"
)
