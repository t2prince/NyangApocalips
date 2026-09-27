"""꿀 스테이지(21) 바닥 — 기획10 「밑에 벌집」: 바닥이 벌집(육각형 칸)이고 위로 꿀이 흘러내린다 (대표님 2026-09-27).
grass_ground.png 와 같은 크기(512×232)·같은 구성(위 42px 표면 + 아래 흙)으로, 가로로 이어 붙여도 이음새가 없게 만든다.
결과: app/src/main/res/drawable-nodpi/honey_ground.png
사용법: python scripts/make_honey_ground.py
"""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 512, 232
SS = 3
TOP = 44                                 # 표면(꿀) 두께 — 잔디 표면과 같게
COLS = 16                                # 가로 칸 수 (512 을 나누어떨어지게)


def main():
    im = Image.new('RGBA', (W * SS, H * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    sp = W / COLS                                   # 칸 가로 간격
    r = sp / math.sqrt(3)                           # 뾰족한 위쪽 육각형 반지름
    rowh = 1.5 * r
    # 벌집 바탕 (진한 호박색)
    d.rectangle([0, (TOP - 22) * SS, W * SS, H * SS], fill=(176, 104, 24, 255))
    rows = int((H - TOP) / rowh) + 3
    for row in range(-1, rows):
        cy = TOP + 6 + row * rowh
        off = (sp / 2) if row % 2 else 0
        for col in range(-1, COLS + 2):
            cx = col * sp + off
            pts = [(cx + r * 0.86 * math.cos(math.radians(60 * k - 30)), cy + r * 0.86 * math.sin(math.radians(60 * k - 30))) for k in range(6)]
            seed = (row * 31 + (col % COLS) * 17) % 7
            fill = (250, 190, 50, 255) if seed in (0, 3) else (236, 160, 40, 255) if seed in (1, 5) else (214, 132, 30, 255)
            d.polygon([(x * SS, y * SS) for x, y in pts], fill=fill, outline=(120, 64, 14, 255), width=SS * 2)
            # 꿀이 찬 칸은 반짝
            if seed in (0, 3):
                d.ellipse([(cx - r * 0.35) * SS, (cy - r * 0.45) * SS, (cx - r * 0.05) * SS, (cy - r * 0.2) * SS], fill=(255, 236, 150, 220))
    # 위 표면: 흘러내리는 꿀 (가로로 이어지게 사인 조합 — 주기가 W 로 나누어떨어진다)
    xs = np.arange(0, W + 1, 2)
    base = TOP - 10
    drip = lambda x: base + 5 * math.sin(2 * math.pi * x / W * 4) + 3 * math.sin(2 * math.pi * x / W * 9 + 1.0)
    poly = [(0, 0)] + [(x, drip(x)) for x in xs] + [(W, 0)]
    # 꿀 표면 (위 가장자리는 반투명 광택)
    top_poly = [(0, base - 16)] + [(x, drip(x)) for x in xs] + [(W, base - 16)]
    d.polygon([(x * SS, y * SS) for x, y in top_poly], fill=(248, 178, 40, 255))
    # 방울져 내려오는 꿀 줄기 (같은 자리 반복)
    for k in range(6):
        x = (k * 89 + 30) % W
        L = 14 + (k * 7) % 16
        y0 = drip(x)
        for dx in (0, W, -W):
            d.rounded_rectangle([(x + dx - 6) * SS, (y0 - 4) * SS, (x + dx + 6) * SS, (y0 + L) * SS], radius=6 * SS, fill=(248, 178, 40, 255))
            d.ellipse([(x + dx - 8) * SS, (y0 + L - 8) * SS, (x + dx + 8) * SS, (y0 + L + 8) * SS], fill=(248, 178, 40, 255))
    # 광택
    d.line([(x * SS, (drip(x) - 9) * SS) for x in xs], fill=(255, 232, 150, 255), width=SS * 3)
    im = im.resize((W, H), Image.LANCZOS)
    a = np.array(im); a[: base - 16, :, 3] = 0; im = Image.fromarray(a)
    im.save('app/src/main/res/drawable-nodpi/honey_ground.png')
    print('honey_ground.png', im.size)


if __name__ == '__main__':
    main()
