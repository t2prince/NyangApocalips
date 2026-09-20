# 그림 방향과 파이프라인

## 방향

- 캐릭터 원안은 아이가 그린 손그림입니다 (`Cats/`, git에는 올리지 않음).
- 처음에는 손그림 선을 그대로 쓰길 원했지만, 지금 기준은 **ChatGPT로 생성한 깔끔한 평면 카툰 시트**(`Plan/cat_frames5`)입니다.
  굵은 매직펜 외곽선 + 평평한 단색, 프레임마다 흔들리지 않는 색과 선.
- 얼굴 구조 규칙은 항상 지킵니다: 작은 점 눈 2개 / 속이 흰 큰 주둥이 원 2개 / 이음새 위 검은 코. 자세한 스펙은 `scripts/CAT_ART_SPEC.md`.

## 새 그림이 필요할 때

1. 코드로 새 캐릭터를 그리지 말고 **프롬프트 문서**를 씁니다.
   - 애니메이션 시트: `Plan/GPT_애니메이션_프롬프트.md`
   - 단일 스프라이트: `Plan/GPT_냥이_스프라이트_프롬프트.md`
2. 사용자가 ChatGPT로 생성해서 `Plan/cat_frames5/` 또는 `Plan/cat_sprites6/`에 넣고 "변환해줘"라고 합니다.
3. 아래 스크립트로 게임 리소스(`app/src/main/res/drawable-nodpi/`)를 만듭니다.

GPT 결과물의 자잘한 오류는 픽셀 후처리 스크립트로 고칩니다. 원본 시트는 `*.before_*.png`로 백업하고 덮어씁니다.
**백업 파일을 지우지 마세요.** `thicken_enemy_tails.py`는 백업을 원본으로 읽습니다.

## 스크립트 실행 순서 (치즈냥이 계열)

모든 명령은 저장소 루트에서 실행합니다. `pip install pillow numpy` 필요.

```bash
python scripts/remove_front_tail.py          # 대기 시트: 얼굴 쪽 두 번째 꼬리 제거 (1회성, 이미 적용됨)
python scripts/remove_punch_leg.py           # 공격 시트 4·5번 칸: 팔 아래 앞발 제거 (1회성, 이미 적용됨)
python scripts/thicken_enemy_tails.py        # 적 꼬리를 두툼한 털 뭉치로 (before_thick_tail 백업을 원본으로 읽음)
python scripts/rebuild_anim.py Plan/cat_frames5   # 시트 → anim_cat_* 7프레임 스트립
# 정적 cat_normal = 대기 시트 2번 칸 (docs/HANDOFF.md 의 스니펫 참고)
python scripts/make_ally_from_normal.py      # cat_ally = 치즈냥이 그림 + 가는 선 꼬리
python scripts/recolor_cats.py               # 그린/파랑(적·아군·대기 스트립) + 순간냥이(분홍 가로줄)
python scripts/make_coat_variants.py         # 치즈냥이 무늬 4종(흰둥이·삼색이·턱시도·고등어): 정지·아군·대기/이동/점프/공격 스트립
python scripts/make_flying_cat.py            # 날아냥이 = 치즈냥이 + 원본 날개 (적·아군)
python scripts/smooth_cats.py                # 나머지 종(똥·모루·레이저·보스) 임시 평면화
python scripts/gen_shop_icons.py             # 상점 아이콘 + 갑옷·무덤·부활 약·공격력 Lv업 (투명 배경)
python scripts/gen_skill_icons.py            # 고양이 기술 skill_*.png + 인간 기술 hskill_*.png
python scripts/find_hands.py                 # 인간 스트립 프레임별 손 위치 → ui/HandTable.kt (권투 장갑용)
```

`ally_tails.py`는 이제 아무 일도 하지 않습니다 (아군 그림은 위 스크립트들이 만듦).

## 아직 남은 그림 일

- 똥·모루·레이저·털뭉·보스 냥이는 `smooth_cats.py`로 임시 평면화한 것입니다. 털뭉냥이·인간편 보스는 자동 처리가 망가뜨려 원본 그대로입니다.
  최종은 `Plan/GPT_냥이_스프라이트_프롬프트.md`로 GPT 재생성이 필요합니다.
- 인간 캐릭터는 모두 같은 졸라맨 시트에 색 필터와 장식을 코드로 얹어 그립니다 (`ui/HeroDraw.kt`). 인간별 전용 그림은 없습니다.
