"""Run with Python; executes the real POSIX entrypoint using a fake JVM."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SHELL = shutil.which('sh') or r'D:/software/Git/bin/sh.exe'
ENTRY = ROOT / 'deploy/docker/backend-entrypoint.sh'


class EntrypointTest(unittest.TestCase):
    def run_mode(self, mode, secrets):
        self.assertTrue(ENTRY.is_file(), 'Container entrypoint has not been implemented')
        temp_root = ROOT / '.tmp' / 'docker-entrypoint-tests'
        temp_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=temp_root) as temp:
            work = Path(temp)
            capture = work / 'capture.json'
            java = work / 'java'
            java.write_text('#!/bin/sh\nexec python "$CAPTURE_SCRIPT" "$@"\n', encoding='utf-8')
            java.chmod(0o755)
            spy = work / 'capture.py'
            spy.write_text('import os,json,sys\nfrom pathlib import Path\n'
                           'Path(os.environ["CAPTURE"]).write_text(json.dumps({'
                           '"args":sys.argv[1:],"env":{k:v for k,v in os.environ.items() '
                           'if k.startswith(("RBAC_","SPRING_DATASOURCE_"))}}))\n', encoding='utf-8')
            env = {k:v for k,v in os.environ.items() if not k.startswith(('RBAC_', 'SPRING_'))}
            env.update(PATH=str(work) + os.pathsep + env['PATH'], CAPTURE=str(capture), CAPTURE_SCRIPT=str(spy),
                       MSYS_NO_PATHCONV='1')
            for name, value in secrets.items():
                path = work / name
                if value is not None:
                    path.write_text(value, encoding='utf-8')
                env[name + '_FILE'] = path.as_posix()
            result = subprocess.run([SHELL, ENTRY.as_posix(), mode], env=env, capture_output=True, text=True)
            return result, json.loads(capture.read_text()) if capture.exists() else None

    def test_server_reads_literal_secrets_without_shell_evaluation(self):
        password = 'literal $HOME $(false); keep spaces'
        result, data = self.run_mode('server', {'SPRING_DATASOURCE_PASSWORD':password,
                                               'RBAC_IDEMPOTENCY_KEYS_JSON':'{"v1":"abc="}'})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(password, data['env']['SPRING_DATASOURCE_PASSWORD'])
        self.assertEqual(['-jar', '/app/app.jar'], data['args'])
        self.assertNotIn(password, result.stdout + result.stderr + str(data['args']))

    def test_missing_secret_fails_before_starting_java(self):
        result, data = self.run_mode('server', {'SPRING_DATASOURCE_PASSWORD':None})
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertIsNone(data)

    def test_migrate_uses_dedicated_entry_and_only_migration_password(self):
        result, data = self.run_mode('migrate', {'RBAC_MIGRATION_PASSWORD':'migration-only'})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('-Dloader.main=com.adgn.backend.migration.MigrationApplication', data['args'])
        self.assertNotIn('SPRING_DATASOURCE_PASSWORD', data['env'])

    def test_bootstrap_passes_both_confirmation_values_without_runtime_secrets(self):
        result, data = self.run_mode('bootstrap', {'RBAC_BOOTSTRAP_PASSWORD':'db-password',
                                                  'RBAC_BOOTSTRAP_ADMIN_PASSWORD':'initial-admin-password'})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('-Dloader.main=com.adgn.backend.bootstrap.BootstrapAdminApplication', data['args'])
        self.assertEqual('initial-admin-password', data['env']['RBAC_BOOTSTRAP_ADMIN_CONFIRM_PASSWORD'])
        self.assertNotIn('RBAC_MIGRATION_PASSWORD', data['env'])

    def test_unknown_mode_is_rejected(self):
        result, data = self.run_mode('typo', {})
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertIsNone(data)


if __name__ == '__main__':
    unittest.main()
