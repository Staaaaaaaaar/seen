#!/usr/bin/env python3
"""Produce source-bound verification receipts only from actual passing reports."""
from pathlib import Path
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def source_digest():
    files = set()
    for folder in ['app/src', 'app/schemas', 'gradle', 'scripts', '.github']:
        files.update(p for p in (ROOT / folder).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
    for name in ['settings.gradle.kts', 'build.gradle.kts', 'app/build.gradle.kts',
                 'gradle.properties', 'gradlew', 'gradlew.bat', 'app/gradle.lockfile', 'gradle.lockfile',
                 'settings-gradle.lockfile', 'buildscript-gradle.lockfile']:
        if (ROOT / name).is_file(): files.add(ROOT / name)
    digest = hashlib.sha256()
    for p in sorted(files):
        digest.update(str(p.relative_to(ROOT)).encode()); digest.update(b'\0')
        digest.update(hashlib.sha256(p.read_bytes()).digest())
    return digest.hexdigest()

def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def junit(folder):
    cases = []
    files = list(folder.rglob('*.xml'))
    if not files: raise ValueError(f'Missing JUnit reports: {folder}')
    for p in files:
        root = ET.parse(p).getroot()
        if root.findall('.//failure') or root.findall('.//error'):
            raise ValueError(f'JUnit failures: {p}')
        cases.extend((x.get('classname', ''), x.get('name', ''), x.find('skipped') is not None)
                     for x in root.iter('testcase'))
    if not cases or any(skipped for _, _, skipped in cases):
        raise ValueError('Tests missing or skipped; no successful receipt is issued')
    return sorted(set((cls, name) for cls, name, _ in cases))

def main():
    if sys.argv[1:] not in ([], ['--device']): raise ValueError('Usage: verification.py [--device]')
    device = '--device' in sys.argv[1:]
    version = re.search(r'versionName\s*=\s*"([^"]+)"', (ROOT / 'app/build.gradle.kts').read_text()).group(1)
    result = {'schemaVersion': 1, 'version': version,
              'verifiedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'sourceSha256': source_digest(), 'kind': 'device' if device else 'build'}
    git = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=ROOT, capture_output=True, text=True)
    result['gitCommit'] = git.stdout.strip() if git.returncode == 0 else None
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    if not apk.is_file(): raise ValueError('Debug APK is missing')
    result['apkSha256'] = sha256(apk)
    if device:
        cases = junit(ROOT / 'app/build/outputs/androidTest-results/connected')
        required = {'com.seen.film.data.RepositoryTest', 'com.seen.film.data.PosterStoreTest',
                    'com.seen.film.ui.MovieJourneyTest', 'com.seen.film.data.TmdbRepositoryTest',
                    'com.seen.film.ui.TmdbJourneyTest', 'com.seen.film.ui.TmdbFallbackJourneyTest',
                    'com.seen.film.ui.TmdbSettingsTest', 'com.seen.film.data.TmdbCredentialStoreTest',
                    'com.seen.film.data.BackupTest', 'com.seen.film.ui.BackupJourneyTest'}
        if not required.issubset({cls for cls, _ in cases}):
            raise ValueError('Required Room/media/UI device test classes have not all run')
        expected = {'com.seen.film.data.RepositoryTest': 7, 'com.seen.film.data.PosterStoreTest': 3,
                    'com.seen.film.ui.MovieJourneyTest': 1, 'com.seen.film.data.TmdbRepositoryTest': 5,
                    'com.seen.film.ui.TmdbJourneyTest': 1, 'com.seen.film.ui.TmdbFallbackJourneyTest': 1,
                    'com.seen.film.ui.TmdbSettingsTest': 1, 'com.seen.film.data.TmdbCredentialStoreTest': 2,
                    'com.seen.film.data.BackupTest': 9, 'com.seen.film.ui.BackupJourneyTest': 2}
        for cls, minimum in expected.items():
            if sum(c == cls for c, _ in cases) < minimum:
                raise ValueError(f'Not all required tests ran in {cls}')
        adb = Path(os.environ.get('ANDROID_HOME', '')) / 'platform-tools/adb'
        command = [str(adb)]
        if os.environ.get('ANDROID_SERIAL'): command.extend(['-s', os.environ['ANDROID_SERIAL']])
        api = subprocess.check_output(command + ['shell', 'getprop', 'ro.build.version.sdk'], text=True).strip()
        if api != '36': raise ValueError('The device gate requires actual Android API 36')
        result['androidApi'] = int(api)
        result['testCases'] = cases
        result['serial'] = os.environ.get('ANDROID_SERIAL', 'CI-managed')
        destination = ROOT / '.tools/device-result.json'
    else:
        cases = junit(ROOT / 'app/build/test-results/testDebugUnitTest')
        lint = ROOT / 'app/build/reports/lint-results-debug.xml'
        issues = ET.parse(lint).getroot().findall('issue')
        if any(x.get('severity') in ('Fatal', 'Error') for x in issues):
            raise ValueError('Lint errors remain; no successful build receipt is issued')
        schemas = list((ROOT / 'app/schemas/com.seen.film.data.SeenDatabase').glob('*.json'))
        if not schemas: raise ValueError('Generated Room schema is missing')
        schema = max(schemas, key=lambda path: int(path.stem))
        if not schema.is_file(): raise ValueError('Generated Room schema is missing')
        result.update(testCases=cases, lintWarnings=sum(x.get('severity') == 'Warning' for x in issues),
                      roomSchemaSha256=sha256(schema))
        destination = ROOT / '.tools/check-result.json'
    destination.parent.mkdir(exist_ok=True)
    temporary = destination.with_suffix('.tmp')
    temporary.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    temporary.replace(destination)
    print(f'Verified {result["kind"]}: {len(cases)} tests; receipt: {destination}')

if __name__ == '__main__':
    try: main()
    except (ValueError, OSError, ET.ParseError, subprocess.SubprocessError) as exc:
        print(f'Verification not accepted: {exc}', file=sys.stderr)
        sys.exit(1)
