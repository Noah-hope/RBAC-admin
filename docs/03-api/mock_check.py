"""HTTP shape checks against Prism; no authentication or stateful business verification.

Start Prism on 127.0.0.1:4010, then run this file. Uses synthetic values only.
"""
import copy
import json
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request
import uuid

import yaml
from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parent
doc = yaml.safe_load((ROOT / 'openapi.yaml').read_text(encoding='utf-8'))
resource = {'$schema': 'https://json-schema.org/draft/2020-12/schema', **doc}
registry = Registry().with_resource('urn:rbac:contract', Resource.from_contents(resource))


def resolve(value):
    if '$ref' not in value:
        return value
    result = doc
    for k in value['$ref'].removeprefix('#/').split('/'):
        result = result[k.replace('~1', '/').replace('~0', '~')]
    return result


def sample(schema):
    s = resolve(schema)
    if s.get('examples'):
        return copy.deepcopy(s['examples'][0])
    if 'const' in s:
        return s['const']
    if 'enum' in s:
        return s['enum'][0]
    if 'anyOf' in s:
        return None if any(v.get('type') == 'null' for v in s['anyOf']) else sample(s['anyOf'][0])
    if 'oneOf' in s:
        return sample(s['oneOf'][0])
    if s.get('type') == 'object':
        return {k: sample(s['properties'][k]) for k in s.get('required', [])}
    if s.get('type') == 'array':
        return [sample(s['items']) for _ in range(s.get('minItems', 0))]
    if s.get('type') == 'integer':
        return max(0, s.get('minimum', 0))
    if s.get('type') == 'boolean':
        return False
    if s.get('format') == 'uuid':
        return '6f4aef21-df11-4a6d-866f-0f11a2296d11'
    if s.get('format') == 'date-time':
        return '2026-10-16T08:00:00Z'
    # Non-secret placeholder, used only in the loopback Mock.
    if s.get('writeOnly'):
        return 'MOCK_ONLY_NOT_A_CREDENTIAL'
    if 'pattern' in s and '{64}' in s['pattern']:
        return 'a' * 64
    return 'demo_01'


def rooted(s):
    if isinstance(s, dict):
        return {k: 'urn:rbac:contract' + v if k == '$ref' and v.startswith('#') else rooted(v)
                for k, v in s.items()}
    if isinstance(s, list):
        return [rooted(v) for v in s]
    return s


def call(method, path, headers, body):
    req = urllib.request.Request('http://127.0.0.1:4010' + path, data=body, method=method.upper(), headers=headers)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, response.read()


passed = []
for path, item in doc['paths'].items():
    for method, op in item.items():
        if method not in {'get', 'post', 'put', 'delete', 'patch'}:
            continue
        status = next(k for k in op['responses'] if k.startswith('2'))
        url = re.sub(r'\{[^}]+\}', 'demo_01', path)
        headers = {'Authorization': 'Bearer MOCK_ONLY_NOT_A_TOKEN', 'Prefer': f'code={status}',
                   'X-Request-Id': 'mock-contract-check'}
        query = {}
        for raw in op.get('parameters', []):
            p = resolve(raw)
            if p['in'] == 'header' and p.get('required'):
                headers[p['name']] = str(sample(p['schema']))
            if p['in'] == 'query' and p.get('required'):
                query[p['name']] = sample(p['schema'])
        if query:
            url += '?' + urllib.parse.urlencode(query)
        body = None
        content = op.get('requestBody', {}).get('content', {})
        if 'application/json' in content:
            media = content['application/json']
            value = media.get('example', sample(media['schema']))
            body = json.dumps(value, ensure_ascii=False).encode('utf-8')
            headers['Content-Type'] = 'application/json'
        if 'multipart/form-data' in content:
            boundary = 'rbacMockBoundary'
            body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="demo.csv"\r\n'
                    'Content-Type: text/csv\r\n\r\nemployeeKey,name\r\ndemo.employee,合成员工\r\n'
                    f'--{boundary}--\r\n').encode('utf-8')
            headers['Content-Type'] = f'multipart/form-data; boundary={boundary}'
        actual, data = call(method, url, headers, body)
        assert actual == int(status), (op['operationId'], actual, data[:400])
        response = resolve(op['responses'][status])
        media = response.get('content', {}).get('application/json')
        if media:
            Draft202012Validator(rooted(media['schema']), registry=registry,
                                 format_checker=FormatChecker()).validate(json.loads(data))
        else:
            assert not data, op['operationId']
        passed.append(op['operationId'])

negative = [
    ('missingEmployee', '/api/v1/integration/permission-checks', {'companyId': 'company_demo_01', 'permissionCode': 'employee:edit'}),
    ('forbiddenDataScope', '/api/v1/integration/authorization-queries', {'employeeKey': 'demo.employee', 'companyId': 'company_demo_01', 'dataScope': 'ALL'}),
    ('nonFinalCallback', '/api/v1/oa/final-results', {**sample({'$ref': '#/components/schemas/OaFinalResult'}), 'isFinal': False}),
]
for name, path, body in negative:
    actual, _ = call('post', path, {'Authorization': 'Bearer MOCK_ONLY_NOT_A_TOKEN',
                                  'Content-Type': 'application/json'}, json.dumps(body).encode())
    assert 400 <= actual < 500, (name, actual)
for query in ['page=0', 'pageSize=101']:
    actual, _ = call('get', '/api/v1/admin/roles?' + query,
                     {'Authorization': 'Bearer MOCK_ONLY_NOT_A_TOKEN'}, None)
    assert 400 <= actual < 500, (query, actual)

# Select the documented conflict response; this proves schema availability only.
actual, body = call('post', '/api/v1/oa/final-results',
                    {'Authorization': 'Bearer MOCK_ONLY_NOT_A_TOKEN', 'Content-Type': 'application/json',
                     'Prefer': 'code=409'},
                    json.dumps(sample({'$ref': '#/components/schemas/OaFinalResult'})).encode())
assert actual == 409
Draft202012Validator(rooted({'$ref': '#/components/schemas/Problem'}), registry=registry).validate(json.loads(body))
print(json.dumps({'positiveHttpOperations': len(passed), 'negativeShapeCases': 5,
                  'documentedProblemResponse': 1, 'result': 'PASS'}, ensure_ascii=False))
