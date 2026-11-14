#!/usr/bin/env python3
"""Video 14: eigene Musik (120 BPM, F-Dur, hell und locker) -> public/music14.wav
und leise Pops/Tipps getrennt -> public/sfx14.wav (so kann man die Musik tauschen, ohne die Pops zu verlieren).
Regeln von Niklas: keine Whooshes/Übergangs-Geräusche, kein Herzschlag."""
import os, wave
import numpy as np
SR = 44100; DUR = 37.0
HERE = os.path.dirname(os.path.abspath(__file__))
n = int(SR * DUR)
rng = np.random.default_rng(14)
mid = lambda m: 440 * 2 ** ((m - 69) / 12)

def mk():
    return np.zeros((n, 2))
def add(buf, sig, at, g=1.0, pan=0.0):
    i = int(at * SR); sig = sig[: max(0, n - i)]
    buf[i:i + len(sig), 0] += sig * g * (1 - max(0, pan)); buf[i:i + len(sig), 1] += sig * g * (1 + min(0, pan))

BPM = 120; BEAT = 60 / BPM; BAR = 4 * BEAT
CH = [[41, 53, 57, 60, 65], [36, 52, 55, 60, 64], [38, 50, 53, 57, 62], [34, 50, 53, 58, 62]]  # F C Dm Bb
def pad(freqs, length):
    t = np.arange(int(length * SR)) / SR
    env = np.minimum(1, t / 0.4) * np.minimum(1, (length - t) / 0.4)
    s = sum(np.sin(2 * np.pi * f * d * t) + 0.2 * np.sin(4 * np.pi * f * d * t) for f in freqs for d in (0.997, 1.0, 1.003))
    return s * env / (len(freqs) * 3)
def pluck(f, length=0.5):
    t = np.arange(int(length * SR)) / SR
    return (np.sin(2 * np.pi * f * t) + 0.45 * np.sin(4 * np.pi * f * t) + 0.2 * np.sin(6 * np.pi * f * t)) * np.exp(-t / 0.13) * np.minimum(1, t / 0.002)
def bass(f, length):
    t = np.arange(int(length * SR)) / SR
    return (np.sin(2 * np.pi * f * t) + 0.3 * np.sin(4 * np.pi * f * t)) * np.exp(-t / 0.35) * np.minimum(1, t / 0.005)
def kick():
    t = np.arange(int(0.3 * SR)) / SR
    f = 50 * (1 + 2.4 * np.exp(-t / 0.022))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.11)
def clap():
    m = int(0.18 * SR); t = np.arange(m) / SR
    x = rng.standard_normal(m)
    x = x - np.concatenate([[0], x[:-1]]) * 0.6
    env = sum(np.exp(-np.maximum(0, t - d) / 0.012) * (t >= d) for d in (0, 0.009, 0.018)) * 0.5 + np.exp(-t / 0.06) * 0.5
    return x * env * 0.5
def hat():
    m = int(0.04 * SR); t = np.arange(m) / SR
    x = rng.standard_normal(m); x = np.diff(np.concatenate([[0], x]))
    return x * np.exp(-t / 0.01) * 0.4

mus = mk(); k = 0; at = 0.0
while at < DUR:
    c = CH[k % 4]
    add(mus, pad([mid(m) for m in c[2:]], BAR + 0.4), at, 0.28)
    for b in range(4):
        add(mus, bass(mid(c[0]), BEAT * 0.9), at + b * BEAT, 0.45)
    arp = [c[2] + 12, c[3] + 12, c[4] + 12, c[3] + 12] * 4
    for j, m in enumerate(arp):
        add(mus, pluck(mid(m)), at + j * BEAT / 4, 0.11 if j % 2 else 0.15, pan=(-0.3 if j % 2 else 0.3))
    for b in range(4):
        add(mus, kick(), at + b * BEAT, 0.4)
        if b % 2 == 1: add(mus, clap(), at + b * BEAT, 0.3, pan=0.1)
        add(mus, hat(), at + b * BEAT + BEAT / 2, 0.22, pan=-0.2)
    k += 1; at += BAR
for ch in range(2):
    d = int(0.125 * SR)
    for i in range(d, n):
        mus[i, ch] += mus[i - d, ch] * 0.22
mus = mus / np.max(np.abs(mus)) * 0.32
fade = np.ones(n); fade[-int(2.0 * SR):] = np.linspace(1, 0, int(2.0 * SR)); fade[:int(0.03 * SR)] = np.linspace(0, 1, int(0.03 * SR))
mus *= fade[:, None]

fx = mk()
def bubble(f0=380, f1=900, length=0.09):
    t = np.arange(int(length * SR)) / SR
    f = f0 + (f1 - f0) * (t / length) ** 0.6
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.03) * np.minimum(1, t / 0.002)
def click():
    t = np.arange(int(0.03 * SR)) / SR
    return (np.sin(2 * np.pi * 1800 * t) * 0.6 + rng.standard_normal(len(t)) * 0.2) * np.exp(-t / 0.005)
def sparkle(length=0.7, count=14):
    out = np.zeros(int(length * SR))
    for j in range(count):
        f = rng.uniform(2500, 5200); st = int(rng.uniform(0, length - 0.12) * SR)
        t = np.arange(int(0.12 * SR)) / SR
        out[st:st + len(t)] += np.sin(2 * np.pi * f * t) * np.exp(-t / 0.03) * (1 - st / len(out))
    return out
def thud():
    t = np.arange(int(0.25 * SR)) / SR
    f = 90 * (1 + 1.5 * np.exp(-t / 0.02))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.07)
for a in [0.05, 6.86, 9.5, 13.9, 14.5, 15.1, 16.88, 21.12, 25.5, 26.9, 29.0]:
    add(fx, bubble(), a, 0.32)
for a in [3.85, 4.74, 11.66, 24.4]:
    add(fx, click(), a, 0.35)
add(fx, sparkle(), 18.3, 0.18, pan=0.2)
add(fx, sparkle(0.5), 15.3, 0.12)
add(fx, thud(), 22.86, 0.5)
add(fx, bubble(300, 700, 0.12), 21.6, 0.22)
add(fx, thud(), 8.06, 0.35)          # "Gone." – Karte fällt
add(fx, sparkle(0.9, 22), 27.72, 0.22)  # Ring zerbricht
for name, buf in (('music14', mus), ('sfx14', fx)):
    o = np.clip(buf, -0.98, 0.98)
    with wave.open(os.path.join(HERE, f'public/{name}.wav'), 'wb') as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes((o * 32767).astype(np.int16).tobytes())
print('ok')
