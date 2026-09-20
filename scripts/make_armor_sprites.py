"""갑옷 입은 냥이 스프라이트 — 원안 Cats/cat_armor.jpeg (2026-09-20).
등·머리 위를 덮는 철판(이음새·리벳) + 눈 위 챙이 있는 투구 + 투구 꼭대기 볏. 얼굴(눈·주둥이)과 배·다리·꼬리는 그대로 드러난다.

갑옷은 내 편 냥이만 입으므로 아군(가는 꼬리) 그림과 대기(춤) 스트립만 만든다.
  cat_{coat}_ally.png            -> cat_{coat}_armor_ally.png       (무늬 5종)
  cat_green_ally / cat_blue_ally / cat_flying_ally -> *_armor_ally.png
  anim_cat_idle[_{coat|green|blue}].png -> anim_cat_idle_{...}_armor.png (7프레임)

사용법: python scripts/make_armor_sprites.py
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, 'scripts')
from rebuild_anim import label_components

DST = 'app/src/main/res/drawable-nodpi'
FRAMES = 7
PAD_TOP = 14            # 볏이 머리 위로 솟아 그림을 위로 늘린다

PLATE = (176, 182, 194)
PLATE_DARK = (140, 146, 160)
PLATE_LIGHT = (206, 210, 220)
INK = (44, 44, 52)
SEAM = (98, 102, 116)

def body_mask(arr):
    a = arr[..., 3] > 128
    rgb = arr[..., :3].astype(int)
    ink = a & (rgb.max(axis=2) < 90)
    white = a & (rgb.min(axis=2) > 200)
    comps = sorted(label_components(a & ~ink & ~white), key=lambda t: -t[0])
    ys, xs = comps[0][1], comps[0][2]
    body = np.zeros_like(a); body[ys, xs] = True
    # 몸통 속 + 그 둘레의 잉크 선까지 갑옷이 덮게 한 번 부풀린다
    grown = body.copy()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        grown |= np.roll(np.roll(body, dy, 0), dx, 1)
    grown &= a
    return grown, (xs.min(), ys.min(), xs.max(), ys.max())

def armor(im, wide=False, base=None):
    """im: 오른쪽을 보는 아군 냥이 한 칸. 위로 PAD_TOP 만큼 늘린 새 그림을 돌려준다.
    base: 몸통 영역을 잴 기준 그림 — 무늬 변형(흰 몸·검은 몸)은 색으로 몸통을 못 찾으니 같은 모양인 치즈냥이 그림을 준다."""
    src = im.convert('RGBA')
    W, H = src.width, src.height + PAD_TOP
    canvas = Image.new('RGBA', (W, H), (0, 0, 0, 0)); canvas.paste(src, (0, PAD_TOP))
    arr = np.array(canvas)
    ref = arr
    if base is not None:
        b = Image.new('RGBA', (W, H), (0, 0, 0, 0)); b.paste(base.convert('RGBA'), (0, PAD_TOP)); ref = np.array(b)
    grown, (x0, y0, x1, y1) = body_mask(ref)
    bw, bh = x1 - x0, y1 - y0
    ux = lambda u: x0 + u * bw
    vy = lambda v: y0 + v * bh
    yy, xx = np.mgrid[0:H, 0:W]
    U = (xx - x0) / max(bw, 1); V = (yy - y0) / max(bh, 1)

    # 1) 판이 덮는 곳: 몸통 윗부분(배는 드러남). 아래 경계는 앞으로 갈수록 살짝 내려간다.
    bottom = 0.56 + 0.06 * U
    face_open = (U > 0.60) & (V > 0.20)              # 눈·주둥이 창 — 얼굴은 드러난다
    plate = grown & (V < bottom) & ~face_open
    out = arr.copy()
    out[plate, :3] = PLATE
    out[plate & (V < 0.12), :3] = PLATE_LIGHT        # 윗면 하이라이트
    out[plate & (V > bottom - 0.12), :3] = PLATE_DARK  # 아랫단 그늘
    img = Image.fromarray(out); d = ImageDraw.Draw(img)
    lw = max(2, int(bh * 0.035))

    # 판 아래 가장자리(배와의 경계) 굵은 선
    pts = [(ux(u), vy(0.56 + 0.06 * u)) for u in np.linspace(0.0, 0.60, 8)]
    d.line(pts, fill=INK, width=lw)
    # 얼굴 창 왼쪽 세로 경계(볼 가리개) + 챙(눈 위 가로 띠)
    d.line([(ux(0.60), vy(0.20)), (ux(0.60), vy(0.56 + 0.06 * 0.60))], fill=INK, width=lw)
    d.line([(ux(0.58), vy(0.20)), (ux(1.02), vy(0.20))], fill=INK, width=lw + 1)
    d.line([(ux(0.58), vy(0.15)), (ux(1.00), vy(0.15))], fill=SEAM, width=max(1, lw - 1))
    # 2) 이음새 (세로 둘, 가로 하나) + 리벳
    for u in (0.26, 0.48):
        d.line([(ux(u), vy(0.02)), (ux(u), vy(0.56 + 0.06 * u))], fill=SEAM, width=max(1, lw - 1))
    d.line([(ux(0.02), vy(0.32)), (ux(0.60), vy(0.32))], fill=SEAM, width=max(1, lw - 1))
    r = max(1.5, bh * 0.02)
    for u, v in ((0.13, 0.18), (0.13, 0.46), (0.37, 0.18), (0.37, 0.46), (0.54, 0.12), (0.54, 0.46), (0.72, 0.10), (0.88, 0.10)):
        x, y = ux(u), vy(v)
        d.ellipse([x - r, y - r, x + r, y + r], fill=INK)
    # 3) 볏 — 투구 꼭대기에서 뒤로 휘는 지느러미 (빗금 무늬)
    cx0, cy0 = ux(0.80), y0 + 1
    crest = [(cx0 - bw * 0.04, cy0), (cx0 - bw * 0.02, cy0 - PAD_TOP * 0.9), (cx0 + bw * 0.12, cy0 - PAD_TOP * 0.75),
             (cx0 + bw * 0.20, cy0 - PAD_TOP * 0.2), (cx0 + bw * 0.16, cy0)]
    d.polygon(crest, fill=PLATE_DARK, outline=INK)
    for i in range(1, 5):
        t = i / 5
        x = cx0 + bw * (-0.02 + 0.18 * t)
        d.line([(x, cy0 - PAD_TOP * (0.85 - 0.5 * t)), (x, cy0 - 1)], fill=INK, width=1)
    # 판 위 외곽선 (몸통 외곽을 따라) — 갑옷이 몸에 붙어 보이게
    edge = plate & ~np.roll(plate, 1, 0)
    e = np.array(img); e[edge & (V < 0.5), :3] = INK; img = Image.fromarray(e)
    return img

def strip(im, base=None):
    w, h = im.size; cw = w // FRAMES
    out = Image.new('RGBA', (w, h + PAD_TOP), (0, 0, 0, 0))
    for i in range(FRAMES):
        x = i * cw
        box = (x, 0, x + cw if i < FRAMES - 1 else w, h)
        out.paste(armor(im.crop(box), base=base.crop(box) if base is not None else None), (x, 0))
    return out

def main():
    statics = {'cat_ally.png': 'cat_cheese_armor_ally.png'}
    for coat in ('white', 'calico', 'tuxedo', 'mackerel'):
        statics[f'cat_{coat}_ally.png'] = f'cat_{coat}_armor_ally.png'
    for c in ('green', 'blue', 'flying'):
        statics[f'cat_{c}_ally.png'] = f'cat_{c}_armor_ally.png'
    base_static = Image.open(os.path.join(DST, 'cat_ally.png'))
    for src, dst in statics.items():
        # 무늬·색 변형은 전부 치즈냥이 아군 그림과 픽셀 모양이 같다 → 몸통은 그 그림으로 잰다. 날아냥이만 제 그림으로.
        base = None if 'flying' in src else base_static
        armor(Image.open(os.path.join(DST, src)), wide='flying' in src, base=base).save(os.path.join(DST, dst)); print(dst)
    strips = {'anim_cat_idle.png': 'anim_cat_idle_cheese_armor.png'}
    for c in ('white', 'calico', 'tuxedo', 'mackerel', 'green', 'blue'):
        strips[f'anim_cat_idle_{c}.png'] = f'anim_cat_idle_{c}_armor.png'
    base_strip = Image.open(os.path.join(DST, 'anim_cat_idle.png'))
    for src, dst in strips.items():
        strip(Image.open(os.path.join(DST, src)), base=base_strip).save(os.path.join(DST, dst)); print(dst)

if __name__ == '__main__':
    main()
