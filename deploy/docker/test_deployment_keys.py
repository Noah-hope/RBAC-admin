"""Exercise the standalone Java key generator without any cloud or database access."""
import base64
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'deploy/docker/GenerateDeploymentKeys.java'
JAVA = shutil.which('java') or 'D:/software/JDK/jdk-21/bin/java.exe'
NAMES = {name + '-' + kind + '.json' for name in ('admin', 'business', 'oa')
         for kind in ('private', 'public')} | {'idempotency-keys.json'}


def integer(value):
    return int.from_bytes(base64.urlsafe_b64decode(value + '=' * (-len(value) % 4)), 'big')


class DeploymentKeysTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(SOURCE.is_file(), 'Deployment key generator has not been implemented')
        parent = ROOT / '.tmp' / 'deployment-key-tests'
        parent.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name) / 'identity'

    def generate(self):
        return subprocess.run([JAVA, str(SOURCE), str(self.directory)], text=True, capture_output=True)

    def test_rsa_components_and_public_files_match_for_all_three_families(self):
        result = self.generate()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(NAMES, {p.name for p in self.directory.iterdir()})
        moduli = set()
        for name in ('admin', 'business', 'oa'):
            private = json.loads((self.directory / (name + '-private.json')).read_text())['keys'][0]
            public = json.loads((self.directory / (name + '-public.json')).read_text())['keys'][0]
            self.assertEqual({'kty', 'kid', 'use', 'alg', 'n', 'e'}, set(public))
            self.assertEqual(name + '-v1', public['kid'])
            self.assertEqual('RS256', public['alg'])
            self.assertEqual(public, {key: private[key] for key in public})
            n, e, d, p, q, dp, dq, qi = (integer(private[key]) for key in ('n', 'e', 'd', 'p', 'q', 'dp', 'dq', 'qi'))
            self.assertGreaterEqual(n.bit_length(), 2048)
            self.assertEqual(n, p * q)
            self.assertEqual(1, e * d % math.lcm(p - 1, q - 1))
            self.assertEqual(dp, d % (p - 1))
            self.assertEqual(dq, d % (q - 1))
            self.assertEqual(1, q * qi % p)
            self.assertEqual(1234567, pow(pow(1234567, d, n), e, n))
            self.assertNotIn(private['d'], result.stdout + result.stderr)
            moduli.add(n)
        self.assertEqual(3, len(moduli))

    def test_hmac_is_standard_base64_and_satisfies_application_minimum(self):
        result = self.generate()
        self.assertEqual(0, result.returncode, result.stderr)
        ring = json.loads((self.directory / 'idempotency-keys.json').read_text())
        self.assertEqual({'server-v1'}, set(ring))
        self.assertEqual(32, len(base64.b64decode(ring['server-v1'], validate=True)))
        self.assertNotIn(ring['server-v1'], result.stdout + result.stderr)
        if os.name != 'nt':
            self.assertEqual(0o700, self.directory.stat().st_mode & 0o777)
            for path in self.directory.iterdir():
                self.assertEqual(0o600, path.stat().st_mode & 0o777)

    def test_existing_partial_output_is_not_overwritten_or_completed(self):
        self.directory.mkdir()
        original = self.directory / 'oa-public.json'
        original.write_text('existing deployment material')
        result = self.generate()
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertEqual('existing deployment material', original.read_text())
        self.assertEqual([original], list(self.directory.iterdir()))

    def test_rerun_preserves_every_existing_byte(self):
        self.assertEqual(0, self.generate().returncode)
        before = {p.name:p.read_bytes() for p in self.directory.iterdir()}
        result = self.generate()
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertEqual(before, {p.name:p.read_bytes() for p in self.directory.iterdir()})


if __name__ == '__main__':
    unittest.main()
