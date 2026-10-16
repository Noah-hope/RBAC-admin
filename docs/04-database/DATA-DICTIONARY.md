# 物理数据字典

由 [schema_model.py](schema_model.py) 生成；修改元数据后运行生成器。设计说明与事务规则仍需人工评审。

全部表 InnoDB、utf8mb4_0900_bin；FK默认 RESTRICT/NO ACTION，无级联删除/更新。列未写默认值时，INSERT必须显式提供（可空列可省略为NULL）。

CHAR(36)为ASCII UUID v4。DATETIME(6)统一UTC；BIGINT版本上界9007199254740991。generated列由数据库计算，禁止客户端/JPA写入。

## mutation_guard

一期本地写事务串行化锚点；没有业务权限含义

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | SMALLINT | 否 | 无 | 固定为1 |
| revision | BIGINT | 否 | 0 | 内部提交序号，不映射API版本 |

主键：`id`。

检查约束：`id = 1`。

## company

公司；无启停或公司层级授权

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| code | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 全局唯一稳定公司编码 |
| name | VARCHAR(100) | 否 | 无 | 显示名称 |
| version | BIGINT | 否 | 1 | API 对象业务版本；仅实质变更递增 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

唯一键：`code`。
检查约束：`version BETWEEN 0 AND 9007199254740991`。

## department

一公司内平级部门；无父级和状态

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| company_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 所属公司 |
| code | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 公司内稳定编码 |
| name | VARCHAR(100) | 否 | 无 | 显示名称 |
| version | BIGINT | 否 | 1 | API 对象业务版本；仅实质变更递增 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

唯一键：`company_id, code`。
唯一键：`id, company_id`。
外键：`company_id` → `company(id)`，RESTRICT。
检查约束：`version BETWEEN 0 AND 9007199254740991`。

## position

明确部门内岗位；不关联业务角色

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| department_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 所属部门 |
| code | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 部门内稳定编码 |
| name | VARCHAR(100) | 否 | 无 | 显示名称 |
| version | BIGINT | 否 | 1 | API 对象业务版本；仅实质变更递增 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

唯一键：`department_id, code`。
唯一键：`id, department_id`。
外键：`department_id` → `department(id)`，RESTRICT。
检查约束：`version BETWEEN 0 AND 9007199254740991`。

## employee

统一员工身份；全局停用独立于后台账号

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| employee_key | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 平台当前统一员工标识，全局唯一，不按姓名匹配 |
| name | VARCHAR(100) | 否 | 无 | 必要姓名 |
| status | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 'ACTIVE' | 全局身份状态；枚举：ACTIVE/INACTIVE |
| version | BIGINT | 否 | 1 | API 对象业务版本；仅实质变更递增 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

唯一键：`employee_key`。
检查约束：`status IN ('ACTIVE','INACTIVE')`。
检查约束：`version BETWEEN 0 AND 9007199254740991`。

## employee_source_identity

当前来源标识到内部员工的显式映射；重映射旧值另存审计

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| source_system | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 来源系统标识 |
| source_key | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 来源的当前唯一标识 |
| employee_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 内部员工 |
| version | BIGINT | 否 | 1 | API 对象业务版本；仅实质变更递增 |

主键：`id`。

唯一键：`source_system, source_key`。
外键：`employee_id` → `employee(id)`，RESTRICT。
检查约束：`version BETWEEN 0 AND 9007199254740991`。

## organization_mapping

经过确认的完整来源路径映射；没有组织自动合并

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| source_system | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 来源系统 |
| source_path | VARCHAR(300) | 否 | 无 | 完整来源路径，二进制区分比较 |
| object_kind | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 映射对象类型；枚举：COMPANY/DEPARTMENT/POSITION |
| company_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 明确公司 |
| department_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 明确部门；公司映射为空 |
| position_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 明确岗位；非岗位映射为空 |
| version | BIGINT | 否 | 1 | API 对象业务版本；仅实质变更递增 |

主键：`id`。

唯一键：`source_system, source_path`。
外键：`company_id` → `company(id)`，RESTRICT。
外键：`department_id, company_id` → `department(id, company_id)`，RESTRICT。
外键：`position_id, department_id` → `position(id, department_id)`，RESTRICT。
检查约束：`(object_kind='COMPANY' AND department_id IS NULL AND position_id IS NULL) OR (object_kind='DEPARTMENT' AND department_id IS NOT NULL AND position_id IS NULL) OR (object_kind='POSITION' AND department_id IS NOT NULL AND position_id IS NOT NULL)`。
检查约束：`version BETWEEN 0 AND 9007199254740991`。

## employment

独立任职及结束历史；同员工同岗位至多一个当前任职

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| employee_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 员工 |
| company_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 任职公司 |
| department_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 任职部门 |
| position_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 任职岗位 |
| status | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 'ACTIVE' | 任职状态；枚举：ACTIVE/ENDED |
| started_at | DATETIME(6) | 是 | 无 | 源资料明确的业务开始时间；初始化未知为NULL |
| ended_at | DATETIME(6) | 是 | 无 | 源资料明确的业务结束时间；未知可为NULL，状态不靠时间推断 |
| recorded_end_at | DATETIME(6) | 是 | 无 | 平台记录结束动作时间；ENDED必须有值 |
| version | BIGINT | 否 | 1 | API 对象业务版本；仅实质变更递增 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |
| active_slot | TINYINT | 是 | CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END | ACTIVE 时为1，结束历史为NULL；利用唯一键允许多份历史 |

主键：`id`。

唯一键：`employee_id, position_id, active_slot`。
外键：`employee_id` → `employee(id)`，RESTRICT。
外键：`department_id, company_id` → `department(id, company_id)`，RESTRICT。
外键：`position_id, department_id` → `position(id, department_id)`，RESTRICT。
检查约束：`status IN ('ACTIVE','ENDED')`。
检查约束：`(status='ACTIVE' AND recorded_end_at IS NULL AND ended_at IS NULL) OR (status='ENDED' AND recorded_end_at IS NOT NULL)`。
检查约束：`started_at IS NULL OR ended_at IS NULL OR ended_at >= started_at`。
检查约束：`version BETWEEN 0 AND 9007199254740991`。

## admin_account

后台账号及凭据失效依据；总管理员仅部署初始化

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| employee_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 关联员工；部署总管理员允许为空 |
| login_name | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 当前登录名全局唯一 |
| password_hash | VARCHAR(255) | 否 | 无 | 带算法参数和盐的专用密码摘要；不存密码历史 |
| enabled | TINYINT | 否 | 1 | 后台账号启用 |
| is_super_admin | TINYINT | 否 | 0 | 部署管理的全局后台身份；普通创建API不得写 |
| must_change_password | TINYINT | 否 | 1 | 临时密码强制修改 |
| version | BIGINT | 否 | 1 | API 对象业务版本；仅实质变更递增 |
| credential_version | BIGINT | 否 | 1 | 改密/重置递增，JWT须精确匹配 |
| session_epoch | BIGINT | 否 | 1 | 失去全部资格/停用等递增，恢复不回退 |
| row_version | BIGINT | 否 | 0 | JPA @Version 专用存储修订；不直接作为 API 业务版本 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

唯一键：`login_name`。
唯一键：`employee_id`。
检查约束：`is_super_admin=1 OR employee_id IS NOT NULL`。
检查约束：`enabled IN (0,1)`。
检查约束：`is_super_admin IN (0,1)`。
检查约束：`must_change_password IN (0,1)`。
检查约束：`version BETWEEN 0 AND 9007199254740991`。
检查约束：`credential_version BETWEEN 0 AND 9007199254740991`。
检查约束：`session_epoch BETWEEN 0 AND 9007199254740991`。
检查约束：`row_version BETWEEN 0 AND 9007199254740991`。

## admin_session

可即时撤销的后台会话；不存JWT原文

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | JWT sid，不可猜测 UUID |
| account_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 所属后台账号 |
| token_jti | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 本会话唯一JWT jti |
| mode | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 会话模式；枚举：NORMAL/PASSWORD_CHANGE_ONLY |
| credential_version | BIGINT | 否 | 1 | 登录时凭据版本 |
| session_epoch | BIGINT | 否 | 1 | 登录时账号会话周期 |
| expires_at | DATETIME(6) | 否 | 无 | 正常15分钟/受限5分钟的到期时刻 |
| revoked_at | DATETIME(6) | 是 | 无 | 单会话退出撤销时刻 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

唯一键：`token_jti`。
外键：`account_id` → `admin_account(id)`，RESTRICT。
检查约束：`mode IN ('NORMAL','PASSWORD_CHANGE_ONLY')`。
检查约束：`expires_at > created_at`。
检查约束：`credential_version BETWEEN 0 AND 9007199254740991`。
检查约束：`session_epoch BETWEEN 0 AND 9007199254740991`。

## management_scope

稳定范围记录；一个公司一个部门，可无人及空系统集合

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| company_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 当前管理公司 |
| department_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 当前指定部门 |
| scope_version | BIGINT | 否 | 1 | API scopeVersion / Scope.version；名单/范围实质变更递增 |
| row_version | BIGINT | 否 | 0 | JPA @Version 专用存储修订；不直接作为 API 业务版本 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

外键：`department_id, company_id` → `department(id, company_id)`，RESTRICT。
检查约束：`scope_version BETWEEN 0 AND 9007199254740991`。
检查约束：`row_version BETWEEN 0 AND 9007199254740991`。

## scope_system

当前范围系统集合；不授予业务进入权限

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| scope_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 范围 |
| system_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 获准系统 |

主键：`scope_id, system_id`。

外键：`scope_id` → `management_scope(id)`，RESTRICT。
外键：`system_id` → `business_system(id)`，RESTRICT。

## admin_appointment

单账号单范围独立任命；结束后不恢复原任命

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| scope_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 稳定范围 |
| account_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 后台账号，员工由账号取得 |
| status | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 'ACTIVE' | 任命状态；枚举：ACTIVE/REVOKED/INVALIDATED |
| appointment_version | BIGINT | 否 | 1 | API appointmentVersion / Appointment.version |
| ended_at | DATETIME(6) | 是 | 无 | 撤销/资格失效时刻 |
| ended_reason | VARCHAR(500) | 是 | 无 | 结束原因 |
| row_version | BIGINT | 否 | 0 | JPA @Version 专用存储修订；不直接作为 API 业务版本 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |
| active_slot | TINYINT | 是 | CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END | ACTIVE 时为1，结束历史为NULL；利用唯一键允许多份历史 |

主键：`id`。

唯一键：`scope_id, account_id, active_slot`。
外键：`scope_id` → `management_scope(id)`，RESTRICT。
外键：`account_id` → `admin_account(id)`，RESTRICT。
检查约束：`status IN ('ACTIVE','REVOKED','INVALIDATED')`。
检查约束：`(status='ACTIVE' AND ended_at IS NULL AND ended_reason IS NULL) OR (status<>'ACTIVE' AND ended_at IS NOT NULL AND ended_reason IS NOT NULL)`。
检查约束：`appointment_version BETWEEN 0 AND 9007199254740991`。
检查约束：`row_version BETWEEN 0 AND 9007199254740991`。

## business_system

业务系统及目录聚合版本；新建同事务建立进入权限

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| code | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 全局唯一稳定编码；新建API限制93字符 |
| name | VARCHAR(100) | 否 | 无 | 名称 |
| description | VARCHAR(500) | 否 | '' | 说明 |
| enabled | TINYINT | 否 | 1 | 系统资源启用 |
| version | BIGINT | 否 | 1 | API 对象业务版本；仅实质变更递增 |
| catalog_version | BIGINT | 否 | 1 | 仅目录实质变化递增 |
| row_version | BIGINT | 否 | 0 | JPA @Version 专用存储修订；不直接作为 API 业务版本 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

唯一键：`code`。
检查约束：`enabled IN (0,1)`。
检查约束：`version BETWEEN 0 AND 9007199254740991`。
检查约束：`catalog_version BETWEEN 0 AND 9007199254740991`。
检查约束：`row_version BETWEEN 0 AND 9007199254740991`。

## directory_node

同系统导航/功能树；操作不作为树节点

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| system_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 所属业务系统 |
| code | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 系统内稳定目录编码 |
| name | VARCHAR(100) | 否 | 无 | 名称 |
| node_type | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 创建后不改类型；枚举：NAVIGATION/FUNCTION |
| parent_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 同系统父节点；根为NULL |
| sort_order | INT | 否 | 0 | 同父级按此值、code、id排序，允许负数/并列 |
| enabled | TINYINT | 否 | 0 | 节点自身状态 |
| visibility_permission_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 本功能menuEligible操作；导航必须为空 |
| version | BIGINT | 否 | 1 | API 对象业务版本；仅实质变更递增 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

唯一键：`system_id, code`。
唯一键：`id, system_id`。
外键：`system_id` → `business_system(id)`，RESTRICT。
外键：`parent_id, system_id` → `directory_node(id, system_id)`，RESTRICT。
外键：`visibility_permission_id, id, system_id` → `permission(id, function_id, system_id)`，RESTRICT。
检查约束：`node_type IN ('NAVIGATION','FUNCTION')`。
检查约束：`parent_id IS NULL OR parent_id <> id`。
检查约束：`(node_type='NAVIGATION' AND visibility_permission_id IS NULL) OR (node_type='FUNCTION' AND (enabled=0 OR visibility_permission_id IS NOT NULL))`。
检查约束：`enabled IN (0,1)`。
检查约束：`version BETWEEN 0 AND 9007199254740991`。

## permission

独立功能操作或唯一系统进入权限；不含数据权限

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| system_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 所属系统 |
| code | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 系统内唯一稳定权限编码 |
| name | VARCHAR(100) | 否 | 无 | 同功能内名称唯一 |
| permission_type | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 权限类型；枚举：SYSTEM_ACCESS/FUNCTION_OPERATION |
| function_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | FUNCTION_OPERATION关联功能节点；进入权限为空 |
| menu_eligible | TINYINT | 否 | 0 | 显式查看/进入页面语义；不解析code |
| enabled | TINYINT | 否 | 1 | 操作自身状态 |
| version | BIGINT | 否 | 1 | API 对象业务版本；仅实质变更递增 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |
| access_slot | TINYINT | 是 | CASE WHEN permission_type='SYSTEM_ACCESS' THEN 1 ELSE NULL END | 系统进入权限为1，其余NULL；系统内唯一 |

主键：`id`。

唯一键：`system_id, code`。
唯一键：`system_id, access_slot`。
唯一键：`function_id, name`。
唯一键：`id, system_id`。
唯一键：`id, function_id, system_id`。
外键：`system_id` → `business_system(id)`，RESTRICT。
外键：`function_id, system_id` → `directory_node(id, system_id)`，RESTRICT。
检查约束：`(permission_type='SYSTEM_ACCESS' AND function_id IS NULL AND menu_eligible=0) OR (permission_type='FUNCTION_OPERATION' AND function_id IS NOT NULL)`。
检查约束：`menu_eligible IN (0,1)`。
检查约束：`enabled IN (0,1)`。
检查约束：`version BETWEEN 0 AND 9007199254740991`。

## business_role

业务角色元数据和有效配置基线；无角色启停

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| code | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 后端生成 ROLE_ 加UUID无连字符；全局唯一且不可更改 |
| name | VARCHAR(100) | 否 | 无 | 可重名的显示名称 |
| description | VARCHAR(500) | 否 | '' | 说明 |
| company_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 固定公司 |
| department_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 部门角色固定部门；公司级共享角色为NULL |
| system_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 固定业务系统 |
| state | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 'UNCONFIGURED' | 首次配置执行成功后EFFECTIVE；枚举：UNCONFIGURED/EFFECTIVE |
| metadata_version | BIGINT | 否 | 1 | 仅名称/说明实质变化递增 |
| configuration_version | BIGINT | 否 | 0 | 有效权限集合版本；未配置0 |
| row_version | BIGINT | 否 | 0 | JPA @Version 专用存储修订；不直接作为 API 业务版本 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

唯一键：`code`。
唯一键：`id, company_id, system_id`。
唯一键：`id, system_id`。
外键：`company_id` → `company(id)`，RESTRICT。
外键：`department_id, company_id` → `department(id, company_id)`，RESTRICT。
外键：`system_id` → `business_system(id)`，RESTRICT。
检查约束：`(state='UNCONFIGURED' AND configuration_version=0) OR (state='EFFECTIVE' AND configuration_version>0)`。
检查约束：`metadata_version BETWEEN 0 AND 9007199254740991`。
检查约束：`configuration_version BETWEEN 0 AND 9007199254740991`。
检查约束：`row_version BETWEEN 0 AND 9007199254740991`。

## role_permission

当前有效角色配置集合；关系只表示授予

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| role_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 业务角色 |
| permission_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 权限项 |
| system_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 约束角色与权限同系统 |

主键：`role_id, permission_id`。

外键：`role_id, system_id` → `business_role(id, system_id)`，RESTRICT。
外键：`permission_id, system_id` → `permission(id, system_id)`，RESTRICT。

## role_configuration_history

每次成功配置的完整历史快照，不是当前授权事实

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| role_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 角色 |
| configuration_version | BIGINT | 否 | 1 | 成功配置版本，无重复 |
| request_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 唯一成功来源申请 |
| configuration_snapshot | JSON | 否 | 无 | 权限稳定ID/类型/code/当时名称及完整集合，schemaVersion=1；JSON顶层OBJECT |
| applied_at | DATETIME(6) | 否 | 无 | 成功配置提交时刻 |

主键：`role_id, configuration_version`。

唯一键：`request_id`。
外键：`role_id` → `business_role(id)`，RESTRICT。
外键：`request_id` → `change_request(id)`，RESTRICT。
检查约束：`configuration_version BETWEEN 0 AND 9007199254740991`。
检查约束：`JSON_TYPE(configuration_snapshot) = 'OBJECT'`。

## authorization_source

员工+公司+来源部门+系统的持久周期；无关系也可存在

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| employee_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 员工 |
| company_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 来源公司 |
| department_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 来源部门 |
| system_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 系统 |
| source_epoch | BIGINT | 否 | 0 | 关闭/资格丧失推进的周期；未落库逻辑基线为0 |
| source_version | BIGINT | 否 | 0 | API SourceBaseline.version；该来源变更递增 |
| row_version | BIGINT | 否 | 0 | JPA @Version 专用存储修订；不直接作为 API 业务版本 |

主键：`id`。

唯一键：`employee_id, company_id, department_id, system_id`。
唯一键：`id, company_id, system_id`。
外键：`employee_id` → `employee(id)`，RESTRICT。
外键：`department_id, company_id` → `department(id, company_id)`，RESTRICT。
外键：`system_id` → `business_system(id)`，RESTRICT。
检查约束：`source_epoch BETWEEN 0 AND 9007199254740991`。
检查约束：`source_version BETWEEN 0 AND 9007199254740991`。
检查约束：`row_version BETWEEN 0 AND 9007199254740991`。

## grant_baseline

同一来源同角色的永久关系版本锚点；不随授权结束删除

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| source_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 独立来源 |
| role_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 业务角色 |
| company_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 用于复合归属约束 |
| system_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 用于复合同系统约束 |
| relation_version | BIGINT | 否 | 0 | API当前关系基线；每次授予/结束递增，未落库为0 |
| row_version | BIGINT | 否 | 0 | JPA @Version 专用存储修订；不直接作为 API 业务版本 |

主键：`id`。

唯一键：`source_id, role_id`。
外键：`source_id, company_id, system_id` → `authorization_source(id, company_id, system_id)`，RESTRICT。
外键：`role_id, company_id, system_id` → `business_role(id, company_id, system_id)`，RESTRICT。
检查约束：`relation_version BETWEEN 0 AND 9007199254740991`。
检查约束：`row_version BETWEEN 0 AND 9007199254740991`。

## role_grant

一次具体来源授予；结束后重授必须新ID

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| baseline_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 永久关系锚点 |
| relation_version | BIGINT | 否 | 1 | 该grant当前演进版本；结束时推进到基线新值 |
| source_epoch | BIGINT | 否 | 0 | 授予时固定来源周期 |
| status | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 'ACTIVE' | 授权关系状态；枚举：ACTIVE/REVOKED/INVALIDATED |
| granted_by_request_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 成功授予申请 |
| ended_by_request_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 一般撤销申请；直接关权时为空 |
| ended_at | DATETIME(6) | 是 | 无 | 结束提交时刻 |
| ended_reason | VARCHAR(500) | 是 | 无 | 撤销/失效原因 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |
| active_slot | TINYINT | 是 | CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END | ACTIVE 时为1，结束历史为NULL；利用唯一键允许多份历史 |

主键：`id`。

唯一键：`baseline_id, active_slot`。
唯一键：`baseline_id, relation_version`。
外键：`baseline_id` → `grant_baseline(id)`，RESTRICT。
外键：`granted_by_request_id` → `change_request(id)`，RESTRICT。
外键：`ended_by_request_id` → `change_request(id)`，RESTRICT。
检查约束：`status IN ('ACTIVE','REVOKED','INVALIDATED')`。
检查约束：`(status='ACTIVE' AND ended_at IS NULL AND ended_reason IS NULL AND ended_by_request_id IS NULL) OR (status<>'ACTIVE' AND ended_at IS NOT NULL AND ended_reason IS NOT NULL)`。
检查约束：`relation_version BETWEEN 0 AND 9007199254740991`。
检查约束：`source_epoch BETWEEN 0 AND 9007199254740991`。

## grant_employment_evidence

实际授予时适用任职依据；后续不因岗位改名改写

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| grant_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 授权关系 |
| employment_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 当时适用任职 |

主键：`grant_id, employment_id`。

外键：`grant_id` → `role_grant(id)`，RESTRICT。
外键：`employment_id` → `employment(id)`，RESTRICT。

## change_request

草稿与固定申请共用生命周期；七响应模型不建七表

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| kind | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 变更类型；枚举：ROLE_CONFIGURATION/ROLE_GRANT/ROLE_REVOCATION |
| company_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 一申请一个公司 |
| content | JSON | 否 | 无 | 草稿可编辑；提交后实际明细冻结，与规范字节一致；JSON顶层OBJECT |
| reason | VARCHAR(500) | 否 | 无 | 提交原因；提交后不可改 |
| draft_version | BIGINT | 否 | 1 | 协作编辑版本；提交后冻结 |
| status_version | BIGINT | 否 | 1 | 投递/审批/执行状态修订 |
| submitted_version | BIGINT | 是 | 无 | 固定提交版本；取提交时draftVersion，提交后不变 |
| submitted_at | DATETIME(6) | 是 | 无 | 固定提交时刻，进入摘要 |
| submitted_by_account_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 原提交人；离任不改写 |
| state | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 'DRAFT' | 受三轴约束的聚合展示状态；枚举：DRAFT/PENDING/REJECTED/APPROVED/EXECUTION_FAILED/EXECUTED/INVALIDATED |
| delivery_status | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 'NOT_SUBMITTED' | OA投递状态；枚举：NOT_SUBMITTED/PENDING_SEND/SEND_FAILED/LINKED |
| approval_result | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 'NONE' | 可信最终结果；枚举：NONE/APPROVED/REJECTED |
| execution_status | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 'NOT_STARTED' | 本地执行状态；枚举：NOT_STARTED/PENDING/FAILED/SUCCEEDED/INVALIDATED |
| authority_type | VARCHAR(10) | 是 | 无 | NULL草稿；GLOBAL或SCOPE |
| scope_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 原范围ID，仅SCOPE；范围保留 |
| appointment_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 原任命ID；历史保留，执行不要求仍ACTIVE |
| original_authority | JSON | 是 | 无 | 固定原资格完整快照，API Authority形状；JSON顶层OBJECT |
| fixed_payload_bytes | MEDIUMBLOB | 是 | 无 | RFC8785规范UTF-8摘要包原字节；不从MySQL JSON序列化重建 |
| content_digest | BINARY(32) | 是 | 无 | 规范字节SHA-256；API输出64位小写hex |
| failure_code | VARCHAR(64) | 是 | 无 | 失效或可重试失败安全码 |
| next_retry_at | DATETIME(6) | 是 | 无 | 执行恢复计划；非取消期限 |
| row_version | BIGINT | 否 | 0 | JPA @Version 专用存储修订；不直接作为 API 业务版本 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

唯一键：`id, kind`。
外键：`company_id` → `company(id)`，RESTRICT。
外键：`scope_id` → `management_scope(id)`，RESTRICT。
外键：`appointment_id` → `admin_appointment(id)`，RESTRICT。
外键：`submitted_by_account_id` → `admin_account(id)`，RESTRICT。
检查约束：`kind IN ('ROLE_CONFIGURATION','ROLE_GRANT','ROLE_REVOCATION')`。
检查约束：`delivery_status IN ('NOT_SUBMITTED','PENDING_SEND','SEND_FAILED','LINKED')`。
检查约束：`approval_result IN ('NONE','APPROVED','REJECTED')`。
检查约束：`execution_status IN ('NOT_STARTED','PENDING','FAILED','SUCCEEDED','INVALIDATED')`。
检查约束：`(state='DRAFT' AND submitted_version IS NULL AND submitted_at IS NULL AND submitted_by_account_id IS NULL AND original_authority IS NULL AND authority_type IS NULL AND scope_id IS NULL AND appointment_id IS NULL AND fixed_payload_bytes IS NULL AND content_digest IS NULL) OR (state<>'DRAFT' AND submitted_version IS NOT NULL AND submitted_version>0 AND submitted_at IS NOT NULL AND submitted_by_account_id IS NOT NULL AND original_authority IS NOT NULL AND authority_type IS NOT NULL AND fixed_payload_bytes IS NOT NULL AND content_digest IS NOT NULL AND ((authority_type='GLOBAL' AND scope_id IS NULL AND appointment_id IS NULL) OR (authority_type='SCOPE' AND scope_id IS NOT NULL AND appointment_id IS NOT NULL)))`。
检查约束：`(state='DRAFT' AND delivery_status='NOT_SUBMITTED' AND approval_result='NONE' AND execution_status='NOT_STARTED' AND failure_code IS NULL AND next_retry_at IS NULL) OR (state='PENDING' AND delivery_status IN ('PENDING_SEND','SEND_FAILED','LINKED') AND approval_result='NONE' AND execution_status='NOT_STARTED' AND failure_code IS NULL AND next_retry_at IS NULL) OR (state='REJECTED' AND delivery_status='LINKED' AND approval_result='REJECTED' AND execution_status='NOT_STARTED' AND failure_code IS NULL AND next_retry_at IS NULL) OR (state='APPROVED' AND delivery_status='LINKED' AND approval_result='APPROVED' AND execution_status='PENDING' AND failure_code IS NULL) OR (state='EXECUTION_FAILED' AND delivery_status='LINKED' AND approval_result='APPROVED' AND execution_status='FAILED' AND failure_code IS NOT NULL AND next_retry_at IS NOT NULL) OR (state='EXECUTED' AND delivery_status='LINKED' AND approval_result='APPROVED' AND execution_status='SUCCEEDED' AND failure_code IS NULL AND next_retry_at IS NULL) OR (state='INVALIDATED' AND delivery_status IN ('PENDING_SEND','SEND_FAILED','LINKED') AND (delivery_status='LINKED' OR approval_result='NONE') AND execution_status='INVALIDATED' AND failure_code IS NOT NULL AND next_retry_at IS NULL)`。
检查约束：`JSON_TYPE(content) = 'OBJECT'`。
检查约束：`draft_version BETWEEN 0 AND 9007199254740991`。
检查约束：`status_version BETWEEN 0 AND 9007199254740991`。
检查约束：`submitted_version BETWEEN 0 AND 9007199254740991`。
检查约束：`JSON_TYPE(original_authority) = 'OBJECT'`。
检查约束：`row_version BETWEEN 0 AND 9007199254740991`。

## change_target

草稿目标/提交后实际清单的可检索投影；必须与对应content一致

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| request_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 所属申请 |
| line_no | INT | 否 | 无 | 固定清单序号，从0起 |
| line_kind | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 匹配父申请类型；枚举：ROLE_CONFIGURATION/ROLE_GRANT/ROLE_REVOCATION |
| company_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 固定公司 |
| system_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 固定系统 |
| department_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 固定来源/角色部门；共享配置可为空 |
| employee_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 分配/撤销目标员工；配置为空 |
| role_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 明确角色 |
| source_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 分配/撤销来源；配置为空 |
| baseline_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 分配/撤销永久关系锚点；配置为空 |
| grant_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 撤销的准确历史ID；分配/配置为空 |
| expected_configuration_version | BIGINT | 是 | 无 | 配置/授予检查；一般撤销为空 |
| expected_source_epoch | BIGINT | 是 | 无 | 授予固定周期；其他类型为空 |
| expected_relation_version | BIGINT | 是 | 无 | 授予/撤销固定关系版本 |
| target_snapshot | JSON | 否 | 无 | schemaVersion=1；当时名称、适用employmentIds、完整权限差异/依据；JSON顶层OBJECT |

主键：`request_id, line_no`。

唯一键：`request_id, baseline_id`。
唯一键：`request_id, grant_id`。
外键：`request_id, line_kind` → `change_request(id, kind)`，RESTRICT。
外键：`role_id, company_id, system_id` → `business_role(id, company_id, system_id)`，RESTRICT。
外键：`department_id, company_id` → `department(id, company_id)`，RESTRICT。
外键：`employee_id` → `employee(id)`，RESTRICT。
外键：`source_id` → `authorization_source(id)`，RESTRICT。
外键：`baseline_id` → `grant_baseline(id)`，RESTRICT。
外键：`grant_id` → `role_grant(id)`，RESTRICT。
外键：`system_id` → `business_system(id)`，RESTRICT。
检查约束：`line_no BETWEEN 0 AND 99`。
检查约束：`(line_kind='ROLE_CONFIGURATION' AND line_no=0 AND employee_id IS NULL AND source_id IS NULL AND baseline_id IS NULL AND grant_id IS NULL AND expected_configuration_version IS NOT NULL AND expected_source_epoch IS NULL AND expected_relation_version IS NULL) OR (line_kind='ROLE_GRANT' AND employee_id IS NOT NULL AND department_id IS NOT NULL AND source_id IS NOT NULL AND baseline_id IS NOT NULL AND grant_id IS NULL AND expected_configuration_version IS NOT NULL AND expected_source_epoch IS NOT NULL AND expected_relation_version IS NOT NULL) OR (line_kind='ROLE_REVOCATION' AND employee_id IS NOT NULL AND department_id IS NOT NULL AND source_id IS NOT NULL AND baseline_id IS NOT NULL AND grant_id IS NOT NULL AND expected_configuration_version IS NULL AND expected_source_epoch IS NULL AND expected_relation_version IS NOT NULL)`。
检查约束：`expected_configuration_version BETWEEN 0 AND 9007199254740991`。
检查约束：`expected_source_epoch BETWEEN 0 AND 9007199254740991`。
检查约束：`expected_relation_version BETWEEN 0 AND 9007199254740991`。
检查约束：`JSON_TYPE(target_snapshot) = 'OBJECT'`。

## role_change_claim

当前角色配置通道；终态删除占用，历史不靠本表

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| role_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 角色唯一配置通道 |
| request_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 占用申请 |

主键：`role_id`。

唯一键：`request_id`。
外键：`role_id` → `business_role(id)`，RESTRICT。
外键：`request_id` → `change_request(id)`，RESTRICT。

## relation_change_claim

当前关系变更通道；同来源去重，不阻塞其他来源

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| baseline_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 关系唯一通道 |
| request_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 占用申请 |

主键：`baseline_id`。

外键：`baseline_id` → `grant_baseline(id)`，RESTRICT。
外键：`request_id` → `change_request(id)`，RESTRICT。

## service_identity

部署管理的服务身份和绑定；不保存私钥/令牌

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| subject | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | JWT sub，全局唯一 |
| kind | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 入口类型；枚举：BUSINESS/OA |
| system_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | BUSINESS唯一绑定系统，OA为空 |
| enabled | TINYINT | 否 | 1 | 当前服务身份状态 |
| version | BIGINT | 否 | 1 | API 对象业务版本；仅实质变更递增 |

主键：`id`。

唯一键：`subject`。
外键：`system_id` → `business_system(id)`，RESTRICT。
检查约束：`(kind='BUSINESS' AND system_id IS NOT NULL) OR (kind='OA' AND system_id IS NULL)`。
检查约束：`enabled IN (0,1)`。
检查约束：`version BETWEEN 0 AND 9007199254740991`。

## oa_link

可靠外部审批关联；先由投递响应落地，回调不得抢绑

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| request_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 申请，一对一 |
| oa_instance_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 部署OA服务身份 |
| oa_approval_id | VARCHAR(64) | 否 | 无 | 外部审批编号 |
| accepted_at | DATETIME(6) | 否 | 无 | OA受理时刻 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`request_id`。

唯一键：`oa_instance_id, oa_approval_id`。
外键：`request_id` → `change_request(id)`，RESTRICT。
外键：`oa_instance_id` → `service_identity(id)`，RESTRICT。

## oa_outbox

同一固定申请可靠投递待办及租约；不复制可变申请内容

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| request_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 申请，一申请一待办 |
| oa_instance_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 目标OA实例 |
| task_state | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 'READY' | 仅内部调度状态；枚举：READY/LEASED/DONE/STOPPED |
| next_attempt_at | DATETIME(6) | 否 | 无 | 下次投递时刻 |
| lease_token | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 每次领取随机令牌 |
| lease_until | DATETIME(6) | 是 | 无 | 领取过期时间 |
| attempt_count | INT | 否 | 0 | 已开始尝试数 |
| last_failure_code | VARCHAR(64) | 是 | 无 | 安全故障码 |
| row_version | BIGINT | 否 | 0 | JPA @Version 专用存储修订；不直接作为 API 业务版本 |

主键：`request_id`。

外键：`request_id` → `change_request(id)`，RESTRICT。
外键：`oa_instance_id` → `service_identity(id)`，RESTRICT。
检查约束：`task_state IN ('READY','LEASED','DONE','STOPPED')`。
检查约束：`attempt_count>=0`。
检查约束：`(task_state='LEASED' AND lease_token IS NOT NULL AND lease_until IS NOT NULL) OR (task_state<>'LEASED' AND lease_token IS NULL AND lease_until IS NULL)`。
检查约束：`row_version BETWEEN 0 AND 9007199254740991`。

## oa_callback_event

OA实例+事件ID防重；同事件不同内容不得覆盖

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| oa_instance_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 可信OA实例 |
| event_id | VARCHAR(64) | 否 | 无 | OA最终结果事件编号 |
| request_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 匹配的固定申请 |
| payload_digest | BINARY(32) | 否 | 无 | 完整规范化回调内容摘要 |
| safe_payload | JSON | 否 | 无 | 不含认证凭据的最终结果原字段；JSON顶层OBJECT |
| received_at | DATETIME(6) | 否 | 无 | 首次可靠接收时刻 |

主键：`id`。

唯一键：`oa_instance_id, event_id`。
外键：`oa_instance_id` → `service_identity(id)`，RESTRICT。
外键：`request_id` → `change_request(id)`，RESTRICT。
检查约束：`JSON_TYPE(safe_payload) = 'OBJECT'`。

## oa_final_result

一申请一不可反转最终结果；新eventId不绕过

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| request_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 申请 |
| first_event_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 首次可信回调记录ID |
| decision | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 最终结果；枚举：APPROVED/REJECTED |
| decided_at | DATETIME(6) | 否 | 无 | OA决定时刻；不是覆盖优先级 |
| comment | VARCHAR(500) | 否 | 无 | 意见摘要 |
| received_at | DATETIME(6) | 否 | 无 | 平台接收时刻 |

主键：`request_id`。

唯一键：`first_event_id`。
外键：`request_id` → `change_request(id)`，RESTRICT。
外键：`first_event_id` → `oa_callback_event(id)`，RESTRICT。
检查约束：`decision IN ('APPROVED','REJECTED')`。

## execution_success

一申请一次成功凭据；业务写入/成功审计同事务

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| request_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 成功申请唯一键 |
| audit_event_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 唯一成功业务事件 |
| executed_at | DATETIME(6) | 否 | 无 | 成功提交时刻 |
| result_snapshot | JSON | 否 | 无 | 实际授予ID/撤销ID/配置版本与计数，不含凭据；JSON顶层OBJECT |

主键：`request_id`。

唯一键：`audit_event_id`。
外键：`request_id` → `change_request(id)`，RESTRICT。
外键：`audit_event_id` → `audit_event(id)`，RESTRICT。
检查约束：`JSON_TYPE(result_snapshot) = 'OBJECT'`。

## execution_attempt

本地执行尝试历史；业务失败回滚后独立记录

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| request_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 原固定申请 |
| attempt_no | INT | 否 | 无 | 该申请递增尝试序号 |
| outcome | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 尝试结果；枚举：FAILED/INVALIDATED/SUCCEEDED |
| failure_code | VARCHAR(64) | 是 | 无 | 安全失败/失效码 |
| started_at | DATETIME(6) | 否 | 无 | 开始时间 |
| finished_at | DATETIME(6) | 否 | 无 | 记录结果时间 |

主键：`id`。

唯一键：`request_id, attempt_no`。
外键：`request_id` → `change_request(id)`，RESTRICT。
检查约束：`attempt_no>0`。
检查约束：`outcome IN ('FAILED','INVALIDATED','SUCCEEDED')`。
检查约束：`finished_at>=started_at`。

## http_idempotency

HTTP接受结果窗口；独立于OA与业务成功防重

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| principal_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 当前后台账号主体 |
| http_method | VARCHAR(10) | 否 | 无 | 大写HTTP方法 |
| canonical_path | VARCHAR(300) | 否 | 无 | 包含稳定资源ID的规范路径，不包含易变查询排序 |
| idempotency_key | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 客户端UUID键 |
| request_digest | BINARY(32) | 否 | 无 | 包含版本/目标/规范内容的受保护摘要 |
| digest_key_id | VARCHAR(64) | 否 | 无 | 摘要HMAC密钥版本；密钥不入库 |
| state | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 处理中或已接受结果；枚举：IN_PROGRESS/COMPLETED |
| http_status | SMALLINT | 是 | 无 | 原接受HTTP状态码 |
| resource_location | VARCHAR(300) | 是 | 无 | 原创建/接受资源地址，无则NULL |
| safe_response | JSON | 是 | 无 | 原安全响应；不含密码/JWT/哈希；JSON顶层OBJECT |
| lease_token | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 当前处理者随机栅栏令牌；完成后清空 |
| lease_until | DATETIME(6) | 是 | 无 | 处理中租约；过期仅允许同内容恢复接管 |
| expires_at | DATETIME(6) | 否 | 无 | 至少首次接受后7天；不删除长期业务防重 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

唯一键：`principal_id, http_method, canonical_path, idempotency_key`。
外键：`principal_id` → `admin_account(id)`，RESTRICT。
检查约束：`(state='IN_PROGRESS' AND http_status IS NULL AND safe_response IS NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL) OR (state='COMPLETED' AND http_status BETWEEN 200 AND 599 AND safe_response IS NOT NULL AND lease_token IS NULL AND lease_until IS NULL)`。
检查约束：`expires_at>created_at`。
检查约束：`JSON_TYPE(safe_response) = 'OBJECT'`。

## import_file

导入文件元数据；文件本体受控存储且不可原地覆盖

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| file_name | VARCHAR(200) | 否 | 无 | 原文件名，仅显示不参与路径拼接 |
| sha256 | BINARY(32) | 否 | 无 | 原文件字节摘要 |
| size_bytes | BIGINT | 否 | 无 | 原文件大小 |
| storage_ref | VARCHAR(300) | 否 | 无 | 服务端受控文件引用，非客户端任意URL |
| sheet_names | JSON | 否 | 无 | 可用工作表名称，CSV固定CSV；JSON顶层ARRAY |
| uploaded_by | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 上传总管理员 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

外键：`uploaded_by` → `admin_account(id)`，RESTRICT。
检查约束：`size_bytes BETWEEN 1 AND 10485760`。
检查约束：`JSON_TYPE(sheet_names) = 'ARRAY'`。

## import_batch

冻结的预检与原子导入结果；成功sourceBatchId长期防重

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| file_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变原文件 |
| source_system | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 来源系统 |
| source_batch_id | VARCHAR(100) COLLATE utf8mb4_0900_bin | 否 | 无 | 来源批次业务编号 |
| import_type | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 导入类别；枚举：ORGANIZATION/EMPLOYEE_INITIALIZATION/PERSONNEL_CHANGES |
| input_snapshot | JSON | 否 | 无 | 完整ImportInput：worksheet、字段/组织映射、初始化状态、原因；JSON顶层OBJECT |
| input_digest | BINARY(32) | 否 | 无 | 文件SHA+完整规范输入摘要，定位同批同内容 |
| content_digest | BINARY(32) | 否 | 无 | 规范行、前后值、候选代码和依赖基线摘要 |
| state | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | API预检/执行状态；枚举：BLOCKED/READY/SUCCEEDED/FAILED/STALE |
| version | BIGINT | 否 | 1 | API 对象业务版本；仅实质变更递增 |
| planned_counts | JSON | 否 | 无 | 冻结计划ImportCounts八字段；JSON顶层OBJECT |
| actual_counts | JSON | 是 | 无 | 仅成功时实际八字段计数；JSON顶层OBJECT |
| blocking_issue_count | INT | 否 | 0 | 阻断问题数 |
| warning_count | INT | 否 | 0 | 警告数 |
| failure_code | VARCHAR(64) | 是 | 无 | 安全故障码 |
| sealed_at | DATETIME(6) | 是 | 无 | 预检完成冻结时间；API只暴露已冻结批次 |
| committed_at | DATETIME(6) | 是 | 无 | 成功提交时刻 |
| created_by | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 发起总管理员 |
| row_version | BIGINT | 否 | 0 | JPA @Version 专用存储修订；不直接作为 API 业务版本 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

唯一键：`source_system, source_batch_id`。
外键：`file_id` → `import_file(id)`，RESTRICT。
外键：`created_by` → `admin_account(id)`，RESTRICT。
检查约束：`import_type IN ('ORGANIZATION','EMPLOYEE_INITIALIZATION','PERSONNEL_CHANGES')`。
检查约束：`state IN ('BLOCKED','READY','SUCCEEDED','FAILED','STALE')`。
检查约束：`(state='SUCCEEDED' AND actual_counts IS NOT NULL AND committed_at IS NOT NULL) OR (state<>'SUCCEEDED' AND actual_counts IS NULL AND committed_at IS NULL)`。
检查约束：`blocking_issue_count>=0 AND warning_count>=0`。
检查约束：`JSON_TYPE(input_snapshot) = 'OBJECT'`。
检查约束：`version BETWEEN 0 AND 9007199254740991`。
检查约束：`JSON_TYPE(planned_counts) = 'OBJECT'`。
检查约束：`JSON_TYPE(actual_counts) = 'OBJECT'`。
检查约束：`row_version BETWEEN 0 AND 9007199254740991`。

## import_plan_row

冻结规范行及逐字段前后值；候选组织尚未建正式记录

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| batch_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 所属预检 |
| row_number | INT | 否 | 无 | 原文件行号 |
| plan_no | INT | 否 | 无 | 行内规范动作序号，同一原行可产生多动作 |
| action | VARCHAR(32) | 否 | 无 | ImportPlanChange.action明确动作或UNCHANGED |
| target_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 既有或预分配候选ID；无FK以允许尚未创建对象 |
| stable_code | VARCHAR(100) | 是 | 无 | 候选/既有稳定编码 |
| company_code | VARCHAR(100) | 是 | 无 | 完整公司归属编码 |
| department_code | VARCHAR(100) | 是 | 无 | 完整部门归属编码 |
| position_code | VARCHAR(100) | 是 | 无 | 完整岗位归属编码 |
| expected_version | BIGINT | 是 | 无 | 既有目标预检版本；新对象为空 |
| summary | VARCHAR(500) | 否 | 无 | 安全摘要 |
| normalized_values | JSON | 否 | 无 | 类型校验后的完整规范输入，不执行公式；JSON顶层OBJECT |
| field_changes | JSON | 否 | 无 | ImportValueChange[]，明确字段及before/after；JSON顶层ARRAY |
| dependency_snapshot | JSON | 否 | 无 | 目标版本、唯一键不存在断言、引用集合摘要和来源周期；JSON顶层OBJECT |

主键：`batch_id, row_number, plan_no`。

外键：`batch_id` → `import_batch(id)`，RESTRICT。
检查约束：`row_number>0 AND plan_no>=0`。
检查约束：`expected_version BETWEEN 0 AND 9007199254740991`。
检查约束：`JSON_TYPE(normalized_values) = 'OBJECT'`。
检查约束：`JSON_TYPE(field_changes) = 'ARRAY'`。
检查约束：`JSON_TYPE(dependency_snapshot) = 'OBJECT'`。

## import_issue

冻结预检报告；仅总管理员可读

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| batch_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 预检批次 |
| row_number | INT | 否 | 无 | 原始行号 |
| field | VARCHAR(100) | 否 | 无 | 字段定位 |
| severity | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 问题严重性；枚举：WARNING/BLOCKING |
| code | VARCHAR(64) | 否 | 无 | 稳定问题码 |
| message | VARCHAR(500) | 否 | 无 | 安全问题说明 |

主键：`id`。

外键：`batch_id` → `import_batch(id)`，RESTRICT。
检查约束：`severity IN ('WARNING','BLOCKING')`。
检查约束：`row_number>0`。

## impact_preview

五分钟固定预览；不是授权凭证

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| creator_account_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 创建身份 |
| appointment_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 创建时所选任命；总管理员为空 |
| authority_snapshot | JSON | 否 | 无 | 创建时范围/版本及可见边界；JSON顶层OBJECT |
| operation | VARCHAR(64) | 否 | 无 | 绑定操作语义与目标资源 |
| input_digest | BINARY(32) | 否 | 无 | 规范输入及目标版本摘要 |
| content_digest | BINARY(32) | 否 | 无 | 预览确认摘要 |
| dependency_snapshot | JSON | 否 | 无 | 影响实际清单/分类/统计的版本和集合依赖，见TRANSACTIONS；JSON顶层OBJECT |
| result_snapshot | JSON | 否 | 无 | Preview/ChangePreview的固定汇总、实际清单与noChangeItems；JSON顶层OBJECT |
| expires_at | DATETIME(6) | 否 | 无 | 创建后五分钟 |
| created_at | DATETIME(6) | 否 | 无 | 记录创建时刻，UTC，非源系统业务起始日期 |

主键：`id`。

外键：`creator_account_id` → `admin_account(id)`，RESTRICT。
外键：`appointment_id` → `admin_appointment(id)`，RESTRICT。
检查约束：`expires_at>created_at`。
检查约束：`JSON_TYPE(authority_snapshot) = 'OBJECT'`。
检查约束：`JSON_TYPE(dependency_snapshot) = 'OBJECT'`。
检查约束：`JSON_TYPE(result_snapshot) = 'OBJECT'`。

## preview_item

同一预览的可见员工/目标分页快照；禁止实时重算混页

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| preview_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 预览 |
| item_kind | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 目标类别；枚举：EMPLOYEE/ROLE/GRANT/SOURCE/APPOINTMENT/PENDING_REQUEST |
| stable_key | VARCHAR(200) | 否 | 无 | 稳定ID或SOURCE完整四元键；员工项按员工公司系统键 |
| safe_item | JSON | 否 | 无 | API ImpactEmployee/ImpactTarget；仅创建时有权明细；JSON顶层OBJECT |
| visibility_basis | JSON | 否 | 无 | 读取时重新检查的固定来源边界，混合申请须全部覆盖；JSON顶层OBJECT |

主键：`id`。

唯一键：`preview_id, item_kind, stable_key`。
外键：`preview_id` → `impact_preview(id)`，RESTRICT。
检查约束：`item_kind IN ('EMPLOYEE','ROLE','GRANT','SOURCE','APPOINTMENT','PENDING_REQUEST')`。
检查约束：`JSON_TYPE(safe_item) = 'OBJECT'`。
检查约束：`JSON_TYPE(visibility_basis) = 'OBJECT'`。

## audit_event

追加式授权/管理审计和申请事件；不通过当前资料倒推历史

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 不可变内部 UUID v4；API 原样输出小写带连字符字符串 |
| occurred_at | DATETIME(6) | 否 | 无 | 事件发生时刻，UTC |
| event_type | VARCHAR(64) | 否 | 无 | 事件类型；提交/批准/执行分别记录 |
| actor_kind | VARCHAR(16) | 否 | 无 | ACCOUNT / SERVICE / DEPLOYMENT |
| actor_id | VARCHAR(64) | 否 | 无 | 当时操作者稳定标识；部署事件可无账号FK |
| original_submitter_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 原申请人账号，已提交申请事件适用 |
| target_id | VARCHAR(64) | 否 | 无 | 稳定目标标识或聚合ID |
| change_request_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 申请；草稿删除事件仅快照保留被删除ID |
| company_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 单来源公司；全局/混合无法安全分隔为空 |
| source_department_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 单来源部门 |
| system_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 是 | 无 | 单来源系统 |
| visibility | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 全局记录仅总管理员；SCOPED按固定来源；枚举：GLOBAL/SCOPED |
| result | VARCHAR(64) | 否 | 无 | 安全业务结果 |
| correlation_id | VARCHAR(64) | 否 | 无 | HTTP X-Request-Id，不是防重键 |
| reason | VARCHAR(500) | 否 | 无 | 业务原因 |
| event_snapshot | JSON | 否 | 无 | schemaVersion=1；当时名称、原/实际差异、审批编号/版本、失败码、敏感信息脱敏；JSON顶层OBJECT |

主键：`id`。

外键：`original_submitter_id` → `admin_account(id)`，RESTRICT。
外键：`company_id` → `company(id)`，RESTRICT。
外键：`source_department_id, company_id` → `department(id, company_id)`，RESTRICT。
外键：`system_id` → `business_system(id)`，RESTRICT。
检查约束：`visibility IN ('GLOBAL','SCOPED')`。
检查约束：`actor_kind IN ('ACCOUNT','SERVICE','DEPLOYMENT')`。
检查约束：`visibility='GLOBAL' OR (company_id IS NOT NULL AND source_department_id IS NOT NULL AND system_id IS NOT NULL)`。
检查约束：`JSON_TYPE(event_snapshot) = 'OBJECT'`。

## audit_subject

审计按稳定员工/角色等目标筛选的历史索引；可指已删除草稿对象

| 字段 | MySQL类型/长度 | NULL | 默认/生成表达式 | 业务含义 |
| --- | --- | --- | --- | --- |
| audit_event_id | CHAR(36) CHARACTER SET ascii COLLATE ascii_bin | 否 | 无 | 所属审计事件 |
| subject_type | VARCHAR(16) | 否 | 无 | EMPLOYEE / ROLE / GRANT / ACCOUNT / REQUEST等稳定类型 |
| subject_id | VARCHAR(64) | 否 | 无 | 当时稳定对象标识，有意不设多态外键 |

主键：`audit_event_id, subject_type, subject_id`。

外键：`audit_event_id` → `audit_event(id)`，RESTRICT。
