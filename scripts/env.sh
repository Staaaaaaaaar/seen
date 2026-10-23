#!/usr/bin/env bash
# Source this file. Resolve tools without installing or writing anything.
seen_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
seen_java="${SEEN_JAVA_HOME:-${JAVA_HOME:-}}"
if [[ -z "$seen_java" ]] && command -v java >/dev/null; then
    seen_java="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
fi
if [[ ! -x "$seen_java/bin/java" || ! -x "$seen_java/bin/javac" ]]; then
    echo '需要完整 JDK 17；请设置 JAVA_HOME 或 SEEN_JAVA_HOME。' >&2
    return 1
fi
seen_java_version="$("$seen_java/bin/java" -version 2>&1)"
if [[ ! "$seen_java_version" =~ version\ \"17[.\"] ]]; then
    echo "构建需要 JDK 17，当前为 $seen_java。请设置 JAVA_HOME 或 SEEN_JAVA_HOME。" >&2
    return 1
fi
export JAVA_HOME="$seen_java"
seen_local_sdk=''
if [[ -f "$seen_root/local.properties" ]]; then
    seen_local_sdk="$(python3 - "$seen_root/local.properties" <<'PY'
from pathlib import Path
import re, sys
for line in Path(sys.argv[1]).read_text().splitlines():
    match = re.match(r'\s*sdk\.dir\s*[=:]\s*(.*)', line)
    if match:
        print(re.sub(r'\\(.)', r'\1', match[1]))
        break
PY
)"
fi
export ANDROID_HOME="${SEEN_ANDROID_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${seen_local_sdk:-$seen_root/.tools/android-sdk}}}}"
if [[ "$ANDROID_HOME" != /* ]]; then
    echo 'Android SDK 路径必须为绝对路径。' >&2
    return 1
fi
if [[ -n "${ANDROID_SDK_ROOT:-}" && "$ANDROID_SDK_ROOT" != "$ANDROID_HOME" ]]; then
    echo 'ANDROID_HOME 与 ANDROID_SDK_ROOT 不一致，请统一 SDK 路径。' >&2
    return 1
fi
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$seen_root/.tools/android-user}"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$seen_root/.tools/android-avd}"
# Preserve an existing project cache; new clones use the standard Gradle cache.
if [[ -z "${GRADLE_USER_HOME:-}" && -d "$seen_root/.tools/gradle-home" && -z "${CI:-}" ]]; then
    export GRADLE_USER_HOME="$seen_root/.tools/gradle-home"
fi
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
