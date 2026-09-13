#!/usr/bin/env python3
"""배경음악(BGM) 생성 — 밝고 경쾌한 칩튠 루프.

C장조 · 132BPM · C→G→Am→F 진행에 스퀘어 멜로디 + 베이스 + 가벼운 드럼.
끝과 처음이 자연스럽게 이어지도록 마디 단위로 딱 떨어지게 만든다.
출력: app/src/main/res/raw/bgm_stage.wav (모노 22.05kHz, 용량 절약)
"""

import math
import wave
from pathlib import Path

import numpy as np

SR = 22050
BPM = 132.0
BEAT = 60.0 / BPM          # 한 박(초)
BAR = BEAT * 4
ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "app" / "src" / "main" / "res" / "raw"


def note_hz(semitone_from_a4):
    return 440.0 * (2.0 ** (semitone_from_a4 / 12.0))


# 음이름 → A4 기준 반음 거리
NAMES = {"C": -9, "D": -7, "E": -5, "F": -4, "G": -2, "A": 0, "B": 2}


def hz(name):
    """'C4', 'A#3' 같은 표기를 주파수로"""
    letter = name[0]
    rest = name[1:]
    sharp = rest.startswith("#")
    if sharp:
        rest = rest[1:]
    octave = int(rest)
    semi = NAMES[letter] + (1 if sharp else 0) + (octave - 4) * 12
    return note_hz(semi)


def square(freq, dur, duty=0.5, vol=0.25, attack=0.005, release=0.05):
    """부드럽게 시작하고 끝나는 스퀘어 웨이브 한 음"""
    n = max(1, int(dur * SR))
    t = np.arange(n) / SR
    phase = (t * freq) % 1.0
    wave_ = np.where(phase < duty, 1.0, -1.0)

    env = np.ones(n)
    a = min(n, max(1, int(attack * SR)))
    r = min(n - a, max(1, int(release * SR)))
    env[:a] = np.linspace(0.0, 1.0, a)
    if r > 0:
        env[-r:] = np.linspace(1.0, 0.0, r)
    return wave_ * env * vol


def triangle(freq, dur, vol=0.3, release=0.04):
    n = max(1, int(dur * SR))
    t = np.arange(n) / SR
    phase = (t * freq) % 1.0
    wave_ = 4.0 * np.abs(phase - 0.5) - 1.0
    env = np.ones(n)
    r = max(1, int(release * SR))
    env[-r:] = np.linspace(1.0, 0.0, r)
    env[:64] = np.linspace(0.0, 1.0, 64)
    return wave_ * env * vol


def kick(dur=0.13, vol=0.5):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = 120.0 * np.exp(-t * 26.0) + 46.0
    return np.sin(2 * math.pi * np.cumsum(f) / SR) * np.exp(-t * 17.0) * vol


def snare(dur=0.12, vol=0.28, rng=None):
    n = int(dur * SR)
    t = np.arange(n) / SR
    rng = rng or np.random.default_rng(7)
    noise = rng.normal(0, 1, n)
    noise = np.concatenate(([0.0], noise[1:] - 0.5 * noise[:-1]))   # 살짝 밝게
    tone = np.sin(2 * math.pi * 210 * t) * 0.35
    return (noise + tone) * np.exp(-t * 26.0) * vol


def hat(dur=0.045, vol=0.10, rng=None):
    n = int(dur * SR)
    t = np.arange(n) / SR
    rng = rng or np.random.default_rng(11)
    noise = rng.normal(0, 1, n)
    noise = np.concatenate(([0.0], noise[1:] - 0.9 * noise[:-1]))
    return noise * np.exp(-t * 90.0) * vol


def add(track, start_sec, samples):
    i = int(start_sec * SR)
    end = min(len(track), i + len(samples))
    if i >= len(track):
        return
    track[i:end] += samples[: end - i]


def main():
    rng = np.random.default_rng(2026)

    bars = 16
    total = int(bars * BAR * SR) + SR // 4
    track = np.zeros(total)

    # C → G → Am → F (밝고 씩씩한 진행)
    chords = [
        ("C3", ["C4", "E4", "G4"]),
        ("G2", ["B3", "D4", "G4"]),
        ("A2", ["A3", "C4", "E4"]),
        ("F2", ["F3", "A3", "C4"]),
    ]

    # 마디마다 반복되는 경쾌한 멜로디 (마디 안에서 8분음표 8개)
    melodies = [
        ["G4", "E4", "G4", "C5", "B4", "G4", "A4", "G4"],
        ["D5", "B4", "G4", "B4", "D5", "B4", "A4", "G4"],
        ["C5", "A4", "E4", "A4", "C5", "E5", "D5", "C5"],
        ["A4", "F4", "A4", "C5", "F5", "E5", "D5", "C5"],
    ]

    for bar in range(bars):
        bar_t = bar * BAR
        bass_name, chord_notes = chords[bar % 4]

        # 베이스: 박마다 통통 튀게 (3박은 5도로 살짝 변화)
        for beat in range(4):
            f = hz(bass_name) * (1.5 if beat == 2 else 1.0)
            add(track, bar_t + beat * BEAT, triangle(f, BEAT * 0.9, vol=0.30))

        # 코드 반주: 2·4박 뒷박에 짧게 (밝은 경쾌함)
        for beat in (0, 1, 2, 3):
            for name in chord_notes:
                add(track, bar_t + beat * BEAT + BEAT * 0.5,
                    square(hz(name), BEAT * 0.32, duty=0.25, vol=0.055))

        # 멜로디: 8분음표 (앞 8마디는 한 번, 뒤 8마디는 한 옥타브 위 장식)
        mel = melodies[bar % 4]
        octave = 2.0 if bar >= 8 and bar % 4 == 3 else 1.0
        for i, name in enumerate(mel):
            add(track, bar_t + i * BEAT * 0.5,
                square(hz(name) * octave, BEAT * 0.46, duty=0.5, vol=0.20))

        # 드럼: 킥(1·3박) · 스네어(2·4박) · 하이햇(8분음표)
        add(track, bar_t + 0 * BEAT, kick())
        add(track, bar_t + 2 * BEAT, kick())
        add(track, bar_t + 1 * BEAT, snare(rng=rng))
        add(track, bar_t + 3 * BEAT, snare(rng=rng))
        for i in range(8):
            add(track, bar_t + i * BEAT * 0.5, hat(rng=rng))

    # 루프 길이에 딱 맞춰 자르고, 이음매 클릭을 없애려 아주 짧게 크로스페이드
    loop_len = int(bars * BAR * SR)
    tail = track[loop_len:loop_len + int(0.02 * SR)]
    out = track[:loop_len].copy()
    if len(tail) > 0:
        out[:len(tail)] += tail                      # 꼬리를 머리에 겹쳐 자연스럽게 순환
    xf = int(0.004 * SR)
    out[:xf] *= np.linspace(0.0, 1.0, xf)
    out[-xf:] *= np.linspace(1.0, 0.0, xf)

    peak = float(np.max(np.abs(out)))
    out = out / peak * 0.82

    RAW_DIR.mkdir(parents=True, exist_ok=True)
    path = RAW_DIR / "bgm_stage.wav"
    pcm = (out * 32767.0).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print(f"wrote {path} ({len(out) / SR:.2f}s, {path.stat().st_size / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
