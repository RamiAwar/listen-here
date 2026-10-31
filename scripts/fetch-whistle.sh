#!/bin/sh
# Downloads the Whistle speech engine and model (Apache-2.0, Cactus Compute) at pinned
# revisions and checks them against known hashes. Run once before building.
set -eu
cd "$(dirname "$0")/.."

NEEDLE_REV=d6c7d6a2e9655505e0f8e23339356dd1705900eb
WHISTLE_REV=741118687e1f008f06d1351a402a731274789790

fetch() { # url dest sha256
    mkdir -p "$(dirname "$2")"
    if [ ! -f "$2" ] || ! echo "$3  $2" | shasum -a 256 -c - >/dev/null 2>&1; then
        curl -fsSL -o "$2" "$1"
    fi
    echo "$3  $2" | shasum -a 256 -c -
}

HF=https://huggingface.co/Cactus-Compute
fetch "$HF/needle3/resolve/$NEEDLE_REV/android-arm64/libneedle.a" app/src/main/cpp/needle/arm64-v8a/libneedle.a \
    7aa6de6132a22bf45f5e903425feb66f159ad46f47fa558921bc4fd02c3a2fa0
fetch "$HF/needle3/resolve/$NEEDLE_REV/android-arm64/needle.h" app/src/main/cpp/needle/needle.h \
    a27dac4b3239eead91d124b7f7dbce5495c13019903fd817b68c16d39b055a99
fetch "$HF/whistle/resolve/$WHISTLE_REV/whistle.cact" app/src/main/assets/whistle.cact \
    b6e02f048568ac5d01a2042556c658061e699acbc0aa2a1439f52f3d461dffeb
