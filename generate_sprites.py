#!/usr/bin/env python3
"""Generate game sprites matching the 전투화면 reference style"""

from PIL import Image, ImageDraw, ImageFont
import os
import math

OUT = "app/src/main/res/drawable"
os.makedirs(OUT, exist_ok=True)

# Colors from reference
CAT_BODY = (215, 175, 120)
CAT_OUTLINE = (90, 60, 30)
CAT_EAR = (180, 130, 70)
CAT_EYE_WHITE = (255, 255, 255)
CAT_PUPIL = (30, 30, 30)
CAT_NOSE = (160, 100, 60)
WOOD = (140, 95, 50)
WOOD_DARK = (100, 65, 30)
STICK_COLOR = (50, 45, 40)
WHITE = (255, 255, 255)
SHADOW = (0, 0, 0, 50)


def outlined_ellipse(d, bbox, fill, outline, width=3):
    d.ellipse(bbox, fill=fill)
    d.ellipse(bbox, outline=outline, width=width)


def outlined_circle(d, cx, cy, r, fill, outline, width=3):
    outlined_ellipse(d, [cx-r, cy-r, cx+r, cy+r], fill, outline, width)


def draw_cat_sprite(size=256, body_col=CAT_BODY, ear_col=CAT_EAR,
                     has_weapon=True, label="cat", name="sprite_normal_cat",
                     eye_style="normal", accessory=None):
    """Draw a cat matching the reference style - round body, outlined, big eyes"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size / 256.0
    cx = int(size * 0.48)

    # Ground shadow
    sh_w, sh_h = int(55*s), int(14*s)
    d.ellipse([cx-sh_w, int(210*s)-sh_h, cx+sh_w, int(210*s)+sh_h], fill=(0,0,0,40))

    body_cy = int(155*s)
    body_rx, body_ry = int(50*s), int(42*s)
    head_cy = int(95*s)
    head_r = int(42*s)

    # Tail
    pts = []
    for i in range(15):
        t = i / 14.0
        tx = cx + int((45 + t*20)*s)
        ty = body_cy - int(t*55*s) - int(math.sin(t*3)*10*s)
        pts.append((tx, ty))
    for i in range(len(pts)-1):
        d.line([pts[i], pts[i+1]], fill=body_col, width=max(3, int(7*s)))
    for i in range(len(pts)-1):
        d.line([pts[i], pts[i+1]], fill=CAT_OUTLINE, width=max(1, int(2*s)))

    # Weapon (wooden club)
    if has_weapon:
        wx = cx - int(50*s)
        wy = body_cy - int(15*s)
        # Handle
        d.line([(wx, wy), (wx-int(25*s), wy+int(45*s))], fill=WOOD_DARK, width=max(2, int(5*s)))
        # Club head
        d.ellipse([wx-int(35*s), wy+int(35*s), wx-int(12*s), wy+int(60*s)], fill=WOOD)
        d.ellipse([wx-int(35*s), wy+int(35*s), wx-int(12*s), wy+int(60*s)], outline=WOOD_DARK, width=max(1, int(2*s)))

    # Feet/legs
    foot_y = int(195*s)
    foot_r = int(12*s)
    outlined_ellipse(d, [cx-int(28*s), foot_y-foot_r, cx-int(8*s), foot_y+foot_r], body_col, CAT_OUTLINE, max(2, int(3*s)))
    outlined_ellipse(d, [cx+int(8*s), foot_y-foot_r, cx+int(28*s), foot_y+foot_r], body_col, CAT_OUTLINE, max(2, int(3*s)))

    # Body
    outlined_ellipse(d, [cx-body_rx, body_cy-body_ry, cx+body_rx, body_cy+body_ry],
                     body_col, CAT_OUTLINE, max(2, int(3.5*s)))

    # "cat" label on body
    try:
        font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", max(12, int(16*s)))
    except:
        font = ImageFont.load_default()
    tw = d.textlength(label, font=font)
    d.text((cx - tw/2, body_cy + int(5*s)), label, fill=CAT_OUTLINE, font=font)

    # Head
    outlined_circle(d, cx, head_cy, head_r, body_col, CAT_OUTLINE, max(2, int(3.5*s)))

    # Ears
    ear_h = int(30*s)
    ear_w = int(16*s)
    for ex in [cx - int(25*s), cx + int(25*s)]:
        ear = [(ex - ear_w, head_cy - int(25*s)),
               (ex, head_cy - int(25*s) - ear_h),
               (ex + ear_w, head_cy - int(25*s))]
        d.polygon(ear, fill=ear_col)
        d.polygon(ear, outline=CAT_OUTLINE, width=max(1, int(2.5*s)))
        # Inner ear
        inner = [(ex - ear_w + int(5*s), head_cy - int(22*s)),
                 (ex, head_cy - int(22*s) - ear_h + int(10*s)),
                 (ex + ear_w - int(5*s), head_cy - int(22*s))]
        d.polygon(inner, fill=(230, 180, 150))

    # Eyes (big white circles with small pupils - matching reference)
    eye_r = int(14*s)
    pupil_r = int(6*s)
    lex, rex = cx - int(16*s), cx + int(16*s)
    ey = head_cy - int(5*s)

    outlined_circle(d, lex, ey, eye_r, CAT_EYE_WHITE, CAT_OUTLINE, max(1, int(2*s)))
    outlined_circle(d, rex, ey, eye_r, CAT_EYE_WHITE, CAT_OUTLINE, max(1, int(2*s)))

    if eye_style == "normal":
        d.ellipse([lex-pupil_r, ey-pupil_r, lex+pupil_r, ey+pupil_r], fill=CAT_PUPIL)
        d.ellipse([rex-pupil_r, ey-pupil_r, rex+pupil_r, ey+pupil_r], fill=CAT_PUPIL)
    elif eye_style == "angry":
        d.ellipse([lex-pupil_r, ey-pupil_r, lex+pupil_r, ey+pupil_r], fill=(180,0,0))
        d.ellipse([rex-pupil_r, ey-pupil_r, rex+pupil_r, ey+pupil_r], fill=(180,0,0))
        # Angry eyebrows
        d.line([(lex-eye_r, ey-eye_r-int(3*s)), (lex+eye_r, ey-int(5*s))], fill=CAT_OUTLINE, width=max(2, int(3*s)))
        d.line([(rex-eye_r, ey-int(5*s)), (rex+eye_r, ey-eye_r-int(3*s))], fill=CAT_OUTLINE, width=max(2, int(3*s)))

    # Eye highlights
    hl = int(4*s)
    d.ellipse([lex-eye_r+int(3*s), ey-eye_r+int(3*s), lex-eye_r+int(3*s)+hl*2, ey-eye_r+int(3*s)+hl*2], fill=WHITE)
    d.ellipse([rex-eye_r+int(3*s), ey-eye_r+int(3*s), rex-eye_r+int(3*s)+hl*2, ey-eye_r+int(3*s)+hl*2], fill=WHITE)

    # Nose
    nose_y = head_cy + int(10*s)
    d.ellipse([cx-int(4*s), nose_y-int(3*s), cx+int(4*s), nose_y+int(3*s)], fill=CAT_NOSE)

    # Mouth
    d.arc([cx-int(8*s), nose_y, cx, nose_y+int(10*s)], 0, 180, fill=CAT_OUTLINE, width=max(1, int(2*s)))
    d.arc([cx, nose_y, cx+int(8*s), nose_y+int(10*s)], 0, 180, fill=CAT_OUTLINE, width=max(1, int(2*s)))

    # Whiskers
    wl = int(30*s)
    for dy_off in [-int(3*s), int(3*s)]:
        d.line([(cx-int(8*s), nose_y+dy_off), (cx-wl-int(8*s), nose_y+dy_off-int(5*s))],
               fill=CAT_OUTLINE, width=max(1, int(1.5*s)))
        d.line([(cx+int(8*s), nose_y+dy_off), (cx+wl+int(8*s), nose_y+dy_off-int(5*s))],
               fill=CAT_OUTLINE, width=max(1, int(1.5*s)))

    # Accessory
    if accessory == "mask":
        d.rectangle([cx-int(18*s), ey-int(3*s), cx+int(18*s), ey+int(5*s)], fill=(30,30,30,200))
    elif accessory == "wizard_hat":
        hat_top = head_cy - head_r - int(45*s)
        hat = [(cx-int(25*s), head_cy-int(30*s)),
               (cx, hat_top),
               (cx+int(25*s), head_cy-int(30*s))]
        d.polygon(hat, fill=(60, 20, 120))
        d.polygon(hat, outline=(40, 10, 80), width=max(1, int(2*s)))
        # Star on hat
        star_y = hat_top + int(18*s)
        sr = int(6*s)
        star_pts = []
        for i in range(5):
            a = math.radians(i * 72 - 90)
            star_pts.append((cx + int(math.cos(a)*sr), star_y + int(math.sin(a)*sr)))
            a2 = math.radians(i * 72 - 90 + 36)
            star_pts.append((cx + int(math.cos(a2)*sr*0.4), star_y + int(math.sin(a2)*sr*0.4)))
        d.polygon(star_pts, fill=(255, 220, 50))

    img.save(f"{OUT}/{name}.png")
    print(f"  {name}.png ({size}x{size})")


def draw_stickman(size=256, pose="idle", name="sprite_player"):
    """Draw stickman matching reference - thin lines, hollow circle head"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size / 256.0
    lw = max(3, int(5*s))
    cx = int(size * 0.45)

    # Shadow
    d.ellipse([cx-int(20*s), int(210*s)-int(5*s), cx+int(20*s), int(210*s)+int(8*s)], fill=(0,0,0,40))

    head_y = int(50*s)
    head_r = int(16*s)
    neck_y = head_y + head_r + int(3*s)
    shoulder_y = neck_y + int(8*s)
    hip_y = shoulder_y + int(50*s)
    foot_y = int(205*s)

    # Head (hollow circle like reference)
    d.ellipse([cx-head_r, head_y-head_r, cx+head_r, head_y+head_r], fill=WHITE)
    d.ellipse([cx-head_r, head_y-head_r, cx+head_r, head_y+head_r], outline=STICK_COLOR, width=lw)

    # Neck
    d.line([(cx, neck_y-int(3*s)), (cx, shoulder_y)], fill=STICK_COLOR, width=lw)
    # Body
    d.line([(cx, shoulder_y), (cx, hip_y)], fill=STICK_COLOR, width=lw)

    if pose == "idle":
        # Arms down
        d.line([(cx, shoulder_y+int(5*s)), (cx+int(22*s), hip_y-int(10*s))], fill=STICK_COLOR, width=lw)
        d.line([(cx, shoulder_y+int(5*s)), (cx-int(22*s), hip_y-int(10*s))], fill=STICK_COLOR, width=lw)
        # Legs
        d.line([(cx, hip_y), (cx+int(16*s), foot_y)], fill=STICK_COLOR, width=lw)
        d.line([(cx, hip_y), (cx-int(16*s), foot_y)], fill=STICK_COLOR, width=lw)

    elif pose == "punch":
        # Punching arm extended right
        fist_x = cx + int(55*s)
        fist_y = shoulder_y + int(8*s)
        d.line([(cx, shoulder_y+int(5*s)), (fist_x, fist_y)], fill=STICK_COLOR, width=lw)
        # Fist circle
        d.ellipse([fist_x-int(6*s), fist_y-int(6*s), fist_x+int(6*s), fist_y+int(6*s)], fill=STICK_COLOR)
        # Guard arm
        d.line([(cx, shoulder_y+int(5*s)), (cx-int(15*s), shoulder_y+int(25*s))], fill=STICK_COLOR, width=lw)
        # Impact burst
        draw_impact(d, fist_x+int(12*s), fist_y, int(18*s), s)
        # Fighting legs
        d.line([(cx, hip_y), (cx+int(18*s), foot_y)], fill=STICK_COLOR, width=lw)
        d.line([(cx, hip_y), (cx-int(12*s), foot_y)], fill=STICK_COLOR, width=lw)

    elif pose == "kick":
        # Guard arms
        d.line([(cx, shoulder_y+int(5*s)), (cx+int(20*s), shoulder_y+int(15*s))], fill=STICK_COLOR, width=lw)
        d.line([(cx, shoulder_y+int(5*s)), (cx-int(12*s), hip_y-int(15*s))], fill=STICK_COLOR, width=lw)
        # Standing leg
        d.line([(cx, hip_y), (cx-int(14*s), foot_y)], fill=STICK_COLOR, width=lw)
        # Kicking leg
        kick_x = cx + int(60*s)
        kick_y = hip_y - int(8*s)
        d.line([(cx, hip_y), (kick_x, kick_y)], fill=STICK_COLOR, width=max(3, int(6*s)))
        d.ellipse([kick_x-int(7*s), kick_y-int(7*s), kick_x+int(7*s), kick_y+int(7*s)], fill=STICK_COLOR)
        # Impact
        draw_impact(d, kick_x+int(12*s), kick_y, int(20*s), s)
        # Glow line
        d.line([(cx+int(20*s), hip_y-int(3*s)), (kick_x+int(5*s), kick_y)],
               fill=(255, 200, 50, 160), width=max(3, int(8*s)))

    img.save(f"{OUT}/{name}.png")
    print(f"  {name}.png ({size}x{size})")


def draw_impact(d, cx, cy, r, s):
    """Draw a star-burst impact effect like in reference"""
    spikes = 8
    for i in range(spikes):
        a = math.radians(i * 360 / spikes)
        inner_r = r * 0.3
        outer_r = r
        x1 = cx + int(math.cos(a) * inner_r)
        y1 = cy + int(math.sin(a) * inner_r)
        x2 = cx + int(math.cos(a) * outer_r)
        y2 = cy + int(math.sin(a) * outer_r)
        d.line([(x1, y1), (x2, y2)], fill=(255, 240, 180), width=max(2, int(3*s)))
    # Center glow
    d.ellipse([cx-int(r*0.35), cy-int(r*0.35), cx+int(r*0.35), cy+int(r*0.35)],
              fill=(255, 255, 230, 220))


def draw_hit_effect(size=128, name="sprite_hit"):
    """Big comic-style hit burst matching reference"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = size//2, size//2
    s = size / 128.0

    # White starburst (like reference)
    spikes = 10
    pts = []
    for i in range(spikes * 2):
        a = math.radians(i * 360 / (spikes * 2) - 90)
        r = int(50*s) if i % 2 == 0 else int(22*s)
        pts.append((cx + int(math.cos(a)*r), cy + int(math.sin(a)*r)))
    d.polygon(pts, fill=(255, 255, 240))
    d.polygon(pts, outline=(200, 180, 100), width=max(1, int(2*s)))

    img.save(f"{OUT}/{name}.png")
    print(f"  {name}.png")


def draw_heart(size=64, name="sprite_heart"):
    """Pink heart like floating hearts in reference"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size / 64.0
    cx, cy = size//2, size//2

    # Heart shape
    r = int(12*s)
    d.ellipse([cx-r*2+int(2*s), cy-r-int(5*s), cx+int(2*s), cy+r-int(5*s)], fill=(230, 60, 80))
    d.ellipse([cx-int(2*s), cy-r-int(5*s), cx+r*2-int(2*s), cy+r-int(5*s)], fill=(230, 60, 80))
    d.polygon([(cx-r*2+int(2*s), cy-int(2*s)), (cx+r*2-int(2*s), cy-int(2*s)),
               (cx, cy+int(20*s))], fill=(230, 60, 80))
    # Outline
    d.ellipse([cx-r*2+int(2*s), cy-r-int(5*s), cx+int(2*s), cy+r-int(5*s)], outline=(150, 30, 40), width=max(1, int(2*s)))
    d.ellipse([cx-int(2*s), cy-r-int(5*s), cx+r*2-int(2*s), cy+r-int(5*s)], outline=(150, 30, 40), width=max(1, int(2*s)))
    # Highlight
    d.ellipse([cx-int(6*s), cy-int(10*s), cx-int(1*s), cy-int(4*s)], fill=(255, 150, 160, 180))

    img.save(f"{OUT}/{name}.png")
    print(f"  {name}.png")


def draw_prison(size=256, name="sprite_prison"):
    """Metal cage with person inside"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size / 256.0

    x1, y1 = int(30*s), int(20*s)
    x2, y2 = int(226*s), int(230*s)

    # Cage frame
    d.rectangle([x1, y1, x2, y2], outline=(100, 100, 110), width=max(3, int(5*s)))
    # Bars
    bars = 6
    spacing = (x2 - x1) / (bars + 1)
    for i in range(1, bars + 1):
        bx = int(x1 + spacing * i)
        d.line([(bx, y1), (bx, y2)], fill=(80, 80, 90), width=max(2, int(4*s)))
    # Horizontal bars
    for hy in [y1 + (y2-y1)//3, y1 + 2*(y2-y1)//3]:
        d.line([(x1, hy), (x2, hy)], fill=(80, 80, 90), width=max(2, int(3*s)))

    # Small stickman inside (arms up for help)
    pcx = (x1+x2)//2
    pcy = (y1+y2)//2 + int(10*s)
    pr = int(10*s)
    plw = max(2, int(3*s))
    d.ellipse([pcx-pr, pcy-int(35*s)-pr, pcx+pr, pcy-int(35*s)+pr], fill=WHITE)
    d.ellipse([pcx-pr, pcy-int(35*s)-pr, pcx+pr, pcy-int(35*s)+pr], outline=STICK_COLOR, width=plw)
    d.line([(pcx, pcy-int(25*s)), (pcx, pcy+int(10*s))], fill=STICK_COLOR, width=plw)
    # Arms up (help!)
    d.line([(pcx, pcy-int(18*s)), (pcx-int(15*s), pcy-int(38*s))], fill=STICK_COLOR, width=plw)
    d.line([(pcx, pcy-int(18*s)), (pcx+int(15*s), pcy-int(38*s))], fill=STICK_COLOR, width=plw)
    # Legs
    d.line([(pcx, pcy+int(10*s)), (pcx-int(10*s), pcy+int(30*s))], fill=STICK_COLOR, width=plw)
    d.line([(pcx, pcy+int(10*s)), (pcx+int(10*s), pcy+int(30*s))], fill=STICK_COLOR, width=plw)

    img.save(f"{OUT}/{name}.png")
    print(f"  {name}.png")


def draw_tile(size=128, dark=False, name="sprite_tile"):
    """Floor tile matching reference beige style"""
    base = (195, 180, 155) if dark else (210, 195, 170)
    line = (175, 160, 135)
    img = Image.new("RGBA", (size, size), base + (255,))
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (size-1, 0)], fill=line, width=1)
    d.line([(0, 0), (0, size-1)], fill=line, width=1)
    d.line([(size-1, 0), (size-1, size-1)], fill=line, width=1)
    d.line([(0, size-1), (size-1, size-1)], fill=line, width=1)
    img.save(f"{OUT}/{name}.png")
    print(f"  {name}.png")


if __name__ == "__main__":
    print("Generating sprites (reference style)...")
    print()

    print("[Player]")
    draw_stickman(256, "idle", "sprite_player")
    draw_stickman(256, "punch", "sprite_player_punch")
    draw_stickman(256, "kick", "sprite_player_kick")

    print("\n[Enemies]")
    draw_cat_sprite(256, CAT_BODY, CAT_EAR, True, "cat", "sprite_normal_cat")
    draw_cat_sprite(256, (60,55,50), (40,35,30), True, "thief", "sprite_thief_cat",
                    eye_style="normal", accessory="mask")
    draw_cat_sprite(280, (85,85,105), (65,65,85), False, "laser", "sprite_laser_cat",
                    eye_style="angry")

    print("\n[Bosses]")
    draw_cat_sprite(320, (240,175,100), (220,140,60), True, "BOSS", "sprite_boss_human_cat")
    draw_cat_sprite(360, (55,25,55), (80,10,10), True, "EVIL", "sprite_boss_evil_cat",
                    eye_style="angry")
    draw_cat_sprite(360, (110,55,160), (140,70,190), False, "MAGE", "sprite_boss_wizard_cat",
                    eye_style="angry", accessory="wizard_hat")

    print("\n[Items & Effects]")
    draw_prison(256, "sprite_prison")
    draw_hit_effect(128, "sprite_hit")
    draw_heart(64, "sprite_heart")

    print("\n[Tiles]")
    draw_tile(128, False, "sprite_tile")
    draw_tile(128, True, "sprite_tile_dark")

    print("\nDone! All sprites in", OUT)
