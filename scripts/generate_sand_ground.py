#!/usr/bin/env python3
"""5~6스테이지(사막)용 지면 스트립 생성.

grass_ground.png / snow_ground.png과 같은 512x232 규격 —
위쪽 약 46px이 표면(고운 모래), 그 아래가 굳은 모래층이다.
"""

from PIL import Image, ImageDraw
import math
import os
import random

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app/src/main/res/drawable")
os.makedirs(OUT, exist_ok=True)

W, H = 512, 232
SAND_H = 46

SAND_TOP = (246, 216, 150)      # 햇빛 받는 표면 모래
SAND_SHADE = (222, 186, 118)    # 표면 아래 그늘
SAND_DEEP = (196, 158, 96)      # 굳은 모래층 위
SAND_DEEP_DARK = (166, 130, 76)  # 굳은 모래층 아래
STONE = (176, 148, 104)
PEBBLE = (146, 120, 84)


def main():
    rnd = random.Random(20260726)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 굳은 모래층: 아래로 갈수록 어두워진다
    for y in range(SAND_H, H):
        t = (y - SAND_H) / (H - SAND_H)
        c = tuple(int(SAND_DEEP[i] + (SAND_DEEP_DARK[i] - SAND_DEEP[i]) * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=c + (255,))

    # 모래 속 잔돌
    for _ in range(28):
        x = rnd.randint(12, W - 12)
        y = rnd.randint(SAND_H + 18, H - 14)
        r = rnd.randint(3, 7)
        col = STONE if rnd.random() < 0.6 else PEBBLE
        d.ellipse([x - r, y - r * 0.8, x + r * 1.15, y + r * 0.8], fill=col + (230,))

    # 표면 모래층: 위아래가 물결치는 띠 (W 주기 사인파 → 좌우 타일링)
    def top_edge(x):
        return 9 + 4 * math.sin(2 * math.pi * 2 * x / W + 0.4) + 2 * math.sin(2 * math.pi * 6 * x / W)

    def bottom_edge(x):
        return (SAND_H + 9 * math.sin(2 * math.pi * 3 * x / W + 1.9)
                + 4 * math.sin(2 * math.pi * 6 * x / W + 0.6))

    xs = list(range(W + 1))
    d.polygon([(x, top_edge(x)) for x in xs] + [(x, bottom_edge(x)) for x in reversed(xs)],
              fill=SAND_TOP)
    d.polygon([(x, bottom_edge(x) - 8) for x in xs] + [(x, bottom_edge(x)) for x in reversed(xs)],
              fill=SAND_SHADE)

    img.save(f"{OUT}/sand_ground.png")
    print(f"  sand_ground.png ({W}x{H})")


if __name__ == "__main__":
    print("사막 지면 생성:")
    main()
    print("완료")
