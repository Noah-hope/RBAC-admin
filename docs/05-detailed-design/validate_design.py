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
GENERATED=['modules.json','issues.json','acceptance.json','operation-map.json','estimates.json','delivery-phases.json',
           'MODULES.md','TRACEABILITY.md','HANDLER-CONTRACTS.md','TEST-PLAN.md','TASKS.md',
           'IMPLEMENTATION-PLAN.md','TEAM-ASSIGNMENT.md','module-structure.drawio','core-flows.drawio']


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
    report['gitHistoryShallow']=subprocess.check_output(['git','rev-parse','--is-shallow-repository'],cwd=REPO,text=True).strip()=='true'
    before={n:sha(ROOT/n) for n in GENERATED}
    subprocess.run([sys.executable,str(ROOT/'design_plan.py')],cwd=REPO,check=True,capture_output=True,text=True)
    require(before=={n:sha(ROOT/n) for n in GENERATED},'生成资料不确定或未刷新')
    report['generatorDeterministic']=True
    modules=read('modules.json');tasks=read('issues.json');cases=read('acceptance.json');mapping=read('operation-map.json')
    mids={m['id'] for m in modules};tids={t['id'] for t in tasks};cids={c['id'] for c in cases}
    module_order=dag(modules,'dependencies');task_order=dag(tasks,'dependencies')
    require((len(modules),len(tasks),len(cases),len(mapping))==(13,35,41,68),'冻结设计规模不符')
    require(len(cids)==len(cases),'重复验收编号')
    partial_evidence={'AT-01':'BE-02-IMPLEMENTATION.md','AT-02':'BE-03-PERSISTENCE.md',
                      'AT-05':'BE-06-IDENTITY.md','AT-06':'BE-06-IDENTITY.md','AT-07':'BE-06-IDENTITY.md',
                      'AT-04':'BE-05-IDEMPOTENCY.md','AT-03':'BE-04-TRANSACTIONS.md','AT-33':'BE-04-TRANSACTIONS.md','AT-37':'BE-02-IMPLEMENTATION.md',
                      'AT-09':'BE-11-13-15-B-MODULE.md','AT-11':'BE-11-13-15-B-MODULE.md','AT-12':'BE-11-13-15-B-MODULE.md',
                      'AT-10':'BE-17-12-PREVIEW-CATALOG.md','AT-31':'BE-17-12-PREVIEW-CATALOG.md','AT-32':'BE-17-12-PREVIEW-CATALOG.md',
                      'AT-36':'BE-32-DEPLOYMENT.md','AT-13':'BE-10-PEOPLE.md','AT-27':'BE-10-PEOPLE.md','AT-29':'BE-10-PEOPLE.md',
                      'AT-08':'BE-08-MANAGEMENT-SCOPE.md','AT-19':'BE-08-MANAGEMENT-SCOPE.md',
                      'AT-26':'BE-28-IMPORT-FILES.md','AT-14':'BE-14-GRANT-BASELINES.md','AT-15':'BE-14-GRANT-BASELINES.md',
                      'AT-22':'BE-16-GRANT-MUTATIONS.md','AT-41':'BE-16-GRANT-MUTATIONS.md','AT-25':'BE-29-IMPORT-PRECHECK.md'}
    require(all(c['status']==('PARTIALLY_VERIFIED' if c['id'] in partial_evidence else 'DESIGNED_NOT_RUN') for c in cases),'验收局部/完整状态不符')
    require(all(c.get('evidence')==partial_evidence[c['id']] for c in cases if c['status']=='PARTIALLY_VERIFIED'),'局部验收缺实施证据')
    deferred={c['id'] for c in cases if c['delivery']=='FRONTEND_DEFERRED'}
    require(deferred=={'AT-40'},'前端后置边界不符')
    phase_plan=read('delivery-phases.json')
    foundation_ids={f'BE-{n:02}' for n in range(1,6)}
    business_ids=tids-foundation_ids
    required_gates=['FOUNDATION_ACCEPTED','BUSINESS_OWNER_ASSIGNED']
    require(phase_plan['revision']=='1.4' and phase_plan['decision']=='D-039' and phase_plan['organizationDecision']=='D-038' and phase_plan['collaborationDecision']=='D-051' and phase_plan['assignmentDecision']=='D-055','分工/验收决策不符')
    assignment_numbers = {'A':[6,7,8,9,27,32,34], 'B':[11,12,13,15,17,18,33,35],
                          'C':[19,20,21,22,23,24,25,31], 'D':[10,14,16,26,28,29,30]}
    assignments = {f'BE-{n:02}': owner for owner, numbers in assignment_numbers.items() for n in numbers}
    require(len(assignments)==sum(map(len,assignment_numbers.values()))==30 and set(assignments)==business_ids,'四人分工遗漏或重复任务')
    require(phase_plan['business']['ownerSlots']==list('ABCD')
            and phase_plan['business']['assignmentStatus']=='ASSIGNED','业务未登记四人分工')
    members=phase_plan['business']['members']
    require([m['alias'] for m in members]==list('ABCD') and all(
        m['realName'] is None and m['githubLogin'] is None and not m['repositoryAccessVerified']
        and m['localEnvironmentVerified']==(m['alias']=='D') for m in members),'虚构成员身份/接入证据')
    require(members[0].get('claimedBy')=='USER' and members[0].get('claimDecision')=='D-056'
            and members[3].get('claimedBy')=='USER' and members[3].get('claimDecision')=='D-064'
            and all(m.get('claimedBy') is None for m in members[1:3]),'成员认领状态不符')
    require(phase_plan['business']['assignmentSource']['evidence']=='TEAM-ASSIGNMENT.md','分工缺依据')
    assignment_doc=(ROOT/'TEAM-ASSIGNMENT.md').read_text(encoding='utf-8')
    summary_at=assignment_doc.index('## 分工汇总')
    sections=[assignment_doc.index(f'## {owner}：') for owner in 'ABCD']
    require(summary_at < sections[0] and sections==sorted(sections),'分工说明未先汇总、再依次按A/B/C/D展开')
    for i, owner in enumerate('ABCD'):
        end=sections[i+1] if i<3 else assignment_doc.index('## 四人共同遵守')
        rows=re.findall(r'^\| (BE-\d{2}) ',assignment_doc[sections[i]:end],re.M)
        expected=[f'BE-{n:02}' for n in assignment_numbers[owner]]
        require(rows==expected,'分工文档任务与登记不符 '+owner)
    require(set(phase_plan['foundation']['taskIds'])==foundation_ids and phase_plan['foundation']['ownerSlot']=='USER','基座阶段边界/负责人不符')
    require(set(phase_plan['business']['taskIds'])==business_ids and phase_plan['business']['ownerSlot'] is None,'业务阶段错误指派')
    require(phase_plan['business']['startConditions']==required_gates,'阶段门槛缺失')
    require(phase_plan['foundation']['status']=='OWNER_ACCEPTED'
            and phase_plan['gateStatus']=='FOUNDATION_ACCEPTED'
            and phase_plan['deliveryDecision']=='D-053','基座整体确认与交付状态不符')
    gate_evidence=phase_plan['gateEvidence']
    require([g['id'] for g in gate_evidence]==[f'F-{n:02}' for n in range(1,7)],'整体核对缺门槛项或重复')
    require(all(g['status']=='VERIFIED'
                and g['evidence']=='FOUNDATION-REVIEW.md' and g['date']=='2026-10-18'
                and g['scope']==('OWNER_ACCEPTANCE_AND_MAIN_DELIVERY' if g['id']=='F-06' else 'PUBLIC_FOUNDATION_ONLY')
                for g in gate_evidence),'技术核对/本人确认/主线交付边界不符')
    foundation_acceptance=phase_plan['foundation']['ownerAcceptance']
    delivery=phase_plan['foundation']['mainDelivery']
    require(foundation_acceptance.get('acceptedBy')=='USER'
            and foundation_acceptance.get('source')=='USER_MESSAGE'
            and foundation_acceptance.get('date')=='2026-10-18'
            and foundation_acceptance.get('message')=='确认基座通过，合并 main 并推送'
            and foundation_acceptance.get('acceptedSourceCommit')=='379ad438220a478093947f657d04eb56a6a2efcb','缺用户整体确认原始依据')
    require(delivery.get('mergeCommit')=='b68aa9aee6e78dd0b2cc1922d68028ce68ddeb11'
            and delivery.get('sourceCommit')==foundation_acceptance['acceptedSourceCommit']
            and delivery.get('branch')=='main' and delivery.get('remote')=='origin'
            and delivery.get('remoteRef')=='refs/heads/main' and delivery.get('pushVerified') is True
            and delivery.get('verifiedRemoteCommit')==delivery['mergeCommit'],'缺实际主线推送回查记录')
    require(subprocess.run(['git','merge-base','--is-ancestor',delivery['mergeCommit'],'HEAD'],cwd=REPO,capture_output=True).returncode==0,
            '浅克隆缺少历史，无法核对交付祖先；需补齐Git历史' if report['gitHistoryShallow'] else '交付合并不是当前代码祖先')
    merge_parents=subprocess.check_output(['git','show','-s','--format=%P',delivery['mergeCommit']],cwd=REPO,text=True).split()
    require(len(merge_parents)==2 and merge_parents[1]==delivery['sourceCommit'],'交付合并与本人确认版本不符')
    require((ROOT/'FOUNDATION-REVIEW.md').exists() and (ROOT/'FOUNDATION-USAGE.md').exists(),'缺交接核对或团队使用指南')
    require(phase_plan['foundation']['reviewerSlot'] is None,'虚构基座评审人')
    require(phase_plan['foundation']['reviewRequired'] is False and phase_plan['foundation']['reviewPolicy']=='OWNER_ACCEPTANCE' and phase_plan['foundation']['acceptanceOwnerSlot']=='USER','未落实基座本人确认')
    require(phase_plan['business']['reviewPolicy']=='OWNER_ACCEPTANCE'
            and phase_plan['business']['reviewRequired'] is False
            and phase_plan['business']['reviewerSlot'] is None
            and phase_plan['business']['acceptanceOwnerPolicy']=='TASK_OWNER','未落实D-051业务负责人确认')
    business_delivery=phase_plan['business'].get('mainDelivery',{})
    require(business_delivery.get('taskIds')==['BE-06','BE-07'] and business_delivery.get('decision')=='D-060'
            and business_delivery.get('sourceCommit')=='9b6d89e20e2f1646bd2ed96223d9c43a21e2f96f'
            and business_delivery.get('mergeCommit')=='ff19d1792a6934dbd6bca6c174dcba29f7acdad8'
            and business_delivery.get('remote')=='origin' and business_delivery.get('branch')=='main'
            and business_delivery.get('remoteRef')=='refs/heads/main' and business_delivery.get('pushVerified') is True
            and business_delivery.get('verifiedRemoteCommit')==business_delivery['mergeCommit'],'BE-06/07主线交付证据不符')
    business_parents=subprocess.check_output(['git','show','-s','--format=%P',business_delivery['mergeCommit']],cwd=REPO,text=True).split()
    require(len(business_parents)==2 and business_parents[1]==business_delivery['sourceCommit'],'BE-06/07合并来源不符')
    require(subprocess.run(['git','merge-base','--is-ancestor',business_delivery['mergeCommit'],'HEAD'],cwd=REPO).returncode==0,
            'BE-06/07交付不是当前提交祖先')
    require(subprocess.run(['git','diff','--quiet',business_delivery['sourceCommit'],business_delivery['mergeCommit']],cwd=REPO).returncode==0,
            'BE-06/07合并树与已确认分支不一致')
    deliveries=phase_plan['business'].get('additionalMainDeliveries',[])
    require(len(deliveries)==8,'BE-32/BE-10/BE-08/B/BE-17-12/BE-28/BE-14/BE-16主线交付记录缺失或重复')
    bootstrap_delivery=deliveries[0]
    require(bootstrap_delivery.get('taskIds')==['BE-32'] and bootstrap_delivery.get('decision')=='D-063'
            and bootstrap_delivery.get('sourceCommit')=='d5941c902712c92d4d8d8d7505bd6df4f5ceb139'
            and bootstrap_delivery.get('mergeCommit')=='609ee4bb017e46b42e43bcd8c2616d31ab410f5a'
            and bootstrap_delivery.get('prNumber')==1 and bootstrap_delivery.get('prState')=='MERGED'
            and bootstrap_delivery.get('prUrl')=='https://github.com/Noah-hope/RBAC-admin/pull/1'
            and bootstrap_delivery.get('remote')=='origin' and bootstrap_delivery.get('branch')=='main'
            and bootstrap_delivery.get('pushVerified') is True
            and bootstrap_delivery.get('verifiedRemoteCommit')==bootstrap_delivery['mergeCommit'],'BE-32 PR主线交付证据不符')
    parents=subprocess.check_output(['git','show','-s','--format=%P',bootstrap_delivery['mergeCommit']],cwd=REPO,text=True).split()
    require(len(parents)==2 and parents[1]==bootstrap_delivery['sourceCommit'],'BE-32合并来源不符')
    require(subprocess.run(['git','merge-base','--is-ancestor',bootstrap_delivery['mergeCommit'],'HEAD'],cwd=REPO).returncode==0,
            'BE-32合并不是当前提交祖先')
    require(subprocess.run(['git','diff','--quiet',bootstrap_delivery['sourceCommit'],bootstrap_delivery['mergeCommit']],cwd=REPO).returncode==0,
            'BE-32合并树与已核对分支不一致')
    people_delivery=deliveries[1]
    require(people_delivery.get('taskIds')==['BE-10'] and people_delivery.get('decision')=='D-065'
            and people_delivery.get('sourceCommit')=='6bfae88c89d0bad4b1599787ea47e2107a3bbcf9'
            and people_delivery.get('mergeCommit')=='8f1db6927b6d82e1a352ad016a4c1c056a6c648a'
            and people_delivery.get('pushVerified') is True and people_delivery.get('ownerAccepted') is False
            and people_delivery.get('verifiedRemoteCommit')==people_delivery['mergeCommit'],'BE-10交付与本人验收边界不符')
    parents=subprocess.check_output(['git','show','-s','--format=%P',people_delivery['mergeCommit']],cwd=REPO,text=True).split()
    require(len(parents)==2 and parents[1]==people_delivery['sourceCommit'],'BE-10合并来源不符')
    require(subprocess.run(['git','merge-base','--is-ancestor',people_delivery['mergeCommit'],'HEAD'],cwd=REPO).returncode==0,'BE-10交付不是当前祖先')
    require(subprocess.run(['git','diff','--quiet',people_delivery['sourceCommit'],people_delivery['mergeCommit']],cwd=REPO).returncode==0,'BE-10合并树与已验证实现不符')
    scope_delivery=deliveries[2]
    require(scope_delivery.get('taskIds')==['BE-08'] and scope_delivery.get('decision')=='D-069'
            and scope_delivery.get('sourceCommit')=='2199f0ef7b36a048845842c9803b08acf0f6612a'
            and scope_delivery.get('mergeCommit')=='ca8518918bd668f48769134763a10a163e10612b'
            and scope_delivery.get('prNumber')==2 and scope_delivery.get('prState')=='MERGED'
            and scope_delivery.get('prUrl')=='https://github.com/Noah-hope/RBAC-admin/pull/2'
            and scope_delivery.get('pushVerified') is True and scope_delivery.get('ownerAccepted') is True
            and scope_delivery.get('verifiedRemoteCommit')==scope_delivery['mergeCommit'],'BE-08 PR交付证据不符')
    parents=subprocess.check_output(['git','show','-s','--format=%P',scope_delivery['mergeCommit']],cwd=REPO,text=True).split()
    require(len(parents)==2 and parents[1]==scope_delivery['sourceCommit'],'BE-08合并来源不符')
    require(subprocess.run(['git','merge-base','--is-ancestor',scope_delivery['mergeCommit'],'HEAD'],cwd=REPO).returncode==0,'BE-08合并不是当前祖先')
    require(subprocess.run(['git','diff','--quiet',scope_delivery['sourceCommit'],scope_delivery['mergeCommit']],cwd=REPO).returncode==0,'BE-08合并树与已验收分支不符')
    b_delivery=deliveries[3]
    require(b_delivery.get('taskIds')==['BE-11','BE-13','BE-15'] and b_delivery.get('decision')=='D-072'
            and b_delivery.get('sourceCommit')=='b92cdbb9b6a30b6e697f7ad80ef06e38fa034067'
            and b_delivery.get('mergeCommit')=='0e391c6a652fb9ee12991b7b7d2ac85a3922d166'
            and b_delivery.get('ownerAccepted') is False and b_delivery.get('pushVerified') is True
            and b_delivery.get('verifiedRemoteCommit')==b_delivery['mergeCommit'],'B主线交付及负责人验收边界不符')
    parents=subprocess.check_output(['git','show','-s','--format=%P',b_delivery['mergeCommit']],cwd=REPO,text=True).split()
    require(len(parents)==2 and parents[1]==b_delivery['sourceCommit'],'B合并来源不符')
    require(subprocess.run(['git','merge-base','--is-ancestor',b_delivery['mergeCommit'],'HEAD'],cwd=REPO).returncode==0,'B交付不是当前祖先')
    require(subprocess.run(['git','diff','--quiet',b_delivery['sourceCommit'],b_delivery['mergeCommit']],cwd=REPO).returncode==0,'B合并树与通过验证的分支不符')
    preview_delivery=deliveries[4]
    require(preview_delivery.get('taskIds')==['BE-12','BE-17'] and preview_delivery.get('decision')=='D-074'
            and preview_delivery.get('sourceCommit')=='1fb95ad3d89789b8e45de7bedbb6397bc04f37e7'
            and preview_delivery.get('mergeCommit')=='3103b351089f300fe2553b79bb6f63ae5a383cf1'
            and preview_delivery.get('ownerAccepted') is False and preview_delivery.get('pushVerified') is True
            and preview_delivery.get('verifiedRemoteCommit')==preview_delivery['mergeCommit'],'BE-17/12主线交付事实不符')
    parents=subprocess.check_output(['git','show','-s','--format=%P',preview_delivery['mergeCommit']],cwd=REPO,text=True).split()
    require(len(parents)==2 and parents[1]==preview_delivery['sourceCommit'],'BE-17/12合并来源不符')
    require(subprocess.run(['git','merge-base','--is-ancestor',preview_delivery['mergeCommit'],'HEAD'],cwd=REPO).returncode==0,'BE-17/12交付不是当前祖先')
    require(subprocess.run(['git','diff','--quiet',preview_delivery['sourceCommit'],preview_delivery['mergeCommit']],cwd=REPO).returncode==0,'BE-17/12合并树与验证源不符')
    import_delivery=deliveries[5]
    require(import_delivery.get('taskIds')==['BE-28'] and import_delivery.get('decision')=='D-076'
            and import_delivery.get('sourceCommit')=='233f63352b0c8dea92fd916c47bd29d59267f97c'
            and import_delivery.get('mergeCommit')=='4b384fb7b629affd8fbd0e5762cad652bbdb3158'
            and import_delivery.get('ownerAccepted') is False and import_delivery.get('pushVerified') is True
            and import_delivery.get('verifiedRemoteCommit')==import_delivery['mergeCommit'],'BE-28交付/本人验收边界不符')
    parents=subprocess.check_output(['git','show','-s','--format=%P',import_delivery['mergeCommit']],cwd=REPO,text=True).split()
    require(len(parents)==2 and parents[1]==import_delivery['sourceCommit'],'BE-28合并来源不符')
    require(subprocess.run(['git','merge-base','--is-ancestor',import_delivery['mergeCommit'],'HEAD'],cwd=REPO).returncode==0,'BE-28交付不是当前祖先')
    require(subprocess.run(['git','diff','--quiet',import_delivery['sourceCommit'],import_delivery['mergeCommit']],cwd=REPO).returncode==0,'BE-28合并树与已验证来源不符')
    grant_delivery=deliveries[6]
    require(grant_delivery.get('taskIds')==['BE-14'] and grant_delivery.get('decision')=='D-078'
            and grant_delivery.get('sourceCommit')=='367cdae2acc6ff247116651abad53adafb6a34b9'
            and grant_delivery.get('mergeCommit')=='c7435d9b17a2f9be57d1e748cae24f125a45deb2'
            and grant_delivery.get('ownerAccepted') is False and grant_delivery.get('pushVerified') is True
            and grant_delivery.get('deliveryMode')=='LOCAL_MERGE_AND_PUSH'
            and grant_delivery.get('verifiedRemoteCommit')==grant_delivery['mergeCommit'],'BE-14交付/本人验收边界不符')
    parents=subprocess.check_output(['git','show','-s','--format=%P',grant_delivery['mergeCommit']],cwd=REPO,text=True).split()
    require(len(parents)==2 and parents[1]==grant_delivery['sourceCommit'],'BE-14合并来源不符')
    require(subprocess.run(['git','merge-base','--is-ancestor',grant_delivery['mergeCommit'],'HEAD'],cwd=REPO).returncode==0,'BE-14交付不是当前祖先')
    require(subprocess.run(['git','diff','--quiet',grant_delivery['sourceCommit'],grant_delivery['mergeCommit']],cwd=REPO).returncode==0,'BE-14合并树与已验证来源不符')
    grant_mutation_delivery=deliveries[7]
    require(grant_mutation_delivery.get('taskIds')==['BE-16'] and grant_mutation_delivery.get('decision')=='D-080'
            and grant_mutation_delivery.get('sourceCommit')=='dba2eaab8990fd17780d7f655fdfa049a6f7b2e1'
            and grant_mutation_delivery.get('mergeCommit')=='7ab021a80e26cbf4e3b4bc245f574fcd5c31c2ff'
            and grant_mutation_delivery.get('ownerAccepted') is False and grant_mutation_delivery.get('pushVerified') is True
            and grant_mutation_delivery.get('deliveryMode')=='LOCAL_MERGE_AND_PUSH'
            and grant_mutation_delivery.get('verifiedRemoteCommit')==grant_mutation_delivery['mergeCommit'],'BE-16交付/负责人验收边界不符')
    parents=subprocess.check_output(['git','show','-s','--format=%P',grant_mutation_delivery['mergeCommit']],cwd=REPO,text=True).split()
    require(parents==['a860dc8d4c321ebb702cda9294201b8a6c2aa0b6',grant_mutation_delivery['sourceCommit']],'BE-16合并双亲与基线/来源不符')
    require(subprocess.run(['git','merge-base','--is-ancestor',grant_mutation_delivery['mergeCommit'],'HEAD'],cwd=REPO).returncode==0,'BE-16交付不是当前祖先')
    require(subprocess.run(['git','diff','--quiet',grant_mutation_delivery['sourceCommit'],grant_mutation_delivery['mergeCommit']],cwd=REPO).returncode==0,'BE-16合并树与已验证来源不符')
    for module in modules:
        require(module['suggestedOwnerSlot'] in 'ABCD','模块建议组无效')
        shared={'M10':['A','D'],'M11':list('ABCD'),'M12':list('ABCD')}
        expected_owner='USER' if module['id']=='M00' else (None if module['id'] in shared else module['suggestedOwnerSlot'])
        require(module['ownerSlot']==expected_owner and module['ownerSlots']==shared.get(module['id'],[expected_owner]),'模块归属与任务分工不符')
        require(module['assignmentStatus']==('OWNER_CONFIRMED' if module['id']=='M00' else ('SHARED_BY_TASK' if module['id'] in shared else 'ASSIGNED')),'共享模块未按任务区分责任')
    referenced=set()
    for task in tasks:
        expected_status={'BE-01':'OWNER_ACCEPTED','BE-02':'OWNER_ACCEPTED','BE-03':'OWNER_ACCEPTED','BE-04':'OWNER_ACCEPTED','BE-05':'OWNER_ACCEPTED','BE-06':'OWNER_ACCEPTED','BE-07':'OWNER_ACCEPTED','BE-08':'OWNER_ACCEPTED','BE-10':'IMPLEMENTED','BE-11':'IN_PROGRESS','BE-12':'IN_PROGRESS','BE-13':'IN_PROGRESS','BE-14':'IMPLEMENTED','BE-15':'IN_PROGRESS','BE-16':'IMPLEMENTED','BE-17':'IN_PROGRESS','BE-28':'IMPLEMENTED','BE-29':'IMPLEMENTED','BE-32':'OWNER_ACCEPTED'}.get(task['id'],'PLANNED')
        require(task['status']==expected_status,'任务准备/实现状态不符')
        if task['id']=='BE-29':
            progress=task.get('implementationProgress',{})
            require(progress.get('decision')=='D-081' and progress.get('implemented') is True
                    and progress.get('httpImplemented') is True and progress.get('ownerAccepted') is False
                    and progress.get('mergedToMain') is False and progress.get('databaseExecuted') is True
                    and progress.get('databaseExecutionScope')=='DISPOSABLE_INSTANCE_ONLY'
                    and progress.get('branch')=='codex/be-29-import-precheck' and progress.get('dependencyBase')=='072585e'
                    and progress.get('evidence')=='BE-29-IMPORT-PRECHECK.md' and 'ownerAcceptance' not in task,
                    'BE-29预检实现/本人验收/未交付边界不符')
        if task['id']=='BE-16':
            progress=task.get('implementationProgress',{})
            require(progress.get('decision')=='D-079' and progress.get('implemented') is True
                    and progress.get('internalWritePortImplemented') is True and progress.get('httpImplemented') is False
                    and progress.get('ownerAccepted') is False and progress.get('mergedToMain') is True
                    and progress.get('pushVerified') is True and progress.get('deliveryDecision')=='D-080'
                    and progress.get('sourceCommit')==grant_mutation_delivery['sourceCommit']
                    and progress.get('mainMergeCommit')==grant_mutation_delivery['mergeCommit']
                    and progress.get('verifiedRemoteCommit')==grant_mutation_delivery['mergeCommit']
                    and progress.get('databaseExecuted') is True and progress.get('databaseExecutionScope')=='DISPOSABLE_INSTANCE_ONLY'
                    and progress.get('branch')=='codex/be-16-grant-mutations' and progress.get('dependencyBase')=='a860dc8'
                    and progress.get('evidence')=='BE-16-GRANT-MUTATIONS.md' and 'ownerAcceptance' not in task,
                    'BE-16内部实现/负责人验收/主线交付边界不符')
        if task['id']=='BE-14':
            progress=task.get('implementationProgress',{})
            require(progress.get('decision')=='D-077' and progress.get('implemented') is True
                    and progress.get('internalReadPortImplemented') is True and progress.get('httpImplemented') is True
                    and progress.get('ownerAccepted') is False and progress.get('mergedToMain') is True
                    and progress.get('pushVerified') is True and progress.get('deliveryDecision')=='D-078'
                    and progress.get('sourceCommit')==grant_delivery['sourceCommit']
                    and progress.get('mainMergeCommit')==grant_delivery['mergeCommit']
                    and progress.get('verifiedRemoteCommit')==grant_delivery['mergeCommit']
                    and progress.get('databaseExecutionScope')=='DISPOSABLE_INSTANCE_ONLY'
                    and progress.get('evidence')=='BE-14-GRANT-BASELINES.md'
                    and 'ownerAcceptance' not in task,'BE-14实现/验收/主线交付边界不符')
        if task['id']=='BE-28':
            progress=task.get('implementationProgress',{})
            require(progress.get('decision')=='D-075' and progress.get('implemented') is True
                    and progress.get('httpImplemented') is True and progress.get('ownerAccepted') is False
                    and progress.get('mergedToMain') is True and progress.get('pushVerified') is True
                    and progress.get('deliveryDecision')=='D-076'
                    and progress.get('sourceCommit')==import_delivery['sourceCommit']
                    and progress.get('mainMergeCommit')==import_delivery['mergeCommit']
                    and progress.get('databaseExecutionScope')=='DISPOSABLE_INSTANCE_ONLY'
                    and progress.get('evidence')=='BE-28-IMPORT-FILES.md'
                    and 'ownerAcceptance' not in task,'BE-28实现/验收/主线交付边界不符')
        if task['id'] in {'BE-11','BE-13','BE-15'}:
            progress=task.get('implementationProgress',{})
            require(progress.get('decision')=='D-070' and progress.get('implemented') is True
                    and progress.get('ownerAccepted') is False and progress.get('mergedToMain') is True
                    and progress.get('mainMergeCommit')==b_delivery['mergeCommit']
                    and progress.get('sourceCommit')==b_delivery['sourceCommit'] and progress.get('pushVerified') is True
                    and progress.get('databaseExecutionScope')=='DISPOSABLE_INSTANCE_ONLY'
                    and (ROOT/progress.get('evidence','')).is_file(),'B模块实施/确认/交付边界不符')
        if task['id'] in {'BE-12','BE-17'}:
            progress=task.get('implementationProgress',{})
            require(progress.get('decision')=='D-073' and progress.get('implemented') is True
                    and progress.get('ownerAccepted') is False and progress.get('mergedToMain') is True
                    and progress.get('deliveryDecision')=='D-074' and progress.get('pushVerified') is True
                    and progress.get('sourceCommit')==preview_delivery['sourceCommit']
                    and progress.get('mainMergeCommit')==preview_delivery['mergeCommit']
                    and progress.get('verifiedRemoteCommit')==preview_delivery['mergeCommit']
                    and progress.get('branch')=='codex/be-17-12-preview-catalog'
                    and progress.get('dependencyBase')=='93acc63'
                    and progress.get('databaseExecutionScope')=='DISPOSABLE_INSTANCE_ONLY'
                    and progress.get('evidence')=='BE-17-12-PREVIEW-CATALOG.md','BE-17/12实现与待交付边界不符')
        if task['id']=='BE-08':
            progress=task['implementationProgress']
            require(progress.get('implemented') is True and progress.get('ownerAccepted') is True
                    and progress.get('mergedToMain') is True and progress.get('decision')=='D-067'
                    and progress.get('databaseExecutionScope')=='DISPOSABLE_INSTANCE_ONLY'
                    and progress.get('evidence')=='BE-08-MANAGEMENT-SCOPE.md','BE-08实现/本人确认边界不符')
            acceptance=task.get('ownerAcceptance',{})
            require(acceptance.get('decision')=='D-068' and acceptance.get('ownerSlot')=='A'
                    and acceptance.get('source')=='USER_MESSAGE'
                    and acceptance.get('message')=='验证通过，接下来做什么'
                    and acceptance.get('implementationCommit')=='1c0eb3c055fe267fd0ff69c37e5120de3951eca1'
                    and acceptance.get('mergedToMain') is True
                    and acceptance.get('mainMergeCommit')==scope_delivery['mergeCommit']
                    and acceptance.get('mainPrNumber')==2 and acceptance.get('mainPrUrl')==scope_delivery['prUrl'],
                    'BE-08本人确认依据或主线状态不符')
        if task['id']=='BE-10':
            progress=task['implementationProgress']
            require(progress.get('internalReadPortImplemented') is True
                    and progress.get('implemented') is True and progress.get('httpImplemented') is True
                    and progress.get('employmentWriterImplemented') is True
                    and progress.get('ownerAccepted') is False and progress.get('mergedToMain') is True
                    and progress.get('mainMergeCommit')==people_delivery['mergeCommit']
                    and progress.get('databaseExecutionScope')=='DISPOSABLE_INSTANCE_ONLY'
                    and progress.get('evidence')=='BE-10-PEOPLE.md','BE-10实现/本人验收边界不符')
        if task['id']=='BE-06':
            progress=task['implementationProgress']
            require(progress.get('decision')=='D-056' and progress.get('evidence')=='BE-06-IDENTITY.md'
                    and progress.get('claimedBy')=='USER' and progress.get('implemented') is True
                    and progress.get('ownerAccepted') is True and progress.get('mergedToMain') is True,
                    'BE-06实施与本人确认/主线边界不符')
            acceptance=task.get('ownerAcceptance',{})
            require(acceptance.get('acceptedBy')=='USER' and acceptance.get('ownerSlot')=='A'
                    and acceptance.get('scope')=='BE-06' and acceptance.get('decision')=='D-057'
                    and acceptance.get('source')=='USER_MESSAGE' and acceptance.get('message')=='验证通过'
                    and acceptance.get('date')=='2026-10-18'
                    and acceptance.get('implementationCommit')=='1907a63f23ab35ad85404c9bde6f7bb2648fc704'
                    and acceptance.get('mergedToMain') is True
                    and acceptance.get('mainMergeCommit')==business_delivery['mergeCommit'],'BE-06负责人确认依据不符')
            require(subprocess.check_output(['git','show',acceptance['implementationCommit']+':backend/src/main/java/com/adgn/backend/identity/service/IdentityService.java'],cwd=REPO),
                    'BE-06确认来源缺少实际实现')
        if task['id']=='BE-01':
            acceptance=task.get('ownerAcceptance',{})
            require(acceptance.get('acceptedBy')=='USER' and acceptance.get('scope')=='BE-01'
                    and acceptance.get('message')==foundation_acceptance['message']
                    and acceptance.get('implementationCommit')==delivery['sourceCommit']
                    and acceptance.get('source')=='USER_MESSAGE' and acceptance.get('date')=='2026-10-18'
                    and acceptance.get('mergedToMain') is True,'BE-01本人确认不符')
            progress=task.get('implementationProgress',{})
            require(progress.get('evidence')=='FOUNDATION-REVIEW.md'
                    and progress.get('scope')=='LOCAL_ENVIRONMENT_AND_FOUNDATION_HANDOFF_REVIEW'
                    and progress.get('localTechnicalPreparationVerified') is True
                    and progress.get('teamRepositoryAccessVerified') is False
                    and progress.get('mergedToMain') is True
                    and progress.get('ownerAccepted') is True,'BE-01准备/协作待办状态不符')
        if task['id']=='BE-03':
            progress=task.get('implementationProgress',{})
            require(progress.get('evidence')=='BE-03-PREPARATION.md'
                    and progress.get('databaseExecuted') is True
                    and progress.get('databaseExecutionScope')=='DISPOSABLE_INSTANCE_ONLY'
                    and progress.get('databaseEvidence')=='BE-03-MYSQL-VALIDATION.md'
                    and progress.get('jpaMappingVerified') is True
                    and progress.get('jpaMappingScope')=='BASIC_SAMPLES_ONLY'
                    and progress.get('persistenceEvidence')=='BE-03-PERSISTENCE.md'
                    and progress.get('startupGuardVerified') is True
                    and progress.get('ownerAccepted') is True,'BE-03实施范围与本人确认记录不符')
            acceptance=task.get('ownerAcceptance',{})
            require(acceptance.get('acceptedBy')=='USER' and acceptance.get('scope')=='BE-03'
                    and acceptance.get('implementationCommit')=='b8caeb2'
                    and acceptance.get('message')=='继续' and acceptance.get('source')=='USER_MESSAGE'
                    and acceptance.get('interpretation')=='CONTINUE_AFTER_BE03_REVIEW'
                    and acceptance.get('date')=='2026-10-18' and acceptance.get('mergedToMain') is True,
                    'BE-03本人确认或主线状态不符')
        if task['id']=='BE-05':
            progress=task.get('implementationProgress',{})
            require(progress.get('evidence')=='BE-05-IDEMPOTENCY.md'
                    and progress.get('scope')=='HTTP_IDEMPOTENCY_RESERVATION_FENCING_AND_SAFE_REPLAY'
                    and progress.get('databaseExecutionScope')=='DISPOSABLE_INSTANCE_ONLY'
                    and progress.get('authorizationImplementation')=='CALLBACK_ONLY'
                    and progress.get('canonicalizationScope')=='CALLER_SUPPLIED_CANONICAL_BYTES_NOT_JCS'
                    and progress.get('defaultEnabled') is False
                    and progress.get('crossDomainBusinessVerified') is False
                    and progress.get('ownerAccepted') is True,'BE-05公共机制或本人确认范围不符')
            acceptance=task.get('ownerAcceptance',{})
            require(acceptance.get('acceptedBy')=='USER' and acceptance.get('scope')=='BE-05'
                    and acceptance.get('implementationCommit')=='d059d11'
                    and acceptance.get('source')=='USER_MESSAGE' and acceptance.get('message')=='是，已设为 true'
                    and acceptance.get('precedingHealthConfirmation')=='确认status状态为up'
                    and acceptance.get('idempotencyEnabledConfirmed') is True
                    and acceptance.get('interpretation')=='ENABLED_LOCAL_UP_CONFIRMATION_AFTER_BE05_HANDOFF'
                    and acceptance.get('date')=='2026-10-18' and acceptance.get('mergedToMain') is True,
                    'BE-05本人确认依据或主线状态不符')
        if task['id']=='BE-04':
            progress=task.get('implementationProgress',{})
            require(progress.get('evidence')=='BE-04-TRANSACTIONS.md'
                    and progress.get('databaseExecutionScope')=='DISPOSABLE_INSTANCE_ONLY'
                    and progress.get('authorizationImplementation')=='CALLBACK_ONLY'
                    and progress.get('crossDomainBusinessVerified') is False
                    and progress.get('ownerAccepted') is True,'BE-04公共机制或本人确认状态不符')
            acceptance=task.get('ownerAcceptance',{})
            require(acceptance.get('acceptedBy')=='USER' and acceptance.get('scope')=='BE-04'
                    and acceptance.get('implementationCommit')=='58b556a'
                    and acceptance.get('source')=='USER_MESSAGE' and acceptance.get('message')=='BE-04 确认服务为up状态，接下来做什么'
                    and acceptance.get('interpretation')=='LOCAL_UP_CONFIRMATION_AFTER_BE04_HANDOFF'
                    and acceptance.get('date')=='2026-10-18' and acceptance.get('mergedToMain') is True,
                    'BE-04本人确认依据或主线状态不符')
        if task['id']=='BE-02':
            acceptance=task.get('ownerAcceptance',{})
            require(acceptance.get('acceptedBy')=='USER' and acceptance.get('scope')=='BE-02'
                    and acceptance.get('date')=='2026-10-17' and acceptance.get('source')=='USER_MESSAGE'
                    and acceptance.get('message')=='确认通过' and acceptance.get('implementationCommit')=='f911c60'
                    and acceptance.get('mergedToMain') is True,'BE-02本人确认依据/主线状态不符')
            require(phase_plan['foundation'].get('ownerAcceptedTaskIds')==['BE-01','BE-02','BE-03','BE-04','BE-05'],'基座五项本人确认清单不符')
        if task['id']=='BE-07':
            progress=task.get('implementationProgress',{})
            require(progress.get('decision')=='D-058' and progress.get('evidence')=='BE-07-ACCOUNTS.md'
                    and progress.get('implemented') is True and progress.get('ownerAccepted') is True
                    and progress.get('mergedToMain') is True and progress.get('databaseExecutionScope')=='DISPOSABLE_INSTANCE_ONLY',
                    'BE-07实施及本人确认/主线边界不符')
            require(progress.get('dependencyIntegrationPolicy')=='ACCEPTED_ANCESTOR_WITH_EXPLICIT_CONTINUATION'
                    and progress.get('acceptedDependency')=='BE-06'
                    and progress.get('acceptedDependencyCommit')=='1907a63f23ab35ad85404c9bde6f7bb2648fc704',
                    'BE-07未记录已确认祖先与显式继续授权')
            require(subprocess.run(['git','merge-base','--is-ancestor',progress['acceptedDependencyCommit'],'HEAD'],cwd=REPO).returncode==0,
                    'BE-07分支不包含已确认BE-06祖先')
            acceptance=task.get('ownerAcceptance',{})
            require(acceptance.get('acceptedBy')=='USER' and acceptance.get('ownerSlot')=='A'
                    and acceptance.get('date')=='2026-10-19' and acceptance.get('scope')=='BE-07'
                    and acceptance.get('decision')=='D-059' and acceptance.get('source')=='USER_MESSAGE'
                    and acceptance.get('message')=='BE-07验证通过'
                    and acceptance.get('verificationMessage')=='BE-07这两项验证通过，下一步做什么'
                    and acceptance.get('implementationCommit')=='584eb4e8365b0041c09ccf3f76fcaf284bb32e26'
                    and acceptance.get('mergedToMain') is True
                    and acceptance.get('mainMergeCommit')==business_delivery['mergeCommit'],'BE-07负责人确认依据不符')
            require(subprocess.check_output(['git','show','584eb4e:backend/src/main/java/com/adgn/backend/identity/service/AccountService.java'],cwd=REPO),
                    'BE-07确认版本缺少实际实现')
        if task['id']=='BE-32':
            progress=task.get('implementationProgress',{})
            require(progress.get('decision')=='D-061' and progress.get('evidence')=='BE-32-DEPLOYMENT.md'
                    and progress.get('implemented') is True and progress.get('ownerAccepted') is True
                    and progress.get('mergedToMain') is True and progress.get('databaseExecuted') is True
                    and progress.get('databaseExecutionScope')=='DISPOSABLE_INSTANCE_ONLY'
                    and progress.get('targetServerDeployed') is False,
                    'BE-32实施/本人确认/真实服务器边界不符')
            acceptance=task.get('ownerAcceptance',{})
            require(acceptance.get('acceptedBy')=='USER' and acceptance.get('ownerSlot')=='A'
                    and acceptance.get('scope')=='BE-32' and acceptance.get('decision')=='D-062'
                    and acceptance.get('date')=='2026-10-19' and acceptance.get('source')=='USER_MESSAGE'
                    and acceptance.get('message')=='现在确认BE-32验证通过'
                    and acceptance.get('implementationCommit')=='cc76ac9c4bf4d073f7306b2f5e95c1d5cf52a614'
                    and acceptance.get('verificationEvidenceCommit')=='27b99700d7d6f747f9e1f1377ca7aed20c5e92c9'
                    and acceptance.get('mergedToMain') is True
                    and acceptance.get('mainMergeCommit')==bootstrap_delivery['mergeCommit']
                    and acceptance.get('mainPrNumber')==1 and acceptance.get('mainPrUrl')==bootstrap_delivery['prUrl'],
                    'BE-32本人确认或主线交付状态不符')
            require(subprocess.check_output(['git','show',acceptance['implementationCommit']+':backend/src/main/java/com/adgn/backend/bootstrap/BootstrapAdminApplication.java'],cwd=REPO),
                    'BE-32确认版本缺实际初始化实现')
        if task['id'] in foundation_ids:
            require(task['ownerAcceptance'].get('mainMergeCommit')==phase_plan['foundation']['mainDelivery']['mergeCommit'],'基座任务缺主线交付提交')
        require(task['remoteIssueNumber'] is None and task['remotePrNumber']=={'BE-32':1,'BE-08':2}.get(task['id']),'远程事项与实际证据不符')
        require(task['suggestedOwnerSlot'] in 'ABCD','建议组无效')
        require(task['reviewerSlot'] is None and task['suggestedReviewerSlot'] is None
                and task['reviewPolicy']=='OWNER_ACCEPTANCE' and task['reviewRequired'] is False
                and task['acceptanceOwnerPolicy']=='TASK_OWNER'
                and all(v==0 for v in task['reviewHours'].values()),'D-051任务仍设置他人评审或单列工时')
        if task['id'] in foundation_ids:
            require(task['phase']=='FOUNDATION' and task['ownerSlot']=='USER' and task['assignmentStatus']=='OWNER_CONFIRMED','基座归属不符')
            require(not task['startConditions'] and set(task['dependencies'])<=foundation_ids,'基座反向依赖业务')
            require(not task['operations'],'提前将业务API纳入基座')
            require(task['reviewPolicy']=='OWNER_ACCEPTANCE' and task['suggestedReviewerSlot'] is None and all(v==0 for v in task['reviewHours'].values()),'基座仍要求他人评审或重复计工时')
        else:
            require(task['phase']=='BUSINESS' and task['ownerSlot']==assignments[task['id']]
                    and task['assignmentStatus']=='ASSIGNED' and task['assignmentDecision']=='D-055','业务任务分工不符')
            require(task['startConditions']==required_gates,'业务缺阶段启动门槛')
            require(task['reviewPolicy']=='OWNER_ACCEPTANCE','业务任务未落实负责人确认')
        require(set(task['modules'])<=mids,'任务引用未知模块')
        require(task['acceptance'] and set(task['acceptance'])<=cids,'任务缺验收/引用未知')
        require(bool(task.get('acceptanceScope','').strip()),'任务缺具体验收边界')
        require(not set(task['acceptance'])&deferred,'前端实施混入后端任务')
        for field in ('estimateHours','reviewHours'):
            values=[task[field][s] for s in ('low','likely','high')]
            require(all(isinstance(n,(int,float)) and n>=0 for n in values) and values==sorted(values),'人时不合法')
        referenced.update(task['acceptance'])
    require(referenced==cids-deferred,'后端验收没有主责任务')
    previous_tasks=json.loads(subprocess.check_output(['git','show','9416980:docs/05-detailed-design/issues.json'],cwd=REPO))
    previous_by_id={t['id']:t for t in previous_tasks}
    for task in tasks:
        for field in ('dependencies','operations','modules','acceptance','estimateHours','tables','transactions','invariants'):
            require(task[field]==previous_by_id[task['id']][field],'组织修订意外改变原业务/技术任务 '+task['id']+'/'+field)
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
        require(row['ownerSlot']==assignments[task['id']] and row['assignmentStatus']=='ASSIGNED' and row['phase']=='BUSINESS','业务操作与任务负责人不符')
        require(row['suggestedOwnerSlot']==task['suggestedOwnerSlot'] and row['startConditions']==required_gates,'操作与任务分工/阶段不一致')
        require(set(row['tables'])<=tables,'操作引用未知表')
        expected_operation_status='OWNER_ACCEPTED_MAIN_DELIVERED' if task['id'] in {'BE-06','BE-07','BE-08'} else ('IMPLEMENTED_MAIN_DELIVERED_PENDING_OWNER' if task['id'] in {'BE-10','BE-11','BE-12','BE-13','BE-14','BE-15','BE-17','BE-28'} else ('IMPLEMENTED_PENDING_OWNER_AND_MAIN_DELIVERY' if task['id']=='BE-29' else 'PLANNED_NOT_IMPLEMENTED'))
        require(set(row['acceptance'])<=cids and row['status']==expected_operation_status,'验收引用/实施状态不符')
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
    require(set(estimate['businessPersonLoad'])==set('ABCD') and 'personLoad' not in estimate,'四人业务估算缺失或混入基座负载')
    for owner, load in estimate['businessPersonLoad'].items():
        selected=[t for t in tasks if t['ownerSlot']==owner]
        require(load['taskIds']==[t['id'] for t in selected] and load['taskCount']==len(selected),'个人任务汇总不符')
        for scenario in ('low','likely','high'):
            dev=sum(t['estimateHours'][scenario] for t in selected)
            require(load['developmentHours'][scenario]==load['totalHours'][scenario]==dev
                    and load['reviewHours'][scenario]==0,'个人负载计算不符')
    require(all(sum(p['totalHours'][s] for p in estimate['businessPersonLoad'].values())==estimate['phaseTotals']['BUSINESS']['totalHours'][s]
                for s in ('low','likely','high')),'四人负载漏计或重复')
    for s,expected in [('low',70.5),('likely',130.5),('high',209)]:
        total=sum(t['estimateHours'][s]+t['reviewHours'][s] for t in tasks)
        require(total==expected==estimate['totalHours'][s],'估算总量错误')
        for phase,ids in [('FOUNDATION',foundation_ids),('BUSINESS',business_ids)]:
            phase_estimate=estimate['phaseTotals'][phase]
            dev=sum(t['estimateHours'][s] for t in tasks if t['id'] in ids)
            review=sum(t['reviewHours'][s] for t in tasks if t['id'] in ids)
            require(phase_estimate['taskCount']==len(ids),'阶段任务数错误')
            require((dev,review,dev+review)==(phase_estimate['developmentHours'][s],phase_estimate['reviewHours'][s],phase_estimate['totalHours'][s]),'阶段估算错误')
        require(sum(p['totalHours'][s] for p in estimate['phaseTotals'].values())==total,'阶段漏计/双计')
        foundation_dev=sum(t['estimateHours'][s] for t in tasks if t['id'] in foundation_ids)
        require(estimate['foundationOwnerDevelopmentHours'][s]==foundation_dev,'基座用户负载错误')
        bound=estimate['capacityLowerBounds'][s];hpd=bound['hoursPerPersonPerDay']
        require(bound['pooledCapacityDays']==math.ceil(total/(4*hpd)),'总容量下界错误')
        business_total=sum(t['estimateHours'][s]+t['reviewHours'][s] for t in tasks if t['id'] in business_ids)
        require(bound['foundationOwnerDevelopmentDayEquivalent']==foundation_dev/hpd and bound['businessPooledDayEquivalent']==business_total/(4*hpd),'阶段小时折算错误')
        require(bound['stagedCapacityDays']==math.ceil(foundation_dev/hpd+business_total/(4*hpd)),'两阶段容量下界错误')
    require(estimate['decision']=='D-051' and estimate['revision']=='1.4' and estimate['assignmentDecision']=='D-055','估算协作规则来源不符')
    require(estimate['totalHours']=={'low':70.5,'likely':130.5,'high':209},'取消单列评审后的估算不符')
    require(estimate['totalHours']['likely']>72,'不应以乐观估算承诺预算内全量交付')
    diagram_pages=vertices=edges=0
    for name in ('module-structure.drawio','core-flows.drawio'):
        original_graph=subprocess.check_output(['git','show','9416980:docs/05-detailed-design/'+name],cwd=REPO)
        require(hashlib.sha256(original_graph).hexdigest()==sha(ROOT/name),'组织调整意外重绘技术图源')
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
    root_readme=(REPO/'README.md').read_text(encoding='utf-8')
    require('## 业务开发顺序' in root_readme,'根README缺业务开发流程图')
    diagram_section=root_readme.split('## 业务开发顺序',1)[1].split('\n## ',1)[0]
    diagram_match=re.search(r'```mermaid\n(.*?)\n```',diagram_section,re.S)
    require(diagram_match is not None,'业务流程图缺Mermaid代码块')
    diagram_text=diagram_match.group(1)
    require(diagram_text.startswith('flowchart TD\n'),'业务流程图不是自上而下树状布局')
    diagram_nodes=re.findall(r'^    (BE\d{2})\["([^"\n]+)"\]$',diagram_text,re.M)
    expected_nodes={t['id'].replace('-',''):t['id']+t['title'] for t in tasks if t['phase']=='BUSINESS'}
    require(len(diagram_nodes)==len(expected_nodes)
            and {key:label.replace('<br/>','') for key,label in diagram_nodes}==expected_nodes,'业务图编号/名称/覆盖不符')
    diagram_edges=set(re.findall(r'^    (BE\d{2}) --> (BE\d{2})$',diagram_text,re.M))
    original_edges={(d.replace('-',''),t['id'].replace('-','')) for t in tasks if t['phase']=='BUSINESS'
                    for d in t['dependencies'] if d in business_ids}
    require(diagram_edges<=original_edges,'业务图增加未声明的前置依赖')
    def dependency_closure(edge_set):
        closure=set()
        for start in expected_nodes:
            pending=[start];seen={start}
            while pending:
                node=pending.pop()
                for source,target in edge_set:
                    if source==node:
                        closure.add((start,target))
                        if target not in seen:seen.add(target);pending.append(target)
        return closure
    require(dependency_closure(diagram_edges)==dependency_closure(original_edges),'精简连线改变了业务任务等待/并行关系')
    roots=set(expected_nodes)-{target for _,target in diagram_edges}
    require(roots=={'BE06','BE10'},'业务起点不符')
    report.update(businessDiagramNodes=len(diagram_nodes),businessDiagramEdges=len(diagram_edges),
                  businessDiagramDependenciesEquivalent=True,businessDiagramRenderedByThisCheck=False)
    links=0
    docs=list(ROOT.glob('*.md'))+[REPO/'README.md',REPO/'docs/README.md',REPO/'backend/README.md']+list((REPO/'docs/project').glob('*.md'))
    for p in docs:
        for dest in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8-sig')):
            dest=dest.strip('<>').split('#')[0]
            if not dest or re.match(r'\w+://',dest):continue
            require((p.parent/dest).resolve().exists(),'失效链接 '+str(p.relative_to(REPO))+': '+dest);links+=1
    for p in ROOT.glob('*.py'):ast.parse(p.read_text(encoding='utf-8'))
    require(not re.search(r'(?<![A-Za-z0-9_])[A-Za-z]:[\\/]',(REPO/'docs/project/MEMORY.md').read_text(encoding='utf-8')),'MEMORY含本机绝对路径')
    decisions=re.findall(r'^\| (D-\d+) \|',(REPO/'docs/project/DECISIONS.md').read_text(encoding='utf-8'),re.M)
    require(len(decisions)==len(set(decisions)),'决策ID重复')
    source_paths=['docs/03-api/openapi.yaml','docs/03-api/apifox-export.json','docs/04-database/schema.sql','docs/04-database/immutability.sql','docs/04-database/schema-model.json','docs/04-database/traceability.json']
    source_paths += [str(p.relative_to(REPO)).replace('\\','/') for p in (REPO/'docs/01-requirements/usecases-v1').glob('*.md')]
    source_hashes={}
    for name in source_paths:
        original=subprocess.check_output(['git','show',f'{BASELINE}:{name}'],cwd=REPO)
        if name=='docs/04-database/schema.sql':
            expected=original.decode('utf-8').replace('CHECK (row_number>0','CHECK (`row_number`>0')
            require(original.decode('utf-8').count('CHECK (row_number>0')==2,'D-044修订范围不符')
            require((REPO/name).read_text(encoding='utf-8')==expected.replace('\r\n','\n'),'超出D-044的上游SQL变更')
            report['upstreamCorrection']='D-044: quote reserved ROW_NUMBER in exactly two CHECK expressions'
        else:
            require(hashlib.sha256(original).hexdigest()==sha(REPO/name),'意外改变上游 '+name)
        source_hashes[name]=sha(REPO/name)
    require((REPO/'backend/pom.xml').exists(),'BE-02工程缺失')
    migration_manifest=read('BE-03-MIGRATIONS.json')
    require(migration_manifest['databaseExecuted'] is True
            and migration_manifest.get('existingUserDatabasesTouched') is False,'隔离迁移证据/边界不符')
    require(len(migration_manifest['migrations'])==3,'初始迁移范围不符')
    for entry in migration_manifest['migrations']:
        require(sha(REPO/'backend/src/main/resources/db/migration'/entry['file'])==entry['sha256'],'迁移摘要变化未同步来源记录')
        if 'sourceSha256' in entry:
            require(sha(REPO/entry['source'])==entry['sourceSha256'],'迁移上游草案变化未核对')
    report['migrationPreparationFiles']=3
    report['migrationEngineExecutedByThisCheck']=False
    for expected in ('README.md','server.env.example','migrate.env.example','bootstrap.env.example',
                     'rbac-admin.service','rbac-migrate.service','rbac-bootstrap.service','nginx.conf.example'):
        require((REPO/'deploy'/expected).is_file(),'BE-32缺部署说明或模板 '+expected)
    for directory in ('tools','.github'):
        require(not (REPO/directory).exists(),'本设计阶段提前创建工程/远程配置目录 '+directory)
    report.update(dict(modules=len(modules),backendIssueDrafts=len(tasks),acceptanceItems=len(cases),
        backendAcceptanceItems=len(cases)-len(deferred),deferredFrontendItems=len(deferred),
        inboundOperations=sum(x[2]=='INBOUND' for x in ops.values()),outboundOperations=sum(x[2]=='OUTBOUND' for x in ops.values()),
        coveredDatabaseTables=len(tables),moduleTopologicalOrder=module_order,issueTopologicalOrder=task_order,
        estimatesHours=estimate['totalHours'],capacityHours=estimate['capacityHours'],diagramPages=diagram_pages,
        diagramVertices=vertices,diagramEdges=edges,localLinks=links,pythonSyntaxFiles=len(list(ROOT.glob('*.py'))),
        decisionIds=len(decisions),upstreamSha256=source_hashes,
        planRevision='1.4',foundationTasks=len(foundation_ids),businessTasksAwaitingAssignment=0,
        businessTasksAssigned=len(assignments),assignmentDecision='D-055',memberIdentitiesPending=4,
        confirmedUserAlias='D',historicallyConfirmedUserAliases=['A','B','D'],implementationDecision='D-081',
        internalPortTasksPendingOwner=['BE-16'],implementedBusinessTasksPendingOwner=['BE-10','BE-11','BE-12','BE-13','BE-14','BE-15','BE-16','BE-17','BE-28','BE-29'],
        ownerAcceptedBusinessTasks=['BE-06','BE-07','BE-08','BE-32'],businessTasksPendingMainDelivery=['BE-29'],businessAcceptanceDecision='D-068',
        businessMainDeliveryDecision='D-080',businessMainDeliveryMerge=grant_mutation_delivery['mergeCommit'],
        businessMainDeliveryPrUrl=None,priorScopeDeliveryPrUrl=scope_delivery['prUrl'],priorBootstrapDeliveryPrUrl=bootstrap_delivery['prUrl'],
        foundationReviewPolicy='OWNER_ACCEPTANCE',businessReviewPolicy='OWNER_ACCEPTANCE',collaborationDecision='D-051',environmentPreparationTask='BE-01:OWNER_ACCEPTED',
        foundationGateStatus=phase_plan['gateStatus'],businessOperationsAwaitingAssignment=0,businessOperationsAssigned=len(mapping),
        artifactSha256={n:sha(ROOT/n) for n in GENERATED}))


def main():
    report=dict(scope='DESIGN_AND_IMPLEMENTATION_TRACKING_STATIC_ONLY',baseline=BASELINE,status='RUNNING',
        businessTestsRunByThisCheck=False,databaseStarted=False,sqlExecuted=False,httpIntegrationRunByThisCheck=False,
        remoteIssuesCreated=0,remotePrsCreated=0,applicationCreatedByThisCheck=False,applicationExists=(REPO/'backend/pom.xml').exists(),deployed=False,
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
