#!/usr/bin/env bash
# Official CLI-only Android SDK installation for Seen on Linux x86_64.
set -euo pipefail
seen_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
seen_sdk="${SEEN_ANDROID_SDK:-$seen_root/.tools/android-sdk}"
case "${1:-}" in
    ''|--emulator) ;;
    *) echo 'Usage: install_android_sdk.sh [--emulator]' >&2; exit 2 ;;
esac
seen_packages=('platform-tools' 'platforms;android-36' 'build-tools;35.0.0')
if [[ "${1:-}" == --emulator ]]; then
    seen_packages+=('emulator' 'system-images;android-36;google_apis;x86_64')
fi
seen_java_home="${SEEN_JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
seen_cli_url='https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip'
# SHA-256 from https://developer.android.com/studio (checked 2026-10-23).
seen_cli_sha='4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583'
for seen_command in curl unzip sha256sum python3 flock; do
    command -v "$seen_command" >/dev/null || { echo "Missing command: $seen_command" >&2; exit 1; }
done
[[ "$(uname -s)" == Linux && "$(uname -m)" == x86_64 ]] || { echo 'This installer supports Linux x86_64 only.' >&2; exit 1; }
[[ -x "$seen_java_home/bin/java" ]] || { echo "JDK not found: $seen_java_home" >&2; exit 1; }
export JAVA_HOME="$seen_java_home"
# Recent SDK tools may require a newer Java than the project's Gradle JDK.
seen_sdk_java_home="${SEEN_SDK_JAVA_HOME:-$seen_java_home}"
seen_studio="${ANDROID_STUDIO_HOME:-$seen_root/.tools/android-studio}"
if [[ -z "${SEEN_SDK_JAVA_HOME:-}" && -x "$seen_studio/jbr/bin/java" ]]; then
    seen_sdk_java_home="$seen_studio/jbr"
fi
export ANDROID_USER_HOME="$seen_root/.tools/android-user"
export ANDROID_AVD_HOME="$seen_root/.tools/android-avd"
export GRADLE_USER_HOME="$seen_root/.tools/gradle-home"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME" "$GRADLE_USER_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
# Java SDK Manager does not automatically use the shell's HTTP_PROXY variables.
seen_proxy_args=()
mapfile -t seen_proxy_args < <(python3 - "$seen_root" <<'PROXY'
import os, sys
from pathlib import Path
import xml.etree.ElementTree as ET
from urllib.parse import urlsplit
raw = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy') or os.environ.get('HTTP_PROXY') or os.environ.get('http_proxy')
if not raw:
    settings = Path(sys.argv[1]) / '.tools/studio-profile/config/options/proxy.settings.xml'
    if settings.exists():
        options = {x.get('name'): x.get('value') for x in ET.parse(settings).iter('option')}
        if options.get('USE_HTTP_PROXY') == 'true' and options.get('PROXY_HOST') and options.get('PROXY_PORT'):
            raw = 'http://' + options['PROXY_HOST'] + ':' + options['PROXY_PORT']
p = urlsplit(raw or '')
if p.scheme in ('http', 'socks', 'socks5') and p.hostname and p.port and not p.username and not p.password:
    print('--proxy=' + ('http' if p.scheme == 'http' else 'socks'))
    print('--proxy_host=' + p.hostname)
    print('--proxy_port=' + str(p.port))
PROXY
)
seen_curl_proxy_args=()
if [[ ${#seen_proxy_args[@]} -eq 3 ]]; then
    seen_proxy_scheme="${seen_proxy_args[0]#--proxy=}"
    [[ "$seen_proxy_scheme" != socks ]] || seen_proxy_scheme=socks5h
    seen_proxy_host="${seen_proxy_args[1]#--proxy_host=}"
    seen_proxy_port="${seen_proxy_args[2]#--proxy_port=}"
    seen_curl_proxy_args=(--proxy "$seen_proxy_scheme://$seen_proxy_host:$seen_proxy_port")
fi
mkdir -p "$seen_root/.tools"
exec 9>"$seen_root/.tools/android-sdk-install.lock"
flock -n 9 || { echo 'Another SDK installation is running.' >&2; exit 1; }
seen_temp="$(mktemp -d "$seen_root/.tools/android-sdk-install.XXXXXX")"
trap 'rm -rf -- "$seen_temp"' EXIT
if [[ ! -x "$seen_sdk/cmdline-tools/latest/bin/sdkmanager" ]]; then
    echo 'Downloading Android command-line tools from Google...'
    if [[ -f "$seen_root/.tools/downloads/commandlinetools-linux-15859902_latest.zip" ]]; then
        cp -- "$seen_root/.tools/downloads/commandlinetools-linux-15859902_latest.zip" "$seen_temp/commandline-tools.zip"
    elif [[ -f "${HOME}/Downloads/commandlinetools-linux-15859902_latest.zip" ]]; then
        cp -- "${HOME}/Downloads/commandlinetools-linux-15859902_latest.zip" "$seen_temp/commandline-tools.zip"
    elif ! curl "${seen_curl_proxy_args[@]}" --fail --location --retry 2 --connect-timeout 10 --max-time 600 \
        --output "$seen_temp/commandline-tools.zip" "$seen_cli_url"; then
        echo 'SDK download failed. Check this terminal network/proxy; no completed installation is claimed.' >&2
        exit 1
    fi
    printf '%s  %s\n' "$seen_cli_sha" "$seen_temp/commandline-tools.zip" | sha256sum --check --status
    unzip -q "$seen_temp/commandline-tools.zip" -d "$seen_temp/unpacked"
    [[ -f "$seen_temp/unpacked/cmdline-tools/bin/sdkmanager" ]]
    mkdir -p "$seen_sdk/cmdline-tools"
    if [[ -e "$seen_sdk/cmdline-tools/latest" ]]; then
        echo 'Existing incomplete cmdline-tools/latest found; preserving it. Inspect it before retrying.' >&2
        exit 1
    fi
    mv -- "$seen_temp/unpacked/cmdline-tools" "$seen_sdk/cmdline-tools/latest"
fi
export ANDROID_HOME="$seen_sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
# SDK Manager prompts only for licenses needed by the requested packages.
JAVA_HOME="$seen_sdk_java_home" sdkmanager --sdk_root="$seen_sdk" "${seen_proxy_args[@]}" "${seen_packages[@]}"
[[ -f "$seen_sdk/platforms/android-36/android.jar" ]]
[[ -x "$seen_sdk/build-tools/35.0.0/aapt2" ]]
[[ -x "$seen_sdk/platform-tools/adb" ]]
JAVA_HOME="$seen_sdk_java_home" sdkmanager --version
adb version
python3 - "$seen_root" "$seen_sdk" <<'PY'
from pathlib import Path
import sys
root, sdk = map(Path, sys.argv[1:])
local = root / 'local.properties'
lines = local.read_text().splitlines() if local.exists() else []
lines = [line for line in lines if not line.lstrip().startswith(('sdk.dir=', 'sdk.dir =', 'sdk.dir:'))]
value = str(sdk).replace('\\', '\\\\').replace(' ', '\\ ')
local.write_text('\n'.join(lines + ['sdk.dir=' + value]) + '\n')
PY
{
    printf 'export JAVA_HOME=%q\n' "$JAVA_HOME"
    printf 'export ANDROID_HOME=%q\n' "$ANDROID_HOME"
    printf 'export ANDROID_USER_HOME=%q\n' "$ANDROID_USER_HOME"
    printf 'export ANDROID_AVD_HOME=%q\n' "$ANDROID_AVD_HOME"
    printf 'export GRADLE_USER_HOME=%q\n' "$GRADLE_USER_HOME"
    printf 'export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"\n'
} > "$seen_root/.tools/android-env.sh"
echo "SDK installed and verified: $seen_sdk"
echo 'Project local.properties configured. Next build:'
echo "  cd '$seen_root'"
echo '  source .tools/android-env.sh'
echo '  ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug'
