"""날아냥이 = 치즈냥이 몸체 + 기존(원본 flying_cat) 날개 (사용자 규칙).
Plan/cat_sprites5/flying_cat.png 에서 몸통 밖으로 뻗은 날개 픽셀만 떼어내 색을 정리하고,
치즈냥이(cat_normal / cat_ally) 몸 크기에 맞춰 뒤에 붙인다.

사용법: python scripts/make_flying_cat.py
"""
import os, sys
sys.path.insert(0, 'scripts')
import numpy as np
from PIL import Image, ImageFilter
from sheet_to_sprites import strip_background
from rebuild_anim import label_components
from remove_front_tail import dilate

DST = 'app/src/main/res/drawable-nodpi'
SRC = 'Plan/cat_sprites5/flying_cat.png'
INK = (25, 21, 19, 255)
BODY = (242, 192, 66, 255)


def body_box(arr):
    m = arr[..., 3] > 128
    rgb = arr[..., :3].astype(int)
    ink = m & (rgb.max(axis=2) < 90)
    white = m & (rgb.min(axis=2) > 200)
    comps = sorted(label_components(m & ~ink & ~white), key=lambda t: -t[0])
    ys, xs = comps[0][1], comps[0][2]
    body = np.zeros_like(m); body[ys, xs] = True
    return body, (xs.min(), ys.min(), xs.max(), ys.max())


def extract_wings():
    """원본 날아냥이에서 날개(몸통 밖으로 가로로 길게 뻗은 덩어리)만 남긴 RGBA 이미지와 몸통 박스."""
    im = strip_background(Image.open(SRC).convert('RGBA'))
    arr = np.array(im)
    m = arr[..., 3] > 128
    body, (bl, bt, br, bb) = body_box(arr)
    bw, bh = br - bl, bb - bt
    protect = dilate(body, 9)                     # 몸통 속 + 외곽선까지 보호
    outside = m & ~protect
    keep = np.zeros_like(m)
    for area, ys, xs in label_components(outside):
        w = xs.max() - xs.min() + 1
        cy = ys.mean()
        beyond = xs.min() < bl - 6 or xs.max() > br + 6          # 몸통 상자 밖으로 뻗어 있어야 날개
        # 가로로 넓고 몸통 중간 높이에 있고 몸 밖으로 뻗은 것 = 날개 (꼬리·다리·수염·주둥이 제외)
        if beyond and w >= bw * 0.25 and bt + bh * 0.25 <= cy <= bt + bh * 0.85 and area >= 60:
            keep[ys, xs] = True
    keep = dilate(keep, 1) & m & ~dilate(body, 7)
    out = np.zeros_like(arr)
    rgb = arr[..., :3].astype(int)
    dark = rgb.max(axis=2) < 110
    out[keep & dark] = INK
    out[keep & ~dark] = BODY
    return Image.fromarray(out), (bl, bt, br, bb)


def compose(cat_name, dst_name, target_h):
    cat = Image.open(os.path.join(DST, cat_name)).convert('RGBA')
    cbody, (cl, ct, cr, cb) = body_box(np.array(cat))
    wings, (bl, bt, br, bb) = extract_wings()
    # 몸통 높이 비율로 날개를 키운다 (원본이 작아 부드럽게 확대)
    s = (cb - ct) / max(1, (bb - bt))
    W = max(1, int(round(wings.width * s))); H = max(1, int(round(wings.height * s)))
    big = wings.resize((W, H), Image.LANCZOS)
    # 확대로 흐려진 알파를 다시 또렷하게, 색은 두 가지로 스냅
    a = np.array(big)
    alpha = a[..., 3] > 100
    dark = a[..., :3].astype(int).max(axis=2) < 140
    a[...] = 0
    a[alpha & dark] = INK; a[alpha & ~dark] = BODY
    big = Image.fromarray(a).filter(ImageFilter.SMOOTH)
    # 원본 몸통 박스의 왼쪽 위가 치즈냥이 몸통 박스의 왼쪽 위에 오도록 정렬
    ox = int(round(cl - bl * s)); oy = int(round(ct - bt * s))
    left = min(0, ox); top = min(0, oy)
    right = max(cat.width, ox + big.width); bottom = max(cat.height, oy + big.height)
    canvas = Image.new('RGBA', (right - left, bottom - top), (0, 0, 0, 0))
    canvas.alpha_composite(big, (ox - left, oy - top))          # 날개는 몸 뒤
    canvas.alpha_composite(cat, (-left, -top))
    canvas = canvas.crop(canvas.getbbox())
    sc = target_h / canvas.height
    canvas = canvas.resize((max(1, round(canvas.width * sc)), target_h), Image.LANCZOS)
    canvas.save(os.path.join(DST, dst_name)); print(dst_name, canvas.size)


def main():
    compose('cat_normal.png', 'cat_flying.png', 113)
    compose('cat_ally.png', 'cat_flying_ally.png', 113)


if __name__ == '__main__':
    main()
