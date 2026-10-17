"""详细设计静态校验：不连接数据库/网络，不运行应用或业务测试。"""
from pathlib import Path
import ast
import hashlib
import json
import math
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import yaml

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent.parent
BASELINE='bd26465'
GENERATED=['modules.json','issues.json','acceptance.json','operation-map.json','estimates.json',
           'MODULES.md','TRACEABILITY.md','HANDLER-CONTRACTS.md','TEST-PLAN.md','TASKS.md',
           'IMPLEMENTATION-PLAN.md','module-structure.drawio','core-flows.drawio']


def require(ok, message):
    if not ok:raise AssertionError(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(name):
    return json.loads((ROOT/name).read_text(encoding='utf-8'))


def content_models(container, api):
    """仅从HTTP content.schema层提取模型，独立核对生成器的通用引用遍历。"""
    if '$ref' in container:
        value=api
        for part in container['$ref'][2:].split('/'):value=value[part]
        container=value
    names=set()
    def walk(schema):
        if isinstance(schema,list):
            for item in schema:walk(item)
        elif isinstance(schema,dict):
            if '$ref' in schema:
                require(schema['$ref'].startswith('#/components/schemas/'),'未知HTTP schema引用')
                names.add(schema['$ref'].rsplit('/',1)[1]);return
            for value in schema.values():walk(value)
    for media in container.get('content',{}).values():walk(media.get('schema',{}))
    return sorted(names)


def dag(nodes, field):
    indexed={n['id']:n for n in nodes};seen=set();visiting=set();order=[]
    require(len(indexed)==len(nodes),'重复ID')
    def visit(key):
        require(key in indexed,'依赖未知ID '+key)
        require(key not in visiting,'依赖成环 '+key)
        if key in seen:return
        visiting.add(key)
        for other in indexed[key][field]:visit(other)
        visiting.remove(key);seen.add(key);order.append(key)
    for key in indexed:visit(key)
    return order


def checks(report):
    before={n:sha(ROOT/n) for n in GENERATED}
    subprocess.run([sys.executable,str(ROOT/'design_plan.py')],cwd=REPO,check=True,capture_output=True,text=True)
    require(before=={n:sha(ROOT/n) for n in GENERATED},'生成资料不确定或未刷新')
    report['generatorDeterministic']=True
    modules=read('modules.json');tasks=read('issues.json');cases=read('acceptance.json');mapping=read('operation-map.json')
    mids={m['id'] for m in modules};tids={t['id'] for t in tasks};cids={c['id'] for c in cases}
    module_order=dag(modules,'dependencies');task_order=dag(tasks,'dependencies')
    require((len(modules),len(tasks),len(cases),len(mapping))==(13,35,41,68),'冻结设计规模不符')
    require(len(cids)==len(cases),'重复验收编号')
    require(all(c['status']=='DESIGNED_NOT_RUN' for c in cases),'误记业务验收已运行')
    deferred={c['id'] for c in cases if c['delivery']=='FRONTEND_DEFERRED'}
    require(deferred=={'AT-40'},'前端后置边界不符')
    referenced=set()
    for task in tasks:
        require(task['status']=='PLANNED','任务误记已实现')
        require(task['remoteIssueNumber'] is None and task['remotePrNumber'] is None,'伪造远程事项')
        require(task['ownerSlot'] in 'ABCD' and task['reviewerSlot'] in 'ABCD' and task['ownerSlot']!=task['reviewerSlot'],'负责人/非作者评审无效')
        require(set(task['modules'])<=mids,'任务引用未知模块')
        require(task['acceptance'] and set(task['acceptance'])<=cids,'任务缺验收/引用未知')
        require(bool(task.get('acceptanceScope','').strip()),'任务缺具体验收边界')
        require(not set(task['acceptance'])&deferred,'前端实施混入后端任务')
        for field in ('estimateHours','reviewHours'):
            values=[task[field][s] for s in ('low','likely','high')]
            require(all(isinstance(n,(int,float)) and n>=0 for n in values) and values==sorted(values),'人时不合法')
        referenced.update(task['acceptance'])
    require(referenced==cids-deferred,'后端验收没有主责任务')
    api_path=REPO/'docs/03-api/openapi.yaml'
    api=yaml.safe_load(api_path.read_text(encoding='utf-8'))
    require(api['info']['version']=='1.2.0','API版本不符')
    require(sha(api_path)=='9910ceafadea88d85793d00f7bb4e6e5673700a8565366a407185c4517e0cbdf','权威契约改变')
    ops={}
    for direction,source in [('INBOUND','paths'),('OUTBOUND','webhooks')]:
        for path,item in api[source].items():
            for method,op in item.items():
                if isinstance(op,dict) and 'operationId'in op:
                    require(op['operationId'] not in ops,'重复operationId')
                    ops[op['operationId']]=(method.upper(),path,direction,op)
    old=json.loads((REPO/'docs/04-database/traceability.json').read_text(encoding='utf-8'))
    old={r['operationId']:r for r in old}
    db=json.loads((REPO/'docs/04-database/schema-model.json').read_text(encoding='utf-8'))
    tables={t['name'] for t in db}
    by_task={t['id']:t for t in tasks};covered=[];ucs=set()
    for row in mapping:
        name=row['operationId'];require(name in ops,'未知操作 '+name)
        method,path,direction,op=ops[name];covered.append(name);ucs.update(row['useCases'])
        require((row['method'],row['path'],row['direction'])==(method,path,direction),'操作身份不符')
        require(row['useCases']==op.get('x-use-cases',[]),'UC映射不符')
        for field in ('tables','transactions','invariants'):
            require(row[field]==old[name][field],'改写数据库追踪 '+name+'/'+field)
        task=by_task[row['issue']]
        require(name in task['operations'] and row['module'] in task['modules'] and row['ownerSlot']==task['ownerSlot'],'操作主责不符')
        require(set(row['tables'])<=tables,'操作引用未知表')
        require(set(row['acceptance'])<=cids and row['status']=='PLANNED_NOT_IMPLEMENTED','验收引用/实施状态不符')
        if direction=='INBOUND':require('AT-37'in row['acceptance'],'入站缺全契约验收')
        require(set(row['requestModels'])<=api['components']['schemas'].keys(),'未知输入模型')
        require(set(model for values in row['successResponseModels'].values() for model in values)<=api['components']['schemas'].keys(),'未知输出模型')
        require(row['requestModels']==content_models(op.get('requestBody',{}),api),'HTTP body绑定不精确')
        expected_responses={str(code):content_models(response,api) for code,response in op.get('responses',{}).items() if str(code).startswith('2')}
        require(row['successResponseModels']==expected_responses,'成功响应绑定不精确')
        parameters=[]
        for p in op.get('parameters',[]):
            if '$ref'in p:p=api['components']['parameters'][p['$ref'].rsplit('/',1)[1]]
            parameters.append(dict(name=p['name'],location=p['in'],required=p.get('required',False)))
        require(parameters==row['parameters'],'参数绑定不符')
    require(len(covered)==len(set(covered)) and set(covered)==ops.keys(),'操作缺失/重复')
    require(ucs=={f'UC-{i:02}' for i in range(1,12)},'UC未全覆盖')
    assigned=[op for t in tasks for op in t['operations']]
    require(len(assigned)==len(set(assigned)) and set(assigned)==ops.keys(),'任务操作主责缺失/重复')
    for case in cases:require(set(case['operations'])<=ops.keys(),'验收引用未知API')
    require({table for t in tasks for table in t['tables']}==tables,'43表未被任务覆盖或引入未知表')
    estimate=read('estimates.json')
    require(estimate['capacityHours']==dict(minimum=4*5*2,maximum=4*6*3),'团队容量错误')
    for s,expected in [('low',79.25),('likely',148),('high',244)]:
        total=sum(t['estimateHours'][s]+t['reviewHours'][s] for t in tasks)
        require(total==expected==estimate['totalHours'][s],'估算总量错误')
        for p in 'ABCD':
            load=sum(t['estimateHours'][s] for t in tasks if t['ownerSlot']==p)+sum(t['reviewHours'][s] for t in tasks if t['reviewerSlot']==p)
            require(load==estimate['personLoad'][p][s],'人员负载错误')
        require(sum(estimate['personLoad'][p][s] for p in 'ABCD')==total,'负载漏计或双计')
        bound=estimate['capacityLowerBounds'][s];hpd=bound['hoursPerPersonPerDay']
        require(bound['pooledCapacityDays']==math.ceil(total/(4*hpd)),'总容量下界错误')
        require(bound['fixedAssignmentCapacityDays']==math.ceil(max(estimate['personLoad'][p][s] for p in 'ABCD')/hpd),'分工容量下界错误')
    require(estimate['totalHours']['low']>72,'不应伪称预算内全量可交付')
    diagram_pages=vertices=edges=0
    for name in ('module-structure.drawio','core-flows.drawio'):
        text=(ROOT/name).read_text(encoding='utf-8');require('<!--'not in text,'图源含XML注释')
        mx=ET.fromstring(text);require(mx.tag=='mxfile','不是原生图源')
        for page in mx.findall('diagram'):
            diagram_pages+=1;cells=page.findall('.//mxCell');ids={c.get('id') for c in cells}
            require(len(ids)==len(cells) and {'0','1'}<=ids,'图页ID/根层不合法')
            graph=page.find('mxGraphModel');rects=[]
            for cell in cells:
                require(cell.get('id')!=cell.get('parent'),'图源自父级')
                if cell.get('vertex')=='1':
                    vertices+=1;geo=cell.find('mxGeometry');require(geo is not None,'节点缺几何')
                    x,y,w,h=(float(geo.get(k,'0')) for k in ('x','y','width','height'))
                    require(w>0 and h>0 and x>=0 and y>=0 and x+w<=float(graph.get('pageWidth')) and y+h<=float(graph.get('pageHeight')),'图页节点越界')
                    for a,b,c,d in rects:require(x+w<=a or a+c<=x or y+h<=b or b+d<=y,'节点矩形重叠')
                    rects.append((x,y,w,h))
                if cell.get('edge')=='1':
                    edges+=1;require(cell.get('source')in ids and cell.get('target')in ids and cell.find('mxGeometry')is not None,'图源悬空连线')
    require(diagram_pages==6,'图页数量不符')
    links=0
    docs=list(ROOT.glob('*.md'))+[REPO/'README.md',REPO/'docs/README.md']+list((REPO/'docs/project').glob('*.md'))
    for p in docs:
        for dest in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8-sig')):
            dest=dest.strip('<>').split('#')[0]
            if not dest or re.match(r'\w+://',dest):continue
            require((p.parent/dest).resolve().exists(),'失效链接 '+str(p.relative_to(REPO))+': '+dest);links+=1
    for p in ROOT.glob('*.py'):ast.parse(p.read_text(encoding='utf-8'))
    require(not re.search(r'[A-Za-z]:[\\/]',(REPO/'docs/project/MEMORY.md').read_text(encoding='utf-8')),'MEMORY含本机绝对路径')
    decisions=re.findall(r'^\| (D-\d+) \|',(REPO/'docs/project/DECISIONS.md').read_text(encoding='utf-8'),re.M)
    require(len(decisions)==len(set(decisions)),'决策ID重复')
    source_paths=['docs/03-api/openapi.yaml','docs/03-api/apifox-export.json','docs/04-database/schema.sql','docs/04-database/immutability.sql','docs/04-database/schema-model.json','docs/04-database/traceability.json']
    source_paths += [str(p.relative_to(REPO)).replace('\\','/') for p in (REPO/'docs/01-requirements/usecases-v1').glob('*.md')]
    source_hashes={}
    for name in source_paths:
        original=subprocess.check_output(['git','show',f'{BASELINE}:{name}'],cwd=REPO)
        require(hashlib.sha256(original).hexdigest()==sha(REPO/name),'意外改变上游 '+name);source_hashes[name]=sha(REPO/name)
    for directory in ('backend','deploy','tools','.github'):
        require(not (REPO/directory).exists(),'本设计阶段提前创建工程/远程配置目录 '+directory)
    report.update(dict(modules=len(modules),backendIssueDrafts=len(tasks),acceptanceItems=len(cases),
        backendAcceptanceItems=len(cases)-len(deferred),deferredFrontendItems=len(deferred),
        inboundOperations=sum(x[2]=='INBOUND' for x in ops.values()),outboundOperations=sum(x[2]=='OUTBOUND' for x in ops.values()),
        coveredDatabaseTables=len(tables),moduleTopologicalOrder=module_order,issueTopologicalOrder=task_order,
        estimatesHours=estimate['totalHours'],capacityHours=estimate['capacityHours'],diagramPages=diagram_pages,
        diagramVertices=vertices,diagramEdges=edges,localLinks=links,pythonSyntaxFiles=len(list(ROOT.glob('*.py'))),
        decisionIds=len(decisions),upstreamSha256=source_hashes,
        artifactSha256={n:sha(ROOT/n) for n in GENERATED}))


def main():
    report=dict(scope='DETAILED_DESIGN_STATIC_ONLY',baseline=BASELINE,status='RUNNING',
        businessTestsRun=False,databaseStarted=False,sqlExecuted=False,httpIntegrationRun=False,
        remoteIssuesCreated=0,remotePrsCreated=0,applicationCreated=False,deployed=False,
        diagramDesktopValidated=False)
    target=ROOT/'validation.json'
    target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    try:
        checks(report);report['status']='PASS'
    except Exception as error:
        report['status']='FAIL';report['failure']=str(error);raise
    finally:
        target.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
        print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
