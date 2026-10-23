#!/usr/bin/env bash
# Start the installed graphical IDE with Seen's local settings and JDK 17 builds.
set -euo pipefail
seen_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
seen_studio="${ANDROID_STUDIO_HOME:-$seen_root/.tools/android-studio}"
[[ -x "$seen_studio/bin/studio" ]] || { echo "Android Studio not found: $seen_studio" >&2; exit 1; }
source "$seen_root/scripts/env.sh"
export STUDIO_PROPERTIES="$seen_root/.tools/studio.properties"
# Let Studio use its tested bundled JBR, independently of the Gradle JDK.
unset STUDIO_JDK
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
mkdir -p "$ANDROID_HOME" "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME" "${GRADLE_USER_HOME:-$HOME/.gradle}"
python3 "$seen_root/scripts/configure_studio.py"
if [[ "${1:-}" == --configure-sdk ]]; then
    shift
elif [[ ! -f "$ANDROID_HOME/platforms/android-36/android.jar" || ! -x "$ANDROID_HOME/platform-tools/adb" || ! -x "$ANDROID_HOME/build-tools/35.0.0/aapt2" ]]; then
    echo 'Android SDK 尚未准备完整，直接打开项目会产生同步错误。' >&2
    echo '请在普通 Ubuntu 终端先运行：' >&2
    echo "  cd '$seen_root'" >&2
    echo '  bash scripts/setup.sh' >&2
    echo '如需仅打开 IDE 的 SDK 设置：bash scripts/studio.sh --configure-sdk' >&2
    exit 2
fi
if [[ $# -eq 0 ]]; then set -- "$seen_root"; fi
exec "$seen_studio/bin/studio" "$@"
