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

Flyway 启动时执行唯一的完整初始化脚本 [V1__init_rbac1.sql](src/main/resources/db/migration/V1__init_rbac1.sql)，创建 17 张业务表，并初始化预置管理角色、权限中心应用、菜单按钮、API 关联及管理员权限。总管理员账号仍按启动配置创建，SQL 不保存账号密码。无需在 Navicat 中手动执行建表 SQL。

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

## 测试

```powershell
mvn -f rbac1-structured/pom.xml verify
```

运行测试的同时生成 Jacoco 覆盖率报告，用浏览器打开 `rbac1-structured/target/site/jacoco/index.html` 查看。

日志统一由 `config/LayerLoggingAspect` 按层输出（接口层 controller、业务层 service、数据访问层 mapper），写到控制台和 `logs/rbac1-structured.log`；各层级别在 `application.yml` 的 `logging.level` 设置，部署时可用 `LOGGING_LEVEL_COM_RBAC_RBAC1_CONTROLLER` 等环境变量覆盖。代码按 [RBAC 代码规范](../.agents/skills/rbac-code-conventions/SKILL.md) 编写：请求放 `dto`（以 DTO 结尾），响应放 `vo`（以 VO 结尾），实体使用 Lombok，同名字段拷贝使用课程提供的 CloneFactory（首次构建前在课程代码 `clonefactory` 目录执行 `mvn install -Dmaven.test.skip=true`）。

JWT 测试、JPA 行为测试和完整 Spring MVC 测试使用临时密钥与测试专用 H2，不需要本地 MySQL。测试覆盖共享 API 解绑、实时授权、退出失效、多层及菱形继承、来源去重、循环拒绝、后代菜单依赖、两类申请、审批回滚和日志快照。控制器映射与 RBAC1 OpenAPI 的 44 个接口核对。完整初始化测试在 H2 MySQL 模式验证 17 张表、基础权限、API 关联、继承外键及申请快照；仅测试时去掉 H2 不支持的 MySQL 列级字符集和排序规则声明。内存数据库测试不代替 MySQL Flyway 迁移验证；真实数据库及课程系统联调仍须在配置好环境后执行。
