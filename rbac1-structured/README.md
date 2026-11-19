# RBAC1 结构化后端

Java 21、Spring Boot、Spring Data JPA、MySQL 8 和 Flyway。接口以 [OpenAPI](../docs/03-api/RBAC1-api.yaml) 为准，在 RBAC0 副本上扩展角色继承。

Controller 接收并校验请求，Service 按业务步骤组织处理，mapper 目录中的 Spring Data Repository 完成持久化。实体只承载数据；不引入领域服务接口或 version 字段。

## 启动

以下命令从仓库根目录执行。先在 MySQL 创建空数据库 `rbac1`，为应用账号配置该库的建表与读写权限。

```powershell
java rbac1-structured/tools/GenerateJwtKeys.java rbac1-structured/.local
$env:RBAC1_JWT_PRIVATE_KEY = 'file:' + (Resolve-Path rbac1-structured/.local/jwt-private.pem).Path
$env:RBAC1_JWT_PUBLIC_KEY = 'file:' + (Resolve-Path rbac1-structured/.local/jwt-public.pem).Path
$env:RBAC1_JWT_ISSUER = 'http://localhost:8081'
$env:RBAC1_DB_URL = 'jdbc:mysql://127.0.0.1:3306/rbac1'
$env:RBAC1_DB_USERNAME = '<MySQL 用户名>'
$env:RBAC1_DB_PASSWORD = Read-Host 'MySQL 密码' -MaskInput
$env:RBAC1_ADMIN_PASSWORD = Read-Host '首次启动管理员密码' -MaskInput
mvn -f rbac1-structured/pom.xml spring-boot:run
```

密钥只生成一次，重复启动使用原密钥；私钥不能提交或提供给业务系统。接入系统使用实际 `issuer` 和 `/.well-known/jwks.json` 验签地址。

默认端口为 `8081`，可通过 `RBAC1_PORT` 修改。RBAC0 与 RBAC1 使用各自数据库和配置变量。

Flyway 启动时按版本应用迁移。[V1__init_rbac1.sql](src/main/resources/db/migration/V1__init_rbac1.sql) 建立 17 张基础表并初始化预置授权，[V2__application_role_codes.sql](src/main/resources/db/migration/V2__application_role_codes.sql) 增加应用角色码配置。总管理员账号按启动配置创建，SQL 不保存账号密码。

该脚本用于空数据库，本地开发与后续 Docker MySQL 使用同一份文件。Flyway 在 `flyway_schema_history` 中记录执行结果，后续启动不会重复初始化。将来有结构变化时再增加增量迁移，不修改已经执行过的版本。

首次启动创建总管理员，默认用户名 `admin`，可由 `RBAC1_ADMIN_USERNAME` 设置；已存在管理员时保留原密码。

## RBAC1 扩展

- 仅业务角色允许多父、多层继承，方向为 `role_id` 子角色继承 `inherited_role_id` 父角色。禁止自继承、循环继承和预置管理员参与继承。
- 直接权限与父角色列表一起保存。有效权限按全部可达角色合并，按权限 ID 去重；来源只列实际直接配置权限的角色。
- 新增直接权限不能重复勾选已继承权限；已有直接权限后来与继承重叠时保留，取消直接关联后仍可通过继承获得。
- 修改角色前校验自身及全部后代的菜单依赖。按钮仍存在但菜单丢失时，整次修改失败。
- 用户只关联显式分配的角色。角色删除前须解除用户关联以及两个方向的正式继承关系。
- 权限申请与候选角色检查包含所有应用的有效权限；授权变化后只检查受影响用户的待审核申请。
- 角色创建申请保存原始直接权限及父角色 ID、名称快照；父角色删除不阻止历史查看。审批可调整配置，使用最新权限重新校验，通过后不自动分配用户。

`RolePermissionService` 批量读取关系图并计算权限，不逐层查询数据库，也不缓存跨请求的权限结果。Controller 负责请求格式校验；Service 负责权限、状态和一致性规则。角色配置、申请审批结果与成功操作记录使用同一事务。

## 接入与鉴权

| 方法与路径 | 用途 |
| --- | --- |
| `POST /auth/login` | 提交 username、password、systems，返回 JWT |
| `POST /auth/logout` | 撤销当前令牌，204 无正文 |
| `GET /auth/me` | 当前应用的用户、角色和前端权限 |
| `GET /auth/permissions/{permission}` | 当前应用单项菜单或按钮检查 |
| `GET /auth/api-permissions/{operationId}` | 当前应用 API 功能权限检查 |
| `GET /.well-known/jwks.json` | 公开验签公钥 |

登录权限中心时 `systems=["permission-center"]`；课程应用须先登记 `code=course-project-management`。多个应用的令牌通过 `X-Application-Code` 指定本次查询目标。JWT 不包含权限；后端同时检查会话、账号状态和当前授权，退出和角色撤销均影响后续请求。

管理请求按后端实际方法及路径查找登记 API，不接受客户端伪造 operationId 来代替实际路由。API 允许任一关联权限满足；总管理员职责、部门范围和业务对象范围仍分别校验。本人权限与申请查询是登录基础能力，不能依赖先获得管理菜单权限。

API 登记与权限关联分开维护，解绑保留 API。权限申请通过时分配现有最小业务角色；角色创建申请在角色成功创建后才通过，不自动分配。业务写入、成功日志和申请状态由事务一起保存；失败写操作的结果单独记录为全局审计，不记录密码和令牌。

## 课程系统统一身份接入

### 配置应用角色码

两套 RBAC1 后端共用角色码配置。应用启用映射后，`GET /auth/me` 从直接分配的业务角色及全部父角色中找出该应用的角色码。角色无需配置应用权限即可映射。已有应用默认沿用数字角色 ID。角色码只表示应用身份，具体操作仍按应用权限码校验。

在权限中心登记 `course-project-management`，创建业务角色「课程教师」「课程学生」「课程管理员」。在 Flyway 迁移完成后，由部署方在 RBAC1 数据库执行以下配置。若角色已按其他名称创建，将查询条件替换为现有角色名称。确认应用和三个业务角色都存在后再执行，执行失败时回滚事务。

```sql
START TRANSACTION;

SET @course_app = (SELECT id FROM sys_application WHERE code = 'course-project-management');
SET @teacher_role = (SELECT id FROM sys_role WHERE name = '课程教师' AND role_type = 'BUSINESS');
SET @student_role = (SELECT id FROM sys_role WHERE name = '课程学生' AND role_type = 'BUSINESS');
SET @admin_role = (SELECT id FROM sys_role WHERE name = '课程管理员' AND role_type = 'BUSINESS');

INSERT INTO sys_application_role_code (application_id, role_id, role_code)
VALUES (@course_app, @teacher_role, 'teacher'),
       (@course_app, @student_role, 'student'),
       (@course_app, @admin_role, 'admin');

UPDATE sys_application SET role_codes_enabled = TRUE WHERE id = @course_app;
COMMIT;
```

给学生分配「课程学生」，给教师分配「课程教师」或继承它的业务角色。需要使用管理功能的教师可同时分配「课程管理员」，再在该角色上配置课程系统使用的权限码，例如添加教师的 `TEC`。测试账号的工号或学号须在课程系统对应名单中。其他应用以自己的应用 ID 配置角色码，可以复用 `admin` 等值。映射使用角色 ID，角色改名后继续有效。

### 配置目录授权和跨域来源

在课程应用 `course-project-management` 下按下表登记 GET 接口，将这些 API 关联到课程系统用于选部门、选人的权限，再把权限授予需要使用目录的业务角色。调用人登录时将课程应用加入 `systems`，之后使用同一个 Bearer 令牌调用；多个受众时继续使用 `X-Application-Code`。没有目录授权时返回 403。

| 路径 | operationId | 返回数据 |
| --- | --- | --- |
| `/auth/departments` | `listIdentityDepartments` | 根部门或指定父部门的直接下级，含 `hasChildren` |
| `/auth/departments/{departmentId}/users` | `listIdentityDepartmentUsers` | 该部门下的启用用户，支持按姓名或账号分页查询 |
| `/auth/users/{userId}` | `lookupIdentityUser` | 按选人返回的 `userId` 读取姓名和工号或学号 |

目录的 `userId` 与 JWT 的 `sub` 使用同一个用户 ID 字符串，`no` 使用登录账号。部门 `code` 使用部门 ID 字符串。登录接受字符串账号和 JSON 整数学号，带前导零的账号继续传字符串。课程系统原有的大写字母数字权限短码可直接登记，例如 `CV`。

两套后端都使用 `RBAC1_CORS_ALLOWED_ORIGINS` 配置浏览器来源。默认允许 `http://localhost:5173` 和 `http://127.0.0.1:5173`。前端独立部署时填写实际浏览器地址的协议、域名和端口，多个来源用逗号分隔。例如：

```sh
export RBAC1_CORS_ALLOWED_ORIGINS='https://course.example.edu,https://rbac.example.edu'
```

把变量传给后端进程或 Swarm 服务并重启。浏览器可对 `/auth/**`、`/api/v1/**` 和签名公钥发起跨域请求。预检由 CORS 过滤器处理，正式请求照常校验会话及权限。

## 测试

```powershell
mvn -f rbac1-structured/pom.xml verify
```

运行测试的同时生成 Jacoco 覆盖率报告，用浏览器打开 `rbac1-structured/target/site/jacoco/index.html` 查看。

日志统一由 `config/LayerLoggingAspect` 按层输出（接口层 controller、业务层 service、数据访问层 mapper），写到控制台和 `logs/rbac1-structured.log`；各层级别在 `application.yml` 的 `logging.level` 设置，部署时可用 `LOGGING_LEVEL_COM_RBAC_RBAC1_CONTROLLER` 等环境变量覆盖。代码按 [RBAC 代码规范](../.agents/skills/rbac-code-conventions/SKILL.md) 编写：请求放 `dto`（以 DTO 结尾），响应放 `vo`（以 VO 结尾），实体使用 Lombok，同名字段拷贝使用课程提供的 CloneFactory（首次构建前在课程代码 `clonefactory` 目录执行 `mvn install -Dmaven.test.skip=true`）。

JWT 测试、JPA 行为测试和完整 Spring MVC 测试使用临时密钥与测试专用 H2，不需要本地 MySQL。测试覆盖共享 API 解绑、实时授权、退出失效、多层及菱形继承、来源去重、循环拒绝、后代菜单依赖、两类申请、审批回滚和日志快照。控制器映射与 RBAC1 OpenAPI 的 47 个接口核对。迁移测试在 H2 MySQL 模式验证 V1、V2 的 18 张表、基础数据和外键约束，仅测试时去掉 H2 不支持的列级声明。角色码验收测试覆盖多层继承、零权限身份、跨应用隔离和旧契约兼容。部署后需在 MySQL 上验证 Flyway 迁移，再执行课程系统联调。
