"""고양이 기술 아이콘 (상점 아이콘과 같은 매직펜 만화풍, 448px 투명 배경). 기술은 사용자가 정한 것만.
출력: app/src/main/res/drawable-nodpi/skill_<id>.png
사용법: python scripts/gen_skill_icons.py
"""
import math, os, sys
sys.path.insert(0, 'scripts')
from PIL import Image, ImageDraw
import gen_shop_icons as G

def save(im, name):
    p = os.path.join(G.DST_DIR, f"skill_{name}.png"); im.save(p); print(p)

def gen_tornado():
    """토네이도: 위로 갈수록 넓어지는 회색 소용돌이 띠 + 날리는 잔해."""
    im, d = G.canvas()
    cx = 224
    bands = [(60, 190, 40), (110, 160, 34), (160, 130, 30), (210, 100, 26), (260, 72, 22), (310, 48, 18), (355, 26, 14)]
    for i, (y, half, h) in enumerate(bands):
        off = 12 * math.sin(i * 1.3)
        col = (205, 210, 220) if i % 2 == 0 else (170, 178, 192)
        G.ell(d, (cx - half + off, y - h, cx + half + off, y + h), col, width=7)
    # 소용돌이 선
    for i, (y, half, h) in enumerate(bands[:-1]):
        off = 12 * math.sin(i * 1.3)
        d.arc((cx - half * 0.8 + off, y - h * 0.6, cx + half * 0.8 + off, y + h * 1.4), 200, 340, fill=G.INK, width=5)
    # 잔해 (나뭇잎, 돌)
    G.poly(d, [(40, 120), (80, 100), (70, 140)], (108, 196, 92), width=5)
    G.poly(d, [(380, 90), (420, 110), (395, 135)], (108, 196, 92), width=5)
    G.ell(d, (395, 230, 425, 256), (170, 172, 180), width=5)
    G.ell(d, (30, 260, 56, 282), (170, 172, 180), width=5)
    # 바람선
    for (x, y) in ((20, 190), (30, 330), (400, 320)):
        G.line(d, [(x, y), (x + 40, y - 10)], width=6)
    save(im, "tornado")

def gen_rapid_fire():
    """연속발사: 오른쪽으로 날아가는 노란 발바닥 탄 3개 + 속도선."""
    im, d = G.canvas()
    for i, (x, y) in enumerate(((120, 300), (230, 224), (340, 148))):
        # 발바닥 모양 탄: 큰 타원 + 작은 발가락 3개, 뒤로 속도선
        G.ell(d, (x - 46, y - 34, x + 46, y + 34), (255, 214, 74))
        for a in (-40, 0, 40):
            tx = x + 44 * math.cos(math.radians(a)); ty = y + 44 * math.sin(math.radians(a))
            G.ell(d, (tx - 13, ty - 13, tx + 13, ty + 13), (255, 214, 74), width=5)
        for k in range(3):
            G.line(d, [(x - 60 - k * 22, y - 12 + k * 12), (x - 90 - k * 22, y - 12 + k * 12)], width=6)
    save(im, "rapid_fire")


def jelly_paw(d, cx, cy, r, fill, hi):
    """말랑한 젤리 발바닥: 큰 패드 + 발가락 4개, 윗부분 하이라이트."""
    G.ell(d, (cx - r, cy - r * 0.7, cx + r, cy + r * 0.85), fill)
    G.ell(d, (cx - r * 0.5, cy - r * 0.45, cx - r * 0.1, cy - r * 0.15), hi, width=0)
    for a in (-60, -22, 22, 60):
        x = cx + math.sin(math.radians(a)) * r * 1.2
        y = cy - r * 0.95 - math.cos(math.radians(a)) * r * 0.3
        G.ell(d, (x - r * 0.3, y - r * 0.34, x + r * 0.3, y + r * 0.34), fill, width=6)


def gen_jelly_paw():
    """젤리발바닥: 구름에서 분홍 젤리 발바닥이 떨어진다."""
    im, d = G.canvas()
    for (x, y, r) in ((150, 95, 55), (230, 75, 70), (315, 95, 55)):      # 구름
        G.ell(d, (x - r, y - r * 0.7, x + r, y + r * 0.7), (255, 255, 255), width=7)
    d.rectangle((100, 90, 365, 140), fill=(255, 255, 255))
    G.line(d, [(95, 140), (370, 140)], width=7)
    for (x, y, r, col, hi) in ((130, 250, 42, (255, 150, 190), (255, 220, 235)),
                               (250, 320, 52, (255, 120, 170), (255, 210, 228)),
                               (345, 225, 38, (255, 170, 200), (255, 228, 240))):
        G.line(d, [(x, y - r * 2.1), (x, y - r * 1.5)], width=5)     # 낙하선
        jelly_paw(d, x, y, r, col, hi)
    save(im, "jelly_paw")


def save_h(im, name):
    """인간 기술 아이콘은 hskill_<id>.png"""
    p = os.path.join(G.DST_DIR, f"hskill_{name}.png"); im.save(p); print(p)


def gen_stone_throw():
    """돌던지기: 날아가는 회색 돌 + 포물선 점선 + 속도선."""
    im, d = G.canvas()
    # 포물선 궤적 (점선)
    for i in range(9):
        t = i / 8
        x = 60 + 250 * t
        y = 330 - 260 * t + 230 * t * t
        G.ell(d, (x - 7, y - 7, x + 7, y + 7), (120, 120, 128), width=0)
    # 울퉁불퉁한 돌
    stone = [(270, 150), (330, 120), (392, 150), (405, 215), (370, 262), (300, 270), (255, 228)]
    G.poly(d, stone, (150, 150, 158))
    G.poly(d, [(290, 165), (330, 145), (365, 165), (345, 190), (300, 190)], (190, 190, 198), width=5)
    G.ell(d, (330, 215, 350, 233), (110, 110, 118), width=4)
    # 속도선
    for (x, y) in ((215, 150), (205, 200), (225, 250)):
        G.line(d, [(x, y), (x - 45, y + 8)], width=7)
    save_h(im, "stone_throw")


def gen_fire_punch():
    """파이어 펀치: 불꽃에 휩싸인 주먹."""
    im, d = G.canvas()
    # 뒤쪽 불꽃 (바깥 주황 → 안쪽 노랑)
    for (pts, col) in (
        ([(70, 330), (60, 220), (110, 250), (120, 140), (170, 210), (210, 90), (250, 200), (300, 120), (320, 240), (380, 200), (360, 340)], (255, 120, 40)),
        ([(120, 320), (120, 250), (160, 270), (175, 190), (215, 240), (245, 160), (270, 245), (310, 215), (310, 320)], (255, 206, 60)),
    ):
        G.poly(d, pts, col, width=6)
    # 주먹 (살색 둥근 사각 + 손가락 마디 네 줄 + 엄지)
    G.rrect(d, (150, 230, 330, 380), 50, (250, 214, 170))
    for x in (195, 240, 285):
        G.line(d, [(x, 240), (x, 300)], width=6)
    G.rrect(d, (140, 300, 230, 360), 26, (250, 214, 170), width=7)
    # 불똥
    for (x, y) in ((60, 110), (380, 100), (395, 300)):
        G.ell(d, (x - 12, y - 12, x + 12, y + 12), (255, 150, 40), width=4)
    save_h(im, "fire_punch")


def gen_missile():
    """미사일: 오른쪽 위로 날아가는 흰·빨강 미사일 + 뒤쪽 불꽃과 연기."""
    im, d = G.canvas()
    ang = math.radians(-28)
    def P(x, y):   # 미사일 좌표계(오른쪽이 앞) → 캔버스
        cx, cy = 234, 214
        return (cx + x * math.cos(ang) - y * math.sin(ang), cy + x * math.sin(ang) + y * math.cos(ang))
    # 연기
    for (x, y, r) in ((-230, 20, 30), (-190, -10, 24), (-160, 18, 20)):
        px, py = P(x, y); G.ell(d, (px - r, py - r, px + r, py + r), (225, 228, 232), width=5)
    # 불꽃
    G.poly(d, [P(-110, -30), P(-200, 0), P(-110, 30)], (255, 120, 40), width=6)
    G.poly(d, [P(-110, -16), P(-160, 0), P(-110, 16)], (255, 214, 74), width=0)
    # 꼬리 날개
    G.poly(d, [P(-100, -30), P(-130, -70), P(-60, -30)], (214, 48, 40), width=6)
    G.poly(d, [P(-100, 30), P(-130, 70), P(-60, 30)], (214, 48, 40), width=6)
    # 몸통 + 빨간 머리
    G.poly(d, [P(-110, -30), P(80, -30), P(80, 30), P(-110, 30)], (245, 245, 248), width=7)
    G.poly(d, [P(80, -30), P(150, 0), P(80, 30)], (214, 48, 40), width=7)
    G.poly(d, [P(-20, -30), P(0, -30), P(0, 30), P(-20, 30)], (214, 48, 40), width=0)
    G.poly(d, [P(-110, -30), P(80, -30), P(80, 30), P(-110, 30)], None, outline=G.INK, width=7)
    save_h(im, "missile")


def main():
    for fn in (gen_tornado, gen_rapid_fire, gen_jelly_paw, gen_stone_throw, gen_fire_punch, gen_missile): fn()

if __name__ == '__main__':
    main()
