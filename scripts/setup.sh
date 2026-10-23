#!/usr/bin/env bash
# Prepare and verify the development environment before opening the graphical IDE.
set -Eeuo pipefail
seen_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$seen_root"
seen_sdk="${SEEN_ANDROID_SDK:-$seen_root/.tools/android-sdk}"
seen_mode="${1:-}"
case "$seen_mode" in
    ''|--no-open|--check-only) ;;
    *) echo '用法：bash scripts/setup.sh [--no-open | --check-only]' >&2; exit 2 ;;
esac
mkdir -p .tools
seen_log="$seen_root/.tools/setup.log"
exec > >(tee -a "$seen_log") 2>&1
seen_step='检查环境'
seen_fail() {
    local seen_code=$?
    trap - ERR
    echo
    echo "配置在「$seen_step」停止；没有标记环境已就绪。"
    echo "日志：$seen_log"
    echo '保留当前文件，解决报错后可重复执行同一条命令。'
    exit "$seen_code"
}
trap seen_fail ERR
printf '\n=== Seen 环境配置 %s ===\n' "$(date -Iseconds)"
if [[ "$seen_mode" == --check-only ]]; then
    java -version
    if [[ ! -f "$seen_sdk/platforms/android-36/android.jar" || ! -x "$seen_sdk/platform-tools/adb" || ! -x "$seen_sdk/build-tools/35.0.0/aapt2" ]]; then
        echo 'SDK 尚未准备完整。请在普通 Ubuntu 终端运行：bash scripts/setup.sh'
        exit 2
    fi
    echo '所需 SDK 文件已存在；此检查不代表已通过编译或真机验收。'
    exit 0
fi
python3 scripts/configure_studio.py
echo '[1/3] 检查 Android SDK。'
seen_step='检查或安装 SDK'
if [[ -f "$seen_sdk/platforms/android-36/android.jar" && -x "$seen_sdk/platform-tools/adb" && -x "$seen_sdk/build-tools/35.0.0/aapt2" && -f .tools/android-env.sh ]]; then
    echo 'SDK 已就绪，跳过下载安装。'
else
    echo '安装缺失组件；所需许可证会在终端显示，请阅读后决定是否接受。'
    bash scripts/install_android_sdk.sh
fi
source .tools/android-env.sh
echo '[2/3] 验证工程、生成 Debug APK、执行单元测试和 lint。首次下载依赖可能较久。'
seen_step='构建与检查'
seen_build_log="$seen_root/.tools/build-latest.log"
if bash scripts/check.sh; then
    :
else
    seen_build_code=$?
    if rg -q 'ModuleVersionResolveException|because "path" is null|Could not resolve|SSLHandshakeException' "$seen_build_log"; then
        echo '检测到依赖下载/解析错误，追加运行、测试和编译器依赖诊断。'
        bash scripts/diagnose-dependencies.sh || true
    fi
    (exit "$seen_build_code")
fi
echo '[3/3] 构建检查已通过。模拟器和 Redmi 真机验收仍需实际运行。'
seen_step='打开 Android Studio'
if [[ "$seen_mode" != --no-open ]]; then
    echo '现在打开 Seen 工程；如系统询问是否信任此项目，请确认这是当前 Seen 工程。'
    bash scripts/studio.sh
else
    echo '准备完成。需要图形界面时运行：bash scripts/studio.sh'
fi
