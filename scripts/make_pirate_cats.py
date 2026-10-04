"""해적 스테이지(31) 냥이 그림 — 기획13 `Plan/기획13_새냥이들.jpg` (대표님 2026-10-04).
치즈냥이(cat_normal.png, 오른쪽을 봄)에 기획서대로 덧그린다.
- 해적냥이 cat_pirate.png : 회색 해적 모자(챙 + 검은 띠), 한쪽 눈을 가린 검은 안대(끈), 앞다리 대신 갈고리
- 배냥이  cat_boat.png   : 갈색 나무배(널빤지 줄 + 둥근 구멍)에 몸 아래쪽이 들어가 있고, 배 밑으로 다리가 보인다
사용법: python scripts/make_pirate_cats.py
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, 'scripts')
from rebuild_anim import label_components

DST = 'app/src/main/res/drawable-nodpi'
INK = (36, 30, 26, 255)
SS = 4


def info(im):
    a = np.array(im)
    m = a[..., 3] > 128
    rgb = a[..., :3].astype(int)
    ink = m & (rgb.max(axis=2) < 90)
    white = m & (rgb.min(axis=2) > 200)
    comps = sorted(label_components(m & ~ink & ~white), key=lambda t: -t[0])
    ys, xs = comps[0][1], comps[0][2]
    x0, y0, x1, y1 = xs.min(), ys.min(), xs.max(), ys.max()
    bw, bh = x1 - x0, y1 - y0
    H, W = m.shape
    yy, xx = np.mgrid[0:H, 0:W]
    U = (xx - x0) / bw; V = (yy - y0) / bh
    eyes = [(xs_.mean(), ys_.mean()) for n, ys_, xs_ in label_components(ink & (U > 0.55) & (U < 0.95) & (V > 0.02) & (V < 0.30)) if 3 <= n <= 40]
    eyes.sort()
    wy, wx = np.nonzero(white & (U > 0.5))
    muzzle = (wx.min(), wy.min(), wx.max(), wy.max())
    return (x0, y0, x1, y1), eyes, muzzle


def canvas_from(src, pad_top=0, pad_bottom=0, pad_x=0):
    im = Image.new('RGBA', (src.width + pad_x * 2, src.height + pad_top + pad_bottom), (0, 0, 0, 0))
    im.paste(src, (pad_x, pad_top)); return im


def overlay(im):
    ov = Image.new('RGBA', (im.width * SS, im.height * SS), (0, 0, 0, 0))
    return ov, ImageDraw.Draw(ov)


def done(im, ov, name):
    im.alpha_composite(ov.resize(im.size, Image.LANCZOS))
    im = im.crop(im.getbbox())
    im.save(os.path.join(DST, name)); print(name, im.size)
    return im


def make_pirate():
    im = canvas_from(Image.open(os.path.join(DST, 'cat_normal.png')).convert('RGBA'), pad_top=34, pad_bottom=10, pad_x=6)
    (x0, y0, x1, y1), eyes, mz = info(im)
    bw, bh = x1 - x0, y1 - y0
    ov, d = overlay(im)
    P = lambda pts: [(x * SS, y * SS) for x, y in pts]
    # 안대: 앞쪽(오른쪽) 눈을 덮는 검은 타원 + 머리를 비스듬히 가로지르는 끈
    ex, ey = eyes[-1] if eyes else (x0 + bw * 0.78, y0 + bh * 0.11)
    d.line(P([(x0 + bw * 0.50, y0 + 2), (ex + 3, ey), (x1 - 2, ey + bh * 0.20)]), fill=INK, width=SS * 3, joint='curve')
    d.ellipse(P([(ex - 8, ey - 7), (ex + 8, ey + 8)]), fill=INK)
    # 해적 모자: 머리 위에 얹힌 회색 챙 + 그 위 둥근 꼭대기(챙에 딱 붙게) + 검은 띠
    hx = x0 + bw * 0.60; hy = y0 - 8                      # 챙이 눈(안대)을 가리지 않게 머리 꼭대기 위로
    d.chord(P([(hx - 22, hy - 30), (hx + 22, hy + 26)]), 180, 360, fill=(140, 140, 150, 255), outline=INK, width=SS * 2)
    d.rectangle(P([(hx - 21, hy - 8), (hx + 21, hy - 3)]), fill=(40, 40, 44, 255))
    d.ellipse(P([(hx - 36, hy - 6), (hx + 36, hy + 8)]), fill=(120, 120, 128, 255), outline=INK, width=SS * 2)
    # 갈고리 다리: 앞다리 자리에 회색 쇠막대 + 휘어진 갈고리
    lx = x0 + bw * 0.62; ly = y1 - 2
    d.line(P([(lx, ly), (lx, ly + 12)]), fill=(150, 150, 160, 255), width=SS * 4)
    d.arc(P([(lx - 10, ly + 6), (lx + 2, ly + 20)]), 270, 180, fill=(150, 150, 160, 255), width=SS * 3)
    d.arc(P([(lx - 10, ly + 6), (lx + 2, ly + 20)]), 270, 180, fill=INK, width=SS * 1)
    return done(im, ov, 'cat_pirate.png')


def make_boat():
    im = canvas_from(Image.open(os.path.join(DST, 'cat_normal.png')).convert('RGBA'), pad_top=4, pad_bottom=6, pad_x=16)
    (x0, y0, x1, y1), eyes, mz = info(im)
    bw, bh = x1 - x0, y1 - y0
    ov, d = overlay(im)
    P = lambda pts: [(x * SS, y * SS) for x, y in pts]
    # 나무배: 몸 아래 절반을 감싸는 배 — 위 가장자리는 곧고, 앞(오른쪽)·뒤 끝이 위로 솟는다
    top = y0 + bh * 0.52; bot = y0 + bh * 0.86
    l, r = x0 - 14, x1 + 14
    hull = [(l - 4, top - 10), (l + 10, top), (r - 10, top), (r + 6, top - 14), (r - 12, bot), (l + 14, bot)]
    d.polygon(P(hull), fill=(150, 78, 42, 255), outline=INK, width=SS * 2)
    for k in range(1, 3):
        yk = top + (bot - top) * k / 3
        d.line(P([(l + 6 + k * 3, yk), (r - 6 - k * 3, yk)]), fill=(96, 46, 22, 255), width=SS * 2)
    d.line(P([(l + 10, top + 3), (r - 10, top + 3)]), fill=(196, 120, 70, 255), width=SS * 2)   # 뱃전 밝은 테
    return done(im, ov, 'cat_boat.png')


if __name__ == '__main__':
    make_pirate()
    make_boat()
