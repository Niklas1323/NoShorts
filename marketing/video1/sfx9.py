#!/usr/bin/env python3
"""Effekt-Geräusche für Video 9 (Ad9), mit Code erzeugt (keine fremden Samples). Schreibt public/sfx5.wav."""
import os, wave
import numpy as np

SR = 48000
HERE = os.path.dirname(os.path.abspath(__file__))
DUR = 40.0
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

import json
W = json.load(open(os.path.join(HERE, "src/words9.json")))
def t(word, nth=0):
    hits = [a for w, a, e in W if w.lower().strip(".,?!") == word]
    return hits[nth] if len(hits) > nth else None
def pad(freqs, length, g=1.0):
    tt = np.arange(int(length * SR)) / SR
    e = np.minimum(1, tt / 1.0) * np.minimum(1, (length - tt) / 1.0)
    return sum(np.sin(2 * np.pi * f * d * tt) for f in freqs for d in (0.996, 1.0, 1.004)) * e / (len(freqs) * 3) * g
# leiser dunkler Grundton (kein Herzschlag, kein Rauschen)
add(pad([55, 82.4, 110], 31.0), 0.0, 0.35)
add(pad([130.8, 164.8, 196.0, 261.6], 9.0), 31.0, 0.35)
# Schloss klickt bei "in"
add(tick(), t("in,"), 1.0); add(hit(0.6, 50), t("in,"), 0.7)
# Stösse auf Schlüsselwörtern
for w, n, g in [("fuck", 0, 0.8), ("shorts,", 0, 0.5), ("minute.", 0, 0.5), ("soldier.", 0, 0.5), ("stop", 0, 0.6),
                ("rotting.", 0, 0.5), ("scrolling.", 0, 0.5), ("off,", 0, 0.6), ("fuck", 1, 1.0), ("gone.", 0, 0.9),
                ("excuses.", 0, 0.6), ("test,", 0, 0.8)]:
    x = t(w, n)
    if x is not None: add(hit(0.6, 50), x, g)
if t("gone.") is not None: add(shatter(0.6), t("gone."), 0.6)
peak = np.max(np.abs(out)) or 1
out = out / peak * 0.85
with wave.open(os.path.join(HERE, "public/sfx9.wav"), "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((out * 32767).astype(np.int16).tobytes())
print("ok")
