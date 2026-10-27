"""仅刷新独立数据库说明书的完整字段附录，保留人工编写的设计正文。"""
from pathlib import Path
import hashlib
import html
import json
import re
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
MARKER = '// BEGIN GENERATED DATABASE DICTIONARY'
GROUPS = [
    ('组织与后台身份', 'company department position employee employment admin_account admin_session management_scope scope_system admin_appointment'),
    ('目录、角色与独立来源', 'business_system directory_node permission business_role role_permission role_configuration_history authorization_source grant_baseline role_grant grant_employment_evidence'),
    ('固定申请、OA与执行', 'change_request change_target role_change_claim relation_change_claim service_identity oa_outbox oa_link oa_callback_event oa_final_result execution_success execution_attempt audit_event audit_subject'),
    ('导入、预览与可靠性', 'import_file import_batch import_plan_row import_issue organization_mapping employee_source_identity impact_preview preview_item mutation_guard http_idempotency'),
]


def digest(file):
    return hashlib.sha256(file.read_bytes()).hexdigest()


def esc(value):
    return str(value).replace('|', r'\|').replace('\n', ' ')


def generate():
    model_path = ROOT / 'docs/04-database/schema-model.json'
    tables = json.loads(model_path.read_text(encoding='utf-8'))
    by_name = {t['name']: t for t in tables}
    names = {}
    for cell in ET.parse(ROOT / 'docs/04-database/database-erd.drawio').iter('mxCell'):
        text = re.sub(r'<[^>]*>', '\n', html.unescape(cell.get('value', ''))).strip()
        if cell.get('id') in by_name and text:
            names[cell.get('id')] = text.splitlines()[0].strip()
    names.update({'business_system': '业务系统表', 'execution_attempt': '执行尝试表'})
    assert set(names) == set(by_name), set(by_name) - set(names)
    grouped = [name for _, group in GROUPS for name in group.split()]
    assert len(grouped) == len(set(grouped)) and set(grouped) == set(by_name)

    # 逐表逐字段检查当前V1迁移，避免把过期元数据印成设计书。
    ddl = (ROOT / 'backend/src/main/resources/db/migration/V1__create_tables_and_constraints.sql').read_text(encoding='utf-8')
    sql_tables = {}
    sql_bodies = {}
    for match in re.finditer(r'CREATE TABLE `([^`]+)` \((.*?)\) ENGINE=', ddl, re.S):
        sql_tables[match[1]] = re.findall(r'^  `([^`]+)` ', match[2], re.M)
        sql_bodies[match[1]] = match[2]
    assert set(sql_tables) == set(by_name)
    for t in tables:
        assert sql_tables[t['name']] == [c['name'] for c in t['columns']], t['name']
        for c in t['columns']:
            line = next(v for v in sql_bodies[t['name']].splitlines() if v.startswith(f"  `{c['name']}` "))
            assert line.startswith(f"  `{c['name']}` {c['type']} "), (t['name'], c['name'], 'type')
            null_text = ' NULL' if c['nullable'] else ' NOT NULL'
            if not c['generated']:
                assert line.startswith(f"  `{c['name']}` {c['type']}" + null_text), (t['name'], c['name'], 'nullability')
            if c['default'] is not None:
                assert 'DEFAULT ' + str(c['default']) + ' ' in line, (t['name'], c['name'], 'default')
            if c['generated']:
                assert 'AS (' + c['generated'] + ')' in line, (t['name'], c['name'], 'generated')

    lines = [MARKER, '', '[appendix]', '== 物理表与字段定义', '',
        '本附录按前文四个数据主题列出字段。PK表示主键组成列；“可空”为该列是否允许SQL NULL。类型列保留类型和长度，字符排序规则采用第一章所述约定；UUID列使用ASCII字符集及ascii_bin排序规则。缺少默认值的必填列由写入方提供，生成列由数据库计算。每张表后列出其实际唯一键和外键，不把跨表业务检查误写成数据库已经保证的约束。', '']
    for title, group in GROUPS:
        lines += ['[.dictionary-group]', '=== ' + title, '']
        for name in group.split():
            t = by_name[name]
            lines += ['[.table-definition]', '==== ' + names[name], '', f"`{name}`：{t['meaning']}。", '',
                      '[cols="27,16,7,50",options="header",role="dictionary-table"]', '|===',
                      '|字段 |类型与长度 |可空 |含义与取值']
            for c in t['columns']:
                kind = re.sub(r' CHARACTER SET \w+', '', c['type'])
                kind = re.sub(r' COLLATE \w+', '', kind)
                desc = c['meaning']
                if c['default'] is not None:
                    desc += '；默认值：`' + str(c['default']) + '`'
                if c['generated'] is not None:
                    desc += '；生成表达式：`' + c['generated'] + '`'
                field = '`' + c['name'] + '`' + ('（PK）' if c['name'] in t['pk'] else '')
                lines.append('|' + ' |'.join(map(esc, [field, kind, '是' if c['nullable'] else '否', desc])))
            lines += ['|===', '']
            details = ['主键：`' + ', '.join(t['pk']) + '`。']
            if t['unique']:
                details.append('唯一组合：' + '；'.join('`' + ', '.join(u) + '`' for u in t['unique']) + '。')
            if t['fks']:
                details.append('外键：' + '；'.join('`' + ', '.join(f['columns']) + '` → `' + f['table'] + '(' + ', '.join(f['references']) + ')`' for f in t['fks']) + '。')
            lines += [' '.join(details), '']
    document = HERE / '数据库设计说明书.adoc'
    body = document.read_text(encoding='utf-8').split(MARKER)[0]
    document.write_text(body + '\n'.join(lines).rstrip() + '\n', encoding='utf-8', newline='\n')
    source_files = [ROOT / ('docs/04-database/' + n) for n in [
        'schema-model.json', 'schema.sql', 'immutability.sql', 'database-erd.drawio',
        'LOGICAL-MODEL.md', 'DATA-DICTIONARY.md', 'INVARIANTS.md', 'TRANSACTIONS.md', 'INDEXES.md']]
    source_files += sorted((ROOT / 'backend/src/main/resources/db/migration').glob('*.sql'))
    source_files += [ROOT / ('backend/src/main/java/com/adgn/backend/core/transaction/' + n)
                     for n in ['CommandBoundary.java', 'ReadBoundary.java', 'WriteContext.java']]
    manifest = json.loads((HERE / 'images/manifest.json').read_text(encoding='utf-8'))
    figures = [v for v in manifest['images'] if v['id'].startswith('er-')]
    assert len(figures) == 4
    for fig in figures:
        source = ROOT / fig['source']
        # 既有官方导出器按UTF-8文字哈希记录原生图源。
        source_hash = hashlib.sha256(source.read_text(encoding='utf-8').encode('utf-8')).hexdigest()
        assert source_hash == fig['sourceSha256'], 'ER source changed; re-export before publication'
        assert digest(HERE / 'images' / fig['file']) == fig['pngSha256']
    record = {'date': '2026-10-27', 'tables': len(tables), 'columns': sum(len(t['columns']) for t in tables),
              'migrationColumnsMatch': True,
              'sources': [{'path': p.relative_to(ROOT).as_posix(), 'sha256': digest(p)} for p in source_files],
              'figures': figures}
    (HERE / 'database-sources.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'tables': record['tables'], 'columns': record['columns'], 'migrationColumnsMatch': True}, ensure_ascii=False))


if __name__ == '__main__':
    generate()
