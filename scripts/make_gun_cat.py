"""총냥이 아군 그림 — 치즈냥이 아군(가는 꼬리)에 등에 달린 총을 얹는다 (대표님 2026-09-20 「총냥이도 자기 팀이 될 수 있어」).
전투의 적 총냥이는 코드로 총을 그리고(GunCat.drawGun), 팀 화면·감옥·아군은 이 구운 그림을 쓴다. 모양은 코드와 같게 맞춘다.
  cat_ally.png -> cat_gun_ally.png,  anim_cat_idle.png -> anim_cat_idle_gun.png
사용법: python scripts/make_gun_cat.py
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, 'scripts')
from rebuild_anim import label_components

DST = 'app/src/main/res/drawable-nodpi'
FRAMES = 7
GUN = (70, 72, 80); INK = (30, 30, 36)
ANGLE = 25   # 총열을 위로 드는 각도(도) — GunCat.drawGun 의 GUN_ANGLE 과 같게

def add_gun(im):
    im = im.convert('RGBA'); arr = np.array(im)
    a = arr[..., 3] > 128
    rgb = arr[..., :3].astype(int)
    ink = a & (rgb.max(axis=2) < 90); white = a & (rgb.min(axis=2) > 200)
    comps = sorted(label_components(a & ~ink & ~white), key=lambda t: -t[0])
    ys, xs = comps[0][1], comps[0][2]
    x0, y0, x1, y1 = xs.min(), ys.min(), xs.max(), ys.max()
    bw, bh = x1 - x0, y1 - y0
    d = ImageDraw.Draw(im)
    # 코드(GunCat.drawGun)와 같은 자리: 등(가로 36%) 위, 발에서 78% 높이. 총열은 머리를 안 가리게 25° 위로 든다.
    cx = x0 + bw * 0.36
    top = y1 - bh * 0.78
    w = bw
    lw = 2
    # 받침 (몸 안으로 조금 들어가 틈이 없게)
    d.rounded_rectangle([cx - w * 0.09, top - 2, cx + w * 0.09, top + bh * 0.11], radius=2, fill=GUN, outline=INK, width=lw)
    # 총 몸통 + 총열: 받침 위 점을 축으로 -25° 회전한 사각형들
    ang = np.deg2rad(-ANGLE)
    def rot(px, py):
        return (cx + px * np.cos(ang) - py * np.sin(ang), top + px * np.sin(ang) + py * np.cos(ang))
    gh = bh * 0.10
    body = [rot(-w * 0.12, -gh), rot(w * 0.10, -gh), rot(w * 0.10, 0), rot(-w * 0.12, 0)]
    d.polygon(body, fill=GUN, outline=INK)
    bl = w * 0.42
    barrel = [rot(w * 0.08, -gh * 0.8), rot(w * 0.08 + bl, -gh * 0.8), rot(w * 0.08 + bl, -gh * 0.2), rot(w * 0.08, -gh * 0.2)]
    d.polygon(barrel, fill=GUN, outline=INK)
    d.line(body + [body[0]], fill=INK, width=lw); d.line(barrel + [barrel[0]], fill=INK, width=lw)
    return im

def gun_strip(src, dst):
    strip = Image.open(os.path.join(DST, src)).convert('RGBA')
    w, h = strip.size; cw = w // FRAMES
    out = Image.new('RGBA', strip.size, (0, 0, 0, 0))
    for i in range(FRAMES):
        x = i * cw
        out.paste(add_gun(strip.crop((x, 0, x + cw if i < FRAMES - 1 else w, h))), (x, 0))
    out.save(os.path.join(DST, dst)); print(dst)

def main():
    add_gun(Image.open(os.path.join(DST, 'cat_ally.png'))).save(os.path.join(DST, 'cat_gun_ally.png')); print('cat_gun_ally.png')
    gun_strip('anim_cat_idle.png', 'anim_cat_idle_gun.png')                 # 아군 대기(춤) — 팀 화면
    # 도감용(적, 두꺼운 꼬리): 정지 + 대기 스트립 — 대표님 「도감의 총냥이도 등에 총」
    add_gun(Image.open(os.path.join(DST, 'cat_normal.png'))).save(os.path.join(DST, 'cat_gun.png')); print('cat_gun.png')
    gun_strip('anim_cat_idle.png', 'anim_cat_idle_gun_enemy.png')

if __name__ == '__main__':
    main()
