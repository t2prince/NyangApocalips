#!/usr/bin/env python3
"""앱 아이콘(런처 타일) 생성 — 게임 속 일반 냥이를 그대로 그린다.

 - drawable/ic_launcher_cat.png : 적응형 아이콘 전경 (432x432, 안전영역 안에 배치)
 - mipmap-*/ic_launcher.png     : 구형 런처용 사각 아이콘
 - mipmap-*/ic_launcher_round.png: 구형 런처용 원형 아이콘
"""

import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app/src/main/res")

OUTLINE = (44, 36, 30)
FUR = (238, 205, 96)
FUR_STRIPE = (208, 154, 62)
WHITE = (255, 255, 255)
PUPIL = (35, 33, 30)
BG = (255, 179, 71)          # 따뜻한 주황 배경 (기존 아이콘 색)


def draw_cat(size):
    """투명 배경에 그려진 일반 냥이 (정사각 캔버스, 아래를 조금 비운다)"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size / 256.0
    lw = max(3, int(7 * s))

    cx, cy = size * 0.5, size * 0.52
    rx, ry = size * 0.34, size * 0.30

    # 꼬리 (오른쪽 뒤로 통통하게)
    pts = []
    for i in range(14):
        t = i / 13.0
        pts.append((cx + rx * 0.8 + size * 0.20 * t,
                    cy - size * 0.04 - math.sin(t * 1.5) * size * 0.16))
    for i, (px, py) in enumerate(pts):
        r = size * (0.035 + 0.02 * (i / 13.0))
        d.ellipse([px - r, py - r, px + r, py + r], fill=OUTLINE)
    for i, (px, py) in enumerate(pts):
        r = max(1.0, size * (0.035 + 0.02 * (i / 13.0)) - lw * 0.5)
        d.ellipse([px - r, py - r, px + r, py + r], fill=FUR)

    # 다리
    for i in range(4):
        lx = cx - rx * 0.62 + i * (rx * 1.24 / 3)
        d.line([(lx, cy + ry - size * 0.03), (lx, cy + ry + size * 0.12)],
               fill=OUTLINE, width=lw)

    # 귀
    for ex in (cx - rx * 0.5, cx - rx * 0.03):
        d.polygon([(ex - size * 0.07, cy - ry * 0.82),
                   (ex, cy - ry - size * 0.11),
                   (ex + size * 0.07, cy - ry * 0.86)], fill=FUR, outline=OUTLINE)

    # 몸통
    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=FUR)
    for k in range(4):
        sx = cx - rx * 0.35 + k * rx * 0.4
        h = ry * 0.62 * (1 - abs(k - 1.5) * 0.16)
        d.line([(sx, cy - h), (sx - size * 0.02, cy + h * 0.5)], fill=FUR_STRIPE,
               width=max(3, int(11 * s)))
    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], outline=OUTLINE, width=lw)

    # 얼굴: 둥근 흰 볼 두 개 + 작은 점 눈
    mcy = cy + size * 0.06
    mr = size * 0.115
    for dx in (-0.175, 0.02):
        mx = cx + size * dx
        d.ellipse([mx - mr, mcy - mr, mx + mr, mcy + mr], fill=WHITE,
                  outline=OUTLINE, width=max(2, int(5 * s)))
    nx, ny = cx - size * 0.078, mcy - size * 0.025
    d.ellipse([nx - size * 0.03, ny - size * 0.02, nx + size * 0.03, ny + size * 0.025], fill=OUTLINE)
    d.line([(nx, ny + size * 0.025), (nx, ny + size * 0.06)], fill=OUTLINE, width=max(2, int(4 * s)))

    er = size * 0.026
    ecy = cy - size * 0.10
    for dx in (-0.175, 0.02):
        ex = cx + size * dx
        d.ellipse([ex - er, ecy - er, ex + er, ecy + er], fill=PUPIL)

    # 수염
    for sy in (-0.03, 0.008, 0.045):
        d.line([(cx - rx * 0.95, mcy + size * sy), (cx - rx * 1.45, mcy + size * (sy - 0.02))],
               fill=OUTLINE, width=max(2, int(4 * s)))
    return img


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print(f"  {os.path.relpath(path, ROOT)}")


def main():
    print("앱 아이콘(일반 냥이) 생성:")

    # 1) 적응형 아이콘 전경: 432x432, 가운데 264px 안전영역 안에 냥이를 담는다
    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    cat = draw_cat(250)
    fg.paste(cat, ((432 - 250) // 2, (432 - 250) // 2), cat)
    save(fg, os.path.join(RES, "drawable/ic_launcher_cat.png"))

    # 2) 구형 런처용 사각/원형 아이콘
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, px in densities.items():
        base = Image.new("RGBA", (px, px), BG + (255,))
        c = draw_cat(int(px * 0.86))
        base.paste(c, ((px - c.width) // 2, (px - c.height) // 2), c)
        save(base, os.path.join(RES, f"mipmap-{name}/ic_launcher.png"))

        # 원형: 배경을 원으로 자른다
        round_img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        mask = Image.new("L", (px, px), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, px - 1, px - 1], fill=255)
        round_img.paste(base, (0, 0), mask)
        save(round_img, os.path.join(RES, f"mipmap-{name}/ic_launcher_round.png"))

    print("완료")


if __name__ == "__main__":
    main()
