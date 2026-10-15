"""Generate UC-02..11 and a combined editable draw.io file; never overwrite UC-01.
Uses native XML per the installed official drawio skill. PNGs are local previews.
"""
from pathlib import Path
import copy, html, json, re
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

OUT=Path(__file__).resolve().parent
SOURCE=OUT.parent/'usecases-v1'
WIDTH=1400
FONT_PATH='C:/Windows/Fonts/msyh.ttc'
COLORS={'blue':('#eef5ff','#32649a'),'amber':('#fff8e8','#bc9036'),'red':('#fff1f0','#b9534e'),'green':('#ecf7ef','#44855e')}

def wrap(s,font,width):
    out=[]
    for paragraph in s.split('\n'):
        line=''
        for c in paragraph:
            if font.getlength(line+c)>width and line:out.append(line);line=c
            else:line+=c
        out.append(line)
    return out

class Flow:
    def __init__(self,key,title,actors):
        self.key,self.title,self.actors=key,title,actors
        self.steps=[];self.branches=[];self.notes=[]
    def a(self,id,label,tone='blue'):
        self.steps.append(dict(id=id,label=label,kind='action',tone=tone));return self
    def d(self,id,label,main='是'):
        self.steps.append(dict(id=id,label=label,kind='decision',tone='blue',main=main));return self
    def b(self,source,labels,guard='否',target=None,tone='red'):
        self.branches.append(dict(source=source,labels=labels,guard=guard,target=target,tone=tone));return self
    def note(self,text):self.notes.append(text);return self
    def build(self):
        nodes=[];edges=[];lookup={};y=220
        def n(id,label,x,y,w,h,kind='action',tone='blue'):
            f=ImageFont.truetype(FONT_PATH,16 if kind in ('text','note') else 17)
            lines=wrap(label,f,w-36 if kind!='decision' else w-90)
            h=max(h,len(lines)*25+22)
            v=dict(id=id,label=label,x=x,y=y,w=w,h=h,kind=kind,tone=tone)
            nodes.append(v);lookup[id]=v;return v
        n('title',self.key+' '+self.title,40,20,1320,60,'text')
        n('subtitle','最终需求基线 · 2026-10-15 · '+self.actors,40,90,1320,50,'text')
        n('start','开始',475,155,190,45,'terminal')
        for step in self.steps:
            source=step['id'];branches=[b for b in self.branches if b['source']==source]
            v=n(source,step['label'],390,y,360,125,'decision') if step['kind']=='decision' else n(source,step['label'],375,y,390,75,tone=step['tone'])
            by=y+max(0,(v['h']-80)/2);bottom=y+v['h']
            assert len(branches)<=1,'Keep one explicit alternate branch per decision'
            for b in branches:
                previous=source
                for index,label in enumerate(b['labels']):
                    bid=source+'_alt_'+str(index)
                    bv=n(bid,label,50,by,260,75,tone=b['tone'])
                    edges.append(dict(a=previous,b=bid,label=b['guard'] if index==0 else '',loop=False))
                    previous=bid;by+=bv['h']+40
                if b['target']:
                    edges.append(dict(a=previous,b=b['target'],label='重新办理' if b['target']=='access' else '返回',loop=True))
                else:
                    bv=n(source+'_end','本次处理结束',90,by,180,45,'terminal',b['tone'])
                    edges.append(dict(a=previous,b=bv['id'],label='',loop=False));by+=bv['h']
                bottom=max(bottom,by)
            y=bottom+75
        last=n('end','完成本次处理',475,y,190,45,'terminal','green')
        sequence=['start']+[s['id'] for s in self.steps]+['end']
        for a,b in zip(sequence,sequence[1:]):
            step=next((s for s in self.steps if s['id']==a),{})
            edges.append(dict(a=a,b=b,label=step.get('main',''),loop=False))
        ny=260
        for i,text in enumerate(self.notes):
            v=n('note'+str(i),text,870,ny,460,90,'note','amber');ny+=v['h']+65
        height=int(max(y+160,ny+40)+1)
        n('footer','箭头表示先后；黄色框是规则说明；不同结束节点表示本次请求或本次办理结束。',40,height-70,1320,45,'text')
        for e in edges:
            assert e['a'] in lookup and e['b'] in lookup,e
        return dict(key=self.key,title=self.title,nodes=nodes,edges=edges,width=WIDTH,height=height)

cases={}
def case(n,actors):
    p=next(SOURCE.glob(f'UC-{n:02d}_*.md'))
    title=p.read_text(encoding='utf-8').splitlines()[0].split(' ',2)[2]
    f=Flow(f'UC-{n:02d}',title,actors);cases[n]=[f];return f

f=case(2,'总管理员／公司权限管理专员；外部 OA；UC-11 执行')
f.a('access','进入角色管理，核对当前公司和系统')
f.d('auth','具有目标范围内的配置申请资格？').b('auth',['拒绝角色配置申请'])
f.a('role','选择已有角色；新角色校验编码唯一\n保存待配置草稿及创建审计')
f.a('edit','配置系统进入权限及功能操作\n可增删权限，填写原因')
f.d('valid','归属、权限状态、非空配置及版本合法？').b('valid',['显示规则错误或版本冲突\n重新读取并修正配置'],target='role',tone='amber')
f.d('changed','存在实际配置差异？').b('changed',['返回无变化，不创建审批申请'],tone='amber')
f.a('preview','预览权限差异与受影响员工')
f.d('confirm','确认提交配置申请？').b('confirm',['仅保留草稿或取消未提交修改\n新角色仍不可分配'],tone='amber')
f.a('freeze','后端复核并保存固定方案\n记录原资格来源、基础版本与摘要')
f.d('stored','方案与提交审计保存成功？').b('stored',['本次提交事务回滚\n当前配置与已保存角色草稿保留'])
f.a('oa','提交／关联外部 OA，显示待审批\n当前有效配置不变')
f.a('execute','外部返回可信最终结果\n转 UC-11 接收、复核与自动执行')
f.a('result','管理员查看实际申请／执行状态\n首次配置执行成功才激活角色',tone='green')
f.note('角色归属\n一个公司＋一个系统，可限定直接任职部门\n岗位不自动绑定角色\n同公司专员共同管理角色，不按申请人隔离')
f.note('草稿与当前配置\n新角色草稿不可分配；允许仅保存草稿\n未提交的新角色草稿可按规则删除\n已有角色修改待审批时旧配置继续有效\n一个角色仅一份未结束配置方案')
f.note('功能权限\n目录登记查看、编辑等独立操作\n角色仅选择是否授予，不配置数据范围\n角色可只含进入权限或只含功能权限\n正式配置不得为空')
f.note('OA 与执行\n驳回后复制新方案修改并重新审批\n批准不等于已执行，失败显示待恢复\n原申请人离任不单独阻止合法方案执行\n本平台不实现 OA 内部审批流程')

f=case(3,'总管理员／公司权限管理专员；外部 OA；UC-11 执行')
f.a('access','选择目标公司并进入员工授权')
f.d('auth','会话与公司／系统管理范围有效？').b('auth',['拒绝本次分配申请'])
f.a('edit','选择员工和已生效角色\n明确员工—角色配对，填写原因')
f.d('valid','全部配对满足范围、任职及版本条件？').b('valid',['整批拒绝，提示问题配对\n重新读取并修改'],target='edit',tone='amber')
f.a('dedupe','排除已有相同有效授权\n失效历史不阻止重新申请')
f.d('changed','仍有实际新增关系？').b('changed',['返回无变化，不提交 OA'],tone='amber')
f.a('preview','预览实际新增配对、角色功能权限集合')
f.d('confirm','确认提交角色分配申请？').b('confirm',['取消本次未提交修改'],tone='amber')
f.a('freeze','复核并固定新增清单、角色版本\n原资格来源、关系版本与原因')
f.d('stored','方案与提交记录保存成功？').b('stored',['整批回滚，原授权不变'])
f.a('oa','关联 OA，显示待审批\n提交本身不新增授权')
f.a('execute','OA 返回最终结果，转 UC-11\n批准后复核并整体执行合法新增')
f.a('result','显示实际执行结果\n业务系统按当前公司重新查询',tone='green')
f.note('任职条件\n公司级角色要求本公司有效任职\n部门级角色要求该部门直接有效任职\n下属部门不自动适用；其他合法兼岗保留')
f.note('多角色\n只新增指定配对，不覆盖已有其他角色\n同公司、同系统角色权限取并集\n进入资格和功能也必须来自该公司、系统')
f.note('申请与失效\n同一目标关系的未结束申请禁止重叠\n角色或授权资格版本变化时旧批准不能强行执行\n失效后需新申请，不能复活旧记录\n批量一项不合法则整批不执行')

f=case(4,'总管理员／公司权限管理专员；外部 OA；UC-11 执行')
f.a('access','查询可管理的员工角色关系及来源')
f.d('auth','会话及对应来源的撤权资格有效？').b('auth',['拒绝撤销申请，不披露越范围关系'])
f.a('edit','选择明确的授权关系，填写撤销原因')
f.a('dedupe','排除已撤销／已失效的无变化项\n保留历史，定位原关系与版本')
f.d('changed','仍有实际需要撤销的关系？').b('changed',['显示历史／无变化，不提交 OA'],tone='amber')
f.a('preview','预览撤销明细与可见范围内影响\n提示其他角色可能提供重合权限')
f.d('confirm','确认提交撤销申请？').b('confirm',['取消本次未提交修改'],tone='amber')
f.a('check','复核原来源范围、全部目标关系及版本')
f.d('valid','全部目标关系合法且未发生冲突？').b('valid',['整批拒绝，重新查询并选择'],target='edit',tone='amber')
f.a('freeze','固定撤销清单并保存方案与提交记录')
f.d('stored','本次提交保存成功？').b('stored',['提交回滚，原授权不变'])
f.a('oa','交由 OA，显示待审批\n不因提交立即撤销有效关系')
f.a('execute','最终结果转 UC-11\n批准后复核并整体撤销指定关系')
f.a('result','显示实际执行结果\n权限按剩余有效来源重新计算',tone='green')
f.note('撤销边界\n不删除角色、不修改角色权限、不停用员工\n不影响其他员工或其他角色的重合权限\n相同操作仍有有效来源则继续保留')
f.note('历史与重新授予\n员工已调离／停用不阻止管理本公司历史\n已失效关系无需 OA 才能失去权限\n旧撤销审批不能误撤后来重新授予的新关系')
f.note('审批等待\n待审批只说明本次撤销尚未执行\n其他合法变更仍可能使原授权失效\n驳回不执行；变更后的实际状态照常有效')

f=case(5,'受信业务系统后端；员工已由业务系统认证')
f.a('access','提交服务凭据、员工、当前公司及系统')
f.d('valid','参数、服务身份与系统绑定合法？').b('valid',['返回调用错误，不返回正常授权结果'])
f.a('read','读取员工、公司及当前任职状态')
f.d('eligible','员工／公司有效且有该公司有效任职？').b('eligible',['canAccessSystem=false\n菜单=[]，操作=[]'],tone='amber')
f.a('roles','一致读取当前公司、系统的有效角色\n排除失效关系、未生效角色及无效目录')
f.d('entry','具备该公司、该系统进入资格？').b('entry',['canAccessSystem=false\n菜单=[]，操作=[]'],tone='amber')
f.a('merge','合并功能操作集合并去重\n不跨公司／系统合并授权来源')
f.a('menus','按对应查看／进入权限生成可见菜单\n补充必要祖先，标记仅导航容器')
f.d('computed','授权读取、目录及功能权限计算正常完成？').b('computed',['返回服务错误\n不默认放行或假成功空结果'])
f.a('result','一致返回 canAccessSystem=true\n菜单及有效功能操作集合',tone='green')
f.a('external','业务系统映射功能入口\n按操作权限执行访问拦截')
f.note('无功能授权\n有进入资格但没有功能权限时\ncanAccessSystem=true，菜单／操作为空\n编辑权限不自动推导查看或菜单可见')
f.note('统一有效性\n只计算当前公司、当前系统有效授权\n授权失效后恢复身份不能恢复旧关系\n相同数据状态下须与 UC-06 判断一致')
f.note('任一读取／计算发生服务错误\n返回服务错误，终止本次调用\n必要权限数据损坏也属于错误\n不伪装成功空集合，不默认放行')
f.note('外部职责\n服务凭据证明调用系统，不证明员工身份\n员工标识来自业务系统受信登录上下文\n本系统不设计业务数据权限\n一期用简单 HTTP 调用演示')

f=case(6,'受信业务系统后端；可独立调用')
f.a('access','提交服务凭据、员工、当前公司\n系统及待检查权限编码')
f.d('valid','参数、服务身份与系统绑定合法？').b('valid',['返回调用错误，不能据此放行'])
f.a('read','检查员工／公司状态和有效任职\n读取目标权限及当前有效授权')
f.d('eligible','员工及任职有效且目标权限有效？').b('eligible',['返回无权限\nhasPermission=false'],tone='amber')
f.d('kind','目标是功能操作权限？').b('kind',['目标为 SYSTEM_ACCESS\n直接检查该公司、系统的进入来源','返回对应 hasPermission'],tone='green')
f.d('entry','具有该公司、该系统进入资格？').b('entry',['返回无权限\nhasPermission=false'],tone='amber')
f.d('source','存在该操作的有效授权来源？').b('source',['返回无权限\nhasPermission=false'],tone='amber')
f.a('merge','只检查该公司、系统内的有效来源\n任一有效角色提供目标操作即可')
f.a('result','返回 hasPermission=true',tone='green')
f.a('external','业务系统按结果执行功能访问拦截\n并执行自身业务校验')
f.note('功能权限查询边界\n不接收业务记录或数据过滤条件\n查看不自动授予编辑，操作分别判断\n菜单不可见不等于操作未获授权')
f.note('SYSTEM_ACCESS\n进入权限直接检查，不递归要求进入资格\n只判断有无权限，不返回数据范围\n进入权限也按当前公司、系统计算')
f.note('异常路径\n未知／停用操作属于无权限\n服务身份错误、系统调用不合法返回调用错误\n数据库故障／权限数据损坏返回服务错误\n所有服务故障终止本次调用，不默认允许')

f=case(7,'权限中心总管理员；保存直接生效，不经过 OA')
f.a('access','打开管理员配置，选择目标公司')
f.d('auth','总管理员会话及资格有效？').b('auth',['拒绝配置管理员及管理范围'])
f.a('read','读取公司部门、专员名单\n共同系统范围及配置版本')
f.a('edit','选择允许管理的系统\n新增、移除或替换公司专员')
f.d('accounts','拟新增人员已有后台账号？').b('accounts',['由总管理员按 UC-09 创建账号\n创建成功不等于已任命'],target='edit',tone='amber')
f.a('preview','预览任免、系统变化与待处理申请影响\n填写原因')
f.d('confirm','确认保存配置？').b('confirm',['取消本次未提交更改'],tone='amber')
f.a('check','复核总管理员资格、员工任职、系统\n最终人数、重复项及配置版本')
f.d('valid','最终配置满足全部约束？').b('valid',['显示问题或版本冲突\n重新读取并修正'],target='edit',tone='amber')
f.a('save','整体保存名单、系统范围、版本与审计')
f.d('stored','全部保存成功？').b('stored',['本次事务回滚，旧名单和范围不变'])
f.a('result','显示直接生效的新范围\n后续管理请求使用最新资格',tone='green')
f.note('人员与共享范围\n每公司通常 1～3 名专员，可暂为零\n同公司共享角色管理，不逐个指定角色\n总公司范围不自动包含其他分公司')
f.note('资格与业务授权分离\n任命不授予外部系统进入或功能权限\n取消任命不撤销仍有效的业务角色\n员工全局停用使任命失效\n恢复员工需总管理员重新任命')
f.note('系统范围缩小\n已有业务授权不自动撤销\n公司专员原申请执行时仍复核当前公司范围\n总管理员原申请按全局资格复核\n处理人身份不能改变原申请依据')

f=case(8,'总管理员／公司权限管理专员；只读查询')
f.a('access','打开授权审计并填写筛选／分页条件')
f.d('auth','会话、查询资格和请求范围有效？').b('auth',['拒绝越范围查询，不披露目标内容'])
f.d('valid','筛选时间、字段及分页参数合法？').b('valid',['提示参数问题，修正后查询'],target='access',tone='amber')
f.a('query','后端强制附加当前管理范围\n分页读取范围内摘要与总数')
f.d('queried','查询正常完成？').b('queried',['返回服务错误，不伪装无日志'])
f.d('found','存在匹配记录？').b('found',['返回正常空列表与零条数量'],tone='amber')
f.a('list','显示安全摘要列表')
f.d('detail','管理员选择查看详情？').b('detail',['本次列表查询完成\n可重新筛选或退出'],tone='green')
f.a('recheck','按最新资格重新检查记录来源\n列表曾出现不代表详情永久可访问')
f.d('allowed','当前仍可访问该记录？').b('allowed',['统一不可访问／未找到响应\n不泄露越范围记录'])
f.a('result','展示差异、审批与执行关联事件\n区分拟变更和实际生效',tone='green')
f.note('审计只读\n不修改、删除或从详情重放历史\n已停用员工和失效授权仍可追溯\n查询范围始终以当前管理员资格计算')
f.note('敏感信息\n不展示密码、令牌、服务密钥、完整手机号\n不返回完整异常堆栈\n全局目录和任免日志仅总管理员可见')
f.note('关联记录与故障\n详情及关联事件读取失败也返回服务错误\n批准、执行失败、实际成功分别展示\n历史快照不代表员工当前权限')

f=case(9,'后台管理员；本平台身份与外部业务权限独立')
f.a('input','填写后台账号与密码，提交登录')
f.d('valid','登录参数合法？').b('valid',['提示参数错误，不尝试默认账号'])
f.a('read','读取账号，验证密码摘要\n检查账号、关联员工和当前管理资格')
f.d('service','身份读取与验证服务正常？').b('service',['返回服务错误，不假称密码错误'])
f.d('auth','密码正确、身份有效且有管理资格？').b('auth',['统一登录失败，不披露账号存在性'])
f.d('temporary','可以直接建立正常会话？').b('temporary',['临时／重置密码：仅建立受限会话\n只允许改密或退出','按密码修改子流程保存新密码\n使旧会话失效，返回登录页'],tone='amber')
f.a('session','建立正常会话并返回身份及当前范围')
f.d('stored','会话建立与必要记录成功？').b('stored',['返回服务错误，不产生半完成可用会话'])
f.a('use','访问管理页面／接口\n每次按最新身份及目标范围检查')
f.d('current','本次管理请求仍满足资格及范围？').b('current',['拒绝该请求，旧页面不能绕过\n资格失效后停止管理'])
f.d('logout','管理员选择退出？').b('logout',['继续使用有效会话'],target='use',tone='blue')
f.a('result','撤销当前会话，清理客户端状态\n返回登录页；重复退出保持已退出',tone='green')
f.note('后台与员工\n导入员工不自动创建后台账号\n只有账号没有有效任命不能进入管理后台\n成功登录不授予外部业务系统权限')
f.note('停用与恢复\n员工停用使业务授权及专员任命失效\n恢复身份不恢复旧任命，需 UC-07 重新任命\n后台账号停用不等于员工全局停用')
f.note('会话与密码\n普通退出只结束当前会话\n改密／重置使所有旧会话失效\n临时密码改密前不能访问管理接口\n配套创建、改密、重置见其他页签')

f=Flow('UC-09-账号创建','创建专员后台账号','总管理员；UC-09 配套子流程，不新增正式用例编号')
cases[9].append(f)
f.a('access','总管理员选择目标员工')
f.d('auth','总管理员资格及目标员工有效？').b('auth',['拒绝创建账号'])
f.d('exists','目标员工尚无后台账号？').b('exists',['复用已有账号，不新建／改绑'],tone='green')
f.a('edit','填写唯一登录名、临时密码\n确认值、原因与请求编号')
f.d('valid','唯一性、字段及密码策略符合要求？').b('valid',['提示问题，不覆盖已有账号'],target='edit',tone='amber')
f.a('save','复核后整体保存账号、员工关联\n密码哈希、强制改密标记与审计')
f.d('stored','全部保存成功？').b('stored',['整体回滚，不产生半完成账号'])
f.a('result','返回账号标识及未任命提示\n总管理员安全交付临时密码',tone='green')
f.a('appoint','另按 UC-07 保存专员任命\n任命有效后才能登录')
f.note('账号不等于资格\n创建账号不授予总管理员、专员或业务权限\n一员工至多一个后台账号，多公司任命复用\n任命失败时已创建账号保留，但没有新增资格')
f.note('保密\n响应、日志与审计不返回密码或哈希\n没有自由注册，也不经过 OA\n重复同内容请求返回原结果')

f=Flow('UC-09-密码修改','修改临时／本人密码','账号本人；UC-09 配套子流程，不新增正式用例编号')
cases[9].append(f)
f.a('access','提交有效会话、当前密码\n新密码及确认值')
f.d('valid','会话、身份、原密码与新密码合法？').b('valid',['不修改密码，提示问题\n会话失效需重新登录'])
f.a('save','保存新密码哈希并清除强制改密标记\n更新凭据版本，使全部旧会话失效')
f.d('stored','相关本地写入全部成功？').b('stored',['保存失败，不返回成功\n本次相关写入回滚'])
f.a('result','返回登录页，使用新密码重新登录',tone='green')
f.note('边界\n本人改密和首次／重置后改密沿用同一规则\n改密不授予或恢复后台任命及业务授权\n临时密码只允许改密和退出')

f=Flow('UC-09-密码重置','总管理员重置专员密码','总管理员；UC-09 配套子流程，不新增正式用例编号')
cases[9].append(f)
f.a('access','选择目标专员账号\n填写新临时密码与原因')
f.d('valid','总管理员资格及目标账号合法？').b('valid',['拒绝重置，专员不能重置他人密码'])
f.a('save','整体更新密码哈希、强制改密标记\n凭据版本与操作记录')
f.d('stored','全部保存成功？').b('stored',['本次写入回滚，不返回重置成功'])
f.a('result','全部旧会话失效\n安全交付临时密码，下次登录先改密',tone='green')
f.note('重置边界\n不查询原密码，不恢复停用账号\n不恢复失效任命，不改变业务角色\n唯一总管理员无法登录属于部署恢复流程')

f=case(10,'总管理员；HR 一期文件／模拟数据，不经过 OA')
f.a('access','选择来源、数据类型与权威工作表\n确认初始导入或明确人员变更模板')
f.d('auth','总管理员资格及文件格式合法？').b('auth',['拒绝本次导入，不执行文件宏'])
f.a('edit','核对员工来源标识、内部 ID\n组织编码、字段映射及明确变更动作')
f.a('precheck','预检依赖、组织循环、岗位归属\n重复、兼岗、身份映射及迁移限制')
f.d('valid','无阻断错误或映射歧义？').b('valid',['只返回问题报告，不写正式数据\n修正来源或映射后重新预检'],target='edit',tone='amber')
f.a('preview','预览新增／更新、任职变化与失效影响\n冻结批次内容与映射')
f.d('confirm','确认执行通过预检的批次？').b('confirm',['取消本次未提交导入'],tone='amber')
f.d('duplicate','该批次尚未成功执行？').b('duplicate',['同编号同内容返回原批次结果\n不同内容拒绝'],tone='amber')
f.a('recheck','复核总管理员资格、固定内容\n映射与目标数据版本')
f.d('fresh','资格及批次条件仍合法？').b('fresh',['拒绝陈旧执行\n资格失效停止，版本变化重新预检'])
f.a('update','同一事务按依赖更新组织／员工／任职\n按变更后的全部任职判定授权有效性')
f.a('invalidate','使不满足条件的授权／任命失效\n全局停用使全部业务授权及任命失效\n保留历史并更新变化依据')
f.a('save','同事务保存批次实际结果与成功审计')
f.d('stored','本次批次全部提交成功？').b('stored',['全部回滚，返回安全失败报告\n不显示部分导入成功'])
f.a('result','返回实际数量、批次编号和失效影响\nUC-05／06／09 使用最新有效状态',tone='green')
f.note('来源识别\n总表与公司分表是重复视图，不重复导入\n不以姓名／手机号识别员工\n组织更名不改变稳定 ID／编码\n用户名变化须明确旧新对应')
f.note('明确变更\n新增任职、结束任职、停用、恢复须明确\n文件缺行不推断离职或删除\n兼岗独立保存，不覆盖其他任职\n有历史引用的部门子树不能直接跨公司迁移')
f.note('持续任职条件\n公司级角色要求本公司有效任职\n部门级角色要求该部门直接有效任职\n岗位变化但条件仍满足时原授权保留\n有其他适用兼岗时不使该授权失效')
f.note('恢复不复活授权\n恢复员工仅恢复身份可用状态\n失效业务角色按 UC-03 重新经 OA 申请\n失效专员由总管理员按 UC-07 重新任命\n人员导入不自动授予新角色或创建后台账号')

f=case(11,'外部 OA；权限中心接收器与自动执行器')
f.a('access','接收 OA 最终结果及关联申请信息')
f.d('trusted','服务身份、参数与申请关联合法？').b('trusted',['拒绝接收／执行，不建立新授权'])
f.d('match','固定内容、版本及最终结果类型匹配？').b('match',['拒绝不匹配／草稿／非最终结果'])
f.d('consistent','与已记录最终结果不冲突？').b('consistent',['拒绝矛盾最终结果\n不能按后到结果反转历史'])
f.d('new','仍需接收或执行本次结果？').b('new',['已执行／已驳回的相同结果\n返回历史结果，不重复变更'],tone='green')
f.a('record','独立事务保存最终结果与接收记录\n批准保存为已批准待执行')
f.d('recorded','接收结果可靠保存成功？').b('recorded',['返回接收失败，不执行授权\nOA 可重送同一结果'])
f.d('approved','最终结果是批准？').b('approved',['保存为已驳回，业务数据不变\n修改内容需新方案重新申请'],tone='amber')
f.a('check','自动读取固定申请，复核原资格依据\n对应范围、目标条件、权限合法性及版本')
f.d('legal','全部目标仍合法且版本适用？').b('legal',['标记申请已失效，整批不执行\n需重新申请，不能强行重试'])
f.a('execute','在受并发控制的本地事务中\n按 UC-02／03／04 执行全部变更')
f.a('audit','同事务保存执行状态、目标版本\n与成功审计，全部成功才提交')
f.d('stored','业务与成功审计全部提交成功？').b('stored',['整体回滚，不显示已生效\n保留批准依据和失败／待恢复状态','故障恢复后服务端自动重试\n仍复核原依据、全部条件与版本'],target='check',tone='amber')
f.a('result','记录执行成功，返回接收及执行状态\n后续权限查询使用实际已提交数据',tone='green')
f.note('批准与执行分开\n接收结果先独立持久化，执行失败不丢批准依据\n同一批准可恢复处理，不要求重新审批\n普通专员不能将未批准方案标成批准')
f.note('原资格来源\n总管理员原申请按全局资格复核\n公司专员原申请按公司当前获准范围复核\n原申请人离任或公司暂无专员不单独阻止执行\n不要求人工接手，不提供任意取消已批准方案')
f.note('按申请类型执行\nUC-02：角色配置与权限功能归属、基础版本\nUC-03：员工适用任职及授权资格版本\nUC-04：准确关系与版本，不因调离拒绝历史清理\n任一批量项不合法则整批不执行')
f.note('防止旧授权复活\n角色扩大不能借旧批准授予新权限\n停用／任职失效后的旧授予不能恢复权限\n旧撤销不能误撤后续重新授予的新关系\n相同已结束结果幂等返回；冲突结果拒绝')

def make_xml(page):
    diagram=ET.Element('diagram',id=page['key'].lower(),name=page['key']+' '+page['title'])
    model=ET.SubElement(diagram,'mxGraphModel',dx=str(WIDTH),dy=str(page['height']),grid='1',gridSize='10',page='1',pageWidth=str(WIDTH),pageHeight=str(page['height']),adaptiveColors='auto',math='0',shadow='0')
    root=ET.SubElement(model,'root');ET.SubElement(root,'mxCell',id='0');ET.SubElement(root,'mxCell',id='1',parent='0')
    for n in page['nodes']:
        fill,stroke=COLORS[n['tone']]
        style=f'html=1;whiteSpace=wrap;fontFamily=Microsoft YaHei;fontSize=17;fontColor=#1e3449;align=center;verticalAlign=middle;spacing=10;fillColor={fill};strokeColor={stroke};strokeWidth=1.5;'
        if n['kind']=='decision':style='rhombus;'+style
        elif n['kind']=='terminal':style='ellipse;'+style
        elif n['kind']=='text':style='text;'+style+'fillColor=none;strokeColor=none;align=left;fontSize='+('28' if n['id']=='title' else '16')+';'
        elif n['kind']=='note':style='rounded=0;'+style+'align=left;fontSize=16;'
        else:style='rounded=1;arcSize=12;'+style
        c=ET.SubElement(root,'mxCell',id=n['id'],value=html.escape(n['label']).replace('\n','<br>'),style=style,vertex='1',parent='1')
        ET.SubElement(c,'mxGeometry',x=str(n['x']),y=str(n['y']),width=str(n['w']),height=str(n['h']),attrib={'as':'geometry'})
    for i,e in enumerate(page['edges']):
        c=ET.SubElement(root,'mxCell',id='e'+str(i),source=e['a'],target=e['b'],value=e['label'],edge='1',parent='1',style='edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=block;endFill=1;strokeColor=#53667b;strokeWidth=1.6;fontSize=15;fontFamily=Microsoft YaHei;labelBackgroundColor=#ffffff;')
        ET.SubElement(c,'mxGeometry',relative='1',attrib={'as':'geometry'})
    return diagram

def preview(page,path):
    im=Image.new('RGB',(page['width'],page['height']),'white');d=ImageDraw.Draw(im)
    lookup={n['id']:n for n in page['nodes']}
    for e in page['edges']:
        a,b=lookup[e['a']],lookup[e['b']]
        ax,ay=a['x']+a['w']/2,a['y']+a['h']/2
        bx,by=b['x']+b['w']/2,b['y']+b['h']/2
        if e['loop']:
            pts=[(a['x'],ay),(25,ay),(25,by),(b['x'],by)]
        elif ax==bx:
            pts=[(ax,a['y']+a['h']),(bx,b['y'])]
        else:
            pts=[(a['x'],ay),(b['x']+b['w'],ay),(b['x']+b['w'],by)]
            if pts[-1]==pts[-2]:pts.pop()
        d.line(pts,fill='#53667b',width=2)
        (px,py),(qx,qy)=pts[-2:]
        if qx>px:arrow=[(qx,qy),(qx-10,qy-5),(qx-10,qy+5)]
        elif qx<px:arrow=[(qx,qy),(qx+10,qy-5),(qx+10,qy+5)]
        elif qy>py:arrow=[(qx,qy),(qx-5,qy-10),(qx+5,qy-10)]
        else:arrow=[(qx,qy),(qx-5,qy+10),(qx+5,qy+10)]
        d.polygon(arrow,fill='#53667b')
        if e['label']:
            font=ImageFont.truetype(FONT_PATH,16)
            x=(pts[0][0]+pts[1][0])/2+5;y=(pts[0][1]+pts[1][1])/2-22
            if e['loop']:x=34;y=by-32
            d.rectangle((x-2,y-2,x+font.getlength(e['label'])+4,y+23),fill='white')
            d.text((x,y),e['label'],font=font,fill='#53667b')
    for n in page['nodes']:
        x,y,w,h=n['x'],n['y'],n['w'],n['h'];fill,stroke=COLORS[n['tone']]
        if n['kind']=='decision':d.polygon([(x+w/2,y),(x+w,y+h/2),(x+w/2,y+h),(x,y+h/2)],fill=fill,outline=stroke,width=2)
        elif n['kind']=='terminal':d.ellipse((x,y,x+w,y+h),fill=fill,outline=stroke,width=2)
        elif n['kind']=='action':d.rounded_rectangle((x,y,x+w,y+h),radius=10,fill=fill,outline=stroke,width=2)
        elif n['kind']=='note':d.rectangle((x,y,x+w,y+h),fill=fill,outline=stroke,width=1)
        size=28 if n['id']=='title' else 16 if n['kind'] in ('text','note') else 17
        font=ImageFont.truetype(FONT_PATH,size)
        lines=wrap(n['label'],font,w-36 if n['kind']!='decision' else w-90)
        lineheight=size+8;top=y+(h-len(lines)*lineheight)/2
        assert len(lines)*lineheight<=h,(page['key'],n['id'],'text overflow')
        for i,line in enumerate(lines):
            xx=x+16 if n['kind'] in ('text','note') else x+(w-font.getlength(line))/2
            d.text((xx,top+i*lineheight),line,font=font,fill='#1e3449')
    im.save(path)

def container():return ET.Element('mxfile',host='app.diagrams.net',agent='drawio official skill XML workflow',modified='2026-10-15T00:00:00.000Z')
def write_xml(root,path):ET.indent(root,space='  ');ET.ElementTree(root).write(path,encoding='utf-8',xml_declaration=True)

combined=container()
uc01=OUT/'UC-01_维护业务系统与权限目录_流程图.drawio'
assert uc01.exists(),'Keep existing UC-01; generate it first only if absent'
for diagram in ET.parse(uc01).getroot().findall('diagram'):combined.append(copy.deepcopy(diagram))
pages=[]
for n,flows in cases.items():
    mx=container()
    for flow in flows:
        page=flow.build();diagram=make_xml(page);mx.append(diagram);combined.append(copy.deepcopy(diagram));pages.append(page)
        preview(page,OUT/(flow.key+'_流程图预览.png'))
    title=flows[0].title
    write_xml(mx,OUT/(f'UC-{n:02d}_{title}_流程图.drawio'))
write_xml(combined,OUT/'权限管理系统_全部流程图.drawio')

# Structural validation of every delivered diagram, including the retained UC-01.
for p in OUT.glob('*.drawio'):
    text=p.read_text(encoding='utf-8');assert '<!--' not in text
    t=ET.fromstring(text)
    for diagram in t.findall('diagram'):
        cells=diagram.findall('./mxGraphModel/root/mxCell');ids=[c.get('id') for c in cells]
        assert len(ids)==len(set(ids)),(p,diagram.get('name'))
        vertices={c.get('id') for c in cells if c.get('vertex')=='1' and not c.get('id','').startswith(('note','rule','title','subtitle','footer'))}
        connected=set()
        for c in cells:
            if c.get('edge')=='1':
                assert c.get('source') in ids and c.get('target') in ids and c.find('mxGeometry') is not None
                connected.update((c.get('source'),c.get('target')))
        assert vertices<=connected,(p,'unconnected flow nodes',vertices-connected)

cards=[('UC-01','维护业务系统与权限目录','UC-01_流程图预览.png')]+[(p['key'],p['title'],p['key']+'_流程图预览.png') for p in pages]
index=['<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>权限管理系统全部流程图</title>',
       '<style>body{font:16px "Microsoft YaHei",sans-serif;margin:0;background:#f2f4f7;color:#1e3449}header{padding:24px;background:white}nav{display:flex;flex-wrap:wrap;gap:12px}main{max-width:1420px;margin:auto}section{margin:28px 16px;padding:20px;background:white}img{width:100%;height:auto}a{color:#32649a}</style>',
       '<header><h1>权限管理系统全部流程图</h1><p>2026-10-15 · 最终用例基线 · 图片为本地辅助预览，编辑以 drawio 为准。</p><nav>']
index.extend(f'<a href="#{key}">{key}</a>' for key,title,img in cards);index.append('</nav></header><main>')
for key,title,img in cards:index.append(f'<section id="{key}"><h2>{html.escape(key+" "+title)}</h2><img loading="lazy" src="{html.escape(img)}" alt="{html.escape(title)}"></section>')
index.append('</main></html>');(OUT/'全部流程图预览.html').write_text('\n'.join(index),encoding='utf-8')
print(json.dumps({'use_cases':11,'pages':len(combined.findall('diagram')),'individual_drawio_files':11,'combined':str(OUT/'权限管理系统_全部流程图.drawio'),'validation':'XML, IDs, edge references, connected flow nodes and preview text bounds passed'},ensure_ascii=False))
