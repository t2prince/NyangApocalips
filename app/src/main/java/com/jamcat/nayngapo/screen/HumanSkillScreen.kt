package com.jamcat.nayngapo.screen

import com.jamcat.nayngapo.engine.GameView
import com.jamcat.nayngapo.engine.HumanSkills
import com.jamcat.nayngapo.engine.SpriteManager

/**
 * 기술 — 인간이 직접 쓰는 기술을 루비로 배운다 (로비 아래 줄 '기술').
 * 고양이 기술과 같은 모양·동작이며 SkillBookScreen에 있다.
 */
class HumanSkillScreen(gameView: GameView) : SkillBookScreen(
    gameView, HumanSkills, { SpriteManager.humanSkillIcons },
    title = "기술",
    hint = "배운 기술은 전투 화면의 인간 기술 버튼으로 써요"
)
