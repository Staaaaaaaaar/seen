#!/usr/bin/env bash
# Configure local paths only. Installation is a separate, explicit operation.
set -euo pipefail
seen_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$seen_root"
case "${1:-}" in
    --check-only) exec bash scripts/doctor.sh ;;
    ''|--no-open|--install-sdk) ;;
    *) echo '用法: setup.sh [--install-sdk | --check-only | --no-open]' >&2; exit 2 ;;
esac
source scripts/env.sh
if [[ "${1:-}" == --install-sdk ]]; then bash scripts/install_android_sdk.sh; fi
bash scripts/doctor.sh
export SEEN_JAVA_HOME="$JAVA_HOME" SEEN_ANDROID_SDK="$ANDROID_HOME"
python3 scripts/configure_studio.py
mkdir -p .tools
printf 'source %q\n' "$seen_root/scripts/env.sh" > .tools/android-env.sh
echo '本机配置完成。下一步: bash scripts/check.sh；用 Android Studio 打开仓库根目录。'
