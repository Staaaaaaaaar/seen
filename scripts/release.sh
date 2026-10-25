#!/usr/bin/env bash
# Build a signed release locally. Never generates keys, uploads, tags or clears data.
set -euo pipefail
seen_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$seen_root"
source scripts/env.sh
python3 - <<'PY'
import os
from pathlib import Path
names = ['SEEN_KEYSTORE_FILE', 'SEEN_KEYSTORE_PASSWORD', 'SEEN_KEY_ALIAS', 'SEEN_KEY_PASSWORD']
if any(not os.environ.get(name) for name in names):
    raise SystemExit('请显式提供全部 SEEN_KEYSTORE_FILE/PASSWORD、SEEN_KEY_ALIAS/PASSWORD 环境变量；不生成或回退到调试签名。')
if not Path(os.environ['SEEN_KEYSTORE_FILE']).is_file():
    raise SystemExit('正式签名文件不存在')
PY
mkdir -p .tools
./gradlew :app:assembleRelease :app:lintRelease --console=plain 2>&1 | tee .tools/release-build-latest.log
python3 - <<'PY'
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import runpy
import shutil
import subprocess
root = Path.cwd()
sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
apk = root / 'app/build/outputs/apk/release/app-release.apk'
if not apk.is_file(): raise SystemExit('Missing signed release APK')
cert = subprocess.check_output([str(sdk/'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
badging = subprocess.check_output([str(sdk/'aapt'), 'dump', 'badging', str(apk)], text=True)
manifest = subprocess.check_output([str(sdk/'aapt'), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], text=True)
version = re.search(r'versionName\s*=\s*"([^"]+)"', (root/'app/build.gradle.kts').read_text()).group(1)
code = int(re.search(r'versionCode\s*=\s*(\d+)', (root/'app/build.gradle.kts').read_text()).group(1))
assert f"name='com.seen.film' versionCode='{code}' versionName='{version}'" in badging
assert 'application-debuggable' not in badging
assert not re.search(r'android:debuggable.*(?:0xffffffff|true)', manifest)
signer = re.search(r'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)', cert).group(1).lower()
expected_cert = subprocess.check_output([
    str(Path(os.environ['JAVA_HOME'])/'bin/keytool'), '-exportcert',
    '-keystore', os.environ['SEEN_KEYSTORE_FILE'], '-alias', os.environ['SEEN_KEY_ALIAS'],
    '-storepass:env', 'SEEN_KEYSTORE_PASSWORD',
])
assert signer == hashlib.sha256(expected_cert).hexdigest(), 'APK signer differs from the supplied release key'
destination = root / '.tools/releases' / version
destination.mkdir(parents=True, exist_ok=True)
name = f'seen-v{version}.apk'
shutil.copyfile(apk, destination/name)
receipt = dict(kind='release', version=version, versionCode=code, package='com.seen.film',
               verifiedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),
               gitCommit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
               sourceSha256=runpy.run_path(str(root/'scripts/verification.py'))['source_digest'](),
               apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(), signerSha256=signer,
               debuggable=False)
(destination/'release-result.json').write_text(json.dumps(receipt, indent=2)+'\n')
(destination/'SHA256SUMS').write_text(receipt['apkSha256']+'  '+name+'\n')
(destination/'SIGNING-CERT-SHA256.txt').write_text(signer+'\n')
(destination/'signature-verification.txt').write_text(cert)
print('Signed release verified:', destination/name)
print('APK SHA-256:', receipt['apkSha256'])
print('Signer SHA-256:', signer)
PY
