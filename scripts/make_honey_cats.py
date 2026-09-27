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


def make_bee():
    src = Image.open(os.path.join(DST, 'cat_normal.png')).convert('RGBA')
    pad = 30                                                   # 날개가 위로, 침이 왼쪽으로 삐져나올 자리
    im = Image.new('RGBA', (src.width + pad, src.height + pad), (0, 0, 0, 0)); im.paste(src, (pad, pad))
    body, (x0, y0, x1, y1), mz = body_info(im)
    bw, bh = x1 - x0, y1 - y0
    P = lambda pts: [(x * SS, y * SS) for x, y in pts]
    # 1) 몸통 위 굵은 검은 세로 줄 두 개 (몸 안에서만)
    ov = overlay(im.size); d = ImageDraw.Draw(ov)
    for u in (0.30, 0.46):
        cx = x0 + u * bw
        d.polygon(P([(cx - bw * 0.055, y0 - 5), (cx + bw * 0.055, y0 - 5), (cx + bw * 0.035, y1 + 5), (cx - bw * 0.075, y1 + 5)]), fill=INK)
    grown = body.copy()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        grown |= np.roll(np.roll(body, dy, 0), dx, 1)
    im = finish(im, ov, grown)
    # 2) 엉덩이 침 (왼쪽 끝, 몸 가운데 높이) + 등 위 파란 날개 한 쌍
    ov = overlay(im.size); d = ImageDraw.Draw(ov)
    sy = y0 + bh * 0.55
    d.polygon(P([(x0 + 4, sy - bh * 0.09), (x0 - 26, sy + 2), (x0 + 4, sy + bh * 0.09)]), fill=(60, 60, 66, 255), outline=INK, width=SS * 2)
    for k, (u, tilt) in enumerate(((0.28, -28), (0.54, 24))):
        wx, wy = x0 + u * bw, y0 + 2
        wl = Image.new('RGBA', ov.size, (0, 0, 0, 0)); wd = ImageDraw.Draw(wl)
        ww, wh = bw * 0.20, bh * 0.42
        wd.ellipse([(wx - ww / 2) * SS, (wy - wh) * SS, (wx + ww / 2) * SS, wy * SS], fill=(70, 120, 220, 235), outline=INK, width=SS * 2)
        wd.line([(wx * SS, (wy - wh * 0.15) * SS), (wx * SS, (wy - wh * 0.85) * SS)], fill=(40, 70, 150, 255), width=SS)
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
    # 1) 주황 가는 줄무늬를 몸에 더한다
    ov = overlay(im.size); d = ImageDraw.Draw(ov)
    for k in range(7):
        u = 0.10 + k * 0.08
        cx = x0 + u * bw
        d.line(P([(cx, y0 + bh * 0.25), (cx + bw * 0.02, y0 + bh * 0.85)]), fill=(236, 120, 30, 200), width=SS * 3)
    im = finish(im, ov, body)
    # 2) 얼굴을 덮는 초록 잎 (눈~주둥이 위, 기획서처럼 얼굴 오른쪽 위를 덮는다)
    ov = overlay(im.size); d = ImageDraw.Draw(ov)
    fx0 = mz[0] - bw * 0.10 if mz else x0 + bw * 0.55
    fy0 = y0 + bh * 0.05
    fy1 = (mz[1] + 4) if mz else y0 + bh * 0.45
    d.polygon(P([(fx0, fy1), (fx0 + bw * 0.08, fy0 + 4), (x1 - bw * 0.02, fy0), (x1 + 4, fy1 - 6), (fx0 + bw * 0.25, fy1 + 6)]),
              fill=(70, 150, 60, 235), outline=INK, width=SS * 2)
    d.line(P([(fx0 + bw * 0.04, fy1 - 2), (x1 - bw * 0.04, fy0 + 6)]), fill=(40, 100, 40, 255), width=SS)
    # 3) 머리 위로 휘어 올라간 분홍 꽃대 두 개 + 꽃
    for k, (u, lean, top) in enumerate(((0.58, 0.40, 48), (0.70, 0.48, 32))):
        bx, by = x0 + u * bw, y0 + 6
        pts = [(bx, by), (bx + bw * lean * 0.15, by - top * 0.8), (bx + bw * lean * 0.55, by - top * 1.05), (bx + bw * lean, by - top * 0.85)]
        d.line(P(pts), fill=(236, 130, 180, 255), width=SS * 7, joint='curve')
        d.line(P(pts), fill=INK, width=SS * 1)
        fx, fy = pts[-1]
        for a in range(5):
            ang = np.deg2rad(a * 72)
            px, py = fx + np.cos(ang) * 7, fy + np.sin(ang) * 7
            d.ellipse(P([(px - 5, py - 5), (px + 5, py + 5)]), fill=(250, 150, 190, 255), outline=INK, width=SS)
        d.ellipse(P([(fx - 4, fy - 4), (fx + 4, fy + 4)]), fill=(255, 220, 90, 255), outline=INK, width=SS)
    im = finish(im, ov)
    bb = im.getbbox(); im = im.crop(bb)
    im.save(os.path.join(DST, 'cat_flower.png')); print('cat_flower.png', im.size)


if __name__ == '__main__':
    make_bee()
    make_flower()
