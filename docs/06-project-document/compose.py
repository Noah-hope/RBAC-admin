"""保留设计正文，仅刷新接口清单与具名模块追踪附录。"""
from pathlib import Path
from collections import defaultdict
import json,hashlib
import yaml

HERE=Path(__file__).resolve().parent
ROOT=HERE.parent.parent

def esc(value):
    return str(value).replace('|',r'\|').replace('\n',' ')

def table(headers,rows,cols='1,3',role=''):
    attrs=f'cols="{cols}",options="header"'+(f',role="{role}"' if role else '')
    result=['['+attrs+']','|===','|'+ ' |'.join(map(esc,headers))]
    result.extend('|'+ ' |'.join(map(esc,row)) for row in rows)
    return '\n'.join([*result,'|===',''])

def generate():
    document=HERE/'项目设计说明书.adoc'
    body=document.read_text(encoding='utf-8').split('// BEGIN GENERATED APPENDICES')[0]
    # 按当前要求从A连续编号：接口清单为A，模块追踪为B。
    appendix=['// BEGIN GENERATED APPENDICES','']
    api=yaml.safe_load((ROOT/'docs/03-api/openapi.yaml').read_text(encoding='utf-8'))
    groups=defaultdict(list)
    operations=[]
    def resolve(value):
        if '$ref' not in value:return value
        result=api
        for key in value['$ref'][2:].split('/'):result=result[key]
        return result
    for path,methods in api['paths'].items():
        for method,operation in methods.items():
            if method not in ['get','post','put','patch','delete','head','options'] or not isinstance(operation,dict):continue
            operations.append(operation)
            parameters=[resolve(p) for p in [*methods.get('parameters',[]),*operation.get('parameters',[])]]
            sec=operation.get('security',api.get('security',[]))
            auth='、'.join(key for s in sec for key in s) or '匿名/按操作约定'
            if operation['operationId']=='logout':auth+='；失效/过期退出统一204'
            successes='/'.join(code for code in operation['responses'] if code.startswith('2'))
            description=operation.get('summary','')+'（'+operation['operationId']+'）'
            semantics='认证：'+auth+'；成功：'+successes
            if operation.get('x-access'):semantics+='；访问：'+operation['x-access']
            heads=[p['name'] for p in parameters if p.get('in')=='header' and p.get('required') and p['name']!='X-Request-Id']
            semantics+='；必需头：'+('、'.join(heads) if heads else '依认证入口；无额外业务必需头')
            groups[(operation.get('tags')or['其他'])[0]].append([method.upper()+' `'+path+'`',description,semantics])
    assert len(operations)==67
    appendix += ['[appendix]','== 入站接口清单','',
        '本附录从OpenAPI1.2.0提取67个入站操作，保留方法、完整路径、操作名、访问类别、成功码与必需业务头。认证头按各入口安全方案提供；路径/请求体中具体版本字段和完整输入输出Schema以契约定义为准。带“范围”的操作按当前单项任命处理，表中多种成功码表示不同业务结果，不代表都修改了授权。','']
    for tag,rows in groups.items():
        appendix += ['=== '+tag,'',table(['方法与路径','操作','认证、响应及必需业务头'],rows,'4,3,5','api-table')]
    appendix += ['[appendix]','== 数据流、用例、表与模块追踪摘要','',
        table(['处理/用例','主要存储与表组','主要模块/责任'],[
            ['P1 / UC-07、09','D1账号/会话/范围，D2任职，D6申请影响，D7审计','后台身份、管理范围、资格失效与安全读取；A'],
            ['P2 / UC-01','D3系统/目录/权限，D4/D5引用，D7审计','系统与权限目录、影响预览；B'],
            ['P3 / UC-02、03、04一般撤销','D4角色草稿，D5来源基线，D6草稿/固定申请及OA关联','变更申请模块编排，调用角色、授权来源、影响预览及OA投递能力；C协调B/D'],
            ['P4 / UC-10、UC-04离岗','D2组织任职，D5来源/授权，D8导入；D1/D6/D7联动','组织任职、授权来源、人员导入与生命周期处理；D，后台资格联动A'],
            ['P5 / UC-11','D6审批/执行；D4配置；D5实际授权；D7审计','OA结果与自动执行模块，调用角色配置及授权写入接口；C协调B/D'],
            ['P6 / UC-05、06','D2、D3、D4、D5同快照读取；D1可信服务身份','有效权限计算与操作校验模块；B'],
            ['P7 / UC-08','D7审计历史、D6申请关联；D1/D2当前资格','审计查询模块；C'],
        ],'3,5,4'),
        '各业务模块共同使用公共基座的事务、幂等和审计追加能力。','']
    document.write_text(body+'\n'.join(appendix),encoding='utf-8',newline='\n')
    sources=[ROOT/'docs/03-api/openapi.yaml',ROOT/'docs/04-database/schema-model.json',ROOT/'docs/05-detailed-design/MODULES.md',*sorted((ROOT/'docs/01-requirements/usecases-v1').glob('UC-*.md'))]
    sources += [ROOT/'docs/01-requirements/usecases-v1/用例说明.md',ROOT/'docs/01-requirements/usecases-v1/管理范围与人员权限规则.md',ROOT/'docs/05-detailed-design/ARCHITECTURE.md',ROOT/'docs/05-detailed-design/CORE-FLOWS.md',ROOT/'docs/04-database/TRANSACTIONS.md',ROOT/'docs/05-detailed-design/BE-33-SYSTEM-ACCEPTANCE.md',ROOT/'docs/project/MEMORY.md']
    (HERE/'sources.json').write_text(json.dumps({'date':'2026-10-27','requirements':'V1.3','api':'1.2.0','database':'1.1','inboundOperations':67,'sources':[{'path':p.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sources]},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'AsciiDoc refreshed: API and named-module appendices, 67 operations; {len(sources)} source records')

if __name__=='__main__':generate()
