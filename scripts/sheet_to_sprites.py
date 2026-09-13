"""
GPT(Codex)가 그려 준 스프라이트 시트를 게임에 바로 쓸 수 있는 PNG로 다듬는다.

하는 일
1. 바깥 흰 배경만 투명으로 (테두리에서 물이 번지듯 채워 나가므로, 머리 안쪽 흰색은 그대로 남는다)
2. 시트를 행×열로 잘라 프레임 순서(왼→오, 위→아래)로 정렬
3. 시트 전체의 내용 영역으로 세로를 잘라 모든 프레임의 바닥선을 맞춘다
4. 프레임을 같은 크기로 줄여 가로로 이어 붙인 스트립 PNG로 저장 (SpriteManager.loadStrip 호환)

사용법:
    python scripts/sheet_to_sprites.py <입력.png> <출력.png> --cols 3 --rows 2 --height 220
    python scripts/sheet_to_sprites.py <입력.png> <출력.png> --cols 1 --rows 1 --height 200   # 단일 스프라이트
"""

import argparse
from collections import deque

import numpy as np
from PIL import Image


def strip_background(img: Image.Image, tol: int = 38) -> Image.Image:
    """테두리에 붙어 있는 밝은 영역만 투명으로 만든다 (내부의 흰 부분은 보존)."""
    rgba = np.array(img.convert("RGBA"), dtype=np.int16)
    h, w = rgba.shape[:2]
    rgb = rgba[:, :, :3]

    # '배경 같은 밝기'인가: 흰색에 충분히 가까운 픽셀
    bright = np.all(rgb >= 255 - tol, axis=2)

    visited = np.zeros((h, w), dtype=bool)
    queue = deque()

    def push(y, x):
        if 0 <= y < h and 0 <= x < w and not visited[y, x] and bright[y, x]:
            visited[y, x] = True
            queue.append((y, x))

    for x in range(w):
        push(0, x)
        push(h - 1, x)
    for y in range(h):
        push(y, 0)
        push(y, w - 1)

    while queue:
        y, x = queue.popleft()
        push(y - 1, x)
        push(y + 1, x)
        push(y, x - 1)
        push(y, x + 1)

    rgba[:, :, 3] = np.where(visited, 0, rgba[:, :, 3])
    return Image.fromarray(rgba.astype(np.uint8), "RGBA")


def keep_largest_blob(img: Image.Image, min_alpha: int = 16) -> Image.Image:
    """가장 큰 덩어리 하나만 남긴다 (옆 칸에서 번져 들어온 조각 제거)."""
    arr = np.array(img)
    solid = arr[:, :, 3] > min_alpha
    h, w = solid.shape
    label = np.zeros((h, w), dtype=np.int32)
    sizes = [0]
    cur = 0

    for sy in range(h):
        for sx in range(w):
            if not solid[sy, sx] or label[sy, sx]:
                continue
            cur += 1
            size = 0
            queue = deque([(sy, sx)])
            label[sy, sx] = cur
            while queue:
                y, x = queue.popleft()
                size += 1
                for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1),
                               (y - 1, x - 1), (y - 1, x + 1), (y + 1, x - 1), (y + 1, x + 1)):
                    if 0 <= ny < h and 0 <= nx < w and solid[ny, nx] and not label[ny, nx]:
                        label[ny, nx] = cur
                        queue.append((ny, nx))
            sizes.append(size)

    if cur <= 1:
        return img
    biggest = int(np.argmax(sizes))
    arr[:, :, 3] = np.where(label == biggest, arr[:, :, 3], 0)
    return Image.fromarray(arr, "RGBA")


def content_rows(img: Image.Image, min_pixels: int = 3):
    """내용(불투명 픽셀)이 있는 세로 구간을 찾는다."""
    alpha = np.array(img)[:, :, 3]
    rows = np.where((alpha > 16).sum(axis=1) >= min_pixels)[0]
    if len(rows) == 0:
        return 0, img.height
    return int(rows[0]), int(rows[-1]) + 1


def content_cols(img: Image.Image, min_pixels: int = 3):
    """내용(불투명 픽셀)이 있는 가로 구간을 찾는다."""
    alpha = np.array(img)[:, :, 3]
    cols = np.where((alpha > 16).sum(axis=0) >= min_pixels)[0]
    if len(cols) == 0:
        return 0, img.width
    return int(cols[0]), int(cols[-1]) + 1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("dst")
    ap.add_argument("--cols", type=int, default=1)
    ap.add_argument("--rows", type=int, default=1)
    ap.add_argument("--frames", type=int, default=0, help="실제로 쓸 프레임 수 (기본: cols*rows)")
    ap.add_argument("--height", type=int, default=220, help="프레임 세로 픽셀")
    ap.add_argument("--tol", type=int, default=38)
    ap.add_argument("--per-cell-crop", action="store_true",
                    help="칸마다 따로 잘라 내용에 딱 맞춘다 (단일 스프라이트용)")
    args = ap.parse_args()

    sheet = strip_background(Image.open(args.src), args.tol)

    cw = sheet.width // args.cols
    ch = sheet.height // args.rows
    n = args.frames or (args.cols * args.rows)

    cells = []
    for r in range(args.rows):
        for c in range(args.cols):
            if len(cells) >= n:
                break
            cells.append(sheet.crop((c * cw, r * ch, (c + 1) * cw, (r + 1) * ch)))

    if args.per_cell_crop:
        cells = [cell.crop(cell.getbbox() or (0, 0, cell.width, cell.height)) for cell in cells]
    else:
        # 시트 전체 기준으로 위아래를 잘라 모든 프레임의 바닥선을 맞춘다
        tops, bots = zip(*(content_rows(cell) for cell in cells))
        top, bot = min(tops), max(bots)
        pad = max(2, (bot - top) // 40)
        top = max(0, top - pad)
        bot = min(ch, bot + pad)
        cells = [cell.crop((0, top, cell.width, bot)) for cell in cells]

    scale = args.height / max(cell.height for cell in cells)
    frame_w = max(int(round(cell.width * scale)) for cell in cells)
    frame_h = args.height

    out = Image.new("RGBA", (frame_w * len(cells), frame_h), (0, 0, 0, 0))
    for i, cell in enumerate(cells):
        w = max(1, int(round(cell.width * scale)))
        h = max(1, int(round(cell.height * scale)))
        resized = cell.resize((w, h), Image.LANCZOS)
        # 가로 가운데, 세로는 아래(발바닥) 기준으로 붙인다
        out.paste(resized, (i * frame_w + (frame_w - w) // 2, frame_h - h), resized)

    out.save(args.dst)
    print(f"{args.dst}  {out.width}x{out.height}  frames={len(cells)}")


if __name__ == "__main__":
    main()
