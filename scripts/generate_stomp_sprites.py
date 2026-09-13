#!/usr/bin/env python3
"""필살기 '내려찍기(스톰프)' 전용 스프라이트 생성.

게임 내 플레이어는 스틱맨(검은 선 + 흰 머리)으로 그려지므로 같은 톤을 유지한다.
 - sprite_player_stomp_drop.png : 공중에서 두 발을 모아 내려찍는 낙하 포즈
 - sprite_player_stomp_impact.png: 착지 직후 무릎 굽히고 착지한 임팩트 포즈
 - sprite_shockwave.png          : 착지 충격파 (좌우로 퍼지는 링 + 파편)
"""

from PIL import Image, ImageDraw
import math
import os

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app/src/main/res/drawable")
os.makedirs(OUT, exist_ok=True)

STICK = (30, 30, 30)
WHITE = (255, 255, 255)
GOLD = (255, 200, 50)
GOLD_SOFT = (255, 220, 120, 170)
SPARK = (255, 245, 200)


def draw_burst(d, cx, cy, r, s, spikes=10, color=SPARK):
    """별 모양 충격 스파크"""
    for i in range(spikes):
        a = math.radians(i * 360 / spikes)
        x1 = cx + math.cos(a) * r * 0.3
        y1 = cy + math.sin(a) * r * 0.3
        x2 = cx + math.cos(a) * r
        y2 = cy + math.sin(a) * r
        d.line([(x1, y1), (x2, y2)], fill=color, width=max(2, int(3 * s)))
    d.ellipse([cx - r * 0.32, cy - r * 0.32, cx + r * 0.32, cy + r * 0.32],
              fill=(255, 255, 235, 220))


def head(d, cx, cy, r, lw):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=STICK, width=lw)


def draw_stomp_drop(size=256, name="sprite_player_stomp_drop"):
    """낙하 포즈: 몸을 수직으로 세우고 두 발을 아래로 모아 내려찍는다.
    발끝이 이미지 하단(=발바닥 기준선)에 오도록 배치한다."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size / 256.0
    lw = max(3, int(6 * s))
    cx = int(size * 0.5)

    # 하강 속도선 (위쪽으로 흩날리는 잔상)
    for i, off in enumerate((-42, -20, 18, 40)):
        x = cx + int(off * s)
        top = int((10 + i % 2 * 14) * s)
        d.line([(x, top), (x, int((70 + i % 2 * 10) * s))],
               fill=(255, 220, 120, 150), width=max(2, int(4 * s)))

    head_y = int(58 * s)
    head_r = int(19 * s)
    neck_y = head_y + head_r
    shoulder_y = neck_y + int(8 * s)
    hip_y = shoulder_y + int(56 * s)
    foot_y = int(228 * s)

    # 낙하 에너지 글로우: 엉덩이에서 발끝까지 굵게
    d.line([(cx, hip_y), (cx, foot_y)], fill=GOLD_SOFT, width=max(6, int(20 * s)))

    # 척추 (수직)
    d.line([(cx, neck_y), (cx, hip_y)], fill=STICK, width=lw)

    # 팔: 균형 잡으려 위로 벌린다
    d.line([(cx, shoulder_y), (cx - int(46 * s), shoulder_y - int(30 * s))], fill=STICK, width=lw)
    d.line([(cx, shoulder_y), (cx + int(46 * s), shoulder_y - int(26 * s))], fill=STICK, width=lw)

    # 다리: 두 발을 모아 아래로 곧게 (내려찍기)
    d.line([(cx, hip_y), (cx - int(9 * s), foot_y)], fill=STICK, width=max(4, int(8 * s)))
    d.line([(cx, hip_y), (cx + int(9 * s), foot_y)], fill=STICK, width=max(4, int(8 * s)))

    # 발바닥 강조
    d.ellipse([cx - int(20 * s), foot_y - int(8 * s), cx + int(20 * s), foot_y + int(8 * s)],
              fill=STICK)

    head(d, cx, head_y, head_r, lw)

    # 발끝 스파크
    draw_burst(d, cx, foot_y + int(6 * s), int(22 * s), s, spikes=8, color=GOLD)

    img.save(f"{OUT}/{name}.png")
    print(f"  {name}.png ({size}x{size})")


def draw_stomp_impact(size=256, name="sprite_player_stomp_impact"):
    """임팩트 포즈: 무릎을 크게 굽히고 착지, 두 손은 땅을 짚는다."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size / 256.0
    lw = max(3, int(6 * s))
    cx = int(size * 0.5)

    head_y = int(112 * s)
    head_r = int(19 * s)
    neck_y = head_y + head_r
    shoulder_y = neck_y + int(6 * s)
    hip_y = shoulder_y + int(40 * s)
    foot_y = int(228 * s)

    # 척추: 살짝 앞으로 숙임
    d.line([(cx + int(4 * s), neck_y), (cx - int(4 * s), hip_y)], fill=STICK, width=lw)

    # 팔: 땅을 짚는다
    d.line([(cx + int(3 * s), shoulder_y), (cx + int(34 * s), foot_y - int(14 * s))],
           fill=STICK, width=lw)
    d.line([(cx + int(3 * s), shoulder_y), (cx - int(36 * s), foot_y - int(16 * s))],
           fill=STICK, width=lw)

    # 다리: 무릎 굽혀 넓게 착지 (엉덩이→무릎→발)
    for side in (-1, 1):
        knee_x = cx + int(30 * s) * side
        knee_y = hip_y + int(24 * s)
        foot_x = cx + int(40 * s) * side
        d.line([(cx - int(4 * s), hip_y), (knee_x, knee_y)], fill=STICK, width=max(4, int(7 * s)))
        d.line([(knee_x, knee_y), (foot_x, foot_y)], fill=STICK, width=max(4, int(7 * s)))
        d.ellipse([foot_x - int(13 * s), foot_y - int(7 * s),
                   foot_x + int(13 * s), foot_y + int(7 * s)], fill=STICK)

    head(d, cx, head_y, head_r, lw)

    # 발밑에서 튀는 먼지/파편
    for dx, dy, r in ((-70, -14, 7), (-52, -34, 5), (66, -18, 7), (48, -40, 5), (0, -50, 4)):
        d.ellipse([cx + int((dx - r) * s), foot_y + int((dy - r) * s),
                   cx + int((dx + r) * s), foot_y + int((dy + r) * s)],
                  fill=(255, 235, 170, 190))

    img.save(f"{OUT}/{name}.png")
    print(f"  {name}.png ({size}x{size})")


def draw_shockwave(w=512, h=192, name="sprite_shockwave"):
    """착지 충격파: 지면을 따라 좌우로 퍼지는 납작한 링 + 솟구치는 파편."""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = w // 2, int(h * 0.78)
    s = w / 512.0

    # 납작한 링 3겹 (바깥이 옅다)
    rings = ((1.00, (255, 235, 150, 230), 10), (0.74, (255, 215, 90, 190), 8),
             (0.48, (255, 255, 220, 150), 6))
    for scale, color, width in rings:
        rx = int(w * 0.47 * scale)
        ry = int(h * 0.34 * scale)
        d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], outline=color, width=max(2, int(width * s)))

    # 좌우로 뻗는 충격 라인
    for side in (-1, 1):
        for i, (dx, dy, ln) in enumerate(((0.30, -0.16, 0.13), (0.40, -0.06, 0.16), (0.44, 0.05, 0.11))):
            x1 = cx + int(w * dx * side)
            y1 = cy + int(h * dy)
            x2 = x1 + int(w * ln * side)
            d.line([(x1, y1), (x2, y1)], fill=(255, 240, 190, 200), width=max(2, int(6 * s)))

    # 위로 솟구치는 파편
    for dx, top, r in ((-0.34, 0.22, 9), (-0.16, 0.10, 7), (0.14, 0.12, 8), (0.33, 0.24, 9),
                       (-0.05, 0.04, 6), (0.05, 0.06, 6)):
        x = cx + int(w * dx)
        y = int(h * top)
        rr = int(r * s)
        d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(255, 230, 160, 210))

    # 중심 섬광
    draw_burst(d, cx, cy, int(h * 0.30), s, spikes=12, color=(255, 250, 210))

    img.save(f"{OUT}/{name}.png")
    print(f"  {name}.png ({w}x{h})")


if __name__ == "__main__":
    print("내려찍기 필살기 스프라이트 생성:")
    draw_stomp_drop()
    draw_stomp_impact()
    draw_shockwave()
    print("완료")
