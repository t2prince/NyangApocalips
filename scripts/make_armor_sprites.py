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
PAD_TOP = 40            # 투구 캡 + 볏이 머리 위로 크게 솟아 그림을 위로 늘린다

PLATE = (176, 182, 194)
PLATE_DARK = (140, 146, 160)
PLATE_LIGHT = (206, 210, 220)
INK = (44, 44, 52)
SEAM = (98, 102, 116)

def lw_of(bh):
    return max(2, int(bh * 0.035))

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
    base: 몸통 영역을 잴 기준 그림 — 무늬 변형(흰 몸·검은 몸)은 색으로 몸통을 못 찾으니 같은 모양인 치즈냥이 그림을 준다.

    원안(Cats/cat_armor.jpeg) 그대로: 등판(세로 이음새 둘·가로 하나·리벳, 아래 경계는 몸 중간의 곧은 선),
    머리를 감싸는 둥근 투구(등판과 곡선 이음새로 구분), 눈 위 챙(빗금 슬롯), 투구 위에서 앞으로 크게 휘는 볏,
    주둥이 옆·아래의 볼 가리개. 눈·주둥이·수염·배·다리·꼬리는 드러난다."""
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
    lw = lw_of(bh)

    # 눈·주둥이 위치를 기준 그림에서 찾는다 — 챙이 눈을 가리면 안 된다
    a_ref = ref[..., 3] > 128
    rgb_ref = ref[..., :3].astype(int)
    ink_ref = a_ref & (rgb_ref.max(axis=2) < 90)
    eye_region = ink_ref & (U > 0.55) & (V > 0.04) & (V < 0.42)
    eye_tops = [ys.min() for n, ys, xs in label_components(eye_region) if 3 <= n <= 60]
    eye_top = min(eye_tops) if eye_tops else int(vy(0.16))
    white_ref = a_ref & (rgb_ref.min(axis=2) > 200) & (U > 0.5)     # 주둥이 두 원
    muzzle_bot = int(np.nonzero(white_ref)[0].max()) if white_ref.any() else int(vy(0.58))
    muzzle_left = int(np.nonzero(white_ref)[1].min()) if white_ref.any() else int(ux(0.62))
    open_y = eye_top - 2
    brim_y = open_y - lw // 2 - 1

    # 1) 판이 덮는 곳 — 몸통 위쪽 절반 남짓, 아래 경계는 곧은 가로선. 얼굴 창(눈~주둥이)만 뚫린다.
    bottom_y = max(muzzle_bot + lw + 1, int(vy(0.60)))
    face_open = (xx >= muzzle_left - lw) & (yy >= open_y) & (yy <= muzzle_bot + 1)
    plate = grown & (yy < bottom_y) & ~face_open
    # 투구: 머리 쪽을 둥글게 감싸는 부분 — 등판과 곡선 이음새로 나뉜다
    seam_u = 0.52
    helmet = plate & (U > seam_u + 0.10 * (V - 0.3))
    out = arr.copy()
    out[plate, :3] = PLATE
    out[helmet, :3] = PLATE_LIGHT
    out[plate & (yy > bottom_y - max(3, bh * 0.08)), :3] = PLATE_DARK     # 아랫단 그늘
    img = Image.fromarray(out); d = ImageDraw.Draw(img)

    # 판 아래 경계(배와의 곧은 선) — 몸 전체 폭
    xs_row = np.nonzero(grown[bottom_y - 1])[0] if grown[bottom_y - 1].any() else np.array([x0, x1])
    d.line([(xs_row.min(), bottom_y), (xs_row.max(), bottom_y)], fill=INK, width=lw)
    # 투구 ↔ 등판 곡선 이음새
    seam_pts = [(ux(seam_u + 0.10 * (v - 0.3)), vy(v)) for v in np.linspace(0.02, 0.60, 8)]
    d.line(seam_pts, fill=INK, width=lw)
    # 얼굴 창 테두리: 왼쪽 세로(볼 가리개) + 아래 가로(턱 가리개 위)
    d.line([(muzzle_left - lw, open_y), (muzzle_left - lw, muzzle_bot + 1)], fill=INK, width=lw)
    d.line([(muzzle_left - lw, muzzle_bot + 1), (ux(1.0), muzzle_bot + 1)], fill=INK, width=max(1, lw - 1))
    # 투구: 이 그림은 눈이 머리 꼭대기 가까이 있어, 원안처럼 머리를 감싸는 둥근 캡을 눈 위로 솟게 그린다
    dome_h = max(6, int(bh * 0.22))
    dome_bot = brim_y + lw
    dome_box = [ux(0.44), dome_bot - dome_h, ux(1.03), dome_bot + dome_h]   # 타원 위 절반 = 챙 위에 얹힌 캡
    d.chord(dome_box, 180, 360, fill=PLATE_LIGHT, outline=INK, width=lw)
    d.line([(ux(0.50), dome_bot - dome_h * 0.55), (ux(0.98), dome_bot - dome_h * 0.55)], fill=SEAM, width=max(1, lw - 1))   # 캡 이음새
    # 챙: 눈 바로 위 굵은 띠(캡보다 앞으로 튀어나옴) + 빗금 슬롯
    d.rectangle([ux(0.50), brim_y - lw // 2, ux(1.06), brim_y + lw // 2 + 1], fill=PLATE_DARK, outline=INK)
    for i in range(7):
        x = ux(0.54) + (ux(1.04) - ux(0.54)) * (i + 0.5) / 7
        d.line([(x, brim_y - lw // 2 + 1), (x, brim_y + lw // 2)], fill=INK, width=1)

    # 2) 등판 이음새(세로 둘·가로 하나) + 리벳 쌍
    for u in (0.20, 0.38):
        d.line([(ux(u), vy(0.03)), (ux(u), bottom_y)], fill=SEAM, width=max(1, lw - 1))
    d.line([(ux(0.03), vy(0.34)), (ux(seam_u), vy(0.34))], fill=SEAM, width=max(1, lw - 1))
    r = max(1.5, bh * 0.018)
    rivets = [(0.10, 0.20), (0.10, 0.48), (0.29, 0.14), (0.29, 0.48), (0.46, 0.16), (0.46, 0.48), (0.16, 0.30), (0.34, 0.30)]
    for u, v in rivets:
        x, y = ux(u), vy(v)
        if y < bottom_y - 2: d.ellipse([x - r, y - r, x + r, y + r], fill=INK)
    # 볼 가리개 리벳 (주둥이 아래)
    for u in (0.70, 0.85):
        x, y = ux(u), (muzzle_bot + 1 + bottom_y) / 2
        if bottom_y - (muzzle_bot + 1) > 4: d.ellipse([x - r, y - r, x + r, y + r], fill=INK)

    # 3) 볏 — 캡 꼭대기에서 위로 솟아 앞(얼굴 쪽)으로 크게 휘어 넘어오는 두툼한 깃 (빗금 무늬)
    cap_top = dome_bot - dome_h
    P = max(8, cap_top - 2)                          # 남은 위 여백을 볏에 쓴다
    cx0 = ux(0.66)
    crest = [(cx0, cap_top + 1), (cx0 - bw * 0.01, cap_top - P * 0.55), (cx0 + bw * 0.08, cap_top - P * 0.92),
             (cx0 + bw * 0.24, cap_top - P * 0.98), (cx0 + bw * 0.40, cap_top - P * 0.80), (cx0 + bw * 0.46, cap_top - P * 0.50),
             (cx0 + bw * 0.38, cap_top - P * 0.52), (cx0 + bw * 0.34, cap_top - P * 0.72), (cx0 + bw * 0.22, cap_top - P * 0.80),
             (cx0 + bw * 0.12, cap_top - P * 0.70), (cx0 + bw * 0.10, cap_top - P * 0.30), (cx0 + bw * 0.16, cap_top + 1)]
    d.polygon(crest, fill=PLATE_DARK, outline=INK)
    d.line(crest + [crest[0]], fill=INK, width=max(1, lw - 1))
    for i in range(1, 8):                            # 깃의 빗금
        t = i / 8
        xa = cx0 + bw * (0.00 + 0.30 * t); ya = cap_top - P * (0.50 + 0.45 * t)
        xb = cx0 + bw * (0.10 + 0.28 * t); yb = cap_top - P * (0.30 + 0.45 * t)
        d.line([(xa, ya), (xb, yb)], fill=INK, width=1)
    # 판 위 외곽선 (몸통 외곽을 따라) — 갑옷이 몸에 붙어 보이게
    edge = plate & ~np.roll(plate, 1, 0)
    e = np.array(img); e[edge & (yy < bottom_y - 2), :3] = INK
    return Image.fromarray(e)

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
