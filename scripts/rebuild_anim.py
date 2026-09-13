"""
냥이 애니메이션 스트립 재생성 — 깨끗하게, 일관되게, 지금의 70% 크기로.

문제(사용자 피드백):
  - 프레임마다 바라보는 방향이 뒤바뀌고, 얼굴이 뭉개진 낙서 프레임이 섞여 있어 지저분함
  - 프레임 간 몸집·색이 들쭉날쭉 (일관성 부족)
  - 몸통 줄무늬는 원안처럼 '갈색'이어야 함

해결:
  - Plan/cat_frames4 의 8칸 시트에서 깨끗하고 방향이 맞는 칸만 골라 왕복(핑퐁) 배치
  - 프레임 정리: 바깥 색 번짐 제거, 잡티 제거, 색을 고정 팔레트로 스냅
  - 몸 안쪽의 길쭉한 검은 획(줄무늬)은 진한 갈색으로 (눈 점은 검정 유지)
  - 기준 몸높이 118px -> 83px (70%)

산출: app/src/main/res/drawable-nodpi/anim_*.png (7프레임 가로 스트립)
      + cat_normal/cat_laser/cat_ally 정적 스프라이트도 같은 정리 적용

사용법: python scripts/rebuild_anim.py
"""

import os
import sys
from collections import deque

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sheet_to_sprites import strip_background, keep_largest_blob  # noqa: E402

# GPT 생성 시트 모드(인자로 폴더 지정)에서는 칸 경계를 넘어온 이웃 그림 조각을
# 지우기 위해 '가장 큰 덩어리만 남기기'를 켠다
USE_LARGEST_BLOB = False

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIR = os.path.join(ROOT, "Plan/cat_frames4")
DST_DIR = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
STATIC_SRC = os.path.join(ROOT, "Plan/cat_sprites5")

COLS, ROWS = 4, 2
CELL_W, CELL_H = 420, 380

# 기준 자세(1번 칸) 몸높이를 이 값으로 (기존 118의 70%)
STAND_HEIGHT = 83

INK = (25, 21, 19)
BODY = (242, 192, 66)
WHITE = (255, 255, 255)
TEAL = (74, 170, 82)      # 레이저냥이 고글·광선의 초록
STRIPE = (126, 82, 34)    # 몸통 줄무늬: 진한 갈색 (원안 색연필)

# 시트별로 쓸 칸 번호(1=기준 칸, 2~8=동작). 지저분하거나 방향이 다른 칸은 뺐다.
SEQUENCES = {
    "normal_idle":   [3, 5, 7, 5, 3, 5, 7],        # 정면 보고 손 흔드는 대기
    "normal_walk":   [2, 3, 5, 6, 7, 6, 5],        # 4·8번 칸은 얼굴이 뭉개져 제외
    "normal_jump":   [2, 3, 4, 5, 6, 7, 8],
    "normal_attack": [5, 7, 4, 8, 6, 8, 4],        # 웅크림 -> 돌진 -> 화난 얼굴
    "laser_idle":    [2, 3, 5, 6, 7, 5, 3],        # 4·8번 칸은 왼쪽을 봐서 제외
    "laser_walk":    [2, 3, 4, 5, 6, 7, 8],
    "laser_jump":    [2, 3, 4, 5, 6, 7, 8],
    "laser_attack":  [2, 3, 4, 5, 6, 7, 8],
}
OUT_NAMES = {
    "normal_idle": "anim_cat_idle", "normal_walk": "anim_cat_walk",
    "normal_jump": "anim_cat_jump", "normal_attack": "anim_cat_attack",
    "laser_idle": "anim_laser_idle", "laser_walk": "anim_laser_walk",
    "laser_jump": "anim_laser_jump", "laser_attack": "anim_laser_attack",
}


def label_components(mask):
    """4-연결 BFS. 반환: [(area, ys, xs)]"""
    h, w = mask.shape
    labels = np.zeros((h, w), dtype=np.int32)
    comps = []
    cur = 0
    for sy in range(h):
        for sx in range(w):
            if not mask[sy, sx] or labels[sy, sx]:
                continue
            cur += 1
            q = deque([(sy, sx)])
            labels[sy, sx] = cur
            ys, xs = [sy], [sx]
            while q:
                y, x = q.popleft()
                for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not labels[ny, nx]:
                        labels[ny, nx] = cur
                        q.append((ny, nx))
                        ys.append(ny); xs.append(nx)
            comps.append((len(ys), np.array(ys), np.array(xs)))
    return comps


def clean_frame(im, outline_min_area, stripe_min, noise_max, has_teal):
    """한 프레임 정리: 색 스냅 + 바깥 번짐 제거 + 줄무늬 갈색화 + 잡티 제거."""
    arr = np.array(im.convert("RGBA"), dtype=np.int16)
    h, w = arr.shape[:2]
    r, g, b, a = arr[..., 0], arr[..., 1], arr[..., 2], arr[..., 3]
    opaque = a > 128
    lum = (r + g + b) / 3

    # 초록(고글·광선)은 밝기와 상관없이 먼저 골라낸다 — 어두운 초록이 잉크로 오인되지 않게
    teal = opaque & (g > r + 25) & (g > b + 25) if has_teal else np.zeros_like(opaque)
    ink = opaque & ~teal & (lum < 100)
    white = opaque & ~teal & ~ink & (np.minimum(np.minimum(r, g), b) > 185)
    body = opaque & ~ink & ~white & ~teal

    # 1) 바깥 색 번짐 제거: 잉크 벽을 넘지 않고 프레임 바깥에서 닿을 수 있는 색 픽셀은 지운다
    passable = ~ink
    reach = np.zeros((h, w), dtype=bool)
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            if passable[y, x] and not reach[y, x]:
                reach[y, x] = True; q.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if passable[y, x] and not reach[y, x]:
                reach[y, x] = True; q.append((y, x))
    while q:
        y, x = q.popleft()
        for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            if 0 <= ny < h and 0 <= nx < w and passable[ny, nx] and not reach[ny, nx]:
                reach[ny, nx] = True
                q.append((ny, nx))
    fringe = reach & (body | white | teal)
    colored_total = int((body | white | teal).sum())
    if colored_total and fringe.sum() < colored_total * 0.3:   # 외곽선이 새는 프레임은 건너뜀
        a[fringe] = 0
        body &= ~fringe; white &= ~fringe; teal &= ~fringe
        opaque &= a > 128

    # 2) 몸 안쪽 검은 획 분류: 길쭉하면 줄무늬(갈색), 작고 둥글면 눈(검정), 티끌은 몸색
    stripes = np.zeros((h, w), dtype=bool)
    dust = np.zeros((h, w), dtype=bool)
    for area, ys, xs in label_components(ink):
        if area >= outline_min_area:
            continue                     # 외곽선·다리·수염 덩어리는 그대로
        bh = ys.max() - ys.min() + 1
        bw = xs.max() - xs.min() + 1
        elong = max(bh, bw) / max(1, min(bh, bw))
        if area <= noise_max:
            dust[ys, xs] = True
        elif area >= stripe_min and elong >= 1.6:
            stripes[ys, xs] = True

    # 3) 색 스냅 (프레임 간 색 흔들림 제거)
    out = arr.copy()
    for mask, color in ((ink, INK), (white, WHITE), (teal, TEAL), (body, BODY)):
        out[mask, 0], out[mask, 1], out[mask, 2] = color
    out[stripes, 0], out[stripes, 1], out[stripes, 2] = STRIPE
    out[dust, 0], out[dust, 1], out[dust, 2] = BODY
    out[..., 3] = a
    return Image.fromarray(out.astype(np.uint8))


def cells_of(path):
    im = Image.open(path).convert("RGBA")
    # 흰 배경 시트(GPT 생성물)는 먼저 배경을 투명으로 벗긴다
    if np.array(im)[..., 3].min() > 250:
        im = strip_background(im)
    # 칸 크기는 시트 크기에서 계산 (1680x760, 1600x800 어느 쪽도 지원)
    cw, ch = im.width // COLS, im.height // ROWS
    return [
        im.crop((c * cw, r * ch, (c + 1) * cw, (r + 1) * ch))
        for r in range(ROWS) for c in range(COLS)
    ]


def trim(im):
    arr = np.array(im)
    ys, xs = np.nonzero(arr[..., 3] > 10)
    if len(ys) == 0:
        return im
    return im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


def body_height(im):
    arr = np.array(im)
    ys, _ = np.nonzero(arr[..., 3] > 200)
    return (ys.max() - ys.min()) if len(ys) else 1


def muzzle_center(im):
    """흰 주둥이 픽셀들의 무게중심 (프레임 안 좌표). 흰 픽셀이 없으면 None."""
    arr = np.array(im, dtype=np.int16)
    r, g, b, a = arr[..., 0], arr[..., 1], arr[..., 2], arr[..., 3]
    white = (a > 128) & (np.minimum(np.minimum(r, g), b) > 185)
    ys, xs = np.nonzero(white)
    if len(ys) < 20:
        return None
    return float(xs.mean()), float(ys.mean())


def build_strip(sheet_name):
    cells = cells_of(os.path.join(SRC_DIR, f"{sheet_name}.png"))
    has_teal = sheet_name.startswith("laser")

    # 배율: 기준 칸(1번)의 몸높이 -> STAND_HEIGHT
    ref = trim(cells[0])
    scale = STAND_HEIGHT / body_height(ref)

    frames = []
    for idx in SEQUENCES[sheet_name]:
        raw = cells[idx - 1]
        if USE_LARGEST_BLOB:
            raw = keep_largest_blob(raw)    # 옆 칸에서 넘어온 광선·획 조각 제거
        cell = clean_frame(raw, outline_min_area=1200, stripe_min=40,
                           noise_max=22, has_teal=has_teal)
        f = trim(cell)
        nw = max(1, int(round(f.width * scale)))
        nh = max(1, int(round(f.height * scale)))
        frames.append(f.resize((nw, nh), Image.LANCZOS))

    fw = max(f.width for f in frames) + 4
    fh = max(f.height for f in frames) + 2
    strip = Image.new("RGBA", (fw * len(frames), fh), (0, 0, 0, 0))

    # 대기(춤) 동작은 얼굴(흰 주둥이) 중심을 프레임마다 같은 자리에 고정한다
    # — 발끝 기준으로만 맞추면 눈·주둥이가 프레임마다 들썩여 보인다 (사용자 피드백)
    anchors = [muzzle_center(f) for f in frames] if sheet_name.endswith("_idle") else None

    for i, f in enumerate(frames):
        px = i * fw + (fw - f.width) // 2
        py = fh - f.height
        if anchors and anchors[0] and anchors[i]:
            dx = int(round(anchors[0][0] - anchors[i][0] + (f.width - frames[0].width) / 2))
            dy = int(round(anchors[0][1] - anchors[i][1] + (f.height - frames[0].height)))
            px += max(-22, min(22, dx))
            py += max(-22, min(22, dy))
        strip.paste(f, (px, py), f)

    out = os.path.join(DST_DIR, f"{OUT_NAMES[sheet_name]}.png")
    strip.save(out)
    print(f"{out}  {strip.width}x{strip.height}")


def clean_static(src_name, dst_name, target_h, has_teal=False):
    """노란 냥이 정적 스프라이트: 같은 정리 + 70% 크기."""
    im = Image.open(os.path.join(STATIC_SRC, f"{src_name}.png")).convert("RGBA")
    im = clean_frame(im, outline_min_area=500, stripe_min=14, noise_max=6, has_teal=has_teal)
    im = trim(im)
    scale = target_h / im.height
    im = im.resize((max(1, int(im.width * scale)), target_h), Image.LANCZOS)
    out = os.path.join(DST_DIR, f"{dst_name}.png")
    im.save(out)
    print(f"{out}  {im.width}x{im.height}")


def main():
    # 인자로 다른 시트 폴더를 주면 (예: GPT가 새로 그린 Plan/cat_frames5)
    # 거기 있는 시트만 처리하고, 프레임은 골라내지 않고 2~8번을 전부 쓴다.
    global SRC_DIR, USE_LARGEST_BLOB
    if len(sys.argv) > 1:
        SRC_DIR = os.path.join(ROOT, sys.argv[1]) if not os.path.isabs(sys.argv[1]) else sys.argv[1]
        USE_LARGEST_BLOB = True
        for name in SEQUENCES:
            SEQUENCES[name] = [2, 3, 4, 5, 6, 7, 8]

    processed = 0
    for name in SEQUENCES:
        if not os.path.exists(os.path.join(SRC_DIR, f"{name}.png")):
            print(f"건너뜀 (시트 없음): {name}.png")
            continue
        build_strip(name)
        processed += 1
    if processed == 0:
        print(f"처리할 시트가 없습니다: {SRC_DIR}")
        return

    # 노란 냥이 3종은 줄무늬 갈색화까지 (나머지는 cat_sprites.py 의 70% 축소만)
    clean_static("normal_cat", "cat_normal", 91)
    clean_static("ally_cat", "cat_ally", 111)
    clean_static("laser_cat", "cat_laser", 105, has_teal=True)


if __name__ == "__main__":
    main()
