"""Editable analysis-level UML sequence diagrams, official drawio XML workflow.
No database column types, API routes, Java classes, or external OA internals implied.
PNG previews are local auxiliary renders of the same message model.
"""
import sys
GENERATE_PREVIEWS = '--previews' in sys.argv

from pathlib import Path
import copy,html,json,re
import xml.etree.ElementTree as ET
from PIL import Image,ImageDraw,ImageFont

OUT=Path(__file__).resolve().parent
SOURCE=OUT.parent/'usecases-v1'
FONT_PATH='C:/Windows/Fonts/msyh.ttc'
WIDTH=1420

def M(a,b,label,kind='call'):return dict(type='message',a=a,b=b,label=label,kind=kind)
def S(a,label):return M(a,a,label,'self')
def R(a,b,label):return M(a,b,label,'return')
def A(a,b,label):return M(a,b,label,'async')
def F(kind,*branches):return dict(type='fragment',kind=kind,branches=branches)
def N(text):return dict(type='note',label=text)
def REF(text):return dict(type='ref',label=text)

cases={}
def case(n,actors,events,notes=(),key=None,title=None):
    p=next(SOURCE.glob(f'UC-{n:02d}_*.md'))
    name=p.read_text(encoding='utf-8').splitlines()[0].split(' ',2)[2]
    c=dict(n=n,key=key or f'UC-{n:02d}',title=title or name,actors=actors,events=events,notes=list(notes),source=p.name)
    cases.setdefault(n,[]).append(c);return c

ADMIN=[('admin','管理员'),('center','权限中心')]
OA=[('admin','总管理员／权限管理员'),('center','权限中心'),('oa','外部 OA 系统')]
BUSINESS=[('business','业务系统后端'),('center','权限中心')]

case(1,[('admin','权限中心总管理员'),('center','权限中心')],[
 M('admin','center','请求目录维护（目标系统）'),S('center','校验总管理员资格，读取当前目录与版本'),
 R('center','admin','返回系统、目录及功能操作'),
 M('admin','center','提交预览（目录变更、可见条件、操作归属）'),
 S('center','校验编码、归属、层级及引用；计算变更影响'),
 F('alt',('[预检不合法]',[R('center','admin','返回问题字段与原因，修正后重新预览')]),('[预检通过]',[R('center','admin','返回差异及停用／恢复影响')])),
 F('opt',('[预检通过且管理员确认保存]',[
 M('admin','center','确认保存（固定变更、基础版本、原因、请求编号）'),S('center','复核资格、版本、全部目录规则'),
 F('alt',('[复核或写入失败]',[S('center','拒绝保存或整体回滚，原目录保持不变'),R('center','admin','返回规则／冲突／服务错误，不显示成功')]),('[全部成功]',[S('center','整体保存目录、版本和成功审计'),R('center','admin','返回保存结果及当前启停状态')]))
 ]))],['直接维护目录不经过 OA；新增目录不自动授予角色或员工权限。','SYSTEM_ACCESS 每系统唯一，不配置数据范围；目录启用不恢复已失效／撤销授权。'])

case(2,OA,[
 M('admin','center','进入角色配置（公司、组织、系统、角色）'),S('center','校验全局／公司申请资格及对应范围'),
 F('opt',('[需要新建角色]',[M('admin','center','保存角色草稿（名称、唯一编码、组织、系统）'),S('center','校验并保存未生效角色草稿及创建审计'),R('center','admin','返回角色／草稿编号及版本，提示不可分配')])),
 R('center','admin','返回当前配置与本系统可配置权限'),
 M('admin','center','预览配置（功能权限变更、原因）'),S('center','校验权限状态、同系统归属、非空配置及版本'),
 R('center','admin','返回差异及受影响员工摘要'),
 M('admin','center','确认提交（角色、固定方案、版本、请求编号）'),
 S('center','复核资格与单一未结束方案，保存固定内容及原资格来源'),
 F('alt',('[不合法／无变化／保存失败]',[R('center','admin','返回拒绝／无变化／服务错误，不新增审批')]),('[提交成功]',[
 M('center','oa','提交或关联审批（申请编号、固定内容、版本、摘要）'),
 R('oa','center','返回外部审批关联信息'),R('center','admin','返回 OA 投递、审批及执行状态，旧配置不变'),
 N('外部审批期间：不展开 OA 内部流转；管理员无需一直等待页面返回'),
 M('oa','center','后续最终结果回调（申请／审批编号、结果、内容依据）'),REF('ref UC-11：验证结果、记录状态；批准后复核并自动执行'),
 R('center','oa','返回接收／执行状态'),M('admin','center','查询方案状态（申请编号）'),R('center','admin','返回驳回／待执行／失败／执行成功等实际状态')
 ]))],['临时网络／业务故障不能把未生效配置展示成有效；OA 发送或关联未完成时不得声称已取得审批关联。','原申请人离任不单独阻止合法固定方案执行；驳回后以新方案重提。'])

case(3,OA,[
 M('admin','center','查询可分配员工及角色（当前公司、系统）'),S('center','校验管理范围、员工任职和角色可分配性'),
 R('center','admin','返回员工任职、已有授权及范围内角色'),
 M('admin','center','预览新增（员工、角色、来源部门、周期与原因）'),S('center','逐条检查范围、来源部门及周期，仅排除同来源有效重复'),
 R('center','admin','返回实际新增清单、角色权限摘要及版本'),
 M('admin','center','确认提交（新增清单、角色／关系版本、请求编号）'),
 F('alt',('[越权／条件不符／无变化／保存失败]',[R('center','admin','整批拒绝／无变化／服务错误，原授权保留')]),('[提交成功]',[
 S('center','保存固定配对、原资格来源、版本及提交记录'),M('center','oa','提交／关联固定分配方案'),R('oa','center','返回审批关联信息'),
 R('center','admin','返回 OA 投递/审批状态；尚未新增授权'),N('审批结束后独立交付结果；原有授权按当前有效性规则计算'),
 M('oa','center','后续回调：交付固定分配方案最终结果'),REF('ref UC-11：批准后复核适用任职、角色／授权版本；整体新增'),
 R('center','oa','返回实际处理状态'),M('admin','center','查询分配结果'),R('center','admin','返回实际生效或未生效状态')
 ]))],['部门角色只适用直接任职；公司级角色要求本公司任职，岗位不自动绑定角色。','已失效历史不阻止重新申请；新授权不能被旧批准或旧撤销回调改变。'])

case(4,OA,[
 M('admin','center','查询可撤销关系（员工、来源公司、系统）'),S('center','按当前撤权范围读取指定关联与历史'),
 R('center','admin','返回关系标识、来源、状态及版本'),
 M('admin','center','预览撤销（明确关系列表、原因）'),S('center','排除已撤销／失效的无变化项，计算可见范围内影响'),
 R('center','admin','返回实际撤销清单及其他来源可能保留权限的提示'),
 M('admin','center','确认提交（关系、版本、来源、请求编号）'),
 F('alt',('[不合法／无变化／提交失败]',[R('center','admin','返回拒绝／无变化／服务错误，原授权不变')]),('[提交成功]',[
 S('center','保存固定撤销清单、原资格来源及提交记录'),M('center','oa','提交／关联固定撤销方案'),R('oa','center','返回审批关联信息'),R('center','admin','返回 OA 投递/审批状态；尚未撤销关系'),
 N('等待期间其他合法变更仍可能使原授权失效'),M('oa','center','后续回调：交付撤销方案最终结果'),REF('ref UC-11：批准后复核关系与版本，整体撤销指定关联'),
 R('center','oa','返回接收／执行状态'),M('admin','center','查询撤销结果'),R('center','admin','返回实际撤销状态及可披露结果')
 ]))],['撤销不删除角色，不停用员工，不影响其他角色仍提供的重合权限。','失效历史只读，不再次 OA；仍有效的指定来源可一般撤销。离岗关权不经 OA。'])

case(5,BUSINESS,[
 M('business','center','查询有效权限（服务身份、员工、当前公司、系统）'),
 S('center','校验参数、服务身份、系统绑定及员工／公司任职'),
 F('alt',('[调用不合法]',[R('center','business','返回参数／认证／系统调用错误')]),('[可进行授权计算]',[
 S('center','一致读取该公司、系统的有效角色与进入资格'),
 F('alt',('[员工不可用／无当前公司任职／无进入资格]',[R('center','business','canAccessSystem=false；菜单与操作为空')]),('[具有进入资格]',[
 S('center','合并功能操作并去重；生成菜单及必要导航祖先'),
 R('center','business','canAccessSystem=true；菜单及功能操作集合'),S('business','映射业务入口；执行功能访问拦截')
 ]))
 ]))],['有进入资格但无功能授权时，返回 true 和空菜单／操作。编辑不自动包含查看。','所有读取／计算故障返回服务错误，不能伪装成功空权限；权限只代表本次读取状态。'])

case(6,BUSINESS,[
 M('business','center','检查权限（服务身份、员工、当前公司、系统、权限编码）'),
 S('center','校验调用身份与系统绑定；检查员工／公司任职与目标权限'),
 F('alt',('[调用不合法]',[R('center','business','返回调用错误；不能据此放行')]),('[合法调用]',[
 F('alt',('[员工／任职／权限无效]',[R('center','business','hasPermission=false')]),('[对象有效]',[
 F('alt',('[检查 SYSTEM_ACCESS]',[S('center','直接判断该公司、系统进入来源'),R('center','business','返回 hasPermission')]),('[检查功能操作]',[
 S('center','先检查同公司、同系统进入资格，再检查操作有效来源'),
 F('alt',('[缺少进入资格或操作来源]',[R('center','business','hasPermission=false')]),('[均满足]',[S('center','检查是否存在该操作的有效授权来源'),R('center','business','hasPermission=true')]))
 ]))
 ]))
 ])),S('business','按结果拦截对应功能；执行自身业务校验')],['权限中心只返回有无功能权限，不读取业务记录，不返回数据范围。','认证／服务故障与正常无权限分开；不能故障默认放行。'])

case(7,[('admin','权限中心总管理员'),('center','权限中心')],[
 M('admin','center','读取范围配置（公司、管理部门）'),S('center','校验总管理员资格，读取名单、指定部门及系统范围及版本'),
 R('center','admin','返回当前权限管理员、系统范围与候选人员'),
 F('opt',('[拟新增权限管理员没有后台账号]',[REF('ref UC-09 账号创建子流程：创建账号不自动任命')])),
 M('admin','center','预览配置（公司、管理部门、系统集合、最终名单、原因）'),R('center','admin','返回任免、系统增减与待处理申请影响'),
 M('admin','center','确认保存（最终配置、基础版本、请求编号）'),S('center','复核总管理员、部门边界、任职、账号、系统及版本'),
 F('alt',('[条件不合法或版本冲突]',[R('center','admin','拒绝保存，重新读取并修正')]),('[条件合法]',[
 S('center','整体保存任命、范围、版本和审计'),
 F('alt',('[写入失败]',[R('center','admin','返回服务错误，原名单与范围保留')]),('[全部提交成功]',[R('center','admin','返回直接生效的新名单与获准部门及系统范围')]))
 ]))],['不经过 OA，不逐个指定角色；同范围管理员共享范围内角色管理。','任命不授予外部业务权限；员工停用使任命失效，恢复后由总管理员重新任命。'])

case(8,ADMIN,[
 M('admin','center','查询审计（时间、公司、系统、目标、事件、分页）'),S('center','校验当前管理资格，强制附加最新范围'),
 F('alt',('[参数错误或越范围]',[R('center','admin','返回错误，不泄露越范围内容')]),('[条件合法]',[
 S('center','分页读取安全摘要及同范围总数'),R('center','admin','返回摘要列表；无匹配时正常空列表')
 ])),
 F('opt',('[选择列表中的某条记录]',[
 M('admin','center','查询详情与关联事件（记录标识）'),S('center','重新校验当前资格和记录来源范围'),
 F('alt',('[不可访问／范围已缩小]',[R('center','admin','统一不可访问／未找到响应')]),('[允许访问]',[R('center','admin','返回历史快照、差异及审批／实际执行状态')]))
 ]))],['只读，不删除、修改或重放历史；记录曾在列表出现不代表永久可访问。','数据库故障返回服务错误；不返回密码、服务密钥、完整个人资料或异常堆栈。'])

case(9,ADMIN,[
 M('admin','center','登录（本平台账号、密码）'),S('center','校验参数，验证密码摘要、身份及当前管理资格'),
 F('alt',('[账号／密码／资格无效]',[R('center','admin','统一登录失败，不泄露账号是否存在')]),('[身份有效]',[
 F('alt',('[使用临时／重置密码]',[R('center','admin','返回受限会话，只允许改密或退出'),REF('ref UC-09 密码修改：保存新密码，旧会话失效，重新登录')]),('[可正常登录]',[
 S('center','建立正常会话并记录必要登录结果'),R('center','admin','返回当前身份与公司／系统管理范围'),
 F('loop',('[使用后台期间，每次管理请求]',[
 M('admin','center','请求管理操作（目标公司／系统及业务内容）'),S('center','重新检查账号、当前任命与目标范围'),R('center','admin','返回获准操作结果或当前资格错误')
 ])),M('admin','center','退出（当前会话）'),S('center','使当前会话不可继续使用'),R('center','admin','返回已退出，客户端清理会话状态')
 ]))
 ]))],['仅本平台后台身份；成功登录不授予外部业务系统权限。服务故障不能假称密码错误。','身份恢复不恢复旧任命；改密／重置使所有旧会话失效，普通退出仅结束当前会话。'])

case(9,[('admin','权限中心总管理员'),('center','权限中心')],[
 M('admin','center','查询目标员工是否已有账号（员工稳定标识）'),S('center','校验总管理员与有效员工，检查唯一关联'),
 F('alt',('[已有账号]',[R('center','admin','返回可复用账号，不重复创建或改绑')]),('[尚无账号]',[
 M('admin','center','创建账号（登录名、临时密码、确认值、原因、请求编号）'),S('center','复核唯一性、密码规则及总管理员资格'),
 F('alt',('[不合法／写入失败]',[R('center','admin','返回拒绝／服务错误，不产生半完成账号')]),('[全部成功]',[
 S('center','整体保存账号、员工关联、哈希、强制改密标记与审计'),R('center','admin','返回账号标识与未任命提示'),S('admin','安全交付临时密码，不写入公开资料'),REF('ref UC-07：另行保存权限管理员任命')
 ]))
 ]))],['创建账号不授予总管理员、权限管理员资格或业务权限；任命失败不删除已创建账号。'],key='UC-09-账号创建',title='创建权限管理员后台账号（UC-09 配套）')

case(9,ADMIN,[
 M('admin','center','修改密码（当前会话、原密码、新密码、确认值）'),S('center','检查会话、身份、原密码和新密码规则'),
 F('alt',('[校验失败]',[R('center','admin','返回适当错误，不修改密码')]),('[校验通过]',[
 S('center','保存新密码哈希，清除强制改密标记，更新凭据版本'),
 F('alt',('[写入失败]',[R('center','admin','返回服务错误，相关本地写入回滚')]),('[成功]',[S('center','使该账号全部旧会话失效'),R('center','admin','返回修改成功，要求重新登录')]))
 ]))],['临时密码修改与普通本人改密沿用同一规则；改密不授予或恢复角色／任命。'],key='UC-09-密码修改',title='修改临时／本人密码（UC-09 配套）')

case(9,[('admin','权限中心总管理员'),('center','权限中心')],[
 M('admin','center','重置权限管理员密码（目标账号、新临时密码、原因）'),S('center','检查总管理员资格及目标账号，校验新密码'),
 F('alt',('[无资格／不合法／写入失败]',[R('center','admin','拒绝或返回服务错误，不返回成功')]),('[全部成功]',[
 S('center','整体更新哈希、强制改密标记、凭据版本与审计'),S('center','使目标账号全部旧会话失效'),R('center','admin','返回重置成功，提示安全交付临时密码')
 ]))],['不查询原密码，不恢复停用账号或失效任命；下次登录强制改密，不经过 OA。'],key='UC-09-密码重置',title='重置权限管理员密码（UC-09 配套）')

case(10,[('admin','权限中心总管理员'),('center','权限中心')],[
 M('admin','center','预检资料（文件／数据、类型、来源、映射、明确变更动作）'),
 S('center','校验管理员、格式、稳定身份、组织依赖、重复及迁移限制'),
 F('alt',('[预检有阻断问题]',[R('center','admin','返回问题行与原因；不写正式数据')]),('[预检通过]',[R('center','admin','返回批次、冻结内容、计划变更与失效影响')])),
 F('opt',('[确认执行通过预检的批次]',[
 M('admin','center','执行导入（批次标识、固定内容及版本依据）'),
 F('alt',('[同内容批次已成功执行]',[R('center','admin','返回原批次结果，不重复导入')]),('[尚未执行]',[
 S('center','复核资格、内容、映射及目标版本'),
 F('alt',('[复核或写入失败]',[R('center','admin','拒绝／整体回滚；版本变化须重新预检')]),('[全部合法且提交成功]',[
 S('center','同事务按冻结动作顺序更新资料；离岗周期与授权／任命失效持久保存'),S('center','同事务保存批次结果与成功审计'),R('center','admin','返回实际数量、批次及失效结果')
 ]))
 ]))
 ]))],['HR 一期通过文件／模拟数据提供资料，不设计真实 HR 查询接口；文件作为数据，不执行宏。','仍有适用兼岗则保留；明确结束最后任职后同批再就职不复权。员工停用使全部授权及任命失效，恢复不复权。','导入不自动建后台账号或授予业务角色；缺行不代表离职，总表与分表不得重复追加。'])

case(11,[('oa','外部 OA 系统'),('center','权限中心')],[
 M('oa','center','最终结果回调（服务身份、申请／审批编号、结果、内容依据）'),
 S('center','校验来源、正式申请关联、固定内容及最终结果类型'),
 F('alt',('[不合法或矛盾最终结果]',[R('center','oa','拒绝，不按后到结果反转历史')]),('[匹配的合法结果]',[
 F('alt',('[相同结果已执行／已驳回]',[R('center','oa','返回历史结果，不重复变更')]),('[仍需处理]',[
 S('center','独立事务可靠保存接收记录及批准／驳回状态'),
 F('alt',('[接收写入失败]',[R('center','oa','返回接收失败，不执行授权；可重送同一结果')]),('[已可靠保存]',[
 F('alt',('[最终驳回]',[R('center','oa','返回已驳回，业务配置与授权不因本结果改变')]),('[最终批准]',[
 REF('ref UC-11 自动执行子流程：复核原依据与目标版本，整体执行或恢复重试'),R('center','oa','返回接收与实际执行状态，不混为一个成功')
 ]))
 ]))
 ]))
 ]))],['OA 的内部审批人、会签、转交等不在本项目实现；非最终结果不能执行。','批准状态与业务执行独立落地。接收响应可表明待执行，不能将它当作权限已经生效。'])

case(11,[('worker','自动执行器\n（权限中心内部）'),('center','权限中心执行处理')],[
 M('worker','center','处理已批准／可重试方案（申请编号）'),S('center','读取固定申请及提交时原资格来源'),
 S('center','复核对应范围、目标条件、功能归属、角色／授权版本'),
 F('alt',('[实际条件或版本已不合法]',[S('center','标记申请失效，保留原因，整批不执行'),R('center','worker','返回已失效；需新申请，不能强行重试')]),('[全部合法]',[
 S('center','在受并发控制的本地事务中执行 UC-02／03／04 全部变更'),
 S('center','同事务保存目标版本、执行状态及成功审计'),
 F('alt',('[业务／成功审计写入失败]',[S('center','整体回滚；独立记录失败或保留已批准待执行依据'),R('center','worker','返回失败／待恢复，不显示生效'),
 F('loop',('[短暂故障恢复后；原方案仍合法]',[M('worker','center','自动重试同一固定方案；重新进行全部复核'),R('center','worker','返回实际成功／仍待恢复／已失效状态')]))]),('[全部提交成功]',[
 S('center','标记实际执行成功，重复结果关联原成功记录'),R('center','worker','返回已执行及实际变更结果')]))
 ]))],['自动执行器为权限中心内部职责，不是新增外部系统；只有批准或可重试方案进入此流程。','总管理员原申请按全局资格，权限管理员原申请按当前公司、部门及系统范围。申请人离任／公司无权限管理员不单独阻止执行。','UC-03 必须检查员工任职及失效后的版本；UC-04 检查具体关系，仍有效的来源可一般撤销，失效历史只读。'],key='UC-11-自动执行',title='批准后自动执行与失败恢复（UC-11 配套）')

def wrap(text,font,width):
    out=[]
    for paragraph in text.split('\n'):
        line=''
        for ch in paragraph:
            if line and font.getlength(line+ch)>width:out.append(line);line=ch
            else:line+=ch
        out.append(line)
    return out

def layout(c):
    positions={k:(240 if len(c['actors'])==2 else 180)+i*(650 if len(c['actors'])==2 else 520) for i,(k,label) in enumerate(c['actors'])}
    rows=[];frames=[];messages=[];y=275;counter=0
    font=ImageFont.truetype(FONT_PATH,16)
    def events(items,depth=0):
        nonlocal y,counter
        for e in items:
            if e['type']=='message':
                counter+=1;v=dict(e,y=y,index=counter)
                a,b=positions[e['a']],positions[e['b']]
                width=410 if a==b else abs(a-b)-55
                v['lines']=wrap(str(counter)+'. '+e['label'],font,width)
                v['labelwidth']=width
                v['labelheight']=len(v['lines'])*24
                # Reserve enough vertical room for multi-line message labels and self calls.
                v['y']=y+v['labelheight']+8
                messages.append(v);rows.append(v)
                y=v['y']+(65 if a==b else 50)
            elif e['type']=='fragment':
                top=y;y+=70;branches=[]
                for i,(guard,body) in enumerate(e['branches']):
                    if i:y+=32
                    label_y=y;guardlines=wrap(guard,font,WIDTH-170-depth*32)
                    y+=len(guardlines)*24+25
                    branches.append(dict(label=guard,lines=guardlines,y=label_y,divider=label_y-18 if i else None))
                    events(body,depth+1);y+=15
                frames.append(dict(kind=e['kind'],top=top,bottom=y,depth=depth,branches=branches));y+=35
            else:
                lines=wrap(e['label'],font,WIDTH-220-depth*32)
                height=max(60,len(lines)*25+24)
                rows.append(dict(e,y=y,h=height,lines=lines,depth=depth));y+=height+35
    events(c['events'])
    bottom=y+10
    notes=[]
    for text in c['notes']:
        lines=wrap(text,font,WIDTH-140);h=len(lines)*25+25
        notes.append(dict(text=text,lines=lines,y=y+40,h=h));y+=h+20
    height=y+145
    return dict(c=c,positions=positions,rows=rows,frames=frames,messages=messages,bottom=bottom,notes=notes,height=int(height))

def xml_page(p):
    c=p['c'];diagram=ET.Element('diagram',id=c['key'].lower(),name=c['key']+' '+c['title'])
    model=ET.SubElement(diagram,'mxGraphModel',dx=str(WIDTH),dy=str(p['height']),page='1',pageWidth=str(WIDTH),pageHeight=str(p['height']),grid='1',gridSize='10',adaptiveColors='auto')
    root=ET.SubElement(model,'root');ET.SubElement(root,'mxCell',id='0');ET.SubElement(root,'mxCell',id='1',parent='0')
    def node(id,text,x,y,w,h,style,parent='1'):
        cell=ET.SubElement(root,'mxCell',id=id,value=html.escape(text).replace('\n','<br>'),style='html=1;fontFamily=Microsoft YaHei;fontSize=16;fontColor=#23384f;'+style,vertex='1',parent=parent)
        ET.SubElement(cell,'mxGeometry',x=str(x),y=str(y),width=str(w),height=str(h),attrib={'as':'geometry'})
    node('frame','sd '+c['key'],30,135,WIDTH-60,p['bottom']-115,'shape=umlFrame;whiteSpace=wrap;align=left;verticalAlign=top;fillColor=none;strokeColor=#60748b;container=0;pointerEvents=0;')
    node('title',c['key']+' '+c['title'],40,20,WIDTH-80,60,'text;align=left;fontSize=26;fillColor=none;strokeColor=none;')
    node('subtitle','需求分析时序图 · 2026-10-15 · 中文业务消息；不固定数据库字段类型或 API 地址',40,87,WIDTH-80,40,'text;align=left;fillColor=none;strokeColor=none;')
    for key,label in c['actors']:
        node(key,label,p['positions'][key]-105,180,210,p['bottom']-180,'shape=umlLifeline;perimeter=lifelinePerimeter;size=60;align=center;verticalAlign=top;spacingTop=15;whiteSpace=wrap;fillColor=#eef5ff;strokeColor=#526b86;strokeWidth=1.5;')
    for i,f in enumerate(sorted(p['frames'],key=lambda x:x['depth'])):
        x=55+f['depth']*18;w=WIDTH-110-f['depth']*36
        node('fragment'+str(i),f['kind'],x,f['top'],w,f['bottom']-f['top'],'shape=umlFrame;align=left;verticalAlign=top;fillColor=none;strokeColor=#8191a4;container=0;pointerEvents=0;')
        for j,b in enumerate(f['branches']):
            node(f'guard{i}_{j}',b['label'],x+18,b['y'],w-36,len(b['lines'])*24+5,'text;align=left;fillColor=none;strokeColor=none;fontStyle=1;')
            if b['divider'] is not None:
                e=ET.SubElement(root,'mxCell',id=f'divider{i}_{j}',edge='1',parent='1',style='html=1;endArrow=none;startArrow=none;dashed=1;strokeColor=#8191a4;')
                g=ET.SubElement(e,'mxGeometry',relative='1',attrib={'as':'geometry'})
                ET.SubElement(g,'mxPoint',x=str(x),y=str(b['divider']),attrib={'as':'sourcePoint'})
                ET.SubElement(g,'mxPoint',x=str(x+w),y=str(b['divider']),attrib={'as':'targetPoint'})
    for i,row in enumerate(p['rows']):
        if row['type']!='message':
            x=100+row['depth']*18
            node('annotation'+str(i),row['label'],x,row['y'],WIDTH-200-row['depth']*36,row['h'],'shape=umlFrame;' if row['type']=='ref' else 'rounded=0;whiteSpace=wrap;spacing=10;fillColor=#fff8e8;strokeColor=#c4a34f;')
            continue
        a,b=p['positions'][row['a']],p['positions'][row['b']];yy=row['y'];selfcall=a==b
        offset=40 if selfcall else 0
        lifeh=p['bottom']-180
        style='html=1;edgeStyle='+('orthogonalEdgeStyle' if selfcall else 'none')+';rounded=0;strokeColor=#334d68;strokeWidth=1.5;endSize=7;'
        style+='endArrow=open;endFill=0;' if row['kind'] in ('return','async') else 'endArrow=block;endFill=1;'
        if row['kind']=='return':style+='dashed=1;'
        # Explicit time connection is geometrically essential for a UML lifeline.
        style+=f'exitX=0.5;entryX=0.5;exitY={(yy-180)/lifeh};entryY={(yy+offset-180)/lifeh};exitPerimeter=0;entryPerimeter=0;jettySize=35;'
        cell=ET.SubElement(root,'mxCell',id='message'+str(i),source=row['a'],target=row['b'],edge='1',parent='1',style=style)
        ET.SubElement(cell,'mxGeometry',relative='1',attrib={'as':'geometry'})
        lx=a+25 if selfcall else min(a,b)+25
        node('label'+str(i),'\n'.join(row['lines']),lx,yy-row['labelheight']-7,row['labelwidth'],row['labelheight'],'text;align='+('left' if selfcall else 'center')+';verticalAlign=middle;fillColor=#ffffff;strokeColor=none;')
    for i,n in enumerate(p['notes']):node('note'+str(i),n['text'],55,n['y'],WIDTH-110,n['h'],'rounded=0;whiteSpace=wrap;align=left;spacing=12;fillColor=#fff8e8;strokeColor=#c4a34f;')
    node('legend','实线实心箭头：请求／处理消息；虚线空心箭头：返回。alt 互斥，opt 可选，loop 重复，ref 引用。OA 回调为后续独立请求，不展开实现级激活条跨度。',40,p['height']-90,WIDTH-80,65,'text;align=left;whiteSpace=wrap;fillColor=none;strokeColor=none;fontSize=14;')
    return diagram

def render(p,file):
    im=Image.new('RGB',(WIDTH,p['height']),'white');d=ImageDraw.Draw(im)
    f=ImageFont.truetype(FONT_PATH,16);title=ImageFont.truetype(FONT_PATH,26)
    d.text((40,24),p['c']['key']+' '+p['c']['title'],font=title,fill='#23384f')
    d.text((40,90),'需求分析时序图 · 2026-10-15 · 中文业务消息；不固定数据库字段类型或 API 地址',font=f,fill='#526b86')
    d.rectangle((30,135,WIDTH-30,p['bottom']+20),outline='#60748b',width=2);d.text((42,140),'sd '+p['c']['key'],font=f,fill='#23384f')
    for key,label in p['c']['actors']:
        x=p['positions'][key]
        for y in range(240,int(p['bottom']),13):d.line((x,y,x,min(y+7,p['bottom'])),fill='#8191a4',width=1)
        d.rectangle((x-105,180,x+105,240),fill='#eef5ff',outline='#526b86',width=2)
        lines=wrap(label,f,190)
        for j,line in enumerate(lines):d.text((x-f.getlength(line)/2,190+j*24),line,font=f,fill='#23384f')
    for frag in sorted(p['frames'],key=lambda x:x['depth']):
        x=55+frag['depth']*18;right=WIDTH-x
        d.rectangle((x,frag['top'],right,frag['bottom']),outline='#8191a4',width=1)
        d.polygon([(x,frag['top']),(x+65,frag['top']),(x+65,frag['top']+20),(x+52,frag['top']+33),(x,frag['top']+33)],fill='white',outline='#8191a4')
        d.text((x+10,frag['top']+5),frag['kind'],font=f,fill='#23384f')
        for b in frag['branches']:
            if b['divider'] is not None:
                for xx in range(int(x),int(right),13):d.line((xx,b['divider'],min(xx+7,right),b['divider']),fill='#8191a4')
            for j,line in enumerate(b['lines']):d.text((x+18,b['y']+j*24),line,font=f,fill='#23384f')
    for row in p['rows']:
        if row['type']!='message':
            x=100+row['depth']*18;right=WIDTH-x
            d.rectangle((x,row['y'],right,row['y']+row['h']),fill='#fff8e8' if row['type']=='note' else '#f6f8fb',outline='#c4a34f' if row['type']=='note' else '#8191a4')
            for j,line in enumerate(row['lines']):d.text((x+12,row['y']+12+j*25),line,font=f,fill='#23384f')
            continue
        a,b=p['positions'][row['a']],p['positions'][row['b']];y=row['y'];selfcall=a==b
        if selfcall:
            d.line([(a,y),(a+65,y),(a+65,y+40),(a,y+40)],fill='#334d68',width=2);end=(a,y+40);direction=-1
        else:
            if row['kind']=='return':
                lo,hi=sorted((a,b))
                for x in range(int(lo),int(hi),13):d.line((x,y,min(x+7,hi),y),fill='#334d68',width=2)
            else:d.line((a,y,b,y),fill='#334d68',width=2)
            end=(b,y);direction=1 if b>a else -1
        x,yy=end;triangle=[(x,yy),(x-direction*10,yy-5),(x-direction*10,yy+5)]
        if row['kind'] in ('return','async'):d.line([triangle[1],triangle[0],triangle[2]],fill='#334d68',width=2)
        else:d.polygon(triangle,fill='#334d68')
        top=y-row['labelheight']-7
        for i,line in enumerate(row['lines']):
            lx=a+25 if selfcall else (a+b-f.getlength(line))/2
            d.rectangle((lx-2,top+i*24-1,lx+f.getlength(line)+2,top+i*24+22),fill='white')
            d.text((lx,top+i*24),line,font=f,fill='#23384f')
    for n in p['notes']:
        d.rectangle((55,n['y'],WIDTH-55,n['y']+n['h']),fill='#fff8e8',outline='#c4a34f')
        for i,line in enumerate(n['lines']):d.text((67,n['y']+12+i*25),line,font=f,fill='#23384f')
    legend='实线实心箭头：请求／处理消息；虚线：返回。alt 互斥，opt 可选，loop 重复，ref 引用；OA 回调是后续独立请求。'
    d.text((40,p['height']-70),legend,font=ImageFont.truetype(FONT_PATH,14),fill='#526b86')
    if GENERATE_PREVIEWS: im.save(file)

def container():return ET.Element('mxfile',host='app.diagrams.net',agent='official drawio skill XML workflow',modified='2026-10-15T00:00:00.000Z')
def write(root,file):ET.indent(root,space='  ');ET.ElementTree(root).write(file,encoding='utf-8',xml_declaration=True)

case(4,[('admin','总管理员／范围内部门权限管理员'),('center','权限中心')],[
 M('admin','center','预览离岗关权（员工、来源部门、系统、原因）'),
 S('center','检查当前范围，读取来源周期、授权及待执行分配'),R('center','admin','返回仅范围内影响，不泄露其他来源'),
 M('admin','center','确认立即关闭原部门权限（固定来源与版本）'),
 F('alt',('[资格/版本不符]',[R('center','admin','拒绝/冲突，不改变权限')]),('[合法]',[
 S('center','同事务更新来源周期、关系及旧申请失效，保存审计'),
 F('alt',('[写入失败]',[S('center','整体回滚'),R('center','admin','失败，不显示已关权')]),('[提交成功]',[R('center','admin','立即生效，其他独立来源保留')]))
 ]))],['不发送 OA；一般撤销仍经 OA。公司级角色同样按来源关闭，不修改共享角色。','即使没有有效关联，也留下关闭周期；迟到批准或员工返回原部门不能恢复旧来源。'],key='UC-04-离岗关权',title='关闭原部门来源权限（UC-04 配套）')

case(9,[('admin','权限中心总管理员'),('center','权限中心')],[
 M('admin','center','预览账号启停（账号、动作、原因）'),S('center','校验总管理员及目标，阻止自身自锁'),R('center','admin','返回状态、关联员工及会话影响'),
 M('admin','center','确认账号启停（版本、请求编号）'),S('center','复核并原子保存账号、会话失效依据与审计'),
 F('alt',('[失败]',[R('center','admin','拒绝/回滚，不部分修改')]),('[成功]',[R('center','admin','返回实际状态，启用不复活旧会话或授予任命')]))
 ],['普通员工不登录平台；账号启停和员工全局启停不同，不改变业务授权。'],key='UC-09-账号启停',title='后台账号启停（UC-09 配套）')

case(10,[('admin','权限中心总管理员'),('center','权限中心')],[
 M('admin','center','预览员工全局停用/恢复（员工、动作、原因）'),S('center','校验总管理员，读取所有来源及任命影响'),R('center','admin','返回全局影响与版本，提示恢复不复权'),
 M('admin','center','明确确认全局员工状态变更'),
 S('center','复核资格/版本；同事务保存员工状态、来源周期、失效及审计'),
 F('alt',('[失败]',[R('center','admin','整体拒绝/回滚')]),('[成功]',[R('center','admin','返回实际全局状态；恢复仅恢复身份')]))
 ],['不等待 OA；部门管理员无此权限，部门离岗关权仅影响获准来源。','不设计组织启停；旧批准不能在员工恢复后复权。'],key='UC-10-全局员工启停',title='全局员工启停（UC-10 配套）')


allxml=container();pages=[]
for n,items in cases.items():
    mx=container()
    for c in items:
        p=layout(c);page=xml_page(p);mx.append(page);allxml.append(copy.deepcopy(page));pages.append(p)
        if GENERATE_PREVIEWS: render(p,OUT/(c['key']+'_时序图预览.png'))
    write(mx,OUT/(f'UC-{n:02d}_'+items[0]['title']+'_时序图.drawio'))
write(allxml,OUT/'权限管理系统_全部时序图.drawio')

for file in OUT.glob('*.drawio'):
    text=file.read_text(encoding='utf-8');assert '<!--' not in text
    tree=ET.fromstring(text)
    for diagram in tree.findall('diagram'):
        cells=diagram.findall('./mxGraphModel/root/mxCell');ids=[c.get('id') for c in cells]
        assert len(ids)==len(set(ids))
        for c in cells:
            assert c.get('parent') is None or c.get('parent') in ids
            if c.get('edge')=='1':
                assert c.find('mxGeometry') is not None
                if c.get('source'):assert c.get('source') in ids and c.get('target') in ids
            g=c.find('mxGeometry')
            if c.get('vertex')=='1':
                assert float(g.get('x'))>=0 and float(g.get('x'))+float(g.get('width'))<=WIDTH,(file,c.get('id'),'horizontal bounds')
                assert float(g.get('y'))+float(g.get('height'))<=float(diagram.find('mxGraphModel').get('pageHeight'))

cards=['<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>全部用例时序图</title><style>body{font:16px "Microsoft YaHei",sans-serif;background:#f2f4f7;color:#23384f;margin:0}header,section{padding:24px;background:white;margin:18px}main{max-width:1480px;margin:auto}nav{display:flex;flex-wrap:wrap;gap:12px}img{width:100%;height:auto}a{color:#32649a}</style><header><h1>权限管理系统全部时序图</h1><p>需求分析级别 · 2026-10-15 · 原文件可编辑；图片为本地辅助预览。</p><nav>']
cards.extend(f'<a href="#{p["c"]["key"]}">{p["c"]["key"]}</a>' for p in pages);cards.append('</nav></header><main>')
for p in pages:
    c=p['c'];cards.append(f'<section id="{c["key"]}"><h2>{html.escape(c["key"]+" "+c["title"])}</h2><img loading="lazy" src="{c["key"]}_时序图预览.png" alt="{html.escape(c["title"])}"></section>')
cards.append('</main></html>');(OUT/'全部时序图预览.html').write_text('\n'.join(cards),encoding='utf-8')
(OUT/'图与用例对应.json').write_text(json.dumps([{'usecase':f'UC-{p["c"]["n"]:02d}','page':p['c']['key'],'source':p['c']['source'],'messages':len(p['messages'])} for p in pages],ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'usecases':len(cases),'pages':len(pages),'individual_files':len(cases),'messages':sum(len(p['messages']) for p in pages),'validation':'XML, IDs, message endpoints and page bounds passed'},ensure_ascii=False))

# Keep auxiliary preview versions explicit even when invoked directly.
import sys as _preview_sys
_preview_sys.path.insert(0, str(OUT.parent))
from preview_support import annotate_indexes as _annotate_indexes
_annotate_indexes()
