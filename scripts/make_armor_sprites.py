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
SEAM = (70, 74, 88)

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
    face_open = (xx >= muzzle_left - 0.04 * bw) & (yy >= open_y) & (yy <= muzzle_bot + 1)
    plate = grown & (yy < bottom_y) & ~face_open
    out = arr.copy()
    # 철판 느낌 (대표님 2026-09-27): 위는 밝고 아래로 갈수록 어두운 금속 명암 + 윗등을 따라 흐르는 흰 광택 줄 + 아랫단 그늘
    top_row = np.where(plate.any(axis=0), plate.argmax(axis=0), H)          # 열마다 판의 맨 윗줄
    depth = np.clip((yy - top_row[None, :]) / max(1.0, bottom_y - y0), 0, 1)   # 판 윗선에서 아랫단까지 0~1
    light, dark = np.array(PLATE_LIGHT, float), np.array(PLATE_DARK, float)
    shade = light[None, None, :] * (1 - depth[..., None]) + dark[None, None, :] * depth[..., None]
    # 가로로도 둥근 몸통처럼: 가운데가 조금 밝다
    roll = 1.0 + 0.06 * np.cos(np.clip((U - 0.35) * np.pi, -np.pi / 2, np.pi / 2))
    shade = np.clip(shade * roll[..., None], 0, 255)
    out[plate, :3] = shade[plate].astype(np.uint8)
    gloss = plate & (yy - top_row[None, :] >= max(2, lw)) & (yy - top_row[None, :] <= max(3, lw + 1)) & (U > 0.06) & (U < 0.5)
    out[gloss, :3] = (238, 242, 250)
    out[plate & (yy > bottom_y - max(3, bh * 0.08)), :3] = (122, 128, 142)   # 아랫단 그늘
    img = Image.fromarray(out); d = ImageDraw.Draw(img)

    # 판 아래 경계(배와의 곧은 선) — 몸 전체 폭
    xs_row = np.nonzero(grown[bottom_y - 1])[0] if grown[bottom_y - 1].any() else np.array([x0, x1])
    d.line([(xs_row.min(), bottom_y), (xs_row.max(), bottom_y)], fill=INK, width=lw)
    # 등판 이음새 (원안: 가운데 살짝 휜 세로 하나 + 1/3 높이 가로 하나) + 이음새를 따라 찍힌 리벳
    r = max(1.0, bh * 0.012)
    def dot(x, y):
        d.ellipse([x - r, y - r, x + r, y + r], fill=INK)
    pil_l = muzzle_left - 0.17 * bw                     # 투구 기둥(뒤통수) 왼쪽 끝
    seam_top = top_row[int(ux(0.26))] + 2                   # 판 윗선 안쪽에서 시작 — 몸 밖으로 삐져나오지 않게
    vs = [(ux(0.26 - 0.05 * np.sin(np.pi * t)), seam_top + (bottom_y - seam_top) * t) for t in np.linspace(0, 1, 9)]
    d.line(vs, fill=SEAM, width=1)
    hy = vy(0.33)
    d.line([(ux(0.02), hy), (pil_l, hy)], fill=SEAM, width=1)
    for u in (0.08, 0.16, 0.32):
        if ux(u) < pil_l - 2: dot(ux(u), hy + r * 2.2)
    for t in (0.25, 0.55, 0.85):
        x, y = vs[int(t * 8)]
        if y < bottom_y - 2: dot(x - r * 2.2, y)
    # 주둥이 아래 앞판: 세로로 리벳 줄
    if bottom_y - (muzzle_bot + 1) > 4:
        mx = (muzzle_left + ux(1.0)) / 2
        n = max(1, int((bottom_y - muzzle_bot) / (r * 4)))
        for k in range(n):
            dot(mx, muzzle_bot + 2 + (bottom_y - muzzle_bot - 3) * (k + 0.5) / n)

    # 판 위 외곽선 (몸통 외곽을 따라) — 갑옷이 몸에 붙어 보이게
    edge = plate & ~np.roll(plate, 1, 0)
    e = np.array(img); e[edge & (yy < bottom_y - 2), :3] = INK
    img = Image.fromarray(e)

    # ---- 투구 (원안 Cats/cat_armor.jpeg · cat_armor_helmet.jpeg) — 4배로 그려 줄여 선을 매끈하게 ----
    # 뒤통수에서 곧게 솟은 기둥 → 둥근 후드가 머리를 덮고 앞으로 내려와 챙에 닿는다.
    # 후드 위에는 줄무늬 볏이 뒤(평평한 끝)에서 앞으로 휘어 내려오고, 눈 바로 위엔 앞으로 살짝 기운 줄무늬 챙과 경첩 리벳.
    # 귀는 기둥 옆·볏 끝 아래로 삐죽 나온다. 얼굴(눈·주둥이·수염)은 챙 아래로 그대로 보인다.
    SS = 4
    ov = Image.new('RGBA', (W * SS, H * SS), (0, 0, 0, 0)); o = ImageDraw.Draw(ov)
    P = lambda pts: [(x * SS, y * SS) for x, y in pts]
    LW = lw * SS
    E = eye_top
    right = ux(1.0)
    brim_l = (muzzle_left - 0.04 * bw, E - 0.11 * bh)   # 챙 뒤끝(경첩) — 앞보다 높다
    brim_r = (right + 0.01 * bw, E - 0.095 * bh)          # 챙 앞끝
    bt = 0.085 * bh                                       # 챙 두께
    pil_r = muzzle_left - 0.04 * bw
    top_y = E - 0.29 * bh
    base_y = muzzle_bot + 1
    # 후드 외곽: 기둥 왼쪽 아래 → 위로 곧게 → 둥근 머리 → 앞으로 내려와 챙 앞끝 위
    hood = [(pil_l, base_y), (pil_l, E + 0.02 * bh)]
    cx, cy = pil_l + (right - pil_l) * 0.48, E + 0.02 * bh
    rx1, ry1 = cx - pil_l, cy - top_y
    for t in np.linspace(np.pi, 1.5 * np.pi, 16)[1:]:
        hood.append((cx + rx1 * np.cos(t), cy + ry1 * np.sin(t)))
    rx2 = right - 0.02 * bw - cx; ry2 = brim_r[1] - bt * 0.5 - top_y
    arc2 = []
    for t in np.linspace(1.5 * np.pi, 2 * np.pi, 16)[1:]:
        arc2.append((cx + rx2 * np.cos(t), top_y + ry2 + ry2 * np.sin(t)))
    hood += arc2
    hood += [(brim_l[0], brim_l[1] + bt), (pil_r, brim_l[1] + bt), (pil_r, base_y)]
    o.polygon(P(hood), fill=PLATE_LIGHT + (255,), outline=INK + (255,), width=LW)
    # 투구 광택: 둥근 머리 안쪽을 따라 흰 줄 (철판 느낌)
    gl = [(cx + (rx1 - 0.035 * bw) * np.cos(t), cy + (ry1 - 0.035 * bh) * np.sin(t)) for t in np.linspace(1.08 * np.pi, 1.45 * np.pi, 8)]
    gl = [(pil_l + 0.035 * bw, base_y - 0.05 * bh)] + gl
    o.line(P(gl), fill=(240, 244, 252, 255), width=max(SS, int(LW * 0.6)), joint='curve')
    # 기둥 가운데 가로 이음새 (원안의 기둥 아랫단)
    sy = (E + muzzle_bot) / 2
    o.line(P([(pil_l, sy), (pil_r, sy)]), fill=INK + (255,), width=max(SS, LW - SS))
    o.polygon(P([(pil_l + LW / SS / 2, sy + 1), (pil_r - LW / SS / 2, sy + 1), (pil_r - LW / SS / 2, base_y - 1), (pil_l + LW / SS / 2, base_y - 1)]),
              fill=PLATE + (255,))

    # 볏: 후드 윗선을 따라 얹힌 가는 띠 — 뒤쪽 끝은 네모나게 잘리고, 앞으로 휘어 내려가 챙 앞끝 위에서 가늘게 끝난다. 빗금은 몇 개만.
    top_pts = [(cx + rx1 * np.cos(t), cy + ry1 * np.sin(t)) for t in np.linspace(1.30 * np.pi, 1.5 * np.pi, 6)]
    top_pts += [(cx + rx2 * np.cos(t), top_y + ry2 + ry2 * np.sin(t)) for t in np.linspace(1.5 * np.pi, 1.93 * np.pi, 12)[1:]]
    cw = 0.055 * bh
    outer = []
    for i, (x, y) in enumerate(top_pts):
        x0_, y0_ = top_pts[max(0, i - 1)]; x1_, y1_ = top_pts[min(len(top_pts) - 1, i + 1)]
        tx, ty = x1_ - x0_, y1_ - y0_; n = max(np.hypot(tx, ty), 1e-6)
        k = cw * (1.0 if i < len(top_pts) - 5 else (len(top_pts) - 1 - i) / 5 * 0.8 + 0.2)   # 앞끝은 가늘어진다
        outer.append((x + ty / n * k, y - tx / n * k))
    crest = top_pts + outer[::-1]
    o.polygon(P(crest), fill=PLATE + (255,), outline=INK + (255,), width=max(SS, LW - SS))
    for i in range(2, len(top_pts) - 3, 3):
        o.line(P([top_pts[i], outer[i]]), fill=INK + (255,), width=SS)

    # 귀: 머리 색으로, 투구를 뚫고 나온 것처럼 챙 위에 붙어 있다 — 경첩 바로 앞 하나, 볏 앞끝 아래 하나
    sxp, syp = int(ux(0.66)), int(y0 + bh * 0.06)
    col = tuple(int(v) for v in arr[min(max(syp, 0), H - 1), min(max(sxp, 0), W - 1), :3]) if a_ref[min(max(syp, 0), H - 1), min(max(sxp, 0), W - 1)] else PLATE
    es = 0.10 * bh
    brim_top = lambda x: brim_l[1] + (brim_r[1] - brim_l[1]) * (x - brim_l[0]) / (brim_r[0] - brim_l[0]) - 0.05 * bh * np.sin(np.pi * np.clip((x - brim_l[0]) / (brim_r[0] - brim_l[0]), 0, 1))
    for ex, lean in ((brim_l[0] + 0.05 * bw, -0.15), (brim_l[0] + (brim_r[0] - brim_l[0]) * 0.58, 0.15)):
        by0, by1 = brim_top(ex) + 1, brim_top(ex + es) + 1
        o.polygon(P([(ex, by0), (ex + es * (0.5 + lean), min(by0, by1) - es * 0.95), (ex + es, by1)]),
                  fill=col + (255,), outline=INK + (255,), width=max(SS, LW - SS))

    # 챙: 경첩(뒤, 낮음)에서 이마를 감싸며 앞으로 — 윗선이 살짝 볼록한 띠 + 세로 빗금 + 경첩 리벳(동그라미 속 점)
    xs_b = np.linspace(brim_l[0], brim_r[0], 14)
    top_edge = [(x, brim_top(x)) for x in xs_b]
    bot_edge = [(x - 0.02 * bw * (x - brim_l[0]) / (brim_r[0] - brim_l[0]), brim_top(x) + bt) for x in xs_b[::-1]]
    o.polygon(P(top_edge + bot_edge), fill=PLATE_DARK + (255,), outline=INK + (255,), width=LW)
    for i in range(1, 8):
        xa = brim_l[0] + (brim_r[0] - brim_l[0]) * i / 8; ya = brim_top(xa)
        o.line(P([(xa, ya + 1), (xa - 0.01 * bw, ya + bt - 1)]), fill=INK + (255,), width=SS)
    hr = bt * 0.55
    o.ellipse(P([(brim_l[0] - hr, brim_l[1] + bt / 2 - hr), (brim_l[0] + hr, brim_l[1] + bt / 2 + hr)]), fill=PLATE_LIGHT + (255,), outline=INK + (255,), width=SS * 2)
    o.ellipse(P([(brim_l[0] - r, brim_l[1] + bt / 2 - r), (brim_l[0] + r, brim_l[1] + bt / 2 + r)]), fill=INK + (255,))

    ov = ov.resize((W, H), Image.LANCZOS)
    img = img.convert('RGBA'); img.alpha_composite(ov)
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
    for c in ('green', 'blue', 'flying', 'gun'):
        statics[f'cat_{c}_ally.png'] = f'cat_{c}_armor_ally.png'
    base_static = Image.open(os.path.join(DST, 'cat_ally.png'))
    for src, dst in statics.items():
        # 무늬·색 변형은 전부 치즈냥이 아군 그림과 픽셀 모양이 같다 → 몸통은 그 그림으로 잰다. 날아냥이만 제 그림으로.
        base = None if 'flying' in src else base_static
        armor(Image.open(os.path.join(DST, src)), wide='flying' in src, base=base).save(os.path.join(DST, dst)); print(dst)
    strips = {'anim_cat_idle.png': 'anim_cat_idle_cheese_armor.png'}
    for c in ('white', 'calico', 'tuxedo', 'mackerel', 'green', 'blue', 'gun'):
        strips[f'anim_cat_idle_{c}.png'] = f'anim_cat_idle_{c}_armor.png'
    base_strip = Image.open(os.path.join(DST, 'anim_cat_idle.png'))
    for src, dst in strips.items():
        strip(Image.open(os.path.join(DST, src)), base=base_strip).save(os.path.join(DST, dst)); print(dst)

if __name__ == '__main__':
    main()
