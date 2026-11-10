#!/usr/bin/env python3
"""Effekt-Geräusche für Video 10 (Ad10), mit Code erzeugt (keine fremden Samples). Schreibt public/sfx5.wav."""
import os, wave
import numpy as np

SR = 48000
HERE = os.path.dirname(os.path.abspath(__file__))
DUR = 28.0
rng = np.random.default_rng(11)
out = np.zeros((int(DUR * SR), 2))


def add(sig, at, gain=1.0, pan=0.0):
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

import json, re
W = [(re.sub(r"[^a-z0-9]", "", w.lower()), a) for w, a, e in json.load(open(os.path.join(HERE, "src/words10.json")))]
def pad(freqs, length, g=1.0):
    tt = np.arange(int(length * SR)) / SR
    e = np.minimum(1, tt / 0.6) * np.minimum(1, (length - tt) / 0.8)
    return sum(np.sin(2 * np.pi * f * d * tt) for f in freqs for d in (0.996, 1.0, 1.004)) * e / (len(freqs) * 3) * g
add(pad([55, 82.4, 110], 20.8), 0.0, 0.3)
add(pad([130.8, 164.8, 196.0, 261.6], 7.0), 20.8, 0.35)
for w, a in W:
    if any(s in w for s in ("fuck", "bitch", "shit")): add(hit(0.7, 42), a, 0.9); add(glitch(0.25), a, 0.35)
    elif w in ("delete",): add(hit(0.4, 70), a, 0.6)
    elif w in ("keep", "gone", "stay", "stop", "test", "12", "no", "built"): add(hit(0.5, 55), a, 0.6)
lock = next(a for w, a in W if w == "in"); add(tick(), lock, 1.0); add(hit(0.5, 50), lock, 0.6)
gone = next(a for w, a in W if w == "gone"); add(shatter(0.6), gone, 0.6)
alls = next(a for w, a in W if w == "shit"); add(shatter(0.8), alls, 0.7)
peak = np.max(np.abs(out)) or 1
out = out / peak * 0.85
with wave.open(os.path.join(HERE, "public/sfx10.wav"), "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((out * 32767).astype(np.int16).tobytes())
print("ok")
