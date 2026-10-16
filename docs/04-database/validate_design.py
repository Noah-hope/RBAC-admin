"""静态设计校验。无数据库连接、无SQL执行；结果不能替代引擎/业务测试。"""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import yaml
import sqlglot
from sqlglot import exp

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent.parent


def check(ok, message):
    if not ok:
        raise AssertionError(message)


def nullable_not(x):
    return None if x is None else not x


def evaluate(node, row):
    """仅用于本草案CHECK的有限三值逻辑推演，不模拟MySQL存储引擎。"""
    if isinstance(node, exp.Paren): return evaluate(node.this,row)
    if isinstance(node, exp.Column): return row.get(node.name)
    if isinstance(node, exp.Literal): return node.this if node.is_string else int(node.this)
    if isinstance(node, exp.Null): return None
    if isinstance(node, exp.Boolean): return node.this
    if isinstance(node, exp.JSONType):
        value=evaluate(node.this,row)
        if value is None:return None
        return 'OBJECT' if isinstance(value,dict) else 'ARRAY' if isinstance(value,list) else 'STRING' if isinstance(value,str) else 'BOOLEAN' if isinstance(value,bool) else 'INTEGER'
    if isinstance(node, exp.And):
        a,b=evaluate(node.this,row),evaluate(node.expression,row)
        return False if a is False or b is False else None if a is None or b is None else True
    if isinstance(node, exp.Or):
        a,b=evaluate(node.this,row),evaluate(node.expression,row)
        return True if a is True or b is True else None if a is None or b is None else False
    if isinstance(node, exp.Not): return nullable_not(evaluate(node.this,row))
    if isinstance(node, exp.Is): return evaluate(node.this,row) is evaluate(node.expression,row)
    if isinstance(node, exp.Between):
        a,b,c=(evaluate(n,row) for n in [node.this,node.args['low'],node.args['high']])
        return None if None in (a,b,c) else b<=a<=c
    if isinstance(node, exp.In):
        a=evaluate(node.this,row); values=[evaluate(n,row) for n in node.expressions]
        if a is None:return None
        return True if a in values else None if None in values else False
    compare={exp.EQ:lambda a,b:a==b,exp.NEQ:lambda a,b:a!=b,exp.GT:lambda a,b:a>b,
             exp.GTE:lambda a,b:a>=b,exp.LT:lambda a,b:a<b,exp.LTE:lambda a,b:a<=b}
    if type(node) in compare:
        a,b=evaluate(node.this,row),evaluate(node.expression,row)
        return None if a is None or b is None else compare[type(node)](a,b)
    raise ValueError('Unsupported CHECK expression: '+str(type(node)))


def check_cases(tables):
    tab=tables['change_request']
    expressions=[sqlglot.parse_one(x,read='mysql') for x in tab['checks']]
    base={c['name']:None for c in tab['columns']}
    for c in tab['columns']:
        if c['default'] is not None:
            base[c['name']]=evaluate(sqlglot.parse_one(c['default'],read='mysql'),{})
    base.update(id='fixture',kind='ROLE_GRANT',company_id='fixture',content={},reason='fixture',created_at='time')
    fixed=dict(submitted_version=1,submitted_at='time',submitted_by_account_id='fixture',authority_type='GLOBAL',
               original_authority={},fixed_payload_bytes=b'fixture',content_digest=b'fixture')
    variants=[dict(),dict(state='PENDING',delivery_status='PENDING_SEND',**fixed),
              dict(state='REJECTED',delivery_status='LINKED',approval_result='REJECTED',**fixed),
              dict(state='APPROVED',delivery_status='LINKED',approval_result='APPROVED',execution_status='PENDING',**fixed),
              dict(state='EXECUTION_FAILED',delivery_status='LINKED',approval_result='APPROVED',execution_status='FAILED',failure_code='EXECUTION_STORAGE_FAILURE',next_retry_at='time',**fixed),
              dict(state='EXECUTED',delivery_status='LINKED',approval_result='APPROVED',execution_status='SUCCEEDED',**fixed),
              dict(state='INVALIDATED',delivery_status='SEND_FAILED',execution_status='INVALIDATED',failure_code='SOURCE_EPOCH_CHANGED',**fixed)]
    def accepted(row):
        # CHECK FALSE拒绝；UNKNOWN通过。NOT NULL另由字段约束检查。
        return all(evaluate(e,row) is not False for e in expressions)
    count=0
    for variant in variants:
        row=base|variant
        check(accepted(row),'合法状态CHECK推演被拒绝：'+str(variant));count+=1
    negatives=[base|dict(state='EXECUTED'), base|variants[5]|dict(approval_result='NONE'),
               base|variants[1]|dict(authority_type=None),base|variants[1]|dict(submitted_at=None),
               base|variants[1]|dict(authority_type='SCOPE',scope_id=None,appointment_id=None),
               base|variants[4]|dict(next_retry_at=None),base|variants[6]|dict(failure_code=None),
               base|dict(state='DRAFT',submitted_version=1),base|variants[6]|dict(approval_result='APPROVED'),
               base|variants[1]|dict(submitted_version=9007199254740992),
               base|variants[1]|dict(original_authority='invalid-json-shape'),base|dict(content=[])]
    for row in negatives:
        check(not accepted(row),'非法状态CHECK推演被接受：'+str(row));count+=1
    # INVALIDATED + LINKED + 迟到最终结果保留合法，不能执行。
    row=base|variants[6]|dict(delivery_status='LINKED',approval_result='REJECTED')
    check(accepted(row),'失效申请合法迟到结果被拒绝');count+=1
    return count


def main():
    model=json.loads((ROOT/'schema-model.json').read_text(encoding='utf-8'))
    tables={t['name']:t for t in model}
    check(len(tables)==len(model),'重复表名')
    fk_count=col_count=0
    names=set()
    for name,t in tables.items():
        check(re.fullmatch('[a-z][a-z0-9_]*',name) is not None,'表名非法')
        cols={c['name']:c for c in t['columns']};col_count+=len(cols)
        check(len(cols)==len(t['columns']),'重复字段 '+name)
        keys=[t['pk']]+t['unique']+t['indexes']
        for key in keys:
            check(set(key)<=cols.keys(),'未知索引字段 '+name)
            check(len(key)==len(set(key)),'索引重复字段 '+name)
        for c in t['columns']:
            if c['generated']:check(c['default'] is None,'生成列带默认')
        for kind,prefix in [('checks','ck'),('unique','uq'),('indexes','ix'),('fks','fk')]:
            for i,_ in enumerate(t[kind],1):
                k=f'{prefix}_{name}_{i}'
                check(len(k)<=64 and k not in names,'重复或超长约束名 '+k);names.add(k)
        for fk in t['fks']:
            fk_count+=1;ref=tables[fk['table']];refs={c['name']:c for c in ref['columns']}
            check(len(fk['columns'])==len(fk['references']),'外键基数不符')
            check(fk['references'] in [ref['pk']]+ref['unique'],'引用未显式唯一候选键 '+name)
            check(any(key[:len(fk['columns'])]==fk['columns'] for key in keys),'外键缺左前缀索引 '+name)
            for a,b in zip(fk['columns'],fk['references']):
                check(cols[a]['type']==refs[b]['type'],'外键类型/字符集不匹配 '+name+'.'+a)
        # 最坏完整索引键字节估算；不是InnoDB页容量测量。
        for key in keys:
            width=0
            for k in key:
                typ=cols[k]['type']; m=re.search(r'(?:VAR)?CHAR\((\d+)\)',typ)
                width+=int(m[1])*(1 if 'ascii' in typ else 4) if m else 8
            check(width<=3072,'索引超过InnoDB通常3072字节上限 '+name+str(key))
    sql=(ROOT/'schema.sql').read_text(encoding='utf-8')
    statements=sqlglot.parse(sql,read='mysql',error_level='RAISE')
    check(not any(isinstance(s,exp.Command) for s in statements),'SQL解析器发生未知语句回退')
    create={s.this.this.name:s for s in statements if isinstance(s,exp.Create)}
    check(create.keys()==tables.keys(),'DDL表集合与模型不一致')
    for name,s in create.items():
        columns=[c.name for c in s.this.expressions if isinstance(c,exp.ColumnDef)]
        check(columns==[c['name'] for c in tables[name]['columns']],'DDL列顺序/字段不一致 '+name)
    check(sum(isinstance(s,exp.Alter) for s in statements)==fk_count,'DDL外键数量不一致')
    ddl_fks=set()
    for stmt in statements:
        if not isinstance(stmt,exp.Alter):continue
        for f in stmt.find_all(exp.ForeignKey):
            reference=f.args['reference'].this
            ddl_fks.add((stmt.this.name,tuple(x.name for x in f.expressions),reference.this.name,tuple(x.name for x in reference.expressions)))
    expected_fks={(n,tuple(f['columns']),f['table'],tuple(f['references'])) for n,t in tables.items() for f in t['fks']}
    check(ddl_fks==expected_fks,'DDL外键实际端点与模型不一致')
    check(not re.search(r'\b(DROP|TRUNCATE|INSERT\s+IGNORE|FOREIGN_KEY_CHECKS|CREATE\s+DATABASE)\b',sql,re.I),'包含非预期部署/绕过语句')
    guards=(ROOT/'immutability.sql').read_text(encoding='utf-8')
    trigger_names=re.findall(r'CREATE TRIGGER `([^`]+)`',guards)
    check(len(trigger_names)==len(set(trigger_names)),'触发器重名')
    check(all(len(n)<=64 for n in trigger_names),'触发器名称超长')
    check(guards.count('END$$')==len(trigger_names),'触发器文本块不平衡')
    for n in ['tr_change_request_fixed','tr_change_target_insert','tr_change_target_update','tr_change_target_delete',
              'tr_import_batch_fixed','tr_audit_event_update','tr_audit_event_delete']:
        check(n in trigger_names,'缺不可变保护 '+n)
    api=yaml.safe_load((REPO/'docs/03-api/openapi.yaml').read_text(encoding='utf-8'))
    check(api['info']['version']=='1.1.0','API基线版本改变')
    digest=hashlib.sha256((REPO/'docs/03-api/openapi.yaml').read_bytes()).hexdigest()
    check(digest=='ec7ef9ef1ab046bb61cec9728c423c32f58311c850860be8ef54a9cf3a4798fd','权威契约摘要改变')
    ops={op['operationId']:(m.upper(),p,op.get('x-use-cases',[])) for source in ['paths','webhooks'] for p,v in api.get(source,{}).items() for m,op in v.items() if isinstance(op,dict) and 'operationId'in op}
    trace=json.loads((ROOT/'traceability.json').read_text(encoding='utf-8'))
    markdown_rows={}
    for line in (ROOT/'TRACEABILITY.md').read_text(encoding='utf-8').splitlines():
        parts=[p.strip() for p in line.strip('|').split('|')]
        if len(parts)!=6:continue
        words=parts[1].split(' ',1)
        if len(words)==2 and words[1] in ops:markdown_rows[words[1]]=parts
    check({r['operationId'] for r in trace}==ops.keys() and len(trace)==len(ops),'API操作追踪缺失/重复')
    for row in trace:
        check((row['method'],row['path'],row['useCases'])==ops[row['operationId']],'追踪与契约不符 '+row['operationId'])
        check(set(row['tables'])<=tables.keys(),'追踪引用未知表')
        check(all(x in (ROOT/'TRANSACTIONS.md').read_text(encoding='utf-8') for x in row['transactions']),'事务编号缺失')
        check(all(x in (ROOT/'INVARIANTS.md').read_text(encoding='utf-8') for x in row['invariants']),'不变量编号缺失')
        cells=markdown_rows.get(row['operationId'])
        check(cells is not None and cells[1]==row['method']+' '+row['operationId'] and cells[2]==row['path'],'Markdown操作追踪不一致')
        check([s.strip() for s in cells[3].split(',')]==row['tables'],'Markdown表追踪与JSON不一致')
    cases=check_cases(tables)
    diagram=ET.parse(ROOT/'database-erd.drawio').getroot()
    check(len(diagram.findall('diagram'))==4,'ER图页数')
    vertices=edges=0;shown=set()
    for d in diagram.findall('diagram'):
        cells=d.findall('.//mxCell');ids=[c.get('id') for c in cells]
        check(len(ids)==len(set(ids)),'图页重复ID')
        check('0'in ids and '1'in ids,'图页根缺失')
        for cell in cells:
            if cell.get('vertex')=='1' and cell.get('id')!='legend':
                check(cell.get('id') in tables,'图引用不存在实体');vertices+=1
                shown.add(cell.get('id'))
            if cell.get('edge')=='1':
                check(cell.get('source')in ids and cell.get('target')in ids,'图连线悬空')
                check(cell.find('mxGeometry')is not None,'图连线无geometry');edges+=1
    check('<!--' not in (ROOT/'database-erd.drawio').read_text(encoding='utf-8'),'图包含XML注释')
    check(shown==tables.keys(),'ER图遗漏实体')
    links=0
    docs=list(ROOT.glob('*.md'))+[REPO/'README.md',REPO/'docs/README.md']+list((REPO/'docs/project').glob('*.md'))
    for p in docs:
        for dest in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8-sig')):
            dest=dest.strip('<>').split('#')[0]
            if not dest or re.match(r'\w+://',dest):continue
            target=(p.parent/dest).resolve()
            check(target.exists(),'失效本地链接 '+str(p)+': '+dest);links+=1
    check('parent_id' not in {c['name'] for c in tables['department']['columns']},'错误引入部门父级')
    for n in ['company','department','business_role']:
        check('deleted_at' not in {c['name'] for c in tables[n]['columns']},'错误引入统一软删除')
    report={'scope':'STATIC_ONLY_NO_DATABASE','mysqlExecuted':False,'databaseConnections':0,
            'tables':len(model),'columns':col_count,'foreignKeys':fk_count,'ddlParsedStatements':len(statements),
            'triggerTextBlocks':len(trigger_names),'triggerEngineValidation':False,
            'checkExpressionReasoningCases':cases,'apiOperations':len(ops),'localLinks':links,
            'diagramPages':4,'diagramVertices':vertices,'diagramDistinctEntities':len(shown),'diagramEdges':edges,'openapiSha256':digest,
            'sqlglotVersion':sqlglot.__version__,'pyyamlVersion':yaml.__version__,
            'artifactSha256':{n:hashlib.sha256((ROOT/n).read_bytes()).hexdigest() for n in ['schema.sql','immutability.sql','schema-model.json','database-erd.drawio']},
            'limitations':['DDL未提交MySQL引擎','触发器仅文本结构检查','CHECK用有限表达式三值逻辑推演','无真实并发/JPA/查询计划/业务测试','图源未在draw.io客户端验收']}
    (ROOT/'static-validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
