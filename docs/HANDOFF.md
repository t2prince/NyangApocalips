# 인수인계 — 다른 PC에서 이어서 작업하기

마지막 갱신: 2026-09-20

## 처음 이어받을 때

1. `CLAUDE.md` → 이 문서 → `docs/GAME_RULES.md` → `docs/ART_PIPELINE.md` 순서로 읽습니다.
2. Claude Code의 자동 메모리(`~/.claude/projects/.../memory`)는 PC마다 따로라서 옮겨지지 않습니다.
   **그 내용은 전부 `docs/GAME_RULES.md`와 이 문서에 옮겨 두었습니다.** 새 규칙이 생기면 이 문서들도 같이 고쳐 주세요.
3. 빌드: `./gradlew assembleDebug`, 설치: `./gradlew installDebug` (Android SDK 경로는 `local.properties`).

## 사용자와 일하는 방식

- 대화·커밋 메시지는 **한국어**. 커밋 접두사 `feat:` / `fix:` / `refactor:`.
- 기획자는 요구를 짧게 한 줄로 줍니다 (예: "기술3:미사일:공격력:12값:190루비"). 빠진 숫자가 있으면 되묻고, 나머지 연출·쿨타임은 Claude가 정한 뒤 **정한 값을 알려 줍니다.**
- **사용자가 정하지 않은 기능·기술·캐릭터를 임의로 추가하지 않습니다.** (임의로 만든 고양이 기술 6종을 삭제해 달라는 요청이 있었음)
- 사용자가 규칙을 정정하면 그게 최종입니다. `docs/GAME_RULES.md`에 반영합니다.

## 기기 테스트 주의

- 테스트 기기에는 **사용자가 실제로 플레이 중인 세이브**가 있습니다 (`shared_prefs/nyang_progress.xml`).
- **세이브에 테스트 값(루비·아이템·배운 기술·냥이)을 넣지 마세요.** 넣었더니 "사지도 않았는데 샀다고 나온다"는 버그로 받아들여졌습니다.
- 사용자가 기기를 쓰는 중일 때는 adb로 화면을 자동 조작하지 말고, **빌드·설치만** 하고 직접 확인을 부탁합니다.
- 화면 확인이 꼭 필요하면 세이브를 백업하고 확인 직후 바로 원복합니다. 세이브 초기화는 사용자 확인 없이 하지 않습니다.

## 지금 상태

구현되어 기기에 설치된 것:

- 초원 1~10, 하늘 11, 정글 12~20 스테이지 / 로딩·타이틀·로비·전투·클리어·게임오버 화면
- 인간 4명 (검은·권투·파란(폭죽)·닌자(표창)), 팀 냥이 지시
- 고양이 관리(돌보기·하트·떠남·이름·출전·갑옷·Lv업·캣타워 춤·무덤·부활), 도감(춤), 고양이 상점 15종, 루비 상점
- 총냥이 (피15·공격2, 권총 보라탄, 초원 8~10 임시 배치, `entity/Enemy.kt` GunCat, 2026-09-20)
- 하늘 스테이지 개편: 공용 HUD(`ui/HUD.kt` Data), 고른 인간 탑승, 아군 구름 추종, 추락 연출, 날아냥이 하늘 전용 (2026-09-20)
- 도둑냥이 개편 (눈가리개·보따리 그림, 레이저 제거, 접촉 시 루비 2개 도둑질·처치 시 회수, 스테이지당 1마리, 2026-09-20)
- 해머냥이 (피10·공격3, 망치 내려치기, 모루냥이 스테이지에 1~3마리 무작위, `entity/Enemy.kt` HammerCat, 2026-09-20)
- 고양이 갑옷 = 갑옷 입은 별도 스프라이트로 그림 교체 (`make_armor_sprites.py`, 2026-09-20). 상점 아이콘 `shop_cat_armor.png` 는 옛 가슴판 그림 그대로 — 요청 있으면 새 갑옷 모양으로 재생성.
- 치즈냥이 털 무늬 5종 (노랑이·하양이·삼색이·턱시도·고등어 — `engine/CatCoat.kt`, 적 무작위·감옥별 고정·팀 명단 `coat` 저장, 2026-09-20)
- 고양이 기술 3종 (토네이도·연속발사·젤리발바닥 — 출전 냥이가 씀)
- 인간 기술 3종 (돌던지기·파이어 펀치·미사일)
- 설정 화면: **데이터 리셋** (루비·점수·물건·깬 스테이지·도감·팀 냥이·배운 기술 전부 삭제, 3초 안에 두 번 눌러 확인) — 밸런스 테스트용. `GameProgress.resetAll()`이 저장소를 통째로 비운다.

로비의 아직 빈 칸: **인터넷 · 미션 · 상점(오른쪽 칸)** — "준비 중" 안내만 뜹니다.

## 확인이 필요한 결정 (사용자에게 물어볼 것)

- **파이어 펀치 "10초 뒤 대미지 1"**: 지금은 10초 동안 불붙은 표시 후 한 번 1 피해. 초당 1씩 들어가는 화상을 원했을 수도 있음.
- **총냥이 배치(초원 8·9·10)·발사 간격 2초·사거리 520**: Claude가 정한 값. 사용자는 "공격 2·HP 15·권총"만 지정. 배치는 물어보는 중.
- **하늘 스테이지 추락 2.4초·탑승 냥이 영구 사망·구름 아군 자동 사격 1.1초·사거리 760·LEVEL=처치/10**: Claude가 정한 값. 사용자는 "화면 구성 같게·버튼 유지·고른 인간·아군은 구름 타고 뒤에서·탑승 냥이 죽으면 구름 사라지고 떨어짐"만 지정.
- **도둑냥이 도둑질 간격 4초·도망 2.5초, 잡으면 되찾기**: Claude가 정한 값. 사용자는 "2루비 훔침·레이저 없음·스테이지당 1마리·도둑처럼 생김"만 지정.
- **해머냥이 마릿수 1~3 무작위** (모루냥이 스테이지에서만): 범위는 Claude가 정한 값. 사용자는 "모루냥이 나오는 곳에만, 무작위"만 지정.
- **루비 환율 500점 = 1루비**: Claude가 정한 값.
- **기술 쿨타임들**: 토네이도 20초, 연속발사 12초, 젤리발바닥 10초, 돌던지기 2초, 미사일 6초 — 모두 Claude 판단.
- **밸런스**: 공격력 Lv업(+60) + 레벨 보너스로 냥이 한 방이 적 체력(15~75)을 크게 넘습니다. 사용자 수치 그대로라 조정은 요청이 있을 때만.
- **인간 기술 버튼**이 JUMP 버튼과 틈이 좁습니다(약 6px). 누르기 불편하다고 하면 위치 조정.
- **팀 냥이가 적 한 방에 영구 사망할 수 있음**: 팀 냥이 체력은 `20 + 5×(Lv-1)`(돌봄 보너스 최대 +25%)인데
  적의 공격력은 레이저냥이 30 · 모루냥이 10 · 마법사냥이 250입니다. Lv1 냥이는 레이저냥이 한 방(갑옷 없으면)에,
  20스테이지 마법사냥이는 갑옷을 입혀도 한 방에 죽습니다(무덤 → 부활 약 200루비).
  냥이 체력을 올릴지, 냥이는 죽지 않고 기절만 할지 등은 사용자 결정이 필요합니다.
  (인간은 영향 없음 — 인간은 잡몹 접촉 1, 보스 3, 탄 1로 하트만 깎입니다.)

## 정리하면 좋을 것 (기능 변화 없음, 요청하면 처리)

- `entity/Enemy.kt`의 `HumanCatBoss`·`EvilCatBoss`는 예전 탑뷰 시절 보스라 지금 아무 스테이지도 쓰지 않습니다
  (`Rewards.kt`의 보스 판정에만 이름이 남아 있음). 하늘 11스테이지 왕왕냥이는 `SkyStageScreen`이 따로 그립니다.
- 저장소 루트의 `generate_sprites.py`는 옛 스크립트로, 지금 쓰지 않는 `res/drawable`에 그림을 씁니다.
  현행 파이프라인은 `scripts/`이고 결과는 `res/drawable-nodpi`입니다.
- `AGENTS.md`(Codex용 안내, `CLAUDE.md`와 내용 동일)는 아직 git에 올리지 않은 상태입니다.

## 참고 코드 위치

| 기능 | 파일 |
|---|---|
| 저장(루비·아이템·해금·선택 인간) | `engine/GameProgress.kt` |
| 팀 냥이 로스터·돌봄·하트 감소 | `engine/CatTeam.kt`, `screen/TeamScreen.kt` |
| 상점 카탈로그 | `engine/ShopCatalog.kt`, `screen/CatShopScreen.kt` |
| 고양이 기술 / 인간 기술 | `engine/CatSkills.kt`, `engine/HumanSkills.kt`, `engine/SkillBook.kt`, `screen/SkillBookScreen.kt` |
| 인간 종류·장식 | `engine/Heroes.kt`, `ui/HeroDraw.kt`, `ui/HandTable.kt` |
| 전투 전체 (투사체·폭발·기술 효과·화상) | `screen/GamePlayScreen.kt` |
| 하늘 스테이지 | `screen/SkyStageScreen.kt` |
| 스테이지 구성 | `level/Level.kt` |

### 정적 치즈냥이(cat_normal) 다시 뽑는 스니펫

```python
import sys; sys.path.insert(0, 'scripts')
from PIL import Image
from rebuild_anim import cells_of, clean_frame, trim
from sheet_to_sprites import keep_largest_blob
raw = keep_largest_blob(cells_of('Plan/cat_frames5/normal_idle.png')[1])
hi = trim(clean_frame(raw, outline_min_area=1200, stripe_min=40, noise_max=22, has_teal=False))
hi.save('Plan/cat_sprites6_auto_normal_cat.png')
hi.resize((int(hi.width * 91 / hi.height), 91), Image.LANCZOS).save('app/src/main/res/drawable-nodpi/cat_normal.png')
```
