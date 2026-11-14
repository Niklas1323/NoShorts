#!/usr/bin/env python3
"""Sprachaufnahme vorbereiten: lange Pausen kürzen (auf KEEP s), weiche Schnitte, Wörter mit Zeiten (faster-whisper).
Benutzung: python3 voiceprep.py public/voiceX_raw.mp3 public/voiceX.wav src/adX/wordsX.json [KEEP]"""
import subprocess, wave, json, sys, tempfile
import numpy as np
from faster_whisper import WhisperModel
raw, out_wav, out_json = sys.argv[1:4]
KEEP = float(sys.argv[4]) if len(sys.argv) > 4 else 0.32
tmp = tempfile.mkdtemp()
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', raw, '-ar', '48000', '-ac', '2', f'{tmp}/f.wav'], check=True)
w = wave.open(f'{tmp}/f.wav'); sr = w.getframerate(); ch = w.getnchannels()
a = np.frombuffer(w.readframes(w.getnframes()), np.int16).reshape(-1, ch).astype(np.float32)
mono = np.abs(a).mean(1); hop = int(0.01 * sr)
rms = np.array([np.sqrt((mono[i:i + hop] ** 2).mean()) for i in range(0, len(mono), hop)])
loud = rms > 0.02 * 32768 * 0.1
segs = []; i = 0; n = len(loud)
while i < n:
    if not loud[i]:
        j = i
        while j < n and not loud[j]: j += 1
        if (j - i) * 0.01 > 0.45 and i > 0 and j < n: segs.append((i * 0.01, j * 0.01))
        i = j
    else: i += 1
out = []; pos = 0.0
for s, e in segs: out.append(a[int(pos * sr):int((s + KEEP / 2) * sr)]); pos = e - KEEP / 2
out.append(a[int(pos * sr):])
fd = int(0.012 * sr)
for k in range(len(out)):
    sg = out[k].copy()
    if len(sg) > 2 * fd:
        r = np.linspace(0, 1, fd)[:, None]
        if k > 0: sg[:fd] *= r
        if k < len(out) - 1: sg[-fd:] *= r[::-1]
    out[k] = sg
with wave.open(out_wav, 'wb') as o:
    o.setnchannels(ch); o.setsampwidth(2); o.setframerate(sr); o.writeframes(np.concatenate(out).astype(np.int16).tobytes())
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', out_wav, '-ac', '1', '-ar', '16000', f'{tmp}/w.wav'], check=True)
w = wave.open(f'{tmp}/w.wav'); x = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768
m = WhisperModel('small', compute_type='int8')
sg, _ = m.transcribe(x, language='en', word_timestamps=True)
ws = [[y.word.strip(), round(y.start, 2), round(y.end, 2)] for s in sg for y in s.words]
json.dump(ws, open(out_json, 'w'))
print(len(segs), 'Pausen gekürzt; Dauer', round(len(x) / 16000, 2)); print(' '.join(f'{a}@{b}' for a, b, c in ws))
