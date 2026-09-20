"""
고양이 상점 아이템 아이콘 — 매직펜 외곽선 + 평평한 단색의 만화풍 그림 (로비 아이콘과 같은 448px 흰 배경).

출력: app/src/main/res/drawable-nodpi/shop_<id>.png
사용법: python scripts/gen_shop_icons.py
"""

import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DST_DIR = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")

SIZE = 448
INK = (28, 24, 22)
W = 9   # 외곽선 두께


def canvas():
    # 투명 배경: 상점 카드뿐 아니라 고양이 관리 화면(종이색 바탕)에도 그대로 놓인다
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def save(im, name):
    path = os.path.join(DST_DIR, f"shop_{name}.png")
    im.save(path)
    print(path)


def rrect(d, box, r, fill, outline=INK, width=W):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def ell(d, box, fill, outline=INK, width=W):
    d.ellipse(box, fill=fill, outline=outline, width=width)


def line(d, pts, width=W, fill=INK):
    d.line(pts, fill=fill, width=width, joint="curve")
    for (x, y) in (pts[0], pts[-1]):
        r = width / 2
        d.ellipse((x - r, y - r, x + r, y + r), fill=fill)


def poly(d, pts, fill, outline=INK, width=W):
    d.polygon(pts, fill=fill, outline=outline, width=width)


def star(d, cx, cy, r, fill=(255, 220, 60)):
    pts = []
    for i in range(10):
        rr = r if i % 2 == 0 else r * 0.45
        a = -math.pi / 2 + math.pi * i / 5
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    poly(d, pts, fill, width=5)


def sparkles(d, pts):
    for (x, y) in pts:
        line(d, [(x - 12, y), (x + 12, y)], width=6)
        line(d, [(x, y - 12), (x, y + 12)], width=6)


def cat_face(d, cx, cy, r, fur=(242, 192, 66)):
    """냥이 얼굴 도장: 작은 점 눈 2개 + 흰 주둥이 원 2개 + 검은 코 (원안 얼굴 구조)."""
    ell(d, (cx - r, cy - r * 0.9, cx + r, cy + r * 0.9), fur)
    # 귀
    poly(d, [(cx - r * 0.7, cy - r * 0.5), (cx - r * 0.55, cy - r * 1.15), (cx - r * 0.2, cy - r * 0.8)], fur)
    poly(d, [(cx + r * 0.7, cy - r * 0.5), (cx + r * 0.55, cy - r * 1.15), (cx + r * 0.2, cy - r * 0.8)], fur)
    mr = r * 0.34
    ell(d, (cx - mr * 1.6, cy - mr * 0.2, cx + 0, cy + mr * 1.6), (255, 255, 255), width=6)
    ell(d, (cx - 0, cy - mr * 0.2, cx + mr * 1.6, cy + mr * 1.6), (255, 255, 255), width=6)
    ell(d, (cx - 9, cy - 4, cx + 9, cy + 12), INK, width=1)
    for ex in (cx - r * 0.35, cx + r * 0.35):
        ell(d, (ex - 6, cy - r * 0.45 - 6, ex + 6, cy - r * 0.45 + 6), INK, width=1)


# ------------------------------------------------------------------ 간식

def gen_churu():
    im, d = canvas()
    # 기울어진 긴 스틱 파우치
    body = [(150, 400), (110, 360), (290, 70), (330, 110)]
    poly(d, body, (238, 120, 90))
    poly(d, [(150, 400), (110, 360), (140, 312), (180, 352)], (200, 60, 50))   # 아래 꼭지
    poly(d, [(290, 70), (330, 110), (312, 140), (272, 100)], (255, 240, 200))  # 위 뜯는 곳
    # 짜낸 츄르
    ell(d, (320, 40, 384, 96), (245, 228, 190))
    ell(d, (350, 70, 400, 120), (245, 228, 190))
    # 라벨 냥이 얼굴
    cat_face(d, 218, 236, 44)
    save(im, "churu")


def gen_tuna_can():
    im, d = canvas()
    ell(d, (74, 250, 374, 400), (190, 196, 205))            # 캔 아랫면
    d.rectangle((74, 200, 374, 325), fill=(108, 150, 214))
    line(d, [(74, 200), (74, 325)]); line(d, [(374, 200), (374, 325)])
    ell(d, (74, 130, 374, 270), (200, 208, 218))            # 뚜껑
    ell(d, (110, 152, 338, 248), (232, 236, 240), width=6)
    # 라벨 물고기
    poly(d, [(150, 300), (250, 265), (250, 335)], (255, 214, 74), width=6)
    poly(d, [(250, 300), (300, 275), (300, 325)], (255, 214, 74), width=6)
    ell(d, (170, 290, 182, 302), INK, width=1)
    # 고리
    ell(d, (200, 176, 250, 210), (200, 208, 218), width=6)
    save(im, "tuna_can")


def gen_feed():
    im, d = canvas()
    rrect(d, (100, 70, 348, 400), 26, (255, 214, 74))
    d.rectangle((100, 70, 348, 110), fill=(232, 66, 56))
    d.rectangle((100, 70, 348, 110), outline=INK, width=W)
    # 톱니 윗부분
    for x in range(110, 340, 26):
        line(d, [(x, 70), (x + 13, 52), (x + 26, 70)], width=6)
    cat_face(d, 224, 230, 62)
    # 알갱이
    for (x, y) in ((140, 350), (190, 370), (250, 360), (300, 350)):
        ell(d, (x - 14, y - 10, x + 14, y + 10), (150, 96, 44), width=5)
    save(im, "feed")


def gen_catnip():
    im, d = canvas()
    # 작은 화분
    poly(d, [(140, 290), (308, 290), (280, 410), (168, 410)], (216, 130, 80))
    rrect(d, (126, 262, 322, 300), 10, (232, 150, 96))
    # 줄기와 잎
    line(d, [(224, 280), (224, 120)])
    for (x, y, flip) in ((224, 230, -1), (224, 190, 1), (224, 150, -1)):
        pts = [(x, y), (x + flip * 60, y - 46), (x + flip * 110, y - 20), (x + flip * 60, y + 10)]
        poly(d, pts, (108, 196, 92))
        line(d, [(x, y), (x + flip * 70, y - 20)], width=5)
    ell(d, (200, 84, 248, 132), (108, 196, 92))
    sparkles(d, [(110, 90), (340, 70), (350, 190)])
    save(im, "catnip")


# ------------------------------------------------------------------ 장난감

def gen_ball():
    im, d = canvas()
    ell(d, (90, 90, 358, 358), (240, 96, 96))
    # 띠
    d.chord((90, 90, 358, 358), 200, 340, fill=(255, 214, 74))
    d.chord((90, 90, 358, 358), 20, 160, fill=(255, 214, 74))
    ell(d, (90, 90, 358, 358), None, width=W)
    line(d, [(97, 180), (351, 180)], width=7); line(d, [(97, 268), (351, 268)], width=7)
    # 방울
    ell(d, (188, 188, 260, 260), (255, 240, 170))
    ell(d, (214, 226, 234, 246), INK, width=1)
    # 움직임 선
    line(d, [(30, 150), (60, 170)], width=6); line(d, [(24, 224), (56, 224)], width=6); line(d, [(30, 298), (60, 278)], width=6)
    save(im, "ball")


def gen_fish_toy():
    im, d = canvas()
    line(d, [(60, 400), (300, 80)], width=12)                       # 막대
    line(d, [(300, 80), (330, 120), (350, 190), (340, 260)], width=5)  # 줄
    # 물고기
    body = (280, 250, 400, 330)
    ell(d, body, (108, 150, 214))
    poly(d, [(290, 290), (250, 250), (250, 330)], (108, 150, 214))
    ell(d, (362, 276, 380, 294), (255, 255, 255), width=4)
    ell(d, (370, 282, 378, 290), INK, width=1)
    # 깃털 장식
    for a in (-30, 0, 30):
        rad = math.radians(a - 60)
        line(d, [(340, 262), (340 + 50 * math.cos(rad), 262 + 50 * math.sin(rad))], width=7, fill=(240, 96, 96))
    save(im, "fish_toy")


def gen_brush():
    im, d = canvas()
    rrect(d, (70, 300, 300, 400), 40, (232, 150, 96))            # 손잡이
    rrect(d, (250, 60, 400, 400), 30, (108, 150, 214))           # 머리
    rrect(d, (272, 84, 378, 376), 18, (200, 220, 245), width=6)
    for y in range(110, 360, 34):
        for x in (300, 326, 352):
            ell(d, (x - 8, y - 8, x + 8, y + 8), INK, width=1)
    # 빠진 털
    line(d, [(200, 120), (230, 90), (240, 130)], width=5, fill=(242, 192, 66))
    save(im, "brush")


# ------------------------------------------------------------------ 생활용품

def gen_water_bowl():
    im, d = canvas()
    ell(d, (60, 250, 388, 400), (108, 150, 214))                 # 그릇 아래
    d.rectangle((60, 220, 388, 325), fill=(108, 150, 214))
    line(d, [(60, 220), (60, 325)]); line(d, [(388, 220), (388, 325)])
    ell(d, (60, 160, 388, 280), (108, 150, 214))                 # 테두리
    ell(d, (92, 180, 356, 260), (150, 210, 255), width=6)        # 물
    # 물결
    line(d, [(140, 220), (170, 208), (200, 220), (230, 208), (260, 220), (290, 208)], width=6, fill=(255, 255, 255))
    # 물방울
    poly(d, [(330, 70), (355, 110), (330, 140), (305, 110)], (150, 210, 255), width=6)
    save(im, "water_bowl")


def gen_cushion():
    im, d = canvas()
    rrect(d, (50, 160, 398, 380), 80, (240, 120, 150))
    rrect(d, (90, 190, 358, 350), 60, (255, 170, 190), width=6)
    # 가운데 단추와 주름
    ell(d, (208, 254, 240, 286), (200, 60, 90))
    for a in range(0, 360, 60):
        rad = math.radians(a)
        line(d, [(224 + 30 * math.cos(rad), 270 + 22 * math.sin(rad)),
                 (224 + 90 * math.cos(rad), 270 + 60 * math.sin(rad))], width=5)
    # 자는 표시
    for i, s in enumerate((18, 26, 34)):
        x = 300 + i * 34; y = 130 - i * 34
        line(d, [(x - s / 2, y - s / 2), (x + s / 2, y - s / 2), (x - s / 2, y + s / 2), (x + s / 2, y + s / 2)], width=6)
    save(im, "cushion")


def gen_scratcher():
    im, d = canvas()
    # 비스듬한 골판지 판
    poly(d, [(70, 380), (250, 90), (390, 130), (210, 420)], (222, 184, 120))
    poly(d, [(70, 380), (210, 420), (210, 440), (70, 400)], (190, 150, 90))
    for i in range(8):
        t = i / 8
        x0 = 100 + (250 - 100) * t; y0 = 372 - (372 - 110) * t
        line(d, [(x0, y0), (x0 + 125, y0 + 36)], width=5, fill=(180, 140, 80))
    # 발톱 자국
    for dx in (0, 22, 44):
        line(d, [(150 + dx, 300), (190 + dx, 240)], width=6, fill=(140, 100, 50))
    save(im, "scratcher")


def gen_litter():
    im, d = canvas()
    # 덮개형 화장실
    rrect(d, (60, 200, 388, 400), 30, (108, 150, 214))
    d.chord((60, 60, 388, 340), 180, 360, fill=(160, 190, 235), outline=INK, width=W)
    d.rectangle((60, 200, 388, 214), fill=(108, 150, 214))
    line(d, [(60, 200), (388, 200)])
    # 입구
    rrect(d, (250, 240, 350, 370), 40, (60, 50, 70))
    # 모래 삽
    line(d, [(90, 120), (150, 190)], width=8)
    poly(d, [(60, 80), (120, 60), (130, 120), (80, 135)], (255, 214, 74))
    # 모래알
    for (x, y) in ((110, 330), (150, 360), (190, 340)):
        ell(d, (x - 8, y - 6, x + 8, y + 6), (240, 220, 170), width=4)
    save(im, "litter")


def gen_cat_tower():
    im, d = canvas()
    # 기둥
    d.rectangle((200, 100, 248, 400), fill=(222, 184, 120), outline=INK, width=W)
    for y in range(120, 400, 28):
        line(d, [(204, y), (244, y + 10)], width=4, fill=(190, 150, 90))
    # 단
    rrect(d, (60, 380, 388, 420), 14, (108, 150, 214))
    rrect(d, (110, 240, 338, 276), 14, (108, 150, 214))
    rrect(d, (130, 90, 318, 126), 14, (108, 150, 214))
    # 꼭대기에 앉은 냥이 얼굴
    cat_face(d, 224, 46, 40)
    # 매달린 공
    line(d, [(320, 276), (320, 320)], width=4)
    ell(d, (304, 316, 336, 348), (240, 96, 96))
    save(im, "cat_tower")


def gen_cat_armor():
    im, d = canvas()
    # 냥이 몸통 위에 덮는 은빛 갑옷 (가슴판 + 등판) + 투구
    ell(d, (90, 150, 360, 380), (200, 206, 214))
    rrect(d, (120, 200, 330, 250), 20, (150, 158, 170), width=6)
    for x in (170, 225, 280):
        ell(d, (x - 10, 215, x + 10, 235), (255, 214, 74), width=4)
    ell(d, (130, 40, 320, 200), (200, 206, 214))
    poly(d, [(150, 95), (170, 30), (205, 90)], (200, 206, 214))
    poly(d, [(300, 95), (280, 30), (245, 90)], (200, 206, 214))
    rrect(d, (160, 150, 290, 170), 8, (150, 158, 170), width=5)
    sparkles(d, [(70, 80), (390, 120)])
    save(im, "cat_armor")


def gen_grave():
    im, d = canvas()
    ell(d, (40, 320, 408, 420), (108, 196, 92))                    # 잔디 둔덕
    rrect(d, (130, 90, 318, 380), 70, (170, 172, 180))               # 묘비
    rrect(d, (156, 120, 292, 240), 40, (200, 202, 210), width=5)
    cx, cy = 224, 180
    for ex in (cx - 26, cx + 26):                                    # X 눈
        line(d, [(ex - 10, cy - 10), (ex + 10, cy + 10)], width=6)
        line(d, [(ex - 10, cy + 10), (ex + 10, cy - 10)], width=6)
    ell(d, (cx - 8, cy + 14, cx + 8, cy + 28), INK, width=1)
    poly(d, [(150, 105), (170, 50), (200, 100)], (170, 172, 180))    # 귀 장식
    poly(d, [(298, 105), (278, 50), (248, 100)], (170, 172, 180))
    line(d, [(350, 400), (350, 330)], width=6, fill=(60, 140, 60))   # 꽃
    for a in range(0, 360, 72):
        r = math.radians(a)
        ell(d, (350 + 18 * math.cos(r) - 10, 318 + 18 * math.sin(r) - 10,
                350 + 18 * math.cos(r) + 10, 318 + 18 * math.sin(r) + 10), (240, 96, 96), width=4)
    ell(d, (342, 310, 358, 326), (255, 214, 74), width=3)
    save(im, "grave")


def gen_revive_potion():
    im, d = canvas()
    # 둥근 물약병: 분홍 액체 + 하트 라벨 + 코르크, 주변 반짝임
    rrect(d, (196, 40, 252, 110), 10, (222, 184, 120))               # 코르크
    d.rectangle((186, 100, 262, 150), fill=(200, 220, 245), outline=INK, width=W)   # 병목
    ell(d, (80, 130, 368, 418), (200, 220, 245))                     # 병
    d.chord((92, 142, 356, 406), 0, 180, fill=(240, 96, 140))        # 아래 절반 액체
    d.chord((92, 142, 356, 406), 0, 180, outline=INK, width=4)
    line(d, [(110, 274), (338, 274)], width=5, fill=(200, 60, 100))  # 수면
    # 하트 라벨
    cx, cy = 224, 300
    d.pieslice((cx - 44, cy - 40, cx, cy + 4), 180, 360, fill=(255, 255, 255))
    d.pieslice((cx, cy - 40, cx + 44, cy + 4), 180, 360, fill=(255, 255, 255))
    poly(d, [(cx - 44, cy - 18), (cx + 44, cy - 18), (cx, cy + 46)], (255, 255, 255), outline=None, width=0)
    line(d, [(cx - 44, cy - 18), (cx, cy + 46), (cx + 44, cy - 18)], width=6)
    d.arc((cx - 44, cy - 40, cx, cy + 4), 180, 360, fill=INK, width=6)
    d.arc((cx, cy - 40, cx + 44, cy + 4), 180, 360, fill=INK, width=6)
    # 기포와 반짝임
    for (x, y, r) in ((150, 330, 10), (300, 360, 8), (270, 330, 6)):
        ell(d, (x - r, y - r, x + r, y + r), (255, 200, 220), width=3)
    sparkles(d, [(60, 90), (390, 120), (400, 300)])
    save(im, "revive_potion")


def gen_revive_all():
    im, d = canvas()
    # 다부활: 큰 물약병(부활 약과 같은 결) + 병 앞에 작은 하트 세 개 — "팀 전부" 느낌
    rrect(d, (196, 40, 252, 110), 10, (222, 184, 120))               # 코르크
    d.rectangle((186, 100, 262, 150), fill=(200, 220, 245), outline=INK, width=W)   # 병목
    ell(d, (80, 130, 368, 418), (200, 220, 245))                     # 병
    d.chord((92, 142, 356, 406), 0, 180, fill=(255, 150, 60))        # 아래 절반 액체 (주황 — 부활 약과 구분)
    d.chord((92, 142, 356, 406), 0, 180, outline=INK, width=4)
    line(d, [(110, 274), (338, 274)], width=5, fill=(220, 110, 40))  # 수면
    for (cx, cy, r) in ((160, 300, 30), (224, 340, 34), (290, 300, 30)):    # 하트 셋
        d.pieslice((cx - r, cy - r * 0.9, cx, cy + r * 0.1), 180, 360, fill=(255, 255, 255))
        d.pieslice((cx, cy - r * 0.9, cx + r, cy + r * 0.1), 180, 360, fill=(255, 255, 255))
        poly(d, [(cx - r, cy - r * 0.4), (cx + r, cy - r * 0.4), (cx, cy + r * 1.05)], (255, 255, 255), outline=None, width=0)
        line(d, [(cx - r, cy - r * 0.4), (cx, cy + r * 1.05), (cx + r, cy - r * 0.4)], width=5)
        d.arc((cx - r, cy - r * 0.9, cx, cy + r * 0.1), 180, 360, fill=INK, width=5)
        d.arc((cx, cy - r * 0.9, cx + r, cy + r * 0.1), 180, 360, fill=INK, width=5)
    sparkles(d, [(60, 90), (390, 120), (400, 300), (50, 330)])
    save(im, "revive_all")


def gen_attack_up():
    im, d = canvas()
    # 발톱 자국이 난 빨간 배지 + 위쪽 화살표 (공격력 Lv업)
    ell(d, (60, 90, 388, 418), (240, 96, 96))
    ell(d, (96, 126, 352, 382), (255, 150, 140), width=5)
    for dx in (-40, 0, 40):                                  # 발톱 자국 3줄
        line(d, [(224 + dx - 18, 320), (224 + dx + 18, 190)], width=12, fill=(200, 50, 60))
    # 위 화살표
    poly(d, [(224, 20), (300, 110), (252, 110), (252, 170), (196, 170), (196, 110), (148, 110)], (255, 214, 74))
    sparkles(d, [(40, 60), (410, 80), (400, 300)])
    save(im, "attack_up")


def main():
    os.makedirs(DST_DIR, exist_ok=True)
    for fn in (gen_churu, gen_tuna_can, gen_feed, gen_catnip, gen_ball, gen_fish_toy,
               gen_brush, gen_water_bowl, gen_cushion, gen_scratcher, gen_litter, gen_cat_tower,
               gen_cat_armor, gen_grave, gen_revive_potion, gen_revive_all, gen_attack_up):
        fn()


if __name__ == "__main__":
    main()
