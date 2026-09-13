#!/usr/bin/env python3
"""cats/ 디자인 시트를 참고한 적 냥이 스프라이트 생성.

시트의 손그림 톤 — 통통한 타원 몸통, 굵은 검은 외곽선, 큼직한 흰 눈,
작은 삼각 귀, 짧은 네 다리, 꼬리 — 을 그대로 옮긴다.
스프라이트는 모두 '왼쪽을 보는' 방향으로 그린다 (게임에서 오른쪽을 볼 때 좌우반전).

  털뭉냥이 HP50 / 그린냥이 HP20 / 날아냥이 HP25
  모루냥이 HP75 / 파랑냥이 HP20 / 똥냥이 HP30
"""

from PIL import Image, ImageDraw
import math
import os
import random

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app/src/main/res/drawable")
os.makedirs(OUT, exist_ok=True)

SIZE = 256
OUTLINE = (40, 32, 28)
WHITE = (255, 255, 255)
PUPIL = (30, 30, 30)
LW = 5


def new_canvas():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def body_oval(d, cx, cy, rx, ry, fill, seed=1):
    """통통한 타원 몸통: 매끈한 잉크 외곽선 + 색연필 덧칠"""
    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill)
    crayon(d, cx, cy, rx, ry, fill, seed)
    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], outline=OUTLINE, width=LW)


def crayon(d, cx, cy, rx, ry, base, seed, strokes=30):
    """색연필로 덧칠한 듯한 짧은 사선 (몸통 안쪽에만)"""
    rnd = random.Random(seed + 77)
    darker = tuple(max(0, c - 26) for c in base[:3])
    lighter = tuple(min(255, c + 22) for c in base[:3])
    for i in range(strokes):
        a = rnd.uniform(0, 2 * math.pi)
        r = rnd.uniform(0, 0.78)
        x = cx + math.cos(a) * rx * r
        y = cy + math.sin(a) * ry * r
        ln = rnd.uniform(9, 24)
        col = darker if i % 3 else lighter
        d.line([(x, y), (x - ln * 0.45, y + ln)], fill=col + (150,), width=rnd.choice([3, 4, 5]))


def legs(d, cx, cy, rx, ry, fill, count=4):
    """짧고 통통한 다리 — 몸통 아래로 살짝 삐져나온다"""
    foot_y = cy + ry
    for i in range(count):
        lx = cx - rx * 0.62 + (2 * rx * 0.62) * i / (count - 1)
        d.line([(lx, foot_y - 6), (lx, foot_y + 22)], fill=OUTLINE, width=LW)


def tail(d, cx, cy, fill, curl=1.0, bushy=False):
    """원본 시트의 꼬리: 몸 뒤에서 위로 짧고 뭉툭하게 솟은 부채꼴.
    bushy=True(상대편)면 같은 모양을 더 큼직하고 털이 풍성하게 그린다."""
    scale = 1.45 if bushy else 1.0
    base_x = cx + 6
    base_y = cy
    # 부채꼴을 이루는 털 다발 (짧은 물방울 모양을 각도별로 겹친다)
    for k in range(7):
        a = math.radians(-118 + k * 15)
        ln = (34 + (3 - abs(k - 3)) * 9) * scale
        ex = base_x + math.cos(a) * ln
        ey = base_y + math.sin(a) * ln
        w = int((13 + (3 - abs(k - 3)) * 3) * scale)
        d.line([(base_x, base_y), (ex, ey)], fill=OUTLINE, width=w + 5)
    for k in range(7):
        a = math.radians(-118 + k * 15)
        ln = (34 + (3 - abs(k - 3)) * 9) * scale
        ex = base_x + math.cos(a) * ln
        ey = base_y + math.sin(a) * ln
        w = int((13 + (3 - abs(k - 3)) * 3) * scale)
        d.line([(base_x, base_y), (ex, ey)], fill=fill, width=w)


def fur_fringe(d, cx, cy, rx, ry, fill=None):
    """털이 많은 몸: 선을 긋지 않고 한 겹 더 두툼하게 감싸 표현한다"""
    pad = 13
    d.ellipse([cx - rx - pad, cy - ry - pad, cx + rx + pad, cy + ry + pad],
              fill=fill or (255, 255, 255), outline=OUTLINE, width=LW + 3)


def ears(d, cx, cy, rx, ry, fill):
    """머리 위 작은 삼각 귀 두 개 (왼쪽을 보는 얼굴 기준)"""
    for dx in (-0.55, -0.12):
        ex = cx + rx * dx
        ey = cy - ry * 0.86
        d.polygon([(ex - 16, ey + 6), (ex - 2, ey - 30), (ex + 16, ey + 4)],
                  fill=fill, outline=OUTLINE)


def face(d, cx, cy, scale=1.0, angry=False, tongue=False, eye_color=PUPIL):
    """시트 얼굴: 코 자리에 둥근 흰 볼(입주변) 두 개, 눈은 그 위의 작은 점 (왼쪽 방향)"""
    # 둥근 흰 볼 두 개 = 코·입 주변
    r = 26 * scale
    muzzle_cy = cy + 16 * scale
    for dx in (-40, 2):
        mx = cx + dx * scale
        d.ellipse([mx - r, muzzle_cy - r, mx + r, muzzle_cy + r],
                  fill=WHITE, outline=OUTLINE, width=LW - 1)

    # 코와 입 (두 볼이 만나는 자리)
    nx, ny = cx - 19 * scale, muzzle_cy - 6 * scale
    d.ellipse([nx - 6 * scale, ny - 4 * scale, nx + 6 * scale, ny + 5 * scale], fill=OUTLINE)
    d.line([(nx, ny + 5 * scale), (nx, ny + 13 * scale)], fill=OUTLINE, width=3)
    d.arc([nx - 18 * scale, ny + 2 * scale, nx, ny + 20 * scale], 300, 60, fill=OUTLINE, width=3)
    d.arc([nx, ny + 2 * scale, nx + 18 * scale, ny + 20 * scale], 120, 240, fill=OUTLINE, width=3)
    if tongue:
        d.ellipse([nx - 7 * scale, ny + 16 * scale, nx + 7 * scale, ny + 31 * scale],
                  fill=(230, 120, 140), outline=OUTLINE, width=2)

    # 눈: 작은 점 두 개
    er = 5 * scale
    eye_cy = cy - 22 * scale
    for dx in (-40, 2):
        ex = cx + dx * scale
        d.ellipse([ex - er, eye_cy - er, ex + er, eye_cy + er], fill=eye_color)

    if angry:   # 사나운 눈썹
        d.line([(cx - 56 * scale, eye_cy - 16 * scale), (cx - 28 * scale, eye_cy - 6 * scale)],
               fill=OUTLINE, width=LW)
        d.line([(cx + 18 * scale, eye_cy - 16 * scale), (cx - 10 * scale, eye_cy - 6 * scale)],
               fill=OUTLINE, width=LW)

    # 수염
    for sy in (-6, 8):
        d.line([(cx - 74 * scale, muzzle_cy + sy), (cx - 106 * scale, muzzle_cy + sy - 6)],
               fill=OUTLINE, width=3)


def save(img, name):
    img.save(f"{OUT}/{name}.png")
    print(f"  {name}.png")


# ---------------------------------------------------------------- 털뭉냥이
def fluffy_cat():
    """온몸이 삐죽삐죽한 털 뭉치. HP 50"""
    img, d = new_canvas()
    cx, cy, rx, ry = 128, 132, 78, 66
    fur = (232, 196, 90)

    # 원본 시트처럼 짧고 촘촘한 털이 몸 둘레 전체에 돋아 있다
    for i in range(72):
        a = i * 2 * math.pi / 72
        ln = 16 + 8 * ((i * 7) % 5) / 4.0
        d.line([(cx + math.cos(a) * rx * 0.99, cy + math.sin(a) * ry * 0.99),
                (cx + math.cos(a) * (rx + ln), cy + math.sin(a) * (ry + ln))],
               fill=OUTLINE, width=5)
    for i in range(72):
        a = i * 2 * math.pi / 72
        ln = 12 + 6 * ((i * 7) % 5) / 4.0
        d.line([(cx + math.cos(a) * rx * 0.96, cy + math.sin(a) * ry * 0.96),
                (cx + math.cos(a) * (rx + ln), cy + math.sin(a) * (ry + ln))],
               fill=fur, width=3)

    legs(d, cx, cy, rx, ry, fur)
    ears(d, cx, cy, rx, ry, fur)
    body_oval(d, cx, cy, rx, ry, fur, seed=3)
    # 호랑이 줄무늬
    for dx in (-30, 0, 30, 58):
        d.line([(cx + dx, cy - ry * 0.72), (cx + dx - 6, cy + ry * 0.55)],
               fill=(186, 138, 54), width=8)
    face(d, cx, cy)
    save(img, "sprite_fluffy_cat")


# ---------------------------------------------------------------- 그린냥이
def green_cat(evil=False, name="sprite_green_cat"):
    """슬라임을 뱉는 초록 냥이. HP 20 (evil=나쁜 편: 수북한 꼬리 + 부슬한 털)"""
    img, d = new_canvas()
    cx, cy, rx, ry = 132, 140, 74, 58
    green = (118, 190, 96)

    tail(d, cx + 40, cy - 10, green, bushy=evil)
    legs(d, cx, cy, rx, ry, green)
    ears(d, cx, cy, rx, ry, green)
    body_oval(d, cx, cy, rx, ry, green, seed=5)
    for dx in (-20, 6, 32):
        d.line([(cx + dx, cy - ry * 0.7), (cx + dx - 5, cy + ry * 0.6)], fill=(82, 150, 66), width=7)
    face(d, cx, cy, tongue=True)

    # 원본처럼 입에서 길쭉한 애벌레 모양 슬라임이 뻗어 나온다
    slime = (128, 214, 104)
    pts = [(58 - i * 9, 176 + math.sin(i * 0.9) * 7) for i in range(7)]
    for px, py in pts:
        d.ellipse([px - 13, py - 12, px + 13, py + 12], fill=OUTLINE)
    for px, py in pts:
        d.ellipse([px - 10, py - 9, px + 10, py + 9], fill=slime)
    d.ellipse([pts[-1][0] - 13, pts[-1][1] - 13, pts[-1][0] + 13, pts[-1][1] + 13],
              fill=slime, outline=OUTLINE, width=3)
    d.ellipse([pts[-1][0] - 6, pts[-1][1] - 5, pts[-1][0] - 1, pts[-1][1]], fill=OUTLINE)
    save(img, name)


# ---------------------------------------------------------------- 날아냥이
def flying_cat():
    """표창을 던지며 떠다니는 냥이. HP 25"""
    img, d = new_canvas()
    cx, cy, rx, ry = 130, 126, 72, 56
    body = (240, 216, 116)


    tail(d, cx + 34, cy, body, curl=0.7, bushy=True)
    legs(d, cx, cy, rx, ry, body)
    ears(d, cx, cy, rx, ry, body)
    body_oval(d, cx, cy, rx, ry, body, seed=7)
    for dx in (-24, 4, 32):
        d.line([(cx + dx, cy - ry * 0.7), (cx + dx - 5, cy + ry * 0.6)], fill=(196, 158, 60), width=7)
    face(d, cx, cy, angry=True)

    # 원본처럼 긴 창(다트)이 몸을 가로질러 앞으로 겨눠져 있다
    spear_y = cy + 4
    d.line([(cx + 96, spear_y + 12), (18, spear_y - 6)], fill=OUTLINE, width=9)
    d.line([(cx + 96, spear_y + 12), (18, spear_y - 6)], fill=(198, 186, 160), width=5)
    d.polygon([(6, spear_y - 8), (34, spear_y - 16), (30, spear_y + 4)],
              fill=(120, 124, 130), outline=OUTLINE)
    save(img, "sprite_flying_cat")


# ---------------------------------------------------------------- 모루냥이
def anvil_cat():
    """모루를 뒤집어쓴 단단한 냥이. HP 75"""
    img, d = new_canvas()
    blue = (72, 96, 208)
    dark = (52, 70, 168)

    # 머리 위 모루 (뾰족한 뿔 + 각진 블록)
    d.polygon([(28, 96), (96, 74), (96, 122), (28, 110)], fill=blue, outline=OUTLINE, width=LW)
    d.rectangle([90, 70, 210, 124], fill=blue, outline=OUTLINE, width=LW)
    # 몸통(각진 블록)
    d.rectangle([56, 150, 208, 208], fill=blue, outline=OUTLINE, width=LW)
    d.rectangle([56, 150, 208, 168], fill=dark)
    for lx in (80, 120, 160, 196):
        d.line([(lx, 206), (lx, 228)], fill=OUTLINE, width=LW)
    # 귀
    for ex in (108, 168):
        d.polygon([(ex - 16, 76), (ex, 44), (ex + 16, 74)], fill=blue, outline=OUTLINE)
    face(d, 150, 132, scale=0.85, angry=True)
    save(img, "sprite_anvil_cat")


# ---------------------------------------------------------------- 파랑냥이
def blue_cat(evil=False, name="sprite_blue_cat"):
    """체크무늬 파랑 냥이. HP 20 (evil=나쁜 편: 수북한 꼬리 + 부슬한 털)"""
    img, d = new_canvas()
    cx, cy, rx, ry = 130, 138, 74, 60
    blue = (110, 140, 226)

    tail(d, cx + 38, cy - 8, blue, bushy=evil)
    legs(d, cx, cy, rx, ry, blue)
    ears(d, cx, cy, rx, ry, blue)
    body_oval(d, cx, cy, rx, ry, blue, seed=9)
    # 체크무늬: 몸통 안쪽에만 사각 패턴
    for row in range(4):
        for col in range(5):
            if (row + col) % 2:
                continue
            x0 = cx - rx * 0.8 + col * rx * 0.34
            y0 = cy - ry * 0.8 + row * ry * 0.42
            if (x0 - cx) ** 2 / (rx * 0.92) ** 2 + (y0 - cy) ** 2 / (ry * 0.9) ** 2 > 0.72:
                continue
            d.rectangle([x0, y0, x0 + rx * 0.3, y0 + ry * 0.36], fill=(78, 106, 196))
    face(d, cx, cy, tongue=True)

    # 원본처럼 입에서 파란 물줄기를 내뿜는다
    water = (96, 168, 236)
    for i in range(4):
        wx = 62 - i * 16
        wy = 172 + math.sin(i * 1.1) * 8
        r = 12 - i
        d.ellipse([wx - r, wy - r, wx + r, wy + r], fill=OUTLINE)
    for i in range(4):
        wx = 62 - i * 16
        wy = 172 + math.sin(i * 1.1) * 8
        r = 9 - i
        d.ellipse([wx - r, wy - r, wx + r, wy + r], fill=water)
    save(img, name)


# ---------------------------------------------------------------- 똥냥이
def poop_cat():
    """똥 모양 냥이. 냄새로 공격한다. HP 30"""
    img, d = new_canvas()
    poop = (196, 146, 74)
    cx = 128

    # 아래에서 위로 좁아지는 똥 3단
    for (cy, rx, ry) in ((186, 84, 40), (140, 62, 32), (102, 40, 24)):
        d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=poop, outline=OUTLINE, width=LW)
    # 꼭대기 꼬빼기
    d.polygon([(cx - 14, 84), (cx, 56), (cx + 14, 84)], fill=poop, outline=OUTLINE)
    # 냄새 표시: 위로 올라가는 물결 세 줄기
    for sx, top in ((58, 48), (98, 30), (188, 52)):
        pts = [(sx + math.sin(i * 0.9) * 7, top + i * 7) for i in range(6)]
        d.line(pts, fill=OUTLINE, width=4, joint="curve")
    for lx in (84, 128, 172):
        d.line([(lx, 220), (lx, 238)], fill=OUTLINE, width=LW)
    tail(d, cx + 58, 186, poop, curl=0.6, bushy=True)
    face(d, cx + 6, 182, scale=0.9)
    save(img, "sprite_poop_cat")


# ---------------------------------------------------------------- 아군 일반 냥이
def ally_normal_cat():
    """인간 편에 선 평범한 노란 냥이 (꼬리가 얇아 나쁜 냥이와 구분된다)"""
    img, d = new_canvas()
    cx, cy, rx, ry = 130, 138, 74, 60
    fur = (238, 205, 96)

    tail(d, cx + 38, cy - 8, fur)
    legs(d, cx, cy, rx, ry, fur)
    ears(d, cx, cy, rx, ry, fur)
    body_oval(d, cx, cy, rx, ry, fur, seed=13)
    for dx in (-18, 8, 34):
        d.line([(cx + dx, cy - ry * 0.7), (cx + dx - 5, cy + ry * 0.6)], fill=(208, 154, 62), width=7)
    face(d, cx, cy, tongue=True)
    save(img, "sprite_ally_cat")


if __name__ == "__main__":
    print("적 냥이 스프라이트 생성 (cats/ 디자인 참고):")
    fluffy_cat()
    green_cat()                                        # 아군 그린냥이
    green_cat(evil=True, name="sprite_green_cat_evil")  # 나쁜 그린냥이
    flying_cat()
    anvil_cat()
    blue_cat()                                         # 아군 파랑냥이
    blue_cat(evil=True, name="sprite_blue_cat_evil")    # 나쁜 파랑냥이
    poop_cat()
    ally_normal_cat()
    print("완료")
