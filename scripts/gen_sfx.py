#!/usr/bin/env python3
import math
import wave
from pathlib import Path

import numpy as np


SR = 44100
ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "app" / "src" / "main" / "res" / "raw"


def env_exp(n, attack=0.002, decay=0.12):
    t = np.arange(n, dtype=np.float64) / SR
    a = max(1, int(attack * SR))
    e = np.exp(-t / decay)
    e[:a] *= np.linspace(0.0, 1.0, a)
    return e


def fade_edges(x, fade_in=0.0015, fade_out=0.008):
    y = x.copy()
    fi = max(1, int(fade_in * SR))
    fo = max(1, int(fade_out * SR))
    y[:fi] *= np.linspace(0.0, 1.0, fi)
    y[-fo:] *= np.linspace(1.0, 0.0, fo)
    return y


def normalize(x, peak=0.90):
    m = float(np.max(np.abs(x)))
    if m > 0:
        x = x * (peak / m)
    return np.clip(x, -0.999, 0.999)


def sine_sweep(duration, f0, f1, decay, attack=0.001):
    n = int(round(duration * SR))
    t = np.arange(n, dtype=np.float64) / SR
    k = (f1 / f0) ** (1.0 / max(duration, 1e-9))
    phase = 2.0 * math.pi * f0 * ((np.power(k, t) - 1.0) / math.log(k))
    return np.sin(phase) * env_exp(n, attack=attack, decay=decay)


def filtered_noise(rng, n, hp=0.965, stages=2):
    noise = rng.normal(0.0, 1.0, n)
    y = noise
    for _ in range(stages):
        y = np.concatenate(([0.0], y[1:] - hp * y[:-1]))
    return y


def punch_sfx():
    rng = np.random.default_rng(120712)
    dur = 0.22
    n = int(round(dur * SR))
    t = np.arange(n, dtype=np.float64) / SR

    swoosh_env = np.exp(-((t - 0.055) / 0.045) ** 2) * np.exp(-t / 0.24)
    swoosh = filtered_noise(rng, n, hp=0.93, stages=1) * swoosh_env * 0.34

    impact = sine_sweep(dur, 175.0, 68.0, decay=0.045, attack=0.0008) * 1.05
    body = sine_sweep(dur, 92.0, 48.0, decay=0.075, attack=0.0012) * 0.42

    click_env = np.exp(-t / 0.004)
    click = filtered_noise(rng, n, hp=0.78, stages=1) * click_env * 0.22

    snap = np.sin(2.0 * math.pi * 980.0 * t) * np.exp(-t / 0.006) * 0.11
    x = swoosh + impact + body + click + snap
    return normalize(fade_edges(x))


def special_sfx():
    rng = np.random.default_rng(120713)
    dur = 0.46
    n = int(round(dur * SR))
    t = np.arange(n, dtype=np.float64) / SR

    pre = np.exp(-((t - 0.085) / 0.07) ** 2)
    tail = np.exp(-np.maximum(t - 0.08, 0.0) / 0.18)
    swoosh = filtered_noise(rng, n, hp=0.91, stages=1) * pre * tail * 0.48

    sub = sine_sweep(dur, 69.0, 43.0, decay=0.22, attack=0.0015) * 0.92
    power = sine_sweep(dur, 145.0, 58.0, decay=0.16, attack=0.001) * 0.80
    upper = sine_sweep(dur, 430.0, 120.0, decay=0.09, attack=0.0008) * 0.24

    hit_env = np.exp(-t / 0.010)
    hit = filtered_noise(rng, n, hp=0.72, stages=1) * hit_env * 0.35
    crack = np.sin(2.0 * math.pi * 1550.0 * t) * np.exp(-t / 0.014) * 0.10

    rumble = rng.normal(0.0, 1.0, n)
    rumble = np.cumsum(rumble)
    rumble = rumble / max(float(np.max(np.abs(rumble))), 1e-9)
    rumble *= np.exp(-t / 0.24) * 0.10

    x = swoosh + sub + power + upper + hit + crack + rumble
    return normalize(fade_edges(x, fade_in=0.002, fade_out=0.014))


def gameover_sfx():
    """죽을 때의 '으어어어...' — 음이 축 처지며 힘이 빠지는 비명"""
    rng = np.random.default_rng(120714)
    dur = 1.5
    n = int(round(dur * SR))
    t = np.arange(n, dtype=np.float64) / SR

    # 음높이가 계단처럼 네 번 꺾이며 내려간다 (으-어-어-어)
    steps = np.array([1.00, 0.82, 0.66, 0.52])
    base = 300.0
    idx = np.clip((t / dur * len(steps)).astype(int), 0, len(steps) - 1)
    f = base * steps[idx]
    f *= 1.0 + 0.05 * np.sin(2 * math.pi * 5.5 * t)      # 떨리는 목소리

    phase = 2 * math.pi * np.cumsum(f) / SR
    # 성대 느낌: 기본음 + 배음 몇 개
    voice = (np.sin(phase) * 0.6 + np.sin(2 * phase) * 0.25 +
             np.sin(3 * phase) * 0.12 + np.sin(5 * phase) * 0.05)

    # 마디마다 살짝 끊기는 '어어어' 리듬
    wobble = 0.75 + 0.25 * np.sin(2 * math.pi * (len(steps) / dur) * t) ** 2
    env = np.exp(-np.maximum(t - 0.15, 0.0) / 0.85)
    env[: int(0.04 * SR)] *= np.linspace(0.0, 1.0, int(0.04 * SR))

    breath = filtered_noise(rng, n, hp=0.6, stages=1) * 0.05 * env
    x = voice * wobble * env * 0.9 + breath
    return normalize(fade_edges(x, fade_in=0.01, fade_out=0.05))


def cat_hit_sfx():
    """고양이가 맞을 때의 '예에에용!' — 짧고 높게 치솟았다 톡 끊긴다"""
    dur = 0.42
    n = int(round(dur * SR))
    t = np.arange(n, dtype=np.float64) / SR

    # 예(치솟음) → 에에에(유지·떨림) → 용!(끝에서 살짝 꺾임)
    f = np.where(t < 0.08, 520 + 900 * (t / 0.08), 1420)
    f = np.where(t > 0.30, 1420 - 520 * ((t - 0.30) / 0.12), f)
    f *= 1.0 + 0.06 * np.sin(2 * math.pi * 22 * t)      # 냥냥거리는 떨림

    phase = 2 * math.pi * np.cumsum(f) / SR
    voice = np.sin(phase) * 0.65 + np.sin(2 * phase) * 0.22 + np.sin(3 * phase) * 0.08

    env = np.exp(-np.maximum(t - 0.05, 0.0) / 0.20)
    env[: int(0.012 * SR)] *= np.linspace(0.0, 1.0, int(0.012 * SR))
    return normalize(fade_edges(voice * env, fade_in=0.004, fade_out=0.03))


def cat_die_sfx():
    """고양이가 쓰러질 때 '야옹' — 두 음절로 또렷하게 우는 소리"""
    rng = np.random.default_rng(120715)
    dur = 0.85
    n = int(round(dur * SR))
    t = np.arange(n, dtype=np.float64) / SR

    # '야'(올라갔다) → '옹'(내려오며 잠긴다)
    f = np.where(
        t < 0.30,
        480 + 260 * (t / 0.30),          # 야 — 살짝 치솟는다
        740 * np.exp(-(t - 0.30) * 2.2) + 210,   # 옹 — 길게 내려앉는다
    )
    f *= 1.0 + 0.05 * np.sin(2 * math.pi * 6.0 * t)   # 자연스러운 떨림
    phase = 2 * math.pi * np.cumsum(f) / SR

    voice = np.zeros(n)
    for k, amp in enumerate([1.0, 0.6, 0.34, 0.2, 0.12], start=1):
        voice += np.sin(phase * k) * amp
    voice /= 2.3

    # 입 모양: '야'는 열린 소리(밝은 공명), '옹'은 닫힌 소리(어두운 공명)
    open_mix = np.clip(1.2 - t / 0.45, 0.0, 1.0)
    for f_c, bw, g in ((760.0, 150.0, 1.0), (1500.0, 240.0, 0.75)):
        r = math.exp(-math.pi * bw / SR)
        a1 = -2.0 * r * math.cos(2 * math.pi * f_c / SR)
        a2 = r * r
        y = np.zeros(n)
        y1 = y2 = 0.0
        for i in range(n):
            cur = voice[i] - a1 * y1 - a2 * y2
            y[i] = cur
            y2 = y1
            y1 = cur
        voice = voice + g * 0.14 * open_mix * (y / max(float(np.max(np.abs(y))), 1e-9))

    # 끝에서 입을 다물며 '옹' 하고 잠긴다
    env = np.ones(n)
    env *= np.exp(-np.maximum(t - 0.25, 0.0) / 0.34)
    env[: int(0.025 * SR)] *= np.linspace(0.0, 1.0, int(0.025 * SR))

    breath = filtered_noise(rng, n, hp=0.5, stages=1) * 0.035 * env
    return normalize(fade_edges(voice * env + breath, fade_in=0.008, fade_out=0.06))


def player_hurt_sfx():
    """주인공이 맞을 때의 짧은 신음 '윽!' — 순간 눌렸다가 잦아든다"""
    rng = np.random.default_rng(120716)
    dur = 0.38
    n = int(round(dur * SR))
    t = np.arange(n, dtype=np.float64) / SR

    # 갑자기 눌린 소리: 높은 데서 툭 떨어진다
    f = 330 * np.exp(-t * 7.0) + 130
    phase = 2 * math.pi * np.cumsum(f) / SR
    voice = np.sin(phase) * 0.7 + np.sin(2 * phase) * 0.3 + np.sin(3 * phase) * 0.12

    # 목이 막히는 느낌의 공명
    r = math.exp(-math.pi * 180.0 / SR)
    a1 = -2.0 * r * math.cos(2 * math.pi * 620.0 / SR)
    a2 = r * r
    y = np.zeros(n)
    y1 = y2 = 0.0
    for i in range(n):
        cur = voice[i] - a1 * y1 - a2 * y2
        y[i] = cur
        y2 = y1
        y1 = cur
    voice = voice + 0.18 * (y / max(float(np.max(np.abs(y))), 1e-9))

    env = np.exp(-t / 0.11)
    env[: int(0.006 * SR)] *= np.linspace(0.0, 1.0, int(0.006 * SR))
    breath = filtered_noise(rng, n, hp=0.55, stages=1) * 0.08 * env
    return normalize(fade_edges(voice * env + breath, fade_in=0.003, fade_out=0.03))


def write_wav(path, samples):
    pcm = (samples * 32767.0).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    outputs = {
        RAW_DIR / "attack.wav": punch_sfx(),
        RAW_DIR / "special.wav": special_sfx(),
        RAW_DIR / "gameover.wav": gameover_sfx(),
        RAW_DIR / "cat_hit.wav": cat_hit_sfx(),
        RAW_DIR / "cat_die.wav": cat_die_sfx(),
        RAW_DIR / "player_hurt.wav": player_hurt_sfx(),
    }
    for path, samples in outputs.items():
        write_wav(path, samples)
        print(f"wrote {path} ({len(samples) / SR:.3f}s)")


if __name__ == "__main__":
    main()
