#!/usr/bin/env python3
"""7~8스테이지(화산)용 지면 스트립 생성.

512x232 규격 — 위쪽 약 46px이 표면 암반, 그 아래는 더 어두운 현무암층.
용암은 배경의 화산에서만 흐르고, 바닥은 담백한 검은 돌로 둔다.
"""

from PIL import Image, ImageDraw
import math
import os
import random

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app/src/main/res/drawable")
os.makedirs(OUT, exist_ok=True)

W, H = 512, 232
CRUST_H = 46

CRUST = (62, 60, 64)          # 표면 암반 (빛을 받는 면)
CRUST_EDGE = (44, 42, 46)     # 표면 아래 그늘
ROCK_TOP = (46, 44, 48)       # 현무암층 위
ROCK_BOTTOM = (26, 25, 28)    # 현무암층 아래
STONE_LIGHT = (78, 76, 80)
STONE_DARK = (34, 33, 36)


def main():
    rnd = random.Random(20260726)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 현무암층: 아래로 갈수록 어둡게
    for y in range(CRUST_H, H):
        t = (y - CRUST_H) / (H - CRUST_H)
        c = tuple(int(ROCK_TOP[i] + (ROCK_BOTTOM[i] - ROCK_TOP[i]) * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=c + (255,))

    # 돌 결: 각진 바위 조각을 흩뿌려 현무암 질감을 낸다
    for _ in range(34):
        x = rnd.randint(12, W - 12)
        y = rnd.randint(CRUST_H + 16, H - 12)
        r = rnd.randint(4, 11)
        col = STONE_LIGHT if rnd.random() < 0.45 else STONE_DARK
        pts = []
        for k in range(6):
            a = math.radians(k * 60 + rnd.randint(-14, 14))
            rr = r * rnd.uniform(0.72, 1.15)
            pts.append((x + math.cos(a) * rr, y + math.sin(a) * rr * 0.8))
        d.polygon(pts, fill=col + (215,))

    # 표면 암반: 위아래가 물결치는 띠 (W 주기 사인파 → 좌우 타일링)
    def top_edge(x):
        return 8 + 5 * math.sin(2 * math.pi * 3 * x / W + 0.9) + 2.5 * math.sin(2 * math.pi * 7 * x / W)

    def bottom_edge(x):
        return (CRUST_H + 10 * math.sin(2 * math.pi * 2 * x / W + 2.4)
                + 4 * math.sin(2 * math.pi * 5 * x / W + 1.1))

    xs = list(range(W + 1))
    d.polygon([(x, top_edge(x)) for x in xs] + [(x, bottom_edge(x)) for x in reversed(xs)],
              fill=CRUST)
    d.polygon([(x, bottom_edge(x) - 7) for x in xs] + [(x, bottom_edge(x)) for x in reversed(xs)],
              fill=CRUST_EDGE)

    # 표면 위 갈라진 돌 틈 (짧은 어두운 선)
    for _ in range(16):
        x = rnd.randint(10, W - 24)
        y = rnd.randint(16, CRUST_H - 12)
        d.line([(x, y), (x + rnd.randint(10, 26), y + rnd.randint(-4, 4))],
               fill=STONE_DARK + (200,), width=2)

    img.save(f"{OUT}/ash_ground.png")
    print(f"  ash_ground.png ({W}x{H})")


if __name__ == "__main__":
    print("화산 지면(검은 암반) 생성:")
    main()
    print("완료")
