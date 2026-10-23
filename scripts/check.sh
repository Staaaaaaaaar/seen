#!/usr/bin/env bash
# Shared verification entry point for local development and GitHub Actions.
# Does not install SDK components, change dependencies or open the IDE.
set -euo pipefail
seen_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$seen_root"
case "${1:-}" in
    '') seen_tasks=(:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug); seen_log_name=build-latest.log ;;
    --device) seen_tasks=(:app:connectedDebugAndroidTest); seen_log_name=device-tests-latest.log ;;
    *) echo '用法：bash scripts/check.sh [--device]' >&2; exit 2 ;;
esac
mkdir -p .tools
if [[ ${1:-} == --device ]]; then rm -f .tools/device-result.json; else rm -f .tools/check-result.json; fi
source scripts/env.sh
{
    python3 scripts/check_project.py
    bash scripts/doctor.sh
    if [[ ${1:-} == --device ]]; then
        if [[ -z "${ANDROID_SERIAL:-}" ]]; then
            mapfile -t seen_devices < <(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')
            [[ ${#seen_devices[@]} == 1 ]] || { echo '请选择一个隔离 API 36 设备并设置 ANDROID_SERIAL。' >&2; exit 2; }
            export ANDROID_SERIAL="${seen_devices[0]}"
        fi
        [[ "$(adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" == 36 ]] || {
            echo '设备验收要求 API 36；未执行测试。' >&2; exit 2;
        }
    fi
    if ./gradlew "${seen_tasks[@]}" --console=plain --stacktrace; then :; else exit "$?"; fi
    if [[ ${1:-} == --device ]]; then python3 scripts/verification.py --device; else python3 scripts/verification.py; fi
    if [[ ${1:-} != --device ]]; then
        [[ -f app/build/outputs/apk/debug/app-debug.apk ]]
        sha256sum app/build/outputs/apk/debug/app-debug.apk
    fi
} 2>&1 | tee ".tools/$seen_log_name"
