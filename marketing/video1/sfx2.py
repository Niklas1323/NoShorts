#!/usr/bin/env python3
"""Effekt-Geräusche für Video 2, komplett mit Code erzeugt (gehören uns, keine fremden Samples).

Liest die Wort-Zeiten aus src/words2.ts und schreibt public/sfx2.wav (48 kHz, Stereo, ohne Stimme).
Aufruf: python3 sfx2.py
"""
import json
import os
import re
import wave

import numpy as np

SR = 48000
HERE = os.path.dirname(os.path.abspath(__file__))
DUR = 30.0

words = json.loads(re.search(r"= (\[.*\]);", open(os.path.join(HERE, "src/words2.ts")).read(), re.S).group(1))
rng = np.random.default_rng(7)
out = np.zeros((int(DUR * SR), 2))


def cue(word, n=0):
    hits = [s for w, s in words if w == word]
    return hits[n] if len(hits) > n else None


def env(n, attack, decay):
    t = np.arange(n) / SR
    return np.minimum(1, t / max(attack, 1e-4)) * np.exp(-t / decay)


def add(sig, at, gain=1.0, pan=0.0):
    if at is None:
        return
    i = int(at * SR)
    sig = sig[: max(0, len(out) - i)]
    out[i : i + len(sig), 0] += sig * gain * (1 - max(0, pan))
    out[i : i + len(sig), 1] += sig * gain * (1 + min(0, pan))


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for k in range(len(x)):
        acc = (1 - a) * x[k] + a * acc
        y[k] = acc
    return y


def whoosh(length=0.35, up=True):
    n = int(length * SR)
    noise = rng.standard_normal(n)
    t = np.linspace(0, 1, n)
    # Band wandert hoch (oder runter): Differenz zweier Tiefpässe mit wechselnder Grenze, grob in Blöcken
    y = np.zeros(n)
    blocks = 24
    for b in range(blocks):
        s, e = b * n // blocks, (b + 1) * n // blocks
        f = 400 + 5000 * (t[s] if up else 1 - t[s])
        seg = noise[s:e]
        y[s:e] = lowpass(seg, f) - lowpass(seg, f * 0.3)
    shape = np.sin(np.pi * t) ** 1.5
    return y * shape * 1.6


def hit(length=0.6, freq=55):
    n = int(length * SR)
    t = np.arange(n) / SR
    f = freq * (1 + 2.5 * np.exp(-t / 0.03))  # Pitch fällt schnell = Kick
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.002, 0.18)
    click = lowpass(rng.standard_normal(n), 3000) * env(n, 0.0005, 0.012)
    return np.tanh(1.8 * (body + 0.6 * click))


def tick():
    n = int(0.03 * SR)
    t = np.arange(n) / SR
    return np.sin(2 * np.pi * 2400 * t) * env(n, 0.0005, 0.006) * 0.8


def shatter(length=0.7):
    n = int(length * SR)
    noise = rng.standard_normal(n)
    hp = noise - lowpass(noise, 2500)
    sparkle = np.zeros(n)
    for _ in range(40):
        i = rng.integers(0, n // 2)
        m = int(0.02 * SR)
        f = rng.uniform(3000, 7000)
        tt = np.arange(m) / SR
        sparkle[i : i + m] += np.sin(2 * np.pi * f * tt) * np.exp(-tt / 0.006)[: len(sparkle[i : i + m])]
    return (hp * env(n, 0.001, 0.15) + 0.4 * sparkle) * 0.9


def glitch(length=0.4):
    n = int(length * SR)
    y = np.zeros(n)
    i = 0
    while i < n:
        m = int(rng.uniform(0.01, 0.05) * SR)
        kind = rng.integers(0, 3)
        tt = np.arange(min(m, n - i)) / SR
        if kind == 0:
            seg = np.sign(np.sin(2 * np.pi * rng.uniform(80, 900) * tt))
        elif kind == 1:
            seg = rng.standard_normal(len(tt))
        else:
            seg = np.zeros(len(tt))
        y[i : i + len(tt)] = seg * 0.5
        i += m
    return y * env(n, 0.001, 0.25)


def riser(length=0.8):
    n = int(length * SR)
    t = np.arange(n) / SR
    f = 200 + 1800 * (t / length) ** 2
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * (t / length) ** 2 * 0.35 + whoosh(length, True) * 0.4


# ---------------------------------------------------------------- Ablauf (an die Wörter gekoppelt)
add(hit(0.7, 48), cue("TONIGHT"), 0.9)
add(whoosh(0.3), cue("EVERY"), 0.35, -0.4)
add(whoosh(0.3), cue("TODAY"), 0.35, 0.4)
add(riser(0.5), (cue("INCLUDING") or 0) - 0.1, 0.6)
add(shatter(0.8), cue("ONE", 0), 0.9)
add(hit(0.4, 70), cue("ONE", 0), 0.6)

add(whoosh(0.25), cue("HOUR"), 0.5)
fift = cue("FIFTEEN")
if fift is not None:
    for k in range(15):
        add(tick(), fift + k * 0.045, 0.7, (k % 2 - 0.5) * 0.6)
add(hit(0.8, 42), cue("GONE"), 1.0)
add(shatter(0.9), cue("GONE"), 0.7)

for w, n in [("DREAMS", 0), ("FRIENDS", 0)]:
    add(whoosh(0.28, n == 0), cue(w), 0.45, -0.3 if n == 0 else 0.3)
for n in range(2):
    add(tick(), cue("WAITING", n), 0.6)
    add(tick(), (cue("WAITING", n) or 0) + 0.25, 0.5)
for w in ["KEEP", "SWIPING"]:
    add(whoosh(0.22, True), cue(w), 0.55)

add(glitch(0.45), cue("ENOUGH"), 0.9)
add(hit(0.9, 40), cue("ENOUGH"), 1.0)
add(hit(0.6, 60), cue("KILLS"), 0.8)
add(shatter(0.6), cue("KILLS"), 0.6)
add(whoosh(0.35), cue("VIDEOS"), 0.4)
add(hit(0.5, 80), cue("STAY"), 0.5)

add(whoosh(0.3), cue("LAUNCH"), 0.4)
add(hit(0.5, 65), cue("TESTERS"), 0.7)
add(tick(), cue("ANDROID"), 0.8)
add(riser(0.6), (cue("COMMENT") or 0) - 0.5, 0.5)
add(hit(1.0, 45), cue("COMMENT"), 1.0)
add(hit(0.8, 50), cue("NOW"), 0.9)

peak = np.abs(out).max() or 1
out = out / peak * 0.7
pcm = (np.clip(out, -1, 1) * 32767).astype("<i2")
with wave.open(os.path.join(HERE, "public/sfx2.wav"), "wb") as f:
    f.setnchannels(2)
    f.setsampwidth(2)
    f.setframerate(SR)
    f.writeframes(pcm.tobytes())
print("sfx2.wav ok")
