"""도둑냥이 그림 — 치즈냥이에 도둑 눈가리개(도미노 가면)를 씌운다 — 보따리는 없다 (대표님 2026-09-20 「도둑처럼 생겼고」).
cat_normal.png -> cat_thief.png.  사용법: python scripts/make_thief_cat.py
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, 'scripts')
from rebuild_anim import label_components

DST = 'app/src/main/res/drawable-nodpi'
INK = (40, 40, 48)

def main():
    im = Image.open(os.path.join(DST, 'cat_normal.png')).convert('RGBA')
    arr = np.array(im)
    a = arr[..., 3] > 128
    rgb = arr[..., :3].astype(int)
    ink = a & (rgb.max(axis=2) < 90)
    white = a & (rgb.min(axis=2) > 200)
    comps = sorted(label_components(a & ~ink & ~white), key=lambda t: -t[0])
    ys, xs = comps[0][1], comps[0][2]
    x0, y0, x1, y1 = xs.min(), ys.min(), xs.max(), ys.max()
    bw, bh = x1 - x0, y1 - y0
    H, W = a.shape
    yy, xx = np.mgrid[0:H, 0:W]
    U = (xx - x0) / bw; V = (yy - y0) / bh
    # 눈 = 얼굴 위쪽의 작은 검은 점
    eyes = [(ys_, xs_) for n, ys_, xs_ in label_components(ink & (U > 0.55) & (V > 0.04) & (V < 0.42)) if 3 <= n <= 60]
    if eyes:
        ey0 = min(y.min() for y, _ in eyes); ey1 = max(y.max() for y, _ in eyes)
        ex0 = min(x.min() for _, x in eyes); ex1 = max(x.max() for _, x in eyes)
    else:
        ey0, ey1 = int(y0 + bh * 0.16), int(y0 + bh * 0.24); ex0, ex1 = int(x0 + bw * 0.66), int(x0 + bw * 0.86)
    d = ImageDraw.Draw(im)
    pad = max(3, int(bh * 0.05))
    # 도둑 눈가리개 (원안 Cats/cat_thief_mask.jpeg): 눈 둘레를 감싸는 도미노 가면 — 두 눈구멍이 크고 하얗게 뚫려 있고,
    # 양쪽 끝이 뾰족하게 머리 뒤로 넘어간다
    if eyes:
        cs = [((x_.min() + x_.max()) / 2, (y_.min() + y_.max()) / 2) for y_, x_ in eyes]
    else:
        cs = [(x0 + bw * 0.70, y0 + bh * 0.20), (x0 + bw * 0.84, y0 + bh * 0.20)]
    cy = sum(c[1] for c in cs) / len(cs)
    hole_r = max(3, int(bh * 0.055))
    mh = hole_r * 1.6                                   # 가면 반높이
    left, right = x0 + bw * 0.46, x0 + bw * 1.02
    mask = [(left, cy), (left + bw * 0.10, cy - mh), (right - bw * 0.06, cy - mh * 1.05), (right, cy - mh * 0.3),
            (right - bw * 0.02, cy + mh * 0.6), (right - bw * 0.10, cy + mh), (left + bw * 0.12, cy + mh * 0.9)]
    d.polygon(mask, fill=INK, outline=INK)
    for cx_, cy_ in cs:                                 # 눈구멍: 크고 하얀 타원 + 원래 눈점
        d.ellipse([cx_ - hole_r * 1.25, cy_ - hole_r, cx_ + hole_r * 1.25, cy_ + hole_r], fill=(250, 250, 245))
        d.ellipse([cx_ - 1.3, cy_ - 1.3, cx_ + 1.3, cy_ + 1.3], fill=INK)
    # 매듭 꼬리 (머리 뒤로 두 갈래)
    kx, ky = int(left), int(cy)
    d.polygon([(kx + 2, ky - 2), (kx - bw * 0.10, ky - mh * 1.3), (kx - bw * 0.03, ky + 1)], fill=INK)
    d.polygon([(kx + 2, ky + 2), (kx - bw * 0.11, ky + mh * 0.9), (kx - bw * 0.03, ky - 1)], fill=INK)
    # 보따리는 없다 (대표님 2026-09-20)
    im.save(os.path.join(DST, 'cat_thief.png')); print('cat_thief.png')

if __name__ == '__main__':
    main()
