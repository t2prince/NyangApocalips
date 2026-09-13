"""
냥이/레이저냥이 동작별 애니메이션 시트(4x2=8칸)를 그린다.

시트 규칙 (CAT_ART_SPEC.md 5-2절):
  - 1번 칸(좌상단) = 모든 시트 공통 '기준 자세' (냥이/레이저냥이 각각 동일)
  - 2~8번 칸 = 그 동작의 7프레임
  - 모든 프레임에서 몸 크기와 바닥선을 동일하게 유지한다

프레임 흔들림 방지 규칙 (2차 리뷰 반영, 반드시 지킬 것):
  1. 몸통 타원의 가로 반지름(rx)과 중심 x(cx)는 모든 프레임에서 리터럴 상수 — 절대 바꾸지 않는다.
     (attack의 '돌진'도 몸통을 옮기지 않고 다리 자세만으로 표현한다)
  2. 세로 squish는 ±3% 이내로만 (CLAMP 함수로 강제).
  3. 프레임마다 바뀌어도 되는 것은 다리 각도/길이, 꼬리 각도(±10°), 표정, squish(±3%)뿐.
  4. 다리는 항상 4개, 길이는 기준의 55~110% 범위 안에서만 (사라지듯 짧아지지 않는다).
  5. 꼬리는 머리 쪽(오른쪽)으로 넘어가지 않게 각도 범위를 제한한다.

얼굴 구조는 scripts/draw_cats.py 의 draw_face()를 그대로 재사용한다.

출력: Plan/cat_frames3/<normal|laser>_<idle|walk|jump|attack>.png

사용법: python scripts/draw_cat_anims.py [시트이름 ...]
검증: python scripts/draw_cat_anims.py --verify [시트이름 ...]
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from draw_cats import (  # noqa: E402
    W, OUTLINE, WHITE, LW,
    wobbly_ellipse, wobbly_line, crayon_hatch, tabby_stripes, draw_face, tail, ears,
)
from PIL import Image, ImageDraw  # noqa: E402

OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                        "Plan/cat_frames3")
os.makedirs(OUT_DIR, exist_ok=True)

COLS, ROWS = 4, 2
CELL_W, CELL_H = 600, 460
SHEET_W, SHEET_H = CELL_W * COLS, CELL_H * ROWS

# 셀 안 기준 좌표 — 모든 프레임에서 절대 바뀌지 않는 상수. W:H ≈ 1.55:1 (원본 실측)
BASE_CX, BASE_RX, BASE_RY = CELL_W * 0.50, 190, 123
GROUND = 340  # 배 밑선(다리가 붙는 y) 고정값 — 모든 프레임이 이 선에 다리를 댄다

FUR = (243, 190, 66)
STRIPE = (168, 112, 40)
GOGGLE = (86, 196, 90)

SQUISH_MAX = 0.03  # 세로 squish 허용치 ±3%


def clamp_squish(s):
    return max(1 - SQUISH_MAX, min(1 + SQUISH_MAX, s))


def cell_origin(idx):
    r, c = divmod(idx, COLS)
    return c * CELL_W, r * CELL_H


def draw_legs_pose(d, cx, ground_y, rx, ry, foot_offsets=None, len_scales=None, seed=0):
    """다리는 항상 4개. foot_offsets/len_scales로 발 위치·길이만 프레임마다 바꾼다
    (몸통 쪽 부착 x는 고정, 길이는 기준의 55~110% 안에서만 움직인다)."""
    n = 4
    base_len = ry * 0.5
    if foot_offsets is None:
        foot_offsets = [0.0] * n
    if len_scales is None:
        len_scales = [1.0] * n
    for i in range(n):
        t = i / (n - 1) - 0.5
        lx = cx + t * rx * 2 * 0.6
        scale = max(0.55, min(1.10, len_scales[i]))
        ln = base_len * scale
        outset = foot_offsets[i]
        # 배경 제거 파이프라인의 '가장 큰 덩어리만 남기기'에서 다리가 몸통과 끊기지 않도록
        # 몸통 안쪽으로 충분히 겹쳐 넣는다.
        inset = max(24, ry * 0.18)
        wobbly_line(d, [(lx, ground_y - inset), (lx + outset, ground_y + ln)],
                    OUTLINE, LW - 1, seed=seed + i, jitter=1.5)


def draw_cat_pose(d, action, i, laser=False):
    """칸 전용 캔버스(0,0 기준)에 냥이 한 마리를 그린다. i=0은 모든 시트 공통 기준 자세.
    칸 밖으로 삐져나온 부분(꼬리, 레이저 등)이 옆 칸으로 번지지 않도록
    호출하는 쪽에서 이 캔버스를 칸 크기로 만들고 잘라 붙인다.

    cx, rx는 절대 바뀌지 않는다 — 이 함수 안 어디서도 몸통 중심 x나 가로 반지름을 건드리지 않는다."""
    cx = BASE_CX          # 상수 — 모든 프레임 동일
    rx = BASE_RX          # 상수 — 모든 프레임 동일
    ry = BASE_RY
    ground_y = GROUND

    squish = 1.0
    tail_sway = 0.0        # -1~1, 각도 ±10°로 매핑
    expression, tongue = "normal", False
    laser_beam = 0.0
    foot_offsets = [0.0, 0.0, 0.0, 0.0]
    len_scales = [1.0, 1.0, 1.0, 1.0]

    if i == 0:
        pass  # 기준 자세: 모든 값 기본값

    elif action == "idle":
        p = (i - 1) / 6.0
        squish = clamp_squish(1.0 + SQUISH_MAX * math.sin(p * 2 * math.pi))
        tail_sway = math.sin(p * 2 * math.pi + 1.0)
        if i in (3, 4):
            expression = "sleepy"
        if i in (5, 6):
            tongue = True

    elif action == "walk":
        p = (i - 1) / 6.0
        phase = p * 2 * math.pi * 1.4
        squish = clamp_squish(1.0 + SQUISH_MAX * math.sin(phase * 2))
        tail_sway = math.sin(phase)
        # 다리 4개가 앞뒤로 교차 스트라이드 — 발 위치(outset)만 흔들고 몸통 부착점은 고정
        for k in range(4):
            foot_offsets[k] = math.sin(phase + k * math.pi / 2) * 34
            len_scales[k] = 1.0 + math.sin(phase + k * math.pi / 2 + math.pi / 2) * 0.16

    elif action == "jump":
        p = (i - 1) / 6.0
        if p < 0.28:                       # 웅크림
            t = p / 0.28
            crouch = 1.0 - t
            for k in range(4):
                len_scales[k] = 1.0 - 0.35 * crouch
                foot_offsets[k] = 0
            squish = clamp_squish(1.0 - SQUISH_MAX * crouch)
        elif p < 0.75:                     # 공중 — 다리를 몸 쪽으로 당겨 접는다(각도만, 항상 4개 보인다)
            for k in range(4):
                len_scales[k] = 0.60
                foot_offsets[k] = (-1 if k < 2 else 1) * 14
            squish = clamp_squish(1.0 + SQUISH_MAX)
        else:                              # 착지 — 다시 웅크림
            t = (p - 0.75) / 0.25
            crouch = t
            for k in range(4):
                len_scales[k] = 1.0 - 0.35 * crouch
            squish = clamp_squish(1.0 - SQUISH_MAX * crouch)
        tail_sway = 0.5

    elif action == "attack":
        p = (i - 1) / 6.0
        # 몸통은 절대 옮기지 않는다 — 앞다리를 앞으로, 뒷다리를 뒤로 뻗어서 '돌진'을 표현한다.
        if p < 0.3:                        # 웅크림(뒷다리 굽힘)
            t = p / 0.3
            for k in range(4):
                len_scales[k] = 1.0 - 0.30 * t
            foot_offsets = [-18 * t, -18 * t, -6 * t, -6 * t]
            expression = "angry"
            squish = clamp_squish(1.0 - SQUISH_MAX * t)
        elif p < 0.65:                     # 돌진 — 앞다리 쭉, 뒷다리 반대로
            t = (p - 0.3) / 0.35
            len_scales = [1.10, 1.10, 0.85, 0.85]
            foot_offsets = [34 * t, 34 * t, -30 * t, -30 * t]
            expression = "angry"
            laser_beam = t
            squish = clamp_squish(1.0 + SQUISH_MAX * t)
        else:                               # 복귀
            t = (p - 0.65) / 0.35
            len_scales = [1.10 - 0.10 * t, 1.10 - 0.10 * t, 0.85 + 0.15 * t, 0.85 + 0.15 * t]
            foot_offsets = [34 * (1 - t), 34 * (1 - t), -30 * (1 - t), -30 * (1 - t)]
            expression = "angry"
            laser_beam = 1.0 - t * 0.3
            squish = clamp_squish(1.0 + SQUISH_MAX * (1 - t))
        tail_sway = -0.6

    ry_eff = ry * squish
    cy = ground_y - ry_eff

    # 꼬리: sway=±1.0 → draw_cats.tail()에서 ±10°로 매핑되고, a1이 머리 쪽에서 충분히 떨어져 있어
    # 어떤 프레임에서도 머리 쪽으로 넘어가지 않는다.
    tail_sway = max(-1.0, min(1.0, tail_sway))
    tail(d, cx - rx * 0.92, cy - ry_eff * 0.05, FUR, rx, seed=i + 1, sway=tail_sway)
    draw_legs_pose(d, cx, ground_y, rx, ry_eff, foot_offsets=foot_offsets,
                    len_scales=len_scales, seed=i + 5)

    wobbly_ellipse(d, cx, cy, rx, ry_eff, fill=FUR, outline=None, seed=2)
    crayon_hatch(d, cx, cy, rx, ry_eff, FUR, seed=2, strokes=40)
    tabby_stripes(d, cx, cy, rx, ry_eff, STRIPE, seed=3, n=9)
    wobbly_ellipse(d, cx, cy, rx, ry_eff, fill=None, outline=OUTLINE, width=LW, seed=2)
    ears(d, cx, cy, rx, ry_eff, FUR)

    f = draw_face(d, cx, cy, rx, ry_eff, expression=expression, tongue=tongue, seed=10)

    if laser:
        eye_cy, eye_positions, r = f["eye_cy"], f["eye_positions"], f["r"]
        gr = r * 0.42
        d.line([(cx - rx * 0.05, eye_cy - gr * 0.3), (cx + rx * 0.95, eye_cy - gr * 0.1)],
               fill=OUTLINE, width=LW)
        for ex in eye_positions:
            wobbly_ellipse(d, ex, eye_cy, gr, gr, fill=GOGGLE, outline=OUTLINE, width=LW - 2, seed=int(ex))
            d.ellipse([ex - gr * 0.3, eye_cy - gr * 0.3, ex + gr * 0.3, eye_cy + gr * 0.3],
                      fill=(200, 255, 200))
        if laser_beam > 0:
            by = eye_cy
            bx0 = f["cx2"] + r * 1.1
            max_len = max(10, CELL_W - bx0 - 10)  # 옆 칸으로 번지지 않게 셀 안에서 자른다
            bx1 = bx0 + min(620 * laser_beam, max_len)
            d.line([(bx0, by), (bx1, by)], fill=(60, 170, 70), width=16)
            d.line([(bx0, by), (bx1, by)], fill=(150, 230, 140), width=6)


def build_sheet(name, action, laser):
    sheet = Image.new("RGB", (SHEET_W, SHEET_H), WHITE)
    for i in range(8):
        ox, oy = cell_origin(i)
        # 칸 전용 캔버스에 그린 뒤 붙인다 — 꼬리/레이저가 옆 칸으로 번지지 않는다
        cell = Image.new("RGB", (CELL_W, CELL_H), WHITE)
        cd = ImageDraw.Draw(cell)
        draw_cat_pose(cd, action, i, laser=laser)
        sheet.paste(cell, (ox, oy))
    path = os.path.join(OUT_DIR, f"{name}.png")
    sheet.save(path)
    print(f"{path}  {sheet.width}x{sheet.height}")


def verify_sheet(name):
    """각 칸의 몸통 외곽선(OUTLINE 색) bounding box 폭을 실제로 픽셀 스캔해서 출력한다.
    cy(=GROUND-ry_eff) 높이의 가로줄에서 검은 외곽선이 처음/마지막으로 나타나는 x를 잰다."""
    import numpy as np
    path = os.path.join(OUT_DIR, f"{name}.png")
    img = np.array(Image.open(path).convert("RGB"))
    widths = []
    print(f"-- {name} 몸통 bbox 폭 검증 (칸마다 cy 높이 가로줄 스캔) --")
    for i in range(8):
        ox, oy = cell_origin(i)
        cy = int(oy + GROUND - BASE_RY)  # 대략적인 몸통 중심 y (squish 영향 작음)
        row = img[cy, ox:ox + CELL_W]
        is_outline = np.all(np.abs(row.astype(int) - np.array(OUTLINE)) < 30, axis=1)
        xs = np.nonzero(is_outline)[0]
        if len(xs) == 0:
            print(f"  칸 {i}: 외곽선을 못 찾음 (y={cy})")
            continue
        width = int(xs.max() - xs.min())
        widths.append(width)
        print(f"  칸 {i}: 몸통 폭 {width}px  (x {xs.min()}~{xs.max()})")
    if widths:
        print(f"  최대-최소 편차: {max(widths) - min(widths)}px "
              f"({'OK' if max(widths) - min(widths) <= 2 else '2px 초과 — 재검토 필요'})")


SHEETS = {
    "normal_idle": ("idle", False),
    "normal_walk": ("walk", False),
    "normal_jump": ("jump", False),
    "normal_attack": ("attack", False),
    "laser_idle": ("idle", True),
    "laser_walk": ("walk", True),
    "laser_jump": ("jump", True),
    "laser_attack": ("attack", True),
}

if __name__ == "__main__":
    args = sys.argv[1:]
    verify = "--verify" in args
    names = [a for a in args if a != "--verify"] or list(SHEETS.keys())
    for name in names:
        action, laser = SHEETS[name]
        if not verify:
            build_sheet(name, action, laser)
        verify_sheet(name)
