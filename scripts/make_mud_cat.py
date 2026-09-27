"""머드냥이 그림 — 기획9 `Plan/기획9_머드냥이.jpg` (대표님 2026-09-27).
치즈냥이(cat_normal.png)의 노랑 몸을 진흙 갈색으로, 줄무늬를 짙은 흙색으로 바꾸고 몸에 진흙 얼룩을 몇 점 찍는다.
결과: app/src/main/res/drawable-nodpi/cat_mud.png
사용법: python scripts/make_mud_cat.py
"""
import colorsys, os, sys
import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, 'scripts')
from rebuild_anim import label_components

DST = 'app/src/main/res/drawable-nodpi'
MUD = np.array([150, 102, 60], float) / 255        # 진흙 몸
MUD_DARK = (92, 60, 34)                              # 얼룩


def main():
    im = Image.open(os.path.join(DST, 'cat_normal.png')).convert('RGBA')
    arr = np.array(im).astype(float) / 255
    rgb, a = arr[..., :3], arr[..., 3]
    mx = rgb.max(-1); mn = rgb.min(-1)
    s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    d = np.maximum(mx - mn, 1e-6)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    h = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4)) / 6 % 1
    sel = (a > 0.05) & (s > 0.25) & (h >= 0.03) & (h <= 0.19)       # 노랑~갈색 계열 (몸·줄무늬)
    # 원래 밝기 비율을 살려 진흙색으로 — 밝은 몸은 진흙, 어두운 줄무늬는 더 짙은 흙
    ref = 0xF2 / 255
    k = (mx / ref)[..., None]
    out = arr.copy()
    out[..., :3] = np.where(sel[..., None], np.clip(MUD * k, 0, 1), rgb)
    img = Image.fromarray((out * 255).astype(np.uint8))

    # 몸에 진흙 얼룩 몇 점 (몸통 안에서만)
    m = np.array(img)[..., 3] > 128
    body = sel & m
    comps = sorted(label_components(body), key=lambda t: -t[0])
    ys, xs = comps[0][1], comps[0][2]
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0)); ld = ImageDraw.Draw(layer)
    bw, bh = x1 - x0, y1 - y0
    for u, v, rr in ((0.18, 0.55, 0.07), (0.32, 0.78, 0.05), (0.12, 0.30, 0.045), (0.42, 0.40, 0.04)):
        cx, cy, R = x0 + u * bw, y0 + v * bh, rr * bw
        ld.ellipse([cx - R, cy - R * 0.7, cx + R, cy + R * 0.7], fill=MUD_DARK + (230,))
    la = np.array(layer); mask = np.zeros(m.shape, bool); mask[ys, xs] = True
    la[~mask, 3] = 0
    img.alpha_composite(Image.fromarray(la))
    img.save(os.path.join(DST, 'cat_mud.png'))
    print('cat_mud.png', img.size)


if __name__ == '__main__':
    main()
