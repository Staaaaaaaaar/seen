"""Regression coverage for clone-local paths, overrides and non-destructive setup."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]


class EnvironmentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='seen environment ')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        shutil.copytree(SCRIPTS, self.root / 'scripts', ignore=shutil.ignore_patterns('__pycache__'))
        self.java = self.root / 'jdk 17'
        (self.java / 'bin').mkdir(parents=True)
        for name in ('java', 'javac'):
            path = self.java / 'bin' / name
            path.write_text('#!/bin/sh\necho \'openjdk version "17.0.1"\' >&2\n')
            path.chmod(0o755)
        self.sdk = self.root / 'shared SDK'
        for name in ('platforms/android-36/android.jar', 'build-tools/35.0.0/aapt2', 'platform-tools/adb'):
            path = self.sdk / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.touch()
        self.env = {k: v for k, v in os.environ.items() if k in ('PATH', 'HOME', 'LANG', 'TMPDIR')}
        self.env.update(JAVA_HOME=str(self.java), ANDROID_HOME=str(self.sdk))

    def run_script(self, script, env=None):
        return subprocess.run(['bash', '-eu', str(self.root / 'scripts' / script)],
                              env=env or self.env, capture_output=True, text=True, cwd='/tmp')

    def test_doctor_is_read_only_with_spaces_in_paths(self):
        before = sorted(str(p.relative_to(self.root)) for p in self.root.rglob('*'))
        result = self.run_script('doctor.sh')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(str(self.sdk), result.stdout)
        self.assertEqual(before, sorted(str(p.relative_to(self.root)) for p in self.root.rglob('*')))

    def test_invalid_jdk_fails_before_writing(self):
        (self.java / 'bin/java').write_text('#!/bin/sh\necho \'openjdk version "21.0.1"\' >&2\n')
        result = self.run_script('setup.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('JDK 17', result.stderr)
        self.assertFalse((self.root / 'local.properties').exists())

    def test_conflicting_sdk_variables_fail(self):
        result = self.run_script('doctor.sh', {**self.env, 'ANDROID_SDK_ROOT': '/different/sdk'})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('不一致', result.stderr)

    def test_explicit_sdk_override_and_setup_preserve_other_settings(self):
        (self.root / 'local.properties').write_text('sdk.dir=/old/sdk\ncustom=value\n')
        env = {**self.env, 'ANDROID_HOME': '/missing', 'SEEN_ANDROID_SDK': str(self.sdk)}
        result = self.run_script('setup.sh', env)
        self.assertEqual(result.returncode, 0, result.stderr)
        first = (self.root / 'local.properties').read_text()
        self.assertIn('custom=value', first)
        self.assertNotIn('/old/sdk', first)
        idea = self.root / '.idea/gradle.xml'
        idea.write_text('<project>user preference</project>')
        self.assertEqual(self.run_script('setup.sh', env).returncode, 0)
        self.assertEqual(first, (self.root / 'local.properties').read_text())
        self.assertEqual(idea.read_text(), '<project>user preference</project>')

    def test_sdk_from_local_properties_without_environment(self):
        (self.root / 'local.properties').write_text('sdk.dir=' + str(self.sdk).replace(' ', '\\ ') + '\n')
        env = {k: v for k, v in self.env.items() if k != 'ANDROID_HOME'}
        result = self.run_script('doctor.sh', env)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(str(self.sdk), result.stdout)

    def test_missing_sdk_fails_without_installing(self):
        result = self.run_script('setup.sh', {**self.env, 'ANDROID_HOME': str(self.root / 'absent')})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('缺少 SDK', result.stderr)
        self.assertFalse((self.root / 'absent').exists())

    def test_run_requires_explicit_device(self):
        result = self.run_script('run.sh')
        self.assertEqual(result.returncode, 2)
        self.assertIn('ANDROID_SERIAL', result.stderr)


if __name__ == '__main__':
    unittest.main()
