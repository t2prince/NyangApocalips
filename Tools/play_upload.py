#!/usr/bin/env python3
"""구운 AAB 를 플레이 콘솔 테스트 트랙에 올린다.

대표님 2026-11-02 「에듀아바타처럼 구글 클라우드로 배포하게 준비하자」 — 에듀아바타 Tools/play_upload.py 를 그대로 가져왔다

게임에 들어가는 것은 없다 — 맥에서 Google Play Developer API 를 부를 뿐이다.
파이썬 기본 라이브러리 + openssl + curl 만 쓴다 (따로 깔 것 없음).

  play_upload.py --check            권한만 확인한다 (아무것도 안 올린다)
  play_upload.py                    내부 테스트에 올리고 바로 출시
  play_upload.py --status draft     올리기만 하고 출시는 콘솔에서 손으로

⚠️ 키는 저장소 밖 ~/.nyangapo (없으면 ~/.eduavatar) 의 play-service-account.json 에서만 읽고, 로그에 찍지 않는다.
"""
import argparse
import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

PACKAGE = "com.jamcat.nayngapo"
# 냥아포 전용 키가 있으면 그것, 없으면 에듀아바타와 같은 개발자 계정 서비스 계정 키를 같이 쓴다
KEY_FILE = next((p for p in map(os.path.expanduser, ["~/.nyangapo/play-service-account.json", "~/.eduavatar/play-service-account.json"]) if os.path.isfile(p)), os.path.expanduser("~/.nyangapo/play-service-account.json"))
AAB_FILE = os.path.expanduser("~/dev/release/nyangapo/NyangApocalips.aab")
API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" + PACKAGE
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/" + PACKAGE
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def b64url(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=")


def access_token():
    with open(KEY_FILE) as f:
        key = json.load(f)
    now = int(time.time())
    header = b64url(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    claim = b64url(json.dumps({
        "iss": key["client_email"], "scope": SCOPE, "aud": key["token_uri"],
        "iat": now, "exp": now + 3600,
    }).encode())
    signing_input = header + b"." + claim
    # 개인키는 잠깐 임시 파일(0600)로만 내려놓고 서명 뒤 바로 지운다
    fd, pem = tempfile.mkstemp()
    try:
        os.write(fd, key["private_key"].encode())
        os.close(fd)
        sig = subprocess.run(["openssl", "dgst", "-sha256", "-sign", pem],
                             input=signing_input, capture_output=True, check=True).stdout
    finally:
        os.remove(pem)
    jwt = signing_input + b"." + b64url(sig)
    body = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": jwt.decode(),
    }).encode()
    with urllib.request.urlopen(urllib.request.Request(key["token_uri"], data=body), timeout=60) as r:
        return json.load(r)["access_token"]


def call(token, method, url, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + token)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            raw = r.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raise SystemExit("✕ %s %s → %d\n%s" % (method, url.replace(API, ""), e.code, e.read().decode()[:2000]))


def upload_bundle(token, edit_id, path):
    # curl 로 흘려 보낸다 (메모리에 통째로 안 올린다)
    url = "%s/edits/%s/bundles?uploadType=media" % (UPLOAD, edit_id)
    out = subprocess.run(
        ["curl", "-sS", "--fail-with-body", "--max-time", "3600", "-X", "POST",
         "-H", "Authorization: Bearer " + token,
         "-H", "Content-Type: application/octet-stream",
         "--data-binary", "@" + path, url],
        capture_output=True, text=True)
    if out.returncode != 0:
        raise SystemExit("✕ 묶음 올리기 실패\n%s\n%s" % (out.stdout[:2000], out.stderr[:500]))
    return json.loads(out.stdout)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="권한만 확인하고 아무것도 올리지 않는다")
    ap.add_argument("--track", default="internal")
    ap.add_argument("--status", default="completed", choices=["completed", "draft"])
    ap.add_argument("--aab", default=AAB_FILE)
    ap.add_argument("--name", default=None, help="출시 이름 (예: 1.0.2 (190))")
    ap.add_argument("--notes-file", default=None, help="출시 노트(한국어) 글 파일 — 콘솔 「이 버전의 새로운 기능」 (500자까지)")
    args = ap.parse_args()

    if not os.path.isfile(KEY_FILE):
        raise SystemExit("✕ %s 가 없다 — 올릴 수 없다" % KEY_FILE)

    token = access_token()
    edit_id = call(token, "POST", API + "/edits")["id"]

    if args.check:
        tracks = call(token, "GET", "%s/edits/%s/tracks" % (API, edit_id)).get("tracks", [])
        for t in tracks:
            for rel in t.get("releases", []):
                print("  %-12s %-10s %s  판번호 %s" % (t["track"], rel.get("status"), rel.get("name", ""),
                                                   ",".join(rel.get("versionCodes", []))))
        call(token, "DELETE", "%s/edits/%s" % (API, edit_id))
        print("✅ 권한 확인 — 아무것도 바꾸지 않았다")
        return

    if not os.path.isfile(args.aab):
        raise SystemExit("✕ %s 가 없다" % args.aab)

    print("올리는 중: %s (%.0fMB)" % (args.aab, os.path.getsize(args.aab) / 1e6), flush=True)
    code = upload_bundle(token, edit_id, args.aab)["versionCode"]
    release = {"versionCodes": [str(code)], "status": args.status}
    if args.name:
        release["name"] = args.name
    # 콘솔 출시 노트에 바뀐 것을 적는다
    if args.notes_file and os.path.isfile(args.notes_file):
        with open(args.notes_file, encoding="utf-8") as f:
            text = f.read().strip()
        if text:
            release["releaseNotes"] = [{"language": "ko-KR", "text": text[:500]}]
    call(token, "PUT", "%s/edits/%s/tracks/%s" % (API, edit_id, args.track),
         {"track": args.track, "releases": [release]})
    call(token, "POST", "%s/edits/%s:commit" % (API, edit_id))
    print("✅ 판번호 %s → %s 트랙 (%s)" % (code, args.track, args.status))


if __name__ == "__main__":
    sys.exit(main())
