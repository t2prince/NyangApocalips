"""
`Cats/` 원본 손그림 사진에서 잉크 선(외곽선)만 가져오고, 그 안은 균일한 카툰 단색으로 채운다.
선을 새로 그리지 않는다 — 원본 잉크 선을 추출해서 그 위에 색만 입힌다.

핵심 원칙: 원본의 색연필 농담(진하기)은 결과에 절대 반영하지 않는다. 손그림은 프레임마다
칠한 밀도가 다르고 사진 조명도 균일하지 않아서, 그 농담을 픽셀 단위로 따라가면 같은 부위인데도
프레임마다 색이 들쭉날쭉해져 애니메이션이 깜빡인다. 그래서 잉크선으로 닫힌 영역은 무조건
하나의 단색으로 채운다 — 콧볼처럼 흰색이어야 하는 영역은 좌표를 지정(--accent)해서 덮어씌운다.

절차:
  1. 배경 평탄화 (채널별로 큰 블러로 나눠서 조명 얼룩 제거)
  2. 어두운 정도로 잉크 선만 추출 (연필 채색은 잉크보다 밝다)
  3. 벽을 굵게 닫아(wall-close-radius) 살짝 끊긴 선으로 flood-fill이 새지 않게 막는다
  4. 크롭 경계에 닿는 '바깥'을 배경(투명)으로, 나머지 닫힌 영역 전체를 하나의
     body_color 단색으로 채운다 (원본 농담 무시)
  5. --accent로 지정한 영역(콧볼=흰색, 고글 등)을 덮어씌운다

사용법: python scripts/lineart_cat.py <소스이미지> <x0,y0,x1,y1> <출력.png> \
    --body 243,190,66 [--ink-thresh 95] --accent x0,y0,x1,y1,255,255,255
"""

import argparse
from collections import deque

import numpy as np
from PIL import Image, ImageFilter

OUTLINE = (22, 18, 16)


def flatten_channels(rgb: np.ndarray, radius: int) -> np.ndarray:
    """채널별로 큰 블러(=배경 밝기 추정)로 나눠서 조명 기울기를 없앤다. 색상은 유지."""
    arr = rgb.astype(np.float32)
    out = np.zeros_like(arr)
    for c in range(3):
        ch = Image.fromarray(arr[:, :, c].astype(np.uint8))
        bg = np.array(ch.filter(ImageFilter.GaussianBlur(radius)), dtype=np.float32)
        bg = np.maximum(bg, 1)
        out[:, :, c] = np.clip(arr[:, :, c] / bg * 235.0, 0, 255)
    return out.astype(np.uint8)


def close_mask(mask: np.ndarray, radius: int) -> np.ndarray:
    if radius <= 0:
        return mask
    im = Image.fromarray((mask * 255).astype(np.uint8))
    im = im.filter(ImageFilter.MaxFilter(radius * 2 + 1))
    return np.array(im) > 127


def label_components(mask: np.ndarray):
    """4-연결 BFS. 반환: labels(int32 배열, 0=속하지 않음), {id: area}"""
    h, w = mask.shape
    labels = np.zeros((h, w), dtype=np.int32)
    areas = {}
    cur = 0
    for sy in range(h):
        row = mask[sy]
        for sx in range(w):
            if not row[sx] or labels[sy, sx]:
                continue
            cur += 1
            q = deque([(sy, sx)])
            labels[sy, sx] = cur
            area = 0
            while q:
                y, x = q.popleft()
                area += 1
                for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not labels[ny, nx]:
                        labels[ny, nx] = cur
                        q.append((ny, nx))
            areas[cur] = area
    return labels, areas


def extract(src_path, box, out_path, body_color,
            ink_thresh=95, close_radius=1, wall_close_radius=5, bg_blur=41,
            pad=30, belly_y=None, accents=None, auto_white=True):
    """
    원본 색칠 밀도(농담)를 절대 결과에 반영하지 않는다 — 프레임마다 아이가 칠한 진하기가
    다르고 사진 조명도 달라서, 그걸 픽셀 단위로 따라가면 같은 부위인데도 프레임마다 색이
    들쭉날쭉해져 애니메이션이 깜빡인다. 그래서 원본에서는 **잉크선(외곽선)만** 가져오고,
    선으로 닫힌 영역은 무조건 **하나의 균일한 단색**으로 채운다 — 그 안의 연필 농담·번짐은
    전부 무시한다. 흰색이어야 하는 영역(콧볼 등)은 좌표를 지정해서(accents) 덮어씌운다.

    절차:
      1. 배경 평탄화 (채널별 큰 블러로 조명 기울기 제거) 후 밝기로 잉크선만 추출.
      2. 벽을 굵게 닫아(wall_close_radius) 살짝 끊긴 선(사진 노출 얼룩 등)으로
         flood-fill이 새지 않게 한다. 렌더링용 얇은 선(close_radius)은 따로 유지.
      3. 크롭 경계에 닿는 '바깥'을 flood-fill로 찾아 배경(투명)으로, 나머지 닫힌 영역
         전체를 body_color 단색으로 채운다.
      4. 배경을 wall_close_radius만큼 다시 부풀려서(3단계에서 두껍게 닫은 벽 때문에
         생긴 틈을 되돌린다) 외곽선 바로 바깥에 흰/색 테두리(halo)가 남지 않게 한다.
      5. accents로 지정한 영역(콧볼=흰색, 고글 등)을 덮어씌운다.

    belly_y: 다리는 몸통 밑에서 서로 떨어진 얇은 선 4개라 몸통 속과 바깥이 다리 사이 틈으로
    이어져 버린다(토폴로지 누수). belly_y(크롭 로컬 y)에 얇은 가로줄 하나를 벽으로 세워
    위쪽 몸통 안쪽과 차단하고, 그 아래는 자기들끼리 정상적으로 flood-fill되어
    다리 옆 빈 공간은 배경으로, 다리 자체는 잉크선으로만 남는다.
    """
    img = Image.open(src_path).convert("RGB")
    x0, y0, x1, y1 = box
    x0, y0 = max(0, x0 - pad), max(0, y0 - pad)
    x1, y1 = min(img.width, x1 + pad), min(img.height, y1 + pad)
    crop = img.crop((x0, y0, x1, y1))

    flat = flatten_channels(np.array(crop), bg_blur)
    gray_flat = flat.mean(axis=2)
    ink_thin = gray_flat < ink_thresh
    ink_closed = close_mask(ink_thin, close_radius)
    ink_wall = close_mask(ink_thin, wall_close_radius)

    h, w = ink_closed.shape
    fill_wall = ink_wall.copy()
    if belly_y is not None:
        wt = max(6, wall_close_radius)
        fill_wall[belly_y:min(h, belly_y + wt), :] = True

    # '바깥(배경)이냐 안쪽(몸)이냐'를 굵게 닫은 벽으로 가른다 — 살짝 끊긴 외곽선으로
    # 새지 않게 하기 위해서다.
    not_ink = ~fill_wall
    labels, areas = label_components(not_ink)
    border_ids = set(labels[0, :]) | set(labels[-1, :]) | set(labels[:, 0]) | set(labels[:, -1])
    border_ids.discard(0)
    background = np.zeros((h, w), dtype=bool)
    for cid, area in areas.items():
        if cid in border_ids:
            background |= labels == cid

    # 굵게 닫은 벽 때문에 배경이 실제 외곽선보다 wall_close_radius만큼 안쪽에서 멈춰
    # 있다 — 그대로 두면 외곽선 바로 바깥에 배경도 몸통도 아닌 '미분류' 띠가 남아
    # 흰/색 테두리(halo)로 보인다. 배경을 다시 부풀려서 실제 외곽선까지 되돌린다.
    background_img = Image.fromarray((background * 255).astype(np.uint8))
    bg_radius = max(wall_close_radius, close_radius)
    background = np.array(background_img.filter(ImageFilter.MaxFilter(bg_radius * 2 + 1))) > 127
    background &= ~ink_closed

    interior = (~background) & (~ink_closed)

    # 몸통 안쪽에 잉크선으로 완전히 둘러싸인 작은 구멍(콧볼 두 원 등)은 interior를
    # 연결성분으로 나누면 몸통 본체와 별도 성분으로 자동으로 갈린다(원 둘레가 잉크선으로
    # 완전히 닫혀 있어서). 가장 큰 성분 = 몸통, 나머지 작은 성분은 기본적으로 흰색으로
    # 칠한다 — 좌표를 일일이 안 잡아도 콧볼이 자동으로 흰색이 된다. 특별한 색(고글 등)이
    # 필요하면 accents로 덮어쓰면 된다.
    out = np.zeros((h, w, 4), dtype=np.uint8)
    ilabels, iareas = label_components(interior)
    if not auto_white:
        out[interior] = (*body_color, 255)
    elif iareas:
        main_iid = max(iareas, key=iareas.get)
        main_area = iareas[main_iid]
        main_xs = np.nonzero(ilabels == main_iid)[1]
        face_x0 = main_xs.min() + (main_xs.max() - main_xs.min()) * 0.45  # 얼굴은 몸통 오른쪽 절반
        for cid, area in iareas.items():
            comp = ilabels == cid
            is_muzzle_size = 0.01 * main_area <= area <= 0.12 * main_area
            cxs = np.nonzero(comp)[1]
            is_on_face_side = cid != main_iid and len(cxs) and cxs.mean() >= face_x0
            if cid == main_iid or not (is_muzzle_size and is_on_face_side):
                out[comp] = (*body_color, 255)
            else:
                # 콧볼 정도 크기이면서 얼굴 쪽(오른쪽)에 있는 구멍만 흰색으로 자동 처리한다.
                # 왼쪽(귀·꼬리 쪽)에 우연히 생긴 비슷한 크기의 구멍은 몸통색으로 둔다.
                out[comp] = (255, 255, 255, 255)

    if accents:
        # 특정 영역(콧볼 흰색, 고글 등)은 지정색으로 덮어쓴다. 좌표는 원본 이미지 기준
        # (x0,y0,x1,y1,r,g,b).
        for ax0, ay0, ax1, ay1, ar, ag, ab in accents:
            lx0, ly0 = ax0 - x0, ay0 - y0
            lx1, ly1 = ax1 - x0, ay1 - y0
            lx0, ly0 = max(0, lx0), max(0, ly0)
            lx1, ly1 = min(w, lx1), min(h, ly1)
            region_interior = interior[ly0:ly1, lx0:lx1]
            sub = out[ly0:ly1, lx0:lx1]
            sub[region_interior] = (ar, ag, ab, 255)

    out[ink_closed] = (*OUTLINE, 255)

    # 캔버스 여백/부유 잉크 조각 제거: 알파>0 픽셀 중 몸통 본체(가장 큰 덩어리)와 8-연결로
    # 안 이어진 작은 조각(옆 그림에서 잘려 들어온 글자·선)을 지우고, 본체에 딱 맞게 자른다.
    out = _clean_and_crop(out)

    Image.fromarray(out, "RGBA").save(out_path)
    print(f"{out_path}  {out.shape[1]}x{out.shape[0]}  "
          f"(crop from {src_path} box=({x0},{y0})-({x1},{y1}))")


def _clean_and_crop(out: np.ndarray, near_radius: int = 4, min_area_frac: float = 0.004) -> np.ndarray:
    """알파>0 픽셀을 8-연결로 묶어 가장 큰 덩어리(몸통 본체)만 남기되, 본체 '모양'을
    near_radius만큼 부풀린 범위에 실제로 닿으면서 어느 정도 크기가 있는 조각(다리·수염 끝처럼
    본체와 살짝 떨어졌을 수 있는 것)만 살려준다. bbox가 아니라 본체 모양 자체를 기준으로
    거리를 재야 한다 — 수염·귀 때문에 본체 bbox가 캔버스 전체만큼 넓어서 bbox 기준으로는
    아무 조각도 못 거른다. '크면 무조건 살린다' 같은 예외는 두지 않는다 — 옆 그림에서 잘려
    들어온 글자 획도 크기가 꽤 나갈 수 있어서, 그런 예외를 두면 못 걸러진다.
    본체와 안 닿았거나 너무 작은 조각은 전부 '부유 쓰레기'로 지운다.
    마지막에 남은 내용의 bbox로 딱 맞게 자른다."""
    h, w = out.shape[:2]
    solid = out[:, :, 3] > 10
    labels, areas = label_components_8(solid)
    if not areas:
        return out
    main_id = max(areas, key=areas.get)
    min_area = max(15, int(areas[main_id] * min_area_frac))
    main_mask = labels == main_id
    main_img = Image.fromarray((main_mask * 255).astype(np.uint8))
    near_main_mask = np.array(main_img.filter(ImageFilter.MaxFilter(near_radius * 2 + 1))) > 127

    keep = np.zeros((h, w), dtype=bool)
    for cid, area in areas.items():
        comp = labels == cid
        if cid == main_id or (area >= min_area and (comp & near_main_mask).any()):
            keep[comp] = True
    out = out.copy()
    out[~keep] = 0

    ys, xs = np.nonzero(out[:, :, 3] > 10)
    if len(ys) == 0:
        return out
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    return out[y0:y1, x0:x1]


def label_components_8(mask: np.ndarray):
    """8-연결 BFS (부유 조각 판정용 — 대각선으로 스치는 것도 '이어짐'으로 본다)."""
    h, w = mask.shape
    labels = np.zeros((h, w), dtype=np.int32)
    areas = {}
    cur = 0
    for sy in range(h):
        row = mask[sy]
        for sx in range(w):
            if not row[sx] or labels[sy, sx]:
                continue
            cur += 1
            q = deque([(sy, sx)])
            labels[sy, sx] = cur
            area = 0
            while q:
                y, x = q.popleft()
                area += 1
                for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1),
                               (y - 1, x - 1), (y - 1, x + 1), (y + 1, x - 1), (y + 1, x + 1)):
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not labels[ny, nx]:
                        labels[ny, nx] = cur
                        q.append((ny, nx))
            areas[cur] = area
    return labels, areas


def parse_color(s):
    return tuple(int(v) for v in s.split(","))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("box", help="x0,y0,x1,y1")
    ap.add_argument("out")
    ap.add_argument("--body", type=parse_color, required=True)
    ap.add_argument("--ink-thresh", type=int, default=95)
    ap.add_argument("--wall-close-radius", type=int, default=5)
    ap.add_argument("--close-radius", type=int, default=1)
    ap.add_argument("--pad", type=int, default=30)
    ap.add_argument("--belly-y", type=int, default=None,
                     help="원본 이미지 좌표 기준 배 밑선 y (다리 시작점). 생략하면 다리 영역 없음으로 간주.")
    ap.add_argument("--accent", action="append", default=[],
                     help="x0,y0,x1,y1,r,g,b (원본 좌표) — 그 영역을 지정색으로 덮는다 "
                          "(콧볼=흰색 255,255,255 등). 여러 번 줄 수 있다.")
    ap.add_argument("--no-auto-white", action="store_true",
                     help="콧볼 자동 흰색 감지를 끄고 몸통 단색으로만 채운다 (자동 감지가 오작동할 때 --accent와 함께 사용).")
    args = ap.parse_args()
    box = tuple(int(v) for v in args.box.split(","))
    belly_local = None
    if args.belly_y is not None:
        belly_local = args.belly_y - (max(0, box[1] - args.pad))
    accents = [tuple(int(v) for v in a.split(",")) for a in args.accent]
    extract(args.src, box, args.out, args.body,
            ink_thresh=args.ink_thresh, wall_close_radius=args.wall_close_radius,
            accents=accents, auto_white=not args.no_auto_white,
            close_radius=args.close_radius, pad=args.pad, belly_y=belly_local)
