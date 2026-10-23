#!/usr/bin/env bash
# A dedicated local API 36 AVD. Never reuses a phone or erases an existing AVD.
set -euo pipefail
seen_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$seen_root"
seen_mode="${1:---show}"
case "$seen_mode" in --prepare|--headless|--show) ;; *) echo 'Usage: emulator.sh [--prepare | --headless | --show]' >&2; exit 2 ;; esac
source scripts/env.sh
seen_avd=Seen_API36
seen_sdk_java="${SEEN_SDK_JAVA_HOME:-$JAVA_HOME}"
seen_studio="${ANDROID_STUDIO_HOME:-$seen_root/.tools/android-studio}"
if [[ -z "${SEEN_SDK_JAVA_HOME:-}" && -x "$seen_studio/jbr/bin/java" ]]; then seen_sdk_java="$seen_studio/jbr"; fi
if [[ ! -x "$ANDROID_HOME/emulator/emulator" || ! -f "$ANDROID_HOME/system-images/android-36/google_apis/x86_64/package.xml" ]]; then
    echo '缺少模拟器组件；请用 Studio SDK Manager 安装 Emulator 与 API 36 Google APIs x86_64 镜像。' >&2
    echo 'Linux x86_64 可运行: bash scripts/install_android_sdk.sh --emulator' >&2
    exit 1
fi
mkdir -p "$ANDROID_AVD_HOME" "$ANDROID_USER_HOME"
if [[ ! -f "$ANDROID_AVD_HOME/$seen_avd.ini" ]]; then
    [[ -x "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" ]] || {
        echo '请用 SDK Manager 安装 Android SDK Command-line Tools (latest)。' >&2; exit 1;
    }
    # Reply to the optional custom-hardware-profile question, not to license prompts.
    printf 'no\n' | JAVA_HOME="$seen_sdk_java" "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
        --name "$seen_avd" --package 'system-images;android-36;google_apis;x86_64' --device pixel_7
fi
if [[ "$seen_mode" == --prepare ]]; then
    echo "API 36 AVD ready: $seen_avd"
    exit 0
fi
seen_accel=()
if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
    if [[ "${SEEN_EMULATOR_NO_ACCEL:-0}" == 1 ]]; then
        seen_accel=(-accel off)
    else
        echo '当前终端无法使用 /dev/kvm；请检查宿主机虚拟化和 kvm 用户组权限。' >&2
        echo '也可显式设置 SEEN_EMULATOR_NO_ACCEL=1 进行较慢的软件模拟。' >&2
        exit 1
    fi
fi
seen_ui=()
if [[ "$seen_mode" == --headless ]]; then seen_ui=(-no-window -gpu swiftshader); fi
exec "$ANDROID_HOME/emulator/emulator" -avd "$seen_avd" -port 5556 \
    -no-audio -no-snapshot -no-boot-anim "${seen_ui[@]}" "${seen_accel[@]}"
