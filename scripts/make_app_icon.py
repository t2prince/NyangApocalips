"""앱 아이콘 = 지금 게임의 우리 팀 치즈냥이 그림 (사용자 요청 2026-11-08).
make_ally_from_normal.py 와 같은 과정(가는 꼬리)으로 큰 그림을 만들어
- drawable/ic_launcher_cat.png  : 적응형 아이콘 앞면 (432px, 가운데 안전 영역 안에 냥이)
- mipmap-*/ic_launcher(_round).png : 옛 기기용 (주황 바탕 + 냥이)
을 만든다. 바탕색은 ic_launcher_background.xml 의 #FFB347 그대로.

사용법: python scripts/make_app_icon.py
"""
import os, sys, tempfile
sys.path.insert(0, 'scripts')
from PIL import Image, ImageDraw
import make_ally_from_normal as ally

BG = (255, 179, 71, 255)
FG_SIZE = 432
CAT_W = 215            # 가운데 원(72dp) 안에 귀·꼬리까지 들어가는 너비
LEGACY = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}


def big_cat():
    tmp = tempfile.mkdtemp()
    ally.DST_DIR = tmp
    ally.TARGET_H = 232                      # 원본 키 그대로 (줄이지 않는다)
    ally.main()
    return Image.open(os.path.join(tmp, 'cat_ally.png')).convert('RGBA')


def main():
    cat = big_cat()
    cat = cat.resize((CAT_W, round(cat.height * CAT_W / cat.width)), Image.LANCZOS)
    fg = Image.new('RGBA', (FG_SIZE, FG_SIZE), (0, 0, 0, 0))
    fg.alpha_composite(cat, ((FG_SIZE - cat.width) // 2, (FG_SIZE - cat.height) // 2))
    fg.save('app/src/main/res/drawable/ic_launcher_cat.png')

    # 옛 기기용: 바탕 + 앞면을 합치고 가운데 72dp 만큼 잘라 쓴다
    full = Image.new('RGBA', (FG_SIZE, FG_SIZE), BG)
    full.alpha_composite(fg)
    m = FG_SIZE * 18 // 108
    full = full.crop((m, m, FG_SIZE - m, FG_SIZE - m))
    for dpi, px in LEGACY.items():
        sq = full.resize((px, px), Image.LANCZOS)
        sq.save(f'app/src/main/res/mipmap-{dpi}/ic_launcher.png')
        mask = Image.new('L', (px * 4, px * 4), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, px * 4 - 1, px * 4 - 1), fill=255)
        rd = Image.new('RGBA', (px, px), (0, 0, 0, 0))
        rd.paste(sq, (0, 0), mask.resize((px, px), Image.LANCZOS))
        rd.save(f'app/src/main/res/mipmap-{dpi}/ic_launcher_round.png')
    print('done')


if __name__ == '__main__':
    main()
