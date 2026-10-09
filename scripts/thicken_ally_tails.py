"""내 편 냥이 꼬리(가는 선)를 조금 두껍게 (대표님 2026-10-09 「같은편 고양이 꼬리가 과하게 얇은데, 두껍게 좀」).
원본은 Plan/ally_before_thick_tail/ 에 한 번 백업해 두고, 늘 그 백업을 읽어 다시 만든다 (여러 번 돌려도 계속 두꺼워지지 않게).
꼬리 찾기: 몸 왼쪽 위(가로 35% 안쪽·세로 75% 위쪽)에서 굵기 3px 이하인 가는 선 = 꼬리. 그 선을 바깥으로 넓혀 같은 색으로 칠한다.
사용법: python scripts/thicken_ally_tails.py
"""
import os, shutil
import numpy as np
from PIL import Image, ImageFilter

DST = 'app/src/main/res/drawable-nodpi'
BAK = 'Plan/ally_before_thick_tail'
NAMES = ['cat_ally', 'cat_white_ally', 'cat_calico_ally', 'cat_tuxedo_ally', 'cat_mackerel_ally', 'cat_bicolor_ally',
         'cat_green_ally', 'cat_blue_ally', 'cat_gun_ally', 'cat_flying_ally', 'cat_red_ally', 'cat_orange_ally', 'cat_yellow_ally']
GROW = 1          # 바깥으로 넓힐 픽셀 (선 굵기가 약 두 배)
PAD = 3           # 넓힌 꼬리가 그림 가장자리에서 잘리지 않게 왼쪽·위에 여백


def thicken(im, flying=False):
    src = im.convert('RGBA')
    big = Image.new('RGBA', (src.width + PAD, src.height + PAD), (0, 0, 0, 0)); big.paste(src, (PAD, PAD))
    a = np.array(big)
    H, W = a.shape[:2]
    m = a[..., 3] > 100
    mi = Image.fromarray((m * 255).astype(np.uint8))
    opened = np.array(mi.filter(ImageFilter.MinFilter(5)).filter(ImageFilter.MaxFilter(5))) > 0   # 굵은 덩어리만 남는다
    thin = m & ~opened
    yy, xx = np.mgrid[0:H, 0:W]
    if flying:   # 날개가 넓어 꼬리는 몸 바로 뒤 위쪽 좁은 칸에서만 찾는다
        tail = thin & (xx > W * 0.26) & (xx < W * 0.42) & (yy < H * 0.40)
    else:        # 꼬리는 몸 뒤 위쪽 — 몸통 가운데 높이 아래는 다리·수염이라 뺀다
        tail = thin & (xx < W * 0.30) & (yy < H * 0.55)
    if not tail.any():
        return im, 0
    col = np.median(a[tail][:, :3], axis=0).astype(np.uint8)          # 꼬리 선 색 (보통 검정)
    grown = np.array(Image.fromarray((tail * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(GROW * 2 + 1))) > 0
    add = grown & ~opened
    a[add, :3] = col; a[add, 3] = 255
    return Image.fromarray(a), int(tail.sum())


os.makedirs(BAK, exist_ok=True)
for n in NAMES:
    src = os.path.join(DST, n + '.png'); bak = os.path.join(BAK, n + '.png')
    if not os.path.exists(src): continue
    if not os.path.exists(bak): shutil.copy(src, bak)
    out, k = thicken(Image.open(bak), flying='flying' in n)
    out.save(src); print(n, 'tail px', k)
