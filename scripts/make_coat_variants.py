"""치즈냥이 무늬 변형 4종을 치즈냥이 그림에서 만든다 (사용자 원안: Cats/cats4.jpeg — 삼색이·턱시도·노랑이·고등어·하양이).

노랑이 = 기존 치즈냥이 그대로. 나머지 넷은 몸 색(노랑)과 줄무늬(갈색)를 바꾸고 무늬 조각을 얹는다.
- white    하양이  : 흰 몸, 줄무늬는 아주 옅게
- calico   삼색이  : 흰 몸 + 주황·검정 얼룩
- tuxedo   턱시도  : 검은 몸 + 흰 가슴
- mackerel 고등어  : 회색 몸 + 진회색 줄무늬 + 이마 진한 무늬

만드는 파일 (drawable-nodpi):
  cat_{coat}.png (적, 두꺼운 꼬리)  cat_{coat}_ally.png (아군, 가는 꼬리)
  anim_cat_{idle,walk,jump,attack}_{coat}.png (7프레임 스트립)

사용법: python scripts/make_coat_variants.py
"""
import os, sys
import numpy as np
from PIL import Image

sys.path.insert(0, 'scripts')
from rebuild_anim import label_components

DST = 'app/src/main/res/drawable-nodpi'
FRAMES = 7

# 얼룩: (중심x, 중심y, 반지름x, 반지름y) — 몸통 바운딩박스 기준 0~1. 얼굴은 오른쪽 40%.
COATS = {
    'white':    dict(body=(247, 246, 241), stripe=(222, 222, 216), patches=[]),
    'calico':   dict(body=(247, 246, 241), stripe=(247, 246, 241), patches=[
                    ((0.66, 0.20, 0.17, 0.15), (242, 150, 48)),    # 이마·귀 쪽 주황
                    ((0.26, 0.50, 0.16, 0.15), (46, 46, 50)),      # 등 검정
                    ((0.46, 0.82, 0.13, 0.10), (242, 150, 48)),    # 배 쪽 주황
                ]),
    'tuxedo':   dict(body=(48, 48, 54), stripe=(62, 62, 70), patches=[
                    ((0.74, 0.66, 0.16, 0.20), (246, 246, 242)),   # 가슴 흰 털
                    ((0.56, 0.90, 0.40, 0.24), (246, 246, 242)),   # 가슴 밑에서 배까지 이어지는 흰 털 (대표님 2026-09-20)
                ]),
    'mackerel': dict(body=(186, 190, 198), stripe=(74, 78, 88), patches=[
                    ((0.70, 0.14, 0.20, 0.11), (104, 108, 118)),   # 이마 진한 무늬
                ]),
}

def classify(arr):
    """픽셀을 잉크(검은 선)/흰색/몸(노랑)/줄무늬(갈색)/기타 로 나눈다."""
    a = arr[..., 3] > 128
    rgb = arr[..., :3].astype(np.float32) / 255
    mx = rgb.max(-1); mn = rgb.min(-1)
    s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    d = np.maximum(mx - mn, 1e-6)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    h = np.zeros_like(mx)
    h = np.where(mx == r, ((g - b) / d) % 6, h)
    h = np.where(mx == g, ((b - r) / d) + 2, h)
    h = np.where(mx == b, ((r - g) / d) + 4, h)
    h = (h / 6) % 1
    ink = a & (mx < 0.35)
    white = a & (mn > 0.78)
    warm = a & ~ink & ~white & (s > 0.25) & (h >= 0.03) & (h <= 0.19)   # 노랑~갈색
    body = warm & (mx > 0.68)
    stripe = warm & ~body
    return a, ink, white, body, stripe

def body_bbox(a, ink, white):
    comps = sorted(label_components(a & ~ink & ~white), key=lambda t: -t[0])
    ys, xs = comps[0][1], comps[0][2]
    return xs.min(), ys.min(), xs.max(), ys.max()

EYE_BLUE = (40, 110, 220)   # 하양이만 눈이 파랗다 (대표님 2026-09-20)

def paint_eyes(out, a, ink, bbox, color):
    """얼굴 위쪽(오른쪽 40%, 위 35%)의 작은 검은 점 = 눈. 그 점들만 색을 바꾼다."""
    x0, y0, x1, y1 = bbox
    H, W = a.shape
    yy, xx = np.mgrid[0:H, 0:W]
    region = ink & (xx > x0 + 0.55 * (x1 - x0)) & (yy < y0 + 0.38 * (y1 - y0)) & (yy > y0 + 0.05 * (y1 - y0))
    for n, ys, xs in label_components(region):
        if 3 <= n <= 60:                     # 눈 점 크기 (수염·외곽선은 훨씬 크거나 길다)
            out[ys, xs, :3] = color

def recoat(im, coat):
    spec = COATS[coat]
    arr = np.array(im.convert('RGBA'))
    a, ink, white, body, stripe = classify(arr)
    if body.sum() == 0:
        return Image.fromarray(arr)
    x0, y0, x1, y1 = body_bbox(a, ink, white)
    out = arr.copy()
    if coat == 'white':
        paint_eyes(out, a, ink, (x0, y0, x1, y1), EYE_BLUE)
    out[body, :3] = spec['body']
    out[stripe, :3] = spec['stripe']
    paintable = body | stripe
    H, W = a.shape
    yy, xx = np.mgrid[0:H, 0:W]
    for (cx, cy, rx, ry), color in spec['patches']:
        px = x0 + cx * (x1 - x0); py = y0 + cy * (y1 - y0)
        prx = max(2.0, rx * (x1 - x0)); pry = max(2.0, ry * (y1 - y0))
        ell = ((xx - px) / prx) ** 2 + ((yy - py) / pry) ** 2 <= 1.0
        sel = ell & paintable
        out[sel, :3] = color
    return Image.fromarray(out)

def recoat_strip(im, coat):
    """스트립은 칸마다 몸통 박스가 달라 칸 단위로 처리한다."""
    w, h = im.size
    cw = w // FRAMES
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    for i in range(FRAMES):
        x = i * cw
        cell = im.crop((x, 0, x + cw if i < FRAMES - 1 else w, h))
        out.paste(recoat(cell, coat), (x, 0))
    return out

def main():
    for coat in COATS:
        for src, dst in (('cat_normal.png', f'cat_{coat}.png'), ('cat_ally.png', f'cat_{coat}_ally.png')):
            recoat(Image.open(os.path.join(DST, src)), coat).save(os.path.join(DST, dst)); print(dst)
        for act in ('idle', 'walk', 'jump', 'attack'):
            src = f'anim_cat_{act}.png'; dst = f'anim_cat_{act}_{coat}.png'
            recoat_strip(Image.open(os.path.join(DST, src)), coat).save(os.path.join(DST, dst)); print(dst)

if __name__ == '__main__':
    main()
