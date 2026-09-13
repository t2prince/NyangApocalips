"""cat_frames5/normal_idle.png 의 각 칸에서 얼굴 쪽(오른쪽) 꼬리를 지운다.
몸통 외곽선은 꼬리 뒤로 이어져 있으므로, 몸통 속(외곽선으로 둘러싸인 노란 영역)과 그 외곽선은 보호하고
꼬리 속 + 꼬리 외곽선만 지운다."""
import sys
sys.path.insert(0, 'scripts')
import numpy as np
from numpy.lib.stride_tricks import sliding_window_view
from PIL import Image
from sheet_to_sprites import strip_background
from rebuild_anim import label_components

SRC = 'Plan/cat_frames5/normal_idle.png'
OUT = sys.argv[1] if len(sys.argv) > 1 else SRC
DBG = sys.argv[2] if len(sys.argv) > 2 else 'Plan/_debug/normal_idle_tail_debug.png'
COLS, ROWS = 4, 2
OUTLINE = 8       # 외곽선 두께(px) 근사 — 이만큼 몸통 속 주변을 보호한다

def hmax(m, k):
    if k <= 0: return m
    p = np.pad(m, ((0, 0), (k, k)))
    return sliding_window_view(p, 2 * k + 1, axis=1).any(axis=-1)

def dilate(m, r):
    h = m.shape[0]; out = np.zeros_like(m)
    for dy in range(-r, r + 1):
        dx = int((r * r - dy * dy) ** 0.5); row = hmax(m, dx)
        if dy >= 0: out[dy:] |= row[:h - dy]
        else: out[:h + dy] |= row[-dy:]
    return out

def main():
    orig = Image.open(SRC).convert('RGBA')
    sheet = np.array(orig).copy()
    alpha = np.array(strip_background(orig))[..., 3] > 128
    cw, ch = orig.width // COLS, orig.height // ROWS
    dbg_arr = np.array(orig).copy()

    for r in range(ROWS):
        for c in range(COLS):
            y0, x0 = r * ch, c * cw
            m = alpha[y0:y0 + ch, x0:x0 + cw]
            rgb = sheet[y0:y0 + ch, x0:x0 + cw, :3].astype(int)
            lum = rgb.sum(axis=2) / 3
            ink = m & (lum < 110)
            yellow = (rgb[..., 0] > 190) & (rgb[..., 1] > 130) & (rgb[..., 2] < 130)
            comps = label_components(m & ~ink)
            comps.sort(key=lambda t: -t[0])
            body = np.zeros_like(m); body[comps[0][1], comps[0][2]] = True   # 가장 큰 = 몸통 속
            x_br = comps[0][2].max()
            tail = np.zeros_like(m)
            for area, ys, xs in comps[1:]:
                if area < 300: continue
                if yellow[ys, xs].mean() < 0.5: continue
                if xs.mean() <= x_br - 5: continue                # 얼굴 쪽(오른쪽) 바깥의 노란 덩어리만
                tail[ys, xs] = True
            if not tail.any():
                print('cell', r, c, 'no front tail found'); continue
            protect = dilate(body, OUTLINE)                          # 몸통 속 + 몸통 외곽선
            erase = dilate(tail, OUTLINE + 1) & m & ~protect        # 꼬리 속 + 꼬리 외곽선
            ghost = dilate(erase, 8) & ~(m & ~erase)                # 주변의 옅은 테두리 잔상
            erase |= ghost
            cell = sheet[y0:y0 + ch, x0:x0 + cw]
            cell[erase] = (255, 255, 255, 255)
            d = dbg_arr[y0:y0 + ch, x0:x0 + cw]; d[erase] = (255, 0, 0, 255)
            print('cell', r, c, 'erased', int(erase.sum()))

    Image.fromarray(sheet).save(OUT)
    Image.fromarray(dbg_arr).save(DBG)


if __name__ == '__main__':
    main()
