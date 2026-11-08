#!/usr/bin/env python3
"""Neue Stimme für Video 6 einbauen: Pausen kürzen + Tempo, Wort-Anker zur Original-Aufnahme (voice6.wav),
Zeitkarte src/map6<suf>.ts, Effekte public/sfx6<suf>.wav.  Aufruf: build6v.py <roh.mp3> <suf> [tempo] [pause]"""
import json, os, re, subprocess, sys, tempfile, wave, difflib
import numpy as np
from faster_whisper import WhisperModel
raw, suf = sys.argv[1], sys.argv[2]
TEMPO = float(sys.argv[3]) if len(sys.argv) > 3 else 1.3
KEEP = float(sys.argv[4]) if len(sys.argv) > 4 else 0.3
H = os.path.dirname(os.path.abspath(__file__)); tmp = tempfile.mkdtemp()
full = f"{tmp}/full.wav"; subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", raw, "-ar", "48000", "-ac", "2", full], check=True)
w = wave.open(full); sr = w.getframerate(); ch = w.getnchannels()
a = np.frombuffer(w.readframes(w.getnframes()), np.int16).reshape(-1, ch).astype(np.float32)
mono = np.abs(a).mean(1); hop = int(0.01 * sr)
rms = np.array([np.sqrt((mono[i:i + hop] ** 2).mean()) for i in range(0, len(mono), hop)])
loud = rms > 0.02 * 32768 * 0.1
segs, i, n = [], 0, len(loud)
while i < n:
    if not loud[i]:
        j = i
        while j < n and not loud[j]: j += 1
        if (j - i) * 0.01 > 0.2 and i > 0 and j < n: segs.append((i * 0.01, j * 0.01))
        i = j
    else: i += 1
out, pos = [], 0.0
for s, e in segs:
    out.append(a[int(pos * sr):int((s + KEEP / 2) * sr)]); pos = e - KEEP / 2
out.append(a[int(pos * sr):])
fade = int(0.012 * sr)
for k in range(len(out)):
    seg = out[k].copy()
    if len(seg) > 2 * fade:
        r = np.linspace(0, 1, fade)[:, None]
        if k > 0: seg[:fade] *= r
        if k < len(out) - 1: seg[-fade:] *= r[::-1]
    out[k] = seg
with wave.open(f"{tmp}/cut.wav", "wb") as o:
    o.setnchannels(ch); o.setsampwidth(2); o.setframerate(sr); o.writeframes(np.concatenate(out).astype(np.int16).tobytes())
voice = f"{H}/public/voice6{suf}.wav"
subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", f"{tmp}/cut.wav", "-af", f"atempo={TEMPO}", voice], check=True)
m = WhisperModel("small", compute_type="int8")
def words(f):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", f, "-ac", "1", "-ar", "16000", f"{tmp}/w.wav"], check=True)
    w = wave.open(f"{tmp}/w.wav"); x = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768
    sg, _ = m.transcribe(x, language="en", word_timestamps=True)
    return [(re.sub(r"[^a-z]", "", y.word.lower()), y.start) for s in sg for y in s.words], len(x) / 16000
N, nd = words(voice); O, _ = words(f"{H}/public/voice6.wav")
print(" ".join(f"{a}@{b:.2f}" for a, b in N))
sm = difflib.SequenceMatcher(a=[x for x, _ in O], b=[x for x, _ in N], autojunk=False)
pairs = [(0.0, 0.0)]
for b in sm.get_matching_blocks():
    for k in range(b.size):
        o, nn = O[b.a + k][1], N[b.b + k][1]
        if o > pairs[-1][0] + 0.05 and nn > pairs[-1][1] + 0.02 and o < 49.3: pairs.append((o, nn))
lo, ln = pairs[-1]
pairs.append((lo + (nd - ln), nd))
sign = [t for x, t in N if x == "sign"][0]
def to_orig(t):
    for (o1, n1), (o2, n2) in zip(pairs, pairs[1:]):
        if n1 <= t <= n2 and n2 > n1: return o1 + (t - n1) / (n2 - n1) * (o2 - o1)
    return pairs[-1][0]
json.dump(pairs, open(f"{H}/map6{suf}.json", "w"))
S = suf.upper()
with open(f"{H}/src/map6{suf}.ts", "w") as f:
    f.write(f"// Zeitkarte Video 6 Fassung {suf}: [neu, original] (erzeugt von build6v.py)\n")
    f.write(f"export const MAP6{S}: [number, number][] = " + json.dumps([[round(nn, 3), round(o, 3)] for o, nn in pairs]) + ";\n")
    f.write(f"export const MAP6{S}_SIGN = {round(to_orig(sign), 3)};\n")
    f.write(f"export const DURATION6{S}_S = {round(nd + 1.8, 2)};\n")
subprocess.run([sys.executable, f"{H}/sfx6.py"], env={**os.environ, "FAST": "1", "SUF": suf}, check=True)
os.replace(f"{H}/public/sfx6fast{suf}.wav", f"{H}/public/sfx6{suf}.wav")
print("fertig", suf, round(nd, 2), "s, sign", round(to_orig(sign), 2))
