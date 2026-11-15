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

Flyway 按 V1→V4 增量迁移为 15 张表。V2 为已有应用生成 `app-数字ID` 接入标识；新应用通过登记时的 code 接入。V3 移除旧应用令牌并清理旧会话，所有用户需重新登录。V4 登记权限中心自身的管理 API、菜单按钮及预置管理员权限。不要修改已经执行的迁移文件。

首次启动创建总管理员，默认用户名 `admin`，可由 `RBAC1_ADMIN_USERNAME` 设置；已存在管理员时保留原密码。

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

JWT 测试、JPA 行为测试和完整 Spring MVC 测试使用临时密钥与测试专用 H2，不需要本地 MySQL。测试覆盖共享 API 解绑、实时授权、退出失效、申请审批、角色创建与日志部门快照。内存数据库测试不代替 MySQL Flyway 迁移验证；真实数据库及课程系统联调仍须在配置好环境后执行。
