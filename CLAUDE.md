# NyangApocalips - Project Guide

## 먼저 읽을 것
이어서 작업할 때는 아래 문서를 먼저 읽는다. PC가 바뀌면 Claude 자동 메모리가 없으므로 이 문서들이 기준이다.
- `docs/HANDOFF.md` — 지금 상태, 사용자와 일하는 방식, **기기 세이브 주의**, 확인이 필요한 결정
- `docs/GAME_RULES.md` — 사용자가 확정한 게임 규칙 (임의로 바꾸지 말 것)
- `docs/ART_PIPELINE.md` — 그림 방향과 스크립트 실행 순서
새 규칙이 확정되면 `docs/GAME_RULES.md`를, 상태가 바뀌면 `docs/HANDOFF.md`를 같이 고친다.

## Project Overview
Android 횡스크롤 액션 플랫포머 (Kotlin, Gradle). 인간 주인공이 냥이 군단을 무찌르고 감옥의 인간·냥이를 구출하며,
구한 냥이를 팀으로 키워 함께 싸우는 게임. 기획은 손그림 기획서(`Plan/`).

## Tech Stack
- Language: Kotlin / Build: Gradle (AGP) / Platform: Android (compileSdk 36)
- UI: Custom SurfaceView + Canvas 게임 렌더링 (XML 레이아웃 없음)
- 그림: `app/src/main/res/drawable-nodpi/` — `scripts/`의 Python(Pillow, numpy) 파이프라인이 생성

## Key Architecture
- `engine/` — `GameView`(게임 루프·화면 전환), `GameProgress`(SharedPreferences 저장), `CatTeam`, 상점·기술 카탈로그, `SpriteManager`
- `entity/` — `Player`, `Enemy`(냥이 종류별 클래스), `AllyCat`, `Projectile`, 함정·감옥·덩굴·토네이도
- `level/Level.kt` — 스테이지 구성 (기획5 표가 주석에 있음)
- `screen/` — 화면 하나당 클래스 하나 (`Screen` 인터페이스: update/draw/onTouchEvent/onBack)
- `ui/` — HUD, `TouchControls`, `HeroDraw`(인간 색·장식), `HandTable`(생성 파일, 손으로 고치지 말 것)

## Build & Run
```bash
./gradlew assembleDebug          # Debug APK 빌드
./gradlew installDebug           # 디바이스에 설치
./gradlew test                   # 단위 테스트
```

## Conventions
- 대화·커밋 메시지: 한국어, `feat:` / `fix:` / `refactor:` 접두사
- 코드 주석: 한국어 허용 / 변수·함수명: 영어 camelCase
- 사용자가 정하지 않은 기능·기술·캐릭터를 임의로 추가하지 않는다. 정하지 않은 세부값(쿨타임 등)은 정하고 사용자에게 알린다.
- **테스트 기기의 세이브에 테스트 데이터를 넣지 않는다** (사용자가 실제로 플레이 중). 사용 중이면 빌드·설치만 한다.

## Agent Orchestration
글로벌 오케스트레이션 전략은 `~/.claude/CLAUDE.md` 참조 (없는 PC에서는 무시해도 됨).
이 프로젝트 고유 위임 규칙:
- 게임 로직 설계 (충돌 판정, 물리 엔진, AI 패턴) → Claude
- 스프라이트/에셋 관련 반복 코드 → Codex 또는 서브에이전트
