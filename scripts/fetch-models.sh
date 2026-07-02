#!/usr/bin/env bash
# Whisper branch: fetch the whisper.cpp native source + a Whisper model so the app can do
# fully-offline, on-device speech recognition. Run before building (CI does this). Safe to re-run.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS_DIR="$ROOT/app/src/main/assets"
CPP_DIR="$ROOT/app/src/main/cpp/whisper"
mkdir -p "$ASSETS_DIR" "$CPP_DIR"

# --- 1. whisper.cpp source (v1.5.4 has the flat ggml layout our CMakeLists expects) ---
WHISPER_VERSION="1.5.4"
WHISPER_TARBALL="https://github.com/ggerganov/whisper.cpp/archive/refs/tags/v${WHISPER_VERSION}.tar.gz"

if [ -f "$CPP_DIR/whisper.cpp" ] && [ -f "$CPP_DIR/ggml.c" ]; then
  echo "whisper.cpp source already present in $CPP_DIR — skipping."
else
  echo "Downloading whisper.cpp v${WHISPER_VERSION} source…"
  tmp="$(mktemp -d)"
  curl -fL --retry 3 -o "$tmp/whisper.tar.gz" "$WHISPER_TARBALL"
  tar -xzf "$tmp/whisper.tar.gz" -C "$tmp"
  src="$tmp/whisper.cpp-${WHISPER_VERSION}"
  # Copy just the sources our CMakeLists compiles, plus every header they include.
  cp "$src/whisper.cpp" "$src/whisper.h" "$CPP_DIR/"
  cp "$src"/ggml*.c "$src"/ggml*.h "$CPP_DIR/"
  rm -rf "$tmp"
  echo "whisper.cpp source ready at $CPP_DIR"
fi

# --- 2. Whisper model (base.en, q5_1 quantised, ~57 MB) ---
MODEL_DIR="$ASSETS_DIR/whisper-model"
MODEL_FILE="ggml-base.en-q5_1.bin"
MODEL_URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/${MODEL_FILE}"
mkdir -p "$MODEL_DIR"

if [ -f "$MODEL_DIR/$MODEL_FILE" ] && [ -s "$MODEL_DIR/$MODEL_FILE" ]; then
  echo "Whisper model already present — skipping download."
else
  echo "Downloading Whisper model ${MODEL_FILE}…"
  curl -fL --retry 3 -o "$MODEL_DIR/$MODEL_FILE" "$MODEL_URL"
  echo "Whisper model ready at $MODEL_DIR/$MODEL_FILE"
fi
