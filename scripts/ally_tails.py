"""
아군 냥이 = 얇은 꼬리 (사용자 규칙: 같은편은 꼬리가 얇고, 다른편은 두껍다).

기존 스프라이트에서 두꺼운 꼬리 영역을 지우고, 가는 잉크 선 꼬리를 그려 넣는다.
  - cat_ally.png        : 덮어쓴다 (아군 전용 리소스)
  - cat_blue_ally.png   : cat_blue 에서 생성 (적 cat_blue 는 그대로)
  - cat_green_ally.png  : cat_green 에서 생성

사용법: python scripts/ally_tails.py  (rebuild_anim.py / cat_sprites.py 다음에 실행)
"""

import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DST = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")

INK = (25, 21, 19, 255)


def thin_tail(src, dst, erase_box, curve, width=5):
    im = Image.open(os.path.join(DST, src)).convert("RGBA")
    px = im.load()
    x0, y0, x1, y1 = erase_box
    for y in range(max(0, y0), min(im.height, y1)):
        for x in range(max(0, x0), min(im.width, x1)):
            px[x, y] = (0, 0, 0, 0)

    d = ImageDraw.Draw(im)
    d.line(curve, fill=INK, width=width, joint="curve")
    tx, ty = curve[-1]
    r = width * 0.7
    d.ellipse((tx - r, ty - r, tx + r, ty + r), fill=INK)   # 둥근 꼬리 끝

    im.save(os.path.join(DST, dst))
    print(dst, im.size)


def main():
    # (지울 두꺼운 꼬리 영역, 새 얇은 꼬리 곡선: 몸 붙는 곳 -> 끝)
    # cat_ally.png 는 scripts/make_ally_from_normal.py 가 일반 냥이 그림에서 만든다 (같은 그림, 꼬리만 얇게)
    # cat_blue_ally.png 도 scripts/recolor_cats.py 가 만든다
    # cat_green_ally.png 는 scripts/recolor_cats.py 가 cat_ally(얇은 꼬리)를 초록으로 바꿔 만든다


if __name__ == "__main__":
    main()
