"""CLI behavior tests: removing a gate must let the corresponding bad fixture pass."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT=Path(__file__).with_name('verify_release.py')
def digest(raw): return hashlib.sha256(raw).hexdigest()

class ReleaseGateTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        self.tasks=[dict(id=f'BE-{n:02}',status='OWNER_ACCEPTED',implementationProgress=dict(implemented=True,mergedToMain=True)) for n in range(1,36)]
        self.operations=[dict(operationId=f'operation{n}',direction='INBOUND',issue='BE-06') for n in range(67)]
        self.cases=[dict(id=f'AT-{n:02}',status='VERIFIED',delivery='BACKEND') for n in range(1,42) if n!=40]
        self.write('backend/src/main/java/Example.java',b'class Example {}\n')
        self.write('backend/src/test/java/example/SuiteTest.java',b'package example; class SuiteTest {}')
        self.write('backend/src/test/java/example/EngineMySqlCheck.java',b'package example; class EngineMySqlCheck {}')
        self.write('backend/target/app.jar',b'candidate jar')
        reports={}
        for folder,name in [('surefire','SuiteTest'),('failsafe','EngineMySqlCheck')]:
            path=f'backend/target/{folder}-reports/TEST-example.{name}.xml'
            raw=f'<testsuite name="example.{name}" tests="1" failures="0" errors="0" skipped="0"/>'.encode();self.write(path,raw);reports[path]=digest(raw)
        self.manifest=dict(schemaVersion=1,sourceSha256=digest(b'java/Example.java\0class Example {}\n'),artifact=dict(path='backend/target/app.jar',sha256=digest(b'candidate jar')),reports=reports,fullSuite=True)
        self.manifest['testSourceSha256']=digest(b'java/example/EngineMySqlCheck.java\0package example; class EngineMySqlCheck {}java/example/SuiteTest.java\0package example; class SuiteTest {}')
        self.manifest['supportSha256']=digest(b'')
        for field in ('server','endToEnd','ownerHandoff'):
            path=f'docs/{field}.json';raw=json.dumps(dict(environment='TARGET_SERVER',artifactSha256=digest(b'candidate jar'),sourceSha256=self.manifest['sourceSha256'],checksPassed=True,reviewedBy='fixture owner')).encode()
            self.write(path,raw);self.manifest[field]=dict(verified=True,path=path,sha256=digest(raw))
    def write(self,path,raw):
        file=self.root/path;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(raw)
    def run_gate(self):
        for name,value in [('issues',self.tasks),('operation-map',self.operations),('acceptance',self.cases)]:self.write(f'docs/05-detailed-design/{name}.json',json.dumps(value).encode())
        self.write('evidence.json',json.dumps(self.manifest).encode())
        return subprocess.run([sys.executable,str(SCRIPT),'--root',str(self.root),'--evidence','evidence.json'],capture_output=True,text=True)
    def blocked(self,code):
        result=self.run_gate();self.assertEqual(result.returncode,2,result.stderr);self.assertIn(code,[b['code'] for b in json.loads(result.stdout)['blockers']])
    def test_complete_verified_snapshot_is_ready(self):
        result=self.run_gate();self.assertEqual(result.returncode,0,result.stderr);self.assertTrue(json.loads(result.stdout)['ready'])
    def test_missing_business_implementation_blocks_release(self):
        self.tasks[24].update(status='PLANNED',implementationProgress={});self.blocked('TASK_NOT_IMPLEMENTED')
    def test_unmerged_local_work_blocks_release(self):
        self.tasks[17]['implementationProgress']['mergedToMain']=False;self.blocked('TASK_NOT_DELIVERED')
    def test_unaccepted_work_is_not_owner_accepted(self):
        self.tasks[5]['status']='IMPLEMENTED';self.blocked('OWNER_ACCEPTANCE_PENDING')
    def test_incomplete_contract_inventory_blocks_release(self):
        self.operations.pop();self.blocked('INBOUND_COVERAGE_INCOMPLETE')
    def test_partial_acceptance_is_not_full_verification(self):
        self.cases[0]['status']='PARTIALLY_VERIFIED';self.blocked('ACCEPTANCE_NOT_VERIFIED')
    def test_modified_jar_is_rejected(self):
        self.write('backend/target/app.jar',b'changed');self.blocked('ARTIFACT_DIGEST_MISMATCH')
    def test_changed_source_invalidates_old_reports(self):
        self.write('backend/src/main/java/Example.java',b'class Changed {}');self.blocked('SOURCE_DIGEST_MISMATCH')
    def test_changed_test_source_invalidates_old_reports(self):
        self.write('backend/src/test/java/example/SuiteTest.java',b'package example; class SuiteTest { /* changed checks */ }');self.blocked('TEST_SOURCE_DIGEST_MISMATCH')
    def test_changed_support_tool_invalidates_old_reports(self):
        self.write('tools/demo_oa_result.py',b'print("changed helper")');self.blocked('SUPPORT_DIGEST_MISMATCH')
    def test_changed_build_configuration_invalidates_old_reports(self):
        self.write('backend/pom.xml',b'<project>changed dependencies</project>');self.blocked('SUPPORT_DIGEST_MISMATCH')
    def test_failed_or_skipped_tests_cannot_pass(self):
        for attr in ('failures','errors','skipped'):
            path='backend/target/surefire-reports/TEST-example.SuiteTest.xml';raw=f'<testsuite name="example.SuiteTest" tests="1" {attr}="1"/>'.encode();self.write(path,raw);self.manifest['reports'][path]=digest(raw);self.blocked('TESTS_NOT_GREEN')
    def test_missing_suite_is_not_zero_success(self):
        path='backend/target/failsafe-reports/TEST-example.EngineMySqlCheck.xml';(self.root/path).unlink();self.blocked('TEST_REPORTS_INCOMPLETE')
    def test_targeted_suite_does_not_masquerade_as_full_run(self):
        self.manifest['fullSuite']=False;self.blocked('FULL_SUITE_NOT_ATTESTED')
    def test_local_environment_is_not_target_server(self):
        record=self.manifest['server'];raw=json.dumps(dict(environment='LOCAL',checksPassed=True,artifactSha256=self.manifest['artifact']['sha256'],sourceSha256=self.manifest['sourceSha256'])).encode();self.write(record['path'],raw);record['sha256']=digest(raw);self.blocked('SERVER_EVIDENCE_INVALID')
    def test_absent_end_to_end_or_owner_review_is_not_complete(self):
        self.manifest['endToEnd']['verified']=False;self.manifest['ownerHandoff']['verified']=False;self.blocked('ENDTOEND_EVIDENCE_INVALID');self.blocked('OWNERHANDOFF_EVIDENCE_INVALID')
    def test_paths_cannot_escape_repository(self):
        self.manifest['artifact']['path']='../outside.jar';self.blocked('ARTIFACT_DIGEST_MISMATCH')

if __name__=='__main__': unittest.main()
