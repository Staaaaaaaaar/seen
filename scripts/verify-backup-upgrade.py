#!/usr/bin/env python3
"""Real old-APK upgrade on an EMPTY isolated emulator. Never uninstalls or clears data.
Requires a supplied synthetic JPEG, already available SDK tools and built APKs.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import sqlite3
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--old-apk', required=True, type=Path)
    parser.add_argument('--new-apk', required=True, type=Path)
    parser.add_argument('--poster', required=True, type=Path)
    args = parser.parse_args()
    serial = os.environ.get('ANDROID_SERIAL', '')
    if not serial.startswith('emulator-'): raise SystemExit('Explicit isolated emulator required')
    adb = ['adb', '-s', serial]; package = 'com.seen.film.debug'
    def run(*command, data=None, check=True):
        return subprocess.run(adb + list(command), input=data, capture_output=True, check=check).stdout
    if run('shell', 'getprop', 'ro.build.version.sdk').strip() != b'36': raise SystemExit('API 36 required')
    if run('shell', 'pm', 'path', package, check=False).strip():
        raise SystemExit('Requires emulator without this package; choose an empty isolated emulator. Do not uninstall user data.')
    root = Path('.tools/v0.3.0/upgrade'); root.mkdir(parents=True, exist_ok=True)
    def stop(): run('shell', 'am', 'force-stop', package)
    def start(): run('shell', 'am', 'start', '-W', '-n', package + '/com.seen.film.MainActivity')
    def private(path, data):
        command = shlex.join(['run-as', package, 'sh', '-c', 'cat > ' + shlex.quote(path)])
        run('shell', command, data=data)
    def nodes():
        run('shell', 'uiautomator', 'dump', '/sdcard/seen-upgrade-window.xml')
        return ET.fromstring(run('exec-out', 'cat', '/sdcard/seen-upgrade-window.xml')).iter('node')
    def tap(text):
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            for node in nodes():
                if node.get('text') == text:
                    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
                    run('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)); return
            time.sleep(0.3)
        raise RuntimeError('Missing UI: ' + text)
    poster = '11111111-1111-4111-8111-111111111111.jpg'
    def seed(number):
        stop(); run('shell', 'run-as', package, 'mkdir', '-p', 'files/posters', 'shared_prefs')
        private('files/posters/' + poster, args.poster.read_bytes())
        private('shared_prefs/tmdb.xml', b'<map><string name="token">synthetic.valid.token</string></map>')
        draft = dict(schemaVersion=1, key=f'22222222-2222-4222-8222-22222222222{number}',
                     movieId='33333333-3333-4333-8333-333333333333', isNewMovie=number != 2, isEditing=False,
                     title='旧 APK 合成电影 🎬', originalTitle='Upgrade Fixture', year='2000', posterKey=poster,
                     date='2020-02-29', time='' if number == 1 else '00:00', location='隔离测试',
                     rating=None if number == 1 else 1, notes='中文 e\u0301 🎬\n' * 100,
                     tmdbId=4242, releaseDate='2000-01-01', directorNames='合成导演', runtimeMinutes=120, productionCountries='中国')
        if number == 3: draft.update(movieId='44444444-4444-4444-8444-444444444444', title='未完成草稿', tmdbId=None)
        private('files/viewing-draft.json', json.dumps(draft, ensure_ascii=False).encode()); start()
    def snapshot(phase):
        stop(); folder = root / phase; folder.mkdir(exist_ok=True)
        for name in ['seen.db', 'seen.db-wal', 'seen.db-shm']:
            result = subprocess.run(adb + ['exec-out', 'run-as', package, 'cat', 'databases/' + name], capture_output=True)
            if result.returncode == 0: (folder/name).write_bytes(result.stdout)
        database = sqlite3.connect(folder/'seen.db'); database.row_factory = sqlite3.Row
        result = {table: [dict(row) for row in database.execute('SELECT * FROM ' + table + ' ORDER BY id')] for table in ['movies', 'viewings']}; database.close()
        result['posterSha256'] = hashlib.sha256(run('exec-out', 'run-as', package, 'cat', 'files/posters/' + poster)).hexdigest()
        result['draft'] = json.loads(run('exec-out', 'run-as', package, 'cat', 'files/viewing-draft.json'))
        result['credentialPreferencesSha256'] = hashlib.sha256(run('exec-out', 'run-as', package, 'cat', 'shared_prefs/tmdb.xml')).hexdigest()
        (folder/'snapshot.json').write_text(json.dumps(result, ensure_ascii=False, indent=2))
        return result
    run('install', '-r', str(args.old_apk)); seed(1); tap('继续未完成的编辑'); tap('保存')
    time.sleep(1); seed(2); tap('继续未完成的编辑'); tap('保存')
    time.sleep(1); seed(3); before = snapshot('before')
    assert len(before['movies']) == 1 and len(before['viewings']) == 2
    run('install', '-r', str(args.new_apk)); start(); time.sleep(2)
    after = snapshot('after'); assert before == after, 'Upgrade changed records, media, draft or credentials'
    result = dict(passed=True, serial=serial, oldApkSha256=hashlib.sha256(args.old_apk.read_bytes()).hexdigest(),
                  newApkSha256=hashlib.sha256(args.new_apk.read_bytes()).hexdigest(), movies=1, viewings=2,
                  preserved=['all fields and IDs', 'poster bytes', 'unsaved draft', 'credentials'])
    (root/'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print('PASS actual old APK → new APK: all fields, IDs, two viewings, JPEG, draft and credentials unchanged')


if __name__ == '__main__': main()
