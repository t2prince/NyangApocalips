"""
`Cats/` 원본 손그림 사진에서 각 그림(냥이 한 마리, 프레임 하나)의 위치를 찾아
번호를 매긴 디버그 이미지를 저장한다. 이 스크립트는 "위치 찾기" 전용이다 —
번호를 실제로 보고 고르는 건 사람(나)이 debug 이미지를 열어서 판단한다.

사용법: python scripts/extract_frames.py <이미지경로> [--min-area 3000] [--dilate 9] [--scale 4]
출력: Plan/_debug/<파일명>_labeled.png (번호 매긴 박스), 콘솔에 번호별 bbox 목록
"""

import argparse
import os
import sys
from collections import deque

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

DEBUG_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "Plan/_debug")
os.makedirs(DEBUG_DIR, exist_ok=True)


def flatten_background(gray: np.ndarray, blur_radius: int) -> np.ndarray:
    """조명 얼룩을 없앤다: 큰 블러로 배경 밝기를 추정하고 나눠서 평탄화한다."""
    im = Image.fromarray(gray.astype(np.uint8))
    bg = np.array(im.filter(ImageFilter.GaussianBlur(blur_radius)), dtype=np.float32)
    bg = np.maximum(bg, 1)
    flat = gray.astype(np.float32) / bg * 235.0
    return np.clip(flat, 0, 255).astype(np.uint8)


def content_mask(rgb: np.ndarray, blur_radius=41, thresh=210) -> np.ndarray:
    """흰 종이가 아닌 모든 것(잉크선+색연필+글자) = True."""
    gray = rgb.mean(axis=2)
    flat = flatten_background(gray, blur_radius)
    return flat < thresh


def dilate(mask: np.ndarray, radius: int) -> np.ndarray:
    im = Image.fromarray((mask * 255).astype(np.uint8))
    im = im.filter(ImageFilter.MaxFilter(radius * 2 + 1))
    return np.array(im) > 127


def label_components(mask: np.ndarray):
    """BFS로 연결 성분을 찾는다 (scipy 없이). 반환: [(id, y0,x0,y1,x1, area), ...]"""
    h, w = mask.shape
    labels = np.zeros((h, w), dtype=np.int32)
    comps = []
    cur = 0
    visited = np.zeros((h, w), dtype=bool)
    for sy in range(h):
        row = mask[sy]
        for sx in range(w):
            if not row[sx] or visited[sy, sx]:
                continue
            cur += 1
            q = deque([(sy, sx)])
            visited[sy, sx] = True
            y0 = y1 = sy
            x0 = x1 = sx
            area = 0
            while q:
                y, x = q.popleft()
                area += 1
                y0, y1 = min(y0, y), max(y1, y)
                x0, x1 = min(x0, x), max(x1, x)
                for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not visited[ny, nx]:
                        visited[ny, nx] = True
                        q.append((ny, nx))
            comps.append((cur, y0, x0, y1, x1, area))
    return comps


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image")
    ap.add_argument("--min-area", type=int, default=1500)
    ap.add_argument("--dilate", type=int, default=6)
    ap.add_argument("--scale", type=int, default=4, help="다운스케일 배수 (속도용)")
    ap.add_argument("--thresh", type=int, default=210)
    args = ap.parse_args()

    img = Image.open(args.image).convert("RGB")
    small = img.resize((img.width // args.scale, img.height // args.scale), Image.LANCZOS)
    arr = np.array(small)
    mask = content_mask(arr, blur_radius=max(15, 121 // args.scale), thresh=args.thresh)
    mask = dilate(mask, args.dilate)

    comps = label_components(mask)
    comps = [c for c in comps if c[5] >= args.min_area]
    comps.sort(key=lambda c: (c[1], c[2]))  # 위->아래, 왼쪽->오른쪽 대략 순

    debug = img.copy()
    d = ImageDraw.Draw(debug)
    print(f"{args.image}  (scale={args.scale}x 축소본 기준 좌표, 실제 좌표 = *{args.scale})")
    for i, (cid, y0, x0, y1, x1, area) in enumerate(comps):
        Y0, X0, Y1, X1 = y0 * args.scale, x0 * args.scale, y1 * args.scale, x1 * args.scale
        d.rectangle([X0, Y0, X1, Y1], outline=(255, 0, 0), width=4)
        d.text((X0 + 4, max(0, Y0 - 28)), str(i), fill=(255, 0, 0))
        print(f"  [{i}] bbox=({X0},{Y0})-({X1},{Y1})  size={X1-X0}x{Y1-Y0}  area={area}")

    name = os.path.splitext(os.path.basename(args.image))[0]
    out = os.path.join(DEBUG_DIR, f"{name}_labeled.png")
    debug.save(out)
    print(f"저장: {out}")


if __name__ == "__main__":
    main()
