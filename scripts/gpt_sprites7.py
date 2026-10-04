"""GPT(Codex 이미지 생성)가 그린 새 냥이들 → 게임 리소스 (2026-10-04 「그림은 지피티에게 맡겨서 그려」).
원본: Plan/cat_sprites7/*.png (1254×1254 흰 바탕). 바깥 흰 배경만 투명으로 하고 내용에 맞춰 자른 뒤 정해진 키로 줄인다.
(Plan/cat_sprites6 는 예전 시트라 건드리지 않는다 — cat_sprites.py 를 거기에 돌리면 기존 냥이들이 덮어써진다)
사용법: python scripts/gpt_sprites7.py
"""
import os, sys
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sheet_to_sprites import strip_background  # noqa: E402

SRC = 'Plan/cat_sprites7'
DST = 'app/src/main/res/drawable-nodpi'
# 원본 이름 → (리소스 이름, 높이 px). 꽃냥이는 대표님이 「이전 이미지로」 해서 GPT 그림을 쓰지 않는다.
ITEMS = {
    'bee_cat': ('cat_bee', 100),
    'mud_cat': ('cat_mud', 86),
    'pirate_cat': ('cat_pirate', 118),
    'boat_cat': ('cat_boat', 92),
    'pirate_ship': ('pirate_ship', 760),
}

for name, (res, h) in ITEMS.items():
    p = os.path.join(SRC, name + '.png')
    if not os.path.exists(p):
        print('없음:', name); continue
    img = strip_background(Image.open(p))
    img = img.crop(img.getbbox())
    img = img.resize((max(1, round(img.width * h / img.height)), h), Image.LANCZOS)
    img.save(os.path.join(DST, res + '.png'))
    print(res, img.size)
