#!/usr/bin/env bash
# Downloads the offline speech models bundled into the APK so the app needs no network at
# runtime. Run before building the Android app (CI does this automatically). Safe to re-run.
set -euo pipefail

ASSETS_DIR="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets"
mkdir -p "$ASSETS_DIR"
cd "$ASSETS_DIR"

# --- Vosk offline English speech-recognition model (~40 MB) ---
VOSK_URL="https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
VOSK_DIR="vosk-model"

if [ -f "$VOSK_DIR/README" ] || [ -d "$VOSK_DIR/am" ]; then
  echo "Vosk model already present in $ASSETS_DIR/$VOSK_DIR — skipping download."
else
  echo "Downloading Vosk model…"
  curl -fL --retry 3 -o vosk.zip "$VOSK_URL"
  unzip -q vosk.zip
  rm -f vosk.zip
  rm -rf "$VOSK_DIR"
  mv vosk-model-small-en-us-0.15 "$VOSK_DIR"
  echo "Vosk model ready at $ASSETS_DIR/$VOSK_DIR"
fi
