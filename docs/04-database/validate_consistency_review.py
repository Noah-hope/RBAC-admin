"""跨阶段设计回归：Schema反例、有限CHECK推演、精确入口与映射检查。

不启动数据库，不发送HTTP，不模拟业务服务；人工事务评审另见CONSISTENCY-REVIEW.md。
依赖：API验证工具版本及sqlglot见VALIDATION.md。
"""
from pathlib import Path
import argparse
import copy
import hashlib
import json
import re
import subprocess

import yaml
import sqlglot
from jsonschema import Draft202012Validator, FormatChecker
from validate_design import evaluate

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent


def historical(revision, path):
    return subprocess.check_output(['git', 'show', f'{revision}:{path}'], cwd=REPO).decode('utf-8')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--baseline', default='e3fa818')
    args = parser.parse_args()
    api_path = REPO / 'docs/03-api/openapi.yaml'
    current = yaml.safe_load(api_path.read_text(encoding='utf-8'))
    before = yaml.safe_load(historical(args.baseline, 'docs/03-api/openapi.yaml'))
    cases = []

    def schema_ok(doc, name, value):
        root = {'$id': 'urn:rbac:review', **doc, '$ref': '#/components/schemas/' + name}
        return not list(Draft202012Validator(root, format_checker=FormatChecker()).iter_errors(value))

    def case(label, name, value, expected_before, expected_current):
        actual = (schema_ok(before, name, value), schema_ok(current, name, value))
        assert actual == (expected_before, expected_current), (label, actual)
        cases.append({'id': label, 'kind': 'JSON_SCHEMA', 'baselineAccepted': actual[0], 'currentAccepted': actual[1]})

    line = copy.deepcopy(current['components']['schemas']['GrantLine']['examples'][0])
    line['employmentIds'] = [f'employment_{i}' for i in range(101)]
    case('C01_all_101_employments', 'GrantLine', line, False, True)
    line['employmentIds'] = ['employment_1'] * 2
    case('C02_duplicate_evidence', 'GrantLine', line, False, False)
    line['employmentIds'] = []
    case('C03_empty_evidence', 'GrantLine', line, False, False)
    result = copy.deepcopy(before['components']['schemas']['SourceCloseResult']['examples'][0])
    case('C04_ambiguous_close_counts', 'SourceCloseResult', result, True, False)
    result.update(visibility='COMPLETE', hasUndisclosedImpact=False)
    case('C05_complete_close_result', 'SourceCloseResult', result, False, True)
    result.update(visibility='SCOPED', hasUndisclosedImpact=True)
    case('C06_zero_visible_with_hidden_impact', 'SourceCloseResult', result, False, True)
    result['visibility'] = 'COMPLETE'
    case('C07_false_completeness_claim', 'SourceCloseResult', result, False, False)
    result.update(visibility='SCOPED', hasUndisclosedImpact=True, hiddenRequestCount=1)
    case('C08_undocumented_hidden_counter', 'SourceCloseResult', result, False, False)

    old_model = json.loads(historical(args.baseline, 'docs/04-database/schema-model.json'))
    model = json.loads((ROOT / 'schema-model.json').read_text(encoding='utf-8'))
    def idem_check(metadata, row):
        table = next(t for t in metadata if t['name'] == 'http_idempotency')
        return evaluate(sqlglot.parse_one(table['checks'][0], read='mysql'), row) is not False
    completed = dict(state='COMPLETED', http_status=204, safe_response={},
                     lease_token=None, lease_until=None, authorization_basis=None)
    for label, row, expected in [
        ('C09_deleted_draft_without_authorization_facts', completed, (True, False)),
        ('C10_completed_with_authorization_facts', completed | {'authorization_basis': {'operationId': 'deleteChangeDraft', 'policy': 'SCOPED', 'targets': [{'companyId': 'company_1', 'departmentId': 'dept_1', 'systemId': 'system_1'}]}}, (True, True)),
        ('C11_invalid_authorization_json_shape', completed | {'authorization_basis': []}, (True, False)),
        ('C12_reservation_before_result', completed | dict(state='IN_PROGRESS', http_status=None, safe_response=None, lease_token='fence', lease_until='future'), (True, True)),
    ]:
        actual = (idem_check(old_model, row), idem_check(model, row))
        assert actual == expected, (label, actual)
        cases.append({'id': label, 'kind': 'FINITE_CHECK_EXPRESSION_ONLY', 'baselineAccepted': actual[0], 'currentAccepted': actual[1]})

    no_key = set()
    operations = {}
    for path, item in current['paths'].items():
        for method, op in item.items():
            if method not in ('get', 'post', 'put', 'patch', 'delete'):
                continue
            operations[op['operationId']] = op
            if method != 'get' and not any(p.get('$ref', '').endswith('/Idempotency') for p in op.get('parameters', [])):
                no_key.add(op['operationId'])
    assert no_key == {'login', 'logout', 'changeOwnPassword', 'systemStatePreview', 'catalogPreview',
                      'grantBaselines', 'changePreview', 'sourceBaselines', 'sourceClosePreview',
                      'employeeStatePreview', 'scopeCreatePreview', 'scopeEditPreview',
                      'authorizationQuery', 'permissionCheck', 'receiveOaResult'}, no_key
    assert operations['receiveOaResult']['security'] == [{'OaJwt': []}]
    schemas = current['components']['schemas']
    assert schemas['RoleGrant']['properties']['items']['maxItems'] == 100
    assert 'maxItems' not in schemas['GrantBaseline']['properties']['employmentIds']
    mapping = (ROOT / 'FIELD-MAPPING.md').read_text(encoding='utf-8')
    tokens = set(re.findall(r'\b[A-Z][A-Za-z]+\b', mapping))
    non_models = {'API', 'BINARY', 'DATETIME', 'EMPLOYEE', 'GLOBAL', 'JSON', 'NULL', 'SCOPE', 'HTTP'}
    assert tokens - non_models <= schemas.keys(), sorted(tokens - non_models - schemas.keys())
    override = json.loads((REPO / 'docs/03-api/APIFOX-EXPORT-OVERRIDE.json').read_text(encoding='utf-8-sig'))
    assert override['info'] == {'title': current['info']['title'], 'version': current['info']['version']}
    for path, item in override['paths'].items():
        assert item == current['paths'][path], path
    assert len(operations) == 67
    assert len(current['webhooks']) == 1
    assert {uc for op in operations.values() for uc in op.get('x-use-cases', [])} == {f'UC-{i:02}' for i in range(1, 12)}
    trace = json.loads((ROOT / 'traceability.json').read_text(encoding='utf-8'))
    for row in trace:
        op = operations.get(row['operationId'])
        expected_key = bool(op and any(p.get('$ref', '').endswith('/Idempotency') for p in op.get('parameters', [])))
        assert ('http_idempotency' in row['tables']) == expected_key, row['operationId']
        assert ('I-16' in row['invariants']) == expected_key, row['operationId']
        if row['method'] == 'GET':
            assert not set(row['transactions']) & {'T0', 'T4', 'T7', 'T8', 'T10'}, row['operationId']
    report = {
        'scope': 'DESIGN_SCHEMA_AND_STATIC_REVIEW_ONLY', 'baseline': args.baseline,
        'inputApiVersion': before['info']['version'], 'apiVersion': current['info']['version'],
        'openapiSha256': hashlib.sha256(api_path.read_bytes()).hexdigest(),
        'cases': cases, 'schemaAndCheckCases': len(cases), 'inboundOperations': len(operations),
        'idempotencyHeaderExceptions': sorted(no_key), 'fieldMappingModelNames': 'PASS',
        'exportOverrideMatchesGit': True, 'remoteExportRevalidated': False,
        'readWriteAndIdempotencyTracing': 'PASS',
        'businessOrConcurrencyTestsRun': False, 'databaseStarted': False, 'sqlExecuted': False,
    }
    (ROOT / 'consistency-validation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
