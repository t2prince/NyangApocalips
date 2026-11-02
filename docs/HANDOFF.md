# 인수인계 — 다른 PC에서 이어서 작업하기

마지막 갱신: 2026-10-31

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
- 총냥이 (피15·공격2, 등에 총·보라탄, 레이저냥이 자리 50:50, `entity/Enemy.kt` GunCat, 2026-09-20)
- 하늘 스테이지 개편: 공용 HUD(`ui/HUD.kt` Data), 고른 인간 탑승, 아군 구름 추종, 추락 연출, 날아냥이 하늘 전용 (2026-09-20)
- 도둑냥이 개편 (눈가리개·보따리 그림, 레이저 제거, 접촉 시 루비 2개 도둑질·처치 시 회수, 스테이지당 1마리, 2026-09-20)
- 해머냥이 (피10·공격3, 망치 내려치기, 모루냥이 스테이지에 1~3마리 무작위, `entity/Enemy.kt` HammerCat, 2026-09-20)
- 고양이 갑옷 = 갑옷 입은 별도 스프라이트로 그림 교체 (`make_armor_sprites.py`, 2026-09-20). 상점 아이콘 `shop_cat_armor.png` 는 옛 가슴판 그림 그대로 — 요청 있으면 새 갑옷 모양으로 재생성.
- 치즈냥이 털 무늬 5종 (노랑이·하양이·삼색이·턱시도·고등어 — `engine/CatCoat.kt`, 적 무작위·감옥별 고정·팀 명단 `coat` 저장, 2026-09-20)
- 고양이 기술 3종 (토네이도·연속발사·젤리발바닥 — 출전 냥이가 씀)
- 인간 기술 4종 (돌던지기·파이어 펀치·미사일·돌벽) — 전투 버튼 4칸 (돌벽 2026-09-25)
- 노랑냥이 번개: 인간 머리 위 먹구름에서 떨어진다 (2026-09-25)
- 설정 화면: **데이터 리셋** (루비·점수·물건·깬 스테이지·도감·팀 냥이·배운 기술 전부 삭제, 3초 안에 두 번 눌러 확인) — 밸런스 테스트용. `GameProgress.resetAll()`이 저장소를 통째로 비운다.

- 2026-09-27 ~ 10-04 추가 (자세한 규칙은 `docs/GAME_RULES.md`):
  - 갑옷 그림을 원안대로 다시 그림(후드형 투구·철판 명암, `scripts/make_armor_sprites.py`)
  - 스테이지마다 다른 지형(`engine/Terrain.kt` Profile), 공중 벽돌 발판 삭제
  - 스토리 화면: 처음 켰을 때 한 번만(2026-10-08), 그림은 GPT로 다시 그림
  - 적: 노랑냥이 하늘 먹구름 번개(빠르게·더 많이), 빨간냥이 불, 주황냥이 용암, 머드냥이(늪), 새끼 고양이(하트 반 칸), 벌냥이·꽃냥이
  - **상점 마을**(로비 '상점'): 스테이지처럼 걸어 다니는 초원, 천막 6개, 인간 한 명 구할 때마다 천막 하나 열림, 가게 주인 광고, 1번 천막 = 고양이 상점(로비 '고양이 상점' 칸 삭제), 출전 냥이 동행 — `screen/ShopScreen.kt`
  - 팀 냥이 교배·새끼(20분마다 50%), 감옥 새끼, Lv5 에 다 자람 — `engine/CatTeam.kt`
  - **31 해적 스테이지**(배냥이·해적냥이 보스·토네이도 함정·해적선, 공격은 Claude 판단) — `PLAYABLE_STAGES = 31`
  - 갑옷·벌냥이·머드냥이·해적냥이·배냥이·해적선은 GPT 그림(`docs/ART_PIPELINE.md` GPT 절)
  - **21~30 꿀 지역**(벌집 바닥·벌냥이·꽃냥이, 22~30 구성은 Claude 판단) — `PLAYABLE_STAGES = 30`
  - 날아냥이가 무덤이면 11 에 못 들어감, 스토리 화면 삭제, 해적냥이·배냥이·해적선 그림(31 용)
- 2026-10-25 **51~60 빙산** (`GameProgress.ICE_STAGES`, 32~50 은 '준비 중', 31 을 깨면 51 이 열림): 얼음냥이(`IceCat`)·잡몹 순간냥이(`TeleportCat`)·60 보스 빙산냥이(`IcebergCatBoss`) — `entity/Enemy.kt` 끝, 수직 구덩이(`Chasm(shaft = true)`)·얼음 구덩이(`entity/IceHole.kt`), 인간 얼음 상태(`Player.freeze`), 우리 편 얼음냥이·순간냥이(`AllyCat.Kind.ICE`·`TELEPORT`), 도감 `ice`·`iceberg` + 도감 쪽 넘기기(24칸씩 ◀ ▶ — 전엔 24칸 넘는 냥이가 안 보였음). 그림 `cat_ice`·`cat_ice_ally`·`cat_ice_armor_ally`·`cat_iceberg_boss`(없으면 코드 그림). 우리 편 도둑냥이가 적의 루비 2개를 훔쳐 온다. 값은 거의 Claude 판단 — `docs/GAME_RULES.md` 빙산 절.
- 2026-11-02 **나누(PlayNANOO) 세이브 동기화** (「나누로 옮길거야 데이터저장」「그게 있다고 가정하고 작업 먼저해. 키 없으면 동작안하게하고」) — `engine/NanooSync.kt`:
  - 나누는 안드로이드 SDK 가 없다 → 유니티 DLL(에듀아바타 `Assets/PlayNANOO/PlayNANOOAndroid.dll`)을 `monodis` 로 풀어 본 REST 를 그대로 부른다. 모두 `PUT` JSON, 헤더 `X-Playnanoo-Key`(Service Key)·`X-Playnanoo-Id`(Game ID)·로그인 뒤 `Authorization: Bearer`. 본문에 공통 값(device_id·platform `aos`·version…)을 붙인다.
  - 게스트 로그인 `service-account.playnanoo.com/api/v20240401/guest/signin` (`linked_id` = ANDROID_ID → 같은 기기는 재설치해도 같은 계정). 저장 `service-storage-api.playnanoo.com/storage/v20221001/save`·`/load`, 키 `save`, 값 = 게임 저장소(`nyang_games`·`nyang_progress*`) 전부를 JSON 하나로. 내가 만든 캐릭터·스테이지 편집은 올리지 않는다.
  - 오류는 HTTP 500 + `{"ErrorCode","Message"}`: 10006 = 서버에 세이브 없음, 30002 = 토큰 끝남(하루) → 다시 로그인. 키 `test`/`test` 로 로그인·저장·받기를 curl 로 실제 확인함(2026-11-02, 1MB 도 저장됨).
  - 규칙(Claude 판단, 코드 리뷰 반영): 계정이 기기 ID 에 묶여 서버 세이브 = 이 기기가 마지막으로 올린 것. 나누와 한 번이라도 맞춘 기기(저장소 `nanoo` 의 `synced`, **백업 규칙에서 뺐다**)는 기기 세이브가 기준 — 앱을 내릴 때·3분마다 바뀌면 올림. 맞춘 적 없는 기기(새로 설치·앱 데이터 삭제·자동 백업으로 되살림)에 서버 세이브가 있으면 서버 것으로 되살림 — 켠 직후 화면(로고·로딩·스토리·시작)에서만, 게임 스레드를 멈춘 뒤, 실패하면 원래대로 되돌림. 덮기 전 기기 세이브는 `files/nanoo_backup_before_restore.json`. 서버 세이브를 받기 전에는 올리지 않음.
  - 한계: 기기 ID(ANDROID_ID)는 서명 키마다 달라 **디버그 판과 플레이 판은 다른 계정**, 새 폰·공장 초기화도 새 계정. 기기를 옮겨 이으려면 구글 로그인(나누 `social/signin`)을 붙여야 함 — 사용자 결정 필요.
  - 키: `~/.nyangapo/build.env` 에 `NANOO_GAME_ID=`·`NANOO_SERVICE_KEY=` 를 넣고 빌드 → `BuildConfig`. **없으면 기능 전체가 꺼짐**(지금 상태). Secret Key 는 앱에 넣지 않는다. logcat 태그 `Nanoo`.
  - 인터넷 권한(`INTERNET`)을 이때 처음 넣었다.
- 2026-11-02 **플레이 콘솔 배포 준비** (「에듀아바타처럼 구글 클라우드로 배포하게 준비하자」 — 에듀아바타 `Tools/aab_build.sh`·`play_upload.py` 방식):
  - `Tools/aab_build.sh`: `version.properties` 의 판 번호(VERSION_CODE)를 올리고 `bundleRelease` → `~/dev/release/nyangapo/NyangApocalips.aab` → 서비스 계정으로 내부 테스트 트랙에 올림 (`UPLOAD=0` 굽기만, `STATUS=draft` 초안, `BUMP=0` 번호 그대로). 출시 노트 = 지난 업로드 뒤 커밋 제목.
  - 서명: 업로드 키 `~/.nyangapo/keystore/upload.keystore`, 비밀번호 `~/.nyangapo/build.env` (저장소 밖, 0600). **잃어버리지 않게 백업** (잃으면 콘솔에서 업로드 키 재설정 요청).
  - 서비스 계정 키: `~/.nyangapo/play-service-account.json`, 없으면 에듀아바타 키(`~/.eduavatar/`)를 같이 쓴다.
  - 남은 일(사람이): 플레이 콘솔에서 앱 만들기(패키지 `com.jamcat.nayngapo`) → 서비스 계정에 이 앱 권한 → 첫 업로드는 `STATUS=draft`. 2026-11-02 `--check` 는 "Package not found"(앱이 아직 콘솔에 없음).
  - targetSdk 35 → 36 (플레이 새 앱 요구 대비 — Claude 판단). 릴리스 빌드는 디버그가 아니라 스테이지 편집이 안 보인다.
- 2026-11-02 **크래시 막기** (「크래시가 많이 나」「날 수 있는 곳들 틀어막아봐」): `GameView` 가 화면 갱신·그리기·터치·뒤로가기·새 화면 만들기에서 난 오류를 잡아 앱을 끄지 않는다. 같은 화면이 90프레임 내리 오류면 로비로(로비면 타이틀로). 모든 오류(잡은 것 + 앱이 꺼진 것)는 `engine/CrashLog.kt` 가 앱 내부 `files/crash_log.txt` 에 적는다 — 읽기: `adb shell run-as com.jamcat.nayngapo cat files/crash_log.txt` (logcat 태그 `CRASH`). 원인을 찾으면 그 자리도 고친다 — 잡아 둔 건 임시 방패.
- 2026-10-31 **스테이지 편집 화면** (디버그 빌드에서만, 로비 맨 위 오른쪽 '🛠 스테이지 편집' — `screen/StageEditorScreen.kt`): 고른 스테이지의 지도에 냥이·함정·감옥을 끌어 놓고 저장. 저장은 세이브와 따로인 `stage_edits`(`engine/StageEdits.kt`). Level 은 배치 목록(`level/StageLayout.kt` `Placement`·`PlaceType`)을 기본 생성기(`Level.defaultLayout`)로 만들거나 저장된 편집을 읽어 `Level.createEntity` 로 짓는다. 규칙·Claude 판단은 `docs/GAME_RULES.md` 스테이지 편집 절. 새 한국어 글자의 `en.json` 번역은 아직 안 채움. 11 하늘 스테이지도 편집된다: 하늘 배치는 `SkyLayout`(같은 파일, 높이 `Placement.y` 0~1 비율), `SkyStageScreen.spawnEnemies` 가 그 목록으로 냥이·감옥을 띄운다.
- 테스트 기기가 바뀐 듯함: 2026-10-04 부터 `R5CR40LCT0P` 에 설치됨(전에는 `R5CT3262MQY`). 사용자 확인 대기.

로비의 아직 빈 칸: **인터넷** — "준비 중" 안내만 뜹니다. (미션은 2026-09-20 구현: `screen/MissionScreen.kt`, `engine/Missions.kt`)

## 확인이 필요한 결정 (사용자에게 물어볼 것)

- **2026-10-25 대기 중인 질문**: 41~50 스테이지표(`Plan/기획18_스테이지41-50.jpg`)에 「이거 50번대로 바꾸고 설치해줘」「거기에 나오는 고양이들은 그냥 만들고 도감에 추가해」 — 41~50 그대로인지 51~60 인지 물어봄. 「턱시도 냥이 갑옷이 이상해」 — 어디가 이상한지 물어봄. 「하늘 스테이지는 벌냥이로도 갈 수 있어」 — 벌냥이 주인공은 이미 됨, 인간이 팀 벌냥이를 타는 뜻인지 물어봄.
- **11 보스 = 먼지냥이**(2026-10-25 「11스테이지 보스는 먼지냥이야」): 생김새·공격을 묻는 중. 지금은 왕왕냥이(`SkyStageScreen` GIANT).

- **2026-10-24 Claude 판단 값 (사용자 확인 필요)**: 조선소 배 10종류의 값·♥·공격·빠르기와 그림(어선 그림 색만 바꿈), 상점마을2 새 물건 16가지 이름·값·효과·코드 그림, 총알 그림(코드), 고양이 주인공의 능력(검은 인간과 같음)·총 사용 허용, 사진 캐릭터의 움직임(관절 11개, 조각 회전 — 오른쪽을 보는 그림 기준), 기습에 지면 배도 잃음.
- **뽑기 상자 3개(안 좋은·그냥·좋은) 기획**(2026-10-24 손그림): 상자 값, 기존 캡슐 뽑기를 바꿀지, '다른 편 고양이 3마리'·'폭탄 데미지 5'·'인간 ×1'·'진화 쿠폰'의 뜻, 그림 — 질문했지만 답을 아직 못 받아 안 만들었음.
- **상점**: 천막 수(임시 6)·2번~ 천막이 파는 물건, "냥이를 데리고 가면 가판대에 냥이 얼굴" 의 뜻(천막 앞 가판대 vs 고양이 상점 화면 안).
- **"이건 고양이 기술이 아니야!"** (2026-09-27) — 무엇을 가리키는지 답을 못 받음.
- **벌냥이 공격력**(기획서 글씨 10/0 불분명, 임시 10), 꽃가루 숨막힘의 1.5초 느려짐(Claude 판단), 21 보스 유무, 21 표의 구출 칸.
- 22~30 구성(Claude 판단) 확인, 30 보스 유무, 해적냥이·배냥이 공격, 토네이도 함정 — Claude 판단으로 만들어 둠. **51~60 빙산의 얼음냥이 능력·수직 구덩이·얼음 구덩이·51 구성·마릿수도 Claude 판단**(2026-10-25) — 확인 필요.
- 교배 20분·50%, 감옥 새끼 25%, 새끼 Lv5 성장, 새끼 고양이 배치, 노랑냥이 마릿수, 지형 모양 배정 — Claude 판단.

- **파이어 펀치 "10초 뒤 대미지 1"**: 지금은 10초 동안 불붙은 표시 후 한 번 1 피해. 초당 1씩 들어가는 화상을 원했을 수도 있음.
- **미션 16개의 목표·보상 값**: Claude가 정한 값. 사용자는 "미션 많이, 하면 루비"만 지정.
- **총냥이 발사 간격 2초·사거리 520**: Claude가 정한 값. 배치는 사용자 지정(레이저냥이 자리 50:50).
- **하늘 스테이지 추락 2.4초·탑승 냥이 영구 사망·구름 아군 자동 사격 1.1초·사거리 760·LEVEL=처치/10**: Claude가 정한 값. 사용자는 "화면 구성 같게·버튼 유지·고른 인간·아군은 구름 타고 뒤에서·탑승 냥이 죽으면 구름 사라지고 떨어짐"만 지정.
- **도둑냥이 도둑질 간격 4초·도망 2.5초, 잡으면 되찾기**: Claude가 정한 값. 사용자는 "2루비 훔침·레이저 없음·스테이지당 1마리·도둑처럼 생김"만 지정.
- **해머냥이 마릿수 1~3 무작위** (모루냥이 스테이지에서만): 범위는 Claude가 정한 값. 사용자는 "모루냥이 나오는 곳에만, 무작위"만 지정.
- **루비 환율 500점 = 1루비**: Claude가 정한 값.
- **기술 쿨타임들**: 토네이도 20초, 연속발사 12초, 젤리발바닥 10초, 돌던지기 2초, 미사일 6초, 돌벽 8초 — 모두 Claude 판단.
- **돌벽 모양·범위** (돌가시 6개, 앞쪽 약 410px)와 **노랑냥이 먹구름이 모이는 0.8초**: Claude 판단.
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

- (2026-10-17) 언어 설정 도입: 화면 글자는 모두 `drawTr`/`measureTr`(engine/I18n.kt)로 그린다 — 새 화면도 `drawText` 대신 이것을 쓴다. 새 한국어 글자를 넣으면 `python3 scripts/i18n_extract.py app/src/main/java/com/jamcat/nayngapo Plan/i18n_ko_strings.json` 후 `assets/i18n/en.json` 에 없는 것을 채운다.
