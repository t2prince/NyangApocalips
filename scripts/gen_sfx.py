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
    }
    for path, samples in outputs.items():
        write_wav(path, samples)
        print(f"wrote {path} ({len(samples) / SR:.3f}s)")


if __name__ == "__main__":
    main()
