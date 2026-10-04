"""해적선 구조물 — 기획14 `Plan/기획14_해적선_구조물.jpg` (대표님 2026-10-04). 31 해적 스테이지 끝에 놓는다.
그림 그대로: 갈색 널빤지 선체(둥근 포문 구멍), 왼쪽 높이 솟은 뱃머리와 앞으로 뻗은 막대, 오른쪽 올라간 고물,
가운데 돛대에 부풀어 오른 흰 돛 두 폭 + 안대 낀 냥이 얼굴과 엇갈린 뼈, 꼭대기 노란 깃발, 뱃머리까지 이어진 밧줄,
돛 양옆에서 갑판으로 내려오는 그물, 갑판 위 철창 두 개(안에 갇힌 인간).
갑판의 냥이들(해적냥이 등)은 그림에 넣지 않는다 — 스테이지에서 적으로 올라선다.
결과: app/src/main/res/drawable-nodpi/pirate_ship.png
사용법: python scripts/make_pirate_ship.py
"""
import math
from PIL import Image, ImageDraw

W, H = 1100, 760
SS = 2
INK = (34, 28, 24, 255)
WOOD = (150, 78, 40, 255)
WOOD_D = (110, 54, 26, 255)
WOOD_L = (196, 118, 66, 255)
SAIL = (250, 248, 240, 255)


def main():
    im = Image.new('RGBA', (W * SS, H * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    P = lambda pts: [(x * SS, y * SS) for x, y in pts]
    L = lambda pts, c=INK, w=3: d.line(P(pts), fill=c, width=int(w * SS), joint='curve')

    deck_y = 470
    # ---- 돛대 + 깃발 ----
    mx = 640
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
    sail(480, 630, 130, 330, 22)
    sail(650, 800, 130, 330, 22)
    # 돛 무늬: 안대 낀 냥이 얼굴 + 엇갈린 뼈
    fx, fy = 640, 200
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
    L([(mx, 120), (930, 455)], INK, 2)
    # ---- 그물: 돛 아래 양옆에서 갑판으로 ----
    def net(top_a, top_b, bot_a, bot_b, n=7):
        for i in range(n + 1):
            t = i / n
            a = (top_a[0] + (top_b[0] - top_a[0]) * t, top_a[1] + (top_b[1] - top_a[1]) * t)
            b = (bot_a[0] + (bot_b[0] - bot_a[0]) * t, bot_a[1] + (bot_b[1] - bot_a[1]) * t)
            L([a, b], INK, 2.2)
        for i in range(n + 1):
            t = i / n
            a = (top_a[0] + (bot_a[0] - top_a[0]) * t, top_a[1] + (bot_a[1] - top_a[1]) * t)
            b = (top_b[0] + (bot_b[0] - top_b[0]) * t, top_b[1] + (bot_b[1] - top_b[1]) * t)
            L([a, b], INK, 2.2)
    net((440, 320), (500, 336), (430, deck_y - 4), (520, deck_y))
    net((800, 336), (860, 320), (850, deck_y), (960, deck_y - 6))
    # ---- 철창 두 개 (안에 갇힌 인간) ----
    for cx in (575, 705):
        x0, x1, y0, y1 = cx - 40, cx + 40, deck_y - 110, deck_y
        d.rectangle(P([(x0, y0), (x1, y1)]), fill=(236, 228, 212, 255), outline=INK, width=SS * 4)
        # 졸라맨 인간 (울상)
        hx, hy = cx, y0 + 30
        d.ellipse(P([(hx - 13, hy - 13), (hx + 13, hy + 13)]), fill=(255, 255, 255, 255), outline=INK, width=SS * 3)
        L([(hx - 5, hy + 5), (hx, hy + 2), (hx + 5, hy + 5)], INK, 2)
        L([(hx, hy + 13), (hx, hy + 60)], INK, 3)
        L([(hx - 20, hy + 30), (hx, hy + 22), (hx + 20, hy + 30)], INK, 3)
        L([(hx, hy + 60), (hx - 14, y1 - 4)], INK, 3); L([(hx, hy + 60), (hx + 14, y1 - 4)], INK, 3)
        for k in range(5):
            bx = x0 + (x1 - x0) * (k + 0.5) / 5
            L([(bx, y0 + 2), (bx, y1 - 2)], (70, 70, 76, 255), 4)
    # ---- 선체 ----
    hull = [(110, 330), (150, 350), (290, deck_y), (870, deck_y), (920, 440), (1010, 438), (980, 600), (900, 690), (420, 700), (330, 650)]
    d.polygon(P(hull), fill=WOOD, outline=INK, width=SS * 4)
    # 뱃머리 막대(앞으로 뻗은 나무)
    L([(110, 330), bow_tip], WOOD_D, 10); L([(110, 330), bow_tip], INK, 2)
    # 갑판 난간 (위 가장자리 밝은 띠)
    L([(150, 350), (290, deck_y), (870, deck_y), (920, 440), (1010, 438)], WOOD_L, 8)
    L([(150, 350), (290, deck_y), (870, deck_y), (920, 440), (1010, 438)], INK, 3)
    # 널빤지 줄
    for k in range(1, 5):
        t = k / 5
        y = deck_y + (690 - deck_y) * t
        xa = 290 + (330 - 290) * t + 40 * t
        xb = 980 - 60 * t
        L([(xa + 30 * (1 - t) - 20, y - 20 * (1 - t)), (xb, y)], WOOD_D, 4)
    # 포문 구멍
    for k in range(8):
        px = 380 + k * 78
        py = deck_y + 40
        d.ellipse(P([(px - 13, py - 13), (px + 13, py + 13)]), fill=(46, 30, 20, 255), outline=INK, width=SS * 2)
    im = im.resize((W, H), Image.LANCZOS)
    im.save('app/src/main/res/drawable-nodpi/pirate_ship.png')
    print('pirate_ship.png', im.size)


if __name__ == '__main__':
    main()
