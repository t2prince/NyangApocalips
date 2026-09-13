"""아군 냥이 = 일반 냥이와 같은 그림, 꼬리만 얇게 (사용자 규칙).
Plan/cat_sprites5/normal_cat.png 에서 몸 뒤쪽의 수북한 꼬리를 지우고 가는 선 꼬리를 그린 뒤,
rebuild_anim.clean_static 과 같은 정리·축소를 거쳐 drawable-nodpi/cat_ally.png 로 저장한다.

사용법: python scripts/make_ally_from_normal.py
"""
import os, sys
sys.path.insert(0, 'scripts')
import numpy as np
from PIL import Image, ImageDraw
from sheet_to_sprites import strip_background
from rebuild_anim import label_components, clean_frame, trim, DST_DIR, INK
from remove_front_tail import dilate

SRC = 'Plan/cat_sprites6_auto_normal_cat.png'   # 매끈한 대기 시트에서 뽑은 일반 냥이
TARGET_H = 91          # cat_normal 과 같은 키
OUTLINE = 8


def main():
    im = strip_background(Image.open(SRC).convert('RGBA'))
    arr = np.array(im)
    m = arr[..., 3] > 128
    rgb = arr[..., :3].astype(int)
    ink = m & (rgb.sum(axis=2) / 3 < 110)
    comps = sorted(label_components(m & ~ink), key=lambda t: -t[0])
    body = np.zeros_like(m); body[comps[0][1], comps[0][2]] = True
    x_bl = comps[0][2].min()
    yellow = (rgb[..., 0] > 190) & (rgb[..., 1] > 130) & (rgb[..., 2] < 130)
    tail = np.zeros_like(m)
    for area, ys, xs in comps[1:]:
        if area < 300 or yellow[ys, xs].mean() < 0.5: continue
        if xs.mean() >= x_bl + 5: continue               # 몸 뒤쪽(왼쪽) 바깥의 노란 덩어리 = 꼬리
        tail[ys, xs] = True
    if not tail.any():
        print('꼬리를 못 찾았다'); return
    protect = dilate(body, OUTLINE)
    erase = dilate(tail, OUTLINE + 1) & m & ~protect
    erase |= dilate(erase, 8) & ~(m & ~erase)
    arr[erase] = (0, 0, 0, 0)
    out = Image.fromarray(arr)

    # 가는 선 꼬리: 몸 왼쪽 위 가장자리에서 시작해 위로 말린다
    ys, xs = np.nonzero(body)
    top, bot = ys.min(), ys.max()
    h = bot - top
    y0 = top + h * 0.35
    x0 = xs[np.abs(ys - y0) < 3].min() if (np.abs(ys - y0) < 3).any() else x_bl
    w = im.width
    import math
    # 부드러운 곡선: 몸에서 나와 위로 말려 올라간다
    curve = []
    for i in range(13):
        t = i / 12
        curve.append((x0 + 4 - w * 0.10 * math.sin(t * math.pi * 0.9), y0 - h * 0.52 * t))
    d = ImageDraw.Draw(out)
    lw = max(8, int(h * 0.05))
    d.line(curve, fill=INK + (255,), width=lw, joint='curve')
    tx, ty = curve[-1]; r = lw * 0.6
    d.ellipse((tx - r, ty - r, tx + r, ty + r), fill=INK + (255,))

    out = clean_frame(out, outline_min_area=500, stripe_min=14, noise_max=6, has_teal=False)
    out = trim(out)
    scale = TARGET_H / out.height
    out = out.resize((max(1, int(out.width * scale)), TARGET_H), Image.LANCZOS)
    path = os.path.join(DST_DIR, 'cat_ally.png')
    out.save(path)
    print(path, out.size)


if __name__ == '__main__':
    main()
