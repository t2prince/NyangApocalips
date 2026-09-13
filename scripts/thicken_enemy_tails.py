"""적 냥이는 꼬리가 두툼하다 (사용자 규칙).
Plan/cat_frames5/normal_*.png 각 칸에서 몸 뒤쪽(왼쪽) 꼬리를 찾아 굵게 부풀리고 털 뭉치 느낌의 외곽선을 다시 그린다.
원본은 *.before_thick_tail.png 로 백업. 이후 rebuild_anim → 정적/색변환/날개 스크립트를 다시 돌린다.

사용법: python scripts/thicken_enemy_tails.py [normal_idle normal_walk ...]
"""
import os, sys, math
sys.path.insert(0, 'scripts')
import numpy as np
from PIL import Image, ImageDraw
from sheet_to_sprites import strip_background
from rebuild_anim import label_components
from remove_front_tail import dilate

SRC_DIR = 'Plan/cat_frames5'
COLS, ROWS = 4, 2
OUTLINE = 8
GROW = 9           # 꼬리를 이만큼(px) 부풀린다
INK = (25, 21, 19, 255)
BODY = (242, 192, 66, 255)


def thicken_cell(sheet, alpha, x0, y0, cw, ch):
    m = alpha[y0:y0 + ch, x0:x0 + cw]
    cell = sheet[y0:y0 + ch, x0:x0 + cw]
    rgb = cell[..., :3].astype(int)
    ink = m & (rgb.sum(axis=2) / 3 < 110)
    comps = sorted(label_components(m & ~ink), key=lambda t: -t[0])
    if not comps: return False
    body = np.zeros_like(m); body[comps[0][1], comps[0][2]] = True
    x_bl = comps[0][2].min()
    yellow = (rgb[..., 0] > 190) & (rgb[..., 1] > 130) & (rgb[..., 2] < 130)
    tail = np.zeros_like(m)
    for area, ys, xs in comps[1:]:
        if area < 300 or yellow[ys, xs].mean() < 0.5: continue
        if xs.mean() >= x_bl + 5: continue
        tail[ys, xs] = True
    if not tail.any(): return False
    protect = dilate(body, OUTLINE)                       # 몸통 속 + 몸통 외곽선은 건드리지 않는다
    old = dilate(tail, OUTLINE + 1) & m & ~protect        # 지금 꼬리(속+선)
    fat = dilate(tail, GROW + OUTLINE) & ~protect         # 부풀린 꼬리 전체
    fat_in = dilate(tail, GROW) & ~protect                # 부풀린 꼬리 속
    # 털 뭉치: 바깥 경계를 따라 작은 원을 덧붙여 울퉁불퉁하게
    ring = fat & ~dilate(tail, GROW + OUTLINE - 2)
    ys, xs = np.nonzero(ring)
    bumps = np.zeros_like(m)
    if len(ys):
        rng = np.random.default_rng(int(x0 + y0))
        idx = rng.choice(len(ys), size=max(6, len(ys) // 40), replace=False)
        yy, xx = np.mgrid[0:ch, 0:cw]
        for i in idx:
            r = rng.integers(5, 9)
            bumps |= (yy - ys[i]) ** 2 + (xx - xs[i]) ** 2 <= r * r
        bumps &= ~protect
    fat |= bumps
    fat_in |= bumps & ~(fat & ~dilate(fat_in | bumps, 0))
    # 칠하기: 속은 몸색, 그 바깥 테두리는 잉크
    inner = dilate(tail, GROW) | (bumps & ~(bumps & ~dilate(bumps, 0)))
    inner &= ~protect
    fill = fat & ~protect
    edge = fill & ~erode_like(fill, OUTLINE - 1)
    cell[old] = (0, 0, 0, 0)
    cell[fill] = BODY
    cell[edge] = INK
    # 배경이 흰 시트면 지운 자리도 흰색으로
    return True


def erode_like(mask, r):
    return ~dilate(~mask, r)


def main():
    names = sys.argv[1:] or ['normal_idle', 'normal_walk', 'normal_jump', 'normal_attack']
    for name in names:
        path = os.path.join(SRC_DIR, f'{name}.png')
        backup = os.path.join(SRC_DIR, f'{name}.before_thick_tail.png')
        if not os.path.exists(backup):
            Image.open(path).save(backup)
        orig = Image.open(backup).convert('RGBA')
        white_bg = np.array(orig)[..., 3].min() > 250
        stripped = strip_background(orig)
        sheet = np.array(stripped).copy()
        alpha = sheet[..., 3] > 128
        cw, ch = orig.width // COLS, orig.height // ROWS
        done = 0
        for r in range(ROWS):
            for c in range(COLS):
                if thicken_cell(sheet, alpha, c * cw, r * ch, cw, ch): done += 1
        out = Image.fromarray(sheet)
        if white_bg:   # 원래 흰 배경 시트였으면 흰 배경으로 되돌린다 (cells_of 가 다시 벗긴다)
            bg = Image.new('RGBA', out.size, (255, 255, 255, 255)); bg.alpha_composite(out); out = bg
        out.save(path)
        print(name, 'thickened cells:', done)


if __name__ == '__main__':
    main()
