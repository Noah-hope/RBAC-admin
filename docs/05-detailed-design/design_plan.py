"""详细设计资料生成器；只读契约/元数据，写本目录文档和可编辑图源。

不创建应用工程、Issue/PR或数据库，不执行SQL、HTTP或部署命令。
"""
from pathlib import Path
import hashlib
import json
import math
import xml.etree.ElementTree as ET
import yaml

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
FOUNDATION_IDS = {f'BE-{n:02}' for n in range(1, 6)}
BUSINESS_START = ['FOUNDATION_ACCEPTED', 'BUSINESS_OWNER_ASSIGNED']
B_MAIN_DELIVERED = {'BE-11', 'BE-13', 'BE-15'}
B_CURRENT = {'BE-12', 'BE-17'}
B_IMPLEMENTED = B_MAIN_DELIVERED | B_CURRENT
B_CURRENT_EVIDENCE = 'BE-17-12-PREVIEW-CATALOG.md'
B_CURRENT_SOURCE_COMMIT = '1fb95ad3d89789b8e45de7bedbb6397bc04f37e7'
B_CURRENT_MERGE_COMMIT = '3103b351089f300fe2553b79bb6f63ae5a383cf1'
IMPORT_SOURCE_COMMIT = '233f63352b0c8dea92fd916c47bd29d59267f97c'
IMPORT_MERGE_COMMIT = '4b384fb7b629affd8fbd0e5762cad652bbdb3158'
GRANT_SOURCE_COMMIT = '367cdae2acc6ff247116651abad53adafb6a34b9'
GRANT_MERGE_COMMIT = 'c7435d9b17a2f9be57d1e748cae24f125a45deb2'
GRANT_MUTATION_SOURCE_COMMIT = 'dba2eaab8990fd17780d7f655fdfa049a6f7b2e1'
GRANT_MUTATION_MERGE_COMMIT = '7ab021a80e26cbf4e3b4bc245f574fcd5c31c2ff'
IMPORT_PRECHECK_SOURCE_COMMIT = 'db3419687f8dc43b1959564ddc19b0e7d32826ed'
IMPORT_PRECHECK_MERGE_COMMIT = '6506cda54c4898e736a2e730ba001294f8de4c2c'
SCOPE_CREATE_SOURCE_COMMIT = '0fee462503274fbed1ab03826c8d0723c9825c19'
SCOPE_CREATE_MERGE_COMMIT = '5f42a2cff11d68c49a4926373eeee97e3f5e8889'
SCOPE_CREATE_PR_URL = 'https://github.com/Noah-hope/RBAC-admin/pull/3'
B_EVIDENCE = 'BE-11-13-15-B-MODULE.md'
B_SOURCE_COMMIT = 'b92cdbb9b6a30b6e697f7ad80ef06e38fa034067'
B_MERGE_COMMIT = '0e391c6a652fb9ee12991b7b7d2ac85a3922d166'
FOUNDATION_SOURCE_COMMIT = '379ad438220a478093947f657d04eb56a6a2efcb'
FOUNDATION_MERGE_COMMIT = 'b68aa9aee6e78dd0b2cc1922d68028ce68ddeb11'
BUSINESS_SOURCE_COMMIT = '9b6d89e20e2f1646bd2ed96223d9c43a21e2f96f'
BUSINESS_MERGE_COMMIT = 'ff19d1792a6934dbd6bca6c174dcba29f7acdad8'
BOOTSTRAP_SOURCE_COMMIT = 'd5941c902712c92d4d8d8d7505bd6df4f5ceb139'
BOOTSTRAP_MERGE_COMMIT = '609ee4bb017e46b42e43bcd8c2616d31ab410f5a'
BOOTSTRAP_PR_URL = 'https://github.com/Noah-hope/RBAC-admin/pull/1'
PEOPLE_SOURCE_COMMIT = '6bfae88c89d0bad4b1599787ea47e2107a3bbcf9'
PEOPLE_MERGE_COMMIT = '8f1db6927b6d82e1a352ad016a4c1c056a6c648a'
SCOPE_SOURCE_COMMIT = '2199f0ef7b36a048845842c9803b08acf0f6612a'
SCOPE_MERGE_COMMIT = 'ca8518918bd668f48769134763a10a163e10612b'
SCOPE_PR_URL = 'https://github.com/Noah-hope/RBAC-admin/pull/2'
BUSINESS_ASSIGNMENTS = {
    'A': [6, 7, 8, 9, 27, 32, 34],
    'B': [11, 12, 13, 15, 17, 18, 33, 35],
    'C': [19, 20, 21, 22, 23, 24, 25, 31],
    'D': [10, 14, 16, 26, 28, 29, 30],
}
BUSINESS_OWNERS = {f'BE-{n:02}': owner for owner, numbers in BUSINESS_ASSIGNMENTS.items() for n in numbers}
SHARED_MODULE_OWNERS = {'M10': ['A', 'D'], 'M11': list('ABCD'), 'M12': list('ABCD')}
BUSINESS_AREAS = {
    'A': '身份与管理范围、运行交付',
    'B': '系统、角色与功能权限、集成验收',
    'C': '申请、OA审批与执行、审计',
    'D': '组织、人员与授权来源、导入',
}

MODULES = [
    ('M00', 'core', '事务、幂等、审计追加与公共契约', 'A', [], 'CommandBoundary/ReadBoundary、WriteContext、HttpIdempotency、AuditAppender、CanonicalPayloadCodec；只定义公共能力，不调用具体业务服务'),
    ('M01', 'identity', '后台账号、JWT与会话', 'A', ['M00','M11'], 'LoginService、AccountService、SessionRevoker；当前身份SQL投影，不以JWT内范围作为授权'),
    ('M02', 'people', '组织、员工与任职基础操作', 'D', ['M00','M11'], 'OrganizationReader、EmploymentWriter；维护稳定ID/映射及单项状态，跨域失效由M10编排'),
    ('M03', 'scope', '范围与管理员任命基础操作', 'A', ['M00','M11'], 'ScopeWriter、AppointmentWriter、ScopeCoveragePolicy；资格读取投影，不回调人员命令服务'),
    ('M04', 'catalog', '系统、目录和权限', 'B', ['M00','M11'], 'CatalogCommandService、FinalTreeValidator；同系统/无环/进入槽/菜单绑定及稳定排序'),
    ('M05', 'role', '角色元数据与有效配置', 'B', ['M00','M11'], 'RoleWriter、RoleConfigurationApplier；只接受受控写上下文，有效配置仅由M09调用应用器'),
    ('M06', 'grant', '来源、永久关系基线与授权历史', 'D', ['M00','M11'], 'BaselineWriter、GrantApplier、SourceCloser；返回失效事实，不自行调用申请服务'),
    ('M07', 'change', '草稿、固定申请与占用', 'C', ['M00','M11','M05','M06'], 'RoleDraftCoordinator、ChangeService、ClaimService、WorkflowStateWriter、RequestInvalidator；提交同事务创建outbox事实，不调用发送器'),
    ('M08', 'approval', 'OA投递、关联和最终结果接收', 'C', ['M00','M11','M07'], 'DeliveryWorker、OaSubmissionPort、FinalResultReceiver；网络/模拟受理在事务外，可靠结果独立提交'),
    ('M09', 'execution', '已批准申请自动执行与恢复', 'C', ['M00','M11','M03','M04','M05','M06','M07'], 'ExecutionWorker、ExecutionCoordinator、FailureRecorder；全清单预检后全写，一份申请一次成功'),
    ('M10', 'lifecycle', '关权、员工/范围变更及HR原子导入', 'D', ['M00','M11','M01','M02','M03','M06','M07'], 'LifecycleCoordinator、ImportPlanner/Committer；统一编排来源/任命/会话/申请持久失效；scope/员工入口由A共同实现'),
    ('M11', 'query', '安全读取、有效权限与影响投影', 'B', ['M00'], 'IdentitySnapshotReader、QualificationReader、EffectivePermissionEvaluator、ImpactService、AuditReader；按功能拆投影文件，可读多域表，不调用各域命令或引用其JPA实体'),
    ('M12', 'web', 'HTTP适配与装配入口', 'A', ['M00','M01','M02','M03','M04','M05','M06','M07','M08','M09','M10','M11'], '按功能分控制器/DTO；路由到应用服务，不直接改实体；每个控制器由对应Issue成员维护，A负责公共装配'),
]

CASES = []
def c(n, title, fixture, expected, operations=(), uc=(), source=''):
    CASES.append(dict(id=f'AT-{n:02}',title=title,fixture=fixture,expected=expected,
                      operations=list(operations),useCases=list(uc),reviewScenario=source,
                      status='DESIGNED_NOT_RUN'))

c(1,'可重复构建与最小部署','空工作目录按固定Wrapper构建；缺必需配置再启动','构建产物可追溯提交；缺配置清楚失败，日志无凭据；健康检查不暴露内部详情')
c(2,'MySQL迁移、即时约束与JPA映射','隔离MySQL8.4空库运行迁移两次，尝试非法跨系统FK/重复ACTIVE/非法CHECK及UUID往返','首次成功、再次无额外业务数据；非法记录拒绝；43表/触发器实际编译与UUID/UTC/生成列/@Version行为有证据')
c(3,'写锚点及原子回滚','两个独立连接先后取得guard，在最后一次审计插入注入异常','写者串行；失败事务所有业务/版本/成功凭据回滚，其他连接看不到半份状态',source='S08/S15/S20')
c(4,'HTTP幂等栅栏与失败恢复','同主体同key相同/不同内容；模拟过期租约、提交后丢响应、删除草稿后重放和失去范围','相同结果回放不重比旧目标版本；异内容409；旧token不能提交；删除后以固定事实复核当前资格；失权401/403',('deleteChangeDraft',),('UC-02','UC-03','UC-04'), 'S16')
c(5,'登录与普通员工边界','有效管理员、普通员工、临时密码账号、错误密码分别登录','普通员工不能登录；失败不泄露账号存在；临时token仅改密/退出；限速/过期符合契约',('login','changeOwnPassword'),('UC-09',),'S21')
c(6,'退出、改密及账号停用即时撤销','同账号两会话；退出一项、改密/重置、停用/启用后再次用旧token','退出只撤当前sid且重复204；改密/重置撤全部旧会话；恢复账号不复活旧token或改变业务授权',('logout','currentSession','setAccountState','resetAccountPassword'),('UC-09',),'S21/S22')
c(7,'三类JWT严格隔离','Admin/Business/OA token交叉调用；错误iss/aud/alg、失效服务和错误system绑定','401/403且不泄露目标；业务凭据只限绑定系统，不增加公开令牌签发API',('authorizationQuery','permissionCheck','receiveOaResult'),('UC-05','UC-06','UC-11'))
c(8,'管理员任命与单项上下文','两项互不覆盖的任命、陈旧/缺失上下文头、没有任职的候选账号','不能拼接；缺头422、陈旧409；不合资格拒绝且全批不变；仅共享角色维护由总管理员',('myAppointments','createScope','replaceScope'),('UC-07','UC-09'),'S04/S11')
c(9,'系统进入槽与同批功能目录','登记系统并同批建FUNCTION/操作/菜单绑定；跨系统/重复localKey/长环','进入权限唯一；合法本地引用一次提交；非法整体拒绝无半成品；排序稳定',('createSystem','saveCatalog'),('UC-01',),'S02/S03')
c(10,'独立操作与目录启停','角色只有编辑、另一角色有进入；停用查看或父目录，再恢复','编辑不推导查看；角色/授权关系保留；父级/操作过滤正确，恢复只作用于仍有效关系',('updateSystem','saveCatalog','authorizationQuery','permissionCheck'),('UC-01','UC-05','UC-06'),'S03/S23')
c(11,'角色元数据与归属','创建部门/共享角色、改名及伪造公司/系统归属','归属不可改；共享角色仅总管理员；改名仅metadataVersion，不改变有效configurationVersion',('createRole','updateRoleMetadata'),('UC-02',),'S04')
c(12,'角色配置新增/保留/移除与空配置','基础A+停用B，最终A+B+有效C；新增C在执行前停用；移除B/清空/无差异','保留B合法、可明确移除；新增C失效则整份INVALIDATED；清空422；无差异200不投OA',('changePreview','submitChange'),('UC-02',),'S05')
c(13,'完整任职依据与岗位不自动授权','同部门101项适用任职、重复/遗漏证据；岗位更名或新增岗位','全部依据可提交；空/重复/遗漏拒绝；岗位变化本身不创建grant，固定证据不悄然改写',('grantBaselines','createChangeDraft','submitChange'),('UC-03',),'S06')
c(14,'同源去重与异源并存','同员工角色同部门重复授予，另一部门独立授予；撤销后新授予','同源仅一ACTIVE；不同来源同时有效；baseline不重置，新grantId，旧撤销不误伤新关系',('grantBaselines','listGrants','submitChange'),('UC-03','UC-04'),'S07/S09')
c(15,'零基线关权与执行两个顺序','来源无grant但有待授予；用两个连接控制执行先/关权先及同key重放','无grant也推进周期；执行先后被关掉，关权先旧批准全份失效；同key不再推进，其他来源保留',('sourceBaselines','closeSource'),('UC-04','UC-11'),'S08')
c(16,'无变化、权限与版本判定顺序','越范围已授予项、同源已授予但旧配置版本、已结束准确grantId、仍需项陈旧、预览后分类改变','先拒越权；合法已授予/已结束归无变化；仍需项版本409；预览变化409而不裁剪',('changePreview','submitChange'),('UC-02','UC-03','UC-04'),'S10')
c(17,'草稿占用与删除','多人改同草稿/同baseline；移出范围后删除；一个新目标占用失败','冲突全回滚，原草稿/claims保留；当前不覆盖者不得处理，管理员通用提示不泄漏隐藏ID；总管理员现有入口清理',('createChangeDraft','updateChangeDraft','deleteChangeDraft'),('UC-02','UC-03','UC-04'),'S12')
c(18,'固定申请与规范摘要','提交后改当前名称；尝试改内容/目标行；使用RFC8785向量重建摘要','冻结内容/字节/投影一致，当前更名不改批准内容；提交后修改拒绝；固定版本与statusVersion分开',('submitChange','getChangeRequest'),('UC-02','UC-03','UC-04','UC-11'),'S13')
c(19,'原申请人离任与原范围缩小','申请人离任/账号停用；原范围实际缩小后扩回；仅名单更换/增无关系统','前两种资格离任不机械影响已提交申请；实质失覆盖持久INVALIDATED且不复活；名单变化不误伤',('replaceScope','receiveOaResult'),('UC-07','UC-11'),'S11')
c(20,'OA投递超时与关联前回调','受理后返回超时；关联未写先回调；申请失效时发送返回','同requestId重试关联同审批号；未关联409且不消耗事件；失效只补历史不回PENDING/不执行',('retryOaDelivery','receiveOaResult','deliverOaSubmission'),('UC-11',),'S14')
c(21,'OA事件、最终结果三层防重','同事件同/异内容；不同事件同决定；同申请相反决定；失效申请迟到批准','匹配重复200；异内容/矛盾409不覆盖；可靠新结果202不等于授权生效；失效留历史不复活',('receiveOaResult',),('UC-11',),'S14/S15')
c(22,'一次成功与全批执行','100目标固定申请，第N项变陈旧；两执行器竞争；成功audit写入失败','不裁剪，一项非法整份失效无有效子集；success请求PK只一次；存储失败全回滚后另事务记失败',('receiveOaResult','getChangeRequest'),('UC-11',),'S15')
c(23,'执行失败恢复与进程重启','已可靠APPROVED后宕机，执行写失败，回包丢失，失败记录前另一执行已成功','恢复扫描拾取；同申请退避重试；不重新审批；失败记录不能覆盖success/INVALIDATED，成功只回读',('getChangeRequest',),('UC-11',),'S15')
c(24,'员工停用/恢复与会话','员工有多来源/兼岗/任命，与授予并发停用后恢复','全部旧grant/任命/会话持久失效，epoch推进；恢复仅员工ACTIVE；保护当前/唯一总管理员',('setEmployeeState',),('UC-10','UC-11'),'S17/S22')
c(25,'离岗事件顺序Q-01','同原子批次END最后适用任职再ADD同部门新岗；另有始终适用兼岗；仅更名','前者推进周期并使旧授权/任命/相关申请失效且新岗不复权；后两者按适用条件保留，全批原子',('commitImport',),('UC-10','UC-11'),'S18')
c(26,'上传与文件解析边界','CSV/XLSX合法样例，超10MiB/解压50MiB/1000行、宏、业务公式、含糊工作表','合法可预检；超限/非法明确拒绝；不执行公式；预检前无正式组织/员工变更',('uploadImportFile','precheckImport'),('UC-10',),'S19')
c(27,'预检冻结、映射与候选冲突','无源编码组织、同名不同路径/源账号、规范化前后值；预检后占用候选code','预分配稳定ID/code并回读完整fieldChanges；同名不归并；提交冲突STALE，全批无正式写入',('precheckImport','listImportPlanChanges','commitImport'),('UC-10',),'S01/S19')
c(28,'导入第N项失败与重试','1000行含停用/离岗，最后行冲突或审计失败；成功批次重放/新批同资料','整批含失效/版本/审计全回滚，失败另存；同成功批号回原counts，新批UNCHANGED，不复活旧状态',('commitImport',),('UC-10',),'S20')
c(29,'部门迁移与历史保护','部门已有角色草稿/历史grant/审计；完全无业务历史引用的迁移','有引用阻断；合法迁移按冻结映射与即时FK顺序同事务完成，不级联改写历史',('precheckImport','commitImport'),('UC-10',),'S25')
c(30,'业务权限完整并集与隔离','同公司系统多部门来源、另一公司同名权限、无进入但有操作、未知code','当前公司/系统完整有效来源并集；进入与功能可跨角色；无进入结果空；未知与存储故障区分，无数据范围',('authorizationQuery','permissionCheck'),('UC-05','UC-06'),'S23')
c(31,'影响快照与新增目标','预览后出现新grant/申请/任职；权限缩小/切换/过期；无关改名','检测集合新增和真实依赖变化；陈旧409或拒绝旧预览，不裁剪/不配旧统计；无关变化不机械失效',('listPreviewEmployees','listPreviewTargets','submitChange'),('UC-01','UC-02','UC-03','UC-04','UC-07','UC-10'),'S24')
c(32,'敏感混合申请计数与审计','关闭可见来源A，命中跨A+B固定申请；当前任命只含A','整份实际失效；不返回混合申请ID/计数或不可读auditId；SCOPED/true配可见计数，缓存相同安全投影',('closeSource','getAudit','listAudit'),('UC-04','UC-08'),'S24')
c(33,'当前快照与撤权后的新查询','业务查询建立新主库快照，另一事务关闭来源提交后再开新查询','新查询不见旧权限；同一响应身份与结果一致；不使用旧EntityManager/副本/权限缓存',('authorizationQuery','permissionCheck'),('UC-05','UC-06'),'S23')
c(34,'审计读取与历史删除','越权详情/显式筛选、混合全局事件、删除草稿与历史引用','详情404/筛选403；列表total按可见范围；不可拆完整混合事件仅总管理员；保留历史及防重事实',('listAudit','getAudit','listChangeEvents'),('UC-08','UC-11'),'S24/S25')
c(35,'演示OA与服务令牌边界','demo profile受理固定申请但未批准；用可信OA短期JWT调用正式回调，再拒绝/重复/重启','受理不自动批准；只经原回调决定；无新管理员审批/强制执行接口；模拟标签明确，重启不丢本地防重依据')
c(36,'服务器部署和数据持久化','新目标环境安装/启动、重启应用、备份恢复到隔离环境、回退应用版本','迁移校验通过，数据/文件/密钥持久；外部HTTPS仅暴露必需入口，健康可查，恢复/回退有证据；不把迁移回滚当事务')
c(37,'全接口契约与Problem','67入站逐项HTTP：成功、401/403、缺版本/非法字段、分页/位置/错误媒体类型','按OpenAPI1.2.0验证请求响应，目标资源当前授权；错误application/problem+json，敏感响应no-store；每项由所属Issue负责')
c(38,'示例全链路演示','虚构两公司/两部门/兼岗、两业务系统、共享/部门角色；按正式导入及审批流程走一遍','角色配置→OA→授予→查询→关来源→迟到批准→恢复不复权可复现；日志与报告无凭据，不依赖正式前端')
c(39,'PR与发布证据','一个任务分支、失败→通过测试记录、任务负责人自检确认、候选部署包','Issue/PR/commit/构建校验和及负责人确认对应；未验收项不关闭，未运行测试不打勾；当前设计阶段不创建远程事项')
c(40,'前端后续衔接','既有原型页面对照真实契约与当前任命/状态三轴','复用页面与交互基线，未来接入真实API；原型Mock不作为后端验收；本周不实现正式前端')

c(41,'来源资格与角色部门资格分别失效','员工仍在来源部门A，但失去所持部门角色所属B部门的最后适用任职','只结束受影响角色各来源grant并推进其baseline/旧申请失效；不关闭仍有效的整个A来源，不误伤其他角色，不更换来源部门',('commitImport','grantBaselines'),('UC-03','UC-10','UC-11'),'S06/S18')
for case in CASES:
    case['delivery'] = 'FRONTEND_DEFERRED' if case['id']=='AT-40' else 'BACKEND'
    if case['id'] in {'AT-01', 'AT-37'}:
        case['status'] = 'PARTIALLY_VERIFIED'
        case['evidence'] = 'BE-02-IMPLEMENTATION.md'
    if case['id'] == 'AT-02':
        case['status'] = 'PARTIALLY_VERIFIED'
        case['evidence'] = 'BE-03-PERSISTENCE.md'
    if case['id'] in {'AT-03', 'AT-33'}:
        case['status'] = 'PARTIALLY_VERIFIED'
        case['evidence'] = 'BE-04-TRANSACTIONS.md'

for case in CASES:
    if case['id']=='AT-04':
        case['status']='PARTIALLY_VERIFIED'
        case['evidence']='BE-05-IDEMPOTENCY.md'
    if case['id'] in {'AT-05','AT-06','AT-07'}:
        case['status']='PARTIALLY_VERIFIED'
        case['evidence']='BE-06-IDENTITY.md'
        if case['id'] in {'AT-06','AT-07'}: case['additionalEvidence']=['BE-07-ACCOUNTS.md']
    if case['id'] in {'AT-09','AT-11','AT-12'}:
        case['status']='PARTIALLY_VERIFIED'
        case['evidence']=B_EVIDENCE
    if case['id']=='AT-37': case['additionalEvidence']=[B_EVIDENCE,B_CURRENT_EVIDENCE]
    if case['id']=='AT-09': case['additionalEvidence']=[B_CURRENT_EVIDENCE]
    if case['id'] in {'AT-10','AT-31','AT-32'}:
        case['status']='PARTIALLY_VERIFIED'
        case['evidence']=B_CURRENT_EVIDENCE
        case['verificationScope']='BE17_FIXED_SAFE_PREVIEW_AND_BE12_ATOMIC_CATALOG_ONLY_NOT_ALL_CONSUMER_COMMANDS'
    if case['id']=='AT-36':
        case['status']='PARTIALLY_VERIFIED'
        case['evidence']='BE-32-DEPLOYMENT.md'
        case['verificationScope']='PACKAGED_BOOTSTRAP_AND_SERVER_CONFIGURATION_ONLY_NOT_TARGET_SERVER'
    if case['id'] in {'AT-13','AT-27','AT-29'}:
        case['status']='PARTIALLY_VERIFIED'
        case['evidence']='BE-10-PEOPLE.md'
        case['verificationScope']='BE10_PEOPLE_HTTP_INTERNAL_READ_AND_IMPORT_PRIMITIVES_ONLY_NOT_FULL_IMPORT_LIFECYCLE'
    if case['id'] in {'AT-08','AT-19'}:
        case['status']='PARTIALLY_VERIFIED'
        case['evidence']='BE-08-MANAGEMENT-SCOPE.md'
        case['verificationScope']='BE08_READ_CONTEXT_AND_COVERAGE_ONLY_NOT_APPOINTMENT_WRITES_OR_REQUEST_INVALIDATION'
    if case['id']=='AT-26':
        case.update(status='PARTIALLY_VERIFIED',evidence='BE-28-IMPORT-FILES.md',
            verificationScope='BE28_UPLOAD_IMMUTABLE_STORAGE_AND_SAFE_PARSING_ONLY_NOT_PRECHECK_OR_COMMIT')
    if case['id']=='AT-25':
        case.update(status='PARTIALLY_VERIFIED',evidence='BE-29-IMPORT-PRECHECK.md',
            verificationScope='BE29_SAME_SOURCE_BATCH_FROZEN_PRECHECK_AND_CONFLICT_ONLY_NOT_COMMIT_REPLAY_OR_NEW_BATCH_ACTUAL_DML')
    if case['id'] in {'AT-26','AT-27','AT-29'}:
        case.setdefault('additionalEvidence',[]).append('BE-29-IMPORT-PRECHECK.md')
        case['verificationScope']='PRIOR_UPLOAD_OR_PEOPLE_PRIMITIVES_AND_BE29_FROZEN_PRECHECK_ONLY_NOT_FORMAL_IMPORT_COMMIT_OR_LIFECYCLE'
    if case['id']=='AT-13': case.setdefault('additionalEvidence',[]).append('BE-14-GRANT-BASELINES.md')
    if case['id'] in {'AT-08','AT-31','AT-37'}:
        case.setdefault('additionalEvidence',[]).append('BE-09-SCOPE-CREATION.md')
    if case['id'] in {'AT-14','AT-15'}:
        case.update(status='PARTIALLY_VERIFIED',evidence='BE-14-GRANT-BASELINES.md',
            verificationScope='BE14_SOURCE_RELATION_AND_HISTORY_READS_ONLY_NOT_GRANT_MUTATIONS_OR_CLOSE_EXECUTION_RACES')
        case.setdefault('additionalEvidence',[]).append('BE-16-GRANT-MUTATIONS.md')
        case['verificationScope']='BE14_READS_AND_BE16_ATOMIC_GRANT_REVOKE_CLOSE_AND_GUARD_RACES_ONLY_NOT_LIFECYCLE_REQUEST_INVALIDATION_OR_PUBLIC_CLOSE'
    if case['id']=='AT-13': case.setdefault('additionalEvidence',[]).append('BE-16-GRANT-MUTATIONS.md')
    if case['id'] in {'AT-22','AT-41'}:
        case.update(status='PARTIALLY_VERIFIED',evidence='BE-16-GRANT-MUTATIONS.md',
            verificationScope='BE16_INTERNAL_ATOMIC_APPROVED_TARGETS_AND_INDEPENDENT_SOURCE_ROLE_QUALIFICATIONS_ONLY_NOT_C_EXECUTOR_OR_LIFECYCLE')
    if case['id'] in {'AT-01','AT-02'}:
        case['additionalEvidence']=['BE-32-DEPLOYMENT.md']

for case in CASES:
    if case['id'] in {'AT-04','AT-11','AT-13','AT-16','AT-17','AT-22','AT-37'}:
        case['status']='PARTIALLY_VERIFIED'
        case.setdefault('evidence','BE-20-CHANGE-DRAFTS.md')
        case.setdefault('additionalEvidence',[]).append('BE-20-CHANGE-DRAFTS.md')
        case['verificationScope']='; '.join(filter(None,[case.get('verificationScope',''),'BE20_DRAFT_HTTP_SCOPE_CLAIMS_REPLAY_AND_INTERNAL_INVALIDATION_ONLY_NOT_SUBMIT_OA_OR_EXECUTION']))

for case in CASES:
    if case['id'] in {'AT-17','AT-18','AT-19','AT-32','AT-34','AT-37'}:
        case['status']='PARTIALLY_VERIFIED'
        case.setdefault('evidence','BE-19-22-AUDIT-REQUEST-QUERIES.md')
        case.setdefault('additionalEvidence',[]).append('BE-19-22-AUDIT-REQUEST-QUERIES.md')
        case['verificationScope']='; '.join(filter(None,[case.get('verificationScope',''),'BE19_22_CURRENT_WHOLE_REQUEST_AUDIT_AND_TIMELINE_READS_ONLY_NOT_SUBMISSION_OA_OR_EXECUTION']))

for case in CASES:
    if case['id'] in {'AT-07','AT-12','AT-13','AT-16','AT-18','AT-19','AT-20','AT-21','AT-22','AT-23','AT-35','AT-37'}:
        case['status']='PARTIALLY_VERIFIED'
        case.setdefault('evidence','BE-21-31-APPROVAL-WORKFLOW.md')
        case.setdefault('additionalEvidence',[]).append('BE-21-31-APPROVAL-WORKFLOW.md')
        case['verificationScope']='; '.join(filter(None,[case.get('verificationScope',''),'BE21_23_24_25_31_FIXED_JCS_OUTBOX_CALLBACK_ATOMIC_EXECUTION_RECOVERY_AND_DEMO_ONLY_NOT_REAL_EXTERNAL_OA_OR_FULL_SYSTEM_ACCEPTANCE']))

for case in CASES:
    if case['id'] in {'AT-07','AT-10','AT-30','AT-33','AT-37'}:
        case['status']='PARTIALLY_VERIFIED'
        case.setdefault('evidence','BE-18-EFFECTIVE-PERMISSIONS.md')
        case.setdefault('additionalEvidence',[]).append('BE-18-EFFECTIVE-PERMISSIONS.md')
        case['verificationScope']='; '.join(filter(None,[case.get('verificationScope',''),'BE18_CURRENT_BUSINESS_QUERY_AND_CHECK_ONLY_NOT_ALL_LIFECYCLE_OR_EXTERNAL_SYSTEMS']))

for case in CASES:
    if case['id'] in {'AT-04','AT-07','AT-11','AT-13','AT-16','AT-30','AT-31','AT-34','AT-37'}:
        case.setdefault('additionalEvidence',[]).append('BE-33-35-INTEGRATION-HANDOFF.md')
        case['verificationScope']='; '.join(filter(None,[case.get('verificationScope',''),'BE33_PREPARATION_DELIVERED_PUBLIC_FLOWS_ONLY_NOT_APPROVAL_EXECUTION_IMPORT_OR_TARGET_SERVER']))

TASKS = []
def t(n,title,owner,estimate,deps,mods,ops,cases,deliverable,notes=''):
    foundation = n <= 5
    TASKS.append(dict(id=f'BE-{n:02}',title=title,
        phase='FOUNDATION' if foundation else 'BUSINESS',
        ownerSlot='USER' if foundation else BUSINESS_OWNERS[f'BE-{n:02}'],reviewerSlot=None,reviewPolicy='OWNER_ACCEPTANCE',
        reviewRequired=False,acceptanceOwnerPolicy='TASK_OWNER',
        assignmentStatus='OWNER_CONFIRMED' if foundation else 'ASSIGNED',
        assignmentDecision='D-053' if foundation else 'D-055',
        suggestedOwnerSlot=owner,suggestedReviewerSlot=None,
        startConditions=[] if foundation else list(BUSINESS_START),
        estimateHours=dict(zip(('low','likely','high'),estimate)),
        reviewHours=dict.fromkeys(('low','likely','high'),0),dependencies=[f'BE-{x:02}' for x in deps],
        modules=mods.split(),operations=ops.split(),acceptance=[f'AT-{x:02}' for x in cases],
        deliverable=deliverable,notes=notes,status='OWNER_ACCEPTED' if foundation or n in (6,7,32) else ('IN_PROGRESS' if f'BE-{n:02}' in B_IMPLEMENTED else 'PLANNED'),milestone='后端全范围可部署演示（拟建）',
        labels=['backend','design-ready'],remoteIssueNumber=None,remotePrNumber=None))
    if f'BE-{n:02}' in B_MAIN_DELIVERED:
        TASKS[-1]['implementationProgress']=dict(evidence=B_EVIDENCE,decision='D-070',implemented=True,
            ownerAccepted=False,mergedToMain=False,databaseExecuted=True,databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',
            branch='codex/be-11-13-15-b-module',dependencyBase='9857e5e',claimSource='USER_MESSAGE')
    if f'BE-{n:02}' in B_CURRENT:
        TASKS[-1]['implementationProgress']=dict(evidence=B_CURRENT_EVIDENCE,decision='D-073',implemented=True,
            ownerAccepted=False,mergedToMain=False,databaseExecuted=True,databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',
            branch='codex/be-17-12-preview-catalog',dependencyBase='93acc63',claimSource='USER_MESSAGE',
            dependencyContinuation='BE-12 consumes BE-17 implemented and verified on the same task branch')
    if n == 18:
        TASKS[-1]['status']='IN_PROGRESS'
        TASKS[-1]['implementationProgress']=dict(evidence='BE-18-EFFECTIVE-PERMISSIONS.md',decision='D-093',
            scope='FRESH_PRIMARY_BUSINESS_PERMISSION_UNION_AND_INDIVIDUAL_CHECK',implemented=True,httpImplemented=True,
            ownerAccepted=False,mergedToMain=False,databaseExecuted=True,databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',
            branch='codex/be-18-effective-permissions',dependencyBase='7914788',claimedOwnerSlot='B',claimSource='USER_MESSAGE')
    if n in (33,35):
        TASKS[-1]['preparationProgress']=dict(evidence='BE-33-35-INTEGRATION-HANDOFF.md',decision='D-094',
            scope='DELIVERED_MODULE_INTEGRATION_AND_RELEASE_EVIDENCE_PREPARATION_ONLY',
            implemented=False,fullAcceptanceVerified=False,ownerAccepted=False,mergedToMain=False,
            branch='codex/be-33-35-integration-handoff',dependencyBase='baeab61',
            integratedMain='6b9a02e4406c5017e9b92b62bd41b026fc726f6c',
            unresolvedDependencies=['BE-27','BE-30'] if n==33 else ['BE-34'],
            transitiveMissingDependencies=['BE-26'] if n==33 else [])
    if n == 6:
        TASKS[-1]['implementationProgress']=dict(evidence='BE-06-IDENTITY.md',decision='D-056',
            scope='LOGIN_SESSION_PASSWORD_AND_THREE_JWT_FAMILIES',implemented=True,
            ownerAccepted=True,mergedToMain=False,databaseExecuted=True,
            databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',setup='BE-06-LOCAL-SETUP.md',
            claimedBy='USER',claimSource='USER_MESSAGE')
        TASKS[-1]['ownerAcceptance']=dict(acceptedBy='USER',ownerSlot='A',date='2026-10-18',
            scope='BE-06',decision='D-057',source='USER_MESSAGE',message='验证通过',
            implementationCommit='1907a63f23ab35ad85404c9bde6f7bb2648fc704',
            interpretation='LOCAL_VERIFICATION_CONFIRMATION_AFTER_BE06_HANDOFF',
            mergedToMain=False,evidence='BE-06-IDENTITY.md')
    if n == 10:
        TASKS[-1]['status']='IMPLEMENTED'
        TASKS[-1]['implementationProgress']=dict(evidence='BE-10-PEOPLE.md',decision='D-065',
            scope='FULL_BE10_PEOPLE_QUERIES_INTERNAL_FACTS_AND_IMPORT_WRITE_PRIMITIVES',
            internalReadPortImplemented=True,implemented=True,httpImplemented=True,
            employmentWriterImplemented=True,ownerAccepted=False,mergedToMain=False,
            databaseExecuted=True,databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',
            claimedBy='USER',claimSource='USER_MESSAGE',claimedOwnerSlot='D')
    if n == 9:
        TASKS[-1]['status']='OWNER_ACCEPTED'
        TASKS[-1]['implementationProgress']=dict(evidence='BE-09-SCOPE-CREATION.md',decision='D-081',
            scope='SUPER_ADMIN_SCOPE_CREATION_PREVIEW_AND_ATOMIC_APPOINTMENTS',implemented=True,
            ownerAccepted=True,mergedToMain=False,databaseExecuted=True,
            databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',claimedBy='USER',claimSource='USER_MESSAGE',
            branch='codex/be-09-scope-creation',dependencyBase='a860dc8',claimedOwnerSlot='A')
        TASKS[-1]['ownerAcceptance']=dict(acceptedBy='USER',ownerSlot='A',date='2026-10-20',
            scope='BE-09',decision='D-082',source='USER_MESSAGE',message='测试通过即可。',
            implementationCommit='41931f45e2319f41434c6470f4e2b564af9058d3',
            interpretation='OWNER_ACCEPTS_AUTOMATED_EVIDENCE_WITHOUT_MANUAL_DEMO',
            manualDemoVerified=False,automatedUnitTests=112,automatedMySqlTests=205,
            mergedToMain=False,evidence='BE-09-SCOPE-CREATION.md')
    if n == 28:
        TASKS[-1]['status']='IMPLEMENTED'
        TASKS[-1]['implementationProgress']=dict(evidence='BE-28-IMPORT-FILES.md',decision='D-075',
            scope='UPLOAD_METADATA_IMMUTABLE_STORAGE_AND_SAFE_CSV_XLSX_PARSING',
            implemented=True,httpImplemented=True,ownerAccepted=False,mergedToMain=False,
            databaseExecuted=True,databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',
            branch='codex/be-28-import-files',dependencyBase='93acc63',claimedOwnerSlot='D')
    if n == 29:
        TASKS[-1]['status']='IMPLEMENTED'
        TASKS[-1]['implementationProgress']=dict(evidence='BE-29-IMPORT-PRECHECK.md',decision='D-084',
            scope='FROZEN_PRECHECK_PLAN_DIFF_ISSUE_QUERY_ONLY_NOT_FORMAL_IMPORT_COMMIT',
            implemented=True,httpImplemented=True,ownerAccepted=False,mergedToMain=False,
            databaseExecuted=True,databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',
            branch='codex/be-29-import-precheck',dependencyBase='072585e',claimedOwnerSlot='D')
    if n in (19,22):
        TASKS[-1]['status']='OWNER_ACCEPTED'
        TASKS[-1]['implementationProgress']=dict(evidence='BE-19-22-AUDIT-REQUEST-QUERIES.md',decision='D-089',
            scope='AUDIT_READS' if n==19 else 'WHOLE_REQUEST_READS_AND_SAFE_TIMELINE',
            implemented=True,httpImplemented=True,ownerAccepted=True,mergedToMain=True,
            databaseExecuted=True,databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',
            branch='BE-19-22-审计与申请查询',dependencyBase='09abaa2',claimedOwnerSlot='C',
            claimedBy='USER',claimSource='USER_MESSAGE',deliveryDecision='D-090',
            sourceCommit='cc88885f2c97df838720fc2f7f0e524d72e56351',mainMergeCommit='cf27f7f910bf5e904c89542f56e3f56fddd884d9',pushVerified=True)
        TASKS[-1]['ownerAcceptance']=dict(acceptedBy='USER',ownerSlot='C',date='2026-10-21',scope=f'BE-{n:02}',
            decision='D-090',source='USER_MESSAGE',message='确认，然后继续完成剩下的任务',manualDemoVerified=False,
            automatedUnitTests=137,automatedMySqlTests=284,mergedToMain=True,evidence='BE-19-22-AUDIT-REQUEST-QUERIES.md')
    if n in (21,23,24,25,31):
        TASKS[-1]['status']='IMPLEMENTED'
        TASKS[-1]['implementationProgress']=dict(evidence='BE-21-31-APPROVAL-WORKFLOW.md',decision='D-091',
            scope={21:'FIXED_JCS_SUBMISSION_PREVIEW_AND_ATOMIC_OUTBOX',23:'LEASED_DELIVERY_RELIABLE_LINK_AND_RETRY',24:'TRUSTED_CALLBACK_THREE_LAYER_DEDUPLICATION',25:'WHOLE_REQUEST_ATOMIC_EXECUTION_INVALIDATION_AND_RECOVERY',31:'DEMO_ONLY_OA_AND_CONTROLLED_SERVICE_CLI'}[n],
            implemented=True,httpImplemented=n in (21,23,24),internalPortImplemented=n==25,cliImplemented=n==31,
            ownerAccepted=False,mergedToMain=True,databaseExecuted=True,databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',
            branch='BE-21-31-申请审批与执行',dependencyBase='7914788',claimedOwnerSlot='C',claimedBy='USER',claimSource='USER_MESSAGE',
            realExternalOaConnected=False,manualDemoVerified=False,deliveryDecision='D-092',
            sourceCommit='adab1861906dc7d973f796961249e1ef87563e29',mainMergeCommit='311aa5d5cbc3d7bfbd064888b4172d48de0a0e99',pushVerified=True,verifiedRemoteCommit='311aa5d5cbc3d7bfbd064888b4172d48de0a0e99')
    if n == 20:
        TASKS[-1]['status']='OWNER_ACCEPTED'
        TASKS[-1]['implementationProgress']=dict(evidence='BE-20-CHANGE-DRAFTS.md',decision='D-086',
            scope='ROLE_AND_THREE_CHANGE_DRAFTS_ATOMIC_CLAIMS_AND_INTERNAL_INVALIDATION_STATE_PORT',
            implemented=True,httpImplemented=True,ownerAccepted=True,mergedToMain=True,
            databaseExecuted=True,databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',
            branch='BE-20-角色与变更草稿',dependencyBase='86089d2',claimedOwnerSlot='C',
            claimedBy='USER',claimSource='USER_MESSAGE',deliveryDecision='D-088',
            sourceCommit='310313a07e0321f58a5b1b03d3d59231fe2a17a6',mainMergeCommit='a9b214cce38d4396120b3902ce19f2b7440bf4f1',
            pushVerified=True,verifiedRemoteCommit='a9b214cce38d4396120b3902ce19f2b7440bf4f1')
        TASKS[-1]['ownerAcceptance']=dict(acceptedBy='USER',ownerSlot='C',date='2026-10-21',
            scope='BE-20',decision='D-088',source='USER_MESSAGE',message='可以',
            interpretation='ACCEPT_AND_DELIVER_BE20_AFTER_TASK_BRANCH_WORKFLOW_EXPLANATION',
            implementationCommit='4e1bcf4f4d79874c71cbd56782f74196095f4384',manualDemoVerified=False,
            automatedUnitTests=137,automatedMySqlTests=265,mergedToMain=True,
            mainMergeCommit='a9b214cce38d4396120b3902ce19f2b7440bf4f1',evidence='BE-20-CHANGE-DRAFTS.md')
    if n == 14:
        TASKS[-1]['status']='IMPLEMENTED'
        TASKS[-1]['implementationProgress']=dict(evidence='BE-14-GRANT-BASELINES.md',decision='D-077',
            scope='SOURCE_RELATION_BASELINE_HISTORY_AND_CURRENT_QUALIFICATION_READS',
            implemented=True,httpImplemented=True,internalReadPortImplemented=True,
            ownerAccepted=False,mergedToMain=False,databaseExecuted=True,databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',
            branch='codex/be-14-grant-baselines',dependencyBase='61132f8',claimedOwnerSlot='D')
    if n == 16:
        TASKS[-1]['status']='IMPLEMENTED'
        TASKS[-1]['implementationProgress']=dict(evidence='BE-16-GRANT-MUTATIONS.md',decision='D-079',
            scope='GUARDED_BASELINE_GRANT_REVOKE_SOURCE_AND_RELATION_CLOSE_PRIMITIVES',
            implemented=True,internalWritePortImplemented=True,httpImplemented=False,
            ownerAccepted=False,mergedToMain=False,databaseExecuted=True,databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',
            branch='codex/be-16-grant-mutations',dependencyBase='a860dc8',claimedOwnerSlot='D')
    if n == 8:
        TASKS[-1]['status']='OWNER_ACCEPTED'
        TASKS[-1]['implementationProgress']=dict(evidence='BE-08-MANAGEMENT-SCOPE.md',decision='D-067',
            scope='SINGLE_APPOINTMENT_CONTEXT_SCOPE_READS_AND_COVERAGE',implemented=True,
            ownerAccepted=True,mergedToMain=False,databaseExecuted=True,
            databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',persistence='SPRING_DATA_JPA_READ_REPOSITORIES',
            branch='codex/be-08-management-scope',dependencyBase='db1be16',claimedOwnerSlot='A')
        TASKS[-1]['ownerAcceptance']=dict(acceptedBy='USER',ownerSlot='A',date='2026-10-19',
            scope='BE-08',decision='D-068',source='USER_MESSAGE',message='验证通过，接下来做什么',
            implementationCommit='1c0eb3c055fe267fd0ff69c37e5120de3951eca1',
            interpretation='OWNER_CONFIRMATION_AFTER_BE08_HANDOFF_AND_LOGIN_GUIDANCE',
            mergedToMain=False,evidence='BE-08-MANAGEMENT-SCOPE.md')
    if n == 7:
        TASKS[-1]['implementationProgress']=dict(evidence='BE-07-ACCOUNTS.md',decision='D-058',
            scope='SUPER_ADMIN_ACCOUNT_READ_CREATE_STATE_AND_PASSWORD_RESET',implemented=True,
            ownerAccepted=True,mergedToMain=False,databaseExecuted=True,
            databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',claimedBy='USER',claimSource='USER_MESSAGE',
            dependencyIntegrationPolicy='ACCEPTED_ANCESTOR_WITH_EXPLICIT_CONTINUATION',
            acceptedDependency='BE-06',acceptedDependencyCommit='1907a63f23ab35ad85404c9bde6f7bb2648fc704')
        TASKS[-1]['ownerAcceptance']=dict(acceptedBy='USER',ownerSlot='A',date='2026-10-19',
            scope='BE-07',decision='D-059',source='USER_MESSAGE',message='BE-07验证通过',
            verificationMessage='BE-07这两项验证通过，下一步做什么',
            implementationCommit='584eb4e8365b0041c09ccf3f76fcaf284bb32e26',interpretation='LOCAL_UP_AND_UNAUTHENTICATED_401_WITH_PRIOR_AUTOMATED_EVIDENCE',
            mergedToMain=False,evidence='BE-07-ACCOUNTS.md')
    if n == 32:
        TASKS[-1]['implementationProgress']=dict(evidence='BE-32-DEPLOYMENT.md',decision='D-061',
            scope='PACKAGED_BOOTSTRAP_SERVER_CONFIGURATION_AND_DEPLOYMENT_TEMPLATES',
            implemented=True,ownerAccepted=True,mergedToMain=False,databaseExecuted=True,
            databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',targetServerDeployed=False,
            claimedBy='USER',claimSource='USER_MESSAGE')
        TASKS[-1]['ownerAcceptance']=dict(acceptedBy='USER',ownerSlot='A',date='2026-10-19',
            scope='BE-32',decision='D-062',source='USER_MESSAGE',message='现在确认BE-32验证通过',
            implementationCommit='cc76ac9c4bf4d073f7306b2f5e95c1d5cf52a614',
            verificationEvidenceCommit='27b99700d7d6f747f9e1f1377ca7aed20c5e92c9',
            interpretation='OWNER_ACCEPTANCE_AFTER_LOCAL_CHAIN_AND_PRIOR_AUTOMATED_EVIDENCE',
            mergedToMain=False,evidence='BE-32-DEPLOYMENT.md')
    if n == 1:
        TASKS[-1]['implementationProgress'] = dict(evidence='FOUNDATION-REVIEW.md',
            scope='LOCAL_ENVIRONMENT_AND_FOUNDATION_HANDOFF_REVIEW',
            localTechnicalPreparationVerified=True,teamRepositoryAccessVerified=False,
            mergedToMain=True,ownerAccepted=True)
        TASKS[-1]['ownerAcceptance'] = dict(acceptedBy='USER',date='2026-10-18',scope='BE-01',
            implementationCommit=FOUNDATION_SOURCE_COMMIT,source='USER_MESSAGE',
            message='确认基座通过，合并 main 并推送',interpretation='OVERALL_FOUNDATION_HANDOFF_ACCEPTANCE',
            mergedToMain=True,evidence='FOUNDATION-REVIEW.md')
    if n == 2:
        TASKS[-1]['ownerAcceptance'] = dict(acceptedBy='USER',date='2026-10-17',
            scope='BE-02',implementationCommit='f911c60',source='USER_MESSAGE',message='确认通过',
            mergedToMain=True,evidence='BE-02-IMPLEMENTATION.md')
    if n == 3:
        TASKS[-1]['implementationProgress'] = dict(evidence='BE-03-PREPARATION.md',
            scope='MIGRATIONS_BASIC_JPA_AND_STARTUP_GUARD',databaseExecuted=True,
            databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',databaseEvidence='BE-03-MYSQL-VALIDATION.md',
            jpaMappingVerified=True,jpaMappingScope='BASIC_SAMPLES_ONLY',
            persistenceEvidence='BE-03-PERSISTENCE.md',startupGuardVerified=True,ownerAccepted=True)
        TASKS[-1]['ownerAcceptance'] = dict(acceptedBy='USER',date='2026-10-18',scope='BE-03',
            implementationCommit='b8caeb2',source='USER_MESSAGE',message='继续',
            interpretation='CONTINUE_AFTER_BE03_REVIEW',mergedToMain=True,evidence='BE-04-TRANSACTIONS.md')
    if n == 4:
        TASKS[-1]['implementationProgress'] = dict(evidence='BE-04-TRANSACTIONS.md',
            scope='PUBLIC_TRANSACTION_SNAPSHOT_AND_AUDIT_MECHANISMS',databaseExecuted=True,
            databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',authorizationImplementation='CALLBACK_ONLY',
            crossDomainBusinessVerified=False,ownerAccepted=True)
        TASKS[-1]['ownerAcceptance'] = dict(acceptedBy='USER',date='2026-10-18',scope='BE-04',
            implementationCommit='58b556a',source='USER_MESSAGE',message='BE-04 确认服务为up状态，接下来做什么',
            interpretation='LOCAL_UP_CONFIRMATION_AFTER_BE04_HANDOFF',mergedToMain=True,
            evidence='BE-04-TRANSACTIONS.md')

    if n == 5:
        TASKS[-1]['implementationProgress'] = dict(evidence='BE-05-IDEMPOTENCY.md',
            scope='HTTP_IDEMPOTENCY_RESERVATION_FENCING_AND_SAFE_REPLAY',databaseExecuted=True,
            databaseExecutionScope='DISPOSABLE_INSTANCE_ONLY',authorizationImplementation='CALLBACK_ONLY',
            canonicalizationScope='CALLER_SUPPLIED_CANONICAL_BYTES_NOT_JCS',defaultEnabled=False,
            crossDomainBusinessVerified=False,ownerAccepted=True)
        TASKS[-1]['ownerAcceptance'] = dict(acceptedBy='USER',date='2026-10-18',scope='BE-05',
            implementationCommit='d059d11',source='USER_MESSAGE',message='是，已设为 true',
            precedingHealthConfirmation='确认status状态为up',idempotencyEnabledConfirmed=True,
            interpretation='ENABLED_LOCAL_UP_CONFIRMATION_AFTER_BE05_HANDOFF',
            mergedToMain=True,evidence='BE-05-IDEMPOTENCY.md')

t(1,'基座开发准备与协作条件核验','A',(1,2,3),[], 'M00 M12','',[1,39], '用户负责并确认基座，使用本机MySQL服务；JDK21可在IDE引用，命令行未配置；云服务器已具备，Docker数据库留后续；核验实际版本/隔离条件及工具。','用户已明确基座无需他人评审；JDK/Wrapper构建、本机MySQL8.4.8测试库迁移/启动/账号及独立实例机制已验证。开发库迁移/guard/启动/健康已获用户截图及日志确认；D-053用户已整体确认并交付main/b68aa9a（远程回查通过）；成员访问与实际业务任务登记留团队接入继续核对（见FOUNDATION-REVIEW.md）；不把云服务器存在当Docker数据库已部署。')
t(2,'应用骨架、契约边界与健康检查','A',(1.5,3,5),[1],'M00 M12','',[1,37], 'backend单Maven工程、按模块分包、配置绑定、统一Problem/请求ID、未知字段拒绝、Actuator健康。')
t(3,'迁移、UUID/版本及JPA基础适配','A',(2.5,5,8),[2],'M00','',[2], 'Flyway初始表/约束/触发器/guard、UUID字符映射、UTC和版本边界；各域实体仍在对应业务Issue实现。','schema.sql是待引擎审查草案，不能原样运行后直接宣布所有业务正确。')
t(4,'统一写事务、读快照与审计追加','A',(2,4,6),[3],'M00 M11','',[3,33], 'CommandBoundary/ReadBoundary、WriteContext及同事务AuditAppender、锁等待与错误分类；两个连接的屏障测试。')
t(5,'HTTP幂等预留、栅栏与删除后重放','A',(2,4,6),[4],'M00','',[4], '请求HMAC/租约/安全结果/authorization_basis、同token提交和过期接管、敏感响应过滤。')
t(6,'三类JWT与后台登录、退出、本人改密','A',(2.5,5,8),[4],'M01 M12','login currentSession logout changeOwnPassword',[5,6,7], 'RS256三类入口、当前资格、会话持久化、受限改密token与限速，普通员工拒绝。')
t(7,'后台账号管理与密码重置','A',(1.5,3,5),[5,6],'M01 M12','listAccounts createAccount getAccount setAccountState resetAccountPassword',[6,7], '账号查询/创建/停用启用/重置，不改变员工业务授权，临时密码响应遵循契约且不缓存秘密。')
t(8,'当前单项任命、范围读模型与覆盖策略','A',(1.5,3,5),[6,10],'M03 M11 M12','myAppointments listScopes getScope listScopeAppointments',[8,19], '三上下文头、当前单项覆盖、列表/详情边界、任命版本与范围版本独立。')
t(9,'创建管理范围及任命、创建预览','A',(1.5,3,5),[5,8,17],'M03 M12','createScope scopeCreatePreview',[8,31], '总管理员创建完整范围/名单，当前部门任职及系统合法，失败全回滚。')
t(10,'组织、员工与任职读取及基础变更端口','D',(1.5,3,5),[3,4],'M02 M11 M12','listCompanies listDepartments listPositions listEmployees getEmployee listEmployments',[13,27,29], '稳定员工/源身份/公司部门岗位映射及任职读取，EmploymentWriter仅供受控导入调用；无新增组织写API。')
t(11,'系统登记及查询','B',(1.5,3,5),[3,4,5,6],'M04 M12','listSystems createSystem getSystem',[9,37], '稳定系统编码和唯一SYSTEM_ACCESS，登记同事务，分页/详情权限与契约一致。')
t(12,'目录原子编辑、系统启停及影响预览','B',(3,5,8),[11,17],'M04 M12','updateSystem systemStatePreview listDirectoryNodes listPermissions catalogPreview saveCatalog',[9,10,31], '完整最终树/本地引用/绑定/稳定排序/无环，合法即时FK顺序、整批/no-change及预览依赖。')
t(13,'角色元数据和当前配置读取','B',(1.5,2.5,4),[3,5,6,11],'M05 M12','listRoles getRole updateRoleMetadata',[11,12], '固定归属、共享角色维护边界、元数据版本与配置版本，当前配置与待审批状态分开显示。')
t(14,'来源和关系基线、授权历史及当前资格','D',(2,3.5,6),[4,10,11],'M06 M11 M12','listGrants grantBaselines sourceBaselines',[13,14,15], '缺失逻辑0基线、全部适用任职、同来源去重/异源并存、准确历史grant版本。')
t(15,'有效角色配置应用器','B',(1.5,3,5),[4,13],'M05','',[11,12,18], '仅执行器可用的配置差集校验/替换/版本和历史写入，保留停用项合法，全部非空。')
t(16,'授予/撤销及关闭来源底层原子操作','D',(2,3.5,6),[4,14],'M06','',[14,15,22,41], 'WriteContext内物化源/永久baseline、ACTIVE槽、新grantID、结束与证据，返回受影响事实供编排器失效申请。')
t(17,'安全影响预览与分页、依赖集合快照','B',(2.5,5,8),[4,8,10],'M11 M12','listPreviewEmployees listPreviewTargets',[31,32], '各业务预览共用纯影响计算及数据投影，5分钟、当前上下文重查、集合新增检测，混合申请安全计数。','仅依赖表和读投影，不依赖目录/角色命令已实现，避免与BE-12形成依赖环。')
t(18,'业务系统有效权限和单项检查','B',(2,4,6),[6,10,14],'M11 M12','authorizationQuery permissionCheck',[7,10,30,33], '主库新快照、完整有效来源并集、进入资格、独立菜单/操作、未知编码与存储错误区分。')
t(19,'审计列表、详情与敏感访问','C',(1.5,2.5,4),[4,6,8],'M11 M12','listAudit getAudit',[32,34], '原事件append-only，混合不可安全分割事件仅总管理员，列表total和详情当前授权一致。')
t(20,'角色创建与配置/授予/撤销草稿、占用','C',(3,5,8),[5,8,13,14],'M07 M12','createRole createChangeDraft updateChangeDraft deleteChangeDraft',[11,13,16,17], '新角色+初始草稿/claim原子，草稿完整投影与并发版本；删除安全快照、幂等授权依据及占用释放。')
t(21,'预览确认、固定申请与OA待办','C',(3,5,8),[17,20],'M07 M12','changePreview submitChange',[12,13,16,18], '先当前授权再无变化/仍需版本/预览依赖，JCS固定摘要包/目标投影/原资格/outbox同事务，提交不授权。')
t(22,'申请列表、详情及安全时间线','C',(1,2,3),[20],'M07 M11 M12','listChangeRequests getChangeRequest listChangeEvents',[17,18,19,34], '整份当前单项范围覆盖，三轴状态/历史与安全失败码，驳回复制仅新草稿。')
t(23,'OA可靠投递、关联及人工投递重试','C',(2,4,6),[21],'M08 M12','retryOaDelivery deliverOaSubmission',[20,35], '租约领取/发送前复核/事务外适配器/关联回写，超时同requestId，不重投已失效申请。')
t(24,'OA最终结果接收与三层去重','C',(2,3.5,6),[21,23,6],'M08 M12','receiveOaResult',[7,20,21], '可信服务身份和可靠关联，事件/最终结果冲突检测、可靠接收独立提交，批准不直接变更权限。')
t(25,'自动执行、整份失效与失败恢复','C',(3,5,8),[15,16,24],'M09','',[12,19,22,23], '完整固定清单复核→全部业务/audit/success/状态同事务，失败后另事务复核记录，启动/定时扫描恢复。')
t(26,'直接来源关闭与统一申请失效编排','D',(2.5,4.5,7),[5,16,20,17],'M10 M12','sourceClosePreview closeSource',[15,16,19,32,41], '统一RequestInvalidator，关权/关系结束触发整份已提交申请失效，安全计数/可见审计及幂等原子，DRAFT不伪终态。')
t(27,'员工状态及管理范围替换的跨域失效','A',(2,4,6),[7,8,9,26],'M10 M03 M01 M12','employeeStatePreview setEmployeeState scopeEditPreview replaceScope',[8,19,24,31], '来源/关系/任命/会话/原范围申请同事务持久失效，恢复不复权，范围扩回不复活、防总管理员自锁。')
t(28,'CSV/XLSX文件上传、元数据及解析','D',(2,3.5,6),[3,5,6],'M10 M12','uploadImportFile getImportFile',[26], '正式文件边界、受控不可变存储/摘要/工作表/宏公式限制，不沿用原型上传限制。')
t(29,'冻结导入预检、差异与问题报告','D',(3,5,8),[10,17,28],'M10 M12','precheckImport listImportBatches getImportBatch listImportIssues listImportPlanChanges',[25,26,27,29], '冻结完整输入/映射/规范前后值/动作顺序/候选ID-code/依赖/唯一性断言，无正式数据副作用。')
t(30,'导入整批提交、历史与失败恢复','D',(3,5,8),[27,29],'M10 M12','commitImport',[24,25,27,28,29,41], '1000源行一个事务，嵌入同一生命周期操作；STALE/FAILED后另事务防覆盖成功；同批防重及新批无变化。')
t(31,'演示OA适配器及受控服务身份工具','C',(1.5,3,5),[6,23,24],'M08 M12','',[7,20,21,35], 'demo-only受理端口和可信服务JWT离线工具；固定申请回执，批准/驳回仍调用原回调，不增加公开审批入口。')
t(32,'部署产物、配置与管理员初始化','A',(2,4,6),[1,2,3,6],'M00 M12','',[1,2,36], '可执行jar、独立迁移步骤/运行账号、受控bootstrap命令、持久目录、HTTPS代理/服务管理与回退说明。')
t(33,'完整业务链路及并发/失败集成验收','B',(3,5,8),[12,18,19,22,25,27,30,31],'M00 M09 M10 M11 M12','',[3,4,7,15,19,22,23,24,25,28,30,31,32,33,37,38], '执行器/停用/来源关闭/导入双连接屏障与故障注入，逐操作契约覆盖，补跨模块遗漏。','各Issue已有自身失败/通过测试；本项只计算新增跨模块集成和系统验收，不代替各模块测试。')
t(34,'实际服务器部署、重启与演示验收','A',(1.5,3,5),[32,33],'M12','',[36,38], '真实目标服务器的版本/提交/校验和、迁移/健康/HTTP演示/重启持久化/恢复证据与未通过项。','服务器/数据库/访问方式未就绪则阻塞；本地Mock或生成部署文档不能关闭此Issue。')
t(35,'交接、任务证据与发布收尾','B',(1,2,3),[34],'M12','',[39], 'README/共享记忆、任务与验收追踪、发布说明及已知限制，所有未完成项保留开放。')

ACCEPTANCE_SCOPES = {
    1: '登记用户确认的JDK21/本机MySQL/已有云服务器和本人验收安排，补实际版本与使用方式；AT-01/39只为准备证据。无需他人批准，不要求永久JAVA_HOME/PATH；能用IDE或显式JDK运行构建即可。未运行的构建/数据库检查仍留待验证。',
    2: 'AT-01验固定Wrapper构建、配置失败和骨架健康；AT-37验公共DTO/Problem适配的样例。完整67入站契约由各接口任务及BE-33验收，不阻塞骨架先合并。',
    3: 'AT-02在此实际运行43表/触发器迁移及基础UUID/UTC/版本映射样例；各域实体映射由所属任务补齐，BE-33汇总集成，不要求本任务提前实现全部域实体。',
    4: '验证公共读写边界、两个连接和故障回滚；AT-33先验新鲜快照基础机制，有效权限查询的完整业务结果由BE-18/33覆盖。',
    5: '验证公共幂等状态机、租约栅栏、结果回放及当前授权回调；删除后重放用隔离测试夹具证明机制，不提前实现deleteChangeDraft。正式草稿端到端行为在BE-20/33补齐，不能把局部通过标记为完整AT-04通过。',
    31: '验证演示受理、可信JWT、正式结果回调及防重；自动执行结果由BE-25/33验收。未实现执行器时不得把已接收批准写成权限已生效。',
    32: '验证产物/配置/初始化及隔离环境演练；AT-36在本任务覆盖可交付部署能力，实际目标服务器、重启与恢复证据由BE-34完成。',
    33: '对照全部后端AT收集各模块证据并运行跨模块场景，补齐全67入站契约及AT-38全链路；AT-36目标服务器和AT-39发布证据分别仍由BE-34/35完成。',
    34: '在实际目标服务器完成完整AT-36/38，不以BE-32的本地演练替代；环境未就绪保持开放。',
    35: '汇总AT-39完整Issue/PR/commit/候选包与发布证据，检查其他后端AT均有实际结果；有未通过项时不得宣布全量发布完成。',
}
for task in TASKS:
    if task['id'] in B_MAIN_DELIVERED:
        task['implementationProgress'].update(mergedToMain=True,mainMergeCommit=B_MERGE_COMMIT,
            sourceCommit=B_SOURCE_COMMIT,pushVerified=True,verifiedRemoteCommit=B_MERGE_COMMIT,
            deliveryDecision='D-072')
    if task['id'] in B_CURRENT:
        task['implementationProgress'].update(mergedToMain=True,mainMergeCommit=B_CURRENT_MERGE_COMMIT,
            sourceCommit=B_CURRENT_SOURCE_COMMIT,pushVerified=True,verifiedRemoteCommit=B_CURRENT_MERGE_COMMIT,
            deliveryDecision='D-074')
    if task['phase']=='FOUNDATION':
        task['ownerAcceptance']['mainMergeCommit']=FOUNDATION_MERGE_COMMIT
    if task['id'] in {'BE-06','BE-07'}:
        task['implementationProgress']['mergedToMain']=True
        task['ownerAcceptance'].update(mergedToMain=True,mainMergeCommit=BUSINESS_MERGE_COMMIT)
    if task['id']=='BE-32':
        task['implementationProgress']['mergedToMain']=True
        task['ownerAcceptance'].update(mergedToMain=True,mainMergeCommit=BOOTSTRAP_MERGE_COMMIT,
                                       mainPrNumber=1,mainPrUrl=BOOTSTRAP_PR_URL)
        task['remotePrNumber']=1
    if task['id']=='BE-10':
        task['implementationProgress'].update(mergedToMain=True,mainMergeCommit=PEOPLE_MERGE_COMMIT,
            sourceCommit=PEOPLE_SOURCE_COMMIT,pushVerified=True,verifiedRemoteCommit=PEOPLE_MERGE_COMMIT)
    if task['id']=='BE-14':
        task['implementationProgress'].update(mergedToMain=True,mainMergeCommit=GRANT_MERGE_COMMIT,
            sourceCommit=GRANT_SOURCE_COMMIT,pushVerified=True,verifiedRemoteCommit=GRANT_MERGE_COMMIT,
            deliveryDecision='D-078')
    if task['id']=='BE-16':
        task['implementationProgress'].update(mergedToMain=True,mainMergeCommit=GRANT_MUTATION_MERGE_COMMIT,
            sourceCommit=GRANT_MUTATION_SOURCE_COMMIT,pushVerified=True,verifiedRemoteCommit=GRANT_MUTATION_MERGE_COMMIT,
            deliveryDecision='D-080')
    if task['id']=='BE-29':
        task['implementationProgress'].update(mergedToMain=True,mainMergeCommit=IMPORT_PRECHECK_MERGE_COMMIT,
            sourceCommit=IMPORT_PRECHECK_SOURCE_COMMIT,pushVerified=True,verifiedRemoteCommit=IMPORT_PRECHECK_MERGE_COMMIT,
            deliveryDecision='D-085')
    if task['id']=='BE-28':
        task['implementationProgress'].update(mergedToMain=True,mainMergeCommit=IMPORT_MERGE_COMMIT,
            sourceCommit=IMPORT_SOURCE_COMMIT,pushVerified=True,verifiedRemoteCommit=IMPORT_MERGE_COMMIT,
            deliveryDecision='D-076')
    if task['id']=='BE-08':
        task['implementationProgress'].update(mergedToMain=True,mainMergeCommit=SCOPE_MERGE_COMMIT,
            sourceCommit=SCOPE_SOURCE_COMMIT,pushVerified=True,verifiedRemoteCommit=SCOPE_MERGE_COMMIT)
        task['ownerAcceptance'].update(mergedToMain=True,mainMergeCommit=SCOPE_MERGE_COMMIT,
            mainPrNumber=2,mainPrUrl=SCOPE_PR_URL)
        task['remotePrNumber']=2
    if task['id']=='BE-09':
        task['implementationProgress'].update(mergedToMain=True,mainMergeCommit=SCOPE_CREATE_MERGE_COMMIT,
            sourceCommit=SCOPE_CREATE_SOURCE_COMMIT,pushVerified=True,verifiedRemoteCommit=SCOPE_CREATE_MERGE_COMMIT,
            deliveryDecision='D-083')
        task['ownerAcceptance'].update(mergedToMain=True,mainMergeCommit=SCOPE_CREATE_MERGE_COMMIT,
            mainPrNumber=3,mainPrUrl=SCOPE_CREATE_PR_URL)
        task['remotePrNumber']=3
    task['acceptanceScope'] = ACCEPTANCE_SCOPES.get(int(task['id'][-2:]),
        '完成所列AT中本任务交付/自有API对应的行为与边界，并记录具体断言；跨模块部分由对应任务补充并在BE-33汇总。不能将局部通过标记为整个AT通过。')

def dump(name, value):
    (ROOT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')

def write(name, text):
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\nD-074（2026-10-20）B 状态：BE-17/12 已由 1fb95ad 无冲突合入并交付远程 main/3103b35，任务分支保留，完整 SHA 回查一致，合并树等于已验证源。完整 93 普通＋169 真实 MySQL/HTTP 通过，本轮合并后 93 普通测试再次通过。任务待本人确认，不代记 OWNER_ACCEPTED。BE-11/13/15 原交付保留；共 9 项 OWNER_ACCEPTED、1 项 IMPLEMENTED、5 项 IN_PROGRESS、20 项 PLANNED。AT-09/10/11/12/31/32 仅局部验证，不代表全部消费者/OA/BE-33 完成。BE-18 仍缺 BE-14，BE-33/35 仍开放。详见 [BE-17/12 实施及交付](BE-17-12-PREVIEW-CATALOG.md)，下方更早数量/待交付说明为历史。\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\nD-076（2026-10-20）：BE-28 已接入最新 BE-17/12 后完整110离线＋178真实MySQL/HTTP通过，并由233f633保留历史合入远程main/4b384fb，远程SHA回查一致，分支保留。Git连接失败后使用官方API按本地SHA原样交付、force=false。BE-28仍待本人确认，mergedToMain=true；交付不代记人工验收。当前共9项OWNER_ACCEPTED、2项IMPLEMENTED、5项IN_PROGRESS、19项PLANNED。D原BE-28实施编号因与B冲突改为D-075，原事实保留；下方旧数量/未授权为历史。见 [BE-28](BE-28-IMPORT-FILES.md)。\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\nD-078（2026-10-20）：D明确要求先合并推送，BE-14来源367cdae已保留历史合入远程main/c7435d9，分支已推送并保留，远程完整SHA回查一致，合并树等于验证来源。沿用完整112离线＋193真实MySQL/HTTP证据，本轮静态检查通过，未重复执行Java/SQL。BE-14仍为IMPLEMENTED、ownerAccepted=false、mergedToMain=true，BE-28仍已交付待本人确认；下方未授权/待主线为历史。AT-13补本任务证据，AT-14/15仅读取局部验证，BE-16/26执行竞争不在本任务。当前共9项OWNER_ACCEPTED、3项IMPLEMENTED、5项IN_PROGRESS、18项PLANNED，下方旧状态/数量为历史。见[BE-14](BE-14-GRANT-BASELINES.md)。\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\nD-079（2026-10-20）：D要求开始下一项，BE-16在codex/be-16-grant-mutations（基线main/a860dc8）按设计/TDD实现BaselineWriter、GrantApplier、SourceCloser及AffectedFacts。完整112离线＋215真实MySQL/HTTP通过，失败/错误/跳过0；专属实例正常关闭，随机库/账号/测试密钥目录0，用户原MySQL84仍Running。BE-16为IMPLEMENTED、ownerAccepted=false、mergedToMain=false，未合并推送；BE-14/28仍已交付待本人确认。AT-14/15/22/41只补底层局部证据，不代表C执行器、真实OA或BE-26生命周期完成。当前9项OWNER_ACCEPTED、4项IMPLEMENTED、5项IN_PROGRESS、17项PLANNED，下方旧数量/边界为历史。见[BE-16](BE-16-GRANT-MUTATIONS.md)。\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\nD-080（2026-10-20）：D明确“先完成合并推送”，BE-16来源dba2eaa已无冲突保留历史合入远程main/7ab021a，合并树等于已验证来源，任务分支推送并保留，远程完整SHA回查一致。核对112离线＋215真实MySQL/HTTP报告与jar摘要一致，本轮静态复核通过，无Java/SQL重跑或部署。BE-16为IMPLEMENTED、mergedToMain=true、ownerAccepted=false；BE-14/28人工验收继续开放。当前9项OWNER_ACCEPTED、4项IMPLEMENTED、5项IN_PROGRESS、17项PLANNED；下方未授权/待主线为历史，不自动开新任务。见[BE-16](BE-16-GRANT-MUTATIONS.md)。\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\nD-081（2026-10-20）：A按已交付BE-05/08/17开始BE-09，创建预览及范围/系统集合/任命的原子保存已实现，使用JPA写仓储、BE-17固定预览与公共guard/幂等/审计。完整112离线＋205真实MySQL/HTTP及静态校验通过；当前IN_PROGRESS待A本人确认，未交付main；验证和本机步骤见[BE-09](BE-09-SCOPE-CREATION.md)。共9项OWNER_ACCEPTED、3项IMPLEMENTED、6项IN_PROGRESS、17项PLANNED，其他成员人工验收状态不变；AT-08/31/37只补本任务部分证据，不代表BE-27/33完成。下方旧状态/数量为历史。\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\nD-082（2026-10-20）：A明确“测试通过即可”，按已有112离线＋205真实MySQL/HTTP证据确认BE-09/41931f4，登记OWNER_ACCEPTED、待主线交付；未做本机演示，不虚构人工操作通过。远程最新072585e已交付D/BE-16，但本任务分支尚未整合该增量，BE-26及BE-33仍PLANNED。下一步建议交付BE-09；A余下BE-27等D/BE-26、BE-34等B/BE-33，不自动开工或推送。原本地BE-09编号D-079与远程D/BE-16冲突，统一为D-081，保留双方事实；下方待A确认/旧数量为历史。\n'+body

    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\nD-083实际交付（2026-10-20）：BE-09已通过[PR #3](https://github.com/Noah-hope/RBAC-admin/pull/3)合入远程main，来源0fee462503274fbed1ab03826c8d0723c9825c19，合并5f42a2cff11d68c49a4926373eeee97e3f5e8889。已回查closed/merged=true、远程完整SHA、双亲及合并树等于验证来源，本地main已同步、任务分支保留；OWNER_ACCEPTED、mergedToMain=true、remotePrNumber=3。接入BE-16后完整112普通＋227真实MySQL/HTTP及静态校验通过，专属资源清理关闭，原MySQL84仍Running；远程检查条目0，不记为CI通过。本轮交付不部署或开新任务，D各任务验收不代确认；下方待授权/待PR/待主线为历史。\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\nD-084（2026-10-20）：D要求下一项，BE-29在codex/be-29-import-precheck（main/072585e）按设计/TDD实现五个预检/报告操作，冻结候选ID-code、规范差异、映射、动作顺序及依赖，不改正式人员/授权数据。完整137离线＋228真实MySQL/HTTP通过，失败/错误/跳过0；随机库/账号/临时密钥文件目录0，专属实例正常关闭，原MySQL84仍Running。BE-29为IMPLEMENTED、ownerAccepted=false、mergedToMain=false；AT-25/26/27/29仅补预检局部证据，BE-26仍等C/BE-20，BE-30仍等A/BE-27及本任务主线交付。共9项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、16项PLANNED；既有验收状态保持，不合并推送或自动开新任务。下方旧状态/数量为历史，见[BE-29](BE-29-IMPORT-PRECHECK.md)。\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\nD-084编号协调（2026-10-20）：BE-29原本地实施记录D-081与远程A/BE-09重复，现统一为D-084；原实现80abde1及真实RED/GREEN事实保留，A的D-081/082/083、验收与PR#3交付均保持。已接入main/06a6b5c，业务代码无冲突；BE-29仍IMPLEMENTED、ownerAccepted=false，待集成回归与本次授权的主线交付。共10项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、15项PLANNED。\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\nD-084集成结果（2026-10-20）：接入远程main/06a6b5c（A已验收并经PR#3交付的BE-09）后，完整clean verify/mysql-check通过，137项离线/20组＋240项真实MySQL/HTTP/19组，失败/错误/跳过0，日志backend/.tmp/be29-main-integration-verify.log。集成未改BE-29业务代码，静态追踪通过；BE-29保持IMPLEMENTED待D人工验收，本次主线交付由用户“合并推送吧”明确授权，实际SHA成功后补。\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\nD-085实际交付（2026-10-20）：成员D明确“合并推送吧”，BE-29接入A已交付BE-09后，来源db3419687f8dc43b1959564ddc19b0e7d32826ed已无冲突no-ff合入远程main/6506cda54c4898e736a2e730ba001294f8de4c2c，合并树等于已完整验证来源，任务分支codex/be-29-import-precheck保留。普通Git连接连续失败，改用官方GitHub Git API按本地对象原始SHA、force=false推送main和任务分支，远程两个完整SHA均回查一致。完整137离线＋240真实MySQL/HTTP及静态校验通过，专属资源清理关闭，原MySQL84仍Running；BE-29为IMPLEMENTED、mergedToMain=true、ownerAccepted=false，既有人工验收保持。D原实施编号D-081与A冲突已统一为D-084；A的D-081/082/083及PR#3交付保留。共10项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、15项PLANNED；不强推或部署，下方未授权/待主线为历史。\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\nD-086（2026-10-20）：本轮用户明确负责C并要求先列计划再执行。BE-20在BE-20-角色与变更草稿（main/86089d2）先设计及真实HTTP/MySQL RED，再实现新角色/三类草稿/完整目标/原子占用/编辑删除/当前授权幂等重放，以及供D调用的最小WorkflowStateWriter。验证及交接见[BE-20](BE-20-CHANGE-DRAFTS.md)。BE-20为IMPLEMENTED、ownerAccepted=false、mergedToMain=false；此前A/D认领和验收事实保留。BE-19及BE-21～25/31仍PLANNED，BE-26仍须本任务主线交付；不代记验收，不合并推送或自动开展其他任务。共10项OWNER_ACCEPTED、6项IMPLEMENTED、5项IN_PROGRESS、14项PLANNED，下方旧状态/数量为历史。\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\n'+'D-088实际交付（2026-10-21）：C在确认BE-20及逐项合并方案后明确“可以”，接受既有137离线＋265真实MySQL/HTTP验证并授权交付。来源310313a07e0321f58a5b1b03d3d59231fe2a17a6已无冲突no-ff合入远程main/a9b214cce38d4396120b3902ce19f2b7440bf4f1，合并树等于验证来源；main与BE-20-角色与变更草稿已原子推送并完整SHA回查一致，任务分支保留。BE-20为OWNER_ACCEPTED、mergedToMain=true，本机人工演示未进行；本轮仅复核原报告/jar摘要和静态追踪，不重跑代码未变的Java/SQL。D/BE-26技术依赖已交付，其他C任务仍开放，不自动开工或部署。共11项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、14项PLANNED。下方待验收/待交付为历史，见[BE-20](BE-20-CHANGE-DRAFTS.md)。'+'\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\n'+'D-089（2026-10-21）：C要求继续其他任务，BE-19/22在BE-19-22-审计与申请查询（main/09abaa2）先设计及真实HTTP/MySQL RED，再实现审计列表/详情和申请列表/详情/时间线。当前单项完整范围先SQL过滤再统计/分页，关联主体与缺损目标投影不能绕过授权，审计仅投影历史公开字段。实现与实际验证见[BE-19/22](BE-19-22-AUDIT-REQUEST-QUERIES.md)。两项为IMPLEMENTED、ownerAccepted=false、mergedToMain=false，不代记人工验收或延用BE-20特定交付授权。其他C写链路仍PLANNED，已交付BE-20状态保持；共11项OWNER_ACCEPTED、7项IMPLEMENTED、5项IN_PROGRESS、12项PLANNED。下方旧状态/数量为历史。'+'\n'+body
        text = text.split('\n',1)[0]+'\n\n'+'D-090（2026-10-21）：C明确“确认，然后继续完成剩下的任务”，接受BE-19/22既有137离线＋284真实MySQL/HTTP证据；人工演示未进行。来源cc88885f2c97df838720fc2f7f0e524d72e56351已no-ff合入main/cf27f7f910bf5e904c89542f56e3f56fddd884d9，合并树等于验证来源，main及任务分支原子推送且远程完整SHA回查一致。两项OWNER_ACCEPTED、mergedToMain=true。后续按BE-21→23→24→25→31连续实施，依赖沿同一任务分支继承，各项实现、本人确认、主线交付分开记录；不代其他成员验收、不部署。共13项OWNER_ACCEPTED、5项IMPLEMENTED、5项IN_PROGRESS、12项PLANNED。下方待确认/待交付为历史。'+'\n'+text.split('\n',1)[1]
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text = title+'\n\n'+'D-091（2026-10-21）：C在明确确认后要求继续全部剩余任务，于BE-21-31-申请审批与执行（main/7914788）依次设计/TDD实现BE-21/23/24/25/31。固定七字段RFC8785包、预览/无变化/占用、可靠OA待办/租约/三层回调防重、整份原子执行/失败记录/启动与周期恢复、显式demo适配器和短期服务JWT工具已实现；完整139离线＋316真实MySQL/HTTP及静态校验通过，失败/错误/跳过0。专属测试资源已清理关闭，用户既有库未操作。五项IMPLEMENTED、ownerAccepted=false、mergedToMain=false；既有BE-19/20/22负责人确认与主线交付保持，C八项实现均具备，剩本轮五项本人验收/主线交付。真实外部OA及服务器/全系统验收不在本轮，其他成员状态保持；共13项OWNER_ACCEPTED、10项IMPLEMENTED、5项IN_PROGRESS、7项PLANNED。证据见BE-21-31-APPROVAL-WORKFLOW.md，下方旧状态为历史。'+'\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text=title+'\n\n'+'D-092（2026-10-21）：C明确数据库暂不处理、先提交合并既有工作。来源adab1861906dc7d973f796961249e1ef87563e29已无冲突no-ff合入远程main/311aa5d5cbc3d7bfbd064888b4172d48de0a0e99，合并树等于已验证来源；main与BE-21-31-申请审批与执行原子推送，远程完整SHA回查一致，任务分支保留。BE-21/23/24/25/31为IMPLEMENTED、mergedToMain=true、ownerAccepted=false；合并授权不代记本机人工验收。核对既有139离线＋316真实MySQL/HTTP报告及jar摘要一致，本轮代码未变，仅静态校验，不重新运行Java/SQL，不配置或连接本机数据库、不部署。C八项代码均已交付主线，本机环境与人工演示仍待后续；下方未交付为历史。'+'\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body = text.partition('\n')
        text=title+'\n\nD-093（2026-10-21）：B 在已交付 BE-14 后按用户“那就改”实现 BE-18 两个业务权限接口，分支 codex/be-18-effective-permissions、基线 main/7914788。完整来源并集、独立进入/菜单/操作、当前身份与主库新快照、未知与故障分离；不改迁移/契约，不代记验收或主线交付。实际验证见 [BE-18](BE-18-EFFECTIVE-PERMISSIONS.md)。当前 13 项 OWNER_ACCEPTED、5 项 IMPLEMENTED、6 项 IN_PROGRESS、11 项 PLANNED；B 六项业务开发已实现待本人确认，BE-33/35 仍开放。下方 BE-18 等依赖/旧数量为历史。\n'+body
    if name in {'MODULES.md','TRACEABILITY.md','TEST-PLAN.md','TASKS.md','TEAM-ASSIGNMENT.md','IMPLEMENTATION-PLAN.md'}:
        title, _, body=text.partition('\n')
        text=title+'\n\nD-094最新集成（2026-10-21）：B 已接入 C 工作流 main/6b9a02e，修正正式审批预览 /previews 路径，并补真实配置审批→授予→业务查询→撤销串联、发布证据工具与交接清单。原 B D-091/092 为避免与 C 重号统一为 D-093/094，源提交与验收事实保留。BE-33 仍缺 BE-27/30（传递依赖 BE-26），BE-35 缺 BE-34 真实服务器验收，两项保持 PLANNED、单列 preparationProgress，不伪造 AT-38/39 或完整项目通过。本地记录显示 60/67 入站操作已有实现，不等于全部契约验收通过。实际结果见 [集成与交接准备](BE-33-35-INTEGRATION-HANDOFF.md)，未推送/合并远程或部署。下方基线 7914788/缺 C 模块为历史。\n'+body
    (ROOT/name).write_text(text.rstrip()+'\n',encoding='utf-8',newline='\n')

def load_inputs():
    api_path=REPO/'docs/03-api/openapi.yaml'
    return yaml.safe_load(api_path.read_text(encoding='utf-8')),json.loads((REPO/'docs/04-database/traceability.json').read_text(encoding='utf-8')),hashlib.sha256(api_path.read_bytes()).hexdigest()

def schema_refs(value, api, visited=()):
    refs=set()
    if isinstance(value,dict):
        ref=value.get('$ref','')
        if ref.startswith('#/components/schemas/'):
            refs.add(ref.rsplit('/',1)[1])
        elif ref.startswith('#/') and ref not in visited:
            target=api
            for key in ref[2:].split('/'):target=target[key]
            refs |= schema_refs(target,api,visited+(ref,))
        for key,item in value.items():
            if key!='$ref':refs |= schema_refs(item,api,visited)
    elif isinstance(value,list):
        for item in value:refs |= schema_refs(item,api,visited)
    return refs

def generate():
    api,old_trace,digest=load_inputs()
    modules=[dict(id=m[0],package=m[1],name=m[2],ownerSlot='USER' if m[0]=='M00' else (None if m[0] in SHARED_MODULE_OWNERS else m[3]),
        ownerSlots=SHARED_MODULE_OWNERS.get(m[0], ['USER' if m[0]=='M00' else m[3]]),
        suggestedOwnerSlot=m[3],assignmentStatus='OWNER_CONFIRMED' if m[0]=='M00' else ('SHARED_BY_TASK' if m[0] in SHARED_MODULE_OWNERS else 'ASSIGNED'),
        dependencies=m[4],responsibility=m[5]) for m in MODULES]
    phase_plan=dict(revision='1.4',decision='D-039',organizationDecision='D-038',collaborationDecision='D-051',deliveryDecision='D-053',assignmentDecision='D-055',implementationDecision='D-065',foundation=dict(
        ownerSlot='USER',taskIds=sorted(FOUNDATION_IDS),status='OWNER_ACCEPTED',reviewRequired=False,reviewPolicy='OWNER_ACCEPTANCE',
        acceptanceOwnerSlot='USER',acceptanceGate='FOUNDATION_ACCEPTED',reviewerSlot=None,
        ownerAcceptedTaskIds=sorted(FOUNDATION_IDS),
        ownerAcceptance=dict(acceptedBy='USER',date='2026-10-18',source='USER_MESSAGE',
            message='确认基座通过，合并 main 并推送',acceptedSourceCommit=FOUNDATION_SOURCE_COMMIT,
            evidence='FOUNDATION-REVIEW.md'),
        mainDelivery=dict(branch='main',mergeCommit=FOUNDATION_MERGE_COMMIT,sourceCommit=FOUNDATION_SOURCE_COMMIT,
            remote='origin',remoteRef='refs/heads/main',pushVerified=True,
            verifiedRemoteCommit=FOUNDATION_MERGE_COMMIT,evidence='FOUNDATION-REVIEW.md')),business=dict(
        taskIds=[t['id'] for t in TASKS if t['phase']=='BUSINESS'],ownerSlot=None,
        ownerSlots=list(BUSINESS_ASSIGNMENTS),assignmentStatus='ASSIGNED',
        members=[dict(alias=owner,area=BUSINESS_AREAS[owner],realName=None,githubLogin=None,
            claimedBy='USER' if owner in {'A','C','D'} else None,
            claimDecision='D-056' if owner=='A' else ('D-064' if owner=='D' else ('D-086' if owner=='C' else None)),
            repositoryAccessVerified=False,localEnvironmentVerified=owner=='D') for owner in BUSINESS_ASSIGNMENTS],
        assignmentSource=dict(date='2026-10-18',source='USER_REQUEST',evidence='TEAM-ASSIGNMENT.md',
            message='现在给出四个人的分工，并且形成文档，说明ABCD四个人分别负责哪一块'),
        startConditions=BUSINESS_START,reviewPolicy='OWNER_ACCEPTANCE',
        reviewRequired=False,reviewerSlot=None,acceptanceOwnerPolicy='TASK_OWNER',
        mainDelivery=dict(taskIds=['BE-06','BE-07'],decision='D-060',date='2026-10-19',
            branch='main',sourceCommit=BUSINESS_SOURCE_COMMIT,mergeCommit=BUSINESS_MERGE_COMMIT,
            remote='origin',remoteRef='refs/heads/main',pushVerified=True,
            verifiedRemoteCommit=BUSINESS_MERGE_COMMIT,evidence='BE-07-ACCOUNTS.md')),
        gateStatus='FOUNDATION_ACCEPTED',
        gateEvidence=[dict(id=f'F-{n:02}',status='VERIFIED',
            evidence='FOUNDATION-REVIEW.md',date='2026-10-18',
            scope='PUBLIC_FOUNDATION_ONLY' if n <= 5 else 'OWNER_ACCEPTANCE_AND_MAIN_DELIVERY')
            for n in range(1,7)],
        reviewRule='D-051：所有任务由唯一负责人核对测试和自检证据并确认完成，不设置独立评审人；技术依赖、真实验证和主线交付要求保留')
    phase_plan['business']['additionalMainDeliveries']=[dict(taskIds=['BE-32'],decision='D-063',date='2026-10-19',
        branch='main',sourceCommit=BOOTSTRAP_SOURCE_COMMIT,mergeCommit=BOOTSTRAP_MERGE_COMMIT,
        remote='origin',remoteRef='refs/heads/main',pushVerified=True,verifiedRemoteCommit=BOOTSTRAP_MERGE_COMMIT,
        prNumber=1,prUrl=BOOTSTRAP_PR_URL,prState='MERGED',evidence='BE-32-DEPLOYMENT.md')]
    phase_plan['business']['additionalMainDeliveries'].append(dict(taskIds=['BE-10'],decision='D-065',date='2026-10-19',
        branch='main',sourceCommit=PEOPLE_SOURCE_COMMIT,mergeCommit=PEOPLE_MERGE_COMMIT,
        remote='origin',remoteRef='refs/heads/main',pushVerified=True,verifiedRemoteCommit=PEOPLE_MERGE_COMMIT,
        ownerAccepted=False,deliveryMode='LOCAL_MERGE_AND_PUSH',evidence='BE-10-PEOPLE.md'))
    phase_plan['business']['additionalMainDeliveries'].append(dict(taskIds=['BE-08'],decision='D-069',date='2026-10-19',
        branch='main',sourceCommit=SCOPE_SOURCE_COMMIT,mergeCommit=SCOPE_MERGE_COMMIT,
        remote='origin',remoteRef='refs/heads/main',pushVerified=True,verifiedRemoteCommit=SCOPE_MERGE_COMMIT,
        ownerAccepted=True,prNumber=2,prUrl=SCOPE_PR_URL,prState='MERGED',evidence='BE-08-MANAGEMENT-SCOPE.md'))
    phase_plan['business']['additionalMainDeliveries'].append(dict(taskIds=sorted(B_MAIN_DELIVERED),decision='D-072',date='2026-10-20',
        branch='main',sourceCommit=B_SOURCE_COMMIT,mergeCommit=B_MERGE_COMMIT,
        remote='origin',remoteRef='refs/heads/main',pushVerified=True,verifiedRemoteCommit=B_MERGE_COMMIT,
        ownerAccepted=False,deliveryMode='LOCAL_MERGE_AND_OFFICIAL_GIT_API_PUSH',evidence='B-MAIN-INTEGRATION.md'))
    phase_plan['business']['additionalMainDeliveries'].append(dict(taskIds=sorted(B_CURRENT),decision='D-074',date='2026-10-20',
        branch='main',sourceCommit=B_CURRENT_SOURCE_COMMIT,mergeCommit=B_CURRENT_MERGE_COMMIT,
        remote='origin',remoteRef='refs/heads/main',pushVerified=True,verifiedRemoteCommit=B_CURRENT_MERGE_COMMIT,
        ownerAccepted=False,deliveryMode='LOCAL_MERGE_AND_OFFICIAL_GIT_API_PUSH',evidence=B_CURRENT_EVIDENCE))
    phase_plan['business']['additionalMainDeliveries'].append(dict(taskIds=['BE-28'],decision='D-076',date='2026-10-20',
        branch='main',sourceCommit=IMPORT_SOURCE_COMMIT,mergeCommit=IMPORT_MERGE_COMMIT,
        remote='origin',remoteRef='refs/heads/main',pushVerified=True,verifiedRemoteCommit=IMPORT_MERGE_COMMIT,
        ownerAccepted=False,deliveryMode='LOCAL_MERGE_AND_OFFICIAL_GIT_API_PUSH',evidence='BE-28-IMPORT-FILES.md'))
    phase_plan['business']['additionalMainDeliveries'].append(dict(taskIds=['BE-14'],decision='D-078',date='2026-10-20',
        branch='main',sourceCommit=GRANT_SOURCE_COMMIT,mergeCommit=GRANT_MERGE_COMMIT,
        remote='origin',remoteRef='refs/heads/main',pushVerified=True,verifiedRemoteCommit=GRANT_MERGE_COMMIT,
        ownerAccepted=False,deliveryMode='LOCAL_MERGE_AND_PUSH',evidence='BE-14-GRANT-BASELINES.md'))
    phase_plan['business']['additionalMainDeliveries'].append(dict(taskIds=['BE-16'],decision='D-080',date='2026-10-20',
        branch='main',sourceCommit=GRANT_MUTATION_SOURCE_COMMIT,mergeCommit=GRANT_MUTATION_MERGE_COMMIT,
        remote='origin',remoteRef='refs/heads/main',pushVerified=True,verifiedRemoteCommit=GRANT_MUTATION_MERGE_COMMIT,
        ownerAccepted=False,deliveryMode='LOCAL_MERGE_AND_PUSH',evidence='BE-16-GRANT-MUTATIONS.md'))
    phase_plan['business']['additionalMainDeliveries'].append(dict(taskIds=['BE-09'],decision='D-083',date='2026-10-20',
        branch='main',sourceCommit=SCOPE_CREATE_SOURCE_COMMIT,mergeCommit=SCOPE_CREATE_MERGE_COMMIT,
        remote='origin',remoteRef='refs/heads/main',pushVerified=True,verifiedRemoteCommit=SCOPE_CREATE_MERGE_COMMIT,
        ownerAccepted=True,prNumber=3,prUrl=SCOPE_CREATE_PR_URL,prState='MERGED',evidence='BE-09-SCOPE-CREATION.md'))
    phase_plan['business']['additionalMainDeliveries'].append(dict(taskIds=['BE-29'],decision='D-085',date='2026-10-20',
        branch='main',sourceCommit=IMPORT_PRECHECK_SOURCE_COMMIT,mergeCommit=IMPORT_PRECHECK_MERGE_COMMIT,
        remote='origin',remoteRef='refs/heads/main',pushVerified=True,verifiedRemoteCommit=IMPORT_PRECHECK_MERGE_COMMIT,
        ownerAccepted=False,deliveryMode='LOCAL_MERGE_AND_OFFICIAL_GIT_API_PUSH',evidence='BE-29-IMPORT-PRECHECK.md'))
    phase_plan['business']['additionalMainDeliveries'].append(dict(taskIds=['BE-20'],decision='D-088',date='2026-10-21',
        branch='main',sourceCommit='310313a07e0321f58a5b1b03d3d59231fe2a17a6',mergeCommit='a9b214cce38d4396120b3902ce19f2b7440bf4f1',
        remote='origin',remoteRef='refs/heads/main',pushVerified=True,verifiedRemoteCommit='a9b214cce38d4396120b3902ce19f2b7440bf4f1',
        ownerAccepted=True,deliveryMode='LOCAL_MERGE_AND_ATOMIC_PUSH',evidence='BE-20-CHANGE-DRAFTS.md'))
    phase_plan['business']['additionalMainDeliveries'].append(dict(taskIds=['BE-19','BE-22'],decision='D-090',date='2026-10-21',
        sourceCommit='cc88885f2c97df838720fc2f7f0e524d72e56351',mergeCommit='cf27f7f910bf5e904c89542f56e3f56fddd884d9',
        branch='main',remote='origin',pushVerified=True,verifiedRemoteCommit='cf27f7f910bf5e904c89542f56e3f56fddd884d9',
        ownerAccepted=True,evidence='BE-19-22-AUDIT-REQUEST-QUERIES.md'))
    phase_plan['business']['additionalMainDeliveries'].append(dict(taskIds=['BE-21','BE-23','BE-24','BE-25','BE-31'],decision='D-092',date='2026-10-21',
        sourceCommit='adab1861906dc7d973f796961249e1ef87563e29',mergeCommit='311aa5d5cbc3d7bfbd064888b4172d48de0a0e99',remote='origin',branch='main',pushVerified=True,verifiedRemoteCommit='311aa5d5cbc3d7bfbd064888b4172d48de0a0e99',ownerAccepted=False,evidence='BE-21-31-APPROVAL-WORKFLOW.md'))
    dump('delivery-phases.json',phase_plan)
    dump('modules.json',modules);dump('acceptance.json',CASES);dump('issues.json',TASKS)
    lines=['# 模块结构与维护责任','','修订1.4/D-055：基座已确认并纳入主线，30项任务已登记A～D成员代号，实名与GitHub账号映射待补。D-056用户认领A，BE-06为OWNER_ACCEPTED（D-057，已由D-060交付main）、BE-07为OWNER_ACCEPTED（D-059，已由D-060交付main）、BE-32为OWNER_ACCEPTED（D-062，已由D-063/PR#1交付main），BE-10为IMPLEMENTED（六个查询接口及内部读写端口已实现，已由D-065交付main，待负责人验收），BE-08为OWNER_ACCEPTED（D-068，已由D-069/PR#2交付main），其余25项PLANNED，由负责人自行确认完成（D-051）。分工详见[四人分工](TEAM-ASSIGNMENT.md)。公共包前缀`com.adgn.backend`、13个逻辑模块及技术依赖不变。','','| 模块 | 包后缀/负责人 | 编译依赖 | 职责与主要接口 |','| --- | --- | --- | --- |']
    for m in modules:lines.append(f"| {m['id']} {m['name']} | `{m['package']}` / {'用户（基座）' if m['ownerSlot']=='USER' else (m['ownerSlot'] or '、'.join(m['ownerSlots'])+'按任务分别负责')} | {','.join(m['dependencies']) or '无'} | {m['responsibility']} |")
    lines+=['','M00的公共能力由用户实现；M11的读事务约定、M12的错误/配置/装配基础也属于BE-02/04的基座部分，不意味着全部查询/HTTP业务由用户承包。M00中规范化codec先明确接口，固定申请的JCS实现和向量验证仍归BE-21。','','M11采用独立SQL/DTO投影，不引用各域JPA实体或命令模块。业务阶段按A～D的对应任务分别维护身份、人员来源、申请、权限投影，避免一个巨大QueryService成为共用编辑热点。','','共同事务与接口约定见[ARCHITECTURE.md](ARCHITECTURE.md)，具体步骤见[CORE-FLOWS.md](CORE-FLOWS.md)，阶段交接见[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)。']
    write('MODULES.md','\n'.join(lines))
    task_by_op={}
    for task in TASKS:
        for op in task['operations']:
            if op in task_by_op:raise AssertionError('操作多主责 '+op)
            task_by_op[op]=task
    primary={
        'login':'M01','currentSession':'M01','logout':'M01','changeOwnPassword':'M01','myAppointments':'M03',
        'listAccounts':'M01','createAccount':'M01','getAccount':'M01','setAccountState':'M01','resetAccountPassword':'M01',
        'createRole':'M07','listRoles':'M05','getRole':'M05','updateRoleMetadata':'M05',
        'authorizationQuery':'M11','permissionCheck':'M11','listPreviewEmployees':'M11','listPreviewTargets':'M11','listAudit':'M11','getAudit':'M11',
        'sourceClosePreview':'M10','closeSource':'M10','replaceScope':'M10','scopeEditPreview':'M10',
        'employeeStatePreview':'M10','setEmployeeState':'M10',
        'retryOaDelivery':'M08','receiveOaResult':'M08','deliverOaSubmission':'M08'}
    for op in 'listSystems createSystem getSystem updateSystem systemStatePreview listDirectoryNodes listPermissions catalogPreview saveCatalog'.split():primary[op]='M04'
    for op in 'listCompanies listDepartments listPositions listEmployees getEmployee listEmployments'.split():primary[op]='M02'
    for op in 'listScopes getScope listScopeAppointments createScope scopeCreatePreview'.split():primary[op]='M03'
    for op in 'listGrants grantBaselines sourceBaselines'.split():primary[op]='M06'
    for op in 'listChangeRequests createChangeDraft getChangeRequest updateChangeDraft deleteChangeDraft changePreview submitChange listChangeEvents'.split():primary[op]='M07'
    for op in 'uploadImportFile getImportFile precheckImport listImportBatches getImportBatch listImportIssues commitImport listImportPlanChanges'.split():primary[op]='M10'
    op_map=[]
    for row in old_trace:
        op=row['operationId'];task=task_by_op[op];mid=primary[op]
        assert mid in task['modules'],(op,mid)
        operation=api['webhooks' if row['direction']=='OUTBOUND' else 'paths'][row['path']][row['method'].lower()]
        parameter_names=[]
        for parameter in operation.get('parameters',[]):
            if '$ref' in parameter:
                parameter=api['components']['parameters'][parameter['$ref'].rsplit('/',1)[1]]
            parameter_names.append(dict(name=parameter['name'],location=parameter['in'],required=parameter.get('required',False)))
        op_map.append(row|dict(module=mid,issue=task['id'],ownerSlot=task['ownerSlot'],
            suggestedOwnerSlot=task['suggestedOwnerSlot'],phase=task['phase'],
            assignmentStatus=task['assignmentStatus'],startConditions=task['startConditions'],
            acceptance=sorted(set(task['acceptance']+(['AT-37'] if row['direction']=='INBOUND' else ['AT-20']))),
            parameters=parameter_names,requestModels=sorted(schema_refs(operation.get('requestBody',{}),api)),
            successResponseModels={str(code):sorted(schema_refs(response,api)) for code,response in operation.get('responses',{}).items() if str(code).startswith('2')},
            status=('OWNER_ACCEPTED_MAIN_DELIVERED' if task['id'] in {'BE-06','BE-07','BE-08','BE-09','BE-19','BE-20','BE-22'} else ('IMPLEMENTED_MAIN_DELIVERED_PENDING_OWNER' if task['id'] in B_IMPLEMENTED | {'BE-10','BE-14','BE-28','BE-29'} else ('IMPLEMENTED_MAIN_DELIVERED_PENDING_OWNER' if task['id'] in {'BE-21','BE-23','BE-24','BE-25','BE-31'} else ('IMPLEMENTED_PENDING_OWNER_AND_MAIN' if task['id']=='BE-18' else 'PLANNED_NOT_IMPLEMENTED'))))))
    assert set(primary)==set(task_by_op)=={r['operationId'] for r in old_trace}
    for task in TASKS:
        owned=[r for r in op_map if r['issue']==task['id']]
        task['useCases']=sorted({uc for r in owned for uc in r['useCases']} | {uc for case in CASES if case['id'] in task['acceptance'] for uc in case['useCases']})
        task['tables']=sorted({table for r in owned for table in r['tables']})
        task['transactions']=sorted({tx for r in owned for tx in r['transactions']})
        task['invariants']=sorted({inv for r in owned for inv in r['invariants']})
    schema_tables=[t['name'] for t in json.loads((REPO/'docs/04-database/schema-model.json').read_text(encoding='utf-8'))]
    internal_tables={
        'BE-03':schema_tables,'BE-04':['mutation_guard','audit_event','audit_subject'],
        'BE-05':['http_idempotency'],'BE-15':['business_role','role_permission','role_configuration_history'],
        'BE-16':['authorization_source','grant_baseline','role_grant','grant_employment_evidence'],
        'BE-25':['change_request','change_target','oa_final_result','execution_success','execution_attempt','audit_event','role_permission','role_grant','grant_baseline'],
        'BE-31':['service_identity','change_request','oa_link']}
    for task in TASKS:
        task['tables']=sorted(set(task['tables'])|set(internal_tables.get(task['id'],[])))
    dump('issues.json',TASKS)
    dump('operation-map.json',op_map)
    trace=['# UC—API—模块—任务—验收追踪','',f'输入：需求V1.3/D-033、API{api["info"]["version"]}、数据库1.1；交接提交bd26465。契约SHA-256 `{digest}`。全部68操作各有唯一主责任务；按D-055登记A～D成员代号，实名/账号待补。操作、表及T/I不变，BE-06的4个操作已获负责人确认（D-057），已由D-060交付main，BE-07的5个操作已本人确认（D-059），已由D-060交付main，BE-10的6个操作已实现并交付main待D确认，BE-08的4个操作已获A确认（D-068）并由D-069/PR#2交付main，其余49项待实现；启动仍须满足门槛和原依赖。','','| UC | 方法/operationId | 路径/方向 | 模块/负责人 | 本地Issue | 验收 | 事务 |','| --- | --- | --- | --- | --- | --- | --- |']
    for r in op_map:trace.append(f"| {','.join(r['useCases'])} | {r['method']} {r['operationId']} | {r['path']} / {r['direction']} | {r['module']} / {r['ownerSlot']} | {r['issue']} | {','.join(r['acceptance'])} | {','.join(r['transactions'])} |")
    trace+=['','自动执行无管理员API，主责M09/BE-25；源码/成功凭据/状态事务设计见数据库T4及本目录流程。operation-map.json另保留逐操作表/I编号；本表不是已实现接口清单。']
    write('TRACEABILITY.md','\n'.join(trace))
    ops_with_body={op['operationId']:bool(op.get('requestBody')) for source in ('paths','webhooks') for item in api[source].values() for op in item.values() if isinstance(op,dict) and 'operationId'in op}
    handlers=['# HTTP处理入口与输入输出契约','','M12负责严格DTO/参数与响应映射，下表为模块入口契约，不是已创建的Java类；精确字段/错误分支仍以OpenAPI为准。公共头逐请求校验，JSON快照不直接映射实体。机器映射另保存所有参数名称、位置及必填性。','','| 入口 | 模块/本地任务 | body模型 | 成功状态/模型 | 参数 |','| --- | --- | --- | --- | --- |']
    for row in op_map:
        responses='；'.join(code+':'+(','.join(models) or '无body/内联结果') for code,models in row['successResponseModels'].items())
        params=', '.join(p['location']+':'+p['name']+('*' if p['required'] else '') for p in row['parameters']) or '无'
        handlers.append(f"| {row['method']} {row['operationId']} | {row['module']} / {row['issue']} | {','.join(row['requestModels']) or ('无body' if not ops_with_body.get(row['operationId']) else '内联multipart/JSON')} | {responses} | {params} |")
    handlers+=['','星号为参数必填；条件必填的管理员上下文仍需结合当前身份校验。204为空响应，幂等内部缓存保存安全空结果及授权事实。error Problem、fieldErrors和认证安全头由公共适配统一处理，不能从成功模型数量推断错误语义。']
    write('HANDLER-CONTRACTS.md','\n'.join(handlers))
    tests=['# 后端验收与TDD交接','','下表为验收目标：AT-01/37的BE-02部分、AT-02的基础持久化及AT-03/33的公共事务/快照及AT-04的公共幂等部分、AT-05/06/07的BE-06身份部分及AT-06/07的BE-07账号管理部分、AT-36的BE-32打包初始化/配置部分为PARTIALLY_VERIFIED，AT-13/27/29的BE-10局部行为为PARTIALLY_VERIFIED，AT-08/19的BE-08任命读取/上下文/覆盖部分为PARTIALLY_VERIFIED，其余26项DESIGNED_NOT_RUN；完整业务仍未验收，局部证据见[BE-02实施记录](BE-02-IMPLEMENTATION.md)、[持久化基础验证](BE-03-PERSISTENCE.md)、[公共事务](BE-04-TRANSACTIONS.md)、[幂等机制](BE-05-IDEMPOTENCY.md)、[身份实施](BE-06-IDENTITY.md)、[账号管理](BE-07-ACCOUNTS.md)及[部署初始化](BE-32-DEPLOYMENT.md)。其中AT-40为前端后置交接，不计本期后端验收；其余40项覆盖后端、部署及协作。实际执行需对应Issue按项目TDD技能记录失败→实现→通过，并在隔离MySQL8.4运行关键事务/并发/故障场景。','','D-038阶段约定：用户先验证BE-01～05的公共机制和对应acceptanceScope，F-01～06为基座交接检查，不新增业务测试编号。真实身份、草稿删除、审批、导入等端到端场景留业务分派后补齐；夹具证明机制不能标记整个AT或业务已通过。D-053已确认F-01～06，基座已合并并推送main/b68aa9a；见FOUNDATION-REVIEW.md。','','| 用例 | 行为/准备 | 必须观察到的结果 | 操作 | 评审追踪 |','| --- | --- | --- | --- | --- |']
    for case in CASES:tests.append(f"| {case['id']} {case['title']} | {case['fixture']} | {case['expected']} | {','.join(case['operations']) or '内部/部署'} | {case['reviewScenario'] or '新增验收准备'} |")
    tests+=['','## 测试层次与运行证据','','- 单元：纯策略/集合差异/规范化/目录无环，不以mock调用次数替代行为。','- HTTP：真实DTO/认证链/Problem与全部67入站响应Schema；单项管理上下文、旧token和三类JWT交叉调用。','- MySQL集成：Testcontainers MySQL8.4；无容器时使用团队批准的独立测试实例和唯一测试schema，绝不指向共享/业务库。H2不替代即时FK、JSON、触发器、生成槽与事务测试。','- 并发：两个独立连接/事务，以barrier控制锁取得/提交先后；测试方超时保证清理。既测执行先也测关权先，观察最终授权/epoch/success/audit，而不是仅断言抛异常。','- 故障：在业务第N项、audit、success及完成幂等结果前注入异常，另连接检查全回滚；结果可靠提交后模拟进程中断/响应丢失，恢复不覆盖成功。','- 部署：候选产物在实际目标服务器启动、迁移、重启、HTTPS与全链路演示；本机Mock不能替代。','','Issue的验收AT列表是最低必测行为；具体负责部分见每卡acceptanceScope。AT-37覆盖每项所属API，包括未单独列出的列表/详情/预览。基础任务可按其负责部分验收合并，不能据此把整个AT标为通过；BE-33汇总后端完整行为与契约证据，BE-34完成目标服务器，BE-35汇总发布证据。BE-01只核验准备清单，不要求先有构建或发布包，因而不形成实现前置条件循环。测试类和断言在对应模块实现时按writing-good-tests设计，BE-02已有公共适配的RED/GREEN及打包HTTP证据，见[BE-02-IMPLEMENTATION.md](BE-02-IMPLEMENTATION.md)；BE-06身份验证见[身份记录](BE-06-IDENTITY.md)，其余业务尚无运行结果。','','拟定构建命令为`./mvnw verify`，其中unit走Surefire、*IT走Failsafe、集成测试不得静默skip；另生成JUnit/契约验证报告。当前backend已有Wrapper及实际构建结果；BE-03准备后完整构建41项离线测试通过，包含原BE-02的30项，不等于真实数据库或完整业务验收通过，见BE-03-PREPARATION.md；最新BE-06身份与旧回归见BE-06-IDENTITY.md；此前BE-05幂等验证见BE-05-IDEMPOTENCY.md；51离线＋25引擎是此前BE-04结果，44离线＋9引擎是此前BE-03结果，领域映射/完整业务并发尚未验收。外部真实OA改为演示适配，信任及回调边界仍保留。']
    write('TEST-PLAN.md','\n'.join(tests))
    cards=['# 本地Issue草案与验收卡','','修订1.4/D-055四人分工，沿用D-053主线交付、D-051协作规则与D-050核对：D-053已确认F-01～06，基座已合并并推送main/b68aa9a（见FOUNDATION-REVIEW.md）；BE-01为OWNER_ACCEPTED（D-053整体交接确认，成员接入待办保留）；BE-02为OWNER_ACCEPTED（D-042/f911c60）；BE-03为OWNER_ACCEPTED（用户核对后回复继续，D-046/b8caeb2，见BE-03-PERSISTENCE）；BE-04为OWNER_ACCEPTED（D-047/58b556a，用户确认服务UP；公共事务/审计/快照证据见BE-04-TRANSACTIONS），BE-05为OWNER_ACCEPTED（D-049/d059d11，用户确认启用true及本机健康UP，见BE-05-IDEMPOTENCY），BE-06为OWNER_ACCEPTED（D-056实施、D-057本人确认，已由D-060交付main），BE-07为OWNER_ACCEPTED（D-059，已由D-060交付main）、BE-32为OWNER_ACCEPTED（D-062，已由D-063/PR#1交付main），BE-10为IMPLEMENTED（六个查询接口及内部读写端口已实现，已由D-065交付main，待负责人验收），BE-08为OWNER_ACCEPTED（D-068，已由D-069/PR#2交付main），其余25项PLANNED。BE-01～05已由D-053整体确认，合并main/b68aa9a并推送回查通过。BE-01～05为用户先实现的基座；BE-06～35已按D-055登记A～D唯一成员代号，assignmentStatus为ASSIGNED，BE-06为OWNER_ACCEPTED（D-057，已由D-060交付main）、BE-07为OWNER_ACCEPTED（D-059，已由D-060交付main）、BE-32为OWNER_ACCEPTED（D-062，已由D-063/PR#1交付main），BE-10为IMPLEMENTED（六个查询接口及内部读写端口已实现，已由D-065交付main，待负责人验收），BE-08为OWNER_ACCEPTED（D-068，已由D-069/PR#2交付main），其余25项PLANNED；历史会话用户已认领A，D会话已认领并完成本机基座配置；当前A会话实施BE-08；实名/GitHub账号与其他成员环境待补，详见TEAM-ASSIGNMENT.md。D-051统一所有任务reviewPolicy=OWNER_ACCEPTANCE，不设置独立评审人，reviewerSlot/suggestedReviewerSlot保留null仅作格式兼容、不是待填写；单列评审工时为0。suggestedOwnerSlot保留为历史建议组，以ownerSlot为准。GitHub/main已就绪，BE-32的PR#1和BE-08的PR#2均已合并；其余任务未登记远程PR，BE编号不是平台Issue号。','','业务任务启动须同时满足FOUNDATION_ACCEPTED、BUSINESS_OWNER_ASSIGNED和原dependencies；这两个阶段门槛不是额外业务需求。基座验收见[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)。开发估算含设计细化/TDD/自测/文档及负责人核对确认；不再单列他人评审，外部等待不计。','','通用DoD：对应设计已读且引用准确→业务/可执行基础能力RED失败原因留证→最小实现→GREEN及重构→对应安全/事务验收→任务负责人核对行为/权限/事务/恢复及测试证据后明确确认完成→集成与文档更新。环境/协作/发布核验使用真实记录。未运行验证不得勾选通过；团队依赖以合并且所属部分通过为准；D-058明确允许A在含已确认BE-06祖先的BE-07分支继续开发，此前不据此宣称主线已交付，现D-060已记录实际合并推送，完整跨域证据由BE-33/34/35汇总。秘密不进Issue正文。']
    for task in TASKS:
        est=task['estimateHours']
        cards += [f"\n## {task['id']} {task['title']}", '',
          f"- 状态：{task['status']}；阶段{task['phase']}；负责人{'用户（已确认），本人确认无需他人评审' if task['ownerSlot']=='USER' else task['ownerSlot']+'（成员代号），由负责人自检确认'}；模块{','.join(task['modules'])}。",
          f"- 分派依据：{task['assignmentDecision']}；启动条件{'、'.join(task['startConditions']) or '基座任务按下列依赖推进'}。",
          f"- 依赖：{','.join(task['dependencies']) or '无'}；开发估算{est['low']}/{est['likely']}/{est['high']}h（低/中/高，含自检和负责人确认；不另设评审人）。",
          f"- API：{', '.join(task['operations']) or '内部能力/部署，无新增公开业务API'}。",
          f"- 需求/数据库：{','.join(task['useCases']) or '全用例公共/部署基础'}；{','.join(task['transactions']) or '公共事务设计'}；{','.join(task['invariants']) or '见对应AT及数据库不变量'}。",
          f"- 交付：{task['deliverable']}",
          '- 验收：'+'；'.join(f"{case_id} {next(c['title'] for c in CASES if c['id']==case_id)}" for case_id in task['acceptance'])+'。',
          '- 验收边界：'+task['acceptanceScope'],
          f"- PR说明：关联真实Issue号；列明变更行为、契约/表版本、实际RED/GREEN命令及结果、待办；所有验收定义见TEST-PLAN。"]
        if task['notes']:cards.append('- 注意：'+task['notes'])
    cards+=['','## 前端后置事项','','前端正式实现不计入本次后端预算，不阻塞后端Issue。现有原型已交付，后续接入项见[FRONTEND-HANDOFF.md](FRONTEND-HANDOFF.md)；本轮只交接，不虚构远程FE Issue或把其实现计为已完成。']
    write('TASKS.md','\n'.join(cards))
    generate_assignment()
    generate_schedule()
    generate_diagrams(modules)
    partial='/'.join(c['id'] for c in CASES if c['status']=='PARTIALLY_VERIFIED')
    print(f'生成{len(modules)}模块、{len(TASKS)}本地后端Issue草案、{len(CASES)}项验收（{partial}局部验证）、{len(op_map)}操作追踪与可编辑图源；本生成器未创建工程/远程事项或执行SQL。')

def generate_assignment():
    lines = ['# 四人业务开发分工', '',
        '## 分工汇总', '',
        '| 成员 | 负责方向 | 对应任务 |',
        '| --- | --- | --- |',
        '| A | **身份与管理范围、运行交付**：登录、账号、任命、范围、员工状态联动、初始化与部署 | BE-06～09、BE-27、BE-32、BE-34 |',
        '| B | **系统、角色与功能权限**：系统目录、角色配置、影响预览、权限查询、集成验收 | BE-11～13、BE-15、BE-17～18、BE-33、BE-35 |',
        '| C | **申请、审批与审计**：草稿、提交申请、OA投递与回调、执行恢复、审计查询 | BE-19～25、BE-31 |',
        '| D | **组织、人员与授权来源**：组织任职、授权基线、授予撤销、来源关权、文件导入 | BE-10、BE-14、BE-16、BE-26、BE-28～30 |']
    lines += ['',
        '按D-055登记分工；D-056用户明确本人负责A，其他成员姓名及GitHub账号映射待补。每项BE只有一名负责人，由该负责人自行确认完成，不另设评审人。', '',
        '**当前：A实施BE-08（D-067），四查询与统一上下文/覆盖策略已获A确认（D-068）并由D-069/PR#2交付main；D的BE-10已由D-065交付main，D人工验收开放。** BE-07按D-058在包含确认祖先的任务分支实现，并已获D-059本人确认；其他成员依赖仍等已由D-060交付main。A已复用BE-10实施BE-08，后续BE-09仍须B/BE-17；B可推进BE-11，D可按依赖推进BE-28，C可先细化接口及测试场景；各任务就绪即可推进，不必等所有人完成同一批。', '',
        'BE-01～05公共基座已由用户完成并确认；本次分工覆盖BE-06～35共30项，BE-06为OWNER_ACCEPTED（D-057，已由D-060交付main）、BE-07为OWNER_ACCEPTED（D-059，已由D-060交付main）、BE-32为OWNER_ACCEPTED（D-062，已由D-063/PR#1交付main），BE-10为IMPLEMENTED（六个查询接口及内部读写端口已实现，已由D-065交付main，待负责人验收），BE-08为OWNER_ACCEPTED（D-068，已由D-069/PR#2交付main），其余25项PLANNED。下文“前置”列列出完整直接依赖；开发先后也可对照[README流程图](../../README.md#业务开发顺序)。', '',
        '每个业务功能由负责人贯穿实现：需要HTTP时负责Controller、请求/响应DTO、Service、所属领域的持久化或查询投影，以及测试和文档；内部能力无需新建Controller，也不要求每个BE重复创建Repository。详细UC/API/表/事务/验收映射见[任务卡](TASKS.md)和[追踪表](TRACEABILITY.md)。', '']
    notes = {
        'A': [
            '主要维护`identity`、`scope`，以及本人任务对应的`lifecycle`、查询投影和HTTP适配。身份是后台账号、任命及三类JWT；管理范围不是业务数据权限。',
            '先做BE-06，提供后台/业务系统/OA三类凭据的校验与后台会话能力。BE-07、BE-32在BE-06之后可按各自依赖推进；D的BE-10已交付main，BE-08已获A确认（D-068）并由D-069/PR#2交付main，BE-08已交付，BE-09须等B的BE-17。BE-27等待账号、范围及D的BE-26，BE-34最后等待BE-32和B的BE-33。',
            '交给B/C/D：认证后的当前主体、受控服务身份及当前任命/范围资格读取接口。调用者仍在基座事务边界中复核当前资格，不能只相信JWT中的旧范围。接口签名和失败语义先写入设计，再由各方分别实现和测试。',
            '需要D提供组织/任职读取、来源关闭与统一失效编排，C提供申请状态写端口，需要B提供影响快照。BE-27由A完成员工停用/恢复、范围替换的跨域入口，调用已有内部能力；与D共同维护lifecycle时按功能分文件，协调会话/任命/来源/申请的原子失效。',
            'BE-32交付部署配置与管理员初始化，BE-34负责实际服务器部署、重启和演示证据；本机启动成功不能代替服务器验收。公共HTTP装配的业务接线由A协调，各控制器仍归各自任务负责人。',
        ],
        'B': [
            '主要维护`catalog`、`role`，以及系统/角色/有效权限/影响预览对应的查询投影与HTTP适配。负责功能权限规则，不添加SELF/ALL、业务行过滤或数据范围字段。',
            '最早从BE-11开始，须等A的BE-06。BE-13在系统登记后推进，BE-15在角色读取后推进；BE-17所需A的BE-08和D的BE-10已交付main，可按依赖推进，BE-12再依赖BE-17。BE-18还要等D的BE-14。以上分支按任务前置推进，无需等待OA链路全部完成。',
            '交给C：BE-15的RoleConfigurationApplier，供BE-25在同一WriteContext中应用已批准角色配置；该内部应用器不新增公开业务接口，不自行提交独立事务。C负责执行编排，B负责目录和角色配置不变量。',
            '交给A/C/D：BE-17的影响计算、分页和依赖集合快照；明确输入、当前授权与快照过期/陈旧失败语义。BE-17只读表和投影，不反向依赖BE-12的目录命令，避免依赖环。BE-18使用D提供的来源/关系事实计算当前有效权限。',
            'BE-33由B负责跨模块整链路、并发和失败集成，发现缺陷交对应模块负责人修复；其他成员仍各自完成任务测试。BE-35在A的BE-34通过后汇总交接与发布证据，不能提前关闭尚未通过的业务验收。',
        ],
        'C': [
            '主要维护`change`、`approval`、`execution`和审计读取投影，负责草稿→固定申请→OA投递/结果→自动执行的流程。OA批准与实际执行是不同状态，模拟适配不能宣称真实OA已接通。',
            'BE-19须等A的BE-06/08；BE-20须等A的BE-08、B的BE-13和D的BE-14。BE-22可在BE-20后推进；BE-21还须B的BE-17，之后推进BE-23→BE-24。BE-25须再等B的BE-15和D的BE-16；BE-31在BE-23/24后开展。',
            'BE-20/21负责占用、冻结内容和授权事实，提交时同事务产生可靠投递事实。固定申请的RFC8785实现和向量验证在BE-21完成，不能将普通JSON序列化当成规范化实现。',
            'BE-23/24负责事务外可靠投递、关联及OA结果去重；复用A的OA服务身份认证。BE-25负责批准后全清单预检、执行及恢复，同一WriteContext中调用B的角色配置应用器和D的授予/撤销端口，保证业务、审计、成功凭据一起提交，禁止复制其他领域的更新逻辑。',
            '交给D/A：申请状态读取、合法状态转换及占用释放端口；D的BE-26负责统一失效能力的交付与关权编排，申请内部状态约束由C维护。grant基础模块只返回受影响事实，由上层编排调用申请端口，避免领域命令相互回调。审计列表/详情由C实现，所有成员的业务审计仍通过基座AuditAppender在本事务追加。',
        ],
        'D': [
            '主要维护`people`、`grant`和来源关闭/导入对应的`lifecycle`，负责组织、员工、任职、授权来源及关系基线。岗位/任职变化本身不自动授予业务角色。',
            '先做BE-10，为A提供组织/任职及资格所需事实。BE-14再等B的BE-11，BE-16在BE-14后推进。BE-28须等A的BE-06，可与来源能力并行；BE-29还须B的BE-17。BE-26等待BE-16、C的BE-20及BE-17；BE-30再等A的BE-27与BE-29。',
            '交给A/B/C：组织、员工、任职读取，来源/关系基线与当前资格，以及BE-16的GrantApplier/SourceCloser等受控写端口。内部原子操作接收现有WriteContext，不新增公开Controller、不自行提交；同源去重、异源并存、周期推进和不复活旧授权由D保证。',
            'BE-26的统一失效能力与直接关来源编排由D负责交付，内部状态转换/占用释放复用C提供的申请端口；涉及change文件与C协调，向A的BE-27提供可复用能力。A负责员工状态/范围替换入口，D负责来源规则和导入，不能各写一套来源关闭逻辑。',
            'BE-28只处理受限文件及元数据；BE-29冻结导入计划、差异和问题；BE-30调用A/C/D已提供的能力完成整批正式变更及失效。第N项或审计失败时全批回滚，失败记录按设计另存；结束任职再新增不代表恢复旧授权。',
        ],
    }
    for owner in BUSINESS_ASSIGNMENTS:
        lines += [f'## {owner}：{BUSINESS_AREAS[owner]}', '', notes[owner][0], '',
            '| 任务 | 完整前置 | 具体交付 |', '| --- | --- | --- |']
        for task in TASKS:
            if task['ownerSlot'] == owner:
                lines.append(f"| {task['id']} {task['title']} | {'、'.join(task['dependencies'])} | {task['deliverable']} |")
        lines += ['', '**推进与交接：**', ''] + [f'{i}. {note}' for i, note in enumerate(notes[owner][1:], 1)] + ['']
    lines += ['## 四人共同遵守', '',
        '1. 各自在最新共同主线建立`codex/be-xx-主题`分支。先按[本机配置指南](FOUNDATION-USAGE.md)完成环境；实名/GitHub账号对应、仓库读写权限及成员本机验证结果仍需如实登记。业务代号已分工不代表这些操作已完成。',
        '2. M11查询投影、M12 Controller/DTO按功能分文件，由对应BE负责人维护；不共写一个巨大QueryService或统一业务Controller。M10按本表A/D的任务拆分。调用其他领域公开内部端口，不随意改其实体和Repository。',
        '3. 修改公共core、pom.xml、全局配置/Problem/认证装配时先与基座维护者（用户）及受影响成员协调；迁移先协调版本号，每个版本只由一人提交。复用既有43表设计，结构变化同步权威设计；协调不增加独立评审人或额外审批门槛。',
        '4. 接口交接写清输入/输出、权限与事务前提、失败语义、验收场景和依赖提交。未就绪时可以准备设计和测试场景，不能把Mock端口记成真实依赖已完成。',
        '5. 每项先设计，再按项目TDD保留RED/GREEN，完成所属行为/权限/异常/事务与恢复验证，同步共享记忆和文档。负责人自行确认完成；合并状态、部署状态分别记录，未运行不勾选。真实数据库测试沿用专属临时实例，不向共享开发库或测试库写夹具。',
        '6. 本文由[design_plan.py](design_plan.py)从任务数据与分工规则生成；调整任务归属先更新生成器，再运行[validate_design.py](validate_design.py)。BE编号是本地任务编号，不是已经创建的GitHub Issue。', '']
    write('TEAM-ASSIGNMENT.md', '\n'.join(lines))


def generate_schedule():
    totals={s:sum(t['estimateHours'][s]+t['reviewHours'][s] for t in TASKS) for s in ('low','likely','high')}
    phases={}
    for phase in ('FOUNDATION','BUSINESS'):
        selected=[t for t in TASKS if t['phase']==phase]
        dev={s:sum(t['estimateHours'][s] for t in selected) for s in totals}
        review={s:sum(t['reviewHours'][s] for t in selected) for s in totals}
        phases[phase]=dict(taskCount=len(selected),developmentHours=dev,reviewHours=review,
            totalHours={s:dev[s]+review[s] for s in totals})
    bounds={}
    for s,hpd in [('low',3),('likely',2.5),('high',2)]:
        foundation_days=phases['FOUNDATION']['developmentHours'][s]/hpd
        business_days=phases['BUSINESS']['totalHours'][s]/(4*hpd)
        bounds[s]=dict(hoursPerPersonPerDay=hpd,pooledCapacityDays=math.ceil(totals[s]/(4*hpd)),
            foundationOwnerDevelopmentDayEquivalent=foundation_days,
            businessPooledDayEquivalent=business_days,
            stagedCapacityDays=math.ceil(foundation_days+business_days))
    loads = {}
    for owner in BUSINESS_ASSIGNMENTS:
        selected = [t for t in TASKS if t['ownerSlot'] == owner]
        dev = {s: sum(t['estimateHours'][s] for t in selected) for s in totals}
        loads[owner] = dict(taskIds=[t['id'] for t in selected],taskCount=len(selected),
            developmentHours=dev,reviewHours=dict.fromkeys(totals,0),totalHours=dict(dev))
    result=dict(revision='1.4',decision='D-051',assignmentDecision='D-055',capacityHours=dict(minimum=40,maximum=72),
        totalHours=totals,phaseTotals=phases,foundationOwnerDevelopmentHours=phases['FOUNDATION']['developmentHours'],
        businessPersonLoad=loads,assumption='沿用逐任务技术工作量，D-055按A～D成员代号汇总负载，实名/账号及实际可投入时间待补；全部任务按D-051由负责人确认，不另设评审。原估算含TDD/自检/确认，不假设agent一定更快。容量下界非工期承诺，不含外部等待/依赖停顿。',capacityLowerBounds=bounds)
    dump('estimates.json',result)
    lines=['# 先基座、后分工：实施估算与依赖计划','','修订1.4/D-055：BE-01～05公共基座已确认并纳入主线，BE-06～35已登记A～D成员代号，详见[四人分工](TEAM-ASSIGNMENT.md)。实名/GitHub账号及成员接入证据待补；BE-06为OWNER_ACCEPTED（D-057，已由D-060交付main）、BE-07为OWNER_ACCEPTED（D-059，已由D-060交付main）、BE-32为OWNER_ACCEPTED（D-062，已由D-063/PR#1交付main），BE-10为IMPLEMENTED（六个查询接口及内部读写端口已实现，已由D-065交付main，待负责人验收），BE-08为OWNER_ACCEPTED（D-068，已由D-069/PR#2交付main），其余25项PLANNED，全部任务由负责人自行确认完成。业务原有技术依赖及启动门槛保留。','','团队原投入40～72人时（4人、5～6天、每天2～3小时）仍作为比较口径；不是可保证的工期。逐任务技术范围及低/中/高工作量沿用1.0，D-051将全部任务单列独立评审工时归零，负责人自检/确认含原开发估算，重算总量和阶段容量；不是实测重新估价。下列个人负载只是按当前任务估算汇总，仍须按实测和成员可投入时间调整排期。','','| 情境 | 全量开发与负责人自检确认人时 | 相对72人时上限 |','| --- | --- | --- |']
    for s,label in [('low','低'),('likely','中'),('high','高')]:lines.append(f"| {label} | {totals[s]:g} | 超出{max(0,totals[s]-72):g} |")
    lines+=['','| 阶段 | 开发人时（低/中/高） | 另计他人评审人时（均0） | 合计人时 |','| --- | --- | --- | --- |']
    for phase,label in [('FOUNDATION','用户实现基座（5项）'),('BUSINESS','已分派业务/交付（30项，未实现）')]:
        p=phases[phase]
        fmt=lambda field:' / '.join(f'{p[field][s]:g}' for s in totals)
        lines.append(f"| {label} | {fmt('developmentHours')} | {fmt('reviewHours')} | {fmt('totalHours')} |")
    lines += ['', '| 成员代号 | 任务数 | 低/中/高人时（含自检确认） |', '| --- | --- | --- |']
    for owner, load in loads.items():
        hours = ' / '.join(f'{load["totalHours"][scenario]:g}' for scenario in totals)
        lines.append(f'| {owner} | {load["taskCount"]} | {hours} |')
    lines+=['','用户基座开发及本人确认估算为9/18/28小时；全部任务由负责人确认，不另设评审人，单列评审为0。业务负载按D-055的A～D成员代号汇总，见businessPersonLoad；具体人员身份及实际可用时间尚未核验，不能把估算当完成时间。','','**完整范围低估算70.5人时接近72上限，中估130.5人时仍超出预算，不能据此保证5～6天全量交付。** 增加投入或顺延需团队协调，业务范围变化另确认。','','## 两阶段容量下界','','基座阶段由用户开发并确认，其他人可准备场景；业务阶段暂以四人可投入计算容量，实际投入仍待成员确认。粗下界=向上取整〔基座开发人时/每人每日小时＋业务阶段开发及负责人自检确认总人时/(4×每人每日小时)〕。该式不含依赖和环境延迟；不是可执行日历。','','| 情境 | 每人每天小时 | 仅看总量的容量下界 | 加入基座单人先行的下界 |','| --- | --- | --- | --- |']
    for s,label in [('low','低'),('likely','中'),('high','高')]:lines.append(f"| {label} | {bounds[s]['hoursPerPersonPerDay']} | {bounds[s]['pooledCapacityDays']}工作日 | {bounds[s]['stagedCapacityDays']}工作日 |")
    lines+=['','公式与阶段汇总见[estimates.json](estimates.json)。基座已通过并交付main，30项已按成员代号分派；技术依赖未就绪的任务不能记为已开工。用户已认领A，BE-06在开发分支实现且已本人确认（D-057），已交付main，D/BE-10可先行；BE-07已按D-059确认已交付main；A已确认BE-32且由D-063/PR#1交付main、BE-08已获A确认（D-068）并由D-069/PR#2交付main，BE-09仍等B/BE-17，B/C按前置交付情况推进。','','## 实际推进顺序','','1. 用户按BE-01→02→03→04→05实施基座，逐项TDD并保留可评审的小PR；基座已交付，D-056开始实施BE-06，详见BE-06-IDENTITY.md。','2. 按[FOUNDATION-HANDOFF.md](FOUNDATION-HANDOFF.md)完成真实环境验证、说明、用户本人确认和主线交付，记录基座验收证据；不是以文件生成或Mock摘要通过门槛。','3. D-055已登记30项任务的A～D分工；团队补成员实名/GitHub账号、访问权限、本机验证及依赖提交。需要时再创建真实Issue/PR，不虚构编号。','4. 业务阶段按既有依赖并行，BE-06/10等基础业务先行，之后目录角色、申请OA、来源导入按条件接入；完整闭环是集成里程碑，不要求所有人等整条闭环才开始自己的就绪任务。','5. BE-33汇总业务实证，BE-34目标服务器验收，BE-35发布交接；未完成项保持开放。','','## 外部条件','','用户确认已有云服务器可后续部署Docker数据库，目前用本机MySQL。JDK21可在IDE引用，命令行java/javac/JAVA_HOME尚不可用；不要求永久环境变量，实施时明确IDE/显式JDK方式。用户本机MySQL8.4.8、应用连接、开发/测试库迁移及账号权限已有基座证据；其他成员本机配置与云端部署仍待核验。JDK21/Wrapper3.9.11及BE-02已实测，见ENVIRONMENT.md和BE-02-IMPLEMENTATION.md。演示OA按既定方案，真实OA不是本期前置。外部等待未计入估算。']
    write('IMPLEMENTATION-PLAN.md','\n'.join(lines))


def page(mx,name,nodes,edges):
    d=ET.SubElement(mx,'diagram',{'id':name.split(' ')[0],'name':name});g=ET.SubElement(d,'mxGraphModel',{'dx':'1200','dy':'900','grid':'1','gridSize':'10','page':'1','pageWidth':'1600','pageHeight':'1200','adaptiveColors':'auto'});root=ET.SubElement(g,'root');ET.SubElement(root,'mxCell',{'id':'0'});ET.SubElement(root,'mxCell',{'id':'1','parent':'0'})
    for key,label,x,y,w,h,color in nodes:
        v=ET.SubElement(root,'mxCell',{'id':key,'value':label,'vertex':'1','parent':'1','style':f'rounded=1;whiteSpace=wrap;html=0;align=center;verticalAlign=middle;fillColor={color};strokeColor=#475569;fontSize=15;spacing=8;'});ET.SubElement(v,'mxGeometry',{'x':str(x),'y':str(y),'width':str(w),'height':str(h),'as':'geometry'})
    for i,(source,target,label) in enumerate(edges):
        e=ET.SubElement(root,'mxCell',{'id':f'edge-{i}','value':label,'edge':'1','parent':'1','source':source,'target':target,'style':'edgeStyle=orthogonalEdgeStyle;rounded=0;endArrow=block;html=0;fontSize=12;strokeColor=#64748b;'});ET.SubElement(e,'mxGeometry',{'relative':'1','as':'geometry'})

def save_graph(name,mx):
    ET.indent(mx);write(name,ET.tostring(mx,encoding='unicode'))

def generate_diagrams(modules):
    mx=ET.Element('mxfile',{'host':'app.diagrams.net','type':'device','version':'detailed-design-1.0'})
    nodes=[('input','三类HTTP入口 / 演示调用方',580,30,360,65,'#e0f2fe'),('M12','M12 HTTP适配与装配',580,140,360,65,'#dbeafe'),('scheduler','启动/定时扫描（内部任务）',1080,140,380,65,'#f1f5f9')]
    mids=['M01','M03','M04','M05','M07','M08','M09','M10']
    for i,mid in enumerate(mids):
        m=next(m for m in modules if m['id']==mid);nodes.append((mid,mid+' '+m['name'],70+(i%4)*370,285+(i//4)*170,300,90,'#eef2ff'))
    nodes += [('M02','M02 组织/任职基础',70,650,300,80,'#fef3c7'),('M06','M06 来源/关系基础',440,650,300,80,'#fef3c7'),('M11','M11 安全读投影/影响计算',810,650,300,80,'#dcfce7'),('M00','M00 事务/幂等/审计/公共契约',440,850,670,80,'#f1f5f9'),('db','MySQL8.4 / 文件持久目录\n单应用，OA仅演示适配',440,1020,670,75,'#ffedd5')]
    edges=[('input','M12','认证与路由')]+[('M12',mid,'委派') for mid in mids if mid!='M09']+[('M12','scheduler','装配'),('scheduler','M09','扫描批准记录'),('scheduler','M08','领取投递'),('M10','M02','人员更新'),('M10','M06','关来源'),('M07','M06','基线'),('M09','M06','授予/撤销'),('M09','M05','配置'),('M08','M07','可靠结果'),('M11','M00','同快照'),('M06','M00','受控写'),('M02','M00','受控写'),('M00','db','同一连接/事务')]
    page(mx,'A 模块层次与调用概览',nodes,edges)
    nodes=[('p1','1.0 身份/管理范围',60,70,350,90,'#e0f2fe'),('p2','2.0 系统/权限目录',60,210,350,90,'#e0f2fe'),('p3','3.0 人员导入/失效',60,350,350,90,'#e0f2fe'),('p4','4.0 角色/授权申请',60,490,350,90,'#e0f2fe'),('p5','5.0 审批/执行',60,630,350,90,'#e0f2fe'),('p6','6.0 有效权限',60,770,350,90,'#e0f2fe'),('p7','7.0 范围内审计',60,910,350,90,'#e0f2fe')]
    right=[('r1','M01/M03/M10 当前身份、范围及失效编排'),('r2','M04/M11 目录规则与影响'),('r3','M02/M06/M10 冻结计划与原子提交'),('r4','M05/M06/M07/M11 草稿、预览与固定'),('r5','M08/M09 可靠结果与一次执行'),('r6','M11 主库快照与完整并集'),('r7','M11读取 / M00追加审计')]
    for i,(key,label) in enumerate(right):nodes.append((key,label,680,70+i*140,750,90,'#dcfce7'))
    page(mx,'B 需求数据流到模块',nodes,[(f'p{i}',f'r{i}','按处理职责细化') for i in range(1,8)])
    save_graph('module-structure.drawio',mx)
    flows=ET.Element('mxfile',{'host':'app.diagrams.net','type':'device','version':'detailed-design-1.0'})
    definitions=[
      ('F1 固定申请到执行',['当前身份/单项范围 + 幂等','全目标授权 → 无变化分类 → 版本/预览','冻结内容/原资格/JCS/投影/待办\n事务提交：尚未授权','事务外OA演示受理 → 可靠关联','可信最终结果独立提交 → 202','执行事务：全部复核后全部写\n业务 + audit + success + EXECUTED','失败全回滚 → 独立复核记失败\n扫描重试 / 非法整份失效']),
      ('F2 关权与迟到批准',['当前身份与单项来源范围','锁guard → 物化0基线 → 精确版本','推进epoch/来源版本 → 结束有效grant','整份受影响已提交申请INVALIDATED\n释放全部claim，其他来源保留','完整审计 + 安全响应 + 幂等同事务','后到批准仅留可靠历史\n恢复/新任职不复活旧周期']),
      ('F3 原子HR导入',['上传受控文件，元数据不可变','预检：解析/规范化/映射/分配候选ID-code','冻结前后值/顺序/依赖/影响\n不写正式组织人员','提交：锁guard，身份/批次成功回放/基线','按明确事件顺序更新全部计划\n离岗后新岗仍推进旧来源周期','正式数据 + 失效 + audit + counts\n一个COMMIT；错误全回滚','另事务只记STALE/FAILED\n先确认未成功，重试完整复核']),
      ('F4 后端部署验收',['固定提交/Wrapper/依赖 → 测试与打包','选定服务器/隔离MySQL8.4/持久目录','备份 → 独立迁移账号 → 迁移校验\nDDL非整份事务回滚','受控bootstrap/服务身份公钥配置','非root运行jar → HTTPS代理 → 健康','Apifox完整链路/重启/数据恢复验收\n留下版本与结果，未通过项保持开放'])]
    for name,labels in definitions:
        nodes=[];edges=[]
        for i,label in enumerate(labels):
            x,y,w=420,35+i*145,730
            if name.startswith('F1') and i==6:x,y,w=30,760,330
            if name.startswith('F3') and i==6:x,y,w=30,760,330
            nodes.append((f'n{i}',label,x,y,w,95,'#dcfce7' if i==len(labels)-1 else '#e0f2fe'))
            if i:edges.append((f'n{i-1}',f'n{i}','明确调用/提交边界'))
        if name.startswith('F1'):
            nodes += [('success','EXECUTED / 一次成功\n轮询读取原结果',420,905,730,95,'#dcfce7'),('invalid','整份INVALIDATED\n释放占用，不改合法子集',1210,760,330,95,'#fee2e2'),('rejected','REJECTED / 释放占用\n不执行权限变更',1210,615,330,95,'#fee2e2')]
            edges=[('n0','n1','通过当前授权'),('n1','n2','有实际变化且版本一致'),('n2','n3','固定内容待投递'),('n3','n4','收到可信最终回调'),('n4','n5','APPROVED，后台扫描'),('n4','rejected','REJECTED'),('n5','success','全部写入COMMIT'),('n5','invalid','任一固定条件失效'),('n5','n6','存储故障，ROLLBACK后'),('n6','n5','到期重试并完整复核')]
        elif name.startswith('F2'):
            edges[-1]=('n4','n5','提交后若收到匹配旧批准')
        elif name.startswith('F3'):
            edges=[('n0','n1','文件合法'),('n1','n2','冻结计划/报告'),('n2','n3','READY且人工确认'),('n3','n4','完整复核通过'),('n4','n5','全部成功COMMIT'),('n3','n6','依赖陈旧，原事务退出'),('n4','n6','存储失败，整体ROLLBACK'),('n6','n1','STALE：新批重预检'),('n6','n3','FAILED：同批完整重试')]
        page(flows,name,nodes,edges)
    save_graph('core-flows.drawio',flows)

if __name__=='__main__':
    generate()
