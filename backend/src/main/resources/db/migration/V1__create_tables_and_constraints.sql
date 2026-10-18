-- V1__create_tables_and_constraints.sql; derived from docs/04-database/schema.sql.
-- MySQL 8.4; engine execution and constraint validation are still pending.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_bin;
SET time_zone = '+00:00';

CREATE TABLE `mutation_guard` (
  `id` SMALLINT NOT NULL COMMENT '固定为1',
  `revision` BIGINT NOT NULL DEFAULT 0 COMMENT '内部提交序号，不映射API版本',
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_mutation_guard_1` CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='一期本地写事务串行化锚点；没有业务权限含义';

CREATE TABLE `company` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `code` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '全局唯一稳定公司编码',
  `name` VARCHAR(100) NOT NULL COMMENT '显示名称',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API 对象业务版本；仅实质变更递增',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_company_1` (`code`),
  CONSTRAINT `ck_company_1` CHECK (version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='公司；无启停或公司层级授权';

CREATE TABLE `department` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `company_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属公司',
  `code` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '公司内稳定编码',
  `name` VARCHAR(100) NOT NULL COMMENT '显示名称',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API 对象业务版本；仅实质变更递增',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_department_1` (`company_id`, `code`),
  UNIQUE KEY `uq_department_2` (`id`, `company_id`),
  CONSTRAINT `ck_department_1` CHECK (version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='一公司内平级部门；无父级和状态';

CREATE TABLE `position` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `department_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属部门',
  `code` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '部门内稳定编码',
  `name` VARCHAR(100) NOT NULL COMMENT '显示名称',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API 对象业务版本；仅实质变更递增',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_position_1` (`department_id`, `code`),
  UNIQUE KEY `uq_position_2` (`id`, `department_id`),
  CONSTRAINT `ck_position_1` CHECK (version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='明确部门内岗位；不关联业务角色';

CREATE TABLE `employee` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `employee_key` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '平台当前统一员工标识，全局唯一，不按姓名匹配',
  `name` VARCHAR(100) NOT NULL COMMENT '必要姓名',
  `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '全局身份状态；枚举：ACTIVE/INACTIVE',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API 对象业务版本；仅实质变更递增',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_employee_1` (`employee_key`),
  CONSTRAINT `ck_employee_1` CHECK (status IN ('ACTIVE','INACTIVE')),
  CONSTRAINT `ck_employee_2` CHECK (version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='统一员工身份；全局停用独立于后台账号';

CREATE TABLE `employee_source_identity` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `source_system` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '来源系统标识',
  `source_key` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '来源的当前唯一标识',
  `employee_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '内部员工',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API 对象业务版本；仅实质变更递增',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_employee_source_identity_1` (`source_system`, `source_key`),
  KEY `ix_employee_source_identity_1` (`employee_id`),
  CONSTRAINT `ck_employee_source_identity_1` CHECK (version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='当前来源标识到内部员工的显式映射；重映射旧值另存审计';

CREATE TABLE `organization_mapping` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `source_system` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '来源系统',
  `source_path` VARCHAR(300) NOT NULL COMMENT '完整来源路径，二进制区分比较',
  `object_kind` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '映射对象类型；枚举：COMPANY/DEPARTMENT/POSITION',
  `company_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '明确公司',
  `department_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '明确部门；公司映射为空',
  `position_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '明确岗位；非岗位映射为空',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API 对象业务版本；仅实质变更递增',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_organization_mapping_1` (`source_system`, `source_path`),
  KEY `ix_organization_mapping_1` (`company_id`),
  KEY `ix_organization_mapping_2` (`department_id`, `company_id`),
  KEY `ix_organization_mapping_3` (`position_id`, `department_id`),
  CONSTRAINT `ck_organization_mapping_1` CHECK ((object_kind='COMPANY' AND department_id IS NULL AND position_id IS NULL) OR (object_kind='DEPARTMENT' AND department_id IS NOT NULL AND position_id IS NULL) OR (object_kind='POSITION' AND department_id IS NOT NULL AND position_id IS NOT NULL)),
  CONSTRAINT `ck_organization_mapping_2` CHECK (version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='经过确认的完整来源路径映射；没有组织自动合并';

CREATE TABLE `employment` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `employee_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '员工',
  `company_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '任职公司',
  `department_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '任职部门',
  `position_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '任职岗位',
  `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '任职状态；枚举：ACTIVE/ENDED',
  `started_at` DATETIME(6) NULL COMMENT '源资料明确的业务开始时间；初始化未知为NULL',
  `ended_at` DATETIME(6) NULL COMMENT '源资料明确的业务结束时间；未知可为NULL，状态不靠时间推断',
  `recorded_end_at` DATETIME(6) NULL COMMENT '平台记录结束动作时间；ENDED必须有值',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API 对象业务版本；仅实质变更递增',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  `active_slot` TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED COMMENT 'ACTIVE 时为1，结束历史为NULL；利用唯一键允许多份历史',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_employment_1` (`employee_id`, `position_id`, `active_slot`),
  KEY `ix_employment_1` (`employee_id`, `company_id`, `department_id`, `status`),
  KEY `ix_employment_2` (`department_id`, `company_id`),
  KEY `ix_employment_3` (`position_id`, `department_id`),
  CONSTRAINT `ck_employment_1` CHECK (status IN ('ACTIVE','ENDED')),
  CONSTRAINT `ck_employment_2` CHECK ((status='ACTIVE' AND recorded_end_at IS NULL AND ended_at IS NULL) OR (status='ENDED' AND recorded_end_at IS NOT NULL)),
  CONSTRAINT `ck_employment_3` CHECK (started_at IS NULL OR ended_at IS NULL OR ended_at >= started_at),
  CONSTRAINT `ck_employment_4` CHECK (version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='独立任职及结束历史；同员工同岗位至多一个当前任职';

CREATE TABLE `admin_account` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `employee_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '关联员工；部署总管理员允许为空',
  `login_name` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '当前登录名全局唯一',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '带算法参数和盐的专用密码摘要；不存密码历史',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '后台账号启用',
  `is_super_admin` TINYINT NOT NULL DEFAULT 0 COMMENT '部署管理的全局后台身份；普通创建API不得写',
  `must_change_password` TINYINT NOT NULL DEFAULT 1 COMMENT '临时密码强制修改',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API 对象业务版本；仅实质变更递增',
  `credential_version` BIGINT NOT NULL DEFAULT 1 COMMENT '改密/重置递增，JWT须精确匹配',
  `session_epoch` BIGINT NOT NULL DEFAULT 1 COMMENT '失去全部资格/停用等递增，恢复不回退',
  `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA @Version 专用存储修订；不直接作为 API 业务版本',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_admin_account_1` (`login_name`),
  UNIQUE KEY `uq_admin_account_2` (`employee_id`),
  CONSTRAINT `ck_admin_account_1` CHECK (is_super_admin=1 OR employee_id IS NOT NULL),
  CONSTRAINT `ck_admin_account_2` CHECK (enabled IN (0,1)),
  CONSTRAINT `ck_admin_account_3` CHECK (is_super_admin IN (0,1)),
  CONSTRAINT `ck_admin_account_4` CHECK (must_change_password IN (0,1)),
  CONSTRAINT `ck_admin_account_5` CHECK (version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_admin_account_6` CHECK (credential_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_admin_account_7` CHECK (session_epoch BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_admin_account_8` CHECK (row_version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='后台账号及凭据失效依据；总管理员仅部署初始化';

CREATE TABLE `admin_session` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'JWT sid，不可猜测 UUID',
  `account_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属后台账号',
  `token_jti` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本会话唯一JWT jti',
  `mode` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '会话模式；枚举：NORMAL/PASSWORD_CHANGE_ONLY',
  `credential_version` BIGINT NOT NULL DEFAULT 1 COMMENT '登录时凭据版本',
  `session_epoch` BIGINT NOT NULL DEFAULT 1 COMMENT '登录时账号会话周期',
  `expires_at` DATETIME(6) NOT NULL COMMENT '正常15分钟/受限5分钟的到期时刻',
  `revoked_at` DATETIME(6) NULL COMMENT '单会话退出撤销时刻',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_admin_session_1` (`token_jti`),
  KEY `ix_admin_session_1` (`account_id`, `expires_at`),
  CONSTRAINT `ck_admin_session_1` CHECK (mode IN ('NORMAL','PASSWORD_CHANGE_ONLY')),
  CONSTRAINT `ck_admin_session_2` CHECK (expires_at > created_at),
  CONSTRAINT `ck_admin_session_3` CHECK (credential_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_admin_session_4` CHECK (session_epoch BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='可即时撤销的后台会话；不存JWT原文';

CREATE TABLE `management_scope` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `company_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前管理公司',
  `department_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前指定部门',
  `scope_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API scopeVersion / Scope.version；名单/范围实质变更递增',
  `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA @Version 专用存储修订；不直接作为 API 业务版本',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  KEY `ix_management_scope_1` (`department_id`, `company_id`),
  CONSTRAINT `ck_management_scope_1` CHECK (scope_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_management_scope_2` CHECK (row_version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='稳定范围记录；一个公司一个部门，可无人及空系统集合';

CREATE TABLE `scope_system` (
  `scope_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '范围',
  `system_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '获准系统',
  PRIMARY KEY (`scope_id`, `system_id`),
  KEY `ix_scope_system_1` (`system_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='当前范围系统集合；不授予业务进入权限';

CREATE TABLE `admin_appointment` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `scope_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定范围',
  `account_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '后台账号，员工由账号取得',
  `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '任命状态；枚举：ACTIVE/REVOKED/INVALIDATED',
  `appointment_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API appointmentVersion / Appointment.version',
  `ended_at` DATETIME(6) NULL COMMENT '撤销/资格失效时刻',
  `ended_reason` VARCHAR(500) NULL COMMENT '结束原因',
  `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA @Version 专用存储修订；不直接作为 API 业务版本',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  `active_slot` TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED COMMENT 'ACTIVE 时为1，结束历史为NULL；利用唯一键允许多份历史',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_admin_appointment_1` (`scope_id`, `account_id`, `active_slot`),
  KEY `ix_admin_appointment_1` (`account_id`, `status`, `id`),
  CONSTRAINT `ck_admin_appointment_1` CHECK (status IN ('ACTIVE','REVOKED','INVALIDATED')),
  CONSTRAINT `ck_admin_appointment_2` CHECK ((status='ACTIVE' AND ended_at IS NULL AND ended_reason IS NULL) OR (status<>'ACTIVE' AND ended_at IS NOT NULL AND ended_reason IS NOT NULL)),
  CONSTRAINT `ck_admin_appointment_3` CHECK (appointment_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_admin_appointment_4` CHECK (row_version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='单账号单范围独立任命；结束后不恢复原任命';

CREATE TABLE `business_system` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `code` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '全局唯一稳定编码；新建API限制93字符',
  `name` VARCHAR(100) NOT NULL COMMENT '名称',
  `description` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '说明',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '系统资源启用',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API 对象业务版本；仅实质变更递增',
  `catalog_version` BIGINT NOT NULL DEFAULT 1 COMMENT '仅目录实质变化递增',
  `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA @Version 专用存储修订；不直接作为 API 业务版本',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_business_system_1` (`code`),
  CONSTRAINT `ck_business_system_1` CHECK (enabled IN (0,1)),
  CONSTRAINT `ck_business_system_2` CHECK (version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_business_system_3` CHECK (catalog_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_business_system_4` CHECK (row_version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='业务系统及目录聚合版本；新建同事务建立进入权限';

CREATE TABLE `directory_node` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `system_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属业务系统',
  `code` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '系统内稳定目录编码',
  `name` VARCHAR(100) NOT NULL COMMENT '名称',
  `node_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建后不改类型；枚举：NAVIGATION/FUNCTION',
  `parent_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '同系统父节点；根为NULL',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '同父级按此值、code、id排序，允许负数/并列',
  `enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '节点自身状态',
  `visibility_permission_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '本功能menuEligible操作；导航必须为空',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API 对象业务版本；仅实质变更递增',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_directory_node_1` (`system_id`, `code`),
  UNIQUE KEY `uq_directory_node_2` (`id`, `system_id`),
  KEY `ix_directory_node_1` (`system_id`, `parent_id`, `sort_order`, `code`, `id`),
  KEY `ix_directory_node_2` (`parent_id`, `system_id`),
  KEY `ix_directory_node_3` (`visibility_permission_id`, `id`, `system_id`),
  CONSTRAINT `ck_directory_node_1` CHECK (node_type IN ('NAVIGATION','FUNCTION')),
  CONSTRAINT `ck_directory_node_2` CHECK (parent_id IS NULL OR parent_id <> id),
  CONSTRAINT `ck_directory_node_3` CHECK ((node_type='NAVIGATION' AND visibility_permission_id IS NULL) OR (node_type='FUNCTION' AND (enabled=0 OR visibility_permission_id IS NOT NULL))),
  CONSTRAINT `ck_directory_node_4` CHECK (enabled IN (0,1)),
  CONSTRAINT `ck_directory_node_5` CHECK (version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='同系统导航/功能树；操作不作为树节点';

CREATE TABLE `permission` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `system_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属系统',
  `code` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '系统内唯一稳定权限编码',
  `name` VARCHAR(100) NOT NULL COMMENT '同功能内名称唯一',
  `permission_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '权限类型；枚举：SYSTEM_ACCESS/FUNCTION_OPERATION',
  `function_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'FUNCTION_OPERATION关联功能节点；进入权限为空',
  `menu_eligible` TINYINT NOT NULL DEFAULT 0 COMMENT '显式查看/进入页面语义；不解析code',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '操作自身状态',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API 对象业务版本；仅实质变更递增',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  `access_slot` TINYINT GENERATED ALWAYS AS (CASE WHEN permission_type='SYSTEM_ACCESS' THEN 1 ELSE NULL END) STORED COMMENT '系统进入权限为1，其余NULL；系统内唯一',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_permission_1` (`system_id`, `code`),
  UNIQUE KEY `uq_permission_2` (`system_id`, `access_slot`),
  UNIQUE KEY `uq_permission_3` (`function_id`, `name`),
  UNIQUE KEY `uq_permission_4` (`id`, `system_id`),
  UNIQUE KEY `uq_permission_5` (`id`, `function_id`, `system_id`),
  KEY `ix_permission_1` (`function_id`, `system_id`),
  CONSTRAINT `ck_permission_1` CHECK ((permission_type='SYSTEM_ACCESS' AND function_id IS NULL AND menu_eligible=0) OR (permission_type='FUNCTION_OPERATION' AND function_id IS NOT NULL)),
  CONSTRAINT `ck_permission_2` CHECK (menu_eligible IN (0,1)),
  CONSTRAINT `ck_permission_3` CHECK (enabled IN (0,1)),
  CONSTRAINT `ck_permission_4` CHECK (version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='独立功能操作或唯一系统进入权限；不含数据权限';

CREATE TABLE `business_role` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `code` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '后端生成 ROLE_ 加UUID无连字符；全局唯一且不可更改',
  `name` VARCHAR(100) NOT NULL COMMENT '可重名的显示名称',
  `description` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '说明',
  `company_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '固定公司',
  `department_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '部门角色固定部门；公司级共享角色为NULL',
  `system_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '固定业务系统',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'UNCONFIGURED' COMMENT '首次配置执行成功后EFFECTIVE；枚举：UNCONFIGURED/EFFECTIVE',
  `metadata_version` BIGINT NOT NULL DEFAULT 1 COMMENT '仅名称/说明实质变化递增',
  `configuration_version` BIGINT NOT NULL DEFAULT 0 COMMENT '有效权限集合版本；未配置0',
  `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA @Version 专用存储修订；不直接作为 API 业务版本',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_business_role_1` (`code`),
  UNIQUE KEY `uq_business_role_2` (`id`, `company_id`, `system_id`),
  UNIQUE KEY `uq_business_role_3` (`id`, `system_id`),
  KEY `ix_business_role_1` (`company_id`, `department_id`, `system_id`, `created_at`, `id`),
  KEY `ix_business_role_2` (`department_id`, `company_id`),
  KEY `ix_business_role_3` (`system_id`),
  CONSTRAINT `ck_business_role_1` CHECK ((state='UNCONFIGURED' AND configuration_version=0) OR (state='EFFECTIVE' AND configuration_version>0)),
  CONSTRAINT `ck_business_role_2` CHECK (metadata_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_business_role_3` CHECK (configuration_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_business_role_4` CHECK (row_version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='业务角色元数据和有效配置基线；无角色启停';

CREATE TABLE `role_permission` (
  `role_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务角色',
  `permission_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '权限项',
  `system_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '约束角色与权限同系统',
  PRIMARY KEY (`role_id`, `permission_id`),
  KEY `ix_role_permission_1` (`role_id`, `system_id`),
  KEY `ix_role_permission_2` (`permission_id`, `system_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='当前有效角色配置集合；关系只表示授予';

CREATE TABLE `role_configuration_history` (
  `role_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '角色',
  `configuration_version` BIGINT NOT NULL DEFAULT 1 COMMENT '成功配置版本，无重复',
  `request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '唯一成功来源申请',
  `configuration_snapshot` JSON NOT NULL COMMENT '权限稳定ID/类型/code/当时名称及完整集合，schemaVersion=1；JSON顶层OBJECT',
  `applied_at` DATETIME(6) NOT NULL COMMENT '成功配置提交时刻',
  PRIMARY KEY (`role_id`, `configuration_version`),
  UNIQUE KEY `uq_role_configuration_history_1` (`request_id`),
  CONSTRAINT `ck_role_configuration_history_1` CHECK (configuration_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_role_configuration_history_2` CHECK (JSON_TYPE(configuration_snapshot) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='每次成功配置的完整历史快照，不是当前授权事实';

CREATE TABLE `authorization_source` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `employee_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '员工',
  `company_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源公司',
  `department_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源部门',
  `system_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '系统',
  `source_epoch` BIGINT NOT NULL DEFAULT 0 COMMENT '关闭/资格丧失推进的周期；未落库逻辑基线为0',
  `source_version` BIGINT NOT NULL DEFAULT 0 COMMENT 'API SourceBaseline.version；该来源变更递增',
  `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA @Version 专用存储修订；不直接作为 API 业务版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_authorization_source_1` (`employee_id`, `company_id`, `department_id`, `system_id`),
  UNIQUE KEY `uq_authorization_source_2` (`id`, `company_id`, `system_id`),
  KEY `ix_authorization_source_1` (`department_id`, `company_id`, `system_id`),
  KEY `ix_authorization_source_2` (`system_id`),
  CONSTRAINT `ck_authorization_source_1` CHECK (source_epoch BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_authorization_source_2` CHECK (source_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_authorization_source_3` CHECK (row_version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='员工+公司+来源部门+系统的持久周期；无关系也可存在';

CREATE TABLE `grant_baseline` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `source_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '独立来源',
  `role_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务角色',
  `company_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用于复合归属约束',
  `system_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用于复合同系统约束',
  `relation_version` BIGINT NOT NULL DEFAULT 0 COMMENT 'API当前关系基线；每次授予/结束递增，未落库为0',
  `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA @Version 专用存储修订；不直接作为 API 业务版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_grant_baseline_1` (`source_id`, `role_id`),
  KEY `ix_grant_baseline_1` (`source_id`, `company_id`, `system_id`),
  KEY `ix_grant_baseline_2` (`role_id`, `company_id`, `system_id`),
  CONSTRAINT `ck_grant_baseline_1` CHECK (relation_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_grant_baseline_2` CHECK (row_version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='同一来源同角色的永久关系版本锚点；不随授权结束删除';

CREATE TABLE `role_grant` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `baseline_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '永久关系锚点',
  `relation_version` BIGINT NOT NULL DEFAULT 1 COMMENT '该grant当前演进版本；结束时推进到基线新值',
  `source_epoch` BIGINT NOT NULL DEFAULT 0 COMMENT '授予时固定来源周期',
  `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '授权关系状态；枚举：ACTIVE/REVOKED/INVALIDATED',
  `granted_by_request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '成功授予申请',
  `ended_by_request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '一般撤销申请；直接关权时为空',
  `ended_at` DATETIME(6) NULL COMMENT '结束提交时刻',
  `ended_reason` VARCHAR(500) NULL COMMENT '撤销/失效原因',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  `active_slot` TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED COMMENT 'ACTIVE 时为1，结束历史为NULL；利用唯一键允许多份历史',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_role_grant_1` (`baseline_id`, `active_slot`),
  UNIQUE KEY `uq_role_grant_2` (`baseline_id`, `relation_version`),
  KEY `ix_role_grant_1` (`granted_by_request_id`),
  KEY `ix_role_grant_2` (`ended_by_request_id`),
  CONSTRAINT `ck_role_grant_1` CHECK (status IN ('ACTIVE','REVOKED','INVALIDATED')),
  CONSTRAINT `ck_role_grant_2` CHECK ((status='ACTIVE' AND ended_at IS NULL AND ended_reason IS NULL AND ended_by_request_id IS NULL) OR (status<>'ACTIVE' AND ended_at IS NOT NULL AND ended_reason IS NOT NULL)),
  CONSTRAINT `ck_role_grant_3` CHECK (relation_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_role_grant_4` CHECK (source_epoch BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='一次具体来源授予；结束后重授必须新ID';

CREATE TABLE `grant_employment_evidence` (
  `grant_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '授权关系',
  `employment_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当时适用任职',
  PRIMARY KEY (`grant_id`, `employment_id`),
  KEY `ix_grant_employment_evidence_1` (`employment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='实际授予时适用任职依据；后续不因岗位改名改写';

CREATE TABLE `change_request` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `kind` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更类型；枚举：ROLE_CONFIGURATION/ROLE_GRANT/ROLE_REVOCATION',
  `company_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '一申请一个公司',
  `content` JSON NOT NULL COMMENT '草稿可编辑；提交后实际明细冻结，与规范字节一致；JSON顶层OBJECT',
  `reason` VARCHAR(500) NOT NULL COMMENT '提交原因；提交后不可改',
  `draft_version` BIGINT NOT NULL DEFAULT 1 COMMENT '协作编辑版本；提交后冻结',
  `status_version` BIGINT NOT NULL DEFAULT 1 COMMENT '投递/审批/执行状态修订',
  `submitted_version` BIGINT NULL COMMENT '固定提交版本；取提交时draftVersion，提交后不变',
  `submitted_at` DATETIME(6) NULL COMMENT '固定提交时刻，进入摘要',
  `submitted_by_account_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '原提交人；离任不改写',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT' COMMENT '受三轴约束的聚合展示状态；枚举：DRAFT/PENDING/REJECTED/APPROVED/EXECUTION_FAILED/EXECUTED/INVALIDATED',
  `delivery_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_SUBMITTED' COMMENT 'OA投递状态；枚举：NOT_SUBMITTED/PENDING_SEND/SEND_FAILED/LINKED',
  `approval_result` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NONE' COMMENT '可信最终结果；枚举：NONE/APPROVED/REJECTED',
  `execution_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_STARTED' COMMENT '本地执行状态；枚举：NOT_STARTED/PENDING/FAILED/SUCCEEDED/INVALIDATED',
  `authority_type` VARCHAR(10) NULL COMMENT 'NULL草稿；GLOBAL或SCOPE',
  `scope_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '原范围ID，仅SCOPE；范围保留',
  `appointment_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '原任命ID；历史保留，执行不要求仍ACTIVE',
  `original_authority` JSON NULL COMMENT '固定原资格完整快照，API Authority形状；JSON顶层OBJECT',
  `fixed_payload_bytes` MEDIUMBLOB NULL COMMENT 'RFC8785规范UTF-8摘要包原字节；不从MySQL JSON序列化重建',
  `content_digest` BINARY(32) NULL COMMENT '规范字节SHA-256；API输出64位小写hex',
  `failure_code` VARCHAR(64) NULL COMMENT '失效或可重试失败安全码',
  `next_retry_at` DATETIME(6) NULL COMMENT '执行恢复计划；非取消期限',
  `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA @Version 专用存储修订；不直接作为 API 业务版本',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_change_request_1` (`id`, `kind`),
  KEY `ix_change_request_1` (`scope_id`, `state`, `id`),
  KEY `ix_change_request_2` (`company_id`, `created_at`, `id`),
  KEY `ix_change_request_3` (`state`, `next_retry_at`, `id`),
  KEY `ix_change_request_4` (`submitted_by_account_id`),
  KEY `ix_change_request_5` (`appointment_id`),
  CONSTRAINT `ck_change_request_1` CHECK (kind IN ('ROLE_CONFIGURATION','ROLE_GRANT','ROLE_REVOCATION')),
  CONSTRAINT `ck_change_request_2` CHECK (delivery_status IN ('NOT_SUBMITTED','PENDING_SEND','SEND_FAILED','LINKED')),
  CONSTRAINT `ck_change_request_3` CHECK (approval_result IN ('NONE','APPROVED','REJECTED')),
  CONSTRAINT `ck_change_request_4` CHECK (execution_status IN ('NOT_STARTED','PENDING','FAILED','SUCCEEDED','INVALIDATED')),
  CONSTRAINT `ck_change_request_5` CHECK ((state='DRAFT' AND submitted_version IS NULL AND submitted_at IS NULL AND submitted_by_account_id IS NULL AND original_authority IS NULL AND authority_type IS NULL AND scope_id IS NULL AND appointment_id IS NULL AND fixed_payload_bytes IS NULL AND content_digest IS NULL) OR (state<>'DRAFT' AND submitted_version IS NOT NULL AND submitted_version>0 AND submitted_at IS NOT NULL AND submitted_by_account_id IS NOT NULL AND original_authority IS NOT NULL AND authority_type IS NOT NULL AND fixed_payload_bytes IS NOT NULL AND content_digest IS NOT NULL AND ((authority_type='GLOBAL' AND scope_id IS NULL AND appointment_id IS NULL) OR (authority_type='SCOPE' AND scope_id IS NOT NULL AND appointment_id IS NOT NULL)))),
  CONSTRAINT `ck_change_request_6` CHECK ((state='DRAFT' AND delivery_status='NOT_SUBMITTED' AND approval_result='NONE' AND execution_status='NOT_STARTED' AND failure_code IS NULL AND next_retry_at IS NULL) OR (state='PENDING' AND delivery_status IN ('PENDING_SEND','SEND_FAILED','LINKED') AND approval_result='NONE' AND execution_status='NOT_STARTED' AND failure_code IS NULL AND next_retry_at IS NULL) OR (state='REJECTED' AND delivery_status='LINKED' AND approval_result='REJECTED' AND execution_status='NOT_STARTED' AND failure_code IS NULL AND next_retry_at IS NULL) OR (state='APPROVED' AND delivery_status='LINKED' AND approval_result='APPROVED' AND execution_status='PENDING' AND failure_code IS NULL) OR (state='EXECUTION_FAILED' AND delivery_status='LINKED' AND approval_result='APPROVED' AND execution_status='FAILED' AND failure_code IS NOT NULL AND next_retry_at IS NOT NULL) OR (state='EXECUTED' AND delivery_status='LINKED' AND approval_result='APPROVED' AND execution_status='SUCCEEDED' AND failure_code IS NULL AND next_retry_at IS NULL) OR (state='INVALIDATED' AND delivery_status IN ('PENDING_SEND','SEND_FAILED','LINKED') AND (delivery_status='LINKED' OR approval_result='NONE') AND execution_status='INVALIDATED' AND failure_code IS NOT NULL AND next_retry_at IS NULL)),
  CONSTRAINT `ck_change_request_7` CHECK (JSON_TYPE(content) = 'OBJECT'),
  CONSTRAINT `ck_change_request_8` CHECK (draft_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_change_request_9` CHECK (status_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_change_request_10` CHECK (submitted_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_change_request_11` CHECK (JSON_TYPE(original_authority) = 'OBJECT'),
  CONSTRAINT `ck_change_request_12` CHECK (row_version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='草稿与固定申请共用生命周期；七响应模型不建七表';

CREATE TABLE `change_target` (
  `request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属申请',
  `line_no` INT NOT NULL COMMENT '固定清单序号，从0起',
  `line_kind` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '匹配父申请类型；枚举：ROLE_CONFIGURATION/ROLE_GRANT/ROLE_REVOCATION',
  `company_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '固定公司',
  `system_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '固定系统',
  `department_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '固定来源/角色部门；共享配置可为空',
  `employee_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '分配/撤销目标员工；配置为空',
  `role_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '明确角色',
  `source_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '分配/撤销来源；配置为空',
  `baseline_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '分配/撤销永久关系锚点；配置为空',
  `grant_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '撤销的准确历史ID；分配/配置为空',
  `expected_configuration_version` BIGINT NULL COMMENT '配置/授予检查；一般撤销为空',
  `expected_source_epoch` BIGINT NULL COMMENT '授予固定周期；其他类型为空',
  `expected_relation_version` BIGINT NULL COMMENT '授予/撤销固定关系版本',
  `target_snapshot` JSON NOT NULL COMMENT 'schemaVersion=1；当时名称、适用employmentIds、完整权限差异/依据；JSON顶层OBJECT',
  PRIMARY KEY (`request_id`, `line_no`),
  UNIQUE KEY `uq_change_target_1` (`request_id`, `baseline_id`),
  UNIQUE KEY `uq_change_target_2` (`request_id`, `grant_id`),
  KEY `ix_change_target_1` (`request_id`, `line_kind`),
  KEY `ix_change_target_2` (`source_id`),
  KEY `ix_change_target_3` (`baseline_id`),
  KEY `ix_change_target_4` (`grant_id`),
  KEY `ix_change_target_5` (`role_id`, `company_id`, `system_id`),
  KEY `ix_change_target_6` (`department_id`, `company_id`),
  KEY `ix_change_target_7` (`employee_id`),
  KEY `ix_change_target_8` (`system_id`),
  CONSTRAINT `ck_change_target_1` CHECK (line_no BETWEEN 0 AND 99),
  CONSTRAINT `ck_change_target_2` CHECK ((line_kind='ROLE_CONFIGURATION' AND line_no=0 AND employee_id IS NULL AND source_id IS NULL AND baseline_id IS NULL AND grant_id IS NULL AND expected_configuration_version IS NOT NULL AND expected_source_epoch IS NULL AND expected_relation_version IS NULL) OR (line_kind='ROLE_GRANT' AND employee_id IS NOT NULL AND department_id IS NOT NULL AND source_id IS NOT NULL AND baseline_id IS NOT NULL AND grant_id IS NULL AND expected_configuration_version IS NOT NULL AND expected_source_epoch IS NOT NULL AND expected_relation_version IS NOT NULL) OR (line_kind='ROLE_REVOCATION' AND employee_id IS NOT NULL AND department_id IS NOT NULL AND source_id IS NOT NULL AND baseline_id IS NOT NULL AND grant_id IS NOT NULL AND expected_configuration_version IS NULL AND expected_source_epoch IS NULL AND expected_relation_version IS NOT NULL)),
  CONSTRAINT `ck_change_target_3` CHECK (expected_configuration_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_change_target_4` CHECK (expected_source_epoch BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_change_target_5` CHECK (expected_relation_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_change_target_6` CHECK (JSON_TYPE(target_snapshot) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='草稿目标/提交后实际清单的可检索投影；必须与对应content一致';

CREATE TABLE `role_change_claim` (
  `role_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '角色唯一配置通道',
  `request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '占用申请',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uq_role_change_claim_1` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='当前角色配置通道；终态删除占用，历史不靠本表';

CREATE TABLE `relation_change_claim` (
  `baseline_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '关系唯一通道',
  `request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '占用申请',
  PRIMARY KEY (`baseline_id`),
  KEY `ix_relation_change_claim_1` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='当前关系变更通道；同来源去重，不阻塞其他来源';

CREATE TABLE `service_identity` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `subject` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'JWT sub，全局唯一',
  `kind` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '入口类型；枚举：BUSINESS/OA',
  `system_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'BUSINESS唯一绑定系统，OA为空',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '当前服务身份状态',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API 对象业务版本；仅实质变更递增',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_service_identity_1` (`subject`),
  KEY `ix_service_identity_1` (`system_id`),
  CONSTRAINT `ck_service_identity_1` CHECK ((kind='BUSINESS' AND system_id IS NOT NULL) OR (kind='OA' AND system_id IS NULL)),
  CONSTRAINT `ck_service_identity_2` CHECK (enabled IN (0,1)),
  CONSTRAINT `ck_service_identity_3` CHECK (version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='部署管理的服务身份和绑定；不保存私钥/令牌';

CREATE TABLE `oa_link` (
  `request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申请，一对一',
  `oa_instance_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '部署OA服务身份',
  `oa_approval_id` VARCHAR(64) NOT NULL COMMENT '外部审批编号',
  `accepted_at` DATETIME(6) NOT NULL COMMENT 'OA受理时刻',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`request_id`),
  UNIQUE KEY `uq_oa_link_1` (`oa_instance_id`, `oa_approval_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='可靠外部审批关联；先由投递响应落地，回调不得抢绑';

CREATE TABLE `oa_outbox` (
  `request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申请，一申请一待办',
  `oa_instance_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标OA实例',
  `task_state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'READY' COMMENT '仅内部调度状态；枚举：READY/LEASED/DONE/STOPPED',
  `next_attempt_at` DATETIME(6) NOT NULL COMMENT '下次投递时刻',
  `lease_token` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '每次领取随机令牌',
  `lease_until` DATETIME(6) NULL COMMENT '领取过期时间',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '已开始尝试数',
  `last_failure_code` VARCHAR(64) NULL COMMENT '安全故障码',
  `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA @Version 专用存储修订；不直接作为 API 业务版本',
  PRIMARY KEY (`request_id`),
  KEY `ix_oa_outbox_1` (`task_state`, `next_attempt_at`, `request_id`),
  KEY `ix_oa_outbox_2` (`oa_instance_id`),
  CONSTRAINT `ck_oa_outbox_1` CHECK (task_state IN ('READY','LEASED','DONE','STOPPED')),
  CONSTRAINT `ck_oa_outbox_2` CHECK (attempt_count>=0),
  CONSTRAINT `ck_oa_outbox_3` CHECK ((task_state='LEASED' AND lease_token IS NOT NULL AND lease_until IS NOT NULL) OR (task_state<>'LEASED' AND lease_token IS NULL AND lease_until IS NULL)),
  CONSTRAINT `ck_oa_outbox_4` CHECK (row_version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='同一固定申请可靠投递待办及租约；不复制可变申请内容';

CREATE TABLE `oa_callback_event` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `oa_instance_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '可信OA实例',
  `event_id` VARCHAR(64) NOT NULL COMMENT 'OA最终结果事件编号',
  `request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '匹配的固定申请',
  `payload_digest` BINARY(32) NOT NULL COMMENT '完整规范化回调内容摘要',
  `safe_payload` JSON NOT NULL COMMENT '不含认证凭据的最终结果原字段；JSON顶层OBJECT',
  `received_at` DATETIME(6) NOT NULL COMMENT '首次可靠接收时刻',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_oa_callback_event_1` (`oa_instance_id`, `event_id`),
  KEY `ix_oa_callback_event_1` (`request_id`),
  CONSTRAINT `ck_oa_callback_event_1` CHECK (JSON_TYPE(safe_payload) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='OA实例+事件ID防重；同事件不同内容不得覆盖';

CREATE TABLE `oa_final_result` (
  `request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申请',
  `first_event_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '首次可信回调记录ID',
  `decision` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最终结果；枚举：APPROVED/REJECTED',
  `decided_at` DATETIME(6) NOT NULL COMMENT 'OA决定时刻；不是覆盖优先级',
  `comment` VARCHAR(500) NOT NULL COMMENT '意见摘要',
  `received_at` DATETIME(6) NOT NULL COMMENT '平台接收时刻',
  PRIMARY KEY (`request_id`),
  UNIQUE KEY `uq_oa_final_result_1` (`first_event_id`),
  CONSTRAINT `ck_oa_final_result_1` CHECK (decision IN ('APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='一申请一不可反转最终结果；新eventId不绕过';

CREATE TABLE `execution_success` (
  `request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '成功申请唯一键',
  `audit_event_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '唯一成功业务事件',
  `executed_at` DATETIME(6) NOT NULL COMMENT '成功提交时刻',
  `result_snapshot` JSON NOT NULL COMMENT '实际授予ID/撤销ID/配置版本与计数，不含凭据；JSON顶层OBJECT',
  PRIMARY KEY (`request_id`),
  UNIQUE KEY `uq_execution_success_1` (`audit_event_id`),
  CONSTRAINT `ck_execution_success_1` CHECK (JSON_TYPE(result_snapshot) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='一申请一次成功凭据；业务写入/成功审计同事务';

CREATE TABLE `execution_attempt` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原固定申请',
  `attempt_no` INT NOT NULL COMMENT '该申请递增尝试序号',
  `outcome` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '尝试结果；枚举：FAILED/INVALIDATED/SUCCEEDED',
  `failure_code` VARCHAR(64) NULL COMMENT '安全失败/失效码',
  `started_at` DATETIME(6) NOT NULL COMMENT '开始时间',
  `finished_at` DATETIME(6) NOT NULL COMMENT '记录结果时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_execution_attempt_1` (`request_id`, `attempt_no`),
  CONSTRAINT `ck_execution_attempt_1` CHECK (attempt_no>0),
  CONSTRAINT `ck_execution_attempt_2` CHECK (outcome IN ('FAILED','INVALIDATED','SUCCEEDED')),
  CONSTRAINT `ck_execution_attempt_3` CHECK (finished_at>=started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='本地执行尝试历史；业务失败回滚后独立记录';

CREATE TABLE `http_idempotency` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `principal_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '当前后台账号主体',
  `http_method` VARCHAR(10) NOT NULL COMMENT '大写HTTP方法',
  `canonical_path` VARCHAR(300) NOT NULL COMMENT '包含稳定资源ID的规范路径，不包含易变查询排序',
  `idempotency_key` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端UUID键',
  `request_digest` BINARY(32) NOT NULL COMMENT '包含版本/目标/规范内容的受保护摘要',
  `digest_key_id` VARCHAR(64) NOT NULL COMMENT '摘要HMAC密钥版本；密钥不入库',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '处理中或已接受结果；枚举：IN_PROGRESS/COMPLETED',
  `http_status` SMALLINT NULL COMMENT '原接受HTTP状态码',
  `resource_location` VARCHAR(300) NULL COMMENT '原创建/接受资源地址，无则NULL',
  `safe_response` JSON NULL COMMENT '原安全响应；不含密码/JWT/哈希；JSON顶层OBJECT',
  `authorization_basis` JSON NULL COMMENT '内部重放授权事实：操作策略及完整目标范围；不能替代当前资格；完成时固定；JSON顶层OBJECT',
  `lease_token` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '当前处理者随机栅栏令牌；完成后清空',
  `lease_until` DATETIME(6) NULL COMMENT '处理中租约；过期仅允许同内容恢复接管',
  `expires_at` DATETIME(6) NOT NULL COMMENT '至少首次接受后7天；不删除长期业务防重',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_http_idempotency_1` (`principal_id`, `http_method`, `canonical_path`, `idempotency_key`),
  KEY `ix_http_idempotency_1` (`expires_at`),
  CONSTRAINT `ck_http_idempotency_1` CHECK ((state='IN_PROGRESS' AND http_status IS NULL AND safe_response IS NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL) OR (state='COMPLETED' AND http_status BETWEEN 200 AND 599 AND safe_response IS NOT NULL AND authorization_basis IS NOT NULL AND JSON_TYPE(authorization_basis)='OBJECT' AND lease_token IS NULL AND lease_until IS NULL)),
  CONSTRAINT `ck_http_idempotency_2` CHECK (expires_at>created_at),
  CONSTRAINT `ck_http_idempotency_3` CHECK (JSON_TYPE(safe_response) = 'OBJECT'),
  CONSTRAINT `ck_http_idempotency_4` CHECK (JSON_TYPE(authorization_basis) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='HTTP接受结果窗口；独立于OA与业务成功防重';

CREATE TABLE `import_file` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `file_name` VARCHAR(200) NOT NULL COMMENT '原文件名，仅显示不参与路径拼接',
  `sha256` BINARY(32) NOT NULL COMMENT '原文件字节摘要',
  `size_bytes` BIGINT NOT NULL COMMENT '原文件大小',
  `storage_ref` VARCHAR(300) NOT NULL COMMENT '服务端受控文件引用，非客户端任意URL',
  `sheet_names` JSON NOT NULL COMMENT '可用工作表名称，CSV固定CSV；JSON顶层ARRAY',
  `uploaded_by` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上传总管理员',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  KEY `ix_import_file_1` (`uploaded_by`),
  CONSTRAINT `ck_import_file_1` CHECK (size_bytes BETWEEN 1 AND 10485760),
  CONSTRAINT `ck_import_file_2` CHECK (JSON_TYPE(sheet_names) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='导入文件元数据；文件本体受控存储且不可原地覆盖';

CREATE TABLE `import_batch` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `file_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变原文件',
  `source_system` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '来源系统',
  `source_batch_id` VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '来源批次业务编号',
  `import_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '导入类别；枚举：ORGANIZATION/EMPLOYEE_INITIALIZATION/PERSONNEL_CHANGES',
  `input_snapshot` JSON NOT NULL COMMENT '完整ImportInput：worksheet、字段/组织映射、初始化状态、原因；JSON顶层OBJECT',
  `input_digest` BINARY(32) NOT NULL COMMENT '文件SHA+完整规范输入摘要，定位同批同内容',
  `content_digest` BINARY(32) NOT NULL COMMENT '规范行、前后值、候选代码和依赖基线摘要',
  `state` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'API预检/执行状态；枚举：BLOCKED/READY/SUCCEEDED/FAILED/STALE',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT 'API 对象业务版本；仅实质变更递增',
  `planned_counts` JSON NOT NULL COMMENT '冻结计划ImportCounts八字段；JSON顶层OBJECT',
  `actual_counts` JSON NULL COMMENT '仅成功时实际八字段计数；JSON顶层OBJECT',
  `blocking_issue_count` INT NOT NULL DEFAULT 0 COMMENT '阻断问题数',
  `warning_count` INT NOT NULL DEFAULT 0 COMMENT '警告数',
  `failure_code` VARCHAR(64) NULL COMMENT '安全故障码',
  `sealed_at` DATETIME(6) NULL COMMENT '预检完成冻结时间；API只暴露已冻结批次',
  `committed_at` DATETIME(6) NULL COMMENT '成功提交时刻',
  `created_by` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发起总管理员',
  `row_version` BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA @Version 专用存储修订；不直接作为 API 业务版本',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_import_batch_1` (`source_system`, `source_batch_id`),
  KEY `ix_import_batch_1` (`created_at`, `id`),
  KEY `ix_import_batch_2` (`file_id`),
  KEY `ix_import_batch_3` (`created_by`),
  CONSTRAINT `ck_import_batch_1` CHECK (import_type IN ('ORGANIZATION','EMPLOYEE_INITIALIZATION','PERSONNEL_CHANGES')),
  CONSTRAINT `ck_import_batch_2` CHECK (state IN ('BLOCKED','READY','SUCCEEDED','FAILED','STALE')),
  CONSTRAINT `ck_import_batch_3` CHECK ((state='SUCCEEDED' AND actual_counts IS NOT NULL AND committed_at IS NOT NULL) OR (state<>'SUCCEEDED' AND actual_counts IS NULL AND committed_at IS NULL)),
  CONSTRAINT `ck_import_batch_4` CHECK (blocking_issue_count>=0 AND warning_count>=0),
  CONSTRAINT `ck_import_batch_5` CHECK (JSON_TYPE(input_snapshot) = 'OBJECT'),
  CONSTRAINT `ck_import_batch_6` CHECK (version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_import_batch_7` CHECK (JSON_TYPE(planned_counts) = 'OBJECT'),
  CONSTRAINT `ck_import_batch_8` CHECK (JSON_TYPE(actual_counts) = 'OBJECT'),
  CONSTRAINT `ck_import_batch_9` CHECK (row_version BETWEEN 0 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='冻结的预检与原子导入结果；成功sourceBatchId长期防重';

CREATE TABLE `import_plan_row` (
  `batch_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属预检',
  `row_number` INT NOT NULL COMMENT '原文件行号',
  `plan_no` INT NOT NULL COMMENT '行内规范动作序号，同一原行可产生多动作',
  `action` VARCHAR(32) NOT NULL COMMENT 'ImportPlanChange.action明确动作或UNCHANGED',
  `target_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '既有或预分配候选ID；无FK以允许尚未创建对象',
  `stable_code` VARCHAR(100) NULL COMMENT '候选/既有稳定编码',
  `company_code` VARCHAR(100) NULL COMMENT '完整公司归属编码',
  `department_code` VARCHAR(100) NULL COMMENT '完整部门归属编码',
  `position_code` VARCHAR(100) NULL COMMENT '完整岗位归属编码',
  `expected_version` BIGINT NULL COMMENT '既有目标预检版本；新对象为空',
  `summary` VARCHAR(500) NOT NULL COMMENT '安全摘要',
  `normalized_values` JSON NOT NULL COMMENT '类型校验后的完整规范输入，不执行公式；JSON顶层OBJECT',
  `field_changes` JSON NOT NULL COMMENT 'ImportValueChange[]，明确字段及before/after；JSON顶层ARRAY',
  `dependency_snapshot` JSON NOT NULL COMMENT '目标版本、唯一键不存在断言、引用集合摘要和来源周期；JSON顶层OBJECT',
  PRIMARY KEY (`batch_id`, `row_number`, `plan_no`),
  CONSTRAINT `ck_import_plan_row_1` CHECK (row_number>0 AND plan_no>=0),
  CONSTRAINT `ck_import_plan_row_2` CHECK (expected_version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT `ck_import_plan_row_3` CHECK (JSON_TYPE(normalized_values) = 'OBJECT'),
  CONSTRAINT `ck_import_plan_row_4` CHECK (JSON_TYPE(field_changes) = 'ARRAY'),
  CONSTRAINT `ck_import_plan_row_5` CHECK (JSON_TYPE(dependency_snapshot) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='冻结规范行及逐字段前后值；候选组织尚未建正式记录';

CREATE TABLE `import_issue` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `batch_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '预检批次',
  `row_number` INT NOT NULL COMMENT '原始行号',
  `field` VARCHAR(100) NOT NULL COMMENT '字段定位',
  `severity` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '问题严重性；枚举：WARNING/BLOCKING',
  `code` VARCHAR(64) NOT NULL COMMENT '稳定问题码',
  `message` VARCHAR(500) NOT NULL COMMENT '安全问题说明',
  PRIMARY KEY (`id`),
  KEY `ix_import_issue_1` (`batch_id`, `row_number`, `id`),
  CONSTRAINT `ck_import_issue_1` CHECK (severity IN ('WARNING','BLOCKING')),
  CONSTRAINT `ck_import_issue_2` CHECK (row_number>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='冻结预检报告；仅总管理员可读';

CREATE TABLE `impact_preview` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `creator_account_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建身份',
  `appointment_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建时所选任命；总管理员为空',
  `authority_snapshot` JSON NOT NULL COMMENT '创建时范围/版本及可见边界；JSON顶层OBJECT',
  `operation` VARCHAR(64) NOT NULL COMMENT '绑定操作语义与目标资源',
  `input_digest` BINARY(32) NOT NULL COMMENT '规范输入及目标版本摘要',
  `content_digest` BINARY(32) NOT NULL COMMENT '预览确认摘要',
  `dependency_snapshot` JSON NOT NULL COMMENT '影响实际清单/分类/统计的版本和集合依赖，见TRANSACTIONS；JSON顶层OBJECT',
  `result_snapshot` JSON NOT NULL COMMENT 'Preview/ChangePreview的固定汇总、实际清单与noChangeItems；JSON顶层OBJECT',
  `expires_at` DATETIME(6) NOT NULL COMMENT '创建后五分钟',
  `created_at` DATETIME(6) NOT NULL COMMENT '记录创建时刻，UTC，非源系统业务起始日期',
  PRIMARY KEY (`id`),
  KEY `ix_impact_preview_1` (`expires_at`),
  KEY `ix_impact_preview_2` (`creator_account_id`),
  KEY `ix_impact_preview_3` (`appointment_id`),
  CONSTRAINT `ck_impact_preview_1` CHECK (expires_at>created_at),
  CONSTRAINT `ck_impact_preview_2` CHECK (JSON_TYPE(authority_snapshot) = 'OBJECT'),
  CONSTRAINT `ck_impact_preview_3` CHECK (JSON_TYPE(dependency_snapshot) = 'OBJECT'),
  CONSTRAINT `ck_impact_preview_4` CHECK (JSON_TYPE(result_snapshot) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='五分钟固定预览；不是授权凭证';

CREATE TABLE `preview_item` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `preview_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '预览',
  `item_kind` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标类别；枚举：EMPLOYEE/ROLE/GRANT/SOURCE/APPOINTMENT/PENDING_REQUEST',
  `stable_key` VARCHAR(200) NOT NULL COMMENT '稳定ID或SOURCE完整四元键；员工项按员工公司系统键',
  `safe_item` JSON NOT NULL COMMENT 'API ImpactEmployee/ImpactTarget；仅创建时有权明细；JSON顶层OBJECT',
  `visibility_basis` JSON NOT NULL COMMENT '读取时重新检查的固定来源边界，混合申请须全部覆盖；JSON顶层OBJECT',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_preview_item_1` (`preview_id`, `item_kind`, `stable_key`),
  KEY `ix_preview_item_1` (`preview_id`, `item_kind`, `id`),
  CONSTRAINT `ck_preview_item_1` CHECK (item_kind IN ('EMPLOYEE','ROLE','GRANT','SOURCE','APPOINTMENT','PENDING_REQUEST')),
  CONSTRAINT `ck_preview_item_2` CHECK (JSON_TYPE(safe_item) = 'OBJECT'),
  CONSTRAINT `ck_preview_item_3` CHECK (JSON_TYPE(visibility_basis) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='同一预览的可见员工/目标分页快照；禁止实时重算混页';

CREATE TABLE `audit_event` (
  `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不可变内部 UUID v4；API 原样输出小写带连字符字符串',
  `occurred_at` DATETIME(6) NOT NULL COMMENT '事件发生时刻，UTC',
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型；提交/批准/执行分别记录',
  `actor_kind` VARCHAR(16) NOT NULL COMMENT 'ACCOUNT / SERVICE / DEPLOYMENT',
  `actor_id` VARCHAR(64) NOT NULL COMMENT '当时操作者稳定标识；部署事件可无账号FK',
  `original_submitter_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '原申请人账号，已提交申请事件适用',
  `target_id` VARCHAR(64) NOT NULL COMMENT '稳定目标标识或聚合ID',
  `change_request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '申请；草稿删除事件仅快照保留被删除ID',
  `company_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '单来源公司；全局/混合无法安全分隔为空',
  `source_department_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '单来源部门',
  `system_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '单来源系统',
  `visibility` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全局记录仅总管理员；SCOPED按固定来源；枚举：GLOBAL/SCOPED',
  `result` VARCHAR(64) NOT NULL COMMENT '安全业务结果',
  `correlation_id` VARCHAR(64) NOT NULL COMMENT 'HTTP X-Request-Id，不是防重键',
  `reason` VARCHAR(500) NOT NULL COMMENT '业务原因',
  `event_snapshot` JSON NOT NULL COMMENT 'schemaVersion=1；当时名称、原/实际差异、审批编号/版本、失败码、敏感信息脱敏；JSON顶层OBJECT',
  PRIMARY KEY (`id`),
  KEY `ix_audit_event_1` (`occurred_at`, `id`),
  KEY `ix_audit_event_2` (`company_id`, `source_department_id`, `system_id`, `occurred_at`, `id`),
  KEY `ix_audit_event_3` (`source_department_id`, `company_id`),
  KEY `ix_audit_event_4` (`system_id`),
  KEY `ix_audit_event_5` (`change_request_id`, `occurred_at`, `id`),
  KEY `ix_audit_event_6` (`original_submitter_id`),
  CONSTRAINT `ck_audit_event_1` CHECK (visibility IN ('GLOBAL','SCOPED')),
  CONSTRAINT `ck_audit_event_2` CHECK (actor_kind IN ('ACCOUNT','SERVICE','DEPLOYMENT')),
  CONSTRAINT `ck_audit_event_3` CHECK (visibility='GLOBAL' OR (company_id IS NOT NULL AND source_department_id IS NOT NULL AND system_id IS NOT NULL)),
  CONSTRAINT `ck_audit_event_4` CHECK (JSON_TYPE(event_snapshot) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='追加式授权/管理审计和申请事件；不通过当前资料倒推历史';

CREATE TABLE `audit_subject` (
  `audit_event_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属审计事件',
  `subject_type` VARCHAR(16) NOT NULL COMMENT 'EMPLOYEE / ROLE / GRANT / ACCOUNT / REQUEST等稳定类型',
  `subject_id` VARCHAR(64) NOT NULL COMMENT '当时稳定对象标识，有意不设多态外键',
  PRIMARY KEY (`audit_event_id`, `subject_type`, `subject_id`),
  KEY `ix_audit_subject_1` (`subject_type`, `subject_id`, `audit_event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='审计按稳定员工/角色等目标筛选的历史索引；可指已删除草稿对象';

-- 所有表先建立，再添加外键；处理目录/权限及申请/历史之间的循环引用。
ALTER TABLE `department` ADD CONSTRAINT `fk_department_1` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`);
ALTER TABLE `position` ADD CONSTRAINT `fk_position_1` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`);
ALTER TABLE `employee_source_identity` ADD CONSTRAINT `fk_employee_source_identity_1` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`);
ALTER TABLE `organization_mapping` ADD CONSTRAINT `fk_organization_mapping_1` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`);
ALTER TABLE `organization_mapping` ADD CONSTRAINT `fk_organization_mapping_2` FOREIGN KEY (`department_id`, `company_id`) REFERENCES `department` (`id`, `company_id`);
ALTER TABLE `organization_mapping` ADD CONSTRAINT `fk_organization_mapping_3` FOREIGN KEY (`position_id`, `department_id`) REFERENCES `position` (`id`, `department_id`);
ALTER TABLE `employment` ADD CONSTRAINT `fk_employment_1` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`);
ALTER TABLE `employment` ADD CONSTRAINT `fk_employment_2` FOREIGN KEY (`department_id`, `company_id`) REFERENCES `department` (`id`, `company_id`);
ALTER TABLE `employment` ADD CONSTRAINT `fk_employment_3` FOREIGN KEY (`position_id`, `department_id`) REFERENCES `position` (`id`, `department_id`);
ALTER TABLE `admin_session` ADD CONSTRAINT `fk_admin_session_1` FOREIGN KEY (`account_id`) REFERENCES `admin_account` (`id`);
ALTER TABLE `management_scope` ADD CONSTRAINT `fk_management_scope_1` FOREIGN KEY (`department_id`, `company_id`) REFERENCES `department` (`id`, `company_id`);
ALTER TABLE `scope_system` ADD CONSTRAINT `fk_scope_system_1` FOREIGN KEY (`scope_id`) REFERENCES `management_scope` (`id`);
ALTER TABLE `scope_system` ADD CONSTRAINT `fk_scope_system_2` FOREIGN KEY (`system_id`) REFERENCES `business_system` (`id`);
ALTER TABLE `admin_appointment` ADD CONSTRAINT `fk_admin_appointment_1` FOREIGN KEY (`scope_id`) REFERENCES `management_scope` (`id`);
ALTER TABLE `admin_appointment` ADD CONSTRAINT `fk_admin_appointment_2` FOREIGN KEY (`account_id`) REFERENCES `admin_account` (`id`);
ALTER TABLE `directory_node` ADD CONSTRAINT `fk_directory_node_1` FOREIGN KEY (`system_id`) REFERENCES `business_system` (`id`);
ALTER TABLE `directory_node` ADD CONSTRAINT `fk_directory_node_2` FOREIGN KEY (`parent_id`, `system_id`) REFERENCES `directory_node` (`id`, `system_id`);
ALTER TABLE `directory_node` ADD CONSTRAINT `fk_directory_node_3` FOREIGN KEY (`visibility_permission_id`, `id`, `system_id`) REFERENCES `permission` (`id`, `function_id`, `system_id`);
ALTER TABLE `permission` ADD CONSTRAINT `fk_permission_1` FOREIGN KEY (`system_id`) REFERENCES `business_system` (`id`);
ALTER TABLE `permission` ADD CONSTRAINT `fk_permission_2` FOREIGN KEY (`function_id`, `system_id`) REFERENCES `directory_node` (`id`, `system_id`);
ALTER TABLE `business_role` ADD CONSTRAINT `fk_business_role_1` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`);
ALTER TABLE `business_role` ADD CONSTRAINT `fk_business_role_2` FOREIGN KEY (`department_id`, `company_id`) REFERENCES `department` (`id`, `company_id`);
ALTER TABLE `business_role` ADD CONSTRAINT `fk_business_role_3` FOREIGN KEY (`system_id`) REFERENCES `business_system` (`id`);
ALTER TABLE `role_permission` ADD CONSTRAINT `fk_role_permission_1` FOREIGN KEY (`role_id`, `system_id`) REFERENCES `business_role` (`id`, `system_id`);
ALTER TABLE `role_permission` ADD CONSTRAINT `fk_role_permission_2` FOREIGN KEY (`permission_id`, `system_id`) REFERENCES `permission` (`id`, `system_id`);
ALTER TABLE `role_configuration_history` ADD CONSTRAINT `fk_role_configuration_history_1` FOREIGN KEY (`role_id`) REFERENCES `business_role` (`id`);
ALTER TABLE `role_configuration_history` ADD CONSTRAINT `fk_role_configuration_history_2` FOREIGN KEY (`request_id`) REFERENCES `change_request` (`id`);
ALTER TABLE `authorization_source` ADD CONSTRAINT `fk_authorization_source_1` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`);
ALTER TABLE `authorization_source` ADD CONSTRAINT `fk_authorization_source_2` FOREIGN KEY (`department_id`, `company_id`) REFERENCES `department` (`id`, `company_id`);
ALTER TABLE `authorization_source` ADD CONSTRAINT `fk_authorization_source_3` FOREIGN KEY (`system_id`) REFERENCES `business_system` (`id`);
ALTER TABLE `grant_baseline` ADD CONSTRAINT `fk_grant_baseline_1` FOREIGN KEY (`source_id`, `company_id`, `system_id`) REFERENCES `authorization_source` (`id`, `company_id`, `system_id`);
ALTER TABLE `grant_baseline` ADD CONSTRAINT `fk_grant_baseline_2` FOREIGN KEY (`role_id`, `company_id`, `system_id`) REFERENCES `business_role` (`id`, `company_id`, `system_id`);
ALTER TABLE `role_grant` ADD CONSTRAINT `fk_role_grant_1` FOREIGN KEY (`baseline_id`) REFERENCES `grant_baseline` (`id`);
ALTER TABLE `role_grant` ADD CONSTRAINT `fk_role_grant_2` FOREIGN KEY (`granted_by_request_id`) REFERENCES `change_request` (`id`);
ALTER TABLE `role_grant` ADD CONSTRAINT `fk_role_grant_3` FOREIGN KEY (`ended_by_request_id`) REFERENCES `change_request` (`id`);
ALTER TABLE `grant_employment_evidence` ADD CONSTRAINT `fk_grant_employment_evidence_1` FOREIGN KEY (`grant_id`) REFERENCES `role_grant` (`id`);
ALTER TABLE `grant_employment_evidence` ADD CONSTRAINT `fk_grant_employment_evidence_2` FOREIGN KEY (`employment_id`) REFERENCES `employment` (`id`);
ALTER TABLE `change_request` ADD CONSTRAINT `fk_change_request_1` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`);
ALTER TABLE `change_request` ADD CONSTRAINT `fk_change_request_2` FOREIGN KEY (`scope_id`) REFERENCES `management_scope` (`id`);
ALTER TABLE `change_request` ADD CONSTRAINT `fk_change_request_3` FOREIGN KEY (`appointment_id`) REFERENCES `admin_appointment` (`id`);
ALTER TABLE `change_request` ADD CONSTRAINT `fk_change_request_4` FOREIGN KEY (`submitted_by_account_id`) REFERENCES `admin_account` (`id`);
ALTER TABLE `change_target` ADD CONSTRAINT `fk_change_target_1` FOREIGN KEY (`request_id`, `line_kind`) REFERENCES `change_request` (`id`, `kind`);
ALTER TABLE `change_target` ADD CONSTRAINT `fk_change_target_2` FOREIGN KEY (`role_id`, `company_id`, `system_id`) REFERENCES `business_role` (`id`, `company_id`, `system_id`);
ALTER TABLE `change_target` ADD CONSTRAINT `fk_change_target_3` FOREIGN KEY (`department_id`, `company_id`) REFERENCES `department` (`id`, `company_id`);
ALTER TABLE `change_target` ADD CONSTRAINT `fk_change_target_4` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`);
ALTER TABLE `change_target` ADD CONSTRAINT `fk_change_target_5` FOREIGN KEY (`source_id`) REFERENCES `authorization_source` (`id`);
ALTER TABLE `change_target` ADD CONSTRAINT `fk_change_target_6` FOREIGN KEY (`baseline_id`) REFERENCES `grant_baseline` (`id`);
ALTER TABLE `change_target` ADD CONSTRAINT `fk_change_target_7` FOREIGN KEY (`grant_id`) REFERENCES `role_grant` (`id`);
ALTER TABLE `change_target` ADD CONSTRAINT `fk_change_target_8` FOREIGN KEY (`system_id`) REFERENCES `business_system` (`id`);
ALTER TABLE `role_change_claim` ADD CONSTRAINT `fk_role_change_claim_1` FOREIGN KEY (`role_id`) REFERENCES `business_role` (`id`);
ALTER TABLE `role_change_claim` ADD CONSTRAINT `fk_role_change_claim_2` FOREIGN KEY (`request_id`) REFERENCES `change_request` (`id`);
ALTER TABLE `relation_change_claim` ADD CONSTRAINT `fk_relation_change_claim_1` FOREIGN KEY (`baseline_id`) REFERENCES `grant_baseline` (`id`);
ALTER TABLE `relation_change_claim` ADD CONSTRAINT `fk_relation_change_claim_2` FOREIGN KEY (`request_id`) REFERENCES `change_request` (`id`);
ALTER TABLE `service_identity` ADD CONSTRAINT `fk_service_identity_1` FOREIGN KEY (`system_id`) REFERENCES `business_system` (`id`);
ALTER TABLE `oa_link` ADD CONSTRAINT `fk_oa_link_1` FOREIGN KEY (`request_id`) REFERENCES `change_request` (`id`);
ALTER TABLE `oa_link` ADD CONSTRAINT `fk_oa_link_2` FOREIGN KEY (`oa_instance_id`) REFERENCES `service_identity` (`id`);
ALTER TABLE `oa_outbox` ADD CONSTRAINT `fk_oa_outbox_1` FOREIGN KEY (`request_id`) REFERENCES `change_request` (`id`);
ALTER TABLE `oa_outbox` ADD CONSTRAINT `fk_oa_outbox_2` FOREIGN KEY (`oa_instance_id`) REFERENCES `service_identity` (`id`);
ALTER TABLE `oa_callback_event` ADD CONSTRAINT `fk_oa_callback_event_1` FOREIGN KEY (`oa_instance_id`) REFERENCES `service_identity` (`id`);
ALTER TABLE `oa_callback_event` ADD CONSTRAINT `fk_oa_callback_event_2` FOREIGN KEY (`request_id`) REFERENCES `change_request` (`id`);
ALTER TABLE `oa_final_result` ADD CONSTRAINT `fk_oa_final_result_1` FOREIGN KEY (`request_id`) REFERENCES `change_request` (`id`);
ALTER TABLE `oa_final_result` ADD CONSTRAINT `fk_oa_final_result_2` FOREIGN KEY (`first_event_id`) REFERENCES `oa_callback_event` (`id`);
ALTER TABLE `execution_success` ADD CONSTRAINT `fk_execution_success_1` FOREIGN KEY (`request_id`) REFERENCES `change_request` (`id`);
ALTER TABLE `execution_success` ADD CONSTRAINT `fk_execution_success_2` FOREIGN KEY (`audit_event_id`) REFERENCES `audit_event` (`id`);
ALTER TABLE `execution_attempt` ADD CONSTRAINT `fk_execution_attempt_1` FOREIGN KEY (`request_id`) REFERENCES `change_request` (`id`);
ALTER TABLE `http_idempotency` ADD CONSTRAINT `fk_http_idempotency_1` FOREIGN KEY (`principal_id`) REFERENCES `admin_account` (`id`);
ALTER TABLE `import_file` ADD CONSTRAINT `fk_import_file_1` FOREIGN KEY (`uploaded_by`) REFERENCES `admin_account` (`id`);
ALTER TABLE `import_batch` ADD CONSTRAINT `fk_import_batch_1` FOREIGN KEY (`file_id`) REFERENCES `import_file` (`id`);
ALTER TABLE `import_batch` ADD CONSTRAINT `fk_import_batch_2` FOREIGN KEY (`created_by`) REFERENCES `admin_account` (`id`);
ALTER TABLE `import_plan_row` ADD CONSTRAINT `fk_import_plan_row_1` FOREIGN KEY (`batch_id`) REFERENCES `import_batch` (`id`);
ALTER TABLE `import_issue` ADD CONSTRAINT `fk_import_issue_1` FOREIGN KEY (`batch_id`) REFERENCES `import_batch` (`id`);
ALTER TABLE `impact_preview` ADD CONSTRAINT `fk_impact_preview_1` FOREIGN KEY (`creator_account_id`) REFERENCES `admin_account` (`id`);
ALTER TABLE `impact_preview` ADD CONSTRAINT `fk_impact_preview_2` FOREIGN KEY (`appointment_id`) REFERENCES `admin_appointment` (`id`);
ALTER TABLE `preview_item` ADD CONSTRAINT `fk_preview_item_1` FOREIGN KEY (`preview_id`) REFERENCES `impact_preview` (`id`);
ALTER TABLE `audit_event` ADD CONSTRAINT `fk_audit_event_1` FOREIGN KEY (`original_submitter_id`) REFERENCES `admin_account` (`id`);
ALTER TABLE `audit_event` ADD CONSTRAINT `fk_audit_event_2` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`);
ALTER TABLE `audit_event` ADD CONSTRAINT `fk_audit_event_3` FOREIGN KEY (`source_department_id`, `company_id`) REFERENCES `department` (`id`, `company_id`);
ALTER TABLE `audit_event` ADD CONSTRAINT `fk_audit_event_4` FOREIGN KEY (`system_id`) REFERENCES `business_system` (`id`);
ALTER TABLE `audit_subject` ADD CONSTRAINT `fk_audit_subject_1` FOREIGN KEY (`audit_event_id`) REFERENCES `audit_event` (`id`);
