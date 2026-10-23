#!/usr/bin/env bash
set -euo pipefail
seen_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$seen_root"
source scripts/env.sh
mkdir -p .tools
printf '仅诊断依赖，不重新安装 SDK，不清理缓存，也不修改版本。\n'
printf '日志：%s\n' "$seen_root/.tools/dependency-diagnosis.log"
./gradlew :app:seenDiagnoseDependencies \
    --init-script scripts/diagnose-dependencies.init.gradle \
    --console=plain 2>&1 | tee .tools/dependency-diagnosis.log
