"""ER 子视图的展示布局；不改变表、字段、关系或数据库元数据。"""
from collections import defaultdict
import math
import re
import unicodedata
import xml.etree.ElementTree as ET


PAGE_WIDTH, PAGE_HEIGHT = 2200, 1800
FONT_SIZE = 17
TABLE_NAMES = {
    'company':'公司表', 'department':'部门表', 'position':'岗位表', 'employee':'员工表',
    'employment':'员工任职表', 'admin_account':'后台账号表', 'admin_session':'后台会话表',
    'management_scope':'管理范围表', 'admin_appointment':'管理员任命表',
    'scope_system':'范围系统关联表', 'service_identity':'服务身份表',
    'business_system':'业务系统表', 'directory_node':'目录节点表', 'permission':'权限项表',
    'business_role':'业务角色表', 'role_permission':'角色权限关联表',
    'authorization_source':'授权来源表', 'grant_baseline':'角色授权关系基线表',
    'role_grant':'员工角色授予表', 'grant_employment_evidence':'授权任职依据表',
    'role_configuration_history':'角色配置历史表', 'change_request':'权限变更申请表',
    'change_target':'申请变更目标表', 'role_change_claim':'角色变更占用表',
    'relation_change_claim':'授权关系变更占用表', 'oa_outbox':'OA投递待办表',
    'oa_link':'OA审批关联表', 'oa_callback_event':'OA回调事件表',
    'oa_final_result':'OA最终审批结果表', 'execution_success':'权限变更执行成功表',
    'execution_attempt':'权限变更执行尝试表', 'audit_event':'审计事件表',
    'audit_subject':'审计关联主体表', 'import_file':'导入文件表',
    'import_batch':'导入批次表', 'import_plan_row':'导入计划明细表',
    'import_issue':'导入预检问题表', 'organization_mapping':'组织路径映射表',
    'employee_source_identity':'员工来源身份映射表', 'impact_preview':'影响预览表',
    'preview_item':'预览明细表', 'http_idempotency':'HTTP请求幂等表',
    'mutation_guard':'写事务协调锁表',
}
LAYOUTS = [
    ({'employee', 'employment', 'admin_account', 'management_scope'}, {
        'employee': (790, 620), 'employment': (790, 1250),
        'admin_account': (1320, 620), 'management_scope': (1320, 1100),
        'company': (280, 340), 'department': (280, 850), 'position': (280, 1400),
        'admin_session': (1910, 340), 'admin_appointment': (1910, 850),
        'scope_system': (1910, 1400),
    }),
    ({'business_role', 'authorization_source', 'grant_baseline', 'role_grant'}, {
        'business_role': (820, 650), 'authorization_source': (1380, 650),
        'role_grant': (820, 1120), 'grant_baseline': (1380, 1120),
        'business_system': (1100, 285), 'directory_node': (280, 340),
        'permission': (280, 820), 'role_permission': (280, 1400),
        'employee': (1910, 420), 'grant_employment_evidence': (1910, 1300),
        'role_configuration_history': (1370, 1580),
    }),
    ({'change_request'}, {
        'change_request': (1100, 890),
        'change_target': (280, 300), 'role_change_claim': (820, 300),
        'relation_change_claim': (1370, 300), 'oa_outbox': (1910, 300),
        'oa_link': (1700, 730), 'service_identity': (1910, 1160),
        'oa_callback_event': (1910, 1580), 'oa_final_result': (1370, 1580),
        'execution_attempt': (820, 1580), 'execution_success': (280, 1580),
        'audit_event': (280, 1140), 'audit_subject': (280, 730),
    }),
    ({'import_batch', 'impact_preview'}, {
        'import_batch': (850, 800), 'impact_preview': (1390, 1080),
        'import_file': (280, 320), 'import_plan_row': (850, 320),
        'import_issue': (1390, 320), 'organization_mapping': (1910, 400),
        'employee_source_identity': (1910, 870), 'preview_item': (1910, 1400),
        'http_idempotency': (280, 1410), 'mutation_guard': (850, 1500),
    }),
]


def text_width(value):
    return sum(FONT_SIZE if unicodedata.east_asian_width(c) in 'WF' else FONT_SIZE * .55 for c in value)


def box_size(cell, core):
    """按当前图内展示的字段计算大小，避免统一固定高度造成大块留白。"""
    title, rest = cell.get('value').split('<br>', 1)
    meaning, fields = rest.split('<hr>', 1)
    lines = fields.split('<br>')
    width = 330 if core else (260 if len(lines) <= 3 else 300)
    longest = max(text_width(re.sub('<[^>]*>', '', t)) for t in [title, *lines])
    width = max(width, math.ceil(longest + 34))
    meaning_lines = max(1, math.ceil(text_width(meaning) / (width - 36)))
    height = 52 + meaning_lines * 25 + 18 + len(lines) * 25 + 28
    return width, height


def blocked(a, b, rect):
    """线段是否穿过矩形内部（边界接触允许）。"""
    x, y, w, h = rect
    lo, hi = 0., 1.
    for p, q in [(-(b[0]-a[0]), a[0]-x), (b[0]-a[0], x+w-a[0]),
                 (-(b[1]-a[1]), a[1]-y), (b[1]-a[1], y+h-a[1])]:
        if abs(p) < 1e-8:
            if q <= 0:
                return False
        else:
            t = q / p
            if p < 0:
                lo = max(lo, t)
            else:
                hi = min(hi, t)
            if lo >= hi:
                return False
    return hi > 0 and lo < 1


def arrange_erd(mx):
    base = 'html=1;fontFamily=SimSun;fontStyle=1;'
    for page,(cores,positions) in zip(mx.findall('diagram'),LAYOUTS):
        positions = dict(positions)
        model = page.find('mxGraphModel')
        model.attrib.update(pageWidth=str(PAGE_WIDTH),pageHeight=str(PAGE_HEIGHT),grid='0',background='#FFFFFF')
        root = model.find('root')
        nodes = {c.get('id'):c for c in root if c.get('vertex')=='1' and c.get('id')!='legend'}
        assert nodes.keys() == positions.keys()
        boxes = {}
        for name,cell in nodes.items():
            w,h = box_size(cell,name in cores);cx,cy = positions[name]
            cell.set('value',f'<b>{TABLE_NAMES[name]}</b><br>'+cell.get('value'))
            cell.set('logicalTable',name)
            cell.set('tableLabel',TABLE_NAMES[name])
            boxes[name] = (cx-w/2,cy-h/2,w,h)
            cell.find('mxGeometry').attrib.update(x=str(cx-w/2),y=str(cy-h/2),width=str(w),height=str(h))
            cell.set('role','core' if name in cores else 'supporting')
            cell.set('style',base+'rounded=0;whiteSpace=wrap;align=left;verticalAlign=top;spacing=14;'+
                     'fontSize=17;fontColor=#17365D;'+
                     ('fillColor=#D9E6F2;strokeColor=#17365D;strokeWidth=3;' if name in cores else 'fillColor=#EAF2F8;strokeColor=#456B87;strokeWidth=2;'))
        legend = next(c for c in root if c.get('id')=='legend')
        legend.set('style',base+'text;whiteSpace=wrap;align=left;fontSize=17;fontColor=#17365D;')
        legend.set('value',f'<span style="font-size:27px">{page.get("name")}</span><br>'+
                   '中心为业务核心表，周边为辅助表；中文表名／英文表名；不同表直连，同表自关联保留回线。<br>'+
                   '实线：物理FK（子→父），N:1，含可空；虚线：事务协调，无FK；全量键及空值见数据字典。')
        legend.find('mxGeometry').attrib.update(x='50',y='20',width='2100',height='115')
        edges = [c for c in root if c.get('edge')=='1']
        ports = defaultdict(list)
        for edge in edges:
            a,b = edge.get('source'),edge.get('target')
            if a == b:
                continue
            for name,other,end in [(a,b,'source'),(b,a,'target')]:
                dx,dy = positions[other][0]-positions[name][0],positions[other][1]-positions[name][1]
                side = ('right' if dx>0 else 'left') if abs(dx)>abs(dy)*1.2 else ('bottom' if dy>0 else 'top')
                ports[(name,side)].append((math.atan2(dy,dx),edge,end))
        assigned = {}
        for (name,side),values in ports.items():
            values.sort(key=lambda row:(row[0],row[1].get('id')))
            x,y,w,h = boxes[name]
            for i,(_,edge,end) in enumerate(values):
                t=.18+.64*(i+1)/(len(values)+1)
                fx,fy = {'left':(0,t),'right':(1,t),'top':(t,0),'bottom':(t,1)}[side]
                p=(x+w*fx,y+h*fy)
                assigned[(edge.get('id'),end)] = (p,fx,fy)
        for edge in sorted(edges,key=lambda c:math.dist(positions[c.get('source')],positions[c.get('target')])):
            a,b=edge.get('source'),edge.get('target')
            dashed='dashed=1' in edge.get('style')
            edge.set('style',base+'edgeStyle=none;rounded=0;strokeWidth=3;fontSize=16;fontColor=#17365D;'+
                     'labelBackgroundColor=#FFFFFF;startSize=14;endSize=14;'+
                     ('dashed=1;dashPattern=8 5;endArrow=open;strokeColor=#777777;' if dashed else 'startArrow=ERmany;endArrow=ERone;strokeColor=#3569A2;'))
            geo=edge.find('mxGeometry');geo.clear();geo.attrib.update(relative='1',**{'as':'geometry'})
            if a == b:
                x,y,w,h=boxes[a]
                edge.set('style',edge.get('style')+'exitX=1;exitY=.3;entryX=.72;entryY=0;exitPerimeter=0;entryPerimeter=0;')
                arr=ET.SubElement(geo,'Array',{'as':'points'})
                for px,py in [(x+w+65,y+h*.3),(x+w+65,y-60),(x+w*.72,y-60)]:
                    ET.SubElement(arr,'mxPoint',x=str(px),y=str(py))
                ET.SubElement(geo,'mxPoint',x='0',y='-15',**{'as':'offset'})
                continue
            p,fx,fy=assigned[(edge.get('id'),'source')]
            q,gx,gy=assigned[(edge.get('id'),'target')]
            fractions=fx,fy,gx,gy
            clashes=[key for key,(x,y,w,h) in boxes.items() if key not in (a,b) and blocked(p,q,(x-8,y-8,w+16,h+16))]
            if clashes:
                options=[]
                def boundary(name):
                    x,y,w,h=boxes[name]
                    for t in [.08,.25,.5,.75,.92]:
                        for fx,fy in [(0,t),(1,t),(t,0),(t,1)]:
                            yield (x+w*fx,y+h*fy),fx,fy
                for pp,ffx,ffy in boundary(a):
                    for qq,ggx,ggy in boundary(b):
                        if any(blocked(pp,qq,(x+1,y+1,w-2,h-2) if key in (a,b) else (x-8,y-8,w+16,h+16)) for key,(x,y,w,h) in boxes.items()):continue
                        score=math.dist(pp,qq)+.3*(math.dist(p,pp)+math.dist(q,qq))
                        options.append((score,pp,qq,(ffx,ffy,ggx,ggy)))
                if options:
                    _,p,q,fractions=min(options,key=lambda r:r[0])
                    clashes=[]
            assert not clashes, (page.get('name'),edge.get('id'),a,b,clashes)
            edge.set('style',edge.get('style')+f'exitX={fractions[0]};exitY={fractions[1]};entryX={fractions[2]};entryY={fractions[3]};exitPerimeter=0;entryPerimeter=0;')
            if a == 'role_configuration_history':
                geo.set('x','.4')
            # 将基数标签放在线段旁，白底保证粗线下仍清楚。
            ET.SubElement(geo,'mxPoint',x='0',y='-15',**{'as':'offset'})
