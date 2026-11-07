#!/usr/bin/env python3
"""Effekt-Geräusche für Video 4b (Ad5), mit Code erzeugt (keine fremden Samples). Schreibt public/sfx5.wav."""
import os, wave
import numpy as np

SR = 48000
HERE = os.path.dirname(os.path.abspath(__file__))
DUR = 50.4
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

# Hook
add(hit(0.7, 50), 0.0, 0.9); add(hit(0.5, 60), 0.28, 0.6); add(hit(0.9, 45), 0.76, 1.0); add(glitch(0.35), 0.76, 0.5)
for i, x in enumerate([2.0, 2.22, 2.44, 2.66, 2.88, 3.1]):
    add(tick(), x, 0.6, pan=(-0.6 if i < 3 else 0.6))
add(whoosh(0.5), 4.55, 0.6); add(riser(0.7), 4.8, 0.4); add(hit(0.6, 70), 5.42, 0.6)
add(whoosh(0.45), 9.15, 0.5); add(hit(0.5, 80), 9.8, 0.5)
add(whoosh(0.4), 12.3, 0.5); add(hit(0.8, 45), 12.98, 0.9); add(glitch(0.4), 12.98, 0.4)
# Swipe-Teil
for x in [15.02, 15.62, 16.1]:
    add(whoosh(0.28, True), x - 0.05, 1.0); add(hit(0.4, 90), x, 0.6)
add(hit(1.0, 40), 16.82, 1.0); add(glitch(0.5), 16.82, 0.6)
add(riser(0.9), 17.1, 0.5); add(hit(1.1, 38), 18.0, 1.0)
# Delete
for x in [18.84, 19.76, 20.74]:
    add(shatter(0.5), x + 0.05, 0.7); add(hit(0.4, 70), x, 0.6)
add(hit(0.6, 50), 21.34, 0.7)
# Keep YouTube
add(whoosh(0.5), 21.9, 0.5); add(hit(0.6, 60), 23.0, 0.7)
for i, x in enumerate([24.28, 24.53, 24.78]):
    add(tick(), x, 0.7, pan=(i - 1) * 0.6)
add(hit(0.5, 80), 27.66, 0.6)
# Shorts da
add(whoosh(0.5), 28.1, 0.5); add(riser(1.6), 30.4, 0.5); add(hit(1.0, 40), 32.12, 1.0); add(glitch(0.6), 32.12, 0.6)
add(hit(0.5, 60), 33.5, 0.5); add(hit(0.5, 60), 34.15, 0.6); add(glitch(0.3), 34.34, 0.5)
# Lösung
add(riser(1.2), 35.4, 0.5); add(hit(1.2, 38), 36.6, 1.0)
add(shatter(0.8), 38.34, 1.0); add(hit(0.6, 55), 38.34, 0.8)
add(hit(0.7, 60), 40.56, 0.8); add(whoosh(0.5), 42.9, 0.5); add(hit(0.6, 50), 43.0, 0.6)
add(hit(0.8, 42), 45.22, 0.8); add(whoosh(0.5), 46.2, 0.5); add(hit(0.7, 70), 46.9, 0.8)

peak = np.max(np.abs(out)) or 1
out = out / peak * 0.85
with wave.open(os.path.join(HERE, "public/sfx5.wav"), "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((out * 32767).astype(np.int16).tobytes())
print("ok")
