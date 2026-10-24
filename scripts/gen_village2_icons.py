"""상점마을2 새 물건 그림 (2026-10-24) — 손그림 톤의 간단한 코드 그림. shop_<id>.png 256x256 로 저장."""
import math, sys
from PIL import Image, ImageDraw

OUT = "app/src/main/res/drawable-nodpi/shop_{}.png"
S = 4
K = (34, 30, 28)
LW = 6 * S


def canvas():
    im = Image.new("RGBA", (256 * S, 256 * S), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def p(*xy):
    return [v * S for v in xy]


def save(im, name):
    im.resize((256, 256), Image.LANCZOS).save(OUT.format(name))


def cup(d, body, top):
    d.polygon(p(70, 90, 186, 90, 170, 220, 86, 220), fill=body, outline=K, width=LW)
    d.ellipse(p(70, 72, 186, 108), fill=top, outline=K, width=LW)


def coconut_milk():
    im, d = canvas()
    d.ellipse(p(50, 70, 206, 226), fill=(130, 84, 50), outline=K, width=LW)
    d.ellipse(p(78, 86, 178, 130), fill=(250, 248, 236), outline=K, width=LW)
    d.line(p(150, 100, 196, 30), fill=(240, 80, 120), width=10 * S)
    d.ellipse(p(80, 150, 96, 166), fill=(90, 56, 30))
    d.ellipse(p(110, 170, 126, 186), fill=(90, 56, 30))
    save(im, "coconut_milk")


def squid():
    im, d = canvas()
    d.line(p(128, 30, 128, 236), fill=(170, 120, 70), width=8 * S)
    d.polygon(p(128, 30, 176, 100, 164, 150, 92, 150, 80, 100), fill=(236, 140, 90), outline=K, width=LW)
    for x in (92, 110, 128, 146, 164):
        d.line(p(x, 150, x + (x - 128) * 0.3, 214), fill=(236, 140, 90), width=12 * S)
        d.line(p(x, 150, x + (x - 128) * 0.3, 214), fill=K, width=2 * S)
    d.ellipse(p(106, 110, 120, 124), fill=K); d.ellipse(p(136, 110, 150, 124), fill=K)
    save(im, "squid")


def fried_shrimp():
    im, d = canvas()
    d.ellipse(p(40, 150, 216, 226), fill=(255, 255, 255), outline=K, width=LW)
    for k, ang in enumerate((-30, -10)):
        cx, cy = 118 + k * 30, 140
        d.rounded_rectangle(p(cx - 22, cy - 90, cx + 22, cy + 30), radius=22 * S, fill=(232, 170, 70), outline=K, width=LW)
        for t in range(4):
            d.line(p(cx - 18, cy - 70 + t * 24, cx + 18, cy - 62 + t * 24), fill=(200, 130, 40), width=4 * S)
        d.polygon(p(cx - 14, cy + 30, cx + 14, cy + 30, cx + 22, cy + 56, cx - 22, cy + 56), fill=(240, 90, 60), outline=K, width=4 * S)
    save(im, "fried_shrimp")


def crab_meat():
    im, d = canvas()
    d.ellipse(p(30, 150, 226, 230), fill=(255, 255, 255), outline=K, width=LW)
    for k in range(3):
        x = 70 + k * 46
        d.rounded_rectangle(p(x, 120, x + 40, 190), radius=12 * S, fill=(255, 245, 240), outline=K, width=4 * S)
        d.rectangle(p(x + 2, 122, x + 38, 146), fill=(236, 80, 70))
    save(im, "crab_meat")


def cat_sushi():
    im, d = canvas()
    for k in range(2):
        x = 52 + k * 82
        d.rounded_rectangle(p(x, 130, x + 72, 196), radius=18 * S, fill=(252, 250, 244), outline=K, width=LW)
        d.rounded_rectangle(p(x - 6, 104, x + 78, 142), radius=16 * S, fill=(250, 140, 100), outline=K, width=LW)
        for t in range(3):
            d.line(p(x + 8 + t * 22, 108, x + 18 + t * 22, 138), fill=(255, 220, 200), width=4 * S)
    # 고양이 귀 모양 김
    d.polygon(p(150, 70, 170, 40, 186, 72), fill=(40, 60, 40), outline=K, width=4 * S)
    d.polygon(p(194, 72, 212, 40, 226, 72), fill=(40, 60, 40), outline=K, width=4 * S)
    save(im, "cat_sushi")


def beach_ball():
    im, d = canvas()
    box = p(38, 38, 218, 218)
    cols = [(240, 70, 70), (255, 255, 255), (60, 130, 230), (255, 255, 255), (250, 200, 40), (255, 255, 255)]
    for i, c in enumerate(cols):
        d.pieslice(box, i * 60, (i + 1) * 60, fill=c)
    d.ellipse(box, outline=K, width=LW)
    d.ellipse(p(112, 112, 144, 144), fill=(255, 255, 255), outline=K, width=4 * S)
    save(im, "beach_ball")


def shell_rattle():
    im, d = canvas()
    d.line(p(128, 150, 128, 236), fill=(170, 120, 70), width=12 * S)
    d.pieslice(p(48, 40, 208, 200), 180, 360, fill=(250, 190, 200), outline=K, width=LW)
    d.rectangle(p(60, 118, 196, 136), fill=(250, 190, 200), outline=K, width=LW)
    for a in range(200, 341, 28):
        r = math.radians(a)
        d.line(p(128, 120, 128 + 76 * math.cos(r), 120 + 76 * math.sin(r)), fill=K, width=3 * S)
    save(im, "shell_rattle")


def crab_doll():
    im, d = canvas()
    for sx in (-1, 1):
        for k in range(3):
            d.line(p(128 + sx * 50, 160 + k * 14, 128 + sx * 96, 180 + k * 18), fill=K, width=6 * S)
        d.ellipse(p(128 + sx * 70 - 26, 70, 128 + sx * 70 + 26, 116), fill=(236, 80, 70), outline=K, width=LW)
        d.polygon(p(128 + sx * 70 - 6, 70, 128 + sx * 70 + 6, 70, 128 + sx * 70, 96), fill=(255, 255, 255))
    d.ellipse(p(66, 110, 190, 200), fill=(236, 80, 70), outline=K, width=LW)
    for sx in (-1, 1):
        d.line(p(128 + sx * 18, 112, 128 + sx * 22, 90), fill=K, width=5 * S)
        d.ellipse(p(128 + sx * 22 - 10, 78, 128 + sx * 22 + 10, 98), fill=(255, 255, 255), outline=K, width=4 * S)
    save(im, "crab_doll")


def fish_kite():
    im, d = canvas()
    d.line(p(150, 150, 40, 240), fill=K, width=3 * S)
    d.polygon(p(40, 110, 140, 50, 200, 110, 140, 170), fill=(80, 170, 240), outline=K, width=LW)
    d.polygon(p(196, 110, 236, 70, 236, 150), fill=(250, 200, 40), outline=K, width=LW)
    d.ellipse(p(80, 96, 100, 116), fill=(255, 255, 255), outline=K, width=4 * S)
    for k in range(3):
        d.line(p(120 + k * 20, 80, 120 + k * 20, 140), fill=(40, 110, 190), width=4 * S)
    save(im, "fish_kite")


def cat_vitamin():
    im, d = canvas()
    d.rounded_rectangle(p(74, 70, 182, 226), radius=20 * S, fill=(250, 200, 60), outline=K, width=LW)
    d.rectangle(p(84, 40, 172, 74), fill=(255, 255, 255), outline=K, width=LW)
    d.ellipse(p(96, 120, 160, 184), fill=(255, 255, 255), outline=K, width=4 * S)
    d.polygon(p(104, 132, 112, 112, 122, 130), fill=(240, 150, 60)); d.polygon(p(134, 130, 144, 112, 152, 132), fill=(240, 150, 60))
    save(im, "cat_vitamin")


def h_big_heal():
    im, d = canvas()
    d.rectangle(p(108, 28, 148, 62), fill=(180, 120, 70), outline=K, width=LW)
    d.ellipse(p(44, 60, 212, 232), fill=(240, 80, 110), outline=K, width=LW)
    d.ellipse(p(76, 90, 112, 126), fill=(255, 200, 215))
    # 하트
    d.polygon(p(128, 196, 88, 150, 128, 124, 168, 150), fill=(255, 255, 255))
    d.ellipse(p(84, 116, 132, 160), fill=(255, 255, 255)); d.ellipse(p(124, 116, 172, 160), fill=(255, 255, 255))
    save(im, "h_big_heal")


def h_coral_glove():
    im, d = canvas()
    d.rounded_rectangle(p(60, 60, 196, 190), radius=50 * S, fill=(250, 120, 110), outline=K, width=LW)
    d.rounded_rectangle(p(40, 100, 90, 160), radius=24 * S, fill=(250, 120, 110), outline=K, width=LW)
    d.rectangle(p(80, 186, 176, 226), fill=(255, 220, 200), outline=K, width=LW)
    for x, y in ((110, 90), (150, 110), (130, 140)):
        d.ellipse(p(x - 10, y - 10, x + 10, y + 10), fill=(255, 190, 180))
    save(im, "h_coral_glove")


def h_shell_armor():
    im, d = canvas()
    d.pieslice(p(36, 40, 220, 240), 180, 360, fill=(250, 214, 170), outline=K, width=LW)
    d.rectangle(p(50, 138, 206, 210), fill=(250, 214, 170), outline=K, width=LW)
    for a in range(196, 345, 24):
        r = math.radians(a)
        d.line(p(128, 140, 128 + 88 * math.cos(r), 140 + 96 * math.sin(r)), fill=K, width=3 * S)
    d.ellipse(p(116, 160, 140, 184), fill=(255, 255, 255), outline=K, width=3 * S)
    save(im, "h_shell_armor")


def h_anchor():
    im, d = canvas()
    d.line(p(128, 50, 128, 210), fill=(110, 116, 130), width=20 * S)
    d.ellipse(p(108, 22, 148, 62), outline=(110, 116, 130), width=12 * S)
    d.line(p(84, 84, 172, 84), fill=(110, 116, 130), width=14 * S)
    d.arc(p(48, 120, 208, 230), 20, 160, fill=(110, 116, 130), width=18 * S)
    d.polygon(p(40, 160, 70, 150, 62, 186), fill=(110, 116, 130)); d.polygon(p(216, 160, 186, 150, 194, 186), fill=(110, 116, 130))
    save(im, "h_anchor")


def boat_plate():
    im, d = canvas()
    d.rounded_rectangle(p(40, 60, 216, 200), radius=14 * S, fill=(170, 176, 190), outline=K, width=LW)
    for x, y in ((62, 82), (194, 82), (62, 178), (194, 178)):
        d.ellipse(p(x - 9, y - 9, x + 9, y + 9), fill=(110, 116, 130), outline=K, width=3 * S)
    d.line(p(70, 110, 120, 100), fill=(230, 234, 240), width=6 * S)
    save(im, "boat_plate")


def boat_harpoon():
    im, d = canvas()
    d.line(p(40, 216, 196, 60), fill=(170, 120, 70), width=14 * S)
    d.polygon(p(196, 60, 236, 20, 222, 80, 208, 72), fill=(170, 176, 190), outline=K, width=LW)
    d.polygon(p(184, 72, 200, 40, 214, 76), fill=(170, 176, 190), outline=K, width=4 * S)
    d.line(p(40, 216, 70, 240), fill=(230, 230, 220), width=5 * S)
    save(im, "boat_harpoon")


if __name__ == "__main__":
    for f in (coconut_milk, squid, fried_shrimp, crab_meat, cat_sushi, beach_ball, shell_rattle, crab_doll, fish_kite,
              cat_vitamin, h_big_heal, h_coral_glove, h_shell_armor, h_anchor, boat_plate, boat_harpoon):
        f()
    print("ok")
