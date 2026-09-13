"""
추출된 개별 냥이 프레임(RGBA PNG)들을 4x2 애니메이션 시트로 조립한다.
1번 칸 = 기준 자세(해당 정적 스프라이트 재사용), 2~8번 칸 = 동작 프레임(부족하면 왕복 배치).

정규화: 각 프레임의 '몸통 폭'(가장 넓은 가로줄 폭 — 다리/꼬리보다 안정적인 척도)을 재서
공통 목표 폭에 맞춰 개별 스케일링한 뒤, 배 밑선(그 가로줄의 y)을 기준으로 세로 정렬한다.
정규화 후 각 칸의 몸통 폭을 다시 측정해 출력한다(검증용).

사용법: python scripts/assemble_anim.py <출력.png> <기준자세.png> <프레임.png> [프레임.png ...]
  프레임은 정확히 7개를 넘겨야 한다(왕복 배치는 호출하는 쪽에서 만들어서 넘긴다).
"""
import sys
import numpy as np
from PIL import Image

CELL_W, CELL_H = 420, 380
TARGET_BODY_H = 260  # 정규화 목표 몸통 높이(px, 셀 안에서)


def body_width_and_row(arr):
    """정규화 척도로 '몸통 오벌 세로 높이'를 쓴다(꼬리/수염이 안 걸리는 유일하게 안정적인
    축 — 이 냥이들은 항상 앉은 자세라 다리도 세로 몇 픽셀 안에서만 움직인다).
    알파>200 전체 bbox 높이를 그대로 척도로 쓰고, 위치 정렬 기준선은 그 bbox의
    아래쪽 12%(발 끝 바로 위, 배 밑선 근사) 지점으로 잡는다."""
    alpha = arr[:, :, 3] > 200
    ys, xs = np.nonzero(alpha)
    if len(ys) == 0:
        return 0, 0
    top, bot = ys.min(), ys.max()
    height = int(bot - top)
    belly_y = int(top + height * 0.88)
    return height, belly_y


def load_and_trim(path):
    im = Image.open(path).convert("RGBA")
    arr = np.array(im)
    ys, xs = np.nonzero(arr[:, :, 3] > 10)
    if len(ys) == 0:
        return im
    return im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


def place(sheet, path, col, row, seed_scale=None):
    im = load_and_trim(path)
    arr = np.array(im)
    bw, by = body_width_and_row(arr)
    scale = TARGET_BODY_H / max(1, bw)
    neww, newh = max(1, int(im.width * scale)), max(1, int(im.height * scale))
    resized = im.resize((neww, newh), Image.LANCZOS)
    # 배 밑선(몸통 최대폭 줄)을 셀의 고정 기준선에 맞춘다
    belly_y_in_resized = int(by * scale)
    ground_in_cell = int(CELL_H * 0.62)
    cx = col * CELL_W + (CELL_W - neww) // 2
    cy = row * CELL_H + ground_in_cell - belly_y_in_resized
    sheet.paste(resized, (cx, cy), resized)
    # 검증용 재측정
    out_arr = np.array(resized)
    vbw, _ = body_width_and_row(out_arr)
    return vbw


def main():
    out_path = sys.argv[1]
    ref_path = sys.argv[2]
    frame_paths = sys.argv[3:]
    assert len(frame_paths) == 7, f"프레임은 정확히 7개여야 한다 (받은 개수: {len(frame_paths)})"

    sheet = Image.new("RGBA", (CELL_W * 4, CELL_H * 2), (0, 0, 0, 0))
    widths = []
    w0 = place(sheet, ref_path, 0, 0)
    widths.append(("ref", w0))
    for i, p in enumerate(frame_paths):
        col, row = (i + 1) % 4, (i + 1) // 4
        w = place(sheet, p, col, row)
        widths.append((f"frame{i+1}", w))

    sheet.save(out_path)
    print(f"{out_path}  {sheet.width}x{sheet.height}")
    vals = [w for _, w in widths]
    print("칸별 몸통 높이:", widths)
    print(f"최대-최소 편차: {max(vals)-min(vals)}px")


if __name__ == "__main__":
    main()
