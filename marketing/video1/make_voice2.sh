#!/bin/sh
# Tonspur Video 2: "Fuck" (17,25–17,53 s) durch Piep ersetzen, dann Effekte erzeugen.
set -e
cd "$(dirname "$0")"
ffmpeg -v error -y -i public/voice2_raw.mp3 -af "volume=enable='between(t,17.25,17.53)':volume=0" -ar 48000 -ac 2 /tmp/m2.wav
ffmpeg -v error -y -f lavfi -i "sine=frequency=1000:sample_rate=48000:duration=0.28" \
  -af "volume=0.2,afade=t=in:d=0.01,afade=t=out:st=0.26:d=0.02,adelay=17250|17250" -ac 2 /tmp/b2.wav
ffmpeg -v error -y -i /tmp/m2.wav -i /tmp/b2.wav -filter_complex "amix=inputs=2:normalize=0:duration=first,apad=whole_dur=29.2" -t 29.2 public/voice2.wav
python3 sfx2.py
