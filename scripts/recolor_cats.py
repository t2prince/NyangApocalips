"""색만 다른 냥이들을 일반 냥이 그림에서 만든다 (사용자 규칙: 그린냥이 = 일반 냥이의 노란 계열을 초록 계열로).

- cat_green.png       <- cat_normal.png 의 노랑·갈색 계열을 초록 계열로
- cat_green_ally.png  <- cat_ally.png (얇은 꼬리) 를 같은 방식으로
- cat_blue.png / cat_blue_ally.png <- 같은 방식으로 파란 계열 (사용자 규칙: 파랑냥이 = 냥이 색을 파란색으로)

사용법: python scripts/recolor_cats.py
"""
import colorsys, os
import numpy as np
from PIL import Image

DST = 'app/src/main/res/drawable-nodpi'

# 노랑(몸) #F2C13C -> 초록 #5FBF4F, 갈색(줄무늬) #7E5222 -> 진초록 #2F7A2A 로 가는 색상 회전
YELLOW_H = colorsys.rgb_to_hsv(0xF2 / 255, 0xC1 / 255, 0x3C / 255)[0]
GREEN_H = colorsys.rgb_to_hsv(0x5F / 255, 0xBF / 255, 0x4F / 255)[0]
BLUE_H = colorsys.rgb_to_hsv(0x4F / 255, 0x86 / 255, 0xD6 / 255)[0]     # 파랑냥이 몸 #4F86D6
# 빨강·주황·노랑냥이 (대표님 2026-09-20 「그린·파랑처럼 일반 냥이 색만 바꾼 것」)
RED_H = colorsys.rgb_to_hsv(0xE0 / 255, 0x48 / 255, 0x48 / 255)[0]      # 빨강냥이 몸 #E04848
ORANGE_H = colorsys.rgb_to_hsv(0xF2 / 255, 0x8C / 255, 0x28 / 255)[0]   # 주황냥이 몸 #F28C28
LEMON_H = colorsys.rgb_to_hsv(0xF2 / 255, 0xE6 / 255, 0x3C / 255)[0]    # 노랑냥이 몸 #F2E63C (치즈냥이보다 샛노랑)

def shift_hue(src, dst, h_shift, sat_min=0.25, h_lo=0.03, h_hi=0.19):
    im = Image.open(src).convert('RGBA')
    arr = np.array(im).astype(np.float32) / 255
    r, g, b, a = arr[..., 0], arr[..., 1], arr[..., 2], arr[..., 3]
    mx = arr[..., :3].max(-1); mn = arr[..., :3].min(-1)
    v = mx; s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    # hue 계산
    d = np.maximum(mx - mn, 1e-6)
    h = np.zeros_like(mx)
    h = np.where(mx == r, ((g - b) / d) % 6, h)
    h = np.where(mx == g, ((b - r) / d) + 2, h)
    h = np.where(mx == b, ((r - g) / d) + 4, h)
    h = (h / 6) % 1
    sel = (a > 0.05) & (s > sat_min) & (h >= h_lo) & (h <= h_hi)     # 노랑~갈색(주황) 계열만
    h2 = (h + h_shift) % 1
    out = arr.copy()
    ys, xs = np.nonzero(sel)
    for y, x in zip(ys, xs):
        rr, gg, bb = colorsys.hsv_to_rgb(h2[y, x], min(1, s[y, x] * 0.95), v[y, x] * 0.92)
        out[y, x, 0], out[y, x, 1], out[y, x, 2] = rr, gg, bb
    Image.fromarray((out * 255).astype(np.uint8)).save(dst)
    print(dst)

PINK = (240, 96, 160)

def add_center_stripe(src, dst, color=PINK, width_frac=0.20):
    """몸통(가장 큰 색 영역) 한가운데를 가로지르는 두꺼운 가로 띠. 순간냥이 = 그린냥이 + 분홍 가로줄 (사용자 규칙)."""
    import sys; sys.path.insert(0, 'scripts')
    from rebuild_anim import label_components
    im = Image.open(src).convert('RGBA'); arr = np.array(im)
    m = arr[..., 3] > 128
    rgb = arr[..., :3].astype(int)
    ink = m & (rgb.max(axis=2) < 90)              # 진짜 검은 선만 (진초록 줄무늬는 띠로 덮는다)
    white = m & (rgb.min(axis=2) > 200)
    comps = sorted(label_components(m & ~ink & ~white), key=lambda t: -t[0])
    ys, xs = comps[0][1], comps[0][2]                     # 몸통 속
    body = np.zeros_like(m); body[ys, xs] = True
    cy = (ys.min() + ys.max()) / 2
    half = (ys.max() - ys.min()) * width_frac / 2
    rows = np.abs(np.arange(m.shape[0])[:, None] - cy) <= half
    cols = np.zeros_like(m); cols[:, xs.min():xs.max() + 1] = True
    # 띠 안은 줄무늬까지 전부 분홍, 흰 주둥이·잉크 선은 남긴다 (몸통 밖 꼬리·다리는 제외)
    body_cols = np.zeros_like(m); body_cols[:, xs.min():xs.max() + 1] = True
    fill = rows & body_cols & m & ~ink & ~white
    arr[fill, 0], arr[fill, 1], arr[fill, 2] = color
    # 띠 양옆에 가는 진한 분홍 선으로 경계
    edge = fill & ~np.roll(fill, 1, axis=0) | fill & ~np.roll(fill, -1, axis=0)
    arr[edge, 0], arr[edge, 1], arr[edge, 2] = (190, 50, 110)
    Image.fromarray(arr).save(dst); print(dst)

def main():
    for name, hue in (('green', GREEN_H), ('blue', BLUE_H), ('red', RED_H), ('orange', ORANGE_H), ('yellow', LEMON_H)):
        shift = hue - YELLOW_H
        shift_hue(os.path.join(DST, 'cat_normal.png'), os.path.join(DST, f'cat_{name}.png'), shift)
        shift_hue(os.path.join(DST, 'cat_ally.png'), os.path.join(DST, f'cat_{name}_ally.png'), shift)
        # 대기(춤) 스트립도 같은 색으로 — 도감·고양이 관리에서 춤추는 데 쓴다
        shift_hue(os.path.join(DST, 'anim_cat_idle.png'), os.path.join(DST, f'anim_cat_idle_{name}.png'), shift)
    # 순간냥이(적, 보스) = 그린냥이 + 가운데 두꺼운 분홍 가로줄
    add_center_stripe(os.path.join(DST, 'cat_green.png'), os.path.join(DST, 'cat_teleport.png'))

if __name__ == '__main__':
    main()
