#!/usr/bin/env python3
"""Eigene Musik + leise Klick-/Pop-Geräusche für Video 13 (keine fremden Samples). Schreibt public/music13.wav.
Regeln von Niklas: keine Whooshes/Übergangs-Geräusche, kein Herzschlag. Nur weiche Pops, wenn etwas herausspringt."""
import os, wave
import numpy as np
SR = 44100; DUR = 31.0
HERE = os.path.dirname(os.path.abspath(__file__))
n = int(SR * DUR); L = np.zeros(n); R = np.zeros(n)
rng = np.random.default_rng(13)
mid = lambda m: 440 * 2 ** ((m - 69) / 12)

def add(sig, at, g=1.0, pan=0.0):
    i = int(at * SR); sig = sig[: max(0, n - i)]
    L[i:i + len(sig)] += sig * g * (1 - max(0, pan)); R[i:i + len(sig)] += sig * g * (1 + min(0, pan))

# --- Musik: warme Akkorde, gezupfte Achtel, weiche Kick + Shaker, 100 BPM
BPM = 100; BEAT = 60 / BPM; BAR = 4 * BEAT
CH = [[50, 57, 62, 65, 69], [46, 53, 58, 62, 65], [48, 55, 60, 64, 67], [45, 52, 57, 60, 64]]  # Dm Bb C Am
def pad(freqs, length):
    t = np.arange(int(length * SR)) / SR
    env = np.minimum(1, t / 0.6) * np.minimum(1, (length - t) / 0.6)
    s = sum(np.sin(2 * np.pi * f * d * t) + 0.25 * np.sin(4 * np.pi * f * d * t) for f in freqs for d in (0.996, 1.0, 1.004))
    return s * env / (len(freqs) * 3)
def pluck(f, length=0.9):
    t = np.arange(int(length * SR)) / SR
    return (np.sin(2 * np.pi * f * t) + 0.35 * np.sin(4 * np.pi * f * t) + 0.12 * np.sin(6 * np.pi * f * t)) * np.exp(-t / 0.22) * np.minimum(1, t / 0.003)
def kick():
    t = np.arange(int(0.35 * SR)) / SR
    f = 48 * (1 + 2.2 * np.exp(-t / 0.025))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.13)
def shaker():
    m = int(0.06 * SR); t = np.arange(m) / SR
    x = rng.standard_normal(m); x = np.diff(np.concatenate([[0], x]))
    return x * np.exp(-t / 0.018) * 0.5
k = 0; at = 0.0
while at < DUR:
    c = CH[k % 4]
    add(pad([mid(m) for m in c[1:]], BAR + 0.6), at, 0.35)
    add(pad([mid(c[0] - 12)], BAR + 0.4), at, 0.45)
    arp = [c[2] + 12, c[3] + 12, c[4] + 12, c[3] + 12, c[2] + 12, c[4] + 12, c[3] + 24, c[4] + 12]
    for j, m in enumerate(arp):
        add(pluck(mid(m)), at + j * BEAT / 2, 0.16, pan=(-0.35 if j % 2 else 0.35))
    for b in range(4):
        if at + b * BEAT > 0.3:
            add(kick(), at + b * BEAT, 0.42)
        add(shaker(), at + b * BEAT + BEAT / 2, 0.12, pan=0.2)
    k += 1; at += BAR
# einfacher Hall
for buf in (L, R):
    d = int(0.19 * SR)
    for i in range(d, n):
        buf[i] += buf[i - d] * 0.28
mus = np.stack([L, R], 1)
mus = mus / np.max(np.abs(mus)) * 0.32
fade = np.ones(n); fade[-int(1.8 * SR):] = np.linspace(1, 0, int(1.8 * SR)); fade[:int(0.05 * SR)] = np.linspace(0, 1, int(0.05 * SR))
mus *= fade[:, None]

# --- weiche Pops, Tipps, kleines Glitzern
fx = np.zeros((n, 2))
def fxadd(sig, at, g, pan=0.0):
    i = int(at * SR); sig = sig[: max(0, n - i)]
    fx[i:i + len(sig), 0] += sig * g * (1 - max(0, pan)); fx[i:i + len(sig), 1] += sig * g * (1 + min(0, pan))
def bubble(f0=380, f1=900, length=0.09):
    t = np.arange(int(length * SR)) / SR
    f = f0 + (f1 - f0) * (t / length) ** 0.6
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.03) * np.minimum(1, t / 0.002)
def click():
    t = np.arange(int(0.03 * SR)) / SR
    return (np.sin(2 * np.pi * 1800 * t) * 0.6 + rng.standard_normal(len(t)) * 0.2) * np.exp(-t / 0.005)
def sparkle(length=0.7):
    out = np.zeros(int(length * SR))
    for j in range(14):
        f = rng.uniform(2500, 5200); st = int(rng.uniform(0, length - 0.12) * SR)
        t = np.arange(int(0.12 * SR)) / SR
        out[st:st + len(t)] += np.sin(2 * np.pi * f * t) * np.exp(-t / 0.03) * (1 - st / len(out))
    return out
def thud():
    t = np.arange(int(0.25 * SR)) / SR
    f = 90 * (1 + 1.5 * np.exp(-t / 0.02))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.07)
for at in [0.05, 6.15, 6.75, 7.4, 13.05, 17.22, 18.26, 18.5, 18.84, 21.92, 24.6, 26.6]:
    fxadd(bubble(), at, 0.32)
for at in [3.35, 20.74]:
    fxadd(click(), at, 0.35)
fxadd(sparkle(), 1.32, 0.18, pan=0.2)
fxadd(sparkle(0.5), 9.75, 0.12)
fxadd(thud(), 14.84, 0.5)
fxadd(bubble(300, 700, 0.12), 13.62, 0.22)  # Knopf dreht sich
out = mus + fx
out = np.clip(out, -0.98, 0.98)
with wave.open(os.path.join(HERE, "public/music13.wav"), "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes((out * 32767).astype(np.int16).tobytes())
print("ok")
