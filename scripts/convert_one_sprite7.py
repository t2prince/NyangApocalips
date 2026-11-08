"""gpt_sprites7.py 의 ITEMS 중 고른 것만 변환한다 (다른 그림을 덮어쓰지 않게).
사용법: python scripts/convert_one_sprite7.py anvil_cat fluffy_cat
"""
import os, sys
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sheet_to_sprites import strip_background  # noqa: E402

src = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'gpt_sprites7.py')).read()
items_src = src[src.index('ITEMS = {'):src.index('\n}\n', src.index('ITEMS = {')) + 3]
ns = {}
exec(items_src, ns)
ITEMS = ns['ITEMS']

for name in sys.argv[1:]:
    res, h = ITEMS[name]
    img = strip_background(Image.open(os.path.join('Plan/cat_sprites7', name + '.png')))
    img = img.crop(img.getbbox())
    img = img.resize((max(1, round(img.width * h / img.height)), h), Image.LANCZOS)
    img.save(os.path.join('app/src/main/res/drawable-nodpi', res + '.png'))
    print(res, img.size)
