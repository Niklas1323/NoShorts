#!/usr/bin/env python3
"""Eigene ruhige Hintergrundmusik für Video 7 (keine fremden Samples). Schreibt public/music7.wav."""
import os, wave
import numpy as np
SR = 44100; DUR = 59.6
HERE = os.path.dirname(os.path.abspath(__file__))
n = int(SR * DUR); L = np.zeros(n); R = np.zeros(n)
mid = lambda m: 440 * 2 ** ((m - 69) / 12)
CH = [[48, 55, 64, 71], [45, 52, 60, 67], [41, 48, 57, 64], [43, 50, 59, 62]]  # Cmaj7 Am7 Fmaj7 G6
BAR = 3.0
def add(sig, at, g=1.0, pan=0.0):
    i = int(at * SR); sig = sig[: max(0, n - i)]
    L[i:i + len(sig)] += sig * g * (1 - max(0, pan)); R[i:i + len(sig)] += sig * g * (1 + min(0, pan))
def pad(freqs, length):
    t = np.arange(int(length * SR)) / SR
    env = np.minimum(1, t / 1.0) * np.minimum(1, (length - t) / 1.0)
    s = sum(np.sin(2 * np.pi * f * d * t) for f in freqs for d in (0.997, 1.0, 1.003))
    return s * env / (len(freqs) * 3)
def pluck(f, length=1.6):
    t = np.arange(int(length * SR)) / SR
    return (np.sin(2 * np.pi * f * t) + 0.3 * np.sin(4 * np.pi * f * t) + 0.1 * np.sin(6 * np.pi * f * t)) * np.exp(-t / 0.45) * np.minimum(1, t / 0.004)
def kick():
    t = np.arange(int(0.4 * SR)) / SR
    f = 50 * (1 + 2 * np.exp(-t / 0.03))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.15)
k = 0; at = 0.0
while at < DUR:
    c = CH[k % 4]
    add(pad([mid(m) for m in c], BAR + 1.0), at, 0.5)
    add(pad([mid(c[0] - 12)], BAR + 0.5), at, 0.6)
    arp = [c[1] + 12, c[2] + 12, c[3] + 12, c[2] + 12, c[0] + 24, c[2] + 12]
    for j, m in enumerate(arp):
        add(pluck(mid(m)), at + j * BAR / 6, 0.28, pan=(-0.4 if j % 2 else 0.4))
    k += 1; at += BAR
for s, e in [(28.95, 37.6), (40.35, 55.85)]:
    b = s
    while b < e:
        add(kick(), b, 0.35); b += 0.75
# einfacher Hall (Rückkopplungs-Delay)
for buf in (L, R):
    d = int(0.21 * SR)
    for i in range(d, n):
        buf[i] += buf[i - d] * 0.35
out = np.stack([L, R], 1)
fade = np.ones(n); fade[-int(2 * SR):] = np.linspace(1, 0, int(2 * SR)); out *= fade[:, None]
out = out / np.max(np.abs(out)) * 0.8
with wave.open(os.path.join(HERE, "public/music7.wav"), "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes((out * 32767).astype(np.int16).tobytes())
print("ok")
