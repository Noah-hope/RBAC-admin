"""Read-only evidence consistency gate, not deployment or independent server attestation.

Exit 0 only when supplied evidence is complete and consistent; exit 2 otherwise.
Never contacts services, signs acceptance, creates releases, or changes task status.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

def sha(raw): return hashlib.sha256(raw).hexdigest()
def local(root,name):
    path=(root/name).resolve();path.relative_to(root);return path
def load(root,name): return json.loads(local(root,name).read_text(encoding='utf-8'))
def source_digest(root,relative='backend/src/main'):
    base=root/relative;raw=bytearray()
    for file in sorted(base.rglob('*')):
        if file.is_file():raw.extend(file.relative_to(base).as_posix().encode()+b'\0'+local(root,file.relative_to(root)).read_bytes())
    return sha(raw)

def inspect(root,evidence):
    blockers=[]
    def block(code,target): blockers.append(dict(code=code,target=target))
    tasks=load(root,'docs/05-detailed-design/issues.json');by_id={t['id']:t for t in tasks}
    if set(by_id)!={f'BE-{n:02}' for n in range(1,36)}:block('TASK_INVENTORY_INCOMPLETE','BE-01..35')
    for task in tasks:
        progress=task.get('implementationProgress',{});accepted=task.get('status')=='OWNER_ACCEPTED'
        if not (progress.get('implemented') is True or accepted):block('TASK_NOT_IMPLEMENTED',task['id'])
        if not accepted:block('OWNER_ACCEPTANCE_PENDING',task['id'])
        delivery=progress.get('mergedToMain',task.get('ownerAcceptance',{}).get('mergedToMain',False))
        if not delivery:block('TASK_NOT_DELIVERED',task['id'])
    operations=[o for o in load(root,'docs/05-detailed-design/operation-map.json') if o['direction']=='INBOUND']
    if len(operations)!=67 or len({o['operationId'] for o in operations})!=67:block('INBOUND_COVERAGE_INCOMPLETE','67 inbound operations')
    for operation in operations:
        task=by_id.get(operation['issue'],{});progress=task.get('implementationProgress',{})
        if not(progress.get('implemented') is True or task.get('status')=='OWNER_ACCEPTED'):block('OPERATION_NOT_IMPLEMENTED',operation['operationId'])
    cases=[c for c in load(root,'docs/05-detailed-design/acceptance.json') if c.get('delivery')!='FRONTEND_DEFERRED']
    if {c['id'] for c in cases}!={f'AT-{n:02}' for n in range(1,42) if n!=40}:block('ACCEPTANCE_INVENTORY_INCOMPLETE','backend AT inventory')
    for case in cases:
        if case.get('status')!='VERIFIED':block('ACCEPTANCE_NOT_VERIFIED',case['id'])
    if evidence.get('schemaVersion')!=1:block('EVIDENCE_SCHEMA_INVALID','schemaVersion')
    current_source=source_digest(root)
    if current_source!=evidence.get('sourceSha256'):block('SOURCE_DIGEST_MISMATCH','backend/src/main')
    if source_digest(root,'backend/src/test')!=evidence.get('testSourceSha256'):block('TEST_SOURCE_DIGEST_MISMATCH','backend/src/test')
    artifact=evidence.get('artifact',{})
    try: artifact_valid=sha(local(root,artifact['path']).read_bytes())==artifact['sha256']
    except (KeyError,ValueError,OSError): artifact_valid=False
    if not artifact_valid:block('ARTIFACT_DIGEST_MISMATCH','candidate artifact')
    if evidence.get('fullSuite') is not True:block('FULL_SUITE_NOT_ATTESTED','fullSuite')
    actual={p.relative_to(root).as_posix() for family in ('surefire','failsafe') for p in (root/f'backend/target/{family}-reports').glob('TEST-*.xml')}
    reports=evidence.get('reports',{})
    if actual!=set(reports) or not actual:block('TEST_REPORTS_INCOMPLETE','test report files')
    suites=set();totals=dict(tests=0,failures=0,errors=0,skipped=0)
    for path,expected in reports.items():
        try:
            raw=local(root,path).read_bytes()
            if sha(raw)!=expected:block('TEST_REPORT_DIGEST_MISMATCH',path)
            suite=ET.fromstring(raw);suites.add(suite.attrib['name'])
            for field in totals:totals[field]+=int(suite.get(field,'0'))
        except (KeyError,ValueError,OSError,ET.ParseError):block('TEST_REPORTS_INCOMPLETE',path)
    expected_suites=set()
    for file in (root/'backend/src/test/java').rglob('*.java'):
        if file.name.endswith(('Test.java','MySqlCheck.java')):
            match=re.search(r'^\s*package\s+([\w.]+)\s*;',file.read_text(encoding='utf-8'),re.M)
            if match:expected_suites.add(match.group(1)+'.'+file.stem)
    if not expected_suites or not expected_suites.issubset(suites):block('TEST_REPORTS_INCOMPLETE','all project Java test suites')
    if totals['tests']<1 or any(totals[f] for f in ('failures','errors','skipped')):block('TESTS_NOT_GREEN','test results')
    for field in ('server','endToEnd','ownerHandoff'):
        record=evidence.get(field,{})
        try:
            raw=local(root,record['path']).read_bytes();data=json.loads(raw)
            valid=(record.get('verified') is True and sha(raw)==record['sha256'] and data.get('environment')=='TARGET_SERVER'
                and data.get('checksPassed') is True and bool(data.get('reviewedBy'))
                and data.get('artifactSha256')==artifact.get('sha256') and data.get('sourceSha256')==current_source)
        except (KeyError,ValueError,OSError,TypeError):valid=False
        if not valid:block(field.upper()+'_EVIDENCE_INVALID',field)
    return dict(scope='READ_ONLY_EVIDENCE_CONSISTENCY_NOT_SERVER_ATTESTATION',ready=not blockers,blockers=blockers,
        implementedInboundOperations=sum(1 for o in operations if by_id.get(o['issue'],{}).get('implementationProgress',{}).get('implemented') is True or by_id.get(o['issue'],{}).get('status')=='OWNER_ACCEPTED'),
        inboundOperations=len(operations),sourceSha256=current_source,testResults=totals)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);parser.add_argument('--evidence',required=True)
    args=parser.parse_args()
    try:report=inspect(args.root.resolve(),load(args.root.resolve(),args.evidence))
    except (OSError,ValueError,KeyError,TypeError):report=dict(ready=False,blockers=[dict(code='EVIDENCE_INPUT_INVALID',target='input')])
    print(json.dumps(report,ensure_ascii=False,indent=2));return 0 if report['ready'] else 2

if __name__=='__main__':sys.exit(main())
