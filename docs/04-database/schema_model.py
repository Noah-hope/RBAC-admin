"""数据库设计元数据；只生成文档和 SQL 文本，绝不连接数据库。"""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
ID = 'CHAR(36) CHARACTER SET ascii COLLATE ascii_bin'
KEY = 'VARCHAR(100) COLLATE utf8mb4_0900_bin'
VER = 'BIGINT'
TABLES = []


def c(name, typ, meaning, null=False, default=None, generated=None):
    return dict(name=name, type=typ, meaning=meaning, nullable=null,
                default=default, generated=generated)


def ident(name='id', meaning='不可变内部 UUID v4；API 原样输出小写带连字符字符串', null=False):
    return c(name, ID, meaning, null)


def v(name='version', meaning='API 对象业务版本；仅实质变更递增', initial='1'):
    return c(name, VER, meaning, default=initial)


def tm(name='created_at', meaning='记录创建时刻，UTC，非源系统业务起始日期', null=False):
    return c(name, 'DATETIME(6)', meaning, null)


def flag(name, meaning, default='0'):
    return c(name, 'TINYINT', meaning, default=default)


def txt(name, size, meaning, null=False, default=None):
    return c(name, f'VARCHAR({size})', meaning, null, default)


def js(name, meaning, null=False):
    shape='ARRAY' if name in ('sheet_names','field_changes') else 'OBJECT'
    col=c(name, 'JSON', meaning+'；JSON顶层'+shape, null)
    col['json_shape']=shape
    return col


def en(name, values, meaning, default=None):
    return c(name, 'VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin',
             meaning + '；枚举：' + '/'.join(values), default=default)


def t(name, meaning, cols, pk=('id',), unique=(), indexes=(), fks=(), checks=()):
    obj = dict(name=name, meaning=meaning, columns=cols, pk=list(pk),
               unique=[list(x) for x in unique], indexes=[list(x) for x in indexes],
               fks=[dict(columns=list(a), table=b, references=list(d)) for a,b,d in fks],
               checks=list(checks))
    # BIGINT 暴露版本均受 JavaScript 安全整数上界限制；row_version 也不允许负数。
    for col in cols:
        if col.get('json_shape'):
            obj['checks'].append(f"JSON_TYPE({col['name']}) = '{col['json_shape']}'")
        if col['type'] == VER and any(x in col['name'] for x in ('version','epoch')):
            obj['checks'].append(f"{col['name']} BETWEEN 0 AND 9007199254740991")
        if col['type'] == 'TINYINT' and not col['generated']:
            obj['checks'].append(f"{col['name']} IN (0,1)")
    TABLES.append(obj)
    return obj


def fk(col, table, ref='id'):
    return ((col,),table,(ref,))


ROW = lambda: v('row_version', 'JPA @Version 专用存储修订；不直接作为 API 业务版本', '0')
ACTIVE = lambda: c('active_slot','TINYINT','ACTIVE 时为1，结束历史为NULL；利用唯一键允许多份历史',True,
                    generated="CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END")

t('mutation_guard','一期本地写事务串行化锚点；没有业务权限含义',[
    c('id','SMALLINT','固定为1'), v('revision','内部提交序号，不映射API版本','0')], checks=['id = 1'])
t('company','公司；无启停或公司层级授权',[
    ident(),c('code',KEY,'全局唯一稳定公司编码'),txt('name',100,'显示名称'),v(),tm()],unique=[('code',)])
t('department','一公司内平级部门；无父级和状态',[
    ident(),ident('company_id','所属公司'),c('code',KEY,'公司内稳定编码'),txt('name',100,'显示名称'),v(),tm()],
    unique=[('company_id','code'),('id','company_id')],fks=[fk('company_id','company')])
t('position','明确部门内岗位；不关联业务角色',[
    ident(),ident('department_id','所属部门'),c('code',KEY,'部门内稳定编码'),txt('name',100,'显示名称'),v(),tm()],
    unique=[('department_id','code'),('id','department_id')],fks=[fk('department_id','department')])
t('employee','统一员工身份；全局停用独立于后台账号',[
    ident(),c('employee_key',KEY,'平台当前统一员工标识，全局唯一，不按姓名匹配'),txt('name',100,'必要姓名'),
    en('status',['ACTIVE','INACTIVE'],'全局身份状态',"'ACTIVE'"),v(),tm()],unique=[('employee_key',)],
    checks=["status IN ('ACTIVE','INACTIVE')"])
t('employee_source_identity','当前来源标识到内部员工的显式映射；重映射旧值另存审计',[
    ident(),c('source_system',KEY,'来源系统标识'),c('source_key',KEY,'来源的当前唯一标识'),ident('employee_id','内部员工'),v()],
    unique=[('source_system','source_key')],indexes=[('employee_id',)],fks=[fk('employee_id','employee')])
t('organization_mapping','经过确认的完整来源路径映射；没有组织自动合并',[
    ident(),c('source_system',KEY,'来源系统'),txt('source_path',300,'完整来源路径，二进制区分比较'),
    en('object_kind',['COMPANY','DEPARTMENT','POSITION'],'映射对象类型'),ident('company_id','明确公司'),
    ident('department_id','明确部门；公司映射为空',True),ident('position_id','明确岗位；非岗位映射为空',True),v()],
    unique=[('source_system','source_path')],indexes=[('company_id',),('department_id','company_id'),('position_id','department_id')],
    fks=[fk('company_id','company'),(('department_id','company_id'),'department',('id','company_id')),
         (('position_id','department_id'),'position',('id','department_id'))],
    checks=["(object_kind='COMPANY' AND department_id IS NULL AND position_id IS NULL) OR (object_kind='DEPARTMENT' AND department_id IS NOT NULL AND position_id IS NULL) OR (object_kind='POSITION' AND department_id IS NOT NULL AND position_id IS NOT NULL)"])
t('employment','独立任职及结束历史；同员工同岗位至多一个当前任职',[
    ident(),ident('employee_id','员工'),ident('company_id','任职公司'),ident('department_id','任职部门'),
    ident('position_id','任职岗位'),en('status',['ACTIVE','ENDED'],'任职状态',"'ACTIVE'"),
    tm('started_at','源资料明确的业务开始时间；初始化未知为NULL',True),
    tm('ended_at','源资料明确的业务结束时间；未知可为NULL，状态不靠时间推断',True),
    tm('recorded_end_at','平台记录结束动作时间；ENDED必须有值',True),v(),tm(),ACTIVE()],
    unique=[('employee_id','position_id','active_slot')],indexes=[('employee_id','company_id','department_id','status'),('department_id','company_id'),('position_id','department_id')],
    fks=[fk('employee_id','employee'),(('department_id','company_id'),'department',('id','company_id')),
         (('position_id','department_id'),'position',('id','department_id'))],
    checks=["status IN ('ACTIVE','ENDED')", "(status='ACTIVE' AND recorded_end_at IS NULL AND ended_at IS NULL) OR (status='ENDED' AND recorded_end_at IS NOT NULL)",
            'started_at IS NULL OR ended_at IS NULL OR ended_at >= started_at'])
t('admin_account','后台账号及凭据失效依据；总管理员仅部署初始化',[
    ident(),ident('employee_id','关联员工；部署总管理员允许为空',True),c('login_name',KEY,'当前登录名全局唯一'),
    txt('password_hash',255,'带算法参数和盐的专用密码摘要；不存密码历史'),flag('enabled','后台账号启用', '1'),
    flag('is_super_admin','部署管理的全局后台身份；普通创建API不得写'),flag('must_change_password','临时密码强制修改','1'),
    v(),v('credential_version','改密/重置递增，JWT须精确匹配'),v('session_epoch','失去全部资格/停用等递增，恢复不回退'),ROW(),tm()],
    unique=[('login_name',),('employee_id',)],checks=['is_super_admin=1 OR employee_id IS NOT NULL'])
t('admin_session','可即时撤销的后台会话；不存JWT原文',[
    ident('id','JWT sid，不可猜测 UUID'),ident('account_id','所属后台账号'),ident('token_jti','本会话唯一JWT jti'),
    en('mode',['NORMAL','PASSWORD_CHANGE_ONLY'],'会话模式'),v('credential_version','登录时凭据版本'),
    v('session_epoch','登录时账号会话周期'),tm('expires_at','正常15分钟/受限5分钟的到期时刻'),
    tm('revoked_at','单会话退出撤销时刻',True),tm()],unique=[('token_jti',)],indexes=[('account_id','expires_at')],
    fks=[fk('account_id','admin_account')],checks=["mode IN ('NORMAL','PASSWORD_CHANGE_ONLY')",'expires_at > created_at'])
t('management_scope','稳定范围记录；一个公司一个部门，可无人及空系统集合',[
    ident(),ident('company_id','当前管理公司'),ident('department_id','当前指定部门'),v('scope_version','API scopeVersion / Scope.version；名单/范围实质变更递增'),ROW(),tm()],
    indexes=[('department_id','company_id')],fks=[(('department_id','company_id'),'department',('id','company_id'))])
t('scope_system','当前范围系统集合；不授予业务进入权限',[
    ident('scope_id','范围'),ident('system_id','获准系统')],pk=('scope_id','system_id'),indexes=[('system_id',)],
    fks=[fk('scope_id','management_scope'),fk('system_id','business_system')])
t('admin_appointment','单账号单范围独立任命；结束后不恢复原任命',[
    ident(),ident('scope_id','稳定范围'),ident('account_id','后台账号，员工由账号取得'),
    en('status',['ACTIVE','REVOKED','INVALIDATED'],'任命状态',"'ACTIVE'"),v('appointment_version','API appointmentVersion / Appointment.version'),
    tm('ended_at','撤销/资格失效时刻',True),txt('ended_reason',500,'结束原因',True),ROW(),tm(),ACTIVE()],
    unique=[('scope_id','account_id','active_slot')],indexes=[('account_id','status','id')],
    fks=[fk('scope_id','management_scope'),fk('account_id','admin_account')],
    checks=["status IN ('ACTIVE','REVOKED','INVALIDATED')", "(status='ACTIVE' AND ended_at IS NULL AND ended_reason IS NULL) OR (status<>'ACTIVE' AND ended_at IS NOT NULL AND ended_reason IS NOT NULL)"])
t('business_system','业务系统及目录聚合版本；新建同事务建立进入权限',[
    ident(),c('code',KEY,'全局唯一稳定编码；新建API限制93字符'),txt('name',100,'名称'),txt('description',500,'说明',default="''"),
    flag('enabled','系统资源启用','1'),v(),v('catalog_version','仅目录实质变化递增'),ROW(),tm()],unique=[('code',)])
t('directory_node','同系统导航/功能树；操作不作为树节点',[
    ident(),ident('system_id','所属业务系统'),c('code',KEY,'系统内稳定目录编码'),txt('name',100,'名称'),
    en('node_type',['NAVIGATION','FUNCTION'],'创建后不改类型'),ident('parent_id','同系统父节点；根为NULL',True),
    c('sort_order','INT','同父级按此值、code、id排序，允许负数/并列',default='0'),flag('enabled','节点自身状态','0'),
    ident('visibility_permission_id','本功能menuEligible操作；导航必须为空',True),v(),tm()],
    unique=[('system_id','code'),('id','system_id')],indexes=[('system_id','parent_id','sort_order','code','id'),('parent_id','system_id'),('visibility_permission_id','id','system_id')],
    fks=[fk('system_id','business_system'),(('parent_id','system_id'),'directory_node',('id','system_id')),
         (('visibility_permission_id','id','system_id'),'permission',('id','function_id','system_id'))],
    checks=["node_type IN ('NAVIGATION','FUNCTION')",'parent_id IS NULL OR parent_id <> id',
            "(node_type='NAVIGATION' AND visibility_permission_id IS NULL) OR (node_type='FUNCTION' AND (enabled=0 OR visibility_permission_id IS NOT NULL))"])
t('permission','独立功能操作或唯一系统进入权限；不含数据权限',[
    ident(),ident('system_id','所属系统'),c('code',KEY,'系统内唯一稳定权限编码'),txt('name',100,'同功能内名称唯一'),
    en('permission_type',['SYSTEM_ACCESS','FUNCTION_OPERATION'],'权限类型'),ident('function_id','FUNCTION_OPERATION关联功能节点；进入权限为空',True),
    flag('menu_eligible','显式查看/进入页面语义；不解析code'),flag('enabled','操作自身状态','1'),v(),tm(),
    c('access_slot','TINYINT','系统进入权限为1，其余NULL；系统内唯一',True,generated="CASE WHEN permission_type='SYSTEM_ACCESS' THEN 1 ELSE NULL END")],
    unique=[('system_id','code'),('system_id','access_slot'),('function_id','name'),('id','system_id'),('id','function_id','system_id')],
    indexes=[('function_id','system_id')],fks=[fk('system_id','business_system'),(('function_id','system_id'),'directory_node',('id','system_id'))],
    checks=["(permission_type='SYSTEM_ACCESS' AND function_id IS NULL AND menu_eligible=0) OR (permission_type='FUNCTION_OPERATION' AND function_id IS NOT NULL)"])
t('business_role','业务角色元数据和有效配置基线；无角色启停',[
    ident(),c('code',KEY,'后端生成 ROLE_ 加UUID无连字符；全局唯一且不可更改'),txt('name',100,'可重名的显示名称'),
    txt('description',500,'说明',default="''"),ident('company_id','固定公司'),ident('department_id','部门角色固定部门；公司级共享角色为NULL',True),
    ident('system_id','固定业务系统'),en('state',['UNCONFIGURED','EFFECTIVE'],'首次配置执行成功后EFFECTIVE',"'UNCONFIGURED'"),
    v('metadata_version','仅名称/说明实质变化递增'),v('configuration_version','有效权限集合版本；未配置0','0'),ROW(),tm()],
    unique=[('code',),('id','company_id','system_id'),('id','system_id')],indexes=[('company_id','department_id','system_id','created_at','id'),('department_id','company_id'),('system_id',)],
    fks=[fk('company_id','company'),(('department_id','company_id'),'department',('id','company_id')),fk('system_id','business_system')],
    checks=["(state='UNCONFIGURED' AND configuration_version=0) OR (state='EFFECTIVE' AND configuration_version>0)"])
t('role_permission','当前有效角色配置集合；关系只表示授予',[
    ident('role_id','业务角色'),ident('permission_id','权限项'),ident('system_id','约束角色与权限同系统')],
    pk=('role_id','permission_id'),indexes=[('role_id','system_id'),('permission_id','system_id')],
    fks=[(('role_id','system_id'),'business_role',('id','system_id')),(('permission_id','system_id'),'permission',('id','system_id'))])
t('role_configuration_history','每次成功配置的完整历史快照，不是当前授权事实',[
    ident('role_id','角色'),v('configuration_version','成功配置版本，无重复'),ident('request_id','唯一成功来源申请'),
    js('configuration_snapshot','权限稳定ID/类型/code/当时名称及完整集合，schemaVersion=1'),tm('applied_at','成功配置提交时刻')],
    pk=('role_id','configuration_version'),unique=[('request_id',)],fks=[fk('role_id','business_role'),fk('request_id','change_request')])
t('authorization_source','员工+公司+来源部门+系统的持久周期；无关系也可存在',[
    ident(),ident('employee_id','员工'),ident('company_id','来源公司'),ident('department_id','来源部门'),ident('system_id','系统'),
    v('source_epoch','关闭/资格丧失推进的周期；未落库逻辑基线为0','0'),v('source_version','API SourceBaseline.version；该来源变更递增','0'),ROW()],
    unique=[('employee_id','company_id','department_id','system_id'),('id','company_id','system_id')],
    indexes=[('department_id','company_id','system_id'),('system_id',)],
    fks=[fk('employee_id','employee'),(('department_id','company_id'),'department',('id','company_id')),fk('system_id','business_system')])
t('grant_baseline','同一来源同角色的永久关系版本锚点；不随授权结束删除',[
    ident(),ident('source_id','独立来源'),ident('role_id','业务角色'),ident('company_id','用于复合归属约束'),ident('system_id','用于复合同系统约束'),
    v('relation_version','API当前关系基线；每次授予/结束递增，未落库为0','0'),ROW()],
    unique=[('source_id','role_id')],indexes=[('source_id','company_id','system_id'),('role_id','company_id','system_id')],
    fks=[(('source_id','company_id','system_id'),'authorization_source',('id','company_id','system_id')),
         (('role_id','company_id','system_id'),'business_role',('id','company_id','system_id'))])
t('role_grant','一次具体来源授予；结束后重授必须新ID',[
    ident(),ident('baseline_id','永久关系锚点'),v('relation_version','该grant当前演进版本；结束时推进到基线新值'),
    v('source_epoch','授予时固定来源周期','0'),en('status',['ACTIVE','REVOKED','INVALIDATED'],'授权关系状态',"'ACTIVE'"),
    ident('granted_by_request_id','成功授予申请'),ident('ended_by_request_id','一般撤销申请；直接关权时为空',True),
    tm('ended_at','结束提交时刻',True),txt('ended_reason',500,'撤销/失效原因',True),tm(),ACTIVE()],
    unique=[('baseline_id','active_slot'),('baseline_id','relation_version')],indexes=[('granted_by_request_id',),('ended_by_request_id',)],
    fks=[fk('baseline_id','grant_baseline'),fk('granted_by_request_id','change_request'),fk('ended_by_request_id','change_request')],
    checks=["status IN ('ACTIVE','REVOKED','INVALIDATED')", "(status='ACTIVE' AND ended_at IS NULL AND ended_reason IS NULL AND ended_by_request_id IS NULL) OR (status<>'ACTIVE' AND ended_at IS NOT NULL AND ended_reason IS NOT NULL)"])
t('grant_employment_evidence','实际授予时适用任职依据；后续不因岗位改名改写',[
    ident('grant_id','授权关系'),ident('employment_id','当时适用任职')],pk=('grant_id','employment_id'),indexes=[('employment_id',)],
    fks=[fk('grant_id','role_grant'),fk('employment_id','employment')])
t('change_request','草稿与固定申请共用生命周期；七响应模型不建七表',[
    ident(),en('kind',['ROLE_CONFIGURATION','ROLE_GRANT','ROLE_REVOCATION'],'变更类型'),ident('company_id','一申请一个公司'),
    js('content','草稿可编辑；提交后实际明细冻结，与规范字节一致'),txt('reason',500,'提交原因；提交后不可改'),
    v('draft_version','协作编辑版本；提交后冻结'),v('status_version','投递/审批/执行状态修订'),
    c('submitted_version',VER,'固定提交版本；取提交时draftVersion，提交后不变',True),tm('submitted_at','固定提交时刻，进入摘要',True),
    ident('submitted_by_account_id','原提交人；离任不改写',True),
    en('state',['DRAFT','PENDING','REJECTED','APPROVED','EXECUTION_FAILED','EXECUTED','INVALIDATED'],'受三轴约束的聚合展示状态',"'DRAFT'"),
    en('delivery_status',['NOT_SUBMITTED','PENDING_SEND','SEND_FAILED','LINKED'],'OA投递状态',"'NOT_SUBMITTED'"),
    en('approval_result',['NONE','APPROVED','REJECTED'],'可信最终结果',"'NONE'"),
    en('execution_status',['NOT_STARTED','PENDING','FAILED','SUCCEEDED','INVALIDATED'],'本地执行状态',"'NOT_STARTED'"),
    txt('authority_type',10,'NULL草稿；GLOBAL或SCOPE',True),ident('scope_id','原范围ID，仅SCOPE；范围保留',True),
    ident('appointment_id','原任命ID；历史保留，执行不要求仍ACTIVE',True),js('original_authority','固定原资格完整快照，API Authority形状',True),
    c('fixed_payload_bytes','MEDIUMBLOB','RFC8785规范UTF-8摘要包原字节；不从MySQL JSON序列化重建',True),
    c('content_digest','BINARY(32)','规范字节SHA-256；API输出64位小写hex',True),
    txt('failure_code',64,'失效或可重试失败安全码',True),tm('next_retry_at','执行恢复计划；非取消期限',True),ROW(),tm()],
    unique=[('id','kind')],indexes=[('scope_id','state','id'),('company_id','created_at','id'),('state','next_retry_at','id'),('submitted_by_account_id',),('appointment_id',)],
    fks=[fk('company_id','company'),fk('scope_id','management_scope'),fk('appointment_id','admin_appointment'),fk('submitted_by_account_id','admin_account')],
    checks=["kind IN ('ROLE_CONFIGURATION','ROLE_GRANT','ROLE_REVOCATION')", "delivery_status IN ('NOT_SUBMITTED','PENDING_SEND','SEND_FAILED','LINKED')", "approval_result IN ('NONE','APPROVED','REJECTED')", "execution_status IN ('NOT_STARTED','PENDING','FAILED','SUCCEEDED','INVALIDATED')",
    "(state='DRAFT' AND submitted_version IS NULL AND submitted_at IS NULL AND submitted_by_account_id IS NULL AND original_authority IS NULL AND authority_type IS NULL AND scope_id IS NULL AND appointment_id IS NULL AND fixed_payload_bytes IS NULL AND content_digest IS NULL) OR (state<>'DRAFT' AND submitted_version IS NOT NULL AND submitted_version>0 AND submitted_at IS NOT NULL AND submitted_by_account_id IS NOT NULL AND original_authority IS NOT NULL AND authority_type IS NOT NULL AND fixed_payload_bytes IS NOT NULL AND content_digest IS NOT NULL AND ((authority_type='GLOBAL' AND scope_id IS NULL AND appointment_id IS NULL) OR (authority_type='SCOPE' AND scope_id IS NOT NULL AND appointment_id IS NOT NULL)))",
    "(state='DRAFT' AND delivery_status='NOT_SUBMITTED' AND approval_result='NONE' AND execution_status='NOT_STARTED' AND failure_code IS NULL AND next_retry_at IS NULL) OR (state='PENDING' AND delivery_status IN ('PENDING_SEND','SEND_FAILED','LINKED') AND approval_result='NONE' AND execution_status='NOT_STARTED' AND failure_code IS NULL AND next_retry_at IS NULL) OR (state='REJECTED' AND delivery_status='LINKED' AND approval_result='REJECTED' AND execution_status='NOT_STARTED' AND failure_code IS NULL AND next_retry_at IS NULL) OR (state='APPROVED' AND delivery_status='LINKED' AND approval_result='APPROVED' AND execution_status='PENDING' AND failure_code IS NULL) OR (state='EXECUTION_FAILED' AND delivery_status='LINKED' AND approval_result='APPROVED' AND execution_status='FAILED' AND failure_code IS NOT NULL AND next_retry_at IS NOT NULL) OR (state='EXECUTED' AND delivery_status='LINKED' AND approval_result='APPROVED' AND execution_status='SUCCEEDED' AND failure_code IS NULL AND next_retry_at IS NULL) OR (state='INVALIDATED' AND delivery_status IN ('PENDING_SEND','SEND_FAILED','LINKED') AND (delivery_status='LINKED' OR approval_result='NONE') AND execution_status='INVALIDATED' AND failure_code IS NOT NULL AND next_retry_at IS NULL)"])
t('change_target','草稿目标/提交后实际清单的可检索投影；必须与对应content一致',[
    ident('request_id','所属申请'),c('line_no','INT','固定清单序号，从0起'),
    en('line_kind',['ROLE_CONFIGURATION','ROLE_GRANT','ROLE_REVOCATION'],'匹配父申请类型'),
    ident('company_id','固定公司'),ident('system_id','固定系统'),ident('department_id','固定来源/角色部门；共享配置可为空',True),
    ident('employee_id','分配/撤销目标员工；配置为空',True),ident('role_id','明确角色'),
    ident('source_id','分配/撤销来源；配置为空',True),ident('baseline_id','分配/撤销永久关系锚点；配置为空',True),
    ident('grant_id','撤销的准确历史ID；分配/配置为空',True),
    c('expected_configuration_version',VER,'配置/授予检查；一般撤销为空',True),
    c('expected_source_epoch',VER,'授予固定周期；其他类型为空',True),c('expected_relation_version',VER,'授予/撤销固定关系版本',True),
    js('target_snapshot','schemaVersion=1；当时名称、适用employmentIds、完整权限差异/依据')],
    pk=('request_id','line_no'),unique=[('request_id','baseline_id'),('request_id','grant_id')],
    indexes=[('request_id','line_kind'),('source_id',),('baseline_id',),('grant_id',),('role_id','company_id','system_id'),('department_id','company_id'),('employee_id',),('system_id',)],
    fks=[(('request_id','line_kind'),'change_request',('id','kind')),(('role_id','company_id','system_id'),'business_role',('id','company_id','system_id')),
         (('department_id','company_id'),'department',('id','company_id')),fk('employee_id','employee'),fk('source_id','authorization_source'),fk('baseline_id','grant_baseline'),fk('grant_id','role_grant'),fk('system_id','business_system')],
    checks=['line_no BETWEEN 0 AND 99',
    "(line_kind='ROLE_CONFIGURATION' AND line_no=0 AND employee_id IS NULL AND source_id IS NULL AND baseline_id IS NULL AND grant_id IS NULL AND expected_configuration_version IS NOT NULL AND expected_source_epoch IS NULL AND expected_relation_version IS NULL) OR (line_kind='ROLE_GRANT' AND employee_id IS NOT NULL AND department_id IS NOT NULL AND source_id IS NOT NULL AND baseline_id IS NOT NULL AND grant_id IS NULL AND expected_configuration_version IS NOT NULL AND expected_source_epoch IS NOT NULL AND expected_relation_version IS NOT NULL) OR (line_kind='ROLE_REVOCATION' AND employee_id IS NOT NULL AND department_id IS NOT NULL AND source_id IS NOT NULL AND baseline_id IS NOT NULL AND grant_id IS NOT NULL AND expected_configuration_version IS NULL AND expected_source_epoch IS NULL AND expected_relation_version IS NOT NULL)"])
t('role_change_claim','当前角色配置通道；终态删除占用，历史不靠本表',[
    ident('role_id','角色唯一配置通道'),ident('request_id','占用申请')],pk=('role_id',),unique=[('request_id',)],
    fks=[fk('role_id','business_role'),fk('request_id','change_request')])
t('relation_change_claim','当前关系变更通道；同来源去重，不阻塞其他来源',[
    ident('baseline_id','关系唯一通道'),ident('request_id','占用申请')],pk=('baseline_id',),indexes=[('request_id',)],
    fks=[fk('baseline_id','grant_baseline'),fk('request_id','change_request')])
t('service_identity','部署管理的服务身份和绑定；不保存私钥/令牌',[
    ident(),c('subject',KEY,'JWT sub，全局唯一'),en('kind',['BUSINESS','OA'],'入口类型'),
    ident('system_id','BUSINESS唯一绑定系统，OA为空',True),flag('enabled','当前服务身份状态','1'),v()],
    unique=[('subject',)],indexes=[('system_id',)],fks=[fk('system_id','business_system')],
    checks=["(kind='BUSINESS' AND system_id IS NOT NULL) OR (kind='OA' AND system_id IS NULL)"])
t('oa_link','可靠外部审批关联；先由投递响应落地，回调不得抢绑',[
    ident('request_id','申请，一对一'),ident('oa_instance_id','部署OA服务身份'),txt('oa_approval_id',64,'外部审批编号'),
    tm('accepted_at','OA受理时刻'),tm()],pk=('request_id',),unique=[('oa_instance_id','oa_approval_id')],
    fks=[fk('request_id','change_request'),fk('oa_instance_id','service_identity')])
t('oa_outbox','同一固定申请可靠投递待办及租约；不复制可变申请内容',[
    ident('request_id','申请，一申请一待办'),ident('oa_instance_id','目标OA实例'),
    en('task_state',['READY','LEASED','DONE','STOPPED'],'仅内部调度状态',"'READY'"),
    tm('next_attempt_at','下次投递时刻'),ident('lease_token','每次领取随机令牌',True),tm('lease_until','领取过期时间',True),
    c('attempt_count','INT','已开始尝试数',default='0'),txt('last_failure_code',64,'安全故障码',True),ROW()],pk=('request_id',),
    indexes=[('task_state','next_attempt_at','request_id'),('oa_instance_id',)],
    fks=[fk('request_id','change_request'),fk('oa_instance_id','service_identity')],
    checks=["task_state IN ('READY','LEASED','DONE','STOPPED')",'attempt_count>=0',"(task_state='LEASED' AND lease_token IS NOT NULL AND lease_until IS NOT NULL) OR (task_state<>'LEASED' AND lease_token IS NULL AND lease_until IS NULL)"])
t('oa_callback_event','OA实例+事件ID防重；同事件不同内容不得覆盖',[
    ident(),ident('oa_instance_id','可信OA实例'),txt('event_id',64,'OA最终结果事件编号'),ident('request_id','匹配的固定申请'),
    c('payload_digest','BINARY(32)','完整规范化回调内容摘要'),js('safe_payload','不含认证凭据的最终结果原字段'),tm('received_at','首次可靠接收时刻')],
    unique=[('oa_instance_id','event_id')],indexes=[('request_id',)],fks=[fk('oa_instance_id','service_identity'),fk('request_id','change_request')])
t('oa_final_result','一申请一不可反转最终结果；新eventId不绕过',[
    ident('request_id','申请'),ident('first_event_id','首次可信回调记录ID'),en('decision',['APPROVED','REJECTED'],'最终结果'),
    tm('decided_at','OA决定时刻；不是覆盖优先级'),txt('comment',500,'意见摘要'),tm('received_at','平台接收时刻')],
    pk=('request_id',),unique=[('first_event_id',)],fks=[fk('request_id','change_request'),fk('first_event_id','oa_callback_event')],checks=["decision IN ('APPROVED','REJECTED')"])
t('execution_success','一申请一次成功凭据；业务写入/成功审计同事务',[
    ident('request_id','成功申请唯一键'),ident('audit_event_id','唯一成功业务事件'),tm('executed_at','成功提交时刻'),js('result_snapshot','实际授予ID/撤销ID/配置版本与计数，不含凭据')],
    pk=('request_id',),unique=[('audit_event_id',)],fks=[fk('request_id','change_request'),fk('audit_event_id','audit_event')])
t('execution_attempt','本地执行尝试历史；业务失败回滚后独立记录',[
    ident(),ident('request_id','原固定申请'),c('attempt_no','INT','该申请递增尝试序号'),
    en('outcome',['FAILED','INVALIDATED','SUCCEEDED'],'尝试结果'),txt('failure_code',64,'安全失败/失效码',True),
    tm('started_at','开始时间'),tm('finished_at','记录结果时间')],unique=[('request_id','attempt_no')],
    fks=[fk('request_id','change_request')],checks=['attempt_no>0',"outcome IN ('FAILED','INVALIDATED','SUCCEEDED')",'finished_at>=started_at'])
t('http_idempotency','HTTP接受结果窗口；独立于OA与业务成功防重',[
    ident(),ident('principal_id','当前后台账号主体'),txt('http_method',10,'大写HTTP方法'),
    txt('canonical_path',300,'包含稳定资源ID的规范路径，不包含易变查询排序'),ident('idempotency_key','客户端UUID键'),
    c('request_digest','BINARY(32)','包含版本/目标/规范内容的受保护摘要'),txt('digest_key_id',64,'摘要HMAC密钥版本；密钥不入库'),
    en('state',['IN_PROGRESS','COMPLETED'],'处理中或已接受结果'),c('http_status','SMALLINT','原接受HTTP状态码',True),
    txt('resource_location',300,'原创建/接受资源地址，无则NULL',True),js('safe_response','原安全响应；不含密码/JWT/哈希',True),
    js('authorization_basis','内部重放授权事实：操作策略及完整目标范围；不能替代当前资格；完成时固定',True),
    ident('lease_token','当前处理者随机栅栏令牌；完成后清空',True),tm('lease_until','处理中租约；过期仅允许同内容恢复接管',True),
    tm('expires_at','至少首次接受后7天；不删除长期业务防重'),tm()],
    unique=[('principal_id','http_method','canonical_path','idempotency_key')],indexes=[('expires_at',)],
    fks=[fk('principal_id','admin_account')],checks=["(state='IN_PROGRESS' AND http_status IS NULL AND safe_response IS NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL) OR (state='COMPLETED' AND http_status BETWEEN 200 AND 599 AND safe_response IS NOT NULL AND authorization_basis IS NOT NULL AND JSON_TYPE(authorization_basis)='OBJECT' AND lease_token IS NULL AND lease_until IS NULL)", 'expires_at>created_at'])
t('import_file','导入文件元数据；文件本体受控存储且不可原地覆盖',[
    ident(),txt('file_name',200,'原文件名，仅显示不参与路径拼接'),c('sha256','BINARY(32)','原文件字节摘要'),
    c('size_bytes','BIGINT','原文件大小'),txt('storage_ref',300,'服务端受控文件引用，非客户端任意URL'),
    js('sheet_names','可用工作表名称，CSV固定CSV'),ident('uploaded_by','上传总管理员'),tm()],
    indexes=[('uploaded_by',)],fks=[fk('uploaded_by','admin_account')],checks=['size_bytes BETWEEN 1 AND 10485760'])
t('import_batch','冻结的预检与原子导入结果；成功sourceBatchId长期防重',[
    ident(),ident('file_id','不可变原文件'),c('source_system',KEY,'来源系统'),c('source_batch_id',KEY,'来源批次业务编号'),
    en('import_type',['ORGANIZATION','EMPLOYEE_INITIALIZATION','PERSONNEL_CHANGES'],'导入类别'),
    js('input_snapshot','完整ImportInput：worksheet、字段/组织映射、初始化状态、原因'),
    c('input_digest','BINARY(32)','文件SHA+完整规范输入摘要，定位同批同内容'),c('content_digest','BINARY(32)','规范行、前后值、候选代码和依赖基线摘要'),
    en('state',['BLOCKED','READY','SUCCEEDED','FAILED','STALE'],'API预检/执行状态'),v(),
    js('planned_counts','冻结计划ImportCounts八字段'),js('actual_counts','仅成功时实际八字段计数',True),
    c('blocking_issue_count','INT','阻断问题数',default='0'),c('warning_count','INT','警告数',default='0'),
    txt('failure_code',64,'安全故障码',True),tm('sealed_at','预检完成冻结时间；API只暴露已冻结批次',True),
    tm('committed_at','成功提交时刻',True),ident('created_by','发起总管理员'),ROW(),tm()],
    unique=[('source_system','source_batch_id')],indexes=[('created_at','id'),('file_id',),('created_by',)],
    fks=[fk('file_id','import_file'),fk('created_by','admin_account')],
    checks=["import_type IN ('ORGANIZATION','EMPLOYEE_INITIALIZATION','PERSONNEL_CHANGES')", "state IN ('BLOCKED','READY','SUCCEEDED','FAILED','STALE')",
            "(state='SUCCEEDED' AND actual_counts IS NOT NULL AND committed_at IS NOT NULL) OR (state<>'SUCCEEDED' AND actual_counts IS NULL AND committed_at IS NULL)",
            'blocking_issue_count>=0 AND warning_count>=0'])
t('import_plan_row','冻结规范行及逐字段前后值；候选组织尚未建正式记录',[
    ident('batch_id','所属预检'),c('row_number','INT','原文件行号'),c('plan_no','INT','行内规范动作序号，同一原行可产生多动作'),
    txt('action',32,'ImportPlanChange.action明确动作或UNCHANGED'),ident('target_id','既有或预分配候选ID；无FK以允许尚未创建对象',True),
    txt('stable_code',100,'候选/既有稳定编码',True),txt('company_code',100,'完整公司归属编码',True),
    txt('department_code',100,'完整部门归属编码',True),txt('position_code',100,'完整岗位归属编码',True),
    c('expected_version',VER,'既有目标预检版本；新对象为空',True),txt('summary',500,'安全摘要'),
    js('normalized_values','类型校验后的完整规范输入，不执行公式'),js('field_changes','ImportValueChange[]，明确字段及before/after'),
    js('dependency_snapshot','目标版本、唯一键不存在断言、引用集合摘要和来源周期')],
    pk=('batch_id','row_number','plan_no'),fks=[fk('batch_id','import_batch')],checks=['row_number>0 AND plan_no>=0'])
t('import_issue','冻结预检报告；仅总管理员可读',[
    ident(),ident('batch_id','预检批次'),c('row_number','INT','原始行号'),txt('field',100,'字段定位'),
    en('severity',['WARNING','BLOCKING'],'问题严重性'),txt('code',64,'稳定问题码'),txt('message',500,'安全问题说明')],
    indexes=[('batch_id','row_number','id')],fks=[fk('batch_id','import_batch')],checks=["severity IN ('WARNING','BLOCKING')",'row_number>0'])
t('impact_preview','五分钟固定预览；不是授权凭证',[
    ident(),ident('creator_account_id','创建身份'),ident('appointment_id','创建时所选任命；总管理员为空',True),
    js('authority_snapshot','创建时范围/版本及可见边界'),txt('operation',64,'绑定操作语义与目标资源'),
    c('input_digest','BINARY(32)','规范输入及目标版本摘要'),c('content_digest','BINARY(32)','预览确认摘要'),
    js('dependency_snapshot','影响实际清单/分类/统计的版本和集合依赖，见TRANSACTIONS'),
    js('result_snapshot','Preview/ChangePreview的固定汇总、实际清单与noChangeItems'),
    tm('expires_at','创建后五分钟'),tm()],indexes=[('expires_at',),('creator_account_id',),('appointment_id',)],
    fks=[fk('creator_account_id','admin_account'),fk('appointment_id','admin_appointment')],checks=['expires_at>created_at'])
t('preview_item','同一预览的可见员工/目标分页快照；禁止实时重算混页',[
    ident(),ident('preview_id','预览'),en('item_kind',['EMPLOYEE','ROLE','GRANT','SOURCE','APPOINTMENT','PENDING_REQUEST'],'目标类别'),
    txt('stable_key',200,'稳定ID或SOURCE完整四元键；员工项按员工公司系统键'),js('safe_item','API ImpactEmployee/ImpactTarget；仅创建时有权明细'),
    js('visibility_basis','读取时重新检查的固定来源边界，混合申请须全部覆盖')],
    unique=[('preview_id','item_kind','stable_key')],indexes=[('preview_id','item_kind','id')],fks=[fk('preview_id','impact_preview')],
    checks=["item_kind IN ('EMPLOYEE','ROLE','GRANT','SOURCE','APPOINTMENT','PENDING_REQUEST')"])
t('audit_event','追加式授权/管理审计和申请事件；不通过当前资料倒推历史',[
    ident(),tm('occurred_at','事件发生时刻，UTC'),txt('event_type',64,'事件类型；提交/批准/执行分别记录'),
    txt('actor_kind',16,'ACCOUNT / SERVICE / DEPLOYMENT'),txt('actor_id',64,'当时操作者稳定标识；部署事件可无账号FK'),
    ident('original_submitter_id','原申请人账号，已提交申请事件适用',True),txt('target_id',64,'稳定目标标识或聚合ID'),
    ident('change_request_id','申请；草稿删除事件仅快照保留被删除ID',True),
    ident('company_id','单来源公司；全局/混合无法安全分隔为空',True),ident('source_department_id','单来源部门',True),
    ident('system_id','单来源系统',True),en('visibility',['GLOBAL','SCOPED'],'全局记录仅总管理员；SCOPED按固定来源'),
    txt('result',64,'安全业务结果'),txt('correlation_id',64,'HTTP X-Request-Id，不是防重键'),txt('reason',500,'业务原因'),
    js('event_snapshot','schemaVersion=1；当时名称、原/实际差异、审批编号/版本、失败码、敏感信息脱敏')],
    indexes=[('occurred_at','id'),('company_id','source_department_id','system_id','occurred_at','id'),('source_department_id','company_id'),('system_id',),('change_request_id','occurred_at','id'),('original_submitter_id',)],
    fks=[fk('original_submitter_id','admin_account'),fk('company_id','company'),
         (('source_department_id','company_id'),'department',('id','company_id')),fk('system_id','business_system')],
    checks=["visibility IN ('GLOBAL','SCOPED')", "actor_kind IN ('ACCOUNT','SERVICE','DEPLOYMENT')", "visibility='GLOBAL' OR (company_id IS NOT NULL AND source_department_id IS NOT NULL AND system_id IS NOT NULL)"])
t('audit_subject','审计按稳定员工/角色等目标筛选的历史索引；可指已删除草稿对象',[
    ident('audit_event_id','所属审计事件'),txt('subject_type',16,'EMPLOYEE / ROLE / GRANT / ACCOUNT / REQUEST等稳定类型'),
    txt('subject_id',64,'当时稳定对象标识，有意不设多态外键')],pk=('audit_event_id','subject_type','subject_id'),
    indexes=[('subject_type','subject_id','audit_event_id')],fks=[fk('audit_event_id','audit_event')])


def q(s):
    return "'" + s.replace("'", "''") + "'"


def generate():
    sql = ['-- MySQL 8.4 / InnoDB 设计草案；未在数据库执行。禁止直接用于业务环境。',
           '-- 不创建数据库/用户，不设置连接，不含真实人员/密码/令牌。',
           "SET NAMES utf8mb4 COLLATE utf8mb4_0900_bin;", "SET time_zone = '+00:00';"]
    dd = ['# 物理数据字典', '', '由 [schema_model.py](schema_model.py) 生成；修改元数据后运行生成器。设计说明与事务规则仍需人工评审。', '',
          '全部表 InnoDB、utf8mb4_0900_bin；FK默认 RESTRICT/NO ACTION，无级联删除/更新。列未写默认值时，INSERT必须显式提供（可空列可省略为NULL）。', '',
          'CHAR(36)为ASCII UUID v4。DATETIME(6)统一UTC；BIGINT版本上界9007199254740991。generated列由数据库计算，禁止客户端/JPA写入。', '']
    idx = ['# 主键、唯一键、外键与索引清单', '', '由 [schema_model.py](schema_model.py) 生成。用途、顺序、成本及查询见 [INDEXES.md](INDEXES.md)。所有显式外键均有左前缀索引；复合被引用键均显式唯一，适配MySQL8.4限制。', '']
    for tab in TABLES:
        n=tab['name']; cols=[]
        dd += [f'## {n}', '',tab['meaning'],'','| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |','| --- | --- | --- | --- | --- |']
        for col in tab['columns']:
            line=f"  `{col['name']}` {col['type']}"
            if col['generated']:
                line+=f" GENERATED ALWAYS AS ({col['generated']}) STORED"
            else:
                line+=' NULL' if col['nullable'] else ' NOT NULL'
                if col['default'] is not None: line+=' DEFAULT '+col['default']
            line+=' COMMENT '+q(col['meaning'])
            cols.append(line)
            default=col['generated'] or col['default'] or '无'
            dd.append(f"| {col['name']} | {col['type']} | {'是' if col['nullable'] else '否'} | {default} | {col['meaning']} |")
        key=lambda names:', '.join('`'+x+'`' for x in names)
        cols += [f"  PRIMARY KEY ({key(tab['pk'])})"]
        idx += [f'## {n}', '', f"- PRIMARY KEY ({', '.join(tab['pk'])})：聚簇定位。"]
        for typ,prefix in [('unique','uq'),('indexes','ix')]:
            for i,ks in enumerate(tab[typ],1):
                name=f'{prefix}_{n}_{i}'
                cols += [f"  {'UNIQUE KEY' if typ=='unique' else 'KEY'} `{name}` ({key(ks)})"]
                idx += [f"- {name} ({', '.join(ks)})：{'唯一性/被引用复合候选键' if typ=='unique' else 'FK支撑/典型查询访问路径'}。"]
        for i,expr in enumerate(tab['checks'],1):
            cols += [f'  CONSTRAINT `ck_{n}_{i}` CHECK ({expr})']
        sql += [f'\nCREATE TABLE `{n}` (\n'+',\n'.join(cols)+f'\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT={q(tab["meaning"])};']
        dd += ['',f"主键：`{', '.join(tab['pk'])}`。", '']
        dd += [f"唯一键：`{', '.join(x)}`。" for x in tab['unique']]
        dd += [f"外键：`{', '.join(f['columns'])}` → `{f['table']}({', '.join(f['references'])})`，RESTRICT。" for f in tab['fks']]
        dd += [f'检查约束：`{x}`。' for x in tab['checks']]
        dd += ['']
        idx += [f"- FK ({', '.join(f['columns'])}) → {f['table']} ({', '.join(f['references'])})；删除/更新默认RESTRICT。" for f in tab['fks']]+['']
    sql += ['\n-- 所有表先建立，再添加外键；处理目录/权限及申请/历史之间的循环引用。']
    for tab in TABLES:
        for i,f in enumerate(tab['fks'],1):
            sql += [f"ALTER TABLE `{tab['name']}` ADD CONSTRAINT `fk_{tab['name']}_{i}` FOREIGN KEY ({key(f['columns'])}) REFERENCES `{f['table']}` ({key(f['references'])});"]
    for name,content in [('schema.sql','\n'.join(sql)+'\n'),('DATA-DICTIONARY.md','\n'.join(dd)+'\n'),('KEYS-INDEXES.md','\n'.join(idx)+'\n'),('schema-model.json',json.dumps(TABLES,ensure_ascii=False,indent=2)+'\n')]:
        (ROOT/name).write_text(content.rstrip()+'\n',encoding='utf-8',newline='\n')
    generate_diagram()
    generate_guards()


def generate_guards():
    parts=['-- MySQL8.4不可变性触发器设计草案；未执行。先审查并应用schema.sql。',
           '-- DELIMITER是mysql客户端指令；迁移器需要逐条提交完整CREATE TRIGGER。','DELIMITER $$']
    def trig(name, event, table, body):
        parts.append(f'CREATE TRIGGER `{name}` BEFORE {event} ON `{table}` FOR EACH ROW\nBEGIN\n{body}\nEND$$')
    def deny(condition, message):
        return f"  IF {condition} THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT={q(message)}; END IF;"
    def diff(fields):
        return ' OR '.join(f'NOT (OLD.`{f}` <=> NEW.`{f}`)' for f in fields)
    idem_fields=[col['name'] for table in TABLES if table['name']=='http_idempotency' for col in table['columns']]
    trig('tr_http_idempotency_completed','UPDATE','http_idempotency',deny("OLD.state='COMPLETED' AND ("+diff(idem_fields)+')','completed HTTP result is immutable'))
    fixed=['id','kind','company_id','content','reason','draft_version','submitted_version','submitted_at',
           'submitted_by_account_id','authority_type','scope_id','appointment_id','original_authority','fixed_payload_bytes','content_digest']
    body=deny('OLD.submitted_version IS NOT NULL AND ('+diff(fixed)+')','fixed request is immutable')
    body+='\n'+deny("NOT ((OLD.state=NEW.state) OR (OLD.state='DRAFT' AND NEW.state='PENDING') OR (OLD.state='PENDING' AND NEW.state IN ('APPROVED','REJECTED','INVALIDATED')) OR (OLD.state IN ('APPROVED','EXECUTION_FAILED') AND NEW.state IN ('EXECUTION_FAILED','EXECUTED','INVALIDATED')))",'illegal request state transition')
    body+='\n'+deny("NEW.delivery_status='LINKED' AND NOT EXISTS (SELECT 1 FROM oa_link WHERE request_id=NEW.id)",'missing reliable OA link')
    body+='\n'+deny("NEW.approval_result<>'NONE' AND NOT EXISTS (SELECT 1 FROM oa_final_result WHERE request_id=NEW.id AND decision=NEW.approval_result)",'missing consistent final result')
    body+='\n'+deny("NEW.state='EXECUTED' AND NOT EXISTS (SELECT 1 FROM execution_success WHERE request_id=NEW.id)",'missing execution success receipt')
    trig('tr_change_request_fixed','UPDATE','change_request',body)
    trig('tr_change_request_insert','INSERT','change_request',deny("NEW.state<>'DRAFT'",'new request must start as draft'))
    trig('tr_change_request_delete','DELETE','change_request',deny('OLD.submitted_version IS NOT NULL','submitted history cannot be deleted'))
    for event in ['INSERT','UPDATE','DELETE']:
        checks=[]
        for row in (['NEW'] if event=='INSERT' else ['OLD'] if event=='DELETE' else ['OLD','NEW']):
            checks.append(f'EXISTS (SELECT 1 FROM change_request WHERE id={row}.request_id AND submitted_version IS NOT NULL)')
        trig('tr_change_target_'+event.lower(),event,'change_target',deny(' OR '.join(checks),'submitted targets are immutable'))
    frozen=['id','file_id','source_system','source_batch_id','import_type','input_snapshot','input_digest','content_digest',
            'planned_counts','blocking_issue_count','warning_count','sealed_at','created_by','created_at']
    trig('tr_import_batch_fixed','UPDATE','import_batch',deny('OLD.sealed_at IS NOT NULL AND ('+diff(frozen)+')','sealed import input is immutable')+'\n'+deny("OLD.state IN ('SUCCEEDED','STALE','BLOCKED') AND NEW.state<>OLD.state",'terminal import cannot be revived'))
    trig('tr_import_batch_delete','DELETE','import_batch',deny('OLD.sealed_at IS NOT NULL','sealed import history cannot be deleted'))
    for table in ['import_plan_row','import_issue']:
        for event in ['INSERT','UPDATE','DELETE']:
            rows=['NEW'] if event=='INSERT' else ['OLD'] if event=='DELETE' else ['OLD','NEW']
            condition=' OR '.join(f'EXISTS (SELECT 1 FROM import_batch WHERE id={row}.batch_id AND sealed_at IS NOT NULL)' for row in rows)
            trig(f'tr_{table}_{event.lower()}',event,table,deny(condition,'sealed import details are immutable'))
    for table in ['audit_event','audit_subject','oa_callback_event','oa_final_result','oa_link','execution_success','execution_attempt','role_configuration_history','grant_employment_evidence']:
        for event in ['UPDATE','DELETE']:
            trig(f'tr_{table}_{event.lower()}',event,table,deny('TRUE','append-only history'))
    for table,fields in [('company',['id','code']),('department',['id','code']),('position',['id','code','department_id']),
                         ('employee',['id']),('business_system',['id','code']),
                         ('directory_node',['id','system_id','code','node_type']),('permission',['id','system_id','code','permission_type','function_id']),
                         ('business_role',['id','code','company_id','department_id','system_id'])]:
        trig(f'tr_{table}_identity','UPDATE',table,deny(diff(fields),'stable identity or ownership cannot change'))
    parts.append('DELIMITER ;')
    (ROOT/'immutability.sql').write_text('\n\n'.join(parts)+'\n',encoding='utf-8',newline='\n')


def generate_diagram():
    # 每页是聚焦的实体关系视图；FK全集在字典。跨页实体允许重复出现。
    pages=[('组织与后台身份', ['company','department','position','employee','employment','admin_account','management_scope','admin_appointment','scope_system','admin_session']),
           ('目录、角色与独立来源', ['business_system','directory_node','permission','business_role','role_permission','employee','authorization_source','grant_baseline','role_grant','grant_employment_evidence','role_configuration_history']),
           ('固定申请、OA与执行', ['change_request','change_target','role_change_claim','relation_change_claim','oa_outbox','oa_link','oa_callback_event','oa_final_result','execution_success','audit_event','execution_attempt','service_identity','audit_subject']),
           ('导入、预览与可靠性', ['import_file','import_batch','import_plan_row','import_issue','organization_mapping','employee_source_identity','impact_preview','preview_item','http_idempotency','mutation_guard'])]
    mx=ET.Element('mxfile', {'host':'app.diagrams.net','type':'device','version':'database-design-1.0'})
    lookup={t['name']:t for t in TABLES}
    for pn,(title,names) in enumerate(pages,1):
        d=ET.SubElement(mx,'diagram',{'id':f'page-{pn}','name':title})
        model=ET.SubElement(d,'mxGraphModel',{'adaptiveColors':'auto','page':'1','pageWidth':'1600','pageHeight':str(170+((len(names)+3)//4)*320)})
        root=ET.SubElement(model,'root');ET.SubElement(root,'mxCell',{'id':'0'});ET.SubElement(root,'mxCell',{'id':'1','parent':'0'})
        titlecell=ET.SubElement(root,'mxCell',{'id':'legend','parent':'1','vertex':'1','style':'text;html=1;whiteSpace=wrap;align=left;fontSize=16;','value':title+'<br>实体关系子视图；实线：物理FK（子→父），N:1，含可空；虚线：事务协调，无FK；全量键及空值见数据字典。'})
        ET.SubElement(titlecell,'mxGeometry',{'x':'40','y':'20','width':'1450','height':'70','as':'geometry'})
        for i,name in enumerate(names):
            tab=lookup[name]
            fields=tab['pk']+[c['name'] for c in tab['columns'] if c['name'] not in tab['pk']][:4]
            label='<b>'+name+'</b><br>'+tab['meaning']+'<hr>'+'<br>'.join(('PK ' if f in tab['pk'] else '')+f for f in fields)
            cell=ET.SubElement(root,'mxCell',{'id':name,'value':label,'vertex':'1','parent':'1','style':'rounded=0;whiteSpace=wrap;html=1;align=left;verticalAlign=top;spacing=10;fillColor=#eaf2f8;strokeColor=#456b87;fontSize=12;'})
            ET.SubElement(cell,'mxGeometry',{'x':str(40+(i%4)*390),'y':str(140+(i//4)*320),'width':'290','height':'230','as':'geometry'})
        edge=0
        for name in names:
            for f in lookup[name]['fks']:
                if f['table'] not in names: continue
                edge+=1
                cell=ET.SubElement(root,'mxCell',{'id':f'e{edge}','value':'N:1','edge':'1','parent':'1','source':name,'target':f['table'],'style':'entityRelationEdgeStyle;html=1;endArrow=ERone;startArrow=ERmany;strokeColor=#456b87;fontSize=11;'})
                ET.SubElement(cell,'mxGeometry',{'relative':'1','as':'geometry'})
        if 'mutation_guard' in names:
            for name in ('import_batch','http_idempotency'):
                edge+=1
                cell=ET.SubElement(root,'mxCell',{'id':f'e{edge}','value':'写事务先锁','edge':'1','parent':'1','source':name,'target':'mutation_guard','style':'edgeStyle=orthogonalEdgeStyle;html=1;dashed=1;endArrow=open;strokeColor=#777777;'})
                ET.SubElement(cell,'mxGeometry',{'relative':'1','as':'geometry'})
    ET.indent(mx)
    ET.ElementTree(mx).write(ROOT/'database-erd.drawio',encoding='utf-8',xml_declaration=True)


if __name__=='__main__':
    generate()
    print(f'生成 {len(TABLES)} 张设计表、数据字典、键索引清单、DDL和4页可编辑ER图；未执行SQL。')
