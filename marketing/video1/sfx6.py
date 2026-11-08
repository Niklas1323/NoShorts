#!/usr/bin/env python3
"""Effekt-Geräusche für Video 6 (Ad6), mit Code erzeugt (keine fremden Samples). Schreibt public/sfx6.wav."""
import os, wave
import numpy as np

SR = 48000
HERE = os.path.dirname(os.path.abspath(__file__))
DUR = 51.6
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


# Schnelle Fassung (FAST=1): Zeiten über die Zeitkarte von fast6.py umrechnen, plus Schnitt-Stösse.
FAST = os.environ.get("FAST") == "1"
if FAST:
    import json as _json
    _SUF = os.environ.get("SUF", "")
    _pts = _json.load(open(os.path.join(HERE, f"map6{_SUF}.json")))  # [original, neu]
    def _to_new(x):
        for (o1, n1), (o2, n2) in zip(_pts, _pts[1:]):
            if o1 <= x <= o2:
                return n1 if o2 == o1 else n1 + (x - o1) / (o2 - o1) * (n2 - n1)
        return _pts[-1][1]
    _add = add
    def add(sig, at, gain=1.0, pan=0.0):
        _add(sig, None if at is None else _to_new(at), gain, pan)
    out = np.zeros((int((_pts[-1][1] + 1.7) * SR), 2))

# Anfang wie das Original. NUR Stösse auf Schlüsselwörtern, keine Übergangs-Geräusche (kein Whoosh/Riser/Tick:
# Niklas fand das Rauschen zwischen den Szenen schrecklich).
add(hit(0.7, 50), 0.0, 0.6); add(hit(0.9, 45), 1.18, 0.7)
add(hit(0.9, 42), 5.46, 0.8)
add(hit(1.0, 40), 13.5, 0.7)
# Wendung
add(hit(1.1, 38), 19.94, 0.9)
for x in [20.98, 21.98, 22.72]:
    add(hit(0.4, 90), x, 0.5)
add(hit(1.0, 40), 23.74, 0.8)
add(hit(1.1, 38), 25.18, 0.8)
for x in [29.76, 30.16, 30.5, 30.76]:
    add(hit(0.3, 70), x, 0.35)
# YouTube
add(hit(1.0, 40), 32.32, 0.8)
# Lösung
add(hit(1.2, 38), 35.78, 0.9)
add(shatter(0.8), 37.86, 0.6); add(hit(0.6, 55), 37.86, 0.7)
add(hit(0.8, 42), 46.64, 0.8)
add(hit(0.7, 70), 48.58, 0.7)

peak = np.max(np.abs(out)) or 1
out = out / peak * 0.85
if FAST:
    # (keine Extra-Geräusche bei den Schnitten mehr: Niklas fand das Rascheln zu laut)
    peak = np.max(np.abs(out)) or 1
    out = out / peak * 0.85
with wave.open(os.path.join(HERE, f"public/sfx6fast{_SUF}.wav" if FAST else "public/sfx6.wav"), "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((out * 32767).astype(np.int16).tobytes())
print("ok")
