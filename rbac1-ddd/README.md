# RBAC1 面向对象（DDD）实现

与 `rbac1-structured` 实现同一份 [RBAC1 需求](../docs/01-requirements/RBAC1需求说明.md)，用 DDD 分层和充血模型组织代码。两者共用同一个数据库，表结构由 `rbac1-structured` 的 Flyway 迁移维护；本模块构建时把该目录引用到 `classpath:db/migration`，因此也能对空库独立启动。

## 分层

```text
com.rbac.rbac1ddd
├── interfaces/rest            接口层：Controller、请求校验、鉴权拦截器、统一错误响应
├── application                应用层：用例编排、事务边界、视图组装
│   ├── auth                   登录退出、认证令牌、权限检查、我的权限、首次启动创建总管理员
│   ├── role                   角色查询、创建、修改、删除
│   ├── department             部门树查询、新增和修改部门
│   ├── user                   用户查询、新增、修改（调动、停用、任免部门管理员），用户角色替换、批量分配撤销
│   ├── app                    应用查询、登记、改名，应用权限树和菜单按钮权限，角色配置权限目录，应用 API 登记
│   ├── audit                  操作记录查询（读模型端口 OperationLogReader）
│   ├── request                权限申请（目录、提交、撤回、审核）、角色创建申请（提交、审核），授权变化后更新待审核权限申请
│   └── shared                 操作记录端口、分页结果
├── domain                     领域层：业务规则所在，不依赖 Spring 和 JPA
│   ├── role                   Role 聚合根、RoleHierarchy 继承图
│   ├── user                   User 聚合根（资料修改、跨部门调动、任免部门管理员）
│   ├── app                    BusinessApplication 聚合根（登记时生成编码、改名）
│   ├── api                    Api 聚合根（接口登记与修改）
│   ├── permission             PermissionDefinition 聚合根（菜单按钮上下级、接口关联）、PermissionCatalog、ManagementApi
│   ├── department             Department 聚合根、DepartmentTree 部门树（防止移动成环）
│   └── request                PermissionRequest、RoleCreationRequest 聚合根，RequestCatalog 申请目录，ApprovalCandidates 候选角色
└── infrastructure             基础设施层：JPA 持久化对象、仓储实现、JWT 签发校验、Argon2 密码摘要
```

依赖方向：接口层 → 应用层 → 领域层 ← 基础设施层。架构测试 `LayerDependencyTest` 会检查这一方向：领域层不依赖 Spring、JPA 和 CloneFactory，应用层不依赖基础设施层。

代码按 [RBAC 代码规范](../.agents/skills/rbac-code-conventions/SKILL.md)编写（改编自老师的 projectmgt-code-conventions）。权限守卫 `Operator` 放在应用层；日志统一由 `infrastructure/logging/LayerLoggingAspect` 输出，业务类不写日志语句。

## 与结构化实现的主要区别

| | 结构化（rbac1-structured） | DDD（本模块） |
|---|---|---|
| 角色、用户 | 实体只有公有字段（贫血模型） | `Role`、`User` 持有关联数据并自带业务方法 |
| 规则位置 | `RoleService`、`RolePermissionService`、`UserAuthorizationService`、`DepartmentService`、`UserService`、`ApplicationService`、`ApiService`、`PermissionRequestService`、`RoleCreationRequestService`、`ApprovalRules` | `Role.createBusiness/reconfigure/ensureDeletable`、`PermissionRequest.submit/withdrawBy/approve/reject/settleWith`、`RoleCreationRequest.submit/approve/reject/previewIn`、`RequestCatalog`、`ApprovalCandidates`、`PermissionDefinition.register/linkApis`、`BusinessApplication.register`、`RoleHierarchy`、`User.replaceBusinessRoles/transferTo/changeDepartmentAdministrator`、`Department.moveUnder`、`DepartmentTree.ensureCanMove`、`Operator.scopeIn/manageableDepartments` |
| 权限计算 | Service 传 roleId 在 Map 上遍历 | `RoleHierarchy` 计算角色有效权限，`User` 计算用户有效权限 |
| 持久化 | Service 直接操作关联表 Mapper | 仓储按聚合整体存取，接口在领域层、实现在基础设施层 |
| 对象拷贝 | 手写赋值 | CloneFactory（注解只写在基础设施层的 PO 上） |
| 测试 | 需要启动 Spring 和数据库 | 领域规则用纯单元测试验证，接口用集成测试验证 |

## 已实现的接口

应用服务返回应用层的 `XxxResult`，Controller 用 `interfaces.rest.vo.XxxVO.from(...)` 转换为响应体，字段名与结构化版本相同。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/login` | 统一登录，签发 RS256 令牌，同一地址 1 分钟内失败 5 次后返回 429 |
| POST | `/auth/logout` | 退出，只撤销当前令牌 |
| GET | `/auth/me` | 当前用户在目标应用中的角色和有效权限（含继承） |
| GET | `/auth/permissions/{permission}` | 菜单或按钮权限检查 |
| GET | `/auth/api-permissions/{operationId}` | 接口权限检查，权限中心自助接口对已登录用户开放 |
| GET | `/auth/departments` | 按层级分页查询统一身份部门 |
| GET | `/auth/departments/{departmentId}/users` | 分页查询指定部门内的启用用户 |
| GET | `/auth/users/{userId}` | 按统一身份 ID 查询用户姓名和账号 |
| GET | `/.well-known/jwks.json` | 签名公钥，无需登录 |
| GET | `/api/v1/roles` | 角色分页查询，部门管理员只看业务角色 |
| POST | `/api/v1/roles` | 创建角色，同时配置直接权限和继承角色 |
| GET | `/api/v1/roles/{roleId}` | 角色详情，区分直接权限与继承权限并显示来源 |
| PUT | `/api/v1/roles/{roleId}` | 修改角色，提交最终的直接权限和继承列表 |
| DELETE | `/api/v1/roles/{roleId}` | 删除角色（有用户关联或继承关系时拒绝） |
| PUT | `/api/v1/users/{userId}/roles` | 替换用户的业务角色列表 |
| PATCH | `/api/v1/user-role-assignments` | 批量分配或撤销业务角色 |
| GET | `/api/v1/departments` | 部门树，总管理员看全部，部门管理员只看本部门及下级部门 |
| POST | `/api/v1/departments` | 新增部门（仅总管理员） |
| PATCH | `/api/v1/departments/{departmentId}` | 修改部门名称或上级部门（仅总管理员），上级不能是本部门或其下级部门 |
| GET | `/api/v1/users` | 用户分页查询，按部门（含下级部门）、关键字和部门名称筛选 |
| POST | `/api/v1/users` | 新增用户，只保存初始密码摘要，可同时任命部门管理员 |
| GET | `/api/v1/users/{userId}` | 用户详情 |
| PATCH | `/api/v1/users/{userId}` | 修改用户资料和账号状态；调动和任免部门管理员仅限总管理员；停用账号立即撤销其全部会话 |
| GET | `/api/v1/applications` | 应用分页查询（仅总管理员） |
| POST | `/api/v1/applications` | 登记应用，编码省略时生成 `app-` 开头的编码 |
| PATCH | `/api/v1/applications/{applicationId}` | 修改应用名称，编码不变 |
| GET | `/api/v1/applications/{applicationId}/permissions` | 应用权限树：菜单及其下的按钮 |
| POST | `/api/v1/applications/{applicationId}/permissions` | 登记菜单或按钮权限并关联 API；权限中心的新权限自动授予总管理员 |
| GET | `/api/v1/applications/{applicationId}/permissions/{permissionId}` | 查看权限及完整关联 API |
| PATCH | `/api/v1/applications/{applicationId}/permissions/{permissionId}` | 修改权限名称、标识，完整替换关联 API |
| GET | `/api/v1/applications/{applicationId}/apis` | 按关键字分页查询应用 API |
| POST | `/api/v1/applications/{applicationId}/apis` | 登记 API，应用内 operationId 唯一、请求方法与路径组合唯一 |
| PATCH | `/api/v1/applications/{applicationId}/apis/{apiId}` | 修改 API 资料 |
| DELETE | `/api/v1/applications/{applicationId}/apis/{apiId}` | 移除 API 登记及其全部权限关联，权限保留 |
| GET | `/api/v1/me/permissions` | 我的权限：按应用列出已获菜单和按钮及直接配置的来源角色，只列显式分配的业务角色 |
| GET | `/api/v1/permission-catalog` | 角色配置权限目录：按应用列出全部登记的菜单和按钮（仅管理员） |
| GET | `/api/v1/operation-logs` | 操作记录搜索和分页；部门管理员只看操作时在管理范围内的用户记录，批量记录只列出范围内的对象 |
| GET | `/api/v1/me/requestable-permissions` | 本人可申请的权限目录：未获得菜单时只列菜单，菜单（直接或继承）获得后列其下未获得按钮 |
| GET | `/api/v1/permission-requests` | 申请分页查询：`view=MINE` 本人提交的，`view=REVIEW` 管理员按审核范围查看 |
| POST | `/api/v1/permission-requests` | 提交权限申请，已拥有、已有待审核申请或缺少所属菜单时拒绝 |
| GET | `/api/v1/permission-requests/{requestId}` | 申请详情，有权审核者同时看到候选角色（含继承权限和无关权限判断） |
| POST | `/api/v1/permission-requests/{requestId}/withdrawals` | 申请人撤回待审核申请 |
| POST | `/api/v1/permission-requests/{requestId}/reviews` | 审核：通过时按当前授权重新校验候选角色并只分配所选角色；权限已满足时转为已满足 |
| GET | `/api/v1/role-creation-requests` | 角色创建申请分页查询，部门管理员只看本人提交的 |
| POST | `/api/v1/role-creation-requests` | 部门管理员提交角色创建申请，按新建角色规则校验，保存父角色名称快照 |
| GET | `/api/v1/role-creation-requests/{requestId}` | 申请详情，按父角色当前配置预览有效权限；父角色已删除时预览为 null |
| POST | `/api/v1/role-creation-requests/{requestId}/reviews` | 总管理员审核：通过时按父角色最新配置实际创建角色，失败时申请保持待审核，不自动分配角色 |

以上 47 个接口即 `RBAC1-api.yaml` 的全部操作（`SharedAcceptanceTest` 检查全部有映射），路径、请求和响应字段与 `rbac1-structured` 相同，契约见 [rbac1-ddd-api.yaml](../docs/03-api/rbac1-ddd-api.yaml)。两个后端使用同一对 RSA 密钥和相同参数的 Argon2 密码摘要，能互相校验对方签发的令牌和保存的密码。

## 运行

1. 先把课程提供的 CloneFactory 安装到本地 Maven 仓库（只需一次），在课程代码的 `clonefactory` 目录执行：

   ```bash
   mvn install -Dmaven.test.skip=true
   ```

2. 在仓库根目录运行测试，Jacoco 报告生成在 `rbac1-ddd/target/site/jacoco/index.html`：

   ```bash
   mvn -f rbac1-ddd/pom.xml test
   ```

3. 启动前设置与结构化模块相同的环境变量 `RBAC1_DB_URL`、`RBAC1_DB_USERNAME`、`RBAC1_DB_PASSWORD`、`RBAC1_JWT_ISSUER`、`RBAC1_JWT_PRIVATE_KEY`、`RBAC1_JWT_PUBLIC_KEY`；空库首次启动还要设置 `RBAC1_ADMIN_PASSWORD`（账号默认 `admin`，可用 `RBAC1_ADMIN_USERNAME` 修改）。端口用 `RBAC1_DDD_PORT` 设置，默认 8082。日志输出到控制台和 `logs/rbac1-ddd.log`，各层级别在 `application.yml` 的 `logging.level` 中设置，部署时可用 `LOGGING_LEVEL_COM_RBAC_RBAC1DDD_INTERFACES` 等环境变量覆盖。

课程系统的角色码映射、API 登记、目录授权和 `RBAC1_CORS_ALLOWED_ORIGINS` 配置与结构化版共用，见[课程系统统一身份接入](../rbac1-structured/README.md#课程系统统一身份接入)。
