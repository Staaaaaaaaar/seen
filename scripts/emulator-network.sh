#!/usr/bin/env bash
# Optional, foreground Linux emulator networking aid; no APK or host DNS changes.
set -euo pipefail
seen_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$seen_root/scripts/env.sh"
[[ -n "${ANDROID_SERIAL:-}" ]] || { echo '请用 ANDROID_SERIAL 明确选择项目专用模拟器。' >&2; exit 2; }
exec python3 "$seen_root/scripts/emulator_network.py" --serial "$ANDROID_SERIAL" "$@"
