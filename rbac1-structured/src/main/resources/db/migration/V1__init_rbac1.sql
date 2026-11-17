-- RBAC1 完整初始化：17 张业务表及系统运行所需的基础数据。
-- 适用于空数据库；由 Flyway 在已配置的数据库内执行，不创建或删除数据库。
-- 总管理员账号由 BootstrapService 按启动配置创建，本文件不保存账号密码。

-- 一、基础表
CREATE TABLE sys_department (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT,
    name VARCHAR(24) NOT NULL,
    INDEX idx_department_parent (parent_id),
    CONSTRAINT fk_department_parent FOREIGN KEY (parent_id) REFERENCES sys_department (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    department_id BIGINT,
    username VARCHAR(32) NOT NULL,
    name VARCHAR(24) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    UNIQUE KEY uq_user_username (username),
    INDEX idx_user_department (department_id),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES sys_department (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_session (
    token_hash BINARY(32) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    INDEX idx_session_user (user_id),
    INDEX idx_session_expires_at (expires_at),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_application (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(24) NOT NULL,
    UNIQUE KEY uq_application_code (code),
    UNIQUE KEY uq_application_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    parent_id BIGINT,
    name VARCHAR(24) NOT NULL,
    code VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
    type VARCHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    UNIQUE KEY uq_permission_application_code (application_id, code),
    INDEX idx_permission_parent (parent_id),
    CONSTRAINT chk_permission_parent CHECK (
        (type = 'MENU' AND parent_id IS NULL) OR (type = 'BUTTON' AND parent_id IS NOT NULL)
    ),
    CONSTRAINT fk_permission_application FOREIGN KEY (application_id) REFERENCES sys_application (id),
    CONSTRAINT fk_permission_parent FOREIGN KEY (parent_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(24) NOT NULL,
    description VARCHAR(48) NOT NULL DEFAULT '',
    role_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    UNIQUE KEY uq_role_name (name),
    CONSTRAINT chk_role_type CHECK (role_type IN ('BUSINESS', 'TOTAL_ADMIN', 'DEPARTMENT_ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    INDEX idx_user_role_role (role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    INDEX idx_role_permission_permission (permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    occurred_at DATETIME(6) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    actor_name VARCHAR(24) NOT NULL,
    action VARCHAR(64) NOT NULL,
    scope VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    detail TEXT NOT NULL,
    result VARCHAR(7) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    INDEX idx_operation_log_time (occurred_at, id),
    CONSTRAINT chk_operation_log_scope CHECK (scope IN ('GLOBAL', 'USER_SCOPE')),
    CONSTRAINT chk_operation_log_result CHECK (result IN ('SUCCESS', 'FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_operation_log_target (
    operation_log_id BIGINT NOT NULL,
    target_no INT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT,
    target_name VARCHAR(128) NOT NULL,
    department_id_at_event BIGINT,
    PRIMARY KEY (operation_log_id, target_no),
    INDEX idx_operation_target_department (department_id_at_event, operation_log_id),
    CONSTRAINT fk_operation_target_log FOREIGN KEY (operation_log_id) REFERENCES sys_operation_log (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 二、API 登记与申请表
CREATE TABLE sys_api (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    operation_id VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL,
    http_method VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    path VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
    UNIQUE KEY uq_api_operation (application_id, operation_id),
    UNIQUE KEY uq_api_route (application_id, http_method, path),
    FOREIGN KEY (application_id) REFERENCES sys_application(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_api_permission (
    permission_id BIGINT NOT NULL,
    api_id BIGINT NOT NULL,
    PRIMARY KEY (permission_id, api_id),
    INDEX idx_api_permission_api (api_id),
    FOREIGN KEY (permission_id) REFERENCES sys_permission(id),
    FOREIGN KEY (api_id) REFERENCES sys_api(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_permission_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    applicant_user_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reviewer_user_id BIGINT,
    approved_role_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6),
    FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    FOREIGN KEY (permission_id) REFERENCES sys_permission(id),
    FOREIGN KEY (reviewer_user_id) REFERENCES sys_user(id),
    FOREIGN KEY (approved_role_id) REFERENCES sys_role(id) ON DELETE SET NULL,
    CHECK (status IN ('PENDING','APPROVED','REJECTED','SATISFIED','WITHDRAWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_create_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    applicant_user_id BIGINT NOT NULL,
    role_name VARCHAR(24) NOT NULL,
    role_description VARCHAR(48) NOT NULL DEFAULT '',
    status VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reviewer_user_id BIGINT,
    created_role_id BIGINT,
    created_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6),
    FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    FOREIGN KEY (reviewer_user_id) REFERENCES sys_user(id),
    FOREIGN KEY (created_role_id) REFERENCES sys_role(id) ON DELETE SET NULL,
    CHECK (status IN ('PENDING','APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_create_request_permission (
    request_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (request_id, permission_id),
    INDEX idx_role_request_permission (permission_id),
    FOREIGN KEY (request_id) REFERENCES sys_role_create_request(id),
    FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 三、角色继承与申请父角色快照
CREATE TABLE sys_role_inheritance (
    role_id BIGINT NOT NULL,
    inherited_role_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, inherited_role_id),
    INDEX idx_role_inheritance_parent (inherited_role_id),
    FOREIGN KEY (role_id) REFERENCES sys_role(id),
    FOREIGN KEY (inherited_role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 父角色 ID 和名称为申请时的快照，不设置指向角色表的外键。
CREATE TABLE sys_role_create_request_inheritance (
    request_id BIGINT NOT NULL,
    inherited_role_id BIGINT NOT NULL,
    inherited_role_name VARCHAR(24) NOT NULL,
    PRIMARY KEY (request_id, inherited_role_id),
    FOREIGN KEY (request_id) REFERENCES sys_role_create_request(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 四、预置角色、权限中心及其权限和 API
INSERT INTO sys_role (name, role_type) VALUES
    ('总管理员', 'TOTAL_ADMIN'),
    ('部门管理员', 'DEPARTMENT_ADMIN');

INSERT INTO sys_application (code, name) VALUES ('permission-center', '权限管理中心');

SET @app=(SELECT id FROM sys_application WHERE code='permission-center');
INSERT INTO sys_permission(application_id,name,code,type) VALUES(@app,'应用与权限','application:view','MENU');
INSERT INTO sys_permission(application_id,name,code,type) VALUES(@app,'角色管理','role:view','MENU');
INSERT INTO sys_permission(application_id,name,code,type) VALUES(@app,'用户与部门','user:view','MENU');
INSERT INTO sys_permission(application_id,name,code,type) VALUES(@app,'用户授权','assignment:view','MENU');
INSERT INTO sys_permission(application_id,name,code,type) VALUES(@app,'操作记录','audit:view','MENU');
INSERT INTO sys_permission(application_id,name,code,type) VALUES(@app,'权限申请审核','request:view','MENU');
INSERT INTO sys_permission(application_id,name,code,type) VALUES(@app,'角色创建申请','roleRequest:view','MENU');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'listApplications','GET','/api/v1/applications');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='application:view';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='application:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'新增应用','application:create','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'createApplication','POST','/api/v1/applications');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='application:create';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='application:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'修改应用名称','application:update','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'updateApplication','PATCH','/api/v1/applications/{applicationId}');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='application:update';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'getApplicationPermissionTree','GET','/api/v1/applications/{applicationId}/permissions');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='application:view';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='application:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'登记菜单或按钮权限','permission:create','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'createPermission','POST','/api/v1/applications/{applicationId}/permissions');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='permission:create';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='application:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'保存权限资料和关联接口','permission:update','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'updatePermission','PATCH','/api/v1/applications/{applicationId}/permissions/{permissionId}');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='permission:update';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'getPermission','GET','/api/v1/applications/{applicationId}/permissions/{permissionId}');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='application:view';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'listRoles','GET','/api/v1/roles');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='role:view';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='role:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'新增业务角色','role:create','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'createRole','POST','/api/v1/roles');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='role:create';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'getRole','GET','/api/v1/roles/{roleId}');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='role:view';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='role:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'保存角色信息和全部权限','role:update','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'updateRole','PUT','/api/v1/roles/{roleId}');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='role:update';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='role:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'删除未关联用户的业务角色','role:delete','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'deleteRole','DELETE','/api/v1/roles/{roleId}');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='role:delete';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'getDepartmentTree','GET','/api/v1/departments');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='user:view';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='user:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'新增部门','department:create','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'createDepartment','POST','/api/v1/departments');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='department:create';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='user:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'修改部门信息','department:update','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'updateDepartment','PATCH','/api/v1/departments/{departmentId}');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='department:update';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'listUsers','GET','/api/v1/users');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='user:view';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='user:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'新增用户','user:create','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'createUser','POST','/api/v1/users');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='user:create';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'getUser','GET','/api/v1/users/{userId}');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='user:view';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='user:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'修改用户、账号状态或所属部门','user:update','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'updateUser','PATCH','/api/v1/users/{userId}');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='user:update';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='assignment:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type)
VALUES(@app,@parent,'分配与撤销业务角色','assignment:manage','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'replaceUserBusinessRoles','PUT','/api/v1/users/{userId}/roles');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='assignment:manage';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='assignment:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'批量分配或撤销业务角色','assignment:batch','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'changeUserRolesInBatch','PATCH','/api/v1/user-role-assignments');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='assignment:batch';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'getMyPermissions','GET','/api/v1/me/permissions');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'listOperationLogs','GET','/api/v1/operation-logs');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='audit:view';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='application:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'查询应用 API 清单','api:manage','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'listApis','GET','/api/v1/applications/{applicationId}/apis');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='api:manage';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'registerApi','POST','/api/v1/applications/{applicationId}/apis');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='api:manage';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'updateApi','PATCH','/api/v1/applications/{applicationId}/apis/{apiId}');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='api:manage';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'deleteApi','DELETE','/api/v1/applications/{applicationId}/apis/{apiId}');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='api:manage';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'getPermissionCatalog','GET','/api/v1/permission-catalog');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='role:view';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'getRequestablePermissions','GET','/api/v1/me/requestable-permissions');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'listPermissionRequests','GET','/api/v1/permission-requests');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'createPermissionRequest','POST','/api/v1/permission-requests');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'getPermissionRequest','GET','/api/v1/permission-requests/{requestId}');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'withdrawPermissionRequest','POST','/api/v1/permission-requests/{requestId}/withdrawals');
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='request:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'审核权限申请并分配业务角色','request:review','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'reviewPermissionRequest','POST','/api/v1/permission-requests/{requestId}/reviews');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='request:review';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'listRoleCreationRequests','GET','/api/v1/role-creation-requests');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='roleRequest:view';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='roleRequest:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'提交角色创建申请','roleRequest:create','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'createRoleCreationRequest','POST','/api/v1/role-creation-requests');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='roleRequest:create';
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'getRoleCreationRequest','GET','/api/v1/role-creation-requests/{requestId}');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='roleRequest:view';
SET @parent=(SELECT id FROM sys_permission WHERE application_id=@app AND code='roleRequest:view');
INSERT INTO sys_permission(application_id,parent_id,name,code,type) VALUES(@app,@parent,'创建角色并通过或拒绝申请','roleRequest:review','BUTTON');
INSERT INTO sys_api(application_id,operation_id,http_method,path) VALUES(@app,'reviewRoleCreationRequest','POST','/api/v1/role-creation-requests/{requestId}/reviews');
INSERT INTO sys_api_permission(permission_id,api_id) SELECT id,LAST_INSERT_ID() FROM sys_permission WHERE application_id=@app AND code='roleRequest:review';
INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r,sys_permission p WHERE r.role_type='TOTAL_ADMIN' AND p.application_id=@app;
INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r,sys_permission p WHERE r.role_type='DEPARTMENT_ADMIN' AND p.application_id=@app AND p.code IN ('user:view','user:create','user:update','assignment:view','assignment:manage','assignment:batch','role:view','audit:view','request:view','request:review','roleRequest:view','roleRequest:create');
