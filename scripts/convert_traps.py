"""함정 GPT 그림 (Plan/traps) → 게임 리소스 trap_*.png (2026-11-08 대표님 「함정 이미지 다시 그려줘 — 다」).
흰 배경을 투명으로 하고 내용에 맞춰 자른다. 구덩이는 위의 경고 표지판을 떼어 내고 흙 틀만 남긴다
(경고 표지는 코드가 그린다). 지면선(위 가장자리) 위치는 코드의 상수와 맞춘다.
사용법: python scripts/convert_traps.py
"""
import os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sheet_to_sprites import strip_background  # noqa: E402

SRC = 'Plan/traps'
DST = 'app/src/main/res/drawable-nodpi'
# 원본 → (리소스, 높이 px)
ITEMS = {
    'trap_spikes': 120,
    'trap_pit': 300,
    'trap_swamp': 140,
    'trap_swamp_honey': 140,
    'trap_ice_hole': 110,
    'trap_ice_hole_broken': 300,
    'trap_cliff_left': 600,
    'trap_shaft': 600,
    'trap_tornado': 360,
}


def drop_top_islands(img):
    """위쪽에 따로 떠 있는 덩어리(구덩이 위 경고 표지)를 떼어 낸다 — 위에서부터 첫 '빈 줄' 아래만 남긴다"""
    a = np.array(img)
    rows = (a[..., 3] > 0).any(axis=1)
    ys = np.nonzero(rows)[0]
    gap = None
    for y in range(ys[0], ys[-1]):
        if not rows[y]:
            gap = y
            break
    if gap is not None and gap < a.shape[0] * 0.45:
        a[:gap, :, 3] = 0
    return Image.fromarray(a)


for name, h in ITEMS.items():
    img = strip_background(Image.open(os.path.join(SRC, name + '.png')))
    if name == 'trap_pit':
        img = drop_top_islands(img)
    img = img.crop(img.getbbox())
    img = img.resize((max(1, round(img.width * h / img.height)), h), Image.LANCZOS)
    img.save(os.path.join(DST, name + '.png'))
    print(name, img.size)
