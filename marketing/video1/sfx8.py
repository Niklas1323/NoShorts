#!/usr/bin/env python3
"""Effekt-Geräusche für Video 8 (Ad8), mit Code erzeugt (keine fremden Samples). Schreibt public/sfx5.wav."""
import os, wave
import numpy as np

SR = 48000
HERE = os.path.dirname(os.path.abspath(__file__))
DUR = 23.0
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

def pad(freqs, length, g=1.0):
    t = np.arange(int(length * SR)) / SR
    env_ = np.minimum(1, t / 0.6) * np.minimum(1, (length - t) / 0.6)
    s = sum(np.sin(2 * np.pi * f * d * t) for f in freqs for d in (0.996, 1.0, 1.004))
    return s * env_ / (len(freqs) * 3) * g
def heartbeat():
    return hit(0.35, 45) * 0.8
# Grundstimmung: dunkler Ton + Herzschlag bis zur Lösung
add(pad([55, 82.4], 17.0), 0.0, 0.5)
b = 0.0
while b < 17.0:
    add(heartbeat(), b, 0.5); add(heartbeat(), b + 0.28, 0.35); b += 1.0
# Hook
add(hit(1.0, 38), 0.0, 1.0); add(hit(0.5, 60), 0.45, 0.6); add(hit(0.5, 60), 0.9, 0.6)
# Zähler: Ticks
def ticks(a, e, step):
    x = a
    while x < e:
        add(tick(), x, 0.5); x += step
add(hit(0.6, 55), 2.95, 0.7); ticks(2.95, 3.8, 0.28)
add(hit(0.6, 55), 5.75, 0.7); ticks(5.75, 7.0, 0.06)
add(hit(0.7, 48), 8.2, 0.7); ticks(8.7, 10.2, 0.033)
ticks(11.6, 12.7, 0.05)
add(pad([110, 164.8, 220], 1.6, 1.0), 13.1, 0.5)   # Anstieg zu 8.4 YEARS
add(hit(1.2, 36), 14.3, 1.0); add(glitch(0.4), 14.3, 0.5)
add(hit(1.0, 38), 15.2, 0.8); add(hit(1.2, 36), 15.9, 1.0)
# Lösung: heller Akkord, Knopf zerspringt
add(pad([261.6, 329.6, 392.0, 523.3], 6.0, 1.0), 17.0, 0.6)
add(shatter(0.7), 18.5, 0.8); add(hit(0.7, 55), 18.5, 0.8)
# CTA
add(hit(0.8, 50), 20.2, 0.8); add(hit(0.5, 70), 20.5, 0.6); add(hit(0.5, 70), 21.0, 0.6)
peak = np.max(np.abs(out)) or 1
out = out / peak * 0.85
with wave.open(os.path.join(HERE, "public/sfx8.wav"), "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((out * 32767).astype(np.int16).tobytes())
print("ok")
