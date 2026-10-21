#!/bin/sh
# Copies the generated French data into the iOS bundle resources. The files are produced by
# `uv run data/fr/build.py`; the Gradle build does the same for Android.
set -e
ROOT="$(cd "$SRCROOT/.." && pwd)"
DATA="$ROOT/../public/data/fr"
OUT="$SRCROOT/Data/fr"
mkdir -p "$OUT"
for name in words forms story shelf conj grammar; do
    if [ ! -f "$DATA/$name.json" ]; then
        echo "error: missing $DATA/$name.json — run \`uv run data/fr/build.py\` first" >&2
        exit 1
    fi
    cp "$DATA/$name.json" "$OUT/$name.json"
done
