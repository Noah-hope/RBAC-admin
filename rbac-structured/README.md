# RBAC0 结构化后端

独立的 Spring Boot / Spring Data JPA 模块，使用 Java 21、Maven 和 MySQL。接口以 [OpenAPI](../docs/03-api/openapi.yaml) 为准。

## 本地运行

先在 MySQL 新建空库 `rbac_structured`，给应用账号授予该库的建表和读写权限。在仓库根目录执行：

```powershell
$env:RBAC_DB_URL = 'jdbc:mysql://127.0.0.1:3306/rbac_structured'
$env:RBAC_DB_USERNAME = '<MySQL 用户名>'
$env:RBAC_DB_PASSWORD = Read-Host 'MySQL 密码' -MaskInput
$env:RBAC_ADMIN_PASSWORD = Read-Host '初始总管理员密码' -MaskInput
mvn -f rbac-structured/pom.xml spring-boot:run
```

Flyway 创建 10 张业务表并登记两个预置管理角色。首次启动创建总管理员，用户名默认为 `admin`，可通过 `RBAC_ADMIN_USERNAME` 设置。数据库已有总管理员时，启动时保留其密码。

## 会话接口

| 方法与路径 | 行为 |
| --- | --- |
| `POST /api/v1/sessions` | 提交用户名和密码，返回令牌和当前用户 |
| `GET /api/v1/me` | 用 `Authorization: Bearer <令牌>` 查询当前用户 |
| `DELETE /api/v1/sessions/current` | 撤销请求所带令牌对应的会话 |

## 业务系统接入

登记应用后，为它生成一个随机令牌，并计算令牌文本的 SHA-256 摘要。PowerShell 7 示例

```powershell
$applicationToken = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$tokenHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($applicationToken)))
```

由数据库管理员将 `$tokenHash` 写入对应应用。把示例中的摘要占位符和应用 ID 换成实际值。

```sql
UPDATE sys_application SET token_hash = UNHEX('<SHA-256 十六进制摘要>') WHERE id = 1;
```

在业务系统后端保管 `$applicationToken`。用 `X-Application-Token` 请求头发送该令牌，并向 `POST /api/v1/applications/{applicationId}/permission-checks` 提交 `userId` 和 `permissionCode`。

## 测试

为测试单独建一个空 MySQL 库，例如 `rbac_structured_test`。设置 `RBAC_TEST_DB_URL`、`RBAC_TEST_DB_USERNAME` 和 `RBAC_TEST_DB_PASSWORD` 后运行 `mvn -f rbac-structured/pom.xml verify`。测试会在该库写入用户和会话。
