"""Compare a client OpenAPI export with Git; does not test backend behavior."""
import json
import sys
import hashlib
from jsonschema import Draft202012Validator
from referencing import Registry, Resource
from openapi_spec_validator import validate_spec
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parent
local = yaml.safe_load((ROOT / 'openapi.yaml').read_text(encoding='utf-8'))
remote = json.loads((ROOT / 'apifox-export.json').read_text(encoding='utf-8-sig'))
METHODS = {'get', 'post', 'put', 'patch', 'delete', 'head', 'options', 'trace'}

def operations(doc):
    return {(path, method): op for path, item in doc.get('paths', {}).items()
            for method, op in item.items() if method in METHODS}

def normalize(value, doc, stack=()):
    if isinstance(value, list):
        return [normalize(v, doc, stack) for v in value]
    if not isinstance(value, dict):
        return value
    ref = value.get('$ref', '')
    if ref.startswith('#/') and ref not in stack:
        target = doc
        for key in ref[2:].split('/'):
            target = target[key.replace('~1', '/').replace('~0', '~')]
        return normalize(target, doc, stack + (ref,))
    result = {k: normalize(v, doc, stack) for k, v in value.items()
              if not k.startswith('x-') and k not in
              {'description', 'summary', 'tags', 'deprecated'}}
    if result.get('required') is False:
        result.pop('required')
    if result.get('headers') == {}:
        result.pop('headers')
    if isinstance(result.get('parameters'), list):
        result['parameters'].sort(key=lambda v: (v.get('in', ''), v.get('name', '')))
    if isinstance(result.get('examples'), dict):
        result['exampleValues'] = [v['value'] for v in result.pop('examples').values()
                                   if 'value' in v]
    if 'example' in result:
        example = result.pop('example')
        if example != '':
            result['exampleValues'] = [example]
    return result

diffs = []
def compare(a, b, path=''):
    if type(a) is not type(b):
        diffs.append({'path': path, 'kind': 'type', 'git': a, 'export': b})
    elif isinstance(a, dict):
        for key in sorted(a.keys() | b.keys()):
            if key not in a or key not in b:
                diffs.append({'path': path + '/' + key, 'kind': 'missing-or-extra',
                              'git': a.get(key), 'export': b.get(key)})
            else:
                compare(a[key], b[key], path + '/' + key)
    elif a != b:
        diffs.append({'path': path, 'kind': 'value', 'git': a, 'export': b})

compare(normalize(local, local), normalize(remote, remote))
a, b = operations(local), operations(remote)
validate_spec(remote)
resource = {'$schema': 'https://json-schema.org/draft/2020-12/schema', **remote}
registry = Registry().with_resource('urn:apifox:export', Resource.from_contents(resource))
example_checks = []
for name, schema in remote['components']['schemas'].items():
    for example in schema.get('examples', []):
        errors = list(Draft202012Validator(
            {'$ref': 'urn:apifox:export#/components/schemas/' + name},
            registry=registry).iter_errors(example))
        example_checks.append({'schema': name, 'valid': not errors,
                               'errors': [e.message for e in errors]})
report = {
    'exportSha256': hashlib.sha256((ROOT / 'apifox-export.json').read_bytes()).hexdigest(),
    'openapiStructureValid': True,
    'modelExamplesChecked': len(example_checks),
    'invalidModelExamples': [v for v in example_checks if not v['valid']],
    'exportOpenapi': remote['openapi'], 'exportVersion': remote['info']['version'],
    'paths': len(remote['paths']), 'operations': len(b),
    'schemas': len(remote['components']['schemas']),
    'webhooks': list(remote.get('webhooks', {})),
    'extraOperations': [list(k) for k in sorted(b.keys() - a.keys())],
    'missingOperations': [list(k) for k in sorted(a.keys() - b.keys())],
    'operationIdMismatches': [list(k) for k in sorted(a.keys() & b.keys())
                              if a[k].get('operationId') != b[k].get('operationId')],
    'securityMismatches': [list(k) for k in sorted(a.keys() & b.keys())
                           if a[k].get('security', local.get('security')) !=
                           b[k].get('security', remote.get('security'))],
    'responseCodeMismatches': [list(k) for k in sorted(a.keys() & b.keys())
                               if a[k]['responses'].keys() != b[k]['responses'].keys()],
    'differenceEntryCount': len(diffs),
    'componentDifferencePaths': [v['path'] for v in diffs if v['path'].startswith('/components/')],
    'webhookDifferencePaths': [v['path'] for v in diffs if v['path'].startswith('/webhooks/')],
    'appointmentFields': {k: remote['components']['schemas']['Appointment']['properties'][k]
                          for k in ['companyId', 'departmentId', 'systemIds']},
    'errorContentTypes': {k: list(v['content']) for k,v in remote['components']['responses'].items()},
    'uploadSchema': remote['paths']['/api/v1/admin/import-files']['post']['requestBody']['content']['multipart/form-data']['schema'],
}
(ROOT / 'APIFOX-EXPORT-DIFF.json').write_text(
    json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({k: v for k, v in report.items() if k != 'differences'},
                 ensure_ascii=False, indent=2))
print('Difference entries (repeated referenced schemas included):', len(diffs))
for row in diffs:
    path = row['path']
    if '/content/application/json' in path or '/content/application/problem+json' in path:
        continue
    if '/security' in path or '/Appointment' in path:
        continue
    print(json.dumps(row, ensure_ascii=False)[:500])
