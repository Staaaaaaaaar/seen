#!/usr/bin/env python3
"""Offline configuration checks. This is NOT a Kotlin compiler or Android test."""
from pathlib import Path
import json
import hashlib
import os
import re
import sys
import configparser
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'


def main():
    # Our catalog uses a flat section/key layout. ConfigParser reads the keys
    # on Python 3.10; inline TOML values are checked below rather than executed.
    catalog = configparser.ConfigParser(interpolation=None)
    catalog.read_string((ROOT / 'gradle/libs.versions.toml').read_text())
    for plugin in ['kotlin-android', 'kotlin-compose']:
        assert re.search(r'version.ref\s*=\s*"kotlin"', catalog['plugins'][plugin])
    for section in ['libraries', 'plugins']:
        for key, value in catalog[section].items():
            assert value.startswith('{') and value.endswith('}'), (section, key)
            reference = re.search(r'version.ref\s*=\s*"([^"]+)"', value)
            if reference:
                assert reference.group(1) in catalog['versions'], (section, key)
    for path in (ROOT / 'app/src').rglob('*.xml'):
        ET.parse(path)
    manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
    app = manifest.find('application')
    assert app is not None and app.get(ANDROID + 'allowBackup') == 'false'
    assert not manifest.findall('uses-permission'), 'Offline v0.1 must not request network/media-library permissions'
    for path in ['backup_rules.xml', 'data_extraction_rules.xml']:
        assert (ROOT / 'app/src/main/res/xml' / path).is_file()
    build = (ROOT / 'app/build.gradle.kts').read_text()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', build).group(1)
    ref = os.environ.get('GITHUB_REF', '')
    if ref.startswith('refs/tags/'):
        assert ref == f'refs/tags/v{version}', f'Tag does not match app version {version}'
    assert 'applicationIdSuffix = ".debug"' in build
    props = (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text()
    checksum = re.search(r'distributionSha256Sum=(\w+)', props).group(1)
    assert checksum == '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'
    wrapper = ROOT / 'gradle/wrapper/gradle-wrapper.jar'
    if not wrapper.exists():
        assert checksum in (ROOT / 'gradlew').read_text(), 'Bootstrap checksum mismatch'
    else:
        assert hashlib.sha256(wrapper.read_bytes()).hexdigest() == '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f', 'Wrapper JAR checksum mismatch'
    for path in (ROOT / 'app/src/main').rglob('*.kt'):
        assert 'fallbackToDestructiveMigration' not in path.read_text(), path
    # Every version-catalog accessor referenced by build scripts must be declared.
    accessors = {'libs.' + k.replace('-', '.') for k in catalog['libraries']}
    accessors |= {'libs.plugins.' + k.replace('-', '.') for k in catalog['plugins']}
    for path in [ROOT / 'build.gradle.kts', ROOT / 'app/build.gradle.kts']:
        for accessor in re.findall(r'libs\.[a-zA-Z][a-zA-Z0-9.]*', path.read_text()):
            assert accessor in accessors, (path, accessor)
    for path in [ROOT / 'docs/design-review.md', ROOT / 'README.md']:
        for link in re.findall(r'\]\(([^)]+)\)', path.read_text()):
            if not re.match(r'^[a-z]+://', link) and not link.startswith('#'):
                assert (path.parent / link.split('#')[0]).exists(), (path, link)
    print(f'PASS: configuration, resources, dependency aliases, privacy flags and version {version}')
    schemas = list((ROOT / 'app/schemas').rglob('*.json'))
    for path in schemas:
        json.loads(path.read_text())
    print('Room schema: ' + ('present (generated structure not runtime-tested here)' if schemas else 'NOT GENERATED; first real KSP build is required before tagging'))
    print('Kotlin compilation / Android unit tests / device tests: not performed by this script')


if __name__ == '__main__':
    try:
        main()
    except (AssertionError, ValueError, KeyError, AttributeError) as exc:
        print(f'FAIL: {exc}', file=sys.stderr)
        sys.exit(1)
