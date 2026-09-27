"""스토리 그림 3장 — 대표님이 찍어 보낸 사진(Plan/story/story1~3.jpg)에서 종이만 반듯하게 펴고 밝기를 고르게 한다 (2026-09-27).
「흐리다·옆이 비었다」(2026-09-27): 두 배 크기로 펴고 잡티를 누른 뒤 선을 또렷하게 하고,
가로 화면 양옆을 채울 배경(같은 그림을 화면 비율로 잘라 흐리게)도 만든다.

결과: app/src/main/res/drawable-nodpi/story_1~3.jpg (본 그림), story_bg_1~3.jpg (양옆 배경)
사용법: python scripts/make_story_pages.py
"""
import numpy as np
from PIL import Image, ImageEnhance, ImageFilter

DST = 'app/src/main/res/drawable-nodpi'
W, H = 1008, 1400          # 종이 비율 그대로, 원본 사진 속 종이(약 500×700)의 두 배

# 사진 속 종이 네 모서리 (왼위·오른위·오른아래·왼아래) — 사진마다 손으로 찍은 값
CORNERS = {
    1: [(524, 22), (1026, 36), (1021, 719), (531, 719)],
    2: [(450, -10), (862, 76), (944, 719), (458, 719)],
    3: [(492, 38), (1002, 132), (960, 719), (446, 719)],
}


def perspective_coeffs(dst, src):
    a, b = [], []
    for (x, y), (X, Y) in zip(dst, src):
        a.append([x, y, 1, 0, 0, 0, -X * x, -X * y]); b.append(X)
        a.append([0, 0, 0, x, y, 1, -Y * x, -Y * y]); b.append(Y)
    return np.linalg.solve(np.array(a, float), np.array(b, float))


def page(i):
    src = Image.open(f'Plan/story/story{i}.jpg').convert('RGB')
    # 사진을 먼저 두 배로 키운 뒤 펴야 계단 없이 부드럽다
    big = src.resize((src.width * 2, src.height * 2), Image.LANCZOS)
    c = [(x * 2, y * 2) for x, y in CORNERS[i]]
    im = big.transform((W, H), Image.PERSPECTIVE, perspective_coeffs([(0, 0), (W, 0), (W, H), (0, H)], c), Image.BICUBIC)
    a = np.asarray(im).astype(float)
    outside = a.sum(axis=2) < 30                       # 사진 밖(검은 빈칸)
    # 조명 얼룩 지우기: 밝은 종이 바탕을 크게 흐려 나누면 종이는 고르게 하얘진다
    g = np.asarray(im.convert('L').filter(ImageFilter.MaxFilter(21)).filter(ImageFilter.GaussianBlur(70))).astype(float)
    flat = np.clip(a / np.maximum(g, 60)[..., None] * 246, 0, 255)
    flat[outside] = 246
    out = Image.fromarray(flat.astype(np.uint8))
    out = out.filter(ImageFilter.MedianFilter(3))                          # 사진 잡티
    out = out.filter(ImageFilter.UnsharpMask(radius=3, percent=170, threshold=2))   # 연필·크레파스 선을 또렷하게
    out = ImageEnhance.Color(out).enhance(1.3)
    out = ImageEnhance.Contrast(out).enhance(1.12)
    return out


def background(pg):
    # 화면(가로 약 2.2:1) 비율로 그림 가운데를 잘라 크게 흐리고 살짝 어둡게 — 양옆을 같은 그림으로 채운다
    ratio = 2.2
    ch = int(pg.width / ratio)
    top = (pg.height - ch) // 2
    bg = pg.crop((0, top, pg.width, top + ch)).resize((720, int(720 / ratio)), Image.LANCZOS)
    bg = bg.filter(ImageFilter.GaussianBlur(14))
    return ImageEnhance.Brightness(bg).enhance(0.72)


if __name__ == '__main__':
    for i in CORNERS:
        pg = page(i)
        pg.save(f'{DST}/story_{i}.jpg', quality=92)
        background(pg).save(f'{DST}/story_bg_{i}.jpg', quality=85)
        print(f'story_{i}.jpg', pg.size)
