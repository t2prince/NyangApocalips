"""GPT(Codex 이미지 생성)가 그린 새 냥이들 → 게임 리소스 (2026-10-04 「그림은 지피티에게 맡겨서 그려」).
원본: Plan/cat_sprites7/*.png (1254×1254 흰 바탕). 바깥 흰 배경만 투명으로 하고 내용에 맞춰 자른 뒤 정해진 키로 줄인다.
(Plan/cat_sprites6 는 예전 시트라 건드리지 않는다 — cat_sprites.py 를 거기에 돌리면 기존 냥이들이 덮어써진다)
사용법: python scripts/gpt_sprites7.py
"""
import glob, os, sys
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sheet_to_sprites import strip_background  # noqa: E402

SRC = 'Plan/cat_sprites7'
DST = 'app/src/main/res/drawable-nodpi'
# 원본 이름 → (리소스 이름, 높이 px). 꽃냥이는 대표님이 「이전 이미지로」 해서 GPT 그림을 쓰지 않는다.
ITEMS = {
    'bee_cat': ('cat_bee', 100),
    'mud_cat': ('cat_mud', 86),
    'pirate_cat': ('cat_pirate', 118),
    'boat_cat': ('cat_boat', 92),
    'honey_cat': ('cat_honey', 86),     # 꿀냥이 (꿀 늪, 2026-10-09)
    'pirate_ship': ('pirate_ship', 760),
    'fishing_boat': ('fishing_boat', 420),   # 낚시터 어선 (2026-10-17 「배는 일반적인 어선」)
    'queen_bee_cat': ('cat_queen_bee', 190),   # 30스테이지 보스 여왕 벌냥이 (2026-10-10)
    'cat_house': ('cat_house', 600),
    'straw_cat': ('straw_cat', 360),    # 투기장 밀짚 가짜 냥이 (2026-10-09)
    # 투기장 가짜 냥이 Lv 별 재료 (2026-10-09 「Lv업을 하면 가짜 고양이 재료가 바뀌어」) — Lv1 밀짚 → 나무 → 돌 → 철 → 금 → 다이아
    'dummy_wood': ('dummy_wood', 360),
    'dummy_stone': ('dummy_stone', 360),
    'dummy_iron': ('dummy_iron', 360),
    'dummy_gold': ('dummy_gold', 360),
    'dummy_diamond': ('dummy_diamond', 360),
    # 자동차 매장·자동차 (2026-10-09 「맨 뒤 자동차 매장에서 자동차 팔아」)
    'car_shop': ('car_shop', 600),
    'car_red': ('car_red', 240),
    'car_blue': ('car_blue', 240),
    'car_yellow': ('car_yellow', 240),
    'gun_side': ('gun_side', 120),      # 무기점 레이저 총 옆모습 — 인간 손·자동차에 단다 (2026-10-09)
    'robot_side': ('robot_side', 460),  # 11번 로봇 상점 로봇 옆모습 (2026-10-09)
    'jamcat_logo': ('jamcat_logo', 520),  # 시작 잼캣 로고 엠블럼 (2026-10-09)
    'gacha_machine': ('gacha_machine', 520),  # 집 앞 뽑기 기계 (2026-10-10)
    # 로봇 부품 — 팔·다리가 움직이고 주먹을 쏜다 (2026-10-10). 관절 자리는 RobotRide 의 상수
    'robot_p_body': ('robot_p_body', 320),
    'robot_p_arm': ('robot_p_arm', 240),
    'robot_p_fist': ('robot_p_fist', 160),
    'robot_p_leg': ('robot_p_leg', 280),
    'hskill_robot_wing': ('hskill_robot_wing', 256),
    'hskill_arrow_rain': ('hskill_arrow_rain', 256),  # 인간 기술 '화살비' 버튼 (2026-10-16)  # 로봇 기술 '날개' 버튼 (2026-10-10)
    'treadmill': ('treadmill', 420),
    'fish_shop': ('fish_shop', 420),   # 낚시 상점 건물 (2026-10-17)   # 13번 고양이 체력 훈련장 러닝머신 (2026-10-10)
    # 집 안 가구 (2026-10-09 「집에 가구들도 있어」)
    'furn_sofa': ('furn_sofa', 300),
    'furn_shelf': ('furn_shelf', 400),
    'furn_table': ('furn_table', 300),
    'furn_lamp': ('furn_lamp', 420),
    'furn_catbeds': ('furn_catbeds', 320),
    'furn_plant': ('furn_plant', 340),
    'furn_fireplace': ('furn_fireplace', 380),
}

def clear_ship_pockets(img):
    """해적선: 밧줄·돛대·선체 사이에 갇힌 흰 바탕, 그물 구멍, 철창 창살 사이를 투명하게 한다.
    돛 안(돛 상자 안에 가운데가 있는 흰 칸)은 돛·고양이 얼굴·뼈라서 남긴다. 좌표는 760 높이 기준."""
    import numpy as np
    from rebuild_anim import label_components
    a = np.array(img)
    H, W = a.shape[:2]
    k = H / 760.0
    white = (a[..., 3] > 0) & (a[..., :3].min(axis=2) > 215)
    sail = (490 * k, 125 * k, 940 * k, 415 * k)
    boxes = [(340 * k, 380 * k, 560 * k, 570 * k), (890 * k, 375 * k, 1100 * k, 530 * k),   # 그물
             (565 * k, 420 * k, 695 * k, 570 * k), (745 * k, 420 * k, 875 * k, 570 * k)]    # 철창
    inside = lambda bx, x, y: bx[0] <= x <= bx[2] and bx[1] <= y <= bx[3]
    for n, ys, xs in label_components(white):
        cx, cy = xs.mean(), ys.mean()
        if inside(sail, cx, cy) and not any(inside(b, cx, cy) for b in boxes):
            continue                                   # 돛 속 흰 칸은 남긴다
        if any(inside(b, cx, cy) for b in boxes) or n > 400:
            a[ys, xs, 3] = 0
    # 철창과 갇힌 사람은 지운다 (대표님 2026-10-04 「사람 갇혀 있는 것만 없애줘」) — 갑판 난간 위까지만
    for x0, y0, x1, y1 in boxes[2:]:
        a[int(y0):int(558 * k), int(x0):int(x1), 3] = 0
        # 철창 바닥 받침(회색, 채도 낮음)이 난간 위에 남지 않게
        sub = a[int(556 * k):int(574 * k), int(x0):int(x1)]
        rgb = sub[..., :3].astype(int)
        gray = (rgb.max(axis=2) - rgb.min(axis=2) < 30) & (rgb.max(axis=2) > 40)
        sub[gray, 3] = 0
        # 받침 테두리까지 지우기: 철창 바깥 양옆 열에서 난간 윗선 높이를 재서, 그 사이 열은 그 선 위를 모두 지운다
        def rail_top(x):
            col = a[int(540 * k):int(590 * k), x]
            d = np.nonzero((col[:, 3] > 0) & (col[:, :3].max(axis=1) < 90))[0]
            return int(540 * k) + (d[0] if len(d) else int(25 * k))
        xl, xr = int(x0) - 14, int(x1) + 14
        yl, yr = rail_top(xl), rail_top(xr)
        for x in range(xl + 4, xr - 3):
            yt = round(yl + (yr - yl) * (x - xl) / (xr - xl))
            a[int(y0):yt, x, 3] = 0
    return Image.fromarray(a)


for name, (res, h) in ITEMS.items():
    p = os.path.join(SRC, name + '.png')
    if not os.path.exists(p):
        print('없음:', name); continue
    img = strip_background(Image.open(p))
    img = img.crop(img.getbbox())
    img = img.resize((max(1, round(img.width * h / img.height)), h), Image.LANCZOS)
    if name == 'pirate_ship':
        img = clear_ship_pockets(img)
    img.save(os.path.join(DST, res + '.png'))
    print(res, img.size)


# ===== 갑옷 입은 내 편 냥이 (GPT, 2026-10-04 「갑옷도 다시 만들어줘」) =====
# 맨몸 아군 그림과 몸집이 비슷하게: 높이를 맨몸 높이 × 1.14(작은 투구 몫)로 맞춘다 .
# 대기(춤) 줄은 GPT 그림 한 장을 7칸으로 — 칸마다 살짝 들썩이고 눌린다.
ARMOR = {  # 원본 → (맨몸 아군 그림, 정지 리소스, 대기 줄 리소스 또는 None)
    'cheese': ('cat_ally', 'cat_cheese_armor_ally', 'anim_cat_idle_cheese_armor'),
    'white': ('cat_white_ally', 'cat_white_armor_ally', 'anim_cat_idle_white_armor'),
    'calico': ('cat_calico_ally', 'cat_calico_armor_ally', 'anim_cat_idle_calico_armor'),
    'tuxedo': ('cat_tuxedo_ally', 'cat_tuxedo_armor_ally', 'anim_cat_idle_tuxedo_armor'),
    'mackerel': ('cat_mackerel_ally', 'cat_mackerel_armor_ally', 'anim_cat_idle_mackerel_armor'),
    'green': ('cat_green_ally', 'cat_green_armor_ally', 'anim_cat_idle_green_armor'),
    'blue': ('cat_blue_ally', 'cat_blue_armor_ally', 'anim_cat_idle_blue_armor'),
    'gun': ('cat_gun_ally', 'cat_gun_armor_ally', 'anim_cat_idle_gun_armor'),
    'flying': ('cat_flying_ally', 'cat_flying_armor_ally', None),
}
BOB = [0, 2, 4, 5, 4, 2, 0]          # 칸마다 들썩임(px)
SQUASH = [1.0, 0.985, 0.97, 0.965, 0.97, 0.985, 1.0]

for name, (plain, res, strip) in ARMOR.items():
    # 2026-10-09 「갑옷만 씌우고 비슷해야」: 맨몸 아군 그림을 GPT에 주고 갑옷만 씌운 armor2_*.png 를 쓴다.
    # 몸 윤곽이 맨몸과 같으므로 가로폭(꼬리~수염)을 맨몸 그림과 똑같이 맞추면 몸집이 그대로다.
    p = os.path.join(SRC, f'armor2_{name}.png')
    if not os.path.exists(p):
        print('없음: armor2_' + name); continue
    img = strip_background(Image.open(p))
    img = img.crop(img.getbbox())
    ref = Image.open(os.path.join(DST, plain + '.png'))
    rb = ref.getbbox()
    img = img.resize((rb[2] - rb[0], max(1, round(img.height * (rb[2] - rb[0]) / img.width))), Image.LANCZOS)
    img.save(os.path.join(DST, res + '.png'))
    print(res, img.size)
    if strip:
        fw, fh = img.width + 13, img.height + 8
        out = Image.new('RGBA', (fw * 7, fh), (0, 0, 0, 0))
        for i in range(7):
            h2 = round(img.height * SQUASH[i]); w2 = round(img.width * (2 - SQUASH[i]))
            fr = img.resize((w2, h2), Image.LANCZOS)
            out.alpha_composite(fr, (i * fw + (fw - w2) // 2, fh - h2 - BOB[i] - 2))
        out.save(os.path.join(DST, strip + '.png'))
        print(strip, out.size)


# ===== 인간 가게 물건 아이콘 (GPT, 2026-10-09 「인간이 가는 상점도 몇 개 만들어 줘」) =====
# Plan/shop_icons/shop_h_*.png → 다른 상점 아이콘과 같은 448×448 투명 칸 가운데에 (여백 24)
ICON = 448
# 다 먹이기(2026-10-10)도 같은 방식
for p in sorted(glob.glob(os.path.join('Plan/shop_icons', 'shop_h_*.png'))) + sorted(glob.glob('Plan/shop_icons/shop_feed_all.png')) + sorted(glob.glob('Plan/shop_icons/shop_fish_*.png')) + sorted(glob.glob('Plan/shop_icons/shop_sea_*.png')) + sorted(glob.glob('Plan/shop_icons/shop_cat_gps.png')) + sorted(glob.glob('Plan/shop_icons/shop_bait_*.png')) + sorted(glob.glob('Plan/shop_icons/shop_fish_rod.png')):
    img = strip_background(Image.open(p))
    img = img.crop(img.getbbox())
    s = (ICON - 48) / max(img.width, img.height)
    img = img.resize((max(1, round(img.width * s)), max(1, round(img.height * s))), Image.LANCZOS)
    out = Image.new('RGBA', (ICON, ICON), (0, 0, 0, 0))
    out.alpha_composite(img, ((ICON - img.width) // 2, (ICON - img.height) // 2))
    name = os.path.basename(p)
    out.save(os.path.join(DST, name))
    print(name[:-4], out.size)
