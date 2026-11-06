#!/bin/sh
# Macht aus der ElevenLabs-Aufnahme (public/voice_raw.mp3) die Tonspur public/voice.wav:
# "Fuck" in "Fuck Shorts" (30,25–30,48 s) stumm und durch einen Piep ersetzt.
set -e
cd "$(dirname "$0")/public"
ffmpeg -v error -y -i voice_raw.mp3 -af "volume=enable='between(t,30.25,30.48)':volume=0" -ar 48000 -ac 2 /tmp/muted.wav
ffmpeg -v error -y -f lavfi -i "sine=frequency=1000:sample_rate=48000:duration=0.23" \
  -af "volume=0.18,afade=t=in:d=0.01,afade=t=out:st=0.21:d=0.02,adelay=30250|30250,apad=whole_dur=40" -ac 2 /tmp/beep.wav
ffmpeg -v error -y -i /tmp/muted.wav -i /tmp/beep.wav -filter_complex "amix=inputs=2:normalize=0,apad=whole_dur=41" -t 41 voice.wav
