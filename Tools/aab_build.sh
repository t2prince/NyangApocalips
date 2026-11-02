#!/bin/bash
# AAB 굽기 + 플레이 콘솔 업로드 (대표님 2026-11-02 「에듀아바타처럼 구글 클라우드로 배포하게 준비하자」).
# 에듀아바타 Tools/aab_build.sh 와 같은 순서: ⓪ 판 번호 올리기 → ① bundleRelease → ② 내부 테스트에 올리기.
#
#   Tools/aab_build.sh                  번호 올리고 굽고 내부 테스트에 올림 (바로 출시)
#   UPLOAD=0 Tools/aab_build.sh         굽기만 (~/dev/release/nyangapo/NyangApocalips.aab)
#   STATUS=draft Tools/aab_build.sh     올리기만 하고 출시는 콘솔에서 — 콘솔에서 아직 한 번도 출시 안 한 앱은 draft 만 된다
#   BUMP=0 Tools/aab_build.sh           번호 그대로 다시 굽기
#
# ⚠️ 서명키 비밀번호는 저장소 밖 ~/.nyangapo/build.env 에서만 읽고 (gradle 이 읽는다) 로그에 안 찍는다.
set -u
cd "$(dirname "$0")/.."

LOG_DIR="$HOME/dev/build_logs"; mkdir -p "$LOG_DIR"
STAMP=$(date +%Y%m%d_%H%M)
LOG="$LOG_DIR/nyangapo_aab_$STAMP.log"
OUT_DIR="$HOME/dev/release/nyangapo"; mkdir -p "$OUT_DIR"
say() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG"; }

if [ ! -f "$HOME/.nyangapo/build.env" ]; then say "✕ ~/.nyangapo/build.env 가 없다 — 서명할 수 없어 멈춘다"; exit 1; fi

VP=version.properties
if [ "${BUMP:-1}" = "1" ]; then
  CODE=$(( $(awk -F= '/^VERSION_CODE=/{print $2}' "$VP") + 1 ))
  sed -i '' "s/^VERSION_CODE=.*/VERSION_CODE=$CODE/" "$VP"
  say "⓪ 판 번호 → $CODE"
fi
CODE=$(awk -F= '/^VERSION_CODE=/{print $2}' "$VP")
VER=$(awk -F= '/^VERSION_NAME=/{print $2}' "$VP")
say "── AAB 굽기 ── v$VER ($CODE)"

if ./gradlew bundleRelease -q >>"$LOG" 2>&1; then
  cp app/build/outputs/bundle/release/app-release.aab "$OUT_DIR/NyangApocalips.aab"
  say "  ✅ $(ls -lh "$OUT_DIR/NyangApocalips.aab" | awk '{print $5}') → $OUT_DIR/NyangApocalips.aab"
else
  say "  ✕ 굽기 실패 (기록: $LOG)"; exit 2
fi

if [ "${UPLOAD:-1}" = "1" ]; then
  say "② 플레이 콘솔 내부 테스트에 올리기"
  NOTES="$LOG_DIR/nyangapo_notes_$STAMP.txt"
  LAST="$HOME/.nyangapo/last_upload_commit"
  # 출시 노트 = 지난 업로드 뒤 커밋 제목 (feat/fix 머리말 떼고)
  if [ -f "$LAST" ]; then RANGE="$(cat "$LAST")..HEAD"; else RANGE="-15"; fi
  git log --no-merges --format='%s' $RANGE 2>/dev/null | sed -E 's/^(feat|fix|refactor|docs|chore)(\([^)]*\))?: //' | head -15 | sed 's/^/• /' > "$NOTES"
  if python3 Tools/play_upload.py --name "$VER ($CODE)" --notes-file "$NOTES" --status "${STATUS:-completed}" >>"$LOG" 2>&1; then
    git rev-parse HEAD > "$LAST"
    say "  ✅ 내부 테스트 $VER ($CODE) ${STATUS:-completed}"
  else
    say "  ✕ 콘솔 업로드 실패 — $(tail -3 "$LOG" | tr '\n' ' ')"; exit 3
  fi
fi
say "── 끝 ── 기록: $LOG"
