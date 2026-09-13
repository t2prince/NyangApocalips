"""나머지 냥이 원본(Plan/cat_sprites5)을 일반 냥이처럼 '매끈한 몸매'로 다듬는다.
- 실루엣과 잉크 선을 블러+임계값으로 매끄럽게
- 색연필 해칭을 큰 중앙값 필터로 뭉개고 몇 가지 대표색으로 스냅 (평평한 단색)
- 결과는 cat_sprites.py 와 같은 키로 drawable-nodpi/cat_*.png 에 저장

사용법: python scripts/smooth_cats.py [이름 ...]   (기본: 전부)
"""
import os, sys
sys.path.insert(0, 'scripts')
import numpy as np
from PIL import Image, ImageFilter
from sheet_to_sprites import strip_background
from cat_sprites import CATS, SRC_DIR, DST_DIR

INK = np.array([25, 21, 19])

def blur_mask(mask, r, thr=0.5):
    im = Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r))
    return np.array(im) > thr * 255

def kmeans(pixels, k, iters=12, seed=0):
    rng = np.random.default_rng(seed)
    centers = pixels[rng.choice(len(pixels), k, replace=False)].astype(float)
    for _ in range(iters):
        d = ((pixels[:, None, :] - centers[None]) ** 2).sum(-1)
        lab = d.argmin(1)
        for i in range(k):
            sel = pixels[lab == i]
            if len(sel): centers[i] = sel.mean(0)
    return centers

def smooth(name, k_colors=5, ink_r=2.0, sil_r=2.5, med=21, ring_px=7):
    src = os.path.join(SRC_DIR, f"{name}.png")
    im = strip_background(Image.open(src).convert('RGBA'))
    arr = np.array(im)
    m = arr[..., 3] > 128
    rgb = arr[..., :3].astype(int)
    lum = rgb.sum(2) / 3
    ink = m & (lum < 105)

    # 1) 실루엣 매끈하게 (털 톱니·잔가시 제거), 잉크 선 매끈하게
    sil = blur_mask(m, sil_r)
    sil = sil | blur_mask(ink, 1.0)          # 가는 다리·수염은 실루엣에서 빠지지 않게
    ink_s = blur_mask(ink, ink_r) & sil
    # 너무 작은 잉크 조각(잡티) 제거
    from rebuild_anim import label_components
    for area, ys, xs in label_components(ink_s):
        if area < 120: ink_s[ys, xs] = False

    # 2) 색 영역: 해칭을 중앙값 필터로 뭉갠 뒤 대표색으로 스냅
    color_src = arr[..., :3].copy()
    color_src[~m | ink] = 0
    # 잉크·배경 자리를 주변 색으로 메워야 중앙값이 어두워지지 않는다 → 가까운 색 픽셀로 채우기
    fill_mask = m & ~ink
    filled = Image.fromarray(np.dstack([color_src, (fill_mask * 255).astype(np.uint8)]).astype(np.uint8))
    # 큰 중앙값: 알파를 무시하므로 검은 구멍이 섞인다 → 대신 색 픽셀만으로 kmeans 후 라벨맵을 중앙값 필터
    px = rgb[fill_mask]
    white = (px.min(1) > 200)
    cand = px[~white] if (~white).sum() > 500 else px
    centers = kmeans(cand[::max(1, len(cand) // 20000)], k_colors)
    # 흰색은 항상 팔레트에
    centers = np.vstack([centers, [255, 255, 255]])
    d = ((rgb[..., None, :] - centers[None, None]) ** 2).sum(-1)
    lab = d.argmin(-1).astype(np.uint8)
    lab[~fill_mask] = 255
    # 라벨맵 중앙값 필터 (해칭 제거). 255(빈 곳)는 필터 전 최근접 라벨로 임시 대체
    lab_img = Image.fromarray(lab)
    # 빈 곳을 채우기 위해 팽창 반복
    tmp = lab.copy()
    for _ in range(12):
        holes = tmp == 255
        if not holes.any(): break
        grown = np.array(Image.fromarray(np.where(holes, 0, tmp + 1).astype(np.uint8)).filter(ImageFilter.MaxFilter(5)))
        tmp = np.where(holes & (grown > 0), grown - 1, tmp).astype(np.uint8)
    if (tmp == 255).any():
        common = np.bincount(tmp[tmp != 255]).argmax() if (tmp != 255).any() else 0
        tmp[tmp == 255] = common
    tmp = np.array(Image.fromarray(tmp).filter(ImageFilter.MedianFilter(med)))
    tmp = np.minimum(tmp, len(centers) - 1)

    out = np.zeros_like(arr)
    body = sil & ~ink_s
    out[body, :3] = centers[tmp[body].astype(int)]
    out[body, 3] = 255
    out[ink_s, :3] = INK
    out[ink_s, 3] = 255
    # 실루엣 가장자리에 잉크 테두리 보장
    inner = np.array(Image.fromarray((sil * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(ring_px))) > 127
    ring = sil & ~inner
    out[ring, :3] = INK; out[ring, 3] = 255

    res = Image.fromarray(out.astype(np.uint8))
    res = res.crop(res.getbbox())
    return res

def main():
    # 자동 매끈화가 잘 먹는 냥이만 기본 대상 (날아·털뭉·인간편 보스는 오히려 망가져 제외)
    GOOD = ['green_cat', 'poop_cat', 'blue_cat', 'anvil_cat', 'laser_cat', 'boss_evil_cat', 'boss_wizard_cat']
    names = sys.argv[1:] or GOOD
    for name in names:
        if not os.path.exists(os.path.join(SRC_DIR, f"{name}.png")): print('없음', name); continue
        res_name, height = CATS[name]
        res = smooth(name)
        scale = height / res.height
        res = res.resize((max(1, round(res.width * scale)), height), Image.LANCZOS)
        dst = os.path.join(DST_DIR, f"{res_name}.png")
        if '--preview' in os.environ.get('SMOOTH_FLAGS', ''):
            dst = os.path.join(os.environ['SMOOTH_OUT'], f"{res_name}.png")
        res.save(dst); print(dst, res.size)

if __name__ == '__main__':
    main()
