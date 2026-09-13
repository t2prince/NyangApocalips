"""
`Cats/` 원본 손그림을 얼굴 구조까지 맞춰 다시 그리는 스크립트.

절대 규칙 (CAT_ART_SPEC.md 참고):
  1. 큰 겹친 원 2개 = 눈이 아니라 주둥이(볼). 안에 눈동자를 그리지 않는다.
  2. 눈은 주둥이 위 머리통에 찍힌 아주 작은 검은 점 2개.
  3. 코는 주둥이 두 원이 만나는 이음새 '위'에 얹힌 작고 새까만 삼각형.
  4. 머리와 몸은 하나의 뚱뚱한 달걀형 덩어리 (목 없음). 기본 방향은 오른쪽.

출력: Plan/cat_sprites4/<이름>.png (흰 배경, 짧은 변 1000px 이상)

사용법: python scripts/draw_cats.py [이름 ...]   (이름 생략 시 전체)
"""

import math
import os
import random
import sys

from PIL import Image, ImageDraw

OUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                        "Plan/cat_sprites4")
os.makedirs(OUT_DIR, exist_ok=True)

W, H = 1750, 1200
OUTLINE = (28, 22, 20)
WHITE = (255, 255, 255)
LW = 10


def new_canvas():
    img = Image.new("RGB", (W, H), WHITE)
    return img, ImageDraw.Draw(img)


# ------------------------------------------------------------- 손그림 떨림 선
def wobbly_ellipse(d, cx, cy, rx, ry, fill=None, outline=None, width=LW,
                    seed=0, n=52, jitter=0.007):
    """살짝 떨리는 손그림 느낌의 타원. fill/outline 둘 다 줄 수 있다."""
    rnd = random.Random(seed)
    pts = []
    for i in range(n):
        a = 2 * math.pi * i / n
        jr = 1 + rnd.uniform(-jitter, jitter)
        pts.append((cx + math.cos(a) * rx * jr, cy + math.sin(a) * ry * jr))
    if fill is not None:
        d.polygon(pts, fill=fill)
    if outline is not None:
        d.line(pts + [pts[0]], fill=outline, width=width, joint="curve")
        for (x, y) in pts:
            d.ellipse([x - width / 2, y - width / 2, x + width / 2, y + width / 2], fill=outline)


def wobbly_line(d, pts, fill, width, seed=0, jitter=3.0):
    rnd = random.Random(seed)
    jp = []
    for i, (x, y) in enumerate(pts):
        if 0 < i < len(pts) - 1:
            x += rnd.uniform(-jitter, jitter)
            y += rnd.uniform(-jitter, jitter)
        jp.append((x, y))
    d.line(jp, fill=fill, width=width, joint="curve")
    for (x, y) in (jp[0], jp[-1]):
        d.ellipse([x - width / 2, y - width / 2, x + width / 2, y + width / 2], fill=fill)


# ------------------------------------------------------------------ 몸통 색칠
def crayon_hatch(d, cx, cy, rx, ry, base, seed, strokes=80, mix=(0, 0, 0)):
    """색연필로 덧칠한 짧은 사선 스트로크. 외곽선까지 안 채워서 흰 종이가 비친다."""
    rnd = random.Random(seed + 11)
    darker = tuple(max(0, c - 34 + mix[i]) for i, c in enumerate(base[:3]))
    lighter = tuple(min(255, c + 26 + mix[i]) for i, c in enumerate(base[:3]))
    for i in range(strokes):
        a = rnd.uniform(0, 2 * math.pi)
        r = rnd.uniform(0.05, 0.82)
        x = cx + math.cos(a) * rx * r
        y = cy + math.sin(a) * ry * r
        ln = rnd.uniform(14, 34)
        ang = rnd.uniform(-0.5, 0.5) + math.pi / 2.4
        dx, dy = math.cos(ang) * ln, math.sin(ang) * ln
        col = darker if i % 3 else lighter
        d.line([(x - dx / 2, y - dy / 2), (x + dx / 2, y + dy / 2)], fill=col, width=rnd.choice([4, 5, 6]))


def tabby_stripes(d, cx, cy, rx, ry, color, seed, n=16):
    """가로로 짧은 갈색 줄무늬. 원본처럼 몸통 왼쪽 절반(등~허리)에 촘촘히 몰아서, 얼굴 영역은 비운다.
    타원 밖으로 삐져나와 몸과 떨어진 얼룩처럼 보이지 않도록, 중심점과 양 끝점이 모두
    타원 안쪽 여유 반경(0.62) 안에 들어오는 위치만 고른다."""
    rnd = random.Random(seed + 23)
    made = 0
    tries = 0
    while made < n and tries < n * 20:
        tries += 1
        fx = rnd.uniform(-0.86, -0.06)
        fy = rnd.uniform(-0.78, 0.80)
        if fx * fx + fy * fy > 0.62 * 0.62:
            continue
        x = cx + fx * rx
        y = cy + fy * ry
        ln = rnd.uniform(0.11, 0.22) * rx
        ang = math.radians(rnd.uniform(-14, 14))  # 거의 가로 방향
        dx, dy = math.cos(ang) * ln, math.sin(ang) * ln
        # 선의 양 끝점도 타원 안쪽인지 확인
        ex1, ey1 = (x - dx / 2 - cx) / rx, (y - dy / 2 - cy) / ry
        ex2, ey2 = (x + dx / 2 - cx) / rx, (y + dy / 2 - cy) / ry
        if ex1 * ex1 + ey1 * ey1 > 0.92 or ex2 * ex2 + ey2 * ey2 > 0.92:
            continue
        w = rnd.choice([9, 10, 12])
        wobbly_line(d, [(x - dx / 2, y - dy / 2), (x + dx / 2, y + dy / 2)], color, w, seed=int(x + y))
        made += 1


# --------------------------------------------------------------------- 다리
def legs(d, cx, cy, rx, ry, n=4, spread=0.62, length=None, splay=0.06):
    """몸 아래로 곧게 뻗은 다리. length 생략 시 몸 높이(ry)에 비례한다 (원본 실측 ≈0.15H)."""
    if length is None:
        length = (ry * 0.42, ry * 0.62)
    foot_y = cy + ry
    rnd = random.Random(int(cx + cy))
    for i in range(n):
        t = i / (n - 1) - 0.5
        lx = cx + t * rx * 2 * spread
        ln = rnd.uniform(*length)
        outset = t * splay * rx
        # 몸통 안쪽으로 충분히 겹쳐 넣어야 배경 제거 파이프라인의 '가장 큰 덩어리만 남기기'에서
        # 다리가 몸통과 픽셀 단위로 확실히 이어진 채로 살아남는다 (살짝만 겹치면 끊겨 보인다).
        inset = max(28, ry * 0.09)
        wobbly_line(d, [(lx, foot_y - inset), (lx + outset, foot_y + ln)], OUTLINE, LW - 1,
                    seed=i + int(cx))


# --------------------------------------------------------------------- 꼬리
def tail(d, base_x, base_y, fill, rx, seed=0, bushy=False, sway=0.0):
    """몸 왼쪽 끝에서 위로 솟은 크고 복슬복슬한 부채꼴 (원본 실측: 길이≈0.28W, 폭≈0.16W).
    한 덩어리로 뭉친 부채꼴을 그린 뒤, 끝단에 살짝 결(notch)만 넣어 갈라진 털 느낌을 낸다.
    sway: -1~1, 애니메이션에서 꼬리가 살랑거리는 각도 오프셋."""
    rnd = random.Random(seed)
    peak_len = rx * (0.62 if bushy else 0.56)
    n = 13
    off = sway * 8
    # 몸 왼쪽 뒤에서 수평 기준 30~45도로 비스듬히 위로 솟는다 (옆으로 퍼지거나 머리 쪽을 넘보지 않게
    # 평균 방향을 약 40도 위쪽으로 좁혀서 잡는다). sway가 커져도 정확히 옆(-180)이나 머리 쪽(-90 이상)으로
    # 넘어가지 않도록 최종 각도를 한 번 더 안전 범위로 clamp한다.
    a0 = max(-172 + off, -172)
    a1 = min(-100 + off, -88)
    outline_pts = [(base_x, base_y)]
    tip_pts = []
    for k in range(n):
        t = k / (n - 1)
        spread_frac = 1 - abs(t - 0.5) * 2  # 중앙이 가장 길다
        a = math.radians(a0 + t * (a1 - a0))
        ln = peak_len * (0.62 + 0.38 * spread_frac) * (1 + rnd.uniform(-0.03, 0.03))
        ex = base_x + math.cos(a) * ln
        ey = base_y + math.sin(a) * ln
        outline_pts.append((ex, ey))
        tip_pts.append((ex, ey))
    outline_pts.append((base_x, base_y))

    d.polygon(outline_pts, fill=fill)
    d.line(outline_pts, fill=OUTLINE, width=LW, joint="curve")
    for (x, y) in outline_pts:
        d.ellipse([x - LW / 2, y - LW / 2, x + LW / 2, y + LW / 2], fill=OUTLINE)

    # 끝단 갈래를 깊게 파서 뭉치가 아니라 갈라진 털 다발처럼 보이게 한다
    for k in range(1, n - 1, 2):
        ex, ey = tip_pts[k]
        a = math.radians(a0 + (k / (n - 1)) * (a1 - a0))
        notch_len = peak_len * 0.34
        nx = ex - math.cos(a) * notch_len
        ny = ey - math.sin(a) * notch_len
        d.line([(nx, ny), (ex, ey)], fill=OUTLINE, width=5)


# ----------------------------------------------------------------------- 귀
def ears(d, cx, cy, rx, ry, fill, spread=(0.20, 0.45), size=1.0):
    """몸통 윤곽선에서 곧바로 솟은 작고 얇은 빈 삼각형 (원본 실측 밑변≈0.08W).
    속은 몸 색 그대로, 테두리만 가늘게. 몸통 외곽선을 그린 '뒤에' 호출해야 이어 붙는다."""
    for f in spread:
        ex = cx + rx * f
        dxr = max(-0.995, min(0.995, f))
        boundary_y = cy - ry * math.sqrt(max(0, 1 - dxr * dxr))
        base_y = boundary_y + 14 * size  # 몸 안쪽으로 살짝 겹쳐서 이음매를 없앤다
        w = rx * 0.08 * size
        h = w * 2.3
        pts = [(ex - w, base_y), (ex + w * 0.12, base_y - h), (ex + w * 0.95, base_y - h * 0.10)]
        d.polygon(pts, fill=fill)
        # 테두리도 몸 크기에 비례해서 가늘게 — 작게 그릴 때(애니메이션) 고정 두께면 속 색이 거의 안 보인다
        ear_lw = max(2, round(rx * 0.013))
        d.line(pts + [pts[0]], fill=OUTLINE, width=ear_lw, joint="curve")


# ---------------------------------------------------------------------- 얼굴
def draw_face(d, cx, cy, rx, ry, scale=1.0, expression="normal", tongue=False,
              muzzle_fill=WHITE, eye_color=OUTLINE, whiskers=True, seed=0):
    """
    원본 실측 비율 (몸통 폭 W=2rx, 높이 H=2ry, 오른쪽 주둥이 원 지름 D 기준):
      - 주둥이 전체 폭 0.40W, 중심은 (0.72W, 0.72H) — 얼굴 오른쪽 아래에 붙는다
      - 왼쪽 원은 오른쪽 원의 0.85배 크기, 지름의 30%만큼 겹친다
      - 코 폭 0.31D (뚜렷한 검은 덩어리), 눈 지름 0.08D (아주 작은 점) — 코가 눈의 4배
      - 눈 위치 x=0.66W/0.80W, y=0.42H
    expression: normal / sleepy(눈 감음 ^) / angry(눈썹) / dead(X자 눈)
    """
    rx, ry = rx * scale, ry * scale
    Wd, Hd = 2 * rx, 2 * ry
    left, top = cx - rx, cy - ry

    r_right = 0.129 * Wd  # 오른쪽 주둥이 반지름 (전체폭 0.40W = 1.55*D_right 로 역산)
    r_left = r_right * 0.85
    gap = 1.25 * r_right  # 중심 간 거리 (지름의 30% 겹침)

    muzzle_cx = left + 0.72 * Wd
    muzzle_cy = top + 0.72 * Hd
    half_total = 0.20 * Wd  # 주둥이 전체 폭(0.40W)의 절반
    cx1 = muzzle_cx - half_total + r_left
    cx2 = cx1 + gap

    # 외곽선 두께를 몸 크기에 비례시킨다 — 애니메이션처럼 작게 그릴 때 고정 두께(LW)를 그대로 쓰면
    # 이음새의 두 원 테두리가 서로 겹쳐 코가 통째로 파묻혀 버린다.
    face_lw = max(3, round(rx * 0.019))

    # 주둥이 두 원 (겹쳐서 이음새가 보이도록 순서대로 그린다)
    wobbly_ellipse(d, cx1, muzzle_cy, r_left, r_left, fill=muzzle_fill, outline=OUTLINE, width=face_lw, seed=seed + 1)
    wobbly_ellipse(d, cx2, muzzle_cy, r_right, r_right, fill=muzzle_fill, outline=OUTLINE, width=face_lw, seed=seed + 2)

    # 두 원이 만나는 이음새 위쪽 교차점 (반지름이 다른 두 원의 실제 기하로 계산)
    a = (gap * gap + r_left * r_left - r_right * r_right) / (2 * gap)
    seam_dy = math.sqrt(max(r_left * r_left - a * a, 0))
    seam_x = cx1 + a
    seam_top_y = muzzle_cy - seam_dy

    # 코: 이음새 꼭대기에 얹힌 크고 새까만 삼각/하트 덩어리 (폭 0.31 × 오른쪽 원 지름)
    D_right = 2 * r_right
    nw, nh = 0.31 * D_right, 0.31 * D_right * 0.9
    top_y = seam_top_y - nh * 0.30
    apex_y = seam_top_y + nh * 0.55
    d.polygon([(seam_x - nw / 2, top_y), (seam_x + nw / 2, top_y), (seam_x, apex_y)], fill=OUTLINE)
    d.ellipse([seam_x - nw * 0.36, top_y - nh * 0.08, seam_x + nw * 0.36, top_y + nh * 0.42], fill=OUTLINE)

    # 눈: 아주 작은 검은 점 2개 (코의 1/4 지름), 얼굴 위쪽 x=0.66W/0.80W, y=0.42H
    eye_cy = top + 0.42 * Hd
    eye_r = 0.04 * D_right
    eye_positions = [left + 0.66 * Wd, left + 0.80 * Wd]
    if expression == "dead":
        for ex in eye_positions:
            d.line([(ex - eye_r, eye_cy - eye_r), (ex + eye_r, eye_cy + eye_r)], fill=eye_color, width=6)
            d.line([(ex - eye_r, eye_cy + eye_r), (ex + eye_r, eye_cy - eye_r)], fill=eye_color, width=6)
    elif expression == "sleepy":
        for ex in eye_positions:
            d.arc([ex - eye_r * 1.6, eye_cy - eye_r, ex + eye_r * 1.6, eye_cy + eye_r * 2.2],
                  200, 340, fill=eye_color, width=6)
    else:
        for ex in eye_positions:
            d.ellipse([ex - eye_r, eye_cy - eye_r, ex + eye_r, eye_cy + eye_r], fill=eye_color)

    if expression == "angry":
        for ex, tilt in zip(eye_positions, (-1, 1)):
            y0 = eye_cy - D_right * 0.16
            d.line([(ex - D_right * 0.20, y0 + tilt * D_right * 0.09),
                    (ex + D_right * 0.20, y0 - tilt * D_right * 0.09)], fill=OUTLINE, width=9)

    if tongue:
        d.ellipse([seam_x - r_right * 0.22, muzzle_cy + r_right * 0.55,
                   seam_x + r_right * 0.22, muzzle_cy + r_right * 1.05],
                  fill=(224, 110, 132), outline=OUTLINE, width=3)

    if whiskers:
        # 원본 실측: 주둥이 오른쪽 원 테두리에서 시작해 몸 밖으로 조금만(≈0.25W) 나간다
        wx0 = cx2 + r_right * 0.9
        wlen = 0.25 * Wd
        for i, dy in enumerate((-0.30, 0.0, 0.32)):
            wobbly_line(d, [(wx0, muzzle_cy + dy * r_right * 1.1),
                             (wx0 + wlen, muzzle_cy + dy * r_right * 1.9)],
                        OUTLINE, 5, seed=seed + 30 + i, jitter=3)

    return dict(cx1=cx1, cx2=cx2, r=r_right, muzzle_cy=muzzle_cy, seam_x=seam_x,
                eye_cy=eye_cy, eye_positions=eye_positions)


def body(d, cx, cy, rx, ry, fill, seed):
    wobbly_ellipse(d, cx, cy, rx, ry, fill=fill, outline=None, seed=seed)
    crayon_hatch(d, cx, cy, rx, ry, fill, seed)


def outline_body(d, cx, cy, rx, ry, seed):
    """다리/귀/얼굴을 얹은 뒤 몸통 테두리를 맨 위에 다시 그려 정리한다."""
    wobbly_ellipse(d, cx, cy, rx, ry, fill=None, outline=OUTLINE, width=LW, seed=seed)


def save(img, name):
    path = os.path.join(OUT_DIR, name + ".png")
    img.save(path)
    print(f"{path}  {img.width}x{img.height}")


# ===================================================================== 냥이들
def normal_cat():
    img, d = new_canvas()
    cx, cy, rx, ry = 780, 580, 505, 326  # W:H ≈ 1.55:1 (원본 실측)
    fur = (243, 190, 66)
    stripe = (168, 112, 40)

    tail(d, cx - rx * 0.92, cy - ry * 0.05, fur, rx, seed=1)
    legs(d, cx, cy, rx, ry)
    body(d, cx, cy, rx, ry, fur, seed=2)
    tabby_stripes(d, cx, cy, rx, ry, stripe, seed=3)
    outline_body(d, cx, cy, rx, ry, seed=2)
    ears(d, cx, cy, rx, ry, fur)
    draw_face(d, cx, cy, rx, ry)
    save(img, "normal_cat")


def laser_cat():
    img, d = new_canvas()
    cx, cy, rx, ry = 780, 580, 505, 326  # W:H ≈ 1.55:1 (원본 실측)
    fur = (243, 190, 66)
    stripe = (168, 112, 40)
    goggle = (86, 196, 90)

    tail(d, cx - rx * 0.92, cy - ry * 0.05, fur, rx, seed=1)
    legs(d, cx, cy, rx, ry)
    body(d, cx, cy, rx, ry, fur, seed=2)
    tabby_stripes(d, cx, cy, rx, ry, stripe, seed=3)
    outline_body(d, cx, cy, rx, ry, seed=2)
    ears(d, cx, cy, rx, ry, fur)
    f = draw_face(d, cx, cy, rx, ry, whiskers=True)

    # 고글: 눈 자리를 가리는 초록색으로 꽉 찬 원 2개 + 굵은 검은 테
    eye_cy, eye_positions, r = f["eye_cy"], f["eye_positions"], f["r"]
    gr = r * 0.42
    strap_y = eye_cy
    d.line([(cx - rx * 0.05, strap_y - gr * 0.3), (cx + rx * 0.95, strap_y - gr * 0.1)],
           fill=OUTLINE, width=LW)
    for ex in eye_positions:
        wobbly_ellipse(d, ex, eye_cy, gr, gr, fill=goggle, outline=OUTLINE, width=LW - 2, seed=int(ex))
        d.ellipse([ex - gr * 0.3, eye_cy - gr * 0.3, ex + gr * 0.3, eye_cy + gr * 0.3], fill=(200, 255, 200))
    save(img, "laser_cat")


def fluffy_cat():
    img, d = new_canvas()
    cx, cy, rx, ry = 780, 580, 520, 336  # W:H ≈ 1.55:1 (원본 실측)
    fur = (238, 196, 92)
    stripe = (150, 100, 40)

    tail(d, cx - rx * 0.92, cy - ry * 0.05, fur, rx, seed=1, bushy=True)
    legs(d, cx, cy, rx, ry, spread=0.6)
    # 뾰족뾰족 톱니 실루엣 (털)
    rnd = random.Random(5)
    n = 90
    pts = []
    for i in range(n):
        a = 2 * math.pi * i / n
        jag = 1 + (0.10 if i % 2 == 0 else -0.02) + rnd.uniform(-0.02, 0.02)
        pts.append((cx + math.cos(a) * rx * jag, cy + math.sin(a) * ry * jag))
    d.polygon(pts, fill=fur, outline=OUTLINE)
    for i in range(0, n, 2):
        x, y = pts[i]
        d.line([(cx + (x - cx) * 0.86, cy + (y - cy) * 0.86), (x, y)], fill=OUTLINE, width=LW - 3)
    body(d, cx, cy, rx * 0.9, ry * 0.9, fur, seed=2)
    tabby_stripes(d, cx, cy, rx * 0.9, ry * 0.9, stripe, seed=3, n=20)
    ears(d, cx, cy, rx, ry, fur, size=1.1)
    draw_face(d, cx, cy, rx, ry)
    save(img, "fluffy_cat")


def green_cat():
    img, d = new_canvas()
    cx, cy, rx, ry = 780, 580, 470, 303  # W:H ≈ 1.55:1 (원본 실측)
    green = (95, 191, 79)

    tail(d, cx - rx * 0.92, cy - ry * 0.05, green, rx, seed=1)
    legs(d, cx, cy, rx, ry)
    body(d, cx, cy, rx, ry, green, seed=2)
    outline_body(d, cx, cy, rx, ry, seed=2)
    ears(d, cx, cy, rx, ry, green)
    draw_face(d, cx, cy, rx, ry)
    save(img, "green_cat")


def flying_cat():
    img, d = new_canvas()
    cx, cy, rx, ry = 780, 580, 460, 297  # W:H ≈ 1.55:1 (원본 실측)
    fur = (243, 200, 90)
    stripe = (168, 112, 40)

    # 가늘고 뾰족한 날개 (길쭉한 렌즈 모양), 좌우로 길게 뻗는다
    for side in (-1, 1):
        wx = cx + side * rx * 0.10
        wy = cy - ry * 0.10
        length = rx * 1.35
        half_w = ry * 0.30
        wing = [(wx, wy)]
        # 부드러운 렌즈 곡선을 여러 점으로 근사
        for t in [0.15, 0.35, 0.55, 0.75, 1.0]:
            wing.append((wx + side * length * t, wy - half_w * math.sin(t * math.pi)))
        for t in [0.75, 0.55, 0.35, 0.15]:
            wing.append((wx + side * length * t, wy + half_w * math.sin(t * math.pi)))
        d.polygon(wing, fill=(250, 236, 200), outline=OUTLINE, width=LW - 3)

    legs(d, cx, cy, rx, ry)
    body(d, cx, cy, rx, ry, fur, seed=2)
    tabby_stripes(d, cx, cy, rx, ry, stripe, seed=3)
    outline_body(d, cx, cy, rx, ry, seed=2)
    tail(d, cx - rx * 0.92, cy - ry * 0.05, fur, rx, seed=1)
    ears(d, cx, cy, rx, ry, fur)
    draw_face(d, cx, cy, rx, ry, expression="angry")
    save(img, "flying_cat")


def blue_cat():
    img, d = new_canvas()
    cx, cy, rx, ry = 780, 580, 470, 303  # W:H ≈ 1.55:1 (원본 실측)
    blue = (108, 150, 226)

    tail(d, cx - rx * 0.92, cy - ry * 0.05, blue, rx, seed=1)
    legs(d, cx, cy, rx, ry)
    body(d, cx, cy, rx, ry, blue, seed=2)
    # 흰 얼룩 무늬
    rnd = random.Random(9)
    for _ in range(6):
        fx = rnd.uniform(-0.7, 0.15)
        fy = rnd.uniform(-0.6, 0.6)
        x, y = cx + fx * rx, cy + fy * ry
        rr = rnd.uniform(0.10, 0.20) * rx
        wobbly_ellipse(d, x, y, rr, rr * 0.7, fill=WHITE, outline=OUTLINE, width=LW - 4, seed=int(x))
    outline_body(d, cx, cy, rx, ry, seed=2)
    ears(d, cx, cy, rx, ry, blue)
    draw_face(d, cx, cy, rx, ry, tongue=True)
    save(img, "blue_cat")


def anvil_cat():
    img, d = new_canvas()
    blue = (78, 104, 214)
    dark = (54, 74, 172)
    cx = 700

    # 모루 위쪽 가로 막대 (왼쪽 끝이 뿔처럼 뾰족)
    top_y0, top_y1 = 300, 470
    d.polygon([(140, (top_y0 + top_y1) / 2), (300, top_y0), (1180, top_y0),
               (1180, top_y1), (300, top_y1)], fill=blue, outline=OUTLINE, width=LW)
    d.rectangle([300, top_y0, 1180, top_y0 + 40], fill=dark)
    # 아래 사각 받침
    d.rectangle([420, 470, 1000, 760], fill=blue, outline=OUTLINE, width=LW)
    legs(d, cx, 760, 300, 40, n=4, spread=0.9, length=(50, 80))
    # 귀 — 모루 윗면(top_y0)에 밑변이 딱 붙도록
    for ex in (620, 820):
        d.polygon([(ex - 60, top_y0 + 16), (ex, top_y0 - 100), (ex + 60, top_y0 + 10)],
                   fill=blue, outline=OUTLINE, width=LW - 2)
    draw_face(d, cx - 60, 470, 360, 260, scale=0.92)
    save(img, "anvil_cat")


def poop_cat():
    img, d = new_canvas()
    poop = (185, 132, 66)
    cx = 660

    for (cy, rx, ry) in ((760, 420, 190), (560, 320, 150), (390, 210, 110)):
        wobbly_ellipse(d, cx, cy, rx, ry, fill=poop, outline=OUTLINE, width=LW, seed=cy)
        crayon_hatch(d, cx, cy, rx, ry, poop, seed=cy)
        wobbly_ellipse(d, cx, cy, rx, ry, fill=None, outline=OUTLINE, width=LW, seed=cy)
    d.polygon([(cx - 70, 300), (cx, 190), (cx + 70, 300)], fill=poop, outline=OUTLINE, width=LW - 2)

    for sx, top in ((330, 160), (600, 100), (880, 170)):
        pts = [(sx + math.sin(i * 0.9) * 24, top - i * 34) for i in range(4)]
        wobbly_line(d, pts, OUTLINE, 6, seed=sx)

    legs(d, cx, 760, 420, 190, n=4, spread=0.65, length=(40, 70))
    draw_face(d, cx - 10, 770, 420, 300, scale=1.05)
    save(img, "poop_cat")


def boss_evil_cat():
    img, d = new_canvas()
    cx, cy, rx, ry = 780, 580, 540, 348  # W:H ≈ 1.55:1 (원본 실측)
    black = (26, 24, 26)

    tail(d, cx - rx * 0.92, cy - ry * 0.05, black, rx, seed=1, bushy=True)
    legs(d, cx, cy, rx, ry, length=(70, 110))
    body(d, cx, cy, rx, ry, black, seed=2)
    tabby_stripes(d, cx, cy, rx, ry, (70, 66, 66), seed=3)
    outline_body(d, cx, cy, rx, ry, seed=2)
    ears(d, cx, cy, rx, ry, black, size=1.15)
    draw_face(d, cx, cy, rx, ry, expression="angry")
    save(img, "boss_evil_cat")


def boss_human_cat():
    img, d = new_canvas()
    cx, cy, rx, ry = 780, 580, 470, 303  # W:H ≈ 1.55:1 (원본 실측)
    fur = (250, 205, 70)

    # 빛나는 방사선 — 몸통 안쪽에서 시작해 몸 뒤로 지나가게, 몸을 그린 뒤 덮이는 부분만 가려진다
    rnd = random.Random(7)
    for i in range(24):
        a = 2 * math.pi * i / 24
        k0, k1 = 0.80, 1.45 + 0.22 * (i % 2)
        wobbly_line(d, [(cx + math.cos(a) * rx * k0, cy + math.sin(a) * ry * k0),
                         (cx + math.cos(a) * rx * k1, cy + math.sin(a) * ry * k1)],
                    (250, 210, 90), 8, seed=i)

    tail(d, cx - rx * 0.92, cy - ry * 0.05, fur, rx, seed=1)
    legs(d, cx, cy, rx, ry)
    body(d, cx, cy, rx, ry, fur, seed=2)
    outline_body(d, cx, cy, rx, ry, seed=2)
    ears(d, cx, cy, rx, ry, fur)
    draw_face(d, cx, cy, rx, ry)
    save(img, "boss_human_cat")


def boss_wizard_cat():
    img, d = new_canvas()
    cx, cy, rx, ry = 700, 620, 440, 284  # W:H ≈ 1.55:1 (원본 실측)
    grey = (150, 150, 158)
    purple = (108, 78, 168)

    tail(d, cx - rx * 0.92, cy - ry * 0.05, grey, rx, seed=1)
    legs(d, cx, cy, rx, ry)
    body(d, cx, cy, rx, ry, grey, seed=2)
    outline_body(d, cx, cy, rx, ry, seed=2)
    ears(d, cx, cy, rx, ry, grey)

    # 별무늬 보라 망토
    cape = [(cx - rx * 0.9, cy - ry * 0.2), (cx - rx * 1.3, cy + ry * 1.1),
            (cx + rx * 0.3, cy + ry * 1.0), (cx + rx * 0.5, cy - ry * 0.3)]
    d.polygon(cape, fill=purple, outline=OUTLINE, width=LW - 2)
    rnd = random.Random(11)
    for _ in range(10):
        sx = cx + rnd.uniform(-rx * 1.1, rx * 0.2)
        sy = cy + rnd.uniform(-ry * 0.1, ry * 0.9)
        star_r = rnd.uniform(10, 18)
        pts = []
        for k in range(10):
            ang = math.pi / 2 + k * math.pi / 5
            rr = star_r if k % 2 == 0 else star_r * 0.45
            pts.append((sx + math.cos(ang) * rr, sy - math.sin(ang) * rr))
        d.polygon(pts, fill=(224, 210, 245))

    # 고깔모자 + 지팡이
    hat_cx = cx + rx * 0.15
    d.polygon([(hat_cx - 130, cy - ry * 0.85), (hat_cx + 30, cy - ry * 1.9),
               (hat_cx + 150, cy - ry * 0.8)], fill=purple, outline=OUTLINE, width=LW - 2)
    d.ellipse([hat_cx - 30, cy - ry * 2.0, hat_cx + 60, cy - ry * 1.75], fill=(224, 210, 245), outline=OUTLINE)
    staff_x = cx + rx * 1.05
    d.line([(staff_x, cy - ry * 1.5), (staff_x, cy + ry * 0.9)], fill=(120, 84, 46), width=LW + 4)
    d.ellipse([staff_x - 34, cy - ry * 1.8, staff_x + 34, cy - ry * 1.2], fill=(180, 140, 230), outline=OUTLINE, width=6)

    draw_face(d, cx, cy, rx, ry)
    save(img, "boss_wizard_cat")


def ally_cat():
    img, d = new_canvas()
    cx, cy, rx, ry = 780, 580, 470, 303  # W:H ≈ 1.55:1 (원본 실측)
    fur = (245, 208, 110)
    stripe = (198, 150, 70)

    tail(d, cx - rx * 0.92, cy - ry * 0.05, fur, rx, seed=1)
    legs(d, cx, cy, rx, ry)
    body(d, cx, cy, rx, ry, fur, seed=2)
    tabby_stripes(d, cx, cy, rx, ry, stripe, seed=3, n=10)
    outline_body(d, cx, cy, rx, ry, seed=2)
    ears(d, cx, cy, rx, ry, fur)
    draw_face(d, cx, cy, rx, ry, tongue=True)
    save(img, "ally_cat")


ALL = {
    "normal_cat": normal_cat,
    "laser_cat": laser_cat,
    "fluffy_cat": fluffy_cat,
    "green_cat": green_cat,
    "flying_cat": flying_cat,
    "blue_cat": blue_cat,
    "anvil_cat": anvil_cat,
    "poop_cat": poop_cat,
    "boss_evil_cat": boss_evil_cat,
    "boss_human_cat": boss_human_cat,
    "boss_wizard_cat": boss_wizard_cat,
    "ally_cat": ally_cat,
}


if __name__ == "__main__":
    names = sys.argv[1:] or list(ALL.keys())
    for name in names:
        ALL[name]()
