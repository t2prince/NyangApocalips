"""cat_frames5/normal_attack.png 에서 냥냥펀치 팔이 나온 칸(4·5번)의 가장 앞쪽 앞발 하나를 지운다.
(사용자 규칙: 팔이 생기는 대신 그 밑의 발 하나가 사라지고, 펀치가 끝나면 다시 생긴다)

사용법: python scripts/remove_punch_leg.py [출력.png] [디버그.png]
"""
import sys
sys.path.insert(0, 'scripts')
import numpy as np
from PIL import Image
from sheet_to_sprites import strip_background
from rebuild_anim import label_components
from remove_front_tail import dilate

SRC = 'Plan/cat_frames5/normal_attack.png'
OUT = sys.argv[1] if len(sys.argv) > 1 else SRC
DBG = sys.argv[2] if len(sys.argv) > 2 else 'Plan/_debug/normal_attack_leg_debug.png'
COLS, ROWS = 4, 2
PUNCH_CELLS = [4, 5]     # 1부터 세는 칸 번호
OUTLINE = 8

orig = Image.open(SRC).convert('RGBA')
sheet = np.array(orig).copy()
alpha = np.array(strip_background(orig))[..., 3] > 128
cw, ch = orig.width // COLS, orig.height // ROWS
dbg_arr = sheet.copy()

for idx in PUNCH_CELLS:
    r, c = divmod(idx - 1, COLS)
    y0, x0 = r * ch, c * cw
    m = alpha[y0:y0 + ch, x0:x0 + cw]
    rgb = sheet[y0:y0 + ch, x0:x0 + cw, :3].astype(int)
    ink = m & (rgb.sum(axis=2) / 3 < 110)
    comps = sorted(label_components(m & ~ink), key=lambda t: -t[0])
    body = np.zeros_like(m); body[comps[0][1], comps[0][2]] = True
    y_bb = comps[0][1].max()
    limbs = m & ~dilate(body, OUTLINE + 1)
    legs = [(ys, xs) for area, ys, xs in label_components(limbs)
            if area >= 80 and ys.mean() > y_bb - 10]        # 몸통 아래쪽에 달린 것 = 다리
    if not legs:
        print('cell', idx, 'no legs found'); continue
    ys, xs = max(legs, key=lambda t: t[1].mean())          # 가장 오른쪽(앞쪽) 다리
    leg = np.zeros_like(m); leg[ys, xs] = True
    erase = dilate(leg, 2) & m & ~dilate(body, OUTLINE)
    erase |= dilate(erase, 8) & ~(m & ~erase)              # 옅은 테두리 잔상
    cell = sheet[y0:y0 + ch, x0:x0 + cw]; cell[erase] = (255, 255, 255, 255)
    d = dbg_arr[y0:y0 + ch, x0:x0 + cw]; d[erase] = (255, 0, 0, 255)
    print('cell', idx, 'legs', len(legs), 'erased', int(erase.sum()))

Image.fromarray(sheet).save(OUT)
Image.fromarray(dbg_arr).save(DBG)
