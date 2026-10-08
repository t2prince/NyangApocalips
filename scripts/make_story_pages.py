"""스토리 그림 3장 — 대표님 손그림(Plan/story/story1~3.jpg)을 참고해 GPT가 다시 그린 그림(Plan/story/gpt_story1~3.png) → 게임 리소스.
(2026-10-08 「앞의 스토리보드는 처음 한 번만 나오게, 손그림 참고해서 지피티로 그려」)
GPT 그림은 `codex exec` 이미지 생성으로 만든다 — 프롬프트는 docs/ART_PIPELINE.md 의 GPT 절 참고.
결과: app/src/main/res/drawable-nodpi/story_1~3.jpg (본 그림), story_bg_1~3.jpg (가로 화면 양옆을 채우는 흐린 배경)
사용법: python scripts/make_story_pages.py
"""
from PIL import Image, ImageEnhance, ImageFilter

DST = 'app/src/main/res/drawable-nodpi'

for i in (1, 2, 3):
    pg = Image.open(f'Plan/story/gpt_story{i}.png').convert('RGB')
    pg = pg.resize((1350, round(pg.height * 1350 / pg.width)), Image.LANCZOS)
    pg.save(f'{DST}/story_{i}.jpg', quality=90)
    # 화면 비율(약 2.2:1)로 가운데를 잘라 크게 흐리고 살짝 어둡게 — 양옆을 같은 그림으로 채운다
    ch = int(pg.width / 2.2); top = (pg.height - ch) // 2
    bg = pg.crop((0, top, pg.width, top + ch)).resize((720, int(720 / 2.2)), Image.LANCZOS).filter(ImageFilter.GaussianBlur(14))
    ImageEnhance.Brightness(bg).enhance(0.72).save(f'{DST}/story_bg_{i}.jpg', quality=85)
    print(f'story_{i}.jpg', pg.size)
