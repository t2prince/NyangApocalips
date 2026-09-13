"""
GPT가 Cats/ 디자인 시트를 보고 그려 준 냥이 그림들을 게임 리소스로 다듬는다.

- 바깥 흰 배경만 투명으로 (몸 안쪽 흰 눈은 남는다)
- 내용에 딱 맞게 잘라 정해진 키로 축소
- app/src/main/res/drawable-nodpi/ 에 저장 → 기기 밀도와 무관하게 원본 크기로 로드된다

사용법: python scripts/cat_sprites.py [소스 폴더]   (기본 Plan/cat_sprites5)
  예) python scripts/cat_sprites.py Plan/cat_sprites6   # GPT가 깔끔한 스타일로 다시 그린 시트
"""

import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sheet_to_sprites import strip_background  # noqa: E402

SRC_DIR = "Plan/cat_sprites5"
DST_DIR = "app/src/main/res/drawable-nodpi"

# 파일 이름 → (리소스 이름, 화면에 그릴 키(px))
# 크기는 원래 값의 70% (사용자 요청: 냥이가 인간보다 확실히 작게)
CATS = {
    "normal_cat": ("cat_normal", 91),
    "fluffy_cat": ("cat_fluffy", 122),
    "green_cat": ("cat_green", 111),
    "flying_cat": ("cat_flying", 113),
    "anvil_cat": ("cat_anvil", 133),
    "blue_cat": ("cat_blue", 111),
    "poop_cat": ("cat_poop", 118),
    "laser_cat": ("cat_laser", 105),
    "ally_cat": ("cat_ally", 111),
    "boss_evil_cat": ("cat_boss_evil", 147),
    "boss_human_cat": ("cat_boss_human", 133),
    "boss_wizard_cat": ("cat_boss_wizard", 154),
    # 아군 변형(얇은 꼬리)을 GPT가 직접 그려 준 경우. 있으면 ally_tails.py 는 돌리지 않는다.
    "green_ally_cat": ("cat_green_ally", 111),
    "blue_ally_cat": ("cat_blue_ally", 111),
    # 나머지 종의 아군 변형 (적과 같은 그림, 꼬리만 얇음) — 그려지면 자동으로 들어온다
    "laser_ally_cat": ("cat_laser_ally", 105),
    "fluffy_ally_cat": ("cat_fluffy_ally", 122),
    "flying_ally_cat": ("cat_flying_ally", 113),
    "poop_ally_cat": ("cat_poop_ally", 118),
    "anvil_ally_cat": ("cat_anvil_ally", 133),
}


def main():
    global SRC_DIR
    if len(sys.argv) > 1:
        SRC_DIR = sys.argv[1]
    os.makedirs(DST_DIR, exist_ok=True)
    missing = []
    for src_name, (res_name, height) in CATS.items():
        src = os.path.join(SRC_DIR, f"{src_name}.png")
        if not os.path.exists(src):
            missing.append(src_name)
            continue

        img = strip_background(Image.open(src))
        img = img.crop(img.getbbox())
        scale = height / img.height
        img = img.resize((max(1, round(img.width * scale)), height), Image.LANCZOS)

        dst = os.path.join(DST_DIR, f"{res_name}.png")
        img.save(dst)
        print(f"{dst}  {img.width}x{img.height}")

    if missing:
        print("아직 안 그려진 냥이:", ", ".join(missing))


if __name__ == "__main__":
    main()
