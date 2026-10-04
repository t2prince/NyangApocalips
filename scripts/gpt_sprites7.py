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

def clear_ship_pockets(img):
    """해적선: 밧줄·돛대·선체 사이에 갇힌 흰 바탕, 그물 구멍, 철창 창살 사이를 투명하게 한다.
    돛 안(돛 상자 안에 가운데가 있는 흰 칸)은 돛·고양이 얼굴·뼈라서 남긴다. 좌표는 760 높이 기준."""
    import numpy as np
    from rebuild_anim import label_components
    a = np.array(img)
    H, W = a.shape[:2]
    k = H / 760.0
    white = (a[..., 3] > 0) & (a[..., :3].min(axis=2) > 215)
    sail = (490 * k, 125 * k, 940 * k, 415 * k)
    boxes = [(340 * k, 380 * k, 560 * k, 570 * k), (890 * k, 375 * k, 1100 * k, 530 * k),   # 그물
             (565 * k, 420 * k, 695 * k, 570 * k), (745 * k, 420 * k, 875 * k, 570 * k)]    # 철창
    inside = lambda bx, x, y: bx[0] <= x <= bx[2] and bx[1] <= y <= bx[3]
    for n, ys, xs in label_components(white):
        cx, cy = xs.mean(), ys.mean()
        if inside(sail, cx, cy) and not any(inside(b, cx, cy) for b in boxes):
            continue                                   # 돛 속 흰 칸은 남긴다
        if any(inside(b, cx, cy) for b in boxes) or n > 400:
            a[ys, xs, 3] = 0
    return Image.fromarray(a)


for name, (res, h) in ITEMS.items():
    p = os.path.join(SRC, name + '.png')
    if not os.path.exists(p):
        print('없음:', name); continue
    img = strip_background(Image.open(p))
    img = img.crop(img.getbbox())
    img = img.resize((max(1, round(img.width * h / img.height)), h), Image.LANCZOS)
    if name == 'pirate_ship':
        img = clear_ship_pockets(img)
    img.save(os.path.join(DST, res + '.png'))
    print(res, img.size)
