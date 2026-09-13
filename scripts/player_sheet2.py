"""
동작별 스프라이트 시트(4x2=8칸)를 게임용 애니메이션 스트립으로 자른다.

시트 규칙: 1번 칸은 모든 시트에 공통인 '기준 서 있는 자세', 2~8번 칸이 그 동작의 7프레임.
기준 칸의 키로 배율을 맞추므로 시트가 달라도 캐릭터 체격이 흔들리지 않는다.

사용법: python scripts/player_sheet2.py
"""

import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sheet_to_sprites import (  # noqa: E402
    strip_background, content_rows, content_cols, keep_largest_blob,
)

SRC_DIR = "Plan/player_frames2"
DST_DIR = "app/src/main/res/drawable-nodpi"

COLS, ROWS = 4, 2

# 기준 자세(1번 칸)의 키를 이 픽셀로 맞춘다
STAND_HEIGHT = 168

# 시트 이름 → 리소스 이름
SHEETS = {
    "idle": "player_idle",
    "walk": "player_walk",
    "jump": "player_jump",
    "punch": "player_punch",
    "cross": "player_cross",
    "kick": "player_kick",
    "jumpkick": "player_jumpkick",
    "stomp": "player_stomp",
}


def slice_sheet(src: str, res_name: str):
    sheet = strip_background(Image.open(src))
    cw, ch = sheet.width // COLS, sheet.height // ROWS

    cells = [
        keep_largest_blob(sheet.crop((c * cw, r * ch, (c + 1) * cw, (r + 1) * ch)))
        for r in range(ROWS) for c in range(COLS)
    ]

    bounds = [content_rows(cell) for cell in cells]
    hbounds = [content_cols(cell) for cell in cells]

    top = max(0, min(t for t, _ in bounds) - 4)
    left = max(0, min(l for l, _ in hbounds) - 4)
    right = min(cw, max(r for _, r in hbounds) + 4)

    # 1번 칸(기준 자세)의 실제 키로 배율을 정한다 → 시트끼리 체격이 같아진다
    ref_top, ref_bot = bounds[0]
    scale = STAND_HEIGHT / (ref_bot - ref_top)

    frames = list(range(1, len(cells)))          # 기준 칸은 게임에 쓰지 않는다
    frame_w = max(1, int(round((right - left) * scale)))
    frame_h = max(1, int(round((max(b for _, b in bounds) - top) * scale)))

    strip = Image.new("RGBA", (frame_w * len(frames), frame_h), (0, 0, 0, 0))
    for i, idx in enumerate(frames):
        own_bot = min(ch, bounds[idx][1] + 4)
        frame = cells[idx].crop((left, top, right, own_bot))
        w = max(1, int(round(frame.width * scale)))
        h = max(1, int(round(frame.height * scale)))
        frame = frame.resize((w, h), Image.LANCZOS)
        # 가로 가운데, 세로는 자기 발끝을 바닥에 붙인다
        strip.paste(frame, (i * frame_w + (frame_w - w) // 2, frame_h - h), frame)

    path = os.path.join(DST_DIR, f"{res_name}.png")
    strip.save(path)
    print(f"{path}  {strip.width}x{strip.height}  frames={len(frames)}")


def main():
    os.makedirs(DST_DIR, exist_ok=True)
    for name, res_name in SHEETS.items():
        src = os.path.join(SRC_DIR, f"{name}.png")
        if os.path.exists(src):
            slice_sheet(src, res_name)
        else:
            print(f"아직 없음: {src}")


if __name__ == "__main__":
    main()
