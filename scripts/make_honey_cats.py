"""꿀 스테이지(21) 냥이 그림 — 기획10 `Plan/기획10_꿀스테이지_벌냥이_꽃냥이.jpg` (대표님 2026-09-27).
치즈냥이(cat_normal.png, 오른쪽을 봄)를 바탕으로 기획서대로 덧그린다.
- 벌냥이 cat_bee.png : 노란 몸에 굵은 검은 세로 줄 두 개, 등 위 파란 날개 한 쌍, 엉덩이(왼쪽 끝)에 검은 침
- 꽃냥이 cat_flower.png : 주황 가는 줄무늬를 더한 노란 몸, 얼굴(주둥이 위)을 덮는 초록 잎, 머리 위로 휘어 올라간 분홍 꽃대 두 개와 꽃
날개는 전투 중 코드에서도 파닥이게 덧그린다(여기 그림은 도감·정지 모습).
사용법: python scripts/make_honey_cats.py
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, 'scripts')
from rebuild_anim import label_components

DST = 'app/src/main/res/drawable-nodpi'
INK = (36, 30, 26, 255)
SS = 4


def body_info(im):
    a = np.array(im)
    m = a[..., 3] > 128
    rgb = a[..., :3].astype(int)
    ink = m & (rgb.max(axis=2) < 90)
    white = m & (rgb.min(axis=2) > 200)
    comps = sorted(label_components(m & ~ink & ~white), key=lambda t: -t[0])
    ys, xs = comps[0][1], comps[0][2]
    body = np.zeros_like(m); body[ys, xs] = True
    wy, wx = np.nonzero(white & (np.arange(m.shape[1])[None, :] > m.shape[1] * 0.45))
    muzzle = (wx.min(), wy.min(), wx.max(), wy.max()) if len(wx) else None
    return body, (xs.min(), ys.min(), xs.max(), ys.max()), muzzle


def overlay(size):
    return Image.new('RGBA', (size[0] * SS, size[1] * SS), (0, 0, 0, 0))


def finish(base, ov, clip=None):
    ov = ov.resize(base.size, Image.LANCZOS)
    if clip is not None:
        a = np.array(ov); a[~clip, 3] = 0; ov = Image.fromarray(a)
    out = base.copy(); out.alpha_composite(ov); return out


def remove_tail(src):
    """벌냥이는 꼬리가 없다 (대표님 2026-10-04). 치즈냥이 몸통 달걀꼴 왼쪽 위 바깥(꼬리 자리)만 지우고,
    잘린 자리에 몸 외곽선을 다시 그린다."""
    a = np.array(src)
    H, W = a.shape[:2]
    # 몸통 달걀꼴(엉덩이까지 넉넉히): 꼬리가 붙은 윗부분(y < 56)에서만 그 바깥을 지운다 — 엉덩이 아래쪽은 그대로
    cx, cy, rx, ry = 70.0, 44.0, 51.0, 38.0
    yy, xx = np.mgrid[0:H, 0:W]
    outside = ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 > 1.0
    tail = outside & (xx < cx - 10) & (yy < 56)
    a[tail, 3] = 0
    # 잘린 자리(지운 칸과 맞닿은 몸 가장자리)에 외곽선을 2px 로 다시 칠한다 — 몸 밖으로 선이 뜨지 않게
    keep = a[..., 3] > 100
    near_cut = np.zeros_like(keep)
    for dy in (-2, -1, 0, 1, 2):
        for dx in (-2, -1, 0, 1, 2):
            near_cut |= np.roll(np.roll(tail, dy, 0), dx, 1)
    a[keep & near_cut, :3] = INK[:3]
    out = Image.fromarray(a)
    return out


def make_bee():
    src = remove_tail(Image.open(os.path.join(DST, 'cat_normal.png')).convert('RGBA'))
    pad = 30                                                   # 날개가 위로, 침이 왼쪽으로 삐져나올 자리
    im = Image.new('RGBA', (src.width + pad, src.height + pad), (0, 0, 0, 0)); im.paste(src, (pad, pad))
    body, (x0, y0, x1, y1), mz = body_info(im)
    bw, bh = x1 - x0, y1 - y0
    P = lambda pts: [(x * SS, y * SS) for x, y in pts]
    # 1) 몸통을 감싸 도는 둥근 검은 띠 두 개 — 몸이 둥그니 띠도 바깥(엉덩이 쪽)으로 휘어진다 (대표님 2026-10-04 「직선 말고 둥글게」)
    ov = overlay(im.size); d = ImageDraw.Draw(ov)
    for u in (0.30, 0.48):
        cx = x0 + u * bw
        bow = -bw * 0.10                                  # 가운데가 엉덩이 쪽으로 볼록
        half = bw * 0.038                                # 「줄무늬 두께 줄여줘」 2026-10-04
        ts = np.linspace(0, 1, 24)
        left = [(cx + bow * np.sin(np.pi * t) - half, y0 - 6 + (bh + 12) * t) for t in ts]
        right = [(cx + bow * np.sin(np.pi * t) + half, y0 - 6 + (bh + 12) * t) for t in ts[::-1]]
        d.polygon(P(left + right), fill=INK)
    grown = body.copy()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        grown |= np.roll(np.roll(body, dy, 0), dx, 1)
    im = finish(im, ov, grown)
    # 2) 엉덩이 침 (왼쪽 끝, 몸 가운데 높이) + 등 위에 곧게 선 하늘색 날개 한 쌍 (대표님 2026-10-04 「파랑 말고 하늘색」, 기획서처럼 둥근 타원, 가운데 날개맥)
    ov = overlay(im.size); d = ImageDraw.Draw(ov)
    sy = y0 + bh * 0.55
    d.polygon(P([(x0 + 4, sy - bh * 0.09), (x0 - 26, sy + 2), (x0 + 4, sy + bh * 0.09)]), fill=(60, 60, 66, 255), outline=INK, width=SS * 2)
    # 날개는 한 쌍 (대표님 2026-10-04 「날개는 한 쌍만」)
    for k, (u, tilt, sz) in enumerate(((0.40, 12, 1.0), (0.52, -10, 1.0))):
        wx, wy = x0 + u * bw, y0 + bh * 0.06
        wl = Image.new('RGBA', ov.size, (0, 0, 0, 0)); wd = ImageDraw.Draw(wl)
        ww, wh = bw * 0.17 * sz, bh * 0.50 * sz
        wd.ellipse([(wx - ww / 2) * SS, (wy - wh) * SS, (wx + ww / 2) * SS, wy * SS], fill=(140, 206, 250, 255), outline=INK, width=SS * 2)
        wd.line([(wx * SS, (wy - wh * 0.12) * SS), (wx * SS, (wy - wh * 0.88) * SS)], fill=(70, 140, 200, 255), width=SS * 2)
        wd.ellipse([(wx - ww * 0.32) * SS, (wy - wh * 0.80) * SS, (wx - ww * 0.05) * SS, (wy - wh * 0.55) * SS], fill=(220, 242, 255, 220))
        wl = wl.rotate(tilt, center=(wx * SS, wy * SS), resample=Image.BICUBIC)
        ov.alpha_composite(wl)
    im = finish(im, ov)
    bb = im.getbbox(); im = im.crop(bb)
    im.save(os.path.join(DST, 'cat_bee.png')); print('cat_bee.png', im.size)


def make_flower():
    src = Image.open(os.path.join(DST, 'cat_normal.png')).convert('RGBA')
    pad = 60                                                   # 꽃대가 머리 위로 높이 올라간다
    im = Image.new('RGBA', (src.width + 20, src.height + pad), (0, 0, 0, 0)); im.paste(src, (0, pad))
    body, (x0, y0, x1, y1), mz = body_info(im)
    bw, bh = x1 - x0, y1 - y0
    P = lambda pts: [(x * SS, y * SS) for x, y in pts]
    # 둥글게 (대표님 2026-10-04 「직선으로 하지 말고 둥글게」): 줄무늬는 둥근 몸을 따라 휘고, 잎은 둥근 덩어리, 꽃대는 부드러운 곡선
    def bezier(p0, p1, p2, n=28):
        t = np.linspace(0, 1, n)[:, None]
        return [tuple(v) for v in ((1 - t) ** 2 * np.array(p0) + 2 * (1 - t) * t * np.array(p1) + t ** 2 * np.array(p2))]
    # 1) 주황 줄무늬 — 몸통을 감싸 도는 휜 띠
    ov = overlay(im.size); d = ImageDraw.Draw(ov)
    for k in range(6):
        u = 0.12 + k * 0.085
        cx = x0 + u * bw
        bow = -bw * (0.07 - k * 0.01)
        ts = np.linspace(0, 1, 20)
        pts = [(cx + bow * np.sin(np.pi * t), y0 + bh * 0.12 + bh * 0.8 * t) for t in ts]
        d.line(P(pts), fill=(236, 120, 30, 210), width=SS * 3, joint='curve')
    im = finish(im, ov, body)
    # 2) 얼굴 위쪽을 덮는 둥근 초록 잎 (눈 둘레까지 동그스름하게)
    ov = overlay(im.size); d = ImageDraw.Draw(ov)
    lx0 = (mz[0] - bw * 0.12) if mz else x0 + bw * 0.52
    ly1 = (mz[1] + 6) if mz else y0 + bh * 0.45
    leaf = Image.new('RGBA', ov.size, (0, 0, 0, 0)); ld = ImageDraw.Draw(leaf)
    ld.ellipse([lx0 * SS, (y0 - 2) * SS, (x1 + 6) * SS, ly1 * SS], fill=(76, 156, 64, 240), outline=INK, width=SS * 2)
    mid = [(lx0 + bw * 0.06, ly1 - 6), ((lx0 + x1) / 2, y0 + bh * 0.10), (x1, y0 + bh * 0.16)]
    ld.line([(x * SS, y * SS) for x, y in bezier(*mid)], fill=(40, 104, 40, 255), width=SS * 2, joint='curve')
    ov.alpha_composite(leaf)
    # 3) 머리 위로 부드럽게 휘어 앞으로 굽은 분홍 꽃대 두 개 + 꽃
    for k, (u, reach, top) in enumerate(((0.56, 0.42, 54), (0.68, 0.46, 34))):
        bx, by = x0 + u * bw, y0 + 8
        tip = (bx + bw * reach, by - top * 0.70)
        ctrl = (bx - bw * 0.02, by - top * 1.45)
        curve = bezier((bx, by), ctrl, tip)
        d.line(P(curve), fill=INK, width=SS * 9, joint='curve')
        d.line(P(curve), fill=(236, 140, 186, 255), width=SS * 6, joint='curve')
        fx, fy = tip
        for a_ in range(5):
            ang = np.deg2rad(a_ * 72)
            px, py = fx + np.cos(ang) * 7, fy + np.sin(ang) * 7
            d.ellipse(P([(px - 5, py - 5), (px + 5, py + 5)]), fill=(250, 150, 190, 255), outline=INK, width=SS)
        d.ellipse(P([(fx - 4, fy - 4), (fx + 4, fy + 4)]), fill=(255, 220, 90, 255), outline=INK, width=SS)
    im = finish(im, ov)
    bb = im.getbbox(); im = im.crop(bb)
    im.save(os.path.join(DST, 'cat_flower.png')); print('cat_flower.png', im.size)


if __name__ == '__main__':
    make_bee()
    make_flower()
