#!/usr/bin/env bash
# User-triggered only. Never called by the editing workflow.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
case "${1:-ui}" in
  ui)
    : "${ANDROID_SERIAL:?请显式指定隔离测试设备 ANDROID_SERIAL}"
    ./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.seen.film.ui.RefinementJourneyTest,com.seen.film.ui.StatisticsJourneyTest"
    ;;
  unit) ./gradlew :app:testDebugUnitTest ;;
  full)
    : "${ANDROID_SERIAL:?请显式指定隔离测试设备 ANDROID_SERIAL}"
    bash scripts/check.sh
    bash scripts/check.sh --device
    ;;
  *) echo '用法：bash scripts/test-manual.sh [ui|unit|full]' >&2; exit 2 ;;
esac
