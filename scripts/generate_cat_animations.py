#!/usr/bin/env python3
"""cats/cat4.png · cat5.png 애니메이션 시트를 옮긴 냥이 프레임 스프라이트.

시트 구성 그대로 대기 / 이동 / 점프 / 공격 4종을 만든다.
  - 냥이(노란 줄무늬, 검은 눈동자)      : anim_cat_*.png
  - 레이저 냥이(초록 눈, 공격 시 광선)  : anim_laser_*.png

각 파일은 프레임을 가로로 이어 붙인 스트립이며, 게임에서 균등 분할해 사용한다.
스프라이트는 모두 '왼쪽을 보는' 방향 (게임에서 오른쪽을 볼 때 좌우반전).
"""

from PIL import Image, ImageDraw
import math
import os

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app/src/main/res/drawable")
os.makedirs(OUT, exist_ok=True)

FW = FH = 200                      # 프레임 크기
GROUND = 182                       # 발이 닿는 기준선
OUTLINE = (44, 36, 30)
WHITE = (255, 255, 255)
FUR = (238, 205, 96)               # 노란 줄무늬 냥이
FUR_STRIPE = (208, 154, 62)
PUPIL = (35, 33, 30)
LASER_EYE = (86, 196, 108)
LASER_BEAM = (92, 208, 108)
TONGUE = (232, 122, 140)
LW = 4


def draw_cat(d, *, body_cy, squash=0.0, leg_phase=0.0, legs_tucked=False,
             tail_lift=0.0, brow=False, eye_close=0.0, tongue=False,
             paw=0.0, paw_up=0.0, eye_color=None, lean=0.0, laser=False):
    """시트의 통통한 냥이 한 마리. 인자로 자세를 바꿔 프레임을 만든다.

    body_cy    몸통 중심 y (점프 시 위로)
    squash     착지/도약 눌림 (+면 납작)
    leg_phase  걷기 위상
    tail_lift  꼬리 들림(0~1)
    paw        앞발 내지르기(0~1, 공격)
    lean       진행 방향으로 몸 기울이기
    """
    cx = 104 + lean * 6
    rx = 58 * (1 + squash * 0.16)
    ry = 52 * (1 - squash * 0.22)
    cy = body_cy

    # 꼬리: 원본 시트처럼 몸 뒤에서 위로 짧고 뭉툭하게 솟은 부채꼴 (상대편은 큼직하게)
    base_x = cx + rx * 0.72
    base_y = cy - 4
    for k in range(6):
        a = math.radians(-116 + k * 16 - tail_lift * 12)
        ln = 30 + (2.5 - abs(k - 2.5)) * 8
        w = int(12 + (2.5 - abs(k - 2.5)) * 3)
        d.line([(base_x, base_y),
                (base_x + math.cos(a) * ln, base_y + math.sin(a) * ln)],
               fill=OUTLINE, width=w + 5)
    for k in range(6):
        a = math.radians(-116 + k * 16 - tail_lift * 12)
        ln = 30 + (2.5 - abs(k - 2.5)) * 8
        w = int(12 + (2.5 - abs(k - 2.5)) * 3)
        d.line([(base_x, base_y),
                (base_x + math.cos(a) * ln, base_y + math.sin(a) * ln)],
               fill=FUR, width=w)

    # 다리: 걷기 위상에 따라 앞뒤로 흔들린다
    foot_y = cy + ry + (10 if not legs_tucked else -6)
    for i in range(4):
        lx = cx - rx * 0.62 + i * (rx * 1.24 / 3)
        swing = math.sin(leg_phase + i * 1.6) * (11 if not legs_tucked else 3)
        d.line([(lx, cy + ry - 8), (lx + swing, foot_y + (14 if not legs_tucked else 4))],
               fill=OUTLINE, width=LW)

    # 귀
    for ex in (cx - rx * 0.5, cx - rx * 0.03):
        d.polygon([(ex - 13, cy - ry * 0.82), (ex - 1, cy - ry - 22), (ex + 13, cy - ry * 0.86)],
                  fill=FUR, outline=OUTLINE)

    # 몸통: 매끈한 타원 + 부슬한 털 + 색연필 덧칠
    n = 54
    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=FUR)
    for k in range(22):        # 색연필 덧칠
        a = k * 1.7
        r = 0.2 + 0.55 * ((k * 37) % 10) / 10.0
        px = cx + math.cos(a) * rx * r
        py = cy + math.sin(a) * ry * r
        d.line([(px, py), (px - 8, py + 16)], fill=FUR_STRIPE + (120,), width=4)
    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], outline=OUTLINE, width=LW)
    for k in range(4):
        sx = cx - rx * 0.35 + k * rx * 0.4
        h = ry * 0.62 * (1 - abs(k - 1.5) * 0.16)
        d.line([(sx, cy - h), (sx - 6, cy + h * 0.5)], fill=FUR_STRIPE, width=7)

    # 얼굴: 코 자리에 둥근 흰 볼 두 개 (입주변), 눈은 그 위 작은 점
    mcy = cy + 12
    mr = 22
    for dx in (-36, 4):
        mx = cx + dx
        d.ellipse([mx - mr, mcy - mr, mx + mr, mcy + mr], fill=WHITE, outline=OUTLINE, width=LW - 1)

    # 코·입
    nx, ny = cx - 16, mcy - 5
    d.ellipse([nx - 6, ny - 4, nx + 6, ny + 5], fill=OUTLINE)
    d.line([(nx, ny + 5), (nx, ny + 12)], fill=OUTLINE, width=3)
    d.arc([nx - 16, ny + 2, nx, ny + 18], 300, 60, fill=OUTLINE, width=3)
    d.arc([nx, ny + 2, nx + 16, ny + 18], 120, 240, fill=OUTLINE, width=3)
    if tongue:
        d.ellipse([nx - 7, ny + 14, nx + 7, ny + 29], fill=TONGUE, outline=OUTLINE, width=2)

    # 눈: 작은 점 (감을 땐 짧은 선)
    ecy = cy - 20
    for dx in (-36, 4):
        ex = cx + dx
        if eye_close > 0.6:
            d.line([(ex - 9, ecy), (ex + 9, ecy)], fill=OUTLINE, width=LW)
        else:
            er = 5
            d.ellipse([ex - er, ecy - er, ex + er, ecy + er], fill=eye_color or PUPIL)

    # 눈썹(공격 시 사납게)
    if brow:
        d.line([(cx - 52, ecy - 16), (cx - 26, ecy - 6)], fill=OUTLINE, width=LW)
        d.line([(cx + 20, ecy - 16), (cx - 6, ecy - 6)], fill=OUTLINE, width=LW)

    # 수염
    for sy in (-8, 2, 12):
        d.line([(cx - rx * 0.95, mcy + sy), (cx - rx * 1.5, mcy + sy - 6)], fill=OUTLINE, width=3)

    # 레이저 냥이: 얼굴 앞으로 초록 렌즈가 달린 짧은 원통이 붙어 있다
    if laser:
        gy = cy - 8
        gx = cx - rx * 0.62
        d.line([(gx + 26, gy), (gx - 26, gy)], fill=OUTLINE, width=22)
        d.line([(gx + 26, gy), (gx - 26, gy)], fill=(150, 154, 160), width=15)
        d.ellipse([gx - 34, gy - 11, gx - 18, gy + 11], fill=LASER_BEAM, outline=OUTLINE, width=3)

    # 앞발 치켜들기 (공격 예비동작)
    if paw_up > 0:
        px = cx - rx * 0.35 + 10 * paw_up
        py = cy - ry * 0.55 - 34 * paw_up
        d.line([(cx - rx * 0.5, cy + ry * 0.1), (px, py)], fill=OUTLINE, width=LW + 3)
        d.ellipse([px - 14, py - 14, px + 14, py + 14], fill=FUR, outline=OUTLINE, width=LW - 1)

    # 앞발 내지르기 (공격)
    if paw > 0:
        px = cx - rx * 0.9 - 40 * paw
        py = cy + ry * 0.25 - 8 * paw
        d.line([(cx - rx * 0.55, cy + ry * 0.3), (px, py)], fill=OUTLINE, width=LW + 3)
        d.ellipse([px - 14, py - 14, px + 14, py + 14], fill=FUR, outline=OUTLINE, width=LW - 1)


def strip(frames_fn, count, name):
    """프레임 그리기 함수를 count번 호출해 가로 스트립으로 저장"""
    img = Image.new("RGBA", (FW * count, FH), (0, 0, 0, 0))
    for i in range(count):
        frame = Image.new("RGBA", (FW, FH), (0, 0, 0, 0))
        frames_fn(ImageDraw.Draw(frame), i)
        img.paste(frame, (FW * i, 0), frame)
    img.save(f"{OUT}/{name}.png")
    print(f"  {name}.png ({count} frames)")


def build(prefix, eye_color=None, beam=False, laser=False):
    base_cy = GROUND - 54

    # 대기(4프레임): 크게 숨 쉬고, 꼬리를 살랑이다, 눈을 깜빡이고, 혀로 코를 핥는다
    def idle(d, i):
        breath = math.sin(i / 4.0 * 2 * math.pi)
        draw_cat(d, body_cy=base_cy - breath * 5, squash=-breath * 0.10,
                 tail_lift=0.2 + 0.55 * (0.5 + 0.5 * math.sin(i * 1.9)),
                 eye_close=0.9 if i == 2 else 0.0,
                 tongue=(i == 3), eye_color=eye_color, laser=laser)

    # 이동(4프레임): 크게 통통 튀며 꼬리를 흔들고 몸을 앞으로 기울인다
    def walk(d, i):
        ph = i / 4.0 * 2 * math.pi
        draw_cat(d, body_cy=base_cy - abs(math.sin(ph)) * 12, leg_phase=ph,
                 squash=0.12 * math.cos(ph),
                 tail_lift=0.35 + 0.4 * math.sin(ph + 1.0),
                 tongue=(i == 1), lean=1.0, eye_color=eye_color, laser=laser)

    # 점프(3프레임): 깊게 웅크림 → 쭉 뻗어 도약 → 다리 벌리고 착지 준비
    def jump(d, i):
        if i == 0:
            draw_cat(d, body_cy=base_cy + 14, squash=0.62, tail_lift=0.0,
                     eye_close=0.7, eye_color=eye_color, laser=laser)
        elif i == 1:
            draw_cat(d, body_cy=base_cy - 58, squash=-0.34, legs_tucked=True,
                     tail_lift=1.0, tongue=True, eye_color=eye_color, laser=laser)
        else:
            draw_cat(d, body_cy=base_cy - 16, squash=0.22, tail_lift=0.5,
                     leg_phase=1.4, eye_color=eye_color, laser=laser)

    # 공격(3프레임): 앞발 치켜들고 노려보기 → 후려치기 → 되돌아오기
    def attack(d, i):
        if i == 0:
            draw_cat(d, body_cy=base_cy - 6, squash=-0.12, brow=True, paw_up=1.0,
                     tail_lift=1.0, lean=-1.4, eye_color=eye_color, laser=laser)
        elif i == 1:
            draw_cat(d, body_cy=base_cy + 4, squash=0.24, brow=True, paw=1.3,
                     tail_lift=0.2, lean=1.6, tongue=True, eye_color=eye_color, laser=laser)
            if beam:
                by = GROUND - 78
                d.rectangle([0, by - 11, 74, by + 11], fill=(190, 245, 190, 180))
                d.rectangle([0, by - 6, 74, by + 6], fill=LASER_BEAM)
        else:
            draw_cat(d, body_cy=base_cy, squash=0.06, brow=True, paw=0.45,
                     tail_lift=0.6, lean=0.4, eye_color=eye_color, laser=laser)

    strip(idle, 4, f"anim_{prefix}_idle")
    strip(walk, 4, f"anim_{prefix}_walk")
    strip(jump, 3, f"anim_{prefix}_jump")
    strip(attack, 3, f"anim_{prefix}_attack")


if __name__ == "__main__":
    print("냥이 애니메이션 스프라이트 생성 (cat4·cat5 시트 기반):")
    build("cat")
    build("laser", eye_color=LASER_EYE, beam=True, laser=True)
    print("완료")
