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
if [[ -f .tools/android-env.sh ]]; then source .tools/android-env.sh; fi
mkdir -p .tools
seen_extra=()
if [[ "${SEEN_CAPTURE_DEPENDENCIES:-0}" == 1 ]]; then
    seen_extra=(--write-locks --write-verification-metadata sha256)
fi
if [[ ${1:-} == --device ]]; then rm -f .tools/device-result.json; else rm -f .tools/check-result.json; fi
{
    python3 scripts/check_project.py
    if ./gradlew "${seen_tasks[@]}" "${seen_extra[@]}" --console=plain --stacktrace; then :; else exit "$?"; fi
    if [[ ${1:-} == --device ]]; then python3 scripts/verification.py --device; else python3 scripts/verification.py; fi
    if [[ ${1:-} != --device ]]; then
        [[ -f app/build/outputs/apk/debug/app-debug.apk ]]
        sha256sum app/build/outputs/apk/debug/app-debug.apk
    fi
} 2>&1 | tee ".tools/$seen_log_name"
