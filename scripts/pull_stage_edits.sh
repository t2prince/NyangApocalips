#!/bin/bash
# 폰의 스테이지 편집(stage_edits)을 뽑아 앱에 넣는다 → app/src/main/assets/stage_layouts/stage_N.json
# 그 다음 빌드부터는 모든 빌드(릴리스 포함)가 같은 배치를 쓴다. 디버그 빌드가 깔린 폰이어야 한다 (run-as).
# 쓰는 법: scripts/pull_stage_edits.sh   (폰에 없는 스테이지 파일은 지우지 않는다)
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=app/src/main/assets/stage_layouts
mkdir -p "$OUT"
TMP=$(mktemp)
adb exec-out run-as com.jamcat.nayngapo cat shared_prefs/stage_edits.xml > "$TMP"
python3 - "$TMP" "$OUT" <<'PY'
import json, sys, xml.etree.ElementTree as ET
src, out = sys.argv[1], sys.argv[2]
n = 0
for el in ET.parse(src).getroot().findall('string'):
    name = el.get('name', '')
    if not name.startswith('stage_'): continue
    layout = json.loads(el.text or '[]')
    with open(f'{out}/{name}.json', 'w') as f:
        json.dump(layout, f, ensure_ascii=False, indent=1)
    print(f'{name}: {len(layout)}개 배치')
    n += 1
print(f'스테이지 {n}개를 {out} 에 넣었다')
PY
rm -f "$TMP"
