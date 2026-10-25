#!/usr/bin/env python3
"""External SIGKILL checks on an explicitly selected isolated API 36 emulator.
Requires already-built and installed debug/test APKs; never installs or clears data.
Only BackupCrashTest's synthetic app_backup-crash-fixture directory is touched.
"""
import os
import hashlib
import json
from verification import source_digest, sha256
from pathlib import Path
import subprocess
import time


def main():
    serial = os.environ.get('ANDROID_SERIAL', '')
    if not serial.startswith('emulator-'):
        raise SystemExit('Select an isolated emulator explicitly with ANDROID_SERIAL.')
    adb = ['adb', '-s', serial]
    def shell(*args, check=True):
        return subprocess.run(adb + ['shell', *args], text=True, capture_output=True, check=check)
    if shell('getprop', 'ro.build.version.sdk').stdout.strip() != '36':
        raise SystemExit('API 36 required')
    package = 'com.seen.film.debug'
    runner = package + '.test/com.seen.film.SeenTestRunner'
    output = Path('.tools/v0.3.0/crash'); output.mkdir(parents=True, exist_ok=True)
    app_apk = Path('app/build/outputs/apk/debug/app-debug.apk')
    test_apk = Path('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
    for target, apk in [(package, app_apk), (package + '.test', test_apk)]:
        installed = shell('pm', 'path', target).stdout.strip().removeprefix('package:')
        raw = subprocess.check_output(adb + ['exec-out', 'cat', installed])
        if hashlib.sha256(raw).hexdigest() != sha256(apk):
            raise SystemExit('Installed APK differs from built APK: ' + target)
    phases = ['media-ready', 'sql-deleted', 'committed']
    for phase in phases:
        common = ['am', 'instrument', '-w', '-e', 'backupCrashPhase', phase, '-e', 'class']
        log = (output / (phase + '-killed.log')).open('w')
        process = subprocess.Popen(adb + ['shell'] + common + ['com.seen.film.data.BackupCrashTest#prepareAndWaitForExternalKill', runner], stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                ready = shell('run-as', package, 'cat', 'app_backup-crash-fixture/ready', check=False)
                if ready.returncode == 0 and ready.stdout.strip() == phase:
                    break
                if process.poll() is not None:
                    raise RuntimeError(f'{phase}: instrumentation exited before checkpoint')
                time.sleep(0.5)
            else:
                raise RuntimeError(f'{phase}: checkpoint timed out')
            pid = shell('pidof', package).stdout.strip().split()
            if len(pid) != 1:
                raise RuntimeError(f'Expected one fixture process, got {pid}')
            shell('run-as', package, 'kill', '-9', pid[0])
            process.wait(timeout=15)
        finally:
            if process.poll() is None: process.terminate()
            log.close()
        result = shell(*common, 'com.seen.film.data.BackupCrashTest#verifyAfterExternalKill', runner)
        (output / (phase + '-verified.log')).write_text(result.stdout + result.stderr)
        if 'OK (1 test)' not in result.stdout or 'FAILURES' in result.stdout:
            raise RuntimeError(f'{phase}: persisted-state verification failed: {result.stdout}')
        print(f'PASS external SIGKILL at {phase}: full records and poster hashes verified', flush=True)
    (output / 'result.json').write_text(json.dumps(dict(passed=True, serial=serial, androidApi=36,
        sourceSha256=source_digest(), apkSha256=sha256(app_apk), testApkSha256=sha256(test_apk), phases=phases), indent=2) + '\n')


if __name__ == '__main__':
    main()
