"""도둑냥이 그림 — 치즈냥이에 도둑 눈가리개(검은 띠 + 눈구멍)와 등에 멘 보따리를 얹는다 (대표님 2026-09-20 「도둑처럼 생겼고」).
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
    # 눈가리개 띠: 얼굴 폭을 가로지르는 검은 띠, 양 끝은 머리 뒤로 묶은 매듭
    band = [int(x0 + bw * 0.52), ey0 - pad, int(x0 + bw * 1.01), ey1 + pad]
    d.rounded_rectangle(band, radius=pad, fill=INK)
    # 눈구멍: 눈보다 조금 큰 흰 타원, 그 안에 원래 눈점
    r = max(2, int((ey1 - ey0) * 0.9))
    for y_, x_ in eyes:
        cx, cy = (x_.min() + x_.max()) / 2, (y_.min() + y_.max()) / 2
        d.ellipse([cx - r - 1.5, cy - r, cx + r + 1.5, cy + r], fill=(250, 250, 245))
        d.ellipse([cx - 1.2, cy - 1.2, cx + 1.2, cy + 1.2], fill=INK)
    # 매듭 꼬리 (머리 뒤로 두 갈래)
    kx, ky = int(x0 + bw * 0.50), (ey0 + ey1) // 2
    d.polygon([(kx, ky - pad), (kx - bw * 0.10, ky - pad * 2.2), (kx - bw * 0.04, ky)], fill=INK)
    d.polygon([(kx, ky + pad), (kx - bw * 0.11, ky + pad * 1.6), (kx - bw * 0.04, ky)], fill=INK)
    # 보따리: 등 위(꼬리 앞)에 멘 갈색 주머니 + 묶은 목
    sx, sy = x0 + bw * 0.22, y0 + bh * 0.08
    sr = bw * 0.13
    d.ellipse([sx - sr, sy - sr * 0.9, sx + sr, sy + sr * 0.9], fill=(196, 140, 80), outline=INK, width=2)
    d.polygon([(sx + sr * 0.5, sy - sr * 0.8), (sx + sr * 0.95, sy - sr * 1.35), (sx + sr * 1.15, sy - sr * 0.75)], fill=(196, 140, 80), outline=INK)
    d.line([(sx - sr * 0.4, sy - sr * 0.1), (sx + sr * 0.3, sy + sr * 0.3)], fill=(150, 100, 55), width=2)
    im.save(os.path.join(DST, 'cat_thief.png')); print('cat_thief.png')

if __name__ == '__main__':
    main()
