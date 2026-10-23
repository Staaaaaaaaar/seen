#!/usr/bin/env bash
# Install without clearing data, then launch one explicitly selected device.
set -euo pipefail
seen_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$seen_root"
source scripts/env.sh
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
    echo '先运行 adb devices -l，再显式设置 ANDROID_SERIAL（如 emulator-5556）。' >&2
    exit 2
fi
adb -s "$ANDROID_SERIAL" get-state
./gradlew :app:assembleDebug --console=plain
adb -s "$ANDROID_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$ANDROID_SERIAL" shell am start -W -n com.seen.film.debug/com.seen.film.MainActivity
