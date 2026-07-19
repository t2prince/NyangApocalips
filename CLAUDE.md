# NyangApocalips - Project Guide

## Project Overview
Android 2D 격투 게임 (Kotlin, Gradle). 고양이 캐릭터가 적과 싸우는 횡스크롤 액션 게임.

## Tech Stack
- Language: Kotlin
- Build: Gradle (AGP)
- Platform: Android (compileSdk 36)
- UI: Custom SurfaceView + Canvas 기반 게임 렌더링

## Key Architecture
- `entity/` - Player, Enemy 등 게임 엔티티
- `ui/` - TouchControls 등 UI 컴포넌트
- `GameView.kt` - 메인 게임 루프 및 렌더링
- 스프라이트 기반 캐릭터 애니메이션 (Pillow 생성 이미지)

## Agent Orchestration
글로벌 오케스트레이션 전략은 `~/.claude/CLAUDE.md` 참조.
이 프로젝트 고유 위임 규칙:
- 게임 로직 설계 (충돌 판정, 물리 엔진, AI 패턴) → Claude
- 스프라이트/에셋 관련 반복 코드 → Codex

## Build & Run
```bash
./gradlew assembleDebug          # Debug APK 빌드
./gradlew installDebug           # 디바이스에 설치
./gradlew test                   # 단위 테스트
```

## Conventions
- 커밋 메시지: 한국어, `feat:` / `fix:` / `refactor:` 접두사
- 코드 주석: 한국어 허용
- 변수/함수명: 영어 camelCase (Kotlin convention)
