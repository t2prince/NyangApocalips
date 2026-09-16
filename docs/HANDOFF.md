# 인수인계 — 다른 PC에서 이어서 작업하기

마지막 갱신: 2026-09-13

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
- 고양이 기술 3종 (토네이도·연속발사·젤리발바닥 — 출전 냥이가 씀)
- 인간 기술 3종 (돌던지기·파이어 펀치·미사일)
- 설정 화면: **데이터 리셋** (루비·점수·물건·깬 스테이지·도감·팀 냥이·배운 기술 전부 삭제, 3초 안에 두 번 눌러 확인) — 밸런스 테스트용. `GameProgress.resetAll()`이 저장소를 통째로 비운다.

로비의 아직 빈 칸: **인터넷 · 미션 · 상점(오른쪽 칸)** — "준비 중" 안내만 뜹니다.

## 확인이 필요한 결정 (사용자에게 물어볼 것)

- **파이어 펀치 "10초 뒤 대미지 1"**: 지금은 10초 동안 불붙은 표시 후 한 번 1 피해. 초당 1씩 들어가는 화상을 원했을 수도 있음.
- **루비 환율 500점 = 1루비**: Claude가 정한 값.
- **기술 쿨타임들**: 토네이도 20초, 연속발사 12초, 젤리발바닥 10초, 돌던지기 2초, 미사일 6초 — 모두 Claude 판단.
- **밸런스**: 공격력 Lv업(+60) + 레벨 보너스로 냥이 한 방이 적 체력(15~75)을 크게 넘습니다. 사용자 수치 그대로라 조정은 요청이 있을 때만.
- **인간 기술 버튼**이 JUMP 버튼과 틈이 좁습니다(약 6px). 누르기 불편하다고 하면 위치 조정.

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
