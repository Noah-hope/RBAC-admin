"""Design-time validation only; does not test backend business implementation.

Requires PyYAML, jsonschema, openapi-spec-validator (versions in VALIDATION.md).
Run: python docs/03-api/validate_contract.py
"""
from pathlib import Path
import copy
import json
import re
import sys
import itertools
import subprocess

import yaml
from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource
from openapi_spec_validator import validate_spec

ROOT = Path(__file__).resolve().parent
document = yaml.safe_load((ROOT / 'openapi.yaml').read_text(encoding='utf-8'))
validate_spec(document)

# One local JSON Schema resource permits OpenAPI component fragment references.
schema_document = {'$schema': 'https://json-schema.org/draft/2020-12/schema', **document}
registry = Registry().with_resource('urn:rbac:contract', Resource.from_contents(schema_document))


def validator(schema):
    return Draft202012Validator(
        {'$ref': 'urn:rbac:contract', '$defs': {}} if schema is None else
        {'$id': 'urn:rbac:example', **schema},
        registry=registry,
        format_checker=FormatChecker(),
    )


def rooted(schema):
    if isinstance(schema, dict):
        return {k: ('urn:rbac:contract' + v if k == '$ref' and v.startswith('#') else rooted(v))
                for k, v in schema.items()}
    if isinstance(schema, list):
        return [rooted(v) for v in schema]
    return schema


operations = []
for path, item in document['paths'].items():
    for method, operation in item.items():
        if method not in {'get', 'post', 'put', 'patch', 'delete'}:
            continue
        operations.append((path, method, operation))
ids = [op['operationId'] for _, _, op in operations]
assert len(ids) == len(set(ids)), 'Duplicate operationId'
webhook_ids = [op['operationId'] for item in document.get('webhooks', {}).values()
               for method, op in item.items() if method in {'post', 'get', 'put', 'patch', 'delete'}]
assert len(ids + webhook_ids) == len(set(ids + webhook_ids)), 'Duplicate webhook operationId'
covered = {uc for _, _, op in operations for uc in op['x-use-cases']}
assert covered == {f'UC-{n:02}' for n in range(1, 12)}, covered


def resolve(ref):
    result = document
    for key in ref.removeprefix('#/').split('/'):
        result = result[key.replace('~1', '/').replace('~0', '~')]
    return result


for path, method, operation in operations:
    parameters = [resolve(p['$ref']) if '$ref' in p else p for p in operation.get('parameters', [])]
    actual_paths = {p['name'] for p in parameters if p['in'] == 'path'}
    assert actual_paths == set(re.findall(r'\{([^}]+)\}', path)), path
    assert all(p['required'] for p in parameters if p['in'] == 'path'), path
    assert len({(p['in'], p['name']) for p in parameters}) == len(parameters), path
    access = operation['x-access']
    security = operation.get('security', document['security'])
    if access == '业务系统':
        assert security == [{'BusinessJwt': []}]
    elif access == 'OA':
        assert security == [{'OaJwt': []}]
    elif access != '公开':
        assert {'AdminJwt': []} in security
    if access == '范围':
        assert {'X-Admin-Appointment-Id', 'X-Admin-Scope-Version', 'X-Admin-Appointment-Version'} <= {p['name'] for p in parameters}
    if operation['operationId'].startswith('list') or operation['operationId'] == 'myAppointments':
        assert {'page', 'pageSize'} <= {p['name'] for p in parameters}, path
    assert operation['operationId'] in (ROOT / 'ENDPOINTS.md').read_text(encoding='utf-8')
    assert operation['operationId'] in (ROOT / 'TRACEABILITY.md').read_text(encoding='utf-8')

# Resolve every local ref, including non-schema references.
refs = []


def walk(value):
    if isinstance(value, dict):
        if '$ref' in value:
            assert value['$ref'].startswith('#/'), 'Unexpected external ref'
            resolve(value['$ref'])
            refs.append(value['$ref'])
        for child in value.values():
            walk(child)
    elif isinstance(value, list):
        for child in value:
            walk(child)


walk(document)
examples = 0
for name, schema in document['components']['schemas'].items():
    for example in schema.get('examples', []):
        validator(rooted(schema)).validate(example)
        examples += 1

# Examples explicitly describe expected acceptance/rejection, not implemented business tests.
scenarios = json.loads((ROOT / 'scenarios.json').read_text(encoding='utf-8'))
review_scenarios = json.loads((ROOT / 'review_cases.json').read_text(encoding='utf-8'))
for case in scenarios + review_scenarios:
    value = copy.deepcopy(case['value'])
    if 'expandItemsTo' in case:
        value['items'] = value['items'] * case['expandItemsTo']
    errors = list(validator(rooted({'$ref': '#/components/schemas/' + case['schema']})).iter_errors(value))
    assert bool(errors) != case['valid'], (case['name'], [e.message for e in errors])

baseline_accepted = None
if '--baseline' in sys.argv:
    raw = subprocess.run(['git', 'show', '269d269:docs/03-api/openapi.yaml'],
                         cwd=ROOT, check=True, capture_output=True).stdout
    baseline = yaml.safe_load(raw.decode('utf-8'))
    baseline_registry = Registry().with_resource('urn:rbac:baseline', Resource.from_contents(
        {'$schema': 'https://json-schema.org/draft/2020-12/schema', **baseline}))
    baseline_cases = [c for c in review_scenarios if 'baselineValue' in c]
    baseline_accepted = 0
    for case in baseline_cases:
        check = Draft202012Validator(
            {'$ref': 'urn:rbac:baseline#/components/schemas/' + case['schema']}, registry=baseline_registry)
        assert check.is_valid(case['baselineValue']), case['name']
        baseline_accepted += 1

# Prove object-union branches are disjoint using contradictory required field domains.
# This is schema analysis, not a simulated authorization/approval implementation.
def object_leaves(schema):
    if '$ref' in schema:
        return object_leaves(resolve(schema['$ref']))
    if 'oneOf' in schema:
        return [leaf for branch in schema['oneOf'] for leaf in object_leaves(branch)]
    return [schema]


def primitive_kind(value):
    if isinstance(value, bool):
        return 'boolean'
    if value is None:
        return 'null'
    if isinstance(value, str):
        return 'string'
    return 'number'


def domain(schema):
    if '$ref' in schema:
        return domain(resolve(schema['$ref']))
    if 'enum' in schema:
        values = {(primitive_kind(v), v) for v in schema['enum']}
        return {k for k, _ in values}, values
    types = schema.get('type')
    if types:
        types = types if isinstance(types, list) else [types]
        return {'number' if t == 'integer' else t for t in types}, None
    return {'null', 'string', 'number', 'boolean', 'object', 'array'}, None


def disjoint(left, right):
    for key in set(left.get('required', [])) & set(right.get('required', [])):
        lt, lv = domain(left['properties'][key])
        rt, rv = domain(right['properties'][key])
        if not lt & rt or (lv is not None and rv is not None and not lv & rv):
            return True
    return False


branch_groups, branch_pairs = 0, 0


def check_branches(value):
    global branch_groups, branch_pairs
    if isinstance(value, dict):
        if 'oneOf' in value:
            leaves = object_leaves(value)
            if all('properties' in leaf for leaf in leaves):
                branch_groups += 1
                for left, right in itertools.combinations(leaves, 2):
                    assert disjoint(left, right), 'Object union branches are not proven disjoint'
                    branch_pairs += 1
        for key, child in value.items():
            if key not in {'example', 'examples'}:
                check_branches(child)
    elif isinstance(value, list):
        for child in value:
            check_branches(child)


check_branches(document['components']['schemas'])

for kind, group in document['components']['responses'].items():
    for content in group.get('content', {}).values():
        if 'example' in content:
            validator(rooted(content['schema'])).validate(content['example'])

media_examples = 0
for _, _, op in operations + [('', 'post', document['webhooks']['oaSubmission']['post'])]:
    content_groups = [op.get('requestBody', {}).get('content', {})]
    for raw in op['responses'].values():
        response = resolve(raw['$ref']) if '$ref' in raw else raw
        content_groups.append(response.get('content', {}))
    for group in content_groups:
        for media in group.values():
            if 'example' in media:
                validator(rooted(media['schema'])).validate(media['example'])
                media_examples += 1

# Never introduce prohibited API surfaces or sensitive field storage in exported schemas.
forbidden = {'dataScope', 'ownerId', 'recordId', 'parentDepartmentId'}
for name, schema in document['components']['schemas'].items():
    assert not forbidden.intersection(schema.get('properties', {})), name


def check_nested_schema(value):
    if isinstance(value, dict):
        assert not forbidden.intersection(value.get('properties', {})), value.get('properties')
        for key, child in value.items():
            if key not in {'examples', 'example'}:
                check_nested_schema(child)
    elif isinstance(value, list):
        for child in value:
            check_nested_schema(child)


check_nested_schema(document['components']['schemas'])
delivery_retry = document['paths']['/api/v1/admin/change-requests/{requestId}/delivery-retries']['post']
assert delivery_retry['x-required-state'] == {'state': 'PENDING', 'deliveryStatus': 'SEND_FAILED', 'approvalResult': 'NONE'}
for direct_path in ['/api/v1/admin/source-closures', '/api/v1/admin/systems/{systemId}/catalog-changes']:
    assert '200' in document['paths'][direct_path]['post']['responses']
    assert '201' not in document['paths'][direct_path]['post']['responses']
for path, method, op in operations:
    assert not re.search(r'/admin/.*/(approve|force-execution|execute|withdraw|cancel)$', path), path

print(json.dumps({'operations': len(operations), 'paths': len(document['paths']),
                  'webhooks': len(document.get('webhooks', {})),
                  'schemas': len(document['components']['schemas']), 'localRefs': len(refs),
                  'modelExamples': examples, 'schemaScenarios': len(scenarios),
                  'reviewSchemaScenarios': len(review_scenarios),
                  'objectUnionGroups': branch_groups, 'disjointBranchPairs': branch_pairs,
                  'baselineInvalidExamplesAccepted': baseline_accepted,
                  'mediaExamples': media_examples,
                  'useCases': sorted(covered), 'result': 'PASS'}, ensure_ascii=False))
