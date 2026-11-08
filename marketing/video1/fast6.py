#!/usr/bin/env python3
"""Video 6 doppelt so schnell: Pausen in voice6.wav auf 0.1 s kürzen, dann Tempo x1.5 (Tonhöhe bleibt).
Schreibt public/voice6fast.wav und src/map6.ts (Zeitkarte schnell -> original)."""
import json, os, subprocess, wave
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
import sys
TEMPO = float(sys.argv[1]) if len(sys.argv) > 1 else 1.5
KEEP = float(sys.argv[2]) if len(sys.argv) > 2 else 0.10
SUF = sys.argv[3] if len(sys.argv) > 3 else ""
w = wave.open(os.path.join(HERE, "public/voice6.wav")); sr = w.getframerate(); ch = w.getnchannels()
a = np.frombuffer(w.readframes(w.getnframes()), np.int16).reshape(-1, ch).astype(np.float32)
mono = np.abs(a).mean(1)
hop = int(0.01 * sr)
rms = np.array([np.sqrt((mono[i:i + hop] ** 2).mean()) for i in range(0, len(mono), hop)])
loud = rms > 0.02 * 32768 * 0.1  # ~ -34 dB
# Stille-Stücke > 0.2 s finden
segs, i, n = [], 0, len(loud)
while i < n:
    if not loud[i]:
        j = i
        while j < n and not loud[j]: j += 1
        if (j - i) * 0.01 > 0.2 and i > 0 and j < n: segs.append((i * 0.01, j * 0.01))
        i = j
    else:
        i += 1
pts = [(0.0, 0.0)]; out = []; pos = 0.0; newt = 0.0
for s, e in segs:
    out.append(a[int(pos * sr):int((s + KEEP / 2) * sr)]); newt += (s + KEEP / 2) - pos
    pts.append((s + KEEP / 2, newt))
    pos = e - KEEP / 2; pts.append((pos, newt))
out.append(a[int(pos * sr):]); newt += len(a) / sr - pos; pts.append((len(a) / sr, newt))
# kurze Ein-/Ausblendung an jeder Schnittstelle (sonst knackt es)
fade = int(0.012 * sr)
for k in range(len(out)):
    seg = out[k].copy()
    if len(seg) > 2 * fade:
        r = np.linspace(0, 1, fade)[:, None]
        if k > 0: seg[:fade] *= r
        if k < len(out) - 1: seg[-fade:] *= r[::-1]
    out[k] = seg
cut = np.concatenate(out).astype(np.int16)
tmp = os.path.join(HERE, "public/_cut6.wav")
with wave.open(tmp, "wb") as o:
    o.setnchannels(ch); o.setsampwidth(2); o.setframerate(sr); o.writeframes(cut.tobytes())
subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", tmp, "-af", f"atempo={TEMPO}", os.path.join(HERE, f"public/voice6fast{SUF}.wav")], check=True)
os.remove(tmp)
pts = [(o, n / TEMPO) for o, n in pts]
with open(os.path.join(HERE, f"src/map6{SUF}.ts"), "w") as f:
    f.write("// Zeitkarte Video 6 schnell: [neu, original] (erzeugt von fast6.py)\n")
    f.write(f"export const MAP6{SUF}: [number, number][] = " + json.dumps([[round(n, 3), round(o, 3)] for o, n in pts]) + ";\n")
    f.write(f"export const DURATION6F{SUF}_S = {round(pts[-1][1] + 1.6, 2)};\n")
with open(os.path.join(HERE, f"map6{SUF}.json"), "w") as f:
    json.dump(pts, f)
print("Pausen gekürzt:", len(segs), "neu", round(pts[-1][1], 2), "s (vorher", round(len(a) / sr, 2), ")")
