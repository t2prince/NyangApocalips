"""
로비 아이콘 재작화 — 고양이가 들어가는 칸을 '진짜 냥이'(원본 손그림 추출 스프라이트)로 다시 그린다.

이전 로비 아이콘은 콧볼(주둥이 원 2개) 안에 눈동자가 들어간 일반 만화 고양이여서 원안과 딴판이었다.
여기서는 얼굴을 새로 그리지 않고, 이미 원안에서 추출해 둔 drawable-nodpi/cat_*.png 를
그대로 배치·합성만 한다 (원안 스펙: 작은 점 눈 + 속이 흰 큰 주둥이).

출력: app/src/main/res/drawable-nodpi/lobby_{battle,cat_skill,cat_manage,skill,mission,settings}.png
      (참고용 사본: Plan/lobby_assets2/)

사용법: python scripts/gen_lobby_icons.py
"""

import math
import os
import random

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPRITE_DIR = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
DST_DIR = SPRITE_DIR
REF_DIR = os.path.join(ROOT, "Plan/lobby_assets2")
os.makedirs(REF_DIR, exist_ok=True)

SIZE = 448
INK = (28, 24, 22)

FONT_PATH = "/System/Library/Fonts/Supplemental/AppleGothic.ttf"


def font(size):
    return ImageFont.truetype(FONT_PATH, size)


def load_sprite(name, width=None, flip=False, rotate=0):
    im = Image.open(os.path.join(SPRITE_DIR, name)).convert("RGBA")
    if flip:
        im = im.transpose(Image.FLIP_LEFT_RIGHT)
    if width:
        s = width / im.width
        im = im.resize((width, max(1, int(im.height * s))), Image.LANCZOS)
    if rotate:
        im = im.rotate(rotate, expand=True, resample=Image.BICUBIC)
    return im


def canvas(bg=(255, 255, 255)):
    im = Image.new("RGB", (SIZE, SIZE), bg)
    return im, ImageDraw.Draw(im)


def paste(dst, sprite, xy):
    dst.paste(sprite, xy, sprite)


def starburst(draw, cx, cy, r_out, r_in, points=8, fill=(255, 220, 60), outline=(235, 120, 30), width=6, phase=0.0):
    pts = []
    for i in range(points * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.pi * i / points + phase
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    draw.polygon(pts, fill=fill, outline=outline, width=width)


def thick_line(draw, pts, width=9, fill=INK):
    draw.line(pts, fill=fill, width=width, joint="curve")
    # 끝을 둥글게
    for (x, y) in (pts[0], pts[-1]):
        r = width / 2
        draw.ellipse((x - r, y - r, x + r, y + r), fill=fill)


def stickman_kick(draw, x, y, scale=1.0, head_r=26):
    """(x, y) = 골반 위치. 오른쪽으로 날아차기 하는 졸라맨 (원안 스타일)."""
    s = scale
    hr = head_r * s
    w = int(9 * s)
    hip = (x, y)
    neck = (x + 6 * s, y - 78 * s)
    head = (x + 9 * s, y - 78 * s - hr)
    # 머리 (흰 속 + 굵은 검은 테)
    draw.ellipse((head[0] - hr, head[1] - hr, head[0] + hr, head[1] + hr),
                 fill=(255, 255, 255), outline=INK, width=int(7 * s))
    # 몸통
    thick_line(draw, [neck, hip], width=w)
    # 팔: 뒤로 뻗은 팔 + 앞주먹
    sh = (x + 4 * s, y - 62 * s)
    thick_line(draw, [sh, (x - 48 * s, y - 40 * s)], width=w)
    thick_line(draw, [sh, (x + 52 * s, y - 84 * s)], width=w)
    # 다리: 딛는 다리 + 앞차기 다리
    thick_line(draw, [hip, (x - 20 * s, y + 78 * s)], width=w)
    thick_line(draw, [hip, (x + 78 * s, y - 6 * s), (x + 96 * s, y - 10 * s)], width=w)


def scribble_fill(draw, box, color, seed, n=90, slope=0.35, width=5):
    """색연필 느낌의 사선 스트로크 채우기 (군데군데 바탕이 비침)."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    for _ in range(n):
        x = rnd.uniform(x0, x1)
        y = rnd.uniform(y0, y1)
        ln = rnd.uniform(18, 46)
        dy = ln * slope * rnd.uniform(0.6, 1.4)
        draw.line([(x, y), (x + ln, y - dy)], fill=color, width=width)


def save(im, name):
    im.save(os.path.join(DST_DIR, name))
    im.save(os.path.join(REF_DIR, name))
    print("saved", name)


# ---------------------------------------------------------------- 전투
def gen_battle():
    im, d = canvas((191, 226, 245))
    # 하늘 크레용 결
    scribble_fill(d, (0, 0, SIZE, 270), (166, 210, 238), seed=11, n=70, width=6)
    # 풀밭
    d.rectangle((0, 300, SIZE, SIZE), fill=(124, 201, 95))
    scribble_fill(d, (0, 305, SIZE, SIZE), (95, 175, 70), seed=7, n=80, width=6)
    d.line((0, 300, SIZE, 300), fill=INK, width=7)

    # 걷어차이며 날아가는 냥이 (원안 스프라이트 그대로, 좌우반전 + 기울임)
    cat = load_sprite("cat_normal.png", width=200, flip=True, rotate=18)
    paste(im, cat, (225, 95))
    # 얻어맞은 효과
    starburst(d, 215, 300, 46, 20, phase=0.4)
    # 날아가는 궤적
    for i, (x0, y0, x1, y1) in enumerate([(180, 250, 230, 220), (170, 285, 225, 262), (196, 320, 240, 296)]):
        d.line((x0, y0, x1, y1), fill=INK, width=5)

    # 날아차기 졸라맨
    stickman_kick(d, 110, 300, scale=1.1)
    return im


# ---------------------------------------------------------------- 고양이 기술
def gen_cat_skill():
    im, d = canvas()
    # 당하는 그린냥이
    green = load_sprite("cat_green.png", width=250)
    paste(im, green, (160, 250))
    # 덮치는 냥이 (냥냥펀치)
    cat = load_sprite("cat_normal.png", width=230, rotate=-24)
    paste(im, cat, (30, 40))
    # 발톱 자국 + 타격 별
    starburst(d, 250, 250, 40, 17, phase=0.2)
    for i in range(3):
        x = 285 + i * 26
        d.line((x, 205, x - 30, 275), fill=(226, 58, 46), width=8)
    # 속도선
    for x0, y0, x1, y1 in [(40, 250, 95, 225), (55, 290, 110, 262)]:
        d.line((x0, y0, x1, y1), fill=INK, width=5)
    return im


# ---------------------------------------------------------------- 고양이 관리
def gen_cat_manage():
    im, d = canvas()
    cat = load_sprite("cat_boss_evil.png", width=270)
    cx, cy = 90, 105
    paste(im, cat, (cx, cy))
    w, h = cat.size

    f = font(36)
    ann = [
        # (냥이 위 지점, 라벨 위치, 라벨)
        ((cx + w * 0.62, cy + h * 0.04), (390, 48), "귀"),
        ((cx + w * 0.86, cy + h * 0.42), (398, 210), "코"),
        ((cx + w * 0.10, cy + h * 0.30), (30, 60), "꼬리"),
    ]
    for (px, py), (lx, ly), label in ann:
        d.line((px, py, lx + 12, ly + 40 if ly < py else ly), fill=INK, width=4)
        d.text((lx, ly), label, font=f, fill=INK)
    return im


# ---------------------------------------------------------------- 기술 (인간 기술)
def gen_skill():
    im, d = canvas()
    # 날아가는 회색 콩(원안의 X눈 콩돌이)
    bean = Image.new("RGBA", (200, 160), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bean)
    bd.ellipse((15, 25, 185, 135), fill=(154, 160, 166), outline=INK, width=9)
    for ex in (70, 120):
        bd.line((ex - 13, 62, ex + 13, 88), fill=INK, width=7)
        bd.line((ex + 13, 62, ex - 13, 88), fill=INK, width=7)
    bean = bean.rotate(16, expand=True, resample=Image.BICUBIC)
    paste(im, bean, (225, 60))

    starburst(d, 230, 255, 44, 19, phase=0.6)
    for x0, y0, x1, y1 in [(175, 190, 228, 168), (165, 228, 222, 205), (185, 265, 235, 243)]:
        d.line((x0, y0, x1, y1), fill=INK, width=5)

    stickman_kick(d, 105, 262, scale=1.15)
    return im


# ---------------------------------------------------------------- 미션 (무지개 냥이 카드)
RAINBOW = [(226, 58, 46), (240, 140, 40), (245, 205, 60), (95, 191, 79), (66, 133, 224), (150, 90, 200)]


def rainbow_cat(width=280):
    """냥이 스프라이트의 채색 픽셀만 가로 무지개 띠로 바꾼다. 검은 선·흰 콧볼은 그대로."""
    im = load_sprite("cat_normal.png", width=width)
    px = im.load()
    w, h = im.size
    for y in range(h):
        band = RAINBOW[min(len(RAINBOW) - 1, int(y / h * len(RAINBOW)))]
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (r + g + b) / 3
            if lum < 90:      # 잉크 선 → 그대로
                continue
            if r > 225 and g > 225 and b > 225:  # 흰 콧볼/종이 → 그대로
                continue
            if lum < 165:     # 진한 해칭 스트로크 → 띠 색을 어둡게
                px[x, y] = (int(band[0] * 0.62), int(band[1] * 0.62), int(band[2] * 0.62), a)
            else:             # 몸통 단색 → 띠 색
                px[x, y] = (band[0], band[1], band[2], a)
    return im


def gen_mission():
    im, d = canvas()
    d.rounded_rectangle((52, 44, 412, 424), radius=26, fill=(255, 248, 231), outline=INK, width=10)
    cat = rainbow_cat(280)
    paste(im, cat, (92, 130))
    f = font(38)
    tw = d.textlength("무지개 냥이", font=f)
    d.text(((52 + 412 - tw) / 2, 356), "무지개 냥이", font=f, fill=INK)
    # 카드 모서리의 별
    starburst(d, 60, 52, 46, 20, points=5, fill=(245, 205, 60), outline=INK, width=7, phase=-math.pi / 2)
    return im


# ---------------------------------------------------------------- 설정
def gen_settings():
    im, d = canvas()

    # 톱니바퀴 2개
    def gear(cx, cy, r, teeth=8):
        for i in range(teeth):
            a = math.pi * 2 * i / teeth
            x0 = cx + (r + 14) * math.cos(a)
            y0 = cy + (r + 14) * math.sin(a)
            d.line((cx + r * 0.7 * math.cos(a), cy + r * 0.7 * math.sin(a), x0, y0),
                   fill=INK, width=18)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(174, 180, 186), outline=INK, width=8)
        d.ellipse((cx - r * 0.38, cy - r * 0.38, cx + r * 0.38, cy + r * 0.38),
                  fill=(255, 255, 255), outline=INK, width=6)

    gear(150, 110, 52)
    gear(268, 66, 30)

    # 점검대(테이블) 위의 냥이
    table_top = 318
    d.rounded_rectangle((56, table_top, 400, table_top + 34), radius=10, fill=(181, 119, 63), outline=INK, width=7)
    for lx in (92, 340):
        d.rectangle((lx, table_top + 34, lx + 22, 424), fill=(181, 119, 63), outline=INK, width=6)

    cat = load_sprite("cat_normal.png", width=235)
    paste(im, cat, (108, table_top - cat.height + 6))

    # 스패너 (냥이를 가리지 않게 오른쪽 위 구석)
    d.line((358, 178, 408, 120), fill=(120, 126, 132), width=15)
    d.ellipse((395, 102, 425, 132), fill=(174, 180, 186), outline=INK, width=6)
    d.ellipse((347, 168, 371, 192), fill=(174, 180, 186), outline=INK, width=6)
    return im


def main():
    save(gen_battle(), "lobby_battle.png")
    save(gen_cat_skill(), "lobby_cat_skill.png")
    save(gen_cat_manage(), "lobby_cat_manage.png")
    save(gen_skill(), "lobby_skill.png")
    save(gen_mission(), "lobby_mission.png")
    save(gen_settings(), "lobby_settings.png")


if __name__ == "__main__":
    main()
