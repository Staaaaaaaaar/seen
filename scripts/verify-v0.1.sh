#!/usr/bin/env bash
# Complete local build + API 36 instrumentation gate. Does not publish or tag.
set -Eeuo pipefail
seen_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$seen_root"
python3 - <<'VERSION'
from pathlib import Path
import re
v = re.search(r'versionName\s*=\s*"([^"]+)"', Path('app/build.gradle.kts').read_text()).group(1)
if v != '0.1.0': raise SystemExit('此验证入口仅用于 v0.1.0')
VERSION
mkdir -p .tools
exec > >(tee -a .tools/v0.1-verification.log) 2>&1
seen_emulator_pid=''
seen_cleanup() {
    if [[ -n "$seen_emulator_pid" ]] && kill -0 "$seen_emulator_pid" 2>/dev/null; then
        # Stop only the process this script launched, never a pre-existing emulator.
        kill "$seen_emulator_pid" 2>/dev/null || true
        wait "$seen_emulator_pid" 2>/dev/null || true
    fi
}
trap seen_cleanup EXIT
seen_failure() {
    local seen_code=$?
    trap - ERR
    echo "v0.1 验证未完成（退出码 $seen_code），日志：$seen_root/.tools/v0.1-verification.log"
    exit "$seen_code"
}
trap seen_failure ERR
echo '=== 1/3：构建、单测、lint 与测试 APK 编译 ==='
if [[ ! -f app/gradle.lockfile || ! -f gradle/verification-metadata.xml ]]; then
    SEEN_CAPTURE_DEPENDENCIES=1 bash scripts/check.sh
else
    bash scripts/check.sh
fi
source .tools/android-env.sh
echo '=== 2/3：准备 Android 16 / API 36 专用模拟器 ==='
bash scripts/emulator.sh --prepare
export ANDROID_SERIAL=emulator-5556
if adb devices | awk '{print $1}' | grep -Fxq "$ANDROID_SERIAL"; then
    seen_existing="$(adb -s "$ANDROID_SERIAL" emu avd name | head -n 1 | tr -d '\r')"
    [[ "$seen_existing" == Seen_API36 ]] || { echo '端口 5556 已被其他模拟器占用；保留该设备，请先手动处理。' >&2; exit 1; }
else
    bash scripts/emulator.sh --headless > .tools/emulator.log 2>&1 &
    seen_emulator_pid=$!
fi
seen_deadline=$((SECONDS + 600))
seen_report_at=$((SECONDS + 15))
while [[ "$(adb -s "$ANDROID_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" != 1 ]]; do
    if [[ -n "$seen_emulator_pid" ]] && ! kill -0 "$seen_emulator_pid" 2>/dev/null; then
        tail -n 50 .tools/emulator.log
        echo '模拟器已退出，验证停止。' >&2; exit 1
    fi
    [[ $SECONDS -lt $seen_deadline ]] || { echo '模拟器启动超时，见 .tools/emulator.log' >&2; exit 1; }
    if [[ $SECONDS -ge $seen_report_at ]]; then
        echo '等待专用 API 36 模拟器启动…（详细日志：.tools/emulator.log）'
        seen_report_at=$((SECONDS + 15))
    fi
    sleep 3
done
[[ "$(adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" == 36 ]]
adb -s "$ANDROID_SERIAL" shell input keyevent 82
for seen_setting in window_animation_scale transition_animation_scale animator_duration_scale; do
    adb -s "$ANDROID_SERIAL" shell settings put global "$seen_setting" 0
done
echo '=== 3/3：Room、海报、草稿与完整界面流程测试 ==='
bash scripts/check.sh --device
python3 - <<'BUNDLE'
import importlib.util,json
from pathlib import Path
spec=importlib.util.spec_from_file_location('verification', 'scripts/verification.py')
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
source=v.source_digest()
apk=v.sha256(Path('app/build/outputs/apk/debug/app-debug.apk'))
for name in ['check-result','device-result']:
    receipt=json.loads(Path('.tools/'+name+'.json').read_text())
    if receipt['sourceSha256'] != source or receipt['apkSha256'] != apk:
        raise SystemExit('源码或 APK 在验证后发生变化，请重新执行验证。')
BUNDLE
mkdir -p .tools/v0.1.0
cp app/build/outputs/apk/debug/app-debug.apk .tools/v0.1.0/seen-v0.1.0-debug.apk
python3 scripts/git_preflight.py
cp .tools/check-result.json .tools/device-result.json .tools/git-preflight.json .tools/v0.1.0/
(cd .tools/v0.1.0 && sha256sum seen-v0.1.0-debug.apk > SHA256SUMS)
echo '本地自动化门槛已通过。交付文件位于 .tools/v0.1.0/。'
echo 'GitHub CI、版本 tag 与后续 Redmi 真机验收状态仍需分别核对。'
