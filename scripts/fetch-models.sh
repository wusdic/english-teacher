#!/usr/bin/env bash
# Downloads the offline speech models bundled into the APK so the app needs no network at
# runtime. Run before building the Android app (CI does this automatically). Safe to re-run.
set -euo pipefail

ASSETS_DIR="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets"
mkdir -p "$ASSETS_DIR"
cd "$ASSETS_DIR"

# --- Vosk offline English speech-recognition model ---
# Larger "lgraph" model (~128 MB) — markedly better recognition (especially for accented,
# non-native English) than the tiny 40 MB small model, while still bundling into the APK and
# running fully offline. Change MODEL_NAME to swap models; the version is tracked in the uuid
# marker so switching triggers a re-download instead of silently keeping the old model.
MODEL_NAME="vosk-model-en-us-0.22-lgraph"
VOSK_URL="https://alphacephei.com/vosk/models/${MODEL_NAME}.zip"
VOSK_DIR="vosk-model"

current_marker="$(cat "$VOSK_DIR/uuid" 2>/dev/null || true)"
if [ "$current_marker" = "$MODEL_NAME" ] && [ -d "$VOSK_DIR/am" ]; then
  echo "Vosk model '$MODEL_NAME' already present in $ASSETS_DIR/$VOSK_DIR — skipping download."
else
  echo "Downloading Vosk model '$MODEL_NAME'…"
  rm -rf "$VOSK_DIR" "$MODEL_NAME" vosk.zip
  curl -fL --retry 3 -o vosk.zip "$VOSK_URL"
  unzip -q vosk.zip
  rm -f vosk.zip
  mv "$MODEL_NAME" "$VOSK_DIR"
  echo "Vosk model ready at $ASSETS_DIR/$VOSK_DIR"
fi

# vosk-android's StorageService.unpack requires a 'uuid' marker file inside the model dir to
# decide whether it needs (re)unpacking. The stock model zip does not ship one, which makes
# unpack fail with "vosk-model/uuid". Write the model name as a stable, version-aware marker.
echo "$MODEL_NAME" > "$VOSK_DIR/uuid"
echo "Wrote $VOSK_DIR/uuid marker ($MODEL_NAME)"
