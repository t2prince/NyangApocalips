#!/usr/bin/env python3
"""3~4스테이지(설원)용 지면 스트립 생성.

grass_ground.png과 같은 512x232 규격 — 위쪽 약 45px이 표면(눈),
그 아래가 얼어붙은 흙이다. 게임에서는 세로 슬라이스로 잘라 지형을 따라 깔린다.
"""

from PIL import Image, ImageDraw
import math
import os
import random

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app/src/main/res/drawable")
os.makedirs(OUT, exist_ok=True)

W, H = 512, 232
SNOW_H = 46                      # 표면 눈층 두께 (잔디와 같은 위치)

SNOW = (247, 251, 255)
SNOW_SHADE = (214, 230, 245)
FROZEN_DIRT = (104, 82, 68)
FROZEN_DIRT_DARK = (86, 67, 56)
ICE_CHUNK = (196, 222, 238)
PEBBLE = (150, 150, 155)


def main():
    rnd = random.Random(20260726)   # 결과가 매번 같도록 고정 시드
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 얼어붙은 흙: 아래로 갈수록 어두워지는 세로 그라데이션
    for y in range(SNOW_H, H):
        t = (y - SNOW_H) / (H - SNOW_H)
        c = tuple(int(FROZEN_DIRT[i] + (FROZEN_DIRT_DARK[i] - FROZEN_DIRT[i]) * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=c + (255,))

    # 흙 속 얼음 조각·자갈 (좌우 타일링을 위해 가장자리는 피한다)
    for _ in range(26):
        x = rnd.randint(12, W - 12)
        y = rnd.randint(SNOW_H + 18, H - 14)
        r = rnd.randint(3, 7)
        col = ICE_CHUNK if rnd.random() < 0.55 else PEBBLE
        d.ellipse([x - r, y - r, x + r * 1.2, y + r * 0.85], fill=col + (235,))

    # 눈층: 위아래 모두 물결치는 띠 (좌우 끝이 이어지도록 W 주기의 사인파만 사용)
    def top_edge(x):
        return 7 + 5 * math.sin(2 * math.pi * 3 * x / W) + 2.5 * math.sin(2 * math.pi * 5 * x / W + 1.3)

    def bottom_edge(x):
        return (SNOW_H + 12 * math.sin(2 * math.pi * 2 * x / W + 0.7)
                + 5 * math.sin(2 * math.pi * 5 * x / W + 2.1))

    xs = list(range(W + 1))
    snow_poly = [(x, top_edge(x)) for x in xs] + [(x, bottom_edge(x)) for x in reversed(xs)]
    d.polygon(snow_poly, fill=SNOW)

    # 눈과 흙이 만나는 경계의 푸른 그림자 (아래 가장자리를 따라 흐른다)
    shade_poly = ([(x, bottom_edge(x) - 9) for x in xs]
                  + [(x, bottom_edge(x)) for x in reversed(xs)])
    d.polygon(shade_poly, fill=SNOW_SHADE)

    # 눈밭에 흩뿌린 반짝임
    for _ in range(30):
        x = rnd.randint(6, W - 6)
        y = rnd.randint(10, SNOW_H - 12)
        d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=(255, 255, 255, 255))

    img.save(f"{OUT}/snow_ground.png")
    print(f"  snow_ground.png ({W}x{H})")


if __name__ == "__main__":
    print("설원 지면 생성:")
    main()
    print("완료")
