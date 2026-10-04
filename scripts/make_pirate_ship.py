"""해적선 구조물 — 기획14 `Plan/기획14_해적선_구조물.jpg` (대표님 2026-10-04). 31 해적 스테이지 끝에 놓는다.
그림 그대로: 갈색 널빤지 선체(둥근 포문 구멍), 왼쪽 높이 솟은 뱃머리와 앞으로 뻗은 막대, 오른쪽 올라간 고물,
가운데 돛대에 부풀어 오른 흰 돛 두 폭 + 안대 낀 냥이 얼굴과 엇갈린 뼈, 꼭대기 노란 깃발, 뱃머리까지 이어진 밧줄,
돛 양옆에서 갑판으로 내려오는 그물, 갑판 위 철창 두 개(안에 갇힌 인간).
갑판의 냥이들(해적냥이 등)은 그림에 넣지 않는다 — 스테이지에서 적으로 올라선다.
결과: app/src/main/res/drawable-nodpi/pirate_ship.png
사용법: python scripts/make_pirate_ship.py
"""
import math
import numpy as np
from PIL import Image, ImageDraw

W, H = 1500, 760                 # 배가 더 길다 (대표님 2026-10-04)
SS = 2
INK = (34, 28, 24, 255)
WOOD = (150, 78, 40, 255)
WOOD_D = (110, 54, 26, 255)
WOOD_L = (214, 146, 84, 255)
WOOD_TOP = (226, 168, 98, 255)   # 1번째 칸은 색이 밝다 (2026-10-04)
SAIL = (250, 248, 240, 255)


def main():
    im = Image.new('RGBA', (W * SS, H * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    P = lambda pts: [(x * SS, y * SS) for x, y in pts]
    L = lambda pts, c=INK, w=3: d.line(P(pts), fill=c, width=int(w * SS), joint='curve')

    deck_y = 470
    # ---- 돛대 + 깃발 ----
    mx = 780
    d.rectangle(P([(mx - 9, 70), (mx + 9, deck_y)]), fill=WOOD_D, outline=INK, width=SS * 3)
    d.polygon(P([(mx + 9, 72), (mx + 70, 82), (mx + 9, 118)]), fill=(250, 200, 70, 255), outline=INK)
    d.rectangle(P([(mx + 9, 72), (mx + 66, 112)]), fill=(250, 200, 70, 255), outline=INK, width=SS * 2)
    # ---- 돛 두 폭 (부풀어 오른 흰 천) ----
    def sail(x0, x1, y0, y1, bulge):
        pts = []
        for i in range(21):
            t = i / 20
            pts.append((x0 + (x1 - x0) * t, y0 - 14 * math.sin(math.pi * t)))
        for i in range(21):
            t = i / 20
            pts.append((x1 + bulge * math.sin(math.pi * t), y0 + (y1 - y0) * t))
        for i in range(21):
            t = i / 20
            pts.append((x1 - (x1 - x0) * t, y1 + 18 * math.sin(math.pi * t)))
        for i in range(21):
            t = i / 20
            pts.append((x0 - bulge * math.sin(math.pi * t), y1 - (y1 - y0) * t))
        d.polygon(P(pts), fill=SAIL, outline=INK, width=SS * 3)
    sail(620, 770, 130, 330, 22)
    sail(790, 940, 130, 330, 22)
    # 돛 무늬: 안대 낀 냥이 얼굴 + 엇갈린 뼈
    fx, fy = 780, 200
    L([(fx - 100, fy + 70), (fx + 100, fy + 120)], INK, 22); L([(fx - 100, fy + 70), (fx + 100, fy + 120)], SAIL, 16)
    L([(fx + 100, fy + 70), (fx - 100, fy + 120)], INK, 22); L([(fx + 100, fy + 70), (fx - 100, fy + 120)], SAIL, 16)
    for bx, by in ((fx - 100, fy + 70), (fx + 100, fy + 120), (fx + 100, fy + 70), (fx - 100, fy + 120)):
        d.ellipse(P([(bx - 13, by - 13), (bx + 13, by + 13)]), fill=SAIL, outline=INK, width=SS * 3)
    d.ellipse(P([(fx - 62, fy - 60), (fx + 62, fy + 58)]), fill=SAIL, outline=INK, width=SS * 4)
    d.polygon(P([(fx - 54, fy - 34), (fx - 42, fy - 82), (fx - 16, fy - 54)]), fill=SAIL, outline=INK, width=SS * 4)
    d.polygon(P([(fx + 16, fy - 54), (fx + 42, fy - 82), (fx + 54, fy - 34)]), fill=SAIL, outline=INK, width=SS * 4)
    d.ellipse(P([(fx - 30, fy - 14), (fx - 4, fy + 12)]), fill=INK)                 # 안대
    L([(fx - 58, fy - 40), (fx + 56, fy + 6)], INK, 3)                                # 안대 끈
    d.ellipse(P([(fx + 14, fy - 6), (fx + 22, fy + 2)]), fill=INK)                    # 다른 눈
    d.ellipse(P([(fx - 26, fy + 14), (fx + 2, fy + 42)]), fill=SAIL, outline=INK, width=SS * 3)   # 주둥이 두 원
    d.ellipse(P([(fx - 2, fy + 14), (fx + 26, fy + 42)]), fill=SAIL, outline=INK, width=SS * 3)
    d.polygon(P([(fx - 6, fy + 12), (fx + 6, fy + 12), (fx, fy + 20)]), fill=INK)
    for k in (-1, 1):
        for j in range(3):
            L([(fx + k * 26, fy + 26 + j * 6), (fx + k * 54, fy + 20 + j * 9)], INK, 2)
    # ---- 밧줄: 돛대 꼭대기 → 뱃머리 막대 끝 ----
    bow_tip = (170, 400)
    L([(mx, 120), bow_tip], INK, 3)
    L([(mx, 120), (1330, 452)], INK, 2)
    # ---- 그물: 돛 아래 양옆에서 갑판으로 ----
    def qb(p0, p1, p2, n=16):
        return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0], (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]) for t in np.linspace(0, 1, n)]
    def net(top_a, top_b, bot_a, bot_b, n=7, sag=38):
        """축 처진 그물 (대표님 2026-10-04): 가로줄은 가운데가 아래로 늘어지고, 세로줄도 바깥으로 불룩하게 처진다"""
        def side(p, q, t):
            return (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)
        for i in range(n + 1):                                           # 가로줄
            t = i / n
            l, r = side(top_a, bot_a, t), side(top_b, bot_b, t)
            mid = ((l[0] + r[0]) / 2, (l[1] + r[1]) / 2 + sag * (0.6 + 0.4 * t))
            L(qb(l, mid, r), INK, 2.2)
        for i in range(n + 1):                                           # 세로줄
            t = i / n
            tp, bt = side(top_a, top_b, t), side(bot_a, bot_b, t)
            droop = sag * 0.9 * math.sin(math.pi * t) + sag * 0.3
            mid = ((tp[0] + bt[0]) / 2, (tp[1] + bt[1]) / 2 + droop)
            L(qb(tp, mid, bt), INK, 2.2)
    net((580, 320), (640, 336), (560, deck_y - 4), (660, deck_y))
    net((940, 336), (1000, 320), (1000, deck_y), (1120, deck_y - 4))
    # ---- 철창 두 개 (안에 갇힌 인간) — 일반 스테이지 감옥(entity/Prison.kt)과 같은 모습 (대표님 2026-10-04) ----
    SKIN = (255, 200, 150, 255); FACE = (40, 34, 30, 255); TEAR = (110, 175, 240, 255)
    for cx in (700, 860):
        k = 2.6                                          # 감옥 그림(폭 100·높이 140)을 이 배율로
        x0, x1, y1 = cx - 50 * k / 2.6 * 1.0, cx + 50 * k / 2.6 * 1.0, deck_y
        cw, ch = 100 * 0.88, 140 * 0.88
        x0, x1, y0 = cx - cw / 2, cx + cw / 2, deck_y - ch
        cy = y0 + ch / 2
        s_ = ch / 140                                    # Prison 좌표 → 여기
        hx, hy = cx, cy - 15 * s_
        d.ellipse(P([(hx - 9 * s_ * 1.7, hy - 9 * s_ * 1.7), (hx + 9 * s_ * 1.7, hy + 9 * s_ * 1.7)]), fill=SKIN)
        d.rectangle(P([(cx - 6 * s_ * 1.7, cy - 5 * s_), (cx + 6 * s_ * 1.7, cy + 15 * s_ * 1.6)]), fill=SKIN)
        L([(cx - 5 * s_ * 1.7, cy - 19 * s_), (cx - 2 * s_ * 1.7, cy - 17 * s_)], FACE, 2.5)
        L([(cx + 5 * s_ * 1.7, cy - 19 * s_), (cx + 2 * s_ * 1.7, cy - 17 * s_)], FACE, 2.5)
        d.arc(P([(cx - 4 * s_ * 1.7, cy - 13 * s_), (cx + 4 * s_ * 1.7, cy - 7 * s_)]), 200, 340, fill=FACE, width=int(2.5 * SS))
        d.ellipse(P([(cx - 7.5 * s_ * 1.7, cy - 13.5 * s_), (cx - 4.5 * s_ * 1.7, cy - 10.5 * s_)]), fill=TEAR)
        d.ellipse(P([(cx - 9.5 * s_ * 1.7, cy - 7.5 * s_), (cx - 6.5 * s_ * 1.7, cy - 4.5 * s_)]), fill=TEAR)
        d.rectangle(P([(x0, y0), (x1, y1)]), outline=(128, 128, 128, 255), width=int(4 * SS))
        for i in range(1, 5):
            bx = x0 + (x1 - x0) * i / 5
            L([(bx, y0), (bx, y1)], (68, 68, 68, 255), 3)
    # ---- 선체: 길게. 옆면은 가로 4칸으로 나뉘고 맨 위 1번째 칸은 밝다 (대표님 2026-10-04) ----
    def bz(p0, p1, p2, n=30):
        return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0], (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]) for t in np.linspace(0, 1, n)]
    # 선이 꺾이지 않게 (대표님 2026-10-04): 뱃머리→갑판→고물, 고물→바닥→뱃머리 모두 부드러운 곡선으로 잇는다
    top = bz((112, 332), (240, 468), (420, 470)) + bz((420, 470), (830, 470), (1240, 470))[1:] + bz((1240, 470), (1340, 470), (1420, 436))[1:]
    bottom = bz((1420, 436), (1415, 640), (1290, 690))[1:] + bz((1290, 690), (860, 708), (430, 700))[1:] + bz((430, 700), (250, 690), (112, 332))[1:]
    hull = top + bottom
    d.polygon(P(hull), fill=WOOD)
    # 열마다 선체 윗선(T)·아랫선(B)을 재서, 그 사이를 4등분한 경계선을 긋는다 (뱃머리 쪽은 윗선을 따라 휘어 오른다)
    mask = Image.new('L', (W, H), 0); ImageDraw.Draw(mask).polygon(hull, fill=255)
    mk = np.asarray(mask) > 0
    cols = [x for x in range(150, 1410, 4) if mk[:, x].any()]
    T = {x: np.nonzero(mk[:, x])[0].min() for x in cols}
    B = {x: np.nonzero(mk[:, x])[0].max() for x in cols}
    def band_line(k):
        ys = np.array([T[x] + (B[x] - T[x]) * k / 4 for x in cols], float)
        ker = np.ones(9) / 9
        sm = np.convolve(np.pad(ys, 4, mode='edge'), ker, mode='valid')     # 꺾임 없이 매끈하게
        return list(zip(cols, sm))
    # 1번째 칸(맨 위)을 밝게
    top_band = [(x, T[x] + 3) for x in cols] + band_line(1)[::-1]
    d.polygon(P(top_band), fill=WOOD_TOP)
    # 3·4번째 칸은 조금 더 짙게 (크레파스 칠 느낌)
    low_band = band_line(3) + [(x, B[x] - 3) for x in cols][::-1]
    d.polygon(P(low_band), fill=WOOD_D)
    for k in (1, 2, 3):
        L(band_line(k), INK, 3)
    d.polygon(P(hull), outline=INK, width=SS * 4)
    # 뱃머리 막대(앞으로 뻗은 나무)
    L([(112, 332), bow_tip], WOOD_D, 10); L([(112, 332), bow_tip], INK, 2)
    # 갑판 난간 (윗선 그대로)
    L(top, INK, 4)
    # 포문 구멍: 1번째 칸 가운데 줄에
    for k in range(11):
        px = 400 + k * 86
        if px not in T and min(cols, key=lambda c: abs(c - px)) is None: continue
        cx_ = min(cols, key=lambda c: abs(c - px))
        py = T[cx_] + (B[cx_] - T[cx_]) / 8
        d.ellipse(P([(px - 12, py - 12), (px + 12, py + 12)]), fill=(46, 30, 20, 255), outline=INK, width=SS * 2)
    im = im.resize((W, H), Image.LANCZOS)
    im.save('app/src/main/res/drawable-nodpi/pirate_ship.png')
    print('pirate_ship.png', im.size)


if __name__ == '__main__':
    main()
