#!/usr/bin/env bash
# Regenerates every sound in the game from code and writes the OGG files the app ships.
# Needs ffmpeg (with libvorbis) on the PATH. Run from anywhere; commit the result.
set -euo pipefail
cd "$(dirname "$0")/.."
WAV="audiogen/build/audio"
RAW="app/src/main/res/raw"
rm -rf "$WAV"
./gradlew -q :audiogen:run --args="$PWD/$WAV"
mkdir -p "$RAW"
for f in "$WAV"/*.wav; do
  name="$(basename "$f" .wav)"
  case "$name" in
    music_*) quality=2 ; channels=2 ;;   # about 96 kbit/s stereo
    *)       quality=4 ; channels=1 ;;   # effects are short: mono, a little better
  esac
  ffmpeg -hide_banner -loglevel error -y -i "$f" -map_metadata -1 -fflags +bitexact \
    -c:a libvorbis -q:a "$quality" -ac "$channels" "$RAW/$name.ogg"
done
du -ch "$RAW"/*.ogg | tail -1
