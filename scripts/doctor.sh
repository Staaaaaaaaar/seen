#!/usr/bin/env bash
# Read-only preflight. No downloads, license acceptance or configuration writes.
set -euo pipefail
seen_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
for seen_command in python3 bash; do
    command -v "$seen_command" >/dev/null || { echo "缺少 $seen_command" >&2; exit 1; }
done
source "$seen_root/scripts/env.sh"
printf 'JDK: %s\nSDK: %s\nGradle cache: %s\n' "$JAVA_HOME" "$ANDROID_HOME" "${GRADLE_USER_HOME:-$HOME/.gradle}"
"$JAVA_HOME/bin/java" -version
seen_missing=0
for seen_file in platforms/android-36/android.jar build-tools/35.0.0/aapt2 platform-tools/adb; do
    if [[ ! -f "$ANDROID_HOME/$seen_file" ]]; then
        echo "缺少 SDK 组件: $seen_file" >&2
        seen_missing=1
    fi
done
printf 'Host: %s / %s\n' "$(uname -s)" "$(uname -m)"
if [[ -r /dev/kvm && -w /dev/kvm ]]; then
    echo 'KVM: 可访问（仅模拟器需要）'
else
    echo 'KVM: 当前终端不可访问；构建不受影响。模拟器请检查宿主机虚拟化/权限。'
fi
if [[ "$seen_missing" == 1 ]]; then
    echo '请用 Studio SDK Manager 安装缺失组件；Linux x86_64 也可用 setup.sh --install-sdk。' >&2
    exit 1
fi
echo '工具预检通过；不代表 Gradle 构建或设备测试通过。'
