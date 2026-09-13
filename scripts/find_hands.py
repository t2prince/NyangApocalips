"""졸라맨 인간 스트립에서 프레임마다 두 손 위치를 찾는다 (권투 장갑을 손에 붙이기 위해).

방법: 검은 선을 한 픽셀 뼈대로 가늘게(Zhang-Suen) → 끝점(이웃 1개)을 찾는다 →
머리(선으로 둘러싸인 흰 원) 테두리에서 뼈대를 따라 가장 가까운 끝점 둘 = 손 (팔이 다리보다 머리에 가깝다).

출력: app/src/main/java/com/jamcat/nayngapo/ui/HandTable.kt (프레임 비율 좌표), Plan/_debug/hands_check.png (확인용)
사용법: python scripts/find_hands.py
"""
import os, sys
from collections import deque
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rebuild_anim import label_components

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'app/src/main/res/drawable-nodpi')
OUT_KT = os.path.join(ROOT, 'app/src/main/java/com/jamcat/nayngapo/ui/HandTable.kt')
DEBUG = os.path.join(ROOT, 'Plan/_debug/hands_check.png')
STRIPS = ['player_idle', 'player_walk', 'player_jump', 'player_punch', 'player_cross',
          'player_kick', 'player_jumpkick', 'player_stomp']
FRAMES = 7
N8 = [(-1, 0), (-1, 1), (0, 1), (1, 1), (1, 0), (1, -1), (0, -1), (-1, -1)]


def thin(mask):
    """Zhang-Suen 세선화."""
    img = np.pad(mask.astype(np.uint8), 1)
    changed = True
    while changed:
        changed = False
        for step in (0, 1):
            P = [np.roll(np.roll(img, -dy, 0), -dx, 1) for dy, dx in N8]   # P2..P9
            B = sum(P)
            seq = P + [P[0]]
            A = sum(((seq[i] == 0) & (seq[i + 1] == 1)).astype(np.uint8) for i in range(8))
            if step == 0:
                c = (P[0] * P[2] * P[4] == 0) & (P[2] * P[4] * P[6] == 0)
            else:
                c = (P[0] * P[2] * P[6] == 0) & (P[0] * P[4] * P[6] == 0)
            rm = (img == 1) & (B >= 2) & (B <= 6) & (A == 1) & c
            if rm.any():
                img[rm] = 0
                changed = True
    return img[1:-1, 1:-1].astype(bool)


def hands_of(frame):
    a = np.array(frame)
    ink = (a[..., 3] > 128) & (a[..., :3].max(-1) < 120)
    h, w = ink.shape
    # 머리: 선으로 둘러싸인(바깥과 이어지지 않은) 밝은 영역 중 가장 큰 것
    holes = [c for c in label_components(~ink)
             if c[1].min() > 0 and c[2].min() > 0 and c[1].max() < h - 1 and c[2].max() < w - 1]
    if not holes:
        return None
    _, hy, hx = max(holes, key=lambda c: c[0])
    head = np.zeros_like(ink); head[hy, hx] = True
    sk = thin(ink)
    ys, xs = np.nonzero(sk)
    if len(ys) == 0:
        return None
    # 뼈대 이웃 수
    pad = np.pad(sk, 1)
    nb = sum(np.roll(np.roll(pad, -dy, 0), -dx, 1) for dy, dx in N8)[1:-1, 1:-1]
    ends = list(zip(*np.nonzero(sk & (nb == 1))))
    # 머리 테두리와 닿는 뼈대 픽셀에서 BFS
    ring = np.zeros_like(sk)
    hy0, hx0 = int(hy.mean()), int(hx.mean())
    r = max(hy.max() - hy.min(), hx.max() - hx.min()) / 2 + 6
    yy, xx = np.nonzero(sk)
    near = (yy - hy0) ** 2 + (xx - hx0) ** 2 <= r * r
    dist = np.full(sk.shape, -1, int)
    q = deque()
    for y, x in zip(yy[near], xx[near]):
        dist[y, x] = 0; q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in N8:
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and sk[ny, nx] and dist[ny, nx] < 0:
                dist[ny, nx] = dist[y, x] + 1; q.append((ny, nx))
    cands = sorted((dist[y, x], y, x) for y, x in ends if dist[y, x] > 3)
    far = max((c[0] for c in cands), default=0)
    # 발 제외: 뼈대 길이가 가장 먼 끝(발)의 62%를 넘거나, 바닥 가까이(아래 20%)에 있으면 발로 본다
    hands = [c for c in cands if c[0] <= far * 0.62 and c[1] < h * 0.80]
    picks = hands[:2]
    if len(picks) == 1:                  # 한 손은 몸 뒤에 가려졌다 → 같은 자리에 하나만
        picks = picks * 2
    if not picks:                        # 두 팔이 몸통에 붙었다 → 어깨 아래(허리 옆)로 추정
        hr = max(hy.max() - hy.min(), hx.max() - hx.min()) / 2
        est = (hx.mean() / w, min(0.75, (hy.max() + hr * 2.6) / h))
        return [est, est]
    return [(x / w, y / h) for _, y, x in picks]


def main():
    table = {}
    dbg_rows = []
    for name in STRIPS:
        im = Image.open(os.path.join(SRC, f'{name}.png')).convert('RGBA')
        fw = im.width // FRAMES
        frames = []
        for f in range(FRAMES):
            fr = im.crop((f * fw, 0, (f + 1) * fw, im.height))
            hs = hands_of(fr)
            frames.append(hs)
        # 못 찾은 프레임은 이웃 프레임 값으로 메운다
        for f in range(FRAMES):
            if frames[f] is None:
                for k in range(1, FRAMES):
                    for g in (f - k, f + k):
                        if 0 <= g < FRAMES and frames[g] is not None:
                            frames[f] = frames[g]; break
                    if frames[f] is not None: break
            if frames[f] is None:
                frames[f] = [(0.72, 0.36), (0.80, 0.42)]
        table[name] = frames
        # 확인 그림 (3배)
        K = 3
        row = Image.new('RGBA', (im.width * K, im.height * K), (255, 255, 255, 255))
        row.alpha_composite(im.resize((im.width * K, im.height * K), Image.NEAREST))
        d = ImageDraw.Draw(row)
        for f, hs in enumerate(frames):
            for (fx, fy), col in zip(hs, ((230, 40, 40, 255), (40, 90, 230, 255))):
                cx, cy = (f + fx) * fw * K, fy * im.height * K
                d.ellipse((cx - 9, cy - 9, cx + 9, cy + 9), outline=col, width=4)
        dbg_rows.append(row)
    Wd = max(r.width for r in dbg_rows); Hd = sum(r.height + 10 for r in dbg_rows)
    dbg = Image.new('RGBA', (Wd, Hd), (255, 255, 255, 255)); y = 0
    for r in dbg_rows:
        dbg.alpha_composite(r, (0, y)); y += r.height + 10
    os.makedirs(os.path.dirname(DEBUG), exist_ok=True)
    dbg.save(DEBUG)

    lines = ['package com.jamcat.nayngapo.ui', '',
             '/**',
             ' * 인간 스트립 프레임마다 두 손의 위치 (프레임 안 비율, 오른쪽을 볼 때).',
             ' * scripts/find_hands.py 가 생성한다 — 손으로 고치지 말 것.',
             ' * 각 프레임 = floatArrayOf(손1 x, 손1 y, 손2 x, 손2 y)',
             ' */',
             'object HandTable {']
    for name, frames in table.items():
        key = name.replace('player_', '').upper()
        vals = ',\n        '.join('floatArrayOf(%.3ff, %.3ff, %.3ff, %.3ff)' % (a[0], a[1], b[0], b[1]) for a, b in frames)
        lines.append(f'    val {key} = arrayOf(\n        {vals}\n    )')
    lines.append('}')
    open(OUT_KT, 'w').write('\n'.join(lines) + '\n')
    print('wrote', OUT_KT, DEBUG)


if __name__ == '__main__':
    main()
