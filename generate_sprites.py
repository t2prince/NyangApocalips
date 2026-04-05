#!/usr/bin/env python3
"""Generate game sprite images for 냥이 아포칼립스"""

from PIL import Image, ImageDraw, ImageFont
import os
import math

OUT_DIR = "app/src/main/res/drawable"
os.makedirs(OUT_DIR, exist_ok=True)


def draw_cat(size=128, body_color=(210, 170, 110), ear_color=(230, 190, 130),
             eye_color=(30, 30, 30), name="sprite_normal_cat"):
    """Draw a cute round cat sprite"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = size // 2, size // 2 + 8
    s = size / 128.0

    # Tail
    tail_pts = []
    for t in range(20):
        tt = t / 19.0
        tx = cx + int((25 + tt * 15) * s)
        ty = cy + int((5 - tt * 35) * s)
        tail_pts.append((tx, ty))
    if len(tail_pts) > 1:
        for i in range(len(tail_pts) - 1):
            d.line([tail_pts[i], tail_pts[i + 1]], fill=body_color, width=max(1, int(5 * s)))

    # Body (oval)
    bw, bh = int(28 * s), int(20 * s)
    d.ellipse([cx - bw, cy - bh // 2, cx + bw, cy + bh + 5], fill=body_color)

    # Feet
    fw = int(8 * s)
    d.ellipse([cx - int(15 * s), cy + int(14 * s), cx - int(15 * s) + fw * 2, cy + int(24 * s)], fill=body_color)
    d.ellipse([cx + int(5 * s), cy + int(14 * s), cx + int(5 * s) + fw * 2, cy + int(24 * s)], fill=body_color)

    # Head
    hr = int(20 * s)
    head_cy = cy - int(18 * s)
    d.ellipse([cx - hr, head_cy - hr, cx + hr, head_cy + hr], fill=body_color)

    # Ears
    ear_h = int(22 * s)
    # Left ear
    d.polygon([(cx - int(16 * s), head_cy - int(14 * s)),
               (cx - int(10 * s), head_cy - int(14 * s) - ear_h),
               (cx - int(2 * s), head_cy - int(14 * s))], fill=ear_color)
    # Right ear
    d.polygon([(cx + int(2 * s), head_cy - int(14 * s)),
               (cx + int(10 * s), head_cy - int(14 * s) - ear_h),
               (cx + int(16 * s), head_cy - int(14 * s))], fill=ear_color)

    # Eyes
    ew = int(6 * s)
    d.ellipse([cx - int(10 * s) - ew, head_cy - ew - int(2*s),
               cx - int(10 * s) + ew, head_cy + ew - int(2*s)], fill=(255, 255, 255))
    d.ellipse([cx + int(10 * s) - ew, head_cy - ew - int(2*s),
               cx + int(10 * s) + ew, head_cy + ew - int(2*s)], fill=(255, 255, 255))
    # Pupils
    pw = int(4 * s)
    d.ellipse([cx - int(8 * s) - pw, head_cy - pw - int(2*s),
               cx - int(8 * s) + pw, head_cy + pw - int(2*s)], fill=eye_color)
    d.ellipse([cx + int(8 * s) - pw, head_cy - pw - int(2*s),
               cx + int(8 * s) + pw, head_cy + pw - int(2*s)], fill=eye_color)
    # Eye highlights
    hl = int(2 * s)
    d.ellipse([cx - int(10 * s) - hl, head_cy - ew + hl,
               cx - int(10 * s) + hl, head_cy - ew + hl * 3], fill=(255, 255, 255))
    d.ellipse([cx + int(8 * s), head_cy - ew + hl,
               cx + int(8 * s) + hl * 2, head_cy - ew + hl * 3], fill=(255, 255, 255))

    # Nose
    nr = int(3 * s)
    nose_y = head_cy + int(6 * s)
    d.ellipse([cx - nr, nose_y - nr, cx + nr, nose_y + nr], fill=(255, 150, 150))

    # Mouth
    d.arc([cx - int(6 * s), nose_y, cx, nose_y + int(8 * s)], 0, 180, fill=(120, 80, 50), width=max(1, int(1.5 * s)))
    d.arc([cx, nose_y, cx + int(6 * s), nose_y + int(8 * s)], 0, 180, fill=(120, 80, 50), width=max(1, int(1.5 * s)))

    # Whiskers
    wl = int(25 * s)
    wy = nose_y + int(2 * s)
    d.line([(cx - int(6 * s), wy - int(3 * s)), (cx - wl, wy - int(8 * s))], fill=(100, 80, 60), width=max(1, int(1.5 * s)))
    d.line([(cx - int(6 * s), wy), (cx - wl, wy)], fill=(100, 80, 60), width=max(1, int(1.5 * s)))
    d.line([(cx + int(6 * s), wy - int(3 * s)), (cx + wl, wy - int(8 * s))], fill=(100, 80, 60), width=max(1, int(1.5 * s)))
    d.line([(cx + int(6 * s), wy), (cx + wl, wy)], fill=(100, 80, 60), width=max(1, int(1.5 * s)))

    # Blush
    blush_r = int(6 * s)
    d.ellipse([cx - int(20 * s) - blush_r, nose_y - blush_r,
               cx - int(20 * s) + blush_r, nose_y + blush_r], fill=(255, 180, 180, 80))
    d.ellipse([cx + int(20 * s) - blush_r, nose_y - blush_r,
               cx + int(20 * s) + blush_r, nose_y + blush_r], fill=(255, 180, 180, 80))

    img.save(f"{OUT_DIR}/{name}.png")
    print(f"  Created {name}.png")
    return img


def draw_stickman(size=128, name="sprite_player"):
    """Draw a stickman player sprite"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = size // 2
    s = size / 128.0
    lw = max(2, int(4 * s))

    head_y = int(22 * s)
    head_r = int(12 * s)
    shoulder_y = head_y + head_r + int(4 * s)
    hip_y = shoulder_y + int(30 * s)
    foot_y = hip_y + int(30 * s)

    # Shadow
    d.ellipse([cx - int(18 * s), foot_y - int(3 * s),
               cx + int(18 * s), foot_y + int(6 * s)], fill=(0, 0, 0, 40))

    # Head
    d.ellipse([cx - head_r, head_y - head_r, cx + head_r, head_y + head_r], fill=(40, 40, 40))

    # Body
    d.line([(cx, shoulder_y), (cx, hip_y)], fill=(40, 40, 40), width=lw)

    # Arms (idle)
    d.line([(cx, shoulder_y + int(4 * s)), (cx + int(18 * s), hip_y - int(5 * s))],
           fill=(40, 40, 40), width=lw)
    d.line([(cx, shoulder_y + int(4 * s)), (cx - int(18 * s), hip_y - int(5 * s))],
           fill=(40, 40, 40), width=lw)

    # Legs
    d.line([(cx, hip_y), (cx + int(12 * s), foot_y)], fill=(40, 40, 40), width=lw)
    d.line([(cx, hip_y), (cx - int(12 * s), foot_y)], fill=(40, 40, 40), width=lw)

    img.save(f"{OUT_DIR}/{name}.png")
    print(f"  Created {name}.png")


def draw_stickman_punch(size=128, name="sprite_player_punch"):
    """Draw stickman in punch pose"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = size // 2 - int(8 * size / 128)
    s = size / 128.0
    lw = max(2, int(4 * s))

    head_y = int(22 * s)
    head_r = int(12 * s)
    shoulder_y = head_y + head_r + int(4 * s)
    hip_y = shoulder_y + int(30 * s)
    foot_y = hip_y + int(30 * s)

    d.ellipse([cx - int(18 * s), foot_y - int(3 * s),
               cx + int(18 * s), foot_y + int(6 * s)], fill=(0, 0, 0, 40))

    d.ellipse([cx - head_r, head_y - head_r, cx + head_r, head_y + head_r], fill=(40, 40, 40))
    d.line([(cx, shoulder_y), (cx, hip_y)], fill=(40, 40, 40), width=lw)

    # Punching arm extended
    fist_x = cx + int(42 * s)
    fist_y = shoulder_y + int(6 * s)
    d.line([(cx, shoulder_y + int(4 * s)), (fist_x, fist_y)], fill=(40, 40, 40), width=lw)
    # Fist
    d.ellipse([fist_x - int(5 * s), fist_y - int(5 * s),
               fist_x + int(5 * s), fist_y + int(5 * s)], fill=(40, 40, 40))
    # Back arm guard
    d.line([(cx, shoulder_y + int(4 * s)), (cx - int(12 * s), shoulder_y + int(18 * s))],
           fill=(40, 40, 40), width=lw)

    # Impact effect
    impact_x = fist_x + int(10 * s)
    for angle_deg in range(0, 360, 45):
        a = math.radians(angle_deg)
        r1, r2 = int(5 * s), int(12 * s)
        d.line([(impact_x + int(math.cos(a) * r1), fist_y + int(math.sin(a) * r1)),
                (impact_x + int(math.cos(a) * r2), fist_y + int(math.sin(a) * r2))],
               fill=(255, 220, 100, 200), width=max(1, int(2 * s)))

    # Fighting stance legs
    d.line([(cx, hip_y), (cx + int(14 * s), foot_y)], fill=(40, 40, 40), width=lw)
    d.line([(cx, hip_y), (cx - int(10 * s), foot_y)], fill=(40, 40, 40), width=lw)

    img.save(f"{OUT_DIR}/{name}.png")
    print(f"  Created {name}.png")


def draw_stickman_kick(size=128, name="sprite_player_kick"):
    """Draw stickman in kick pose"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = size // 2 - int(5 * size / 128)
    s = size / 128.0
    lw = max(2, int(4 * s))

    head_y = int(22 * s)
    head_r = int(12 * s)
    shoulder_y = head_y + head_r + int(4 * s)
    hip_y = shoulder_y + int(30 * s)
    foot_y = hip_y + int(30 * s)

    d.ellipse([cx - int(18 * s), foot_y - int(3 * s),
               cx + int(18 * s), foot_y + int(6 * s)], fill=(0, 0, 0, 40))

    d.ellipse([cx - head_r, head_y - head_r, cx + head_r, head_y + head_r], fill=(40, 40, 40))
    d.line([(cx, shoulder_y), (cx, hip_y)], fill=(40, 40, 40), width=lw)

    # Guard arms
    d.line([(cx, shoulder_y + int(4 * s)), (cx + int(15 * s), shoulder_y + int(12 * s))],
           fill=(40, 40, 40), width=lw)
    d.line([(cx, shoulder_y + int(4 * s)), (cx - int(10 * s), hip_y - int(8 * s))],
           fill=(40, 40, 40), width=lw)

    # Standing leg
    d.line([(cx, hip_y), (cx - int(10 * s), foot_y)], fill=(40, 40, 40), width=lw)
    # Kicking leg extended
    kick_x = cx + int(40 * s)
    kick_y = hip_y - int(5 * s)
    d.line([(cx, hip_y), (kick_x, kick_y)], fill=(40, 40, 40), width=max(2, int(5 * s)))
    # Foot
    d.ellipse([kick_x - int(5 * s), kick_y - int(5 * s),
               kick_x + int(5 * s), kick_y + int(5 * s)], fill=(40, 40, 40))

    # Glow on kick
    d.line([(cx + int(15 * s), hip_y - int(2 * s)), (kick_x + int(5 * s), kick_y)],
           fill=(255, 200, 50, 180), width=max(2, int(8 * s)))
    # Impact
    for angle_deg in range(0, 360, 60):
        a = math.radians(angle_deg)
        r1, r2 = int(6 * s), int(14 * s)
        d.line([(kick_x + int(math.cos(a) * r1), kick_y + int(math.sin(a) * r1)),
                (kick_x + int(math.cos(a) * r2), kick_y + int(math.sin(a) * r2))],
               fill=(255, 255, 100, 220), width=max(1, int(3 * s)))

    img.save(f"{OUT_DIR}/{name}.png")
    print(f"  Created {name}.png")


def draw_tile(size=128, name="sprite_tile"):
    """Draw floor tiles"""
    img = Image.new("RGBA", (size, size), (210, 195, 170, 255))
    d = ImageDraw.Draw(img)
    # Grid line
    d.line([(0, 0), (size, 0)], fill=(180, 165, 140), width=1)
    d.line([(0, 0), (0, size)], fill=(180, 165, 140), width=1)
    # Subtle texture
    d.line([(size // 4, size // 4), (size // 4 + 3, size // 4 + 2)], fill=(195, 180, 155), width=1)
    d.line([(size * 3 // 4, size * 2 // 3), (size * 3 // 4 + 2, size * 2 // 3 + 1)], fill=(195, 180, 155), width=1)
    img.save(f"{OUT_DIR}/{name}.png")
    print(f"  Created {name}.png")


def draw_tile_dark(size=128, name="sprite_tile_dark"):
    """Draw darker floor tile"""
    img = Image.new("RGBA", (size, size), (200, 185, 160, 255))
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (size, 0)], fill=(180, 165, 140), width=1)
    d.line([(0, 0), (0, size)], fill=(180, 165, 140), width=1)
    img.save(f"{OUT_DIR}/{name}.png")
    print(f"  Created {name}.png")


def draw_prison(size=128, name="sprite_prison"):
    """Draw a prison/cage"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size / 128.0

    # Cage frame
    x1, y1 = int(15 * s), int(10 * s)
    x2, y2 = size - int(15 * s), size - int(10 * s)
    d.rectangle([x1, y1, x2, y2], outline=(120, 120, 120), width=max(2, int(4 * s)))

    # Bars
    bar_count = 5
    spacing = (x2 - x1) / (bar_count + 1)
    for i in range(1, bar_count + 1):
        bx = int(x1 + spacing * i)
        d.line([(bx, y1), (bx, y2)], fill=(90, 90, 90), width=max(1, int(3 * s)))

    # Human inside (small stick figure)
    hcx = size // 2
    hcy = size // 2 + int(5 * s)
    hr = int(6 * s)
    d.ellipse([hcx - hr, hcy - int(20 * s) - hr, hcx + hr, hcy - int(20 * s) + hr],
              fill=(255, 200, 150))
    d.line([(hcx, hcy - int(14 * s)), (hcx, hcy + int(5 * s))],
           fill=(255, 200, 150), width=max(1, int(2 * s)))
    # Arms up (help!)
    d.line([(hcx, hcy - int(10 * s)), (hcx - int(8 * s), hcy - int(20 * s))],
           fill=(255, 200, 150), width=max(1, int(2 * s)))
    d.line([(hcx, hcy - int(10 * s)), (hcx + int(8 * s), hcy - int(20 * s))],
           fill=(255, 200, 150), width=max(1, int(2 * s)))

    img.save(f"{OUT_DIR}/{name}.png")
    print(f"  Created {name}.png")


def draw_hit_effect(size=64, name="sprite_hit"):
    """Draw impact/hit effect"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = size // 2, size // 2
    s = size / 64.0

    # Star burst
    for angle_deg in range(0, 360, 30):
        a = math.radians(angle_deg)
        r_inner = int(5 * s)
        r_outer = int(25 * s)
        x1 = cx + int(math.cos(a) * r_inner)
        y1 = cy + int(math.sin(a) * r_inner)
        x2 = cx + int(math.cos(a) * r_outer)
        y2 = cy + int(math.sin(a) * r_outer)
        d.line([(x1, y1), (x2, y2)], fill=(255, 255, 200, 230), width=max(1, int(3 * s)))

    # Center glow
    d.ellipse([cx - int(8 * s), cy - int(8 * s), cx + int(8 * s), cy + int(8 * s)],
              fill=(255, 255, 255, 200))

    img.save(f"{OUT_DIR}/{name}.png")
    print(f"  Created {name}.png")


def draw_heart(size=48, name="sprite_heart"):
    """Draw a heart icon for HP"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size / 48.0

    # Heart shape using two circles + triangle
    r = int(10 * s)
    cx = size // 2
    top = int(14 * s)
    d.ellipse([cx - r * 2, top - r, cx, top + r], fill=(220, 40, 40))
    d.ellipse([cx, top - r, cx + r * 2, top + r], fill=(220, 40, 40))
    d.polygon([(cx - r * 2, top + int(2 * s)),
               (cx + r * 2, top + int(2 * s)),
               (cx, top + int(22 * s))], fill=(220, 40, 40))

    # Highlight
    d.ellipse([cx - int(5 * s), top - int(5 * s),
               cx - int(1 * s), top + int(1 * s)], fill=(255, 120, 120, 150))

    img.save(f"{OUT_DIR}/{name}.png")
    print(f"  Created {name}.png")


if __name__ == "__main__":
    print("Generating game sprites...")

    # Player sprites
    draw_stickman(128, "sprite_player")
    draw_stickman_punch(128, "sprite_player_punch")
    draw_stickman_kick(128, "sprite_player_kick")

    # Cat enemies
    draw_cat(128, (210, 170, 110), (230, 190, 130), (30, 30, 30), "sprite_normal_cat")
    draw_cat(128, (50, 50, 50), (30, 30, 30), (200, 50, 50), "sprite_thief_cat")
    draw_cat(148, (80, 80, 100), (60, 60, 80), (200, 0, 0), "sprite_laser_cat")

    # Boss cats
    draw_cat(160, (255, 180, 100), (255, 140, 60), (30, 30, 30), "sprite_boss_human_cat")
    draw_cat(180, (50, 20, 50), (80, 0, 0), (255, 0, 0), "sprite_boss_evil_cat")
    draw_cat(180, (100, 50, 150), (130, 60, 180), (255, 200, 50), "sprite_boss_wizard_cat")

    # Items & effects
    draw_prison(128, "sprite_prison")
    draw_hit_effect(64, "sprite_hit")
    draw_heart(48, "sprite_heart")

    # Tiles
    draw_tile(128, "sprite_tile")
    draw_tile_dark(128, "sprite_tile_dark")

    print("\nDone! All sprites saved to", OUT_DIR)
